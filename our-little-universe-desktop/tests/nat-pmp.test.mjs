import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const natpmp=require('../nat-pmp.cjs');
test('Built-in NAT-PMP has a safe private-router boundary',async()=>{
 await assert.rejects(natpmp.request('8.8.8.8',Buffer.from([0,0])),/private home router/);
 await assert.rejects(natpmp.request('127.0.0.1',Buffer.from([0,0])),/private home router/);
 await assert.rejects(natpmp.request('100.64.0.1',Buffer.from([0,0])),/private home router/);
 await assert.rejects(natpmp.mapPort('192.168.1.1',1337),/Unexpected port/);
});
test('Windows NAT-PMP discovery uses the operating system route table',async()=>{
 if(process.platform!=='win32')await assert.rejects(natpmp.defaultGateway(),/Windows router discovery only/);
 assert.equal(typeof natpmp.discover,'function');
 assert.equal(typeof natpmp.mapPort,'function');
 assert.equal(typeof natpmp.publicAddress,'function');
});
