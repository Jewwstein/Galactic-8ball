from pathlib import Path
import UnityPy, json
src=Path("app/src/main/assets/galactic_table.unity3d")
out=Path("app/src/main/assets/extracted"); out.mkdir(parents=True,exist_ok=True)
env=UnityPy.load(str(src)); inv=[]
for obj in env.objects:
    try: data=obj.read()
    except Exception: continue
    name=getattr(data,"m_Name","") or getattr(data,"name","") or f"{obj.type.name}_{obj.path_id}"
    inv.append({"type":obj.type.name,"name":name,"path_id":obj.path_id})
    if obj.type.name=="Texture2D":
        try: data.image.save(out/(str(obj.path_id)+"_"+name.replace("/","_")+".png"))
        except Exception as e: pass
(out/"inventory.json").write_text(json.dumps(inv,indent=2))
print("objects",len(inv),"textures",len(list(out.glob("*.png"))))
