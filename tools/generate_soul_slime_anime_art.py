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


# ---------- expanded cave room art ----------
def save_room_variant(name, tint=(0,0,0,0), glows=(), feature=None):
    room=bg.copy()
    if tint[3] > 0:
        overlay=Image.new("RGBA",(W,H),tint)
        room=Image.alpha_composite(room,overlay)
    for gx,gy,gr,gcol,ga in glows:
        room=add_glow(room,gx,gy,gr,gcol,ga)
    rd=ImageDraw.Draw(room,"RGBA")
    if feature=="mana":
        # glowing mana spring and rune bands
        rd.ellipse((610,610,1310,925),fill=(46,176,218,70),outline=(166,248,255,130),width=8)
        rd.ellipse((690,665,1230,870),outline=(210,255,255,120),width=5)
        for i in range(10):
            a=i*math.pi*2/10
            x=960+math.cos(a)*360
            y=740+math.sin(a)*130
            rd.arc((x-42,y-42,x+42,y+42),20,300,fill=(171,249,255,90),width=4)
    elif feature=="crossroads":
        # dark anime passage mouths
        for box in [(760,90,1160,420),(0,470,250,870),(1670,460,1920,870),(730,700,1190,1080)]:
            rd.rounded_rectangle(box,radius=95,fill=(2,7,18,220),outline=(77,145,191,80),width=5)
    elif feature=="web":
        # stylized web chamber
        for cx,cy,rr in [(480,360,280),(1410,420,340),(950,250,230)]:
            for a in range(0,360,30):
                x2=cx+math.cos(math.radians(a))*rr
                y2=cy+math.sin(math.radians(a))*rr
                rd.line((cx,cy,x2,y2),fill=(225,235,255,85),width=3)
            for ring in (.30,.55,.78,1.0):
                r2=rr*ring
                rd.ellipse((cx-r2,cy-r2,cx+r2,cy+r2),outline=(229,239,255,55),width=3)
    elif feature=="scale":
        # mossy/emerald crystal growths
        for cx,cy,rr in [(420,650,82),(610,560,58),(1370,650,95),(1580,540,62)]:
            draw_crystal(rd,cx,cy,rr,(114,244,184,220),(218,255,236,235))
        for i in range(22):
            x=random.randint(130,W-130); y=random.randint(270,800)
            rd.line((x,y,x+random.randint(-80,80),y+random.randint(25,90)),fill=(91,183,131,45),width=5)
    elif feature=="gate":
        # ancient inner gate
        rd.rounded_rectangle((610,155,1310,890),radius=210,fill=(5,11,26,210),outline=(119,192,255,110),width=12)
        rd.rounded_rectangle((705,250,1215,885),radius=165,outline=(184,235,255,95),width=6)
        for i in range(8):
            a=i*math.pi*2/8
            x=960+math.cos(a)*410
            y=520+math.sin(a)*300
            rd.ellipse((x-24,y-24,x+24,y+24),outline=(185,248,255,95),width=5)
    elif feature=="dragon":
        # enormous sanctum / magic seal
        for rr,a in [(420,55),(325,75),(240,105)]:
            rd.ellipse((960-rr,470-rr*.55,960+rr,470+rr*.55),outline=(187,126,255,a),width=8)
        rd.polygon([(760,720),(960,330),(1160,720)],outline=(218,183,255,100))
    elif feature=="exit":
        # blinding daylight cave mouth on the right
        room=add_glow(room,1700,500,420,(245,252,255),185)
        rd=ImageDraw.Draw(room,"RGBA")
        rd.rounded_rectangle((1480,180,1920,880),radius=180,fill=(225,247,255,120),outline=(255,255,255,195),width=14)
    room.save(OUT/name)

save_room_variant("cave_corridor.png",glows=[(420,470,220,(80,215,255),45),(1520,550,220,(130,92,255),35)])
save_room_variant("cave_mana.png",tint=(0,30,40,20),glows=[(960,700,520,(74,235,255),115),(1250,430,250,(130,116,255),45)],feature="mana")
save_room_variant("cave_crossroads.png",tint=(5,0,18,18),glows=[(960,520,340,(95,184,255),55)],feature="crossroads")
save_room_variant("cave_web.png",tint=(38,0,50,26),glows=[(620,430,360,(193,130,255),58),(1400,430,390,(155,118,255),52)],feature="web")
save_room_variant("cave_scale.png",tint=(0,42,22,24),glows=[(530,600,280,(104,255,179),52),(1450,610,310,(104,255,179),48)],feature="scale")
save_room_variant("cave_gate.png",tint=(0,0,16,42),glows=[(960,500,420,(100,195,255),55)],feature="gate")
save_room_variant("cave_dragon.png",tint=(30,0,55,38),glows=[(960,470,600,(150,102,255),85),(960,730,280,(74,218,255),36)],feature="dragon")
save_room_variant("cave_exit.png",tint=(10,10,0,8),glows=[(1640,510,480,(235,250,255),140)],feature="exit")

# mana / essence orb
eo=Image.new("RGBA",(384,384),(0,0,0,0))
eo=add_glow(eo,192,192,145,(95,229,255),120)
eod=ImageDraw.Draw(eo,"RGBA")
eod.ellipse((105,105,279,279),fill=(95,221,255,215),outline=(227,254,255,255),width=8)
eod.ellipse((132,126,210,204),fill=(255,255,255,150))
eod.arc((80,80,304,304),25,205,fill=(167,112,255,155),width=10)
eo.save(OUT/"essence_orb.png")

# anime crystal spider
sp=Image.new("RGBA",(1024,1024),(0,0,0,0))
sp=add_glow(sp,512,530,330,(177,115,255),55)
sd=ImageDraw.Draw(sp,"RGBA")
# legs
for side in (-1,1):
    for i,(yy,ang) in enumerate([(380,-70),(465,-48),(550,-30),(630,-12)]):
        x0=512+side*190; y0=yy
        x1=512+side*(325+35*i); y1=yy+ang
        x2=512+side*(405+28*i); y2=yy+ang+95
        sd.line((x0,y0,x1,y1,x2,y2),fill=(43,26,70,255),width=36,joint="curve")
        sd.line((x0,y0,x1,y1,x2,y2),fill=(146,93,205,255),width=22,joint="curve")
# body/head
sd.ellipse((300,360,724,790),fill=(90,51,145,255),outline=(35,22,58,255),width=16)
sd.ellipse((350,245,674,530),fill=(145,91,202,255),outline=(35,22,58,255),width=16)
# cel highlights
sd.pieslice((330,270,625,525),200,310,fill=(219,166,255,145))
# big anime eyes
for ex in (430,594):
    sd.ellipse((ex-72,330,ex+72,482),fill=(238,250,255,255),outline=(36,22,57,255),width=12)
    sd.ellipse((ex-38,355,ex+38,455),fill=(58,34,85,255))
    sd.ellipse((ex-20,370,ex+2,400),fill=(255,255,255,235))
# fangs + expression
sd.polygon([(464,500),(492,575),(515,505)],fill=(245,247,255,255))
sd.polygon([(560,500),(532,575),(509,505)],fill=(245,247,255,255))
sd.arc((430,470,594,610),15,165,fill=(38,22,59,255),width=12)
sp.save(OUT/"anime_spider.png")

# anime cave lizard
lz=Image.new("RGBA",(1024,1024),(0,0,0,0))
lz=add_glow(lz,510,540,330,(92,255,173),45)
ld=ImageDraw.Draw(lz,"RGBA")
# tail
tail=[(650,600),(850,545),(920,610),(785,670),(680,690)]
ld.polygon(tail,fill=(69,161,120,255),outline=(24,57,46,255))
# body
ld.ellipse((270,390,740,760),fill=(80,189,137,255),outline=(25,60,47,255),width=17)
# head
head=[(315,430),(300,270),(455,205),(650,250),(735,390),(660,520),(440,535)]
ld.polygon(head,fill=(107,213,159,255),outline=(25,60,47,255))
# lighter anime cheek / belly
ld.ellipse((355,470,640,720),fill=(149,234,192,150))
# crest
for i in range(5):
    x=400+i*58
    ld.polygon([(x,278),(x+30,150-i*8),(x+58,285)],fill=(131,246,199,255),outline=(28,68,52,255))
# eyes
for ex in (440,615):
    ld.ellipse((ex-62,315,ex+62,435),fill=(246,252,224,255),outline=(28,64,50,255),width=10)
    ld.ellipse((ex-22,330,ex+22,422),fill=(32,72,54,255))
    ld.ellipse((ex-12,342,ex+2,365),fill=(255,255,255,240))
# smile
ld.arc((455,420,620,545),10,165,fill=(31,67,53,255),width=11)
# feet
for ex in (355,650):
    ld.ellipse((ex-80,690,ex+65,785),fill=(74,173,128,255),outline=(25,60,47,255),width=12)
lz.save(OUT/"anime_lizard.png")

# ancient anime dragon
dr=Image.new("RGBA",(1536,1024),(0,0,0,0))
dr=add_glow(dr,780,520,460,(144,92,255),62)
dd=ImageDraw.Draw(dr,"RGBA")
# wings
dd.polygon([(565,470),(260,190),(120,260),(330,500),(160,640),(540,625)],fill=(64,48,111,235),outline=(25,23,55,255))
dd.polygon([(980,470),(1280,185),(1420,270),(1210,500),(1380,650),(995,625)],fill=(64,48,111,235),outline=(25,23,55,255))
# long body
dd.ellipse((505,320,1030,850),fill=(79,67,151,255),outline=(24,25,60,255),width=20)
# neck and head
dd.polygon([(580,515),(535,275),(650,120),(845,105),(990,260),(952,505)],fill=(103,91,184,255),outline=(24,25,60,255))
# cel highlight
dd.polygon([(610,300),(650,160),(790,135),(740,475),(600,600)],fill=(177,151,242,95))
# horns
dd.polygon([(640,180),(560,40),(720,140)],fill=(231,225,255,255),outline=(49,44,83,255))
dd.polygon([(855,155),(965,40),(910,225)],fill=(231,225,255,255),outline=(49,44,83,255))
# eyes
for ex in (690,870):
    dd.polygon([(ex-58,255),(ex+58,240),(ex+38,335),(ex-48,335)],fill=(155,242,255,255))
    dd.ellipse((ex-15,265,ex+15,330),fill=(23,38,72,255))
# muzzle / smile
dd.polygon([(680,345),(790,325),(900,350),(855,455),(715,455)],fill=(126,110,201,255),outline=(40,36,78,255))
dd.arc((710,365,875,480),10,160,fill=(28,30,67,255),width=12)
# chest rune
dd.ellipse((660,550,875,755),outline=(160,236,255,185),width=14)
dd.polygon([(768,580),(825,660),(768,730),(712,660)],outline=(214,251,255,225))
dr.save(OUT/"ancient_dragon.png")

# silk projectile / web burst
wb=Image.new("RGBA",(512,512),(0,0,0,0))
wd=ImageDraw.Draw(wb,"RGBA")
for ring in (70,120,170,215):
    wd.ellipse((256-ring,256-ring,256+ring,256+ring),outline=(232,241,255,135),width=6)
for a in range(0,360,30):
    wd.line((256,256,256+math.cos(math.radians(a))*225,256+math.sin(math.radians(a))*225),fill=(240,246,255,155),width=5)
wb.save(OUT/"web_burst.png")

# dragon storm effect
st=Image.new("RGBA",(1024,1024),(0,0,0,0))
std=ImageDraw.Draw(st,"RGBA")
for i in range(10):
    x=180+i*70
    pts=[(x,90),(x+70,310),(x+25,300),(x+105,560),(x+35,535),(x+135,865)]
    std.line(pts,fill=(188,238,255,210),width=15)
st=st.filter(ImageFilter.GaussianBlur(1.0))
st.save(OUT/"storm_burst.png")


# ==================== v6 ADULT ANIME / DRAGON-DRIVEN ART PASS ====================
# Sharper silhouette language, layered charcoal rock, aged gold mineral armor,
# crimson magical fissures and cyan mana crystal lighting.

def jagged_poly(cx,cy,rx,ry,n=12,seed=1):
    rr=random.Random(seed)
    pts=[]
    for i in range(n):
        a=math.pi*2*i/n
        jitter=.78+rr.random()*.35
        pts.append((cx+math.cos(a)*rx*jitter,cy+math.sin(a)*ry*jitter))
    return pts

def gold_spine(draw,x,y,length,angle,thick=18,alpha=210):
    ca,sa=math.cos(angle),math.sin(angle)
    nx,ny=-sa,ca
    p1=(x+nx*thick,y+ny*thick)
    p2=(x+ca*length,y+sa*length)
    p3=(x-nx*thick,y-ny*thick)
    draw.polygon([p1,p2,p3],fill=(165,115,58,alpha),outline=(239,194,111,min(255,alpha+25)))
    draw.line((x,y,p2[0],p2[1]),fill=(255,221,154,min(255,alpha+20)),width=max(2,int(thick*.18)))

def adult_room(name, base_img, red=1.0, cyan=1.0, gold=1.0, feature=""):
    im=base_img.copy().convert("RGBA")
    im=add_glow(im,960,525,620,(120,34,58),int(48*red))
    im=add_glow(im,650,500,300,(48,176,236),int(42*cyan))
    d=ImageDraw.Draw(im,"RGBA")

    # fine crack network and red fissures
    rr=random.Random(hash(name)&0xffffffff)
    for i in range(75):
        x=rr.randint(30,W-30); y=rr.randint(120,H-100)
        segs=[(x,y)]
        for j in range(rr.randint(2,5)):
            x+=rr.randint(-55,55); y+=rr.randint(18,65)
            segs.append((x,y))
        d.line(segs,fill=(135,29,38,rr.randint(28,65)),width=rr.randint(2,5))
        if i%8==0:d.line(segs,fill=(250,64,55,38),width=1)

    # dragon-like gold mineral spines framing architecture
    for i in range(22):
        edge=rr.choice([0,1])
        x=rr.randint(0,W) if i%3 else (rr.randint(20,240) if edge==0 else rr.randint(W-240,W-20))
        y=rr.randint(80,H-90)
        ang=rr.uniform(-1.1,-.25) if edge==0 else rr.uniform(-2.9,-2.05)
        gold_spine(d,x,y,rr.randint(60,170),ang,rr.randint(8,18),int(175*gold))

    # sharp slate plates, layered like scales
    for i in range(48):
        x=rr.randint(50,W-50); y=rr.randint(100,H-80)
        rw=rr.randint(28,85); rh=rr.randint(20,70)
        pts=[(x-rw,y),(x-rw*.25,y-rh),(x+rw,y-rh*.20),(x+rw*.45,y+rh),(x-rw*.55,y+rh*.65)]
        d.polygon(pts,fill=(20,22,31,rr.randint(35,85)),outline=(108,79,50,rr.randint(25,75)))

    if feature=="obsidian":
        for x in (340,750,1180,1540):
            pts=jagged_poly(x,720,115,240,8,int(x))
            d.polygon(pts,fill=(15,17,24,230),outline=(188,131,61,120))
    elif feature=="crypt":
        for x in (380,720,1100,1450):
            d.rounded_rectangle((x-70,420,x+70,760),radius=20,fill=(12,14,20,215),outline=(157,113,61,125),width=5)
            d.polygon([(x-90,430),(x,310),(x+90,430)],fill=(28,24,28,225),outline=(206,150,76,140))
    elif feature=="echo":
        for rr2,a in [(330,75),(245,95),(165,115)]:
            d.ellipse((960-rr2,535-rr2*.42,960+rr2,535+rr2*.42),outline=(82,203,255,a),width=6)
            d.arc((960-rr2,535-rr2*.42,960+rr2,535+rr2*.42),205,120,fill=(220,246,255,a),width=3)
    elif feature=="shard":
        for x,y,r2 in [(400,650,95),(700,520,72),(1040,700,110),(1430,560,82),(1650,720,96)]:
            draw_crystal(d,x,y,r2,(75,190,245,230),(226,252,255,245))
            gold_spine(d,x-40,y+50,r2*1.4,-1.15,12,185)
    elif feature=="grotto":
        d.ellipse((380,590,1540,1030),fill=(8,37,45,120),outline=(69,171,188,85),width=6)
        for i in range(16):
            x=rr.randint(420,1500); y=rr.randint(650,970)
            d.ellipse((x-4,y-2,x+4,y+2),fill=(125,235,224,rr.randint(40,100)))
    elif feature=="vault":
        d.rounded_rectangle((620,185,1300,850),radius=145,fill=(8,10,17,195),outline=(210,153,75,165),width=11)
        d.rounded_rectangle((700,260,1220,835),radius=120,outline=(112,76,43,150),width=5)
        for i in range(10):
            a=i*math.pi*2/10
            x=960+math.cos(a)*390;y=520+math.sin(a)*290
            gold_spine(d,x,y,95,a+math.pi,13,190)
    elif feature=="chasm":
        d.polygon([(570,515),(790,420),(960,600),(1130,420),(1380,520),(1190,1080),(720,1080)],fill=(1,3,9,235))
        for i in range(11):
            x=650+i*70
            d.line((x,600,x+rr.randint(-60,60),1020),fill=(235,49,46,45),width=rr.randint(3,8))
    elif feature=="approach":
        d.rounded_rectangle((650,170,1270,910),radius=220,outline=(212,157,81,185),width=13)
        for i in range(7):
            x=735+i*82
            d.line((x,250,x,850),fill=(112,76,44,80),width=5)

    # extra crispness / mature high detail
    im=im.filter(ImageFilter.UnsharpMask(radius=2.0,percent=165,threshold=3))
    im.save(OUT/name)

# Overwrite core cave rooms with the mature pass.
adult_room("cave_bg.png",bg,red=.85,cyan=1.15,gold=1.0,feature="")
adult_room("cave_corridor.png",bg,red=.72,cyan=.72,gold=1.15,feature="obsidian")
adult_room("cave_mana.png",bg,red=.48,cyan=1.75,gold=.95,feature="echo")
adult_room("cave_crossroads.png",bg,red=1.0,cyan=.80,gold=1.2,feature="shard")
adult_room("cave_web.png",bg,red=1.45,cyan=.42,gold=1.08,feature="")
adult_room("cave_scale.png",bg,red=.92,cyan=.62,gold=1.40,feature="")
adult_room("cave_gate.png",bg,red=1.22,cyan=.45,gold=1.65,feature="vault")
adult_room("cave_dragon.png",bg,red=1.75,cyan=.25,gold=1.75,feature="approach")
adult_room("cave_exit.png",bg,red=.35,cyan=.65,gold=1.1,feature="")

# New maze room backgrounds.
adult_room("cave_obsidian.png",bg,red=.82,cyan=.42,gold=1.22,feature="obsidian")
adult_room("cave_crypt.png",bg,red=.72,cyan=.42,gold=1.28,feature="crypt")
adult_room("cave_echo.png",bg,red=.45,cyan=1.45,gold=.90,feature="echo")
adult_room("cave_shard.png",bg,red=.70,cyan=1.35,gold=1.20,feature="shard")
adult_room("cave_grotto.png",bg,red=.50,cyan=1.15,gold=.82,feature="grotto")
adult_room("cave_vault.png",bg,red=1.05,cyan=.34,gold=1.75,feature="vault")
adult_room("cave_chasm.png",bg,red=1.65,cyan=.28,gold=1.10,feature="chasm")
adult_room("cave_dragon_approach.png",bg,red=1.45,cyan=.25,gold=1.72,feature="approach")

# Mature Cave Mite: angular armored cave arthropod, no cute face.
mite2=Image.new("RGBA",(1400,1400),(0,0,0,0))
mite2=add_glow(mite2,700,760,420,(193,52,48),40)
md=ImageDraw.Draw(mite2,"RGBA")
# rear legs / blade limbs
for side in (-1,1):
    for i in range(4):
        y=650+i*90
        x0=700+side*(235+i*12)
        x1=700+side*(410+i*38)
        y1=y-120+i*25
        x2=700+side*(520+i*25)
        y2=y+80
        md.line((x0,y,x1,y1,x2,y2),fill=(22,24,31,255),width=68,joint="curve")
        md.line((x0,y,x1,y1,x2,y2),fill=(158,112,58,210),width=16,joint="curve")
# armored shell
shell=jagged_poly(700,725,310,330,14,77)
md.polygon(shell,fill=(32,34,42,255),outline=(211,154,78,255))
for i in range(7):
    yy=470+i*75
    md.polygon([(465,yy),(700,yy-52),(935,yy),(870,yy+55),(530,yy+55)],fill=(50+i*2,48+i*2,54+i*2,245),outline=(143,101,56,205))
# horned head + mandibles
md.polygon([(490,515),(575,315),(665,450),(700,275),(745,450),(845,315),(920,520)],fill=(44,42,49,255),outline=(224,164,84,255))
md.polygon([(540,550),(400,620),(560,690)],fill=(210,151,77,245),outline=(252,207,128,255))
md.polygon([(860,550),(1000,620),(840,690)],fill=(210,151,77,245),outline=(252,207,128,255))
for ex in (600,800):
    md.polygon([(ex-48,500),(ex+48,490),(ex+25,555),(ex-35,560)],fill=(255,75,38,255))
    md.ellipse((ex-8,510,ex+8,548),fill=(255,225,118,255))
mite2=mite2.filter(ImageFilter.UnsharpMask(radius=1.6,percent=180,threshold=2))
mite2.save(OUT/"cave_mite.png")

# Mature Crystal Weaver: blade-legged armored spider with small glowing eyes.
sp2=Image.new("RGBA",(1600,1600),(0,0,0,0))
sp2=add_glow(sp2,800,830,500,(159,42,74),44)
sd=ImageDraw.Draw(sp2,"RGBA")
for side in (-1,1):
    for i,(ang,yy) in enumerate([(-.82,590),(-.48,700),(-.18,825),(.14,930)]):
        x0=800+side*260;y0=yy
        x1=x0+side*(260+i*35);y1=y0-120+i*35
        x2=x1+side*(210+i*18);y2=y1+160+i*34
        sd.line((x0,y0,x1,y1,x2,y2),fill=(25,24,31,255),width=78,joint="curve")
        sd.line((x0,y0,x1,y1,x2,y2),fill=(192,132,62,220),width=18,joint="curve")
        gold_spine(sd,x1,y1,105,-1.2 if side<0 else -1.95,15,220)
abd=jagged_poly(800,900,330,350,16,91)
sd.polygon(abd,fill=(37,35,43,255),outline=(204,145,70,255))
for i in range(6):
    yy=680+i*92
    sd.arc((505,yy-120,1095,yy+130),200,140,fill=(155,105,57,165),width=15)
head=jagged_poly(800,520,235,190,10,43)
sd.polygon(head,fill=(43,39,46,255),outline=(220,158,80,255))
# head crest
for xoff,angle in [(-170,-2.0),(-80,-1.75),(80,-1.4),(170,-1.15)]:
    gold_spine(sd,800+xoff,430,165,angle,18,235)
# eight small predatory eyes
for i in range(4):
    ex=705+i*63
    for ey in (500,545):
        sd.ellipse((ex-16,ey-10,ex+16,ey+10),fill=(255,56,36,255))
        sd.ellipse((ex-5,ey-5,ex+5,ey+5),fill=(255,229,154,255))
# fangs
sd.polygon([(700,590),(760,760),(820,600)],fill=(226,180,102,255),outline=(255,223,161,255))
sd.polygon([(900,590),(840,760),(780,600)],fill=(226,180,102,255),outline=(255,223,161,255))
sp2=sp2.filter(ImageFilter.UnsharpMask(radius=1.8,percent=190,threshold=2))
sp2.save(OUT/"anime_spider.png")

# Mature Scale Runner: lean predatory cave drake/lizard with gold armor.
lz2=Image.new("RGBA",(1600,1600),(0,0,0,0))
lz2=add_glow(lz2,790,860,480,(42,154,111),36)
ld=ImageDraw.Draw(lz2,"RGBA")
# long tail
tail=[(990,960),(1280,820),(1510,900),(1320,980),(1120,1060),(980,1080)]
ld.polygon(tail,fill=(27,41,38,255),outline=(179,126,63,230))
# athletic body
bodypts=jagged_poly(790,870,390,280,14,140)
ld.polygon(bodypts,fill=(34,45,43,255),outline=(202,145,71,255))
# plated back
for i in range(7):
    x=515+i*90
    ld.polygon([(x,720),(x+45,610-i*5),(x+92,730),(x+70,805),(x+10,805)],fill=(88,76,58,235),outline=(224,166,86,225))
# neck/head
head=[(450,760),(410,515),(570,355),(845,365),(1015,540),(940,750),(720,810)]
ld.polygon(head,fill=(36,48,45,255),outline=(216,154,75,255))
# jaw armor
ld.polygon([(515,630),(700,585),(915,625),(850,745),(620,750)],fill=(74,65,54,235),outline=(198,140,68,220))
# dorsal horns
for i in range(7):
    x=540+i*76
    gold_spine(ld,x,470-i*5,145,-1.47,16,220)
# serious narrow eyes
for ex in (610,825):
    ld.polygon([(ex-65,545),(ex+65,530),(ex+32,600),(ex-50,608)],fill=(99,255,184,255))
    ld.ellipse((ex-10,548,ex+10,600),fill=(11,41,31,255))
# claws/limbs
for ex in (520,945):
    ld.line((ex,930,ex-90,1130),fill=(31,42,39,255),width=80)
    ld.line((ex+120,930,ex+205,1115),fill=(31,42,39,255),width=80)
    for j in range(3):
        gold_spine(ld,ex-90+j*24,1130,75,1.8+j*.18,9,220)
lz2=lz2.filter(ImageFilter.UnsharpMask(radius=1.8,percent=190,threshold=2))
lz2.save(OUT/"anime_lizard.png")

# Ancient dragon, directly driven by the supplied reference's adult black/gold/red language.
dr2=Image.new("RGBA",(1800,1800),(0,0,0,0))
dr2=add_glow(dr2,900,950,650,(175,40,32),42)
dd=ImageDraw.Draw(dr2,"RGBA")
# wings behind body
leftwing=[(760,830),(320,300),(80,360),(340,780),(130,1120),(700,1030)]
rightwing=[(1040,830),(1480,280),(1730,370),(1460,790),(1690,1130),(1100,1020)]
for wing in (leftwing,rightwing):
    dd.polygon(wing,fill=(83,18,25,245),outline=(202,145,72,255))
# black wing fingers / gold ridges
for side in (-1,1):
    root=(900+side*170,800)
    for i in range(5):
        tip=(900+side*(420+i*120),360+i*145)
        dd.line((root[0],root[1],tip[0],tip[1]),fill=(28,28,34,255),width=54)
        dd.line((root[0],root[1],tip[0],tip[1]),fill=(204,147,75,220),width=12)
# serpentine body and tail
dd.ellipse((565,600,1245,1435),fill=(32,34,41,255),outline=(214,155,76,255),width=22)
tail=[(1070,1180),(1420,1180),(1700,1390),(1490,1545),(1180,1410),(1000,1270)]
dd.polygon(tail,fill=(30,32,39,255),outline=(202,145,72,255))
# layered gold breast armor
for i in range(7):
    y=650+i*112
    half=205-i*10
    dd.polygon([(900-half,y),(900,y-55),(900+half,y),(900+half*.70,y+105),(900-half*.70,y+105)],fill=(132,100,65,245),outline=(236,190,112,255))
# neck and head
neck=[(690,850),(620,500),(700,235),(900,120),(1110,230),(1190,500),(1080,850)]
dd.polygon(neck,fill=(31,33,40,255),outline=(217,158,79,255))
# long armored snout
head=[(650,420),(610,290),(755,145),(970,105),(1160,230),(1195,390),(1050,520),(780,540)]
dd.polygon(head,fill=(37,37,44,255),outline=(227,166,85,255))
# head golden crown horns
for x,y,length,ang in [(690,240,250,-2.15),(770,190,245,-1.85),(915,145,255,-1.45),(1040,195,235,-1.15),(1125,275,205,-.86)]:
    gold_spine(dd,x,y,length,ang,26,255)
# jaw spikes
for i in range(6):
    x=690+i*80
    gold_spine(dd,x,485,100,1.35+(i-3)*.05,12,230)
# red glowing eyes
for ex in (790,1015):
    dd.polygon([(ex-70,315),(ex+55,295),(ex+20,355),(ex-55,360)],fill=(255,57,24,255))
    dd.ellipse((ex-5,315,ex+12,352),fill=(255,228,128,255))
# dorsal spines
for i in range(9):
    x=1080+i*42;y=720+i*80
    gold_spine(dd,x,y,135,-.85,15,230)
# claws
for bx,by in [(620,1240),(1050,1260)]:
    for j in range(4):
        gold_spine(dd,bx+j*45,by,110,1.72+j*.11,14,245)
dr2=dr2.filter(ImageFilter.UnsharpMask(radius=2.0,percent=205,threshold=2))
dr2.save(OUT/"ancient_dragon.png")

print("Generated anime cave assets:")
for p in sorted(OUT.glob("*.png")):
    print(p.name, p.stat().st_size)
