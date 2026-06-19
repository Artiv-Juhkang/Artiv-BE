/* AppToon Studio Console — 작가/관리자 콘솔 SPA (Vanilla JS) */
(() => {
  'use strict';

  const AGE = { ALL: '전체', AGE_12: '12세', AGE_15: '15세', AGE_19: '19세' };
  const STATUS = { ONGOING: '연재중', COMPLETED: '완결', HIATUS: '휴재' };
  const DAYS = [['MONDAY','월'],['TUESDAY','화'],['WEDNESDAY','수'],['THURSDAY','목'],['FRIDAY','금'],['SATURDAY','토'],['SUNDAY','일']];

  const state = {
    token: localStorage.getItem('apptoon_token') || null,
    refresh: localStorage.getItem('apptoon_refresh') || null,
    user: null,
    view: null,
  };

  const app = document.getElementById('app');
  const $ = (s, r = document) => r.querySelector(s);
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;' }[c]));

  function setTokens(access, refresh) {
    state.token = access; state.refresh = refresh;
    localStorage.setItem('apptoon_token', access);
    localStorage.setItem('apptoon_refresh', refresh);
  }
  function clearTokens() {
    state.token = state.refresh = state.user = null;
    localStorage.removeItem('apptoon_token');
    localStorage.removeItem('apptoon_refresh');
  }

  // ---- toast ----
  let toastTimer;
  function toast(msg, kind = '') {
    const t = document.getElementById('toast');
    t.innerHTML = `<div class="toast ${kind ? 'toast--' + kind : ''}">${esc(msg)}</div>`;
    t.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.classList.remove('show'), 2600);
  }

  // ---- api ----
  async function tryRefresh() {
    if (!state.refresh) return false;
    try {
      const r = await fetch('/api/auth/refresh', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: state.refresh }),
      });
      if (!r.ok) return false;
      const d = await r.json();
      setTokens(d.accessToken, d.refreshToken);
      return true;
    } catch { return false; }
  }

  async function api(method, path, opts = {}) {
    const headers = {};
    if (state.token) headers['Authorization'] = 'Bearer ' + state.token;
    let body;
    if (opts.json !== undefined) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(opts.json); }
    else if (opts.form) { body = opts.form; }
    const res = await fetch(path, { method, headers, body });
    if (res.status === 401 && !opts._retry && state.refresh) {
      if (await tryRefresh()) return api(method, path, { ...opts, _retry: true });
    }
    const text = await res.text();
    let data = null;
    if (text) { try { data = JSON.parse(text); } catch { data = text; } }
    if (!res.ok) throw { status: res.status, data };
    return data;
  }
  const errMsg = (e) => (e && e.data && e.data.message) || (e && e.data && e.data.code) || '요청을 처리하지 못했어요.';

  // ====================================================================
  // 로그인
  // ====================================================================
  function renderLogin() {
    document.title = 'AppToon Studio — 로그인';
    app.innerHTML = `
      <div class="login">
        <form class="login__card panel" id="loginForm" novalidate>
          <div class="login__brand">
            <span class="login__logo">App<b>Toon</b></span>
            <span class="stamp">STUDIO</span>
          </div>
          <p class="login__sub">작가·관리자 콘솔에 로그인하세요</p>
          <div class="field">
            <label for="email">이메일</label>
            <input id="email" type="email" autocomplete="username" placeholder="you@example.com" required />
          </div>
          <div class="field">
            <label for="password">비밀번호</label>
            <input id="password" type="password" autocomplete="current-password" placeholder="••••••••" required />
          </div>
          <button class="btn btn--accent" type="submit" style="width:100%" id="loginBtn">로그인</button>
          <div class="login__err" id="loginErr"></div>
        </form>
      </div>`;
    $('#loginForm').addEventListener('submit', onLogin);
    $('#email').focus();
  }

  async function onLogin(e) {
    e.preventDefault();
    const btn = $('#loginBtn'), errEl = $('#loginErr');
    errEl.textContent = '';
    const email = $('#email').value.trim(), password = $('#password').value;
    if (!email || !password) { errEl.textContent = '이메일과 비밀번호를 입력하세요.'; return; }
    btn.disabled = true; btn.innerHTML = '<span class="spinner"></span>';
    try {
      const tok = await api('POST', '/api/auth/login', { json: { email, password } });
      setTokens(tok.accessToken, tok.refreshToken);
      const me = await api('GET', '/api/users/me');
      state.user = me;
      if (me.role === 'READER') {
        clearTokens();
        errEl.textContent = '이 콘솔은 작가·관리자만 사용할 수 있어요.';
        return;
      }
      boot();
    } catch (err) {
      errEl.textContent = err.status === 401 ? '이메일 또는 비밀번호가 올바르지 않아요.' : errMsg(err);
    } finally {
      btn.disabled = false; btn.textContent = '로그인';
    }
  }

  // ====================================================================
  // 셸 (사이드바 + 메인)
  // ====================================================================
  function navItems() {
    if (state.user.role === 'ADMIN') {
      return [['admin-series', '작품 관리'], ['admin-users', '사용자 권한']];
    }
    return [['dashboard', '내 작품'], ['create', '작품 등록']];
  }

  function renderShell(inner) {
    const items = navItems();
    if (!state.view || !items.some((i) => i[0] === state.view)) state.view = items[0][0];
    const roleStamp = state.user.role === 'ADMIN'
      ? '<span class="stamp stamp--admin">ADMIN</span>'
      : '<span class="stamp">CREATOR</span>';
    app.innerHTML = `
      <div class="shell">
        <aside class="side">
          <div class="side__logo">App<b>Toon</b></div>
          <div class="side__who"><strong>${esc(state.user.nickname)}</strong><br>${esc(state.user.email)}<br>${roleStamp}</div>
          <nav class="nav">
            ${items.map(([k, label]) => `<button data-view="${k}" ${k === state.view ? 'aria-current="true"' : ''}>${label}</button>`).join('')}
          </nav>
          <div class="side__foot"><button class="btn btn--ghost btn--sm" id="logout" style="color:#cdcad6;border-color:#3a3744">로그아웃</button></div>
        </aside>
        <main class="main" id="main">${inner || ''}</main>
      </div>`;
    $('.nav').addEventListener('click', (e) => {
      const b = e.target.closest('button[data-view]');
      if (b) { state.view = b.dataset.view; route(); }
    });
    $('#logout').addEventListener('click', () => { clearTokens(); renderLogin(); });
  }

  function setMain(html) { const m = $('#main'); if (m) m.innerHTML = html; }
  const loading = '<div class="loading"><span class="spinner"></span></div>';

  // ====================================================================
  // 라우팅
  // ====================================================================
  function route() {
    renderShell(loading);
    const v = state.view;
    if (v === 'dashboard') return viewMySeries();
    if (v === 'create') return viewCreate();
    if (v === 'episodes') return viewEpisodes();
    if (v === 'admin-series') return viewAdminSeries();
    if (v === 'admin-users') return viewAdminUsers();
  }

  // ====================================================================
  // 작가 — 내 작품
  // ====================================================================
  let pickedSeries = null; // 회차 화면용

  async function viewMySeries() {
    try {
      const list = await api('GET', '/api/series/mine');
      const total = list.length;
      const visible = list.filter((s) => s.visible).length;
      const cards = list.length ? list.map(seriesCard).join('') : emptyBox('아직 등록한 작품이 없어요', '“작품 등록”에서 첫 작품을 만들어 보세요.');
      setMain(`
        <div class="page-head"><div><span class="eyebrow">Creator</span><h1>내 작품</h1><p>등록한 작품과 회차를 관리하세요</p></div>
          <button class="btn btn--accent" data-go="create">+ 작품 등록</button></div>
        <div class="stats-row">
          ${stat(total, '작품 수')}${stat(visible, '공개중')}${stat(total - visible, '비공개')}
        </div>
        <div class="grid">${cards}</div>`);
      bindGo();
      $('#main').addEventListener('click', onMySeriesClick);
    } catch (e) { setMain(errBox(e)); }
  }

  function seriesCard(s) {
    return `<article class="panel card" data-id="${s.id}" data-title="${esc(s.title)}">
      <div class="card__top"><h3>${esc(s.title)}</h3>
        <div class="card__meta">${s.visible ? '<span class="tag tag--on">공개</span>' : '<span class="tag tag--off">비공개</span>'}</div></div>
      <div class="card__meta">
        <span class="tag ${s.ageRating === 'AGE_19' ? 'tag--19' : ''}">${AGE[s.ageRating] || s.ageRating}</span>
        <span class="tag">${STATUS[s.status] || s.status}</span>
        ${s.adultOnly ? '<span class="tag tag--adult">성인전용</span>' : ''}
      </div>
      <div class="card__actions">
        <button class="btn btn--accent btn--sm" data-act="upload">+ 회차 업로드</button>
        <button class="btn btn--sm" data-act="episodes">회차 보기</button>
      </div>
    </article>`;
  }

  function onMySeriesClick(e) {
    const card = e.target.closest('.card'); if (!card) return;
    const id = Number(card.dataset.id), title = card.dataset.title;
    const act = e.target.closest('button[data-act]')?.dataset.act;
    if (act === 'upload') openUpload(id, title);
    else if (act === 'episodes') { pickedSeries = { id, title }; state.view = 'episodes'; route(); }
  }

  // ====================================================================
  // 작가 — 작품 등록
  // ====================================================================
  function viewCreate() {
    setMain(`
      <div class="page-head"><div><span class="eyebrow">Creator</span><h1>작품 등록</h1><p>새 웹툰의 기본 정보를 입력하세요</p></div></div>
      <form class="panel form-panel" id="createForm" style="max-width:620px">
        <div class="form-grid">
          <div class="field full"><label for="c-title">제목</label><input id="c-title" required maxlength="100" placeholder="작품 제목" /></div>
          <div class="field full"><label for="c-desc">설명</label><textarea id="c-desc" placeholder="작품 소개"></textarea></div>
          <div class="field"><label for="c-age">연령등급</label><select id="c-age">
            ${Object.entries(AGE).map(([k, v]) => `<option value="${k}">${v}</option>`).join('')}</select></div>
          <div class="field"><label for="c-status">연재 상태</label><select id="c-status">
            ${Object.entries(STATUS).map(([k, v]) => `<option value="${k}">${v}</option>`).join('')}</select></div>
          <div class="field full"><label>연재 요일</label>
            <div class="checks" id="c-days">${DAYS.map(([k, v]) => `<label class="check"><input type="checkbox" value="${k}"><span>${v}</span></label>`).join('')}</div>
            <span class="hint">하나 이상 선택</span></div>
          <div class="field full"><label class="check" id="c-adult-wrap"><input type="checkbox" id="c-adult"><span>성인 전용 (19세 등급에서만 가능)</span></label></div>
        </div>
        <button class="btn btn--accent" type="submit" id="c-submit">작품 등록</button>
      </form>`);
    const days = $('#c-days');
    days.addEventListener('change', (e) => { e.target.closest('.check')?.classList.toggle('check--on', e.target.checked); });
    $('#c-adult-wrap').addEventListener('change', (e) => $('#c-adult-wrap').classList.toggle('check--on', e.target.checked));
    $('#createForm').addEventListener('submit', onCreate);
  }

  async function onCreate(e) {
    e.preventDefault();
    const title = $('#c-title').value.trim();
    const publishDays = [...document.querySelectorAll('#c-days input:checked')].map((i) => i.value);
    const ageRating = $('#c-age').value, adultOnly = $('#c-adult').checked;
    if (!title) return toast('제목을 입력하세요', 'err');
    if (!publishDays.length) return toast('연재 요일을 하나 이상 선택하세요', 'err');
    if (adultOnly && ageRating !== 'AGE_19') return toast('성인 전용은 19세 등급에서만 가능해요', 'err');
    const btn = $('#c-submit'); btn.disabled = true;
    try {
      await api('POST', '/api/series', { json: {
        title, description: $('#c-desc').value.trim(), ageRating,
        status: $('#c-status').value, publishDays, adultOnly,
      } });
      toast('작품을 등록했어요', 'ok');
      state.view = 'dashboard'; route();
    } catch (err) { toast(errMsg(err), 'err'); btn.disabled = false; }
  }

  // ====================================================================
  // 작가 — 회차 보기
  // ====================================================================
  async function viewEpisodes() {
    if (!pickedSeries) { state.view = 'dashboard'; return route(); }
    const { id, title } = pickedSeries;
    setMain(`<div class="page-head"><div><span class="eyebrow">Creator</span><h1>${esc(title)}</h1><p>발행된 회차</p></div>
      <div style="display:flex;gap:8px"><button class="btn btn--sm" data-go="dashboard">← 작품 목록</button>
      <button class="btn btn--accent btn--sm" id="ep-upload">+ 회차 업로드</button></div></div>
      <div id="ep-list">${loading}</div>`);
    bindGo();
    $('#ep-upload').addEventListener('click', () => openUpload(id, title));
    try {
      const slice = await api('GET', `/api/series/${id}/episodes?size=50`);
      const rows = slice.content;
      $('#ep-list').innerHTML = rows.length ? `<div class="rows">${rows.map(epRow).join('')}</div>`
        : emptyBox('발행된 회차가 없어요', '회차를 업로드하면 여기에 표시돼요. (예약·미발행 회차는 발행 후 보여요)');
    } catch (e) { $('#ep-list').innerHTML = errBox(e); }
  }
  function epRow(ep) {
    const when = ep.publishAt ? new Date(ep.publishAt).toLocaleString('ko-KR') : '';
    return `<div class="row"><span class="row__no mono">${ep.episodeNo}</span>
      <div class="row__main"><div class="t">${esc(ep.title)}</div><div class="s">${when}</div></div></div>`;
  }

  // ====================================================================
  // 작가 — 회차 업로드 모달
  // ====================================================================
  let uploadFiles = [];
  function openUpload(seriesId, title) {
    uploadFiles = [];
    const m = document.createElement('div');
    m.className = 'modal-bg'; m.id = 'uploadModal';
    m.innerHTML = `<form class="modal panel" id="uploadForm">
      <h2>회차 업로드</h2><p class="sub">${esc(title)}</p>
      <div class="field"><label for="u-title">회차 제목</label><input id="u-title" required placeholder="예) 1화 — 시작" /></div>
      <div class="field"><label for="u-publish">예약 발행 (선택)</label><input id="u-publish" type="datetime-local" />
        <span class="hint">비워두면 즉시 발행</span></div>
      <div class="field"><label>이미지 (여러 장, jpg/png)</label>
        <label class="upload-drop" id="u-drop"><input id="u-files" type="file" accept="image/png,image/jpeg" multiple hidden>
          <b>클릭해서 이미지 선택</b><div class="hint">순서대로 업로드돼요</div></label>
        <div class="thumbs" id="u-thumbs"></div></div>
      <div class="modal__actions">
        <button type="button" class="btn btn--sm" id="u-cancel">취소</button>
        <button type="submit" class="btn btn--accent btn--sm" id="u-submit">업로드</button>
      </div></form>`;
    document.body.appendChild(m);
    const filesInput = $('#u-files'), drop = $('#u-drop');
    $('#u-title').focus();
    drop.addEventListener('click', () => filesInput.click());
    filesInput.addEventListener('change', () => { uploadFiles = [...filesInput.files]; renderThumbs(); });
    ['dragover','dragleave','drop'].forEach((ev) => drop.addEventListener(ev, (e) => {
      e.preventDefault(); drop.classList.toggle('drag', ev === 'dragover');
      if (ev === 'drop') { uploadFiles = [...e.dataTransfer.files].filter((f)=>f.type.startsWith('image/')); renderThumbs(); }
    }));
    $('#u-cancel').addEventListener('click', () => m.remove());
    m.addEventListener('click', (e) => { if (e.target === m) m.remove(); });
    $('#uploadForm').addEventListener('submit', (e) => onUpload(e, seriesId, m));
  }
  function renderThumbs() {
    $('#u-thumbs').innerHTML = uploadFiles.map((f) => `<img class="thumb" src="${URL.createObjectURL(f)}" alt="">`).join('');
  }
  async function onUpload(e, seriesId, modal) {
    e.preventDefault();
    const title = $('#u-title').value.trim();
    if (!title) return toast('회차 제목을 입력하세요', 'err');
    if (!uploadFiles.length) return toast('이미지를 한 장 이상 선택하세요', 'err');
    const fd = new FormData();
    fd.append('title', title);
    const pub = $('#u-publish').value;
    if (pub) fd.append('publishAt', new Date(pub).toISOString());
    uploadFiles.forEach((f) => fd.append('images', f));
    const btn = $('#u-submit'); btn.disabled = true; btn.innerHTML = '<span class="spinner"></span>';
    try {
      const r = await api('POST', `/api/series/${seriesId}/episodes`, { form: fd });
      toast(`${r.episodeNo}화를 업로드했어요`, 'ok');
      modal.remove();
      if (state.view === 'episodes') route();
    } catch (err) { toast(errMsg(err), 'err'); btn.disabled = false; btn.textContent = '업로드'; }
  }

  // ====================================================================
  // 관리자 — 작품 관리
  // ====================================================================
  async function viewAdminSeries() {
    setMain(`<div class="page-head"><div><span class="eyebrow">Admin</span><h1>작품 관리</h1>
      <p>공개 작품의 연령등급·공개여부·성인분류를 변경하세요</p></div></div>
      <div id="as-list">${loading}</div>`);
    try {
      const page = await api('GET', '/api/series?size=100');
      const rows = page.content;
      $('#as-list').innerHTML = rows.length ? `<div class="rows">${rows.map(adminSeriesRow).join('')}</div>`
        : emptyBox('공개된 작품이 없어요', '작가가 작품을 공개하면 여기에 표시돼요.');
      $('#as-list').addEventListener('click', onAdminSeriesClick);
    } catch (e) { $('#as-list').innerHTML = errBox(e); }
  }
  function adminSeriesRow(s) {
    return `<div class="row" data-id="${s.id}" data-title="${esc(s.title)}" data-age="${s.ageRating}">
      <span class="row__no mono">#${s.id}</span>
      <div class="row__main"><div class="t">${esc(s.title)}</div><div class="s">${esc(s.authorNickname)}</div></div>
      <div class="row__side">
        <span class="tag ${s.ageRating === 'AGE_19' ? 'tag--19' : ''}">${AGE[s.ageRating] || s.ageRating}</span>
        ${s.adultOnly ? '<span class="tag tag--adult">성인</span>' : ''}
        ${s.visible ? '<span class="tag tag--on">공개</span>' : '<span class="tag tag--off">비공개</span>'}
        <button class="btn btn--sm" data-act="manage">관리</button>
      </div></div>`;
  }
  function onAdminSeriesClick(e) {
    if (e.target.closest('button[data-act="manage"]')) {
      const row = e.target.closest('.row');
      openManageSeries(Number(row.dataset.id), row.dataset.title, row.dataset.age);
    }
  }
  function openManageSeries(id, title, age) {
    const m = document.createElement('div'); m.className = 'modal-bg';
    m.innerHTML = `<div class="modal panel">
      <h2>작품 관리</h2><p class="sub">#${id} · ${esc(title)}</p>
      <div class="field"><label>연령등급</label><select id="m-age">
        ${Object.entries(AGE).map(([k,v])=>`<option value="${k}" ${k===age?'selected':''}>${v}</option>`).join('')}</select></div>
      <div class="modal__actions"><button class="btn btn--sm" data-x>닫기</button>
        <button class="btn btn--accent btn--sm" data-act="age">연령등급 변경</button></div>
      <hr style="border:none;border-top:2px solid var(--ink);margin:18px 0">
      <div class="modal__actions" style="justify-content:flex-start">
        <button class="btn btn--sm" data-act="vis-on">공개</button>
        <button class="btn btn--sm" data-act="vis-off">비공개</button>
        <button class="btn btn--sm" data-act="adult-on">성인전용 지정</button>
        <button class="btn btn--sm" data-act="adult-off">성인전용 해제</button>
      </div></div>`;
    document.body.appendChild(m);
    m.addEventListener('click', async (e) => {
      if (e.target === m || e.target.closest('[data-x]')) return m.remove();
      const act = e.target.closest('button[data-act]')?.dataset.act; if (!act) return;
      try {
        if (act === 'age') await api('PATCH', `/api/admin/series/${id}/age-rating`, { json: { ageRating: $('#m-age', m).value } });
        else if (act === 'vis-on') await api('PATCH', `/api/admin/series/${id}/visibility`, { json: { visible: true } });
        else if (act === 'vis-off') await api('PATCH', `/api/admin/series/${id}/visibility`, { json: { visible: false } });
        else if (act === 'adult-on') await api('PATCH', `/api/admin/series/${id}/adult-only`, { json: { adultOnly: true } });
        else if (act === 'adult-off') await api('PATCH', `/api/admin/series/${id}/adult-only`, { json: { adultOnly: false } });
        toast('변경했어요', 'ok'); m.remove(); route();
      } catch (err) { toast(errMsg(err), 'err'); }
    });
  }

  // ====================================================================
  // 관리자 — 사용자 권한
  // ====================================================================
  function viewAdminUsers() {
    setMain(`<div class="page-head"><div><span class="eyebrow">Admin</span><h1>사용자 권한</h1>
      <p>사용자 ID로 역할을 변경합니다 (작가 권한 부여 등)</p></div></div>
      <form class="panel form-panel" id="roleForm" style="max-width:480px">
        <div class="field"><label for="r-uid">사용자 ID</label><input id="r-uid" type="number" min="1" required placeholder="예) 2" />
          <span class="hint">현재는 사용자 ID 기반 — 사용자 검색 API는 추후 추가 예정</span></div>
        <div class="field"><label for="r-role">역할</label><select id="r-role">
          <option value="READER">독자 (READER)</option>
          <option value="CREATOR">작가 (CREATOR)</option>
          <option value="ADMIN">관리자 (ADMIN)</option></select></div>
        <button class="btn btn--accent" type="submit" id="r-submit">역할 변경</button>
        <div id="r-result" style="margin-top:14px"></div>
      </form>`);
    $('#roleForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      const uid = $('#r-uid').value, role = $('#r-role').value;
      if (!uid) return toast('사용자 ID를 입력하세요', 'err');
      const btn = $('#r-submit'); btn.disabled = true;
      try {
        const u = await api('PATCH', `/api/admin/users/${uid}/role`, { json: { role } });
        $('#r-result').innerHTML = `<div class="panel" style="padding:14px"><b>${esc(u.nickname)}</b> (${esc(u.email)}) → <span class="tag">${esc(u.role)}</span></div>`;
        toast('역할을 변경했어요', 'ok');
      } catch (err) { toast(err.status === 404 ? '해당 ID의 사용자가 없어요' : errMsg(err), 'err'); }
      finally { btn.disabled = false; }
    });
  }

  // ---- helpers ----
  function stat(n, label) { return `<div class="panel"><div class="stat"><span class="n mono">${n}</span><span class="l">${label}</span></div></div>`; }
  function emptyBox(title, sub) { return `<div class="empty"><b>${esc(title)}</b>${esc(sub)}</div>`; }
  function errBox(e) { return `<div class="empty"><b>불러오지 못했어요</b>${esc(errMsg(e))}</div>`; }
  function bindGo() { app.querySelectorAll('[data-go]').forEach((b) => b.addEventListener('click', () => { state.view = b.dataset.go; route(); })); }

  // ====================================================================
  // 부트스트랩
  // ====================================================================
  function boot() { route(); }

  async function init() {
    if (!state.token) return renderLogin();
    try {
      state.user = await api('GET', '/api/users/me');
      if (state.user.role === 'READER') { clearTokens(); return renderLogin(); }
      boot();
    } catch { clearTokens(); renderLogin(); }
  }

  init();
})();
