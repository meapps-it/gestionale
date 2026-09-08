const CACHE='gestionale-commercial-v118';
const CORE=[
  './',
  './index.html',
  './manifest.json',
  './favicon-v1-32.png',
  './icon-v1-192.png',
  './icon-v1-512.png',
  './icon-v1-maskable-512.png',
  './apple-touch-icon-v1.png',
  './supabaseClient.js'
];
self.addEventListener('install',event=>{
  event.waitUntil(caches.open(CACHE).then(c=>c.addAll(CORE)).catch(()=>{}));
  self.skipWaiting();
});
self.addEventListener('activate',event=>{
  event.waitUntil(
    caches.keys().then(keys=>Promise.all(
      keys.filter(k=>k.startsWith('gestionale-commercial-')&&k!==CACHE).map(k=>caches.delete(k))
    ))
  );
  self.clients.claim();
});
self.addEventListener('fetch',event=>{
  const req=event.request;
  if(req.method!=='GET') return;
  const url=new URL(req.url);
  const same=url.origin===self.location.origin;
  const isNav=req.mode==='navigate' || /\/(?:index\.html|manifest\.json)$/.test(url.pathname);
  if(isNav){
    event.respondWith(fetch(req,{cache:'no-store'}).then(r=>{
      const copy=r.clone(); caches.open(CACHE).then(c=>c.put(req,copy)).catch(()=>{});
      return r;
    }).catch(()=>caches.match(req).then(r=>r||caches.match('./index.html'))));
    return;
  }
  if(same && url.pathname.includes('/demo-photos/')){
    event.respondWith(caches.match(req).then(hit=>hit||fetch(req).then(r=>{
      const copy=r.clone(); caches.open(CACHE).then(c=>c.put(req,copy)).catch(()=>{});
      return r;
    })));
  }
});
