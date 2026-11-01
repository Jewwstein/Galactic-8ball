import JSZip from 'jszip';

const DB='OurLittleUniverse-Web-v1';
const VERSION=1;
let instance;

function req(request){
 return new Promise((resolve,reject)=>{
  request.onsuccess=()=>resolve(request.result);
  request.onerror=()=>reject(request.error||new Error('Storage operation failed'));
 });
}
export async function openStore(){
 if(instance)return instance;
 instance=await new Promise((resolve,reject)=>{
  const request=indexedDB.open(DB,VERSION);
  request.onupgradeneeded=()=>{
   const d=request.result;
   for(const table of ['memories','media','details','items','trash','settings'])
    if(!d.objectStoreNames.contains(table))d.createObjectStore(table,{keyPath:'id'});
  };
  request.onsuccess=()=>resolve(request.result);
  request.onerror=()=>reject(request.error);
 });
 return instance;
}
async function store(name,mode,action){
 const db=await openStore();
 const tx=db.transaction(name,mode),os=tx.objectStore(name);
 return req(action(os));
}
export const get=(name,id)=>store(name,'readonly',os=>os.get(id));
export const all=name=>store(name,'readonly',os=>os.getAll());
export const put=(name,record)=>store(name,'readwrite',os=>os.put(record));
export const drop=(name,id)=>store(name,'readwrite',os=>os.delete(id));

export const uuid=()=>crypto.randomUUID();
export function dateString(date=new Date()){
 const local=new Date(date.getTime()-date.getTimezoneOffset()*60000);
 return local.toISOString().slice(0,10);
}
export async function profile(){
 const saved=await get('settings','profile');
 return saved?.value||{childName:'',parents:'',dueDate:'',contributorName:'Family'};
}
export async function saveProfile(value){return put('settings',{id:'profile',value});}
export async function upsertMemory(record,details={}){
 const id=record.id||uuid();
 const day=record.day||dateString();
 const obj={id,day,kind:record.kind||'Memory',title:record.title||'Untitled memory',
  body:record.body||'',media:[...new Set(record.media||[])]};
 await put('memories',obj);
 const p=await profile();
 const before=(await get('details',id))?.value||{};
 await put('details',{id,value:{author:p.contributorName||'Family',...before,...details}});
 return obj;
}
export async function saveMedia(file){
 const id=uuid()+extension(file.name||'',file.type);
 const bytes=await file.arrayBuffer();
 await put('media',{id,blob:new Blob([bytes],{type:file.type||mediaMime(id)}),size:bytes.byteLength});
 return id;
}
export async function media(name){return (await get('media',name))?.blob||null;}
export function extension(name,mime=''){
 const ext=/\.([a-zA-Z0-9]{1,8})$/.exec(name)?.[0]?.toLowerCase();
 if(ext)return ext;
 if(mime.startsWith('image/'))return '.'+mime.split('/')[1].replace('jpeg','jpg');
 if(mime.startsWith('audio/'))return '.webm';
 if(mime.startsWith('video/'))return '.mp4';
 return '.bin';
}
export function mediaMime(name){
 const ext=name.toLowerCase().split('.').at(-1);
 return {jpg:'image/jpeg',jpeg:'image/jpeg',png:'image/png',webp:'image/webp',
  heic:'image/heic',gif:'image/gif',mp4:'video/mp4',mov:'video/quicktime',
  webm:'video/webm',m4a:'audio/mp4',mp3:'audio/mpeg',wav:'audio/wav',ogg:'audio/ogg'}[ext]||'application/octet-stream';
}
export async function recycleMemory(id){
 const m=await get('memories',id);
 if(!m)return false;
 const meta=(await get('details',id))?.value||{};
 await put('trash',{id,memory:m,details:meta,removedAt:Date.now()});
 await drop('memories',id);
 return true;
}
export async function restoreMemory(id){
 const deleted=await get('trash',id);
 if(!deleted||await get('memories',id))return false;
 await put('memories',deleted.memory);
 await put('details',{id,value:deleted.details});
 await drop('trash',id);
 return true;
}
export async function removeForever(id){
 await drop('trash',id);
 const active=await all('memories'),removed=await all('trash');
 const refs=new Set([...active,...removed.map(x=>x.memory)].flatMap(x=>x.media||[]));
 for(const x of await all('media'))if(!refs.has(x.id))await drop('media',x.id);
}
export async function checksum(blob){
 const bytes=await blob.arrayBuffer();
 const digest=await crypto.subtle.digest('SHA-256',bytes);
 return Array.from(new Uint8Array(digest),x=>x.toString(16).padStart(2,'0')).join('');
}
export async function exportZip({family=false}={}){
 const zip=new JSZip();
 const allMem=await all('memories'),details=await all('details'),items=await all('items'),trash=await all('trash');
 const byId=Object.fromEntries(details.map(x=>[x.id,x.value]));
 const memories=allMem.filter(x=>!family||byId[x.id]?.visibility!=='private');
 const visible=new Set(memories.map(x=>x.id));
 const detailCopy={};
 for(const m of memories)detailCopy[m.id]=byId[m.id]||{};
 const removed=family?[]:trash.map(x=>({memory:x.memory,details:x.details,removedAt:x.removedAt}));
 const ids=new Set([...memories.flatMap(x=>x.media||[]),...removed.flatMap(x=>x.memory?.media||[])]);
 const hashes={};
 for(const name of ids){
  const original=await media(name);
  if(!original)throw new Error('Original attachment missing: '+name+'. Export cancelled.');
  zip.file('media/'+name,original);
  hashes[name]=await checksum(original);
 }
 const p=await profile();
 const manifest={
  format:'little-universe-android-v1',
  settings:{childName:p.childName||'',parents:p.parents||'',dueDate:p.dueDate||''},
  memories:memories.map(x=>({id:x.id,day:x.day,title:x.title,kind:x.kind,body:x.body,media:x.media||[]})),
  features:{details:detailCopy,items:family?items.filter(i=>i.kind!=='Reminder'&&i.kind!=='Private'):items},
  recycleBin:removed,
  mediaSha256:hashes
 };
 zip.file('archive.json',JSON.stringify(manifest));
 return zip.generateAsync({type:'blob',compression:'DEFLATE',compressionOptions:{level:4}});
}
function isSafeMediaName(name){
 return /^[a-zA-Z0-9_-][a-zA-Z0-9_.-]{0,149}$/.test(name)&&!name.includes('..');
}
function validateRecord(m){
 if(!m||typeof m.id!=='string'||!/^[a-zA-Z0-9_-]{1,120}$/.test(m.id)||
    typeof m.title!=='string'||typeof m.day!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(m.day)||
    typeof m.kind!=='string'||typeof m.body!=='string'||!Array.isArray(m.media))
   throw new Error('The ZIP contains an invalid memory record.');
 if(m.title.length>5000||m.body.length>3000000||m.media.some(x=>typeof x!=='string'||!isSafeMediaName(x)))
   throw new Error('The ZIP contains unsafe attachment data.');
}
async function inspectZip(file){
 if(!file||file.size>2*1024*1024*1024)throw new Error('The file is too large for this browser.');
 const zip=await JSZip.loadAsync(file,{checkCRC32:true});
 const entry=zip.file('archive.json');
 if(!entry)throw new Error('Not an Our Little Universe ZIP (archive.json is missing).');
 const text=await entry.async('string');
 if(text.length>32*1024*1024)throw new Error('The archive index is too large.');
 const manifest=JSON.parse(text);
 if(manifest.format!=='little-universe-android-v1'||!Array.isArray(manifest.memories))
  throw new Error('Unsupported archive format.');
 if(manifest.memories.length>60000)throw new Error('Too many memories.');
 const names=new Set();
 for(const m of manifest.memories){validateRecord(m);if(names.has(m.id))throw new Error('Duplicate memory ID in ZIP.');names.add(m.id);}
 const deleted=manifest.recycleBin||[];
 if(!Array.isArray(deleted))throw new Error('Invalid recycle bin data.');
 const required=new Set([...manifest.memories.flatMap(x=>x.media),...deleted.flatMap(x=>x.memory?.media||[])]);
 const extracted=new Map();
 for(const name of required){
  if(!isSafeMediaName(name))throw new Error('Unsafe media filename.');
  const entry=zip.file('media/'+name);
  if(!entry)throw new Error('Missing original media in the ZIP: '+name);
  const bytes=await entry.async('blob');
  if(manifest.mediaSha256?.[name]){
   if(await checksum(bytes)!==manifest.mediaSha256[name].toLowerCase())
    throw new Error('Original media failed SHA-256 check: '+name);
  }
  extracted.set(name,bytes);
 }
 return {manifest,extracted};
}
export async function checkZip(file){
 const {manifest,extracted}=await inspectZip(file);
 return {memories:manifest.memories.length,media:extracted.size,
  removed:(manifest.recycleBin||[]).length,hashes:!!manifest.mediaSha256};
}
export async function importZip(file){
 const {manifest,extracted}=await inspectZip(file);
 // Preflight collisions before writing anything.
 for(const [name,b] of extracted){
  const existing=await media(name);
  if(existing&&await checksum(existing)!==await checksum(b))
   throw new Error('A different original uses the same media filename: '+name+'. Import cancelled.');
 }
 const oldMem=await all('memories'),oldDeleted=await all('trash');
 const live=new Map(oldMem.map(x=>[x.id,x]));
 const recycle=new Set(oldDeleted.map(x=>x.id));
 const incomingDetails=manifest.features?.details||{};
 let added=0,existing=0,attachments=0;
 for(const [name,blob] of extracted){
  if(!await media(name))await put('media',{id:name,blob,size:blob.size});
 }
 for(const incoming of manifest.memories){
  if(recycle.has(incoming.id)){existing++;continue;}
  const mine=live.get(incoming.id);
  if(!mine){
   await put('memories',incoming);
   await put('details',{id:incoming.id,value:incomingDetails[incoming.id]||{author:'Family'}});
   added++;
  }else{
   existing++;
   const merged=[...new Set([...(mine.media||[]),...incoming.media])];
   attachments+=merged.length-(mine.media||[]).length;
   if(merged.length!==(mine.media||[]).length)await put('memories',{...mine,media:merged});
   const old=(await get('details',mine.id))?.value||{};
   const other=incomingDetails[mine.id]||{};
   const join=(a,b,key)=>{
    const seen=new Set();
    return [...(a||[]),...(b||[])].filter(x=>{
     const id=key?x?.[key]:JSON.stringify(x);
     if(seen.has(id))return false;
     seen.add(id);return true;
    });
   };
   const mergedDetails={...other,...old,
    author:old.author||other.author||'Family',
    tags:join(old.tags,other.tags),
    comments:join(old.comments,other.comments,'id'),
    favorite:!!(old.favorite||other.favorite)};
   await put('details',{id:mine.id,value:mergedDetails});
  }
 }
 for(const other of manifest.features?.items||[]){
  if(!other?.id||await get('items',other.id))continue;
  await put('items',{...other,id:other.id});
 }
 for(const entry of manifest.recycleBin||[]){
  if(!entry?.memory?.id)continue;
  const id=entry.memory.id;
  if(await get('memories',id)||await get('trash',id))continue;
  await put('trash',{id,memory:entry.memory,details:entry.details||{},removedAt:entry.removedAt||Date.now()});
 }
 const oldProfile=await profile(),incoming=manifest.settings||{};
 await saveProfile({...oldProfile,
  childName:oldProfile.childName||incoming.childName||'',
  parents:oldProfile.parents||incoming.parents||'',
  dueDate:oldProfile.dueDate||incoming.dueDate||''});
 return {added,existing,attachments,media:extracted.size};
}
export function downloadBlob(blob,filename){
 const url=URL.createObjectURL(blob);
 const a=document.createElement('a');a.href=url;a.download=filename;
 document.body.append(a);a.click();a.remove();
 setTimeout(()=>URL.revokeObjectURL(url),30000);
}
export function normalizeName(value){return String(value||'').replace(/[\\/:*?"<>|]+/g,' ').trim().slice(0,60)||'Memory';}
