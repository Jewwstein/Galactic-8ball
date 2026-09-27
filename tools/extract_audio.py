from pathlib import Path
import UnityPy,json,subprocess

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
# 4 pocket, 5 scratch, 6 Sith victory, 7 Jedi victory, 8 UI transition, 9 charging hum.
mapping={
    0:"sfx_ignite_sith",
    1:"sfx_ignite_jedi",
    2:"sfx_clash",
    3:"sfx_deactivate",
    4:"sfx_pocket",
    5:"sfx_scratch",
    6:"sfx_victory_sith",
    7:"sfx_victory_jedi",
    8:"sfx_ui_trigger8",
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

(rawout/"sfx_manifest.json").write_text(json.dumps(manifest,indent=2))
print("Extracted",len(manifest),"exact v428 gameplay sounds")


# Dedicated Death Star Assault blaster. This is generated deterministically at
# build time so the arcade mode has its own short laser report without reusing
# a lightsaber effect or adding a large binary source file.
laser=rawout/"sfx_arcade_laser.wav"
subprocess.run([
    "ffmpeg","-hide_banner","-loglevel","error","-y",
    "-f","lavfi","-i",
    "sine=frequency=980:duration=0.12:sample_rate=44100",
    "-af","asetrate=44100*0.72,aresample=44100,volume=0.62,afade=t=out:st=0.08:d=0.08",
    "-c:a","pcm_s16le",str(laser)
],check=True)
laserogg=rawout/"sfx_arcade_laser.ogg"
subprocess.run([
    "ffmpeg","-hide_banner","-loglevel","error","-y",
    "-i",str(laser),"-filter:a","highpass=f=180,lowpass=f=6500",
    "-c:a","libvorbis","-q:a","5",str(laserogg)
],check=True)
laser.unlink()
print("SFX arcade laser ->",laserogg.name,laserogg.stat().st_size)


# Original placeholder arcade polish SFX. These are synthesized from basic
# waveforms/noise so the test build has no dependency on film/game recordings.
import math, random, wave, struct
SR=44100
def write_fx(name,dur,fn):
    n=max(1,int(SR*dur)); p=rawout/(name+".wav")
    random.seed(0x8BA11 + sum(map(ord,name)))
    with wave.open(str(p),"wb") as w:
        w.setnchannels(1);w.setsampwidth(2);w.setframerate(SR)
        frames=[]
        for i in range(n):
            t=i/SR; x=max(-1.0,min(1.0,fn(t,dur,i,n)))
            frames.append(struct.pack("<h",int(x*32767)))
        w.writeframes(b"".join(frames))
    ogg=rawout/(name+".ogg")
    subprocess.run(["ffmpeg","-hide_banner","-loglevel","error","-y","-i",str(p),"-c:a","libvorbis","-q:a","5",str(ogg)],check=True)
    p.unlink(); print("SFX original",name,"->",ogg.stat().st_size)

def env(t,d,a=.015,r=.12):
    return min(1,t/max(a,1e-4))*min(1,max(0,d-t)/max(r,1e-4))

write_fx("sfx_tie_blaster",.22,lambda t,d,i,n: env(t,d,.004,.09)*(.62*math.sin(2*math.pi*(1450-900*t/d)*t)+.18*math.sin(2*math.pi*2900*t)))
write_fx("sfx_deathstar_fire",.48,lambda t,d,i,n: env(t,d,.01,.18)*(.52*math.sin(2*math.pi*(210-80*t/d)*t)+.28*math.sin(2*math.pi*(760-360*t/d)*t)+.10*(random.random()*2-1)))
write_fx("sfx_arcade_explosion",1.05,lambda t,d,i,n: env(t,d,.002,.55)*((.72*(random.random()*2-1))/(1+5*t)+.30*math.sin(2*math.pi*(72-28*t/d)*t)))
write_fx("sfx_arcade_transition",.72,lambda t,d,i,n: env(t,d,.01,.20)*(.34*math.sin(2*math.pi*(180+720*t/d)*t)+.22*math.sin(2*math.pi*(360+980*t/d)*t)))
write_fx("sfx_deathstar_charge",.55,lambda t,d,i,n: env(t,d,.02,.05)*(.30*math.sin(2*math.pi*(120+680*t/d)*t)+.22*math.sin(2*math.pi*(240+1100*t/d)*t)))
# Loop-friendly low engine bed: phase-locked harmonics over exactly two seconds.
write_fx("sfx_tie_engine",2.0,lambda t,d,i,n: .23*math.sin(2*math.pi*55*t)+.11*math.sin(2*math.pi*110*t)+.055*math.sin(2*math.pi*220*t)+.025*(random.random()*2-1))
