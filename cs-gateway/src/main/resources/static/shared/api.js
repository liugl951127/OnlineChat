/* ============================================
   共享 API 工具：fetch / auth / token
   ============================================ */

export function getToken() {
  const params = new URLSearchParams(location.search);
  const t = params.get('token');
  if (t) {
    localStorage.setItem('cs_token', t);
    // 清理 URL
    history.replaceState(null, '', location.pathname);
    return t;
  }
  return localStorage.getItem('cs_token') || '';
}

export function clearToken() {
  localStorage.removeItem('cs_token');
}

export function authHeader() {
  const t = getToken();
  return t ? { 'Authorization': 'Bearer ' + t } : {};
}

export async function api(path, opts = {}) {
  const headers = { ...authHeader(), ...(opts.headers || {}) };
  if (opts.json !== undefined && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }
  const r = await fetch(path, { ...opts, headers });
  const data = await r.json().catch(() => ({}));
  if (data && data.code && data.code !== 0 && r.status >= 400) {
    const err = new Error(data.msg || '请求失败');
    err.code = data.code;
    err.data = data;
    throw err;
  }
  return data;
}

export function showToast(text, type = 'info') {
  let toast = document.getElementById('cs-toast');
  if (!toast) {
    toast = document.createElement('div');
    toast.id = 'cs-toast';
    toast.style.cssText = 'position:fixed;top:20px;left:50%;transform:translateX(-50%);padding:8px 16px;background:rgba(0,0,0,0.85);color:#fff;border-radius:8px;font-size:13px;z-index:9999;animation:fadeInUp 0.25s cubic-bezier(0.16,1,0.3,1);';
    document.body.appendChild(toast);
  }
  toast.textContent = text;
  toast.dataset.type = type;
  if (type === 'error') toast.style.background = 'rgba(255,77,79,0.9)';
  else if (type === 'success') toast.style.background = 'rgba(82,196,26,0.9)';
  else toast.style.background = 'rgba(0,0,0,0.85)';
  clearTimeout(toast._timer);
  toast._timer = setTimeout(() => toast.textContent = '', 2500);
}

export function formatTime(ts) {
  if (!ts) return '';
  const d = typeof ts === 'number' ? new Date(ts) : new Date(ts);
  if (isNaN(d.getTime())) return '';
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}

export function formatSize(b) {
  if (!b) return '';
  if (b < 1024) return b + ' B';
  if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB';
  return (b / 1024 / 1024).toFixed(2) + ' MB';
}

export function downloadFile(url, name) {
  const a = document.createElement('a');
  a.href = url;
  a.download = name || '';
  a.click();
}