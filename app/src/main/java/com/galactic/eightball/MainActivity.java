package com.galactic.eightball;

import android.app.*;
import android.os.*;
import android.opengl.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.egl.EGLConfig;
import org.jbox2d.collision.shapes.CircleShape;
import org.jbox2d.collision.shapes.EdgeShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.Body;
import org.jbox2d.dynamics.BodyDef;
import org.jbox2d.dynamics.BodyType;
import org.jbox2d.dynamics.FixtureDef;
import org.jbox2d.dynamics.World;

public class MainActivity extends Activity {
  GameView game;
  HudView hud;

  public void onCreate(Bundle b){
    super.onCreate(b);
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
    game=new GameView(this);
    hud=new HudView(this,game);
    FrameLayout root=new FrameLayout(this);
    root.addView(game,new FrameLayout.LayoutParams(-1,-1));
    root.addView(hud,new FrameLayout.LayoutParams(-1,-1));
    setContentView(root);
  }

  static class GameView extends GLSurfaceView{
    GameRenderer r;
    GameView(Context c){
      super(c);
      setEGLContextClientVersion(2);
      r=new GameRenderer(c);
      setRenderer(r);
      setRenderMode(RENDERMODE_CONTINUOUSLY);
    }
  }

  static class HudView extends View {
    final GameView game;
    final Paint p=new Paint(3);
    final Paint stroke=new Paint(3);
    Bitmap[] hilts=new Bitmap[6], blades=new Bitmap[6];
    RectF lockRect=new RectF(),saberMenuRect=new RectF(),rackRect=new RectF(),confirmRect=new RectF(),cancelRect=new RectF();
    RectF[] hiltChoices=new RectF[6],bladeChoices=new RectF[6];
    float englishCx,englishCy,englishR;
    boolean touchingEnglish=false,menuOpen=false,camGesture=false,pullingHilt=false;
    float camPrevDist=0,camPrevMidX=0,camPrevMidY=0,hiltPullStartX=0,hiltPullStartY=0;

    final String[] hiltFiles={"hilt_default.png","hilt_2.png","hilt_crystal.png","hilt_double.png","hilt_classic.png","hilt_weathered.png"};
    final String[] bladeFiles={"blade_dark.png","blade_gold.png","blade_purple.png","blade_green.png","blade_red.png","blade_blue.png"};
    final String[] hiltNames={"DEFAULT","ALT","CRYSTAL","DOUBLE","CLASSIC","WEATHERED"};
    final String[] bladeNames={"DARK","GOLD","PURPLE","GREEN","RED","BLUE"};

    HudView(Context c,GameView g){
      super(c);game=g;setLayerType(View.LAYER_TYPE_SOFTWARE,null);
      stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(4);
      for(int i=0;i<6;i++){hilts[i]=loadHorizontal(c,hiltFiles[i]);blades[i]=loadBlade(c,bladeFiles[i]);hiltChoices[i]=new RectF();bladeChoices[i]=new RectF();}
    }

    Bitmap loadHorizontal(Context c,String n){
      try(InputStream in=c.getAssets().open("ui/"+n)){
        Bitmap b=BitmapFactory.decodeStream(in);
        if(b!=null && b.getHeight()>b.getWidth()){
          android.graphics.Matrix m=new android.graphics.Matrix();m.postRotate(90);
          return Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);
        }
        return b;
      }catch(Exception e){return null;}
    }

    Bitmap loadBlade(Context c,String n){
      try(InputStream in=c.getAssets().open("ui/"+n)){
        Bitmap b=BitmapFactory.decodeStream(in);if(b==null)return null;
        int minX=b.getWidth(),minY=b.getHeight(),maxX=-1,maxY=-1;
        for(int yy=0;yy<b.getHeight();yy+=2)for(int xx=0;xx<b.getWidth();xx+=2){
          if(Color.alpha(b.getPixel(xx,yy))>10){if(xx<minX)minX=xx;if(xx>maxX)maxX=xx;if(yy<minY)minY=yy;if(yy>maxY)maxY=yy;}
        }
        if(maxX>=minX&&maxY>=minY){
          minX=Math.max(0,minX-3);minY=Math.max(0,minY-3);
          maxX=Math.min(b.getWidth()-1,maxX+3);maxY=Math.min(b.getHeight()-1,maxY+3);
          b=Bitmap.createBitmap(b,minX,minY,maxX-minX+1,maxY-minY+1);
        }
        if(b.getHeight()>b.getWidth()){
          android.graphics.Matrix m=new android.graphics.Matrix();m.postRotate(90);
          b=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);
        }
        return b;
      }catch(Exception e){return null;}
    }

    protected void onDraw(Canvas c){
      super.onDraw(c);
      int w=getWidth(),h=getHeight(); GameRenderer r=game.r;
      float ui=Math.max(.90f,Math.min(w/900f,h/640f));

      saberMenuRect.set(28*ui,24*ui,190*ui,78*ui);
      rackRect.set(202*ui,24*ui,352*ui,78*ui);
      lockRect.set(w-142*ui,24*ui,w-28*ui,138*ui);

      p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);
      drawButton(c,saberMenuRect,"SABER MENU",17*ui,0xCC10151F);
      drawButton(c,rackRect,"NEW RACK",17*ui,0xCC3A1010);

      if(r.state==GameRenderer.AIMING){
        drawWorldShotHilt(c,w,h,ui,r);
        p.setTextSize(19*ui);p.setColor(0xEEFFFFFF);
        c.drawText("AIM WITH ONE FINGER",w*.5f,42*ui,p);
      } else if(r.state==GameRenderer.SELECTING_ENGLISH){
        drawEnglish(c,w,h,ui,r);
      } else if(r.state==GameRenderer.CHARGING){
        drawWorldShotHilt(c,w,h,ui,r);
      } else {
        p.setTextSize(20*ui);p.setColor(0xEEFFFFFF);
        c.drawText("BALLS ROLLING",w*.5f,42*ui,p);
      }
      p.setTextSize(13*ui);p.setColor(0xBBD1D5DB);p.setTextAlign(Paint.Align.CENTER);
      c.drawText("2 FINGERS: ORBIT CAMERA   •   PINCH: ZOOM",w*.5f,h-12*ui,p);
      if(menuOpen)drawSaberMenu(c,w,h,ui,r);
      postInvalidateOnAnimation();
    }

    void drawButton(Canvas c,RectF rr,String text,float fs,int bg){
      p.setStyle(Paint.Style.FILL);p.setColor(bg);c.drawRoundRect(rr,14,14,p);
      stroke.setColor(0xAAFFFFFF);stroke.setStrokeWidth(2);c.drawRoundRect(rr,14,14,stroke);
      p.setColor(Color.WHITE);p.setTextSize(fs);p.setTextAlign(Paint.Align.CENTER);
      c.drawText(text,rr.centerX(),rr.centerY()+fs*.34f,p);
    }

    void drawSaberMenu(Canvas c,int w,int h,float ui,GameRenderer r){
      float pw=Math.min(w*.82f,1080*ui),ph=Math.min(h*.64f,430*ui),x=w*.5f-pw*.5f,y=h*.5f-ph*.5f;
      RectF panel=new RectF(x,y,x+pw,y+ph);
      p.setColor(0xF010141C);p.setStyle(Paint.Style.FILL);c.drawRoundRect(panel,24,24,p);
      stroke.setColor(0xFFE4B84D);stroke.setStrokeWidth(3*ui);c.drawRoundRect(panel,24,24,stroke);
      p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(22*ui);p.setColor(Color.WHITE);
      c.drawText("GALACTIC SABER LOADOUT",w*.5f,y+34*ui,p);
      p.setTextSize(14*ui);p.setColor(0xFFD1D5DB);c.drawText("HILT",x+45*ui,y+75*ui,p);c.drawText("BLADE",x+45*ui,y+235*ui,p);
      float gap=12*ui,cw=(pw-56*ui-gap*5)/6f;
      for(int i=0;i<6;i++){
        float lx=x+28*ui+i*(cw+gap);
        hiltChoices[i].set(lx,y+88*ui,lx+cw,y+198*ui);
        bladeChoices[i].set(lx,y+248*ui,lx+cw,y+360*ui);
        int hb=(i==r.hiltIndex)?0xCC5B4514:0xAA171C25;
        int bb=(i==r.bladeIndex)?0xCC5B4514:0xAA171C25;
        p.setColor(hb);c.drawRoundRect(hiltChoices[i],12,12,p);
        p.setColor(bb);c.drawRoundRect(bladeChoices[i],12,12,p);
        stroke.setColor(i==r.hiltIndex?0xFFF4C542:0x667A8494);stroke.setStrokeWidth(i==r.hiltIndex?3*ui:1.5f*ui);c.drawRoundRect(hiltChoices[i],12,12,stroke);
        stroke.setColor(i==r.bladeIndex?0xFFF4C542:0x667A8494);stroke.setStrokeWidth(i==r.bladeIndex?3*ui:1.5f*ui);c.drawRoundRect(bladeChoices[i],12,12,stroke);
        if(hilts[i]!=null){RectF img=new RectF(hiltChoices[i].left+5*ui,hiltChoices[i].top+19*ui,hiltChoices[i].right-5*ui,hiltChoices[i].bottom-27*ui);c.drawBitmap(hilts[i],null,img,p);}
        if(blades[i]!=null){RectF img=new RectF(bladeChoices[i].left+7*ui,bladeChoices[i].top+32*ui,bladeChoices[i].right-7*ui,bladeChoices[i].top+57*ui);c.drawBitmap(blades[i],null,img,p);}
        p.setTextSize(10*ui);p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);
        c.drawText(hiltNames[i],hiltChoices[i].centerX(),hiltChoices[i].bottom-9*ui,p);
        c.drawText(bladeNames[i],bladeChoices[i].centerX(),bladeChoices[i].bottom-9*ui,p);
      }
      p.setTextSize(13*ui);p.setColor(0xFFB9C1CC);c.drawText("Tap SABER MENU again to close",w*.5f,y+ph-18*ui,p);
    }

    void drawCrosshairButton(Canvas c,RectF rr,float ui){
      p.setColor(0xDD111827);p.setStyle(Paint.Style.FILL);c.drawOval(rr,p);
      stroke.setStrokeWidth(4*ui);stroke.setColor(0xFFF4C542);c.drawOval(rr,stroke);
      float cx=rr.centerX(),cy=rr.centerY(),rad=rr.width()*.28f;
      stroke.setColor(0xFFFFFFFF);stroke.setStrokeWidth(3*ui);
      c.drawCircle(cx,cy,rad,stroke);c.drawLine(cx-rad*1.45f,cy,cx+rad*1.45f,cy,stroke);c.drawLine(cx,cy-rad*1.45f,cx,cy+rad*1.45f,stroke);
      p.setTextSize(13*ui);p.setColor(0xFFF4C542);p.setTextAlign(Paint.Align.CENTER);c.drawText("LOCK",cx,rr.bottom-10*ui,p);
    }

    void drawEnglish(Canvas c,int w,int h,float ui,GameRenderer r){
      boolean portrait=h>w;englishCx=portrait?w*.5f:w*.79f;englishCy=portrait?h*.56f:h*.48f;englishR=Math.min(portrait?w*.31f:h*.205f,145*ui);
      p.setColor(0xC8000000);c.drawRoundRect(new RectF(englishCx-englishR-28*ui,englishCy-englishR-54*ui,englishCx+englishR+28*ui,englishCy+englishR+126*ui),24,24,p);
      p.setColor(0xFFF5F5F5);c.drawCircle(englishCx,englishCy,englishR,p);
      stroke.setStrokeWidth(4*ui);stroke.setColor(0xFF9CA3AF);c.drawCircle(englishCx,englishCy,englishR,stroke);
      stroke.setStrokeWidth(2*ui);stroke.setColor(0x55374151);
      c.drawLine(englishCx-englishR,englishCy,englishCx+englishR,englishCy,stroke);c.drawLine(englishCx,englishCy-englishR,englishCx,englishCy+englishR,stroke);
      float dx=r.englishX*englishR*.82f,dy=-r.englishY*englishR*.82f;
      p.setColor(0xFF111827);c.drawCircle(englishCx+dx,englishCy+dy,13*ui,p);
      p.setColor(0xFFF4C542);c.drawCircle(englishCx+dx,englishCy+dy,7*ui,p);
      p.setTextSize(19*ui);p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);
      c.drawText("ENGLISH",englishCx,englishCy-englishR-18*ui,p);
      p.setTextSize(13*ui);p.setColor(0xFFD1D5DB);
      c.drawText("Tap cue ball • top / back / left / right",englishCx,englishCy+englishR+24*ui,p);
      confirmRect.set(englishCx-englishR,englishCy+englishR+42*ui,englishCx+englishR,englishCy+englishR+92*ui);
      cancelRect.set(englishCx-englishR,englishCy+englishR+98*ui,englishCx+englishR,englishCy+englishR+137*ui);
      drawButton(c,confirmRect,"LOCK ENGLISH",18*ui,0xDD0B3A2E);
      drawButton(c,cancelRect,"CANCEL",14*ui,0xCC3A1010);
    }

    RectF worldHiltRect=new RectF();
    float worldHiltAngle=0,worldCueX=0,worldCueY=0,worldDirX=1,worldDirY=0;

    void updateWorldHiltGeometry(int w,int h,GameRenderer r,float pullPx){
      float[] cue=r.worldToScreen(r.balls.size()>0?r.balls.get(0).x:-20f,2.45f,r.balls.size()>0?r.balls.get(0).z:0f,w,h);
      if(cue==null){worldHiltRect.setEmpty();return;}
      float backWorld=14.2f + pullPx/Math.max(10f,h*.030f);
      float[] hp=r.worldToScreen(r.balls.get(0).x-r.aimX*backWorld,2.45f,r.balls.get(0).z-r.aimZ*backWorld,w,h);
      if(hp==null){worldHiltRect.setEmpty();return;}
      worldCueX=cue[0];worldCueY=cue[1];
      float dx=cue[0]-hp[0],dy=cue[1]-hp[1],d=(float)Math.sqrt(dx*dx+dy*dy);
      if(d<1){dx=1;dy=0;d=1;}
      worldDirX=dx/d;worldDirY=dy/d;worldHiltAngle=(float)Math.toDegrees(Math.atan2(dy,dx));
      Bitmap hb=hilts[Math.max(0,Math.min(5,r.hiltIndex))];
      float len=Math.max(125f,h*.132f);
      float aspect=(hb!=null&&hb.getHeight()>0)?((float)hb.getWidth()/hb.getHeight()):1.70f;
      float thick=len/Math.max(1.15f,aspect);
      float cx=hp[0],cy=hp[1];
      worldHiltRect.set(cx-len*.5f,cy-thick*.5f,cx+len*.5f,cy+thick*.5f);
    }

    void drawWorldShotHilt(Canvas c,int w,int h,float ui,GameRenderer r){
      float pull=(r.state==GameRenderer.CHARGING)?r.chargePullPx:0;
      updateWorldHiltGeometry(w,h,r,pull);
      if(worldHiltRect.isEmpty())return;
      Bitmap hilt=hilts[r.hiltIndex],blade=blades[r.bladeIndex];
      c.save();
      c.rotate(worldHiltAngle,worldHiltRect.centerX(),worldHiltRect.centerY());
      if(hilt!=null)c.drawBitmap(hilt,null,worldHiltRect,p);
      c.restore();

      if(r.state==GameRenderer.CHARGING && r.power>0.1f){
        // Blade grows from the hilt emitter toward the cue ball as the hilt is pulled back.
        float ex=worldHiltRect.centerX()+worldDirX*worldHiltRect.width()*.48f;
        float ey=worldHiltRect.centerY()+worldDirY*worldHiltRect.width()*.48f;
        float full=(float)Math.sqrt((worldCueX-ex)*(worldCueX-ex)+(worldCueY-ey)*(worldCueY-ey));
        float endX=ex+(worldCueX-ex)*(r.power/100f),endY=ey+(worldCueY-ey)*(r.power/100f);
        float ang=(float)Math.toDegrees(Math.atan2(endY-ey,endX-ex));
        float len=(float)Math.sqrt((endX-ex)*(endX-ex)+(endY-ey)*(endY-ey));
        float aspect=(blade!=null&&blade.getHeight()>0)?((float)blade.getWidth()/blade.getHeight()):7f;
        float thick=Math.max(18f,Math.min(h*.060f,len/Math.max(3f,aspect)));
        RectF bladeDst=new RectF(ex,ey-thick*.5f,ex+Math.max(2,len),ey+thick*.5f);
        c.save();c.rotate(ang,ex,ey);
        if(blade!=null)c.drawBitmap(blade,null,bladeDst,p);
        c.restore();
        p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(Math.max(20f,h*.026f));p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);
        c.drawText(Math.round(r.power)+"%",worldHiltRect.centerX(),worldHiltRect.centerY()-worldHiltRect.height()*.80f,p);
      }

      if(r.state==GameRenderer.AIMING){
        p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(Math.max(16f,h*.020f));p.setColor(0xEEF4C542);p.setTextAlign(Paint.Align.CENTER);
        c.drawText("PULL HILT BACK FOR ENGLISH",worldHiltRect.centerX(),worldHiltRect.centerY()-worldHiltRect.height()*.82f,p);
      } else if(r.state==GameRenderer.CHARGING){
        p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(Math.max(16f,h*.020f));p.setColor(0xEEFFFFFF);p.setTextAlign(Paint.Align.CENTER);
        c.drawText("PULL BACK • RELEASE TO STRIKE",worldHiltRect.centerX(),worldHiltRect.centerY()+worldHiltRect.height()*1.15f,p);
      }
    }

    public boolean onTouchEvent(MotionEvent e){
      final int a=e.getActionMasked();final float x=e.getX(),y=e.getY();final int w=getWidth(),h=getHeight();
      final float ui=Math.max(.90f,Math.min(w/900f,h/640f));
      final GameRenderer r=game.r;

      // Two-finger camera control: orbit + pinch zoom.
      if(e.getPointerCount()>=2 || camGesture){
        if((a==MotionEvent.ACTION_POINTER_DOWN||a==MotionEvent.ACTION_DOWN) && e.getPointerCount()>=2){
          camGesture=true;pullingHilt=false;
          float x0=e.getX(0),y0=e.getY(0),x1=e.getX(1),y1=e.getY(1);
          camPrevMidX=(x0+x1)*.5f;camPrevMidY=(y0+y1)*.5f;
          float dx=x1-x0,dy=y1-y0;camPrevDist=(float)Math.sqrt(dx*dx+dy*dy);
          return true;
        }
        if(a==MotionEvent.ACTION_MOVE && e.getPointerCount()>=2){
          float x0=e.getX(0),y0=e.getY(0),x1=e.getX(1),y1=e.getY(1);
          float midX=(x0+x1)*.5f,midY=(y0+y1)*.5f,dx=x1-x0,dy=y1-y0;
          float dist=(float)Math.sqrt(dx*dx+dy*dy);
          final float dragX=midX-camPrevMidX,dragY=midY-camPrevMidY;
          final float pinch=(camPrevDist>8)?dist/camPrevDist:1f;
          camPrevMidX=midX;camPrevMidY=midY;camPrevDist=dist;
          game.queueEvent(()->r.cameraGesture(dragX,dragY,pinch));return true;
        }
        if(a==MotionEvent.ACTION_POINTER_UP||a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){
          if(e.getPointerCount()<=2)camGesture=false;return true;
        }
      }

      if(a==MotionEvent.ACTION_DOWN){
        if(saberMenuRect.contains(x,y)){menuOpen=!menuOpen;return true;}
        if(rackRect.contains(x,y)){menuOpen=false;pullingHilt=false;game.queueEvent(()->r.resetRack());return true;}
        if(menuOpen){
          for(int i=0;i<6;i++){
            if(hiltChoices[i].contains(x,y)){final int k=i;game.queueEvent(()->r.hiltIndex=k);return true;}
            if(bladeChoices[i].contains(x,y)){final int k=i;game.queueEvent(()->r.bladeIndex=k);return true;}
          }
          return true;
        }

        if(r.state==GameRenderer.AIMING){
          updateWorldHiltGeometry(w,h,r,0);
          RectF hit=new RectF(worldHiltRect);hit.inset(-28*ui,-28*ui);
          if(hit.contains(x,y)){
            pullingHilt=true;hiltPullStartX=x;hiltPullStartY=y;return true;
          }
        }

        if(r.state==GameRenderer.SELECTING_ENGLISH){
          float dx=x-englishCx,dy=y-englishCy;
          if(dx*dx+dy*dy<=englishR*englishR){touchingEnglish=true;setEnglishFromTouch(x,y);return true;}
          if(confirmRect.contains(x,y)){game.queueEvent(()->r.confirmEnglish());return true;}
          if(cancelRect.contains(x,y)){game.queueEvent(()->r.cancelEnglish());return true;}
        }

        if(r.state==GameRenderer.CHARGING){
          updateWorldHiltGeometry(w,h,r,r.chargePullPx);
          RectF hit=new RectF(worldHiltRect);hit.inset(-36*ui,-36*ui);
          if(hit.contains(x,y)){pullingHilt=true;hiltPullStartX=x;hiltPullStartY=y;game.queueEvent(()->r.beginWorldCharge());return true;}
        }
      }

      if(pullingHilt){
        if(a==MotionEvent.ACTION_MOVE){
          float dx=x-hiltPullStartX,dy=y-hiltPullStartY;
          // Pull distance is measured opposite the cue direction on screen.
          float pull=-(dx*worldDirX+dy*worldDirY);
          if(r.state==GameRenderer.CHARGING){
            final float fp=Math.max(0,pull);game.queueEvent(()->r.updateWorldCharge(fp,h));
          }
          return true;
        }
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){
          float dx=x-hiltPullStartX,dy=y-hiltPullStartY;
          float pull=-(dx*worldDirX+dy*worldDirY);
          pullingHilt=false;
          if(r.state==GameRenderer.AIMING){
            if(pull>Math.max(55f,h*.065f))game.queueEvent(()->r.lockAngle());
          } else if(r.state==GameRenderer.CHARGING){
            game.queueEvent(()->r.releaseWorldCharge());
          }
          return true;
        }
      }

      if(menuOpen)return true;
      if(r.state==GameRenderer.SELECTING_ENGLISH && touchingEnglish){
        if(a==MotionEvent.ACTION_MOVE)setEnglishFromTouch(x,y);
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL)touchingEnglish=false;
        return true;
      }
      if(r.state==GameRenderer.AIMING){
        game.queueEvent(()->r.aimTouch(a,x,y,w,h));return true;
      }
      return true;
    }

    void setEnglishFromTouch(float x,float y){
      float dx=(x-englishCx)/englishR,dy=(y-englishCy)/englishR;
      float d=(float)Math.sqrt(dx*dx+dy*dy);if(d>1){dx/=d;dy/=d;}
      final float ex=dx,ey=-dy;game.queueEvent(()->game.r.setEnglish(ex,ey));
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
    float x,z,vx,vz,spin,rotX,rotZ; boolean active=true; int tex;
    Ball(float X,float Z,int T){x=X;z=Z;tex=T;}
    float speed2(){return vx*vx+vz*vz;}
  }

  static class GameRenderer implements GLSurfaceView.Renderer{
    static final int AIMING=0,SELECTING_ENGLISH=1,CHARGING=2,ROLLING=3;
    Context ctx; ArrayList<Part> table=new ArrayList<>(); ArrayList<Ball> balls=new ArrayList<>();
    Mesh sphere; Mesh[] saberMeshes=new Mesh[6]; int[] saberTextures=new int[6]; HashMap<String,Integer> tex=new HashMap<>();
    int program,aPos,aUv,uMvp,uUseTex,uColor,uTex;
    float aspect=16f/9f; long last=0; float[] pvCache=new float[16];
    volatile float camYaw=0f,camPitch=41f,camDist=128f,camTargetX=0f,camTargetZ=0f;
    volatile int state=AIMING,hiltIndex=0,bladeIndex=5;
    volatile float power=0,englishX=0,englishY=0;
    float aimX=1,aimZ=0,desiredAimX=1,desiredAimZ=0,chargeStartY=-1,sideSpin=0,topSpin=0; volatile float chargePullPx=0; boolean breakAssistArmed=true;
    float physicsAccum=0f;
    static final float FIXED_DT=1f/360f;
    static final float BALL_E=.925f;
    static final float RAIL_E=.74f;
    static final float ROLL_DECEL=2.25f;
    static final float STOP_SPEED=.35f;
    static final float CONTACT_EPS=.0010f;
    final float R=1.192f, MINX=-40.808f,MAXX=40.808f,MINZ=-19.808f,MAXZ=19.808f;
    final String[] objectFolders={"00_DeathStar","01_Tatooine","02_Kamino","03_Mustafar","04_Coruscant","05_Geonosis","06_Endor","07_Korriban","08_Exegol","09_Yavin","10_Bespin","11_Malastare","12_Kessel","13_Jakku","14_Felucia","15_Dathomir"};
    final float[][] bladeRgb={{.92f,.95f,1f},{1f,.72f,.18f},{.68f,.28f,1f},{.18f,1f,.42f},{1f,.12f,.10f},{.20f,.66f,1f}};
    final String[] saberFolders={"white","gold","purple","green","red","blue"};

    GameRenderer(Context c){ctx=c;}

    public void onSurfaceCreated(GL10 gl,EGLConfig cfg){
      GLES20.glClearColor(0.002f,0.004f,0.012f,1);
      GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glEnable(GLES20.GL_BLEND);
      GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
      String vs="attribute vec3 aPos;attribute vec2 aUv;uniform mat4 uMvp;varying vec2 vUv;void main(){gl_Position=uMvp*vec4(aPos,1.0);vUv=vec2(aUv.x,1.0-aUv.y);}";
      String fs="precision mediump float;varying vec2 vUv;uniform sampler2D uTex;uniform float uUseTex;uniform vec4 uColor;void main(){vec4 c=uColor;if(uUseTex>0.5)c*=texture2D(uTex,vUv);if(c.a<0.015)discard;gl_FragColor=c;}";
      program=GLES20.glCreateProgram();int sv=shader(GLES20.GL_VERTEX_SHADER,vs),sf=shader(GLES20.GL_FRAGMENT_SHADER,fs);
      GLES20.glAttachShader(program,sv);GLES20.glAttachShader(program,sf);GLES20.glLinkProgram(program);
      aPos=GLES20.glGetAttribLocation(program,"aPos");aUv=GLES20.glGetAttribLocation(program,"aUv");
      uMvp=GLES20.glGetUniformLocation(program,"uMvp");uUseTex=GLES20.glGetUniformLocation(program,"uUseTex");
      uColor=GLES20.glGetUniformLocation(program,"uColor");uTex=GLES20.glGetUniformLocation(program,"uTex");
      loadAssets();resetRack();last=System.nanoTime();
    }

    int shader(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);return s;}
    public void onSurfaceChanged(GL10 gl,int w,int h){GLES20.glViewport(0,0,w,h);aspect=(float)w/Math.max(1,h);}

    public void onDrawFrame(GL10 gl){
      long now=System.nanoTime();float dt=Math.min(.033f,(now-last)/1_000_000_000f);last=now;
      step(dt);
      if(state==AIMING){
        float k=.085f;
        aimX=aimX*(1f-k)+desiredAimX*k;aimZ=aimZ*(1f-k)+desiredAimZ*k;
        float an=(float)Math.sqrt(aimX*aimX+aimZ*aimZ);if(an>.0001f){aimX/=an;aimZ/=an;}
      }
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);GLES20.glUseProgram(program);
      float[] P=new float[16],V=new float[16];
      android.opengl.Matrix.perspectiveM(P,0,40,aspect,.1f,320f);
      float viewYaw=camYaw+(aspect<1f?90f:0f);float viewDist=camDist*(aspect<1f?1.03f:1f);
      float yaw=(float)Math.toRadians(viewYaw),pitch=(float)Math.toRadians(camPitch),flat=(float)Math.cos(pitch)*viewDist;
      float cx=camTargetX+(float)Math.sin(yaw)*flat,cy=-1f+(float)Math.sin(pitch)*viewDist,cz=camTargetZ+(float)Math.cos(yaw)*flat;
      android.opengl.Matrix.setLookAtM(V,0,cx,cy,cz,camTargetX,-1f,camTargetZ,0,1,0);
      android.opengl.Matrix.multiplyMM(pvCache,0,P,0,V,0);
      for(Part p:table)drawMesh(p.mesh,pvCache,identity(),p.texKey==null?0:tex.getOrDefault(p.texKey,0),p.color);
      if(state!=ROLLING)drawPredictor(pvCache);
      for(Ball b:balls)if(b.active){float[] M=identity();android.opengl.Matrix.translateM(M,0,b.x,2.22f,b.z);android.opengl.Matrix.rotateM(M,0,b.rotX,1,0,0);android.opengl.Matrix.rotateM(M,0,b.rotZ,0,0,1);android.opengl.Matrix.scaleM(M,0,R,R,R);drawMesh(sphere,pvCache,M,b.tex,new float[]{1,1,1,1});}
    }

    void drawMesh(Mesh m,float[] pv,float[] model,int texture,float[] color){
      if(m==null)return;float[] mvp=new float[16];android.opengl.Matrix.multiplyMM(mvp,0,pv,0,model,0);
      GLES20.glUniformMatrix4fv(uMvp,1,false,mvp,0);GLES20.glUniform4fv(uColor,1,color,0);GLES20.glUniform1f(uUseTex,texture!=0?1f:0f);
      if(texture!=0){GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);GLES20.glUniform1i(uTex,0);}
      GLES20.glEnableVertexAttribArray(aPos);GLES20.glVertexAttribPointer(aPos,3,GLES20.GL_FLOAT,false,0,m.pos);
      GLES20.glEnableVertexAttribArray(aUv);GLES20.glVertexAttribPointer(aUv,2,GLES20.GL_FLOAT,false,0,m.uv);
      GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,m.count);GLES20.glDisableVertexAttribArray(aPos);GLES20.glDisableVertexAttribArray(aUv);
    }

    void loadAssets(){
      try{
        tex.put("felt",loadTexture(findAsset("extracted","_Felt.png")));tex.put("wood",loadTexture(findAsset("extracted","_Wood.png")));tex.put("foot",loadTexture(findAsset("extracted","_Foot.png")));
        addTable("1631477688504185304_default.obj","felt",1,1,1);addTable("106991748306668190_default.obj","wood",1,1,1);
        addTable("-4426823995715296130_default.obj","wood",1,1,1);addTable("-2382927779929703425_default.obj","wood",1,1,1);
        addTable("327116407711713708_default.obj","foot",1,1,1);
        addTable("-7406165369254709877_default.obj",null,.376f,.376f,.376f);addTable("-1542075189251850320_default.obj",null,.376f,.376f,.376f);
        addTable("-4920293310515908916_default.obj",null,.063f,.063f,.063f);addTable("6851484603853778718_default.obj",null,.9f,.92f,.96f);addTable("6459398429916909974_default.obj",null,.8f,.8f,.8f);
        sphere=loadObj("objects/01_Tatooine/-8750451297455424342_default.obj");
        for(int i=0;i<objectFolders.length;i++)tex.put("ball"+i,loadTexture(findAsset("objects/"+objectFolders[i],".png")));
        for(int i=0;i<6;i++){
          saberMeshes[i]=null;
          try{saberTextures[i]=loadSaberTexture("sabers/"+saberFolders[i]+"/blade.png");}catch(Exception e){saberTextures[i]=0;}
        }
      }catch(Exception e){e.printStackTrace();}
    }

    void addTable(String file,String key,float r,float g,float b)throws Exception{table.add(new Part(loadObj("extracted/"+file),key,r,g,b));}
    String findAsset(String folder,String suffix)throws Exception{String[] a=ctx.getAssets().list(folder);if(a!=null)for(String n:a)if(n.endsWith(suffix))return folder+"/"+n;throw new IOException("Missing "+suffix+" in "+folder);}

    int loadTexture(String path)throws Exception{
      Bitmap bmp;try(InputStream in=ctx.getAssets().open(path)){bmp=BitmapFactory.decodeStream(in);}if(bmp==null)throw new IOException(path);
      int[] id=new int[1];GLES20.glGenTextures(1,id,0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,id[0]);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR_MIPMAP_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_REPEAT);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_REPEAT);
      GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bmp,0);GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
      String ext=GLES20.glGetString(GLES20.GL_EXTENSIONS);
      if(ext!=null&&ext.contains("GL_EXT_texture_filter_anisotropic")){
        float[] maxA=new float[1];GLES20.glGetFloatv(0x84FF,maxA,0);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,0x84FE,Math.min(8f,maxA[0]));
      }
      bmp.recycle();return id[0];
    }

    int loadSaberTexture(String path)throws Exception{
      Bitmap bmp;try(InputStream in=ctx.getAssets().open(path)){bmp=BitmapFactory.decodeStream(in);}if(bmp==null)throw new IOException(path);
      int[] id=new int[1];GLES20.glGenTextures(1,id,0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,id[0]);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
      GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bmp,0);bmp.recycle();return id[0];
    }

    Mesh loadObj(String path)throws Exception{
      ArrayList<float[]> verts=new ArrayList<>(),uvs=new ArrayList<>();ArrayList<Float> po=new ArrayList<>(),to=new ArrayList<>();
      try(BufferedReader br=new BufferedReader(new InputStreamReader(ctx.getAssets().open(path)))){String l;while((l=br.readLine())!=null){
        if(l.startsWith("v ")){String[] q=l.trim().split("\\s+");verts.add(new float[]{Float.parseFloat(q[1]),Float.parseFloat(q[2]),Float.parseFloat(q[3])});}
        else if(l.startsWith("vt ")){String[] q=l.trim().split("\\s+");uvs.add(new float[]{Float.parseFloat(q[1]),Float.parseFloat(q[2])});}
        else if(l.startsWith("f ")){String[] q=l.trim().split("\\s+");for(int k=2;k<q.length-1;k++){appendFace(q[1],verts,uvs,po,to);appendFace(q[k],verts,uvs,po,to);appendFace(q[k+1],verts,uvs,po,to);}}
      }}
      float[] p=new float[po.size()],t=new float[to.size()];for(int i=0;i<p.length;i++)p[i]=po.get(i);for(int i=0;i<t.length;i++)t[i]=to.get(i);return new Mesh(p,t);
    }

    void appendFace(String token,ArrayList<float[]> v,ArrayList<float[]> uv,ArrayList<Float> po,ArrayList<Float> to){
      String[] a=token.split("/");int vi=parseIndex(a[0],v.size());float[] p=v.get(vi);po.add(p[0]);po.add(p[1]);po.add(p[2]);
      float u=0,w=0;if(a.length>1&&!a[1].isEmpty()){int ti=parseIndex(a[1],uv.size());if(ti>=0&&ti<uv.size()){u=uv.get(ti)[0];w=uv.get(ti)[1];}}to.add(u);to.add(w);
    }
    int parseIndex(String s,int size){int i=Integer.parseInt(s);return i<0?size+i:i-1;}

    void cameraGesture(float dragX,float dragY,float pinch){
      camYaw-=dragX*.16f;
      camPitch=Math.max(18f,Math.min(78f,camPitch+dragY*.12f));
      if(pinch>.01f)camDist=Math.max(58f,Math.min(190f,camDist/pinch));
    }

    float[] worldToScreen(float x,float y,float z,int w,int h){
      float[] v={x,y,z,1},clip=new float[4];android.opengl.Matrix.multiplyMV(clip,0,pvCache,0,v,0);
      if(clip[3]<=.001f)return null;
      float nx=clip[0]/clip[3],ny=clip[1]/clip[3];
      return new float[]{(nx*.5f+.5f)*w,(.5f-ny*.5f)*h};
    }

    void resetRack(){
      balls.clear();physicsAccum=0;
      balls.add(new Ball(-20f,0f,tex.getOrDefault("ball0",0)));

      // Tight triangle with a small non-overlap gap and microscopic deterministic
      // asymmetry so a dead-center break does not become an artificial Newton cradle.
      float S=2*R+.0018f,HX=S*.5f,DX=S*.8660254f;
      float[][] p={{20f,0f},{20f+DX,-HX-.002f},{20f+DX,HX+.002f},
        {20f+2*DX,-S-.003f},{20f+2*DX,.003f},{20f+2*DX,S+.001f},
        {20f+3*DX,-3*HX-.002f},{20f+3*DX,-HX+.002f},{20f+3*DX,HX-.001f},{20f+3*DX,3*HX+.003f},
        {20f+4*DX,-2*S-.002f},{20f+4*DX,-S+.003f},{20f+4*DX,-.002f},{20f+4*DX,S+.001f},{20f+4*DX,2*S+.003f}};
      for(int i=0;i<15;i++)balls.add(new Ball(p[i][0],p[i][1],tex.getOrDefault("ball"+(i+1),0)));
      state=AIMING;power=0;chargePullPx=0;englishX=englishY=0;aimX=desiredAimX=1;aimZ=desiredAimZ=0;sideSpin=topSpin=0;
    }

    void aimTouch(int action,float sx,float sy,int w,int h){
      if(state!=AIMING||!allStopped())return;
      if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP){
        float[] q=screenToTable(sx,sy,w,h);if(q!=null){Ball cue=balls.get(0);float dx=q[0]-cue.x,dz=q[1]-cue.z;float d=(float)Math.sqrt(dx*dx+dz*dz);if(d>2.0f){desiredAimX=dx/d;desiredAimZ=dz/d;}}
      }
    }

    float[] screenToTable(float sx,float sy,int w,int h){
      float nx=2f*sx/w-1f,ny=1f-2f*sy/h;float[] inv=new float[16];if(!android.opengl.Matrix.invertM(inv,0,pvCache,0))return null;
      float[] a={nx,ny,-1,1},b={nx,ny,1,1},wa=new float[4],wb=new float[4];android.opengl.Matrix.multiplyMV(wa,0,inv,0,a,0);android.opengl.Matrix.multiplyMV(wb,0,inv,0,b,0);
      for(int i=0;i<3;i++){wa[i]/=wa[3];wb[i]/=wb[3];}
      float dy=wb[1]-wa[1];if(Math.abs(dy)<1e-5)return null;float t=(2.22f-wa[1])/dy;if(t<0)return null;
      return new float[]{wa[0]+(wb[0]-wa[0])*t,wa[2]+(wb[2]-wa[2])*t};
    }

    void lockAngle(){if(state==AIMING&&allStopped()){state=SELECTING_ENGLISH;power=0;englishX=englishY=0;}}
    void setEnglish(float x,float y){englishX=Math.max(-1,Math.min(1,x));englishY=Math.max(-1,Math.min(1,y));}
    void confirmEnglish(){if(state==SELECTING_ENGLISH){state=CHARGING;power=0;chargePullPx=0;chargeStartY=-1;}}
    void cancelEnglish(){if(state==SELECTING_ENGLISH){state=AIMING;power=0;chargePullPx=0;englishX=englishY=0;}}

    void beginWorldCharge(){if(state==CHARGING){power=0;chargePullPx=0;}}
    void updateWorldCharge(float pullPx,int h){
      if(state!=CHARGING)return;chargePullPx=pullPx;
      power=Math.min(100f,pullPx/Math.max(85f,h*.18f)*100f);
    }
    void releaseWorldCharge(){
      if(state!=CHARGING)return;
      if(power>=5)executeShot();else{power=0;chargePullPx=0;}
    }

    void executeShot(){
      Ball cue=balls.get(0);
      if(!cue.active){cue.active=true;cue.x=-20;cue.z=0;}
      float speed=power*1.60f;
      cue.vx=aimX*speed;cue.vz=aimZ*speed;
      cue.spin=englishX*speed*.06f;
      sideSpin=englishX;topSpin=englishY;
      state=ROLLING;power=0;chargePullPx=0;chargeStartY=-1;
    }

    void advanceBalls(float dt){
      for(Ball b:balls)if(b.active){
        float ox=b.x,oz=b.z;
        b.x+=b.vx*dt;b.z+=b.vz*dt;
        b.rotX+=(b.z-oz)/R*57.29578f;b.rotZ-=(b.x-ox)/R*57.29578f;

        float sp=(float)Math.sqrt(b.speed2());
        if(sp>0){
          // Constant rolling resistance, scaled up slightly at high speed to remove
          // the air-hockey glide without killing the break.
          float dec=ROLL_DECEL*(1f+.0035f*sp);
          float ns=Math.max(0f,sp-dec*dt);
          if(ns<STOP_SPEED){b.vx=b.vz=0;}
          else{float q=ns/sp;b.vx*=q;b.vz*=q;}
        }
        b.spin*=Math.pow(.26,dt);
      }
    }

    float ballCollisionTime(Ball a,Ball b,float maxT){
      if(!a.active||!b.active)return Float.POSITIVE_INFINITY;
      float rx=b.x-a.x,rz=b.z-a.z,rvx=b.vx-a.vx,rvz=b.vz-a.vz;
      float target=2*R+CONTACT_EPS;
      float A=rvx*rvx+rvz*rvz;
      float B=2*(rx*rvx+rz*rvz);
      float C=rx*rx+rz*rz-target*target;
      if(C<=0)return B<-.00001f?0f:Float.POSITIVE_INFINITY;
      if(A<1e-8f||B>=0)return Float.POSITIVE_INFINITY;
      float D=B*B-4*A*C;if(D<0)return Float.POSITIVE_INFINITY;
      float t=(-B-(float)Math.sqrt(D))/(2*A);
      return (t>=0&&t<=maxT)?t:Float.POSITIVE_INFINITY;
    }

    void separate(Ball a,Ball b){
      float dx=b.x-a.x,dz=b.z-a.z,d=(float)Math.sqrt(dx*dx+dz*dz);
      if(d<1e-6f){dx=1;dz=0;d=1;}
      float target=2*R+CONTACT_EPS,over=target-d;
      if(over>0){float nx=dx/d,nz=dz/d;a.x-=nx*over*.5f;a.z-=nz*over*.5f;b.x+=nx*over*.5f;b.z+=nz*over*.5f;}
    }

    void resolveBallCollision(Ball a,Ball b){
      separate(a,b);
      float dx=b.x-a.x,dz=b.z-a.z,d=(float)Math.sqrt(dx*dx+dz*dz);if(d<1e-6f)return;
      float nx=dx/d,nz=dz/d,tx=-nz,tz=nx;
      float v1n=a.vx*nx+a.vz*nz,v2n=b.vx*nx+b.vz*nz;
      float rel=v1n-v2n;if(rel<=0)return;

      // Pooltool/Alciatore-style equal-mass frictional inelastic collision:
      // normal restitution plus a bounded tangential "throw" impulse.
      float nv1=.5f*((1-BALL_E)*v1n+(1+BALL_E)*v2n);
      float nv2=.5f*((1+BALL_E)*v1n+(1-BALL_E)*v2n);
      float v1t=a.vx*tx+a.vz*tz,v2t=b.vx*tx+b.vz*tz;
      float tangRel=(v1t-v2t)+R*(a.spin+b.spin);
      float mu=.009951f+.108f*(float)Math.exp(-1.088f*Math.abs(tangRel));
      float normalChange=Math.abs(nv2-v2n);
      float jt=Math.min(mu*normalChange,Math.abs(tangRel)/7f)*Math.signum(tangRel);
      v1t-=jt;v2t+=jt;
      a.spin-=jt/R*.35f;b.spin-=jt/R*.35f;

      a.vx=nv1*nx+v1t*tx;a.vz=nv1*nz+v1t*tz;
      b.vx=nv2*nx+v2t*tx;b.vz=nv2*nz+v2t*tz;
    }

    void resolveTouchingCluster(){
      for(int pass=0;pass<18;pass++){
        boolean any=false;
        for(int i=0;i<balls.size();i++)for(int j=i+1;j<balls.size();j++){
          Ball a=balls.get(i),b=balls.get(j);if(!a.active||!b.active)continue;
          float dx=b.x-a.x,dz=b.z-a.z,d2=dx*dx+dz*dz;
          if(d2<=(2*R+CONTACT_EPS)*(2*R+CONTACT_EPS)){
            float d=(float)Math.sqrt(Math.max(d2,1e-10f)),nx=dx/d,nz=dz/d;
            if((a.vx-b.vx)*nx+(a.vz-b.vz)*nz>0){resolveBallCollision(a,b);any=true;}
            else separate(a,b);
          }
        }
        if(!any)break;
      }
    }

    void bounceRails(Ball b){
      if(!b.active)return;
      if(b.x-R<MINX){b.x=MINX+R;b.vx=Math.abs(b.vx)*RAIL_E;b.vz*=.965f;b.vz+=b.spin*Math.abs(b.vx)*.05f;}
      if(b.x+R>MAXX){b.x=MAXX-R;b.vx=-Math.abs(b.vx)*RAIL_E;b.vz*=.965f;b.vz-=b.spin*Math.abs(b.vx)*.05f;}
      if(b.z-R<MINZ){b.z=MINZ+R;b.vz=Math.abs(b.vz)*RAIL_E;b.vx*=.965f;b.vx-=b.spin*Math.abs(b.vz)*.05f;}
      if(b.z+R>MAXZ){b.z=MAXZ-R;b.vz=-Math.abs(b.vz)*RAIL_E;b.vx*=.965f;b.vx+=b.spin*Math.abs(b.vz)*.05f;}
    }

    void physicsSlice(float dt){
      float remain=dt;int events=0;
      while(remain>1e-6f&&events<64){
        float best=remain;Ball ca=null,cb=null;
        for(int i=0;i<balls.size();i++)for(int j=i+1;j<balls.size();j++){
          float t=ballCollisionTime(balls.get(i),balls.get(j),best);
          if(t<best){best=t;ca=balls.get(i);cb=balls.get(j);}
        }
        advanceBalls(best);
        for(Ball b:balls)bounceRails(b);
        if(ca!=null){resolveBallCollision(ca,cb);resolveTouchingCluster();events++;}
        else break;
        remain-=best;
        if(best<1e-6f)remain-=1e-5f;
      }
      if(remain>0){advanceBalls(remain);for(Ball b:balls)bounceRails(b);resolveTouchingCluster();}
      for(int i=0;i<balls.size();i++)checkPocket(i,balls.get(i));
    }

    void step(float dt){
      if(balls.isEmpty())return;
      if(state==ROLLING){
        physicsAccum=Math.min(.06f,physicsAccum+dt);
        int loops=0;
        while(physicsAccum>=FIXED_DT&&loops<24){physicsSlice(FIXED_DT);physicsAccum-=FIXED_DT;loops++;}
        if(allStopped()){state=AIMING;englishX=englishY=0;sideSpin=topSpin=0;chargePullPx=0;physicsAccum=0;}
      }
    }

    void checkPocket(int index,Ball b){
      if(!b.active)return;
      float[][] p={{MINX,MINZ},{0,MINZ},{MAXX,MINZ},{MINX,MAXZ},{0,MAXZ},{MAXX,MAXZ}};
      for(float[] q:p){
        float dx=b.x-q[0],dz=b.z-q[1];
        if(dx*dx+dz*dz<8.4f){
          b.vx=b.vz=b.spin=0;
          if(index==0){b.x=-20;b.z=0;b.active=true;}
          else b.active=false;
          return;
        }
      }
    }

    boolean allStopped(){
      for(Ball b:balls)if(b.active&&b.speed2()>STOP_SPEED*STOP_SPEED)return false;
      return true;
    }

    void drawPredictor(float[] pv){
      if(balls.isEmpty()||!balls.get(0).active)return;Ball cue=balls.get(0);float x=cue.x,z=cue.z,dx=aimX,dz=aimZ;
      float[] rgb=bladeRgb[Math.max(0,Math.min(5,bladeIndex))];
      for(int bank=0;bank<4;bank++){
        float railT=9999;int railAxis=0;
        if(dx>1e-5){float t=(MAXX-R-x)/dx;if(t>0&&t<railT){railT=t;railAxis=1;}}
        if(dx<-1e-5){float t=(MINX+R-x)/dx;if(t>0&&t<railT){railT=t;railAxis=1;}}
        if(dz>1e-5){float t=(MAXZ-R-z)/dz;if(t>0&&t<railT){railT=t;railAxis=2;}}
        if(dz<-1e-5){float t=(MINZ+R-z)/dz;if(t>0&&t<railT){railT=t;railAxis=2;}}
        float hitT=railT;Ball hit=null;
        for(int i=1;i<balls.size();i++){Ball b=balls.get(i);if(!b.active)continue;float ox=b.x-x,oz=b.z-z,proj=ox*dx+oz*dz;if(proj<=0)continue;float perp=ox*ox+oz*oz-proj*proj,rr=4*R*R;if(perp>rr)continue;float t=proj-(float)Math.sqrt(Math.max(0,rr-perp));if(t>.03f&&t<hitT){hitT=t;hit=b;}}
        float ex=x+dx*hitT,ez=z+dz*hitT;drawSaberSegment(pv,x,z,ex,ez,rgb[0],rgb[1],rgb[2]);
        if(hit!=null){
          float nx=hit.x-ex,nz=hit.z-ez,nd=(float)Math.sqrt(nx*nx+nz*nz);if(nd>.001){nx/=nd;nz/=nd;}
          float objLen=18f;drawSaberSegment(pv,hit.x,hit.z,hit.x+nx*objLen,hit.z+nz*objLen,1f,.50f,.08f);
          float dot=dx*nx+dz*nz,cx=dx-dot*nx,cz=dz-dot*nz,cd=(float)Math.sqrt(cx*cx+cz*cz);
          if(cd>.08f){cx/=cd;cz/=cd;drawSaberSegment(pv,ex,ez,ex+cx*12f,ez+cz*12f,.2f,.85f,1f);}
          break;
        }
        x=ex;z=ez;
        if(railAxis==1){dx=-dx;dz*=2.80f;}else{dz=-dz;dx*=2.80f;}
        float a=englishX*.13f,cs=(float)Math.cos(a),sn=(float)Math.sin(a),rx=dx*cs-dz*sn,rz=dx*sn+dz*cs;
        float n=(float)Math.sqrt(rx*rx+rz*rz);dx=rx/n;dz=rz/n;x+=dx*.04f;z+=dz*.04f;
      }
    }

    void drawSaberSegment(float[] pv,float x1,float z1,float x2,float z2,float r,float g,float b){
      int idx=Math.max(0,Math.min(5,bladeIndex));
      float dx=x2-x1,dz=z2-z1,len=(float)Math.sqrt(dx*dx+dz*dz);if(len<.02f)return;
      float angle=(float)Math.toDegrees(Math.atan2(-dz,dx));
      int texture=saberTextures[idx];
      float width=3.90f;

      GLES20.glDepthMask(false);GLES20.glDisable(GLES20.GL_DEPTH_TEST);

      // Soft halo.
      GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE);
      drawTexturedBlade(texture,pv,x1,z1,len,angle,width*1.55f,.34f,2.49f);

      // Native AssetBundle texture with its baked WHITE CORE intact.
      GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
      drawTexturedBlade(texture,pv,x1,z1,len,angle,width,1.0f,2.52f);

      GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glDepthMask(true);
    }

    void drawTexturedBlade(int texture,float[] pv,float x,float z,float len,float angle,float width,float alpha,float y){
      float[] v={0,y,-.5f, 0,y,.5f, 1,y,.5f, 0,y,-.5f, 1,y,.5f, 1,y,-.5f};
      // The original AssetBundle PNG is vertical (long axis = V), so map V along
      // segment length and U across blade width. The old mapping rotated/crushed it.
      float[] u={0,1, 1,1, 1,0, 0,1, 1,0, 0,0};
      Mesh q=new Mesh(v,u);
      float[] M=identity();android.opengl.Matrix.translateM(M,0,x,0,z);android.opengl.Matrix.rotateM(M,0,angle,0,1,0);android.opengl.Matrix.scaleM(M,0,len,1,width);
      drawMesh(q,pv,M,texture,new float[]{1,1,1,alpha});
    }

    static float[] identity(){float[] m=new float[16];android.opengl.Matrix.setIdentityM(m,0);return m;}
  }
}
