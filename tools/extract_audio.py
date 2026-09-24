from pathlib import Path
import UnityPy,re,json

src=Path("app/src/main/assets/sfx_bundle/saber_sfx.unity3d")
out=Path("app/src/main/assets/audio")
out.mkdir(parents=True,exist_ok=True)
env=UnityPy.load(str(src))
inv=[]

def clean(x):
    x=re.sub(r'[^A-Za-z0-9_.-]+','_',str(x)).strip('._')
    return x or "clip"

for obj in env.objects:
    try:data=obj.read()
    except Exception as e:
        print("READERR",obj.type.name,obj.path_id,repr(e));continue
    name=getattr(data,"m_Name","") or f"{obj.type.name}_{obj.path_id}"
    rec={"type":obj.type.name,"name":str(name),"path_id":obj.path_id}
    inv.append(rec)
    if obj.type.name=="AudioClip":
        try:
            samples=data.samples
            print("AUDIO",name,"samples",list(samples.keys()))
            for sample_name,blob in samples.items():
                fn=clean(sample_name)
                if "." not in Path(fn).name:
                    fn += ".wav"
                (out/fn).write_bytes(blob)
                print(" wrote",fn,len(blob))
        except Exception as e:
            print("AUDIOERR",name,obj.path_id,repr(e))
            # Keep metadata for diagnosis even if UnityPy cannot decode a clip.
            try:
                tree=obj.read_typetree()
                (out/f"audio_{obj.path_id}_tree.json").write_text(json.dumps(tree,indent=2,default=str))
            except Exception: pass

(out/"inventory.json").write_text(json.dumps(inv,indent=2))
print("audio files", [p.name for p in out.iterdir() if p.is_file()])
