import {test} from 'node:test';
import assert from 'node:assert/strict';
import os from 'node:os';
import path from 'node:path';
import fs from 'node:fs';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const {hub,normalizeRecord,joinDetails}=require('../hub-server.cjs');

test('Windows owner hub pairs family members and synchronizes original media',async()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'our-little-universe-hub-'));
 let server=hub(dir);
 const first=await server.start(0);
 const url='http://127.0.0.1:'+first.port;
 const fetchJson=async(route,token,method='GET',body)=>{
  const r=await fetch(url+route,{method,headers:{
   ...(token?{Authorization:'Bearer '+token}:{}),
   ...(body?{'Content-Type':'application/json'}:{})
  },body:body?JSON.stringify(body):undefined});
  return {status:r.status,payload:await r.json()};
 };
 try{
  const unauth=await fetchJson('/api/v1/info');
  assert.equal(unauth.status,401);
  const health=await fetchJson('/api/v1/health');
  assert.equal(health.payload.online,true);
  const issued=await fetchJson('/api/v1/invite',first.adminToken,'POST',{label:'Grandma',role:'contributor'});
  assert.equal(issued.status,200);
  const wrong=await fetchJson('/api/v1/pair',null,'POST',{code:'bad',label:'Intruder'});
  assert.equal(wrong.status,403);
  const join=await fetchJson('/api/v1/pair',null,'POST',{code:issued.payload.code,label:'Grandma'});
  assert.equal(join.status,200);
  const token=join.payload.token;
  assert.ok(token.length>32);
  assert.equal((await fetchJson('/api/v1/pair',null,'POST',{code:issued.payload.code})).status,403);
  const bytes=Buffer.from('Our original photo never leaves the family hub','utf8');
  const uploaded=await fetch(url+'/api/v1/media/test-photo.png',{method:'PUT',
   headers:{Authorization:'Bearer '+token},body:bytes});
  assert.equal(uploaded.status,200);
  const record={id:'f98a8136-90ba-4b4a-a6d1-3b866e4ed98a',day:'2026-10-31',
   title:'The day everything changed',kind:'Memory',body:'We found out',media:['test-photo.png']};
  const accepted=await fetchJson('/api/v1/records',token,'POST',
   {memories:[{memory:record,details:{author:'Grandma',tags:['family']}}],items:[]});
  assert.equal(accepted.payload.added,1);
  const changes=await fetchJson('/api/v1/changes?since=0',first.adminToken);
  assert.equal(changes.payload.memories.length,1);
  assert.equal(changes.payload.memories[0].memory.title,record.title);
  const reused=await fetchJson('/api/v1/records',token,'POST',
   {memories:[{memory:record,details:{author:'Grandma',tags:['family']}}],items:[]});
  assert.equal(reused.payload.added,0);
  const original=await fetch(url+'/api/v1/media/test-photo.png',{
   headers:{Authorization:'Bearer '+first.adminToken}});
  assert.equal(Buffer.compare(Buffer.from(await original.arrayBuffer()),bytes),0);
  assert.equal(original.headers.get('x-file-sha256').length,64);
  const secondInfo=await fetchJson('/api/v1/info',first.adminToken);
  assert.equal(secondInfo.payload.memoryCount,1);
  const rejected=await fetch(url+'/api/v1/media/..%2fsecrets',{
   headers:{Authorization:'Bearer '+token}});
  assert.notEqual(rejected.status,200);
  const revoked=await fetchJson('/api/v1/revoke',first.adminToken,'POST',{id:join.payload.id});
  assert.equal(revoked.payload.revoked,true);
  assert.equal((await fetchJson('/api/v1/info',token)).status,401);
 }finally{
  await server.stop();
 }
 // Local owner access must survive restarting the Windows EXE.
 server=hub(dir);
 const again=await server.start(0);
 try{
  assert.equal(again.adminToken,first.adminToken);
  const resp=await fetch('http://127.0.0.1:'+again.port+'/api/v1/changes?since=0',{
   headers:{Authorization:'Bearer '+again.adminToken}});
  assert.equal((await resp.json()).memories.length,1);
 }finally{await server.stop();fs.rmSync(dir,{recursive:true,force:true});}
});
test('Hub validates memory IDs and preserves local wording',()=>{
 assert.throws(()=>normalizeRecord({id:'../escape',title:'X',kind:'Memory',day:'2026-10-31',body:'',media:[]}));
 const merged=joinDetails({author:'Dad',tags:['First']},{author:'Grandma',tags:['First','Family']});
 assert.equal(merged.author,'Dad');
 assert.deepEqual(merged.tags,['First','Family']);
});
