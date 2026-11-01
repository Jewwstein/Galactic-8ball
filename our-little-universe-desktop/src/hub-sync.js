// Family Sync client: no cloud storage, automatic incremental media transfers.
// Network traffic travels only to the Windows owner's paired private hub.
import {all,get,put,media,mediaMime,profile} from './store.js';
const KEY='familyHub';
const now=()=>new Date().toISOString();
let inflight=null;
const safeURL=value=>{
 const url=new URL(String(value||'').trim().replace(/\/+$/,'')+'/');
 if(url.username||url.password||url.search||url.hash)throw Error('Invalid hub address');
 const local=url.hostname==='127.0.0.1'||url.hostname==='localhost';
 if(url.protocol!=='https:'&&!(local&&url.protocol==='http:'))
  throw Error('Remote family sync requires a private HTTPS address');
 return url.origin;
};
export async function currentHub(){return (await get('settings',KEY))?.value||null;}
async function remember(value){await put('settings',{id:KEY,value});}
async function request(hub,pathname,{method='GET',body,timeout=90000}={}){
 const ctrl=new AbortController(),alarm=setTimeout(()=>ctrl.abort(),timeout);
 try{
  const response=await fetch(hub.url+pathname,{
   method,headers:{Authorization:'Bearer '+hub.token,
    ...(body!==undefined&&! (body instanceof Blob)?{'Content-Type':'application/json'}:{})},
   body:body instanceof Blob?body:body===undefined?undefined:JSON.stringify(body),
   cache:'no-store',signal:ctrl.signal
  });
  if(!response.ok){
   let err;
   try{err=(await response.json()).error;}catch{}
   throw Error(err||'Family hub request failed: '+response.status);
  }
  if(response.headers.get('content-type')?.includes('application/json'))return response.json();
  return response.blob();
 }finally{clearTimeout(alarm);}
}
export async function setHostHub(url,token){
 if(!token)throw Error('Hub owner token is not available');
 const base=safeURL(url),existing=await currentHub();
 const hub={url:base,token,role:'admin',
  cursor:(existing?.url===base&&existing?.token===token)?existing.cursor||0:0,connectedAt:now()};
 await remember(hub);return hub;
}
export async function pairHub(url,code,label){
 const base=safeURL(url);
 if(!String(code||'').trim())throw Error('Enter the family invitation code');
 const response=await fetch(base+'/api/v1/pair',{
  method:'POST',headers:{'Content-Type':'application/json'},
  body:JSON.stringify({code:String(code).trim(),label:String(label||'Family member').slice(0,64)})
 });
 const data=await response.json();
 if(!response.ok)throw Error(data.error||'Pairing was not approved');
 const hub={url:base,token:data.token,id:data.id,role:data.role,cursor:0,connectedAt:now()};
 await remember(hub);return hub;
}
export async function clearHub(){
 await remember({url:'',token:'',cursor:0,disabled:true});
}
async function mediaChunks(hub,mediaNames,progress){
 const list=[...new Set(mediaNames)];
 let uploaded=0;
 for(let i=0;i<list.length;i+=300){
  const block=list.slice(i,i+300);
  const missing=(await request(hub,'/api/v1/media/missing',{method:'POST',body:{names:block}})).missing;
  for(const name of missing){
   const original=await media(name);
   if(!original)throw Error('Original media missing locally: '+name);
   await request(hub,'/api/v1/media/'+encodeURIComponent(name),{
    method:'PUT',body:original,timeout:Math.max(90000,original.size/100000*2000)});
   uploaded++;
   if(progress)progress({status:'Uploading original attachments',uploaded});
  }
 }
 return uploaded;
}
function mergedDetails(local={},incoming={}){
 const m={...incoming,...local};
 const union=(a,b,key)=>{
  const seen=new Set();
  return [...(a||[]),...(b||[])].filter(x=>{
   const identity=key?x?.[key]:JSON.stringify(x);
   if(seen.has(identity))return false;seen.add(identity);return true;
  });
 };
 m.tags=union(local.tags,incoming.tags);
 m.comments=union(local.comments,incoming.comments,'id');
 m.favorite=!!(local.favorite||incoming.favorite);
 return m;
}
async function push(hub,progress){
 if(hub.role==='viewer')return {media:0,sent:0};
 const memories=(await all('memories'));
 const details=Object.fromEntries((await all('details')).map(x=>[x.id,x.value||{}]));
 const allowed=memories.filter(m=>details[m.id]?.visibility!=='private');
 const items=(await all('items')).filter(x=>x.kind!=='Reminder'&&x.kind!=='Private');
 const mediaNames=allowed.flatMap(x=>x.media||[]);
 const mediaCount=await mediaChunks(hub,mediaNames,progress);
 let pushed=0;
 for(let i=0;i<allowed.length;i+=100){
  const chunk=allowed.slice(i,i+100);
  await request(hub,'/api/v1/records',{
   method:'POST',body:{
    memories:chunk.map(m=>({memory:m,details:details[m.id]||{}})),
    items:i===0?items:[]
   }
  });
  pushed+=chunk.length;
 }
 if(!allowed.length&&items.length)
  await request(hub,'/api/v1/records',{method:'POST',body:{memories:[],items}});
 return {media:mediaCount,sent:pushed};
}
async function pull(hub,progress){
 // A sync cursor avoids downloading other family members' entire archive repeatedly.
 const since=hub.cursor||0;
 const data=await request(hub,'/api/v1/changes?since='+since);
 let received=0,downloaded=0;
 for(const record of data.memories||[]){
  const incoming=record.memory;
  if(!incoming?.id)continue;
  const local=await get('memories',incoming.id);
  const allFiles=[...new Set([...(local?.media||[]),...(incoming.media||[])])];
  for(const name of incoming.media||[]){
   if(await media(name))continue;
   const blob=await request(hub,'/api/v1/media/'+encodeURIComponent(name),{timeout:300000});
   await put('media',{id:name,blob:new Blob([blob],{type:mediaMime(name)}),size:blob.size});
   downloaded++;
   if(progress)progress({status:'Receiving family photos',downloaded});
  }
  if(!local){
   await put('memories',incoming);received++;
  }else if(allFiles.length!==(local.media||[]).length){
   // Existing words are never silently overwritten by family edits.
   await put('memories',{...local,media:allFiles});
  }
  const d=(await get('details',incoming.id))?.value||{};
  await put('details',{id:incoming.id,value:mergedDetails(d,record.details||{})});
 }
 for(const item of data.items||[]){
  if(!item?.id||await get('items',item.id))continue;
  await put('items',item);
 }
 hub.cursor=data.seq||hub.cursor||0;
 hub.lastSynced=now();
 await remember(hub);
 return {received,downloaded};
}
export async function syncNow(progress){
 if(inflight)return inflight;
 inflight=(async()=>{
  const hub=await currentHub();
  if(!hub?.token||!hub.url||hub.disabled)throw Error('Pair this device with your Windows family hub first');
  if(progress)progress({status:'Connecting to private family hub…'});
  await request(hub,'/api/v1/info',{timeout:13000});
  // Send local changes first so this device's new memories reach the family.
  const sent=await push(hub,progress);
  if(progress)progress({status:'Checking for new family memories…'});
  const got=await pull(hub,progress);
  const summary={...sent,...got,lastSynced:hub.lastSynced};
  document.dispatchEvent(new CustomEvent('our-family-synced',{detail:summary}));
  return summary;
 })();
 try{return await inflight;}finally{inflight=null;}
}
export function beginAutoSync(onState){
 let stopped=false;
 const interval=setInterval(async()=>{
  if(stopped||document.visibilityState==='hidden')return;
  const hub=await currentHub();
  if(!hub?.token||hub.disabled)return;
  try{const result=await syncNow();onState?.({ok:true,result});}
  catch(error){onState?.({ok:false,error:error.message});}
 },120000);
 document.addEventListener('visibilitychange',async()=>{
  if(stopped||document.visibilityState==='hidden')return;
  const hub=await currentHub();if(!hub?.token||hub.disabled)return;
  try{const result=await syncNow();onState?.({ok:true,result});}
  catch(error){onState?.({ok:false,error:error.message});}
 });
 return ()=>{stopped=true;clearInterval(interval);};
}
