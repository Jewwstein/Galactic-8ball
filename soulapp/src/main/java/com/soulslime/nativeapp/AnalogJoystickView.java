package com.soulslime.nativeapp;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;

public class AnalogJoystickView extends View {
    public interface Listener { void onMove(float x,float y); }

    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Listener listener;
    private float knobX=0,knobY=0;
    private boolean active=false;

    public AnalogJoystickView(Context c,Listener l){
        super(c);
        listener=l;
        stroke.setStyle(Paint.Style.STROKE);
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight(),cx=w/2f,cy=h/2f;
        float base=Math.min(w,h)*.42f;
        float knob=base*.43f;

        p.setColor(Color.argb(76,4,18,31));
        c.drawCircle(cx,cy,base*1.10f,p);

        stroke.setStrokeWidth(Math.max(3f,base*.035f));
        stroke.setColor(Color.argb(165,116,228,255));
        c.drawCircle(cx,cy,base,stroke);

        stroke.setStrokeWidth(Math.max(2f,base*.015f));
        stroke.setColor(Color.argb(90,196,248,255));
        c.drawCircle(cx,cy,base*.73f,stroke);

        float kx=cx+knobX*base*.64f,ky=cy+knobY*base*.64f;
        p.setShader(new RadialGradient(kx-knob*.20f,ky-knob*.24f,knob*1.4f,
            new int[]{Color.argb(220,160,240,255),Color.argb(205,44,119,157),Color.argb(230,7,31,49)},
            new float[]{0f,.46f,1f},Shader.TileMode.CLAMP));
        c.drawCircle(kx,ky,knob,p);p.setShader(null);

        stroke.setStrokeWidth(Math.max(3f,knob*.055f));
        stroke.setColor(Color.argb(225,196,250,255));
        c.drawCircle(kx,ky,knob,stroke);

        p.setColor(Color.argb(155,255,255,255));
        c.drawCircle(kx-knob*.27f,ky-knob*.30f,knob*.15f,p);
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        float cx=getWidth()/2f,cy=getHeight()/2f;
        float base=Math.min(getWidth(),getHeight())*.42f;
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                active=true;
                float dx=(e.getX()-cx)/(base*.64f);
                float dy=(e.getY()-cy)/(base*.64f);
                float mag=(float)Math.sqrt(dx*dx+dy*dy);
                if(mag>1f){dx/=mag;dy/=mag;}
                if(mag<.09f){dx=0;dy=0;}
                knobX=dx;knobY=dy;
                if(listener!=null)listener.onMove(dx,dy);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                active=false;knobX=knobY=0;
                if(listener!=null)listener.onMove(0,0);
                invalidate();
                return true;
        }
        return true;
    }
}
