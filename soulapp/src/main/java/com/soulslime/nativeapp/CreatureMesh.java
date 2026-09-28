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
 * Small mobile mesh container used by the painted-scene 3D creature overlay.
 * SSM1 stores interleaved position/normal/uv data plus 16-bit indexable faces.
 */
public final class CreatureMesh {
    public final FloatBuffer verts;
    public final ShortBuffer indices;
    public final int indexCount;

    public CreatureMesh(float[] v, short[] i) {
        ByteBuffer vb=ByteBuffer.allocateDirect(v.length*4).order(ByteOrder.nativeOrder());
        verts=vb.asFloatBuffer();
        verts.put(v).position(0);

        ByteBuffer ib=ByteBuffer.allocateDirect(i.length*2).order(ByteOrder.nativeOrder());
        indices=ib.asShortBuffer();
        indices.put(i).position(0);
        indexCount=i.length;
    }

    public void draw(int aPos,int aNormal,int aUv){
        final int stride=8*4;
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

        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aNormal);
        GLES20.glDisableVertexAttribArray(aUv);
    }

    public static CreatureMesh loadSSM(Context c,String asset) throws IOException {
        byte[] all=readAll(c.getAssets().open(asset));
        if(all.length<12 || all[0]!='S' || all[1]!='S' || all[2]!='M' || all[3]!='1')
            throw new IOException("Bad SSM: "+asset);

        ByteBuffer h=ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
        h.position(4);
        int rawLen=h.getInt();
        int compLen=h.getInt();
        if(compLen<1 || 12+compLen>all.length) throw new IOException("Bad SSM length: "+asset);

        InflaterInputStream in=new InflaterInputStream(new ByteArrayInputStream(all,12,compLen));
        byte[] raw=new byte[rawLen];
        int p=0,n;
        while(p<rawLen && (n=in.read(raw,p,rawLen-p))>0)p+=n;
        in.close();
        if(p!=rawLen) throw new IOException("Short SSM inflate: "+asset);

        ByteBuffer b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        int nv=b.getInt(), nf=b.getInt();
        float[] v=new float[nv*8];
        for(int k=0;k<v.length;k++)v[k]=b.getFloat();

        short[] idx=new short[nf*3];
        for(int k=0;k<idx.length;k++){
            long q=((long)b.getInt())&0xffffffffL;
            if(q>65535)throw new IOException("Index overflow in "+asset);
            idx[k]=(short)q;
        }
        return new CreatureMesh(v,idx);
    }

    private static byte[] readAll(InputStream in)throws IOException{
        ByteArrayOutputStream o=new ByteArrayOutputStream();
        byte[] buf=new byte[8192];
        int n;
        while((n=in.read(buf))>0)o.write(buf,0,n);
        in.close();
        return o.toByteArray();
    }
}