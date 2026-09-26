from pathlib import Path
import math, struct
import numpy as np
from PIL import Image, ImageDraw, ImageEnhance

ROOT=Path("app/src/main/assets")
REAL=ROOT/"real_hilts"
UI=ROOT/"ui"
UI.mkdir(parents=True,exist_ok=True)

W,H=1024,2048

def read_mesh(path):
    data=path.read_bytes()
    if data[:4]!=b"GMH1":
        raise RuntimeError(f"bad mesh {path}")
    n=struct.unpack(">I",data[4:8])[0]
    arr=np.frombuffer(data,dtype=">f4",offset=8).reshape((n,5)).astype(np.float32)
    return arr[:,:3],arr[:,3:5]

def rotate(p):
    # HD portrait menu render. Models are authored lengthwise on +X; rotate the
    # geometry into portrait here instead of rotating a small landscape bitmap in Android.
    yaw=math.radians(18)
    pitch=math.radians(-7)
    cy,sy=math.cos(yaw),math.sin(yaw)
    cp,sp=math.cos(pitch),math.sin(pitch)
    Ry=np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]],dtype=np.float32)
    Rx=np.array([[1,0,0],[0,cp,-sp],[0,sp,cp]],dtype=np.float32)
    return p@Ry.T@Rx.T

def render(mesh_path,tex_path,out_path):
    pos,uv=read_mesh(mesh_path)
    p=rotate(pos)
    # Project authored X length vertically and Y thickness horizontally.
    mn=p.min(axis=0);mx=p.max(axis=0);ctr=(mn+mx)*.5
    p-=ctr

    sx=(W-120)/max(1e-5,mx[1]-mn[1])
    sy=(H-150)/max(1e-5,mx[0]-mn[0])
    scale=min(sx,sy)
    xy=np.empty((len(p),2),dtype=np.float32)
    xy[:,0]=p[:,1]*scale+W*.5
    xy[:,1]=-p[:,0]*scale+H*.5

    tex=np.asarray(Image.open(tex_path).convert("RGBA"))
    th,tw=tex.shape[:2]
    tris=[]
    light=np.array([.35,.65,.68],dtype=np.float32)
    light/=np.linalg.norm(light)

    for i in range(0,len(p)-2,3):
        a,b,c=p[i],p[i+1],p[i+2]
        normal=np.cross(b-a,c-a)
        nl=float(np.linalg.norm(normal))
        if nl<1e-8:
            continue
        normal/=nl
        shade=.42+.58*abs(float(np.dot(normal,light)))
        uc=float((uv[i,0]+uv[i+1,0]+uv[i+2,0])/3.0)%1.0
        vc=float((uv[i,1]+uv[i+1,1]+uv[i+2,1])/3.0)%1.0
        tx=min(tw-1,max(0,int(uc*(tw-1))))
        ty=min(th-1,max(0,int((1.0-vc)*(th-1))))
        col=tex[ty,tx].astype(np.float32)
        rgb=np.clip(col[:3]*shade,0,255).astype(np.uint8)
        alpha=int(col[3])
        poly=[tuple(xy[i]),tuple(xy[i+1]),tuple(xy[i+2])]
        depth=float((p[i,2]+p[i+1,2]+p[i+2,2])/3.0)
        tris.append((depth,poly,(int(rgb[0]),int(rgb[1]),int(rgb[2]),alpha)))

    # Back-to-front painter's algorithm is enough for the small menu preview.
    tris.sort(key=lambda q:q[0])
    canvas=Image.new("RGBA",(W,H),(0,0,0,0))
    draw=ImageDraw.Draw(canvas,"RGBA")
    for _,poly,color in tris:
        draw.polygon(poly,fill=color)

    # Add a very subtle silhouette shadow so silver hilts stay legible on the dark menu.
    alpha=canvas.getchannel("A")
    shadow=Image.new("RGBA",(W,H),(0,0,0,0))
    shadow.putalpha(alpha.point(lambda a:int(a*.42)))
    final=Image.new("RGBA",(W,H),(0,0,0,0))
    final.alpha_composite(shadow,(3,4))
    final.alpha_composite(canvas)
    final=ImageEnhance.Sharpness(final).enhance(1.35)
    final.save(out_path,optimize=True)
    print("thumbnail",out_path)

for i in range(6):
    render(REAL/f"hilt_{i}.meshbin",REAL/f"hilt_{i}.webp",UI/f"hilt_thumb_{i}.png")
