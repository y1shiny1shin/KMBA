'use strict';

const $$ = (sel, root) => (root || document).querySelector(sel);
const $$$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));

function esc(s){
    return String(s == null ? '' : s)
        .replace(/&/g,'&amp;')
        .replace(/</g,'&lt;')
        .replace(/>/g,'&gt;')
        .replace(/"/g,'&quot;')
        .replace(/'/g,'&#39;');
}

// Strictly serial requests (no parallel).
let serialChain = Promise.resolve();
function serial(task){
    serialChain = serialChain.then(task, task);
    return serialChain;
}
async function apiText(path){
    return serial(async () => {
        const res = await fetch(path, { cache: 'no-store' });
        const txt = await res.text();
        // Small delay helps stabilize WS command sequences on some environments.
        await new Promise(r => setTimeout(r, 80));
        return txt;
    });
}
async function apiJson(path){
    const txt = await apiText(path);
    if (!txt) return [];
    try { return JSON.parse(txt); } catch { return []; }
}

const defaults = {
    keywords: [
        'javax.crypto.',
        'ProcessBuilder',
        'getRuntime',
        'shell',
        'rebeyond',
        'metasploit'
    ]
};

const state = {
    target: { type: 'none', label: 'not connected' }, // type: local|remote|none
    selected: 'overview',
    moduleRows: {},            // moduleId -> normalized rows
    moduleCounts: {},          // moduleId -> count
    suspicious: new Map(),     // signature -> { moduleId, row, hits, codeHint }
    keywords: defaults.keywords.slice(),
    choiceResolver: null,
    lastProcList: [],
    lastJadCache: new Map(),    // key: className|hash -> src
    jadContext: null            // { className, classLoaderHash } of current JAD view
};

const modules = [
    { id:'overview', title:'概览', desc:'全局统计、可疑命中、规则管理' },
    { id:'servlet', title:'Servlet', desc:'route -> className', list:'/servlet/list' },
    { id:'filter', title:'Filter', desc:'filterName -> URLPattern', list:'/filter/list' },
    { id:'listener', title:'Listener', desc:'className', list:'/listener/list' },
    { id:'socket', title:'WebSocket', desc:'urlName -> className', list:'/socket/list' },
    { id:'proxyValve', title:'ProxyValve', desc:'first/basic -> className', list:'/proxyValve/list' },
    { id:'valve', title:'Valve', desc:'className', list:'/valve/list' },
    { id:'executor', title:'Executor', desc:'className', list:'/executor/list' },
    { id:'thread', title:'Thread', desc:'threadName -> className', list:'/thread/list' },
    { id:'timer', title:'Timer', desc:'className', list:'/timer/list' },
    { id:'upgrade', title:'Upgrade', desc:'upgradeName -> className', list:'/upgrade/list' },
    { id:'smc', title:'SpringMVC Controller', desc:'urlPath -> className', list:'/SMC/list' },
    { id:'smi', title:'SpringMVC Interceptor', desc:'className', list:'/SMI/list' },
    { id:'sfwf', title:'SpringFlux WebFilter', desc:'filterName -> className', list:'/SFWF/list' }
];

function signature(moduleId, row){
    return [moduleId, row.key || '', row.className || '', row.aux || ''].join('@@');
}

function nowTime(){
    const d = new Date();
    const p = (n) => String(n).padStart(2,'0');
    return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
}

const dock = {
    hidden: false,
    logs: [],
    push(title, msg, level){
        const id = Math.random().toString(16).slice(2);
        const item = { id, title: title || '提示', msg: msg || '', level: level || 'busy', time: nowTime() };
        this.logs.unshift(item);
        this.render();
        return id;
    },
    update(id, patch){
        const x = this.logs.find(l => l.id === id);
        if (!x) return;
        Object.assign(x, patch || {});
        this.render();
    },
    clear(){
        this.logs = [];
        this.render();
    },
    render(){
        const dockEl = $$('#dock');
        const miniEl = $$('#dockMini');
        if (this.hidden){
            dockEl.style.display = 'none';
            miniEl.style.display = 'block';
            return;
        }
        dockEl.style.display = 'block';
        miniEl.style.display = 'none';
        const body = $$('#dockBody');
        body.innerHTML = this.logs.slice(0, 30).map(l => {
            return (
                '<div class="log">' +
                '<div class="h">' +
                '<span class="bub ' + esc(l.level) + '"></span>' +
                '<span class="t">' + esc(l.title) + '</span>' +
                '<span class="time">' + esc(l.time) + '</span>' +
                '</div>' +
                '<div class="msg">' + esc(l.msg) + '</div>' +
                '</div>'
            );
        }).join('');
    }
};

function openMask(id){ $$('#' + id).classList.add('show'); }
function closeMask(id){ $$('#' + id).classList.remove('show'); }

$$$('[data-close]').forEach(b => {
    b.addEventListener('click', () => closeMask(b.getAttribute('data-close')));
});
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape'){
        ['maskConnect','maskTerminal','maskJad','maskChoice'].forEach(closeMask);
    }
    if (e.key === 'Enter' && document.activeElement && document.activeElement.id === 'cmdIn'){
        ui.runCmd();
    }
    if (e.key === 'Enter' && document.activeElement && document.activeElement.id === 'quickJadClass'){
        ui.quickJad();
    }
});

async function choice(title, sub, hint, items){
    $$('#choiceTitle').textContent = title || '选择';
    $$('#choiceSub').textContent = sub || '';
    $$('#choiceHint').textContent = hint || '';
    const list = $$('#choiceList');
    list.innerHTML = '';
    for (const it of (items || [])){
        const btn = document.createElement('button');
        btn.innerHTML = '<div class="t">' + esc(it.t) + '</div>' + (it.d ? '<div class="d">' + esc(it.d) + '</div>' : '');
        btn.addEventListener('click', () => {
            closeMask('maskChoice');
            if (state.choiceResolver){
                const r = state.choiceResolver;
                state.choiceResolver = null;
                r(it.v);
            }
        });
        list.appendChild(btn);
    }
    $$('#choiceCancel').onclick = () => {
        closeMask('maskChoice');
        if (state.choiceResolver){
            const r = state.choiceResolver;
            state.choiceResolver = null;
            r(null);
        }
    };
    openMask('maskChoice');
    return new Promise(resolve => { state.choiceResolver = resolve; });
}

function setTarget(type, label){
    state.target = { type, label };
    $$('#target').textContent = 'target: ' + label;
}

function parseKeywords(s){
    const raw = String(s || '').trim();
    if (!raw) return defaults.keywords.slice();
    return raw.split(',').map(x => x.trim()).filter(Boolean);
}

function normalize(moduleId, data){
    const rows = [];
    if (!Array.isArray(data)) return rows;
    for (const item of data){
        if (typeof item === 'string'){
            if (moduleId === 'filter'){
                // New format: "urlPattern:filterName:className" (sometimes embedded in @String[...])
                const parsed = parseFilterTriples(item);
                for (const r of parsed) rows.push(r);
            } else {
                rows.push({ key:'', className:item, aux:'', raw:item });
            }
            continue;
        }
        if (item && typeof item === 'object' && !Array.isArray(item)){
            if (moduleId === 'filter'){
                // New JSON format:
                // [{ "filterName": "...", "className": "...", "urlPattern": "..." }, ...]
                if (Object.prototype.hasOwnProperty.call(item, 'urlPattern')
                    && Object.prototype.hasOwnProperty.call(item, 'filterName')
                    && Object.prototype.hasOwnProperty.call(item, 'className')) {
                    const urlPattern = String(item.urlPattern == null ? '' : item.urlPattern);
                    const filterName = String(item.filterName == null ? '' : item.filterName);
                    const className = String(item.className == null ? '' : item.className);
                    if (urlPattern) rows.push({ key: urlPattern, aux: filterName, className, raw: item });
                    continue;
                }

                // Backward-compatible: old backend returned { filterName: URLPattern }.
                const ks = Object.keys(item);
                if (!ks.length) continue;
                const k = ks[0];
                const v = item[k] == null ? '' : String(item[k]);
                rows.push({ key: v, className:'', aux: k, raw: item, legacy: true });
            } else {
                const ks = Object.keys(item);
                if (!ks.length) continue;
                const k = ks[0];
                const v = item[k] == null ? '' : String(item[k]);
                rows.push({ key: k, className: v, aux:'', raw: item });
            }
        }
    }
    return rows;
}

function parseFilterTriples(text){
    const s = String(text || '');
    const out = [];

    // If it looks like arthas output: @ArrayList[@String[/a:b:c],...]
    const tokenRe = /\@String\[(.*?)\]/g;
    let m;
    let anyToken = false;
    while ((m = tokenRe.exec(s)) !== null) {
        anyToken = true;
        const inner = String(m[1] || '');
        const parts = inner.split(':');
        if (parts.length >= 3) {
            const urlPattern = parts[0];
            const filterName = parts[1];
            const className = parts.slice(2).join(':'); // be tolerant if className contains ':'
            out.push({ key: urlPattern, aux: filterName, className, raw: inner });
        }
    }
    if (anyToken) return out;

    // Otherwise treat as plain "urlPattern:filterName:className"
    const parts = s.trim().split(':');
    if (parts.length >= 3) {
        out.push({ key: parts[0], aux: parts[1], className: parts.slice(2).join(':'), raw: s });
    }
    return out;
}

function moduleById(id){ return modules.find(m => m.id === id) || modules[0]; }

function navRender(){
    const nav = $$('#nav');
    nav.innerHTML = '';
    for (const m of modules){
        const btn = document.createElement('button');
        const active = (state.selected === m.id);
        const cnt = m.id === 'overview' ? '' : (state.moduleCounts[m.id] == null ? '' : String(state.moduleCounts[m.id]));
        let suspectCnt = 0;
        for (const k of state.suspicious.keys()){
            if (k.startsWith(m.id + '@@')) suspectCnt++;
        }
        btn.className = active ? 'active' : '';
        const dotClass = suspectCnt ? 'warn' : 'ok';
        btn.innerHTML =
            '<span class="dot ' + dotClass + '"></span>' +
            '<span>' + esc(m.title) + '</span>' +
            '<span class="meta">' +
            (cnt ? ('<span class="pill">' + esc(cnt) + '</span>') : '') +
            (suspectCnt ? ('<span class="pill hot">' + esc(suspectCnt) + '</span>') : '') +
            '</span>';
        btn.addEventListener('click', () => ui.select(m.id));
        nav.appendChild(btn);
    }
}

function panelRender(){
    const sel = state.selected;
    const m = moduleById(sel);

    const isOverview = sel === 'overview';
    $$('#panelOverview').style.display = isOverview ? '' : 'none';
    $$('#panelModule').style.display = isOverview ? 'none' : '';

    if (isOverview){
        ui.renderOverview();
        return;
    }

    $$('#mTitle').textContent = m.title;
    $$('#mDesc').textContent = m.desc || '';
    const tbody = $$('#tbody');
    const rows = state.moduleRows[sel] || [];

    tbody.innerHTML = rows.map((r, idx) => {
        const sig = signature(sel, r);
        const hit = state.suspicious.get(sig);
        let keyText = r.key || (idx + 1);
        let valText = r.className || '';
        if (sel === 'filter'){
            // Show: urlPattern (key) and filterName + className
            keyText = r.key || '';
            const filterName = r.aux || '';
            const className = r.className || '';
            valText = (filterName ? (filterName + ' · ') : '') + className;
        }

        const tags = [];
        if (sel === 'filter'){
            tags.push('<span class="tag">URLPattern</span>');
            if (r.aux) tags.push('<span class="tag">filterName</span>');
            if (r.className) tags.push('<span class="tag">class</span>');
        } else if (sel === 'proxyValve'){
            if (r.key) tags.push('<span class="tag">' + esc(r.key) + '</span>');
            if (r.className) tags.push('<span class="tag">class</span>');
        } else if (r.className){
            tags.push('<span class="tag">class</span>');
        }
        if (hit){
            tags.push('<span class="tag hot">suspect</span>');
            tags.push('<span class="tag hot">' + esc(hit.hits.join('|')) + '</span>');
        }

        const canJad = !!r.className;
        const canUnload = (sel !== 'overview');
        const jadBtn = canJad ? '<button class="a" data-act="jad">JAD</button>' : '';
        const unloadBtn = canUnload ? '<button class="a danger" data-act="unload">UNLOAD</button>' : '';

        return (
            '<tr class="row" data-sig="' + esc(sig) + '">' +
            '<td><div class="k">' + esc(keyText) + '</div></td>' +
            '<td>' +
            '<div class="v">' + esc(valText) + '</div>' +
            '<div class="tags" style="margin-top:6px">' + tags.join('') + '</div>' +
            '<div class="actions">' + jadBtn + unloadBtn + '</div>' +
            '</td>' +
            '</tr>'
        );
    }).join('');

    // Bind row actions
    $$$('#tbody .row').forEach(tr => {
        const sig = tr.getAttribute('data-sig');
        const parts = sig.split('@@');
        const moduleId = parts[0];
        const rows = state.moduleRows[moduleId] || [];
        const row = rows.find(x => signature(moduleId, x) === sig);
        if (!row) return;
        $$$('button[data-act]', tr).forEach(btn => {
            const act = btn.getAttribute('data-act');
            btn.addEventListener('click', async (e) => {
                e.stopPropagation();
                if (act === 'jad') await ui.showJad(moduleId, row);
                if (act === 'unload') await ui.unload(moduleId, row);
            });
        });
    });
}

function setLoadingUI(on, ctx){
    const topBtn = $$('#btnRefresh');
    const modBtn = $$('#btnMRefresh');
    const ovBtn = $$('#btnOvRefresh');
    const scanBtn = $$('#btnMScan');
    const scanAllBtn = $$('#btnOvScan');

    function flip(btn, busyText){
        if (!btn) return;
        if (!btn.dataset.idle) btn.dataset.idle = btn.textContent;
        btn.disabled = !!on;
        btn.textContent = on ? busyText : btn.dataset.idle;
    }

    const label = ctx ? (' ' + ctx) : '';
    flip(topBtn, '加载中' + label);

    // If overview is selected, show overview loading button; else show module button.
    if (state.selected === 'overview'){
        flip(ovBtn, '加载中' + label);
        if (scanAllBtn) scanAllBtn.disabled = !!on;
    } else {
        flip(modBtn, '加载中' + label);
        if (scanBtn) scanBtn.disabled = !!on;
    }
}

async function jadFetch(className){
    // cache by className (unique path will still be disambiguated later)
    const ck = 'jad|' + className;
    if (state.lastJadCache.has(ck)){
        state.jadContext = { className: className, classLoaderHash: null };
        return state.lastJadCache.get(ck);
    }

    const checkId = dock.push('加载源码', 'checking: ' + className, 'busy');
    const check = (await apiText('/jad/check?className=' + encodeURIComponent(className))).trim();

    if (check === 'dont'){
        dock.update(checkId, { level:'err', time: nowTime(), msg: 'class not found: ' + className });
        return null;
    }

    if (check === 'yes'){
        dock.update(checkId, { msg: 'jad: ' + className + ' ...' });
        const src = await apiText('/jad/jad?className=' + encodeURIComponent(className));
        state.lastJadCache.set(ck, src);
        state.jadContext = { className: className, classLoaderHash: null };
        dock.update(checkId, { level:'ok', time: nowTime(), msg: 'jad loaded: ' + className });
        return src;
    }

    if (check === 'no'){
        dock.update(checkId, { level:'warn', time: nowTime(), msg: 'duplicate class, need classLoaderHashCode: ' + className });
        const list = await apiJson('/jad/hashcode?className=' + encodeURIComponent(className));
        const opts = (Array.isArray(list) ? list : []).filter(x =>
            x && typeof x === 'object' && x['class-info'] && x.classLoaderHash
        );
        if (!opts.length){
            dock.update(checkId, { level:'err', time: nowTime(), msg: 'no classLoaderHash candidates returned for: ' + className });
            return null;
        }
        const picked = await choice(
            '选择 ClassLoader',
            className,
            '该类不唯一，请选择 classLoaderHash（用于 /jad/jadHash）',
            opts.map(o => ({
                v: { classInfo: String(o['class-info'] || ''), hash: String(o.classLoaderHash || '') },
                t: String(o.classLoaderHash || ''),
                d: String(o['class-info'] || '')
            }))
        );
        if (!picked){
            dock.update(checkId, { level:'warn', time: nowTime(), msg: 'cancelled: ' + className });
            return null;
        }
        const pickedClass = String(picked.classInfo || '').trim();
        const pickedHash = String(picked.hash || '').trim();
        if (!pickedClass || !pickedHash){
            dock.update(checkId, { level:'err', time: nowTime(), msg: 'invalid selection for: ' + className });
            return null;
        }
        const hk = 'jad|' + pickedClass + '|' + pickedHash;
        if (state.lastJadCache.has(hk)){
            dock.update(checkId, { level:'ok', time: nowTime(), msg: 'jad cached: ' + pickedClass + ' -c ' + pickedHash });
            state.jadContext = { className: pickedClass, classLoaderHash: pickedHash };
            return state.lastJadCache.get(hk);
        }
        dock.update(checkId, { msg: 'jadHash: ' + pickedClass + ' -c ' + pickedHash + ' ...' });
        const src = await apiText('/jad/jadHash?className=' + encodeURIComponent(pickedClass) + '&classLoaderHashCode=' + encodeURIComponent(pickedHash));
        state.lastJadCache.set(hk, src);
        state.jadContext = { className: pickedClass, classLoaderHash: pickedHash };
        dock.update(checkId, { level:'ok', time: nowTime(), msg: 'jad loaded: ' + pickedClass + ' -c ' + pickedHash });
        return src;
    }

    dock.update(checkId, { level:'err', time: nowTime(), msg: 'jad check failed: ' + check });
    return null;
}

function resetJadVmtoolUI(){
    $$('#jadCodeWrap').style.display = '';
    $$('#jadVmtoolWrap').style.display = 'none';
    const btn = $$('#btnJadVmtool');
    btn.textContent = '查看参数';
    btn.dataset.mode = '';
    btn.disabled = false;
    $$('#btnJadCopy').style.display = '';
}

function renderCode(src){
    const codeEl = $$('#jadCode');
    let text = String(src || '');
    try {
        if (window.formatJavaSource) text = window.formatJavaSource(text);
    } catch (_) {}
    codeEl.textContent = text;
    try { Prism.highlightElement(codeEl); } catch (_) {}
}

const ui = {
    async select(id){
        state.selected = id;
        navRender();
        panelRender();
        if (id !== 'overview' && !state.moduleRows[id]){
            await ui.refreshModule(id);
        }
        if (id === 'overview'){
            // Lazy load overview stats only if missing.
            if (!Object.keys(state.moduleCounts).length) await ui.refreshOverview();
        }
    },

    async refreshSelected(){
        if (state.selected === 'overview') return ui.refreshOverview();
        return ui.refreshModule(state.selected);
    },

    async refreshOverview(){
        setLoadingUI(true, '');
        const op = dock.push('加载概览', '正在加载所有模块统计...', 'busy');
        const counts = {};
        let i = 0;
        const totalM = modules.filter(x => x.id !== 'overview').length;
        for (const m of modules){
            if (m.id === 'overview') continue;
            i++;
            $$('#ovHint').textContent = '加载中... ' + i + '/' + totalM + ' · ' + m.title;
            const data = await apiJson(m.list);
            const rows = normalize(m.id, data);
            state.moduleRows[m.id] = rows;
            counts[m.id] = rows.length;
        }
        state.moduleCounts = Object.assign(state.moduleCounts, counts);

        dock.update(op, { level:'ok', time: nowTime(), msg: '概览加载完成' });
        ui.renderOverview();
        navRender();
        setLoadingUI(false, '');
    },

    renderOverview(){
        const total = Object.values(state.moduleCounts).reduce((a, b) => a + (Number(b) || 0), 0);
        const suspects = state.suspicious.size;
        $$('#ovHint').textContent = 'items=' + total + ' · suspects=' + suspects;

        const cards = $$('#ovCards');
        const target = state.target.label;
        cards.innerHTML =
            '<div class="card"><div class="l">Total Items</div><div class="n">' + esc(total) + '</div><div class="s">所有模块合计</div></div>' +
            '<div class="card"><div class="l">Suspects</div><div class="n">' + esc(suspects) + '</div><div class="s">命中关键字的组件</div></div>' +
            '<div class="card"><div class="l">Target</div><div class="n" style="font-size:14px; font-weight:950; line-height:1.35; margin-top:6px">' + esc(target) + '</div><div class="s">当前连接状态</div></div>';

        const box = $$('#ovSuspects');
        const items = Array.from(state.suspicious.values()).slice(0, 80);
        if (!items.length){
            box.innerHTML = '<div class="li"><div class="t">暂无命中</div><div class="d">点击"提取可疑"或"提取全局可疑"开始扫描。</div></div>';
            return;
        }
        box.innerHTML = items.map(it => {
            const label = it.moduleId + ' · ' + (it.row.className || it.row.aux || it.row.key || '');
            const detail = (it.row.key ? ('key=' + it.row.key + ' ') : '') + (it.hits.length ? ('hits=' + it.hits.join(',')) : '');
            return (
                '<div class="li">' +
                '<div class="t">' + esc(label) + '</div>' +
                '<div class="d">' + esc(detail) + '</div>' +
                '</div>'
            );
        }).join('');
    },

    async refreshModule(moduleId){
        const m = moduleById(moduleId);
        setLoadingUI(true, m.title);
        if (state.selected === moduleId){
            $$('#mDesc').textContent = (m.desc || '') + ' · 加载中...';
            $$('#tbody').innerHTML =
                '<tr><td><div class="k"><span class="spin"></span> loading</div></td><td><div class="v">正在加载 ' + esc(m.title) + ' ...</div></td></tr>';
        }
        const op = dock.push('刷新模块', '正在加载: ' + m.title, 'busy');
        const data = await apiJson(m.list);
        let rows = normalize(moduleId, data);
        // Fallback for rare non-JSON filter output (older versions / ognl failure noise)
        if (moduleId === 'filter' && (!rows || !rows.length)) {
            const txt = await apiText(m.list);
            rows = parseFilterTriples(txt);
        }
        state.moduleRows[moduleId] = rows;
        state.moduleCounts[moduleId] = rows.length;
        dock.update(op, { level:'ok', time: nowTime(), msg: '加载完成: ' + m.title + ' (' + rows.length + ')' });
        navRender();
        if (state.selected === moduleId) panelRender();
        setLoadingUI(false, '');
    },

    async showJad(moduleId, row){
        const className = row.className;
        if (!className){
            dock.push('JAD', '该条目没有 className，无法反编译', 'warn');
            return;
        }
        await ui.showJadByClassName(className);
    },

    async showJadByClassName(className){
        const cn = String(className || '').trim();
        if (!cn){
            dock.push('JAD', '类名为空', 'warn');
            return;
        }
        $$('#jadTitle').textContent = '源码';
        $$('#jadSub').textContent = cn;
        $$('#jadHint').textContent = '加载中...';
        $$('#jadCode').textContent = '';
        state.jadContext = null;
        resetJadVmtoolUI();
        openMask('maskJad');

        const src = await jadFetch(cn);
        if (!src || String(src).trim() === 'false'){
            $$('#jadHint').textContent = '加载失败: ' + cn;
            return;
        }
        $$('#jadHint').textContent = '已加载: ' + cn;
        renderCode(src);
    },

    async quickJad(){
        const cn = String($$('#quickJadClass').value || '').trim();
        if (!cn){
            dock.push('JAD', '请输入需要反编译的类名（全限定名）', 'warn');
            return;
        }
        await ui.showJadByClassName(cn);
    },

    async unload(moduleId, row){
        const m = moduleById(moduleId);
        const label = (row.className || row.aux || row.key || '');
        if (!label){
            dock.push('卸载', '参数为空，无法卸载: ' + m.title, 'err');
            return;
        }

        // Special modes with risk explanation.
        if (moduleId === 'executor'){
            const picked = await choice(
                'Executor 卸载模式',
                label,
                '两种方式各有利弊（建议先温柔）。请选择:',
                [
                    { v:'gently', t:'温柔卸载 (unloadGently)', d:'对业务影响更小，但攻击者仍可能继续添加恶意 Executor。' },
                    { v:'brutly', t:'暴力卸载 (unloadBrutly)', d:'更彻底，但可能导致后续 Executor 加载失败或类型不兼容，业务风险更高。' }
                ]
            );
            if (!picked){
                dock.push('Executor 卸载', '已取消: ' + label, 'warn');
                return;
            }
            const op = dock.push('Executor 卸载', '执行中: ' + picked + ' · ' + label, 'busy');
            const url = picked === 'brutly' ? '/executor/unloadBrutly' : '/executor/unloadGently';
            const res = (await apiText(url + '?className=' + encodeURIComponent(row.className))).trim();
            if (res === 'success') dock.update(op, { level:'ok', time: nowTime(), msg:'卸载成功: ' + label });
            else dock.update(op, { level:'err', time: nowTime(), msg:'卸载失败: ' + label + '\n' + res });
            await ui.refreshModule(moduleId);
            return;
        }

        if (moduleId === 'timer'){
            const picked = await choice(
                'Timer 卸载模式',
                label,
                '请选择卸载方式:',
                [
                    { v:'gently', t:'温和取消 (unload)', d:'调用 cancel，更安全。' },
                    { v:'force', t:'强制停止 (unloadForce)', d:'可能有资源泄露风险，且 stop 已弃用，执行可能失败。' }
                ]
            );
            if (!picked){
                dock.push('Timer 卸载', '已取消: ' + label, 'warn');
                return;
            }
            const op = dock.push('Timer 卸载', '执行中: ' + picked + ' · ' + label, 'busy');
            const url = picked === 'force' ? '/timer/unloadForce' : '/timer/unload';
            const res = (await apiText(url + '?className=' + encodeURIComponent(row.className))).trim();
            if (res === 'success') dock.update(op, { level:'ok', time: nowTime(), msg:'卸载成功: ' + label });
            else dock.update(op, { level:'err', time: nowTime(), msg:'卸载失败: ' + label + '\n' + res });
            await ui.refreshModule(moduleId);
            return;
        }

        // Confirmation via choice modal (no native confirm).
        const ok = await choice(
            '确认卸载',
            m.title,
            '确认卸载该组件?\n' + label,
            [
                { v:'yes', t:'确认卸载', d:'将调用后端 unload 接口' },
                { v:'no', t:'取消', d:'不执行任何操作' }
            ]
        );
        if (ok !== 'yes'){
            dock.push('卸载', '已取消: ' + label, 'warn');
            return;
        }

        const op = dock.push('卸载', '执行中: ' + m.title + ' · ' + label, 'busy');
        const { url, qs } = ui.buildUnload(moduleId, row);
        if (!url){
            dock.update(op, { level:'err', time: nowTime(), msg:'未配置卸载接口: ' + moduleId });
            return;
        }
        const res = (await apiText(url + '?' + qs.toString())).trim();
        if (res === 'success') dock.update(op, { level:'ok', time: nowTime(), msg:'卸载成功: ' + label });
        else dock.update(op, { level:'err', time: nowTime(), msg:'卸载失败: ' + label + '\n' + res });
        await ui.refreshModule(moduleId);
    },

    buildUnload(moduleId, row){
        const qs = new URLSearchParams();
        if (moduleId === 'servlet'){ qs.set('urlPath', row.key || ''); return { url:'/servlet/unload', qs }; }
        if (moduleId === 'filter'){
            // New format uses urlPattern as key.
            qs.set('URLPattern', row.key || '');
            return { url:'/filter/unload', qs };
        }
        if (moduleId === 'listener'){ qs.set('className', row.className || ''); return { url:'/listener/unload', qs }; }
        if (moduleId === 'socket'){ qs.set('urlName', row.key || ''); qs.set('className', row.className || ''); return { url:'/socket/unload', qs }; }
        if (moduleId === 'proxyValve'){
            qs.set('className', row.className || '');
            const k = String(row.key || '').toLowerCase();
            const url = (k === 'basic') ? '/proxyValve/unloadBasic' : '/proxyValve/unloadFirst';
            return { url, qs };
        }
        if (moduleId === 'valve'){ qs.set('className', row.className || ''); return { url:'/valve/unload', qs }; }
        if (moduleId === 'thread'){ qs.set('threadName', row.key || ''); qs.set('className', row.className || ''); return { url:'/thread/unload', qs }; }
        if (moduleId === 'upgrade'){ qs.set('upgradeName', row.key || ''); return { url:'/upgrade/unload', qs }; }
        if (moduleId === 'smc'){ qs.set('urlPath', row.key || ''); return { url:'/SMC/unload', qs }; }
        if (moduleId === 'smi'){ qs.set('className', row.className || ''); return { url:'/SMI/unload', qs }; }
        if (moduleId === 'sfwf'){ qs.set('className', row.className || ''); return { url:'/SFWF/unload', qs }; }
        return { url:'', qs };
    },

    async scanModule(moduleId){
        const m = moduleById(moduleId);
        const rows = state.moduleRows[moduleId] || [];
        const op = dock.push('提取可疑', '扫描中: ' + m.title + ' (items=' + rows.length + ')', 'busy');

        let hitCnt = 0;
        for (const r of rows){
            if (!r.className) continue;
            const src = await jadFetch(r.className);
            if (!src || String(src).trim() === 'false') continue;
            const low = String(src).toLowerCase();
            const hits = [];
            for (const kw of state.keywords){
                const k = String(kw || '').trim();
                if (!k) continue;
                if (low.includes(k.toLowerCase())) hits.push(k);
            }
            if (hits.length){
                const sig = signature(moduleId, r);
                state.suspicious.set(sig, { moduleId, row: r, hits });
                hitCnt++;
            }
        }

        dock.update(op, { level: hitCnt ? 'warn' : 'ok', time: nowTime(), msg: '扫描完成: ' + m.title + ' · hits=' + hitCnt });
        navRender();
        if (state.selected === moduleId) panelRender();
        if (state.selected === 'overview') ui.renderOverview();
    },

    async scanAll(){
        const op = dock.push('提取全局可疑', '开始扫描所有模块...', 'busy');
        for (const m of modules){
            if (m.id === 'overview') continue;
            if (!state.moduleRows[m.id]) await ui.refreshModule(m.id);
            await ui.scanModule(m.id);
        }
        dock.update(op, { level:'ok', time: nowTime(), msg:'全局扫描完成 · suspects=' + state.suspicious.size });
        if (state.selected === 'overview') ui.renderOverview();
    },

    async openConnect(){
        openMask('maskConnect');
        await ui.refreshProcList();
    },

    async refreshProcList(){
        const op = dock.push('加载本地 JVM', '正在获取 jps 列表...', 'busy');
        const list = await apiJson('/arthas/processes');
        state.lastProcList = Array.isArray(list) ? list : [];
        dock.update(op, { level:'ok', time: nowTime(), msg:'本地 JVM 已加载: ' + state.lastProcList.length });
        ui.renderProcList();
    },

    renderProcList(){
        const kw = String($$('#procFilter').value || '').trim().toLowerCase();
        const box = $$('#procList');
        box.innerHTML = '';
        const list = state.lastProcList.filter(p => {
            const s = (String(p.pid || '') + ' ' + String(p.name || '')).toLowerCase();
            return !kw || s.includes(kw);
        });
        if (!list.length){
            box.innerHTML = '<div class="hint">无可选 JVM（请确认目标 Java 进程存在且可被 jps 识别）。</div>';
            return;
        }
        for (const p of list){
            const pid = String(p.pid || '');
            const name = String(p.name || '');
            const btn = document.createElement('button');
            btn.innerHTML = '<div class="t">' + esc(pid) + '</div><div class="d">' + esc(name) + '</div>';
            btn.addEventListener('click', async () => {
                const op = dock.push('连接本地 JVM', 'attach pid=' + pid, 'busy');
                const res = (await apiText('/arthas/connect?pid=' + encodeURIComponent(pid))).trim();
                if (res === 'success'){
                    setTarget('local', 'local pid=' + pid + ' · ' + name);
                    dock.update(op, { level:'ok', time: nowTime(), msg:'连接成功: pid=' + pid });
                    closeMask('maskConnect');
                } else {
                    dock.update(op, { level:'err', time: nowTime(), msg:'连接失败: pid=' + pid + '\n' + res });
                }
            });
            box.appendChild(btn);
        }
    },

    async connectDirectPid(){
        const raw = String($$('#directPid').value || '').trim();
        if (!/^[0-9]+$/.test(raw)){
            dock.push('直接连接 PID', 'PID 不合法: ' + raw, 'err');
            return;
        }
        const pid = raw;
        const op = dock.push('直接连接 PID', 'attach pid=' + pid + ' ...', 'busy');

        const res = (await apiText('/arthas/connect?pid=' + encodeURIComponent(pid))).trim();
        if (res === 'success'){
            setTarget('local', 'local pid=' + pid);
            dock.update(op, { level:'ok', time: nowTime(), msg:'连接成功: pid=' + pid });
            closeMask('maskConnect');
        } else {
            dock.update(op, { level:'err', time: nowTime(), msg:'连接失败: pid=' + pid + '\n' + res });
        }
    },

    async connectRemote(){
        const ip = String($$('#remoteIp').value || '').trim() || '127.0.0.1';
        const port = parseInt(String($$('#remotePort').value || '').trim() || '8563', 10);
        if (!Number.isFinite(port) || port <= 0){
            dock.push('远程连接', '端口不合法: ' + $$('#remotePort').value, 'err');
            return;
        }
        const op = dock.push('远程连接', 'connecting: ' + ip + ':' + port, 'busy');
        const res = (await apiText('/arthas/connectRemote?ip=' + encodeURIComponent(ip) + '&port=' + encodeURIComponent(port))).trim();
        if (res === 'success'){
            setTarget('remote', 'remote ' + ip + ':' + port);
            dock.update(op, { level:'ok', time: nowTime(), msg:'远程连接成功: ' + ip + ':' + port });
            closeMask('maskConnect');
        } else {
            dock.update(op, { level:'err', time: nowTime(), msg:'远程连接失败: ' + ip + ':' + port + '\n' + res });
        }
    },

    async checkPort(){
        const ip = String($$('#remoteIp').value || '').trim() || '127.0.0.1';
        const port = parseInt(String($$('#remotePort').value || '').trim() || '8563', 10);
        const op = dock.push('端口检查', 'checking: ' + ip + ':' + port, 'busy');
        const res = (await apiText('/arthas/checkPort?ip=' + encodeURIComponent(ip) + '&port=' + encodeURIComponent(port))).trim();
        dock.update(op, { level: res === 'open' ? 'ok' : 'warn', time: nowTime(), msg: ip + ':' + port + ' -> ' + res });
    },

    async stopArthas(){
        if (state.target.type === 'remote'){
            const ok = await choice(
                '确认远程关闭',
                state.target.label,
                '你正在远程模式，执行 stop 将尝试关闭对端 Arthas。\n确认继续?',
                [
                    { v:'yes', t:'确认 stop', d:'发送 /arthas/stop' },
                    { v:'no', t:'取消', d:'不执行' }
                ]
            );
            if (ok !== 'yes'){
                dock.push('停止 Arthas', '已取消', 'warn');
                return;
            }
        }
        const op = dock.push('停止 Arthas', 'sending stop...', 'busy');
        const res = (await apiText('/arthas/stop')).trim();
        if (res === 'success'){
            setTarget('none', 'not connected');
            dock.update(op, { level:'ok', time: nowTime(), msg:'stop success' });
        } else {
            dock.update(op, { level:'err', time: nowTime(), msg:'stop failed\n' + res });
        }
    },

    openTerminal(){
        openMask('maskTerminal');
        $$('#cmdIn').focus();
    },

    async runCmd(){
        const cmd = String($$('#cmdIn').value || '').trim();
        if (!cmd){
            dock.push('命令执行', 'cmd 为空', 'warn');
            return;
        }
        const op = dock.push('命令执行', 'running: ' + cmd, 'busy');
        const out = await apiText('/arthas/exec?cmd=' + encodeURIComponent(cmd));
        // Remove very short "ok N" noise lines if any appear; keep everything else.
        const lines = String(out || '').split('\n');
        const filtered = lines.filter(l => !/^ok\s+\d+\s*$/i.test(String(l).trim()));
        $$('#cmdOut').textContent = filtered.join('\n').trim();
        dock.update(op, { level:'ok', time: nowTime(), msg:'done: ' + cmd });
    }
};

// Wire buttons
$$('#btnConnect').addEventListener('click', () => ui.openConnect());
$$('#btnTerminal').addEventListener('click', () => ui.openTerminal());
$$('#btnQuickJad').addEventListener('click', () => ui.quickJad());
$$('#btnRefresh').addEventListener('click', () => ui.refreshSelected());
$$('#btnStop').addEventListener('click', () => ui.stopArthas());

$$('#btnMRefresh').addEventListener('click', () => ui.refreshSelected());
$$('#btnMScan').addEventListener('click', () => ui.scanModule(state.selected));

$$('#btnOvRefresh').addEventListener('click', () => ui.refreshOverview());
$$('#btnOvScan').addEventListener('click', () => ui.scanAll());
$$('#btnKwSave').addEventListener('click', () => {
    state.keywords = parseKeywords($$('#kwIn').value);
    dock.push('规则', '已应用关键字: ' + state.keywords.join(', '), 'ok');
});

$$('#btnProcRefresh').addEventListener('click', () => ui.refreshProcList());
$$('#btnDirectPid').addEventListener('click', () => ui.connectDirectPid());
$$('#procFilter').addEventListener('input', () => ui.renderProcList());
$$('#tabLocal').addEventListener('click', () => {
    $$('#tabLocal').classList.add('active');
    $$('#tabRemote').classList.remove('active');
    $$('#paneLocal').style.display = '';
    $$('#paneRemote').style.display = 'none';
});
$$('#tabRemote').addEventListener('click', () => {
    $$('#tabRemote').classList.add('active');
    $$('#tabLocal').classList.remove('active');
    $$('#paneRemote').style.display = '';
    $$('#paneLocal').style.display = 'none';
});
$$('#btnCheckPort').addEventListener('click', () => ui.checkPort());
$$('#btnConnectRemote').addEventListener('click', () => ui.connectRemote());

$$('#btnCmdRun').addEventListener('click', () => ui.runCmd());
$$('#btnCmdClear').addEventListener('click', () => { $$('#cmdOut').textContent = ''; });

$$('#btnJadCopy').addEventListener('click', async () => {
    const txt = $$('#jadCode').textContent || '';
    try {
        await navigator.clipboard.writeText(txt);
        dock.push('复制源码', '已复制到剪贴板', 'ok');
    } catch {
        dock.push('复制源码', '复制失败（浏览器可能限制 clipboard）', 'warn');
    }
});

$$('#btnJadVmtool').addEventListener('click', async () => {
    const ctx = state.jadContext;
    if (!ctx || !ctx.className){
        dock.push('查看参数', '缺少类名上下文，无法查询', 'warn');
        return;
    }

    const vmtoolWrap = $$('#jadVmtoolWrap');
    const codeWrap = $$('#jadCodeWrap');
    const btn = $$('#btnJadVmtool');
    const copyBtn = $$('#btnJadCopy');

    // 切换回源码
    if (btn.dataset.mode === 'vmtool'){
        vmtoolWrap.style.display = 'none';
        codeWrap.style.display = '';
        btn.textContent = '查看参数';
        btn.dataset.mode = '';
        copyBtn.style.display = '';
        $$('#jadTitle').textContent = '源码';
        $$('#jadHint').textContent = '已加载: ' + ctx.className;
        return;
    }

    // 切换到 vmtool
    btn.textContent = '…';
    btn.disabled = true;

    const params = new URLSearchParams();
    params.set('className', ctx.className);
    if (ctx.classLoaderHash) params.set('classLoaderHash', ctx.classLoaderHash);

    const op = dock.push('查看参数', 'vmtool: ' + ctx.className, 'busy');
    const json = await apiJson('/vmtool/get?' + params.toString());

    btn.disabled = false;

    let text = '';
    if (!json || !json.type){
        dock.update(op, { level:'err', time: nowTime(), msg: 'vmtool 返回异常' });
        btn.textContent = '查看参数';
        return;
    }

    if (json.type === 'error'){
        dock.update(op, { level:'err', time: nowTime(), msg: json.message || 'vmtool error' });
        btn.textContent = '查看参数';
        return;
    }

    if (json.type === '404' || json.type === 'not_found'){
        dock.update(op, { level:'warn', time: nowTime(), msg: 'class not found: ' + ctx.className });
        btn.textContent = '查看参数';
        return;
    }

    if (json.type === 'single'){
        text = (json.data || '') + '\n\n// classLoaderHash: ' + (json.classLoaderHash || '');
        dock.update(op, { level:'ok', time: nowTime(), msg: 'vmtool loaded: ' + ctx.className });
    } else if (json.type === 'multi'){
        const results = json.results || [];
        const parts = [];
        for (let i = 0; i < results.length; i++){
            const r = results[i];
            parts.push(
                '// ── ' + (r.classLoaderHash || '') + '  ' + (r.classInfo || '') + ' ──\n' +
                (r.data || '')
            );
        }
        text = parts.join('\n\n');
        dock.update(op, { level:'ok', time: nowTime(), msg: 'vmtool loaded: ' + results.length + ' instances' });
    }

    $$('#jadVmtoolOut').textContent = text;
    codeWrap.style.display = 'none';
    vmtoolWrap.style.display = '';
    btn.textContent = '查看源码';
    btn.dataset.mode = 'vmtool';
    copyBtn.style.display = 'none';
    $$('#jadTitle').textContent = '参数';
    $$('#jadHint').textContent = 'vmtool: ' + ctx.className + (ctx.classLoaderHash ? ' -c ' + ctx.classLoaderHash : '');
});

$$('#btnDockClear').addEventListener('click', () => dock.clear());
$$('#btnDockHide').addEventListener('click', () => { dock.hidden = true; dock.render(); });
$$('#btnDockShow').addEventListener('click', () => { dock.hidden = false; dock.render(); });

// Init
(function init(){
    $$('#kwIn').value = defaults.keywords.join(', ');
    state.keywords = defaults.keywords.slice();
    navRender();
    panelRender();
    dock.render();
    ui.openConnect();
})();
