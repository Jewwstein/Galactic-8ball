from pathlib import Path
import UnityPy,json,re
src=Path("app/src/main/assets/galactic_table.unity3d");out=Path("app/src/main/assets/extracted");out.mkdir(parents=True,exist_ok=True)
env=UnityPy.load(str(src));inv=[]
def clean(x):return re.sub(r'[^A-Za-z0-9_.-]+','_',str(x))
for obj in env.objects:
 try:data=obj.read()
 except:continue
 name=getattr(data,"m_Name","") or f"{obj.type.name}_{obj.path_id}";inv.append({"type":obj.type.name,"name":str(name),"path_id":obj.path_id})
 if obj.type.name=="Texture2D":
  try:data.image.save(out/f"{obj.path_id}_{clean(name)}.png")
  except:pass
 if obj.type.name=="Mesh":
  try:
   with open(out/f"{obj.path_id}_{clean(name)}.obj","wt",newline="") as q:q.write(data.export())
  except Exception as e:print("MESHERR",obj.path_id,repr(e))
(out/"inventory.json").write_text(json.dumps(inv,indent=2))
print("objects",len(inv),"textures",len(list(out.glob("*.png"))),"meshes",len(list(out.glob("*.obj"))))
