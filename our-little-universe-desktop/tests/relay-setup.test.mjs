import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const {deploy,probePublic}=require('../relay-setup.cjs');

test('Existing deployed family Worker is reused after first-run status check fails',async()=>{
 const original=globalThis.fetch;
 const paths=[],submitted=[];
 try{
  globalThis.fetch=async(url,options={})=>{
   const u=String(url);
   paths.push(u);
   if(u.endsWith('/workers/scripts'))
    return Response.json({success:true,result:[{id:'our-little-universe-0123456789',modified_on:'2026-11-01'}]});
   if(u.endsWith('/workers/subdomain'))
    return Response.json({success:true,result:{subdomain:'examplefamily'}});
   if(u.includes('api.cloudflare.com')){
    if(u.endsWith('/workers/scripts/our-little-universe-0123456789')&&options.method==='PUT'){
     const meta=JSON.parse(await options.body.get('metadata').text());
     submitted.push(meta);
    }
    return Response.json({success:true,result:{}});
   }
   if(u.includes('workers.dev/status'))return new Response('Not found',{status:404});
   throw Error('Unexpected URL: '+u);
  };
  const data=await deploy({accountId:'0123456789abcdef0123456789abcdef',
   apiToken:'test-token-that-is-not-a-real-token-abcdef'});
  assert.equal(data.script,'our-little-universe-0123456789');
  assert.equal(data.reused,true);
  assert.equal(data.ready,false);
  assert.match(data.diagnostic,/HTTPS 404/);
  assert.ok(data.secret.length>24);
  assert.equal(submitted.length,1);
  assert.equal(submitted[0].migrations,undefined);
  assert.ok(paths.filter(x=>x.endsWith('/workers/scripts')).length===1);
 }finally{globalThis.fetch=original;}
});

test('Status-check errors are diagnostic, not a reason to discard the saved connection',async()=>{
 const original=globalThis.fetch;
 try{
  globalThis.fetch=async()=>Response.json({app:'Our Little Universe',storage:'none'});
  const result=await probePublic('https://examplefamily.workers.dev',1);
  assert.equal(result.ready,true);
 }finally{globalThis.fetch=original;}
});

test('Status-check returns HTTP diagnostics when hosted Worker returns an error',async()=>{
 const original=globalThis.fetch;
 try{
  globalThis.fetch=async()=>new Response('<html>Not Found</html>',{status:404});
  const result=await probePublic('https://examplefamily.workers.dev',1);
  assert.equal(result.ready,false);
  assert.match(result.diagnostic,/HTTPS 404/);
 }finally{globalThis.fetch=original;}
});
