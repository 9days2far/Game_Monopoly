(() => {
  'use strict';

  const STORAGE_KEY = 'monopoly_online_session_v1';
  const NAME_KEY = 'monopoly_online_name_v1';

  const appState = {
    roomCode: null,
    token: null,
    role: null,
    playerId: null,
    snapshot: null,
    eventSource: null,
    roomsTimer: null,
    lastRollSeq: -1,
    lastCardSeq: -1,
    lastBuyPromptKey: '',
    lastGameOverPrompted: false,
    selectedSquarePos: null,
    cellsCreated: false,
    cellNodes: [],
  };

  // =========================================================================
  // DOM REFERENCES & HELPERS
  // =========================================================================
  const $ = (id) => document.getElementById(id);

  function esc(str) {
    return String(str ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function showToast(msg, type = 'info') {
    const container = $('toast-container');
    const div = document.createElement('div');
    div.className = `toast ${type}`;
    div.textContent = msg;
    container.appendChild(div);
    setTimeout(() => {
      div.remove();
    }, 3600);
  }

  async function apiRequest(url, method = 'GET', body = null) {
    const opts = {
      method,
      headers: {},
    };
    if (body !== null) {
      opts.headers['Content-Type'] = 'application/json; charset=UTF-8';
      opts.body = JSON.stringify(body);
    }
    const res = await fetch(url, opts);
    let data = {};
    try {
      data = await res.json();
    } catch (_) {
      data = {};
    }
    if (!res.ok) {
      throw new Error(data.error || `Lỗi HTTP ${res.status}`);
    }
    return data;
  }

  function saveSession() {
    if (appState.roomCode && appState.token) {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
          roomCode: appState.roomCode,
          token: appState.token,
          role: appState.role,
          playerId: appState.playerId,
        })
      );
    }
  }

  function clearSession() {
    localStorage.removeItem(STORAGE_KEY);
  }

  function loadSession() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch (_) {
      return null;
    }
  }

  function getPlayerNameInput() {
    const val = $('input-name').value.trim();
    if (val) {
      localStorage.setItem(NAME_KEY, val);
      return val;
    }
    return 'Người chơi';
  }

  function openModal(id) {
    $(id).classList.remove('hidden');
  }

  function closeModal(id) {
    $(id).classList.add('hidden');
  }

  function closeAllModals() {
    document.querySelectorAll('.modal-backdrop').forEach((el) => el.classList.add('hidden'));
  }

  function showView(viewId) {
    ['view-landing', 'view-waiting', 'view-board'].forEach((id) => {
      $(id).classList.toggle('hidden', id !== viewId);
    });
    if (viewId === 'view-landing') {
      startPublicRoomsPolling();
    } else {
      stopPublicRoomsPolling();
    }
  }

  function copyRoomLink(isWatch) {
    if (!appState.roomCode) return;
    const url = new URL(window.location.href);
    url.search = '';
    url.searchParams.set('room', appState.roomCode);
    if (isWatch) url.searchParams.set('watch', '1');
    const text = url.toString();
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(
        () => showToast(isWatch ? 'Đã copy link XEM TRỰC TIẾP!' : 'Đã copy link MỜI CHƠI!', 'success'),
        () => window.prompt('Copy đường dẫn bên dưới:', text)
      );
    } else {
      window.prompt('Copy đường dẫn bên dưới:', text);
    }
  }

  // =========================================================================
  // LOBBY & ROOM CONNECTION
  // =========================================================================
  async function loadPublicRooms() {
    try {
      const data = await apiRequest('/api/rooms');
      renderPublicRooms(data.rooms || []);
    } catch (_) {
      // Bỏ qua lỗi tạm thời khi refresh danh sách phòng
    }
  }

  function startPublicRoomsPolling() {
    loadPublicRooms();
    stopPublicRoomsPolling();
    appState.roomsTimer = setInterval(loadPublicRooms, 4000);
  }

  function stopPublicRoomsPolling() {
    if (appState.roomsTimer) {
      clearInterval(appState.roomsTimer);
      appState.roomsTimer = null;
    }
  }

  function renderPublicRooms(rooms) {
    const container = $('public-rooms-list');
    if (!rooms.length) {
      container.innerHTML =
        '<div class="empty-state">Hiện chưa có phòng công khai nào. Hãy tạo phòng mới ở bên trên để bắt đầu!</div>';
      return;
    }
    container.innerHTML = rooms
      .map((r) => {
        const badgeClass =
          r.phase === 'LOBBY' ? 'badge-lobby' : r.phase === 'IN_GAME' ? 'badge-playing' : 'badge-over';
        const phaseLabel =
          r.phase === 'LOBBY' ? 'Đang chờ' : r.phase === 'IN_GAME' ? `Đang đấu (Lượt ${r.turnNumber})` : 'Đã kết thúc';
        const canJoinPlay = r.phase === 'LOBBY' && r.playerCount < r.maxPlayers;
        return `
          <div class="room-item">
            <div class="room-item-top">
              <span class="room-item-title">${esc(r.roomName)}</span>
              <span class="badge ${badgeClass}">${phaseLabel}</span>
            </div>
            <div class="room-item-meta">
              <span>Mã: <strong>${esc(r.roomCode)}</strong></span>
              <span>👥 ${r.playerCount}/${r.maxPlayers}</span>
              <span>👁️ ${r.spectatorCount} đang xem</span>
              <span>💰 $${r.startingBalance}</span>
            </div>
            <div class="room-item-actions">
              ${
                canJoinPlay
                  ? `<button class="btn btn-success btn-sm" data-quick-join="${esc(r.roomCode)}" data-role="PLAYER">Vào chơi</button>`
                  : ''
              }
              <button class="btn btn-secondary btn-sm" data-quick-join="${esc(r.roomCode)}" data-role="SPECTATOR">👁️ Xem trực tiếp</button>
            </div>
          </div>
        `;
      })
      .join('');

    container.querySelectorAll('[data-quick-join]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const code = btn.getAttribute('data-quick-join');
        const role = btn.getAttribute('data-role');
        joinRoom(code, role, null);
      });
    });
  }

  async function createRoom() {
    try {
      const hostName = getPlayerNameInput();
      const roomName = $('input-room-name').value.trim();
      const maxPlayers = parseInt($('input-max-players').value, 10) || 4;
      const startingBalance = parseInt($('input-starting-balance').value, 10) || 1500;
      const isPublic = $('input-public').checked;

      const res = await apiRequest('/api/rooms/create', 'POST', {
        hostName,
        roomName,
        maxPlayers,
        startingBalance,
        isPublic,
      });
      enterRoomSession(res);
      showToast(`Đã tạo phòng ${res.roomCode}!`, 'success');
    } catch (err) {
      showToast(err.message, 'error');
    }
  }

  async function joinRoom(rawCode, role, reconnectToken = null) {
    const code = String(rawCode || '').trim().toUpperCase();
    if (!code) {
      showToast('Vui lòng nhập mã phòng.', 'error');
      return;
    }
    try {
      const name = role === 'SPECTATOR' && !$('input-name').value.trim()
        ? 'Khán giả'
        : getPlayerNameInput();
      const res = await apiRequest(`/api/rooms/${encodeURIComponent(code)}/join`, 'POST', {
        name,
        role,
        token: reconnectToken,
      });
      enterRoomSession(res);
    } catch (err) {
      if (reconnectToken) {
        clearSession();
      }
      if (!appState.roomCode) {
        showView('view-landing');
      }
      showToast(err.message, 'error');
    }
  }

  function enterRoomSession(res) {
    appState.roomCode = res.roomCode;
    appState.token = res.token;
    appState.role = res.role;
    appState.playerId = res.playerId || null;
    appState.lastRollSeq = -1;
    appState.lastCardSeq = -1;
    appState.lastBuyPromptKey = '';
    appState.lastGameOverPrompted = false;
    appState.cellsCreated = false;
    appState.cellNodes = [];
    saveSession();

    const url = new URL(window.location.href);
    url.searchParams.set('room', res.roomCode);
    if (res.role === 'SPECTATOR') {
      url.searchParams.set('watch', '1');
    } else {
      url.searchParams.delete('watch');
    }
    window.history.replaceState({}, '', url.toString());

    connectSse();
    if (res.snapshot) {
      applySnapshot(res.snapshot, true);
    }
  }

  function connectSse() {
    disconnectSse();
    if (!appState.roomCode || !appState.token) return;
    const url = `/api/rooms/${encodeURIComponent(appState.roomCode)}/events?token=${encodeURIComponent(appState.token)}`;
    const es = new EventSource(url);
    es.addEventListener('state', (evt) => {
      try {
        const snap = JSON.parse(evt.data);
        applySnapshot(snap, false);
      } catch (_) {}
    });
    es.onerror = () => {
      // EventSource tự động reconnect nếu server còn sống
    };
    appState.eventSource = es;
  }

  function disconnectSse() {
    if (appState.eventSource) {
      appState.eventSource.close();
      appState.eventSource = null;
    }
  }

  async function leaveCurrentRoom() {
    const code = appState.roomCode;
    const token = appState.token;
    disconnectSse();
    clearSession();
    closeAllModals();

    appState.roomCode = null;
    appState.token = null;
    appState.role = null;
    appState.playerId = null;
    appState.snapshot = null;

    const url = new URL(window.location.href);
    url.search = '';
    window.history.replaceState({}, '', url.toString());
    showView('view-landing');

    if (code && token) {
      try {
        await apiRequest(`/api/rooms/${encodeURIComponent(code)}/leave`, 'POST', { token });
      } catch (_) {}
    }
  }

  async function sendAction(type, extra = {}) {
    if (!appState.roomCode || !appState.token) return;
    try {
      const res = await apiRequest(
        `/api/rooms/${encodeURIComponent(appState.roomCode)}/action`,
        'POST',
        { token: appState.token, type, ...extra }
      );
      if (res.snapshot) {
        applySnapshot(res.snapshot, false);
      }
    } catch (err) {
      showToast(err.message, 'error');
    }
  }

  async function sendChat(inputEl) {
    const message = inputEl.value.trim();
    if (!message || !appState.roomCode || !appState.token) return;
    inputEl.value = '';
    try {
      await apiRequest(`/api/rooms/${encodeURIComponent(appState.roomCode)}/chat`, 'POST', {
        token: appState.token,
        message,
      });
    } catch (err) {
      showToast(err.message, 'error');
    }
  }

  // =========================================================================
  // STATE APPLICATION & RENDERING
  // =========================================================================
  function applySnapshot(snap, isInitial) {
    appState.snapshot = snap;
    if (snap.me) {
      appState.role = snap.me.role;
      appState.playerId = snap.me.playerId || null;
    }

    if (snap.phase === 'LOBBY') {
      closeAllModals();
      showView('view-waiting');
      renderWaitingRoom(snap);
      return;
    }

    showView('view-board');
    renderLiveBoard(snap, isInitial);
  }

  function renderWaitingRoom(snap) {
    $('wait-room-code').textContent = snap.roomCode;
    $('wait-room-name').textContent = snap.roomName;
    $('wait-player-count').textContent = `${snap.players.length}/${snap.maxPlayers}`;
    $('wait-starting-balance').textContent = `$${snap.startingBalance.toLocaleString()}`;

    const isSpec = snap.me && snap.me.role === 'SPECTATOR';
    const isHost = snap.me && snap.me.isHost;
    const badge = $('wait-role-badge');
    badge.textContent = isSpec ? '👁️ Đang xem' : isHost ? '👑 Chủ phòng' : '🎮 Người chơi';
    badge.classList.toggle('is-spectator', isSpec);

    $('wait-seats-list').innerHTML = snap.players
      .map((p) => {
        const tags = [];
        if (p.isHost) tags.push('<span class="tag tag-host">Chủ phòng</span>');
        if (p.isBot) tags.push('<span class="tag tag-bot">Bot AI</span>');
        if (snap.me && p.id === snap.me.playerId) tags.push('<span class="tag tag-me">Bạn</span>');
        if (!p.connected && !p.isBot) tags.push('<span class="tag tag-offline">Mất kết nối</span>');
        return `
          <div class="seat-card" style="border-left: 4px solid ${esc(p.colorHex)}">
            <img src="/${esc(p.avatarUrl)}" alt="${esc(p.name)}" />
            <div class="seat-meta">
              <div class="seat-name">${esc(p.name)}</div>
              <div class="seat-tags">${tags.join('')}</div>
            </div>
            ${
              isHost && p.isBot
                ? `<button class="btn btn-danger btn-sm" data-remove-bot="${esc(p.id)}">✕</button>`
                : ''
            }
          </div>
        `;
      })
      .join('');

    $('wait-seats-list')
      .querySelectorAll('[data-remove-bot]')
      .forEach((btn) => {
        btn.addEventListener('click', () => {
          sendAction('REMOVE_BOT', { playerId: btn.getAttribute('data-remove-bot') });
        });
      });

    $('wait-host-controls').classList.toggle('hidden', !isHost);
    $('wait-nonhost-note').classList.toggle('hidden', isHost);
    $('btn-add-bot').disabled = snap.players.length >= snap.maxPlayers;
    $('btn-start-game').disabled = snap.players.length < 2;

    $('wait-spec-count').textContent = String(snap.spectatorCount || 0);
    $('wait-spec-names').textContent =
      snap.spectators && snap.spectators.length ? snap.spectators.join(', ') : 'Chưa có khán giả';

    renderChatMessages($('wait-chat-list'), snap.chats || []);
  }

  function renderLiveBoard(snap, isInitial) {
    const isSpec = snap.me && snap.me.role === 'SPECTATOR';
    const isHost = snap.me && snap.me.isHost;
    const myId = snap.me ? snap.me.playerId : null;
    const myPlayer = snap.players.find((p) => p.id === myId) || null;
    const activePlayer = snap.players.find((p) => p.id === snap.currentPlayerId) || null;
    const isMyTurn = Boolean(myId && snap.currentPlayerId === myId && snap.phase === 'IN_GAME');

    $('live-room-code').textContent = snap.roomCode;
    $('live-turn-badge').textContent =
      snap.phase === 'GAME_OVER' ? 'Đã kết thúc' : `Lượt #${snap.turnNumber}`;
    $('live-spec-badge').textContent = `👁️ ${snap.spectatorCount || 0} người xem`;
    $('live-spec-count-2').textContent = String(snap.spectatorCount || 0);
    $('live-spec-names').textContent =
      snap.spectators && snap.spectators.length ? snap.spectators.join(', ') : 'Không có';

    const roleBanner = $('live-role-banner');
    roleBanner.textContent = isSpec
      ? '👁️ ĐANG XEM TRỰC TIẾP'
      : myPlayer
        ? `🎮 Bạn là: ${myPlayer.name}`
        : 'Khách';
    roleBanner.classList.toggle('is-spectator', isSpec);

    $('btn-surrender').classList.toggle(
      'hidden',
      !(myPlayer && !myPlayer.bankrupt && snap.phase === 'IN_GAME')
    );

    // 1. Render cột người chơi
    $('live-players-list').innerHTML = snap.players
      .map((p) => {
        const isCurrent = p.id === snap.currentPlayerId && snap.phase === 'IN_GAME';
        const tags = [];
        if (p.id === myId) tags.push('<span class="tag tag-me">Bạn</span>');
        if (p.isBot) tags.push('<span class="tag tag-bot">Bot</span>');
        if (p.inJail && !p.bankrupt) tags.push('<span class="tag tag-jail">Ở tù</span>');
        if (p.bankrupt) tags.push('<span class="tag tag-offline">Phá sản</span>');
        if (!p.connected && !p.isBot && !p.bankrupt)
          tags.push('<span class="tag tag-offline">Offline</span>');

        return `
          <div class="player-card ${isCurrent ? 'active-turn' : ''} ${p.bankrupt ? 'is-bankrupt' : ''}"
               style="border-left: 4px solid ${esc(p.colorHex)}">
            <div class="player-card-head">
              <img src="/${esc(p.avatarUrl)}" alt="${esc(p.name)}" class="player-card-avatar"
                   style="border-color:${esc(p.colorHex)}" />
              <div class="player-card-title">
                <div class="player-card-name">${esc(p.name)}</div>
                <div class="seat-tags">${tags.join('')}</div>
              </div>
            </div>
            <div class="player-card-stats">
              <div class="stat-cash">💵 $${p.balance.toLocaleString()}</div>
              <div>💎 $${p.totalAssetValue.toLocaleString()}</div>
              <div>🏠 ${p.assetCount} tài sản</div>
              <div>🎫 ${p.outOfJailTickets} thẻ tù</div>
            </div>
          </div>
        `;
      })
      .join('');

    // 2. Render bàn cờ 40 ô
    ensureBoardCellsCreated();
    updateBoardCells(snap);

    // 3. Center HUD
    $('center-turn-owner').textContent =
      snap.phase === 'GAME_OVER'
        ? 'Ván đấu đã kết thúc'
        : activePlayer
          ? `Lượt của ${activePlayer.name}`
          : 'Đang chờ';
    $('center-turn-state').textContent = translateTurnState(snap.phase, snap.turnState);
    $('center-banner').textContent = snap.lastBanner || '';

    // Xúc xắc
    const d1 = snap.dice && snap.dice.die1 > 0 ? snap.dice.die1 : 1;
    const d2 = snap.dice && snap.dice.die2 > 0 ? snap.dice.die2 : 1;
    $('dice-img-1').src = `/assets/dice/dice_${d1}.png`;
    $('dice-img-2').src = `/assets/dice/dice_${d2}.png`;
    const totalBadge = $('dice-total-badge');
    if (snap.dice && snap.dice.total > 0) {
      totalBadge.classList.remove('hidden');
      totalBadge.textContent = snap.dice.isDouble ? `${snap.dice.total} (ĐÔI)` : `${snap.dice.total}`;
    } else {
      totalBadge.classList.add('hidden');
    }

    if (snap.rollSeq !== appState.lastRollSeq) {
      const shouldAnimate = appState.lastRollSeq !== -1 && snap.rollSeq > appState.lastRollSeq;
      appState.lastRollSeq = snap.rollSeq;
      if (shouldAnimate) {
        const box = $('dice-box');
        box.classList.remove('rolling');
        void box.offsetWidth;
        box.classList.add('rolling');
      }
    }

    // Lá bài vừa rút
    const cardBox = $('center-last-card');
    if (snap.lastCard) {
      cardBox.classList.remove('hidden');
      $('center-last-card-img').src = `/${snap.lastCard.imagePath}`;
      $('center-last-card-type').textContent = `${snap.lastCard.cardTypeLabel} • ${snap.lastCard.drawnByName}`;
      $('center-last-card-title').textContent = snap.lastCard.title;
      $('center-last-card-desc').textContent = snap.lastCard.description;
    } else {
      cardBox.classList.add('hidden');
    }

    if (snap.cardSeq !== appState.lastCardSeq) {
      const shouldShowModal = !isInitial && appState.lastCardSeq !== -1 && snap.cardSeq > appState.lastCardSeq;
      appState.lastCardSeq = snap.cardSeq;
      if (shouldShowModal && snap.lastCard) {
        openCardRevealModal(snap.lastCard);
      }
    }

    // Nút điều khiển lượt
    renderCenterActions(snap, myPlayer, isMyTurn, isSpec);

    // 4. Nhật ký & Chat
    renderLogs($('live-logs-list'), snap.logs || []);
    renderChatMessages($('live-chat-list'), snap.chats || []);
    const chatCount = (snap.chats || []).length;
    $('chat-count-pill').textContent = chatCount > 0 ? `(${chatCount})` : '';

    // 5. Tự động bật modal mua đất / trade / game over theo trạng thái
    handleAutoModals(snap, myPlayer, isMyTurn);

    // Nếu modal Chi tiết ô hoặc Quản lý tài sản đang mở, cập nhật luôn dữ liệu mới nhất
    if (!$('modal-square').classList.contains('hidden') && appState.selectedSquarePos !== null) {
      renderSquareDetailModal(appState.selectedSquarePos);
    }
    if (!$('modal-assets').classList.contains('hidden')) {
      renderMyAssetsModal();
    }
  }

  function translateTurnState(phase, turnState) {
    if (phase === 'GAME_OVER') return 'KẾT THÚC';
    switch (turnState) {
      case 'WAIT_JAIL_CHOICE':
        return 'Đang ở tù';
      case 'WAIT_ROLL':
        return 'Chờ đổ xúc xắc';
      case 'WAIT_BUY_DECISION':
        return 'Chờ quyết định mua';
      case 'WAIT_TRADE_RESPONSE':
        return 'Đang thương lượng';
      case 'WAIT_END_TURN':
        return 'Hành động cuối lượt';
      default:
        return turnState;
    }
  }

  function renderCenterActions(snap, myPlayer, isMyTurn, isSpec) {
    const container = $('center-actions');
    const specNote = $('center-spectator-note');
    specNote.classList.toggle('hidden', !isSpec);

    if (snap.phase === 'GAME_OVER') {
      container.innerHTML = `<button class="btn btn-primary btn-sm" id="btn-open-gameover">🏆 Xem Kết Quả</button>`;
      $('btn-open-gameover').addEventListener('click', () => openGameOverModal(snap));
      return;
    }

    if (!isMyTurn || !myPlayer || myPlayer.bankrupt) {
      container.innerHTML = isSpec
        ? ''
        : `<span class="muted">Đang chờ lượt của đối thủ...</span>`;
      return;
    }

    const buttons = [];
    if (snap.turnState === 'WAIT_JAIL_CHOICE') {
      buttons.push(
        `<button class="btn btn-warning btn-sm" data-act="JAIL_PAY_BAIL" ${myPlayer.balance < 50 ? 'disabled' : ''}>💸 Nộp bảo lãnh ($50)</button>`
      );
      if (myPlayer.outOfJailTickets > 0) {
        buttons.push(`<button class="btn btn-success btn-sm" data-act="JAIL_USE_CARD">🎫 Dùng thẻ ra tù (${myPlayer.outOfJailTickets})</button>`);
      }
      buttons.push(`<button class="btn btn-primary btn-sm" data-act="JAIL_ROLL">🎲 Thử đổ đôi</button>`);
    } else if (snap.turnState === 'WAIT_ROLL') {
      const label = snap.dice && snap.dice.isDouble && snap.dice.consecutiveDoubles > 0
        ? '🎲 Đổ Tiếp (Đôi!)'
        : '🎲 Đổ Xúc Xắc';
      buttons.push(`<button class="btn btn-primary" data-act="ROLL_DICE">${label}</button>`);
    } else if (snap.turnState === 'WAIT_BUY_DECISION') {
      buttons.push(`<button class="btn btn-success btn-sm" id="btn-reopen-buy">🏷️ Mở Bảng Mua Đất</button>`);
    } else if (snap.turnState === 'WAIT_END_TURN') {
      buttons.push(`<button class="btn btn-success" data-act="END_TURN">✅ Kết Thúc Lượt</button>`);
    }

    if (['WAIT_ROLL', 'WAIT_END_TURN', 'WAIT_JAIL_CHOICE'].includes(snap.turnState)) {
      buttons.push(`<button class="btn btn-secondary btn-sm" id="btn-open-assets">🏠 Tài Sản (${myPlayer.assetCount})</button>`);
      const hasPartner = snap.players.some((p) => p.id !== myPlayer.id && !p.bankrupt);
      if (hasPartner) {
        buttons.push(`<button class="btn btn-secondary btn-sm" id="btn-open-trade">🤝 Giao Dịch</button>`);
      }
    }

    container.innerHTML = buttons.join('');
    container.querySelectorAll('[data-act]').forEach((btn) => {
      btn.addEventListener('click', () => sendAction(btn.getAttribute('data-act')));
    });
    const reopenBuy = $('btn-reopen-buy');
    if (reopenBuy) reopenBuy.addEventListener('click', () => openBuyModal(snap));
    const openAssets = $('btn-open-assets');
    if (openAssets) openAssets.addEventListener('click', () => openMyAssetsModal());
    const openTrade = $('btn-open-trade');
    if (openTrade) openTrade.addEventListener('click', () => openTradeBuilderModal());
  }

  // =========================================================================
  // 40-CELL GRID BOARD RENDERING
  // =========================================================================
  function getGridCoords(pos) {
    if (pos === 0) return { col: 11, row: 11, side: 'corner' };
    if (pos >= 1 && pos <= 9) return { col: 11 - pos, row: 11, side: 'side-bottom' };
    if (pos === 10) return { col: 1, row: 11, side: 'corner' };
    if (pos >= 11 && pos <= 19) return { col: 1, row: 21 - pos, side: 'side-left' };
    if (pos === 20) return { col: 1, row: 1, side: 'corner' };
    if (pos >= 21 && pos <= 29) return { col: pos - 19, row: 1, side: 'side-top' };
    if (pos === 30) return { col: 11, row: 1, side: 'corner' };
    return { col: 11, row: pos - 29, side: 'side-right' };
  }

  function ensureBoardCellsCreated() {
    if (appState.cellsCreated) return;
    const grid = $('board-grid');
    grid.querySelectorAll('.sq-cell').forEach((el) => el.remove());
    appState.cellNodes = [];
    for (let pos = 0; pos < 40; pos++) {
      const { col, row, side } = getGridCoords(pos);
      const cell = document.createElement('div');
      cell.className = `sq-cell ${side}`;
      cell.style.gridColumn = String(col);
      cell.style.gridRow = String(row);
      cell.title = `Ô #${pos}`;

      cell.innerHTML = `
        <img class="sq-img" alt="Ô ${pos}" loading="lazy" />
        <div class="sq-owner-bar hidden"></div>
        <div class="sq-houses hidden"></div>
        <div class="sq-mortgage-tag hidden">Thế chấp</div>
        <div class="sq-tokens"></div>
      `;
      cell.addEventListener('click', () => {
        appState.selectedSquarePos = pos;
        renderSquareDetailModal(pos);
        openModal('modal-square');
      });

      grid.appendChild(cell);
      appState.cellNodes.push(cell);
    }
    appState.cellsCreated = true;
  }

  function updateBoardCells(snap) {
    const squares = snap.squares || [];
    if (squares.length < 40) return;

    const tokensByPos = Array.from({ length: 40 }, () => []);
    for (const p of snap.players) {
      if (!p.bankrupt && p.position >= 0 && p.position < 40) {
        tokensByPos[p.position].push(p);
      }
    }

    for (let pos = 0; pos < 40; pos++) {
      const sq = squares[pos];
      const cell = appState.cellNodes[pos];
      if (!cell || !sq) continue;

      const imgEl = cell.querySelector('.sq-img');
      if (imgEl.getAttribute('src') !== `/${sq.cellImg}`) {
        imgEl.setAttribute('src', `/${sq.cellImg}`);
        imgEl.setAttribute('alt', sq.name);
        cell.title = `#${pos} ${sq.name}`;
      }

      const ownerBar = cell.querySelector('.sq-owner-bar');
      if (sq.ownerColor) {
        ownerBar.classList.remove('hidden');
        ownerBar.style.backgroundColor = sq.ownerColor;
      } else {
        ownerBar.classList.add('hidden');
      }

      const housesEl = cell.querySelector('.sq-houses');
      const level = sq.houseLevel || 0;
      if (level > 0) {
        housesEl.classList.remove('hidden');
        if (level === 5) {
          housesEl.innerHTML = '<img src="/assets/square/hotel.png" class="sq-house-icon" alt="Khách sạn" />';
        } else {
          housesEl.innerHTML = '<img src="/assets/square/house.png" class="sq-house-icon" alt="Nhà" />'.repeat(level);
        }
      } else {
        housesEl.classList.add('hidden');
        housesEl.innerHTML = '';
      }

      const mortEl = cell.querySelector('.sq-mortgage-tag');
      mortEl.classList.toggle('hidden', !sq.mortgaged);

      const tokensEl = cell.querySelector('.sq-tokens');
      const here = tokensByPos[pos];
      if (here.length > 0) {
        tokensEl.innerHTML = here
          .map((p) => {
            const isActive = p.id === snap.currentPlayerId && snap.phase === 'IN_GAME';
            return `<img src="/${esc(p.avatarUrl)}" class="token-chip ${isActive ? 'active-token' : ''}" style="border-color:${esc(p.colorHex)}" title="${esc(p.name)}" />`;
          })
          .join('');
      } else {
        tokensEl.innerHTML = '';
      }

      cell.classList.toggle('is-highlighted', snap.lastMovedPlayerId && here.some((p) => p.id === snap.lastMovedPlayerId));
    }
  }

  // =========================================================================
  // LOGS & CHAT RENDERERS
  // =========================================================================
  function renderLogs(el, logs) {
    el.innerHTML = logs.map((line) => `<div class="log-entry">${esc(line)}</div>`).join('');
  }

  function renderChatMessages(el, chats) {
    const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 40;
    el.innerHTML = chats
      .map((c) => {
        const badge = c.role === 'SPECTATOR' ? '<span class="tag">👁️ Khán giả</span>' : '';
        return `
          <div class="chat-msg">
            <div class="chat-msg-head">
              <strong style="color:${esc(c.colorHex || '#94a3b8')}">${esc(c.sender)} ${badge}</strong>
              <span class="muted">${esc(c.time)}</span>
            </div>
            <div>${esc(c.message)}</div>
          </div>
        `;
      })
      .join('');
    if (atBottom) {
      el.scrollTop = el.scrollHeight;
    }
  }

  // =========================================================================
  // MODALS
  // =========================================================================
  function handleAutoModals(snap, myPlayer, isMyTurn) {
    // 1. Mua đất
    if (isMyTurn && snap.turnState === 'WAIT_BUY_DECISION' && snap.pendingBuySquarePos >= 0) {
      const key = `${snap.turnNumber}:${snap.pendingBuySquarePos}:${snap.rollSeq}`;
      if (appState.lastBuyPromptKey !== key) {
        appState.lastBuyPromptKey = key;
        openBuyModal(snap);
      }
    } else if (snap.turnState !== 'WAIT_BUY_DECISION') {
      closeModal('modal-buy');
    }

    // 2. Trade Incoming / Pending
    if (snap.turnState === 'WAIT_TRADE_RESPONSE' && snap.pendingTrade && myPlayer) {
      const t = snap.pendingTrade;
      if (t.receiverId === myPlayer.id || t.senderId === myPlayer.id) {
        renderIncomingTradeModal(t, myPlayer.id);
        openModal('modal-trade-incoming');
      } else {
        closeModal('modal-trade-incoming');
      }
    } else {
      closeModal('modal-trade-incoming');
    }

    // 3. Game Over
    if (snap.phase === 'GAME_OVER') {
      if (!appState.lastGameOverPrompted) {
        appState.lastGameOverPrompted = true;
        openGameOverModal(snap);
      }
    } else {
      appState.lastGameOverPrompted = false;
      closeModal('modal-gameover');
    }
  }

  function openBuyModal(snap) {
    const sq = (snap.squares || [])[snap.pendingBuySquarePos];
    const myPlayer = snap.players.find((p) => p.id === snap.me.playerId);
    if (!sq || !myPlayer) return;
    $('buy-card-img').src = `/${sq.uprightImg}`;
    $('buy-name').textContent = `#${sq.position} ${sq.name}`;
    $('buy-price').textContent = `$${sq.price}`;
    $('buy-balance').textContent = `$${myPlayer.balance}`;
    $('buy-after').textContent = `$${myPlayer.balance - sq.price}`;
    openModal('modal-buy');
  }

  function openCardRevealModal(card) {
    $('card-modal-type').textContent = `🃏 ${card.cardTypeLabel}`;
    $('card-modal-img').src = `/${card.imagePath}`;
    $('card-modal-player').textContent = `Người rút: ${card.drawnByName}`;
    $('card-modal-title').textContent = card.title;
    $('card-modal-desc').textContent = card.description;
    openModal('modal-card');
  }

  function renderSquareDetailModal(pos) {
    const snap = appState.snapshot;
    if (!snap || !snap.squares) return;
    const sq = snap.squares[pos];
    if (!sq) return;

    $('sq-modal-title').textContent = `Ô #${sq.position}: ${sq.name}`;
    $('sq-modal-img').src = `/${sq.uprightImg}`;

    const details = [];
    if (sq.kind === 'PROPERTY' || sq.kind === 'RAILROAD' || sq.kind === 'UTILITY') {
      details.push(`<p><strong>Giá mua:</strong> $${sq.price} | <strong>Thế chấp:</strong> $${sq.mortgageValue}</p>`);
      details.push(
        `<p><strong>Chủ sở hữu:</strong> ${
          sq.ownerName
            ? `<span style="color:${esc(sq.ownerColor)}"><strong>${esc(sq.ownerName)}</strong></span>`
            : '<span class="muted">Chưa có chủ</span>'
        } ${sq.mortgaged ? '<span class="tag tag-offline">ĐANG THẾ CHẤP</span>' : ''}</p>`
      );

      if (sq.kind === 'PROPERTY') {
        details.push(
          `<p><strong>Nhóm màu:</strong> ${esc(sq.colorGroupLabel)} | <strong>Giá xây nhà:</strong> $${sq.houseCost}</p>`
        );
        const tierNames = ['Đất trống', 'Đủ bộ màu', '1 Nhà', '2 Nhà', '3 Nhà', '4 Nhà', 'Khách sạn'];
        const activeRowIdx = sq.houseLevel > 0 ? sq.houseLevel + 1 : sq.currentRent > sq.baseRent ? 1 : 0;
        const rows = (sq.rentTiers || [])
          .map(
            (val, idx) =>
              `<tr class="${idx === activeRowIdx ? 'active-tier' : ''}"><td>${tierNames[idx]}</td><td>$${val}</td></tr>`
          )
          .join('');
        details.push(`<table class="rent-table">${rows}</table>`);
      } else if (sq.kind === 'RAILROAD') {
        const rrRows = [25, 50, 100, 200]
          .map((val, idx) => `<tr><td>Sở hữu ${idx + 1} ga</td><td>$${val}</td></tr>`)
          .join('');
        details.push(`<table class="rent-table">${rrRows}</table>`);
      } else {
        details.push(`<p><strong>Tiền thuê:</strong> ${esc(sq.rentNote)}</p>`);
      }
    } else if (sq.kind === 'TAX') {
      details.push(`<p><strong>Tiền thuế phải nộp:</strong> $${sq.taxAmount}</p>`);
    } else if (sq.kind === 'CHANCE') {
      details.push('<p>Rút 1 lá bài <strong>Cơ Hội</strong> và thực thi hiệu ứng.</p>');
    } else if (sq.kind === 'COMMUNITY_CHEST') {
      details.push('<p>Rút 1 lá bài <strong>Khí Vận</strong> và thực thi hiệu ứng.</p>');
    } else if (sq.kind === 'GO_TO_JAIL') {
      details.push('<p>Đi thẳng vào Nhà Tù (Ô #10), không qua GO, không nhận $200.</p>');
    } else if (sq.kind === 'JAIL') {
      details.push('<p>Ô Nhà Tù / Thăm tù. Phí bảo lãnh: $50.</p>');
    } else if (sq.kind === 'GO') {
      details.push('<p>Đi qua hoặc dừng tại GO nhận ngay <strong>+$200</strong>.</p>');
    } else {
      details.push('<p>Bãi đỗ xe miễn phí — an toàn không mất phí.</p>');
    }

    $('sq-modal-details').innerHTML = details.join('');

    const actionsEl = $('sq-modal-actions');
    const isMyTurn = snap.me && snap.me.playerId && snap.currentPlayerId === snap.me.playerId;
    const canManage = isMyTurn && ['WAIT_ROLL', 'WAIT_END_TURN', 'WAIT_JAIL_CHOICE'].includes(snap.turnState);
    const btns = [];
    if (canManage && sq.viewerCanUpgrade) {
      btns.push(`<button class="btn btn-success btn-sm" id="btn-sq-upgrade">🏗️ Nâng cấp ($${sq.houseCost})</button>`);
    }
    if (canManage && sq.viewerCanMortgage) {
      btns.push(`<button class="btn btn-warning btn-sm" id="btn-sq-mortgage">🏦 Thế chấp (+$${sq.mortgageValue})</button>`);
    }
    if (canManage && sq.viewerCanUnmortgage) {
      btns.push(`<button class="btn btn-primary btn-sm" id="btn-sq-unmortgage">🔓 Chuộc lại (-$${sq.unmortgageCost})</button>`);
    }
    btns.push(`<button class="btn btn-secondary btn-sm" data-close="modal-square">Đóng</button>`);
    actionsEl.innerHTML = btns.join('');

    actionsEl.querySelectorAll('[data-close]').forEach((b) =>
      b.addEventListener('click', () => closeModal(b.getAttribute('data-close')))
    );
    const upBtn = $('btn-sq-upgrade');
    if (upBtn) upBtn.addEventListener('click', () => sendAction('UPGRADE_PROPERTY', { squarePos: pos }));
    const mBtn = $('btn-sq-mortgage');
    if (mBtn) mBtn.addEventListener('click', () => sendAction('MORTGAGE_ASSET', { squarePos: pos }));
    const uBtn = $('btn-sq-unmortgage');
    if (uBtn) uBtn.addEventListener('click', () => sendAction('UNMORTGAGE_ASSET', { squarePos: pos }));
  }

  function openMyAssetsModal() {
    renderMyAssetsModal();
    openModal('modal-assets');
  }

  function renderMyAssetsModal() {
    const snap = appState.snapshot;
    const myId = snap && snap.me ? snap.me.playerId : null;
    const listEl = $('my-assets-list');
    if (!snap || !myId) {
      listEl.innerHTML = '<div class="empty-state">Không có dữ liệu tài sản.</div>';
      return;
    }

    const mine = (snap.squares || []).filter((sq) => sq.ownerId === myId);
    if (!mine.length) {
      listEl.innerHTML = '<div class="empty-state">Bạn chưa sở hữu bất động sản nào.</div>';
      return;
    }

    listEl.innerHTML = mine
      .map((sq) => {
        const levelText =
          sq.kind === 'PROPERTY'
            ? sq.houseLevel === 5
              ? '🏨 Khách sạn (Cấp 5)'
              : `🏠 Nhà cấp ${sq.houseLevel}`
            : sq.kind === 'RAILROAD'
              ? '🚂 Nhà ga'
              : '⚡ Tiện ích';
        return `
          <div class="asset-manage-item">
            <div>
              <strong>#${sq.position} ${esc(sq.name)}</strong>
              <div class="muted">${levelText} ${sq.mortgaged ? '• ĐANG THẾ CHẤP' : ''}</div>
              <div class="muted">Giá trị: $${sq.price} • Thế chấp: $${sq.mortgageValue}</div>
            </div>
            <div class="asset-manage-actions">
              ${
                sq.kind === 'PROPERTY'
                  ? `<button class="btn btn-success btn-sm" data-asset-act="UPGRADE_PROPERTY" data-pos="${sq.position}" ${
                      sq.viewerCanUpgrade ? '' : 'disabled'
                    }>+ Nhà ($${sq.houseCost})</button>`
                  : ''
              }
              ${
                !sq.mortgaged
                  ? `<button class="btn btn-warning btn-sm" data-asset-act="MORTGAGE_ASSET" data-pos="${sq.position}" ${
                      sq.viewerCanMortgage ? '' : 'disabled'
                    }>Thế chấp (+$${sq.mortgageValue})</button>`
                  : `<button class="btn btn-primary btn-sm" data-asset-act="UNMORTGAGE_ASSET" data-pos="${sq.position}" ${
                      sq.viewerCanUnmortgage ? '' : 'disabled'
                    }>Chuộc (-$${sq.unmortgageCost})</button>`
              }
            </div>
          </div>
        `;
      })
      .join('');

    listEl.querySelectorAll('[data-asset-act]').forEach((btn) => {
      btn.addEventListener('click', () => {
        sendAction(btn.getAttribute('data-asset-act'), {
          squarePos: parseInt(btn.getAttribute('data-pos'), 10),
        });
      });
    });
  }

  function hasHousesInColorGroup(squares, ownerId, colorGroup) {
    if (!colorGroup) return false;
    return squares.some(
      (sq) => sq.ownerId === ownerId && sq.colorGroup === colorGroup && (sq.houseLevel || 0) > 0
    );
  }

  function openTradeBuilderModal() {
    const snap = appState.snapshot;
    if (!snap || !snap.me || !snap.me.playerId) return;
    const myId = snap.me.playerId;
    const me = snap.players.find((p) => p.id === myId);
    const partners = snap.players.filter((p) => p.id !== myId && !p.bankrupt);
    if (!me || !partners.length) {
      showToast('Không có đối tác hợp lệ để giao dịch.', 'error');
      return;
    }

    const select = $('trade-partner-select');
    select.innerHTML = partners
      .map((p) => `<option value="${esc(p.id)}">${esc(p.name)} ($${p.balance} • ${p.assetCount} ô)</option>`)
      .join('');

    $('trade-sender-name').textContent = me.name;
    $('trade-sender-max-cash').textContent = String(me.balance);
    $('trade-sender-max-tickets').textContent = String(me.outOfJailTickets);
    $('trade-offered-cash').value = '0';
    $('trade-offered-cash').max = String(me.balance);
    $('trade-offered-tickets').value = '0';
    $('trade-offered-tickets').max = String(me.outOfJailTickets);

    const myTradables = (snap.squares || []).filter((sq) => sq.ownerId === myId && sq.viewerIsTradable);
    $('trade-offered-assets').innerHTML = myTradables.length
      ? myTradables
          .map(
            (sq) => `
              <label class="trade-check-item">
                <input type="checkbox" class="trade-offer-chk" value="${sq.position}" />
                <span>#${sq.position} ${esc(sq.name)} ($${sq.price})${sq.mortgaged ? ' [Thế chấp]' : ''}</span>
              </label>
            `
          )
          .join('')
      : '<div class="muted">Không có bất động sản khả dụng.</div>';

    const updatePartnerSide = () => {
      const partnerId = select.value;
      const partner = partners.find((p) => p.id === partnerId);
      if (!partner) return;
      $('trade-receiver-name').textContent = partner.name;
      $('trade-receiver-max-cash').textContent = String(partner.balance);
      $('trade-receiver-max-tickets').textContent = String(partner.outOfJailTickets);
      $('trade-requested-cash').value = '0';
      $('trade-requested-cash').max = String(partner.balance);
      $('trade-requested-tickets').value = '0';
      $('trade-requested-tickets').max = String(partner.outOfJailTickets);

      const partnerTradables = (snap.squares || []).filter(
        (sq) =>
          sq.ownerId === partner.id &&
          !hasHousesInColorGroup(snap.squares, partner.id, sq.colorGroup)
      );
      $('trade-requested-assets').innerHTML = partnerTradables.length
        ? partnerTradables
            .map(
              (sq) => `
                <label class="trade-check-item">
                  <input type="checkbox" class="trade-req-chk" value="${sq.position}" />
                  <span>#${sq.position} ${esc(sq.name)} ($${sq.price})${sq.mortgaged ? ' [Thế chấp]' : ''}</span>
                </label>
              `
            )
            .join('')
        : '<div class="muted">Đối tác không có bất động sản khả dụng.</div>';
    };

    select.onchange = updatePartnerSide;
    updatePartnerSide();
    openModal('modal-trade');
  }

  function submitTradeProposal() {
    const receiverId = $('trade-partner-select').value;
    const offeredCash = Math.max(0, parseInt($('trade-offered-cash').value, 10) || 0);
    const requestedCash = Math.max(0, parseInt($('trade-requested-cash').value, 10) || 0);
    const offeredJailCards = Math.max(0, parseInt($('trade-offered-tickets').value, 10) || 0);
    const requestedJailCards = Math.max(0, parseInt($('trade-requested-tickets').value, 10) || 0);

    const offeredAssets = Array.from(document.querySelectorAll('.trade-offer-chk:checked')).map((el) =>
      parseInt(el.value, 10)
    );
    const requestedAssets = Array.from(document.querySelectorAll('.trade-req-chk:checked')).map((el) =>
      parseInt(el.value, 10)
    );

    closeModal('modal-trade');
    sendAction('PROPOSE_TRADE', {
      receiverId,
      offeredCash,
      offeredAssets,
      offeredJailCards,
      requestedCash,
      requestedAssets,
      requestedJailCards,
    });
  }

  function renderIncomingTradeModal(trade, myId) {
    const formatAssets = (arr) =>
      arr && arr.length
        ? arr.map((a) => `#${a.position} ${esc(a.name)}${a.mortgaged ? ' [Thế chấp]' : ''}`).join(', ')
        : 'Không có';

    $('trade-incoming-body').innerHTML = `
      <p><strong>${esc(trade.senderName)}</strong> gửi lời đề nghị giao dịch tới <strong>${esc(trade.receiverName)}</strong>:</p>
      <div class="trade-columns">
        <div class="trade-col">
          <h4>📤 ${esc(trade.senderName)} đưa ra</h4>
          <p>💵 Tiền mặt: <strong>$${trade.offeredCash}</strong></p>
          <p>🎫 Vé ra tù: <strong>${trade.offeredJailCards}</strong></p>
          <p>🏠 Tài sản: <strong>${formatAssets(trade.offeredAssets)}</strong></p>
        </div>
        <div class="trade-col">
          <h4>📥 ${esc(trade.receiverName)} đáp ứng</h4>
          <p>💵 Tiền mặt: <strong>$${trade.requestedCash}</strong></p>
          <p>🎫 Vé ra tù: <strong>${trade.requestedJailCards}</strong></p>
          <p>🏠 Tài sản: <strong>${formatAssets(trade.requestedAssets)}</strong></p>
        </div>
      </div>
    `;

    const foot = $('trade-incoming-foot');
    if (trade.receiverId === myId) {
      foot.innerHTML = `
        <button class="btn btn-success" id="btn-trade-accept">✅ Đồng Ý Giao Dịch</button>
        <button class="btn btn-danger" id="btn-trade-decline">❌ Từ Chối</button>
      `;
      $('btn-trade-accept').addEventListener('click', () => sendAction('RESPOND_TRADE', { accept: true }));
      $('btn-trade-decline').addEventListener('click', () => sendAction('RESPOND_TRADE', { accept: false }));
    } else {
      foot.innerHTML = `
        <span class="muted">Đang chờ ${esc(trade.receiverName)} phản hồi...</span>
        <button class="btn btn-warning btn-sm" id="btn-trade-cancel">🚫 Hủy Đề Nghị</button>
      `;
      $('btn-trade-cancel').addEventListener('click', () => sendAction('CANCEL_TRADE'));
    }
  }

  function openGameOverModal(snap) {
    const winner = snap.players.find((p) => p.id === snap.winnerPlayerId);
    const ranked = [...snap.players].sort((a, b) => {
      if (a.bankrupt !== b.bankrupt) return a.bankrupt ? 1 : -1;
      return b.totalAssetValue - a.totalAssetValue;
    });

    $('gameover-body').innerHTML = `
      <h2 class="gold-text">${winner ? `🏆 ${esc(winner.name)} CHIẾN THẮNG!` : '💀 HÒA CẢ BÀN'}</h2>
      <p class="muted">Tổng kết bảng xếp hạng tài sản cuối ván:</p>
      ${ranked
        .map(
          (p, i) => `
          <div class="rank-row">
            <span>#${i + 1} <strong style="color:${esc(p.colorHex)}">${esc(p.name)}</strong> ${p.bankrupt ? '(Phá sản)' : ''}</span>
            <strong>💵 $${p.balance.toLocaleString()} • 💎 $${p.totalAssetValue.toLocaleString()}</strong>
          </div>
        `
        )
        .join('')}
    `;

    const isHost = snap.me && snap.me.isHost;
    $('btn-rematch').classList.toggle('hidden', !isHost);
    openModal('modal-gameover');
  }

  // =========================================================================
  // EVENT BINDINGS & STARTUP
  // =========================================================================
  function bindEvents() {
    $('input-name').value = localStorage.getItem(NAME_KEY) || '';

    $('btn-create-room').addEventListener('click', createRoom);
    $('btn-join-player').addEventListener('click', () =>
      joinRoom($('input-join-code').value, 'PLAYER', null)
    );
    $('btn-join-spectator').addEventListener('click', () =>
      joinRoom($('input-join-code').value, 'SPECTATOR', null)
    );
    $('btn-refresh-rooms').addEventListener('click', loadPublicRooms);

    $('btn-wait-copy-play').addEventListener('click', () => copyRoomLink(false));
    $('btn-wait-copy-watch').addEventListener('click', () => copyRoomLink(true));
    $('btn-live-copy-play').addEventListener('click', () => copyRoomLink(false));
    $('btn-live-copy-watch').addEventListener('click', () => copyRoomLink(true));

    $('btn-wait-leave').addEventListener('click', leaveCurrentRoom);
    $('btn-live-leave').addEventListener('click', leaveCurrentRoom);
    $('btn-gameover-leave').addEventListener('click', leaveCurrentRoom);

    $('btn-add-bot').addEventListener('click', () => sendAction('ADD_BOT'));
    $('btn-start-game').addEventListener('click', () =>
      sendAction('START_GAME', { randomizeOrder: $('chk-randomize-order').checked })
    );
    $('btn-rematch').addEventListener('click', () => sendAction('REMATCH'));

    $('btn-surrender').addEventListener('click', () => {
      if (window.confirm('Bạn có chắc muốn đầu hàng và rút khỏi ván chơi? Toàn bộ tài sản sẽ trả về Ngân hàng.')) {
        sendAction('SURRENDER');
      }
    });

    $('btn-confirm-buy').addEventListener('click', () => sendAction('BUY_ASSET'));
    $('btn-skip-buy').addEventListener('click', () => sendAction('SKIP_BUY'));
    $('btn-submit-trade').addEventListener('click', submitTradeProposal);

    $('center-last-card').addEventListener('click', () => {
      if (appState.snapshot && appState.snapshot.lastCard) {
        openCardRevealModal(appState.snapshot.lastCard);
      }
    });

    $('wait-chat-form').addEventListener('submit', (e) => {
      e.preventDefault();
      sendChat($('wait-chat-input'));
    });

    $('live-chat-form').addEventListener('submit', (e) => {
      e.preventDefault();
      sendChat($('live-chat-input'));
    });

    $('tab-btn-logs').addEventListener('click', () => {
      $('tab-btn-logs').classList.add('active');
      $('tab-btn-chat').classList.remove('active');
      $('tab-pane-logs').classList.add('active');
      $('tab-pane-chat').classList.remove('active');
    });

    $('tab-btn-chat').addEventListener('click', () => {
      $('tab-btn-chat').classList.add('active');
      $('tab-btn-logs').classList.remove('active');
      $('tab-pane-chat').classList.add('active');
      $('tab-pane-logs').classList.remove('active');
    });

    document.querySelectorAll('[data-close]').forEach((btn) => {
      btn.addEventListener('click', () => closeModal(btn.getAttribute('data-close')));
    });
  }

  function boot() {
    bindEvents();
    const params = new URLSearchParams(window.location.search);
    const urlRoom = (params.get('room') || '').trim().toUpperCase();
    const watchMode = params.get('watch') === '1';
    const saved = loadSession();

    if (urlRoom) {
      $('input-join-code').value = urlRoom;
      if (saved && saved.roomCode === urlRoom && saved.token && !watchMode) {
        joinRoom(urlRoom, saved.role || 'PLAYER', saved.token);
        return;
      }
      if (watchMode) {
        joinRoom(urlRoom, 'SPECTATOR', saved && saved.roomCode === urlRoom ? saved.token : null);
        return;
      }
      showView('view-landing');
      return;
    }

    if (saved && saved.roomCode && saved.token) {
      joinRoom(saved.roomCode, saved.role || 'PLAYER', saved.token);
      return;
    }

    showView('view-landing');
  }

  window.addEventListener('DOMContentLoaded', boot);
})();
