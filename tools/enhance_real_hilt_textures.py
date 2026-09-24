from pathlib import Path
from PIL import Image, ImageEnhance, ImageFilter
import numpy as np

ROOT=Path("app/src/main/assets/real_hilts")

def enhance(path, target):
    im=Image.open(path).convert("RGB")
    # Preserve source artwork, but give the mobile renderer much more texel density.
    if im.size != target:
        im=im.resize(target, Image.Resampling.LANCZOS)

    arr=np.asarray(im).astype(np.float32)/255.0
    lum=(arr[...,0]*.2126+arr[...,1]*.7152+arr[...,2]*.0722)
    mx=arr.max(axis=2); mn=arr.min(axis=2)
    sat=(mx-mn)/(mx+1e-5)

    # Brushed-metal microdetail only on likely metal regions:
    # low saturation + mid/high brightness. This keeps colored buttons/accent pieces intact.
    metal=np.clip((.38-sat)/.38,0,1)*np.clip((lum-.12)/.55,0,1)
    h,w=lum.shape
    yy=np.arange(h,dtype=np.float32)[:,None]
    xx=np.arange(w,dtype=np.float32)[None,:]
    brushed=(np.sin(xx*.43)+.55*np.sin(xx*1.37)+.35*np.sin(xx*2.91))*0.010
    brushed+=np.sin(yy*.19)*0.004

    rng=np.random.default_rng(72641 + w + h)
    grain=rng.normal(0,.008,(h,w)).astype(np.float32)
    detail=(brushed+grain)*metal
    arr=np.clip(arr+detail[...,None],0,1)

    out=Image.fromarray((arr*255).astype(np.uint8),"RGB")
    # Local texture definition. This cannot invent geometry, but it removes the
    # muddy 256px look and makes the original UV detail hold up close.
    out=out.filter(ImageFilter.UnsharpMask(radius=2.2,percent=185,threshold=2))
    out=ImageEnhance.Contrast(out).enhance(1.10)
    out=ImageEnhance.Sharpness(out).enhance(1.18)

    # High-quality WebP keeps APK size under control.
    out.save(path,"WEBP",quality=96,method=6)
    print("enhanced",path,target,path.stat().st_size)

for i in range(6):
    p=ROOT/f"hilt_{i}.webp"
    if not p.exists():
        continue
    # Maul is a long 4:1 atlas; the others are square atlases.
    target=(4096,1024) if i==3 else (2048,2048)
    enhance(p,target)
