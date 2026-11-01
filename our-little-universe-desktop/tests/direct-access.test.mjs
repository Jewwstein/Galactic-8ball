import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
const require=createRequire(import.meta.url);
const {publicIPv4,csrForIP,cached}=require('../direct-acme.cjs');
const {privateIPv4}=require('../home-router.cjs');
const {hub}=require('../hub-server.cjs');
test('Direct-connect router classification rejects CGNAT and private addresses',()=>{
 for(const ip of ['10.0.0.4','192.168.1.1','172.18.1.2','100.64.0.1','100.127.2.1','127.0.0.1','not-an-ip'])
  assert.equal(publicIPv4(ip),false,ip);
 for(const ip of ['8.8.8.8','1.1.1.1','203.0.113.7'])
  assert.equal(publicIPv4(ip),true,ip);
 assert.equal(privateIPv4('192.168.1.1'),true);
 assert.equal(privateIPv4('8.8.8.8'),false);
});
test('In-app ACME client produces signed CSR containing public IP SAN',()=>{
 const {privateKey}=crypto.generateKeyPairSync('rsa',{modulusLength:2048});
 const csr=csrForIP(privateKey,'8.8.8.8');
 assert.ok(csr.length>500);
 const check=spawnSync('openssl',['req','-inform','DER','-noout','-verify','-text'],{
  input:csr,encoding:'utf8',timeout:10000
 });
 assert.equal(check.status,0,check.stderr);
 assert.match(check.stdout,/IP Address:8\.8\.8\.8/);
 assert.match(check.stdout+'\n'+check.stderr,/verify OK/i);
});
test('Remote listener stays unavailable until owner explicitly starts HTTPS',async()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'olu-closed-'));
 const service=hub(dir);
 const start=await service.start(0);
 try{
  assert.ok(start.adminToken.length>20);
  assert.equal(service.startPublic instanceof Function,true);
  assert.equal(typeof service.stopPublic,'function');
  assert.equal(typeof service.updatePublicCertificate,'function');
  assert.equal(cached(dir,'8.8.8.8'),null);
 }finally{await service.stop();fs.rmSync(dir,{recursive:true,force:true});}
});
