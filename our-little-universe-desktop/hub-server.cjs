'use strict';
// Local family hub. Receives remote traffic ONLY via an HTTPS reverse proxy
// such as Tailscale Serve. Never bind this HTTP endpoint to 0.0.0.0.
const http=require('node:http'),https=require('node:https'),fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const PORT=43877;
const SAFE=/^[A-Za-z0-9_-][A-Za-z0-9_.-]{0,149}$/;
const ID=/^[A-Za-z0-9_-]{1,120}$/;
const hash=v=>crypto.createHash('sha256').update(Buffer.isBuffer(v)?v:String(v)).digest('hex');
const random=()=>crypto.randomBytes(32).toString('base64url');
async function hashFile(file){
 const sha=crypto.createHash('sha256');
 for await(const bytes of fs.createReadStream(file))sha.update(bytes);
 return sha.digest('hex');
}
function saveAtomic(file,obj){
 const tmp=file+'.tmp-'+process.pid+'-'+crypto.randomBytes(3).toString('hex');
 fs.writeFileSync(tmp,JSON.stringify(obj),{mode:0o600});
 fs.renameSync(tmp,file);
}
function readJSON(file,fallback){
 try{return JSON.parse(fs.readFileSync(file,'utf8'));}catch(e){
  if(e.code==='ENOENT')return fallback;
  throw e;
 }
}
function normalizeRecord(m){
 if(!m||!ID.test(m.id||'')||typeof m.day!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(m.day)||
  typeof m.title!=='string'||typeof m.kind!=='string'||typeof m.body!=='string'||
  !Array.isArray(m.media)||m.title.length>5000||m.body.length>3e6||
  m.kind.length>100||m.media.length>5000||m.media.some(x=>typeof x!=='string'||!SAFE.test(x)||x.includes('..')))
  throw new Error('Invalid memory data');
 return {id:m.id,day:m.day,title:m.title,kind:m.kind,body:m.body,media:[...new Set(m.media)]};
}
function joinDetails(local={},remote={}){
 const result={...remote,...local};
 result.favorite=!!(local.favorite||remote.favorite);
 const union=(a,b,key)=>{const seen=new Set();return [...(a||[]),...(b||[])].filter(v=>{
  const id=key?String(v?.[key]||''):JSON.stringify(v);
  if(seen.has(id))return false;seen.add(id);return true;
 });};
 result.tags=union(local.tags,remote.tags);
 result.comments=union(local.comments,remote.comments,'id');
 return result;
}
function hub(directory,options={}){
 fs.mkdirSync(directory,{recursive:true});
 const mediaDir=path.join(directory,'media');fs.mkdirSync(mediaDir,{recursive:true});
 const configPath=path.join(directory,'config.json'),dataPath=path.join(directory,'archive.json');
 let config=readJSON(configPath,null);
 const ownerFile=path.join(directory,'owner.token');
 const newConfig=!config;
 if(!config)config={adminHash:'',devices:{},invites:{}};
 let initialAdminToken=options.adminToken||null;
 if(!initialAdminToken){
  try{initialAdminToken=fs.readFileSync(ownerFile,'utf8').trim();}catch(e){
   if(e.code!=='ENOENT')throw e;
  }
 }
 if(!initialAdminToken||hash(initialAdminToken)!==config.adminHash){
  // Recovery happens on the same Windows PC only, from this protected local file.
  initialAdminToken=random();config.adminHash=hash(initialAdminToken);
  fs.writeFileSync(ownerFile,initialAdminToken,{mode:0o600});
 }
 if(newConfig||!config.adminHash)saveAtomic(configPath,config);
 let db=readJSON(dataPath,{seq:0,memories:{},items:{}});
 const persist=()=>saveAtomic(dataPath,db);
 const auth=req=>{
  const token=/^Bearer ([A-Za-z0-9_-]{16,128})$/.exec(req.headers.authorization||'')?.[1];
  if(!token)return null;
  const digest=hash(token);
  if(digest===config.adminHash)return {id:'owner',label:'This PC',role:'admin'};
  const device=Object.values(config.devices).find(x=>x.tokenHash===digest&&!x.revoked);
  return device||null;
 };
 const invite=(label,role='contributor')=>{
  if(!['viewer','contributor'].includes(role))throw Error('Invalid role');
  const code=random().slice(0,26);
  config.invites[hash(code)]={label:String(label||'Family').slice(0,64),role,expires:Date.now()+7*86400000};
  saveAtomic(configPath,config);
  return {code,role,expires:config.invites[hash(code)].expires};
 };
 const revoke=id=>{
  if(!config.devices[id])return false;
  config.devices[id].revoked=true;
  saveAtomic(configPath,config);return true;
 };
 let server=null,publicServer=null;
 const pairingAttempts=new Map();
 async function request(req,res){
  const origin=req.headers.origin||'';
  const allowed=origin==='null'||origin==='https://jewwstein.github.io'||
   /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(origin);
  const headers={
   'Cache-Control':'no-store','X-Content-Type-Options':'nosniff',
   'Access-Control-Allow-Methods':'GET,POST,PUT,OPTIONS',
   'Access-Control-Allow-Headers':'Authorization,Content-Type',
   'Vary':'Origin'
  };
  if(allowed&&origin)headers['Access-Control-Allow-Origin']=origin;
  const reply=(status,value,extra={})=>{
   const payload=Buffer.from(JSON.stringify(value));
   res.writeHead(status,{...headers,'Content-Type':'application/json; charset=utf-8',
    'Content-Length':payload.length,...extra});res.end(payload);
  };
  const receive=async(limit=6e6)=>{
   const chunks=[];let total=0;
   for await(const part of req){total+=part.length;if(total>limit)throw Error('Payload too large');chunks.push(part);}
   try{return JSON.parse(Buffer.concat(chunks).toString('utf8'));}catch{throw Error('Invalid JSON');}
  };
  if(origin&&!allowed)return reply(403,{error:'Web origin not allowed'});
  if(req.method==='OPTIONS'){res.writeHead(204,headers);res.end();return;}
  let pathname;
  try{pathname=new URL(req.url,'http://localhost').pathname;}catch{return reply(400,{error:'Invalid URL'});}
  if(req.method==='GET'&&pathname==='/api/v1/health')return reply(200,{online:true,version:1,owner:'Our Little Universe'});
  if(req.method==='POST'&&pathname==='/api/v1/pair'){
   // The invitation has ~156 bits of entropy, but throttle guessing anyway.
   const source=req.socket.remoteAddress||'unknown',now=Date.now();
   const recent=(pairingAttempts.get(source)||[]).filter(time=>now-time<300000);
   recent.push(now);pairingAttempts.set(source,recent);
   if(pairingAttempts.size>2000){
    for(const [key,hits] of pairingAttempts)if(hits[hits.length-1]<now-300000)pairingAttempts.delete(key);
   }
   if(recent.length>12)return reply(429,{error:'Too many pairing attempts. Try again later.'});
   const body=await receive(4096),entry=config.invites[hash(String(body.code||''))];
   if(!entry||entry.expires<Date.now())return reply(403,{error:'Invalid or expired family invitation'});
   delete config.invites[hash(String(body.code||''))];
   const id=crypto.randomUUID(),token=random();
   config.devices[id]={id,label:String(body.label||entry.label).slice(0,64),role:entry.role,
    tokenHash:hash(token),revoked:false,created:Date.now(),lastSeen:Date.now()};
   saveAtomic(configPath,config);
   return reply(200,{token,id,role:entry.role});
  }
  const user=auth(req);
  if(!user)return reply(401,{error:'Not paired with this family hub'});
  if(user.id!=='owner'){config.devices[user.id].lastSeen=Date.now();} // saved at next write
  if(req.method==='GET'&&pathname==='/api/v1/info')
   return reply(200,{seq:db.seq,memoryCount:Object.keys(db.memories).length,
    mediaCount:fs.readdirSync(mediaDir).length,device:user.label,role:user.role});
  if(req.method==='POST'&&pathname==='/api/v1/invite'){
   if(user.role!=='admin')return reply(403,{error:'Owner only'});
   const body=await receive(4096);return reply(200,invite(body.label,body.role));
  }
  if(req.method==='GET'&&pathname==='/api/v1/devices'){
   if(user.role!=='admin')return reply(403,{error:'Owner only'});
   return reply(200,{devices:Object.values(config.devices).map(x=>({
    id:x.id,label:x.label,role:x.role,revoked:x.revoked,created:x.created,lastSeen:x.lastSeen
   }))});
  }
  if(req.method==='POST'&&pathname==='/api/v1/revoke'){
   if(user.role!=='admin')return reply(403,{error:'Owner only'});
   const data=await receive(2000);return reply(revoke(data.id)?200:404,{revoked:!!config.devices[data.id]});
  }
  if(req.method==='POST'&&pathname==='/api/v1/media/missing'){
   const data=await receive(2e6);
   if(!Array.isArray(data.names)||data.names.length>10000||data.names.some(x=>!SAFE.test(x)||x.includes('..')))
    return reply(400,{error:'Invalid media list'});
   return reply(200,{missing:data.names.filter(name=>!fs.existsSync(path.join(mediaDir,name)))});
  }
  if(pathname.startsWith('/api/v1/media/')){
   const name=decodeURIComponent(pathname.slice('/api/v1/media/'.length));
   if(!SAFE.test(name)||name.includes('..'))return reply(400,{error:'Invalid filename'});
   const dest=path.join(mediaDir,name);
   const params=new URL(req.url,'http://localhost').searchParams;
   if(req.method==='GET'&&params.has('meta')){
    if(!fs.existsSync(dest))return reply(404,{error:'Original not available'});
    return reply(200,{size:fs.statSync(dest).size,sha256:await hashFile(dest)});
   }
   if(req.method==='GET'&&params.has('offset')){
    if(!fs.existsSync(dest))return reply(404,{error:'Original not available'});
    const size=fs.statSync(dest).size;
    const offset=Number(params.get('offset')),limit=Number(params.get('limit')||196608);
    if(!Number.isSafeInteger(offset)||offset<0||offset>=size||
       !Number.isSafeInteger(limit)||limit<1||limit>196608)
     return reply(400,{error:'Invalid original chunk request'});
    const count=Math.min(limit,size-offset);
    res.writeHead(200,{...headers,'Content-Type':'application/octet-stream',
     'Content-Length':count,'X-File-Size':size});
    fs.createReadStream(dest,{start:offset,end:offset+count-1}).pipe(res);return;
   }
   if(req.method==='PUT'&&params.has('offset')){
    if(user.role==='viewer')return reply(403,{error:'Read-only member'});
    const offset=Number(params.get('offset')),total=Number(params.get('total'));
    if(!Number.isSafeInteger(total)||total<0||total>1024*1024*1024||
       !Number.isSafeInteger(offset)||offset<0||offset>total)
     return reply(400,{error:'Invalid original upload size'});
    const staging=path.join(mediaDir,'.part-'+name);
    if(fs.existsSync(dest)){
     // An already-complete original is never overwritten by another device.
     if(fs.statSync(dest).size!==total)return reply(409,{error:'Original filename collision'});
     return reply(200,{stored:false,complete:true});
    }
    if(offset===0)fs.writeFileSync(staging,Buffer.alloc(0),{mode:0o600});
    if(!fs.existsSync(staging)||fs.statSync(staging).size!==offset)
     return reply(409,{error:'Upload interrupted; restart attachment'});
    let chunk,totalRead=0;const parts=[];
    for await(const part of req){
     totalRead+=part.length;
     if(totalRead>196608)throw Error('Attachment chunk too large');
     parts.push(part);
    }
    if(offset+totalRead>total)throw Error('Attachment chunk exceeds declared original size');
    chunk=Buffer.concat(parts);
    fs.appendFileSync(staging,chunk);
    if(offset+totalRead===total){
     fs.renameSync(staging,dest);
     return reply(200,{stored:true,complete:true,sha256:await hashFile(dest)});
    }
    return reply(200,{stored:false,complete:false,next:offset+totalRead});
   }
   if(req.method==='GET'){
    if(!fs.existsSync(dest))return reply(404,{error:'Original not on PC'});
    const stat=fs.statSync(dest);
    res.writeHead(200,{...headers,'Content-Type':'application/octet-stream',
     'Content-Length':stat.size,'Content-Disposition':'attachment; filename="'+name+'"',
     'X-File-SHA256':await hashFile(dest)});fs.createReadStream(dest).pipe(res);return;
   }
   if(req.method==='PUT'){
    if(user.role==='viewer')return reply(403,{error:'Read-only member'});
    const tmp=path.join(mediaDir,'.upload-'+crypto.randomUUID());
    let total=0;
    try{
     const output=fs.createWriteStream(tmp,{flags:'wx',mode:0o600});
     for await(const part of req){
      total+=part.length;
      if(total>1024*1024*1024)throw Error('Attachment exceeds 1 GB limit');
      if(!output.write(part))await new Promise(done=>output.once('drain',done));
     }
     await new Promise((resolve,reject)=>output.end(err=>err?reject(err):resolve()));
     const incomingHash=await hashFile(tmp);
     if(fs.existsSync(dest)){
      if(await hashFile(dest)!==incomingHash)throw Error('Filename collision; original preserved');
      fs.unlinkSync(tmp);
      return reply(200,{stored:false,sha256:incomingHash});
     }
     fs.renameSync(tmp,dest);
     return reply(200,{stored:true,sha256:incomingHash});
    }finally{try{fs.unlinkSync(tmp);}catch{}}
   }
  }
  if(req.method==='POST'&&pathname==='/api/v1/records'){
   if(user.role==='viewer')return reply(403,{error:'Read-only member'});
   const data=await receive(10e6);
   if(!Array.isArray(data.memories)||data.memories.length>1000||
      !Array.isArray(data.items||[])||(data.items||[]).length>5000)
    return reply(400,{error:'Batch is too large'});
   let additions=0,mediaLinks=0,changed=false;
   for(const entry of data.memories){
    const memory=normalizeRecord(entry.memory);
    if(entry.details?.visibility==='private')continue;
    if(memory.media.some(x=>!fs.existsSync(path.join(mediaDir,x))))
     throw Error('Upload original media before memory metadata: '+memory.id);
    const local=db.memories[memory.id];
    if(!local){
     db.seq++;
     db.memories[memory.id]={memory,details:entry.details||{},rev:db.seq};
     additions++;changed=true;
    }else{
     const links=[...new Set([...(local.memory.media||[]),...memory.media])];
     const detail=joinDetails(local.details,entry.details||{});
     if(links.length!==local.memory.media.length||JSON.stringify(detail)!==JSON.stringify(local.details)){
      mediaLinks+=links.length-local.memory.media.length;
      local.memory.media=links;local.details=detail;local.rev=++db.seq;changed=true;
     }
    }
   }
   for(const item of data.items||[]){
    if(!ID.test(item?.id||'')||String(item.kind||'').length>100)continue;
    if(!db.items[item.id]){db.items[item.id]={item,rev:++db.seq};changed=true;}
   }
   if(changed)persist();
   return reply(200,{seq:db.seq,added:additions,attachments:mediaLinks});
  }
  if(req.method==='GET'&&pathname==='/api/v1/changes'){
   const since=Math.max(0,Number(new URL(req.url,'http://localhost').searchParams.get('since')||0));
   if(!Number.isSafeInteger(since))return reply(400,{error:'Invalid sync cursor'});
   const allChanges=[
    ...Object.values(db.memories).filter(x=>x.rev>since).map(x=>({type:'memory',record:x,rev:x.rev})),
    ...Object.values(db.items).filter(x=>x.rev>since).map(x=>({type:'item',record:x.item,rev:x.rev}))
   ].sort((a,b)=>a.rev-b.rev);
   // Pagination is essential for memory collections too large for a single relay message.
   const batch=allChanges.slice(0,12);
   const more=allChanges.length>batch.length;
   const memories=batch.filter(x=>x.type==='memory').map(x=>x.record);
   const items=batch.filter(x=>x.type==='item').map(x=>x.record);
   const end=more?batch[batch.length-1].rev:db.seq;
   return reply(200,{seq:end,more,memories,items});
  }
  return reply(404,{error:'Unknown family hub operation'});
 }
 const handler=(req,res)=>{request(req,res).catch(error=>{
  if(!res.headersSent){const payload=Buffer.from(JSON.stringify({error:error.message||'Hub error'}));
   res.writeHead(400,{'Content-Type':'application/json','Content-Length':payload.length});res.end(payload);}
  else res.destroy();
 });};
 server=http.createServer(handler);
 function start(port=PORT,host='127.0.0.1'){
  return new Promise((resolve,reject)=>{
   server.once('error',reject);server.listen(port,host,()=>{
    server.off('error',reject);
    const address=server.address();
    resolve({port:address.port,host,adminToken:initialAdminToken});
   });
  });
 }
 async function startPublic(certificate,port=443){
  if(publicServer){
   publicServer.setSecureContext({key:fs.readFileSync(certificate.keyPath),cert:fs.readFileSync(certificate.certPath)});
   return {port:publicServer.address().port};
  }
  const web=https.createServer({
   key:fs.readFileSync(certificate.keyPath),cert:fs.readFileSync(certificate.certPath),
   minVersion:'TLSv1.2',maxHeaderSize:16384,requestTimeout:120000
  },handler);
  await new Promise((resolve,reject)=>{
   web.once('error',reject);
   web.listen(port,'0.0.0.0',()=>{web.off('error',reject);resolve();});
  });
  publicServer=web;
  return {port:web.address().port};
 }
 async function updatePublicCertificate(certificate){
  if(!publicServer)throw Error('The remote HTTPS listener is not active');
  publicServer.setSecureContext({key:fs.readFileSync(certificate.keyPath),cert:fs.readFileSync(certificate.certPath)});
 }
 async function stopPublic(){
  if(!publicServer)return;
  const old=publicServer;publicServer=null;
  await new Promise(resolve=>old.close(()=>resolve()));
 }
 function stop(){return Promise.all([
  new Promise(resolve=>server.close(resolve)),stopPublic()
 ]);}
 return {start,stop,startPublic,stopPublic,updatePublicCertificate,invite,revoke,
 devices:()=>Object.values(config.devices).map(x=>({
  id:x.id,label:x.label,role:x.role,revoked:x.revoked,lastSeen:x.lastSeen
 })),adminToken:initialAdminToken,port:PORT};
}
module.exports={hub,normalizeRecord,joinDetails};
