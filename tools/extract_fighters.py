from pathlib import Path
import UnityPy,re
root=Path("app/src/main/assets/fighter_bundles")
outroot=Path("app/src/main/assets/fighters");outroot.mkdir(parents=True,exist_ok=True)
def clean(x): return re.sub(r'[^A-Za-z0-9_.-]+','_',str(x))
def normalize_obj(src,dst):
 lines=src.read_text(errors="ignore").splitlines(); verts=[]
 for ln in lines:
  if ln.startswith("v "):
   q=ln.split(); verts.append([float(q[1]),float(q[2]),float(q[3])])
 if not verts: raise RuntimeError("no vertices "+str(src))
 mins=[min(v[i] for v in verts) for i in range(3)]; maxs=[max(v[i] for v in verts) for i in range(3)]
 spans=[maxs[i]-mins[i] for i in range(3)]; center=[(mins[i]+maxs[i])*.5 for i in range(3)]
 scale=max(max(spans),1e-6); vi=0; out=[]
 for ln in lines:
  if ln.startswith("v "):
   v=verts[vi];vi+=1
   # Keep the authored model orientation; center and normalize only.
   out.append("v %.7f %.7f %.7f"%((v[0]-center[0])/scale,(v[1]-center[1])/scale,(v[2]-center[2])/scale))
  else: out.append(ln)
 dst.write_text("\n".join(out)+"\n")
 print(dst, "spans", spans)
for bundle in sorted(root.glob("*.unity3d")):
 name=bundle.stem; out=outroot/name; out.mkdir(parents=True,exist_ok=True)
 env=UnityPy.load(str(bundle)); meshes=[]; textures=[]
 for obj in env.objects:
  try: data=obj.read()
  except Exception as e: continue
  nm=getattr(data,"m_Name","") or f"{obj.type.name}_{obj.path_id}"
  if obj.type.name=="Mesh":
   try:
    p=out/f"raw_{obj.path_id}_{clean(nm)}.obj"; p.write_text(data.export()); meshes.append(p)
   except Exception as e: print("MESHERR",name,obj.path_id,repr(e))
  elif obj.type.name=="Texture2D":
   try:
    p=out/f"{obj.path_id}_{clean(nm)}.png"; data.image.save(p); textures.append(p)
   except Exception as e: print("TEXERR",name,obj.path_id,repr(e))
 if not meshes: raise RuntimeError(f"{name}: no Mesh in AssetBundle")
 chosen=max(meshes,key=lambda p:p.stat().st_size); normalize_obj(chosen,out/"model.obj")
 if textures:
  chosen_tex=max(textures,key=lambda p:p.stat().st_size); (out/"diffuse.png").write_bytes(chosen_tex.read_bytes())
 print(name,"mesh",chosen.name,chosen.stat().st_size,"textures",len(textures))
