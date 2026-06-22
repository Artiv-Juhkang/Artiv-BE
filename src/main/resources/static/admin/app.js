/* AppToon Studio Console — 작가/관리자 콘솔 SPA (Vanilla JS) */
(() => {
  'use strict';

  const AGE = { ALL: '전체', AGE_12: '12세', AGE_15: '15세', AGE_19: '19세' };
  const STATUS = { ONGOING: '연재중', COMPLETED: '완결', HIATUS: '휴재' };
  const GENRE = { ROMANCE: '로맨스', FANTASY: '판타지', ACTION: '액션', DRAMA: '드라마', DAILY: '일상', COMEDY: '코미디', THRILLER: '스릴러', SPORTS: '스포츠', HORROR: '공포', ETC: '기타' };
  const DAYS = [['MONDAY','월'],['TUESDAY','화'],['WEDNESDAY','수'],['THURSDAY','목'],['FRIDAY','금'],['SATURDAY','토'],['SUNDAY','일']];
  const ITYPE = { ACCOUNT: '계정', PAYMENT: '결제', CONTENT: '콘텐츠·신고', CREATOR: '작가·작품', BUG: '오류', ETC: '기타' };
  const ISTATUS = { PENDING: '대기', ANSWERED: '답변완료', CLOSED: '종료' };
  const CONSENT_LABEL = { TERMS_OF_SERVICE: '서비스 이용약관', PRIVACY_POLICY: '개인정보 처리방침', MARKETING_EMAIL: '마케팅 수신', ADULT_CONTENT_19: '성인 콘텐츠 열람' };
  const CREQ_STATUS = { PENDING: '대기', APPROVED: '승인', REJECTED: '거부' };
  const PCAT = { RECOMMEND: '추천', FREE: '자유', FANART: '팬아트', QUESTION: '질문' };
  const RREASON = { SPAM: '스팸', ABUSE: '욕설', SEXUAL: '음란', COPYRIGHT: '저작권', ETC: '기타' };
  const RSTATUS = { PENDING: '접수', RESOLVED: '처리', DISMISSED: '기각' };
  const RTYPE = { POST: '게시글', COMMENT: '댓글', USER: '사용자', SERIES: '작품', EPISODE: '회차' };

  const state = {
    token: localStorage.getItem('apptoon_token') || null,
    refresh: localStorage.getItem('apptoon_refresh') || null,
    user: null,
    view: null,
    unread: 0,
  };
  let unreadTimer = null;
  const NOTI_LABEL = { INQUIRY_ANSWERED: '문의 답변', EPISODE_PUBLISHED: '새 회차' };

  const app = document.getElementById('app');
  const $ = (s, r = document) => r.querySelector(s);
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;' }[c]));
  const fmtDate = (iso) => { try { return new Date(iso).toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' }); } catch (_) { return ''; } };

  function setTokens(access, refresh) {
    state.token = access; state.refresh = refresh;
    localStorage.setItem('apptoon_token', access);
    localStorage.setItem('apptoon_refresh', refresh);
  }
  function clearTokens() {
    state.token = state.refresh = state.user = null;
    state.unread = 0;
    if (unreadTimer) { clearInterval(unreadTimer); unreadTimer = null; }
    localStorage.removeItem('apptoon_token');
    localStorage.removeItem('apptoon_refresh');
  }

  // ---- theme (라이트/다크/시스템) ----
  const THEME_KEY = 'apptoon_theme';
  const sysDark = window.matchMedia('(prefers-color-scheme: dark)');
  const themePref = () => localStorage.getItem(THEME_KEY) || 'system';
  const resolveTheme = (pref) => (pref === 'system' ? (sysDark.matches ? 'dark' : 'light') : pref);
  function applyTheme() { document.documentElement.setAttribute('data-theme', resolveTheme(themePref())); }
  function setTheme(pref) {
    if (pref === 'system') localStorage.removeItem(THEME_KEY);
    else localStorage.setItem(THEME_KEY, pref);
    applyTheme();
  }
  sysDark.addEventListener('change', () => { if (themePref() === 'system') applyTheme(); });

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
          <button type="button" class="login__theme" id="loginTheme" aria-label="테마 전환"></button>
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
    syncLoginTheme();
    $('#loginTheme').addEventListener('click', () => {
      setTheme(resolveTheme(themePref()) === 'dark' ? 'light' : 'dark');
      syncLoginTheme();
    });
    $('#email').focus();
  }
  function syncLoginTheme() {
    const b = $('#loginTheme');
    if (b) b.textContent = resolveTheme(themePref()) === 'dark' ? '🌙' : '☀️';
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
      return [['admin-series', '작품 관리'], ['admin-users', '사용자 관리'], ['admin-inquiries', '문의 관리'],
        ['admin-creator-requests', '작가 신청'], ['admin-reports', '신고 관리'], ['admin-community', '커뮤니티 관리']];
    }
    return [['dashboard', '내 작품'], ['create', '작품 등록'], ['followers', '팔로워'], ['inquiries', '문의']];
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
          <div class="side__brandsub">Studio · ${state.user.role === 'ADMIN' ? '관리자' : '작업실'}</div>
          <button class="side__who" id="account" aria-haspopup="dialog" title="설정 열기">
            <strong>${esc(state.user.nickname)}</strong>
            <span class="em">${esc(state.user.email)}</span><br>${roleStamp}
            <span class="cog" aria-hidden="true">⚙</span>
          </button>
          <button class="side__bell" id="bell" aria-haspopup="dialog" title="알림">
            <span class="bell__ico" aria-hidden="true">🔔</span> 알림
            <span class="bell__badge" id="bellBadge" hidden>0</span>
          </button>
          <nav class="nav">
            ${items.map(([k, label]) => `<button data-view="${k}" ${k === state.view ? 'aria-current="true"' : ''}>${label}</button>`).join('')}
          </nav>
        </aside>
        <main class="main" id="main">${inner || ''}</main>
      </div>`;
    $('.nav').addEventListener('click', (e) => {
      const b = e.target.closest('button[data-view]');
      if (b) { state.view = b.dataset.view; route(); }
    });
    $('#account').addEventListener('click', openSettings);
    $('#bell').addEventListener('click', openNotifications);
    syncBellBadge();
  }

  // ---- 알림 벨 (폴링 배지 + 알림함 모달) ----
  function syncBellBadge() {
    const b = $('#bellBadge'); if (!b) return;
    if (state.unread > 0) { b.textContent = state.unread > 99 ? '99+' : state.unread; b.hidden = false; }
    else b.hidden = true;
  }
  async function refreshUnread() {
    try { const r = await api('GET', '/api/me/notifications/unread-count'); state.unread = r.count; syncBellBadge(); }
    catch (_) { /* 폴링 실패는 조용히 무시 */ }
  }
  function startUnreadPoll() {
    refreshUnread();
    if (unreadTimer) clearInterval(unreadTimer);
    unreadTimer = setInterval(refreshUnread, 60000); // 60s 폴링
  }

  async function openNotifications() {
    const m = document.createElement('div'); m.className = 'modal-bg';
    m.innerHTML = `<div class="modal panel" role="dialog" aria-label="알림">
      <div class="modal__hd"><h2>알림</h2><button class="btn btn--ghost btn--sm" id="noti-allread">모두 읽음</button></div>
      <div class="seg seg--wrap" id="noti-tabs">
        <button data-noti-type="" aria-current="true">전체</button>
        ${Object.entries(NOTI_LABEL).map(([k, l]) => `<button data-noti-type="${k}">${l}</button>`).join('')}
      </div>
      <div id="noti-list" class="noti-list">${loading}</div>
      <div class="modal__actions"><button class="btn btn--sm" data-x>닫기</button></div></div>`;
    document.body.appendChild(m);
    const dialog = m.querySelector('.modal'); dialog.setAttribute('tabindex', '-1'); dialog.focus();
    const close = () => { m.remove(); document.removeEventListener('keydown', onKey); };
    function onKey(ev) { if (ev.key === 'Escape') close(); }
    document.addEventListener('keydown', onKey);
    let curType = '';
    const loadList = async () => {
      const box = $('#noti-list', m);
      try {
        const page = await api('GET', `/api/me/notifications${curType ? '?type=' + curType : ''}`);
        box.innerHTML = page.content.length
          ? page.content.map(notiRow).join('')
          : '<div class="empty">알림이 없어요</div>';
      } catch (e) { box.innerHTML = errBox(e); }
    };
    m.addEventListener('click', async (e) => {
      if (e.target === m || e.target.closest('[data-x]')) return close();
      const tab = e.target.closest('[data-noti-type]');
      if (tab) {
        m.querySelectorAll('#noti-tabs button').forEach((b) => b.removeAttribute('aria-current'));
        tab.setAttribute('aria-current', 'true'); curType = tab.dataset.notiType; return loadList();
      }
      if (e.target.closest('#noti-allread')) {
        try { await api('PATCH', '/api/me/notifications/read-all'); await refreshUnread(); await loadList(); toast('모두 읽음 처리했어요', 'ok'); }
        catch (err) { toast(errMsg(err), 'err'); }
        return;
      }
      const row = e.target.closest('[data-noti-id]');
      if (row) {
        try {
          const n = await api('PATCH', `/api/me/notifications/${row.dataset.notiId}/read`);
          await refreshUnread(); close(); notiRoute(n); // 읽음 처리 + 라우팅
        } catch (err) { toast(errMsg(err), 'err'); }
      }
    });
    loadList();
  }

  function notiRow(n) {
    return `<button class="noti-row ${n.read ? '' : 'noti-row--unread'}" data-noti-id="${n.id}">
      <span class="noti-row__dot" aria-hidden="true"></span>
      <span class="noti-row__body"><b>${esc(n.title)}</b><span class="noti-row__msg">${esc(n.message)}</span>
        <span class="noti-row__meta">${esc(NOTI_LABEL[n.type] || n.type)} · ${fmtDate(n.createdAt)}</span></span></button>`;
  }

  /** 클릭 시 (targetType,targetId)로 콘솔 내 라우팅. 콘솔에 화면이 없으면 안내만. */
  function notiRoute(n) {
    if (n.targetType === 'INQUIRY') {
      state.view = state.user.role === 'ADMIN' ? 'admin-inquiries' : 'inquiries';
      route(); return;
    }
    toast(`이동 대상: ${esc(n.targetType || '-')} #${n.targetId ?? '-'}`, 'ok');
  }

  // ---- 설정 모달 (계정 · 테마) ----
  function openSettings() {
    const u = state.user;
    const joined = u.createdAt ? new Date(u.createdAt).toLocaleDateString('ko-KR') : '';
    const roleStamp = u.role === 'ADMIN' ? '<span class="stamp stamp--admin">ADMIN</span>'
      : `<span class="stamp">${u.role === 'CREATOR' ? 'CREATOR' : 'READER'}</span>`;
    const pref = themePref();
    const opts = [['system', '🖥️', '시스템'], ['light', '☀️', '라이트'], ['dark', '🌙', '다크']];
    const avatar = u.avatarUrl
      ? `<img class="set-avatar" src="${esc(u.avatarUrl)}" alt="">`
      : `<div class="set-avatar set-avatar--empty">${esc((u.nickname || '?').slice(0, 1))}</div>`;
    const m = document.createElement('div'); m.className = 'modal-bg';
    m.innerHTML = `<div class="modal panel" role="dialog" aria-label="설정">
      <h2>설정</h2><p class="sub">계정과 화면을 관리하세요</p>
      <div class="set-sec"><h3>내 정보</h3>
        <div class="set-me" style="flex-direction:row;align-items:center;gap:14px">${avatar}
          <div><div class="nm">${esc(u.nickname)} ${roleStamp}</div>
            <div class="ln">${esc(u.email)}</div><div class="ln">가입일 ${joined}</div></div></div></div>
      <div class="set-sec"><h3>프로필 편집</h3>
        <div class="field"><label for="pf-nick">닉네임</label><input id="pf-nick" maxlength="20" value="${esc(u.nickname)}"></div>
        <div class="field"><label for="pf-bio">소개</label><textarea id="pf-bio" maxlength="500" placeholder="자기소개">${esc(u.bio || '')}</textarea></div>
        <div class="field"><label>아바타 <span class="hint">(jpg/png)</span></label>
          <label class="btn btn--sm" style="width:fit-content">이미지 선택<input id="pf-avatar" type="file" accept="image/png,image/jpeg" hidden></label></div>
        <button class="btn btn--accent btn--sm" id="pf-save">프로필 저장</button></div>
      <div class="set-sec"><h3>비밀번호 변경</h3>
        <div class="field"><label for="pf-cur">현재 비밀번호</label><input id="pf-cur" type="password" autocomplete="current-password"></div>
        <div class="field"><label for="pf-new">새 비밀번호</label><input id="pf-new" type="password" autocomplete="new-password" placeholder="8자 이상"></div>
        <button class="btn btn--sm" id="pf-pw">비밀번호 변경</button></div>
      <div class="set-sec"><h3>테마</h3>
        <div class="seg" id="set-theme">${opts.map(([v, ic, l]) =>
          `<button data-theme-val="${v}" ${v === pref ? 'aria-current="true"' : ''}>${ic} ${l}</button>`).join('')}</div></div>
      <div class="set-sec"><h3>동의 내역</h3><div id="set-consents">${loading}</div></div>
      <hr class="divider">
      <div class="modal__actions" style="justify-content:space-between">
        <button class="btn btn--ghost btn--sm" id="set-logout">로그아웃</button>
        <button class="btn btn--sm" data-x>닫기</button>
      </div>
      <div class="set-foot">AppToon Studio Console</div>
    </div>`;
    document.body.appendChild(m);
    const dialog = m.querySelector('.modal');
    dialog.setAttribute('tabindex', '-1');
    dialog.focus(); // 포커스를 모달로 이동 — 접근성 + Esc 키 동작 보장
    const close = () => { m.remove(); document.removeEventListener('keydown', onKey); };
    function onKey(ev) { if (ev.key === 'Escape') close(); }
    document.addEventListener('keydown', onKey);
    m.addEventListener('click', (e) => {
      if (e.target === m || e.target.closest('[data-x]')) return close();
      const t = e.target.closest('[data-theme-val]');
      if (t) {
        setTheme(t.dataset.themeVal);
        m.querySelectorAll('#set-theme button').forEach((b) => b.removeAttribute('aria-current'));
        t.setAttribute('aria-current', 'true');
        return;
      }
      if (e.target.closest('#set-logout')) { close(); clearTokens(); renderLogin(); }
    });
    $('#pf-save', m).addEventListener('click', async () => {
      try {
        const nick = $('#pf-nick', m).value.trim();
        if (nick && nick !== state.user.nickname) state.user = await api('PATCH', '/api/users/me/nickname', { json: { nickname: nick } });
        state.user = await api('PATCH', '/api/users/me/bio', { json: { bio: $('#pf-bio', m).value } });
        toast('프로필을 저장했어요', 'ok');
      } catch (err) { toast(errMsg(err), 'err'); }
    });
    $('#pf-avatar', m).addEventListener('change', async (e) => {
      const f = e.target.files[0]; if (!f) return;
      const fd = new FormData(); fd.append('file', f);
      try { state.user = await api('POST', '/api/users/me/avatar', { form: fd }); toast('아바타를 변경했어요', 'ok'); close(); openSettings(); }
      catch (err) { toast(errMsg(err), 'err'); }
    });
    $('#pf-pw', m).addEventListener('click', async () => {
      const cur = $('#pf-cur', m).value, nw = $('#pf-new', m).value;
      if (!cur || !nw) return toast('현재·새 비밀번호를 입력하세요', 'err');
      try { await api('PATCH', '/api/users/me/password', { json: { currentPassword: cur, newPassword: nw } });
        toast('비밀번호를 변경했어요', 'ok'); $('#pf-cur', m).value = ''; $('#pf-new', m).value = ''; }
      catch (err) { toast('현재 비밀번호가 올바르지 않아요', 'err'); }
    });
    loadConsents(m);
  }

  async function loadConsents(m) {
    const box = $('#set-consents', m); if (!box) return;
    try {
      const list = await api('GET', '/api/users/me/consents');
      box.innerHTML = list.map((c) => {
        const label = CONSENT_LABEL[c.consentType] || c.consentType;
        const when = c.agreedAt ? new Date(c.agreedAt).toLocaleDateString('ko-KR') : '-';
        const ctrl = c.consentType === 'MARKETING_EMAIL'
          ? `<label class="check ${c.agreed ? 'check--on' : ''}"><input type="checkbox" data-consent="MARKETING_EMAIL" ${c.agreed ? 'checked' : ''}><span>${c.agreed ? '동의' : '미동의'}</span></label>`
          : `<span class="tag ${c.agreed ? 'tag--on' : 'tag--off'}">${c.agreed ? '동의' : '미동의'}</span>`;
        return `<div class="set-consent"><div><b>${label}</b>${c.required ? ' <span class="hint">(필수)</span>' : ''}<div class="ln">v${c.version} · ${when}</div></div>${ctrl}</div>`;
      }).join('');
      box.addEventListener('change', async (e) => {
        const cb = e.target.closest('[data-consent]'); if (!cb) return;
        try { await api('PATCH', '/api/users/me/consents', { json: { consents: { [cb.dataset.consent]: cb.checked } } });
          toast('동의 설정을 변경했어요', 'ok'); loadConsents(m); }
        catch (err) { toast(errMsg(err), 'err'); cb.checked = !cb.checked; }
      });
    } catch (e) { box.innerHTML = errBox(e); }
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
    if (v === 'admin-inquiries') return viewAdminInquiries();
    if (v === 'admin-creator-requests') return viewAdminCreatorRequests();
    if (v === 'inquiries') return viewMyInquiries();
    if (v === 'followers') return viewFollowers();
    if (v === 'admin-reports') return viewAdminReports();
    if (v === 'admin-community') return viewAdminCommunity();
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
        ${s.genre ? `<span class="tag">${GENRE[s.genre] || s.genre}</span>` : ''}
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
          <div class="field"><label for="c-genre">장르</label><select id="c-genre">
            ${Object.entries(GENRE).map(([k, v]) => `<option value="${k}">${v}</option>`).join('')}</select></div>
          <div class="field full"><label for="c-tags">태그 <span class="hint">(쉼표로 구분 · 최대 10개)</span></label>
            <input id="c-tags" placeholder="예) 회귀, 먼치킨, 학원물" /></div>
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
    const genre = $('#c-genre').value;
    const tags = $('#c-tags').value.split(',').map((t) => t.trim()).filter(Boolean);
    if (!title) return toast('제목을 입력하세요', 'err');
    if (!publishDays.length) return toast('연재 요일을 하나 이상 선택하세요', 'err');
    if (adultOnly && ageRating !== 'AGE_19') return toast('성인 전용은 19세 등급에서만 가능해요', 'err');
    const btn = $('#c-submit'); btn.disabled = true;
    try {
      await api('POST', '/api/series', { json: {
        title, description: $('#c-desc').value.trim(), ageRating,
        status: $('#c-status').value, publishDays, adultOnly, genre, tags,
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
  const MAX_IMG_BYTES = 10 * 1024 * 1024;
  let uploadFiles = []; // [{ id, file, url }]
  let uploadSeq = 0;
  function openUpload(seriesId, title) {
    uploadFiles = []; uploadSeq = 0;
    const m = document.createElement('div');
    m.className = 'modal-bg'; m.id = 'uploadModal';
    m.innerHTML = `<form class="modal panel" id="uploadForm">
      <h2>회차 업로드</h2><p class="sub">${esc(title)}</p>
      <div class="field"><label for="u-title">회차 제목</label><input id="u-title" required placeholder="예) 1화 — 시작" /></div>
      <div class="field"><label for="u-publish">예약 발행 (선택)</label><input id="u-publish" type="datetime-local" />
        <span class="hint">비워두면 즉시 발행</span></div>
      <div class="field"><label>이미지 (여러 장, jpg/png · 최대 10MB)</label>
        <label class="upload-drop" id="u-drop"><input id="u-files" type="file" accept="image/png,image/jpeg" multiple hidden>
          <b id="u-drop-label">클릭하거나 끌어다 놓기</b><div class="hint">위에서부터 순서대로 발행돼요 · 여러 번 추가할 수 있어요</div></label>
        <div class="thumbs" id="u-thumbs"></div></div>
      <div class="modal__actions">
        <button type="button" class="btn btn--sm" id="u-cancel">취소</button>
        <button type="submit" class="btn btn--accent btn--sm" id="u-submit">업로드</button>
      </div></form>`;
    document.body.appendChild(m);
    const filesInput = $('#u-files'), drop = $('#u-drop');
    $('#u-title').focus();
    filesInput.addEventListener('change', () => { addFiles(filesInput.files); filesInput.value = ''; });
    ['dragover','dragleave','drop'].forEach((ev) => drop.addEventListener(ev, (e) => {
      e.preventDefault(); drop.classList.toggle('drag', ev === 'dragover');
      if (ev === 'drop') addFiles(e.dataTransfer.files);
    }));
    $('#u-thumbs').addEventListener('click', (e) => {
      const del = e.target.closest('[data-del]');
      if (del) { e.preventDefault(); removeFile(Number(del.dataset.del)); }
    });
    $('#u-cancel').addEventListener('click', () => closeUpload(m));
    m.addEventListener('click', (e) => { if (e.target === m) closeUpload(m); });
    $('#uploadForm').addEventListener('submit', (e) => onUpload(e, seriesId, m));
  }
  function addFiles(fileList) {
    const all = [...fileList];
    const imgs = all.filter((f) => f.type === 'image/png' || f.type === 'image/jpeg');
    const ok = imgs.filter((f) => f.size <= MAX_IMG_BYTES);
    ok.forEach((f) => uploadFiles.push({ id: ++uploadSeq, file: f, url: URL.createObjectURL(f) }));
    renderThumbs();
    if (imgs.length < all.length) toast('jpg·png 이미지만 추가할 수 있어요', 'err');
    else if (ok.length < imgs.length) toast(`${imgs.length - ok.length}장은 10MB를 넘어 제외했어요`, 'err');
  }
  function removeFile(id) {
    const i = uploadFiles.findIndex((it) => it.id === id);
    if (i < 0) return;
    URL.revokeObjectURL(uploadFiles[i].url);
    uploadFiles.splice(i, 1);
    renderThumbs();
  }
  function closeUpload(m) { uploadFiles.forEach((it) => URL.revokeObjectURL(it.url)); uploadFiles = []; m.remove(); }
  function renderThumbs() {
    $('#u-thumbs').innerHTML = uploadFiles.map((it, i) =>
      `<div class="thumb-wrap"><img class="thumb" src="${it.url}" alt=""><span class="thumb-no">${i + 1}</span>` +
      `<button type="button" class="thumb-x" data-del="${it.id}" aria-label="삭제">×</button></div>`).join('');
    const label = $('#u-drop-label');
    if (label) label.textContent = uploadFiles.length ? `이미지 ${uploadFiles.length}장 — 더 추가하기` : '클릭하거나 끌어다 놓기';
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
    uploadFiles.forEach((it) => fd.append('images', it.file));
    const btn = $('#u-submit'); btn.disabled = true; btn.innerHTML = '<span class="spinner"></span>';
    try {
      const r = await api('POST', `/api/series/${seriesId}/episodes`, { form: fd });
      toast(`${r.episodeNo}화를 업로드했어요`, 'ok');
      closeUpload(modal);
      if (state.view === 'episodes') route();
    } catch (err) { toast(errMsg(err), 'err'); btn.disabled = false; btn.textContent = '업로드'; }
  }

  // ====================================================================
  // 관리자 — 작품 관리
  // ====================================================================
  let adminSeriesFilter = ''; // '' 전체 | 'true' 공개 | 'false' 비공개
  async function viewAdminSeries() {
    const chips = [['', '전체'], ['true', '공개'], ['false', '비공개']];
    setMain(`<div class="page-head"><div><span class="eyebrow">Admin</span><h1>작품 관리</h1>
      <p>비공개 작품 포함 전체. 연령등급·공개여부·성인분류를 변경하세요</p></div></div>
      <div class="filters" id="as-filter">${chips.map(([v, l]) =>
        `<button class="chip" data-v="${v}" ${v === adminSeriesFilter ? 'aria-current="true"' : ''}>${l}</button>`).join('')}</div>
      <div id="as-list">${loading}</div>`);
    $('#as-filter').addEventListener('click', (e) => {
      const b = e.target.closest('[data-v]'); if (!b) return;
      adminSeriesFilter = b.dataset.v; viewAdminSeries();
    });
    try {
      const q = adminSeriesFilter ? `&visible=${adminSeriesFilter}` : '';
      const page = await api('GET', `/api/admin/series?size=100${q}`);
      const rows = page.content;
      $('#as-list').innerHTML = rows.length ? `<div class="rows">${rows.map(adminSeriesRow).join('')}</div>`
        : emptyBox('작품이 없어요', '작가가 작품을 등록하면 여기에 표시돼요.');
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
      <hr class="divider">
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
  const ROLE_LABEL = { READER: '독자', CREATOR: '작가', ADMIN: '관리자' };
  function viewAdminUsers() {
    setMain(`<div class="page-head"><div><span class="eyebrow">Admin</span><h1>사용자 관리</h1>
      <p>닉네임·이메일로 검색하고 역할을 변경하세요</p></div></div>
      <form class="filters" id="us-search">
        <input class="search-input" id="us-kw" placeholder="닉네임 또는 이메일 검색" autocomplete="off" />
        <select id="us-role">
          <option value="">전체 역할</option>
          <option value="READER">독자</option><option value="CREATOR">작가</option><option value="ADMIN">관리자</option>
        </select>
        <button class="btn btn--sm" type="submit">검색</button>
      </form>
      <div id="us-list">${loading}</div>`);
    $('#us-search').addEventListener('submit', (e) => { e.preventDefault(); loadUsers(); });
    loadUsers();
  }
  async function loadUsers() {
    const kw = $('#us-kw')?.value.trim() || '', role = $('#us-role')?.value || '';
    const list = $('#us-list'); if (list) list.innerHTML = loading;
    try {
      const q = `size=50${kw ? `&keyword=${encodeURIComponent(kw)}` : ''}${role ? `&role=${role}` : ''}`;
      const page = await api('GET', `/api/admin/users?${q}`);
      $('#us-list').innerHTML = page.content.length
        ? `<div class="rows">${page.content.map(userRow).join('')}</div>`
        : emptyBox('사용자가 없어요', '검색어나 역할 필터를 바꿔보세요.');
      $('#us-list').addEventListener('click', onUserClick);
    } catch (e) { $('#us-list').innerHTML = errBox(e); }
  }
  function userRow(u) {
    const joined = u.createdAt ? new Date(u.createdAt).toLocaleDateString('ko-KR') : '';
    return `<div class="row" data-id="${u.id}" data-role="${u.role}">
      <span class="row__no mono">#${u.id}</span>
      <div class="row__main"><div class="t">${esc(u.nickname)}</div><div class="s">${esc(u.email)} · 가입 ${joined}</div></div>
      <div class="row__side"><span class="tag ${u.role === 'ADMIN' ? 'tag--19' : ''}">${ROLE_LABEL[u.role] || u.role}</span>
        <button class="btn btn--sm" data-act="role">역할 변경</button></div></div>`;
  }
  function onUserClick(e) {
    if (e.target.closest('[data-act="role"]')) {
      const row = e.target.closest('.row');
      openRoleModal(Number(row.dataset.id), row.dataset.role, row.querySelector('.t').textContent);
    }
  }
  function openRoleModal(id, role, name) {
    const m = document.createElement('div'); m.className = 'modal-bg';
    m.innerHTML = `<div class="modal panel">
      <h2>역할 변경</h2><p class="sub">#${id} · ${esc(name)}</p>
      <div class="field"><label>역할</label><select id="rm-role">
        ${['READER', 'CREATOR', 'ADMIN'].map((r) => `<option value="${r}" ${r === role ? 'selected' : ''}>${ROLE_LABEL[r]} (${r})</option>`).join('')}</select></div>
      <div class="modal__actions"><button class="btn btn--sm" data-x>취소</button>
        <button class="btn btn--accent btn--sm" data-ok>변경</button></div></div>`;
    document.body.appendChild(m);
    m.addEventListener('click', async (e) => {
      if (e.target === m || e.target.closest('[data-x]')) return m.remove();
      if (e.target.closest('[data-ok]')) {
        try { await api('PATCH', `/api/admin/users/${id}/role`, { json: { role: $('#rm-role', m).value } });
          toast('역할을 변경했어요', 'ok'); m.remove(); loadUsers();
        } catch (err) { toast(errMsg(err), 'err'); }
      }
    });
  }

  // ====================================================================
  // 문의 — 공통
  // ====================================================================
  function statusTag(s) {
    const cls = s === 'ANSWERED' ? 'tag--on' : (s === 'CLOSED' ? 'tag--off' : '');
    return `<span class="tag ${cls}">${ISTATUS[s] || s}</span>`;
  }
  function imageGallery(images) {
    if (!images || !images.length) return '';
    return `<div class="iq-gallery">${images.map((im) => `<a href="${esc(im.url)}" target="_blank" rel="noopener"><img src="${esc(im.url)}" alt="" loading="lazy"></a>`).join('')}</div>`;
  }

  // ====================================================================
  // 작가/사용자 — 내 문의
  // ====================================================================
  function viewMyInquiries() {
    setMain(`<div class="page-head"><div><span class="eyebrow">Creator</span><h1>문의</h1><p>관리자에게 문의하고 답변을 확인하세요</p></div>
      <button class="btn btn--accent" id="iq-new">＋ 문의하기</button></div>
      <div id="iq-list">${loading}</div>`);
    $('#iq-new').addEventListener('click', openCreateInquiry);
    loadMyInquiries();
  }
  async function loadMyInquiries() {
    const list = $('#iq-list'); if (list) list.innerHTML = loading;
    try {
      const page = await api('GET', '/api/me/inquiries?size=50');
      $('#iq-list').innerHTML = page.content.length
        ? `<div class="rows">${page.content.map(myInquiryRow).join('')}</div>`
        : emptyBox('문의가 없어요', '“문의하기”로 첫 문의를 남겨보세요.');
      $('#iq-list').addEventListener('click', onMyInquiryClick);
    } catch (e) { $('#iq-list').innerHTML = errBox(e); }
  }
  function myInquiryRow(q) {
    const when = q.createdAt ? new Date(q.createdAt).toLocaleDateString('ko-KR') : '';
    return `<div class="row" data-id="${q.id}">
      <div class="row__main"><div class="t">${esc(q.title)}</div><div class="s">${ITYPE[q.type] || q.type} · ${when}</div></div>
      <div class="row__side">${statusTag(q.status)}<button class="btn btn--sm" data-act="open">보기</button></div></div>`;
  }
  function onMyInquiryClick(e) {
    const row = e.target.closest('.row');
    if (row && e.target.closest('[data-act="open"]')) openMyInquiry(Number(row.dataset.id));
  }

  function openCreateInquiry() {
    uploadFiles = []; uploadSeq = 0;
    const m = document.createElement('div'); m.className = 'modal-bg';
    m.innerHTML = `<form class="modal panel" id="iqForm">
      <h2>문의하기</h2><p class="sub">관리자에게 전달됩니다</p>
      <div class="field"><label for="iq-type">종류</label><select id="iq-type">
        ${Object.entries(ITYPE).map(([k, v]) => `<option value="${k}">${v}</option>`).join('')}</select></div>
      <div class="field"><label for="iq-title">제목</label><input id="iq-title" maxlength="255" placeholder="제목"></div>
      <div class="field"><label for="iq-content">내용</label><textarea id="iq-content" placeholder="문의 내용을 적어주세요"></textarea></div>
      <div class="field"><label>이미지 (선택 · jpg/png · 최대 5장)</label>
        <label class="upload-drop" id="u-drop"><input id="u-files" type="file" accept="image/png,image/jpeg" multiple hidden>
          <b id="u-drop-label">클릭하거나 끌어다 놓기</b></label>
        <div class="thumbs" id="u-thumbs"></div></div>
      <div class="modal__actions"><button type="button" class="btn btn--sm" data-x>취소</button>
        <button type="submit" class="btn btn--accent btn--sm" id="iq-submit">보내기</button></div></form>`;
    document.body.appendChild(m);
    const filesInput = $('#u-files'), drop = $('#u-drop');
    filesInput.addEventListener('change', () => { addFiles(filesInput.files); filesInput.value = ''; });
    ['dragover', 'dragleave', 'drop'].forEach((ev) => drop.addEventListener(ev, (e) => {
      e.preventDefault(); drop.classList.toggle('drag', ev === 'dragover');
      if (ev === 'drop') addFiles(e.dataTransfer.files);
    }));
    $('#u-thumbs').addEventListener('click', (e) => {
      const del = e.target.closest('[data-del]');
      if (del) { e.preventDefault(); removeFile(Number(del.dataset.del)); }
    });
    const close = () => { closeUpload(m); document.removeEventListener('keydown', onKey); };
    const onKey = (ev) => { if (ev.key === 'Escape') close(); };
    document.addEventListener('keydown', onKey);
    m.addEventListener('click', (e) => { if (e.target === m || e.target.closest('[data-x]')) close(); });
    $('#iqForm').addEventListener('submit', (e) => onCreateInquiry(e, m, close));
    $('#iq-title').focus();
  }
  async function onCreateInquiry(e, m, close) {
    e.preventDefault();
    const type = $('#iq-type').value, title = $('#iq-title').value.trim(), content = $('#iq-content').value.trim();
    if (!title) return toast('제목을 입력하세요', 'err');
    if (!content) return toast('내용을 입력하세요', 'err');
    if (uploadFiles.length > 5) return toast('이미지는 최대 5장이에요', 'err');
    const fd = new FormData();
    fd.append('type', type); fd.append('title', title); fd.append('content', content);
    uploadFiles.forEach((it) => fd.append('images', it.file));
    const btn = $('#iq-submit'); btn.disabled = true; btn.innerHTML = '<span class="spinner"></span>';
    try {
      await api('POST', '/api/me/inquiries', { form: fd });
      toast('문의를 보냈어요', 'ok');
      close(); loadMyInquiries();
    } catch (err) { toast(errMsg(err), 'err'); btn.disabled = false; btn.textContent = '보내기'; }
  }

  async function openMyInquiry(id) {
    const m = document.createElement('div'); m.className = 'modal-bg';
    m.innerHTML = `<div class="modal panel">${loading}</div>`;
    document.body.appendChild(m);
    const close = () => { m.remove(); document.removeEventListener('keydown', onKey); };
    const onKey = (ev) => { if (ev.key === 'Escape') close(); };
    document.addEventListener('keydown', onKey);
    m.addEventListener('click', (e) => { if (e.target === m || e.target.closest('[data-x]')) close(); });
    try {
      const q = await api('GET', `/api/me/inquiries/${id}`);
      m.querySelector('.modal').innerHTML = `
        <h2>${esc(q.title)}</h2>
        <div class="card__meta" style="margin-bottom:14px"><span class="tag">${ITYPE[q.type] || q.type}</span>${statusTag(q.status)}</div>
        <div class="iq-body">${esc(q.content)}</div>
        ${imageGallery(q.images)}
        ${q.answer ? `<div class="iq-answer"><div class="iq-answer__h">관리자 답변</div>${esc(q.answer)}</div>`
          : '<p class="hint" style="margin-top:14px">아직 답변이 없어요.</p>'}
        <div class="modal__actions" style="justify-content:space-between">
          <button class="btn btn--ghost btn--sm" data-del>삭제</button>
          <button class="btn btn--sm" data-x>닫기</button></div>`;
      m.querySelector('[data-del]').addEventListener('click', async () => {
        try { await api('DELETE', `/api/me/inquiries/${id}`); toast('삭제했어요', 'ok'); close(); loadMyInquiries(); }
        catch (err) { toast(errMsg(err), 'err'); }
      });
    } catch (e) {
      m.querySelector('.modal').innerHTML = errBox(e) + '<div class="modal__actions"><button class="btn btn--sm" data-x>닫기</button></div>';
    }
  }

  // ====================================================================
  // 관리자 — 문의 관리
  // ====================================================================
  let adminInquiryStatus = '';
  let adminInquiryType = '';
  function viewAdminInquiries() {
    const chips = [['', '전체'], ['PENDING', '대기'], ['ANSWERED', '답변완료'], ['CLOSED', '종료']];
    setMain(`<div class="page-head"><div><span class="eyebrow">Admin</span><h1>문의 관리</h1>
      <p>사용자 문의를 확인하고 답변하세요</p></div></div>
      <div class="filters" id="aiq-filter">
        ${chips.map(([v, l]) => `<button class="chip" data-v="${v}" ${v === adminInquiryStatus ? 'aria-current="true"' : ''}>${l}</button>`).join('')}
        <select id="aiq-type"><option value="">전체 종류</option>
          ${Object.entries(ITYPE).map(([k, v]) => `<option value="${k}" ${k === adminInquiryType ? 'selected' : ''}>${v}</option>`).join('')}</select>
      </div>
      <div id="aiq-list">${loading}</div>`);
    $('#aiq-filter').addEventListener('click', (e) => {
      const b = e.target.closest('[data-v]'); if (b) { adminInquiryStatus = b.dataset.v; viewAdminInquiries(); }
    });
    $('#aiq-type').addEventListener('change', (e) => { adminInquiryType = e.target.value; loadAdminInquiries(); });
    loadAdminInquiries();
  }
  async function loadAdminInquiries() {
    const list = $('#aiq-list'); if (list) list.innerHTML = loading;
    try {
      const q = `size=50${adminInquiryStatus ? `&status=${adminInquiryStatus}` : ''}${adminInquiryType ? `&type=${adminInquiryType}` : ''}`;
      const page = await api('GET', `/api/admin/inquiries?${q}`);
      $('#aiq-list').innerHTML = page.content.length
        ? `<div class="rows">${page.content.map(adminInquiryRow).join('')}</div>`
        : emptyBox('문의가 없어요', '필터 조건을 바꿔보세요.');
      $('#aiq-list').addEventListener('click', onAdminInquiryClick);
    } catch (e) { $('#aiq-list').innerHTML = errBox(e); }
  }
  function adminInquiryRow(q) {
    const when = q.createdAt ? new Date(q.createdAt).toLocaleDateString('ko-KR') : '';
    return `<div class="row" data-id="${q.id}"><span class="row__no mono">#${q.id}</span>
      <div class="row__main"><div class="t">${esc(q.title)}</div>
        <div class="s">${esc(q.authorNickname)} · ${ROLE_LABEL[q.authorRole] || q.authorRole} · ${when}</div></div>
      <div class="row__side"><span class="tag">${ITYPE[q.type] || q.type}</span>${statusTag(q.status)}
        <button class="btn btn--sm" data-act="open">상세</button></div></div>`;
  }
  function onAdminInquiryClick(e) {
    const row = e.target.closest('.row');
    if (row && e.target.closest('[data-act="open"]')) openAdminInquiry(Number(row.dataset.id));
  }
  async function openAdminInquiry(id) {
    const m = document.createElement('div'); m.className = 'modal-bg';
    m.innerHTML = `<div class="modal panel">${loading}</div>`;
    document.body.appendChild(m);
    const close = () => { m.remove(); document.removeEventListener('keydown', onKey); };
    const onKey = (ev) => { if (ev.key === 'Escape') close(); };
    document.addEventListener('keydown', onKey);
    m.addEventListener('click', (e) => { if (e.target === m || e.target.closest('[data-x]')) close(); });
    try {
      const q = await api('GET', `/api/admin/inquiries/${id}`);
      m.querySelector('.modal').innerHTML = `
        <h2>${esc(q.title)}</h2>
        <p class="sub">${esc(q.authorNickname)} · ${esc(q.authorEmail)} · ${ROLE_LABEL[q.authorRole] || q.authorRole}</p>
        <div class="card__meta" style="margin-bottom:14px"><span class="tag">${ITYPE[q.type] || q.type}</span>${statusTag(q.status)}</div>
        <div class="iq-body">${esc(q.content)}</div>
        ${imageGallery(q.images)}
        <hr class="divider">
        <div class="field"><label for="aiq-answer">답변</label>
          <textarea id="aiq-answer" maxlength="2000" placeholder="답변을 입력하세요">${esc(q.answer || '')}</textarea></div>
        <div class="field"><label for="aiq-status">상태</label><select id="aiq-status">
          ${['PENDING', 'ANSWERED', 'CLOSED'].map((s) => `<option value="${s}" ${s === q.status ? 'selected' : ''}>${ISTATUS[s]}</option>`).join('')}</select></div>
        <div class="modal__actions" style="justify-content:space-between">
          <button class="btn btn--ghost btn--sm" data-del>삭제</button>
          <span style="display:flex;gap:8px"><button class="btn btn--sm" data-status>상태만 변경</button>
            <button class="btn btn--accent btn--sm" data-answer>답변 저장</button></span></div>`;
      const refresh = () => { close(); loadAdminInquiries(); };
      m.querySelector('[data-answer]').addEventListener('click', async () => {
        const answer = $('#aiq-answer', m).value.trim();
        if (!answer) return toast('답변을 입력하세요', 'err');
        try { await api('PATCH', `/api/admin/inquiries/${id}/answer`, { json: { answer } }); toast('답변을 저장했어요', 'ok'); refresh(); }
        catch (err) { toast(errMsg(err), 'err'); }
      });
      m.querySelector('[data-status]').addEventListener('click', async () => {
        try { await api('PATCH', `/api/admin/inquiries/${id}/status`, { json: { status: $('#aiq-status', m).value } }); toast('상태를 변경했어요', 'ok'); refresh(); }
        catch (err) { toast(errMsg(err), 'err'); }
      });
      m.querySelector('[data-del]').addEventListener('click', async () => {
        try { await api('DELETE', `/api/admin/inquiries/${id}`); toast('삭제했어요', 'ok'); refresh(); }
        catch (err) { toast(errMsg(err), 'err'); }
      });
    } catch (e) {
      m.querySelector('.modal').innerHTML = errBox(e) + '<div class="modal__actions"><button class="btn btn--sm" data-x>닫기</button></div>';
    }
  }

  // ====================================================================
  // 작가 — 팔로워
  // ====================================================================
  async function viewFollowers() {
    setMain(`<div class="page-head"><div><span class="eyebrow">Creator</span><h1>팔로워</h1>
      <p>나를 팔로우하는 독자들</p></div></div>
      <div class="stats-row" id="fl-stats">${loading}</div>
      <div id="fl-list">${loading}</div>`);
    try {
      const [stats, list] = await Promise.all([
        api('GET', `/api/users/${state.user.id}/follow-stats`),
        api('GET', '/api/users/me/followers'),
      ]);
      $('#fl-stats').innerHTML = stat(stats.followerCount, '팔로워') + stat(stats.followingCount, '팔로잉');
      $('#fl-list').innerHTML = list.length
        ? `<div class="rows">${list.map(followerRow).join('')}</div>`
        : emptyBox('아직 팔로워가 없어요', '작품과 소식으로 독자를 모아보세요.');
    } catch (e) { $('#fl-stats').innerHTML = ''; $('#fl-list').innerHTML = errBox(e); }
  }
  function followerRow(f) {
    const av = f.avatarUrl
      ? `<img class="set-avatar" src="${esc(f.avatarUrl)}" alt="">`
      : `<div class="set-avatar set-avatar--empty">${esc((f.nickname || '?').slice(0, 1))}</div>`;
    return `<div class="row">${av}<div class="row__main"><div class="t">${esc(f.nickname)}</div></div></div>`;
  }

  // ====================================================================
  // 관리자 — 작가 신청 관리
  // ====================================================================
  let adminCreqStatus = '';
  let creqCache = [];
  function viewAdminCreatorRequests() {
    const chips = [['', '전체'], ['PENDING', '대기'], ['APPROVED', '승인'], ['REJECTED', '거부']];
    setMain(`<div class="page-head"><div><span class="eyebrow">Admin</span><h1>작가 신청</h1>
      <p>독자의 작가 전환 신청을 검토하고 승인/거부하세요</p></div></div>
      <div class="filters" id="cq-filter">${chips.map(([v, l]) =>
        `<button class="chip" data-v="${v}" ${v === adminCreqStatus ? 'aria-current="true"' : ''}>${l}</button>`).join('')}</div>
      <div id="cq-list">${loading}</div>`);
    $('#cq-filter').addEventListener('click', (e) => {
      const b = e.target.closest('[data-v]'); if (b) { adminCreqStatus = b.dataset.v; viewAdminCreatorRequests(); }
    });
    loadCreatorRequests();
  }
  async function loadCreatorRequests() {
    const list = $('#cq-list'); if (list) list.innerHTML = loading;
    try {
      const q = `size=50${adminCreqStatus ? `&status=${adminCreqStatus}` : ''}`;
      const page = await api('GET', `/api/admin/creator-requests?${q}`);
      creqCache = page.content;
      $('#cq-list').innerHTML = page.content.length
        ? `<div class="rows">${page.content.map(creqRow).join('')}</div>`
        : emptyBox('신청이 없어요', '조건을 바꿔보세요.');
      $('#cq-list').addEventListener('click', onCreqClick);
    } catch (e) { $('#cq-list').innerHTML = errBox(e); }
  }
  function creqStatusTag(s) {
    const cls = s === 'APPROVED' ? 'tag--on' : (s === 'REJECTED' ? 'tag--off' : '');
    return `<span class="tag ${cls}">${CREQ_STATUS[s] || s}</span>`;
  }
  function creqRow(r) {
    const when = r.createdAt ? new Date(r.createdAt).toLocaleDateString('ko-KR') : '';
    return `<div class="row" data-id="${r.id}"><span class="row__no mono">#${r.id}</span>
      <div class="row__main"><div class="t">${esc(r.applicantNickname)}</div><div class="s">${esc(r.applicantEmail)} · ${when}</div></div>
      <div class="row__side">${creqStatusTag(r.status)}<button class="btn btn--sm" data-act="open">상세</button></div></div>`;
  }
  function onCreqClick(e) {
    const row = e.target.closest('.row');
    if (row && e.target.closest('[data-act="open"]')) openCreatorRequest(Number(row.dataset.id));
  }
  function openCreatorRequest(id) {
    const r = creqCache.find((x) => x.id === id);
    if (!r) return;
    const m = document.createElement('div'); m.className = 'modal-bg';
    m.innerHTML = `<div class="modal panel">
      <h2>작가 전환 신청</h2>
      <p class="sub">${esc(r.applicantNickname)} · ${esc(r.applicantEmail)} · ${creqStatusTag(r.status)}</p>
      <div class="set-sec"><h3>신청 사유</h3><div class="iq-body">${esc(r.requestReason)}</div></div>
      <hr class="divider">
      <div class="field"><label for="cq-note">관리자 메모 (선택)</label>
        <input id="cq-note" value="${esc(r.adminNote || '')}" placeholder="승인/거부 사유"></div>
      <div class="modal__actions" style="justify-content:space-between">
        <button class="btn btn--sm" data-x>닫기</button>
        <span style="display:flex;gap:8px">
          <button class="btn btn--ghost btn--sm" data-reject>거부</button>
          <button class="btn btn--accent btn--sm" data-approve>승인 (작가 전환)</button></span></div>
      <p class="hint" style="margin-top:8px;text-align:center">결정은 언제든 정정할 수 있어요 (승인 ↔ 거부)</p></div>`;
    document.body.appendChild(m);
    m.addEventListener('click', (e) => { if (e.target === m || e.target.closest('[data-x]')) m.remove(); });
    const act = async (path) => {
      try {
        await api('PATCH', `/api/admin/creator-requests/${id}/${path}`, { json: { adminNote: $('#cq-note', m).value || null } });
        toast(path === 'approve' ? '승인했어요' : '거부했어요', 'ok'); m.remove(); loadCreatorRequests();
      } catch (err) { toast(errMsg(err), 'err'); }
    };
    m.querySelector('[data-approve]').addEventListener('click', () => act('approve'));
    m.querySelector('[data-reject]').addEventListener('click', () => act('reject'));
  }

  // ====================================================================
  // 관리자 — 신고 관리
  // ====================================================================
  let adminReportStatus = 'PENDING';
  let adminReportType = '';
  let adminReportReason = '';
  function viewAdminReports() {
    const chips = [['', '전체'], ['PENDING', '접수'], ['RESOLVED', '처리'], ['DISMISSED', '기각']];
    setMain(`<div class="page-head"><div><span class="eyebrow">Admin</span><h1>신고 관리</h1>
      <p>신고를 눌러 대상 내용을 확인하고 처리하세요 (게시글·댓글은 신고 5건 시 자동 블라인드)</p></div></div>
      <div class="filters" id="rp-filter">
        ${chips.map(([v, l]) => `<button class="chip" data-v="${v}" ${v === adminReportStatus ? 'aria-current="true"' : ''}>${l}</button>`).join('')}
        <select id="rp-type"><option value="">전체 대상</option>${Object.entries(RTYPE).map(([k, v]) => `<option value="${k}" ${k === adminReportType ? 'selected' : ''}>${v}</option>`).join('')}</select>
        <select id="rp-reason"><option value="">전체 사유</option>${Object.entries(RREASON).map(([k, v]) => `<option value="${k}" ${k === adminReportReason ? 'selected' : ''}>${v}</option>`).join('')}</select>
      </div>
      <div id="rp-list">${loading}</div>`);
    $('#rp-filter').addEventListener('click', (e) => { const b = e.target.closest('[data-v]'); if (b) { adminReportStatus = b.dataset.v; viewAdminReports(); } });
    $('#rp-type').addEventListener('change', (e) => { adminReportType = e.target.value; loadReports(); });
    $('#rp-reason').addEventListener('change', (e) => { adminReportReason = e.target.value; loadReports(); });
    loadReports();
  }
  async function loadReports() {
    const list = $('#rp-list'); if (list) list.innerHTML = loading;
    try {
      const q = `size=50${adminReportStatus ? `&status=${adminReportStatus}` : ''}${adminReportType ? `&targetType=${adminReportType}` : ''}${adminReportReason ? `&reason=${adminReportReason}` : ''}`;
      const page = await api('GET', `/api/admin/reports?${q}`);
      $('#rp-list').innerHTML = page.content.length
        ? `<div class="rows">${page.content.map(reportRow).join('')}</div>`
        : emptyBox('신고가 없어요', '조건을 바꿔보세요.');
      $('#rp-list').addEventListener('click', onReportClick);
    } catch (e) { $('#rp-list').innerHTML = errBox(e); }
  }
  function rStatusTag(s) { const cls = s === 'RESOLVED' ? 'tag--on' : (s === 'DISMISSED' ? 'tag--off' : ''); return `<span class="tag ${cls}">${RSTATUS[s] || s}</span>`; }
  function reportRow(r) {
    const when = r.createdAt ? new Date(r.createdAt).toLocaleDateString('ko-KR') : '';
    return `<div class="row" data-id="${r.id}"><span class="row__no mono">#${r.id}</span>
      <div class="row__main"><div class="t">${RTYPE[r.targetType] || r.targetType} #${r.targetId} · ${RREASON[r.reason] || r.reason}</div>
        <div class="s">신고자 ${esc(r.reporterNickname)}${r.detail ? ' · ' + esc(r.detail) : ''} · ${when}</div></div>
      <div class="row__side">${rStatusTag(r.status)}<button class="btn btn--sm" data-act="open">상세</button></div></div>`;
  }
  function onReportClick(e) { const row = e.target.closest('.row'); if (row && e.target.closest('[data-act="open"]')) openReport(Number(row.dataset.id)); }
  async function openReport(id) {
    const m = document.createElement('div'); m.className = 'modal-bg'; m.innerHTML = `<div class="modal panel">${loading}</div>`;
    document.body.appendChild(m);
    m.addEventListener('click', (e) => { if (e.target === m || e.target.closest('[data-x]')) m.remove(); });
    try {
      const r = await api('GET', `/api/admin/reports/${id}`);
      const canAct = r.targetType === 'POST' || r.targetType === 'COMMENT';
      m.querySelector('.modal').innerHTML = `
        <h2>신고 상세</h2>
        <p class="sub">${RTYPE[r.targetType] || r.targetType} #${r.targetId} · ${RREASON[r.reason] || r.reason} · ${rStatusTag(r.status)}</p>
        <div class="set-sec"><h3>신고 대상 — ${esc(r.targetAuthorNickname || '-')}</h3><div class="iq-body">${esc(r.targetContent)}</div></div>
        ${r.detail ? `<div class="set-sec"><h3>신고 사유 상세</h3><div class="iq-body">${esc(r.detail)}</div></div>` : ''}
        <p class="hint">신고자 ${esc(r.reporterNickname)} · 같은 대상 신고 ${r.relatedReportCount}건</p>
        <hr class="divider">
        <div class="modal__actions" style="justify-content:space-between;flex-wrap:wrap;gap:8px">
          <button class="btn btn--ghost btn--sm" data-dismiss>기각</button>
          <span style="display:flex;gap:8px;flex-wrap:wrap">
            <button class="btn btn--sm" data-resolve="NONE">처리(유지)</button>
            ${canAct ? `<button class="btn btn--sm" data-resolve="BLIND_TARGET">블라인드</button>
              <button class="btn btn--accent btn--sm" data-resolve="DELETE_TARGET">대상 삭제</button>` : ''}
          </span></div>`;
      const act = async (fn, msg) => { try { await fn(); toast(msg, 'ok'); m.remove(); loadReports(); } catch (err) { toast(errMsg(err), 'err'); } };
      m.querySelectorAll('[data-resolve]').forEach((b) => b.addEventListener('click', () =>
        act(() => api('PATCH', `/api/admin/reports/${id}/resolve`, { json: { action: b.dataset.resolve } }), '처리했어요')));
      m.querySelector('[data-dismiss]').addEventListener('click', () =>
        act(() => api('PATCH', `/api/admin/reports/${id}/dismiss`, { json: {} }), '기각했어요'));
    } catch (e) { m.querySelector('.modal').innerHTML = errBox(e) + '<div class="modal__actions"><button class="btn btn--sm" data-x>닫기</button></div>'; }
  }

  // ====================================================================
  // 관리자 — 커뮤니티 관리
  // ====================================================================
  let adminPostKw = '';
  let adminPostCategory = '';
  let adminPostBlinded = '';
  function viewAdminCommunity() {
    setMain(`<div class="page-head"><div><span class="eyebrow">Admin</span><h1>커뮤니티 관리</h1>
      <p>블라인드 포함 전체 게시글. 검색·필터 후 글을 눌러 확인하세요</p></div></div>
      <form class="filters" id="pc-filter">
        <input class="search-input" id="pc-kw" placeholder="제목 검색" autocomplete="off" value="${esc(adminPostKw)}">
        <select id="pc-cat"><option value="">전체 종류</option>${Object.entries(PCAT).map(([k, v]) => `<option value="${k}" ${k === adminPostCategory ? 'selected' : ''}>${v}</option>`).join('')}</select>
        <select id="pc-blind"><option value="">전체</option><option value="false" ${adminPostBlinded === 'false' ? 'selected' : ''}>공개</option><option value="true" ${adminPostBlinded === 'true' ? 'selected' : ''}>블라인드</option></select>
        <button class="btn btn--sm" type="submit">검색</button>
      </form>
      <div id="pc-list">${loading}</div>`);
    $('#pc-filter').addEventListener('submit', (e) => { e.preventDefault(); adminPostKw = $('#pc-kw').value.trim(); adminPostCategory = $('#pc-cat').value; adminPostBlinded = $('#pc-blind').value; loadAdminPosts(); });
    loadAdminPosts();
  }
  async function loadAdminPosts() {
    const list = $('#pc-list'); if (list) list.innerHTML = loading;
    try {
      const q = `size=50${adminPostKw ? `&keyword=${encodeURIComponent(adminPostKw)}` : ''}${adminPostCategory ? `&category=${adminPostCategory}` : ''}${adminPostBlinded ? `&blinded=${adminPostBlinded}` : ''}`;
      const page = await api('GET', `/api/admin/posts?${q}`);
      $('#pc-list').innerHTML = page.content.length
        ? `<div class="rows">${page.content.map(adminPostRow).join('')}</div>`
        : emptyBox('게시글이 없어요', '검색 조건을 바꿔보세요.');
      $('#pc-list').addEventListener('click', onAdminPostClick);
    } catch (e) { $('#pc-list').innerHTML = errBox(e); }
  }
  function adminPostRow(p) {
    const when = p.createdAt ? new Date(p.createdAt).toLocaleDateString('ko-KR') : '';
    return `<div class="row" data-id="${p.id}" ${p.blinded ? 'style="opacity:.6"' : ''}><span class="row__no mono">#${p.id}</span>
      <div class="row__main"><div class="t">${esc(p.title)} ${p.blinded ? '<span class="tag tag--19">블라인드</span>' : ''}</div>
        <div class="s">${PCAT[p.category] || p.category} · ${esc(p.authorNickname)} · ♡${p.likeCount} · ${when}</div></div>
      <div class="row__side">
        <button class="btn btn--sm" data-act="open">상세</button>
        ${p.blinded ? `<button class="btn btn--ghost btn--sm" data-act="unblind">해제</button>`
          : `<button class="btn btn--ghost btn--sm" data-act="blind">블라인드</button>`}
        <button class="btn btn--ghost btn--sm" data-act="delete">삭제</button></div></div>`;
  }
  async function onAdminPostClick(e) {
    const btn = e.target.closest('[data-act]'); if (!btn) return;
    const row = e.target.closest('.row'); const id = row.dataset.id, act = btn.dataset.act;
    if (act === 'open') return openAdminPost(Number(id));
    try {
      if (act === 'delete') { await api('DELETE', `/api/posts/${id}`); toast('삭제했어요', 'ok'); }
      else { await api('PATCH', `/api/admin/posts/${id}/${act}`); toast(act === 'blind' ? '블라인드했어요' : '해제했어요', 'ok'); }
      loadAdminPosts();
    } catch (err) { toast(errMsg(err), 'err'); }
  }
  async function openAdminPost(id) {
    const m = document.createElement('div'); m.className = 'modal-bg'; m.innerHTML = `<div class="modal panel">${loading}</div>`;
    document.body.appendChild(m);
    m.addEventListener('click', (e) => { if (e.target === m || e.target.closest('[data-x]')) m.remove(); });
    try {
      const p = await api('GET', `/api/admin/posts/${id}`);
      m.querySelector('.modal').innerHTML = `
        <h2>${esc(p.title)}</h2>
        <div class="card__meta" style="margin-bottom:14px"><span class="tag">${PCAT[p.category] || p.category}</span>
          <span class="tag">♡ ${p.likeCount}</span>${p.blinded ? '<span class="tag tag--19">블라인드</span>' : ''}</div>
        <p class="sub">${esc(p.authorNickname)}</p>
        <div class="iq-body">${esc(p.content)}</div>
        ${imageGallery(p.images)}
        <div class="modal__actions"><button class="btn btn--sm" data-x>닫기</button></div>`;
    } catch (e) { m.querySelector('.modal').innerHTML = errBox(e) + '<div class="modal__actions"><button class="btn btn--sm" data-x>닫기</button></div>'; }
  }

  // ---- helpers ----
  function stat(n, label) { return `<div class="panel"><div class="stat"><span class="n mono">${n}</span><span class="l">${label}</span></div></div>`; }
  function emptyBox(title, sub) { return `<div class="empty"><b>${esc(title)}</b>${esc(sub)}</div>`; }
  function errBox(e) { return `<div class="empty"><b>불러오지 못했어요</b>${esc(errMsg(e))}</div>`; }
  function bindGo() { app.querySelectorAll('[data-go]').forEach((b) => b.addEventListener('click', () => { state.view = b.dataset.go; route(); })); }

  // ====================================================================
  // 부트스트랩
  // ====================================================================
  function boot() { route(); startUnreadPoll(); }

  async function init() {
    applyTheme();
    if (!state.token) return renderLogin();
    try {
      state.user = await api('GET', '/api/users/me');
      if (state.user.role === 'READER') { clearTokens(); return renderLogin(); }
      boot();
    } catch { clearTokens(); renderLogin(); }
  }

  init();
})();
