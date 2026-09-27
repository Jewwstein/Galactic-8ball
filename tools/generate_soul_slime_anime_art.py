from PIL import Image, ImageDraw, ImageFilter
import math, random
from pathlib import Path

OUT=Path("soulapp/src/main/res/drawable-nodpi")
OUT.mkdir(parents=True, exist_ok=True)
random.seed(20260927)

W,H=1920,1080

def glow_layer(size=W):
    return Image.new("RGBA",(W,H),(0,0,0,0))

def add_glow(base, x,y,r,color,alpha=160):
    g=Image.new("RGBA",base.size,(0,0,0,0))
    d=ImageDraw.Draw(g,"RGBA")
    for k in range(6,0,-1):
        rr=int(r*k/6)
        a=int(alpha*(1-k/7)*0.8)
        d.ellipse((x-rr,y-rr,x+rr,y+rr),fill=(*color,a))
    g=g.filter(ImageFilter.GaussianBlur(max(4,int(r*0.16))))
    return Image.alpha_composite(base,g)

def crystal_poly(cx,cy,r):
    return [(cx,cy-r),(cx+r*.62,cy-r*.20),(cx+r*.36,cy+r*.82),(cx-r*.36,cy+r*.82),(cx-r*.62,cy-r*.20)]

def draw_crystal(draw,cx,cy,r,base=(92,218,255,255),outline=(218,252,255,255)):
    pts=crystal_poly(cx,cy,r)
    draw.polygon(pts,fill=base,outline=outline)
    draw.polygon([(cx-r*.16,cy-r*.64),(cx+r*.04,cy-r*.42),(cx-r*.06,cy+r*.28)],fill=(255,255,255,135))
    draw.polygon([(cx+r*.04,cy-r*.42),(cx+r*.42,cy-r*.14),(cx+r*.18,cy+r*.55)],fill=(54,141,216,80))

# --- main anime painted cave background ---
bg=Image.new("RGBA",(W,H),(8,15,34,255))
px=bg.load()
for y in range(H):
    t=y/(H-1)
    for x in range(W):
        cx=(x-W*.54)/(W*.72)
        cy=(y-H*.42)/(H*.82)
        rad=max(0,1-math.sqrt(cx*cx+cy*cy))
        r=int(8+11*(1-t)+36*rad)
        g=int(15+25*(1-t)+70*rad)
        b=int(34+38*(1-t)+100*rad)
        px[x,y]=(r,g,b,255)

bg=add_glow(bg,1040,420,520,(84,159,255),105)
bg=add_glow(bg,560,570,290,(93,224,255),75)
bg=add_glow(bg,1450,520,260,(178,105,255),55)
d=ImageDraw.Draw(bg,"RGBA")

# distant cave silhouettes, faceted like anime background art
back=[(0,360)]
for i in range(15):
    x=i*W/14
    y=310+65*math.sin(i*1.21)+random.randint(-22,22)
    back.append((x,y))
back += [(W,0),(0,0)]
d.polygon(back,fill=(16,27,55,255))

# mid rock band
mid=[(0,670)]
for i in range(14):
    x=i*W/13
    y=610+45*math.sin(i*1.73+0.8)+random.randint(-18,18)
    mid.append((x,y))
mid += [(W,370),(0,400)]
d.polygon(mid,fill=(18,42,70,215))

# rock facet highlights
for i in range(24):
    x=random.randint(80,W-80)
    y=random.randint(180,760)
    rw=random.randint(70,180)
    rh=random.randint(90,250)
    col=random.choice([(62,111,166,52),(77,151,204,42),(130,107,188,28)])
    pts=[(x-rw*.4,y),(x+rw*.25,y-rh*.5),(x+rw*.45,y+rh*.15),(x-rw*.1,y+rh*.55)]
    d.polygon(pts,fill=col)

# floor with cel shading
floor=[(0,790)]
for i in range(18):
    x=i*W/17
    y=775+32*math.sin(i*1.55)+random.randint(-10,10)
    floor.append((x,y))
floor += [(W,H),(0,H)]
d.polygon(floor,fill=(10,23,39,255))
d.polygon([(0,845),(W,815),(W,H),(0,H)],fill=(7,18,32,150))

# painted crystal clusters
for cx,cy,scale in [(250,625,58),(480,545,46),(750,690,65),(1110,585,52),(1450,640,74),(1650,500,48)]:
    bg=add_glow(bg,cx,cy,int(scale*2.2),(70,210,255),55)
    d=ImageDraw.Draw(bg,"RGBA")
    for j in range(4):
        ox=(j-1.5)*scale*.62
        rr=scale*(.75+.10*j)
        col=(100+12*j,215+7*j,255,220)
        draw_crystal(d,cx+ox,cy+abs(j-1.5)*9,rr,col)

# magical motes, no baked UI
for i in range(160):
    x=random.randint(30,W-30); y=random.randint(80,H-100)
    rr=random.choice([1,2,2,3,4])
    d.ellipse((x-rr,y-rr,x+rr,y+rr),fill=(170,242,255,random.randint(35,120)))

bg.save(OUT/"cave_bg.png")

# --- mid overlay: mist, floating light, side rock shelves ---
midimg=Image.new("RGBA",(W,H),(0,0,0,0))
md=ImageDraw.Draw(midimg,"RGBA")
# translucent mist bands
for y,a in [(530,18),(620,26),(710,20)]:
    md.ellipse((-160,y-110,W+180,y+130),fill=(104,204,255,a))
midimg=midimg.filter(ImageFilter.GaussianBlur(38))
md=ImageDraw.Draw(midimg,"RGBA")
# mid rocks
md.polygon([(0,650),(220,580),(350,660),(270,760),(0,790)],fill=(9,25,43,150))
md.polygon([(W,610),(W-250,550),(W-360,660),(W-250,760),(W,810)],fill=(9,25,43,165))
# additional crystal glows
for cx,cy in [(360,690),(1290,665)]:
    midimg=add_glow(midimg,cx,cy,110,(80,225,255),65)
    md=ImageDraw.Draw(midimg,"RGBA")
    draw_crystal(md,cx,cy,48,(104,227,255,205))
midimg.save(OUT/"cave_mid.png")

# --- foreground overlay: strong anime framing ---
fg=Image.new("RGBA",(W,H),(0,0,0,0))
fd=ImageDraw.Draw(fg,"RGBA")
left=[(0,0),(245,0),(180,175),(210,315),(140,470),(170,670),(90,850),(0,900)]
right=[(W,0),(W-225,0),(W-175,160),(W-215,330),(W-150,495),(W-190,680),(W-95,865),(W,920)]
fd.polygon(left,fill=(4,9,20,245))
fd.polygon(right,fill=(4,9,20,245))
# stalactites
for x in range(80,W,180):
    depth=random.randint(70,190)
    fd.polygon([(x-55,0),(x+50,0),(x+18,depth),(x-8,depth+random.randint(20,80))],fill=(3,8,18,235))
# foreground crystals
for cx,cy,rr in [(110,880,72),(1810,865,92)]:
    fg=add_glow(fg,cx,cy,int(rr*2.1),(93,220,255),70)
    fd=ImageDraw.Draw(fg,"RGBA")
    draw_crystal(fd,cx,cy,rr,(79,188,245,225))
fg.save(OUT/"cave_fg.png")

# collectible crystal
cr=Image.new("RGBA",(512,512),(0,0,0,0))
cr=add_glow(cr,256,260,160,(90,225,255),90)
cd=ImageDraw.Draw(cr,"RGBA")
draw_crystal(cd,256,245,150,(102,226,255,245),(230,254,255,255))
cd.polygon([(220,120),(252,155),(238,300)],fill=(255,255,255,155))
cr.save(OUT/"crystal_blue.png")

# Cave Mite anime creature
em=Image.new("RGBA",(1024,1024),(0,0,0,0))
em=add_glow(em,512,520,330,(153,83,255),45)
ed=ImageDraw.Draw(em,"RGBA")
# shadow
ed.ellipse((260,720,764,830),fill=(0,0,0,70))
body=[(250,620),(225,460),(290,320),(420,245),(512,260),(604,245),(735,325),(805,470),(770,625),(650,735),(512,770),(370,735)]
ed.polygon(body,fill=(111,57,171,255),outline=(32,20,55,255))
# cel light patches
ed.polygon([(330,360),(430,275),(510,290),(450,470),(320,500)],fill=(204,137,255,185))
ed.polygon([(610,300),(725,370),(748,520),(635,470)],fill=(82,34,133,210))
# horns
ed.polygon([(330,330),(260,160),(410,300)],fill=(236,222,255,255),outline=(45,28,62,255))
ed.polygon([(694,330),(764,160),(614,300)],fill=(236,222,255,255),outline=(45,28,62,255))
# eyes
ed.polygon([(335,470),(470,440),(440,535),(330,530)],fill=(255,238,118,255))
ed.polygon([(689,470),(554,440),(584,535),(694,530)],fill=(255,238,118,255))
ed.ellipse((387,462,430,515),fill=(43,18,55,255))
ed.ellipse((594,462,637,515),fill=(43,18,55,255))
# mouth
ed.arc((430,535,595,650),start=20,end=160,fill=(35,16,46,255),width=16)
# small cheek accents
ed.ellipse((300,548,360,580),fill=(225,119,210,65)); ed.ellipse((664,548,724,580),fill=(225,119,210,65))
em.save(OUT/"cave_mite.png")

# magic slash
sl=Image.new("RGBA",(1024,512),(0,0,0,0))
slg=Image.new("RGBA",(1024,512),(0,0,0,0))
sg=ImageDraw.Draw(slg,"RGBA")
for w,a in [(60,25),(36,55),(18,120)]:
    sg.arc((120,20,960,520),start=208,end=332,fill=(115,220,255,a),width=w)
slg=slg.filter(ImageFilter.GaussianBlur(10))
sl=Image.alpha_composite(sl,slg)
sd=ImageDraw.Draw(sl,"RGBA")
sd.arc((120,20,960,520),start=208,end=332,fill=(220,253,255,245),width=16)
sd.arc((145,45,930,495),start=210,end=330,fill=(72,173,255,210),width=8)
sl.save(OUT/"magic_slash.png")

# impact burst
ib=Image.new("RGBA",(512,512),(0,0,0,0))
idraw=ImageDraw.Draw(ib,"RGBA")
for i in range(24):
    a=i*math.pi*2/24
    inner=70+random.randint(0,25); outer=190+random.randint(-20,45)
    x1=256+math.cos(a)*inner; y1=256+math.sin(a)*inner
    x2=256+math.cos(a)*outer; y2=256+math.sin(a)*outer
    idraw.line((x1,y1,x2,y2),fill=(255,238,145,230),width=random.randint(4,10))
idraw.ellipse((185,185,327,327),fill=(255,255,255,220))
ib=ib.filter(ImageFilter.GaussianBlur(1.1))
ib.save(OUT/"impact_burst.png")

# absorb ring
ar=Image.new("RGBA",(1024,1024),(0,0,0,0))
ad=ImageDraw.Draw(ar,"RGBA")
for rr,a,wid in [(350,80,20),(285,135,13),(220,190,9)]:
    ad.ellipse((512-rr,512-rr,512+rr,512+rr),outline=(110,255,210,a),width=wid)
for i in range(16):
    a=i*math.pi*2/16
    r1=250; r2=360
    ad.line((512+math.cos(a)*r1,512+math.sin(a)*r1,512+math.cos(a)*r2,512+math.sin(a)*r2),fill=(150,255,226,95),width=5)
ar=ar.filter(ImageFilter.GaussianBlur(1.0))
ar.save(OUT/"absorb_ring.png")

print("Generated anime cave assets:")
for p in sorted(OUT.glob("*.png")):
    print(p.name, p.stat().st_size)
