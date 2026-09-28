package com.soulslime.nativeapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.view.MotionEvent;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class Slime3DView extends GLSurfaceView {
    private final SlimeRenderer renderer;
    private boolean interactive=true;
    private float lastX;

    public Slime3DView(Context context){
        super(context);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8,8,8,8,16,0);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setZOrderOnTop(true);
        setFocusable(false);
        setFocusableInTouchMode(false);
        renderer=new SlimeRenderer(context.getApplicationContext());
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    public void setInteractive(boolean value){ interactive=value; }

    public void setConfig(int shape,int material,int color,int decal,int core,int alpha,int aura){
        queueEvent(()->renderer.setConfig(shape,material,color,decal,core,alpha,aura));
    }

    public void react(){
        queueEvent(renderer::react);
    }

    public void setGameplayPosition(float nx,float ny,float depth,float moveX,float moveY,boolean visible){
        queueEvent(()->renderer.setGameplay(nx,ny,depth,moveX,moveY,visible?0:-1));
    }

    public void setGameplayState(float nx,float ny,float depth,float moveX,float moveY,int form){
        queueEvent(()->renderer.setGameplay(nx,ny,depth,moveX,moveY,form));
    }

    public void setEnemyState(int type,float nx,float groundY,float depth,float facing,int state,
                              float attackPulse,float hitPulse,boolean visible){
        queueEvent(()->renderer.setEnemy(type,nx,groundY,depth,facing,state,attackPulse,hitPulse,visible));
    }

    public void setPreviewMode(){
        queueEvent(renderer::setPreview);
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(!interactive)return false;
        if(e.getAction()==MotionEvent.ACTION_DOWN){lastX=e.getX();return true;}
        if(e.getAction()==MotionEvent.ACTION_MOVE){
            float dx=e.getX()-lastX; lastX=e.getX();
            queueEvent(()->renderer.userYaw+=dx*.35f);
            return true;
        }
        return e.getAction()==MotionEvent.ACTION_UP;
    }

    static class SlimeRenderer implements Renderer {
        private final Context context;

        private int program,coreProgram,creatureProgram;
        private int aPos,aNorm,uMvp,uModel,uColor,uTime,uAlpha,uMaterial,uDecal,uAura;
        private int caPos,cuMvp,cuTime,cuColor;

        private int crPos,crNorm,crUv,crMvp,crModel,crTex,crTint,crAlpha,crEmissive,crLightColor,crTime;

        private Mesh[] shapes=new Mesh[3];
        private Mesh sphere;
        private CreatureMesh razorbeast,voidweaver;
        private int texRazor=0,texVoid=0;

        private int shape=0,material=0,colorIndex=0,decal=0,core=0,alpha=2,aura=2;
        private long start=System.nanoTime(),reactionStart=0;
        private int width=1,height=1;
        private float userYaw=0;

        private boolean gameplay=false,gameVisible=true;
        private int playerForm=0;
        private float gx=.5f,gy=.5f,gdepth=1f,moveX=0,moveY=0,playerFacing=1f;

        private boolean enemyVisible=false;
        private int enemyType=0,enemyState=2;
        private float ex=.5f,enemyGroundY=.5f,edepth=1f,enemyFacing=1f,enemyAttackPulse=0f,enemyHitPulse=0f;

        private final float[] proj=new float[16],view=new float[16],model=new float[16],mv=new float[16],mvp=new float[16];

        SlimeRenderer(Context context){ this.context=context; }

        void setConfig(int sh,int mat,int col,int dec,int co,int al,int au){
            shape=Math.max(0,Math.min(2,sh));
            material=Math.max(0,Math.min(2,mat));
            colorIndex=Math.max(0,Math.min(5,col));
            decal=Math.max(0,Math.min(3,dec));
            core=Math.max(0,Math.min(3,co));
            alpha=Math.max(0,Math.min(3,al));
            aura=Math.max(0,Math.min(4,au));
        }
        void react(){reactionStart=System.nanoTime();}
        void setPreview(){
            gameplay=false;gameVisible=true;playerForm=0;
            gx=.5f;gy=.5f;gdepth=1f;moveX=moveY=0;
            enemyVisible=false;
        }
        void setGameplay(float x,float y,float depth,float mx,float my,int form){
            gameplay=true;gx=x;gy=y;gdepth=depth;moveX=mx;moveY=my;
            if(Math.abs(mx)>.05f)playerFacing=mx<0f?-1f:1f;
            playerForm=form;
            gameVisible=form>=0;
        }
        void setEnemy(int type,float x,float groundY,float depth,float facing,int state,
                      float attackPulse,float hitPulse,boolean visible){
            enemyType=type;ex=x;enemyGroundY=groundY;edepth=depth;enemyFacing=facing;enemyState=state;
            enemyAttackPulse=Math.max(0f,Math.min(1f,attackPulse));
            enemyHitPulse=Math.max(0f,Math.min(1f,hitPulse));
            enemyVisible=visible&&type>0&&state!=2;
        }

        @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl, javax.microedition.khronos.egl.EGLConfig cfg){
            GLES20.glClearColor(0f,0f,0f,0f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);

            program=link(VS,FS);
            coreProgram=link(VS_CORE,FS_CORE);
            aPos=GLES20.glGetAttribLocation(program,"aPosition");
            aNorm=GLES20.glGetAttribLocation(program,"aNormal");
            uMvp=GLES20.glGetUniformLocation(program,"uMVP");
            uModel=GLES20.glGetUniformLocation(program,"uModel");
            uColor=GLES20.glGetUniformLocation(program,"uColor");
            uTime=GLES20.glGetUniformLocation(program,"uTime");
            uAlpha=GLES20.glGetUniformLocation(program,"uAlpha");
            uMaterial=GLES20.glGetUniformLocation(program,"uMaterial");
            uDecal=GLES20.glGetUniformLocation(program,"uDecal");
            uAura=GLES20.glGetUniformLocation(program,"uAura");

            caPos=GLES20.glGetAttribLocation(coreProgram,"aPosition");
            cuMvp=GLES20.glGetUniformLocation(coreProgram,"uMVP");
            cuTime=GLES20.glGetUniformLocation(coreProgram,"uTime");
            cuColor=GLES20.glGetUniformLocation(coreProgram,"uColor");

            creatureProgram=link(VS_CREATURE,FS_CREATURE);
            crPos=GLES20.glGetAttribLocation(creatureProgram,"aPosition");
            crNorm=GLES20.glGetAttribLocation(creatureProgram,"aNormal");
            crUv=GLES20.glGetAttribLocation(creatureProgram,"aUv");
            crMvp=GLES20.glGetUniformLocation(creatureProgram,"uMVP");
            crModel=GLES20.glGetUniformLocation(creatureProgram,"uModel");
            crTex=GLES20.glGetUniformLocation(creatureProgram,"uTex");
            crTint=GLES20.glGetUniformLocation(creatureProgram,"uTint");
            crAlpha=GLES20.glGetUniformLocation(creatureProgram,"uAlpha");
            crEmissive=GLES20.glGetUniformLocation(creatureProgram,"uEmissive");
            crLightColor=GLES20.glGetUniformLocation(creatureProgram,"uLightColor");
            crTime=GLES20.glGetUniformLocation(creatureProgram,"uTime");

            for(int i=0;i<3;i++)shapes[i]=Mesh.slime(i,42,28);
            sphere=Mesh.sphere(30,20);

            try{
                razorbeast=CreatureMesh.loadSSM(context,"razorbeast.ssm");
                voidweaver=CreatureMesh.loadSSM(context,"voidweaver.ssm");
                texRazor=loadTexture("razorbeast.jpg");
                texVoid=loadTexture("voidweaver.jpg");
            }catch(Exception ignored){
                razorbeast=null;voidweaver=null;texRazor=texVoid=0;
            }
        }

        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,int w,int h){
            width=Math.max(1,w);height=Math.max(1,h);
            GLES20.glViewport(0,0,width,height);
            float aspect=width/(float)height;
            Matrix.orthoM(proj,0,-aspect,aspect,-1f,1f,0.1f,10f);
            Matrix.setLookAtM(view,0,0,0,4,0,0,0,0,1,0);
        }

        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl){
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
            if(!gameVisible&&!enemyVisible)return;

            float t=(System.nanoTime()-start)/1_000_000_000f;

            // Painted cave actors are depth-sorted by their screen Y.  Each actor gets a
            // fresh depth buffer so the later (lower-on-screen) actor cleanly overlaps.
            if(gameplay&&enemyVisible&&gameVisible){
                if(enemyGroundY<gy){
                    drawEnemy(t);
                    GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);
                    drawPlayer(t);
                }else{
                    drawPlayer(t);
                    GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);
                    drawEnemy(t);
                }
            }else{
                if(enemyVisible)drawEnemy(t);
                if(enemyVisible&&gameVisible)GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);
                if(gameVisible)drawPlayer(t);
            }
        }

        private void drawPlayer(float t){
            if(gameplay&&playerForm>0){
                float facing=playerFacing;
                // Player creature forms use the same real 3D assets, scaled to the original
                // player footprint and bottom-anchored to the painted floor.
                float ground=Math.min(.965f,gy+.057f*Math.max(.80f,Math.min(1.12f,gdepth)));
                drawCreature(playerForm,gx,ground,gdepth,facing,0,0f,0f,true,t);
                return;
            }

            float mag=(float)Math.sqrt(moveX*moveX+moveY*moveY);
            float bob=gameplay&&mag>.05f?Math.abs((float)Math.sin(t*10.5f))*.045f:(float)Math.sin(t*2.0f)*.012f;
            float squash=gameplay&&mag>.05f?1f+.055f*(float)Math.sin(t*10.5f):1f+.018f*(float)Math.sin(t*2.1f);
            if(reactionStart>0){
                float age=(System.nanoTime()-reactionStart)/1_000_000_000f;
                if(age<.45f)squash+=.12f*(float)Math.sin(age/.45f*Math.PI);
            }

            float aspect=width/(float)height;
            Matrix.setIdentityM(model,0);
            if(gameplay){
                float wx=(gx-.5f)*2f*aspect;
                float wy=(.5f-gy)*2f+bob;
                Matrix.translateM(model,0,wx,wy,0f);
                float ss=.23f*Math.max(.72f,Math.min(1.12f,gdepth));
                Matrix.scaleM(model,0,ss*squash,ss*(2f-squash),ss);
                float face=playerFacing<0f?180f:0f;
                Matrix.rotateM(model,0,face,0,1,0);
            }else{
                Matrix.translateM(model,0,0,-.06f+bob,0f);
                Matrix.scaleM(model,0,.74f*squash,.74f*(2f-squash),.74f);
                Matrix.rotateM(model,0,9f*(float)Math.sin(t*.42f)+userYaw,0,1,0);
            }

            float[] base=baseColor(colorIndex);
            float auraBoost=.10f+aura*.045f;

            if(core!=3){
                float[] coreModel=model.clone();
                Matrix.translateM(coreModel,0,0,-.10f,.02f);
                float cs=core==1?.25f:core==2?.29f:.27f;
                Matrix.scaleM(coreModel,0,cs,cs,cs);
                makeMvp(coreModel);
                GLES20.glUseProgram(coreProgram);
                GLES20.glUniformMatrix4fv(cuMvp,1,false,mvp,0);
                GLES20.glUniform1f(cuTime,t);
                float[] cc=core==1?new float[]{1f,.78f,.25f}:core==2?new float[]{.70f,.78f,1f}:new float[]{.34f,.92f,1f};
                GLES20.glUniform3fv(cuColor,1,cc,0);
                sphere.drawPositionOnly(caPos);
            }

            makeMvp(model);
            GLES20.glUseProgram(program);
            GLES20.glUniformMatrix4fv(uMvp,1,false,mvp,0);
            GLES20.glUniformMatrix4fv(uModel,1,false,model,0);
            GLES20.glUniform3fv(uColor,1,base,0);
            GLES20.glUniform1f(uTime,t);
            GLES20.glUniform1f(uAlpha,new float[]{.48f,.60f,.73f,.86f}[alpha]);
            GLES20.glUniform1f(uMaterial,(float)material);
            GLES20.glUniform1f(uDecal,(float)decal);
            GLES20.glUniform1f(uAura,auraBoost);
            GLES20.glDepthMask(false);
            shapes[shape].draw(aPos,aNorm);
            GLES20.glDepthMask(true);
        }

        private void drawEnemy(float t){
            drawCreature(enemyType,ex,enemyGroundY,edepth,enemyFacing,enemyState,
                         enemyAttackPulse,enemyHitPulse,false,t);
        }

        private void drawCreature(int type,float nx,float groundY,float depth,float facing,int state,
                                  float attackPulse,float hitPulse,boolean player,float t){
            CreatureMesh mesh=type==1?razorbeast:voidweaver;
            int texture=type==1?texRazor:texVoid;
            if(mesh==null||texture==0)return;

            float aspect=width/(float)height;
            float wx=(nx-.5f)*2f*aspect;
            float gyWorld=(.5f-groundY)*2f;

            float d=Math.max(.78f,Math.min(1.16f,depth));
            float baseScale;
            float minZ;
            if(type==1){
                baseScale=(player?.205f:.270f)*d;
                minZ=-.69545f;
            }else{
                baseScale=(player?.315f:.500f)*d;
                minZ=-.46818f;
            }

            // Meshy creature exports are Z-up. Keep X horizontal, map Z to world Y,
            // and use original Y only as depth. The old extra Y-axis quarter-turn made
            // the spider appear as a folded side slice even when its world anchor was correct.
            float sx=baseScale*(1f+.055f*attackPulse);
            float sz=baseScale*(1f-.035f*attackPulse);
            float centerY=gyWorld-minZ*sz;

            Matrix.setIdentityM(model,0);
            Matrix.translateM(model,0,wx,centerY,0f);
            Matrix.rotateM(model,0,-90f,1f,0f,0f);

            float flip=facing<0?-1f:1f;
            Matrix.scaleM(model,0,sx*flip,baseScale,sz);

            makeMvp(model);
            GLES20.glUseProgram(creatureProgram);
            GLES20.glUniformMatrix4fv(crMvp,1,false,mvp,0);
            GLES20.glUniformMatrix4fv(crModel,1,false,model,0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
            GLES20.glUniform1i(crTex,0);

            if(type==1){
                GLES20.glUniform3f(crTint,1f,.98f,.96f);
                GLES20.glUniform3f(crLightColor,1f,.28f,.055f);
            }else{
                GLES20.glUniform3f(crTint,.96f,.94f,1f);
                GLES20.glUniform3f(crLightColor,.58f,.22f,1f);
            }

            float defeated=state==1?1f:0f;
            float alphaValue=state==1?.64f:1f;
            float emissive=.045f+hitPulse*.85f+attackPulse*.22f+defeated*.04f;
            GLES20.glUniform1f(crAlpha,alphaValue);
            GLES20.glUniform1f(crEmissive,emissive);
            GLES20.glUniform1f(crTime,t);

            mesh.draw(crPos,crNorm,crUv);
        }

        private int loadTexture(String asset){
            try{
                InputStream in=context.getAssets().open(asset);
                Bitmap bm=BitmapFactory.decodeStream(in);
                in.close();
                int[] id=new int[1];
                GLES20.glGenTextures(1,id,0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,id[0]);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR_MIPMAP_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_REPEAT);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_REPEAT);
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bm,0);
                GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
                bm.recycle();
                return id[0];
            }catch(Exception ignored){
                return 0;
            }
        }

        private void makeMvp(float[] m){
            Matrix.multiplyMM(mv,0,view,0,m,0);
            Matrix.multiplyMM(mvp,0,proj,0,mv,0);
        }

        static float[] baseColor(int i){
            switch(i){
                case 1:return new float[]{.64f,.24f,1f};
                case 2:return new float[]{1f,.18f,.08f};
                case 3:return new float[]{.10f,.92f,.46f};
                case 4:return new float[]{.87f,.95f,1f};
                case 5:return new float[]{.12f,.09f,.28f};
                default:return new float[]{.05f,.55f,1f};
            }
        }

        static int link(String vs,String fs){
            int v=shader(GLES20.GL_VERTEX_SHADER,vs),f=shader(GLES20.GL_FRAGMENT_SHADER,fs);
            int p=GLES20.glCreateProgram();
            GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);
            int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);
            if(ok[0]==0)throw new RuntimeException("GL link: "+GLES20.glGetProgramInfoLog(p));
            GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);return p;
        }
        static int shader(int type,String src){
            int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);
            int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);
            if(ok[0]==0)throw new RuntimeException("GL shader: "+GLES20.glGetShaderInfoLog(s));
            return s;
        }

        static final String VS=
            "uniform mat4 uMVP; uniform mat4 uModel; attribute vec3 aPosition; attribute vec3 aNormal;"+
            "varying vec3 vPos; varying vec3 vNormal;"+
            "void main(){vPos=aPosition;vNormal=normalize(mat3(uModel)*aNormal);gl_Position=uMVP*vec4(aPosition,1.0);}";

        static final String FS=
            "precision mediump float; varying vec3 vPos; varying vec3 vNormal;"+
            "uniform vec3 uColor; uniform float uTime; uniform float uAlpha; uniform float uMaterial; uniform float uDecal; uniform float uAura;"+
            "float hash(vec3 p){return fract(sin(dot(p,vec3(17.13,91.7,43.23)))*43758.5453);}"+
            "void main(){"+
            " vec3 N=normalize(vNormal); vec3 L=normalize(vec3(-.35,.75,.55)); float diff=max(dot(N,L),0.0);"+
            " float fres=pow(1.0-max(N.z,0.0),2.2); float spec=pow(max(dot(reflect(-L,N),vec3(0.,0.,1.)),0.0),22.0);"+
            " float pat=0.0;"+
            " if(uMaterial<.5){pat=.5+.5*sin(vPos.y*13.0+atan(vPos.z,vPos.x)*5.0+uTime*.7);pat*=.45;}"+
            " else if(uMaterial<1.5){pat=pow(abs(sin((vPos.x*.8+vPos.y+vPos.z*.45)*18.0+sin(vPos.y*9.0)*2.0)),7.0);}"+
            " else {float q=sin(length(vPos.xz*vec2(12.0,9.0))+uTime*.45);pat=pow(max(q,0.0),10.0);}"+
            " vec3 col=uColor*(.38+.62*diff)+vec3(.48,.86,1.0)*(fres*(.28+uAura)+spec*.78)+uColor*pat*.38;"+
            " float decal=0.0;"+
            " if(uDecal>.5&&uDecal<1.5)decal=pow(abs(sin(atan(vPos.z,vPos.x)*4.0+vPos.y*9.0)),12.0);"+
            " else if(uDecal>1.5&&uDecal<2.5)decal=step(.88,hash(floor(vPos*18.0)));"+
            " else if(uDecal>2.5)decal=pow(abs(sin(vPos.x*9.0)-cos(vPos.y*11.0)),14.0);"+
            " col+=vec3(.72,.94,1.0)*decal*.7;"+
            // eyes painted procedurally on front hemisphere
            " float ex=abs(vPos.x); vec2 e=vec2((ex-.28)/.13,(vPos.y-.10)/.105); float eye=1.0-smoothstep(.72,1.0,dot(e,e)); eye*=smoothstep(.28,.55,vPos.z);"+
            " float pupil=1.0-smoothstep(.12,.28,abs((ex-.28)/.13)); pupil*=eye;"+
            " col=mix(col,vec3(.03,.08,.16),eye*.88); col+=vec3(.18,.78,1.0)*eye*.85; col=mix(col,vec3(.01,.02,.06),pupil*.78);"+
            " float a=clamp(uAlpha+fres*.14+eye*.35,0.0,.96); gl_FragColor=vec4(col,a);"+
            "}";

        static final String VS_CREATURE=
            "uniform mat4 uMVP; uniform mat4 uModel; attribute vec3 aPosition; attribute vec3 aNormal; attribute vec2 aUv;"+
            "varying vec3 vNormal; varying vec2 vUv;"+
            "void main(){vNormal=normalize(mat3(uModel)*aNormal);vUv=aUv;gl_Position=uMVP*vec4(aPosition,1.0);}";

        static final String FS_CREATURE=
            "precision mediump float; varying vec3 vNormal; varying vec2 vUv;"+
            "uniform sampler2D uTex; uniform vec3 uTint; uniform vec3 uLightColor;"+
            "uniform float uAlpha; uniform float uEmissive; uniform float uTime;"+
            "void main(){"+
            " vec3 N=normalize(vNormal); vec3 L=normalize(vec3(-.34,.72,.60));"+
            " float diff=max(dot(N,L),0.0); float rim=pow(1.0-max(N.z,0.0),2.0);"+
            " vec3 base=texture2D(uTex,vUv).rgb*uTint;"+
            " float pulse=.82+.18*sin(uTime*3.2);"+
            " vec3 lit=base*(.42+.72*diff)+uLightColor*(rim*.18+uEmissive*pulse);"+
            " gl_FragColor=vec4(lit,uAlpha);"+
            "}";

        static final String VS_CORE=
            "uniform mat4 uMVP; attribute vec3 aPosition; varying vec3 vPos; void main(){vPos=aPosition;gl_Position=uMVP*vec4(aPosition,1.0);}";
        static final String FS_CORE=
            "precision mediump float; varying vec3 vPos; uniform float uTime; uniform vec3 uColor;"+
            "void main(){float r=length(vPos);float glow=1.0-smoothstep(.15,1.0,r);float pulse=.75+.25*sin(uTime*4.0);gl_FragColor=vec4(uColor*(1.2+glow*pulse),.86*glow);} ";
    }

    static class Mesh {
        final FloatBuffer pos,norm;
        final ShortBuffer idx;
        final int count;
        Mesh(float[] p,float[] n,short[] ii){
            pos=fb(p);norm=fb(n);
            idx=ByteBuffer.allocateDirect(ii.length*2).order(ByteOrder.nativeOrder()).asShortBuffer();
            idx.put(ii).position(0);count=ii.length;
        }
        void draw(int aPos,int aNorm){
            pos.position(0);norm.position(0);idx.position(0);
            GLES20.glEnableVertexAttribArray(aPos);GLES20.glEnableVertexAttribArray(aNorm);
            GLES20.glVertexAttribPointer(aPos,3,GLES20.GL_FLOAT,false,12,pos);
            GLES20.glVertexAttribPointer(aNorm,3,GLES20.GL_FLOAT,false,12,norm);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES,count,GLES20.GL_UNSIGNED_SHORT,idx);
            GLES20.glDisableVertexAttribArray(aPos);GLES20.glDisableVertexAttribArray(aNorm);
        }
        void drawPositionOnly(int aPos){
            pos.position(0);idx.position(0);
            GLES20.glEnableVertexAttribArray(aPos);
            GLES20.glVertexAttribPointer(aPos,3,GLES20.GL_FLOAT,false,12,pos);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES,count,GLES20.GL_UNSIGNED_SHORT,idx);
            GLES20.glDisableVertexAttribArray(aPos);
        }
        static FloatBuffer fb(float[] a){
            FloatBuffer b=ByteBuffer.allocateDirect(a.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            b.put(a).position(0);return b;
        }
        static Mesh sphere(int lon,int lat){return make(-1,lon,lat);}
        static Mesh slime(int shape,int lon,int lat){return make(shape,lon,lat);}
        static Mesh make(int shape,int lon,int lat){
            int verts=(lon+1)*(lat+1);
            float[] p=new float[verts*3],n=new float[verts*3];
            short[] id=new short[lon*lat*6];
            int vi=0;
            for(int iy=0;iy<=lat;iy++){
                float v=iy/(float)lat;
                float phi=(float)(-Math.PI/2+Math.PI*v);
                float sy=(float)Math.sin(phi), rr=(float)Math.cos(phi);
                for(int ix=0;ix<=lon;ix++){
                    float u=ix/(float)lon;
                    float th=(float)(Math.PI*2*u);
                    float x=rr*(float)Math.cos(th),y=sy,z=rr*(float)Math.sin(th);
                    if(shape>=0){
                        if(shape==0){
                            x*=1.03f;z*=1.03f;y*=.92f;
                            if(y>.42f){float q=y-.42f;x+=.10f*q*q;y+=.22f*q*q;}
                            if(y<-.58f)y=-.58f+(y+.58f)*.28f;
                        }else if(shape==1){
                            x*=.82f;z*=.82f;y*=1.16f;
                            if(y>.30f){float q=y-.30f;x+=.17f*q*q;y+=.18f*q*q;}
                            if(y<-.72f)y=-.72f+(y+.72f)*.25f;
                        }else{
                            x*=1.28f;z*=1.20f;y*=.66f;
                            if(y<-.42f)y=-.42f+(y+.42f)*.20f;
                        }
                    }
                    p[vi]=x;p[vi+1]=y;p[vi+2]=z;
                    float len=(float)Math.sqrt(x*x+y*y+z*z);if(len<.001f)len=1;
                    n[vi]=x/len;n[vi+1]=y/len;n[vi+2]=z/len;vi+=3;
                }
            }
            int k=0;
            for(int iy=0;iy<lat;iy++)for(int ix=0;ix<lon;ix++){
                short a=(short)(iy*(lon+1)+ix),b=(short)(a+1),c=(short)((iy+1)*(lon+1)+ix),d=(short)(c+1);
                id[k++]=a;id[k++]=c;id[k++]=b;id[k++]=b;id[k++]=c;id[k++]=d;
            }
            return new Mesh(p,n,id);
        }
    }
}