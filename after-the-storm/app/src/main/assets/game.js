/* AFTER THE STORM · HEAVY RESCUE 3D — improved controls, darker lighting, richer equipment HUD */
(()=>{'use strict';
const $=s=>document.querySelector(s), $$=s=>Array.from(document.querySelectorAll(s));
const clamp=THREE.MathUtils.clamp, TAU=Math.PI*2;
const V=(x=0,y=0,z=0)=>new THREE.Vector3(x,y,z);
const rnd=(a,b)=>a+Math.random()*(b-a);
let renderer,scene,camera,clock;
let houses=[],debris=[],particles=[],vehicles={},player,depot;
let active='dozer',started=false,storm=3,score=0,completed=0,initial=0,gameTime=0,cameraSide=0,noticeUntil=0;
let cargo=null,target=null,invertSteer=false,safetyChecked=true;
const input={x:0,y:0};
const state={};
const textures={}, mats={};
const geo={cube:new THREE.BoxGeometry(1,1,1),cyl:new THREE.CylinderGeometry(1,1,1,14),sphere:new THREE.SphereGeometry(1,14,10),cone:new THREE.ConeGeometry(1,1,10),torus:new THREE.TorusGeometry(1,.14,10,18)};

function makeNoiseTexture(c1,c2,size=128,scale=8){
  const key=[c1,c2,size,scale].join('|'); if(textures[key]) return textures[key];
  const cv=document.createElement('canvas'); cv.width=cv.height=size; const ctx=cv.getContext('2d');
  const g=ctx.createLinearGradient(0,0,size,size); g.addColorStop(0,c1); g.addColorStop(1,c2); ctx.fillStyle=g; ctx.fillRect(0,0,size,size);
  for(let i=0;i<size*size/3;i++){
    const x=Math.random()*size,y=Math.random()*size,s=Math.random()*3+1;
    ctx.fillStyle=Math.random()>.5?'rgba(255,255,255,.06)':'rgba(0,0,0,.06)'; ctx.fillRect(x,y,s,s);
  }
  for(let i=0;i<50;i++){ctx.strokeStyle='rgba(0,0,0,.04)';ctx.beginPath();ctx.moveTo(Math.random()*size,Math.random()*size);ctx.lineTo(Math.random()*size,Math.random()*size);ctx.stroke();}
  const t=new THREE.CanvasTexture(cv); t.wrapS=t.wrapT=THREE.RepeatWrapping; t.repeat.set(scale,scale); t.anisotropy=2; return textures[key]=t;
}
function material(opts){const key=JSON.stringify(opts); if(mats[key]) return mats[key]; return mats[key]=new THREE.MeshStandardMaterial(opts);}
const texSand=makeNoiseTexture('#90836a','#b0a386',128,20), texGrass=makeNoiseTexture('#506b4f','#6d835e',128,18), texRoad=makeNoiseTexture('#424648','#2e3437',128,28), texRoof=makeNoiseTexture('#66635e','#4b4e50',128,12), texMetal=makeNoiseTexture('#7c8788','#4f585c',128,10), texLeaves=makeNoiseTexture('#50784e','#284b30',128,8), texTrunk=makeNoiseTexture('#7a5b3e','#5e4731',128,8);
const matGlass=new THREE.MeshStandardMaterial({color:0x89adb3,roughness:.08,metalness:.18,transparent:true,opacity:.55,side:THREE.DoubleSide});
const matSand=material({color:0xb6aa8d,map:texSand,roughness:1});
const matGrass=material({color:0x5d7159,map:texGrass,roughness:1});
const matRoad=material({color:0x404446,map:texRoad,roughness:.96});
const matMetal=material({color:0x66767c,map:texMetal,roughness:.84,metalness:.18});
const matRubber=material({color:0x1f2224,roughness:1});
const matLeaf=material({color:0x4e6f49,map:texLeaves,roughness:1});
const matTrunk=material({color:0x72553c,map:texTrunk,roughness:1});
function solid(c,rough=.85,metal=.05){return material({color:c,roughness:rough,metalness:metal});}

function group(parent,x=0,y=0,z=0){const g=new THREE.Group();g.position.set(x,y,z);parent.add(g);return g;}
function mesh(parent,g,m,x=0,y=0,z=0){const o=new THREE.Mesh(g,m);o.position.set(x,y,z);o.castShadow=o.receiveShadow=false;parent.add(o);return o;}
function box(p,m,x,y,z,sx,sy,sz){const o=mesh(p,geo.cube,m,x,y,z);o.scale.set(sx,sy,sz);return o;}
function cyl(p,m,x,y,z,r,h,rx=0,rz=0){const o=mesh(p,geo.cyl,m,x,y,z);o.scale.set(r,h,r);o.rotation.set(rx,0,rz);return o;}
function sphere(p,m,x,y,z,rx,ry,rz){const o=mesh(p,geo.sphere,m,x,y,z);o.scale.set(rx,ry,rz);return o;}
function beam(p,m,a,b,r=.14,r2){const d=V().subVectors(b,a),o=mesh(p,r2?new THREE.CylinderGeometry(r2,r,d.length(),10):geo.cyl,m);o.position.copy(a).addScaledVector(d,.5);o.quaternion.setFromUnitVectors(V(0,1,0),d.clone().normalize());if(!r2)o.scale.set(r,d.length(),r);return o;}
function plane(p,m,x,y,z,w,d,rot=-Math.PI/2){const o=mesh(p,new THREE.PlaneGeometry(w,d),m,x,y,z);o.rotation.x=rot;return o;}
function ring(p,m,x,y,z,r,rotX=Math.PI/2){const o=mesh(p,geo.torus,m,x,y,z);o.scale.setScalar(r);o.rotation.x=rotX;return o;}
function dist2(a,b){return Math.hypot(a.x-b.x,a.z-b.z)}
function seeded(n){let seed=n>>>0; return ()=>{seed=(1664525*seed+1013904223)>>>0; return seed/4294967296;};}
let rand=Math.random; function R(a,b){return a+rand()*(b-a)}

function makeWheel(p,x,y,z,r=.65,w=.34){
  const wrap=group(p,x,y,z); cyl(wrap,matRubber,0,0,0,r,w,0,Math.PI/2);
  const hub=cyl(wrap,solid(0xa9b2ae,.35,.4),Math.sign(x)*w*.56,0,0,r*.5,w*.1,0,Math.PI/2);
  for(let k=0;k<8;k++){const a=k*TAU/8; sphere(wrap,solid(0x4f5c62,.6),Math.sign(x)*w*.62,Math.cos(a)*r*.33,Math.sin(a)*r*.33,.05,.05,.05);} return wrap;
}
function makeTrack(p,x,z=0,len=3.75){const g=group(p,x,.56,z); box(g,solid(0x2b3031,.96),0,0,0,.78,.96,len); for(let i=0;i<8;i++){let zz=-len*.41+i*len*.82/7; cyl(g,solid(0x626c6d,.8),Math.sign(x)*.42,-.02,zz,.3,.09,0,Math.PI/2);} for(let side of [-1,1]) for(let i=0;i<10;i++) box(g,solid(0x151819,.98),0,side*.33,-len*.47+i*len*.104,.8,.14,.26); return g;}
function makeCab(p,z=0,y=2.25,x=0,sz=1){const cab=group(p,x,y,z); box(cab,solid(0x2e3739,.72),0,-.55,0,2.15*sz,.2,2.05*sz); for(let s of [-1,1]){ box(cab,solid(0x384348,.72),s*.95*sz,.18,0,.16,1.65*sz,2.15*sz); box(cab,matGlass,s*1.005*sz,.23,0,.028,1.38*sz,1.67*sz);} for(let s of [-1,1]){ box(cab,solid(0x35484c,.72),0,.19,s*.91*sz,2.0*sz,1.6*sz,.12); box(cab,matGlass,0,.25,s*.988*sz,1.7*sz,1.33*sz,.025);} box(cab,solid(0xffbf40,.55),0,1.07*sz,0,2.42*sz,.22,2.32*sz); return cab;}

function makeDozer(){
  const root=group(scene,-8,0,32); box(root,solid(0xcf912e,.7),0,1.4,-.15,3.1,1.38,3.85); box(root,solid(0x232c30,.9),0,2.16,-1.02,2.6,.23,1.85);
  for(let x of [-1.46,1.46]) makeTrack(root,x,-.1,4.45); makeCab(root,-1.05,2.72,0,.87);
  for(let s of [-1,1]){ box(root,solid(0x2f3532,.8),s*1.31,1.98,.62,.16,.12,3.4); box(root,solid(0xd5a343,.62),s*1.61,1.77,1.55,.28,.26,2.7); beam(root,solid(0xc5c2b1,.55),V(s*1.55,1.77,1.3),V(s*1.4,1.95,3.5),.18); }
  const blade=group(root,0,1.3,3.5); box(blade,solid(0xd9aa46,.58),0,0,0,4.25,1.68,.28); box(blade,solid(0x454849,.92),0,-.82,.09,4.4,.18,.34); box(blade,solid(0xf0d49a,.55),0,.82,.02,4.3,.11,.32); for(let i=0;i<9;i++) box(blade,solid(0x806939,.9),-1.91+i*.48,-.89,.11,.06,.21,.11);
  const dozer={name:'dozer',root,blade,speed:0,bladeHeight:1.3}; return dozer;
}
function makeLoader(){
  const root=group(scene,-4,0,24); box(root,solid(0xe1b23e,.68),0,1.43,0,3.35,1.28,4.25); box(root,solid(0x454644,.86),0,2.01,-1.12,2.82,.29,1.66); makeCab(root,-.95,2.6,0,.85);
  for(let x of [-1.47,1.47]) for(let z of [-1.48,1.52]) makeWheel(root,x,.77,z,.82,.44);
  const lift=group(root,0,1.5,1.12); for(let x of [-1.35,1.35]){ beam(lift,solid(0xe0a839,.62),V(x,0,0),V(x,-.36,2.65),.23); beam(lift,solid(0xb7b5a0,.52),V(x,0,-.2),V(x,-.1,1.75),.10); }
  const bucket=group(lift,0,-.42,3); box(bucket,solid(0xaeb0ab,.62),0,-.23,.28,3.35,.53,1.35); box(bucket,solid(0x5a686f,.86),0,-.02,-.4,3.35,1.14,.15); box(bucket,solid(0xd5bb79,.64),0,-.55,.85,3.5,.11,.18); for(let x of [-1.56,1.56]) box(bucket,solid(0x7d8079,.82),x,-.17,.13,.13,.73,1.45);
  return {name:'loader',root,lift,bucket,speed:0,liftAngle:0,bucketCurl:0};
}
function makeExcavator(){
  const root=group(scene,1,0,32); for(let x of [-1.27,1.27]) makeTrack(root,x,0,4.65); box(root,solid(0x4e4a39,.8),0,1.18,0,2.7,.45,3.1);
  const turret=group(root,0,1.6,0); cyl(turret,solid(0x292f31,.9),0,-.24,0,1.38,.32); box(turret,solid(0xd9ac3a,.66),0,.43,-.35,3.08,1.58,3.05); box(turret,solid(0x474c45,.88),0,1.29,-.51,3.05,.2,3.03);
  const cab=group(turret,-.77,.95,.05); box(cab,solid(0x2b3439,.72),0,-.2,0,1.33,1.35,1.6); box(cab,matGlass,-.691,-.05,.05,.03,1.06,1.4); box(cab,matGlass,.0,-.07,.85,1.26,1.05,.03);
  const boom=group(turret,.68,.96,.9); beam(boom,solid(0xe2b53a,.62),V(0,0,0),V(0,2.5,2.9),.37); beam(boom,solid(0xaaa897,.5),V(-.22,.16,.2),V(-.22,1.74,1.9),.15);
  const fore=group(boom,0,2.5,2.9); beam(fore,solid(0xe1b844,.62),V(0,0,0),V(0,-2.45,2.6),.29); beam(fore,solid(0xc0beb2,.52),V(.1,-.4,.55),V(.1,-1.8,2.06),.09);
  const bucket=group(fore,0,-2.52,2.73); box(bucket,solid(0x545f63,.78),0,-.29,.3,1.7,.58,.94); box(bucket,solid(0x9b9a89,.65),0,-.65,.55,1.8,.13,.6); for(let i=-2;i<=2;i++) box(bucket,solid(0xcfcab2,.58),i*.35,-.65,.87,.13,.2,.27);
  return {name:'excavator',root,turret,boom,fore,bucket,speed:0,turretAngle:0,boomAngle:0,stickAngle:0,bucketAngle:0};
}
function makeCrew(){
  const root=group(scene,-1,0,23); box(root,solid(0x343b39,.8),0,1.08,0,.85,.92,.48); box(root,solid(0xefbb5a,.5),0,1.27,.295,.83,.68,.14); sphere(root,solid(0xb89072,.9),0,1.97,0,.38,.39,.36); box(root,solid(0xf7d64b,.48),0,2.28,0,.76,.17,.66);
  for(let x of [-.2,.2]){ box(root,solid(0x283039,.85),x,.42,0,.31,.87,.34); box(root,solid(0x7d8582,.75),x,.05,.16,.35,.18,.53);} for(let x of [-.52,.52]) box(root,solid(0xd99a65,.82),x,1.28,.04,.25,.76,.25);
  const saw=group(root,.53,.95,.45); box(saw,solid(0xe07631,.58),0,0,0,.62,.3,.58); box(saw,solid(0x23282a,.92),-.18,.22,-.1,.22,.23,.24); box(saw,solid(0x202527,.92),.08,.25,.08,.18,.14,.2); box(saw,solid(0xcfd2d3,.46),0,-.02,.75,.18,.08,1.35); for(let z of [.25,.42,.58,.73,.9,1.05]) box(saw,solid(0x323a3c,.94),0,.06,z,.25,.05,.05); box(saw,solid(0x111517,.96),0,.28,.22,.5,.08,.14);
  return {name:'crew',root,saw,speed:0};
}
function makeSkidder(){
  const root=group(scene,14,0,37);
  const placeholder=group(root,0,0,0);
  const chassis=box(placeholder,solid(0xb6a640,.65),0,1.45,0,2.8,1.3,5.6);
  box(placeholder,solid(0x2f393b,.8),0,2.4,1,2.2,1.6,2.2);
  for(const x of [-1.5,1.5])for(const z of [-1.65,1.65])makeWheel(placeholder,x,.9,z,.9,.47);
  const front=group(root,0,0,1.45);
  // Separate visible hydraulic blade and rear grapple. Imported FBX meshes provide the detailed chassis.
  const blade=group(root,0,1.1,4);
  box(blade,solid(0x9eaa9f,.8),0,0,0,3.6,1.05,.22);
  const boom=group(root,0,2.8,-3.6);
  beam(boom,solid(0xc1a634,.54),V(0,0,.5),V(0,-.45,-2),.2);
  beam(boom,solid(0x8f999b,.45),V(.25,-.1,.3),V(.25,-.39,-1.8),.12);
  const claw=group(boom,0,-.4,-2.2);
  const left=group(claw,-.43,0,0),right=group(claw,.43,0,0);
  box(left,solid(0x414a4c,.72),-.04,-.23,-.12,.22,.7,.35);
  box(right,solid(0x414a4c,.72),.04,-.23,-.12,.22,.7,.35);
  for(const g of [left,right])box(g,solid(0x8a8f86,.7),0,-.56,-.26,.28,.14,.6);
  const item={name:'skidder',root,front,placeholder,blade,boom,claw,left,right,
     speed:0,boomLift:0,boomExtend:0,bladeHeight:1.1,grappleOpen:1.0,engine:true,brake:false,
     loads:[],modelReady:false,steerAngle:0};
  // GLB is bundled with the app and parsed locally; offline engine does not need any remote assets.
  loadSkidderGeometry().then(model=>{
    if(item.root.parent!==scene) return;
    const shell=group(root,.56,0,1.24);shell.name='545D_real_asset';shell.add(model);
    item.placeholder.visible=false;
    item.modelReady=true;
    // Convert original, independently modeled gripper prongs into hinged sections.
    for(const [meshname,hinge] of [['polySurface1764',left],['polySurface1765',right]]){
      const n=model.getObjectByName(meshname);
      if(n&&n.children.length){
        n.visible=false;
        for(const m of n.children){if(!m.isMesh)continue;
          const geometry=m.geometry.clone();
          // Source vertex coords are model-local. Translate into hinge-local coordinates.
          geometry.translate(.56+(0),-2.4,1.24+5.65);
          const animated=new THREE.Mesh(geometry,m.material);animated.frustumCulled=false;
          // Claws already have precise shape from source FBX.
          hinge.add(animated);
        }
      }
    }
    notice('CAT 545D REAL MODEL LOADED',2200);
  }).catch(e=>{
    console.warn('skidder GLB load failed',e);
    notice('SKIDDER MODEL ERROR · USING BACKUP MESH',4500);
  });
  return item;
}
function makeHouse(x,z,index){
  const g=group(scene,x,0,z),w=R(8,12),d=R(9,12), wallColors=[0x9baeb2,0xd1ba9c,0xc5c8ba,0x9dab9b,0xb9a6a0,0xcabfae];
  const wallMat=material({color:wallColors[index%wallColors.length],roughness:.95}); const roofMat=material({color:[0x51565c,0x79665c,0x55504d,0x424e50][index%4],map:texRoof,roughness:.98});
  box(g,wallMat,0,2.45,0,w,4.9,d); box(g,solid(0xc4c4bd,.9),0,4.96,0,w+.5,.28,d+.5); const roof=box(g,roofMat,0,5.92,0,w+1,1.75,d+1); roof.rotation.z=index%4===2?.08:0;
  for(let s of [-1,1]) for(let i=-1;i<=1;i++){ box(g,matGlass,s*(w/2+.025),3,i*d*.27,.08,1.45,1.35); box(g,solid(0xebdad0,.85),s*(w/2+.08),3,i*d*.27,.13,.11,1.35); }
  box(g,solid(0x3f5355,.85),0,1.7,d*.507,1.55,3.4,.11); box(g,solid(0xc6d4ca,.76),0,2.02,d*.57,.67,.63,.08); for(let i=0;i<3;i++){let zz=d/2+.5+i*1.12; box(g,solid(0xb8b4ac,.96),0,.18,zz,w*.7,.28,.95);}
  const damaged=R(0,1)<.65; if(damaged){ roof.scale.x*=R(.6,.94); roof.rotation.z+=R(-.15,.17); for(let i=0;i<5;i++) box(g,roofMat,R(-w*.46,w*.46),5.03+R(0,.2),R(-d*.48,d*.48),R(.5,1.8),.18,R(.6,1.5)); }
  houses.push({g,x,z,r:Math.max(w,d)/2+1,damaged}); return g;
}
function uprightTree(x,z,h=R(7,12),damaged=false){
  const root=group(scene,x,0,z); const trunk=cyl(root,matTrunk,0,h*.44,0,.32,h*.88); trunk.rotation.z=R(-.045,.045);
  const crown=group(root,0,h*.84,0); for(let i=0;i<9;i++) sphere(crown,matLeaf,R(-1.65,1.65),R(-.65,1.2),R(-1.65,1.65),R(1.15,2.1),R(1.15,2.2),R(1.15,2)); if(damaged) root.rotation.z=R(-.3,.3); return root;
}
function palm(x,z){const g=group(scene,x,0,z); for(let i=0;i<5;i++) cyl(g,matTrunk,Math.sin(i*.4)*.13,1+i*1.4,Math.sin(i*.33)*.15,.25-(i*.018),1.5,0,Math.sin(i*.3)*-.03); let h=8; for(let k=0;k<9;k++){let a=k*TAU/9; let p=group(g,.2,h,.1); p.rotation.y=a; let leaf=mesh(p,new THREE.ConeGeometry(.54,5.5,6),matLeaf,0,-.5,2.5); leaf.rotation.x=.81; leaf.scale.set(1,.8,1);} return g;}
function roadMarkings(){
  const root=group(scene); box(root,matRoad,0,.02,0,18,.05,142); box(root,matRoad,0,.025,0,142,.06,18);
  box(root,solid(0x5b6664,.82),-9,.04,0,.22,.025,142); box(root,solid(0x5b6664,.82),9,.04,0,.22,.025,142); box(root,solid(0x5b6664,.82),0,.04,-9,142,.025,.2); box(root,solid(0x5b6664,.82),0,.04,9,142,.025,.2);
  for(let z=-64;z<65;z+=9) box(root,solid(0xd7c26f,.6),0,.07,z,.17,.016,4.2); for(let x=-64;x<65;x+=9) box(root,solid(0xd7c26f,.6),x,.076,0,4.2,.018,.17);
}
function setupLights(){
  scene.background=new THREE.Color(0x5f777a); scene.fog=new THREE.FogExp2(0x708487,.0105);
  scene.add(new THREE.AmbientLight(0xdce8e2,.26));
  scene.add(new THREE.HemisphereLight(0xafc2c5,0x465343,.68));
  const sun=new THREE.DirectionalLight(0xffe1b8,1.08); sun.position.set(-70,110,-60); scene.add(sun);
}
function spawnDebris(kind,x,z,weight,radius){
  const g=group(scene,x,0,z); let y=.2;
  if(kind==='tree'){ box(g,matTrunk,0,.33,0,3.2,.42,.55); for(let i=0;i<4;i++) sphere(g,matLeaf,R(-1.1,1.1),.75+R(-.1,.3),R(-.9,.9),.8,.7,.8); y=.35; }
  else if(kind==='log'){ box(g,matTrunk,0,.22,0,2.2,.32,.42); y=.22; }
  else if(kind==='rubble'){ for(let i=0;i<4;i++) box(g,solid(i%2?0x8f7b62:0x6d6358,.96),R(-.55,.55),.22+R(-.05,.16),R(-.5,.5),R(.4,.8),R(.22,.5),R(.4,.8)); y=.22; }
  else if(kind==='beam'){ for(let i=0;i<3;i++) box(g,solid(0x7a5b3e,.92),R(-.55,.55),.18,R(-.45,.45),2.4,.18,.25); y=.18; }
  else if(kind==='car'){ box(g,solid(0x7d4c48,.74),0,.72,0,2.6,.8,4.2); box(g,solid(0x445861,.74),0,1.25,-.1,2.05,.56,1.92); for(let sx of [-1,1]) for(let sz of [-1.5,1.5]) makeWheel(g,sx*1.15,.38,sz,.48,.24); y=.72; }
  else { box(g,matMetal,0,.4,0,1.6,.8,1.6); y=.4; }
  const obj={g,kind,weight,radius,cleared:false,carried:false,vel:V(),y0:y}; debris.push(obj); return obj;
}
function makeWorld(){
  houses=[]; debris=[]; particles=[]; scene=new THREE.Scene(); setupLights();
  plane(scene,matSand,0,-.08,0,300,300); const turf=box(scene,matGrass,0,-.41,0,180,.6,180); turf.material.map.repeat.set(30,30); roadMarkings();
  for(let i=0;i<20;i++){const x=(i%2?1:-1)*R(18,58), z=-56+(i%10)*12; makeHouse(x,z,i);}
  for(let i=0;i<12;i++) uprightTree(R(-66,66),R(-66,66),R(6,12),false); for(let i=0;i<9;i++) palm(R(-66,66),R(-66,66));
  for(let i=0;i<8+storm*3;i++){const r=R(-58,58),z=R(-58,58); if(Math.abs(r)<10||Math.abs(z)<10){i--; continue;} spawnDebris('tree',r,z,0,1.3);} 
  for(let i=0;i<10+storm*3;i++){const r=R(-60,60),z=R(-60,60), kinds=['log','rubble','beam','car']; const kind=kinds[Math.floor(R(0,kinds.length))]; const weight=kind==='car'?3:kind==='beam'?2:1; const radius=kind==='car'?2.2:1.3; spawnDebris(kind,r,z,weight,radius);}
  depot={x:58,z:-58}; const yard=group(scene,depot.x,0,depot.z); box(yard,solid(0xe0b14e,.62),0,.08,0,12,.12,12); for(let k=0;k<4;k++) box(yard,solid(0x4a504f,.85),0,.38,-6+k*4,12,.22,.18);
  vehicles={dozer:makeDozer(),loader:makeLoader(),excavator:makeExcavator(),crew:makeCrew(),skidder:makeSkidder()};
  for(const [x,z] of [[13,29],[16,27],[10,28],[14,21]])spawnDebris('tree',x,z,0,1.3); player=vehicles[active]; initial=debris.length; score=0; completed=0; cargo=null; target=null; updateHUD();
}

function particlesAt(x,y,z,c,n=1){for(let i=0;i<n;i++){const o=mesh(scene,geo.cube,solid(c,.9),x+rnd(-.15,.15),y+rnd(-.1,.2),z+rnd(-.15,.15));o.scale.setScalar(rnd(.08,.18));particles.push({o,life:rnd(.35,.8),v:V(rnd(-.45,.45),rnd(.6,1.4),rnd(-.45,.45))});}}
function beep(freq=440,d=.1){try{const ctx=(beep.ctx||(beep.ctx=new (window.AudioContext||window.webkitAudioContext)())); const o=ctx.createOscillator(),g=ctx.createGain(); o.type='triangle'; o.frequency.value=freq; g.gain.value=.025; o.connect(g); g.connect(ctx.destination); o.start(); g.gain.exponentialRampToValueAtTime(.0001,ctx.currentTime+d); o.stop(ctx.currentTime+d);}catch(e){}}
function notice(msg,time=1300){$('#midNotice').textContent=msg; $('#midNotice').style.opacity=1; noticeUntil=performance.now()+time;}
function pct(){return initial?Math.floor(completed/initial*100):100}
function nearDepot(p){return Math.hypot(p.x-depot.x,p.z-depot.z)<8.5}
function getNearest(r=5,filter){ let best=null,d=1e9; const pos=player.root.position; for(const o of debris){ if(o.cleared||o.carried) continue; if(filter && !filter(o)) continue; const q=dist2(pos,o.g.position); if(q<r&&q<d){best=o; d=q;}} return best; }
function nearestToPoint(pt,r=3,filter){ let best=null,d=1e9; for(const o of debris){ if(o.cleared||o.carried) continue; if(filter && !filter(o)) continue; const q=Math.hypot(pt.x-o.g.position.x,pt.z-o.g.position.z); if(q<r&&q<d){best=o;d=q;}} return best; }
function clearDebris(o){ if(!o||o.cleared) return; o.cleared=true; scene.remove(o.g); completed++; score+=o.kind==='car'?120:o.kind==='tree'?80:o.weight===2?70:45; particlesAt(o.g.position.x,.4,o.g.position.z,0xc9b483,8); beep(640,.14); updateHUD(); if(completed>=initial) notice('MISSION COMPLETE · TOWN ACCESS RESTORED',4000); }
function collectCargo(){ if(!cargo) return; clearDebris(cargo); cargo=null; player.loaded=null; notice('LOAD DELIVERED'); }
function dropCargoLoose(){ if(!cargo) return; cargo.carried=false; const a=player.root.rotation.y,f=active==='crew'?1.1:3.4; cargo.g.position.set(player.root.position.x+Math.sin(a)*f,cargo.y0,player.root.position.z+Math.cos(a)*f); cargo.g.rotation.y=player.root.rotation.y; cargo.vel.set(Math.sin(a)*2,0,Math.cos(a)*2); cargo=null; player.loaded=null; notice('LOAD DROPPED'); }
function avoidBuildings(p,old){ for(let h of houses){ let dx=p.x-h.x,dz=p.z-h.z,collision=h.r+(active==='crew'?0.75:1.55); if(Math.abs(dx)<collision&&Math.abs(dz)<collision){p.x=old.x; p.z=old.z; break;}} }
function setVehicle(name){ Object.keys(state).forEach(k=>state[k]=false); active=name; player=vehicles[name]; cargo=player.loaded||null; $$('.tool').forEach(b=>b.classList.toggle('selected',b.dataset.tool===name)); $$('.panel').forEach(p=>p.classList.remove('active')); $('#panel-'+name).classList.add('active'); updateHUD(); notice(name.toUpperCase()+' READY'); }

function updateHUD(){
  $('#cash').textContent='$'+score+' · CREW 01'; $('#pct').textContent=pct()+'%'; $('#bar').style.width=pct()+'%'; $('#count').textContent=completed+' / '+initial+' cleared'; $('#level').textContent='CATEGORY '+storm;
  const title={dozer:'BULLDOZER CONTROLS',loader:'WHEEL LOADER CONTROLS',excavator:'EXCAVATOR CONTROLS',crew:'CHAINSAW CREW CONTROLS',skidder:'CAT 545D · SKIDDER'};
  const sub={dozer:'Blade height, push force, cleanup mode',loader:'Lift arms, curl bucket, load and haul',excavator:'Swing, boom, stick, bucket, heavy pickup',crew:'On-foot cutting, carrying logs, sprint control',skidder:'Rear grapple · hydraulic boom · blade · brakes'};
  $('#hudTitle').textContent=title[active]; $('#hudSub').textContent=sub[active];
  if(active==='dozer') $('#machineReadout').textContent='BLADE '+(player.bladeHeight>1.7?'HIGH':player.bladeHeight<1.05?'LOW':'MID');
  if(active==='loader') $('#machineReadout').textContent='ARM '+Math.round(player.liftAngle*100)+' · BUCKET '+Math.round(player.bucketCurl*100);
  if(active==='excavator') $('#machineReadout').textContent='SWING '+Math.round(player.turretAngle*57.3)+'°';
  if(active==='crew') $('#machineReadout').textContent=safetyChecked?'GUARD OK':'CHECK GUARD';
  if(active==='skidder') $('#machineReadout').textContent=(player.engine?'ENGINE RUN':'ENGINE OFF')+' · '+player.loads.length+'/3 LOGS';
  $('#mission').textContent = completed>=initial ? 'Town access restored. Start a new storm or keep clearing.' : 'Clear hurricane debris, haul loads to depot, and reopen access roads.';
  $('#status-dozer').textContent='Dozer works best with blade low. Push loose rubble and small obstructions out of the roadway.';
  $('#status-loader').textContent = cargo?'Load secured. Drive to the yellow depot to dump it.':'Loader handles lighter debris. Scoop or pick up rubble, logs, and beams.';
  $('#status-excavator').textContent = cargo?'Heavy load attached. Deliver it to the yellow depot.':'Use swing + boom + stick + bucket to position the tool over heavy debris.';
  $('#status-crew').textContent = cargo?'Log carried by crew. Return it to the depot or drop it nearby.':'Walk up to a fallen tree and hold the chainsaw to cut and clear it.';
  $('#status-skidder').textContent=player?.name==='skidder' ? (player.loads.length+' of 3 logs gripped. '+(player.brake?'BRAKE ON. ':'')+'Reverse near fallen trees to grab, then drive to yellow depot and RELEASE.') : 'Select skidder to use the log grapple.';
}

function driveAndAnimate(dt){
  const p=player.root, old=p.position.clone();
  const speed=active==='crew'?(state.crewSprint?11:7.3):active==='excavator'?6.3:active==='dozer'?9.2:active==='skidder'?10.5:12.5;
  const turn=active==='crew'?3.6:active==='excavator'?1.55:active==='skidder'?1.3:1.85; const smooth=1-Math.exp(-dt*(active==='crew'?9:3));
  const steer=input.x*(invertSteer?-1:1), throttle=(active==='skidder'&&(!player.engine||player.brake))?0:input.y; player.speed=THREE.MathUtils.lerp(player.speed||0,throttle*speed,smooth);
  if(Math.abs(steer)>.04) p.rotation.y += steer*turn*dt*(active==='crew'?1:(.38+.62*Math.abs(player.speed)/speed));
  p.position.x += Math.sin(p.rotation.y)*player.speed*dt; p.position.z += Math.cos(p.rotation.y)*player.speed*dt; p.position.x=clamp(p.position.x,-72,72); p.position.z=clamp(p.position.z,-72,72); avoidBuildings(p.position,old);
  if(Math.abs(player.speed)>2&&active!=='crew'&&Math.random()<dt*8) particlesAt(p.position.x,.25,p.position.z,0x7d7b67,1);

  if(active==='dozer'){
    const delta=(state.dozerBladeUp?1:0)-(state.dozerBladeDown?1:0); player.bladeHeight=clamp(player.bladeHeight+delta*dt*1.5,.85,2.15); player.blade.position.y=player.bladeHeight;
  }
  if(active==='loader'){
    player.liftAngle=clamp(player.liftAngle+(((state.loaderLiftUp?1:0)-(state.loaderLiftDown?1:0))*dt*.95),-.5,.4); player.bucketCurl=clamp(player.bucketCurl+(((state.loaderCurl?1:0)-(state.loaderDump?1:0))*dt*1.25),-.85,.55); player.lift.rotation.x=player.liftAngle; player.bucket.rotation.x=.05+player.bucketCurl;
  }
  if(active==='excavator'){
    player.turretAngle=clamp(player.turretAngle+(((state.excSwingRight?1:0)-(state.excSwingLeft?1:0))*dt*1.1),-1.15,1.15); player.turret.rotation.y=player.turretAngle;
    player.boomAngle=clamp(player.boomAngle+(((state.excBoomUp?1:0)-(state.excBoomDown?1:0))*dt*.9),-.75,.5); player.stickAngle=clamp(player.stickAngle+(((state.excStickOut?1:0)-(state.excStickIn?1:0))*dt*1.05),-1.25,.2); player.bucketAngle=clamp(player.bucketAngle+(((state.excBucketCurl?1:0)-(state.excBucketDump?1:0))*dt*1.2),-1.15,.55);
    player.boom.rotation.x=player.boomAngle; player.fore.rotation.x=player.stickAngle; player.bucket.rotation.x=player.bucketAngle;
  }
  if(active==='crew') player.saw.rotation.x=state.crewSaw?Math.sin(gameTime*28)*.16:0;
  if(active==='skidder'){
     player.steerAngle=THREE.MathUtils.lerp(player.steerAngle,steer*.22,Math.min(1,dt*3));
     player.front.rotation.y=player.steerAngle;
     player.boomLift=clamp(player.boomLift+((state.skidBoomUp?1:0)-(state.skidBoomDown?1:0))*dt*.7,-.75,.6);
     player.boomExtend=clamp(player.boomExtend+((state.skidBoomExtend?1:0)-(state.skidBoomRetract?1:0))*dt*1.3,0,2.0);
     player.bladeHeight=clamp(player.bladeHeight+((state.skidBladeUp?1:0)-(state.skidBladeDown?1:0))*dt,0.6,2.05);
     player.boom.rotation.x=player.boomLift;player.boom.position.z=-3.6-player.boomExtend;
     player.blade.position.y=player.bladeHeight;
     player.left.rotation.y=-player.grappleOpen*.43; player.right.rotation.y=player.grappleOpen*.43;
     player.loads.forEach((o,i)=>{const a=p.rotation.y;const lag=5.5+i*2;const h=Math.max(.3,player.boomLift*1.4+.5);
       o.g.position.set(p.position.x-Math.sin(a)*lag,h,p.position.z-Math.cos(a)*lag);o.g.rotation.y=a;});
  }

  if(cargo){
    const a=p.rotation.y; let pos;
    if(active==='loader') pos=V(p.position.x+Math.sin(a)*3.2,1.5+(-player.lift.rotation.x*2.4),p.position.z+Math.cos(a)*3.2);
    else if(active==='excavator'){ const bucketWorld=new THREE.Vector3(); player.bucket.getWorldPosition(bucketWorld); pos=V(bucketWorld.x,bucketWorld.y+.35,bucketWorld.z); }
    else if(active==='crew') pos=V(p.position.x+Math.sin(a)*.95,1.2,p.position.z+Math.cos(a)*.95);
    else pos=V(p.position.x+Math.sin(a)*3,1.2,p.position.z+Math.cos(a)*3);
    cargo.g.position.copy(pos); cargo.g.rotation.y=a;
  }

  for(const o of debris){
    if(o.cleared||o.carried) continue; const d=dist2(p.position,o.g.position), collision=(active==='dozer'?3.9:active==='crew'?.95:active==='skidder'?2.5:2.15)+o.radius*.36;
    if(d<collision&&Math.abs(player.speed)>.8){
      if(active==='dozer'){
        const shove=(state.dozerPush?1.4:1)*(player.bladeHeight<1.15?1.35:1); if(o.weight<=2){ o.vel.x += Math.sin(p.rotation.y)*Math.abs(player.speed)*dt*8*shove; o.vel.z += Math.cos(p.rotation.y)*Math.abs(player.speed)*dt*8*shove; }
        else { p.position.copy(old); player.speed*=.32; }
      } else if(active==='loader'&&o.weight<=1){ o.vel.x += Math.sin(p.rotation.y)*dt*4; o.vel.z += Math.cos(p.rotation.y)*dt*4; }
      else if(active==='crew') p.position.copy(old); else if(active==='skidder' && (o.kind==='tree'||o.kind==='log')){o.vel.x+=Math.sin(p.rotation.y)*dt*2;o.vel.z+=Math.cos(p.rotation.y)*dt*2;}else {p.position.copy(old); player.speed*=.3; }
    }
  }
}
function doWork(dt){
  if(active==='crew'&&state.crewSaw){
    const o=getNearest(3.2,x=>x.kind==='tree'||x.kind==='log'); if(o){ o.work=(o.work||0)+dt*(safetyChecked?1:.7); target=o; if(o.work>1.2){ if(o.kind==='tree'){ clearDebris(o); } else if(!cargo){ o.carried=true; cargo=o; player.loaded=o; notice('LOG PICKED UP'); beep(420,.12); updateHUD(); } o.work=0; } }
  }
}
function skidderAction(name){
  const v=vehicles.skidder;
  if(name==='skidEngine'){v.engine=!v.engine;v.speed=0;notice(v.engine?'SKIDDER ENGINE STARTED':'SKIDDER ENGINE STOPPED');beep(v.engine?230:130,.18);}
  if(name==='skidBrake'){v.brake=!v.brake;v.speed=0;notice(v.brake?'PARKING BRAKE ENGAGED':'PARKING BRAKE RELEASED');}
  if(name==='skidGrapple'){v.grappleOpen=v.grappleOpen>.5?0:1;notice(v.grappleOpen?'GRAPPLE OPEN':'GRAPPLE CLOSED');}
  if(name==='skidGrab'){
    if(v.loads.length>=3){notice('GRAPPLE FULL · DELIVER LOGS');return;}
    const a=v.root.rotation.y,p=v.root.position;const local=V(p.x-Math.sin(a)*(5.4+v.boomExtend),0,p.z-Math.cos(a)*(5.4+v.boomExtend));
    const o=nearestToPoint(local,5.7,x=>x.kind==='tree'||x.kind==='log');
    if(!o){notice('BACK UP TO A FALLEN TREE · LOWER BOOM');return;}
    o.carried=true;o.vel.set(0,0,0);v.loads.push(o);v.grappleOpen=0;
    notice('LOG '+v.loads.length+'/3 SECURED');beep(390,.14);
  }
  if(name==='skidRelease'){
    if(!v.loads.length){notice('NO LOGS TO RELEASE');return;}
    const count=v.loads.length;
    for(const o of v.loads){
      if(nearDepot(v.root.position))clearDebris(o);
      else{o.carried=false;o.g.position.y=o.y0;o.vel.set(0,0,0);}
    }
    v.loads=[];v.grappleOpen=1;
    notice(nearDepot(v.root.position)?count+' LOGS DELIVERED':'LOGS RELEASED ON GROUND');beep(570,.15);
  }
  updateHUD();
}
function tapAction(name){
  if(name.startsWith('skid')){skidderAction(name);return;}
  if(name==='dropCargo'){ if(cargo && nearDepot(player.root.position)) collectCargo(); else if(cargo) dropCargoLoose(); else notice('NO LOAD CARRIED'); updateHUD(); return; }
  if(name==='toggleSafety'){ safetyChecked=!safetyChecked; notice(safetyChecked?'CHAIN BRAKE CHECKED':'CHAIN GUARD WARNING'); updateHUD(); return; }
  if(name==='loaderGrab'){
    if(cargo){ if(nearDepot(player.root.position)) collectCargo(); else dropCargoLoose(); updateHUD(); return; }
    const o=getNearest(4.2,x=>x.weight<=1 && x.kind!=='tree' && x.kind!=='car'); if(!o){ notice('LOADER NEEDS LIGHT DEBRIS'); return; } o.carried=true; cargo=o; player.loaded=o; notice('LIGHT LOAD SECURED'); beep(420,.12); updateHUD(); return;
  }
  if(name==='excGrab'){
    if(cargo){ if(nearDepot(player.root.position)) collectCargo(); else dropCargoLoose(); updateHUD(); return; }
    const p=new THREE.Vector3(); player.bucket.getWorldPosition(p); const o=nearestToPoint(p,3.2,x=>x.kind!=='tree'); if(!o){ notice('POSITION BUCKET OVER DEBRIS'); return; } o.carried=true; cargo=o; player.loaded=o; notice('HEAVY LOAD ATTACHED'); beep(390,.13); updateHUD(); return;
  }
}
function debrisPhysics(dt){ for(const o of debris){ if(o.cleared||o.carried) continue; if(o.vel.lengthSq()>.003){ o.g.position.x += o.vel.x*dt; o.g.position.z += o.vel.z*dt; o.vel.multiplyScalar(Math.pow(.78,dt*10)); o.g.position.x=clamp(o.g.position.x,-72,72); o.g.position.z=clamp(o.g.position.z,-72,72); o.g.rotation.y += dt*.18; if(nearDepot(o.g.position)) clearDebris(o); } } }
function cameraFollow(dt){ const p=player.root.position,a=player.root.rotation.y+cameraSide*Math.PI/2,dist=active==='crew'?13:active==='skidder'?22:23,height=active==='crew'?9:active==='skidder'?13:16; const camGoal=V(p.x-Math.sin(a)*dist,height,p.z-Math.cos(a)*dist); if(!camera.userData.focus) camera.userData.focus=V(); const k=1-Math.exp(-4*dt); camera.position.lerp(camGoal,k); camera.userData.focus.lerp(V(p.x,active==='crew'?2.2:3.1,p.z),k); camera.lookAt(camera.userData.focus); }
function drawMap(){ const cn=$('#minimap'),ctx=cn.getContext('2d'),W=cn.width,H=cn.height; ctx.clearRect(0,0,W,H); ctx.fillStyle='#32423a'; ctx.fillRect(0,0,W,H); ctx.fillStyle='#434a4d'; ctx.fillRect(W*.435,0,W*.13,H); ctx.fillRect(0,H*.44,W,H*.13); const trans=(x,z)=>[(x+75)/150*W,(z+75)/150*H]; ctx.fillStyle='#7b8e72'; for(let h of houses){ let [x,y]=trans(h.x,h.z); ctx.fillRect(x-3,y-3,6,6);} let [dx,dy]=trans(depot.x,depot.z); ctx.fillStyle='#f3bd5f'; ctx.fillRect(dx-9,dy-9,18,18); ctx.fillStyle='#e58f70'; for(let o of debris){ if(o.cleared) continue; let [x,y]=trans(o.g.position.x,o.g.position.z); ctx.fillRect(x-2,y-2,4,4);} let [x,y]=trans(player.root.position.x,player.root.position.z); ctx.fillStyle='#a4f3e0'; ctx.beginPath(); ctx.arc(x,y,5,0,TAU); ctx.fill(); ctx.strokeStyle='#ffffe5'; ctx.lineWidth=2; ctx.beginPath(); ctx.moveTo(x,y); ctx.lineTo(x+Math.sin(player.root.rotation.y)*9,y+Math.cos(player.root.rotation.y)*9); ctx.stroke(); }
function animate(){ requestAnimationFrame(animate); const dt=Math.min(clock.getDelta(),.05); if(started){ gameTime+=dt; driveAndAnimate(dt); doWork(dt); debrisPhysics(dt); for(let i=particles.length-1;i>=0;i--){ const o=particles[i]; o.life-=dt; o.v.y-=dt*8; o.o.position.addScaledVector(o.v,dt); o.o.scale.multiplyScalar(.98); if(o.life<=0){ scene.remove(o.o); particles.splice(i,1); } } if(performance.now()>noticeUntil) $('#midNotice').style.opacity=0; cameraFollow(dt); if(((gameTime*20)|0)%5===0){ drawMap(); const n=getNearest(active==='excavator'?7:active==='crew'?3.4:active==='skidder'?8:5.3); let hint='Find red dots on the map and reopen the streets.'; if(cargo) hint='Load secured. Deliver it to the yellow depot.'; else if(active==='dozer') hint='Lower blade and push loose debris from the roads.'; else if(active==='loader') hint=n?'Use loader arms and bucket to pick up lighter debris.':'Search for loose rubble, logs, and beams.'; else if(active==='excavator') hint='Swing the turret and position the bucket over heavy debris.'; else if(active==='crew') hint=n?'Hold chainsaw on downed trees or logs.':'Walk to downed trees and cut them clear.'; else if(active==='skidder')hint=player.loads.length?('HAUL '+player.loads.length+' LOGS TO YELLOW DEPOT · RELEASE TO COLLECT'):'REVERSE TO LOGS · GRAB · HAUL TO DEPOT'; $('#hint').textContent=hint; } }
  renderer.render(scene,camera);
}
function handleResize(){ if(!camera||!renderer) return; camera.aspect=innerWidth/innerHeight; camera.updateProjectionMatrix(); renderer.setSize(innerWidth,innerHeight); }
function setupControls(){
  const joy=$('#joystick'),stick=$('#stick'); let pid=null;
  function moveStick(e){ const rect=joy.getBoundingClientRect(); let x=e.clientX-(rect.left+rect.width/2),y=e.clientY-(rect.top+rect.height/2),max=rect.width*.31,len=Math.hypot(x,y); if(len>max){x=x/len*max;y=y/len*max;} input.x=clamp(-x/max,-1,1); input.y=clamp(-y/max,-1,1); stick.style.transform='translate('+x+'px,'+y+'px)'; }
  joy.addEventListener('pointerdown',e=>{pid=e.pointerId; joy.setPointerCapture(pid); moveStick(e); e.preventDefault();}); joy.addEventListener('pointermove',e=>{if(e.pointerId===pid) moveStick(e)});
  function release(e){ if(e.pointerId!==pid) return; pid=null; input.x=input.y=0; stick.style.transform='translate(0,0)'; }
  joy.addEventListener('pointerup',release); joy.addEventListener('pointercancel',release);
  $$('[data-hold]').forEach(btn=>{ const name=btn.dataset.hold; btn.addEventListener('pointerdown',e=>{ state[name]=true; btn.classList.add('on'); btn.setPointerCapture(e.pointerId); e.preventDefault(); }); const end=()=>{ state[name]=false; btn.classList.remove('on'); }; btn.addEventListener('pointerup',end); btn.addEventListener('pointercancel',end); btn.addEventListener('pointerleave',e=>{ if(e.buttons===0) end();});});
  $$('[data-tap]').forEach(btn=>btn.addEventListener('click',()=>tapAction(btn.dataset.tap)));
  $$('.tool').forEach(t=>t.addEventListener('click',()=>setVehicle(t.dataset.tool)));
  $('#cameraBtn').onclick=()=>{ cameraSide=(cameraSide+1)%4; notice(['FOLLOW CAMERA','RIGHT SIDE CAMERA','FRONT CAMERA','LEFT SIDE CAMERA'][cameraSide]); };
  $('#invertBtn').onclick=()=>{ invertSteer=!invertSteer; $('#invertBtn').textContent=invertSteer?'↔ STEER INVERT':'↔ STEER NORMAL'; notice(invertSteer?'STEERING INVERTED':'STEERING NORMAL'); };
  $('#reset').onclick=()=>{ $('#menu').classList.remove('hidden'); started=false; $('#play').textContent='DEPLOY NEW CREW →'; };
  $('#play').onclick=()=>{ storm=Math.floor(R(2,6)); started=true; $('#menu').classList.add('hidden'); while(scene.children.length) scene.remove(scene.children[0]); makeWorld(); camera.position.copy(V(-3,14,53)); if(camera.userData.focus) camera.userData.focus.copy(V(-8,2,32)); notice('CATEGORY '+storm+' HURRICANE · RESPONSE TEAM DEPLOYED',3200); beep(550,.4); };
  const keys={}; window.addEventListener('keydown',e=>{ keys[e.code]=true; if(['ArrowUp','ArrowDown','ArrowLeft','ArrowRight','Space'].includes(e.code)) e.preventDefault(); if(e.code==='Digit1') setVehicle('dozer'); if(e.code==='Digit2') setVehicle('loader'); if(e.code==='Digit3') setVehicle('excavator'); if(e.code==='Digit4') setVehicle('crew'); if(e.code==='Digit5') setVehicle('skidder'); if(e.code==='KeyC') cameraSide=(cameraSide+1)%4; if(e.code==='KeyI'&&!e.repeat) $('#invertBtn').click(); if(e.code==='KeyE'&&!e.repeat) tapAction(active==='loader'?'loaderGrab':active==='excavator'?'excGrab':active==='skidder'?'skidGrab':'dropCargo'); }); window.addEventListener('keyup',e=>{ keys[e.code]=false; });
  setInterval(()=>{ if(pid===null){ input.x=(keys.KeyD||keys.ArrowRight?1:0)-(keys.KeyA||keys.ArrowLeft?1:0); input.y=(keys.KeyW||keys.ArrowUp?1:0)-(keys.KeyS||keys.ArrowDown?1:0); } state.dozerBladeUp=!!keys.KeyR; state.dozerBladeDown=!!keys.KeyF; state.dozerPush=!!keys.Space; state.loaderLiftUp=!!keys.KeyR; state.loaderLiftDown=!!keys.KeyF; state.loaderCurl=!!keys.KeyT; state.loaderDump=!!keys.KeyG; state.excSwingLeft=!!keys.KeyQ; state.excSwingRight=!!keys.KeyE; state.excBoomUp=!!keys.KeyR; state.excBoomDown=!!keys.KeyF; state.excStickOut=!!keys.KeyT; state.excStickIn=!!keys.KeyG; state.excBucketCurl=!!keys.KeyY; state.excBucketDump=!!keys.KeyH; state.crewSaw=!!keys.Space; state.crewSprint=!!keys.ShiftLeft||!!keys.ShiftRight; },35);
  window.addEventListener('blur',()=>{ input.x=input.y=0; Object.keys(state).forEach(k=>state[k]=false); });
}
function boot(){ try{ if(typeof THREE==='undefined') throw Error('3D engine was not included in APK.'); renderer=new THREE.WebGLRenderer({canvas:$('#view'),antialias:true,alpha:false,powerPreference:'high-performance'}); renderer.setPixelRatio(Math.min(devicePixelRatio||1,1.4)); renderer.setSize(innerWidth,innerHeight); renderer.outputEncoding=THREE.sRGBEncoding; renderer.toneMapping=THREE.ACESFilmicToneMapping; renderer.toneMappingExposure=.82; camera=new THREE.PerspectiveCamera(65,innerWidth/innerHeight,.25,420); camera.position.set(-4,20,55); clock=new THREE.Clock(); rand=seeded(912329); makeWorld(); setupControls(); window.addEventListener('resize',handleResize); animate(); }catch(e){ $('#error').style.display='block'; $('#error').textContent='3D ENGINE ERROR\n'+e.stack; console.error(e);} }
window.addEventListener('error',e=>{ const msg=String(e.message||'').slice(0,380); if(msg){ $('#error').style.display='block'; $('#error').textContent='RUNTIME ERROR\n'+msg+'\n'+e.filename+':'+e.lineno; } });
boot();
})();