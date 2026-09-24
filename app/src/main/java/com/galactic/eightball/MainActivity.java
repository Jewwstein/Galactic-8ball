package com.galactic.eightball;

import android.app.*;
import android.os.*;
import android.opengl.*;
import android.media.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import android.text.InputType;
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
import okhttp3.*;

public class MainActivity extends Activity {
  GameView game;
  HudView hud;
  MultiplayerManager multiplayer;

  public void onCreate(Bundle b){
    super.onCreate(b);
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
    game=new GameView(this);
    hud=new HudView(this,game);
    multiplayer=new MultiplayerManager(this,game,hud);
    game.r.net=multiplayer;
    hud.net=multiplayer;
    FrameLayout root=new FrameLayout(this);
    ImageView space=new ImageView(this);
    space.setScaleType(ImageView.ScaleType.CENTER_CROP);
    try(InputStream in=getAssets().open("environment/sky.jpg")){space.setImageBitmap(BitmapFactory.decodeStream(in));}catch(Exception ignored){space.setBackgroundColor(Color.BLACK);}
    root.addView(space,new FrameLayout.LayoutParams(-1,-1));
    root.addView(game,new FrameLayout.LayoutParams(-1,-1));
    root.addView(hud,new FrameLayout.LayoutParams(-1,-1));
    setContentView(root);
  }

  protected void onDestroy(){
    if(multiplayer!=null)multiplayer.disconnect();
    if(game!=null&&game.r!=null&&game.r.sfx!=null)game.r.sfx.shutdown();
    super.onDestroy();
  }

  void showMultiplayerDialog(){
    String status=multiplayer==null?"OFFLINE":multiplayer.statusText();
    String server=multiplayer==null?"NOT SET":multiplayer.serverDisplay();
    new AlertDialog.Builder(this)
      .setTitle("ONLINE MULTIPLAYER")
      .setMessage(status+"\nSERVER: "+server+"\n\nThe server owns the physics and game state. Both players only send controls.")
      .setItems(new String[]{"CREATE ONLINE GAME","JOIN WITH CODE","SET SERVER","DISCONNECT","CANCEL"},(d,which)->{
        if(which==0){
          multiplayer.createRoom();
        }else if(which==1){
          showJoinDialog();
        }else if(which==2){
          showServerDialog();
        }else if(which==3){
          multiplayer.disconnect();
          Toast.makeText(this,"Multiplayer disconnected",Toast.LENGTH_SHORT).show();
        }
      }).show();
  }

  void showJoinDialog(){
    final EditText input=new EditText(this);
    input.setSingleLine(true);
    input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
    input.setHint("ROOM CODE");
    input.setPadding(36,18,36,18);
    new AlertDialog.Builder(this)
      .setTitle("JOIN ONLINE GAME")
      .setMessage("Enter the 6-character room code from your friend.")
      .setView(input)
      .setPositiveButton("JOIN",(d,w)->{
        String code=input.getText().toString().trim().toUpperCase(Locale.US);
        if(!code.isEmpty())multiplayer.joinRoom(code);
      })
      .setNegativeButton("CANCEL",null)
      .show();
  }

  void showServerDialog(){
    final EditText input=new EditText(this);
    input.setSingleLine(true);
    input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
    input.setHint("https://your-server.onrender.com");
    input.setText(multiplayer==null?"":multiplayer.savedServerUrl());
    input.setSelection(input.getText().length());
    input.setPadding(36,18,36,18);
    new AlertDialog.Builder(this)
      .setTitle("ONLINE SERVER")
      .setMessage("Paste the Render service URL. The app converts it to a secure WebSocket automatically.")
      .setView(input)
      .setPositiveButton("SAVE",(d,w)->{
        String url=input.getText().toString().trim();
        multiplayer.setServerUrl(url);
        Toast.makeText(this,"Server saved",Toast.LENGTH_SHORT).show();
      })
      .setNegativeButton("CANCEL",null)
      .show();
  }

  static class MultiplayerManager {
    final MainActivity activity; final GameView game; final HudView hud;
    final Handler main=new Handler(Looper.getMainLooper());
    final OkHttpClient http=new OkHttpClient.Builder()
      .pingInterval(20,TimeUnit.SECONDS)
      .connectTimeout(8,TimeUnit.SECONDS)
      .build();
    volatile boolean connected=false,connecting=false;
    volatile int localPlayer=0;
    volatile String roomCode="";
    volatile String status="OFFLINE";
    volatile boolean hosting=false; // retained only for old renderer compatibility; server is authoritative.
    WebSocket socket;

    MultiplayerManager(MainActivity a,GameView g,HudView h){activity=a;game=g;hud=h;}

    boolean isConnected(){return connected;}
    boolean isFollower(){return connected;} // every online client follows the authoritative server simulation.
    boolean canLocalControl(int activeShooter){return !connected||localPlayer==activeShooter;}

    String savedServerUrl(){
      return activity.getSharedPreferences("galactic_online",Context.MODE_PRIVATE).getString("server_url","");
    }

    void setServerUrl(String url){
      activity.getSharedPreferences("galactic_online",MODE_PRIVATE).edit().putString("server_url",url==null?"":url.trim()).apply();
    }

    String serverDisplay(){
      String s=savedServerUrl();
      if(s==null||s.isEmpty())return "NOT SET";
      s=s.replace("https://","").replace("http://","").replace("wss://","").replace("ws://","");
      if(s.endsWith("/ws"))s=s.substring(0,s.length()-3);
      return s;
    }

    String websocketUrl(){
      String s=savedServerUrl();
      if(s==null)s="";
      s=s.trim();
      if(s.isEmpty())return "";
      if(s.startsWith("https://"))s="wss://"+s.substring(8);
      else if(s.startsWith("http://"))s="ws://"+s.substring(7);
      else if(!s.startsWith("wss://")&&!s.startsWith("ws://"))s="wss://"+s;
      while(s.endsWith("/"))s=s.substring(0,s.length()-1);
      if(!s.endsWith("/ws"))s+="/ws";
      return s;
    }

    String statusText(){
      if(connected){
        String code=roomCode==null||roomCode.isEmpty()?"":(" • "+roomCode);
        return "ONLINE • PLAYER "+localPlayer+code;
      }
      if(connecting)return "CONNECTING";
      return status;
    }

    void toast(String t){main.post(()->Toast.makeText(activity,t,Toast.LENGTH_LONG).show());}

    void createRoom(){
      connectThen("CREATE");
    }

    void joinRoom(String code){
      if(code==null||code.trim().isEmpty())return;
      connectThen("JOIN|"+code.trim().toUpperCase(Locale.US));
    }

    synchronized void connectThen(String firstMessage){
      String url=websocketUrl();
      if(url.isEmpty()){
        toast("Set the online server first.");
        main.post(activity::showServerDialog);
        return;
      }
      disconnect();
      connecting=true;status="CONNECTING";localPlayer=0;roomCode="";
      if(hud!=null)main.post(hud::invalidate);

      Request request=new Request.Builder().url(url).build();
      socket=http.newWebSocket(request,new WebSocketListener(){
        public void onOpen(WebSocket ws,Response response){
          connecting=false;
          ws.send(firstMessage);
        }

        public void onMessage(WebSocket ws,String msg){
          if(msg.startsWith("ROOM|")){
            String[] p=msg.split("\\|");
            if(p.length>=3){
              roomCode=p[1];
              try{localPlayer=Integer.parseInt(p[2]);}catch(Exception ignored){}
              connected=true;connecting=false;status="ONLINE";
              toast(localPlayer==1?"Room "+roomCode+" created — send this code to your friend.":"Joined room "+roomCode+" as Player 2.");
              if(hud!=null)main.post(hud::invalidate);
            }
          }else if(msg.startsWith("STATE|")){
            game.queueEvent(()->game.r.applyNetworkState(msg));
          }else if(msg.startsWith("PLAYER_JOINED")){
            toast("Player 2 joined room "+roomCode);
          }else if(msg.startsWith("PLAYER_LEFT")){
            toast("The other player disconnected.");
          }else if(msg.startsWith("ERROR|")){
            String err=msg.substring(6).replace('_',' ');
            toast("Server: "+err);
          }
        }

        public void onClosing(WebSocket ws,int code,String reason){
          ws.close(code,reason);
        }

        public void onClosed(WebSocket ws,int code,String reason){
          if(socket==ws){
            connected=false;connecting=false;localPlayer=0;roomCode="";status="OFFLINE";
            if(hud!=null)main.post(hud::invalidate);
          }
        }

        public void onFailure(WebSocket ws,Throwable t,Response response){
          if(socket==ws){
            connected=false;connecting=false;localPlayer=0;roomCode="";status="CONNECTION FAILED";
            toast("Could not reach multiplayer server: "+(t.getMessage()==null?"connection failed":t.getMessage()));
            if(hud!=null)main.post(hud::invalidate);
          }
        }
      });
    }

    synchronized void send(String line){
      if(!connected||socket==null)return;
      socket.send(line);
    }

    synchronized void disconnect(){
      WebSocket old=socket;socket=null;
      if(old!=null){try{old.send("LEAVE");}catch(Exception ignored){}try{old.close(1000,"bye");}catch(Exception ignored){}}
      connected=false;connecting=false;localPlayer=0;roomCode="";hosting=false;status="OFFLINE";
      if(hud!=null)main.post(hud::invalidate);
    }

    void onFrame(GameRenderer r){
      // No client snapshots are sent. The cloud server owns physics and broadcasts state.
    }
  }

  static class SfxManager {
    final Context ctx; final Handler main=new Handler(Looper.getMainLooper());
    MediaPlayer humPlayer=null,victoryPlayer=null; int humGeneration=0;

    SfxManager(Context c){ctx=c;}

    int rawId(String name){return ctx.getResources().getIdentifier(name,"raw",ctx.getPackageName());}

    MediaPlayer make(String name){
      int id=rawId(name);if(id==0)return null;
      try{return MediaPlayer.create(ctx,id);}catch(Exception e){return null;}
    }

    void oneShot(String name,float vol){
      main.post(()->{
        MediaPlayer mp=make(name);if(mp==null)return;
        mp.setVolume(vol,vol);
        mp.setOnCompletionListener(p->{try{p.release();}catch(Exception ignored){}});
        try{mp.start();}catch(Exception e){try{mp.release();}catch(Exception ignored){}}
      });
    }

    void stopHum(){
      humGeneration++;
      main.post(()->{
        if(humPlayer!=null){try{humPlayer.stop();}catch(Exception ignored){}try{humPlayer.release();}catch(Exception ignored){}humPlayer=null;}
      });
    }

    void startHum(){
      final int my=humGeneration;
      main.post(()->{
        if(my!=humGeneration)return;
        if(humPlayer!=null){try{humPlayer.release();}catch(Exception ignored){}}
        humPlayer=make("sfx_hum");
        if(humPlayer!=null){
          humPlayer.setLooping(true);humPlayer.setVolume(.34f,.34f);
          try{humPlayer.start();}catch(Exception ignored){}
        }
      });
    }

    void ignite(boolean jedi){
      stopHum();
      final int my=humGeneration;
      oneShot(jedi?"sfx_ignite_jedi":"sfx_ignite_sith",.82f);
      main.postDelayed(()->{if(my==humGeneration)startHum();},1750);
    }

    void clash(){stopHum();main.postDelayed(()->oneShot("sfx_clash",.88f),250);}
    void deactivate(){stopHum();main.postDelayed(()->oneShot("sfx_deactivate",.78f),250);}
    void pocket(){oneShot("sfx_pocket",.82f);}
    void scratch(){oneShot("sfx_scratch",.86f);}

    void stopVictory(){
      main.post(()->{
        if(victoryPlayer!=null){try{victoryPlayer.stop();}catch(Exception ignored){}try{victoryPlayer.release();}catch(Exception ignored){}victoryPlayer=null;}
      });
    }

    void victory(boolean jedi){
      stopHum();stopVictory();
      main.post(()->{
        victoryPlayer=make(jedi?"sfx_victory_jedi":"sfx_victory_sith");
        if(victoryPlayer!=null){
          victoryPlayer.setVolume(.78f,.78f);
          victoryPlayer.setOnCompletionListener(p->{try{p.release();}catch(Exception ignored){}if(victoryPlayer==p)victoryPlayer=null;});
          try{victoryPlayer.start();}catch(Exception ignored){}
        }
      });
    }

    void shutdown(){stopHum();stopVictory();main.removeCallbacksAndMessages(null);}
  }

  static class GameView extends GLSurfaceView{
    GameRenderer r;
    GameView(Context c){
      super(c);
      setEGLContextClientVersion(2);
      setEGLConfigChooser(8,8,8,8,16,0);
      getHolder().setFormat(PixelFormat.TRANSLUCENT);
      setZOrderOnTop(false);
      r=new GameRenderer(c);
      setRenderer(r);
      setRenderMode(RENDERMODE_CONTINUOUSLY);
    }
  }

  static class HudView extends View {
    final GameView game;
    MultiplayerManager net;
    final Paint p=new Paint(3);
    final Paint stroke=new Paint(3);
    Bitmap[] hilts=new Bitmap[6], blades=new Bitmap[6];
    RectF lockRect=new RectF(),saberMenuRect=new RectF(),rackRect=new RectF(),activeShooterRect=new RectF(),teamSwitchRect=new RectF(),multiplayerRect=new RectF(),saberPanelRect=new RectF(),confirmRect=new RectF(),cancelRect=new RectF();
    RectF[] hiltChoices=new RectF[6],bladeChoices=new RectF[6];
    float englishCx,englishCy,englishR;
    boolean touchingEnglish=false,menuOpen=false,camGesture=false,pullingHilt=false,aimingHilt=false;
    float camPrevDist=0,camPrevMidX=0,camPrevMidY=0,hiltPullStartX=0,hiltPullStartY=0,lastAimTapX=0,lastAimTapY=0,aimStartFingerAngle=0,aimStartWorldAngle=0;
    long lastAimTapMs=0;

    final String[] hiltFiles={"hilt_thumb_0.png","hilt_thumb_1.png","hilt_thumb_2.png","hilt_thumb_3.png","hilt_thumb_4.png","hilt_thumb_5.png"};
    final String[] bladeFiles={"blade_dark.png","blade_gold.png","blade_purple.png","blade_green.png","blade_red.png","blade_blue.png"};
    final String[] hiltNames={"OBI-WAN","LUKE BLUE","MACE WINDU","DARTH MAUL","LUKE GREEN","DARTH VADER"};
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

      // Premium command stack lives in the lower-left so the top edge stays clean.
      float bw=228*ui,bh=62*ui,gap=9*ui,left=22*ui,bottom=h-24*ui;
      saberMenuRect.set(left,bottom-bh,left+bw,bottom);
      rackRect.set(left,bottom-(bh*2+gap),left+bw,bottom-(bh+gap));
      activeShooterRect.set(left,bottom-(bh*3+gap*2),left+bw,bottom-(bh*2+gap*2));
      teamSwitchRect.set(left,bottom-(bh*4+gap*3),left+bw,bottom-(bh*3+gap*3));
      multiplayerRect.set(left,bottom-(bh*5+gap*4),left+bw,bottom-(bh*4+gap*4));
      lockRect.set(w-138*ui,h-162*ui,w-24*ui,h-48*ui);

      p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);
      drawPremiumButton(c,saberMenuRect,"SABER MENU","LOADOUT",ui,0xFF46C7FF,menuOpen);
      drawPremiumButton(c,rackRect,"NEW RACK","RESET TABLE",ui,0xFFFF6B55,false);
      drawPremiumButton(c,activeShooterRect,"ACTIVE SHOOTER","PLAYER "+r.activeShooter,ui,0xFFF4C542,r.localCanControl());
      drawPremiumButton(c,teamSwitchRect,"TEAM SWITCH","TEAM "+r.currentTeam,ui,r.currentTeam==1?0xFF66B7FF:0xFFFF7979,false);
      String mp=(net==null)?"OFFLINE":net.statusText();
      if(mp.length()>22)mp=mp.substring(0,22);
      drawPremiumButton(c,multiplayerRect,"MULTIPLAYER",mp,ui,(net!=null&&net.isConnected())?0xFF65E4A5:0xFF9CA3AF,net!=null&&net.isConnected());

      drawMatchHud(c,w,h,ui,r);

      if(r.state==GameRenderer.AIMING && !r.gameOver){
        drawWorldShotHilt(c,w,h,ui,r);
        drawCrosshairButton(c,lockRect,ui);
        p.setTextSize(19*ui);p.setColor(0xEEFFFFFF);
        c.drawText(r.localCanControl()?"DRAG HILT • TAP LOCK":"WAITING FOR PLAYER "+r.activeShooter,w*.5f,42*ui,p);
      } else if(r.gameOver){
        drawWinnerOverlay(c,w,h,ui,r);
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
    void drawPremiumButton(Canvas c,RectF rr,String title,String sub,float ui,int accent,boolean active){
      float rad=16*ui;
      p.setShader(null);p.setStyle(Paint.Style.FILL);
      p.setShadowLayer(8*ui,0,4*ui,0x99000000);
      LinearGradient base=new LinearGradient(rr.left,rr.top,rr.left,rr.bottom,
        active?0xEF283744:0xEA111821,active?0xF00A1018:0xED05080D,Shader.TileMode.CLAMP);
      p.setShader(base);c.drawRoundRect(rr,rad,rad,p);
      p.clearShadowLayer();p.setShader(null);

      // Metallic bevel: bright upper edge, dark lower edge, colored energy rail.
      stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(2.2f*ui);stroke.setColor(0xCC8592A6);
      c.drawRoundRect(new RectF(rr.left+1.2f*ui,rr.top+1.2f*ui,rr.right-1.2f*ui,rr.bottom-1.2f*ui),rad,rad,stroke);
      stroke.setStrokeWidth(1.2f*ui);stroke.setColor(0xAAFFFFFF);
      c.drawLine(rr.left+rad,rr.top+4*ui,rr.right-rad,rr.top+4*ui,stroke);
      stroke.setColor(0xAA000000);c.drawLine(rr.left+rad,rr.bottom-4*ui,rr.right-rad,rr.bottom-4*ui,stroke);

      p.setColor(accent);p.setStyle(Paint.Style.FILL);
      c.drawRoundRect(new RectF(rr.left+6*ui,rr.top+8*ui,rr.left+12*ui,rr.bottom-8*ui),3*ui,3*ui,p);
      p.setColor((accent&0x00FFFFFF)|0x33000000);
      for(int k=0;k<3;k++){
        float yy=rr.top+(18+k*13)*ui;
        c.drawRect(rr.left+18*ui,yy,rr.right-10*ui,yy+1*ui,p);
      }

      p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.LEFT);
      p.setTextSize(16*ui);p.setColor(0xFFF7FAFC);
      c.drawText(title,rr.left+24*ui,rr.top+25*ui,p);
      p.setTypeface(Typeface.DEFAULT);p.setTextSize(10.5f*ui);p.setColor(active?accent:0xFFB8C1CE);
      c.drawText(sub,rr.left+24*ui,rr.bottom-12*ui,p);

      p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(16*ui);p.setColor(accent);
      c.drawText("›",rr.right-18*ui,rr.centerY()+5*ui,p);
      p.setShader(null);
    }


    void drawSaberMenu(Canvas c,int w,int h,float ui,GameRenderer r){
      float pw=Math.min(w*.82f,1080*ui),ph=Math.min(h*.64f,430*ui),x=w*.5f-pw*.5f,y=h*.5f-ph*.5f;
      saberPanelRect.set(x,y,x+pw,y+ph);
      RectF panel=saberPanelRect;
      p.setStyle(Paint.Style.FILL);p.setShadowLayer(18*ui,0,8*ui,0xCC000000);
      p.setShader(new LinearGradient(panel.left,panel.top,panel.right,panel.bottom,0xF51B2531,0xFA070A0F,Shader.TileMode.CLAMP));
      c.drawRoundRect(panel,24*ui,24*ui,p);p.clearShadowLayer();p.setShader(null);
      stroke.setColor(0xFFE4B84D);stroke.setStrokeWidth(3*ui);c.drawRoundRect(panel,24*ui,24*ui,stroke);
      stroke.setColor(0x66FFFFFF);stroke.setStrokeWidth(1.2f*ui);c.drawRoundRect(new RectF(panel.left+6*ui,panel.top+6*ui,panel.right-6*ui,panel.bottom-6*ui),18*ui,18*ui,stroke);
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
      p.setTextSize(13*ui);p.setColor(0xFFB9C1CC);c.drawText("Tap anywhere outside this panel to close",w*.5f,y+ph-18*ui,p);
    }

    void drawMatchHud(Canvas c,int w,int h,float ui,GameRenderer r){
      float panelW=Math.min(w*.38f,235*ui),panelH=86*ui,top=90*ui;
      RectF left=new RectF(18*ui,top,18*ui+panelW,top+panelH);
      RectF right=new RectF(w-18*ui-panelW,top,w-18*ui,top+panelH);
      drawTeamCard(c,left,1,ui,r);
      drawTeamCard(c,right,2,ui,r);

      p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);
      p.setTextSize(15*ui);p.setColor(0xFFF4C542);
      String center=r.gameOver?("TEAM "+r.winnerTeam+" WINS"):("TEAM "+r.currentTeam+" TURN");
      c.drawText(center,w*.5f,top+16*ui,p);

      p.setTextSize(11*ui);p.setColor(0xFFD1D5DB);
      c.drawText(r.ruleMessage==null?"":r.ruleMessage,w*.5f,top+35*ui,p);

      // Persistent 8-ball objective between both team cards.
      float bx=w*.5f,by=top+61*ui,br=16*ui;
      p.setColor(0xFF080808);c.drawCircle(bx,by,br,p);
      stroke.setColor(0xFFE6E6E6);stroke.setStrokeWidth(2*ui);c.drawCircle(bx,by,br,stroke);
      p.setColor(Color.WHITE);p.setTextSize(13*ui);p.setTextAlign(Paint.Align.CENTER);
      c.drawText("8",bx,by+4.5f*ui,p);
    }

    void drawTeamCard(Canvas c,RectF rr,int team,float ui,GameRenderer r){
      boolean active=!r.gameOver&&r.currentTeam==team;
      p.setStyle(Paint.Style.FILL);p.setColor(active?0xD5232A36:0xB8141821);c.drawRoundRect(rr,14*ui,14*ui,p);
      stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth((active?3f:1.5f)*ui);
      stroke.setColor(active?0xFFF4C542:(team==1?0xAA55A8FF:0xAAFF5F5F));c.drawRoundRect(rr,14*ui,14*ui,stroke);

      p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.LEFT);
      p.setTextSize(14*ui);p.setColor(team==1?0xFF8CC8FF:0xFFFF9B9B);
      c.drawText("TEAM "+team,rr.left+10*ui,rr.top+18*ui,p);

      int suit=r.teamSuit[team-1];
      String suitText=suit==1?"SOLIDS":suit==2?"STRIPES":"OPEN TABLE";
      p.setTextAlign(Paint.Align.RIGHT);p.setTextSize(12*ui);p.setColor(Color.WHITE);
      c.drawText(suitText,rr.right-10*ui,rr.top+18*ui,p);

      if(suit==0){
        p.setTextAlign(Paint.Align.CENTER);p.setTextSize(11*ui);p.setColor(0xFFCBD5E1);
        c.drawText("FIRST MADE GROUP CLAIMS",rr.centerX(),rr.top+51*ui,p);
      }else{
        int start=suit==1?1:9,end=suit==1?7:15;
        float gap=(rr.width()-22*ui)/7f,cx=rr.left+11*ui+gap*.5f,cy=rr.top+49*ui,rad=Math.min(11*ui,gap*.35f);
        int remaining=0;
        for(int n=start;n<=end;n++){
          boolean onTable=r.isBallOnTable(n);
          if(onTable)remaining++;
          float x=cx+(n-start)*gap;
          if(suit==1){
            p.setColor(onTable?0xFFE8B84C:0x443A3A3A);c.drawCircle(x,cy,rad,p);
          }else{
            p.setColor(onTable?0xFFF7F7F7:0x443A3A3A);c.drawCircle(x,cy,rad,p);
            stroke.setColor(onTable?0xFFE8B84C:0x44444444);stroke.setStrokeWidth(3*ui);c.drawCircle(x,cy,rad*.72f,stroke);
          }
          p.setTextAlign(Paint.Align.CENTER);p.setTextSize(7.5f*ui);p.setColor(onTable?0xFF111111:0x66888888);
          c.drawText(String.valueOf(n),x,cy+2.7f*ui,p);
        }
        p.setTextAlign(Paint.Align.CENTER);p.setTextSize(10*ui);p.setColor(remaining==0?0xFFF4C542:0xFFCBD5E1);
        c.drawText(remaining==0?"8 BALL READY":remaining+" REMAINING",rr.centerX(),rr.bottom-8*ui,p);
      }
    }

    void drawWinnerOverlay(Canvas c,int w,int h,float ui,GameRenderer r){
      RectF box=new RectF(w*.16f,h*.38f,w*.84f,h*.55f);
      p.setColor(0xE510141C);p.setStyle(Paint.Style.FILL);c.drawRoundRect(box,24*ui,24*ui,p);
      stroke.setColor(0xFFF4C542);stroke.setStrokeWidth(4*ui);c.drawRoundRect(box,24*ui,24*ui,stroke);
      p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);
      p.setTextSize(32*ui);p.setColor(0xFFF4C542);c.drawText("TEAM "+r.winnerTeam+" WINS",box.centerX(),box.centerY()-5*ui,p);
      p.setTextSize(15*ui);p.setColor(Color.WHITE);c.drawText("8 BALL POCKETED • TAP NEW RACK",box.centerX(),box.centerY()+28*ui,p);
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
    float worldHiltAngle=0,worldCueX=0,worldCueY=0,worldDirX=1,worldDirY=0,worldEmitterX=0,worldEmitterY=0,worldRearEmitterX=0,worldRearEmitterY=0;

    void updateWorldHiltGeometry(int w,int h,GameRenderer r,float pullPx){
      if(r.balls.isEmpty()){worldHiltRect.setEmpty();return;}
      Ball cueBall=r.balls.get(0);
      float hiltY=2.72f;
      float[] cue=r.worldToScreen(cueBall.x,2.45f,cueBall.z,w,h);
      if(cue==null){worldHiltRect.setEmpty();return;}

      float backWorld=r.hiltBackWorld();
      float hLen=r.hiltWorldLength();
      float frontOff=r.hiltFrontEmitterOffset(),rearOff=r.hiltRearEndOffset();
      float cxw=cueBall.x-r.aimX*backWorld,czw=cueBall.z-r.aimZ*backWorld;
      float exw=cxw+r.aimX*frontOff,ezw=czw+r.aimZ*frontOff;
      float bxw=cxw-r.aimX*rearOff,bzw=czw-r.aimZ*rearOff;

      float[] hp=r.worldToScreen(cxw,hiltY,czw,w,h);
      float[] ep=r.worldToScreen(exw,hiltY,ezw,w,h);
      float[] bp=r.worldToScreen(bxw,hiltY,bzw,w,h);
      if(hp==null||ep==null||bp==null){worldHiltRect.setEmpty();return;}

      worldCueX=cue[0];worldCueY=cue[1];
      worldEmitterX=ep[0];worldEmitterY=ep[1];worldRearEmitterX=bp[0];worldRearEmitterY=bp[1];

      float dx=ep[0]-bp[0],dy=ep[1]-bp[1],len=(float)Math.sqrt(dx*dx+dy*dy);
      if(len<1){dx=1;dy=0;len=1;}
      worldDirX=dx/len;worldDirY=dy/len;
      worldHiltAngle=(float)Math.toDegrees(Math.atan2(dy,dx));

      // Hit target follows the true projected 3D hilt instead of a fixed-size PNG.
      float thick=Math.max(42f,Math.min(94f,len*.22f));
      worldHiltRect.set(hp[0]-len*.56f,hp[1]-thick*.65f,hp[0]+len*.56f,hp[1]+thick*.65f);
    }

    void drawWorldShotHilt(Canvas c,int w,int h,float ui,GameRenderer r){
      float pull=(r.state==GameRenderer.CHARGING)?r.chargePullPx:0;
      updateWorldHiltGeometry(w,h,r,pull);
      if(worldHiltRect.isEmpty())return;
      Bitmap blade=blades[r.bladeIndex];

      if(r.state==GameRenderer.CHARGING && r.power>0.1f){
        // Blade grows from the actual projected emitter of the 3D hilt.
        float ex=worldEmitterX;
        float ey=worldEmitterY;
        float full=(float)Math.sqrt((worldCueX-ex)*(worldCueX-ex)+(worldCueY-ey)*(worldCueY-ey));
        float endX=worldCueX,endY=worldCueY;
        float ang=(float)Math.toDegrees(Math.atan2(endY-ey,endX-ex));
        float len=full;
        float aspect=(blade!=null&&blade.getHeight()>0)?((float)blade.getWidth()/blade.getHeight()):7f;
        float thick=Math.max(48f,Math.min(h*.085f,len*.34f));
        RectF bladeDst=new RectF(ex,ey-thick*.5f,ex+Math.max(2,len),ey+thick*.5f);
        c.save();c.rotate(ang,ex,ey);
        if(blade!=null)c.drawBitmap(blade,null,bladeDst,p);
        c.restore();

        if(r.hiltIndex==3){
          float rex=worldRearEmitterX,rey=worldRearEmitterY;
          float rendX=rex-worldDirX*len,rendY=rey-worldDirY*len;
          float rang=(float)Math.toDegrees(Math.atan2(rendY-rey,rendX-rex));
          RectF rearDst=new RectF(rex,rey-thick*.5f,rex+Math.max(2,len),rey+thick*.5f);
          c.save();c.rotate(rang,rex,rey);
          if(blade!=null)c.drawBitmap(blade,null,rearDst,p);
          c.restore();
        }

        p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(Math.max(20f,h*.026f));p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);
        c.drawText(Math.round(r.power)+"%",worldHiltRect.centerX(),worldHiltRect.centerY()-worldHiltRect.height()*.80f,p);
      }

      if(r.state==GameRenderer.AIMING){
        p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(Math.max(16f,h*.020f));p.setColor(0xEEF4C542);p.setTextAlign(Paint.Align.CENTER);
        c.drawText("DRAG HILT TO FINE AIM",worldHiltRect.centerX(),worldHiltRect.centerY()-worldHiltRect.height()*.82f,p);
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
        // Saber loadout behaves like a real modal: any tap outside dismisses it.
        if(menuOpen){
          if(!saberPanelRect.contains(x,y)){menuOpen=false;invalidate();return true;}
          for(int i=0;i<6;i++){
            if(hiltChoices[i].contains(x,y)){final int k=i;game.queueEvent(()->r.userSelectHilt(k));return true;}
            if(bladeChoices[i].contains(x,y)){final int k=i;game.queueEvent(()->r.userSelectBlade(k));return true;}
          }
          return true;
        }

        if(saberMenuRect.contains(x,y)){menuOpen=true;invalidate();return true;}
        if(rackRect.contains(x,y)){pullingHilt=false;game.queueEvent(()->r.userResetRack());return true;}
        if(activeShooterRect.contains(x,y)){game.queueEvent(()->r.userToggleActiveShooter());return true;}
        if(teamSwitchRect.contains(x,y)){game.queueEvent(()->r.userSwitchTeam());return true;}
        if(multiplayerRect.contains(x,y)){
          Context cc=getContext();
          if(cc instanceof MainActivity)((MainActivity)cc).showMultiplayerDialog();
          return true;
        }

        if(!r.gameOver && r.state==GameRenderer.AIMING && lockRect.contains(x,y)){
          aimingHilt=false;
          game.queueEvent(()->r.lockAngle());
          return true;
        }

        if(r.state==GameRenderer.SELECTING_ENGLISH){
          float dx=x-englishCx,dy=y-englishCy;
          if(dx*dx+dy*dy<=englishR*englishR){touchingEnglish=true;setEnglishFromTouch(x,y);return true;}
          if(confirmRect.contains(x,y)){game.queueEvent(()->r.confirmEnglish());return true;}
          if(cancelRect.contains(x,y)){game.queueEvent(()->r.cancelEnglish());return true;}
        }

        if(!r.gameOver && r.state==GameRenderer.AIMING){
          updateWorldHiltGeometry(w,h,r,0);
          RectF hit=new RectF(worldHiltRect);
          hit.inset(-34*ui,-34*ui);
          if(hit.contains(x,y)){
            float[] q=r.screenToTable(x,y,w,h);
            if(q!=null){
              Ball cue=r.balls.get(0);
              aimingHilt=true;
              aimStartFingerAngle=(float)Math.atan2(q[1]-cue.z,q[0]-cue.x);
              aimStartWorldAngle=(float)Math.atan2(r.aimZ,r.aimX);
              return true;
            }
          }
        }

        if(r.state==GameRenderer.CHARGING){
          updateWorldHiltGeometry(w,h,r,r.chargePullPx);
          RectF hit=new RectF(worldHiltRect);hit.inset(-36*ui,-36*ui);
          if(hit.contains(x,y)){pullingHilt=true;hiltPullStartX=x;hiltPullStartY=y;game.queueEvent(()->r.beginWorldCharge());return true;}
        }
      }

      if(aimingHilt){
        if(a==MotionEvent.ACTION_MOVE){
          float[] q=r.screenToTable(x,y,w,h);
          if(q!=null){
            Ball cue=r.balls.get(0);
            float fingerAngle=(float)Math.atan2(q[1]-cue.z,q[0]-cue.x);
            float delta=fingerAngle-aimStartFingerAngle;
            while(delta>(float)Math.PI)delta-=(float)(Math.PI*2.0);
            while(delta<(float)-Math.PI)delta+=(float)(Math.PI*2.0);
            // Precision gear remains intentionally slow for fine control.
            final float target=aimStartWorldAngle+delta*.42f;
            game.queueEvent(()->r.previewAimAngle(target));
          }
          return true;
        }
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){
          // Important: do not read the ACTION_UP coordinates. Finger lift often
          // produces a tiny final movement; ignoring it keeps the line exactly
          // where the player last saw it.
          aimingHilt=false;
          return true;
        }
        return true;
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
          if(r.state==GameRenderer.CHARGING){
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
      return true;
    }

    void setEnglishFromTouch(float x,float y){
      float dx=(x-englishCx)/englishR,dy=(y-englishCy)/englishR;
      float d=(float)Math.sqrt(dx*dx+dy*dy);if(d>1){dx/=d;dy/=d;}
      final float ex=dx,ey=-dy;game.queueEvent(()->game.r.setEnglish(ex,ey));
    }
  }

  static class Mesh {
    FloatBuffer pos,uv,norm; int count;
    Mesh(float[] p,float[] t){
      count=p.length/3;
      ByteBuffer bp=ByteBuffer.allocateDirect(p.length*4).order(ByteOrder.nativeOrder());
      pos=bp.asFloatBuffer(); pos.put(p).position(0);
      ByteBuffer bt=ByteBuffer.allocateDirect(t.length*4).order(ByteOrder.nativeOrder());
      uv=bt.asFloatBuffer(); uv.put(t).position(0);

      float[] n=buildSmoothNormals(p);
      ByteBuffer bn=ByteBuffer.allocateDirect(n.length*4).order(ByteOrder.nativeOrder());
      norm=bn.asFloatBuffer(); norm.put(n).position(0);
    }

    static String key(float x,float y,float z){
      return Math.round(x*10000f)+":"+Math.round(y*10000f)+":"+Math.round(z*10000f);
    }

    static float[] buildSmoothNormals(float[] p){
      HashMap<String,float[]> sums=new HashMap<>();
      int verts=p.length/3;
      for(int i=0;i+8<p.length;i+=9){
        float ax=p[i],ay=p[i+1],az=p[i+2];
        float bx=p[i+3],by=p[i+4],bz=p[i+5];
        float cx=p[i+6],cy=p[i+7],cz=p[i+8];
        float ux=bx-ax,uy=by-ay,uz=bz-az;
        float vx=cx-ax,vy=cy-ay,vz=cz-az;
        float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
        float nl=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(nl<1e-8f){nx=0;ny=1;nz=0;}else{nx/=nl;ny/=nl;nz/=nl;}
        for(int k=0;k<3;k++){
          int j=i+k*3;String q=key(p[j],p[j+1],p[j+2]);
          float[] a=sums.get(q);
          if(a==null){a=new float[]{0,0,0};sums.put(q,a);}
          a[0]+=nx;a[1]+=ny;a[2]+=nz;
        }
      }
      float[] out=new float[verts*3];
      for(int v=0;v<verts;v++){
        int j=v*3;float[] a=sums.get(key(p[j],p[j+1],p[j+2]));
        float nx=a==null?0:a[0],ny=a==null?1:a[1],nz=a==null?0:a[2];
        float nl=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(nl<1e-8f){nx=0;ny=1;nz=0;nl=1;}
        out[j]=nx/nl;out[j+1]=ny/nl;out[j+2]=nz/nl;
      }
      return out;
    }
  }

  static class Part{
    Mesh mesh; String texKey; float[] color;
    Part(Mesh m,String t,float r,float g,float b){mesh=m;texKey=t;color=new float[]{r,g,b,1};}
  }

  static class Ball{
    float x,z,vx,vz,spin; boolean active=true,sinking=false; int tex; Body body;
    float qx=0,qy=0,qz=0,qw=1;
    float sinkT=0,sinkStartX=0,sinkStartZ=0,sinkX=0,sinkZ=0; int index=0;
    Ball(float X,float Z,int T){x=X;z=Z;tex=T;}
    float speed2(){return vx*vx+vz*vz;}
  }

  static class GameRenderer implements GLSurfaceView.Renderer{
    static final int AIMING=0,SELECTING_ENGLISH=1,CHARGING=2,ROLLING=3;
    Context ctx; SfxManager sfx; MultiplayerManager net; ArrayList<Part> table=new ArrayList<>(); ArrayList<Mesh> falconMeshes=new ArrayList<>(); ArrayList<Ball> balls=new ArrayList<>();
    Mesh sphere,hiltCylinder,hiltBox; Mesh[] realHiltMeshes=new Mesh[6]; int[] realHiltTextures=new int[6]; Mesh[] saberMeshes=new Mesh[6]; int[] saberTextures=new int[6]; int[] hiltTextures=new int[6]; HashMap<String,Integer> tex=new HashMap<>();
    Mesh[] ringMeshes=new Mesh[7*3];
    final float[][][] ringRadii={
      {{1.72f,.20f},{1.53f,.10f},{0,0}},
      {{1.76f,.22f},{1.56f,.13f},{1.38f,.07f}},
      {{1.70f,.20f},{1.40f,.10f},{0,0}},
      {{1.74f,.17f},{1.55f,.10f},{1.38f,.06f}},
      {{1.78f,.21f},{1.51f,.09f},{0,0}},
      {{1.74f,.20f},{1.55f,.10f},{1.38f,.06f}},
      {{1.80f,.23f},{1.57f,.13f},{1.38f,.07f}}
    };
    final float[][][] ringColors={
      {{.38f,.72f,1f},{.82f,.94f,1f},{0,0,0}},
      {{.93f,.68f,.35f},{.56f,.31f,.15f},{1f,.84f,.56f}},
      {{.72f,.28f,.12f},{.95f,.52f,.18f},{0,0,0}},
      {{.72f,.84f,.94f},{.42f,.58f,.72f},{.90f,.95f,1f}},
      {{.78f,.58f,.34f},{.45f,.30f,.18f},{0,0,0}},
      {{.72f,.28f,1f},{.22f,.90f,.92f},{.92f,.52f,1f}},
      {{.72f,.10f,.08f},{.25f,.03f,.04f},{1f,.30f,.12f}}
    };
    final float[][] ringTilt={{17,24},{24,32},{29,-22},{14,38},{31,16},{20,-38},{34,27}};
    final float[] ringDepth={.18f,.22f,.20f,.18f,.22f,.18f,.24f};
    int program,aPos,aUv,aNormal,uMvp,uModel,uUseTex,uColor,uTex,uLit;
    float aspect=16f/9f; long last=0; float[] pvCache=new float[16];
    volatile float camYaw=180f,camPitch=46f,camDist=225f,camTargetX=0f,camTargetZ=0f;
    volatile int state=AIMING,hiltIndex=0,bladeIndex=5;
    volatile int currentTeam=1,winnerTeam=0,activeShooter=1;
    final int[] teamSuit={0,0}; // 0=open, 1=solids, 2=stripes
    final ArrayList<Integer> ballsSunkThisShot=new ArrayList<>();
    volatile boolean tableOpen=true,gameOver=false;
    volatile String ruleMessage="BREAK • TEAM 1";
    volatile float power=0,englishX=0,englishY=0;
    float aimX=1,aimZ=0,desiredAimX=1,desiredAimZ=0,chargeStartY=-1,sideSpin=0,topSpin=0; volatile float chargePullPx=0,chargePullWorld=0; boolean breakAssistArmed=true;
    World world; Body railBody; float physicsAccum=0f;
    static final float FIXED_DT=1f/240f;
    static final float TTS_MASS=.375f;
    static final float TTS_DRAG=.50f;
    static final float TTS_ANGULAR_DRAG=.45f;
    static final float TTS_STATIC_FRICTION=.40f;
    static final float TTS_DYNAMIC_FRICTION=.20f;
    static final float TTS_BOUNCINESS=1.0f;
    static final float STOP_SPEED=.20f;
    static final float ROLL_DECEL_FAST=1.60f;
    static final float ROLL_DECEL_SLOW=5.25f;
    // Visual radius is the TTS predictor radius. Collision radius is derived from
    // the exact TTS rack spacing sqrt(2.09^2+1.21^2)/2 so the rack is actually in contact.
    final float R=1.192f, PHYS_R=1.1900001f, MINX=-40.808f,MAXX=40.808f,MINZ=-19.808f,MAXZ=19.808f;
    final String[] objectFolders={"00_DeathStar","01_Tatooine","02_Kamino","03_Mustafar","04_Coruscant","05_Geonosis","06_Endor","07_Korriban","08_Exegol","09_Yavin","10_Bespin","11_Malastare","12_Kessel","13_Jakku","14_Felucia","15_Dathomir"};
    final float[][] bladeRgb={{.92f,.95f,1f},{1f,.72f,.18f},{.68f,.28f,1f},{.18f,1f,.42f},{1f,.12f,.10f},{.20f,.66f,1f}};
    final String[] saberFolders={"white","gold","purple","green","red","blue"};

    GameRenderer(Context c){ctx=c;sfx=new SfxManager(c.getApplicationContext());}

    public void onSurfaceCreated(GL10 gl,EGLConfig cfg){
      GLES20.glClearColor(0f,0f,0f,0f);
      GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glEnable(GLES20.GL_BLEND);
      GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
      String vs="attribute vec3 aPos;attribute vec2 aUv;attribute vec3 aNormal;uniform mat4 uMvp;uniform mat4 uModel;varying vec2 vUv;varying vec3 vNormal;void main(){gl_Position=uMvp*vec4(aPos,1.0);vUv=vec2(aUv.x,1.0-aUv.y);vNormal=normalize(mat3(uModel)*aNormal);}";
      String fs="precision mediump float;varying vec2 vUv;varying vec3 vNormal;uniform sampler2D uTex;uniform float uUseTex;uniform float uLit;uniform vec4 uColor;void main(){vec4 c=uColor;if(uUseTex>0.5)c*=texture2D(uTex,vUv);if(c.a<0.015)discard;if(uLit>0.5){vec3 n=normalize(vNormal);vec3 l=normalize(vec3(0.35,0.82,0.46));float d=max(dot(n,l),0.0);float s=pow(max(dot(n,normalize(vec3(-0.18,0.94,0.29))),0.0),22.0);float edge=pow(1.0-abs(n.y),2.0);c.rgb=c.rgb*(0.58+0.58*d)+vec3(s*0.34)+vec3(edge*0.035);}gl_FragColor=c;}";
      program=GLES20.glCreateProgram();int sv=shader(GLES20.GL_VERTEX_SHADER,vs),sf=shader(GLES20.GL_FRAGMENT_SHADER,fs);
      GLES20.glAttachShader(program,sv);GLES20.glAttachShader(program,sf);GLES20.glLinkProgram(program);
      aPos=GLES20.glGetAttribLocation(program,"aPos");aUv=GLES20.glGetAttribLocation(program,"aUv");aNormal=GLES20.glGetAttribLocation(program,"aNormal");
      uMvp=GLES20.glGetUniformLocation(program,"uMvp");uModel=GLES20.glGetUniformLocation(program,"uModel");uUseTex=GLES20.glGetUniformLocation(program,"uUseTex");
      uColor=GLES20.glGetUniformLocation(program,"uColor");uTex=GLES20.glGetUniformLocation(program,"uTex");uLit=GLES20.glGetUniformLocation(program,"uLit");
      loadAssets();resetRack();last=System.nanoTime();
    }

    int shader(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);return s;}
    public void onSurfaceChanged(GL10 gl,int w,int h){GLES20.glViewport(0,0,w,h);aspect=(float)w/Math.max(1,h);}

    public void onDrawFrame(GL10 gl){
      long now=System.nanoTime();float dt=Math.min(.033f,(now-last)/1_000_000_000f);last=now;
      step(dt);
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);GLES20.glUseProgram(program);
      float[] P=new float[16],V=new float[16];
      android.opengl.Matrix.perspectiveM(P,0,40,aspect,.1f,320f);
      float viewYaw=camYaw+(aspect<1f?90f:0f);float viewDist=camDist*(aspect<1f?1.03f:1f);
      float yaw=(float)Math.toRadians(viewYaw),pitch=(float)Math.toRadians(camPitch),flat=(float)Math.cos(pitch)*viewDist;
      float cx=camTargetX+(float)Math.sin(yaw)*flat,cy=-1f+(float)Math.sin(pitch)*viewDist,cz=camTargetZ+(float)Math.cos(yaw)*flat;
      android.opengl.Matrix.setLookAtM(V,0,cx,cy,cz,camTargetX,-1f,camTargetZ,0,1,0);
      android.opengl.Matrix.multiplyMM(pvCache,0,P,0,V,0);
      if(!falconMeshes.isEmpty()){
        float[] FM=identity();
        android.opengl.Matrix.translateM(FM,0,0f,-6.0953f,0f);
        android.opengl.Matrix.rotateM(FM,0,90f,0,1,0);
        android.opengl.Matrix.scaleM(FM,0,11.25f,6f,11.25f);
        int ft=tex.getOrDefault("falcon",0);
        for(Mesh fm:falconMeshes)drawMesh(fm,pvCache,FM,ft,new float[]{1,1,1,1});
      }
      for(Part p:table){int tt=p.texKey==null?0:tex.getOrDefault(p.texKey,0);if("felt".equals(p.texKey))drawMesh(p.mesh,pvCache,identity(),tt,p.color);else drawLitMesh(p.mesh,pvCache,identity(),tt,p.color);}
      if(state!=ROLLING)drawPredictor(pvCache);
      for(Ball b:balls)if(b.active){
        float sinkEase=b.sinking?(1f-(1f-b.sinkT)*(1f-b.sinkT)):0f;
        float by=2.22f-2.8f*sinkEase,bs=R*(1f-.18f*sinkEase);
        float[] T=identity();android.opengl.Matrix.translateM(T,0,b.x,by,b.z);
        float[] Q=quatMatrix(b),M=new float[16];
        android.opengl.Matrix.multiplyMM(M,0,T,0,Q,0);
        android.opengl.Matrix.scaleM(M,0,bs,bs,bs);
        drawMesh(sphere,pvCache,M,b.tex,new float[]{1,1,1,1});
        drawPlanetRing(pvCache,b);
      }
      if((state==AIMING||state==CHARGING)&&!gameOver)drawWorldHilt3D(pvCache);
      if(net!=null)net.onFrame(this);
    }

    float[] quatMatrix(Ball b){
      float x=b.qx,y=b.qy,z=b.qz,w=b.qw;
      float xx=x*x,yy=y*y,zz=z*z,xy=x*y,xz=x*z,yz=y*z,wx=w*x,wy=w*y,wz=w*z;
      return new float[]{
        1-2*(yy+zz), 2*(xy+wz), 2*(xz-wy), 0,
        2*(xy-wz), 1-2*(xx+zz), 2*(yz+wx), 0,
        2*(xz+wy), 2*(yz-wx), 1-2*(xx+yy), 0,
        0,0,0,1
      };
    }

    void drawMeshInternal(Mesh m,float[] pv,float[] model,int texture,float[] color,boolean lit){
      if(m==null)return;float[] mvp=new float[16];android.opengl.Matrix.multiplyMM(mvp,0,pv,0,model,0);
      GLES20.glUniformMatrix4fv(uMvp,1,false,mvp,0);GLES20.glUniformMatrix4fv(uModel,1,false,model,0);
      GLES20.glUniform4fv(uColor,1,color,0);GLES20.glUniform1f(uUseTex,texture!=0?1f:0f);GLES20.glUniform1f(uLit,lit?1f:0f);
      if(texture!=0){GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);GLES20.glUniform1i(uTex,0);}
      GLES20.glEnableVertexAttribArray(aPos);GLES20.glVertexAttribPointer(aPos,3,GLES20.GL_FLOAT,false,0,m.pos);
      GLES20.glEnableVertexAttribArray(aUv);GLES20.glVertexAttribPointer(aUv,2,GLES20.GL_FLOAT,false,0,m.uv);
      GLES20.glEnableVertexAttribArray(aNormal);GLES20.glVertexAttribPointer(aNormal,3,GLES20.GL_FLOAT,false,0,m.norm);
      GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,m.count);
      GLES20.glDisableVertexAttribArray(aPos);GLES20.glDisableVertexAttribArray(aUv);GLES20.glDisableVertexAttribArray(aNormal);
    }

    void drawMesh(Mesh m,float[] pv,float[] model,int texture,float[] color){
      drawMeshInternal(m,pv,model,texture,color,false);
    }

    void drawLitMesh(Mesh m,float[] pv,float[] model,int texture,float[] color){
      drawMeshInternal(m,pv,model,texture,color,true);
    }

    void loadAssets(){
      try{
        tex.put("felt",loadTexture(findAsset("extracted","_Felt.png")));tex.put("wood",loadTexture(findAsset("extracted","_Wood.png")));tex.put("foot",loadTexture(findAsset("extracted","_Foot.png")));
        addTable("1631477688504185304_default.obj","felt",1,1,1);addTable("106991748306668190_default.obj","wood",1,1,1);
        addTable("-4426823995715296130_default.obj","wood",1,1,1);addTable("-2382927779929703425_default.obj","wood",1,1,1);
        addTable("327116407711713708_default.obj","foot",1,1,1);
        addTable("-7406165369254709877_default.obj",null,.376f,.376f,.376f);addTable("-1542075189251850320_default.obj",null,.376f,.376f,.376f);
        addTable("-4920293310515908916_default.obj",null,.063f,.063f,.063f);addTable("6851484603853778718_default.obj",null,.9f,.92f,.96f);addTable("6459398429916909974_default.obj",null,.8f,.8f,.8f);
        try{
          tex.put("falcon",loadTexture("falcon/falcon_diffuse.png"));
          for(int i=1;i<=16;i++){
            String n=String.format(java.util.Locale.US,"falcon/chassis_%02d.obj",i);
            try{falconMeshes.add(loadObj(n));}catch(Exception ignored){}
          }
        }catch(Exception ignored){}
        sphere=loadObj("objects/01_Tatooine/-8750451297455424342_default.obj");
        hiltCylinder=makeCylinderMesh(40);
        hiltBox=makeBoxMesh();
        for(int i=0;i<6;i++){
          try{realHiltMeshes[i]=loadMeshBin("real_hilts/hilt_"+i+".meshbin");}catch(Exception e){realHiltMeshes[i]=null;}
          try{realHiltTextures[i]=loadTexture("real_hilts/hilt_"+i+".webp");}catch(Exception e){realHiltTextures[i]=0;}
          try{hiltTextures[i]=loadTexture("hilt_materials/hilt_"+i+".png");}catch(Exception e){hiltTextures[i]=0;}
        }
        for(int i=0;i<objectFolders.length;i++)tex.put("ball"+i,loadTexture(findAsset("objects/"+objectFolders[i],".png")));
        for(int i=0;i<6;i++){
          saberMeshes[i]=null;
          try{saberTextures[i]=loadSaberTexture("sabers/"+saberFolders[i]+"/blade.png");}catch(Exception e){saberTextures[i]=0;}
        }
        for(int st=0;st<7;st++)for(int band=0;band<3;band++){
          float rr=ringRadii[st][band][0],th=ringRadii[st][band][1];
          ringMeshes[st*3+band]=rr>0?makeRingMesh(rr,th*.88f,48):null;
        }
      }catch(Exception e){e.printStackTrace();}
    }

    Mesh makeCylinderMesh(int seg){
      ArrayList<Float> p=new ArrayList<>(),u=new ArrayList<>();
      for(int i=0;i<seg;i++){
        float a0=(float)(Math.PI*2*i/seg),a1=(float)(Math.PI*2*(i+1)/seg);
        float y0=(float)Math.cos(a0),z0=(float)Math.sin(a0),y1=(float)Math.cos(a1),z1=(float)Math.sin(a1);
        float v0=i/(float)seg,v1=(i+1)/(float)seg;

        float[][] side={
          {-.5f,y0,z0,0f,v0},{ .5f,y0,z0,1f,v0},{ .5f,y1,z1,1f,v1},
          {-.5f,y0,z0,0f,v0},{ .5f,y1,z1,1f,v1},{-.5f,y1,z1,0f,v1}
        };
        for(float[] q:side){
          p.add(q[0]);p.add(q[1]);p.add(q[2]);u.add(q[3]);u.add(q[4]);
        }

        // End caps use a neutral sample from the material.
        float[][] caps={
          {-.5f,0,0,.5f,.5f},{-.5f,y1,z1,.5f,.5f},{-.5f,y0,z0,.5f,.5f},
          { .5f,0,0,.5f,.5f},{ .5f,y0,z0,.5f,.5f},{ .5f,y1,z1,.5f,.5f}
        };
        for(float[] q:caps){
          p.add(q[0]);p.add(q[1]);p.add(q[2]);u.add(q[3]);u.add(q[4]);
        }
      }
      float[] pp=new float[p.size()],uv=new float[u.size()];
      for(int i=0;i<pp.length;i++)pp[i]=p.get(i);for(int i=0;i<uv.length;i++)uv[i]=u.get(i);
      return new Mesh(pp,uv);
    }

    Mesh makeBoxMesh(){
      float[] p={
        -.5f,-.5f,-.5f, .5f,-.5f,-.5f, .5f,.5f,-.5f,  -.5f,-.5f,-.5f, .5f,.5f,-.5f, -.5f,.5f,-.5f,
        -.5f,-.5f,.5f,  .5f,.5f,.5f,  .5f,-.5f,.5f,   -.5f,-.5f,.5f, -.5f,.5f,.5f, .5f,.5f,.5f,
        -.5f,-.5f,-.5f,-.5f,.5f,-.5f,-.5f,.5f,.5f,   -.5f,-.5f,-.5f,-.5f,.5f,.5f,-.5f,-.5f,.5f,
         .5f,-.5f,-.5f, .5f,-.5f,.5f, .5f,.5f,.5f,    .5f,-.5f,-.5f, .5f,.5f,.5f, .5f,.5f,-.5f,
        -.5f,.5f,-.5f, .5f,.5f,-.5f, .5f,.5f,.5f,     -.5f,.5f,-.5f, .5f,.5f,.5f,-.5f,.5f,.5f,
        -.5f,-.5f,-.5f, .5f,-.5f,.5f, .5f,-.5f,-.5f, -.5f,-.5f,-.5f,-.5f,-.5f,.5f,.5f,-.5f,.5f
      };
      float[] uv=new float[p.length/3*2];
      return new Mesh(p,uv);
    }

    float hiltWorldLength(){float[] L={10.8f,10.7f,10.5f,13.8f,10.7f,10.9f};return L[Math.max(0,Math.min(5,hiltIndex))];}
    float hiltWorldRadius(){return hiltIndex==3?.72f:.78f;}
    float hiltFrontEmitterOffset(){
      float L=hiltWorldLength();
      return hiltIndex==3?(L*.515f+.58f):(L*.50f);
    }
    float hiltRearEndOffset(){
      float L=hiltWorldLength();
      return hiltIndex==3?(L*.515f+.58f):(L*.50f);
    }
    float hiltBackWorld(){return hiltFrontEmitterOffset()+.95f+chargePullWorld;}

    void drawTexturedHiltCore(float[] pv,float len,float radius,float y,float angle,int texture){
      float[] M=identity();
      Ball cue=balls.get(0);
      float cx=cue.x-aimX*hiltBackWorld();
      float cz=cue.z-aimZ*hiltBackWorld();
      android.opengl.Matrix.translateM(M,0,cx,y,cz);
      android.opengl.Matrix.rotateM(M,0,angle,0,1,0);
      android.opengl.Matrix.scaleM(M,0,len,radius,radius);
      drawMesh(hiltCylinder,pv,M,texture,new float[]{1f,1f,1f,1f});
    }

    void drawHiltPart(float[] pv,float center,float len,float radius,float y,float z,float angle,float[] color){
      float[] M=identity();
      Ball cue=balls.get(0);
      float cx=cue.x-aimX*hiltBackWorld()+aimX*center;
      float cz=cue.z-aimZ*hiltBackWorld()+aimZ*center;
      android.opengl.Matrix.translateM(M,0,cx,y,cz);
      android.opengl.Matrix.rotateM(M,0,angle,0,1,0);
      android.opengl.Matrix.scaleM(M,0,len,radius,radius);
      drawLitMesh(hiltCylinder,pv,M,0,color);
    }

    void drawHiltBox(float[] pv,float center,float len,float sy,float sz,float y,float z,float angle,float[] color){
      float[] M=identity();
      Ball cue=balls.get(0);
      float cx=cue.x-aimX*hiltBackWorld()+aimX*center;
      float cz=cue.z-aimZ*hiltBackWorld()+aimZ*center;
      android.opengl.Matrix.translateM(M,0,cx,y,cz);
      android.opengl.Matrix.rotateM(M,0,angle,0,1,0);
      android.opengl.Matrix.scaleM(M,0,len,sy,sz);
      drawLitMesh(hiltBox,pv,M,0,color);
    }

    void drawWorldHilt3D(float[] pv){
      if(balls.isEmpty())return;
      float angle=(float)Math.toDegrees(Math.atan2(-aimZ,aimX));
      float y=2.72f;

      int hi=Math.max(0,Math.min(5,hiltIndex));
      Mesh authored=realHiltMeshes[hi];
      if(authored!=null){
        Ball cue=balls.get(0);
        float cx=cue.x-aimX*hiltBackWorld();
        float cz=cue.z-aimZ*hiltBackWorld();
        float[] M=identity();
        android.opengl.Matrix.translateM(M,0,cx,y,cz);
        android.opengl.Matrix.rotateM(M,0,angle,0,1,0);
        // Exporter normalized each real model to a 1.0-unit dominant axis while
        // preserving its true proportions, so a uniform scale keeps the authored shape.
        float L=hiltWorldLength();
        android.opengl.Matrix.scaleM(M,0,L,L,L);
        drawLitMesh(authored,pv,M,realHiltTextures[hi],new float[]{1f,1f,1f,1f});

        float[] rgb=bladeRgb[Math.max(0,Math.min(5,bladeIndex))];

        if(hi==3){
          // The source Maul blend is slightly asymmetric at the emitter caps.
          // Add matching physical collars to BOTH ends so the double hilt reads
          // correctly from every camera angle without changing gameplay physics.
          float e=L*.515f;
          float[] silver={.78f,.81f,.86f,1f};
          float[] gunmetal={.13f,.14f,.16f,1f};
          // Mirrored 3-piece emitter assemblies on both ends.
          drawHiltPart(pv, e,.52f,1.14f,y,0,angle,silver);
          drawHiltPart(pv,-e,.52f,1.14f,y,0,angle,silver);
          drawHiltPart(pv, e+.30f,.22f,.94f,y,0,angle,gunmetal);
          drawHiltPart(pv,-e-.30f,.22f,.94f,y,0,angle,gunmetal);
          drawHiltPart(pv, e+.45f,.16f,.72f,y,0,angle,silver);
          drawHiltPart(pv,-e-.45f,.16f,.72f,y,0,angle,silver);

          GLES20.glDepthMask(false);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE);
          drawHiltPart(pv, e+.51f,.10f,.80f,y,0,angle,new float[]{rgb[0],rgb[1],rgb[2],.46f});
          drawHiltPart(pv,-e-.51f,.10f,.80f,y,0,angle,new float[]{rgb[0],rgb[1],rgb[2],.46f});
          GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);GLES20.glDepthMask(true);

          // In aiming mode the cue-facing blade joins the predictor at the cue ball,
          // while the rear emitter projects an equal cosmetic blade in the opposite
          // direction. These are render-only and have no collider.
          if(state==AIMING){
            float frontX=cx+aimX*(e+.58f),frontZ=cz+aimZ*(e+.58f);
            float rearX=cx-aimX*(e+.58f),rearZ=cz-aimZ*(e+.58f);
            drawSaberSegment(pv,frontX,frontZ,cue.x,cue.z,rgb[0],rgb[1],rgb[2]);
            float rearBladeLen=12.5f;
            drawSaberSegment(pv,rearX,rearZ,rearX-aimX*rearBladeLen,rearZ-aimZ*rearBladeLen,rgb[0],rgb[1],rgb[2]);
          }
        }else{
          // Single-ended hilts keep one subtle emitter glow.
          GLES20.glDepthMask(false);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE);
          drawHiltPart(pv,L*.49f,.12f,.98f,y,0,angle,new float[]{rgb[0],rgb[1],rgb[2],.42f});
          GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);GLES20.glDepthMask(true);
        }
        return;
      }
      float[] silver={.72f,.76f,.82f,1};
      float[] bright={.90f,.93f,.98f,1};
      float[] dark={.055f,.065f,.08f,1};
      float[] black={.018f,.020f,.025f,1};
      float[] bronze={.34f,.19f,.08f,1};
      float[] gold={.62f,.43f,.10f,1};
      float[] gunmetal={.18f,.20f,.23f,1};

      // Variant palettes keep the six loadout choices visually distinct while
      // remaining true 3D objects. PNGs are now only menu thumbnails.
      float[] body=silver,grip=dark,accent=bright;
      if(hiltIndex==1){body=bright;grip=gunmetal;accent=gold;}
      else if(hiltIndex==2){body=gunmetal;grip=black;accent=new float[]{.30f,.68f,.92f,1};}
      else if(hiltIndex==3){body=gunmetal;grip=black;accent=bright;}
      else if(hiltIndex==4){body=bright;grip=black;accent=bronze;}
      else if(hiltIndex==5){body=bronze;grip=black;accent=gunmetal;}

      float L=hiltWorldLength();
      float coreRadius=hiltIndex==3?.63f:.72f;
      drawTexturedHiltCore(pv,L,coreRadius,y,angle,hiltTextures[Math.max(0,Math.min(5,hiltIndex))]);

      if(hiltIndex==3){
        // Double-ended hilt.
        // Textured core supplies the detailed body/grip finish.
        drawHiltPart(pv,-4.35f,.48f,.82f,y,0,angle,body);
        drawHiltPart(pv, 4.35f,.48f,.82f,y,0,angle,body);
        drawHiltPart(pv,-5.45f,.42f,1.02f,y,0,angle,accent);
        drawHiltPart(pv, 5.45f,.42f,1.02f,y,0,angle,accent);
        for(int i=-2;i<=2;i++)drawHiltPart(pv,i*1.0f,.12f,.74f,y,0,angle,new float[]{.62f,.66f,.72f,.72f});
        drawHiltBox(pv,0,.78f,.36f,.72f,y+.66f,0,angle,new float[]{.65f,.08f,.05f,1});
      }else{
        // The HD TTS-derived material supplies the main grip/body. These pieces
        // only add physical depth at the emitter and pommel.
        drawHiltPart(pv, 3.55f,.95f,.80f,y,0,angle,body);
        drawHiltPart(pv, 4.45f,.46f,1.02f,y,0,angle,accent);
        drawHiltPart(pv,-4.10f,.72f,.84f,y,0,angle,body);
        drawHiltPart(pv,-4.62f,.34f,.94f,y,0,angle,accent);

        // Grip bands.
        for(int i=0;i<3;i++){
          float x=-2.65f+i*1.45f;
          drawHiltPart(pv,x,.10f,.76f,y,0,angle,new float[]{.70f,.73f,.78f,.58f});
        }

        // Style-specific details.
        if(hiltIndex==2){
          drawHiltPart(pv,.55f,1.22f,.73f,y,0,angle,accent);
          GLES20.glDepthMask(false);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE);
          drawHiltPart(pv,.55f,1.05f,.82f,y,0,angle,new float[]{.12f,.62f,1f,.34f});
          GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);GLES20.glDepthMask(true);
        }else if(hiltIndex==4){
          drawHiltBox(pv,.35f,1.48f,.40f,.84f,y+.72f,0,angle,bronze);
        }else if(hiltIndex==5){
          for(int i=0;i<4;i++)drawHiltPart(pv,-2.55f+i*.78f,.28f,.79f,y,0,angle,bronze);
        }

        // Activation switch/control box.
        float[] switchColor=hiltIndex==2?new float[]{.10f,.65f,1f,1}:new float[]{.70f,.06f,.045f,1};
        drawHiltBox(pv,1.25f,.72f,.30f,.64f,y+.72f,0,angle,switchColor);
      }

      // Selected blade color softly lights the emitter ring.
      float[] rgb=bladeRgb[Math.max(0,Math.min(5,bladeIndex))];
      GLES20.glDepthMask(false);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE);
      float emitter=hiltIndex==3?5.72f:4.78f;
      drawHiltPart(pv,emitter,.18f,hiltIndex==3?1.12f:1.16f,y,0,angle,new float[]{rgb[0],rgb[1],rgb[2],.46f});
      if(hiltIndex==3)drawHiltPart(pv,-emitter,.18f,1.12f,y,0,angle,new float[]{rgb[0],rgb[1],rgb[2],.34f});
      GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);GLES20.glDepthMask(true);
    }

    Mesh makeRingMesh(float radius,float thickness,int seg){
      float inner=Math.max(.02f,radius-thickness*.5f),outer=radius+thickness*.5f;
      float[] p=new float[seg*6*3],uv=new float[seg*6*2];int pi=0,ui=0;
      for(int i=0;i<seg;i++){
        float a0=(float)(Math.PI*2*i/seg),a1=(float)(Math.PI*2*(i+1)/seg);
        float c0=(float)Math.cos(a0),s0=(float)Math.sin(a0),c1=(float)Math.cos(a1),s1=(float)Math.sin(a1);
        float[][] v={{inner, c0,s0},{outer,c0,s0},{outer,c1,s1},{inner,c0,s0},{outer,c1,s1},{inner,c1,s1}};
        for(int k=0;k<6;k++){
          float rad=v[k][0],c=v[k][1],sn=v[k][2];
          p[pi++]=rad*c;p[pi++]=0;p[pi++]=rad*sn;
          uv[ui++]=(k==1||k==2||k==4)?1:0;uv[ui++]=(i+(k>=2?1:0))/(float)seg;
        }
      }
      return new Mesh(p,uv);
    }

    void drawPlanetRing(float[] pv,Ball b){
      if(b.index<9||b.index>15||b.sinking)return;
      int st=b.index-9;
      GLES20.glDepthMask(false);
      GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE);
      for(int layer=0;layer<7;layer++){
        float t=layer/6f,yy=(t-.5f)*ringDepth[st]*.78f;
        float center=1f-Math.abs(t-.5f)*2f,fade=.62f+.38f*center;
        for(int band=0;band<3;band++){
          Mesh rm=ringMeshes[st*3+band];if(rm==null)continue;
          float[] M=identity();
          android.opengl.Matrix.translateM(M,0,b.x,2.22f+yy,b.z);
          android.opengl.Matrix.rotateM(M,0,ringTilt[st][0],1,0,0);
          android.opengl.Matrix.rotateM(M,0,ringTilt[st][1],0,0,1);
          float[] c=ringColors[st][band];
          drawMesh(rm,pv,M,0,new float[]{c[0]*fade,c[1]*fade,c[2]*fade,.36f+.34f*center});
        }
      }
      GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
      GLES20.glDepthMask(true);
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
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,0x84FE,Math.min(16f,maxA[0]));
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

    Mesh loadMeshBin(String path)throws Exception{
      try(DataInputStream in=new DataInputStream(new BufferedInputStream(ctx.getAssets().open(path),262144))){
        byte[] magic=new byte[4];in.readFully(magic);
        if(magic[0]!='G'||magic[1]!='M'||magic[2]!='H'||magic[3]!='1')throw new IOException("Bad mesh "+path);
        int n=in.readInt();
        if(n<=0||n>500000)throw new IOException("Bad vertex count "+n+" in "+path);
        float[] p=new float[n*3],t=new float[n*2];
        for(int i=0;i<n;i++){
          p[i*3]=in.readFloat();p[i*3+1]=in.readFloat();p[i*3+2]=in.readFloat();
          t[i*2]=in.readFloat();t[i*2+1]=in.readFloat();
        }
        return new Mesh(p,t);
      }
    }

    Mesh loadGzipMesh(String path)throws Exception{
      try(DataInputStream in=new DataInputStream(new BufferedInputStream(new java.util.zip.GZIPInputStream(ctx.getAssets().open(path)),262144))){
        byte[] magic=new byte[4];in.readFully(magic);
        if(magic[0]!='G'||magic[1]!='M'||magic[2]!='H'||magic[3]!='1')throw new IOException("Bad mesh "+path);
        int n=in.readInt();
        float[] p=new float[n*3],t=new float[n*2];
        for(int i=0;i<n;i++){
          p[i*3]=in.readFloat();p[i*3+1]=in.readFloat();p[i*3+2]=in.readFloat();
          t[i*2]=in.readFloat();t[i*2+1]=in.readFloat();
        }
        return new Mesh(p,t);
      }
    }

    Mesh loadObj(String path)throws Exception{
      String bin=path.endsWith(".obj")?path.substring(0,path.length()-4)+".meshbin":path+".meshbin";
      try(DataInputStream in=new DataInputStream(new BufferedInputStream(ctx.getAssets().open(bin),262144))){
        byte[] magic=new byte[4];in.readFully(magic);
        if(magic[0]=='G'&&magic[1]=='M'&&magic[2]=='H'&&magic[3]=='1'){
          int n=in.readInt();
          float[] p=new float[n*3],t=new float[n*2];
          for(int i=0;i<n;i++){
            p[i*3]=in.readFloat();p[i*3+1]=in.readFloat();p[i*3+2]=in.readFloat();
            t[i*2]=in.readFloat();t[i*2+1]=in.readFloat();
          }
          return new Mesh(p,t);
        }
      }catch(IOException ignored){}

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
      if(pinch>.01f)camDist=Math.max(70f,Math.min(340f,camDist/pinch));
    }

    float[] worldToScreen(float x,float y,float z,int w,int h){
      float[] v={x,y,z,1},clip=new float[4];android.opengl.Matrix.multiplyMV(clip,0,pvCache,0,v,0);
      if(clip[3]<=.001f)return null;
      float nx=clip[0]/clip[3],ny=clip[1]/clip[3];
      return new float[]{(nx*.5f+.5f)*w,(.5f-ny*.5f)*h};
    }

    void createRailEdge(float x1,float z1,float x2,float z2){
      EdgeShape edge=new EdgeShape();edge.set(new Vec2(x1,z1),new Vec2(x2,z2));
      FixtureDef fd=new FixtureDef();fd.shape=edge;fd.friction=TTS_DYNAMIC_FRICTION;fd.restitution=.74f;
      railBody.createFixture(fd);
    }

    void buildPhysicsWorld(){
      world=new World(new Vec2(0,0));
      world.setContinuousPhysics(true);world.setWarmStarting(true);
      BodyDef rbd=new BodyDef();rbd.type=BodyType.STATIC;railBody=world.createBody(rbd);

      boolean loadedTableCollider=false;
      try(BufferedReader br=new BufferedReader(new InputStreamReader(ctx.getAssets().open("extracted/table_collider_2d.txt")))){
        String line;
        while((line=br.readLine())!=null){
          line=line.trim();if(line.isEmpty()||line.startsWith("#"))continue;
          String[] q=line.split("\\s+");if(q.length<4)continue;
          createRailEdge(Float.parseFloat(q[0]),Float.parseFloat(q[1]),Float.parseFloat(q[2]),Float.parseFloat(q[3]));
          loadedTableCollider=true;
        }
      }catch(Exception e){e.printStackTrace();}

      // Fallback only if the extracted Unity MeshCollider could not be loaded.
      if(!loadedTableCollider){
        final float CX=42f,CZ=21f,CORNER=3.65f,SIDE=2.95f;
        createRailEdge(-CX,-CZ+CORNER,-CX,CZ-CORNER);
        createRailEdge( CX,-CZ+CORNER, CX,CZ-CORNER);
        createRailEdge(-CX+CORNER,-CZ,-SIDE,-CZ);
        createRailEdge(SIDE,-CZ,CX-CORNER,-CZ);
        createRailEdge(-CX+CORNER, CZ,-SIDE, CZ);
        createRailEdge(SIDE, CZ,CX-CORNER, CZ);
      }

      float density=(float)(TTS_MASS/(Math.PI*PHYS_R*PHYS_R));
      for(Ball b:balls){
        BodyDef bd=new BodyDef();bd.type=BodyType.DYNAMIC;bd.position.set(b.x,b.z);
        bd.linearDamping=TTS_DRAG;bd.angularDamping=TTS_ANGULAR_DRAG;
        bd.bullet=(b.index==0);bd.allowSleep=true;
        b.body=world.createBody(bd);
        CircleShape cs=new CircleShape();cs.m_radius=PHYS_R;
        FixtureDef fd=new FixtureDef();fd.shape=cs;fd.density=density;fd.friction=TTS_DYNAMIC_FRICTION;fd.restitution=.89f;
        b.body.createFixture(fd);
        b.body.setUserData(b);
      }
    }

    int suitForBall(int index){
      if(index>=1&&index<=7)return 1;
      if(index>=9&&index<=15)return 2;
      return 0;
    }

    boolean isBallOnTable(int index){
      for(Ball b:balls)if(b.index==index)return b.active||b.sinking;
      return false;
    }

    int remainingForSuit(int suit){
      int start=suit==1?1:9,end=suit==1?7:15,n=0;
      for(int i=start;i<=end;i++)if(isBallOnTable(i))n++;
      return n;
    }

    void resetRules(){
      if(sfx!=null){sfx.stopHum();sfx.stopVictory();}
      currentTeam=1;winnerTeam=0;activeShooter=1;teamSuit[0]=teamSuit[1]=0;
      tableOpen=true;gameOver=false;ballsSunkThisShot.clear();
      ruleMessage="BREAK • TEAM 1";
    }

    void recordPocket(int index){
      if(!ballsSunkThisShot.contains(index))ballsSunkThisShot.add(index);
      if(index==8&&!gameOver){
        winnerTeam=currentTeam;gameOver=true;
        ruleMessage="8 BALL • TEAM "+currentTeam+" WINS";
      }
    }

    void resolveShotRules(){
      if(gameOver){ballsSunkThisShot.clear();return;}
      boolean scratch=false,valid=false;
      int teamIdx=currentTeam-1;

      for(Integer idx:ballsSunkThisShot){
        if(idx==0){scratch=true;continue;}
        if(idx==8)continue;
        int type=suitForBall(idx);
        if(type==0)continue;

        if(tableOpen){
          tableOpen=false;
          teamSuit[teamIdx]=type;
          teamSuit[1-teamIdx]=(type==1)?2:1;
          valid=true;
          ruleMessage="TEAM "+currentTeam+" CLAIMED "+(type==1?"SOLIDS":"STRIPES");
        }else if(teamSuit[teamIdx]==type){
          valid=true;
        }
      }

      if(scratch){
        currentTeam=currentTeam==1?2:1;
        ruleMessage="SCRATCH • TEAM "+currentTeam+" TURN";
      }else if(valid){
        int remain=remainingForSuit(teamSuit[currentTeam-1]);
        ruleMessage=remain==0?("TEAM "+currentTeam+" • 8 BALL READY"):("TEAM "+currentTeam+" CONTINUES");
      }else{
        currentTeam=currentTeam==1?2:1;
        ruleMessage="TEAM "+currentTeam+" TURN";
      }
      activeShooter=currentTeam;
      ballsSunkThisShot.clear();
    }

    boolean localCanControl(){
      return net==null||net.canLocalControl(activeShooter);
    }

    boolean routeFollowerCommand(String cmd){
      if(net!=null&&net.isFollower()){
        if(localCanControl())net.send("CMD|"+cmd);
        return true;
      }
      return false;
    }

    void userResetRack(){
      if(net!=null&&net.isFollower()){net.send("CMD|RACK");return;}
      resetRack();
    }

    void userSelectHilt(int k){
      k=Math.max(0,Math.min(5,k));
      if(net!=null&&net.isFollower()){
        if(localCanControl()){hiltIndex=k;net.send("CMD|HILT|"+k);}
        return;
      }
      if(localCanControl())hiltIndex=k;
    }

    void userSelectBlade(int k){
      k=Math.max(0,Math.min(5,k));
      if(net!=null&&net.isFollower()){
        if(localCanControl()){bladeIndex=k;net.send("CMD|BLADE|"+k);}
        return;
      }
      if(localCanControl())bladeIndex=k;
    }

    void userToggleActiveShooter(){
      if(net!=null&&net.isFollower()){net.send("CMD|SHOOTER");return;}
      activeShooter=activeShooter==1?2:1;
      ruleMessage="PLAYER "+activeShooter+" ACTIVE SHOOTER";
    }

    void userSwitchTeam(){
      if(net!=null&&net.isFollower()){net.send("CMD|TEAM");return;}
      currentTeam=currentTeam==1?2:1;
      ruleMessage="TEAM "+currentTeam+" ACTIVE";
    }

    void resetRack(){
      balls.clear();physicsAccum=0;resetRules();
      Ball cueBall=new Ball(-20f,0f,tex.getOrDefault("ball0",0));cueBall.index=0;balls.add(cueBall);

      // Exact positions from Star Wars Galactic 8-Ball v428 / TTS.
      float[][] p={
        {20.000f, 0.000f},
        {22.090f,-1.214f},{22.092f, 1.207f},
        {24.178f,-2.421f},{24.184f, 0.004f},{24.180f, 2.417f},
        {26.271f,-3.635f},{26.267f,-1.205f},{26.274f, 1.216f},{26.269f, 3.626f},
        {28.355f,-4.846f},{28.364f,-2.414f},{28.357f,-0.006f},{28.366f, 2.427f},{28.360f, 4.836f}
      };
      // TTS visual rack coordinates leave a tiny gap relative to the extracted
      // 1.19-radius SphereCollider. Compress the rack by ~1.3% around the apex so
      // neighboring physical colliders are actually touching like a real tight rack.
      final float rackPack=.987f;
      for(int i=0;i<15;i++){
        float px=20f+(p[i][0]-20f)*rackPack;
        float pz=p[i][1]*rackPack;
        Ball nb=new Ball(px,pz,tex.getOrDefault("ball"+(i+1),0));nb.index=i+1;balls.add(nb);
      }
      buildPhysicsWorld();
      state=AIMING;activeShooter=1;power=0;chargePullPx=0;chargePullWorld=0;englishX=englishY=0;aimX=desiredAimX=1;aimZ=desiredAimZ=0;sideSpin=topSpin=0;
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

    void setAimAngleDirect(float angle){
      float x=(float)Math.cos(angle),z=(float)Math.sin(angle);
      aimX=desiredAimX=x;aimZ=desiredAimZ=z;
    }

    void previewAimAngle(float angle){
      if(!localCanControl())return;
      if(net!=null&&net.isFollower()){setAimAngleDirect(angle);net.send("CMD|AIM|"+angle);return;}
      setAimAngleDirect(angle);
    }

    void commitAimAngle(float angle){
      if(!localCanControl())return;
      if(net!=null&&net.isFollower()){setAimAngleDirect(angle);net.send("CMD|AIM|"+angle);return;}
      setAimAngleDirect(angle);
    }

    void lockAngleDirect(){
      if(state==AIMING&&!gameOver&&allStopped()){
        float n=(float)Math.sqrt(desiredAimX*desiredAimX+desiredAimZ*desiredAimZ);
        if(n>.0001f){aimX=desiredAimX/n;aimZ=desiredAimZ/n;desiredAimX=aimX;desiredAimZ=aimZ;}
        state=SELECTING_ENGLISH;power=0;englishX=englishY=0;
      }
    }

    void lockAngle(){
      if(!localCanControl())return;
      if(net!=null&&net.isFollower()){net.send("CMD|LOCK");return;}
      lockAngleDirect();
    }

    void setEnglishDirect(float x,float y){
      englishX=Math.max(-1,Math.min(1,x));englishY=Math.max(-1,Math.min(1,y));
    }

    void setEnglish(float x,float y){
      if(!localCanControl())return;
      setEnglishDirect(x,y);
      if(net!=null&&net.isFollower())net.send("CMD|ENG|"+englishX+"|"+englishY);
    }

    void confirmEnglishDirect(){
      if(state==SELECTING_ENGLISH){
        state=CHARGING;power=0;chargePullPx=0;chargePullWorld=0;chargeStartY=-1;
        if(sfx!=null)sfx.ignite(false);
      }
    }

    void confirmEnglish(){
      if(!localCanControl())return;
      if(net!=null&&net.isFollower()){net.send("CMD|CONFIRM");confirmEnglishDirect();return;}
      confirmEnglishDirect();
    }

    void cancelEnglishDirect(){
      if(state==SELECTING_ENGLISH){
        state=AIMING;power=0;chargePullPx=0;chargePullWorld=0;englishX=englishY=0;
        if(sfx!=null)sfx.deactivate();
      }
    }

    void cancelEnglish(){
      if(!localCanControl())return;
      if(net!=null&&net.isFollower()){net.send("CMD|CANCEL");cancelEnglishDirect();return;}
      cancelEnglishDirect();
    }

    void beginWorldChargeDirect(){
      if(state==CHARGING){power=0;chargePullPx=0;chargePullWorld=0;}
    }

    void beginWorldCharge(){
      if(!localCanControl())return;
      if(net!=null&&net.isFollower()){net.send("CMD|BEGIN");beginWorldChargeDirect();return;}
      beginWorldChargeDirect();
    }

    void updateWorldChargeDirect(float pullPx,int h){
      if(state!=CHARGING)return;
      chargePullPx=pullPx;
      chargePullWorld=pullPx/Math.max(10f,h*.030f);
      power=Math.min(100f,pullPx/Math.max(85f,h*.18f)*100f);
    }

    void updateWorldCharge(float pullPx,int h){
      if(!localCanControl())return;
      updateWorldChargeDirect(pullPx,h);
      if(net!=null&&net.isFollower())net.send("CMD|POWER|"+pullPx+"|"+h);
    }

    void releaseWorldChargeDirect(){
      if(state!=CHARGING)return;
      if(power>=5){
        if(sfx!=null)sfx.clash();
        executeShot();
      }else{
        power=0;chargePullPx=0;chargePullWorld=0;state=AIMING;
        if(sfx!=null)sfx.deactivate();
      }
    }

    void releaseWorldCharge(){
      if(!localCanControl())return;
      if(net!=null&&net.isFollower()){net.send("CMD|RELEASE");return;}
      releaseWorldChargeDirect();
    }

    String buildNetworkState(){
      String rule="";
      try{rule=android.util.Base64.encodeToString((ruleMessage==null?"":ruleMessage).getBytes("UTF-8"),android.util.Base64.NO_WRAP);}catch(Exception ignored){}
      StringBuilder sb=new StringBuilder(1400);
      sb.append("STATE|").append(state).append('|').append(currentTeam).append('|').append(activeShooter)
        .append('|').append(hiltIndex).append('|').append(bladeIndex)
        .append('|').append(teamSuit[0]).append('|').append(teamSuit[1])
        .append('|').append(tableOpen?1:0).append('|').append(gameOver?1:0).append('|').append(winnerTeam)
        .append('|').append(aimX).append('|').append(aimZ).append('|').append(desiredAimX).append('|').append(desiredAimZ)
        .append('|').append(power).append('|').append(chargePullPx).append('|').append(chargePullWorld)
        .append('|').append(englishX).append('|').append(englishY).append('|').append(rule).append('|');
      for(Ball b:balls){
        sb.append(b.index).append(',').append(b.active?1:0).append(',').append(b.sinking?1:0)
          .append(',').append(b.x).append(',').append(b.z).append(',').append(b.vx).append(',').append(b.vz)
          .append(',').append(b.qx).append(',').append(b.qy).append(',').append(b.qz).append(',').append(b.qw)
          .append(',').append(b.sinkT).append(',').append(b.spin).append(';');
      }
      return sb.toString();
    }

    void applyNetworkState(String line){
      if(net==null||!net.isFollower())return;
      try{
        String[] q=line.split("\\|",-1);
        if(q.length<22)return;
        state=Integer.parseInt(q[1]);currentTeam=Integer.parseInt(q[2]);activeShooter=Integer.parseInt(q[3]);
        hiltIndex=Integer.parseInt(q[4]);bladeIndex=Integer.parseInt(q[5]);
        teamSuit[0]=Integer.parseInt(q[6]);teamSuit[1]=Integer.parseInt(q[7]);
        tableOpen="1".equals(q[8]);gameOver="1".equals(q[9]);winnerTeam=Integer.parseInt(q[10]);
        aimX=Float.parseFloat(q[11]);aimZ=Float.parseFloat(q[12]);desiredAimX=Float.parseFloat(q[13]);desiredAimZ=Float.parseFloat(q[14]);
        power=Float.parseFloat(q[15]);chargePullPx=Float.parseFloat(q[16]);chargePullWorld=Float.parseFloat(q[17]);
        englishX=Float.parseFloat(q[18]);englishY=Float.parseFloat(q[19]);
        try{ruleMessage=new String(android.util.Base64.decode(q[20],android.util.Base64.DEFAULT),"UTF-8");}catch(Exception ignored){}
        String[] bs=q[21].split(";");
        for(String row:bs){
          if(row.isEmpty())continue;
          String[] a=row.split(",");
          if(a.length<13)continue;
          int idx=Integer.parseInt(a[0]);Ball b=null;
          for(Ball z:balls)if(z.index==idx){b=z;break;}
          if(b==null)continue;
          b.active="1".equals(a[1]);b.sinking="1".equals(a[2]);
          b.x=Float.parseFloat(a[3]);b.z=Float.parseFloat(a[4]);b.vx=Float.parseFloat(a[5]);b.vz=Float.parseFloat(a[6]);
          b.qx=Float.parseFloat(a[7]);b.qy=Float.parseFloat(a[8]);b.qz=Float.parseFloat(a[9]);b.qw=Float.parseFloat(a[10]);
          b.sinkT=Float.parseFloat(a[11]);b.spin=Float.parseFloat(a[12]);
          if(b.body!=null)b.body.setActive(false);
        }
      }catch(Exception ignored){}
    }

    void applyNetworkCommand(String cmd){
      if(net==null||!net.hosting)return;
      try{
        String[] a=cmd.split("\\|");
        String op=a[0];
        if("AIM".equals(op)&&a.length>1){setAimAngleDirect(Float.parseFloat(a[1]));}
        else if("LOCK".equals(op)){lockAngleDirect();}
        else if("ENG".equals(op)&&a.length>2){setEnglishDirect(Float.parseFloat(a[1]),Float.parseFloat(a[2]));}
        else if("CONFIRM".equals(op)){confirmEnglishDirect();}
        else if("CANCEL".equals(op)){cancelEnglishDirect();}
        else if("BEGIN".equals(op)){beginWorldChargeDirect();}
        else if("POWER".equals(op)&&a.length>2){updateWorldChargeDirect(Float.parseFloat(a[1]),Integer.parseInt(a[2]));}
        else if("RELEASE".equals(op)){releaseWorldChargeDirect();}
        else if("HILT".equals(op)&&a.length>1){hiltIndex=Math.max(0,Math.min(5,Integer.parseInt(a[1])));}
        else if("BLADE".equals(op)&&a.length>1){bladeIndex=Math.max(0,Math.min(5,Integer.parseInt(a[1])));}
        else if("RACK".equals(op)){resetRack();}
        else if("SHOOTER".equals(op)){activeShooter=activeShooter==1?2:1;ruleMessage="PLAYER "+activeShooter+" ACTIVE SHOOTER";}
        else if("TEAM".equals(op)){currentTeam=currentTeam==1?2:1;ruleMessage="TEAM "+currentTeam+" ACTIVE";}
      }catch(Exception ignored){}
    }

    void executeShot(){
      Ball cue=balls.get(0);
      if(!cue.active){cue.active=true;cue.x=-20;cue.z=0;if(cue.body!=null){cue.body.setActive(true);cue.body.setTransform(new Vec2(-20,0),0);}}
      // Exact TTS shot formula: power * speedMultiplier(1.65) * SHOT_VELOCITY_FACTOR(1.25).
      float pn=Math.max(0f,Math.min(1f,power/100f));
      float androidScale=.60f+.18f*pn*pn;
      float speed=power*1.65f*1.25f*androidScale;
      cue.spin=englishX*speed*.06f;
      sideSpin=englishX;topSpin=englishY;
      if(cue.body!=null){
        cue.body.setLinearVelocity(new Vec2(aimX*speed,aimZ*speed));
        cue.body.setAwake(true);
      }
      state=ROLLING;power=0;chargePullPx=0;chargePullWorld=0;chargeStartY=-1;
    }

    void applyRollQuaternion(Ball b,float dx,float dz){
      float dist=(float)Math.sqrt(dx*dx+dz*dz);if(dist<1e-6f)return;
      float ax=-dz/dist,ay=0,az=dx/dist;
      float half=(dist/R)*.5f;
      float sh=(float)Math.sin(half),ch=(float)Math.cos(half);
      float rx=ax*sh,ry=ay*sh,rz=az*sh,rw=ch;

      // World-space incremental rotation: dq * current.
      float nx=rw*b.qx + rx*b.qw + ry*b.qz - rz*b.qy;
      float ny=rw*b.qy - rx*b.qz + ry*b.qw + rz*b.qx;
      float nz=rw*b.qz + rx*b.qy - ry*b.qx + rz*b.qw;
      float nw=rw*b.qw - rx*b.qx - ry*b.qy - rz*b.qz;
      float n=(float)Math.sqrt(nx*nx+ny*ny+nz*nz+nw*nw);
      if(n>.00001f){b.qx=nx/n;b.qy=ny/n;b.qz=nz/n;b.qw=nw/n;}
    }

    void syncBodies(){
      for(Ball b:balls){
        if(!b.active||b.sinking||b.body==null)continue;
        Vec2 p=b.body.getPosition(),v=b.body.getLinearVelocity();
        float ox=b.x,oz=b.z;
        b.x=p.x;b.z=p.y;
        applyRollQuaternion(b,b.x-ox,b.z-oz);

        // Add cloth-style rolling resistance. JBox2D's exponential damping alone
        // reads visually like air-hockey sliding; this constant deceleration makes
        // the balls roll down naturally and then come to a clean stop.
        float sp=(float)Math.sqrt(v.x*v.x+v.y*v.y);
        if(sp>0){
          float slowBlend=1f-Math.min(1f,sp/11f);
          float clothDecel=ROLL_DECEL_FAST+(ROLL_DECEL_SLOW-ROLL_DECEL_FAST)*slowBlend;
          float ns=Math.max(0f,sp-clothDecel*FIXED_DT);
          if(ns<STOP_SPEED){
            b.body.setLinearVelocity(new Vec2(0,0));
            b.body.setAngularVelocity(0);
            b.vx=b.vz=0;
          }else{
            float q=ns/sp;
            b.body.setLinearVelocity(new Vec2(v.x*q,v.y*q));
            b.vx=v.x*q;b.vz=v.y*q;
          }
        }else{
          b.vx=b.vz=0;
        }
      }
    }

    void advanceSinks(float dt){
      for(Ball b:balls)if(b.active&&b.sinking){
        b.sinkT=Math.min(1f,b.sinkT+dt*3.2f);
        float e=1f-(1f-b.sinkT)*(1f-b.sinkT);
        b.x=b.sinkStartX+(b.sinkX-b.sinkStartX)*e;
        b.z=b.sinkStartZ+(b.sinkZ-b.sinkStartZ)*e;
        if(b.sinkT>=1f){
          b.sinking=false;b.sinkT=0;b.vx=b.vz=b.spin=0;
          if(b.index==0){
            b.x=-20f;b.z=0f;b.active=true;
            if(b.body!=null){b.body.setActive(true);b.body.setTransform(new Vec2(-20,0),0);b.body.setLinearVelocity(new Vec2(0,0));b.body.setAngularVelocity(0);}
          }else{
            b.active=false;
          }
        }
      }
    }

    void step(float dt){
      if(net!=null&&net.isFollower())return;
      if(balls.isEmpty()||world==null)return;
      if(state==ROLLING){
        physicsAccum=Math.min(.08f,physicsAccum+dt);
        int loops=0;
        while(physicsAccum>=FIXED_DT&&loops<32){
          world.step(FIXED_DT,30,12);
          syncBodies();
          for(int i=0;i<balls.size();i++)checkPocket(i,balls.get(i));
          advanceSinks(FIXED_DT);
          physicsAccum-=FIXED_DT;loops++;
        }
        if(allStopped()){
          resolveShotRules();
          state=AIMING;englishX=englishY=0;sideSpin=topSpin=0;chargePullPx=0;chargePullWorld=0;physicsAccum=0;
        }
      }else{
        advanceSinks(dt);
      }
    }

    void startPocketSink(int index,Ball b,float px,float pz){
      if(b.sinking)return;
      if(sfx!=null){
        if(index==0)sfx.scratch();
        else if(index==8)sfx.victory(false);
        else sfx.pocket();
      }
      recordPocket(index);
      b.sinking=true;b.sinkT=0;b.sinkStartX=b.x;b.sinkStartZ=b.z;b.sinkX=px;b.sinkZ=pz;
      b.vx=b.vz=b.spin=0;
      if(b.body!=null){b.body.setLinearVelocity(new Vec2(0,0));b.body.setAngularVelocity(0);b.body.setActive(false);}
    }

    void checkPocket(int index,Ball b){
      if(!b.active||b.sinking)return;
      float ax=Math.abs(b.x),az=Math.abs(b.z);

      if(az>MAXZ+.18f && Math.abs(b.x)<2.35f){
        startPocketSink(index,b,0,b.z>0?MAXZ:MINZ);return;
      }

      if(ax>MAXX-.55f && az>MAXZ-.55f){
        float px=b.x>0?MAXX:MINX,pz=b.z>0?MAXZ:MINZ;
        float dx=b.x-px,dz=b.z-pz;
        if(dx*dx+dz*dz<2.35f*2.35f && (ax>MAXX+.08f||az>MAXZ+.08f)){
          startPocketSink(index,b,px,pz);return;
        }
      }

      if(ax>MAXX+4f||az>MAXZ+4f){
        float px=Math.abs(b.x)<8f?0:(b.x>0?MAXX:MINX);
        float pz=b.z>0?MAXZ:MINZ;
        startPocketSink(index,b,px,pz);
      }
    }

    boolean allStopped(){
      for(Ball b:balls){
        if(!b.active)continue;
        if(b.sinking)return false;
        if(b.body!=null&&b.body.isActive()){
          Vec2 v=b.body.getLinearVelocity();
          if(v.x*v.x+v.y*v.y>STOP_SPEED*STOP_SPEED)return false;
        }
      }
      return true;
    }

    int predictorAltBlade(int ordinal){
      // TTS-style multicolor continuation palette. Never deliberately reuse the
      // player's chosen blade color for auxiliary predictor paths.
      int[] order={2,3,1,5,4,0}; // purple, green, gold, blue, red, white
      int selected=Math.max(0,Math.min(5,bladeIndex));
      for(int k=0;k<order.length;k++){
        int idx=order[(ordinal+k)%order.length];
        if(idx!=selected)return idx;
      }
      return (selected+1)%6;
    }

    float predictorRailDistance(float x,float z,float dx,float dz){
      float t=9999f;
      float px=MAXX+1.35f,nx=MINX-1.35f,pz=MAXZ+1.35f,nz=MINZ-1.35f;
      if(dx>1e-5f)t=Math.min(t,(px-x)/dx);
      if(dx<-1e-5f)t=Math.min(t,(nx-x)/dx);
      if(dz>1e-5f)t=Math.min(t,(pz-z)/dz);
      if(dz<-1e-5f)t=Math.min(t,(nz-z)/dz);
      return Math.max(0f,t);
    }

    void drawPredictor(float[] pv){
      if(balls.isEmpty()||!balls.get(0).active)return;
      Ball cue=balls.get(0);
      float x=cue.x,z=cue.z,dx=aimX,dz=aimZ;
      int selected=Math.max(0,Math.min(5,bladeIndex));

      // Show more table travel than before. The first segment always uses the
      // shooter's selected blade color; later bank segments deliberately rotate
      // through other saber colors to match the multicolor TTS presentation.
      for(int bank=0;bank<6;bank++){
        float railT=9999f;int railAxis=0;
        if(dx>1e-5f){float t=(MAXX-R-x)/dx;if(t>0&&t<railT){railT=t;railAxis=1;}}
        if(dx<-1e-5f){float t=(MINX+R-x)/dx;if(t>0&&t<railT){railT=t;railAxis=1;}}
        if(dz>1e-5f){float t=(MAXZ-R-z)/dz;if(t>0&&t<railT){railT=t;railAxis=2;}}
        if(dz<-1e-5f){float t=(MINZ+R-z)/dz;if(t>0&&t<railT){railT=t;railAxis=2;}}

        float hitT=railT;Ball hit=null;
        for(int i=1;i<balls.size();i++){
          Ball b=balls.get(i);if(!b.active)continue;
          float ox=b.x-x,oz=b.z-z,proj=ox*dx+oz*dz;if(proj<=0)continue;
          float perp=ox*ox+oz*oz-proj*proj,rr=4*R*R;if(perp>rr)continue;
          float t=proj-(float)Math.sqrt(Math.max(0,rr-perp));
          if(t>.03f&&t<hitT){hitT=t;hit=b;}
        }

        float ex=x+dx*hitT,ez=z+dz*hitT;
        int pathBlade=(bank==0)?selected:predictorAltBlade(bank-1);
        drawSaberSegment(pv,x,z,ex,ez,pathBlade);

        if(hit!=null){
          // Object-ball path: extend all the way to the next cushion so the player
          // can actually judge pocket entry instead of getting a short stub.
          float nx=hit.x-ex,nz=hit.z-ez,nd=(float)Math.sqrt(nx*nx+nz*nz);
          if(nd>.001f){nx/=nd;nz/=nd;}
          float objRail=predictorRailDistance(hit.x,hit.z,nx,nz);
          float objLen=Math.max(18f,Math.min(72f,objRail));
          int objectBlade=predictorAltBlade(2);
          drawSaberSegment(pv,hit.x,hit.z,hit.x+nx*objLen,hit.z+nz*objLen,objectBlade);

          // Cue-ball deflection path: also carry it to the cushion.
          float dot=dx*nx+dz*nz,cx=dx-dot*nx,cz=dz-dot*nz;
          float cd=(float)Math.sqrt(cx*cx+cz*cz);
          if(cd>.08f){
            cx/=cd;cz/=cd;
            float cueRail=predictorRailDistance(ex,ez,cx,cz);
            float cueLen=Math.max(16f,Math.min(64f,cueRail));
            int cueBlade=predictorAltBlade(4);
            drawSaberSegment(pv,ex,ez,ex+cx*cueLen,ez+cz*cueLen,cueBlade);
          }
          break;
        }

        x=ex;z=ez;
        if(railAxis==1){dx=-dx;dz*=2.80f;}else{dz=-dz;dx*=2.80f;}
        float a=englishX*.13f,cs=(float)Math.cos(a),sn=(float)Math.sin(a);
        float rx=dx*cs-dz*sn,rz=dx*sn+dz*cs;
        float n=(float)Math.sqrt(rx*rx+rz*rz);dx=rx/n;dz=rz/n;
        x+=dx*.04f;z+=dz*.04f;
      }
    }

    void drawSaberSegment(float[] pv,float x1,float z1,float x2,float z2,int bladeTextureIndex){
      int idx=Math.max(0,Math.min(5,bladeTextureIndex));
      float dx=x2-x1,dz=z2-z1,len=(float)Math.sqrt(dx*dx+dz*dz);if(len<.02f)return;
      float angle=(float)Math.toDegrees(Math.atan2(-dz,dx));
      int texture=saberTextures[idx];
      float width=5.50f;

      GLES20.glDepthMask(false);GLES20.glDisable(GLES20.GL_DEPTH_TEST);

      // Soft halo.
      GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE);
      drawTexturedBlade(texture,pv,x1,z1,len,angle,width*1.55f,.34f,2.49f);

      // Native AssetBundle texture with its baked WHITE CORE intact.
      GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
      drawTexturedBlade(texture,pv,x1,z1,len,angle,width,1.0f,2.52f);

      GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glDepthMask(true);
    }

    // Convenience wrapper for hilt-to-cue blades that should always use the
    // player's currently selected blade.
    void drawSaberSegment(float[] pv,float x1,float z1,float x2,float z2,float r,float g,float b){
      drawSaberSegment(pv,x1,z1,x2,z2,Math.max(0,Math.min(5,bladeIndex)));
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
