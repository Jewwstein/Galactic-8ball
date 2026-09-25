from pathlib import Path
import UnityPy,re

root=Path("app/src/main/assets/fighter_bundles")
outroot=Path("app/src/main/assets/fighters");outroot.mkdir(parents=True,exist_ok=True)

def clean(x): return re.sub(r'[^A-Za-z0-9_.-]+','_',str(x))

def normalize_obj(src,dst):
    lines=src.read_text(errors="ignore").splitlines(); verts=[]
    for ln in lines:
        if ln.startswith("v "):
            q=ln.split(); verts.append([float(q[1]),float(q[2]),float(q[3])])
    if not verts: raise RuntimeError("no vertices "+str(src))
    mins=[min(v[i] for v in verts) for i in range(3)]
    maxs=[max(v[i] for v in verts) for i in range(3)]
    spans=[maxs[i]-mins[i] for i in range(3)]
    center=[(mins[i]+maxs[i])*.5 for i in range(3)]
    scale=max(max(spans),1e-6); vi=0; out=[]
    for ln in lines:
        if ln.startswith("v "):
            v=verts[vi];vi+=1
            out.append("v %.7f %.7f %.7f"%((v[0]-center[0])/scale,(v[1]-center[1])/scale,(v[2]-center[2])/scale))
        else:
            out.append(ln)
    dst.write_text("\n".join(out)+"\n")
    print(dst,"normalized spans",spans)

def obj_index(raw,count):
    i=int(raw)
    return (count+i) if i<0 else (i-1)

def merge_native_objs(srcs,dst):
    """Merge every authored mesh in the X-Wing bundle without normalizing it.

    The TTS X-Wing prefab is a multi-mesh model. Picking only the largest Mesh
    drops visible pieces. Keeping the original coordinates also lets Android use
    the exact 0.035 TTS object scale instead of guessing from normalized bounds.
    """
    verts=[]; uvs=[]; faces=[]
    for src in srcs:
        lv=[]; lt=[]; lf=[]
        for ln in src.read_text(errors="ignore").splitlines():
            if ln.startswith("v "):
                q=ln.split(); lv.append((float(q[1]),float(q[2]),float(q[3])))
            elif ln.startswith("vt "):
                q=ln.split(); lt.append((float(q[1]),float(q[2])))
            elif ln.startswith("f "):
                lf.append(ln.split()[1:])
        vb=len(verts); tb=len(uvs)
        verts.extend(lv); uvs.extend(lt)
        for face in lf:
            remap=[]
            for token in face:
                a=token.split("/")
                vi=obj_index(a[0],len(lv))+vb
                ti=None
                if len(a)>1 and a[1]:
                    ti=obj_index(a[1],len(lt))+tb
                remap.append((vi,ti))
            faces.append(remap)

    if not verts: raise RuntimeError("no vertices while merging "+str(dst))

    # Normalize the COMPLETE merged X-Wing as one object. This preserves every
    # extracted mesh while matching the same overall world size as the original
    # four-corner Android test (runtime scale 3.2).
    mins=[min(v[i] for v in verts) for i in range(3)]
    maxs=[max(v[i] for v in verts) for i in range(3)]
    spans=[maxs[i]-mins[i] for i in range(3)]
    center=[(mins[i]+maxs[i])*.5 for i in range(3)]
    scale=max(max(spans),1e-6)
    verts=[((v[0]-center[0])/scale,(v[1]-center[1])/scale,(v[2]-center[2])/scale) for v in verts]

    lines=[]
    for x,y,z in verts: lines.append(f"v {x:.7f} {y:.7f} {z:.7f}")
    for u,v in uvs: lines.append(f"vt {u:.7f} {v:.7f}")
    for face in faces:
        parts=[]
        for vi,ti in face:
            parts.append(f"{vi+1}/{ti+1}" if ti is not None else f"{vi+1}")
        lines.append("f "+" ".join(parts))
    dst.write_text("\n".join(lines)+"\n")
    print(dst,"FULL MERGED NORMALIZED MODEL meshes",len(srcs),"verts",len(verts),"source spans",spans)

for bundle in sorted(root.glob("*.unity3d")):
    name=bundle.stem; out=outroot/name; out.mkdir(parents=True,exist_ok=True)
    env=UnityPy.load(str(bundle)); meshes=[]; textures=[]
    for obj in env.objects:
        try: data=obj.read()
        except Exception: continue
        nm=getattr(data,"m_Name","") or f"{obj.type.name}_{obj.path_id}"
        if obj.type.name=="Mesh":
            try:
                p=out/f"raw_{obj.path_id}_{clean(nm)}.obj"
                p.write_text(data.export()); meshes.append(p)
            except Exception as e:
                print("MESHERR",name,obj.path_id,repr(e))
        elif obj.type.name=="Texture2D":
            try:
                p=out/f"{obj.path_id}_{clean(nm)}.png"
                data.image.save(p); textures.append(p)
            except Exception as e:
                print("TEXERR",name,obj.path_id,repr(e))

    if not meshes: raise RuntimeError(f"{name}: no Mesh in AssetBundle")

    if name.lower()=="xwing":
        # PapaX007 TTS model: preserve the complete multi-mesh authored model
        # and its original dimensions. Runtime applies the exact TTS scale 0.035.
        merge_native_objs(sorted(meshes,key=lambda p:p.name),out/"model.obj")
    else:
        # Existing TIE import is visually correct in Android; keep that baseline.
        chosen=max(meshes,key=lambda p:p.stat().st_size)
        normalize_obj(chosen,out/"model.obj")

    if textures:
        chosen_tex=max(textures,key=lambda p:p.stat().st_size)
        (out/"diffuse.png").write_bytes(chosen_tex.read_bytes())

    print(name,"source meshes",len(meshes),"textures",len(textures))
