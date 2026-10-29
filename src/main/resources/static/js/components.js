(function () {
  'use strict';
  const Q = window.Quiz;
  Q.components = {};
  Q.components.Pager = {
    props: {page: {default: 1}, size: {default: 10}, total: {default: 0}}, emits: ['change'],
    computed: {pages() {return Math.max(1, Math.ceil(this.total / this.size));}},
    methods: {go(value) {const n = Math.max(1, Math.min(this.pages, Number(value) || 1)); if (n !== Number(this.page)) this.$emit('change', n);}},
    template: `<div class="pager"><span>共 {{total}} 条</span><div class="pager-controls"><button :disabled="page<=1" @click="go(page-1)">上一页</button><input aria-label="页码" type="number" :value="page" min="1" :max="pages" @change="go($event.target.value)"><span>/ {{pages}}</span><button :disabled="page>=pages" @click="go(page+1)">下一页</button></div></div>`
  };
  Q.components.EmptyState = {props: {title: {default: '这里还没有内容'}, text: {default: '试试调整筛选条件，或添加第一条内容。'}}, template: `<div class="empty"><div class="empty-symbol">◇</div><h3>{{title}}</h3><p>{{text}}</p><slot></slot></div>`};
  Q.monthValue = value => typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value.slice(0, 7) : value;
  Q.listEditor = {
    prefix: /^\d+\.(?:[ \t]|$)/,
    lines(text) {return text ? text.replace(/\r\n?/g, '\n').split('\n').map(line => line.replace(this.prefix, '')) : [];},
    items(text) {return this.lines(text).filter(line => line.trim());},
    format(lines) {return lines.map((line, index) => `${index + 1}. ${line}`).join('\n');},
    normalize(text, position) {
      const lines = text.replace(/\r\n?/g, '\n').split('\n');
      const formatted = this.format(this.lines(text));
      if (!text) return {text: '', position: 0};
      const before = text.slice(0, position);
      const index = before.split('\n').length - 1;
      const offset = position - before.lastIndexOf('\n') - 1;
      const prefixLength = (lines[index].match(this.prefix) || [''])[0].length;
      const formattedLines = formatted.split('\n');
      const lineStart = formattedLines.slice(0, index).reduce((length, line) => length + line.length + 1, 0);
      return {text: formatted, position: lineStart + Math.min(formattedLines[index].length, `${index + 1}. `.length + Math.max(0, offset - prefixLength))};
    }
  };
  Q.components.NumberedList = {
    props: {modelValue: {default: () => []}, required: {default: false}, label: {default: '分条内容'}}, emits: ['update:modelValue'],
    data() {return {draft: Q.listEditor.format(this.modelValue), composing: false, touched: false};},
    computed: {
      items() {return Q.listEditor.items(this.draft);},
      validationError() {
        if (this.required && !this.items.length) return `${this.label}至少填写 1 条内容`;
        if (this.items.length > 30) return `${this.label}最多 30 条，当前 ${this.items.length} 条`;
        const lines = Q.listEditor.lines(this.draft);
        const index = lines.findIndex(item => item.length > 200);
        return index < 0 ? '' : `${this.label}第 ${index + 1} 条最多 200 字，当前 ${lines[index].length} 字`;
      }
    },
    watch: {
      modelValue: {deep: true, handler(value) {if (JSON.stringify(value) !== JSON.stringify(this.items)) this.draft = Q.listEditor.format(value);}},
      validationError() {this.$nextTick(() => this.validate());}
    },
    mounted() {this.validate();},
    methods: {
      validate() {this.$refs.input.setCustomValidity(this.validationError);},
      input(event) {
        if (this.composing || event.isComposing) return;
        const input = event.target;
        const start = Q.listEditor.normalize(input.value, input.selectionStart);
        const end = Q.listEditor.normalize(input.value, input.selectionEnd);
        this.draft = start.text;
        if (input.value !== this.draft) {
          input.value = this.draft;
          input.setSelectionRange(start.position, end.position);
        }
        this.touched = true;
        this.validate();
        this.$emit('update:modelValue', this.items);
      },
      keydown(event) {
        if (this.composing || event.isComposing || !['Backspace', 'Delete'].includes(event.key)) return;
        const input = event.target;
        const position = input.selectionStart;
        if (position !== input.selectionEnd) return;
        const lineStart = input.value.slice(0, position).lastIndexOf('\n') + 1;
        const lineEnd = input.value.indexOf('\n', position);
        const line = input.value.slice(lineStart, lineEnd < 0 ? input.value.length : lineEnd);
        const prefixLength = (line.match(Q.listEditor.prefix) || [''])[0].length;
        if (event.key === 'Backspace' && position <= lineStart + prefixLength) {
          event.preventDefault();
          if (!lineStart) {
            if (line.length !== prefixLength) return;
            input.setRangeText('', 0, lineEnd < 0 ? prefixLength : lineEnd + 1, 'end');
          } else input.setRangeText('', lineStart - 1, lineStart + prefixLength, 'end');
        } else if (event.key === 'Delete' && position === lineEnd) {
          event.preventDefault();
          const nextLine = input.value.slice(lineEnd + 1).split('\n')[0];
          const nextPrefixLength = (nextLine.match(Q.listEditor.prefix) || [''])[0].length;
          input.setRangeText('', lineEnd, lineEnd + 1 + nextPrefixLength, 'end');
        } else return;
        this.input({target: input});
      },
      compositionEnd(event) {this.composing = false; this.input(event);}
    },
    template: `<div class="numbered-list"><textarea ref="input" :value="draft" :required="required" :aria-label="label" :aria-invalid="touched&&!!validationError" @input="input" @keydown="keydown" @compositionstart="composing=true" @compositionend="compositionEnd" @invalid="touched=true" placeholder="1. 填写第一条内容，回车自动添加下一条&#10;2. 每条最多 200 字，最多 30 条"></textarea><div class="hint-line">已填写 {{items.length}} / 30 条 · 每条最多 200 字 · 回车自动编号</div><div v-if="touched&&validationError" class="field-error" role="alert">{{validationError}}</div></div>`
  };
  Q.components.BlockFields = {
    props: {schema: {default: () => []}, modelValue: {default: () => ({})}}, emits: ['update:modelValue'],
    data: () => ({previousDates: Object.create(null)}),
    computed: {fields() {return Q.json(this.schema, []);}},
    methods: {
      set(field, value) {this.$emit('update:modelValue', {...this.modelValue, [field.key]: value});},
      value(field) {const v = this.modelValue[field.key]; return field.type === 'list' ? (Array.isArray(v) ? v : []) : (field.type === 'date' ? Q.monthValue(v) : v) || '';},
      supportsPresent(field) {return field.type === 'date' && (field.key === '结束时间' || field.label === '结束时间');},
      setPresent(field, checked) {
        if (checked) {
          this.previousDates[field.key] = this.modelValue[field.key] || '';
          this.set(field, '至今');
        } else this.set(field, this.previousDates[field.key] || '');
      }
    },
    template: `<div><div v-for="f in fields" :key="f.key" class="field"><div :class="{'date-field':f.type==='date'}"><label>{{f.label}} <span v-if="f.required" style="color:var(--red)">*</span><numbered-list v-if="f.type==='list'" :model-value="value(f)" @update:model-value="set(f,$event)" :required="f.required" :label="f.label"/><textarea v-else-if="f.type==='textarea'" :value="value(f)" @input="set(f,$event.target.value)" :required="f.required" maxlength="2000"></textarea><input v-else-if="f.type==='date'" type="month" :value="value(f)==='至今'?'':value(f)" :disabled="supportsPresent(f)&&value(f)==='至今'" @input="set(f,$event.target.value)" @click="$event.target.showPicker?.()" :required="f.required" max="9999-12"><input v-else :value="value(f)" @input="set(f,$event.target.value)" :required="f.required" maxlength="200"></label><label v-if="supportsPresent(f)" class="date-present"><input type="checkbox" :checked="value(f)==='至今'" @change="setPresent(f,$event.target.checked)">至今</label></div><div v-if="f.type==='textarea'" class="hint-line">整段最多 2000 字，支持自由换行。</div></div></div>`
  };
  Q.components.MdEditor = {
    props: {modelValue: {default: ''}, label: {default: ''}, required: {default: false}, maxlength: {default: 30000}, placeholder: {default: ''}},
    emits: ['update:modelValue'],
    data() {return {showPreview: true};},
    computed: {html() {return Q.md(this.modelValue);}},
    template: `<div class="md-editor"><div class="md-editor-bar"><span class="small muted">支持 Markdown：**加粗**、\`代码\`、\`\`\`java 代码块</span><label class="toggle"><input type="checkbox" v-model="showPreview"><span class="small">实时预览</span></label></div><div class="md-editor-body" :class="{'with-preview':showPreview}"><textarea :value="modelValue" @input="$emit('update:modelValue',$event.target.value)" :required="required" :maxlength="maxlength" :placeholder="placeholder" :aria-label="label"></textarea><div v-show="showPreview" class="md md-editor-preview" v-html="html"></div></div></div>`
  };
  Q.emptyContent = schema => Object.fromEntries(Q.json(schema, []).map(f => [f.key, f.type === 'list' ? [] : '']));
  Q.components.QuestionForm = {
    mixins: [Q.mix], props: {initial: {default: null}, parentId: {default: null}, resumeId: {default: null}, blockId: {default: null}}, emits: ['saved', 'cancel'],
    data() {return {categories: [], blocks: [], form: {categoryId: '', type: 'QA', difficulty: 2, title: '', content: '', options: [{key:'A',text:''},{key:'B',text:''}], answer: '', analysis: '', blockId: this.blockId || null}, types: Q.types};},
    async created() {await this.run(async () => {this.categories = Q.list(await Q.get('/categories')); if (this.initial) {this.form = {...this.form, ...this.initial, options: Q.json(this.initial.options, [{key:'A',text:''},{key:'B',text:''}])}; if (!this.form.options.length) this.form.options = [{key:'A',text:''},{key:'B',text:''}];} else if (this.categories.length) this.form.categoryId = this.categories[0].id; const rid = this.resumeId || this.form.resumeId; if (rid) this.blocks = Q.list(await Q.get(`/resumes/${rid}/blocks`));});},
    methods: {
      addOption() {if (this.form.options.length < 6) this.form.options.push({key: String.fromCharCode(65 + this.form.options.length), text:''});},
      removeOption(index) {if (this.form.options.length > 2) {this.form.options.splice(index,1); this.form.options.forEach((o,i)=>o.key=String.fromCharCode(65+i));}},
      save() {return this.run(async () => {const f=this.form; const body={categoryId:Number(f.categoryId),type:f.type,difficulty:Number(f.difficulty),title:f.title,content:f.content,options:['SINGLE','MULTI'].includes(f.type)?f.options:null,answer:f.answer,analysis:f.analysis,blockId:f.blockId?Number(f.blockId):null}; if(this.parentId)body.parentId=Number(this.parentId);if(this.resumeId)body.resumeId=Number(this.resumeId); const value=this.initial?await Q.put(`/questions/${this.initial.id}`,body):await Q.post('/questions',body); Q.notify('题目已保存');this.$emit('saved',value);});}
    },
    template: `<form @submit.prevent="save"><div v-if="error" class="error">{{error}}</div><div v-if="parentId" class="info">当前题目将作为追问加入问题链，归属与父题保持一致。</div><div class="grid"><label class="field">分类<select v-model="form.categoryId" required><option v-for="c in categories" :value="c.id">{{c.name}}</option></select></label><label class="field">题型<select v-model="form.type"><option v-for="(label,key) in types" :value="key">{{label}}</option></select></label><label class="field">难度<select v-model.number="form.difficulty"><option :value="1">简单</option><option :value="2">中等</option><option :value="3">困难</option></select></label><label v-if="resumeId||form.resumeId" class="field">关联简历条目<select v-model="form.blockId"><option :value="null">不关联条目</option><option v-for="b in blocks" :value="b.id">{{b.blockName}} · #{{b.id}}</option></select></label></div><label class="field">题目标题<input v-model="form.title" maxlength="200" required placeholder="用一句话概括问题"></label><label class="field">题目内容<md-editor v-model="form.content" :required="true" :maxlength="30000" label="题目内容" placeholder="补充完整的问题描述，支持 Markdown 与 \`\`\` 代码块"></md-editor></label><div v-if="['SINGLE','MULTI'].includes(form.type)" class="field"><div class="section-head"><label>选项（2～6 项）</label><button type="button" class="link" :disabled="form.options.length>=6" @click="addOption">＋ 添加选项</button></div><div v-for="(o,i) in form.options" :key="i" class="option-editor"><span class="option-letter">{{o.key}}</span><input v-model="o.text" required maxlength="1000" :aria-label="'选项 '+o.key"><button type="button" :disabled="form.options.length<=2" @click="removeOption(i)">移除</button></div></div><label class="field">{{form.type==='QA'?'参考答案':'标准答案'}}<select v-if="form.type==='JUDGE'" v-model="form.answer" required><option value="" disabled>选择判断结果</option><option value="T">正确</option><option value="F">错误</option></select><md-editor v-else-if="form.type==='QA'" v-model="form.answer" :required="true" :maxlength="30000" label="参考答案" placeholder="填写清晰、完整的参考答案，支持 Markdown 与代码块"></md-editor><textarea v-else v-model="form.answer" required maxlength="30000" :placeholder="form.type==='FILL'?'多个空用 || 分隔，如：进程||线程':form.type==='MULTI'?'填写至少两个选项字母，如 ACD':'填写一个选项字母，如 A'"></textarea></label><label class="field">解析 <small>选填</small><md-editor v-model="form.analysis" :maxlength="30000" label="解析" placeholder="补充答题思路、易错点或参考资料，支持 Markdown 与代码块"></md-editor></label><div class="form-footer"><button type="button" @click="$emit('cancel')" :disabled="busy">取消</button><button class="primary" :disabled="busy">{{busy?'保存中…':'保存题目'}}</button></div></form>`
  };
  Q.components.ResumePreview = {
    props: {blocks: {default: () => []}, modelValue: {default: 0}, editable: {default: false}}, emits: ['update:modelValue','edit','add','remove'],
    computed: {current() {return this.blocks[this.modelValue] || null;}, content() {return this.current ? Q.json(this.current.content,{}) : {};}, fields() {if(!this.current)return[];const schema=Q.json(this.current.fieldSchema,[]);return [...schema.filter(f=>Object.prototype.hasOwnProperty.call(this.content,f.key)),...Object.keys(this.content).filter(key=>!schema.some(f=>f.key===key)).map(key=>({key,label:key}))];}},
    methods: {value(field) {const v = this.content[field.key]; return field.type === 'date' ? Q.monthValue(v) : v;}},
    template: `<aside class="preview"><div class="preview-header"><span class="small muted">简历内容预览</span><button v-if="editable" class="link" @click="$emit('add')">＋ 添加条目</button></div><div v-if="current" class="preview-page"><div class="section-head"><h2>{{current.blockName}}</h2><div v-if="editable" class="actions"><button class="link" @click="$emit('edit',current)">编辑</button><button class="link" @click="$emit('remove',current)">删除</button></div></div><dl v-for="f in fields" :key="f.key" class="preview-field"><dt>{{f.label}}</dt><dd v-if="Array.isArray(content[f.key])"><ol><li v-for="(item,i) in content[f.key]" :key="i">{{item}}</li></ol><span v-if="!content[f.key].length" class="muted">未填写</span></dd><dd v-else>{{value(f)||'未填写'}}</dd></dl></div><empty-state v-else title="还没有简历内容" text="先添加分块条目，再围绕经历构建问题链。"/><div v-if="blocks.length" class="preview-footer"><button :disabled="modelValue===0" @click="$emit('update:modelValue',modelValue-1)">上一页</button><select :value="modelValue" @change="$emit('update:modelValue',Number($event.target.value))" aria-label="跳转简历条目"><option v-for="(b,i) in blocks" :value="i">{{i+1}} / {{blocks.length}} · {{b.blockName}}</option></select><button :disabled="modelValue>=blocks.length-1" @click="$emit('update:modelValue',modelValue+1)">下一页</button></div></aside>`
  };
  Q.components.PracticePanel = {
    mixins:[Q.mix], props:{resumeId:{default:null},settings:{default:null},questionId:{default:null}}, emits:['question','finished'],
    data:()=>({questions:[],index:0,userAnswer:'',multi:[],feedback:null,recorded:false,correctCount:0,answered:0,finished:false,children:[],started:false}),
    computed:{current(){return this.questions[this.index]||null;},options(){return this.current?Q.json(this.current.options,[]):[];},progress(){return this.questions.length?Math.min(100,(this.index/this.questions.length)*100):0;}},
    created(){this.start();},
    methods:{
      start(){return this.run(async()=>{let config=this.settings||Q.json(sessionStorage.getItem('quiz.practice'),{mode:'RANDOM',count:10});if(this.questionId){const chain=Q.list(await Q.get(`/questions/${this.questionId}/chain`,{brief:1}));this.questions=chain.filter(q=>String(q.id)===String(this.questionId));}else if(this.resumeId){config={mode:'ORDER',resumeId:Number(this.resumeId),count:100,startIndex:0};this.questions=[];let batch;do{batch=Q.list(await Q.post('/practice/start',config));this.questions.push(...batch);config.startIndex+=batch.length;}while(batch.length===100);}else this.questions=Q.list(await Q.post('/practice/start',config));this.started=true;this.reset();});},
      reset(){this.userAnswer='';this.multi=[];this.feedback=null;this.recorded=false;this.children=[];if(this.current)this.$emit('question',this.current);},
      submit(selfCorrect){return this.run(async()=>{const q=this.current;const body={questionId:q.id,userAnswer:q.type==='MULTI'?this.multi.join(''):this.userAnswer};if(q.type==='QA'&&selfCorrect===undefined){this.feedback=await Q.post('/practice/reveal',body);return;}if(q.type==='QA')body.selfCorrect=selfCorrect;this.feedback=await Q.post('/practice/submit',body);this.recorded=true;this.answered++;if(q.type==='QA'?selfCorrect===1:this.feedback.correct)this.correctCount++;});},
      next(){if(this.index+1>=this.questions.length){this.finished=true;this.$emit('finished');}else{this.index++;this.reset();}},
      follow(){return this.run(async()=>{const chain=Q.list(await Q.get(`/questions/${this.current.id}/chain`,{brief:1}));this.children=chain.filter(q=>String(q.parentId)===String(this.current.id));if(this.children.length===1)this.chooseChild(this.children[0]);});},
      chooseChild(q){const existing=this.questions.findIndex((item,i)=>i>this.index&&item.id===q.id);if(existing>=0)this.questions.splice(existing,1);this.questions.splice(this.index+1,0,q);this.index++;this.reset();},
      again(){this.index=0;this.correctCount=0;this.answered=0;this.finished=false;return this.start();}
    },
    template:`<section class="card"><div v-if="error" class="error">{{error}}</div><div v-if="busy&&!started" class="loading">正在准备练习…</div><div v-else-if="finished" class="practice-score"><span class="badge public">本轮练习完成</span><div class="score">{{answered?Math.round(correctCount/answered*100):0}}<span style="font-size:25px">%</span></div><h2>每一次练习，都在积累。</h2><p class="muted">共作答 {{answered}} 题，掌握 {{correctCount}} 题。</p><div class="actions" style="justify-content:center"><button class="primary" @click="again" :disabled="busy">再练一轮</button><router-link class="button" to="/my/wrong">复习错题</router-link></div></div><empty-state v-else-if="started&&!current" title="没有符合条件的题目" text="调整筛选条件，或先录入一些题目。"><router-link to="/" class="button">返回练习首页</router-link></empty-state><template v-else-if="current"><div class="practice-top"><span>{{resumeId?'简历专项':'专注练习'}} · {{index+1}} / {{questions.length}}</span><span>已掌握 {{correctCount}} 题</span></div><div class="progress"><span :style="{width:progress+'%'}"></span></div><div class="badges"><span class="badge">{{typeName(current.type)}}</span><span class="badge warn">{{difficultyName(current.difficulty)}}</span><span v-if="current.parentId" class="badge">追问</span></div><h2 style="margin-top:20px;line-height:1.7">{{current.title}}</h2><div class="question-content md" v-html="md(current.content)"></div><fieldset :disabled="busy||!!feedback" style="border:0;padding:0;margin:0"><label v-if="current.type==='QA'" class="field"><span class="small muted">试着组织自己的答案，也可以直接查看参考答案。</span><textarea v-model="userAnswer" maxlength="30000" placeholder="在这里写下你的思路…"></textarea></label><template v-if="current.type==='SINGLE'||current.type==='MULTI'"><label v-for="o in options" :key="o.key" class="option"><input v-if="current.type==='SINGLE'" type="radio" v-model="userAnswer" :value="o.key"><input v-else type="checkbox" v-model="multi" :value="o.key"><span class="option-letter">{{o.key}}</span><span class="md-inline" v-html="mdInline(o.text)"></span></label></template><template v-if="current.type==='JUDGE'"><label class="option"><input type="radio" v-model="userAnswer" value="T">正确</label><label class="option"><input type="radio" v-model="userAnswer" value="F">错误</label></template><label v-if="current.type==='FILL'" class="field">填写答案<input v-model="userAnswer" maxlength="10000" placeholder="多个空请用 || 分隔"></label></fieldset><button v-if="!feedback" class="primary" :disabled="busy" @click="submit()">{{busy?'提交中…':current.type==='QA'?'查看参考答案':'提交答案'}}</button><div v-if="feedback" class="result" :class="{wrong:recorded&&((current.type==='QA'&&!feedback.correct)||!feedback.correct)}"><h3>{{!recorded?'对照答案，评估掌握程度':feedback.correct?'已掌握，继续保持':'已加入错题本，稍后再巩固'}}</h3><div class="small muted">{{current.type==='QA'?'参考答案':'正确答案'}}</div><div class="answer-content md" v-html="md(feedback.rightAnswer)"></div><template v-if="feedback.analysis"><div class="small muted">答案解析</div><div class="answer-content md" v-html="md(feedback.analysis)"></div></template><div v-if="current.type==='QA'&&!recorded" class="actions"><button class="success" :disabled="busy" @click="submit(1)">✓ 掌握了</button><button class="danger" :disabled="busy" @click="submit(0)">还没掌握</button></div></div><div v-if="recorded" class="actions"><button class="primary" :disabled="busy" @click="next">{{index+1>=questions.length?'完成练习':'下一题 →'}}</button><button v-if="current.hasFollowUp||feedback.hasFollowUp" :disabled="busy" @click="follow">继续追问 ↳</button></div><div v-if="children.length>1" class="block-form"><h3>选择一个追问方向</h3><div class="chain"><a v-for="q in children" :key="q.id" href="#" @click.prevent="chooseChild(q)">{{q.title}} →</a></div></div></template></section>`
  };
})();
