from pathlib import Path
from PIL import Image, ImageEnhance, ImageFilter
import numpy as np

ROOT=Path("app/src/main/assets/extracted")

def find(suffix):
    hits=list(ROOT.glob("*"+suffix))
    if not hits: raise FileNotFoundError(suffix)
    return hits[0]

def highpass_detail(img, amount=0.12):
    arr=np.asarray(img.convert("RGB")).astype(np.float32)
    blur=np.asarray(img.convert("RGB").filter(ImageFilter.GaussianBlur(radius=1.6))).astype(np.float32)
    out=np.clip(arr+(arr-blur)*amount,0,255).astype(np.uint8)
    return Image.fromarray(out,"RGB")

def bump_luma(path,size):
    b=Image.open(path).convert("RGB").resize(size,Image.Resampling.LANCZOS)
    a=np.asarray(b).astype(np.float32)/255.0
    # Unity normal maps contain useful high-frequency surface information even
    # when we are not running a full tangent-space normal shader.
    l=(a[...,0]*.30+a[...,1]*.59+a[...,2]*.11)
    l=(l-l.mean())
    return l

felt=find("_Felt.png")
felt_bump=find("_FeltBump.png")
wood=find("_Wood.png")
wood_bump=find("_WoodBump.png")
foot=find("_Foot.png")

# Felt is already 4096x4096: preserve every source texel, add only subtle cloth depth.
im=Image.open(felt).convert("RGB")
arr=np.asarray(im).astype(np.float32)/255.0
detail=bump_luma(felt_bump,im.size)
arr=np.clip(arr + detail[...,None]*0.035,0,1)
out=Image.fromarray((arr*255).astype(np.uint8),"RGB")
out=highpass_detail(out,.10)
out=ImageEnhance.Contrast(out).enhance(1.035)
out.save(felt,"PNG",optimize=True)
print("table felt enhanced",felt,out.size)

# Wood was only 1024. Upscale to 2048, recover the original bump detail and grain.
im=Image.open(wood).convert("RGB").resize((2048,2048),Image.Resampling.LANCZOS)
arr=np.asarray(im).astype(np.float32)/255.0
detail=bump_luma(wood_bump,im.size)
arr=np.clip(arr + detail[...,None]*0.090,0,1)
out=Image.fromarray((arr*255).astype(np.uint8),"RGB")
out=highpass_detail(out,.24)
out=ImageEnhance.Contrast(out).enhance(1.08)
out=ImageEnhance.Sharpness(out).enhance(1.18)
out.save(wood,"PNG",optimize=True)
print("table wood enhanced",wood,out.size)

# Foot texture is tiny in the source bundle; make its filtering less visibly soft.
im=Image.open(foot).convert("RGBA").resize((1024,1024),Image.Resampling.LANCZOS)
rgb=ImageEnhance.Sharpness(im.convert("RGB")).enhance(1.35)
rgb=ImageEnhance.Contrast(rgb).enhance(1.06)
rgb.putalpha(im.getchannel("A"))
rgb.save(foot,"PNG",optimize=True)
print("table foot enhanced",foot,rgb.size)
