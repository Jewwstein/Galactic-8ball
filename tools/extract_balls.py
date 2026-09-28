from pathlib import Path
import UnityPy,re,json
root=Path("app/src/main/assets/bundles")
outroot=Path("app/src/main/assets/objects");outroot.mkdir(parents=True,exist_ok=True)
def clean(x):return re.sub(r'[^A-Za-z0-9_.-]+','_',str(x))
for src in sorted(root.glob("*.unity3d")):
 out=outroot/src.stem;out.mkdir(parents=True,exist_ok=True)
 env=UnityPy.load(str(src));inv=[];physics=[]
 for obj in env.objects:
  try:data=obj.read()
  except:continue
  name=getattr(data,"m_Name","") or f"{obj.type.name}_{obj.path_id}"
  inv.append({"type":obj.type.name,"name":str(name),"path_id":obj.path_id})
  if obj.type.name=="Texture2D":
   try:data.image.save(out/f"{obj.path_id}_{clean(name)}.png")
   except Exception as e:print("TEXERR",src.name,obj.path_id,repr(e))
  elif obj.type.name=="Mesh":
   try:
    with open(out/f"{obj.path_id}_{clean(name)}.obj","wt",newline="") as q:q.write(data.export())
   except Exception as e:print("MESHERR",src.name,obj.path_id,repr(e))
  if obj.type.name in ("SphereCollider","MeshCollider","BoxCollider","CapsuleCollider","Rigidbody","PhysicMaterial","PhysicsMaterial2D","GameObject","Transform"):
   try:
    tree=obj.read_typetree()
    physics.append({"type":obj.type.name,"path_id":obj.path_id,"name":str(name),"tree":tree})
   except Exception as e:print("PHYERR",src.name,obj.type.name,obj.path_id,repr(e))
 (out/"inventory.json").write_text(json.dumps(inv,indent=2))
 (out/"physics.json").write_text(json.dumps(physics,indent=2,default=str))
 print(src.name,"meshes",len(list(out.glob("*.obj"))),"textures",len(list(out.glob("*.png"))))
