const token = localStorage.getItem('token');
if (!token) window.location.href = '/index.html';

let currentSymbol = null;
let currentPrice = 0;
let allStocks = [];

// Live Refresh
const REFRESH_INTERVAL = 60;
let refreshTimer = null;
let countdown = REFRESH_INTERVAL;

// Popular stocks config with emojis
const POPULAR_STOCKS = [
    { symbol: 'AAPL',  name: 'Apple Inc.',           emoji: '🍎' },
    { symbol: 'TSLA',  name: 'Tesla Inc.',            emoji: '⚡' },
    { symbol: 'MSFT',  name: 'Microsoft Corp.',       emoji: '🪟' },
    { symbol: 'GOOGL', name: 'Alphabet Inc.',         emoji: '🔍' },
    { symbol: 'NVDA',  name: 'NVIDIA Corp.',          emoji: '🎮' },
    { symbol: 'META',  name: 'Meta Platforms Inc.',   emoji: '🌐' },
    { symbol: 'AMZN',  name: 'Amazon.com Inc.',       emoji: '📦' },
    { symbol: 'NFLX',  name: 'Netflix Inc.',          emoji: '🎬' },
    { symbol: 'AMD',   name: 'AMD Inc.',              emoji: '💻' },
    { symbol: 'JPM',   name: 'JPMorgan Chase',        emoji: '🏦' },
];

// ===== INIT =====
document.addEventListener('DOMContentLoaded', () => {
    const username = localStorage.getItem('username') || 'User';
    document.getElementById('nav-username').textContent = username;
    document.getElementById('user-avatar').textContent = username[0].toUpperCase();
    loadBalance();
    loadStockList();

    document.getElementById('search-input').addEventListener('input', debounce(onSearchInput, 400));
    document.getElementById('search-input').addEventListener('keydown', e => {
        if (e.key === 'Enter') onSearchInput();
    });
    document.getElementById('trade-qty').addEventListener('input', updateTradeTotal);
});

function debounce(fn, ms) {
    let t; return function(...a) { clearTimeout(t); t = setTimeout(() => fn(...a), ms); };
}

// ===== API HELPER =====
async function api(url, method, body) {
    method = method || 'GET';
    const opts = { method, headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token } };
    if (body) opts.body = JSON.stringify(body);
    const res = await fetch(url, opts);
    if (res.status === 401 || res.status === 403) { localStorage.clear(); window.location.href = '/index.html'; return null; }
    return res.json();
}

// ===== NAVIGATION =====
function showSection(name) {
    document.querySelectorAll('.section').forEach(s => s.classList.add('hidden'));
    document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
    document.getElementById('section-' + name).classList.remove('hidden');
    document.querySelectorAll('.nav-btn')[['market', 'portfolio', 'history'].indexOf(name)].classList.add('active');
    if (name === 'portfolio') loadPortfolio();
    if (name === 'history') loadHistory();
    if (name !== 'market') stopLiveRefresh();
    else if (currentSymbol) startLiveRefresh();
}

function logout() { stopLiveRefresh(); localStorage.clear(); window.location.href = '/index.html'; }

// ===== BALANCE =====
async function loadBalance() {
    const data = await api('/api/portfolio/balance');
    if (!data) return;
    document.getElementById('nav-balance').textContent = formatCurrency(data.balance);
    document.getElementById('port-balance').textContent = formatCurrency(data.balance);
}

// ===== STOCK LIST =====
async function loadStockList() {
    const listEl = document.getElementById('stock-list');

    // Alle Aktien erst als Placeholder anzeigen
    allStocks = POPULAR_STOCKS.map(s => ({
        symbol: s.symbol, name: s.name, emoji: s.emoji,
        price: 0, change: 0, changePercent: 0, loading: true
    }));
    renderStockList(allStocks);

    // Nacheinander laden mit 13s Delay (Alpha Vantage: max 5/min = 12s pro Request)
    for (let i = 0; i < POPULAR_STOCKS.length; i++) {
        const s = POPULAR_STOCKS[i];

        // Countdown in der Liste anzeigen
        const itemEl = document.getElementById('item-' + s.symbol);
        if (itemEl) {
            const priceEl = itemEl.querySelector('.price');
            if (priceEl) priceEl.textContent = 'Laden…';
        }

        const data = await api('/api/stocks/quote/' + s.symbol);

        if (data && !data.error) {
            allStocks[i] = { ...data, emoji: s.emoji };
        }

        // Einzelne Zeile sofort updaten ohne alles neu zu rendern
        updateStockListItem(allStocks[i]);

        // Warten zwischen Requests (außer beim letzten)
        if (i < POPULAR_STOCKS.length - 1) {
            await sleep(13000);
        }
    }
}

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

function updateStockListItem(s) {
    const itemEl = document.getElementById('item-' + s.symbol);
    if (!itemEl) return;

    const isPos = s.change >= 0;
    const sign = isPos ? '+' : '';

    const priceEl = itemEl.querySelector('.price');
    const changeEl = itemEl.querySelector('.change');

    if (priceEl) priceEl.textContent = s.price > 0 ? formatCurrency(s.price) : '—';
    if (changeEl) {
        changeEl.textContent = s.price > 0 ? sign + s.changePercent.toFixed(2) + '%' : '—';
        changeEl.className = 'change ' + (isPos ? 'pos' : 'neg');
    }

    if (s.price > 0) drawMiniChart(s);
}

function renderStockList(stocks) {
    const listEl = document.getElementById('stock-list');
    if (!stocks.length) { listEl.innerHTML = '<p class="empty-msg">Keine Aktien gefunden</p>'; return; }

    listEl.innerHTML = stocks.map(s => {
        const isPos = s.change >= 0;
        const colorClass = isPos ? 'pos' : 'neg';
        const sign = isPos ? '+' : '';
        return `
        <div class="stock-item" id="item-${s.symbol}" onclick="loadQuote('${s.symbol}')">
            <div class="stock-item-logo">${s.emoji}</div>
            <div class="stock-item-info">
                <div class="stock-item-symbol">${s.symbol}</div>
                <div class="stock-item-name">${s.name}</div>
            </div>
            <canvas class="stock-item-chart" id="mini-${s.symbol}" width="70" height="32"></canvas>
            <div class="stock-item-price">
                <div class="price">${s.price > 0 ? formatCurrency(s.price) : '—'}</div>
                <div class="change ${colorClass}">${s.price > 0 ? sign + s.changePercent.toFixed(2) + '%' : '—'}</div>
            </div>
        </div>`;
    }).join('');

    // Mini-Sparklines zeichnen (aus OHLC schätzen)
    stocks.forEach(s => drawMiniChart(s));
}

function drawMiniChart(stock) {
    const canvas = document.getElementById('mini-' + stock.symbol);
    if (!canvas || stock.price === 0) return;
    const ctx = canvas.getContext('2d');
    const w = canvas.width, h = canvas.height;

    // Einfache Trendlinie aus open → close mit high/low
    const isPos = stock.change >= 0;
    const color = isPos ? '#4ade80' : '#f87171';

    // 5 Punkte: open, low, high, (open+close)/2, close
    const open = stock.open || stock.price - stock.change;
    const close = stock.price;
    const high = stock.high || Math.max(open, close) * 1.002;
    const low = stock.low || Math.min(open, close) * 0.998;

    const pts = [open, isPos ? low : high, isPos ? high : low, (open + close) / 2, close];
    const minV = Math.min(...pts), maxV = Math.max(...pts);
    const range = maxV - minV || 1;

    const xs = pts.map((_, i) => (i / (pts.length - 1)) * w);
    const ys = pts.map(v => h - ((v - minV) / range) * (h - 4) - 2);

    ctx.clearRect(0, 0, w, h);

    // Gradient fill
    const grad = ctx.createLinearGradient(0, 0, 0, h);
    grad.addColorStop(0, color + '30');
    grad.addColorStop(1, color + '00');

    ctx.beginPath();
    ctx.moveTo(xs[0], ys[0]);
    for (let i = 1; i < xs.length; i++) {
        const cpx = (xs[i - 1] + xs[i]) / 2;
        ctx.bezierCurveTo(cpx, ys[i - 1], cpx, ys[i], xs[i], ys[i]);
    }
    ctx.lineTo(xs[xs.length - 1], h);
    ctx.lineTo(xs[0], h);
    ctx.closePath();
    ctx.fillStyle = grad;
    ctx.fill();

    // Line
    ctx.beginPath();
    ctx.moveTo(xs[0], ys[0]);
    for (let i = 1; i < xs.length; i++) {
        const cpx = (xs[i - 1] + xs[i]) / 2;
        ctx.bezierCurveTo(cpx, ys[i - 1], cpx, ys[i], xs[i], ys[i]);
    }
    ctx.strokeStyle = color;
    ctx.lineWidth = 1.5;
    ctx.stroke();
}

// ===== FILTER TABS =====
function setFilter(type, btn) {
    document.querySelectorAll('.filter-tab').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');

    let filtered = [...allStocks];
    if (type === 'gainers') filtered = filtered.filter(s => s.change > 0).sort((a, b) => b.changePercent - a.changePercent);
    else if (type === 'losers') filtered = filtered.filter(s => s.change < 0).sort((a, b) => a.changePercent - b.changePercent);
    renderStockList(filtered);
}

// ===== SEARCH =====
async function onSearchInput() {
    const q = document.getElementById('search-input').value.trim();
    if (!q) { renderStockList(allStocks); return; }

    // Lokale Filterung zuerst
    const local = allStocks.filter(s =>
        s.symbol.includes(q.toUpperCase()) || s.name.toLowerCase().includes(q.toLowerCase())
    );
    if (local.length) { renderStockList(local); return; }

    // API Suche
    const data = await api('/api/stocks/search?q=' + encodeURIComponent(q));
    if (!data || data.error || !data.length) return;

    const results = data.map(s => ({
        symbol: s.symbol, name: s.name, emoji: '📈',
        price: 0, change: 0, changePercent: 0
    }));
    renderStockList(results);
}

// ===== DETAIL VIEW =====
async function loadQuote(symbol) {
    // Active state in list
    document.querySelectorAll('.stock-item').forEach(el => el.classList.remove('active'));
    const item = document.getElementById('item-' + symbol);
    if (item) item.classList.add('active');

    document.getElementById('detail-placeholder').classList.add('hidden');
    document.getElementById('stock-detail').classList.remove('hidden');

    stopLiveRefresh();
    currentSymbol = symbol;

    // Aus gecachtem allStocks laden wenn verfügbar
    const cached = allStocks.find(s => s.symbol === symbol);
    if (cached && cached.price > 0) renderDetail(cached);

    // Trotzdem frisch laden
    const data = await api('/api/stocks/quote/' + symbol);
    if (data && !data.error) {
        currentPrice = data.price;
        // Emoji aus config holen
        const cfg = POPULAR_STOCKS.find(s => s.symbol === symbol);
        data.emoji = cfg ? cfg.emoji : '📈';
        renderDetail(data);
        startLiveRefresh();
    }

    // Sparkline laden
    loadDetailChart(symbol);
}

function renderDetail(data) {
    currentPrice = data.price;
    const isPos = data.change >= 0;

    document.getElementById('detail-symbol').textContent = data.symbol;
    document.getElementById('detail-name').textContent = data.name;
    document.getElementById('detail-logo').textContent = data.emoji || data.symbol[0];
    document.getElementById('detail-price').textContent = formatCurrency(data.price);

    const changeEl = document.getElementById('detail-change');
    const sign = isPos ? '+' : '';
    changeEl.textContent = sign + formatCurrency(data.change) + '  (' + sign + data.changePercent.toFixed(2) + '%)';
    changeEl.className = 'detail-change ' + (isPos ? 'pos' : 'neg');

    document.getElementById('stat-open').textContent = formatCurrency(data.open);
    document.getElementById('stat-high').textContent = formatCurrency(data.high);
    document.getElementById('stat-low').textContent = formatCurrency(data.low);
    document.getElementById('stat-volume').textContent = formatNumber(data.volume);

    updateTradeTotal();
}

async function loadDetailChart(symbol) {
    document.getElementById('chart-loading').classList.remove('hidden');
    const prices = await api('/api/stocks/intraday/' + symbol);
    document.getElementById('chart-loading').classList.add('hidden');

    if (!prices || !prices.length) {
        // Fallback: OHLC-basierter Chart
        const cached = allStocks.find(s => s.symbol === symbol);
        if (cached) drawDetailChart(generateFallbackPrices(cached));
        return;
    }
    drawDetailChart(prices);
}

function generateFallbackPrices(stock) {
    // 8 Punkte zwischen open und close interpolieren
    const open = stock.open || stock.price - stock.change;
    const close = stock.price;
    const points = [];
    for (let i = 0; i <= 7; i++) {
        const t = i / 7;
        const base = open + (close - open) * t;
        const jitter = (Math.sin(i * 1.5) * (stock.high - stock.low) * 0.15) || 0;
        points.push(base + jitter);
    }
    return points;
}

function drawDetailChart(prices) {
    const canvas = document.getElementById('detail-chart');
    const ctx = canvas.getContext('2d');
    const w = canvas.offsetWidth || 600;
    canvas.width = w;
    canvas.height = 120;
    const h = 120;

    const minV = Math.min(...prices) * 0.9995;
    const maxV = Math.max(...prices) * 1.0005;
    const range = maxV - minV || 1;

    const isPos = prices[prices.length - 1] >= prices[0];
    const color = isPos ? '#4ade80' : '#f87171';

    const xs = prices.map((_, i) => (i / (prices.length - 1)) * w);
    const ys = prices.map(v => h - ((v - minV) / range) * (h - 8) - 4);

    ctx.clearRect(0, 0, w, h);

    // Gradient fill
    const grad = ctx.createLinearGradient(0, 0, 0, h);
    grad.addColorStop(0, color + '25');
    grad.addColorStop(1, color + '00');

    ctx.beginPath();
    ctx.moveTo(xs[0], ys[0]);
    for (let i = 1; i < xs.length; i++) {
        const cpx = (xs[i-1] + xs[i]) / 2;
        ctx.bezierCurveTo(cpx, ys[i-1], cpx, ys[i], xs[i], ys[i]);
    }
    ctx.lineTo(xs[xs.length-1], h);
    ctx.lineTo(xs[0], h);
    ctx.closePath();
    ctx.fillStyle = grad;
    ctx.fill();

    // Line
    ctx.beginPath();
    ctx.moveTo(xs[0], ys[0]);
    for (let i = 1; i < xs.length; i++) {
        const cpx = (xs[i-1] + xs[i]) / 2;
        ctx.bezierCurveTo(cpx, ys[i-1], cpx, ys[i], xs[i], ys[i]);
    }
    ctx.strokeStyle = color;
    ctx.lineWidth = 2;
    ctx.stroke();

    // Preis-Labels
    ctx.fillStyle = '#64748b';
    ctx.font = '10px DM Mono, monospace';
    ctx.textAlign = 'right';
    ctx.fillText('$' + maxV.toFixed(2), w - 4, 12);
    ctx.textAlign = 'left';
    ctx.fillText('$' + minV.toFixed(2), 4, h - 4);
}

// ===== TRADE =====
function adjustQty(delta) {
    const input = document.getElementById('trade-qty');
    const val = Math.max(1, (parseInt(input.value) || 1) + delta);
    input.value = val;
    updateTradeTotal();
}

function updateTradeTotal() {
    const qty = parseInt(document.getElementById('trade-qty').value) || 0;
    document.getElementById('trade-total').textContent = formatCurrency(qty * currentPrice);
}

async function executeTrade(type) {
    if (!currentSymbol) return;
    const qty = parseInt(document.getElementById('trade-qty').value);
    if (!qty || qty < 1) { showTradeMsg('Bitte eine gueltige Anzahl eingeben', 'error'); return; }

    const endpoint = type === 'buy' ? '/api/portfolio/buy' : '/api/portfolio/sell';
    const data = await api(endpoint, 'POST', { symbol: currentSymbol, quantity: qty });

    if (!data) return;
    if (data.error) { showTradeMsg(data.error, 'error'); }
    else {
        showTradeMsg(data.message, 'success');
        showToast(data.message, 'success');
        loadBalance();
    }
}

function showTradeMsg(msg, type) {
    const el = document.getElementById('trade-message');
    el.textContent = msg;
    el.className = 'trade-message ' + type;
    el.classList.remove('hidden');
}

// ===== LIVE REFRESH =====
function startLiveRefresh() {
    stopLiveRefresh();
    countdown = REFRESH_INTERVAL;
    updateCountdownUI();
    refreshTimer = setInterval(async () => {
        countdown--;
        updateCountdownUI();
        if (countdown <= 0) {
            countdown = REFRESH_INTERVAL;
            if (currentSymbol) await refreshQuietly(currentSymbol);
        }
    }, 1000);
}

function stopLiveRefresh() {
    if (refreshTimer) { clearInterval(refreshTimer); refreshTimer = null; }
    const el = document.getElementById('live-indicator');
    if (el) el.remove();
}

function updateCountdownUI() {
    let el = document.getElementById('live-indicator');
    if (!el) {
        el = document.createElement('div');
        el.id = 'live-indicator';
        document.body.appendChild(el);
    }
    const dot = countdown === REFRESH_INTERVAL ? '#4ade80' : '#fbbf24';
    el.innerHTML = '<span style="width:7px;height:7px;border-radius:50%;background:' + dot + ';display:inline-block;flex-shrink:0"></span> Live · ' + countdown + 's';
}

async function refreshQuietly(symbol) {
    const data = await api('/api/stocks/quote/' + symbol);
    if (!data || data.error) return;
    const wasHigher = data.price > currentPrice;
    const cfg = POPULAR_STOCKS.find(s => s.symbol === symbol);
    data.emoji = cfg ? cfg.emoji : '📈';
    renderDetail(data);

    // Preis aufleuchten
    const priceEl = document.getElementById('detail-price');
    priceEl.style.transition = 'color 0.4s';
    priceEl.style.color = wasHigher ? 'var(--green)' : 'var(--red)';
    setTimeout(() => { priceEl.style.color = ''; }, 1200);

    // Mini-Chart in Liste aktualisieren
    const idx = allStocks.findIndex(s => s.symbol === symbol);
    if (idx >= 0) {
        allStocks[idx] = { ...data, emoji: data.emoji };
        drawMiniChart(allStocks[idx]);
        const priceInList = document.querySelector('#item-' + symbol + ' .price');
        const changeInList = document.querySelector('#item-' + symbol + ' .change');
        if (priceInList) priceInList.textContent = formatCurrency(data.price);
        if (changeInList) {
            const sign = data.change >= 0 ? '+' : '';
            changeInList.textContent = sign + data.changePercent.toFixed(2) + '%';
            changeInList.className = 'change ' + (data.change >= 0 ? 'pos' : 'neg');
        }
    }
}

// ===== PORTFOLIO =====
async function loadPortfolio() {
    const [portfolio, balanceData] = await Promise.all([api('/api/portfolio'), api('/api/portfolio/balance')]);
    if (!portfolio) return;
    const balance = balanceData ? balanceData.balance : 0;
    document.getElementById('port-balance').textContent = formatCurrency(balance);

    if (!portfolio.length) {
        document.getElementById('portfolio-list').innerHTML = '<p class="empty-msg">Noch keine Aktien im Portfolio</p>';
        ['port-invested','port-current','port-pnl'].forEach(id => document.getElementById(id).textContent = formatCurrency(0));
        return;
    }

    let invested = portfolio.reduce((s, p) => s + p.avgBuyPrice * p.quantity, 0);
    document.getElementById('port-invested').textContent = formatCurrency(invested);
    document.getElementById('port-current').textContent = formatCurrency(invested);
    document.getElementById('port-pnl').textContent = formatCurrency(0);

    document.getElementById('portfolio-list').innerHTML = '<table><thead><tr><th>Symbol</th><th>Name</th><th>Anzahl</th><th>Ø Kaufpreis</th><th>Investierter Wert</th><th>Aktion</th></tr></thead><tbody>'
        + portfolio.map(p => `<tr><td><strong style="color:var(--accent)">${p.symbol}</strong></td><td>${p.companyName}</td><td>${p.quantity}</td><td>${formatCurrency(p.avgBuyPrice)}</td><td>${formatCurrency(p.avgBuyPrice * p.quantity)}</td><td><button class="sell-btn-small" onclick="quickSell('${p.symbol}',${p.quantity})">Verkaufen</button></td></tr>`).join('')
        + '</tbody></table>';
}

async function quickSell(symbol, maxQty) {
    const qty = parseInt(prompt('Wie viele ' + symbol + '-Aktien verkaufen? (max. ' + maxQty + ')'));
    if (!qty || qty < 1 || qty > maxQty) { showToast('Ungueltige Menge', 'error'); return; }
    const data = await api('/api/portfolio/sell', 'POST', { symbol, quantity: qty });
    if (!data) return;
    if (data.error) showToast(data.error, 'error');
    else { showToast(data.message, 'success'); loadPortfolio(); loadBalance(); }
}

// ===== HISTORY =====
async function loadHistory() {
    const transactions = await api('/api/portfolio/transactions');
    if (!transactions) return;

    if (!transactions.length) { document.getElementById('history-list').innerHTML = '<p class="empty-msg">Noch keine Transaktionen</p>'; return; }

    document.getElementById('history-list').innerHTML = '<table><thead><tr><th>Datum</th><th>Typ</th><th>Symbol</th><th>Name</th><th>Anzahl</th><th>Preis</th><th>Gesamt</th></tr></thead><tbody>'
        + transactions.map(t => `<tr><td style="color:var(--muted);font-size:0.82rem">${formatDate(t.timestamp)}</td><td><span class="badge-${t.type.toLowerCase()}">${t.type === 'BUY' ? '▲ Kauf' : '▼ Verkauf'}</span></td><td><strong style="color:var(--accent)">${t.symbol}</strong></td><td>${t.companyName}</td><td>${t.quantity}</td><td>${formatCurrency(t.price)}</td><td><strong>${formatCurrency(t.total)}</strong></td></tr>`).join('')
        + '</tbody></table>';
}

// ===== TOAST =====
let toastTimer;
function showToast(msg, type) {
    const toast = document.getElementById('toast');
    toast.textContent = msg; toast.className = 'toast ' + (type || 'success'); toast.classList.remove('hidden');
    clearTimeout(toastTimer); toastTimer = setTimeout(() => toast.classList.add('hidden'), 4000);
}

// ===== HELPERS =====
function formatCurrency(val) { return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(val); }
function formatNumber(val) { return new Intl.NumberFormat('de-DE').format(val); }
function formatDate(s) { const d = new Date(s); return d.toLocaleDateString('de-DE') + ' ' + d.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }); }
