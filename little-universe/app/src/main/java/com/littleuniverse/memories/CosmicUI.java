package com.littleuniverse.memories;

import android.animation.*;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import java.util.Random;

/** Native, entirely offline blue-and-violet dimensional design system.
 * These are real Android canvas effects and animations, not screenshot overlays.
 */
public final class CosmicUI {
    public static final int BLUE = 0xff397CFF;
    public static final int VIOLET = 0xff915AFF;
    public static final int PALE = 0xffE3DEFF;
    public static final int NIGHT = 0xff090F23;
    private CosmicUI() {}

    static float dp(Context context,float number){
        return number*context.getResources().getDisplayMetrics().density;
    }
    public static Drawable glass(Context ctx,int radius,int top,int bottom,int rim){
        return new GlassDrawable(ctx,radius,top,bottom,rim);
    }
    public static Drawable sphere(Context ctx,int top,int bottom){
        return new SphereDrawable(ctx,top,bottom);
    }
    public static void lift(View view,Context ctx){
        view.setElevation(dp(ctx,7));
        view.setTranslationZ(dp(ctx,2));
        view.setOnTouchListener((v,event)->{
            if(event.getAction()==MotionEvent.ACTION_DOWN){
                v.animate().scaleX(.965f).scaleY(.965f).translationZ(dp(ctx,0)).setDuration(105).start();
            }else if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL){
                v.animate().scaleX(1).scaleY(1).translationZ(dp(ctx,2)).setDuration(170)
                    .setInterpolator(new DecelerateInterpolator()).start();
            }
            return false;
        });
    }
    public static void enter(View v,Context ctx,int index){
        v.setAlpha(0f);v.setTranslationY(dp(ctx,12));
        v.animate().alpha(1).translationY(0).setStartDelay(Math.min(260,index*55))
            .setDuration(360).setInterpolator(new DecelerateInterpolator()).start();
    }
    public static void pulse(View view){
        ObjectAnimator ax=ObjectAnimator.ofFloat(view,"scaleX",1f,1.075f,1f);
        ObjectAnimator ay=ObjectAnimator.ofFloat(view,"scaleY",1f,1.075f,1f);
        ax.setDuration(1650);ay.setDuration(1650);
        ax.setRepeatCount(ValueAnimator.INFINITE);ay.setRepeatCount(ValueAnimator.INFINITE);
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            @Override public void onViewAttachedToWindow(View v){ax.start();ay.start();}
            @Override public void onViewDetachedFromWindow(View v){ax.cancel();ay.cancel();v.setScaleX(1f);v.setScaleY(1f);}
        });
        if(view.isAttachedToWindow()){ax.start();ay.start();}
    }
    private static final class GlassDrawable extends Drawable {
        final Context ctx;final float radius;final int top,bottom,rim;final Paint p=new Paint(3);
        GlassDrawable(Context c,int r,int t,int b,int outline){ctx=c;radius=dp(c,r);top=t;bottom=b;rim=outline;}
        @Override public void draw(Canvas c){
            Rect r=getBounds();float x=r.left+dp(ctx,2),y=r.top+dp(ctx,2),w=r.width()-dp(ctx,4),h=r.height()-dp(ctx,5);
            if(w<=0||h<=0)return;
            RectF shape=new RectF(x,y,x+w,y+h);
            p.setShader(null);p.setStyle(Paint.Style.FILL);
            p.setColor(0x992F2481);
            c.drawRoundRect(new RectF(x+dp(ctx,2),y+dp(ctx,7),x+w+dp(ctx,2),y+h+dp(ctx,8)),radius,radius,p);
            p.setColor(0x663CA1FF);
            c.drawRoundRect(new RectF(x,y+dp(ctx,4),x+w,y+h+dp(ctx,5)),radius,radius,p);
            p.setShader(new LinearGradient(x,y,x+w,y+h,new int[]{top,blend(top,bottom,.35f),bottom},null,Shader.TileMode.CLAMP));
            c.drawRoundRect(shape,radius,radius,p);p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(ctx,1.5f));p.setColor(rim);
            c.drawRoundRect(shape,radius,radius,p);
            p.setStrokeWidth(dp(ctx,1.3f));p.setColor(0x6FFFFFFF);
            c.drawArc(new RectF(x+dp(ctx,3),y+dp(ctx,3),x+w-dp(ctx,3),y+h-dp(ctx,5)),191,153,false,p);
            p.setStrokeWidth(dp(ctx,2));p.setColor(0x664B85FF);
            c.drawArc(shape,8,166,false,p);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(x,y,x,y+h*.52f,0x42FFFFFF,0x00FFFFFF,Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(x+dp(ctx,5),y+dp(ctx,5),x+w-dp(ctx,5),y+h*.55f),radius*.85f,radius*.85f,p);
            p.setShader(null);
        }
        private int blend(int a,int b,float t){
            int aa=(int)(Color.alpha(a)*(1-t)+Color.alpha(b)*t);
            int rr=(int)(Color.red(a)*(1-t)+Color.red(b)*t);
            int gg=(int)(Color.green(a)*(1-t)+Color.green(b)*t);
            int bb=(int)(Color.blue(a)*(1-t)+Color.blue(b)*t);
            return Color.argb(aa,rr,gg,bb);
        }
        @Override public void setAlpha(int a){p.setAlpha(a);}
        @Override public void setColorFilter(android.graphics.ColorFilter f){p.setColorFilter(f);}
        @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
    private static final class SphereDrawable extends Drawable {
        final Context ctx;final Paint p=new Paint(3);final int a,b;
        SphereDrawable(Context c,int primary,int secondary){ctx=c;a=primary;b=secondary;}
        @Override public void draw(Canvas c){
            Rect r=getBounds();float x=r.exactCenterX(),y=r.exactCenterY(),size=Math.min(r.width(),r.height())*.46f;
            if(size<=0)return;
            p.setShader(new RadialGradient(x-size*.32f,y-size*.40f,size*1.8f,
                new int[]{0xffD2C8FF,a,b,0xff201852},new float[]{0,.26f,.73f,1},Shader.TileMode.CLAMP));
            c.drawCircle(x,y,size,p);p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(ctx,1.7f));p.setColor(0xffE3DAFF);c.drawCircle(x,y,size-dp(ctx,1),p);
            p.setColor(0x884FA1FF);p.setStrokeWidth(dp(ctx,4));c.drawArc(new RectF(x-size,y-size,x+size,y+size),6,140,false,p);
            p.setColor(0xD8FFFFFF);p.setStrokeWidth(dp(ctx,2));c.drawArc(new RectF(x-size*.80f,y-size*.80f,x+size*.80f,y+size*.80f),201,104,false,p);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(x-size*.40f,y-size*.44f,size*.48f,0x9CFFFFFF,0x00FFFFFF,Shader.TileMode.CLAMP));
            c.drawCircle(x-size*.40f,y-size*.44f,size*.48f,p);p.setShader(null);
        }
        @Override public void setAlpha(int alpha){p.setAlpha(alpha);}
        @Override public void setColorFilter(android.graphics.ColorFilter cf){p.setColorFilter(cf);}
        @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
    public static final class Galaxy extends View {
        private final Paint p=new Paint(3);
        private final Random random=new Random(20261031);
        private final float[] x=new float[105],y=new float[105],size=new float[105],phase=new float[105];
        private long start=android.os.SystemClock.uptimeMillis();
        public Galaxy(Context c){
            super(c);
            for(int i=0;i<x.length;i++){x[i]=random.nextFloat();y[i]=random.nextFloat();size[i]=random.nextFloat();phase[i]=random.nextFloat()*6.283f;}
        }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
            float time=(android.os.SystemClock.uptimeMillis()-start)/1000f;
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(w*.78f,h*.22f,w*.82f,
                new int[]{0x665A2CA9,0x443A65CB,0x000C1536},null,Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);p.setShader(null);
            p.setShader(new RadialGradient(w*.25f,h*.9f,w*.6f,
                new int[]{0x644544B9,0x00332280},null,Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);p.setShader(null);
            float moonX=w*.80f,moonY=h*.26f,rad=Math.min(w,h)*.18f;
            p.setShader(new RadialGradient(moonX,moonY,rad*2.15f,new int[]{0xA44777FF,0x3F9E5BFF,0x00654AFF},null,Shader.TileMode.CLAMP));
            c.drawCircle(moonX,moonY,rad*2.15f,p);p.setShader(null);
            p.setShader(new RadialGradient(moonX-rad*.28f,moonY-rad*.28f,rad*1.45f,
                new int[]{0xffF6F2FF,0xffA4BCFF,0xff5842D8,0xff211A5D},
                new float[]{0,.42f,.76f,1f},Shader.TileMode.CLAMP));
            c.drawCircle(moonX,moonY,rad,p);p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(getContext(),1.5f));p.setColor(0xAAAFC6FF);
            c.drawArc(new RectF(moonX-rad,moonY-rad,moonX+rad,moonY+rad),193,98,false,p);
            p.setStyle(Paint.Style.FILL);
            for(int i=0;i<x.length;i++){
                float brightness=(float)(.48+.52*Math.sin(time*(.55+size[i])+phase[i]));
                int alpha=(int)(70+130*brightness);
                p.setColor(Color.argb(alpha, i%3==0?186:255,i%4==0?149:232,255));
                float radius=dp(getContext(),.45f+size[i]*1.2f);
                c.drawCircle(x[i]*w,y[i]*h,radius,p);
            }
            p.setShader(new LinearGradient(0,h*.66f,0,h,new int[]{0x00101843,0xCE111739},null,Shader.TileMode.CLAMP));
            c.drawRect(0,h*.66f,w,h,p);p.setShader(null);
            if(isAttachedToWindow()&&getVisibility()==VISIBLE)postInvalidateDelayed(70);
        }
    }
}