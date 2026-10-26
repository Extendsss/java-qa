/* No npm install is required: use the same browser compiler shipped with the app. */
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const root = path.resolve(__dirname, '..');
const jsRoot = path.join(root, 'src/main/resources/static/js');
const files = ['api.js', 'components.js', 'views/questions.js', 'views/practice.js', 'views/resumes.js', 'views/admin.js', 'router.js'];
let failures = 0;
for (const file of files) {
  try {new vm.Script(fs.readFileSync(path.join(jsRoot, file), 'utf8'), {filename: file});}
  catch (error) {failures++; console.error(`Syntax check failed: ${file}: ${error.message}`);}
}
if (failures) process.exit(1);
console.log(`JavaScript syntax: ${files.length} files passed`);
const decodeEntities = value => value.replace(/&(?:#(\d+)|#x([0-9a-f]+)|(amp|lt|gt|quot|apos|nbsp));/gi, (all, decimal, hex, name) => decimal ? String.fromCodePoint(Number(decimal)) : hex ? String.fromCodePoint(parseInt(hex,16)) : ({amp:'&',lt:'<',gt:'>',quot:'"',apos:"'",nbsp:'\u00a0'}[name] || all));
// Vue's DOM compiler uses an element only to decode HTML entities. Support both
// textContent and the temporary <div foo="..."> used for attribute values.
const documentStub = {createElement() {return {
  set innerHTML(value) {
    this.textContent = decodeEntities(value);
    const attribute = value.match(/^<div foo="([\s\S]*)">/);
    this.children = [{getAttribute() {return decodeEntities(attribute ? attribute[1] : value);}}];
  }, textContent: '', children: [], content: {firstChild: null}
};}};
const captured = [];
const context = {
  console, setTimeout, clearTimeout, document: documentStub, window: {},
  localStorage: {getItem() {return '';}, setItem() {}, removeItem() {}},
  sessionStorage: {getItem() {return '';}, setItem() {}},
  VueRouter: {createWebHashHistory() {}, createRouter() {return {beforeEach() {}};}},
  axios: {create() {return {interceptors: {request: {use() {}}, response: {use() {}}}};}}
};
vm.createContext(context);
vm.runInContext(fs.readFileSync(path.join(root, 'src/main/resources/static/vendor/vue.global.prod.js'), 'utf8'), context, {filename: 'vue.global.prod.js'});
const realVue = context.Vue;
context.Vue = {...realVue, createApp(app) {captured.push(app); return {config: {globalProperties: {}}, component() {}, use() {}, mount() {}};}};
for (const file of files) vm.runInContext(fs.readFileSync(path.join(jsRoot, file), 'utf8'), context, {filename: file});
const quiz = context.window.Quiz;
const templates = [...Object.entries(quiz.components), ...Object.entries(quiz.views), ['App', captured[0]]];
for (const [name, component] of templates) {
  try {
    realVue.compile(component.template);
    console.log(`PASS template: ${name}`);
  } catch (error) {
    failures++;
    console.error(`FAIL template: ${name}: ${error.message}`);
  }
}
console.log(`Vue templates: ${templates.length} checked, ${failures} failures`);
process.exitCode = failures ? 1 : 0;
