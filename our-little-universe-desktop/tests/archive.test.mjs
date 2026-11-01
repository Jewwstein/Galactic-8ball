import {test} from 'node:test';
import assert from 'node:assert/strict';
import JSZip from 'jszip';
test('Android ZIP v1 archive format roundtrips with original image bytes',async()=>{
 const zip=new JSZip(),file=new Uint8Array([137,80,78,71,13,10,26,10]);
 const record={id:'b274d8f8-1aba-478d-8798-349ec4cfefff',
  day:'2026-10-31',title:'Positive test',kind:'Memory',body:'We found out!',media:['image.png']};
 zip.file('archive.json',JSON.stringify({
  format:'little-universe-android-v1',settings:{childName:'',parents:'Dad and Mariah',dueDate:''},
  memories:[record],features:{details:{[record.id]:{author:'Dad'}},items:[]},
  mediaSha256:{}}));
 zip.file('media/image.png',file);
 const exported=await zip.generateAsync({type:'uint8array'});
 const restored=await JSZip.loadAsync(exported,{checkCRC32:true});
 const manifest=JSON.parse(await restored.file('archive.json').async('string'));
 assert.equal(manifest.format,'little-universe-android-v1');
 assert.equal(manifest.memories[0].id,record.id);
 assert.equal(manifest.features.details[record.id].author,'Dad');
 assert.deepEqual(await restored.file('media/image.png').async('uint8array'),file);
});
test('Same stable ID is not treated as a new moment',()=>{
 const local=new Map([['id-1',{id:'id-1',title:'Original'}]]);
 const received=[{id:'id-1',title:'Other copy'},{id:'id-2',title:'New'}];
 let added=0;
 for(const row of received)if(!local.has(row.id)){local.set(row.id,row);added++;}
 assert.equal(added,1);
 assert.equal(local.get('id-1').title,'Original');
});
