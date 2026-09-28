from pathlib import Path
import base64, zipfile, io
from PIL import Image, ImageFilter, ImageEnhance

ROOT=Path(__file__).resolve().parents[1]
CHUNKS=ROOT/"tools"/"v7_real_art_chunks"
OUT=ROOT/"soulapp"/"src"/"main"/"res"/"drawable-nodpi"
OUT.mkdir(parents=True,exist_ok=True)

parts=sorted(CHUNKS.glob("*.b64"))
if not parts:
    raise SystemExit("No v7 art chunks found")
payload="".join(p.read_text().strip() for p in parts)
raw=base64.b64decode(payload)

with zipfile.ZipFile(io.BytesIO(raw)) as z:
    names=set(z.namelist())
    required={"slime_v7mini.webp","dragon_v7mini.webp","rooms_v7mini.webp"}
    missing=required-names
    if missing:
        raise SystemExit(f"Missing v7 art assets: {sorted(missing)}")
    slime=Image.open(io.BytesIO(z.read("slime_v7mini.webp"))).convert("RGBA")
    dragon=Image.open(io.BytesIO(z.read("dragon_v7mini.webp"))).convert("RGBA")
    rooms=Image.open(io.BytesIO(z.read("rooms_v7mini.webp"))).convert("RGB")

# Preserve the actual uploaded painted references as the runtime masters.
slime.save(OUT/"slime_master.webp","WEBP",quality=92,method=6)
dragon.save(OUT/"dragon_master.webp","WEBP",quality=92,method=6)

# The concept sheet contains four finished painted rooms. Crop away the title bands
# and package each painting directly rather than recreating its artwork.
W,H=rooms.size
mid_x=W//2
mid_y=H//2
panels={
    "room_painted_awakening.webp": (0,42,mid_x-3,mid_y-2),
    "room_painted_spider.webp": (mid_x+5,42,W,mid_y-2),
    "room_painted_lizard.webp": (0,mid_y+42,mid_x-3,H),
    "room_painted_dragon.webp": (mid_x+5,mid_y+42,W,H),
}
for name,box in panels.items():
    im=rooms.crop(box)
    # Mild high-quality upscale only to avoid device resampling shimmer.
    target=(1280,720)
    scale=max(target[0]/im.width,target[1]/im.height)
    up=im.resize((round(im.width*scale),round(im.height*scale)),Image.Resampling.LANCZOS)
    x=(up.width-target[0])//2
    y=(up.height-target[1])//2
    up=up.crop((x,y,x+target[0],y+target[1]))
    up=ImageEnhance.Contrast(up).enhance(1.035)
    up=up.filter(ImageFilter.UnsharpMask(radius=1.2,percent=115,threshold=3))
    up.save(OUT/name,"WEBP",quality=88,method=6)

for name in [
    "slime_master.webp","dragon_master.webp",
    "room_painted_awakening.webp","room_painted_spider.webp",
    "room_painted_lizard.webp","room_painted_dragon.webp"
]:
    p=OUT/name
    if not p.exists() or p.stat().st_size < 10000:
        raise SystemExit(f"Bad generated v7 asset: {name}")
    print(name,p.stat().st_size)
