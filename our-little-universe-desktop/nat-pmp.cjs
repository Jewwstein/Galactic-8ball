'use strict';
// First-party NAT-PMP client. Uses only Node's UDP socket and the host's built-in
// route table. Provides an automatic alternative when UPnP isn't available.
const dgram=require('node:dgram');
const net=require('node:net');
const {execFile}=require('node:child_process');
const PRIVATE=/^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/;
function defaultGateway(){
 return new Promise((resolve,reject)=>{
  if(process.platform!=='win32')return reject(Error('Windows router discovery only'));
  execFile('route',['PRINT','-4'],{encoding:'utf8',windowsHide:true,timeout:6000},
   (err,stdout)=>{
    if(err)return reject(Error('Could not read the Windows internet route: '+err.message));
    for(const line of stdout.split(/\r?\n/)){
     const parts=line.trim().split(/\s+/);
     if(parts.length<5||parts[0]!=='0.0.0.0'||parts[1]!=='0.0.0.0')continue;
     const gateway=parts[2],local=parts[3];
     if(net.isIP(gateway)===4&&net.isIP(local)===4&&PRIVATE.test(gateway)&&PRIVATE.test(local))
      return resolve({gateway,local});
    }
    reject(Error('Windows could not find a private home router gateway'));
   });
 });
}
function request(gateway,bytes,{port=5351,timeout=1300,retries=2}={}){
 if(net.isIP(gateway)!==4||!PRIVATE.test(gateway))return Promise.reject(Error('Not a private home router'));
 return new Promise((resolve,reject)=>{
  const sock=dgram.createSocket('udp4');
  let tries=0,finished=false,timer;
  const done=(err,result)=>{
   if(finished)return;
   finished=true;clearTimeout(timer);
   try{sock.close();}catch{}
   if(err)reject(err);else resolve(result);
  };
  sock.on('error',done);
  sock.on('message',(response,info)=>{
   if(info.address!==gateway||response.length<8||response[0]!==0)return;
   if(response[1]!==bytes[1]+128)return;
   const code=response.readUInt16BE(2);
   if(code!==0)return done(Error('Router rejected NAT-PMP request (code '+code+')'));
   done(null,response);
  });
  function send(){
   tries++;
   sock.send(bytes,port,gateway,err=>{
    if(err)return done(err);
    timer=setTimeout(()=>{
     if(tries<=retries)send();
     else done(Error('This router does not respond to built-in NAT-PMP setup'));
    },timeout*tries);
   });
  }
  send();
 });
}
async function publicAddress(gateway,options){
 const data=await request(gateway,Buffer.from([0,0]),options);
 if(data.length<12)throw Error('Router returned incomplete NAT-PMP address');
 return Array.from(data.subarray(8,12)).join('.');
}
async function mapPort(gateway,port,lifetime=3600,options={}){
 if(![80,443].includes(port))throw Error('Unexpected port requested');
 const message=Buffer.alloc(12);
 message[0]=0;message[1]=2; // TCP
 message.writeUInt16BE(port,4);message.writeUInt16BE(port,6);
 message.writeUInt32BE(lifetime>>>0,8);
 const response=await request(gateway,message,options);
 if(response.length<16)throw Error('Router returned incomplete NAT-PMP mapping');
 const external=response.readUInt16BE(10),ttl=response.readUInt32BE(12);
 if(lifetime&&external!==port)throw Error('Router assigned external port '+external+' instead of '+port);
 if(lifetime&&ttl<300)throw Error('Router granted an insufficient port-mapping lifetime');
 return {publicPort:external,expires:Date.now()+ttl*1000};
}
async function discover(){
 const network=await defaultGateway();
 const ip=await publicAddress(network.gateway);
 return {kind:'natpmp',gateway:network.gateway,local:network.local,ip};
}
module.exports={defaultGateway,request,publicAddress,mapPort,discover};
