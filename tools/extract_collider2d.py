from pathlib import Path
import math

src=Path("app/src/main/assets/extracted/7803042505894671335_default.obj")
out=Path("app/src/main/assets/extracted/table_collider_2d.txt")
Y=1.19  # TTS table local height at ball center: table posY 1.0, ball center 2.19
verts=[]
tris=[]

with src.open("r",errors="ignore") as f:
    for line in f:
        if line.startswith("v "):
            q=line.split()
            verts.append((float(q[1]),float(q[2]),float(q[3])))
        elif line.startswith("f "):
            q=line.split()[1:]
            ids=[]
            for tok in q:
                i=int(tok.split("/")[0])
                ids.append((len(verts)+i) if i<0 else i-1)
            for k in range(1,len(ids)-1):
                tris.append((ids[0],ids[k],ids[k+1]))

def intersect(a,b):
    x1,y1,z1=verts[a];x2,y2,z2=verts[b]
    d1=y1-Y;d2=y2-Y
    eps=1e-6
    if abs(d1)<eps and abs(d2)<eps:
        return None
    if d1*d2>0:return None
    if abs(y2-y1)<eps:return None
    t=(Y-y1)/(y2-y1)
    if t<-eps or t>1+eps:return None
    return (x1+(x2-x1)*t,z1+(z2-z1)*t)

raw=[]
for tri in tris:
    pts=[]
    for a,b in ((tri[0],tri[1]),(tri[1],tri[2]),(tri[2],tri[0])):
        p=intersect(a,b)
        if p is not None and all((p[0]-q[0])**2+(p[1]-q[1])**2>1e-10 for q in pts):
            pts.append(p)
    if len(pts)>=2:
        a,b=pts[0],pts[1]
        if math.dist(a,b)>.01:
            raw.append((a,b))

# Deduplicate exact triangle-shared cuts while preserving actual cushion/jaw geometry.
seen=set();segments=[]
for a,b in raw:
    qa=(round(a[0],4),round(a[1],4));qb=(round(b[0],4),round(b[1],4))
    key=tuple(sorted((qa,qb)))
    if key in seen:continue
    seen.add(key)
    # Keep the actual table body collision cross-section, but reject remote decorative geometry.
    if max(abs(a[0]),abs(b[0]))>46.0 or max(abs(a[1]),abs(b[1]))>25.2:continue
    segments.append((a,b))

with out.open("w") as f:
    f.write("# x1 z1 x2 z2 -- direct cross-section of Unity MeshCollider 7803042505894671335 at local y=1.19\n")
    for (x1,z1),(x2,z2) in segments:
        f.write(f"{x1:.6f} {z1:.6f} {x2:.6f} {z2:.6f}\n")

print("wrote",len(segments),"Unity MeshCollider cross-section edges to",out)
