'use strict';
// Self-contained UPnP IGD port mapping. No UPnP helper application is installed.
// Runs only after the PC owner presses Enable Remote Family Access.
// If the router rejects mapping, display actionable diagnostics.
const dgram=require('node:dgram'),os=require('node:os'),net=require('node:net');
const http=require('node:http');
const {URL}=require('node:url');
function localIPs(){
 const results=[];
 for(const group of Object.values(os.networkInterfaces()))
  for(const x of group||[])if(x.family==='IPv4'&&!x.internal)
   results.push(x.address);
 return results;
}
function privateIPv4(ip){
 return net.isIP(ip)===4&&(
  /^10\./.test(ip)||/^192\.168\./.test(ip)||
  /^172\.(1[6-9]|2\d|3[01])\./.test(ip));
}
function fetchLocal(url,{method='GET',headers={},body='',timeout=5000}={}){
 return new Promise((resolve,reject)=>{
  let target;
  try{target=new URL(url);}catch{return reject(Error('Invalid router address'));}
  // Prevent SSRF to the internet or arbitrary private credentials.
  if(target.protocol!=='http:'||!privateIPv4(target.hostname))
   return reject(Error('Router description is outside the private home network'));
  const request=http.request(target,{method,headers,timeout},response=>{
   let data='',size=0;
   response.on('data',part=>{
    size+=part.length;
    if(size>1024*1024){request.destroy(Error('Router response too large'));return;}
    data+=part;
   });
   response.on('end',()=>resolve({status:response.statusCode,data}));
  });
  request.on('timeout',()=>request.destroy(Error('Router timed out')));
  request.on('error',reject);if(body)request.write(body);request.end();
 });
}
async function discover(timeout=2400){
 const addresses=localIPs();
 if(!addresses.length)throw Error('PC has no active IPv4 home network');
 const found=new Set();
 await Promise.all(addresses.map(ip=>new Promise(resolve=>{
  const socket=dgram.createSocket({type:'udp4',reuseAddr:true});
  const end=()=>{try{socket.close();}catch{}resolve();};
  const timer=setTimeout(end,timeout);
  socket.on('error',()=>{clearTimeout(timer);end();});
  socket.on('message',(bytes,rinfo)=>{
   const response=bytes.toString('utf8');
   const location=/^location:\s*(\S+)/im.exec(response)?.[1];
   if(location&&privateIPv4(rinfo.address)){
    try{
     const target=new URL(location);
     if(target.hostname===rinfo.address&&target.protocol==='http:')
      found.add(location);
    }catch{}
   }
  });
  socket.bind(0,ip,()=>{
   const msg=[
    'M-SEARCH * HTTP/1.1','HOST: 239.255.255.250:1900',
    'MAN: "ssdp:discover"','MX: 2',
    'ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1','',''
   ].join('\r\n');
   socket.send(Buffer.from(msg),1900,'239.255.255.250',()=>{});
  });
  setTimeout(()=>clearTimeout(timer),timeout+30);
 })));
 for(const location of found){
  try{
   const xml=(await fetchLocal(location)).data;
   const service=xml.match(/<service>([\s\S]*?<\/service>)/gi)||[];
   for(const item of service){
    const type=/<serviceType>([^<]+)<\/serviceType>/i.exec(item)?.[1];
    const route=/<controlURL>([^<]+)<\/controlURL>/i.exec(item)?.[1];
    if(!type||!route||!/WAN(IP|PPP)Connection:\d+$/.test(type))continue;
    const control=new URL(route,location);
    if(control.protocol!=='http:'||!privateIPv4(control.hostname))continue;
    const local=localIPs().find(ip=>ip.split('.').slice(0,3).join('.')===
      control.hostname.split('.').slice(0,3).join('.'))||localIPs()[0];
    return {control:control.href,type,local};
   }
  }catch{}
 }
 throw Error('Your router did not respond to built-in UPnP setup. You may need a one-time router setting.');
}
async function soap(gateway,operation,fields={}){
 const args=Object.entries(fields).map(([k,v])=>'<'+k+'>'+String(v).replace(/[<>&]/g,'')+'</'+k+'>').join('');
 const body='<?xml version="1.0"?>'+
  '<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">'+
  '<s:Body><u:'+operation+' xmlns:u="'+gateway.type+'">'+args+'</u:'+operation+'></s:Body></s:Envelope>';
 const result=await fetchLocal(gateway.control,{method:'POST',
  headers:{'Content-Type':'text/xml; charset="utf-8"',
   SOAPACTION:'"'+gateway.type+'#'+operation+'"','Content-Length':Buffer.byteLength(body)},body});
 if(result.status!==200)throw Error(operation+' failed on home router ('+result.status+')');
 return result.data;
}
async function externalIP(gateway){
 const body=await soap(gateway,'GetExternalIPAddress');
 return /<NewExternalIPAddress>([^<]+)</.exec(body)?.[1]||'';
}
async function map(gateway,port,{duration=3600}={}){
 if(![80,443].includes(port))throw Error('Only certificate and encrypted family-sync ports are supported');
 const args={
  NewRemoteHost:'',NewExternalPort:port,NewProtocol:'TCP',
  NewInternalPort:port,NewInternalClient:gateway.local,
  NewEnabled:1,NewPortMappingDescription:'Our Little Universe Family Hub',
  NewLeaseDuration:duration
 };
 await soap(gateway,'AddPortMapping',args);
 return {publicPort:port,localIP:gateway.local,expires:Date.now()+duration*1000};
}
async function unmap(gateway,port){
 try{await soap(gateway,'DeletePortMapping',{
  NewRemoteHost:'',NewExternalPort:port,NewProtocol:'TCP'
 });return true;}catch{return false;}
}
module.exports={discover,externalIP,map,unmap,privateIPv4,localIPs};
