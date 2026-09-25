from pathlib import Path
from PIL import Image

src=Path("app/src/main/assets/ui/galactic_logo.png")
dst=Path("app/src/main/res/drawable/app_icon_hd.png")
im=Image.open(src).convert("RGBA")

# The supplied logo is 3:1 (2172x724): its left square is the Death Star 8-ball
# emblem. Keep that artwork untouched, center it on a transparent square and
# downsample with Lanczos for a crisp Android launcher icon.
side=min(im.height, im.width//3)
icon=im.crop((0,0,side,side))

# Trim only fully transparent outside pixels, then restore comfortable launcher
# padding so Android launchers do not clip the Death Star glow.
alpha=icon.getchannel("A")
box=alpha.getbbox()
if box:
    art=icon.crop(box)
else:
    art=icon
canvas=Image.new("RGBA",(512,512),(0,0,0,0))
art.thumbnail((438,438),Image.Resampling.LANCZOS)
x=(512-art.width)//2
y=(512-art.height)//2
canvas.alpha_composite(art,(x,y))
dst.parent.mkdir(parents=True,exist_ok=True)
canvas.save(dst,"PNG",optimize=True)
print(f"Launcher icon: {im.size} -> left {side}x{side} -> {dst} {canvas.size}")
