/* Run with: node scripts/test-resume-forms.cjs. No npm packages are required. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const jsRoot = path.resolve(__dirname, '../src/main/resources/static/js');
const sources = ['api.js', 'components.js', 'views/resumes.js'].map(file => ({
  file, source: fs.readFileSync(path.join(jsRoot, file), 'utf8')
}));
const plain = value => JSON.parse(JSON.stringify(value));

function runtime() {
  const http = {interceptors: {request: {use() {}}, response: {use() {}}}};
  const context = vm.createContext({
    window: {}, console, setTimeout, clearTimeout,
    Vue: {reactive: value => value},
    axios: {create: () => http},
    localStorage: {getItem: () => '', setItem() {}, removeItem() {}}
  });
  for (const {file, source} of sources) vm.runInContext(source, context, {filename: file});
  return context.window.Quiz;
}

// Construct component state without mounting a DOM. The actual methods,
// computed properties, data functions, mixins and API runner are production code.
function instance(component, props = {}, extras = {}) {
  const emitted = [];
  const target = {
    $refs: {},
    $emit(name, value) {emitted.push({name, value: plain(value)});},
    $nextTick(action) {return Promise.resolve().then(() => action.call(target));},
    ...props, ...extras
  };
  for (const [name, definition] of Object.entries(component.props || {})) {
    if (name in target) continue;
    target[name] = typeof definition.default === 'function' ? definition.default() : definition.default;
  }
  function apply(definition) {
    for (const mixin of definition.mixins || []) apply(mixin);
    if (definition.data) Object.assign(target, definition.data.call(target));
    for (const [name, method] of Object.entries(definition.methods || {})) target[name] = method.bind(target);
    for (const [name, getter] of Object.entries(definition.computed || {})) {
      Object.defineProperty(target, name, {get: () => getter.call(target), configurable: true});
    }
  }
  apply(component);
  return {target, emitted};
}

function listFixture(props = {}) {
  const Q = runtime();
  const input = {
    value: '', selectionStart: 0, selectionEnd: 0, validityMessage: '', selections: [],
    setCustomValidity(message) {this.validityMessage = message;},
    setSelectionRange(start, end) {this.selections.push([start, end]); this.selectionStart = start; this.selectionEnd = end;},
    setRangeText(replacement, start, end, selectionMode) {
      this.value = this.value.slice(0, start) + replacement + this.value.slice(end);
      assert.equal(selectionMode, 'end');
      this.selectionStart = this.selectionEnd = start + replacement.length;
    }
  };
  const {target: editor, emitted} = instance(Q.components.NumberedList, props, {$refs: {input}});
  input.value = editor.draft;
  function enter(value, extraEvent = {}) {
    input.value = value;
    input.selectionStart = input.selectionEnd = value.length;
    editor.input({target: input, ...extraEvent});
  }
  function key(keyName, position) {
    input.selectionStart = input.selectionEnd = position;
    let prevented = false;
    editor.keydown({key: keyName, target: input, preventDefault() {prevented = true;}});
    return prevented;
  }
  return {Q, editor, emitted, input, enter, key};
}

function resumeFixture({valid = true} = {}) {
  const Q = runtime();
  const posts = [], routes = [], notices = [];
  const titleInput = {focusCount: 0, focus() {this.focusCount++;}};
  const form = {
    validityChecks: 0,
    elements: {namedItem(name) {assert.equal(name, 'resumeTitle'); return titleInput;}},
    reportValidity() {this.validityChecks++; return valid;}
  };
  Q.http.post = async (url, body) => {posts.push({url, body: plain(body)}); return {id: 42};};
  Q.notify = message => notices.push(message);
  const {target: resume} = instance(Q.views.ResumeNew, {}, {$router: {async push(route) {routes.push(route);}}});
  return {Q, resume, form, titleInput, posts, routes, notices, event: {currentTarget: form}};
}

const tests = [];
function test(name, run) {tests.push({name, run});}

test('Enter creates the next number while the saved items contain only content', () => {
  const Q = runtime();
  const result = Q.listEditor.normalize('第一条\n', '第一条\n'.length);
  assert.equal(result.text, '1. 第一条\n2. ');
  assert.equal(result.position, result.text.length);
  assert.deepEqual(plain(Q.listEditor.items(result.text)), ['第一条']);
});

test('Pasted numbered items are renumbered once and keep their content', () => {
  const {Q, enter, input, emitted} = listFixture();
  enter('7. 第一个项目\r\n12. 第二个项目');
  assert.equal(input.value, '1. 第一个项目\n2. 第二个项目');
  assert.deepEqual(emitted.at(-1).value, ['第一个项目', '第二个项目']);
  assert.deepEqual(plain(Q.listEditor.items(input.value)), ['第一个项目', '第二个项目']);
});

test('A decimal at the beginning of an item is kept', () => {
  const {enter, input, emitted} = listFixture();
  enter('3.14 是圆周率\n2.0 版本迁移');
  assert.equal(input.value, '1. 3.14 是圆周率\n2. 2.0 版本迁移');
  assert.deepEqual(emitted.at(-1).value, ['3.14 是圆周率', '2.0 版本迁移']);
});

test('Typing and then pressing Enter updates the editor and emits no empty extra item', () => {
  const {enter, editor, input, emitted} = listFixture();
  enter('负责服务开发');
  assert.equal(input.value, '1. 负责服务开发');
  enter(`${input.value}\n`);
  assert.equal(input.value, '1. 负责服务开发\n2. ');
  assert.equal(editor.draft, input.value);
  assert.deepEqual(emitted.at(-1), {name: 'update:modelValue', value: ['负责服务开发']});
  assert.deepEqual(input.selections.at(-1), [input.value.length, input.value.length]);
  enter(`${input.value}完成性能优化`);
  assert.deepEqual(emitted.at(-1).value, ['负责服务开发', '完成性能优化']);
});

test('Backspace at a new empty item removes that item instead of trapping the caret after its number', () => {
  const {enter, key, input, emitted} = listFixture();
  enter('内容\n');
  assert.equal(input.value, '1. 内容\n2. ');
  assert.equal(key('Backspace', input.value.length), true);
  assert.equal(input.value, '1. 内容');
  assert.deepEqual(emitted.at(-1).value, ['内容']);
});

test('Backspace at an item start joins its content with the previous item without copying its number', () => {
  const {enter, key, input, emitted} = listFixture();
  enter('前\n后');
  assert.equal(key('Backspace', input.value.indexOf('后')), true);
  assert.equal(input.value, '1. 前后');
  assert.deepEqual(emitted.at(-1).value, ['前后']);
});

test('Delete at the end of an item joins the next item without copying its number', () => {
  const {enter, key, input, emitted} = listFixture();
  enter('前\n后');
  assert.equal(key('Delete', input.value.indexOf('\n')), true);
  assert.equal(input.value, '1. 前后');
  assert.deepEqual(emitted.at(-1).value, ['前后']);
});

test('Backspace clears an otherwise empty first numbered item', () => {
  const {enter, key, input, emitted} = listFixture();
  enter('1. ');
  assert.equal(key('Backspace', input.value.length), true);
  assert.equal(input.value, '');
  assert.deepEqual(emitted.at(-1).value, []);
});

test('Backspace removes a leading empty numbered item and renumbers the following item', () => {
  const {enter, key, input, emitted} = listFixture();
  enter('\n后');
  assert.equal(input.value, '1. \n2. 后');
  assert.equal(key('Backspace', input.value.indexOf('\n')), true);
  assert.equal(input.value, '1. 后');
  assert.deepEqual(emitted.at(-1).value, ['后']);
});

test('Chinese composition is left untouched until composition ends', () => {
  const {editor, enter, input, emitted} = listFixture();
  editor.composing = true;
  enter('输入中文');
  assert.equal(input.value, '输入中文');
  assert.equal(editor.draft, '');
  assert.equal(emitted.length, 0);
  assert.equal(input.selections.length, 0);
  editor.compositionEnd({target: input, isComposing: false});
  assert.equal(input.value, '1. 输入中文');
  assert.deepEqual(emitted.at(-1).value, ['输入中文']);
});

test('An input event marked isComposing does not rewrite text or emit a value', () => {
  const {editor, enter, input, emitted} = listFixture();
  enter('正在组词', {isComposing: true});
  assert.equal(input.value, '正在组词');
  assert.equal(editor.draft, '');
  assert.equal(emitted.length, 0);
});

test('Thirty items of two hundred characters each are valid, totaling six thousand', () => {
  const items = Array.from({length: 30}, () => '字'.repeat(200));
  const {editor, input} = listFixture({modelValue: items});
  editor.validate();
  assert.equal(editor.items.length, 30);
  assert.equal(editor.items.reduce((total, item) => total + item.length, 0), 6000);
  assert.equal(editor.validationError, '');
  assert.equal(input.validityMessage, '');
});

test('Thirty-one short items are invalid even with fewer than two hundred total characters', () => {
  const {editor, input} = listFixture({modelValue: Array.from({length: 31}, () => '字'), label: '工作内容'});
  editor.validate();
  assert.match(editor.validationError, /工作内容最多 30 条/);
  assert.equal(input.validityMessage, editor.validationError);
});

test('One item of two hundred and one characters reports its item number and limit', () => {
  const {editor, input} = listFixture({modelValue: ['字'.repeat(201)], label: '项目详情'});
  editor.validate();
  assert.match(editor.validationError, /项目详情第 1 条最多 200 字，当前 201 字/);
  assert.equal(input.validityMessage, editor.validationError);
});

test('An oversized item after a blank line reports its visible line number while the payload skips blanks', () => {
  const {editor, enter, input, emitted} = listFixture({label: '项目详情'});
  enter(`正常内容\n\n${'字'.repeat(201)}`);
  assert.match(editor.validationError, /项目详情第 3 条最多 200 字，当前 201 字/);
  assert.equal(input.validityMessage, editor.validationError);
  assert.deepEqual(emitted.at(-1).value, ['正常内容', '字'.repeat(201)]);
});

test('Numbers and blank lines do not satisfy a required list', () => {
  const {editor, enter, input, emitted} = listFixture({required: true, label: '工作内容'});
  enter('1. \n2.   \n3. ');
  assert.equal(editor.items.length, 0);
  assert.match(editor.validationError, /工作内容至少填写 1 条内容/);
  assert.equal(input.validityMessage, editor.validationError);
  assert.deepEqual(emitted.at(-1).value, []);
});

test('An optional empty list is valid', () => {
  const {editor, input} = listFixture();
  editor.validate();
  assert.equal(editor.validationError, '');
  assert.equal(input.validityMessage, '');
});

for (const title of ['', ' \t\n ']) {
  test(`${title ? 'Whitespace-only' : 'Empty'} resume title shows a message and focuses the title instead of posting`, async () => {
    const {resume, event, titleInput, form, posts, routes} = resumeFixture();
    resume.title = title;
    resume.entries = [{blockDefId: 3, content: {公司: '已填写公司', 工作内容: ['已完成一条分块']}}];
    await resume.save(event);
    assert.match(resume.error, /请填写简历标题/);
    assert.equal(titleInput.focusCount, 1);
    assert.equal(form.validityChecks, 0);
    assert.equal(posts.length, 0);
    assert.equal(routes.length, 0);
    assert.equal(resume.busy, false);
  });
}

test('A completed work block posts month/present dates and unnumbered items then enters its workbench', async () => {
  const {Q, resume, event, posts, routes, notices} = resumeFixture();
  const schema = [
    {key: '公司', label: '公司', type: 'text', required: true},
    {key: '开始时间', label: '开始时间', type: 'date', required: false},
    {key: '结束时间', label: '结束时间', type: 'date', required: false},
    {key: '工作内容', label: '工作内容', type: 'list', required: false}
  ];
  resume.add({id: 3, name: '工作经历', repeatable: 1, fieldSchema: schema});
  const entry = resume.entries[0];
  const {target: fields, emitted: fieldEvents} = instance(Q.components.BlockFields, {schema, modelValue: entry.content});
  const setField = (field, value) => {
    fields.set(field, value);
    entry.content = fieldEvents.at(-1).value;
    fields.modelValue = entry.content;
  };
  setField(schema[0], '示例公司');
  setField(schema[1], '2026-10');
  fields.setPresent(schema[2], true);
  entry.content = fieldEvents.at(-1).value;
  fields.modelValue = entry.content;
  const {enter, emitted} = listFixture();
  enter('负责服务开发\n完成性能优化');
  setField(schema[3], emitted.at(-1).value);
  resume.title = '  Java 后端工程师  ';
  await resume.save(event);
  assert.deepEqual(posts, [{url: '/resumes', body: {
    title: 'Java 后端工程师', blocks: [{blockDefId: 3, sort: 0, content: {
      公司: '示例公司', 开始时间: '2026-10', 结束时间: '至今', 工作内容: ['负责服务开发', '完成性能优化']
    }}]
  }}]);
  assert.deepEqual(routes, ['/resumes/42']);
  assert.deepEqual(notices, ['简历已创建']);
  assert.equal(resume.busy, false);
});

test('A form rejected by reportValidity does not post or navigate', async () => {
  const {resume, event, form, posts, routes} = resumeFixture({valid: false});
  resume.title = '已填写标题';
  await resume.save(event);
  assert.equal(form.validityChecks, 1);
  assert.equal(posts.length, 0);
  assert.equal(routes.length, 0);
  assert.equal(resume.busy, false);
});

test('Invalid fields report their visible label and browser validation message', () => {
  const {resume} = resumeFixture();
  resume.invalid({target: {
    getAttribute: name => name === 'aria-label' ? '工作内容' : null,
    validationMessage: '第 1 条最多 200 字'
  }});
  assert.equal(resume.error, '请检查工作内容：第 1 条最多 200 字');
});

test('A busy form ignores another create attempt', async () => {
  const {resume, event, form, posts} = resumeFixture();
  resume.title = '合法标题';
  resume.busy = true;
  await resume.save(event);
  assert.equal(form.validityChecks, 0);
  assert.equal(posts.length, 0);
  assert.equal(resume.busy, true);
});

test('A second click during an in-flight create does not duplicate the request', async () => {
  const {Q, resume, event, posts, routes} = resumeFixture();
  let finish;
  Q.http.post = (url, body) => {
    posts.push({url, body: plain(body)});
    return new Promise(resolve => {finish = resolve;});
  };
  resume.title = '合法标题';
  const pending = resume.save(event);
  assert.equal(resume.busy, true);
  await resume.save(event);
  assert.equal(posts.length, 1);
  finish({id: 43});
  await pending;
  assert.equal(resume.busy, false);
  assert.deepEqual(routes, ['/resumes/43']);
});

test('A failed API request displays its error, clears busy and allows a successful retry', async () => {
  const {Q, resume, event, routes} = resumeFixture();
  let attempts = 0;
  Q.http.post = async () => {attempts++; throw new Error('保存失败，请重试');};
  resume.title = '合法标题';
  await resume.save(event);
  assert.equal(resume.error, '保存失败，请重试');
  assert.equal(resume.busy, false);
  assert.equal(attempts, 1);
  assert.deepEqual(routes, []);
  Q.http.post = async () => {attempts++; return {id: 44};};
  await resume.save(event);
  assert.equal(resume.error, '');
  assert.equal(resume.busy, false);
  assert.equal(attempts, 2);
  assert.deepEqual(routes, ['/resumes/44']);
});

(async () => {
  let failures = 0;
  for (const {name, run} of tests) {
    try {await run(); console.log(`PASS ${name}`);}
    catch (error) {failures++; console.error(`FAIL ${name}\n${error.stack}`);}
  }
  console.log(`Resume form behavior: ${tests.length} checked, ${failures} failures`);
  process.exitCode = failures ? 1 : 0;
})();
