'use strict';
/**
 * Direct, built-in remote connection manager for the Our Little Universe Windows hub.
 *
 * No VPN app, third-party tunnel client, relay, commercial database, or remote
 * photo store. The owner enables remote access explicitly. Traffic goes directly
 * to their public home IPv4 with a public-trust HTTPS certificate.
 *
 * A public IPv4 and inbound TCP 80/443 reachability are fundamental requirements.
 * CGNAT, blocked ports, or routers with disabled UPnP may need owner intervention.
 */
const fs=require('node:fs'),path=require('node:path');
const {discover,externalIP,map,unmap,localIPs}=require('./home-router.cjs');
const {obtain,cached,publicIPv4}=require('./direct-acme.cjs');
const certFolder='public-tls';
function manager({hub,dataDirectory,notify=()=>{}}){
 if(!hub||typeof hub.startPublic!=='function')throw Error('Secure hub instance required');
 const statePath=path.join(dataDirectory,'remote-access.json');
 let config={enabled:false};
 try{config={...config,...JSON.parse(fs.readFileSync(statePath,'utf8'))};}catch{}
 let current={status:'not-configured',url:'',lastError:''},gateway=null,refresh=null,running=false;
 function status(){return {...current,enabled:config.enabled,localIPs:localIPs()};}
 function persist(){
  fs.writeFileSync(statePath,JSON.stringify(config),{mode:0o600});
 }
 function progress(stage){current.status=stage;notify(status());}
 async function attempt({manual=false,ip:manualIP=''}={}){
  if(running)throw Error('Remote access setup is already running');
  running=true;
  current.lastError='';
  let createdMaps=[];
  try{
   progress('discovering-home-router');
   gateway=null;
   if(!manual){
    gateway=await discover();
   }
   let ip=manualIP;
   if(gateway){
    ip=await externalIP(gateway);
    if(!publicIPv4(ip))throw Error(
     'Your internet connection appears to use CGNAT or a non-public router address. A direct connection from distant phones cannot be established with ordinary port forwarding. Ask your provider about a public IPv4 address, or use native IPv6 in a future version.'
    );
    progress('opening-private-family-gateway');
    for(const port of [80,443]){
     try{await map(gateway,port);createdMaps.push(port);}
     catch(err){throw Error('Your router rejected automatic port '+port+
      ' setup. Use its built-in port-forward setting to forward TCP 80 and 443 to '+gateway.local+'. '+err.message);}
    }
   }
   if(!publicIPv4(ip))throw Error(
    'A public IPv4 address is required for this direct connection. If router discovery is disabled, enter your public IPv4 and manually forward TCP 80 and 443 to this PC.'
   );
   const directory=path.join(dataDirectory,certFolder);
   progress('securing-https-certificate');
   const certificate=cached(directory,ip)||await obtain(directory,ip);
   progress('starting-private-memory-connection');
   await hub.startPublic(certificate);
   config={enabled:true,ip,manual};
   persist();
   current={status:'ready',url:'https://'+ip,lastError:'',ip,
    expires:certificate.expires,network:'Direct HTTPS to your Windows PC'};
   notify(status());
   if(refresh)clearInterval(refresh);
   refresh=setInterval(async()=>{
    try{
     if(gateway){
      const liveIP=await externalIP(gateway);
      if(liveIP!==ip){
       if(!publicIPv4(liveIP))throw Error('Home ISP no longer provides a public IPv4 address');
       await attempt();
       return;
      }
      for(const port of [80,443])await map(gateway,port);
     }
     const fresh=cached(directory,ip);
     if(!fresh){
      const cert=await obtain(directory,ip);
      await hub.updatePublicCertificate(cert);
      current.expires=cert.expires;notify(status());
     }
    }catch(err){
     current.lastError='Automatic certificate/network maintenance needs attention: '+err.message;
     notify(status());
    }
   },25*60*1000);
   if(refresh.unref)refresh.unref();
   return status();
  }catch(error){
   if(gateway&&createdMaps.length){
    for(const port of createdMaps)await unmap(gateway,port);
   }
   current={status:'needs-home-network-setup',lastError:error.message,url:'',ip:''};
   notify(status());
   return status();
  }finally{running=false;}
 }
 async function resume(){
  if(!config.enabled)return status();
  return attempt({manual:config.manual,ip:config.ip});
 }
 async function disable(){
  if(refresh)clearInterval(refresh);
  refresh=null;
  await hub.stopPublic();
  if(gateway){for(const port of [80,443])await unmap(gateway,port);}
  config.enabled=false;persist();
  current={status:'disabled',lastError:'',url:''};
  notify(status());return status();
 }
 return {attempt,resume,disable,status};
}
module.exports={manager};
