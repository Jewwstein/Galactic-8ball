from pathlib import Path
import UnityPy,json,re,sys
src=Path("app/src/main/assets/galactic_table.unity3d")
out=Path("app/src/main/assets/extracted");out.mkdir(parents=True,exist_ok=True)
env=UnityPy.load(str(src));inv=[];trees=[]
def clean(x):return re.sub(r'[^A-Za-z0-9_.-]+','_',str(x))
def safe(x):
 if isinstance(x,(str,int,float,bool)) or x is None:return x
 if isinstance(x,bytes):return {"bytes":len(x)}
 if isinstance(x,list):return [safe(v) for v in x]
 if isinstance(x,dict):return {str(k):safe(v) for k,v in x.items()}
 return str(x)
for obj in env.objects:
 try:data=obj.read()
 except:continue
 name=getattr(data,"m_Name","") or f"{obj.type.name}_{obj.path_id}"
 rec={"type":obj.type.name,"name":str(name),"path_id":obj.path_id};inv.append(rec)
 if obj.type.name=="Texture2D":
  try:data.image.save(out/f"{obj.path_id}_{clean(name)}.png")
  except Exception as e:print("TEXERR",obj.path_id,repr(e))
 if obj.type.name=="Mesh":
  try:
   with open(out/f"{obj.path_id}_{clean(name)}.obj","wt",newline="") as q:q.write(data.export())
  except Exception as e:print("MESHERR",obj.path_id,repr(e))
 if obj.type.name in ("GameObject","Transform","MeshFilter","MeshRenderer","Material","MeshCollider","SphereCollider","BoxCollider","CapsuleCollider","Rigidbody","PhysicMaterial","PhysicsMaterial2D"):
  try:trees.append({"type":obj.type.name,"path_id":obj.path_id,"tree":safe(obj.read_typetree())})
  except Exception as e:print("TREEERR",obj.type.name,obj.path_id,repr(e))
(out/"inventory.json").write_text(json.dumps(inv,indent=2))
(out/"scene_raw.json").write_text(json.dumps(trees,indent=2))
print("objects",len(inv),"textures",len(list(out.glob("*.png"))),"meshes",len(list(out.glob("*.obj"))),"trees",len(trees))
