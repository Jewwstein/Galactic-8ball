from pathlib import Path
from collections import deque
from PIL import Image

NAMES = ["obiwan","luke_blue","mace","luke_green","vader","yoda","ahsoka","anakin","nihilus","stormtrooper","kylo","maul_double","babsyblade"]
SRC=Path("model_assets/reward_hilts")
DST=Path("app/src/main/assets/new_hilts")
DST.mkdir(parents=True,exist_ok=True)

def remove_edge_black(im):
    im=im.convert("RGBA")
    w,h=im.size
    px=im.load()
    # Flood only dark pixels connected to the outer image edge. This removes the
    # studio-black background without erasing black grips/panels enclosed by the hilt.
    seen=bytearray(w*h)
    q=deque()
    def dark(x,y):
        r,g,b,a=px[x,y]
        return a>0 and max(r,g,b)<=42 and (r+g+b)<=105
    def add(x,y):
        i=y*w+x
        if not seen[i] and dark(x,y):
            seen[i]=1;q.append((x,y))
    for x in range(w):
        add(x,0);add(x,h-1)
    for y in range(h):
        add(0,y);add(w-1,y)
    while q:
        x,y=q.popleft()
        px[x,y]=(px[x,y][0],px[x,y][1],px[x,y][2],0)
        if x: add(x-1,y)
        if x+1<w: add(x+1,y)
        if y: add(x,y-1)
        if y+1<h: add(x,y+1)
    # Crop transparent margins, then place on a 1024x2048 transparent canvas.
    alpha=im.getchannel("A")
    box=alpha.getbbox()
    if box: im=im.crop(box)
    canvas=Image.new("RGBA",(1024,2048),(0,0,0,0))
    scale=min(900/im.width,1880/im.height,1.0)
    if scale<1:
        im=im.resize((max(1,round(im.width*scale)),max(1,round(im.height*scale))),Image.Resampling.LANCZOS)
    x=(1024-im.width)//2;y=(2048-im.height)//2
    canvas.alpha_composite(im,(x,y))
    return canvas

for n in NAMES:
    p=SRC/(n+".png")
    if not p.exists(): raise FileNotFoundError(p)
    with Image.open(p) as raw:
        out=remove_edge_black(raw)
    target=DST/(n+".png")
    out.save(target,format="PNG",optimize=True)
    print("Canonical hilt",n,"source",p.stat().st_size,"=>",out.size,out.mode,target.stat().st_size)
