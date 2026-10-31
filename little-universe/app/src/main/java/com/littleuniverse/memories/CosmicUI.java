package com.littleuniverse.memories;

import android.animation.*;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import java.util.Random;

/**
 * Little Universe's native dimensional UI.
 * No orbit lines, decorative arcs, wireframes or outlined geometry on cards.
 * Depth comes from real layered shadows, inset glass reflections and soft lighting.
 */
public final class CosmicUI {
    public static final int BLUE = 0xff367CFA;
    public static final int VIOLET = 0xff8453F4;
    public static final int PALE = 0xffE7DFFF;
    public static final int NIGHT = 0xff090E22;
    private CosmicUI() {}
    static float dp(Context ctx,float value){
        return ctx.getResources().getDisplayMetrics().density*value;
    }
    public static Drawable glass(Context ctx,int radius,int top,int bottom,int rim){
        return new GlassDrawable(ctx,radius,top,bottom,rim);
    }
    public static Drawable sphere(Context ctx,int primary,int secondary){
        return new SphereDrawable(ctx,primary,secondary);
    }
    public static void lift(View view,Context ctx){
        view.setElevation(dp(ctx,10));
        view.setTranslationZ(dp(ctx,2));
        view.setOnTouchListener((v,event)->{
            if(event.getAction()==MotionEvent.ACTION_DOWN){
                v.animate().scaleX(.974f).scaleY(.974f).translationZ(0f).setDuration(95).start();
            }else if(event.getAction()==MotionEvent.ACTION_UP||
                     event.getAction()==MotionEvent.ACTION_CANCEL){
                v.animate().scaleX(1).scaleY(1).translationZ(dp(ctx,2)).setDuration(190)
                    .setInterpolator(new DecelerateInterpolator()).start();
            }
            return false;
        });
    }
    public static void enter(View view,Context ctx,int index){
        view.setAlpha(0);view.setTranslationY(dp(ctx,13));
        view.animate().alpha(1).translationY(0).setDuration(330)
            .setStartDelay(Math.min(240,index*42))
            .setInterpolator(new DecelerateInterpolator()).start();
    }
    public static void pulse(View view){
        ObjectAnimator scaleX=ObjectAnimator.ofFloat(view,"scaleX",1f,1.045f,1f);
        ObjectAnimator scaleY=ObjectAnimator.ofFloat(view,"scaleY",1f,1.045f,1f);
        scaleX.setDuration(2100);scaleY.setDuration(2100);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            @Override public void onViewAttachedToWindow(View v){scaleX.start();scaleY.start();}
            @Override public void onViewDetachedFromWindow(View v){
                scaleX.cancel();scaleY.cancel();v.setScaleX(1f);v.setScaleY(1f);
            }
        });
        if(view.isAttachedToWindow()){scaleX.start();scaleY.start();}
    }
    private static int blended(int x,int y,float t){
        return Color.argb((int)(Color.alpha(x)*(1-t)+Color.alpha(y)*t),
            (int)(Color.red(x)*(1-t)+Color.red(y)*t),
            (int)(Color.green(x)*(1-t)+Color.green(y)*t),
            (int)(Color.blue(x)*(1-t)+Color.blue(y)*t));
    }
    private static final class GlassDrawable extends Drawable {
        final Context ctx;
        final float radius;
        final int top,bottom,rim;
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        GlassDrawable(Context context,int r,int start,int end,int edge){
            ctx=context;radius=dp(context,r);top=start;bottom=end;rim=edge;
        }
        @Override public void draw(Canvas c){
            Rect bounds=getBounds();
            float d=dp(ctx,1),x=bounds.left+3*d,y=bounds.top+2*d;
            float w=bounds.width()-6*d,h=bounds.height()-9*d;
            if(w<=0||h<=0)return;
            float rad=Math.min(radius,Math.min(w,h)*.47f);
            RectF front=new RectF(x,y,x+w,y+h);

            // A soft, offset base gives the tile a tactile raised surface.
            p.setShader(null);p.setStyle(Paint.Style.FILL);
            for(int i=4;i>=1;i--){
                p.setColor(Color.argb(16+i*6,1,4,27));
                float offset=dp(ctx,2+i*1.8f);
                c.drawRoundRect(new RectF(x-offset*.20f,y+offset*.55f,x+w+offset*.20f,
                    y+h+offset),rad+offset*.1f,rad+offset*.1f,p);
            }

            // Dimensional lower layer, like the dark side of a piece of polished glass.
            p.setShader(new LinearGradient(x,y+h*.5f,x,y+h+dp(ctx,6),
                blended(bottom,0xff05091D,.12f),0xff0A102D,Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(x,y+dp(ctx,4),x+w,y+h+dp(ctx,5)),
                rad,rad,p);
            p.setShader(null);

            // Main blue/purple material, lit from the upper left.
            int mid=blended(top,bottom,.45f);
            p.setShader(new LinearGradient(x,y,x+w*.75f,y+h,
                new int[]{blended(top,0xffFFFFFF,.13f),mid,bottom},
                new float[]{0,.42f,1},Shader.TileMode.CLAMP));
            c.drawRoundRect(front,rad,rad,p);p.setShader(null);

            // All reflections are clipped to the card itself. No elliptical outlines.
            Path clip=new Path();clip.addRoundRect(front,rad,rad,Path.Direction.CW);
            c.save();c.clipPath(clip);
            p.setShader(new RadialGradient(x+w*.12f,y+h*.04f,Math.max(w,h)*.86f,
                new int[]{0x45FFFFFF,0x16A5B9FF,0x00000000},
                new float[]{0,.48f,1},Shader.TileMode.CLAMP));
            c.drawRect(front,p);p.setShader(null);
            p.setShader(new RadialGradient(x+w*.96f,y+h*.93f,Math.max(w,h)*.63f,
                new int[]{0x38524FEA,0x0C6657D8,0x00000000},
                null,Shader.TileMode.CLAMP));
            c.drawRect(front,p);p.setShader(null);

            // Soft top glass sheen fades out naturally instead of drawing arcs.
            p.setShader(new LinearGradient(x,y,x,y+h*.53f,
                new int[]{0x35FFFFFF,0x11FFFFFF,0x00FFFFFF},
                new float[]{0,.3f,1},Shader.TileMode.CLAMP));
            c.drawRect(x,y,x+w,y+h*.53f,p);p.setShader(null);
            c.restore();

            // Restrained bevel edges: thin top light, dim bottom edge.
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(ctx,1.1f));
            p.setColor(blended(rim,0xffFFFFFF,.12f)&0xBFFFFFFF);
            c.drawRoundRect(front,rad,rad,p);
            p.setStyle(Paint.Style.FILL);

            // Tiny specular highlight along the TOP EDGE ONLY, not a dome/arc.
            float inset=Math.min(rad,dp(ctx,28));
            p.setShader(new LinearGradient(x+inset,y,x+w-inset,y,
                new int[]{0x00FFFFFF,0x55FFFFFF,0x1DFFFFFF,0x00FFFFFF},
                new float[]{0,.23f,.7f,1},Shader.TileMode.CLAMP));
            c.drawRect(x+inset,y+dp(ctx,.7f),x+w-inset,y+dp(ctx,2f),p);
            p.setShader(null);
        }
        @Override public void setAlpha(int alpha){p.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter cf){p.setColorFilter(cf);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private static final class SphereDrawable extends Drawable {
        final Context ctx;final Paint p=new Paint(3);final int primary,secondary;
        SphereDrawable(Context c,int a,int b){ctx=c;primary=a;secondary=b;}
        @Override public void draw(Canvas canvas){
            Rect r=getBounds();
            float x=r.exactCenterX(),y=r.exactCenterY(),rad=Math.min(r.width(),r.height())*.44f;
            if(rad<=0)return;
            p.setStyle(Paint.Style.FILL);
            // Natural soft-edged gel material, without concentric rings or arcs.
            p.setShader(new RadialGradient(x-rad*.32f,y-rad*.35f,rad*1.75f,
                new int[]{0xffF1E9FF,primary,secondary,0xff191642},
                new float[]{0,.27f,.73f,1},Shader.TileMode.CLAMP));
            canvas.drawCircle(x,y,rad,p);p.setShader(null);
            p.setShader(new RadialGradient(x-rad*.37f,y-rad*.47f,rad*.66f,
                new int[]{0xA2FFFFFF,0x2CFFFFFF,0x00FFFFFF},
                new float[]{0,.55f,1},Shader.TileMode.CLAMP));
            canvas.drawCircle(x-rad*.2f,y-rad*.20f,rad*.66f,p);p.setShader(null);
        }
        @Override public void setAlpha(int alpha){p.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter cf){p.setColorFilter(cf);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    public static final class Galaxy extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        final Random random=new Random(20261031);
        final float[] starsX=new float[72],starsY=new float[72];
        final float[] starsSize=new float[72],starsPhase=new float[72];
        final long started=android.os.SystemClock.uptimeMillis();
        public Galaxy(Context context){
            super(context);
            for(int i=0;i<starsX.length;i++){
                starsX[i]=random.nextFloat();starsY[i]=random.nextFloat();
                starsSize[i]=random.nextFloat();starsPhase[i]=random.nextFloat()*6.283f;
            }
        }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
            float time=(android.os.SystemClock.uptimeMillis()-started)/1000f;

            // Soft atmospheric blue-violet gradients instead of a cartoon planet,
            // moon circle, orbital lines, or wireframe geometry.
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0,0,w,h,
                new int[]{0x713866E5,0x332C78C9,0x242724A4},
                new float[]{0,.46f,1},Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);p.setShader(null);
            p.setShader(new RadialGradient(w*.84f,h*.14f,Math.max(w,h)*.67f,
                new int[]{0x887442DC,0x2A5C3FCD,0x00000000},
                new float[]{0,.42f,1},Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);p.setShader(null);
            p.setShader(new RadialGradient(w*.06f,h*.96f,Math.max(w,h)*.71f,
                new int[]{0x66417CEB,0x1B203E87,0x00000000},
                new float[]{0,.4f,1},Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);p.setShader(null);
            for(int i=0;i<starsX.length;i++){
                float twinkle=(float)(.5+.5*Math.sin(time*(.42+starsSize[i]) + starsPhase[i]));
                int alpha=45+(int)(78*twinkle);
                p.setColor(Color.argb(alpha,228,224,255));
                c.drawCircle(starsX[i]*w,starsY[i]*h,dp(getContext(),.35f+starsSize[i]*.75f),p);
            }
            if(isAttachedToWindow()&&getVisibility()==VISIBLE)postInvalidateDelayed(95);
        }
    }
}