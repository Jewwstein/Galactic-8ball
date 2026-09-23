from pathlib import Path
import UnityPy, json, re
src=Path("app/src/main/assets/galactic_table.unity3d")
out=Path("app/src/main/assets/extracted"); out.mkdir(parents=True,exist_ok=True)
env=UnityPy.load(str(src)); inv=[]
def clean(s): return re.sub(r'[^A-Za-z0-9_.-]+','_',str(s))
for obj in env.objects:
    try: data=obj.read()
    except Exception as e: continue
    name=getattr(data,"m_Name","") or getattr(data,"name","") or f"{obj.type.name}_{obj.path_id}"
    rec={"type":obj.type.name,"name":str(name),"path_id":obj.path_id}
    inv.append(rec)
    if obj.type.name=="Texture2D":
        try: data.image.save(out/(f"{obj.path_id}_{clean(name)}.png"))
        except: pass
    if obj.type.name=="Mesh":
        try:
            data.export(str(out/(f"{obj.path_id}_{clean(name)}.obj")))
        except Exception as e:
            try:
                mesh=data
                verts=getattr(mesh,"m_Vertices",None) or getattr(mesh,"vertices",None)
                inds=getattr(mesh,"m_Indices",None) or getattr(mesh,"indices",None)
                if verts and inds:
                    with open(out/(f"{obj.path_id}_{clean(name)}.obj"),"w") as q:
                        q.write("# extracted from original Galactic table Unity AssetBundle\n")
                        for v in verts:q.write(f"v {v[0]} {v[1]} {v[2]}\n")
                        for i in range(0,len(inds)-2,3):q.write(f"f {inds[i]+1} {inds[i+1]+1} {inds[i+2]+1}\n")
            except: pass
(out/"inventory.json").write_text(json.dumps(inv,indent=2))
print("objects",len(inv),"textures",len(list(out.glob("*.png"))),"meshes",len(list(out.glob("*.obj"))))
print("types",json.dumps({t:sum(1 for x in inv if x["type"]==t) for t in sorted(set(x["type"] for x in inv))}))
