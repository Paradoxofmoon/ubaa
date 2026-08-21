const SHELL_CACHE = 'ubaa-shell-v2';
const STATIC_CACHE = 'ubaa-static-v2';
const CACHE_NAMES = [SHELL_CACHE, STATIC_CACHE];
const APP_SHELL_ASSETS = [
    './',
    './index.html',
    './manifest.json',
    './styles.css',
    './favicon.ico',
    './favicon-32.png',
    './favicon-16.png',
    './apple-touch-icon.png',
    './pwa-icon-192.png',
    './pwa-icon-512.png'
];

self.addEventListener('install', (event) => {
    event.waitUntil((async () => {
        const cache = await caches.open(SHELL_CACHE);
        await cache.addAll(APP_SHELL_ASSETS);
        await self.skipWaiting();
    })());
});

self.addEventListener('activate', (event) => {
    event.waitUntil((async () => {
        const cacheNames = await caches.keys();
        await Promise.all(
            cacheNames
                .filter((cacheName) => !CACHE_NAMES.includes(cacheName))
                .map((cacheName) => caches.delete(cacheName))
        );
        await self.clients.claim();
    })());
});

self.addEventListener('fetch', (event) => {
    const { request } = event;
    if (request.method !== 'GET') {
        return;
    }

    const requestUrl = new URL(request.url);
    const isSameOrigin = requestUrl.origin === self.location.origin;
    const isApiRequest = requestUrl.pathname.startsWith('/api/');

    if (!isSameOrigin || isApiRequest) {
        return;
    }

    if (request.mode === 'navigate') {
        event.respondWith(handleNavigationRequest(request));
        return;
    }

    if (shouldCacheStaticAsset(requestUrl.pathname)) {
        event.respondWith(handleStaticAssetRequest(event, request));
    }
});

async function handleNavigationRequest(request) {
    try {
        const response = await fetch(request);
        if (response.ok) {
            const cache = await caches.open(SHELL_CACHE);
            cache.put('./index.html', response.clone());
        }
        return response;
    } catch (error) {
        const cached = await caches.match('./index.html');
        if (cached) {
            return cached;
        }
        throw error;
    }
}

async function handleStaticAssetRequest(event, request) {
    const cache = await caches.open(STATIC_CACHE);
    const cached = await cache.match(request);

    const fetchPromise = fetch(request)
        .then((response) => {
            if (response.ok) {
                cache.put(request, response.clone());
            }
            return response;
        })
        .catch(() => null);

    if (cached) {
        event.waitUntil(fetchPromise);
        return cached;
    }

    const response = await fetchPromise;
    if (response) {
        return response;
    }

    const shellCached = await caches.match(request);
    if (shellCached) {
        return shellCached;
    }

    throw new Error(`No cached response available for ${request.url}`);
}

function shouldCacheStaticAsset(pathname) {
    return pathname.includes('/composeResources/') ||
        pathname.endsWith('.js') ||
        pathname.endsWith('.css') ||
        pathname.endsWith('.wasm') ||
        pathname.endsWith('.png') ||
        pathname.endsWith('.ico') ||
        pathname.endsWith('.svg') ||
        pathname.endsWith('.json') ||
        pathname.endsWith('.map');
}
