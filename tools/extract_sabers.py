from pathlib import Path
import UnityPy,re,json,math
root=Path("app/src/main/assets/saber_bundles")
outroot=Path("app/src/main/assets/sabers");outroot.mkdir(parents=True,exist_ok=True)
def clean(x):return re.sub(r'[^A-Za-z0-9_.-]+','_',str(x))
def normalize_obj(src,dst):
 lines=src.read_text(errors="ignore").splitlines()
 verts=[];out=[]
 for ln in lines:
  if ln.startswith("v "):
   q=ln.split();verts.append([float(q[1]),float(q[2]),float(q[3])])
 if not verts:
  dst.write_text(src.read_text(errors="ignore"));return
 mins=[min(v[i] for v in verts) for i in range(3)]
 maxs=[max(v[i] for v in verts) for i in range(3)]
 spans=[maxs[i]-mins[i] for i in range(3)]
 axis=max(range(3),key=lambda i:spans[i])
 center=[(mins[i]+maxs[i])*0.5 for i in range(3)]
 length=max(spans[axis],1e-6)
 vi=0
 for ln in lines:
  if ln.startswith("v "):
   v=verts[vi];vi+=1
   # align dominant/native length axis to +X, center cross-section at 0.
   if axis==0:x=(v[0]-mins[0])/length;y=(v[1]-center[1])/length;z=(v[2]-center[2])/length
   elif axis==1:x=(v[1]-mins[1])/length;y=(v[0]-center[0])/length;z=(v[2]-center[2])/length
   else:x=(v[2]-mins[2])/length;y=(v[1]-center[1])/length;z=(v[0]-center[0])/length
   out.append(f"v {x} {y} {z}")
  else: out.append(ln)
 dst.write_text("\n".join(out)+"\n")
 print("normalized",src.name,"axis",axis,"spans",spans)
for bundle in sorted(root.glob("*.unity3d")):
 name=bundle.stem
 out=outroot/name;out.mkdir(parents=True,exist_ok=True)
 env=UnityPy.load(str(bundle));meshes=[];textures=[]
 for obj in env.objects:
  try:data=obj.read()
  except:continue
  nm=getattr(data,"m_Name","") or f"{obj.type.name}_{obj.path_id}"
  if obj.type.name=="Mesh":
   try:
    raw=out/f"raw_{obj.path_id}_{clean(nm)}.obj"
    raw.write_text(data.export())
    meshes.append(raw)
   except Exception as e:print("MESHERR",name,obj.path_id,repr(e))
  elif obj.type.name=="Texture2D":
   try:
    p=out/f"{obj.path_id}_{clean(nm)}.png";data.image.save(p);textures.append(p)
   except Exception as e:print("TEXERR",name,obj.path_id,repr(e))
 if meshes:
  chosen=max(meshes,key=lambda p:p.stat().st_size)
  normalize_obj(chosen,out/"blade.obj")
 for p in meshes:
  if p.name!="blade.obj":
   try:p.unlink()
   except:pass
 if textures:
  # Keep all textures; duplicate largest as blade.png for simple runtime lookup.
  chosen=max(textures,key=lambda p:p.stat().st_size)
  (out/"blade.png").write_bytes(chosen.read_bytes())
 print(name,"meshes",len(meshes),"textures",len(textures))
