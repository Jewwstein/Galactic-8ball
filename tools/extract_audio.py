from pathlib import Path
import UnityPy,re,json

src=Path("app/src/main/assets/sfx_bundle/saber_sfx.unity3d")
out=Path("app/src/main/assets/audio")
out.mkdir(parents=True,exist_ok=True)
env=UnityPy.load(str(src))
inv=[]; trees=[]

def safe(x):
    if isinstance(x,(str,int,float,bool)) or x is None:return x
    if isinstance(x,bytes):return {"bytes":len(x)}
    if isinstance(x,list):return [safe(v) for v in x]
    if isinstance(x,dict):return {str(k):safe(v) for k,v in x.items()}
    return str(x)

for obj in env.objects:
    try:data=obj.read()
    except Exception as e:
        print("READERR",obj.type.name,obj.path_id,repr(e));continue
    name=getattr(data,"m_Name","") or f"{obj.type.name}_{obj.path_id}"
    inv.append({"type":obj.type.name,"name":str(name),"path_id":obj.path_id})
    if obj.type.name in ("MonoBehaviour","GameObject"):
        try:
            tree=safe(obj.read_typetree())
            trees.append({"type":obj.type.name,"name":str(name),"path_id":obj.path_id,"tree":tree})
            if obj.type.name=="MonoBehaviour":
                print("EFFECT_TREE_BEGIN")
                print(json.dumps(tree,indent=2,default=str))
                print("EFFECT_TREE_END")
        except Exception as e:
            print("TREEERR",obj.type.name,obj.path_id,repr(e))

(out/"inventory.json").write_text(json.dumps(inv,indent=2))
(out/"effects.json").write_text(json.dumps(trees,indent=2))
print("SFX diagnostic objects",len(inv),"trees",len(trees))
