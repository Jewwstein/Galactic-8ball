// Our Little Universe first-party transport relay.
// No photo database. No queues, uploads, or persistent family content.
// Only forwards authenticated app requests to the owner's Windows Hub.
// Hosted on a free Worker with a SQLite-backed Durable Object (state unused).
const LIMIT=512*1024;
const allowedOrigins=new Set(['https://jewwstein.github.io','null']);
const cors=origin=>({
 'Access-Control-Allow-Origin':allowedOrigins.has(origin)?origin:'https://jewwstein.github.io',
 'Access-Control-Allow-Headers':'Authorization,Content-Type',
 'Access-Control-Allow-Methods':'GET,POST,PUT,OPTIONS',
 'Access-Control-Expose-Headers':'X-File-Size,X-File-SHA256,Content-Length',
 'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Vary':'Origin'
});
function jsonError(code,message,origin=''){
 return new Response(JSON.stringify({error:message}),{status:code,
  headers:{'Content-Type':'application/json',...cors(origin)}});
}
function b64(buffer){
 const bytes=new Uint8Array(buffer),size=8192;let result='';
 for(let i=0;i<bytes.length;i+=size)
  result+=String.fromCharCode(...bytes.subarray(i,i+size));
 return btoa(result);
}
function from64(encoded){
 const s=atob(encoded||''),b=new Uint8Array(s.length);
 for(let i=0;i<s.length;i++)b[i]=s.charCodeAt(i);
 return b;
}
export default {
 async fetch(request,env){
  const url=new URL(request.url),origin=request.headers.get('origin')||'';
  if(origin&&!allowedOrigins.has(origin))
   return jsonError(403,'Only the family app may contact this connection',origin);
  if(request.method==='OPTIONS')return new Response(null,{status:204,headers:cors(origin)});
  if(url.pathname==='/status')
   return new Response(JSON.stringify({app:'Our Little Universe',version:1,storage:'none'}),
    {headers:{'Content-Type':'application/json',...cors(origin)}});
  const isSocket=url.pathname==='/connect';
  if(isSocket){
   if(request.headers.get('Upgrade')?.toLowerCase()!=='websocket')
    return jsonError(426,'Windows Hub WebSocket required',origin);
   const key=request.headers.get('Sec-WebSocket-Protocol')||'';
   if(!key||key!=='olu-'+env.HUB_SECRET)return jsonError(403,'Unrecognized family owner',origin);
  }else if(!url.pathname.startsWith('/api/v1/')){
   return jsonError(404,'Unknown connection route',origin);
  }
  try{
   const room=env.FAMILY.getByName('owner-pc');
   const response=await room.fetch(request);
   if(isSocket)return response;
   const headers=new Headers(response.headers);
   for(const [name,value] of Object.entries(cors(origin)))headers.set(name,value);
   return new Response(response.body,{status:response.status,headers});
  }catch(error){return jsonError(503,'Family relay temporarily unavailable',origin);}
 }
};
export class FamilyRoom {
 constructor(ctx,env){
  this.ctx=ctx;this.env=env;this.pending=new Map();
 }
 owner(){
  return this.ctx.getWebSockets().find(ws=>ws.deserializeAttachment()?.role==='owner');
 }
 async fetch(request){
  const pathname=new URL(request.url).pathname;
  if(pathname==='/connect'){
   const previous=this.owner();
   if(previous)try{previous.close(4000,'Owner reconnected');}catch{}
   const [client,server]=Object.values(new WebSocketPair());
   this.ctx.acceptWebSocket(server);
   server.serializeAttachment({role:'owner'});
   return new Response(null,{status:101,webSocket:client,headers:{
    'Sec-WebSocket-Protocol':request.headers.get('Sec-WebSocket-Protocol')||''}});
  }
  const owner=this.owner();
  if(!owner)return jsonError(503,'Family PC is offline');
  const body=await request.arrayBuffer();
  if(body.byteLength>LIMIT)return jsonError(413,'Split large attachment into smaller chunks');
  if(this.pending.size>30)return jsonError(503,'Family PC is busy; retry');
  const id=crypto.randomUUID();
  const upstream=new URL(request.url);
  const outgoing={type:'request',id,method:request.method,
   path:upstream.pathname+upstream.search,body:b64(body),headers:{
    'authorization':request.headers.get('Authorization')||'',
    'content-type':request.headers.get('Content-Type')||'',
    'x-family-origin':request.headers.get('Origin')||''
   }};
  const answer=new Promise((resolve,reject)=>{
   const timer=setTimeout(()=>{this.pending.delete(id);reject(Error('timeout'));},55000);
   this.pending.set(id,payload=>{
    clearTimeout(timer);this.pending.delete(id);resolve(payload);
   });
  });
  try{owner.send(JSON.stringify(outgoing));}
  catch{
   this.pending.delete(id);return jsonError(503,'Family PC disconnected');
  }
  let result;
  try{result=await answer;}catch{return jsonError(504,'Family PC did not respond');}
  if(!result||typeof result.status!=='number'||!Number.isInteger(result.status))
   return jsonError(502,'Invalid response from Family PC');
  const headers=new Headers();
  for(const name of ['content-type','x-file-sha256','x-file-size']){
   if(result.headers?.[name])headers.set(name,result.headers[name]);
  }
  headers.set('Cache-Control','no-store');
  try{return new Response(from64(result.body),{status:result.status,headers});}
  catch{return jsonError(502,'Corrupted response from Family PC');}
 }
 async webSocketMessage(ws,message){
  if(ws.deserializeAttachment()?.role!=='owner'||typeof message!=='string')return;
  if(message.length>2*1024*1024){try{ws.close(1009,'Message too large');}catch{}return;}
  let data;try{data=JSON.parse(message);}catch{return;}
  if(data.type!=='reply'||typeof data.id!=='string')return;
  this.pending.get(data.id)?.(data);
 }
 async webSocketClose(ws){
  if(ws.deserializeAttachment()?.role==='owner'){
   for(const [id,callback] of this.pending)
    callback({status:503,body:b64(new TextEncoder().encode(JSON.stringify({
     error:'Family PC disconnected'}))),headers:{'content-type':'application/json'}});
   this.pending.clear();
  }
 }
}
