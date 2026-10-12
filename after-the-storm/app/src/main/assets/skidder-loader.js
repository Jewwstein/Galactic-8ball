/* Self-contained offline glTF2/GLB renderer for the 545D mesh set. Three.js r149. */
window.loadSkidderGeometry=async function(){
  let data;
  if(window.AndroidAssets && typeof AndroidAssets.readSkidder==='function'){
    const base64=AndroidAssets.readSkidder();
    if(!base64||base64.length<5000)throw new Error('Missing bundled skidder GLB');
    const raw=atob(base64),arr=new Uint8Array(raw.length);
    for(let i=0;i<raw.length;i++)arr[i]=raw.charCodeAt(i);
    data=arr.buffer;
  }else{
    const response=await fetch('skidder.glb');
    if(!response.ok) throw new Error('Cannot load local skidder model ('+response.status+')');
    data=await response.arrayBuffer();
  }
  const dv=new DataView(data);
  if(dv.getUint32(0,true)!==0x46546c67) throw new Error('Invalid skidder GLB header');
  const len=dv.getUint32(12,true);
  const json=JSON.parse(new TextDecoder().decode(new Uint8Array(data,20,len)));
  let offset=20+len,binOffset=0;
  while(offset+8<data.byteLength){const size=dv.getUint32(offset,true),type=dv.getUint32(offset+4,true);
    if(type===0x004e4942){binOffset=offset+8;break;}offset+=8+size;}
  if(!binOffset)throw new Error('Skidder binary payload missing');
  const glb=json;
  function rawView(i){const v=glb.bufferViews[i];return new Uint8Array(data,binOffset+(v.byteOffset||0),v.byteLength);}
  function attribute(i){
    const a=glb.accessors[i],v=glb.bufferViews[a.bufferView];
    const n={SCALAR:1,VEC2:2,VEC3:3,VEC4:4,MAT4:16}[a.type];
    const bpc={5120:1,5121:1,5122:2,5123:2,5125:4,5126:4}[a.componentType];
    const Type={5120:Int8Array,5121:Uint8Array,5122:Int16Array,5123:Uint16Array,5125:Uint32Array,5126:Float32Array}[a.componentType];
    const stride=v.byteStride||bpc*n,base=binOffset+(v.byteOffset||0)+(a.byteOffset||0);
    const output=new Type(a.count*n);
    for(let k=0;k<a.count;k++)for(let c=0;c<n;c++){
      const pos=base+k*stride+c*bpc;
      let val;
      switch(a.componentType){case 5126:val=dv.getFloat32(pos,true);break;case 5125:val=dv.getUint32(pos,true);break;case 5123:val=dv.getUint16(pos,true);break;case 5122:val=dv.getInt16(pos,true);break;case 5121:val=dv.getUint8(pos);break;case 5120:val=dv.getInt8(pos);break;}
      output[k*n+c]=val;
    }
    return new THREE.BufferAttribute(output,n,!!a.normalized);
  }
  const images=(glb.images||[]).map(im=>{
    if(im.bufferView==null)return null;
    const bytes=rawView(im.bufferView);
    // Embed as base64/data URI to keep textures self-contained and work in offline Android WebView.
    let binary='';const batch=0x4000;
    for(let i=0;i<bytes.length;i+=batch)binary+=String.fromCharCode.apply(null,bytes.subarray(i,i+batch));
    return 'data:'+(im.mimeType||'image/png')+';base64,'+btoa(binary);
  });
  const tex=(glb.textures||[]).map(t=>{
    const url=images[t.source];if(!url)return null;
    const texture=new THREE.TextureLoader().load(url);
    texture.flipY=false;
    texture.encoding=THREE.sRGBEncoding;
    const sampler=glb.samplers?.[t.sampler||0];
    texture.wrapS=sampler?.wrapS===33071?THREE.ClampToEdgeWrapping:THREE.RepeatWrapping;
    texture.wrapT=sampler?.wrapT===33071?THREE.ClampToEdgeWrapping:THREE.RepeatWrapping;
    return texture;
  });
  const materials=(glb.materials||[]).map(m=>{
    const p=m.pbrMetallicRoughness||{},c=p.baseColorFactor||[1,1,1,1];
    const mat=new THREE.MeshStandardMaterial({
      color:new THREE.Color(c[0],c[1],c[2]),metalness:p.metallicFactor??0.08,roughness:p.roughnessFactor??0.85,
      transparent:m.alphaMode==='BLEND'||c[3]<0.999,opacity:c[3],side:m.doubleSided?THREE.DoubleSide:THREE.FrontSide
    });
    if(p.baseColorTexture)mat.map=tex[p.baseColorTexture.index];
    if(m.alphaMode==='MASK'){mat.alphaTest=m.alphaCutoff??.5;mat.transparent=false;}
    return mat;
  });
  const root=new THREE.Group();root.name='CAT_545D_Imported';
  const objects=glb.nodes.map(n=>{
    const g=new THREE.Group();g.name=n.name||'mesh';
    if(n.translation)g.position.fromArray(n.translation);
    if(n.rotation)g.quaternion.fromArray(n.rotation);
    if(n.scale)g.scale.fromArray(n.scale);
    if(n.mesh!==undefined){
      for(let i=0;i<glb.meshes[n.mesh].primitives.length;i++){
        const p=glb.meshes[n.mesh].primitives[i],geometry=new THREE.BufferGeometry();
        const names={POSITION:'position',NORMAL:'normal',TEXCOORD_0:'uv',COLOR_0:'color'};
        for(const [k,j] of Object.entries(p.attributes))if(names[k])geometry.setAttribute(names[k],attribute(j));
        if(p.indices!==undefined)geometry.setIndex(attribute(p.indices));
        if(!geometry.getAttribute('normal'))geometry.computeVertexNormals();
        const m=new THREE.Mesh(geometry,materials[p.material||0]||new THREE.MeshStandardMaterial({color:0xa2aa9b}));
        m.name=g.name+'-part'+i;m.frustumCulled=true;g.add(m);
      }
    }
    return g;
  });
  glb.nodes.forEach((n,i)=>{if(n.children)for(const j of n.children)objects[i].add(objects[j]);});
  const sc=glb.scenes?.[glb.scene||0];
  if(sc?.nodes)for(const i of sc.nodes)root.add(objects[i]);else root.add(objects[0]);
  return root;
};