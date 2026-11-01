'use strict';
// One-time deployment of OUR relay code, directly from Our Little Universe EXE.
// Hosting provider supplies internet reachability only, never a photo database.
// The provider account API token is deliberately not persisted.
const crypto=require('node:crypto'),fs=require('node:fs'),path=require('node:path');
const ENDPOINT='https://api.cloudflare.com/client/v4';
async function deploy({accountId,apiToken,workerName}){
 const id=String(accountId||'').trim(),token=String(apiToken||'').trim();
 if(!/^[a-f0-9]{32}$/i.test(id))
  throw Error('Enter the 32-character account ID from your free relay hosting account.');
 if(token.length<30||token.length>500)
  throw Error('Enter a Workers Scripts Edit API token (not an account password).');
 const secret=crypto.randomBytes(28).toString('base64url');
 // Discover already-deployed versions before creating anything else.
 // The first installer uploaded a Worker but threw away its address on an
 // immediate reachability failure. Reuse that Worker rather than orphan it.
 let script=workerName||'';
 let existing=false;
 const call=async(route,method='GET',body=null)=>{
  const headers={Authorization:'Bearer '+token};
  if(body!==null&&!(body instanceof FormData))headers['Content-Type']='application/json';
  const response=await fetch(ENDPOINT+'/accounts/'+id+route,{
   method,headers,body:body===null?undefined:body instanceof FormData?body:JSON.stringify(body),
   signal:AbortSignal.timeout(60000)
  });
  const payload=await response.json().catch(()=>({success:false,errors:[{message:'Invalid provider API response'}]}));
  if(!response.ok||payload.success===false)throw Error(
   'One-time relay setup: '+(payload.errors||[]).map(e=>e.message).join('; ').slice(0,450)
   ||'Hosting provider rejected the request ('+response.status+')'
  );
  return payload.result;
 };
 if(!script){
  const scripts=await call('/workers/scripts');
  const entries=Array.isArray(scripts)?scripts:Array.isArray(scripts?.scripts)?scripts.scripts:[];
  const matches=entries.filter(x=>/^our-little-universe-[a-f0-9]{10}$/.test(x.id||x.name||''))
   .sort((a,b)=>String(b.modified_on||b.created_on||'').localeCompare(String(a.modified_on||a.created_on||'')));
  if(matches.length){script=matches[0].id||matches[0].name;existing=true;}
  else script='our-little-universe-'+crypto.randomBytes(5).toString('hex');
 }
 if(!/^[a-z0-9-]{5,100}$/.test(script))throw Error('Unexpected relay script name');
 const subdomainInfo=await call('/workers/subdomain');
 const subdomain=subdomainInfo?.subdomain;
 if(!/^[a-z0-9-]{3,100}$/.test(subdomain||''))
  throw Error('Your free relay account needs a workers.dev subdomain activated in the hosting dashboard.');
 const source=fs.readFileSync(path.join(__dirname,'relay','worker.mjs'),'utf8');
 const metadata={
  main_module:'worker.mjs',compatibility_date:'2026-10-30',
  bindings:[{type:'durable_object_namespace',name:'FAMILY',class_name:'FamilyRoom'}],
  ...(existing?{}:{migrations:{new_tag:'family-v1',new_sqlite_classes:['FamilyRoom']}})
 };
 const form=new FormData();
 form.set('metadata',new Blob([JSON.stringify(metadata)],{type:'application/json'}),'metadata.json');
 form.set('worker.mjs',new Blob([source],{type:'application/javascript+module'}),'worker.mjs');
 await call('/workers/scripts/'+script,'PUT',form);
 await call('/workers/scripts/'+script+'/secrets','PUT',{name:'HUB_SECRET',type:'secret_text',text:secret});
 await call('/workers/scripts/'+script+'/subdomain','POST',{enabled:true,previews_enabled:false});
 const url='https://'+script+'.'+subdomain+'.workers.dev';
 // The hosting provider can activate a new workers.dev hostname after API
 // deployment has succeeded. A transient 404 must not discard the new
 // secret and leave an orphaned Worker behind.
 const probe=await probePublic(url);
 return {url,secret,script,ready:probe.ready,diagnostic:probe.diagnostic,reused:existing};
}
async function probePublic(url,attempts=3){
 let last='No connection was returned';
 for(let i=0;i<attempts;i++){
  if(i)await new Promise(resolve=>setTimeout(resolve,i*1800));
  try{
   const response=await fetch(url+'/status',{cache:'no-store',signal:AbortSignal.timeout(6000)});
   const body=(await response.text()).slice(0,600);
   if(response.status===200){
    let details;try{details=JSON.parse(body);}catch{}
    if(details?.app==='Our Little Universe'&&details?.storage==='none')
     return {ready:true,diagnostic:'Our Little Universe relay is responding'};
    last='HTTP 200 but the relay status response was not recognized';
   }else{
    last='HTTPS '+response.status+' '+body.replace(/<[^>]*>/g,' ').replace(/\s+/g,' ').slice(0,140);
   }
  }catch(error){last='Connection check failed: '+String(error.message||error).slice(0,150);}
 }
 return {ready:false,diagnostic:last};
}
module.exports={deploy,probePublic};
