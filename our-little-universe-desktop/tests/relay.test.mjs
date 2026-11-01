import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import os from 'node:os';
import fs from 'node:fs';
import path from 'node:path';
const require=createRequire(import.meta.url);
const {hub}=require('../hub-server.cjs');

test('Chunked family originals survive relay-sized transfers with exact checksums',async()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'olu-media-relay-'));
 const server=hub(dir);
 const {port,adminToken}=await server.start(0);
 const base='http://127.0.0.1:'+port;
 const headers={Authorization:'Bearer '+adminToken,'Content-Type':'application/octet-stream'};
 const bytes=Buffer.alloc(430000);
 for(let i=0;i<bytes.length;i++)bytes[i]=i%251;
 try{
  for(let offset=0;offset<bytes.length;offset+=196608){
   const part=bytes.subarray(offset,offset+196608);
   const result=await fetch(base+'/api/v1/media/video-test.mp4?offset='+offset+'&total='+bytes.length,{
    method:'PUT',headers,body:part});
   assert.equal(result.status,200,await result.text());
  }
  const metaRes=await fetch(base+'/api/v1/media/video-test.mp4?meta=1',{headers});
  assert.equal(metaRes.status,200);
  const meta=await metaRes.json();assert.equal(meta.size,bytes.length);
  const received=[];
  for(let offset=0;offset<meta.size;offset+=196608){
   const response=await fetch(base+'/api/v1/media/video-test.mp4?offset='+offset+'&limit=196608',{headers});
   assert.equal(response.status,200);
   received.push(Buffer.from(await response.arrayBuffer()));
  }
  const all=Buffer.concat(received);
  assert.equal(Buffer.compare(all,bytes),0);
  const crypto=require('node:crypto');
  assert.equal(meta.sha256,crypto.createHash('sha256').update(all).digest('hex'));
  // A different original cannot overwrite existing family media.
  const conflict=await fetch(base+'/api/v1/media/video-test.mp4?offset=0&total=42',{
   method:'PUT',headers,body:Buffer.from('test')});
  assert.equal(conflict.status,409);
 }finally{await server.stop();fs.rmSync(dir,{recursive:true,force:true});}
});

test('Our relay forwards only short-lived HTTP requests and stores no archive',async()=>{
 // Cloudflare supplies the WebSocket runtime; mocked here to test our routing logic.
 const worker=await import('../relay/worker.mjs');
 class ServerSocket {
  constructor(){this.data=null;this.sent=[];}
  deserializeAttachment(){return this.data;}
  serializeAttachment(d){this.data=d;}
  send(message){this.sent.push(JSON.parse(message));}
  close(){}
 }
 const socket=new ServerSocket();socket.serializeAttachment({role:'owner'});
 const context={getWebSockets:()=>[socket],acceptWebSocket:()=>{}};
 const room=new worker.FamilyRoom(context,{HUB_SECRET:'testing'});
 const handler=room.fetch(new Request('https://relay.example/api/v1/info',{
  method:'GET',headers:{Authorization:'Bearer abc'}
 }));
 while(!socket.sent.length)await new Promise(resolve=>setTimeout(resolve,1));
 assert.equal(socket.sent[0].path,'/api/v1/info');
 assert.equal(socket.sent[0].headers.authorization,'Bearer abc');
 await room.webSocketMessage(socket,JSON.stringify({type:'reply',id:socket.sent[0].id,
  status:200,body:Buffer.from('{"online":true}').toString('base64'),headers:{'content-type':'application/json'}}));
 const result=await handler;
 assert.equal(result.status,200);
 assert.equal((await result.json()).online,true);
 assert.equal(room.pending.size,0);
 const offline=new worker.FamilyRoom({getWebSockets:()=>[]},{});
 assert.equal((await offline.fetch(new Request('https://relay.example/api/v1/info'))).status,503);
 const api=worker.default;
 assert.equal((await api.fetch(new Request('https://relay.example/api/v1/info',{
  headers:{Origin:'https://evil.example'}}),{})).status,403);
});
