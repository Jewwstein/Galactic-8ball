'use strict';
// Built-in Windows relay client. Outbound WSS only; never changes router,
// creates a VPN interface, or routes traffic from any other app.
// Uses Node/Electron's native WebSocket and local HTTP APIs.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const http=require('node:http');
const LIMIT=512*1024,MAX_MESSAGE=2*1024*1024;
function agent(directory,{onState=()=>{}}={}){
 fs.mkdirSync(directory,{recursive:true});
 const configFile=path.join(directory,'family-relay.json');
 let config=null;
 try{config=JSON.parse(fs.readFileSync(configFile,'utf8'));}catch{}
 let socket=null,stopped=false,timer=null,retry=0,online=false;
 const state=()=>({configured:!!config?.url,connected:online,url:config?.url||'',error:lastError});
 let lastError='';
 const update=message=>{lastError=message||'';onState(state());};
 function save(settings){
  const temp=configFile+'.tmp';
  fs.writeFileSync(temp,JSON.stringify(settings),{mode:0o600});
  fs.renameSync(temp,configFile);config=settings;
 }
 async function handle(message){
  if(!socket||socket.readyState!==WebSocket.OPEN)return;
  let incoming;
  try{incoming=JSON.parse(message);}catch{return;}
  if(incoming.type!=='request'||typeof incoming.id!=='string'||incoming.id.length>100)return;
  const id=incoming.id;
  const respond=(status,data,headers={})=>{
   if(!socket||socket.readyState!==WebSocket.OPEN)return;
   const payload=JSON.stringify({type:'reply',id,status,body:Buffer.from(data||'').toString('base64'),
    headers});
   if(payload.length<MAX_MESSAGE)socket.send(payload);
  };
  try{
   if(!/^\/api\/v1\/[A-Za-z0-9/?&_.=%-]*$/.test(incoming.path||''))
    throw Error('Invalid family API route');
   if(!['GET','PUT','POST'].includes(incoming.method))throw Error('Invalid method');
   const bytes=Buffer.from(incoming.body||'','base64');
   if(bytes.length>LIMIT)throw Error('Family request too large');
   const headers={};
   for(const field of ['authorization','content-type'])
    if(typeof incoming.headers?.[field]==='string'&&incoming.headers[field].length<2048)
     headers[field]=incoming.headers[field];
   // Don't rely on any claimed visitor identity. Local Hub independently
   // authenticates every bearer token and checks the family member role.
   const request=http.request('http://127.0.0.1:43877'+incoming.path,
    {method:incoming.method,headers,timeout:40000},res=>{
     const chunks=[];let total=0;
     res.on('data',buf=>{
      total+=buf.length;
      if(total>LIMIT){res.destroy(Error('Response exceeded relay chunk limit'));return;}
      chunks.push(buf);
     });
     res.on('end',()=>respond(res.statusCode,Buffer.concat(chunks),{
      'content-type':res.headers['content-type']||'application/octet-stream',
      'x-file-sha256':res.headers['x-file-sha256']||'',
      'x-file-size':res.headers['x-file-size']||''
     }));
    });
   request.on('timeout',()=>request.destroy(Error('Local family hub timed out')));
   request.on('error',error=>respond(502,JSON.stringify({error:error.message}),{
    'content-type':'application/json'}));
   if(bytes.length)request.write(bytes);
   request.end();
  }catch(error){
   respond(400,JSON.stringify({error:error.message}),{'content-type':'application/json'});
  }
 }
 function connect(){
  if(stopped||!config?.url||socket)return;
  const address=config.url.replace(/^https:/,'wss:')+'/connect';
  try{socket=new WebSocket(address,'olu-'+config.secret);}catch(error){
   socket=null;update(error.message);schedule();return;
  }
  const current=socket;
  current.addEventListener('open',()=>{
   if(current!==socket)return;
   online=true;retry=0;update('');
  });
  current.addEventListener('message',event=>{
   if(current!==socket||typeof event.data!=='string')return;
   handle(event.data).catch(error=>update(error.message));
  });
  current.addEventListener('error',()=>{if(!online)update('Cannot connect to family relay');});
  current.addEventListener('close',event=>{
   if(current!==socket)return;
   socket=null;online=false;
   update('Connection paused ('+(event.code||'network')+'); retrying automatically');
   schedule();
  });
 }
 function schedule(){
  if(stopped||timer)return;
  retry=Math.min(retry+1,8);
  const wait=Math.min(30000,1000*Math.pow(2,retry));
  timer=setTimeout(()=>{timer=null;connect();},wait);
 }
 function stop(){
  stopped=true;clearTimeout(timer);timer=null;
  if(socket)try{socket.close();}catch{}
  socket=null;online=false;
 }
 function start(){
  stopped=false;connect();return state();
 }
 function configure(settings){
  if(!/^https:\/\/[a-z0-9-]+(?:\.[a-z0-9-]+)*\.workers\.dev$/.test(settings.url))
   throw Error('Invalid family relay address');
  if(!/^[A-Za-z0-9_-]{24,128}$/.test(settings.secret))
   throw Error('Invalid family relay key');
  stop();save({url:settings.url,secret:settings.secret,script:settings.script||''});
  return start();
 }
 return {configure,start,stop,state};
}
module.exports={agent};
