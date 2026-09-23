package com.galactic.eightball;
import android.app.*;import android.os.*;import android.graphics.*;import android.view.*;import android.content.*;import java.util.*;
public class MainActivity extends Activity{
 public void onCreate(Bundle b){super.onCreate(b);getWindow().setFlags(1024,1024);setContentView(new Game(this));}
 static class Ball{float x,y,vx,vy;int n;boolean sunk=false;Ball(float x,float y,int n){this.x=x;this.y=y;this.n=n;}}
 class Game extends View{
  Paint p=new Paint(3),t=new Paint(3);ArrayList<Ball> balls=new ArrayList<>();Ball cue;long last;float aimX,aimY;boolean aiming=false;float power=.55f;final float BR=13;RectF table=new RectF();Random rnd=new Random();
  Game(Context c){super(c);setBackgroundColor(Color.rgb(2,4,14));setKeepScreenOn(true);rack();}
  void rack(){balls.clear();cue=new Ball(.25f,.5f,0);balls.add(cue);int n=1,k=0;for(int row=0;row<5;row++)for(int j=0;j<=row;j++){float x=.66f+row*.028f,y=.5f+(j-row/2f)*.034f;balls.add(new Ball(x,y,n++));}invalidate();}
  protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();float m=38;table.set(m,34,w-m,h-34);p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(7,11,31));c.drawRoundRect(table,34,34,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(10);p.setColor(Color.rgb(177,127,38));c.drawRoundRect(table,34,34,p);p.setStrokeWidth(2);p.setColor(Color.rgb(39,78,128));for(int i=1;i<4;i++)c.drawLine(table.left+table.width()*i/4,table.top,table.left+table.width()*i/4,table.bottom,p);
   float[][] pk={{0,0},{.5f,0},{1,0},{0,1},{.5f,1},{1,1}};p.setStyle(Paint.Style.FILL);p.setColor(Color.BLACK);for(float[]q:pk)c.drawCircle(table.left+q[0]*table.width(),table.top+q[1]*table.height(),20,p);
   for(Ball b:balls)if(!b.sunk)drawBall(c,b);
   if(aiming&&!cue.sunk){float cx=X(cue.x),cy=Y(cue.y),dx=aimX-cx,dy=aimY-cy,L=(float)Math.hypot(dx,dy);if(L>1){dx/=L;dy/=L;p.setStrokeWidth(12);p.setColor(Color.argb(65,70,155,255));c.drawLine(cx,cy,cx+dx*Math.min(700,L+400),cy+dy*Math.min(700,L+400),p);p.setStrokeWidth(4);p.setColor(Color.rgb(110,210,255));c.drawLine(cx,cy,cx+dx*Math.min(700,L+400),cy+dy*Math.min(700,L+400),p);}}
   t.setColor(Color.WHITE);t.setTextSize(22);t.setTypeface(Typeface.DEFAULT_BOLD);c.drawText("GALACTIC 8-BALL",54,31,t);t.setTextSize(14);t.setColor(Color.rgb(135,174,215));c.drawText("Drag from the Death Star to aim • release to fire",240,29,t);
   p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(22,28,60));c.drawRoundRect(getWidth()-190,8,getWidth()-45,38,15,15,p);t.setColor(Color.WHITE);t.setTextSize(14);c.drawText("NEW RACK",getWidth()-158,29,t);
   physics();postInvalidateOnAnimation();
  }
  float X(float x){return table.left+x*table.width();}float Y(float y){return table.top+y*table.height();}
  void drawBall(Canvas c,Ball b){float x=X(b.x),y=Y(b.y);if(b.n==0){p.setColor(Color.rgb(165,170,177));c.drawCircle(x,y,BR,p);p.setColor(Color.rgb(60,65,72));c.drawCircle(x-3,y-2,5,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(Color.rgb(90,95,102));c.drawCircle(x,y,BR-3,p);p.setStyle(Paint.Style.FILL);return;}float hue=(b.n*37)%360;int col=Color.HSVToColor(new float[]{hue,.72f,.95f});p.setColor(col);c.drawCircle(x,y,BR,p);if(b.n>8){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(Color.rgb(205,205,220));c.drawCircle(x,y,BR-3,p);p.setStyle(Paint.Style.FILL);}if(b.n==8){p.setColor(Color.rgb(12,12,20));c.drawCircle(x,y,BR,p);}}
  public boolean onTouchEvent(MotionEvent e){float x=e.getX(),y=e.getY();if(e.getAction()==MotionEvent.ACTION_DOWN){if(x>getWidth()-210&&y<55){rack();return true;}float cx=X(cue.x),cy=Y(cue.y);if(Math.hypot(x-cx,y-cy)<80){aiming=true;aimX=x;aimY=y;}}else if(e.getAction()==MotionEvent.ACTION_MOVE&&aiming){aimX=x;aimY=y;}else if(e.getAction()==MotionEvent.ACTION_UP&&aiming){float cx=X(cue.x),cy=Y(cue.y),dx=x-cx,dy=y-cy,L=(float)Math.hypot(dx,dy);if(L>10){float s=Math.min(1,L/350f)*.016f;cue.vx=dx/L*s;cue.vy=dy/L*s;}aiming=false;}invalidate();return true;}
  void physics(){long now=System.nanoTime();if(last==0){last=now;return;}float dt=Math.min(.025f,(now-last)/1e9f);last=now;for(Ball b:balls)if(!b.sunk){b.x+=b.vx*dt*60;b.y+=b.vy*dt*60;b.vx*=.985;b.vy*=.985;if(Math.abs(b.vx)<.00003)b.vx=0;if(Math.abs(b.vy)<.00003)b.vy=0;float rx=BR/table.width(),ry=BR/table.height();if(b.x<rx){b.x=rx;b.vx=Math.abs(b.vx)*.82f;}if(b.x>1-rx){b.x=1-rx;b.vx=-Math.abs(b.vx)*.82f;}if(b.y<ry){b.y=ry;b.vy=Math.abs(b.vy)*.82f;}if(b.y>1-ry){b.y=1-ry;b.vy=-Math.abs(b.vy)*.82f;}checkPocket(b);}
   for(int i=0;i<balls.size();i++)for(int j=i+1;j<balls.size();j++)collide(balls.get(i),balls.get(j));
  }
  void collide(Ball a,Ball b){if(a.sunk||b.sunk)return;float dx=X(b.x)-X(a.x),dy=Y(b.y)-Y(a.y),d2=dx*dx+dy*dy,min=BR*2;if(d2>0&&d2<min*min){float d=(float)Math.sqrt(d2),nx=dx/d,ny=dy/d,rv=(b.vx-a.vx)*nx+(b.vy-a.vy)*ny;if(rv<0){float imp=-rv*.96f;a.vx-=imp*nx;a.vy-=imp*ny;b.vx+=imp*nx;b.vy+=imp*ny;}float push=(min-d)/2/table.width();a.x-=nx*push;b.x+=nx*push;}}
  void checkPocket(Ball b){float[][]q={{0,0},{.5f,0},{1,0},{0,1},{.5f,1},{1,1}};for(float[]z:q){float dx=X(b.x)-X(z[0]),dy=Y(b.y)-Y(z[1]);if(dx*dx+dy*dy<24*24){if(b.n==0){b.x=.25f;b.y=.5f;b.vx=b.vy=0;}else b.sunk=true;return;}}}
 }
}