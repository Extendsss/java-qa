(function () {
  'use strict';const Q=window.Quiz;
  const routes=[
    {path:'/login',component:Q.views.Login,meta:{title:'登录'}},
    {path:'/',component:Q.views.Home,meta:{title:'刷题首页'}},
    {path:'/practice',component:Q.views.Practice,meta:{title:'专注练习'}},
    {path:'/questions',component:Q.views.Questions,meta:{title:'题目库'}},
    {path:'/questions/new',component:Q.views.QuestionEditor,meta:{title:'录入题目'}},
    {path:'/questions/:id/edit',component:Q.views.QuestionEditor,meta:{title:'编辑题目'}},
    {path:'/questions/:id',component:Q.views.QuestionDetail,meta:{title:'题目详情'}},
    {path:'/resumes',component:Q.views.Resumes,meta:{title:'简历专项'}},
    {path:'/resumes/new',component:Q.views.ResumeNew,meta:{title:'录入简历'}},
    {path:'/resumes/:id/practice',component:Q.views.ResumeWorkbench,meta:{title:'简历专项练习'}},
    {path:'/resumes/:id',component:Q.views.ResumeWorkbench,meta:{title:'简历工作台'}},
    {path:'/my/wrong',component:Q.views.History,meta:{title:'我的错题本'}},
    {path:'/my/records',component:Q.views.History,meta:{title:'个人中心'}},
    {path:'/admin/users',component:Q.views.AdminUsers,meta:{title:'用户管理',admin:true}},
    {path:'/admin/questions',component:Q.views.Questions,meta:{title:'题目管理',admin:true}},
    {path:'/admin/questions/new',component:Q.views.QuestionEditor,meta:{title:'公共题录入',admin:true}},
    {path:'/admin/resumes',component:Q.views.Resumes,meta:{title:'简历管理',admin:true}},
    {path:'/admin/block-defs',component:Q.views.AdminBlockDefs,meta:{title:'模板配置',admin:true}},
    {path:'/admin/categories',component:Q.views.AdminCategories,meta:{title:'分类管理',admin:true}},
    {path:'/:pathMatch(.*)*',redirect:'/'}
  ];
  Q.router=VueRouter.createRouter({history:VueRouter.createWebHashHistory(),routes,scrollBehavior(){return{top:0};}});
  Q.router.beforeEach(async to=>{
    Q.state.mobileNav=false;
    if(Q.state.token&&!Q.state.user){try{Q.state.user=await Q.get('/auth/me');}catch(_){Q.clearSession();}}
    if(to.path!=='/login'&&!Q.state.user)return'/login';
    if(to.path==='/login'&&Q.state.user)return'/';
    if(to.meta.admin&&Q.state.user?.role!=='ADMIN')return'/';
    document.title=`${to.meta.title||'面试练习'} · 知序`;
  });
  const App={
    data:()=>({state:Q.state,logoutBusy:false}),
    computed:{isLogin(){return this.$route.path==='/login';},isAdmin(){return this.state.user?.role==='ADMIN';},initial(){return(this.state.user?.nickname||this.state.user?.username||'知').slice(0,1);}},
    methods:{async logout(){if(this.logoutBusy)return;this.logoutBusy=true;try{await Q.post('/auth/logout');Q.clearSession();this.$router.replace('/login');}catch(e){Q.notify(e.message);}finally{this.logoutBusy=false;}}},
    template:`<div><div v-if="state.notice" class="toast" role="status">{{state.notice}}</div><router-view v-if="isLogin"/><div v-else-if="state.user" class="shell"><aside class="sidebar" :class="{open:state.mobileNav}"><router-link to="/" class="brand"><span class="brand-mark">知</span>知序</router-link><div class="brand-caption">THINK · PRACTICE · GROW</div><div class="nav-label">我的学习空间</div><nav><router-link class="nav-link" to="/"><span class="nav-icon">◈</span>刷题首页</router-link><router-link class="nav-link" to="/questions"><span class="nav-icon">▤</span>题目库</router-link><router-link class="nav-link" to="/resumes"><span class="nav-icon">▧</span>简历专项</router-link><router-link class="nav-link" to="/my/wrong"><span class="nav-icon">↺</span>我的错题本</router-link><router-link class="nav-link" to="/my/records"><span class="nav-icon">◷</span>个人中心</router-link></nav><template v-if="isAdmin"><div class="nav-label">管理后台</div><nav><router-link class="nav-link" to="/admin/users"><span class="nav-icon">♧</span>用户管理</router-link><router-link class="nav-link" to="/admin/questions"><span class="nav-icon">▦</span>题目管理</router-link><router-link class="nav-link" to="/admin/resumes"><span class="nav-icon">▣</span>简历管理</router-link><router-link class="nav-link" to="/admin/block-defs"><span class="nav-icon">⚙</span>模板配置</router-link><router-link class="nav-link" to="/admin/categories"><span class="nav-icon">⊞</span>分类管理</router-link></nav></template><div class="sidebar-bottom">每天进步一点。<br>让知识的积累，成为从容的底气。</div></aside><main class="main"><header class="topbar"><button class="mobile-menu" @click="state.mobileNav=!state.mobileNav" aria-label="切换导航">☰</button><span class="topbar-path">学习空间 / {{ $route.meta.title }}</span><div class="account"><span class="avatar">{{initial}}</span><span class="nickname">{{state.user.nickname||state.user.username}}<br><small>{{isAdmin?'管理员':'学习者'}}</small></span><button class="link" :disabled="logoutBusy" @click="logout">退出</button></div></header><div class="content"><router-view :key="$route.fullPath"/></div></main></div><div v-else class="boot">正在进入学习空间…</div></div>`
  };
  const app=Vue.createApp(App);
  app.config.globalProperties.Quiz=Q;
  app.config.errorHandler=(error)=>{console.error(error);Q.notify('页面出现异常，请刷新后重试');};
  Object.entries(Q.components).forEach(([name,component])=>app.component(name,component));
  app.use(Q.router);app.mount('#app');
})();
