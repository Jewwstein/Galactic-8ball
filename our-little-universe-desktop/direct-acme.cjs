'use strict';
// First-party ACME client. No Certbot, VPN, proxy, or other installed applications.
// Uses Let's Encrypt for a publicly trusted, six-day IP certificate.
// The only public challenge listener is HTTP on port 80.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),
 crypto=require('node:crypto'),net=require('node:net');
const DIRECTORY='https://acme-v02.api.letsencrypt.org/directory';
const b64=bytes=>Buffer.from(bytes).toString('base64url');
const digest=bytes=>crypto.createHash('sha256').update(bytes).digest();
const delay=ms=>new Promise(r=>setTimeout(r,ms));
function save(file,content){
 const temp=file+'.'+process.pid+'.tmp';
 fs.writeFileSync(temp,content,{mode:0o600});fs.renameSync(temp,file);
}
function keypair(file){
 if(fs.existsSync(file))return crypto.createPrivateKey(fs.readFileSync(file,'utf8'));
 const {privateKey}=crypto.generateKeyPairSync('rsa',{modulusLength:2048});
 save(file,privateKey.export({format:'pem',type:'pkcs8'}));
 return privateKey;
}
function len(n){if(n<128)return Buffer.from([n]);let x=n,a=[];while(x){a.unshift(x&255);x>>>=8;}return Buffer.from([0x80|a.length,...a]);}
function der(tag,value){const b=Buffer.from(value);return Buffer.concat([Buffer.from([tag]),len(b.length),b]);}
function seq(...parts){return der(0x30,Buffer.concat(parts));}
function oid(value){
 const nums=value.split('.').map(Number),out=[40*nums[0]+nums[1]];
 for(const n of nums.slice(2)){let c=n,parts=[c&127];while(c>>=7)parts.unshift((c&127)|128);out.push(...parts);}
 return der(0x06,Buffer.from(out));
}
function bitstring(bytes){return der(0x03,Buffer.concat([Buffer.from([0]),Buffer.from(bytes)]));}
function csrForIP(key,ip){
 if(net.isIP(ip)!==4)throw Error('A public IPv4 address is required for this first release');
 const cn=der(0x31,seq(oid('2.5.4.3'),der(0x0c,Buffer.from(ip))));
 const name=seq(cn);
 const spki=crypto.createPublicKey(key).export({format:'der',type:'spki'});
 const ipBytes=Buffer.from(ip.split('.').map(Number));
 const subjectAltName=seq(der(0x87,ipBytes)); // GeneralName [7] iPAddress
 const ext=seq(oid('2.5.29.17'),der(0x04,subjectAltName));
 const extensionRequest=seq(oid('1.2.840.113549.1.9.14'),der(0x31,seq(ext)));
 const info=seq(der(0x02,[0]),name,spki,der(0xa0,extensionRequest));
 const signature=crypto.sign('sha256',info,key);
 const algorithm=seq(oid('1.2.840.113549.1.1.11'),der(0x05,[]));
 return seq(info,algorithm,bitstring(signature));
}
function publicIPv4(ip){
 return net.isIP(ip)===4&&!(
  /^10\./.test(ip)||/^127\./.test(ip)||/^192\.168\./.test(ip)||
  /^169\.254\./.test(ip)||/^172\.(1[6-9]|2\d|3[01])\./.test(ip)||
  /^100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\./.test(ip)||
  /^0\./.test(ip)||/^224\./.test(ip)||/^23[0-9]\./.test(ip)||
  /^24[0-9]\./.test(ip)||/^25[0-5]\./.test(ip)
 );
}
function jwkFor(key){
 const pub=crypto.createPublicKey(key).export({format:'jwk'});
 return {e:pub.e,kty:'RSA',n:pub.n};
}
// Complete ACME order with a protected order URL and persist cert+metadata atomically.
async function obtain(directory,ip,opts={}){
 if(!publicIPv4(ip))throw Error('A public IPv4 address is required');
 fs.mkdirSync(directory,{recursive:true});
 const accountKey=keypair(path.join(directory,'acme-account.pem'));
 const certKey=keypair(path.join(directory,'server-key.pem'));
 const jwk=jwkFor(accountKey),thumb=b64(digest(JSON.stringify(jwk)));
 const response=await fetch(opts.endpoint||DIRECTORY);
 if(!response.ok)throw Error('Free certificate service unavailable');
 const links=await response.json();
 let nonce=null,kid='';
 const accountPath=path.join(directory,'acme-account-url');
 if(fs.existsSync(accountPath))kid=fs.readFileSync(accountPath,'utf8').trim();
 async function send(url,data,kind='json',initial=false){
  if(!nonce){
   const res=await fetch(links.newNonce,{method:'HEAD'});
   if(!res.ok)throw Error('Certificate nonce failed');
   nonce=res.headers.get('replay-nonce');
  }
  const header={alg:'RS256',nonce,url,...(initial?{jwk}:{kid})};
  const protectedPart=b64(JSON.stringify(header)),payload=data===null?'':b64(JSON.stringify(data));
  const signature=b64(crypto.sign('sha256',Buffer.from(protectedPart+'.'+payload),accountKey));
  const res=await fetch(url,{method:'POST',headers:{
   'Content-Type':'application/jose+json',
   Accept:kind==='pem'?'application/pem-certificate-chain':'application/json'
  },body:JSON.stringify({protected:protectedPart,payload,signature})});
  nonce=res.headers.get('replay-nonce');
  const body=await res.text();
  if(!res.ok)throw Error('ACME '+res.status+': '+body.slice(0,400));
  return {data:kind==='pem'?body:JSON.parse(body),location:res.headers.get('location')};
 }
 if(!kid){
  const created=await send(links.newAccount,{termsOfServiceAgreed:true},'json',true);
  kid=created.location;
  if(!kid)throw Error('Missing ACME account ID');
  save(accountPath,kid);
 }
 const order=await send(links.newOrder,{identifiers:[{type:'ip',value:ip}],profile:'shortlived'});
 const tokenMap=new Map();
 const validator=http.createServer((req,res)=>{
  const prefix='/.well-known/acme-challenge/';
  const token=req.url.startsWith(prefix)?req.url.substring(prefix.length):'';
  if(!tokenMap.has(token)){res.writeHead(404);return res.end();}
  res.writeHead(200,{'Content-Type':'text/plain','Cache-Control':'no-store'});
  res.end(tokenMap.get(token));
 });
 const listenPort=opts.port||80;
 await new Promise((resolve,reject)=>{
  validator.once('error',reject);
  validator.listen(listenPort,'0.0.0.0',()=>{validator.off('error',reject);resolve();});
 }).catch(err=>{throw Error('Could not open public validation port '+listenPort+': '+err.message)});
 try{
  for(const link of order.data.authorizations){
   let auth=(await send(link,null)).data;
   if(auth.status==='valid')continue;
   const challenge=auth.challenges?.find(x=>x.type==='http-01');
   if(!challenge)throw Error('Certificate authority does not support HTTP-01 for this address');
   tokenMap.set(challenge.token,challenge.token+'.'+thumb);
   await send(challenge.url,{});
   for(let i=0;i<25;i++){
    await delay(2500);
    auth=(await send(link,null)).data;
    if(auth.status==='valid')break;
    if(auth.status==='invalid')throw Error('Certificate validation failed: port 80 must reach your PC.');
   }
   if(auth.status!=='valid')throw Error('Port 80 could not be verified from the internet');
  }
  await send(order.data.finalize,{csr:b64(csrForIP(certKey,ip))});
  for(let i=0;i<30;i++){
   await delay(1700);
   const result=(await send(order.location,null)).data;
   if(result.status==='valid'&&result.certificate){
    const pem=(await send(result.certificate,null,'pem')).data;
    if(!pem.includes('BEGIN CERTIFICATE'))throw Error('No public certificate issued');
    const certPath=path.join(directory,'server-cert.pem');
    save(certPath,pem);
    const certificate=new crypto.X509Certificate(pem);
    save(path.join(directory,'certificate-status.json'),JSON.stringify({
     ip,expires:Date.parse(certificate.validTo),lastRenewed:Date.now()}));
    return {ip,certPath,keyPath:path.join(directory,'server-key.pem'),
     expires:Date.parse(certificate.validTo)};
   }
   if(result.status==='invalid')throw Error('Certificate order rejected');
  }
  throw Error('Certificate order timed out');
 }finally{
  await new Promise(resolve=>validator.close(resolve));
 }
}
function cached(directory,ip,renewBefore=3*86400000){
 const file=path.join(directory,'certificate-status.json');
 try{
  const status=JSON.parse(fs.readFileSync(file,'utf8'));
  if(status.ip!==ip||status.expires-Date.now()<renewBefore)return null;
  const keyPath=path.join(directory,'server-key.pem'),certPath=path.join(directory,'server-cert.pem');
  if(!fs.existsSync(keyPath)||!fs.existsSync(certPath))return null;
  return {...status,keyPath,certPath};
 }catch{return null;}
}
module.exports={publicIPv4,csrForIP,obtain,cached};
