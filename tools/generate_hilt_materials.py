from pathlib import Path
from PIL import Image, ImageEnhance, ImageFilter
import numpy as np

ROOT=Path("app/src/main/assets")
UI=ROOT/"ui"
OUT=ROOT/"hilt_materials"
OUT.mkdir(parents=True,exist_ok=True)

FILES=[
    "hilt_default.png",
    "hilt_2.png",
    "hilt_crystal.png",
    "hilt_double.png",
    "hilt_classic.png",
    "hilt_weathered.png",
]

W,H=1024,256

for idx,name in enumerate(FILES):
    im=Image.open(UI/name).convert("RGBA")
    bbox=im.getbbox()
    if bbox:
        im=im.crop(bbox)

    # Original TTS hilt artwork is vertical. Rotate it so U follows hilt length.
    im=im.transpose(Image.Transpose.ROTATE_270)
    im=im.resize((W,H),Image.Resampling.LANCZOS)

    arr=np.asarray(im).astype(np.float32)
    rgb=arr[...,:3]
    alpha=arr[...,3]/255.0

    # Build a complete cylindrical material from the actual hilt artwork.
    # Each longitudinal column keeps the source's real silver/black/gold/blue
    # banding, while transparent silhouette areas are filled from opaque pixels.
    mask=alpha>0.08
    col=np.zeros((W,3),dtype=np.float32)
    last=np.array([90,95,105],dtype=np.float32)
    for x in range(W):
        m=mask[:,x]
        if np.any(m):
            vals=rgb[m,x,:]
            # Median keeps engraved/metal details without being dominated by glow.
            last=np.median(vals,axis=0)
        col[x]=last

    base=np.repeat(col[None,:,:],H,axis=0)

    # Mix in the detailed source render wherever it exists.
    a=np.clip(alpha[...,None]*0.86,0,0.86)
    tex=base*(1-a)+rgb*a

    # Bake subtle cylindrical/specular lighting so the procedural 3D cylinder
    # reads as polished metal even with the current lightweight ES2 shader.
    yy=np.linspace(0,1,H,dtype=np.float32)[:,None,None]
    cyl=0.58+0.46*np.power(np.sin(np.pi*yy),0.72)
    highlight=1.0+0.13*np.exp(-((yy-0.42)/0.075)**2)
    tex*=cyl*highlight

    # Fine brushed-metal grain, deterministic and mild.
    xx=np.linspace(0,1,W,dtype=np.float32)[None,:,None]
    grain=1.0+0.022*np.sin(xx*np.pi*96)+0.010*np.sin(xx*np.pi*213)
    tex*=grain

    tex=np.clip(tex,0,255).astype(np.uint8)
    out=Image.fromarray(tex,"RGB")
    out=ImageEnhance.Contrast(out).enhance(1.08)
    out=ImageEnhance.Sharpness(out).enhance(1.28)
    out=out.filter(ImageFilter.UnsharpMask(radius=1.25,percent=125,threshold=2))
    path=OUT/f"hilt_{idx}.png"
    out.save(path,optimize=True)
    print("HD hilt material",idx,name,"->",path,out.size)
