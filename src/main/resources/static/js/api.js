(function () {
  'use strict';
  const Q = window.Quiz = {};
  Q.state = Vue.reactive({user: null, token: localStorage.getItem('quiz.token') || '', notice: '', mobileNav: false});
  Q.types = {QA: '问答题', SINGLE: '单选题', MULTI: '多选题', JUDGE: '判断题', FILL: '填空题'};
  Q.difficulties = {1: '简单', 2: '中等', 3: '困难'};
  Q.json = (value, fallback) => { if (typeof value !== 'string') return value == null ? fallback : value; try {return JSON.parse(value);} catch (_) {return fallback;} };
  Q.list = value => Array.isArray(value) ? value : value && value.list || [];
  Q.editable = item => !!item && !!Q.state.user && (Q.state.user.role === 'ADMIN' || (String(item.ownerId) === String(Q.state.user.id) && Number(item.isPublic) === 0));
  Q.time = value => value ? String(value).replace('T', ' ').slice(0, 16) : '—';
  Q.percent = value => `${Number(value || 0).toFixed(1)}%`;
  Q.clean = obj => Object.fromEntries(Object.entries(obj).filter(([, v]) => v !== '' && v !== null && v !== undefined));
  Q.notify = message => { Q.state.notice = message; clearTimeout(Q.noticeTimer); Q.noticeTimer = setTimeout(() => {Q.state.notice = '';}, 3500); };
  Q.run = async (vm, action) => { if (vm.busy) return; vm.busy = true; vm.error = ''; try {return await action();} catch (e) {vm.error = e.message || '请求失败，请重试';} finally {vm.busy = false;} };
  Q.http = axios.create({baseURL: '/api', timeout: 20000});
  Q.http.interceptors.request.use(config => {if (Q.state.token) config.headers.satoken = Q.state.token; return config;});
  Q.http.interceptors.response.use(response => {
    const result = response.data;
    if (result.code === 0) return result.data;
    if (result.code === 401) { Q.clearSession(); if (Q.router && Q.router.currentRoute.value.path !== '/login') Q.router.replace('/login'); }
    return Promise.reject(new Error(result.message || '请求失败'));
  }, error => Promise.reject(new Error(error.response && error.response.data && error.response.data.message || (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '连接服务失败，请检查服务是否已启动'))));
  Q.get = (url, params) => Q.http.get(url, {params: Q.clean(params || {})});
  Q.post = (url, body) => Q.http.post(url, body);
  Q.put = (url, body) => Q.http.put(url, body);
  Q.del = url => Q.http.delete(url);
  Q.clearSession = () => {Q.state.token = ''; Q.state.user = null; localStorage.removeItem('quiz.token');};
  Q.saveSession = user => {Q.state.user = user; Q.state.token = user.token; localStorage.setItem('quiz.token', user.token);};
  Q.views = {};
  Q.mix = {data: () => ({busy: false, error: ''}), methods: {run(action) {return Q.run(this, action);}, editable: Q.editable, typeName(type) {return Q.types[type] || type;}, difficultyName(level) {return Q.difficulties[level] || '—';}, time: Q.time, percent: Q.percent}};
})();
