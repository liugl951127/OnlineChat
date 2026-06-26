/**
 * Service Worker（v1.9.0 PWA）
 *
 * 策略：
 * 1. 静态资源：Cache First
 * 2. API 请求：Network First（失败降级到 Cache）
 */
const CACHE_NAME = 'onlinechat-v1.9.0';
const STATIC_ASSETS = ['/', '/index.html', '/manifest.webmanifest'];

self.addEventListener('install', (event) => {
    event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(STATIC_ASSETS)));
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys().then((keys) =>
            Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
        )
    );
    self.clients.claim();
});

self.addEventListener('fetch', (event) => {
    const url = new URL(event.request.url);
    if (event.request.method !== 'GET') return;
    if (url.origin !== self.location.origin) return;

    if (url.pathname.startsWith('/auth/') ||
        url.pathname.startsWith('/im/') ||
        url.pathname.startsWith('/risk/') ||
        url.pathname.startsWith('/order/') ||
        url.pathname.startsWith('/product/') ||
        url.pathname.startsWith('/robot/') ||
        url.pathname.startsWith('/faq/') ||
        url.pathname.startsWith('/ticket/') ||
        url.pathname.startsWith('/replay/')) {
        event.respondWith(
            fetch(event.request)
                .then((res) => {
                    const cloned = res.clone();
                    caches.open(CACHE_NAME).then((cache) => cache.put(event.request, cloned));
                    return res;
                })
                .catch(() => caches.match(event.request))
        );
        return;
    }
    event.respondWith(caches.match(event.request).then((cached) => cached || fetch(event.request)));
});