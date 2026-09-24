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
    # Keep the exact decoded TTS clip and encode it as Vorbis for Android.
    # This preserves the original source far better than shipping huge PCM WAVs.
    wav=rawout/f"{outname}_source.wav"
    ogg=rawout/f"{outname}.ogg"
    wav.write_bytes(blob)
    subprocess.run([
        "ffmpeg","-hide_banner","-loglevel","error","-y",
        "-i",str(wav),"-vn","-c:a","libvorbis","-q:a","4",str(ogg)
    ],check=True)
    wav.unlink()
    manifest.append({
        "trigger_index":idx,
        "effect_name":effect.get("Name",""),
        "clip_name":clipname,
        "source_sample":sample_name,
        "output":ogg.name,
        "bytes":ogg.stat().st_size
    })
    print("SFX",idx,effect.get("Name",""),"->",clipname,"->",ogg.name,ogg.stat().st_size)
