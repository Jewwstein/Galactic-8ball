from pathlib import Path
import struct

ROOT = Path("app/src/main/assets")

def parse_obj(path: Path):
    verts=[]
    uvs=[]
    pos=[]
    tex=[]
    with path.open("r", errors="ignore") as f:
        for line in f:
            if line.startswith("v "):
                q=line.strip().split()
                if len(q)>=4:
                    verts.append((float(q[1]),float(q[2]),float(q[3])))
            elif line.startswith("vt "):
                q=line.strip().split()
                if len(q)>=3:
                    uvs.append((float(q[1]),float(q[2])))
            elif line.startswith("f "):
                q=line.strip().split()
                if len(q)<4:
                    continue
                for k in range(2,len(q)-1):
                    for tok in (q[1],q[k],q[k+1]):
                        parts=tok.split("/")
                        vi=int(parts[0]); vi=(len(verts)+vi) if vi<0 else (vi-1)
                        x,y,z=verts[vi]
                        u=v=0.0
                        if len(parts)>1 and parts[1]:
                            ti=int(parts[1]); ti=(len(uvs)+ti) if ti<0 else (ti-1)
                            if 0<=ti<len(uvs): u,v=uvs[ti]
                        pos.extend((x,y,z)); tex.extend((u,v))
    return pos,tex

count=0
bytes_in=bytes_out=0
for p in ROOT.rglob("*.obj"):
    pos,uv=parse_obj(p)
    if not pos:
        continue
    out=p.with_suffix(".meshbin")
    with out.open("wb") as f:
        f.write(b"GMH1")
        n=len(pos)//3
        f.write(struct.pack(">I",n))
        for i in range(n):
            f.write(struct.pack(">fffff",pos[i*3],pos[i*3+1],pos[i*3+2],uv[i*2],uv[i*2+1]))
    count+=1
    bytes_in+=p.stat().st_size
    bytes_out+=out.stat().st_size
    print("meshbin",p,"->",out,n,"verts")

print("converted",count,"OBJ files",bytes_in,"bytes ->",bytes_out,"bytes")
