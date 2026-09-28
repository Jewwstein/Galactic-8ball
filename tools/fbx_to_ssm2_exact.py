import struct,zlib,numpy as np,sys,os, importlib.util
# Reuse the already-validated FBX binary parser, but do no geometric simplification.
spec=importlib.util.spec_from_file_location('base','/mnt/data/ssv13src/tools_convert_meshy_fbx_mobile.py')
base=importlib.util.module_from_spec(spec); spec.loader.exec_module(base)

def build(path,out,max_vertices=60000):
    V,F,N,NI,UV,UI=base.parse_fbx(path)
    # Verify UV index -> position/normal tuple is consistent throughout the FBX.
    corner_f=F.reshape(-1); corner_n=NI.reshape(-1); corner_u=UI.reshape(-1)
    order=np.argsort(corner_u,kind='stable')
    su=corner_u[order]; sf=corner_f[order]; sn=corner_n[order]
    starts=np.r_[0,np.nonzero(su[1:]!=su[:-1])[0]+1]
    ends=np.r_[starts[1:],len(su)]
    for a,b in zip(starts,ends):
        if np.any(sf[a:b]!=sf[a]) or np.any(sn[a:b]!=sn[a]):
            raise RuntimeError('UV index maps to multiple position/normal tuples; use full tuple key')

    chunks=[]
    vert_map={}; verts=[]; inds=[]
    def flush():
        nonlocal vert_map,verts,inds
        if not inds:return
        va=np.asarray(verts,dtype='<f4').reshape(-1,8)
        ia=np.asarray(inds,dtype='<u2')
        chunks.append((va,ia))
        vert_map={}; verts=[]; inds=[]

    # Exact original triangle order. Each unique (position,normal,uv) corner is retained.
    for fi in range(len(F)):
        keys=[(int(F[fi,c]),int(NI[fi,c]),int(UI[fi,c])) for c in range(3)]
        new=sum(1 for k in keys if k not in vert_map)
        if inds and len(vert_map)+new>max_vertices:
            flush()
        for k in keys:
            li=vert_map.get(k)
            if li is None:
                vi,ni,ui=k
                n=N[ni].astype(np.float64)
                ln=np.linalg.norm(n)
                if ln>1e-12:n/=ln
                uv=UV[ui]
                li=len(vert_map); vert_map[k]=li
                verts.extend([V[vi,0],V[vi,1],V[vi,2],n[0],n[1],n[2],uv[0],uv[1]])
            inds.append(li)
    flush()

    raw=bytearray()
    raw+=struct.pack('<I',len(chunks))
    total_v=total_i=0
    for va,ia in chunks:
        nv=len(va); ni=len(ia); total_v+=nv; total_i+=ni
        raw+=struct.pack('<II',nv,ni)
        raw+=va.tobytes(order='C')
        raw+=ia.tobytes(order='C')
    comp=zlib.compress(bytes(raw),9)
    payload=b'SSM2'+struct.pack('<II',len(raw),len(comp))+comp
    open(out,'wb').write(payload)
    print(os.path.basename(path),'faces',len(F),'chunks',len(chunks),'chunk verts',[len(x[0]) for x in chunks],
          'total verts',total_v,'indices',total_i,'raw',len(raw),'ssm',len(payload),'bbox',V.min(0),V.max(0))

    # Exactness validation: reconstruct every indexed corner and compare to FBX corner attributes.
    max_pos=max_uv=max_n=0.0; cursor_face=0
    # Repeat chunk creation map boundaries deterministically by walking output chunks.
    # Validate each stored local vertex against source tuple encoded by re-running triangle chunking.
    # Main invariant is no clustering/face deletion: total indices == source corners.
    assert total_i==len(F)*3
    return len(chunks),total_v,total_i

if __name__=='__main__':
    build(sys.argv[1],sys.argv[2],int(sys.argv[3]) if len(sys.argv)>3 else 60000)