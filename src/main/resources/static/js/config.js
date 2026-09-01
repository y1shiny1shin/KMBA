/**
 * KMBA 模块配置
 * 添加新模块只需在这里加一条记录，无需修改 index.js
 *
 * 字段说明:
 *   id       - 模块标识（CLI 命令中使用的名称）
 *   title    - 导航栏显示名称
 *   desc     - 面板描述
 *   list     - list 接口路径（overview 无此字段）
 *   unload   - 卸载配置（overview 无此字段）
 *     url        - 卸载接口路径（字符串），或动态生成函数 (row) => url，
 *                  或有 choices 时 (picked) => url
 *     params     - 设置查询参数的函数 (qs, row) => void
 *     choices    - 可选，多选项 [{v, t, d}]，有此字段时卸载前显示选择弹窗
 */
const MODULES = [
    // ── 概览 ──
    { id: 'overview', title: '概览', desc: '全局统计、可疑命中、规则管理' },

    // ── Tomcat ──
    { id: 'servlet',  title: 'Servlet',   desc: 'route -> className',       list: '/servlet/list',
        unload: { url: '/servlet/unload',  params: (qs, r) => qs.set('urlPath', r.key || '') } },
    { id: 'filter',   title: 'Filter',    desc: 'filterName -> URLPattern',  list: '/filter/list',
        unload: { url: '/filter/unload',   params: (qs, r) => qs.set('filterName', r.aux || '') } },
    { id: 'listener', title: 'Listener',  desc: 'className',                 list: '/listener/list',
        unload: { url: '/listener/unload', params: (qs, r) => qs.set('className', r.className || '') } },
    { id: 'socket',   title: 'WebSocket', desc: 'urlName -> className',      list: '/socket/list',
        unload: { url: '/socket/unload',   params: (qs, r) => { qs.set('urlName', r.key || ''); qs.set('className', r.className || ''); } } },
    { id: 'proxyValve', title: 'ProxyValve', desc: 'first/basic -> className', list: '/proxyValve/list',
        unload: {
            url: (r) => String(r.key || '').toLowerCase() === 'basic' ? '/proxyValve/unloadBasic' : '/proxyValve/unloadFirst',
            params: (qs, r) => qs.set('className', r.className || '')
        } },
    { id: 'valve',    title: 'Valve',     desc: 'className',                 list: '/valve/list',
        unload: { url: '/valve/unload',    params: (qs, r) => qs.set('className', r.className || '') } },
    { id: 'executor', title: 'Executor',  desc: 'className',                 list: '/executor/list',
        unload: {
            choices: [
                { v: 'gently', t: '温柔卸载 (unloadGently)', d: '对业务影响更小，但攻击者仍可能继续添加恶意 Executor。' },
                { v: 'brutly', t: '暴力卸载 (unloadBrutly)', d: '更彻底，但可能导致后续 Executor 加载失败或类型不兼容，业务风险更高。' }
            ],
            url: (p) => p === 'brutly' ? '/executor/unloadBrutly' : '/executor/unloadGently',
            params: (qs, r) => qs.set('className', r.className || '')
        } },
    { id: 'thread',   title: 'Thread',    desc: 'threadName -> className',   list: '/thread/list',
        unload: { url: '/thread/unload',   params: (qs, r) => { qs.set('threadName', r.key || ''); qs.set('className', r.className || ''); } } },
    { id: 'timer',    title: 'Timer',     desc: 'className',                 list: '/timer/list',
        unload: { url: '/timer/unload',   params: (qs, r) => qs.set('className', r.className || '') } },
    { id: 'upgrade',  title: 'Upgrade',   desc: 'upgradeName -> className',  list: '/upgrade/list',
        unload: { url: '/upgrade/unload',  params: (qs, r) => qs.set('upgradeName', r.key || '') } },
    { id: 'agent',    title: 'Agent',     desc: 'transformer className',     list: '/agent/list',
        unload: { url: '/agent/unload',   params: (qs, r) => qs.set('className', r.className || '') } },

    // ── SpringMVC ──
    { id: 'struts2Action',       title: 'Struts2 Action',       desc: 'urlPath -> className',  list: '/Struts2Action/list',
        unload: { url: '/Struts2Action/unload', params: (qs, r) => qs.set('urlPath', r.key || '') } },
    { id: 'springMvcController',  title: 'SpringMVC Controller',  desc: 'urlPath -> className',  list: '/SpringMvcController/list',
        unload: { url: '/SpringMvcController/unload',  params: (qs, r) => qs.set('urlPath', r.key || '') } },
    { id: 'springMvcInterceptor', title: 'SpringMVC Interceptor', desc: 'className',              list: '/SpringMvcInterceptor/list',
        unload: { url: '/SpringMvcInterceptor/unload', params: (qs, r) => qs.set('className', r.className || '') } },

    // ── SpringFlux ──
    { id: 'springFluxWebFilter',    title: 'SpringFlux WebFilter',    desc: 'filterName -> className', list: '/SpringFluxWebFilter/list',
        unload: { url: '/SpringFluxWebFilter/unload',    params: (qs, r) => qs.set('className', r.className || '') } },
    { id: 'springFluxNettyHandler', title: 'SpringFlux NettyHandler', desc: 'className',                list: '/SpringFluxNettyHandler/list',
        unload: { url: '/SpringFluxNettyHandler/unload', params: (qs, r) => qs.set('className', r.className || '') } },
    { id: 'springFluxHandlerMethod', title: 'SpringFlux HandlerMethod', desc: 'urlPath -> className',    list: '/SpringFluxHandlerMethod/list',
        unload: { url: '/SpringFluxHandlerMethod/unload', params: (qs, r) => qs.set('urlPattern', r.key || '') } },
    { id: 'springFluxHandlerFunction', title: 'SpringFlux HandlerFunction', desc: 'urlPath -> className', list: '/SpringFluxHandlerFunction/list',
        unload: { url: '/SpringFluxHandlerFunction/unload', params: (qs, r) => qs.set('url', r.key || '') } },

    // ── Weblogic / Jetty ──
    { id: 'weblogicFilter', title: 'WebLogic Filter', desc: 'filterName -> className', list: '/WeblogicFilter/list',
        unload: { url: '/WeblogicFilter/unload', params: (qs, r) => qs.set('filterName', r.key || '') } },
    { id: 'weblogicServlet', title: 'WebLogic Servlet', desc: 'urlPath -> className', list: '/WeblogicServlet/list',
        unload: { url: '/WeblogicServlet/unload', params: (qs, r) => qs.set('urlPath', r.key || '') } },
    { id: 'weblogicListener', title: 'WebLogic Listener', desc: 'className', list: '/WeblogicListener/list',
        unload: { url: '/WeblogicListener/unload', params: (qs, r) => qs.set('className', r.className || '') } },
    { id: 'jettyListener',  title: 'Jetty Listener',  desc: 'className',                list: '/JettyListener/list',
        unload: { url: '/JettyListener/unload',  params: (qs, r) => qs.set('className', r.className || '') } },
    { id: 'jettyFilter',    title: 'Jetty Filter',    desc: 'filterName -> className',  list: '/JettyFilter/list',
        unload: { url: '/JettyFilter/unload',    params: (qs, r) => qs.set('filterName', r.key || '') } },
    { id: 'jettyServlet',   title: 'Jetty Servlet',   desc: 'urlPath -> className',     list: '/JettyServlet/list',
        unload: { url: '/JettyServlet/unload',   params: (qs, r) => qs.set('urlPath', r.key || '') } }
];
