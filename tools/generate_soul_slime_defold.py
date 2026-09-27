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
bundle_identifier = com.soulslime.defoldcreator

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
package = com.soulslime.defoldcreator
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

# Full-screen anime analysis chamber.
bg=Image.new("RGBA",(W,H),(3,8,20,255))
p=bg.load()
for y in range(H):
    for x in range(W):
        d=((x-385)**2+(y-360)**2)**0.5
        g=max(0.0,1.0-d/620.0)
        p[x,y]=(int(3+4*g),int(8+29*g),int(20+49*g),255)
d=ImageDraw.Draw(bg,"RGBA")
# perspective-ish grid
for x in range(0,W,64): d.line((x,0,x,H),fill=(58,165,215,15),width=1)
for y in range(0,H,48): d.line((0,y,W,y),fill=(58,165,215,13),width=1)
# scanner chamber
for i,r in enumerate((118,154,194,240,288,332)):
    col=(90,232,255,max(17,74-i*8))
    for seg in range(5):
        s=(seg*72+i*11)%360
        d.arc((385-r,360-r,385+r,360+r),s,s+37+(seg%3)*7,fill=col,width=2+(i%2))
for deg in range(0,360,15):
    a=math.radians(deg)
    d.line((385+math.cos(a)*108,360+math.sin(a)*108,
            385+math.cos(a)*326,360+math.sin(a)*326),fill=(95,220,255,15),width=1)
# particles / data squares
random.seed(913)
for i in range(95):
    x=random.randint(26,815); y=random.randint(24,696); s=random.choice((2,3,4,6,8))
    alpha=random.randint(18,75)
    if i%5==0: d.rectangle((x,y,x+s,y+s),outline=(90,230,255,alpha),width=1)
    else: d.ellipse((x,y,x+s,y+s),fill=(90,230,255,alpha))
# panels
rr(d,(18,18,818,702),26,(5,20,39,78),(69,210,247,72),2)
rr(d,(758,18,1262,702),26,(4,15,34,238),(72,215,250,124),2)
d.line((758,101,1262,101),fill=(86,225,255,90),width=1)
d.line((758,614,1262,614),fill=(86,225,255,75),width=1)
# top UI
d.text((42,36),"ANALYSIS ACTIVE",font=font(30,True),fill=(225,250,255,255))
d.text((42,73),"INITIAL VESSEL CONFIGURATION  //  SOUL SLIME",font=font(15,True),fill=(86,219,255,220))
d.text((786,38),"SYSTEM / MATERIALIZATION",font=font(16,True),fill=(181,243,255,245))
d.text((786,67),"Tap a category, then tap an option",font=font(13),fill=(108,173,205,220))
# direct-button selector areas
d.text((786,590),"1  SELECT PARAMETER",font=font(12,True),fill=(78,215,249,220))
d.text((786,374),"2  SELECT OPTION",font=font(12,True),fill=(78,215,249,220))
rr(d,(782,392,1238,576),16,(6,24,45,180),(48,142,188,100),1)
rr(d,(782,118,1238,356),16,(6,24,45,180),(48,142,188,100),1)
d.text((786,646),"SOUL DATA / LIVE PREVIEW",font=font(12,True),fill=(78,215,249,210))
d.text((786,668),"Large direct-touch controls active.",font=font(12),fill=(133,177,201,215))
d.text((786,686),"LOCK FORM stores the vessel for awakening.",font=font(12),fill=(133,177,201,215))
bg.save(A/"background.png")

# scanner overlay
S=720
scanner=Image.new("RGBA",(S,S),(0,0,0,0)); sd=ImageDraw.Draw(scanner,"RGBA")
for r,a,w in ((315,92,3),(273,60,2),(225,45,2),(180,35,2)):
    sd.ellipse((S/2-r,S/2-r,S/2+r,S/2+r),outline=(92,230,255,a),width=w)
for deg in range(0,360,30):
    a=math.radians(deg)
    sd.line((360+math.cos(a)*300,360+math.sin(a)*300,
             360+math.cos(a)*338,360+math.sin(a)*338),fill=(110,238,255,80),width=2)
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

# ---------- atlas ----------
images=[
"background","scanner","aura",
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
            vmath.vector4(0.55,1.0,1.0,1.0) or vmath.vector4(0.72,0.82,0.88,0.82))
    end
    for i=1,6 do
        if i<=#names then
            go.set("#opt"..i,"tint",i==selected and
                vmath.vector4(0.64,1.0,1.0,1.0) or vmath.vector4(1,1,1,0.94))
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
    local c=COLORS[self.color]
    local pulse=0.80+math.sin(self.t*2.5)*0.16
    go.set("#aura","tint",vmath.vector4(c.x,c.y,c.z,AURAS[self.aura_value]*pulse))
    local sx=0.70+math.sin(self.t*2.1)*0.010
    go.set_scale(vmath.vector3(sx,0.70+(0.70-sx)*0.25,1),"#body")
end

local function inside(x,y,cx,cy,w,h)
    return x>=cx-w/2 and x<=cx+w/2 and y>=cy-h/2 and y<=cy+h/2
end

function on_input(self,action_id,action)
    if action_id~=hash("touch") or not action.pressed then return false end

    local world=camera.screen_xy_to_world(action.screen_x,action.screen_y,"/camera#camera")
    local x,y=world.x,world.y

    for _,k in ipairs(CATS) do
        local p=CAT_POS[k]
        if inside(x,y,p[1],p[2],216,50) then
            self.category=k; refresh_options(self)
            set_status("PARAMETER SELECTED // "..string.upper(k):gsub("_"," "))
            return true
        end
    end

    local names=category_data(self,self.category)
    for i,p in ipairs(OPT_POS) do
        if i<=#names and inside(x,y,p[1],p[2],216,62) then
            choose(self,self.category,i); refresh(self)
            set_status("ANALYSIS UPDATED // "..names[i].." applied.")
            return true
        end
    end

    if inside(x,y,842,66,166,70) then
        self.body=math.random(#BODY); self.color=math.random(#COLORS); self.eyes=math.random(#EYES)
        self.mark=math.random(#MARKS); self.core=math.random(#CORES)
        self.alpha=math.random(#ALPHAS); self.aura_value=math.random(#AURAS)
        refresh(self); set_status("RANDOM SOUL DATA SYNTHESIZED // Candidate vessel generated."); return true
    elseif inside(x,y,1010,66,166,70) then
        save_state(self); set_status("PROFILE SAVED // Vessel parameters stored locally."); return true
    elseif inside(x,y,1178,66,166,70) then
        save_state(self); set_status("MATERIALIZATION CONFIRMED // Form locked for cave awakening."); return true
    end
    return false
end
'''
(OUT/"main/main.script").write_text(script)
(OUT/"README.md").write_text("""# Soul Slime HD Creator — Defold

Engine-switch prototype.
- Defold 1.13.1
- Android landscape / immersive fullscreen with Auto Fit camera
- Original layered anime-inspired slime textures
- Original cyan analysis-chamber UI using broad visual cues from fantasy-anime analysis sequences
- Large direct-touch category and option buttons
- Local save + lock form
""")
print("generated project",OUT)
print("textures",len(list(A.glob("*.png"))))
