/* ============================================
   页面共用 Bootstrap：token 提取 + 错误处理
   所有页面应在 <head> 引入此脚本（在 Vue 前）
   ============================================ */
(function() {
  // 1. URL ?token=xxx → localStorage（统一一件部署）
  const params = new URLSearchParams(location.search);
  const token = params.get('token');
  if (token) {
    localStorage.setItem('cs_token', token);
    // 清理 URL
    const u = new URL(location.href);
    u.searchParams.delete('token');
    history.replaceState(null, '', u.pathname + (u.search ? u.search : '') + u.hash);
  }

  // 2. URL ?error=xxx → 全局 Toast
  const err = params.get('error');
  if (err) {
    setTimeout(() => {
      const text = decodeURIComponent(err);
      let toast = document.getElementById('cs-toast');
      if (!toast) {
        toast = document.createElement('div');
        toast.id = 'cs-toast';
        toast.style.cssText = 'position:fixed;top:20px;left:50%;transform:translateX(-50%);padding:8px 16px;background:rgba(255,77,79,0.9);color:#fff;border-radius:8px;font-size:13px;z-index:9999;';
        document.body.appendChild(toast);
      }
      toast.textContent = text;
      setTimeout(() => toast.textContent = '', 4000);
      const u = new URL(location.href);
      u.searchParams.delete('error');
      history.replaceState(null, '', u.pathname + (u.search ? u.search : '') + u.hash);
    }, 300);
  }

  // 3. 401/403 全局处理：清 token + 跳登录
  const origFetch = window.fetch;
  window.fetch = async function(...args) {
    const r = await origFetch.apply(this, args);
    if (r.status === 401 || r.status === 403) {
      const data = await r.clone().json().catch(() => ({}));
      // 仅在非登录页响应 401 时跳转
      const path = location.pathname;
      if (!path.includes('/login') && !path.includes('/auth/')) {
        // 检查响应是否带 code（说明是业务错误）
        if (data && data.code && data.code >= 400) {
          localStorage.removeItem('cs_token');
          // 显示错误后跳登录
          setTimeout(() => {
            const next = path.includes('/agent') ? 'agent' : path.includes('/admin') ? 'admin' : 'customer';
            location.href = '/login/?next=' + next + '&error=' + encodeURIComponent(data.msg || '请重新登录');
          }, 1000);
        }
      }
    }
    return r;
  };

  // 4. 暴露全局工具
  window.csApi = {
    getToken() { return localStorage.getItem('cs_token') || ''; },
    authHeader() {
      const t = localStorage.getItem('cs_token');
      return t ? { 'Authorization': 'Bearer ' + t } : {};
    },
    clearToken() { localStorage.removeItem('cs_token'); },
    showToast(text, type) {
      let toast = document.getElementById('cs-toast');
      if (!toast) {
        toast = document.createElement('div');
        toast.id = 'cs-toast';
        toast.style.cssText = 'position:fixed;top:20px;left:50%;transform:translateX(-50%);padding:8px 16px;background:rgba(0,0,0,0.85);color:#fff;border-radius:8px;font-size:13px;z-index:9999;';
        document.body.appendChild(toast);
      }
      toast.textContent = text;
      if (type === 'error') toast.style.background = 'rgba(255,77,79,0.9)';
      else if (type === 'success') toast.style.background = 'rgba(82,196,26,0.9)';
      else toast.style.background = 'rgba(0,0,0,0.85)';
      clearTimeout(toast._timer);
      toast._timer = setTimeout(() => toast.textContent = '', 2500);
    }
  };
})();