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
 const script=workerName||('our-little-universe-'+crypto.randomBytes(5).toString('hex'));
 if(!/^[a-z0-9-]{5,100}$/.test(script))throw Error('Invalid relay script name');
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
 const subdomainInfo=await call('/workers/subdomain');
 const subdomain=subdomainInfo?.subdomain;
 if(!/^[a-z0-9-]{3,100}$/.test(subdomain||''))
  throw Error('Your free relay account needs a workers.dev subdomain activated in the hosting dashboard.');
 const source=fs.readFileSync(path.join(__dirname,'relay','worker.mjs'),'utf8');
 const metadata={
  main_module:'worker.mjs',compatibility_date:'2026-10-30',
  bindings:[{type:'durable_object_namespace',name:'FAMILY',class_name:'FamilyRoom'}],
  migrations:{new_tag:'family-v1',new_sqlite_classes:['FamilyRoom']}
 };
 const form=new FormData();
 form.set('metadata',new Blob([JSON.stringify(metadata)],{type:'application/json'}),'metadata.json');
 form.set('worker.mjs',new Blob([source],{type:'application/javascript+module'}),'worker.mjs');
 await call('/workers/scripts/'+script,'PUT',form);
 await call('/workers/scripts/'+script+'/secrets','PUT',{name:'HUB_SECRET',type:'secret_text',text:secret});
 await call('/workers/scripts/'+script+'/subdomain','POST',{enabled:true,previews_enabled:false});
 const url='https://'+script+'.'+subdomain+'.workers.dev';
 const test=await fetch(url+'/status',{signal:AbortSignal.timeout(15000)}).catch(()=>null);
 if(!test?.ok)throw Error('Relay was deployed, but its public address has not become reachable yet. Check the Worker in your account.');
 const details=await test.json();
 if(details.app!=='Our Little Universe'||details.storage!=='none')
  throw Error('The deployed relay returned an unexpected response.');
 return {url,secret,script};
}
module.exports={deploy};
