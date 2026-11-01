import './style.css';
import {registerSW} from 'virtual:pwa-register';
import {currentHub,setHostHub,pairHub,clearHub,syncNow,beginAutoSync} from './hub-sync.js';
import {openStore,get,all,put,profile,saveProfile,upsertMemory,saveMedia,media,
recycleMemory,restoreMemory,removeForever,exportZip,importZip,checkZip,downloadBlob,
uuid,dateString,mediaMime,normalizeName} from './store.js';
registerSW({immediate:true});
const $=id=>document.getElementById(id);
const safe=x=>String(x??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const short=(x,n=200)=>String(x||'').length>n?String(x).slice(0,n)+'…':String(x||'');
let page='home',filter='',query='',objectUrls=[],pendingAudio=[],recorder=null;
function message(text){
 const div=$('toast');div.textContent=text;div.classList.add('visible');
 clearTimeout(message.handle);message.handle=setTimeout(()=>div.classList.remove('visible'),4500);
}
function modal(title,body){
 $('modalTitle').textContent=title;$('modalBody').innerHTML=body;$('modal').hidden=false;
}
function close(){
 if(recorder?.state==='recording')recorder.stop();
 $('modal').hidden=true;$('modalBody').replaceChildren();
}
function btn(title,cmd,extra='',className='secondary'){
 return '<button class="'+className+'" data-command="'+cmd+'" '+extra+'>'+safe(title)+'</button>';
}
function blank(title,body){
 return '<div class="card empty"><div class="empty-symbol">♡</div><strong>'+safe(title)+'</strong>'+safe(body)+'</div>';
}
function input(name,label,value='',type='text'){
 if(type==='textarea')return '<label class="field"><span>'+safe(label)+'</span><textarea name="'+name+'">'+safe(value)+'</textarea></label>';
 return '<label class="field"><span>'+safe(label)+'</span><input type="'+type+'" name="'+name+'" value="'+safe(value)+'"></label>';
}
function cleanURLs(){for(const url of objectUrls)URL.revokeObjectURL(url);objectUrls=[];}
async function fileURL(id){
 const b=await media(id);if(!b)return null;
 const u=URL.createObjectURL(b);objectUrls.push(u);return u;
}
async function home(){
 const p=await profile(),memories=(await all('memories')).sort((a,b)=>b.day.localeCompare(a.day));
 const name=p.childName?'Our world with '+p.childName:'A little universe of love.';
 let recent=memories.slice(0,3).map(m=>
  '<article class="card memory-card"><p class="eyebrow">'+safe(m.day)+' · '+safe(m.kind)+'</p><h3>'+safe(m.title)+'</h3><p class="muted">'+safe(short(m.body,160))+'</p>'+
  btn('Open memory','details','data-id="'+safe(m.id)+'"','tertiary')+'</article>').join('');
 if(!recent)recent=blank('Your first chapter awaits','Import an Android backup ZIP or start a memory here.');
 return '<section class="hero"><div class="hero-main"><p class="eyebrow">A LIFETIME OF LITTLE MOMENTS</p><h1>'+safe(name)+'</h1>'+
  '<p class="muted">From that first positive test to every laugh, first step, and adventure. A story you can one day hand to your child.</p>'+
  '<div class="buttonrow">'+btn('✎ Write a new memory','new','','primary')+btn('✉ Write a letter','letter')+'</div></div>'+
  '<div class="hero-side"><div class="tile"><span class="tile-icon">✦</span><strong>'+memories.length+
  ' treasured moments</strong><p class="muted">Saved locally, under your control.</p></div>'+
  '<div class="tile"><span class="tile-icon">♡</span><strong>Across every device</strong><p class="muted">Exchange the same family ZIP with Android, Windows and iPhone.</p></div></div></section>'+
  '<div class="section-heading"><h2>Return to your story</h2><small>'+safe(p.contributorName||'Family')+' · Offline</small></div>'+
  '<div class="grid">'+recent+'</div><div class="section-heading"><h2>Made with love</h2></div>'+
  '<div class="grid"><div class="tile" data-view="timeline"><span class="tile-icon">▤</span><strong>The timeline</strong><p class="muted">Everything you have recorded.</p></div>'+
  '<div class="tile" data-view="letters"><span class="tile-icon">✉</span><strong>Future letters</strong><p class="muted">Words they will treasure someday.</p></div>'+
  '<div class="tile" data-view="vault"><span class="tile-icon">◇</span><strong>Family vault</strong><p class="muted">Bring memories from your phone or relatives.</p></div></div>';
}
function memoryCard(m,d={}){
 return '<article class="card memory-card"><div class="topline"><span class="pill">'+safe(m.kind)+'</span><span class="meta">'+safe(m.day)+'</span></div>'+
 '<h3>'+safe(m.title)+'</h3><p class="muted">'+safe(short(m.body,460))+'</p>'+
 '<div class="meta">Preserved by '+safe(d.author||'Family')+' · '+(m.media||[]).length+' attachment(s)</div>'+
 '<div class="memory-thumbs" data-thumbs="'+safe(m.id)+'"></div>'+
 '<div class="buttonrow">'+btn('Open','details','data-id="'+safe(m.id)+'"','primary small')+
 btn('Edit','edit','data-id="'+safe(m.id)+'"','secondary small')+
 btn(d.favorite?'♥ Favorite':'♡ Favorite','star','data-id="'+safe(m.id)+'"','tertiary small')+
 btn('Delete','delete','data-id="'+safe(m.id)+'"','tertiary small danger')+'</div></article>';
}
async function timeline(letters=false){
 let list=(await all('memories')).sort((a,b)=>b.day.localeCompare(a.day));
 const d=Object.fromEntries((await all('details')).map(x=>[x.id,x.value]));
 if(letters)list=list.filter(x=>x.kind==='Letter');
 if(filter==='favorites')list=list.filter(x=>d[x.id]?.favorite);
 if(filter==='media')list=list.filter(x=>(x.media||[]).length>0);
 if(query)list=list.filter(x=>[x.title,x.body,x.day,x.kind,d[x.id]?.author,...(d[x.id]?.tags||[])]
  .join(' ').toLowerCase().includes(query.toLowerCase()));
 return '<p class="eyebrow">PRESERVED WITH LOVE</p><div class="section-heading"><h1>'+
  (letters?'Letters to our child':'Our family timeline')+'</h1>'+btn(letters?'Write a letter':'Add memory',letters?'letter':'new','','primary')+'</div>'+
  '<div class="row" style="flex-wrap:wrap;margin-bottom:20px"><input id="searchText" placeholder="Search memories, authors or dates…" value="'+safe(query)+'" style="max-width:360px">'+
  '<select id="filterList" style="width:auto"><option value="">All memories</option><option value="favorites"'+(filter==='favorites'?' selected':'')+
  '>Favorites</option><option value="media"'+(filter==='media'?' selected':'')+'>With media</option></select>'+
  btn('Search','search')+'</div><div class="stack">'+(list.length?list.map(m=>memoryCard(m,d[m.id])).join(''):blank('Nothing here yet','Try a different search or write a memory.'))+'</div>';
}
async function gallery(){
 const records=(await all('memories')).sort((a,b)=>b.day.localeCompare(a.day));
 const files=records.flatMap(m=>(m.media||[]).map(id=>({id,m})));
 return '<p class="eyebrow">OUR STORY IN PICTURES</p><div class="section-heading"><h1>Photos & recordings</h1><small>'+files.length+' original files</small></div>'+
 '<p class="muted">Tap a photo, video or voice recording to see the original and save a copy.</p>'+
 '<div class="photo-grid">'+(files.length?files.map(x=>
  '<div class="photo-card" data-command="details" data-id="'+safe(x.m.id)+'"><div class="image-slot" data-photo="'+safe(x.id)+'"></div>'+
  '<div class="photo-caption">'+safe(x.m.title)+'<small>'+safe(x.m.day)+' · '+safe(mediaMime(x.id).split('/')[0])+'</small></div></div>'
  ).join(''):blank('Your gallery awaits','Add a photo or recording to a memory.'))+'</div>';
}
async function vault(){
 const p=await profile(),deleted=await all('trash'),saved=await all('memories'),files=await all('media');
 const connection=await currentHub();
 const isOwner=!!window.ourHub;
 const hubUi=isOwner?'<div class="card"><h3>♡ Your Windows Family Hub</h3><p class="muted">This PC stores the master family archive. It listens only on your own computer until you connect it privately through Tailscale.</p>'+
 '<div class="buttonrow">'+btn('✓ Sync with this PC','sync-now','','primary')+
 btn('＋ Invite a family member','hub-invite')+btn('Manage paired devices','hub-devices')+
 btn('Connect family internet access','hub-network')+btn('Setup help','hub-help')+'</div>'+
 '<p class="subtle">Hub port: 43877 · No public web port exposed · Leave the Windows app running to receive updates.</p></div>':
 '<div class="card"><h3>♡ Connect to your private family hub</h3>'+
 '<p class="muted">Pair once with the Windows PC. Afterward, updates transfer automatically while this app is open. No daily ZIPs.</p>'+
 '<form id="hubPairForm">'+input('url','Private hub HTTPS address',connection?.url||'')+
 input('code','One-time family invitation code','')+
 input('label','This device is',p.contributorName||'Family member')+
 '<div class="buttonrow"><button type="submit" class="primary">Pair this device</button>'+
 (connection?.token?btn('Sync now','sync-now')+btn('Disconnect','hub-disconnect'):'')+
 '</div></form></div>';

 const size=(files.reduce((n,x)=>n+(x.size||x.blob?.size||0),0)/1048576).toFixed(1);
 return '<p class="eyebrow">YOURS, OFFLINE AND PRIVATE</p><h1>The family vault</h1><p class="muted">Manual ZIP sharing keeps everyone together without a cloud account.</p>'+
 '<div style="margin-top:24px" class="stack">'+hubUi+'</div>'+
 '<div class="grid two" style="margin-top:26px"><div class="card"><h3>Import a family ZIP</h3>'+
 '<p class="muted">Merge the latest archive from Android or another family member. Existing IDs are skipped; new photos and letters are added.</p>'+
 btn('↑ Import ZIP','import','','primary')+'</div>'+
 '<div class="card"><h3>Export memories</h3><p class="muted">Save every original photo, video and recording, or export just family-visible entries.</p>'+
 '<div class="buttonrow">'+btn('↓ Full backup','export','','primary')+btn('Family ZIP','family')+'</div></div>'+
 '<div class="card"><h3>Verify your backup</h3><p class="muted">Read an archive and check its media and available hashes before relying on it.</p>'+
 btn('✓ Verify ZIP','verify')+'</div>'+
 '<div class="card"><h3>Recently Deleted</h3><p class="muted">'+deleted.length+' recoverable memories kept here until erased.</p>'+
 btn('Open recovery bin','trash')+'</div></div>'+
 '<div class="section-heading"><h2>Your family details</h2></div><form id="profileForm" class="card">'+
 '<div class="grid two">'+input('childName',"Child's name",p.childName)+input('contributorName','Contributor on this device',p.contributorName||'Family')+
 input('parents','Parent names',p.parents)+input('dueDate','Estimated due date',p.dueDate,'date')+'</div>'+
 '<button type="submit" class="primary">Save details</button></form>'+
 '<div class="info" style="margin-top:22px">'+saved.length+' memories · '+files.length+' originals · '+size+
 ' MB stored here. <p>No data is uploaded automatically. Browser storage can be cleared: keep a full ZIP backup somewhere else. ZIPs are not encrypted.</p></div>';
}
async function addImages(){
 for(const thumb of document.querySelectorAll('[data-thumbs]')){
  const m=await get('memories',thumb.dataset.thumbs);
  for(const name of (m?.media||[]).slice(0,4)){
   if(!mediaMime(name).startsWith('image/'))continue;
   const url=await mediaURL(name);
   if(url){const img=document.createElement('img');img.src=url;img.alt='Family memory';thumb.append(img);}
  }
 }
 for(const slot of document.querySelectorAll('[data-photo]')){
  const mime=mediaMime(slot.dataset.photo);
  if(mime.startsWith('image/')||mime.startsWith('video/')){
   const url=await mediaURL(slot.dataset.photo);
   if(url){
    const tag=document.createElement(mime.startsWith('video/')?'video':'img');
    tag.src=url;tag.alt='Memory photo';slot.append(tag);
   }
  }else{slot.classList.add('empty');slot.innerHTML='<div class="empty-symbol">♫</div><b>Voice recording</b>';}
 }
}
async function mediaURL(name){
 const blob=await media(name);if(!blob)return null;
 const url=URL.createObjectURL(blob);objectUrls.push(url);return url;
}
async function render(view=page){
 page=view;close();
 objectUrls.forEach(x=>URL.revokeObjectURL(x));objectUrls=[];
 document.querySelectorAll('[data-view]').forEach(x=>x.classList.toggle('active',x.dataset.view===view));
 const body=view==='home'?await home():view==='timeline'?await timeline():view==='letters'?await timeline(true):
  view==='albums'?await gallery():await vault();
 $('screen').innerHTML=body;
 $('counter').textContent=(await all('memories')).length+' memories';
 await addImages();
}
async function editor(id=null,kind='Memory'){
 const m=id?await get('memories',id):null;
 const p=await profile(),detail=id?(await get('details',id))?.value||{}:{};
 const kindList=['Memory','Journal','Milestone','Letter','Voice','Interview','Gratitude','Quote','Tradition'];
 const select='<label class="field"><span>Memory type</span><select name="kind">'+
 kindList.map(x=>'<option value="'+x+'"'+((m?.kind||kind)===x?' selected':'')+'>'+x+'</option>').join('')+
 '</select></label>';
 const form='<form id="memoryForm"><div class="grid two">'+select+
 input('day','Date',m?.day||dateString(),'date')+'</div>'+
 input('title','Title',m?.title||'')+input('body','Write your memory or letter',m?.body||'','textarea')+
 '<div class="grid two">'+input('author','Preserved by',detail.author||p.contributorName||'Family')+
 input('album','Album / chapter',detail.album||'')+'</div>'+input('tags','Tags (comma separated)',(detail.tags||[]).join(', '))+
 '<label class="field"><span>Attach original photos, videos or audio</span><input type="file" name="files" multiple accept="image/*,video/*,audio/*">'+
 '<small>'+(m?.media||[]).length+' existing original(s) will remain attached.</small></label>'+
 '<label class="row"><input type="checkbox" name="private"'+(detail.visibility==='private'?' checked':'')+
 '> Private (exclude from family ZIP)</label><div class="buttonrow">'+btn('● Record voice note','record')+
 '<button type="submit" class="primary">Save this memory ♡</button></div><p id="recordHint" class="subtle"></p></form>';
 pendingAudio=[];
 modal(m?'Edit this memory':kind==='Letter'?'Write a letter':'Capture a moment',form);
 $('memoryForm').addEventListener('submit',async ev=>{
  ev.preventDefault();
  const f=new FormData(ev.currentTarget),title=String(f.get('title')||'').trim();
  if(!title){message('Please enter a title.');return;}
  const newMedia=[...(m?.media||[])];
  try{
   for(const file of ev.currentTarget.querySelector('[type=file]').files)newMedia.push(await saveMedia(file));
   for(const file of pendingAudio)newMedia.push(await saveMedia(file));
   await upsertMemory({id:m?.id||uuid(),day:f.get('day')||dateString(),title,
    kind:f.get('kind')||kind,body:String(f.get('body')||''),media:newMedia},
    {...detail,author:f.get('author')||p.contributorName||'Family',
     album:f.get('album')||'',tags:String(f.get('tags')||'').split(',').map(x=>x.trim()).filter(Boolean),
     visibility:f.get('private')?'private':'family'});
   message('Your memory was saved ♡');await render('timeline');
  }catch(err){message('Save failed: '+err.message);}
 });
}
async function startRecording(){
 const status=$('recordHint'),btnEl=$('modalBody').querySelector('[data-command="record"]');
 if(recorder?.state==='recording'){recorder.stop();return;}
 if(!navigator.mediaDevices?.getUserMedia||!window.MediaRecorder){
  message('Voice recording is unavailable here. Attach an audio file instead.');return;
 }
 try{
  const stream=await navigator.mediaDevices.getUserMedia({audio:true});
  const chunks=[],mime=['audio/webm;codecs=opus','audio/mp4','audio/webm'].find(x=>MediaRecorder.isTypeSupported(x))||'';
  recorder=new MediaRecorder(stream,mime?{mimeType:mime}:undefined);
  recorder.ondataavailable=event=>{if(event.data.size)chunks.push(event.data);};
  recorder.onstop=()=>{
   stream.getTracks().forEach(t=>t.stop());
   if(chunks.length){
    const type=recorder.mimeType||'audio/webm';
    const extension=type.includes('mp4')?'.m4a':'.webm';
    pendingAudio.push(new File(chunks,'Voice-'+Date.now()+extension,{type}));
    if(status)status.textContent=pendingAudio.length+' recording(s) ready. Save this memory to keep them.';
   }
   if(btnEl)btnEl.textContent='● Record another';
   recorder=null;
  };
  recorder.start();
  if(btnEl)btnEl.textContent='■ Stop recording';
  if(status)status.textContent='Recording… tap Stop, then save your memory.';
 }catch(err){message('Microphone unavailable: '+err.message);}
}
async function details(id){
 const m=await get('memories',id);if(!m)return message('Memory not found.');
 const d=(await get('details',id))?.value||{};
 const html='<span class="pill">'+safe(m.kind)+'</span><p class="meta">'+safe(m.day)+
 ' · Preserved by '+safe(d.author||'Family')+'</p><p class="muted" style="white-space:pre-wrap">'+safe(m.body)+'</p>'+
 '<div id="originals" class="stack"></div><div class="buttonrow">'+btn('Edit memory','edit','data-id="'+safe(id)+'"')+'</div>';
 modal(m.title,html);
 for(const name of m.media||[]){
  const blob=await media(name);if(!blob)continue;
  const url=await mediaURL(name),type=mediaMime(name),box=document.createElement('div');
  box.className='info';
  const node=document.createElement(type.startsWith('image/')?'img':type.startsWith('video/')?'video':'audio');
  node.src=url;node.className='preview';node.controls=!type.startsWith('image/');
  box.append(node);
  const download=document.createElement('button');
  download.className='secondary small';download.textContent='↓ Save original file';
  download.onclick=()=>downloadBlob(blob,normalizeName(m.title)+'-'+name);
  box.append(download);$('originals').append(box);
 }
}
async function recycle(){
 const list=(await all('trash')).sort((a,b)=>b.removedAt-a.removedAt);
 modal('Recently Deleted',list.length?'<div class="stack">'+list.map(x=>
  '<article class="card"><h3>'+safe(x.memory.title)+'</h3><p class="muted">'+safe(x.memory.day)+' · '+(x.memory.media||[]).length+
  ' attachment(s)</p><div class="buttonrow">'+btn('Restore','restore','data-id="'+safe(x.id)+'"','primary')+
  btn('Erase forever','erase','data-id="'+safe(x.id)+'"','tertiary danger')+'</div></article>').join('')+'</div>':
  blank('Nothing deleted','Your stories are safe in the main timeline.'));
}
async function act(name,el){
 const id=el?.dataset.id;
 if(name==='new')return editor();
 if(name==='letter')return editor(null,'Letter');
 if(name==='edit')return editor(id);
 if(name==='details')return details(id);
 if(name==='search'){query=$('searchText')?.value||'';return render(page);}
 if(name==='star'){
  const old=(await get('details',id))?.value||{};
  await put('details',{id,value:{...old,favorite:!old.favorite}});
  return render(page);
 }
 if(name==='delete'){
  if(!confirm('Move this memory to Recently Deleted? You can restore it from the Vault.'))return;
  await recycleMemory(id);message('Memory moved to Recently Deleted.');return render(page);
 }
 if(name==='record')return startRecording();
 if(name==='sync-now'){
  message('Synchronizing with your private PC…');
  const result=await syncNow();
  message('Sync complete: '+result.received+' new memories, '+result.downloaded+' downloaded originals, '+result.media+' uploads.');
  await render(page);return;
 }
 if(name==='hub-disconnect'){
  if(confirm('Disconnect this device from Family Sync? Existing memories stay here.'))
   {await clearHub();message('Device disconnected.');return render('vault');}
 }
 if(name==='hub-network'){
  if(!window.ourHub?.privateNetwork)throw Error('This feature runs only on the Windows PC');
  message('Checking your PC private network…');
  const result=await window.ourHub.privateNetwork();
  return modal(result.ok?'Private family network is ready':'Private network setup needs a step',
    result.ok?'<div class="info"><b>Share this private HTTPS address with invited family members:</b><p style="overflow-wrap:anywhere">'+
      safe(result.url||'Check Tailscale Serve status')+'</p>'+
      '<p>Your Windows PC is the master hub. Next, create one-time invitation codes for your relatives.</p></div>':
      '<div class="info">'+safe(result.error||'Tailscale is not ready')+
      '<p>Install the free Tailscale client on your PC, sign in, then press Connect family internet access again.</p></div>');
 }
 if(name==='hub-help')return modal('Private remote access',
  '<div class="info">1. Install Tailscale on your Windows PC and sign in with a free personal account.<br>'+
  '2. Run <code>tailscale serve --bg 43877</code> from Windows Terminal once; it provides a private HTTPS address ending in .ts.net.<br>'+
  '3. Invite family members to your private Tailscale network, then share their one-time Our Little Universe pairing code.<br><br>'+
  'Do not use Tailscale Funnel or forward a public router port. Your originals stay on this PC.</div>');
 if(name==='hub-invite'){
  if(!window.ourHub)throw Error('Invitations can be created on the owner PC only');
  const label=prompt('Who are you inviting? (e.g. Grandma)');if(!label)return;
  const role=confirm('Should '+label+' be allowed to add memories? Select OK for Contributor, or Cancel for read-only.')?'contributor':'viewer';
  const invite=await window.ourHub.invite(label,role);
  modal('Invitation for '+label,
   '<div class="info"><b>One-time code</b><p style="overflow-wrap:anywhere;font-size:21px">'+safe(invite.code)+'</p>'+
   '<p>This code expires in 7 days and can be used once.</p>'+
   '<p>Share your private HTTPS address from Tailscale and the code privately. Do not put family codes on GitHub.</p></div>');
  return;
 }
 if(name==='hub-devices'){
  if(!window.ourHub)throw Error('Only available on the owner PC');
  const devices=await window.ourHub.devices();
  return modal('Approved family devices',devices.length?
   '<div class="stack">'+devices.map(d=>'<div class="card">'+safe(d.label)+
   ' · '+safe(d.role)+(d.revoked?' · Revoked':'')+
   (d.revoked?'':btn('Revoke access','hub-revoke','data-id="'+safe(d.id)+'"','tertiary danger'))+
   '</div>').join('')+'</div>':blank('No paired devices','Create an invitation to add your first family member.'));
 }
 if(name==='hub-revoke'){
  if(!window.ourHub||!confirm('Revoke this device? It will not be able to sync again until you issue a new invite.'))return;
  await window.ourHub.revoke(id);message('Device access revoked.');return act('hub-devices',el);
 }
 if(name==='import'||name==='verify'){
  $('zipInput').dataset.mode=name;$('zipInput').click();return;
 }
 if(name==='export'||name==='family'){
  message('Preparing archive with original photos and recordings…');
  const zip=await exportZip({family:name==='family'});
  downloadBlob(zip,'Our_Little_Universe_'+(name==='family'?'Family':'Full_Backup')+'_'+dateString()+'.zip');
  return message('ZIP prepared. Keep it somewhere safe.');
 }
 if(name==='trash')return recycle();
 if(name==='restore'){await restoreMemory(id);message('Memory recovered ♡');return recycle();}
 if(name==='erase'){
  if(!confirm('Erase forever? This cannot be undone without a backup.'))return;
  await removeForever(id);message('Permanently removed.');return recycle();
 }
}
document.addEventListener('click',event=>{
 const nav=event.target.closest('[data-view]');if(nav){render(nav.dataset.view).catch(e=>message(e.message));return;}
 const cmd=event.target.closest('[data-command]');
 if(cmd)act(cmd.dataset.command,cmd).catch(e=>message(e.message));
});
document.addEventListener('submit',async event=>{
 if(event.target.id==='hubPairForm'){
  event.preventDefault();const data=new FormData(event.target);
  try{
   const connection=await pairHub(data.get('url'),data.get('code'),data.get('label'));
   message('This device is paired! Synchronizing your family's memories…');
   await syncNow();await render('vault');
  }catch(err){message('Pairing failed: '+err.message);}
  return;
 }
 if(event.target.id!=='profileForm')return;
 event.preventDefault();
 const p=await profile(),changes=Object.fromEntries(new FormData(event.target).entries());
 await saveProfile({...p,...changes});message('Family details saved.');await render('vault');
});
document.addEventListener('change',event=>{
 if(event.target.id==='filterList'){filter=event.target.value;render(page);}
});
$('createButton').onclick=()=>editor();
$('closeModal').onclick=close;
$('modal').onclick=event=>{if(event.target===$('modal'))close();};
$('zipInput').onchange=async event=>{
 const f=event.target.files?.[0],mode=event.target.dataset.mode;
 event.target.value='';if(!f)return;
 try{
  message('Checking the original files in your ZIP…');
  if(mode==='verify'){
   const result=await checkZip(f);
   modal('Backup verified ✓','<div class="info">'+result.memories+' memories · '+result.media+' original files · '+
    result.removed+' recoverable deleted memories.<br>'+
    (result.hashes?'SHA-256 file checksums matched.':'Older archive: files and ZIP CRC verified.')+'</div>');
  }else{
   const result=await importZip(f);
   modal('Family memories merged ♡','<div class="info">'+result.added+' new memories added.<br>'+
    result.existing+' already saved (no duplicates).<br>'+
    result.attachments+' new attachments merged.<br>'+
    result.media+' originals checked.<br><br>Your existing wording was preserved.</div>');
  }
 }catch(err){modal('Could not import ZIP','<div class="info">'+safe(err.message||err)+
  '<br><br>Do not delete your old copy of the archive.</div>');}
};
async function launchFamilySync(){
 if(window.ourHub){
  const admin=await window.ourHub.info();
  if(admin?.adminToken&&!admin.error){
   await setHostHub(admin.url,admin.adminToken);
  }
 }
 beginAutoSync(result=>{
  if(result.ok&&result.result?.received>0){
   message(result.result.received+' new family memories received ♡');
   if(!$('modal').hidden)return;
   render(page).catch(()=>{});
  }
 });
 const connection=await currentHub();
 if(connection?.token&&!connection.disabled){
  try{await syncNow();await render(page);}catch(error){
   console.warn('Family hub not reachable yet:',error.message);
  }
 }
}
openStore().then(async()=>{
 await render('home');launchFamilySync().catch(console.error);
}).catch(err=>{
 $('screen').innerHTML=blank('Storage unavailable',err.message+' — open the app in a normal browser.');
});
