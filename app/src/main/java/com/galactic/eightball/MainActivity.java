package com.galactic.eightball;

import android.app.*;
import android.os.*;
import android.opengl.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.egl.EGLConfig;

public class MainActivity extends Activity {
  public void onCreate(Bundle b){
    super.onCreate(b);
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
    GameView v=new GameView(this);
    setContentView(v);
  }

  static class GameView extends GLSurfaceView{
    GameRenderer r;
    GameView(Context c){
      super(c); setEGLContextClientVersion(2);
      r=new GameRenderer(c); setRenderer(r); setRenderMode(RENDERMODE_CONTINUOUSLY);
    }
    public boolean onTouchEvent(MotionEvent e){
      final int a=e.getActionMasked(); final float x=e.getX(),y=e.getY();
      queueEvent(()->r.touch(a,x,y,getWidth(),getHeight()));
      return true;
    }
  }

  static class Mesh {
    FloatBuffer pos,uv; int count;
    Mesh(float[] p,float[] t){
      count=p.length/3;
      ByteBuffer bp=ByteBuffer.allocateDirect(p.length*4).order(ByteOrder.nativeOrder());
      pos=bp.asFloatBuffer(); pos.put(p).position(0);
      ByteBuffer bt=ByteBuffer.allocateDirect(t.length*4).order(ByteOrder.nativeOrder());
      uv=bt.asFloatBuffer(); uv.put(t).position(0);
    }
  }
  static class Part{
    Mesh mesh; String texKey; float[] color;
    Part(Mesh m,String t,float r,float g,float b){mesh=m;texKey=t;color=new float[]{r,g,b,1};}
  }
  static class Ball{
    float x,z,vx,vz; boolean active=true; int tex;
    Ball(float X,float Z,int T){x=X;z=Z;tex=T;}
  }

  static class GameRenderer implements GLSurfaceView.Renderer{
    Context ctx; ArrayList<Part> table=new ArrayList<>(); ArrayList<Ball> balls=new ArrayList<>();
    Mesh sphere; HashMap<String,Integer> tex=new HashMap<>();
    int program,aPos,aUv,uMvp,uUseTex,uColor,uTex;
    float aspect=16f/9f; long last=0;
    float downX,downY,curX,curY; boolean dragging=false;
    final float R=1.192f, MINX=-40.808f,MAXX=40.808f,MINZ=-19.808f,MAXZ=19.808f;
    final String[] objectFolders={"00_DeathStar","01_Tatooine","02_Kamino","03_Mustafar","04_Coruscant","05_Geonosis","06_Endor","07_Korriban","08_Exegol","09_Yavin","10_Bespin","11_Malastare","12_Kessel","13_Jakku","14_Felucia","15_Dathomir"};

    GameRenderer(Context c){ctx=c;}

    public void onSurfaceCreated(GL10 gl,EGLConfig cfg){
      GLES20.glClearColor(0.002f,0.004f,0.012f,1);
      GLES20.glEnable(GLES20.GL_DEPTH_TEST);
      GLES20.glEnable(GLES20.GL_BLEND);
      GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
      String vs="attribute vec3 aPos;attribute vec2 aUv;uniform mat4 uMvp;varying vec2 vUv;void main(){gl_Position=uMvp*vec4(aPos,1.0);vUv=vec2(aUv.x,1.0-aUv.y);}";
      String fs="precision mediump float;varying vec2 vUv;uniform sampler2D uTex;uniform float uUseTex;uniform vec4 uColor;void main(){vec4 c=uColor;if(uUseTex>0.5)c*=texture2D(uTex,vUv);gl_FragColor=c;}";
      program=GLES20.glCreateProgram(); int sv=shader(GLES20.GL_VERTEX_SHADER,vs),sf=shader(GLES20.GL_FRAGMENT_SHADER,fs);
      GLES20.glAttachShader(program,sv); GLES20.glAttachShader(program,sf); GLES20.glLinkProgram(program);
      aPos=GLES20.glGetAttribLocation(program,"aPos"); aUv=GLES20.glGetAttribLocation(program,"aUv");
      uMvp=GLES20.glGetUniformLocation(program,"uMvp");uUseTex=GLES20.glGetUniformLocation(program,"uUseTex");
      uColor=GLES20.glGetUniformLocation(program,"uColor");uTex=GLES20.glGetUniformLocation(program,"uTex");
      loadAssets(); resetRack(); last=System.nanoTime();
    }

    int shader(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);return s;}

    public void onSurfaceChanged(GL10 gl,int w,int h){GLES20.glViewport(0,0,w,h);aspect=(float)w/Math.max(1,h);}

    public void onDrawFrame(GL10 gl){
      long now=System.nanoTime(); float dt=Math.min(.033f,(now-last)/1_000_000_000f); last=now; step(dt);
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
      GLES20.glUseProgram(program);
      float[] P=new float[16],V=new float[16],PV=new float[16];
      Matrix.perspectiveM(P,0,40,aspect,.1f,300f);
      Matrix.setLookAtM(V,0,0,62,72,0,-1,0,0,1,0);
      Matrix.multiplyMM(PV,0,P,0,V,0);
      for(Part p:table) drawMesh(p.mesh,PV,identity(),p.texKey==null?0:tex.getOrDefault(p.texKey,0),p.color);
      for(Ball b:balls) if(b.active){
        float[] M=identity(); Matrix.translateM(M,0,b.x,2.22f,b.z); Matrix.scaleM(M,0,R,R,R);
        drawMesh(sphere,PV,M,b.tex,new float[]{1,1,1,1});
      }
      drawAim(PV);
    }

    void drawMesh(Mesh m,float[] pv,float[] model,int texture,float[] color){
      if(m==null)return; float[] mvp=new float[16]; Matrix.multiplyMM(mvp,0,pv,0,model,0);
      GLES20.glUniformMatrix4fv(uMvp,1,false,mvp,0);
      GLES20.glUniform4fv(uColor,1,color,0);
      GLES20.glUniform1f(uUseTex,texture!=0?1f:0f);
      if(texture!=0){GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);GLES20.glUniform1i(uTex,0);}
      GLES20.glEnableVertexAttribArray(aPos); GLES20.glVertexAttribPointer(aPos,3,GLES20.GL_FLOAT,false,0,m.pos);
      GLES20.glEnableVertexAttribArray(aUv); GLES20.glVertexAttribPointer(aUv,2,GLES20.GL_FLOAT,false,0,m.uv);
      GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,m.count);
      GLES20.glDisableVertexAttribArray(aPos);GLES20.glDisableVertexAttribArray(aUv);
    }

    void loadAssets(){
      try{
        tex.put("felt",loadTexture(findAsset("extracted","_Felt.png")));
        tex.put("wood",loadTexture(findAsset("extracted","_Wood.png")));
        tex.put("foot",loadTexture(findAsset("extracted","_Foot.png")));
        addTable("1631477688504185304_default.obj","felt",1,1,1);
        addTable("106991748306668190_default.obj","wood",1,1,1);
        addTable("-4426823995715296130_default.obj","wood",1,1,1);
        addTable("-2382927779929703425_default.obj","wood",1,1,1);
        addTable("327116407711713708_default.obj","foot",1,1,1);
        addTable("-7406165369254709877_default.obj",null,.376f,.376f,.376f);
        addTable("-1542075189251850320_default.obj",null,.376f,.376f,.376f);
        addTable("-4920293310515908916_default.obj",null,.063f,.063f,.063f);
        addTable("6851484603853778718_default.obj",null,.9f,.92f,.96f);
        addTable("6459398429916909974_default.obj",null,.8f,.8f,.8f);
        sphere=loadObj("objects/01_Tatooine/-8750451297455424342_default.obj");
        for(int i=0;i<objectFolders.length;i++){
          String p=findAsset("objects/"+objectFolders[i],".png");
          tex.put("ball"+i,loadTexture(p));
        }
      }catch(Exception e){e.printStackTrace();}
    }

    void addTable(String file,String key,float r,float g,float b)throws Exception{table.add(new Part(loadObj("extracted/"+file),key,r,g,b));}

    String findAsset(String folder,String suffix)throws Exception{
      String[] a=ctx.getAssets().list(folder); if(a==null)throw new IOException(folder);
      for(String n:a)if(n.endsWith(suffix))return folder+"/"+n;
      throw new IOException("Missing "+suffix+" in "+folder);
    }

    int loadTexture(String path)throws Exception{
      Bitmap bmp; try(InputStream in=ctx.getAssets().open(path)){bmp=BitmapFactory.decodeStream(in);}
      int[] id=new int[1];GLES20.glGenTextures(1,id,0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,id[0]);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR_MIPMAP_LINEAR);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_REPEAT);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_REPEAT);
      GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bmp,0);GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);bmp.recycle();return id[0];
    }

    Mesh loadObj(String path)throws Exception{
      ArrayList<float[]> verts=new ArrayList<>(),uvs=new ArrayList<>();ArrayList<Float> po=new ArrayList<>(),to=new ArrayList<>();
      try(BufferedReader br=new BufferedReader(new InputStreamReader(ctx.getAssets().open(path)))){
        String l;
        while((l=br.readLine())!=null){
          if(l.startsWith("v ")){String[] q=l.trim().split("\\s+");verts.add(new float[]{Float.parseFloat(q[1]),Float.parseFloat(q[2]),Float.parseFloat(q[3])});}
          else if(l.startsWith("vt ")){String[] q=l.trim().split("\\s+");uvs.add(new float[]{Float.parseFloat(q[1]),Float.parseFloat(q[2])});}
          else if(l.startsWith("f ")){
            String[] q=l.trim().split("\\s+");
            for(int k=2;k<q.length-1;k++){appendFace(q[1],verts,uvs,po,to);appendFace(q[k],verts,uvs,po,to);appendFace(q[k+1],verts,uvs,po,to);}
          }
        }
      }
      float[] p=new float[po.size()],t=new float[to.size()];for(int i=0;i<p.length;i++)p[i]=po.get(i);for(int i=0;i<t.length;i++)t[i]=to.get(i);return new Mesh(p,t);
    }

    void appendFace(String token,ArrayList<float[]> v,ArrayList<float[]> uv,ArrayList<Float> po,ArrayList<Float> to){
      String[] a=token.split("/");int vi=parseIndex(a[0],v.size());float[] p=v.get(vi);po.add(p[0]);po.add(p[1]);po.add(p[2]);
      float u=0,w=0;if(a.length>1&&!a[1].isEmpty()){int ti=parseIndex(a[1],uv.size());if(ti>=0&&ti<uv.size()){u=uv.get(ti)[0];w=uv.get(ti)[1];}}to.add(u);to.add(w);
    }
    int parseIndex(String s,int size){int i=Integer.parseInt(s);return i<0?size+i:i-1;}

    void resetRack(){
      balls.clear(); balls.add(new Ball(-20,0,tex.getOrDefault("ball0",0)));
      float[][] p={{20,0},{22.09f,-1.21f},{22.09f,1.21f},{24.18f,-2.42f},{24.18f,0},{24.18f,2.42f},{26.27f,-3.63f},{26.27f,-1.21f},{26.27f,1.21f},{26.27f,3.63f},{28.36f,-4.84f},{28.36f,-2.42f},{28.36f,0},{28.36f,2.42f},{28.36f,4.84f}};
      for(int i=0;i<15;i++)balls.add(new Ball(p[i][0],p[i][1],tex.getOrDefault("ball"+(i+1),0)));
    }

    void touch(int action,float x,float y,int w,int h){
      if(action==MotionEvent.ACTION_DOWN){downX=curX=x;downY=curY=y;dragging=true;}
      else if(action==MotionEvent.ACTION_MOVE){curX=x;curY=y;}
      else if(action==MotionEvent.ACTION_UP&&dragging){
        curX=x;curY=y;float dx=downX-curX,dy=curY-downY;float mag=(float)Math.sqrt(dx*dx+dy*dy);
        if(mag>20&&allStopped()){
          float speed=Math.min(34f,mag/18f);float nx=dx/mag,nz=dy/mag;
          Ball cue=balls.get(0);if(!cue.active){cue.active=true;cue.x=-20;cue.z=0;}cue.vx=nx*speed;cue.vz=nz*speed;
        }
        dragging=false;
      }
    }

    void step(float dt){
      if(balls.isEmpty())return;
      for(Ball b:balls)if(b.active){
        b.x+=b.vx*dt*3.2f;b.z+=b.vz*dt*3.2f;
        float damp=(float)Math.pow(.28,dt);b.vx*=damp;b.vz*=damp;if(Math.abs(b.vx)<.015)b.vx=0;if(Math.abs(b.vz)<.015)b.vz=0;
        if(b.x-R<MINX){b.x=MINX+R;b.vx=Math.abs(b.vx)*.88f;}if(b.x+R>MAXX){b.x=MAXX-R;b.vx=-Math.abs(b.vx)*.88f;}
        if(b.z-R<MINZ){b.z=MINZ+R;b.vz=Math.abs(b.vz)*.88f;}if(b.z+R>MAXZ){b.z=MAXZ-R;b.vz=-Math.abs(b.vz)*.88f;}
      }
      for(int i=0;i<balls.size();i++)for(int j=i+1;j<balls.size();j++)collide(balls.get(i),balls.get(j));
      for(int i=0;i<balls.size();i++)checkPocket(i,balls.get(i));
    }

    void collide(Ball a,Ball b){
      if(!a.active||!b.active)return;float dx=b.x-a.x,dz=b.z-a.z,d2=dx*dx+dz*dz,min=2*R;
      if(d2<=0||d2>=min*min)return;float d=(float)Math.sqrt(d2),nx=dx/d,nz=dz/d,over=min-d;
      a.x-=nx*over*.5f;a.z-=nz*over*.5f;b.x+=nx*over*.5f;b.z+=nz*over*.5f;
      float rel=(b.vx-a.vx)*nx+(b.vz-a.vz)*nz;if(rel<0){float imp=-rel*.98f;a.vx-=imp*nx;a.vz-=imp*nz;b.vx+=imp*nx;b.vz+=imp*nz;}
    }

    void checkPocket(int index,Ball b){
      if(!b.active)return;float[][] p={{MINX,MINZ},{0,MINZ},{MAXX,MINZ},{MINX,MAXZ},{0,MAXZ},{MAXX,MAXZ}};
      for(float[] q:p){float dx=b.x-q[0],dz=b.z-q[1];if(dx*dx+dz*dz<7.0f){b.active=false;b.vx=b.vz=0;if(index==0){b.x=-20;b.z=0;}return;}}
    }

    boolean allStopped(){for(Ball b:balls)if(b.active&&(Math.abs(b.vx)+Math.abs(b.vz)>.08f))return false;return true;}

    void drawAim(float[] pv){
      if(!dragging||balls.isEmpty())return;Ball cue=balls.get(0);float dx=downX-curX,dy=curY-downY,mag=(float)Math.sqrt(dx*dx+dy*dy);if(mag<4)return;
      float nx=dx/mag,nz=dy/mag,len=20;float[] p={cue.x,2.25f,cue.z,cue.x+nx*len,2.25f,cue.z+nz*len};float[] u={0,0,0,0};
      Mesh line=new Mesh(p,u);float[] mvp=new float[16];Matrix.multiplyMM(mvp,0,pv,0,identity(),0);
      GLES20.glUniformMatrix4fv(uMvp,1,false,mvp,0);GLES20.glUniform4f(uColor,.2f,.85f,1,1);GLES20.glUniform1f(uUseTex,0);
      GLES20.glEnableVertexAttribArray(aPos);GLES20.glVertexAttribPointer(aPos,3,GLES20.GL_FLOAT,false,0,line.pos);
      GLES20.glDisableVertexAttribArray(aUv);GLES20.glLineWidth(6);GLES20.glDrawArrays(GLES20.GL_LINES,0,2);GLES20.glDisableVertexAttribArray(aPos);
    }

    static float[] identity(){float[] m=new float[16];Matrix.setIdentityM(m,0);return m;}
  }
}
