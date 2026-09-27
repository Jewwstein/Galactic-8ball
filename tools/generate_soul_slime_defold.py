from pathlib import Path
import os, math, random
from PIL import Image, ImageDraw, ImageFilter, ImageChops, ImageFont

OUT = Path(os.environ.get("DEFOLD_OUT", "defold_project")).resolve()
(OUT/"main").mkdir(parents=True, exist_ok=True)
(OUT/"input").mkdir(parents=True, exist_ok=True)
(OUT/"assets").mkdir(parents=True, exist_ok=True)

FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
def font(size,bold=False):
    try: return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)
    except: return ImageFont.load_default()

# ---------- project ----------
(OUT/"game.project").write_text("""[project]
title = Soul Slime HD Creator
version = 0.2
publisher = Soul Slime
developer = Soul Slime
bundle_identifier = com.soulslime.reincarnation.v06

[bootstrap]
main_collection = /main/main.collectionc

[input]
game_binding = /input/game.input_bindingc
use_accelerometer = 0

[display]
width = 1280
height = 720
high_dpi = 1
fullscreen = 1
dynamic_orientation = 0
samples = 0
update_frequency = 60
swap_interval = 1

[android]
package = com.soulslime.reincarnation.v06
minimum_sdk_version = 26
target_sdk_version = 36
immersive_mode = 1
input_method = HiddenInputField

[script]
shared_state = 1

[graphics]
max_characters = 4096
""")
(OUT/"input/game.input_binding").write_text("""mouse_trigger {
  input: MOUSE_BUTTON_1
  action: "touch"
}
""")

# ---------- art helpers ----------
A=OUT/"assets"
W,H=1280,720
CYAN=(76,225,255,255)
PALE=(220,250,255,255)

def rr(draw,box,r,fill,outline=None,width=1):
    draw.rounded_rectangle(box,radius=r,fill=fill,outline=outline,width=width)

# Bright anime-inspired analysis chamber.
# Intentionally much brighter than v0.2 so the space reads clearly on OLED phones.
bg=Image.new("RGBA",(W,H),(8,28,48,255))
p=bg.load()
for y in range(H):
    for x in range(W):
        dx=(x-360)/520.0
        dy=(y-360)/410.0
        r=(dx*dx+dy*dy)**0.5
        core=max(0.0,1.0-r)
        side=max(0.0,1.0-abs(x-360)/900.0)
        rr0=int(8 + 24*side + 52*core)
        gg0=int(28 + 72*side + 142*core)
        bb0=int(48 + 92*side + 168*core)
        p[x,y]=(min(rr0,255),min(gg0,255),min(bb0,255),255)
d=ImageDraw.Draw(bg,"RGBA")

# Wide luminous mist / energy field.
for i in range(11,0,-1):
    pad=i*28
    alpha=10+i*5
    d.ellipse((360-300-pad,360-300-pad,360+300+pad,360+300+pad),
              fill=(55,215,246,alpha))

# Bright central analysis core behind the slime.
for r0,a0 in ((300,22),(250,30),(205,42),(160,56),(118,76)):
    d.ellipse((360-r0,360-r0,360+r0,360+r0),outline=(166,246,255,a0),width=4)
d.ellipse((240,240,480,480),fill=(104,225,246,40))
d.ellipse((288,288,432,432),fill=(200,252,255,55))

# Holographic radial spokes.
for deg in range(0,360,12):
    a=math.radians(deg)
    d.line((360+math.cos(a)*118,360+math.sin(a)*118,
            360+math.cos(a)*315,360+math.sin(a)*315),
           fill=(130,239,255,54 if deg%24==0 else 26),width=2)

# Broken concentric scanner arcs.
for i,r0 in enumerate((132,168,208,252,298,338)):
    for seg in range(6):
        start=(seg*60+i*9)%360
        end=start+24+(seg%2)*15
        d.arc((360-r0,360-r0,360+r0,360+r0),start,end,
              fill=(167,246,255,165-i*18),width=3 if i<3 else 2)

# Visible cyan grid and floating data.
for x in range(18,W,54):
    d.line((x,0,x,H),fill=(79,201,233,30),width=1)
for y in range(18,H,42):
    d.line((0,y,W,y),fill=(79,201,233,26),width=1)
random.seed(913)
for i in range(150):
    x=random.randint(20,1240); y=random.randint(18,700)
    if 760 < x < 1260:
        alpha=random.randint(35,82)
    else:
        alpha=random.randint(45,120)
    sz=random.choice((2,3,4,6,8,10))
    if i%4==0:
        d.rectangle((x,y,x+sz,y+sz),outline=(145,244,255,alpha),width=1)
    else:
        d.ellipse((x,y,x+sz,y+sz),fill=(130,240,255,alpha))

# Strong glass panels; translucent enough to keep the energy field visible.
rr(d,(16,16,742,704),28,(10,51,74,108),(157,242,255,122),2)
rr(d,(758,18,1262,702),26,(7,35,60,214),(136,239,255,170),2)
d.line((758,100,1262,100),fill=(175,248,255,145),width=2)
d.line((758,614,1262,614),fill=(175,248,255,110),width=1)

d.text((42,34),"ANALYSIS SPACE // ACTIVE",font=font(30,True),fill=(238,254,255,255))
d.text((42,72),"SOUL VESSEL MATERIALIZATION / LIVE SCAN",font=font(15,True),fill=(152,243,255,245))
d.text((786,38),"SYSTEM / MATERIALIZATION",font=font(16,True),fill=(224,252,255,255))
d.text((786,67),"Tap a parameter, then tap an option",font=font(13),fill=(158,228,246,240))

d.text((786,590),"1  SELECT PARAMETER",font=font(12,True),fill=(166,246,255,255))
d.text((786,374),"2  SELECT OPTION",font=font(12,True),fill=(166,246,255,255))
rr(d,(782,392,1238,576),16,(8,48,76,190),(126,230,252,155),2)
rr(d,(782,118,1238,356),16,(8,48,76,190),(126,230,252,155),2)
d.text((786,646),"SOUL DATA / LIVE PREVIEW",font=font(12,True),fill=(166,246,255,255))
d.text((786,668),"Selected choices glow bright cyan.",font=font(12),fill=(181,228,241,240))
d.text((786,686),"LOCK FORM stores the vessel for awakening.",font=font(12),fill=(181,228,241,240))
bg.save(A/"background.png")

# scanner overlay
S=720
scanner=Image.new("RGBA",(S,S),(0,0,0,0)); sd=ImageDraw.Draw(scanner,"RGBA")
for r,a,w in ((315,190,4),(273,145,3),(225,110,3),(180,88,3)):
    sd.ellipse((S/2-r,S/2-r,S/2+r,S/2+r),outline=(158,247,255,a),width=w)
for deg in range(0,360,30):
    a=math.radians(deg)
    sd.line((360+math.cos(a)*300,360+math.sin(a)*300,
             360+math.cos(a)*338,360+math.sin(a)*338),fill=(165,249,255,125),width=2)
scanner.save(A/"scanner.png")

# aura rings
aura=Image.new("RGBA",(800,800),(0,0,0,0)); ad=ImageDraw.Draw(aura,"RGBA")
for r,a,w in ((278,82,8),(314,48,6),(346,27,4)):
    ad.ellipse((400-r,400-r,400+r,400+r),outline=(88,232,255,a),width=w)
aura=aura.filter(ImageFilter.GaussianBlur(2.2)); aura.save(A/"aura.png")

# white cel-shaded body variants, tinted at runtime
BS=800
def bodymask(style):
    m=Image.new("L",(BS,BS),0); q=ImageDraw.Draw(m)
    if style=="round":
        q.ellipse((112,125,688,686),fill=255); q.rounded_rectangle((138,445,662,704),radius=135,fill=255)
    elif style=="drop":
        pts=[]
        for i in range(160):
            a=2*math.pi*i/160; x=math.cos(a); y=math.sin(a)
            rx=247*(0.73+0.27*((y+1)/2)); ry=297
            pts.append((400+x*rx,420+y*ry))
        q.polygon(pts,fill=255)
    elif style=="wide":
        q.ellipse((72,205,728,674),fill=255); q.rounded_rectangle((103,457,697,698),radius=124,fill=255)
    else:
        q.ellipse((104,160,696,686),fill=255)
        q.polygon([(286,228),(340,92),(400,230),(458,79),(518,235)],fill=255)
        q.rounded_rectangle((130,462,670,702),radius=128,fill=255)
    return m.filter(ImageFilter.GaussianBlur(1.0))

for style in ("round","drop","wide","crest"):
    m=bodymask(style)
    im=Image.new("RGBA",(BS,BS),(0,0,0,0)); q=ImageDraw.Draw(im,"RGBA")
    q.bitmap((0,0),m,fill=(235,246,253,255))
    # hard lower-right cel shadow
    sh=Image.new("RGBA",(BS,BS),(0,0,0,0)); shd=ImageDraw.Draw(sh,"RGBA")
    shd.ellipse((295,302,760,745),fill=(37,57,80,100))
    sh.putalpha(ImageChops.multiply(sh.getchannel("A"),m))
    im=Image.alpha_composite(im,sh)
    edge=ImageChops.subtract(m.filter(ImageFilter.MaxFilter(17)),m.filter(ImageFilter.MinFilter(13))).point(lambda z:int(z*0.55))
    rim=Image.new("RGBA",(BS,BS),(190,245,255,0)); rim.putalpha(edge)
    im=Image.alpha_composite(im,rim)
    im.save(A/f"body_{style}.png")

# common gloss
hl=Image.new("RGBA",(BS,BS),(0,0,0,0)); hd=ImageDraw.Draw(hl,"RGBA")
hd.ellipse((208,197,460,342),fill=(255,255,255,146))
hd.ellipse((175,274,282,337),fill=(255,255,255,82))
hd.ellipse((520,545,602,585),fill=(255,255,255,40))
hl=hl.filter(ImageFilter.GaussianBlur(5)); hl.save(A/"highlight.png")

# overlays
def star(cx,cy,r1,r2):
    out=[]
    for i in range(10):
        r=r1 if i%2==0 else r2; a=-math.pi/2+i*math.pi/5
        out.append((cx+math.cos(a)*r,cy+math.sin(a)*r))
    return out

for kind in ("calm","sharp","void","star"):
    im=Image.new("RGBA",(BS,BS),(0,0,0,0)); q=ImageDraw.Draw(im,"RGBA")
    pts=((318,382),(482,382))
    for idx,(x,y) in enumerate(pts):
        if kind=="calm":
            q.ellipse((x-43,y-58,x+43,y+58),fill=(4,11,25,248))
            q.ellipse((x-24,y-39,x+4,y-10),fill=(181,246,255,250))
            q.ellipse((x-30,y-46,x-14,y-30),fill=(255,255,255,250))
        elif kind=="sharp":
            poly=[(x-53,y-31),(x+46,y-47),(x+34,y+46),(x-41,y+42)] if idx==0 else [(x-46,y-47),(x+53,y-31),(x+41,y+42),(x-34,y+46)]
            q.polygon(poly,fill=(3,8,22,250)); q.ellipse((x-12,y-12,x+12,y+12),fill=(118,238,255,255))
        elif kind=="void":
            q.ellipse((x-49,y-49,x+49,y+49),fill=(4,4,13,252),outline=(143,73,255,240),width=7)
            q.ellipse((x-18,y-18,x+18,y+18),fill=(190,100,255,255)); q.ellipse((x-6,y-6,x+6,y+6),fill=(255,255,255,255))
        else:
            q.polygon(star(x,y,53,23),fill=(255,234,126,255),outline=(255,255,255,230))
    im.save(A/f"eyes_{kind}.png")

Image.new("RGBA",(BS,BS),(0,0,0,0)).save(A/"mark_none.png")
im=Image.new("RGBA",(BS,BS),(0,0,0,0)); q=ImageDraw.Draw(im,"RGBA")
q.arc((312,245,488,421),205,335,fill=(211,250,255,140),width=13); q.line((400,230,400,289),fill=(211,250,255,145),width=13); q.ellipse((390,205,410,225),fill=(211,250,255,165)); im.save(A/"mark_rune.png")
im=Image.new("RGBA",(BS,BS),(0,0,0,0)); q=ImageDraw.Draw(im,"RGBA"); random.seed(4)
for i in range(24):
    x=random.randint(238,562); y=random.randint(276,565); r=random.randint(3,9)
    q.ellipse((x-r,y-r,x+r,y+r),fill=(239,253,255,94))
im.save(A/"mark_speckles.png")
im=Image.new("RGBA",(BS,BS),(0,0,0,0)); q=ImageDraw.Draw(im,"RGBA")
q.arc((202,305,598,701),205,335,fill=(220,251,255,120),width=17); q.arc((241,344,559,662),205,335,fill=(88,207,249,90),width=9); im.save(A/"mark_crest.png")

for kind in ("soul","star","moon","none"):
    im=Image.new("RGBA",(BS,BS),(0,0,0,0)); cx,cy=400,518
    if kind!="none":
        gl=Image.new("RGBA",(BS,BS),(0,0,0,0)); gd=ImageDraw.Draw(gl,"RGBA")
        for r,a in ((100,22),(73,37),(48,60)): gd.ellipse((cx-r,cy-r,cx+r,cy+r),fill=(110,241,255,a))
        gl=gl.filter(ImageFilter.GaussianBlur(14)); im=Image.alpha_composite(im,gl); q=ImageDraw.Draw(im,"RGBA")
        if kind=="soul":
            q.ellipse((cx-39,cy-39,cx+39,cy+39),fill=(116,244,255,229),outline=(236,255,255,250),width=4); q.ellipse((cx-18,cy-24,cx+2,cy-4),fill=(255,255,255,235))
        elif kind=="star": q.polygon(star(cx,cy,45,20),fill=(255,220,80,240),outline=(255,251,215,255))
        else:
            q.ellipse((cx-42,cy-42,cx+42,cy+42),fill=(235,245,255,240)); q.ellipse((cx-12,cy-40,cx+57,cy+38),fill=(36,91,143,230))
    im.save(A/f"core_{kind}.png")

# Large direct-touch category and option buttons
for name,text in (
    ("cat_body","BODY"),("cat_color","COLOR"),("cat_eyes","EYES"),
    ("cat_mark","MARKINGS"),("cat_core","SOUL CORE"),
    ("cat_alpha","TRANSPARENCY"),("cat_aura","AURA")
):
    im=Image.new("RGBA",(206,44),(0,0,0,0)); q=ImageDraw.Draw(im,"RGBA")
    rr(q,(2,2,203,41),12,(8,34,59,244),(73,198,235,190),2)
    bb=q.textbbox((0,0),text,font=font(12,True)); tw=bb[2]-bb[0]; th=bb[3]-bb[1]
    q.text(((206-tw)/2,(44-th)/2-2),text,font=font(12,True),fill=(214,249,255,255))
    im.save(A/f"{name}.png")

for name,col in (("option_button",(8,39,68,246)),("option_selected",(14,85,116,250))):
    im=Image.new("RGBA",(206,58),(0,0,0,0)); q=ImageDraw.Draw(im,"RGBA")
    rr(q,(2,2,203,55),14,col,(86,226,255,220),2)
    im.save(A/f"{name}.png")

for name,text,col in (
    ("button_random","RANDOMIZE",(12,66,105,242)),
    ("button_save","SAVE PROFILE",(18,86,67,242)),
    ("button_lock","LOCK FORM",(79,33,119,245))
):
    im=Image.new("RGBA",(172,64),(0,0,0,0)); q=ImageDraw.Draw(im,"RGBA")
    rr(q,(3,3,168,60),17,col,(93,229,255,220),2)
    tw=q.textbbox((0,0),text,font=font(14,True)); w=tw[2]-tw[0]; h=tw[3]-tw[1]
    q.text(((172-w)/2,(64-h)/2-2),text,font=font(14,True),fill=(231,253,255,255))
    im.save(A/f"{name}.png")


# ---------- scene backdrops ----------
# Creator ambient background: visual atmosphere only. UI panels are now live GUI
# nodes so they stay aligned with the buttons on every aspect ratio.
ambient=Image.new("RGBA",(W,H),(5,27,45,255))
ap=ambient.load()
for y in range(H):
    for x in range(W):
        dx=(x-360)/530.0; dy=(y-360)/430.0
        rr0=(dx*dx+dy*dy)**0.5
        g=max(0.0,1.0-rr0)
        ap[x,y]=(int(5+46*g),int(27+132*g),int(45+164*g),255)
adraw=ImageDraw.Draw(ambient,"RGBA")
for r0,a0 in ((340,95),(292,112),(244,130),(198,148),(151,172),(110,205)):
    adraw.ellipse((360-r0,360-r0,360+r0,360+r0),outline=(165,248,255,a0),width=3)
for deg in range(0,360,12):
    a=math.radians(deg)
    adraw.line((360+math.cos(a)*120,360+math.sin(a)*120,
                360+math.cos(a)*338,360+math.sin(a)*338),
               fill=(126,238,255,65 if deg%24==0 else 34),width=2)
for x in range(12,W,54):
    adraw.line((x,0,x,H),fill=(76,198,231,28),width=1)
for y in range(12,H,42):
    adraw.line((0,y,W,y),fill=(76,198,231,24),width=1)
random.seed(221)
for i in range(180):
    x=random.randint(16,W-16); y=random.randint(16,H-16)
    sz=random.choice((2,3,4,5,7,9))
    alpha=random.randint(48,135)
    if i%4==0:
        adraw.rectangle((x,y,x+sz,y+sz),outline=(160,246,255,alpha),width=1)
    else:
        adraw.ellipse((x,y,x+sz,y+sz),fill=(140,242,255,alpha))
ambient=ambient.filter(ImageFilter.GaussianBlur(0.25))
ambient.save(A/"ambient.png")

# Modern-world death scene: rainy urban road, defocused lights, cinematic vignette.
death=Image.new("RGBA",(W,H),(6,10,18,255))
dd=ImageDraw.Draw(death,"RGBA")
# distant city blocks
for bx,bw,bh in ((20,170,330),(205,135,260),(355,210,370),(585,150,285),(750,210,390),(975,125,250),(1115,145,320)):
    dd.rectangle((bx,80,bx+bw,80+bh),fill=(12,22,36,255))
    for wx in range(bx+18,bx+bw-10,34):
        for wy in range(104,70+bh,42):
            if (wx+wy)//17%3==0:
                dd.rectangle((wx,wy,wx+9,wy+12),fill=(120,190,220,85))
# wet road
dd.polygon([(0,430),(1280,430),(1280,720),(0,720)],fill=(5,14,24,255))
for y in range(448,720,48):
    dd.line((0,y,1280,y),fill=(43,92,120,28),width=2)
# road perspective lines
dd.line((515,720,612,430),fill=(220,232,235,150),width=8)
dd.line((760,720,682,430),fill=(220,232,235,150),width=8)
# blurred headlight glows
glow=Image.new("RGBA",(W,H),(0,0,0,0)); gd=ImageDraw.Draw(glow,"RGBA")
for cx,cy,col in ((610,468,(255,238,184,165)),(698,468,(255,238,184,165)),(950,388,(90,196,255,105))):
    for r0,a0 in ((78,18),(52,35),(26,95)):
        gd.ellipse((cx-r0,cy-r0,cx+r0,cy+r0),fill=(*col[:3],min(a0,col[3])))
glow=glow.filter(ImageFilter.GaussianBlur(18))
death=Image.alpha_composite(death,glow)
dd=ImageDraw.Draw(death,"RGBA")
# rain
random.seed(88)
for i in range(280):
    x=random.randint(0,W); y=random.randint(0,H); ln=random.randint(10,28)
    dd.line((x,y,x-5,y+ln),fill=(155,213,235,random.randint(35,95)),width=1)
# cyan soul flare in the upper half, hinting at what's next
for r0,a0 in ((160,18),(110,32),(65,62)):
    dd.ellipse((640-r0,260-r0,640+r0,260+r0),outline=(110,233,255,a0),width=4)
death.save(A/"death_bg.png")

# Reincarnation / Sage void: bright white-cyan core in an endless analysis field.
rebirth=Image.new("RGBA",(W,H),(7,31,55,255))
rp=rebirth.load()
for y in range(H):
    for x in range(W):
        dx=(x-640)/620.0; dy=(y-340)/470.0
        rr0=(dx*dx+dy*dy)**0.5
        core=max(0.0,1.0-rr0)
        rp[x,y]=(int(7+92*core),int(31+178*core),int(55+196*core),255)
rd=ImageDraw.Draw(rebirth,"RGBA")
for r0,a0,w0 in ((315,62,4),(255,92,4),(198,130,5),(140,175,6),(84,220,7)):
    rd.ellipse((640-r0,330-r0,640+r0,330+r0),outline=(210,252,255,a0),width=w0)
rd.ellipse((575,265,705,395),fill=(238,255,255,74))
for deg in range(0,360,18):
    a=math.radians(deg)
    rd.line((640+math.cos(a)*95,330+math.sin(a)*95,
             640+math.cos(a)*310,330+math.sin(a)*310),
            fill=(175,247,255,72),width=2)
random.seed(19)
for i in range(125):
    x=random.randint(20,1260); y=random.randint(18,700); sz=random.choice((2,3,5,8))
    rd.ellipse((x,y,x+sz,y+sz),fill=(188,250,255,random.randint(45,130)))
rebirth.save(A/"reincarnation_bg.png")

# ---------- atlas ----------
images=[
"background","ambient","death_bg","reincarnation_bg","scanner","aura",
"body_round","body_drop","body_wide","body_crest","highlight",
"eyes_calm","eyes_sharp","eyes_void","eyes_star",
"mark_none","mark_rune","mark_speckles","mark_crest",
"core_soul","core_star","core_moon","core_none",
"cat_body","cat_color","cat_eyes","cat_mark","cat_core","cat_alpha","cat_aura",
"option_button","option_selected","button_random","button_save","button_lock"
]
atlas="".join(f'images {{\\n  image: "/assets/{n}.png"\\n}}\\n' for n in images)+"margin: 0\\nextrude_borders: 2\\ninner_padding: 0\\n"
(OUT/"main/creator.atlas").write_text(atlas)

def qstr(s):
    return s.replace("\\\\","\\\\\\\\").replace('"','\\\\"')

def sprite_comp(cid,anim,x,y,z,sx=1.0,sy=None):
    sy=sx if sy is None else sy
    lines=[
        f'default_animation: "{anim}"',
        'material: "/builtins/materials/sprite.material"',
        'textures {',
        '  sampler: "texture_sampler"',
        '  texture: "/main/creator.atlas"',
        '}'
    ]
    data='  data: "'+qstr(lines[0])+'\\\\n"\\n'
    for ln in lines[1:]:
        data+='  "'+qstr(ln)+'\\\\n"\\n'
    data+='  ""\\n'
    return f'''embedded_components {{
  id: "{cid}"
  type: "sprite"
{data}  position {{
    x: {x}
    y: {y}
    z: {z}
  }}
  scale {{
    x: {sx}
    y: {sy}
    z: 1.0
  }}
}}
'''

def label_comp(cid,text,x,y,z,scale=1.0,width=280,height=34):
    lines=[
        'size {',f'  x: {width}',f'  y: {height}','}',
        'pivot: PIVOT_CENTER',f'text: "{text}"',
        'font: "/builtins/fonts/default.font"',
        'material: "/builtins/fonts/label-df.material"'
    ]
    data='  data: "'+qstr(lines[0])+'\\\\n"\\n'
    for ln in lines[1:]:
        data+='  "'+qstr(ln)+'\\\\n"\\n'
    data+='  ""\\n'
    return f'''embedded_components {{
  id: "{cid}"
  type: "label"
{data}  position {{
    x: {x}
    y: {y}
    z: {z}
  }}
  scale {{
    x: {scale}
    y: {scale}
    z: 1.0
  }}
}}
'''

# ---------- game object ----------
go='''components {
  id: "main"
  component: "/main/main.script"
}
'''
go+=sprite_comp("background","background",640,360,-5,1.0)
go+=sprite_comp("scanner","scanner",385,360,-2,0.88)
go+=sprite_comp("aura","aura",385,360,-1.9,0.66)
for cid,anim,z in (("body","body_round",-1.5),("marking","mark_rune",-1.3),("core","core_soul",-1.2),("eyes","eyes_calm",-1.1),("highlight","highlight",-1.0)):
    go+=sprite_comp(cid,anim,385,360,z,0.70)

# The camera uses AUTO_FIT so controls are never cropped. Oversize only the
# background sprite to fill extra-wide phone displays.
go=go.replace('x: 1.0\\n    y: 1.0', 'x: 1.45\\n    y: 1.45', 1)

cats=[("body","cat_body",890,552),("color","cat_color",1112,552),
      ("eyes","cat_eyes",890,502),("mark","cat_mark",1112,502),
      ("core","cat_core",890,452),("alpha","cat_alpha",1112,452),
      ("aura_value","cat_aura",890,402)]
for key,anim,x,y in cats:
    go+=sprite_comp("cat_"+key,anim,x,y,0,1.0)

option_pos=[(890,326),(1112,326),(890,264),(1112,264),(890,202),(1112,202)]
for i,(x,y) in enumerate(option_pos,1):
    go+=sprite_comp("opt"+str(i),"option_button",x,y,0,1.0)
    go+=label_comp("opt_label"+str(i),"",x,y-4,0.3,0.90,190,34)

go+=sprite_comp("btn_random","button_random",842,66,0,0.92)
go+=sprite_comp("btn_save","button_save",1010,66,0,0.92)
go+=sprite_comp("btn_lock","button_lock",1178,66,0,0.92)
go+=label_comp("status","ANALYSIS ONLINE // Tap a parameter, then an option.",365,666,0.5,0.82,700,34)
(OUT/"main/creator.go").write_text(go)

# ---------- collection ----------
(OUT/"main/main.collection").write_text('''name: "main"
instances {
  id: "creator"
  prototype: "/main/creator.go"
}
scale_along_z: 0
embedded_instances {
  id: "camera"
  data: "embedded_components {\\n"
  "  id: \\"camera\\"\\n"
  "  type: \\"camera\\"\\n"
  "  data: \\"aspect_ratio: 1.7777778\\\\n"
  "fov: 0.7854\\\\n"
  "near_z: -10.0\\\\n"
  "far_z: 10.0\\\\n"
  "orthographic_projection: 1\\\\n"
  "orthographic_mode: ORTHO_MODE_AUTO_FIT\\\\n"
  "\\"\\n"
  "}\\n"
  ""
  position {
    x: 640.0
    y: 360.0
    z: 5.0
  }
}
''')

# ---------- behavior ----------
script=r'''local BODY = {"body_round","body_drop","body_wide","body_crest"}
local BODY_NAMES = {"ROUND CORE","DROPLET","WIDE FORM","CREST FORM"}
local COLORS = {
    vmath.vector4(0.14,0.72,1.00,1), vmath.vector4(0.58,0.34,1.00,1),
    vmath.vector4(1.00,0.36,0.22,1), vmath.vector4(0.25,0.84,0.52,1),
    vmath.vector4(0.88,0.94,1.00,1), vmath.vector4(0.18,0.15,0.30,1)
}
local COLOR_NAMES={"AZURE","AMETHYST","EMBER","JADE","PEARL","SHADOW"}
local EYES={"eyes_calm","eyes_sharp","eyes_void","eyes_star"}
local EYE_NAMES={"CALM","SHARP","VOID","STAR"}
local MARKS={"mark_none","mark_rune","mark_speckles","mark_crest"}
local MARK_NAMES={"NONE","RUNE","SPECKLES","CREST"}
local CORES={"core_soul","core_star","core_moon","core_none"}
local CORE_NAMES={"SOUL","STAR","MOON","NONE"}
local ALPHAS={0.55,0.68,0.82,0.92}
local ALPHA_NAMES={"55%","68%","82%","92%"}
local AURAS={0.20,0.42,0.62,0.82,1.00}
local AURA_NAMES={"20%","42%","62%","82%","100%"}
local CATS={"body","color","eyes","mark","core","alpha","aura_value"}
local CAT_POS={
    body={890,552},color={1112,552},eyes={890,502},mark={1112,502},
    core={890,452},alpha={1112,452},aura_value={890,402}
}
local OPT_POS={{890,326},{1112,326},{890,264},{1112,264},{890,202},{1112,202}}
local SAVE=sys.get_save_file("soul_slime_hd","creator")

local function set_status(v) label.set_text("#status",v) end

local function save_state(self)
    sys.save(SAVE,{body=self.body,color=self.color,eyes=self.eyes,mark=self.mark,
        core=self.core,alpha=self.alpha,aura=self.aura_value})
end

local function load_state(self)
    local t=sys.load(SAVE)
    self.body=t.body or 1; self.color=t.color or 1; self.eyes=t.eyes or 1
    self.mark=t.mark or 2; self.core=t.core or 1; self.alpha=t.alpha or 3
    self.aura_value=t.aura or 3; self.category="body"
end

local function category_data(self,key)
    if key=="body" then return BODY_NAMES,self.body
    elseif key=="color" then return COLOR_NAMES,self.color
    elseif key=="eyes" then return EYE_NAMES,self.eyes
    elseif key=="mark" then return MARK_NAMES,self.mark
    elseif key=="core" then return CORE_NAMES,self.core
    elseif key=="alpha" then return ALPHA_NAMES,self.alpha
    else return AURA_NAMES,self.aura_value end
end

local function choose(self,key,index)
    if key=="body" then self.body=index
    elseif key=="color" then self.color=index
    elseif key=="eyes" then self.eyes=index
    elseif key=="mark" then self.mark=index
    elseif key=="core" then self.core=index
    elseif key=="alpha" then self.alpha=index
    else self.aura_value=index end
end

local function refresh_options(self)
    local names,selected=category_data(self,self.category)
    for _,k in ipairs(CATS) do
        go.set("#cat_"..k,"tint",k==self.category and
            vmath.vector4(0.95,1.0,1.0,1.0) or vmath.vector4(0.68,0.82,0.90,0.74))
    end
    for i=1,6 do
        if i<=#names then
            go.set("#opt"..i,"tint",i==selected and
                vmath.vector4(0.92,1.0,1.0,1.0) or vmath.vector4(0.72,0.86,0.94,0.88))
            label.set_text("#opt_label"..i,names[i])
            go.set("#opt_label"..i,"color",i==selected and
                vmath.vector4(0.96,1.0,1.0,1.0) or vmath.vector4(0.80,0.94,0.98,1.0))
        else
            go.set("#opt"..i,"tint",vmath.vector4(1,1,1,0))
            label.set_text("#opt_label"..i,"")
        end
    end
end

local function refresh(self)
    sprite.play_flipbook("#body",hash(BODY[self.body]))
    sprite.play_flipbook("#eyes",hash(EYES[self.eyes]))
    sprite.play_flipbook("#marking",hash(MARKS[self.mark]))
    sprite.play_flipbook("#core",hash(CORES[self.core]))
    local c=COLORS[self.color]
    go.set("#body","tint",vmath.vector4(c.x,c.y,c.z,ALPHAS[self.alpha]))
    go.set("#aura","tint",vmath.vector4(c.x,c.y,c.z,AURAS[self.aura_value]))
    refresh_options(self)
end

function init(self)
    msg.post(".","acquire_input_focus")
    math.randomseed(os.time())
    load_state(self); refresh(self)
    go.set("#status","color",vmath.vector4(0.40,0.90,1.0,1.0))
    set_status("ANALYSIS ONLINE // Tap a parameter, then tap the exact option.")
end

function update(self,dt)
    self.t=(self.t or 0)+dt
    self.scene_elapsed=(self.scene_elapsed or 0)+dt
    local c=COLORS[self.color]
    local pulse=0.80+math.sin(self.t*2.5)*0.16
    go.set("#aura","tint",vmath.vector4(c.x,c.y,c.z,AURAS[self.aura_value]*pulse))
    local sx=0.70+math.sin(self.t*2.1)*0.010
    go.set_scale(vmath.vector3(sx,0.70+(0.70-sx)*0.25,1),"#body")
end

local function screen_inside(sx,sy,cx,cy,w,h)
    local cam="/camera#camera"
    local l=camera.world_to_screen(vmath.vector3(cx-w/2,cy,0),cam)
    local r=camera.world_to_screen(vmath.vector3(cx+w/2,cy,0),cam)
    local b=camera.world_to_screen(vmath.vector3(cx,cy-h/2,0),cam)
    local t=camera.world_to_screen(vmath.vector3(cx,cy+h/2,0),cam)
    local minx=math.min(l.x,r.x)
    local maxx=math.max(l.x,r.x)
    local miny=math.min(b.y,t.y)
    local maxy=math.max(b.y,t.y)
    return sx>=minx and sx<=maxx and sy>=miny and sy<=maxy
end

function on_input(self,action_id,action)
    if action_id~=hash("touch") or not action.pressed then return false end

    local sx,sy=action.screen_x,action.screen_y

    for _,k in ipairs(CATS) do
        local p=CAT_POS[k]
        if screen_inside(sx,sy,p[1],p[2],216,50) then
            self.category=k; refresh_options(self)
            set_status("PARAMETER SELECTED // "..string.upper(k):gsub("_"," "))
            return true
        end
    end

    local names=category_data(self,self.category)
    for i,p in ipairs(OPT_POS) do
        if i<=#names and screen_inside(sx,sy,p[1],p[2],216,62) then
            choose(self,self.category,i); refresh(self)
            set_status("SELECTED // "..names[i].." // "..string.upper(self.category):gsub("_"," "))
            return true
        end
    end

    if screen_inside(sx,sy,842,66,166,70) then
        self.body=math.random(#BODY); self.color=math.random(#COLORS); self.eyes=math.random(#EYES)
        self.mark=math.random(#MARKS); self.core=math.random(#CORES)
        self.alpha=math.random(#ALPHAS); self.aura_value=math.random(#AURAS)
        refresh(self); set_status("RANDOM SOUL DATA SYNTHESIZED // Candidate vessel generated."); return true
    elseif screen_inside(sx,sy,1010,66,166,70) then
        save_state(self); set_status("PROFILE SAVED // Vessel parameters stored locally."); return true
    elseif screen_inside(sx,sy,1178,66,166,70) then
        save_state(self); set_status("MATERIALIZATION CONFIRMED // Form locked for cave awakening."); return true
    end
    return false
end
'''
(OUT/"main/main.script").write_text(script)

# ---------- GUI opening flow + creator ----------
# Everything interactive is in one GUI coordinate system. The stretched scene
# backdrops contain no menu chrome; live panels and buttons stay aligned.

def gui_box(node_id, x, y, w, h, texture="", color=(1,1,1,1), adjust="ADJUST_MODE_FIT"):
    return f'''nodes {{
  position {{
    x: {x}
    y: {y}
    z: 0.0
    w: 1.0
  }}
  rotation {{
    x: 0.0
    y: 0.0
    z: 0.0
    w: 1.0
  }}
  scale {{
    x: 1.0
    y: 1.0
    z: 1.0
    w: 1.0
  }}
  size {{
    x: {w}
    y: {h}
    z: 0.0
    w: 1.0
  }}
  color {{
    x: {color[0]}
    y: {color[1]}
    z: {color[2]}
    w: {color[3]}
  }}
  type: TYPE_BOX
  blend_mode: BLEND_MODE_ALPHA
  texture: "{texture}"
  id: "{node_id}"
  xanchor: XANCHOR_NONE
  yanchor: YANCHOR_NONE
  pivot: PIVOT_CENTER
  adjust_mode: {adjust}
  layer: ""
  inherit_alpha: true
  slice9 {{
    x: 0.0
    y: 0.0
    z: 0.0
    w: 0.0
  }}
  clipping_mode: CLIPPING_MODE_NONE
  clipping_visible: true
  clipping_inverted: false
  alpha: 1.0
  template_node_child: false
  size_mode: SIZE_MODE_MANUAL
}}
'''

def gui_text(node_id, text, x, y, w, h, scale=1.0, color=(1,1,1,1), adjust="ADJUST_MODE_FIT"):
    safe=text.replace('\\\\','\\\\\\\\').replace('\\n','\\\\n').replace('"','\\"')
    return f'''nodes {{
  position {{
    x: {x}
    y: {y}
    z: 0.0
    w: 1.0
  }}
  rotation {{
    x: 0.0
    y: 0.0
    z: 0.0
    w: 1.0
  }}
  scale {{
    x: {scale}
    y: {scale}
    z: 1.0
    w: 1.0
  }}
  size {{
    x: {w}
    y: {h}
    z: 0.0
    w: 1.0
  }}
  color {{
    x: {color[0]}
    y: {color[1]}
    z: {color[2]}
    w: {color[3]}
  }}
  type: TYPE_TEXT
  blend_mode: BLEND_MODE_ALPHA
  text: "{safe}"
  font: "default"
  id: "{node_id}"
  xanchor: XANCHOR_NONE
  yanchor: YANCHOR_NONE
  pivot: PIVOT_CENTER
  outline {{
    x: 0.0
    y: 0.0
    z: 0.0
    w: 1.0
  }}
  shadow {{
    x: 0.0
    y: 0.0
    z: 0.0
    w: 1.0
  }}
  adjust_mode: {adjust}
  line_break: true
  layer: ""
  inherit_alpha: true
  alpha: 1.0
  outline_alpha: 0.0
  shadow_alpha: 0.0
  template_node_child: false
  text_leading: 1.0
  text_tracking: 0.0
}}
'''

gui='''script: "/main/creator.gui_script"
fonts {
  name: "default"
  font: "/builtins/fonts/default.font"
}
textures {
  name: "art"
  texture: "/main/creator.atlas"
}
background_color {
  x: 0.02
  y: 0.12
  z: 0.18
  w: 1.0
}
'''

# ----- Death scene -----
gui+=gui_box("death_bg",640,360,1280,720,"art/death_bg",(1,1,1,1),"ADJUST_MODE_STRETCH")
gui+=gui_box("death_card",640,170,970,235,"",(0.015,0.05,0.085,0.92),"ADJUST_MODE_FIT")
gui+=gui_text("death_kicker","FINAL MOMENTS",640,262,800,40,1.15,(0.72,0.94,1.0,1.0))
gui+=gui_text("death_title","A LIFE ENDS",640,222,850,54,1.55,(1.0,1.0,1.0,1.0))
gui+=gui_text("death_event","",640,160,890,70,1.0,(0.86,0.94,0.98,1.0))
gui+=gui_text("death_hint","You feel the world disappear. Something else is listening. Touch anywhere when you are ready.",640,154,900,70,0.82,(0.64,0.84,0.92,1.0))
gui+=gui_box("death_continue",640,96,420,82,"",(0.05,0.32,0.46,0.98))
gui+=gui_text("death_continue_label","TAP ANYWHERE TO CONTINUE",640,94,390,48,0.92,(0.94,1.0,1.0,1.0))

# ----- Reincarnation / Sage-like analysis void -----
gui+=gui_box("rebirth_bg",640,360,1280,720,"art/reincarnation_bg",(1,1,1,1),"ADJUST_MODE_STRETCH")
gui+=gui_box("rebirth_panel",640,360,1090,640,"",(0.02,0.11,0.18,0.62))
gui+=gui_text("rebirth_kicker","SOUL ANALYSIS // CONSCIOUSNESS DETECTED",640,655,1000,40,0.95,(0.73,0.96,1.0,1.0))
gui+=gui_text("rebirth_title","REINCARNATION PARAMETERS",640,610,1000,56,1.45,(1.0,1.0,1.0,1.0))
gui+=gui_text("rebirth_text","Your memories remain. Your former body does not. Select one starting ability before your new vessel is formed.",640,552,1000,70,0.86,(0.76,0.91,0.97,1.0))
skill_x=[260,640,1020]
for i,x in enumerate(skill_x,1):
    gui+=gui_box(f"skill{i}",x,350,320,170,"",(0.025,0.18,0.28,0.94))
    gui+=gui_text(f"skill_name{i}","",x,390,285,38,0.98,(0.92,1.0,1.0,1.0))
    gui+=gui_text(f"skill_desc{i}","",x,337,275,80,0.72,(0.68,0.86,0.93,1.0))
gui+=gui_text("skill_status","Choose one skill.",640,225,750,40,0.9,(0.67,0.92,1.0,1.0))
gui+=gui_box("rebirth_continue",640,145,260,60,"",(0.16,0.52,0.65,1.0))
gui+=gui_text("rebirth_continue_label","FORM NEW VESSEL",640,143,240,40,0.92,(0.96,1.0,1.0,1.0))

# ----- Creator -----
gui+=gui_box("creator_bg",640,360,1280,720,"art/ambient",(1,1,1,1),"ADJUST_MODE_STRETCH")
# Live UI chrome: these panels share the same FIT transform as the buttons.
gui+=gui_box("creator_left_panel",365,360,710,680,"",(0.015,0.10,0.16,0.35))
gui+=gui_box("creator_panel",1000,360,500,680,"",(0.015,0.09,0.15,0.88))
gui+=gui_box("creator_cat_panel",1000,470,460,205,"",(0.025,0.16,0.24,0.72))
gui+=gui_box("creator_opt_panel",1000,245,460,245,"",(0.025,0.16,0.24,0.72))
gui+=gui_box("creator_action_panel",1000,72,500,92,"",(0.025,0.12,0.20,0.78))
gui+=gui_text("creator_title","ANALYSIS SPACE // SOUL VESSEL",365,675,680,38,1.05,(0.90,1.0,1.0,1.0))
gui+=gui_text("creator_subtitle","Customize the vessel. Every visible button is its actual touch target.",365,646,680,34,0.72,(0.60,0.86,0.95,1.0))
gui+=gui_text("creator_cat_title","1  SELECT PARAMETER",1000,584,420,34,0.80,(0.72,0.96,1.0,1.0))
gui+=gui_text("creator_opt_title","2  SELECT OPTION",1000,366,420,34,0.80,(0.72,0.96,1.0,1.0))

# Slime preview.
gui+=gui_box("scanner",365,350,610,610,"art/scanner",(1,1,1,0.96))
gui+=gui_box("aura",365,350,550,550,"art/aura",(0.35,0.95,1.0,0.75))
gui+=gui_box("body",365,350,550,550,"art/body_round",(0.14,0.72,1.0,0.82))
gui+=gui_box("marking",365,350,550,550,"art/mark_rune")
gui+=gui_box("core",365,350,550,550,"art/core_soul")
gui+=gui_box("eyes",365,350,550,550,"art/eyes_calm")
gui+=gui_box("highlight",365,350,550,550,"art/highlight")

# Categories sit inside creator_cat_panel.
cat_defs=[
    ("cat_body","BODY",880,535),("cat_color","COLOR",1110,535),
    ("cat_eyes","EYES",880,485),("cat_mark","MARKINGS",1110,485),
    ("cat_core","SOUL CORE",880,435),("cat_alpha","TRANSPARENCY",1110,435),
    ("cat_aura_value","AURA",995,385)
]
for nid,label,x,y in cat_defs:
    gui+=gui_box(nid,x,y,202,42,"",(0.035,0.23,0.33,0.94))
    gui+=gui_text(nid+"_label",label,x,y-1,190,30,0.75,(0.90,0.99,1.0,1.0))

# Options sit inside creator_opt_panel.
opt_pos=[(880,315),(1110,315),(880,252),(1110,252),(880,189),(1110,189)]
for i,(x,y) in enumerate(opt_pos,1):
    gui+=gui_box(f"opt{i}",x,y,202,54,"",(0.03,0.20,0.30,0.96))
    gui+=gui_text(f"opt_label{i}","",x,y-1,188,34,0.80,(0.90,0.98,1.0,1.0))

# Actions live inside creator_action_panel.
for nid,label,x,col in (
    ("btn_random","RANDOMIZE",835,(0.04,0.31,0.45,1.0)),
    ("btn_save","SAVE PROFILE",1000,(0.06,0.38,0.30,1.0)),
    ("btn_lock","LOCK FORM",1165,(0.32,0.14,0.50,1.0))
):
    gui+=gui_box(nid,x,72,148,56,"",col)
    gui+=gui_text(nid+"_label",label,x,70,138,34,0.76,(0.97,1.0,1.0,1.0))
gui+=gui_text("status","",365,40,680,34,0.74,(0.70,0.95,1.0,1.0))

gui+='''material: "/builtins/materials/gui.material"
adjust_reference: ADJUST_REFERENCE_PARENT
max_nodes: 768
'''
(OUT/"main/creator.gui").write_text(gui)

gui_script=r'''local BODY = {"body_round","body_drop","body_wide","body_crest"}
local BODY_NAMES = {"ROUND CORE","DROPLET","WIDE FORM","CREST FORM"}
local COLORS = {
    vmath.vector4(0.14,0.72,1.00,1), vmath.vector4(0.58,0.34,1.00,1),
    vmath.vector4(1.00,0.36,0.22,1), vmath.vector4(0.25,0.84,0.52,1),
    vmath.vector4(0.88,0.94,1.00,1), vmath.vector4(0.18,0.15,0.30,1)
}
local COLOR_NAMES={"AZURE","AMETHYST","EMBER","JADE","PEARL","SHADOW"}
local EYES={"eyes_calm","eyes_sharp","eyes_void","eyes_star"}
local EYE_NAMES={"CALM","SHARP","VOID","STAR"}
local MARKS={"mark_none","mark_rune","mark_speckles","mark_crest"}
local MARK_NAMES={"NONE","RUNE","SPECKLES","CREST"}
local CORES={"core_soul","core_star","core_moon","core_none"}
local CORE_NAMES={"SOUL","STAR","MOON","NONE"}
local ALPHAS={0.55,0.68,0.82,0.92}
local ALPHA_NAMES={"55%","68%","82%","92%"}
local AURAS={0.20,0.42,0.62,0.82,1.00}
local AURA_NAMES={"20%","42%","62%","82%","100%"}
local CATS={"body","color","eyes","mark","core","alpha","aura_value"}
local CREATOR_SAVE=sys.get_save_file("soul_slime_hd","creator_gui_v05")
local RUN_SAVE=sys.get_save_file("soul_slime_hd","opening_v05")

local DEATHS={
    "A delivery van loses control on a rain-slick street. Headlights fill your vision.",
    "A transformer erupts during a storm. Blue-white light swallows the night.",
    "A construction scaffold gives way above a crowded sidewalk. There is no time to move.",
    "The last train screams into the station as the platform edge disappears beneath you."
}
local SKILLS={
    {name="MAGIC SENSE",desc="Detect mana, spells, living signatures, and hidden magical traces."},
    {name="ACCELERATED THOUGHT",desc="Process danger, analysis, and decisions at supernatural speed."},
    {name="REGENERATION",desc="Recover lost slime mass and repair your vessel over time."},
    {name="SPATIAL STORAGE",desc="Store absorbed matter inside a hidden personal space."},
    {name="HEAT RESISTANCE",desc="Reduce damage from fire, heat, and extreme temperature."},
    {name="NIGHT VISION",desc="See clearly in caves, darkness, and low-light environments."},
    {name="PREDATOR",desc="Analyze and absorb defeated creatures more efficiently."},
    {name="WATER AFFINITY",desc="Gain an early advantage with water magic and fluid control."}
}

local DEATH_NODES={
"death_bg","death_card","death_kicker","death_title","death_event","death_hint",
"death_continue","death_continue_label"
}
local REBIRTH_NODES={
"rebirth_bg","rebirth_panel","rebirth_kicker","rebirth_title","rebirth_text",
"skill1","skill_name1","skill_desc1","skill2","skill_name2","skill_desc2",
"skill3","skill_name3","skill_desc3","skill_status","rebirth_continue","rebirth_continue_label"
}
local CREATOR_NODES={
"creator_bg","creator_left_panel","creator_panel","creator_cat_panel","creator_opt_panel",
"creator_action_panel","creator_title","creator_subtitle","creator_cat_title","creator_opt_title",
"scanner","aura","body","marking","core","eyes","highlight",
"cat_body","cat_body_label","cat_color","cat_color_label","cat_eyes","cat_eyes_label",
"cat_mark","cat_mark_label","cat_core","cat_core_label","cat_alpha","cat_alpha_label",
"cat_aura_value","cat_aura_value_label",
"opt1","opt_label1","opt2","opt_label2","opt3","opt_label3",
"opt4","opt_label4","opt5","opt_label5","opt6","opt_label6",
"btn_random","btn_random_label","btn_save","btn_save_label","btn_lock","btn_lock_label","status"
}

local function enable_list(list,value)
    for _,id in ipairs(list) do gui.set_enabled(gui.get_node(id),value) end
end

local function show_scene(self,scene)
    self.scene=scene
    self.scene_elapsed=0
    enable_list(DEATH_NODES,scene=="death")
    enable_list(REBIRTH_NODES,scene=="rebirth")
    enable_list(CREATOR_NODES,scene=="creator")
    if scene=="rebirth" then
        gui.set_enabled(gui.get_node("rebirth_continue"),self.skill_selected~=nil)
        gui.set_enabled(gui.get_node("rebirth_continue_label"),self.skill_selected~=nil)
    end
end

local function set_status(v)
    gui.set_text(gui.get_node("status"),v)
end

local function load_creator(self)
    local t=sys.load(CREATOR_SAVE)
    self.body=t.body or 1
    self.color=t.color or 1
    self.eyes=t.eyes or 1
    self.mark=t.mark or 2
    self.core=t.core or 1
    self.alpha=t.alpha or 3
    self.aura_value=t.aura or 3
    self.category="body"
end

local function save_creator(self)
    sys.save(CREATOR_SAVE,{
        body=self.body,color=self.color,eyes=self.eyes,mark=self.mark,
        core=self.core,alpha=self.alpha,aura=self.aura_value
    })
end

local function data_for(self,key)
    if key=="body" then return BODY_NAMES,self.body
    elseif key=="color" then return COLOR_NAMES,self.color
    elseif key=="eyes" then return EYE_NAMES,self.eyes
    elseif key=="mark" then return MARK_NAMES,self.mark
    elseif key=="core" then return CORE_NAMES,self.core
    elseif key=="alpha" then return ALPHA_NAMES,self.alpha
    else return AURA_NAMES,self.aura_value end
end

local function select_value(self,key,index)
    if key=="body" then self.body=index
    elseif key=="color" then self.color=index
    elseif key=="eyes" then self.eyes=index
    elseif key=="mark" then self.mark=index
    elseif key=="core" then self.core=index
    elseif key=="alpha" then self.alpha=index
    else self.aura_value=index end
end

local function pulse_button(node)
    gui.cancel_animation(node,gui.PROP_SCALE)
    gui.set_scale(node,vmath.vector3(1,1,1))
    gui.animate(node,gui.PROP_SCALE,vmath.vector3(1.06,1.06,1),
        gui.EASING_OUTQUAD,0.08,0,function()
            gui.animate(node,gui.PROP_SCALE,vmath.vector3(1,1,1),
                gui.EASING_OUTBOUNCE,0.16)
        end)
end

local function bounce_slime()
    local ids={"aura","body","marking","core","eyes","highlight"}
    for _,id in ipairs(ids) do
        local n=gui.get_node(id)
        gui.cancel_animation(n,gui.PROP_SCALE)
        gui.set_scale(n,vmath.vector3(1,1,1))
        gui.animate(n,gui.PROP_SCALE,vmath.vector3(1.07,0.93,1),
            gui.EASING_OUTQUAD,0.10,0,function()
                gui.animate(n,gui.PROP_SCALE,vmath.vector3(1,1,1),
                    gui.EASING_OUTBOUNCE,0.20)
            end)
    end
end

local function refresh_options(self)
    local names,selected=data_for(self,self.category)
    for _,key in ipairs(CATS) do
        local box=gui.get_node("cat_"..key)
        local active=key==self.category
        gui.set_color(box,active and
            vmath.vector4(0.10,0.53,0.66,1.0) or vmath.vector4(0.035,0.23,0.33,0.94))
    end
    for i=1,6 do
        local box=gui.get_node("opt"..i)
        local label=gui.get_node("opt_label"..i)
        if i<=#names then
            gui.set_enabled(box,true); gui.set_enabled(label,true)
            gui.set_text(label,names[i])
            gui.set_color(box,i==selected and
                vmath.vector4(0.09,0.55,0.68,1.0) or vmath.vector4(0.03,0.20,0.30,0.96))
            gui.set_color(label,i==selected and
                vmath.vector4(1,1,1,1) or vmath.vector4(0.90,0.98,1.0,1.0))
        else
            gui.set_enabled(box,false); gui.set_enabled(label,false)
        end
    end
end

local function refresh_creator(self)
    gui.play_flipbook(gui.get_node("body"),BODY[self.body])
    gui.play_flipbook(gui.get_node("eyes"),EYES[self.eyes])
    gui.play_flipbook(gui.get_node("marking"),MARKS[self.mark])
    gui.play_flipbook(gui.get_node("core"),CORES[self.core])
    local c=COLORS[self.color]
    gui.set_color(gui.get_node("body"),vmath.vector4(c.x,c.y,c.z,ALPHAS[self.alpha]))
    gui.set_color(gui.get_node("aura"),vmath.vector4(c.x,c.y,c.z,AURAS[self.aura_value]))
    refresh_options(self)
end

local function shuffle_three(self)
    local pool={}
    for i=1,#SKILLS do pool[i]=i end
    for i=#pool,2,-1 do
        local j=math.random(i)
        pool[i],pool[j]=pool[j],pool[i]
    end
    self.skill_choices={pool[1],pool[2],pool[3]}
    self.skill_selected=nil
    for i=1,3 do
        local sk=SKILLS[self.skill_choices[i]]
        gui.set_text(gui.get_node("skill_name"..i),sk.name)
        gui.set_text(gui.get_node("skill_desc"..i),sk.desc)
        gui.set_color(gui.get_node("skill"..i),vmath.vector4(0.025,0.18,0.28,0.94))
    end
    gui.set_text(gui.get_node("skill_status"),"Choose one skill.")
    gui.set_enabled(gui.get_node("rebirth_continue"),false)
    gui.set_enabled(gui.get_node("rebirth_continue_label"),false)
end

function init(self)
    msg.post(".","acquire_input_focus")
    math.randomseed(os.time())
    load_creator(self)
    self.death_index=math.random(#DEATHS)
    gui.set_text(gui.get_node("death_event"),DEATHS[self.death_index])
    shuffle_three(self)
    refresh_creator(self)
    show_scene(self,"death")
end

function update(self,dt)
    self.t=(self.t or 0)+dt
    if self.scene=="creator" then
        local c=COLORS[self.color]
        local pulse=0.76+math.sin(self.t*2.5)*0.18
        gui.set_color(gui.get_node("aura"),
            vmath.vector4(c.x,c.y,c.z,AURAS[self.aura_value]*pulse))
    elseif self.scene=="rebirth" then
        local a=0.82+math.sin(self.t*1.8)*0.10
        gui.set_color(gui.get_node("rebirth_panel"),vmath.vector4(0.02,0.11,0.18,a))
    end
end

function on_input(self,action_id,action)
    if action_id~=hash("touch") or not action.pressed then return false end

    if self.scene=="death" then
        -- Deliberately bypass button hit-testing here. Any touch advances.
        -- This keeps the opening from ever becoming a dead-end on phones
        -- with unusual safe-area / gesture-navigation transforms.
        pulse_button(gui.get_node("death_continue"))
        show_scene(self,"rebirth")
        return true
    end

    if self.scene=="rebirth" then
        for i=1,3 do
            local node=gui.get_node("skill"..i)
            if gui.pick_node(node,action.x,action.y) then
                self.skill_selected=i
                for n=1,3 do
                    gui.set_color(gui.get_node("skill"..n),n==i and
                        vmath.vector4(0.07,0.52,0.66,1.0) or vmath.vector4(0.025,0.18,0.28,0.94))
                end
                local sk=SKILLS[self.skill_choices[i]]
                gui.set_text(gui.get_node("skill_status"),"SELECTED // "..sk.name)
                gui.set_enabled(gui.get_node("rebirth_continue"),true)
                gui.set_enabled(gui.get_node("rebirth_continue_label"),true)
                pulse_button(node)
                return true
            end
        end
        if self.skill_selected and gui.pick_node(gui.get_node("rebirth_continue"),action.x,action.y) then
            local chosen=SKILLS[self.skill_choices[self.skill_selected]]
            sys.save(RUN_SAVE,{death=self.death_index,skill=chosen.name})
            show_scene(self,"creator")
            set_status("STARTING SKILL // "..chosen.name)
            return true
        end
        return false
    end

    -- Creator
    for _,key in ipairs(CATS) do
        local node=gui.get_node("cat_"..key)
        if gui.pick_node(node,action.x,action.y) then
            self.category=key
            refresh_options(self)
            pulse_button(node)
            set_status("PARAMETER // "..string.upper(key):gsub("_"," "))
            return true
        end
    end

    local names=data_for(self,self.category)
    for i=1,6 do
        local node=gui.get_node("opt"..i)
        if i<=#names and gui.pick_node(node,action.x,action.y) then
            select_value(self,self.category,i)
            refresh_creator(self)
            pulse_button(node)
            bounce_slime()
            set_status("SELECTED // "..names[i])
            return true
        end
    end

    if gui.pick_node(gui.get_node("btn_random"),action.x,action.y) then
        self.body=math.random(#BODY); self.color=math.random(#COLORS); self.eyes=math.random(#EYES)
        self.mark=math.random(#MARKS); self.core=math.random(#CORES)
        self.alpha=math.random(#ALPHAS); self.aura_value=math.random(#AURAS)
        refresh_creator(self); bounce_slime(); pulse_button(gui.get_node("btn_random"))
        set_status("RANDOM SOUL FORM GENERATED.")
        return true
    elseif gui.pick_node(gui.get_node("btn_save"),action.x,action.y) then
        save_creator(self); pulse_button(gui.get_node("btn_save"))
        set_status("PROFILE SAVED.")
        return true
    elseif gui.pick_node(gui.get_node("btn_lock"),action.x,action.y) then
        save_creator(self); pulse_button(gui.get_node("btn_lock"))
        set_status("FORM LOCKED // NEXT BUILD: CAVE AWAKENING.")
        return true
    end
    return false
end
'''
(OUT/"main/creator.gui_script").write_text(gui_script)

(OUT/"main/main.go").write_text('''components {
  id: "gui"
  component: "/main/creator.gui"
}
''')
(OUT/"main/main.collection").write_text('''name: "main"
instances {
  id: "opening"
  prototype: "/main/main.go"
}
scale_along_z: 0
''')

(OUT/"README.md").write_text("""# Soul Slime Reincarnation Prototype v0.5

- Defold 1.13.1
- GUI-only rendering/input for exact button hitboxes
- Menu chrome is live GUI instead of baked into the stretched background
- Modern-world random death scene; any screen tap advances (no hitbox dependency)
- Reincarnation / analysis void
- Three randomized starting skill choices
- Polished slime creator with live-aligned panels and buttons
- Slime bounce reaction on creator changes
- Local creator + opening-state saves
""")

print("generated project",OUT)
print("textures",len(list(A.glob("*.png"))))
