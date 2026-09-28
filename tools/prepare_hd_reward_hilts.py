from pathlib import Path
from PIL import Image, ImageEnhance, ImageFilter

ROOT=Path("app/src/main/assets/reward_hilts")
OUT=ROOT/"hd"
OUT.mkdir(parents=True,exist_ok=True)
NAMES=["yoda.png","ahsoka.webp","anakin.png","nihilus.webp","stormtrooper.png","kylo.png"]

def hd_portrait(path,out):
    im=Image.open(path).convert("RGBA")
    # Trim transparent padding before enlargement so the hilt uses the full card.
    a=im.getchannel("A")
    box=a.getbbox()
    if box: im=im.crop(box)
    # Existing reward art is authored horizontally. Upscale before rotation.
    target_long=1800
    scale=target_long/max(1,im.width)
    nw=max(1,int(im.width*scale)); nh=max(1,int(im.height*scale))
    im=im.resize((nw,nh),Image.Resampling.LANCZOS)
    # Conservative detail recovery: no generated/invented geometry.
    im=im.filter(ImageFilter.UnsharpMask(radius=1.6,percent=165,threshold=2))
    im=ImageEnhance.Contrast(im).enhance(1.06)
    im=ImageEnhance.Sharpness(im).enhance(1.16)
    im=im.rotate(90,expand=True,resample=Image.Resampling.BICUBIC)
    canvas=Image.new("RGBA",(1024,2048),(0,0,0,0))
    fit=min(900/max(1,im.width),1900/max(1,im.height),1.0)
    if fit<1:
        im=im.resize((int(im.width*fit),int(im.height*fit)),Image.Resampling.LANCZOS)
    canvas.alpha_composite(im,((1024-im.width)//2,(2048-im.height)//2))
    canvas.save(out,"PNG",optimize=True)
    print("HD reward",path.name,"->",out,canvas.size)

for n in NAMES:
    p=ROOT/n
    if p.exists(): hd_portrait(p,OUT/(Path(n).stem+".png"))

# The uploaded Maul asset is already the high-resolution source; normalize only
# its canvas/orientation without applying legacy-image enhancement.
p=ROOT/"maul_double.png"
if p.exists():
    im=Image.open(p).convert("RGBA")
    box=im.getchannel("A").getbbox()
    if box: im=im.crop(box)
    if im.width>im.height: im=im.rotate(90,expand=True,resample=Image.Resampling.BICUBIC)
    fit=min(900/max(1,im.width),1900/max(1,im.height))
    if fit<1: im=im.resize((int(im.width*fit),int(im.height*fit)),Image.Resampling.LANCZOS)
    canvas=Image.new("RGBA",(1024,2048),(0,0,0,0))
    canvas.alpha_composite(im,((1024-im.width)//2,(2048-im.height)//2))
    canvas.save(OUT/"maul_double.png","PNG",optimize=True)
    print("HD Maul ->",OUT/"maul_double.png")
