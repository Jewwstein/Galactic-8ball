from pathlib import Path
import UnityPy,json,io,wave
import numpy as np

src=Path("app/src/main/assets/sfx_bundle/saber_sfx.unity3d")
rawout=Path("app/src/main/res/raw")
rawout.mkdir(parents=True,exist_ok=True)

env=UnityPy.load(str(src))
audio={}
effects=None
inventory=[]

for obj in env.objects:
    try:data=obj.read()
    except Exception as e:
        print("READERR",obj.type.name,obj.path_id,repr(e));continue
    name=getattr(data,"m_Name","") or f"{obj.type.name}_{obj.path_id}"
    inventory.append({"type":obj.type.name,"name":str(name),"path_id":obj.path_id})
    if obj.type.name=="AudioClip":
        audio[obj.path_id]=(obj,data,str(name))
    elif obj.type.name=="MonoBehaviour":
        try:
            tree=obj.read_typetree()
            if "TriggerEffects" in tree:
                effects=tree
        except Exception as e:
            print("TREEERR",obj.path_id,repr(e))

if effects is None:
    raise RuntimeError("TTSAssetBundleEffects TriggerEffects not found")

# Exact gameplay mapping used by Galactic 8-Ball v428:
# 0 ignite Sith, 1 ignite Jedi, 2 clash, 3 deactivate,
# 4 pocket, 5 scratch, 6 Sith victory, 7 Jedi victory, 9 charging hum.
mapping={
    0:"sfx_ignite_sith",
    1:"sfx_ignite_jedi",
    2:"sfx_clash",
    3:"sfx_deactivate",
    4:"sfx_pocket",
    5:"sfx_scratch",
    6:"sfx_victory_sith",
    7:"sfx_victory_jedi",
    9:"sfx_hum",
}

manifest=[]
for idx,outname in mapping.items():
    effect=effects["TriggerEffects"][idx]
    pid=effect["Sound"]["Audio"]["m_PathID"]
    if pid not in audio:
        raise RuntimeError(f"AudioClip path {pid} for trigger {idx} not found")
    obj,data,clipname=audio[pid]
    samples=data.samples
    if not samples:
        raise RuntimeError(f"No decoded sample for {clipname}")
    sample_name,blob=next(iter(samples.items()))
    # The source bundle is PCM16 HQ. Preserve the exact clip content while
    # resampling to 32 kHz for a compact mobile build (no external encoder needed).
    with wave.open(io.BytesIO(blob),"rb") as w:
        nch=w.getnchannels(); sw=w.getsampwidth(); rate=w.getframerate(); frames=w.readframes(w.getnframes())
    if sw!=2:
        raise RuntimeError(f"Expected PCM16 for {clipname}, got sample width {sw}")
    arr=np.frombuffer(frames,dtype="<i2")
    arr=arr.reshape((-1,nch)).astype(np.float32)
    target_rate=32000
    if rate!=target_rate and len(arr)>1:
        oldx=np.arange(len(arr),dtype=np.float32)
        newlen=max(1,int(round(len(arr)*target_rate/rate)))
        newx=np.linspace(0,len(arr)-1,newlen,dtype=np.float32)
        chans=[np.interp(newx,oldx,arr[:,c]) for c in range(nch)]
        arr=np.stack(chans,axis=1)
    arr=np.clip(arr,-32768,32767).astype("<i2")
    wav=rawout/f"{outname}.wav"
    with wave.open(str(wav),"wb") as o:
        o.setnchannels(nch);o.setsampwidth(2);o.setframerate(target_rate);o.writeframes(arr.tobytes())
    manifest.append({
        "trigger_index":idx,
        "effect_name":effect.get("Name",""),
        "clip_name":clipname,
        "source_sample":sample_name,
        "output":wav.name,
        "bytes":wav.stat().st_size
    })
    print("SFX",idx,effect.get("Name",""),"->",clipname,"->",wav.name,wav.stat().st_size)

(rawout/"sfx_manifest.json").write_text(json.dumps(manifest,indent=2))
print("Extracted",len(manifest),"exact v428 gameplay sounds")
