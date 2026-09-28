package com.soulslime.nativeapp;

import android.content.Context;
import android.opengl.GLES20;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.zip.InflaterInputStream;

/**
 * Mobile creature mesh container.
 *
 * SSM1 is the older single-chunk format. SSM2 stores the exact Meshy topology in
 * several <= 60k-vertex chunks so OpenGL ES 2 can draw every original triangle
 * with 16-bit indices. No vertex clustering or face deletion is performed.
 */
public final class CreatureMesh {
    private static final int STRIDE_FLOATS=8;

    private static final class Chunk {
        final FloatBuffer verts;
        final ShortBuffer indices;
        final int indexCount;

        Chunk(float[] v,short[] i){
            ByteBuffer vb=ByteBuffer.allocateDirect(v.length*4).order(ByteOrder.nativeOrder());
            verts=vb.asFloatBuffer();
            verts.put(v).position(0);

            ByteBuffer ib=ByteBuffer.allocateDirect(i.length*2).order(ByteOrder.nativeOrder());
            indices=ib.asShortBuffer();
            indices.put(i).position(0);
            indexCount=i.length;
        }

        void draw(int aPos,int aNormal,int aUv){
            final int stride=STRIDE_FLOATS*4;
            verts.position(0);
            GLES20.glVertexAttribPointer(aPos,3,GLES20.GL_FLOAT,false,stride,verts);
            GLES20.glEnableVertexAttribArray(aPos);

            verts.position(3);
            GLES20.glVertexAttribPointer(aNormal,3,GLES20.GL_FLOAT,false,stride,verts);
            GLES20.glEnableVertexAttribArray(aNormal);

            verts.position(6);
            GLES20.glVertexAttribPointer(aUv,2,GLES20.GL_FLOAT,false,stride,verts);
            GLES20.glEnableVertexAttribArray(aUv);

            indices.position(0);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES,indexCount,GLES20.GL_UNSIGNED_SHORT,indices);
        }
    }

    private final Chunk[] chunks;

    private CreatureMesh(Chunk[] chunks){ this.chunks=chunks; }

    public void draw(int aPos,int aNormal,int aUv){
        for(Chunk chunk:chunks)chunk.draw(aPos,aNormal,aUv);
        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aNormal);
        GLES20.glDisableVertexAttribArray(aUv);
    }

    public static CreatureMesh loadSSM(Context c,String asset) throws IOException {
        byte[] all=readAll(c.getAssets().open(asset));
        if(all.length<12 || all[0]!='S' || all[1]!='S' || all[2]!='M')
            throw new IOException("Bad SSM: "+asset);

        int version=all[3]-'0';
        if(version!=1 && version!=2)throw new IOException("Unsupported SSM version: "+asset);

        ByteBuffer h=ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
        h.position(4);
        int rawLen=h.getInt();
        int compLen=h.getInt();
        if(rawLen<1 || compLen<1 || 12+compLen>all.length)
            throw new IOException("Bad SSM length: "+asset);

        InflaterInputStream in=new InflaterInputStream(new ByteArrayInputStream(all,12,compLen));
        byte[] raw=new byte[rawLen];
        int p=0,n;
        while(p<rawLen && (n=in.read(raw,p,rawLen-p))>0)p+=n;
        in.close();
        if(p!=rawLen)throw new IOException("Short SSM inflate: "+asset);

        ByteBuffer b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        if(version==2)return parseSSM2(b,asset);
        return parseSSM1(b,asset);
    }

    private static CreatureMesh parseSSM2(ByteBuffer b,String asset)throws IOException{
        if(b.remaining()<4)throw new IOException("Short SSM2: "+asset);
        int chunkCount=b.getInt();
        if(chunkCount<1 || chunkCount>64)throw new IOException("Bad SSM2 chunk count: "+asset);
        Chunk[] chunks=new Chunk[chunkCount];
        for(int ci=0;ci<chunkCount;ci++){
            if(b.remaining()<8)throw new IOException("Short SSM2 chunk header: "+asset);
            int nv=b.getInt(), ni=b.getInt();
            if(nv<3 || nv>65535 || ni<3 || ni%3!=0)
                throw new IOException("Bad SSM2 chunk geometry: "+asset);
            long need=(long)nv*STRIDE_FLOATS*4L+(long)ni*2L;
            if(need>b.remaining())throw new IOException("Short SSM2 chunk data: "+asset);

            float[] v=new float[nv*STRIDE_FLOATS];
            for(int k=0;k<v.length;k++)v[k]=b.getFloat();
            short[] idx=new short[ni];
            for(int k=0;k<ni;k++)idx[k]=b.getShort();
            chunks[ci]=new Chunk(v,idx);
        }
        return new CreatureMesh(chunks);
    }

    private static CreatureMesh parseSSM1(ByteBuffer b,String asset)throws IOException{
        if(b.remaining()<8)throw new IOException("Short SSM1: "+asset);
        int nv=b.getInt(), nf=b.getInt();
        if(nv<3 || nv>65535 || nf<1)throw new IOException("Bad SSM1 geometry: "+asset);
        float[] v=new float[nv*STRIDE_FLOATS];
        for(int k=0;k<v.length;k++)v[k]=b.getFloat();
        short[] idx=new short[nf*3];
        for(int k=0;k<idx.length;k++){
            long q=((long)b.getInt())&0xffffffffL;
            if(q>65535)throw new IOException("Index overflow in "+asset);
            idx[k]=(short)q;
        }
        return new CreatureMesh(new Chunk[]{new Chunk(v,idx)});
    }

    private static byte[] readAll(InputStream in)throws IOException{
        ByteArrayOutputStream o=new ByteArrayOutputStream();
        byte[] buf=new byte[32768];
        int n;
        while((n=in.read(buf))>0)o.write(buf,0,n);
        in.close();
        return o.toByteArray();
    }
}