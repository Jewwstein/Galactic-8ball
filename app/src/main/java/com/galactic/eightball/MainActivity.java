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
import java.nio.charset.StandardCharsets;
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
  static final String DEFAULT_SERVER_URL="https://galactic-8ball-server.onrender.com";
  GameView game;
  HudView hud;
  MultiplayerManager multiplayer;
  FrameLayout appRoot, gameRoot;
  View homeScreen, lobbyScreen;
  TextView homeStatus, lobbyStatus;
  Button continueButton;
  boolean showingTable=false;

  public void onCreate(Bundle b){
    super.onCreate(b);
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);

    game=new GameView(this);
    hud=new HudView(this,game);
    multiplayer=new MultiplayerManager(this,game,hud);
    game.r.net=multiplayer;
    hud.net=multiplayer;

    appRoot=new FrameLayout(this);
    gameRoot=new FrameLayout(this);

    ImageView space=new ImageView(this);
    space.setScaleType(ImageView.ScaleType.CENTER_CROP);
    try(InputStream in=getAssets().open("environment/sky.jpg")){
      space.setImageBitmap(BitmapFactory.decodeStream(in));
    }catch(Exception ignored){
      space.setBackgroundColor(Color.BLACK);
    }

    gameRoot.addView(space,new FrameLayout.LayoutParams(-1,-1));
    gameRoot.addView(game,new FrameLayout.LayoutParams(-1,-1));
    gameRoot.addView(hud,new FrameLayout.LayoutParams(-1,-1));
    appRoot.addView(gameRoot,new FrameLayout.LayoutParams(-1,-1));

    homeScreen=buildHomeScreen();
    appRoot.addView(homeScreen,new FrameLayout.LayoutParams(-1,-1));

    lobbyScreen=buildLobbyScreen();
    appRoot.addView(lobbyScreen,new FrameLayout.LayoutParams(-1,-1));

    setContentView(appRoot);
    showHomeScreen();
  }

  int dp(float v){
    return (int)(v*getResources().getDisplayMetrics().density+0.5f);
  }

  GradientDrawable homePanel(int fill,int stroke,float radius){
    GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
      new int[]{Color.argb(238,10,22,45),Color.argb(232,3,7,19),Color.argb(242,1,3,10)});
    g.setCornerRadius(dp(radius));
    if(stroke!=Color.TRANSPARENT)g.setStroke(dp(1.4f),stroke);
    return g;
  }

  Drawable galacticPanelDrawable(int accent){
    GradientDrawable shadow=new GradientDrawable();
    shadow.setColor(Color.argb(150,0,0,0));shadow.setCornerRadius(dp(24));
    GradientDrawable body=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
      new int[]{Color.argb(242,13,31,61),Color.argb(244,5,10,28),Color.argb(248,2,5,15)});
    body.setCornerRadius(dp(22));body.setStroke(dp(1.4f),Color.argb(180,Color.red(accent),Color.green(accent),Color.blue(accent)));
    GradientDrawable sheen=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
      new int[]{Color.argb(45,255,255,255),Color.TRANSPARENT});
    sheen.setCornerRadius(dp(22));
    LayerDrawable layer=new LayerDrawable(new Drawable[]{shadow,body,sheen});
    layer.setLayerInset(0,dp(5),dp(8),0,0);
    layer.setLayerInset(1,0,0,dp(5),dp(8));
    layer.setLayerInset(2,dp(2),dp(2),dp(7),dp(10));
    return layer;
  }

  Drawable galacticButtonBackground(int accent,boolean pressed){
    int ar=Color.red(accent),ag=Color.green(accent),ab=Color.blue(accent);
    GradientDrawable shadow=new GradientDrawable();
    shadow.setColor(Color.argb(170,0,0,0));shadow.setCornerRadius(dp(15));
    GradientDrawable body=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
      pressed
        ? new int[]{Color.rgb(8,18,34),Color.rgb(18,35,55),Color.rgb(5,10,20)}
        : new int[]{Color.rgb(31,54,81),Color.rgb(10,22,42),Color.rgb(4,9,19)});
    body.setCornerRadius(dp(14));
    body.setStroke(dp(1.6f),Color.argb(225,ar,ag,ab));
    GradientDrawable shine=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
      new int[]{Color.argb(70,255,255,255),Color.argb(8,255,255,255),Color.TRANSPARENT});
    shine.setCornerRadius(dp(14));
    LayerDrawable l=new LayerDrawable(new Drawable[]{shadow,body,shine});
    l.setLayerInset(0,dp(3),dp(6),0,0);
    l.setLayerInset(1,0,0,dp(3),dp(6));
    l.setLayerInset(2,dp(2),dp(2),dp(5),dp(9));
    return l;
  }

  int planetAccentFor(String label){
    String q=label==null?"":label.toLowerCase(java.util.Locale.US);
    if(q.contains("logout"))return Color.rgb(255,86,92);
    if(q.contains("single")||q.contains("ai"))return Color.rgb(171,105,255);
    if(q.contains("create"))return Color.rgb(255,184,72);
    if(q.contains("browse")||q.contains("room"))return Color.rgb(68,210,255);
    if(q.contains("continue"))return Color.rgb(72,224,177);
    return Color.rgb(86,153,255);
  }

  class PlanetButton extends Button{
    final Paint bp=new Paint(3),bs=new Paint(3);
    final int accent;
    final String label;
    PlanetButton(Context c,String t,int a){
      super(c);label=t;accent=a;
      setText("");
      setContentDescription(t);
      setAllCaps(false);
      setGravity(Gravity.CENTER_VERTICAL);
      setPadding(0,0,0,0);
      setBackgroundColor(Color.TRANSPARENT);
      setWillNotDraw(false);
      if(android.os.Build.VERSION.SDK_INT>=21)setElevation(dp(7));
    }
    protected void onDraw(Canvas c){
      float w=getWidth(),h=getHeight(),press=isPressed()?2.5f:0f;
      c.save();c.translate(0,press);

      RectF body=new RectF(dp(3),dp(3),w-dp(3),h-dp(8));
      bp.setStyle(Paint.Style.FILL);
      bp.setShader(new LinearGradient(0,body.top,0,body.bottom,
        isPressed()?new int[]{0xEE101C2A,0xF40A101A,0xFF03070D}:new int[]{0xF52A4058,0xF3152435,0xFF050A12},
        null,Shader.TileMode.CLAMP));
      bp.setShadowLayer(dp(8),0,dp(5),0xB0000000);
      c.drawRoundRect(body,dp(22),dp(22),bp);
      bp.clearShadowLayer();bp.setShader(null);

      bs.setStyle(Paint.Style.STROKE);bs.setStrokeWidth(dp(1.5f));
      bs.setColor((accent&0x00FFFFFF)|0xD9000000);
      c.drawRoundRect(body,dp(22),dp(22),bs);
      bs.setStrokeWidth(dp(.8f));bs.setColor(0x66FFFFFF);
      c.drawRoundRect(new RectF(body.left+dp(2),body.top+dp(2),body.right-dp(2),body.bottom-dp(2)),dp(20),dp(20),bs);

      float pr=Math.min(h*.33f,dp(20)),px=body.left+dp(34),py=body.centerY();
      int ar=Color.red(accent),ag=Color.green(accent),ab=Color.blue(accent);
      bp.setShader(new RadialGradient(px-pr*.38f,py-pr*.42f,pr*1.18f,
        new int[]{Color.rgb(Math.min(255,ar+105),Math.min(255,ag+105),Math.min(255,ab+105)),accent,Color.rgb(Math.max(0,ar/5),Math.max(0,ag/5),Math.max(0,ab/5))},
        new float[]{0,.50f,1f},Shader.TileMode.CLAMP));
      bp.setShadowLayer(dp(11),0,0,(accent&0x00FFFFFF)|0xAA000000);
      c.drawCircle(px,py,pr,bp);bp.clearShadowLayer();bp.setShader(null);

      double t=System.nanoTime()/1_000_000_000.0;
      c.save();c.rotate((float)(t*34.0)%360f,px,py);
      bs.setStyle(Paint.Style.STROKE);bs.setStrokeWidth(dp(1.6f));bs.setColor(0xCDE8F7FF);
      c.drawOval(new RectF(px-pr*1.58f,py-pr*.34f,px+pr*1.58f,py+pr*.34f),bs);
      float moonA=(float)(t*1.7);
      bp.setStyle(Paint.Style.FILL);bp.setColor(0xFFEAF8FF);
      c.drawCircle(px+(float)Math.cos(moonA)*pr*1.48f,py+(float)Math.sin(moonA)*pr*.30f,dp(2.2f),bp);
      c.restore();

      bp.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
      bp.setTextAlign(Paint.Align.LEFT);bp.setTextSize(dp(15));
      bp.setColor(0xFFF4F8FC);
      c.drawText(label,body.left+dp(66),body.centerY()+dp(5),bp);

      bp.setTextAlign(Paint.Align.CENTER);bp.setTextSize(dp(20));bp.setColor(accent);
      c.drawText("›",body.right-dp(20),body.centerY()+dp(7),bp);

      c.restore();
      postInvalidateOnAnimation();
    }
  }

  Button homeButton(String label){
    PlanetButton b=new PlanetButton(this,label,planetAccentFor(label));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(66));
    lp.setMargins(0,dp(7),0,dp(7));
    b.setLayoutParams(lp);
    return b;
  }

  View galacticLogoView(){
    ImageView iv=new ImageView(this);
    iv.setAdjustViewBounds(true);
    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
    iv.setMaxHeight(dp(230));
    try(InputStream in=getAssets().open("ui/galactic_logo.png")){
      BitmapFactory.Options o=new BitmapFactory.Options();
      o.inPreferredConfig=Bitmap.Config.ARGB_8888;
      o.inScaled=false;
      Bitmap src=BitmapFactory.decodeStream(in,null,o);
      if(src!=null){
        // The original art was previously being blown up directly from its small
        // bundled preview and looked blocky. Pre-render a filtered 4x working copy
        // once, then let ImageView downsample from that instead of magnifying pixels.
        int targetW=Math.max(src.getWidth(),1280);
        int targetH=Math.max(1,Math.round(src.getHeight()*(targetW/(float)src.getWidth())));
        Bitmap hd=Bitmap.createBitmap(targetW,targetH,Bitmap.Config.ARGB_8888);
        Canvas cc=new Canvas(hd);
        Paint pp=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|Paint.DITHER_FLAG);
        cc.drawBitmap(src,null,new RectF(0,0,targetW,targetH),pp);
        iv.setImageBitmap(hd);
      }
    }catch(Exception ignored){}
    return iv;
  }


  View buildHomeScreen(){
    FrameLayout home=new FrameLayout(this);
    home.setBackgroundColor(Color.rgb(1,4,12));

    ImageView bg=new ImageView(this);
    bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
    try(InputStream in=getAssets().open("environment/sky.jpg")){
      bg.setImageBitmap(BitmapFactory.decodeStream(in));
    }catch(Exception ignored){
      bg.setBackgroundColor(Color.rgb(1,4,12));
    }
    home.addView(bg,new FrameLayout.LayoutParams(-1,-1));

    View shade=new View(this);
    shade.setBackgroundColor(Color.argb(178,0,3,13));
    home.addView(shade,new FrameLayout.LayoutParams(-1,-1));
    GalacticOrbitView orbit=new GalacticOrbitView(this);
    orbit.setAlpha(.82f);
    home.addView(orbit,new FrameLayout.LayoutParams(-1,-1));

    ScrollView scroll=new ScrollView(this);
    scroll.setFillViewport(true);
    FrameLayout.LayoutParams slp=new FrameLayout.LayoutParams(-1,-1);
    home.addView(scroll,slp);

    LinearLayout outer=new LinearLayout(this);
    outer.setOrientation(LinearLayout.VERTICAL);
    outer.setGravity(Gravity.CENTER);
    outer.setPadding(dp(22),dp(24),dp(22),dp(24));
    scroll.addView(outer,new ScrollView.LayoutParams(-1,-1));

    LinearLayout card=new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setGravity(Gravity.CENTER_HORIZONTAL);
    card.setPadding(dp(24),dp(24),dp(24),dp(24));
    card.setBackground(galacticPanelDrawable(Color.rgb(63,181,255)));
    LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(-1,-2);
    cardLp.width=Math.min(dp(620),Math.max(dp(300),getResources().getDisplayMetrics().widthPixels-dp(44)));
    card.setLayoutParams(cardLp);
    outer.addView(card);

    View logo=galacticLogoView();
    LinearLayout.LayoutParams logoLp=new LinearLayout.LayoutParams(-1,-2);
    logoLp.bottomMargin=dp(10);
    logo.setLayoutParams(logoLp);
    card.addView(logo);

    TextView title=new TextView(this);
    title.setText("ONLINE GALACTIC POOL");
    title.setTextColor(Color.rgb(194,226,255));
    title.setTextSize(15);
    title.setGravity(Gravity.CENTER);
    title.setLetterSpacing(.14f);
    LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,-2);
    titleLp.bottomMargin=dp(14);
    card.addView(title,titleLp);

    homeStatus=new TextView(this);
    homeStatus.setTextColor(Color.rgb(190,200,219));
    homeStatus.setTextSize(14);
    homeStatus.setGravity(Gravity.CENTER);
    homeStatus.setLineSpacing(0,1.15f);
    LinearLayout.LayoutParams statusLp=new LinearLayout.LayoutParams(-1,-2);
    statusLp.bottomMargin=dp(14);
    card.addView(homeStatus,statusLp);

    continueButton=homeButton("Continue saved login");
    continueButton.setOnClickListener(v->{
      multiplayer.autoConnect();
      refreshHomeScreen();
    });
    card.addView(continueButton);

    Button login=homeButton("Log in");
    login.setOnClickListener(v->openAuthFromHome(false));
    card.addView(login);

    Button create=homeButton("Create account");
    create.setOnClickListener(v->openAuthFromHome(true));
    card.addView(create);

    TextView foot=new TextView(this);
    foot.setText("Sign in first. Authentication takes you to the Galactic lobby.");
    foot.setTextColor(Color.rgb(120,137,164));
    foot.setTextSize(12);
    foot.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams footLp=new LinearLayout.LayoutParams(-1,-2);
    footLp.topMargin=dp(12);
    card.addView(foot,footLp);

    return home;
  }

  void openAuthFromHome(boolean register){
    if(multiplayer==null)return;
    showAuthDialog(register);
  }

  void refreshHomeScreen(){
    if(multiplayer==null||homeStatus==null)return;
    String user=multiplayer.username==null?"":multiplayer.username;
    boolean hasToken=!multiplayer.savedToken().isEmpty();

    if(hasToken){
      homeStatus.setText("GALACTIC NETWORK ONLINE\nSaved account: "+(user.isEmpty()?"Galactic player":user));
    }else{
      homeStatus.setText("GALACTIC NETWORK ONLINE\nLog in or create an account to enter the Galactic lobby.");
    }

    if(continueButton!=null){
      continueButton.setVisibility(hasToken?View.VISIBLE:View.GONE);
      if(hasToken&&!user.isEmpty())continueButton.setText("Continue as "+user);
    }
  }

  View buildLobbyScreen(){
    FrameLayout lobby=new FrameLayout(this);
    lobby.setBackgroundColor(Color.rgb(1,4,12));

    ImageView bg=new ImageView(this);
    bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
    try(InputStream in=getAssets().open("environment/sky.jpg")){
      bg.setImageBitmap(BitmapFactory.decodeStream(in));
    }catch(Exception ignored){
      bg.setBackgroundColor(Color.rgb(1,4,12));
    }
    lobby.addView(bg,new FrameLayout.LayoutParams(-1,-1));

    View shade=new View(this);
    shade.setBackgroundColor(Color.argb(184,0,3,13));
    lobby.addView(shade,new FrameLayout.LayoutParams(-1,-1));
    GalacticOrbitView orbit=new GalacticOrbitView(this);
    orbit.setAlpha(.78f);
    lobby.addView(orbit,new FrameLayout.LayoutParams(-1,-1));

    ScrollView scroll=new ScrollView(this);
    scroll.setFillViewport(true);
    lobby.addView(scroll,new FrameLayout.LayoutParams(-1,-1));

    LinearLayout outer=new LinearLayout(this);
    outer.setOrientation(LinearLayout.VERTICAL);
    outer.setGravity(Gravity.CENTER);
    outer.setPadding(dp(22),dp(22),dp(22),dp(22));
    scroll.addView(outer,new ScrollView.LayoutParams(-1,-1));

    LinearLayout card=new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setGravity(Gravity.CENTER_HORIZONTAL);
    card.setPadding(dp(24),dp(22),dp(24),dp(24));
    card.setBackground(galacticPanelDrawable(Color.rgb(115,106,255)));
    LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(-1,-2);
    cardLp.width=Math.min(dp(620),Math.max(dp(300),getResources().getDisplayMetrics().widthPixels-dp(44)));
    outer.addView(card,cardLp);

    View logo=galacticLogoView();
    LinearLayout.LayoutParams logoLp=new LinearLayout.LayoutParams(-1,dp(168));
    logoLp.bottomMargin=dp(2);
    card.addView(logo,logoLp);

    TextView title=new TextView(this);
    title.setText("GALACTIC LOBBY");
    title.setTextColor(Color.rgb(194,226,255));
    title.setTextSize(20);
    title.setGravity(Gravity.CENTER);
    title.setLetterSpacing(.12f);
    LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,-2);
    titleLp.bottomMargin=dp(8);
    card.addView(title,titleLp);

    lobbyStatus=new TextView(this);
    lobbyStatus.setTextColor(Color.rgb(190,200,219));
    lobbyStatus.setTextSize(14);
    lobbyStatus.setGravity(Gravity.CENTER);
    lobbyStatus.setLineSpacing(0,1.15f);
    LinearLayout.LayoutParams statusLp=new LinearLayout.LayoutParams(-1,-2);
    statusLp.bottomMargin=dp(12);
    card.addView(lobbyStatus,statusLp);

    Button browse=homeButton("Browse public rooms");
    browse.setOnClickListener(v->multiplayer.requestLobby());
    card.addView(browse);

    Button create=homeButton("Create a room");
    create.setOnClickListener(v->showCreateRoomDialog());
    card.addView(create);

    Button ai=homeButton("Single player vs AI");
    ai.setOnClickListener(v->startSinglePlayer());
    card.addView(ai);

    Button logout=homeButton("Log out");
    logout.setOnClickListener(v->multiplayer.logout());
    card.addView(logout);

    TextView foot=new TextView(this);
    foot.setText("Join or create an online room, or start a local match against the Galactic AI.");
    foot.setTextColor(Color.rgb(120,137,164));
    foot.setTextSize(12);
    foot.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams footLp=new LinearLayout.LayoutParams(-1,-2);
    footLp.topMargin=dp(12);
    card.addView(foot,footLp);

    return lobby;
  }

  void refreshLobbyScreen(){
    if(multiplayer==null||lobbyStatus==null)return;
    String user=multiplayer.username==null||multiplayer.username.isEmpty()?"Galactic player":multiplayer.username;
    lobbyStatus.setText("SIGNED IN AS "+user+"\nGALACTIC NETWORK • CONNECTED");
  }

  void showHomeScreen(){
    showingTable=false;
    if(gameRoot!=null)gameRoot.setVisibility(View.GONE);
    if(lobbyScreen!=null)lobbyScreen.setVisibility(View.GONE);
    if(homeScreen!=null)homeScreen.setVisibility(View.VISIBLE);
    refreshHomeScreen();
  }

  void showLobbyScreen(){
    showingTable=false;
    if(gameRoot!=null)gameRoot.setVisibility(View.GONE);
    if(homeScreen!=null)homeScreen.setVisibility(View.GONE);
    if(lobbyScreen!=null)lobbyScreen.setVisibility(View.VISIBLE);
    if(game!=null)game.queueEvent(()->{game.r.aiEnabled=false;game.r.aiThinking=false;});
    refreshLobbyScreen();
  }

  void startSinglePlayer(){
    if(multiplayer!=null&&multiplayer.inRoom){
      Toast.makeText(this,"Leave the online room before starting single player.",Toast.LENGTH_LONG).show();
      return;
    }
    game.queueEvent(()->{
      game.r.aiEnabled=true;
      game.r.aiThinking=false;
      game.r.resetRack();
      game.r.ruleMessage="SINGLE PLAYER • YOU BREAK";
    });
    showGameScreen();
    Toast.makeText(this,"Single player started. You are Team 1; Galactic AI is Team 2.",Toast.LENGTH_LONG).show();
  }

  void showGameScreen(){
    showingTable=true;
    if(homeScreen!=null)homeScreen.setVisibility(View.GONE);
    if(lobbyScreen!=null)lobbyScreen.setVisibility(View.GONE);
    if(gameRoot!=null)gameRoot.setVisibility(View.VISIBLE);
    if(hud!=null)hud.invalidate();
  }

  boolean tutorialSeen(String user){
    if(user==null||user.trim().isEmpty())return false;
    return getSharedPreferences("galactic_ui",MODE_PRIVATE)
      .getBoolean("tutorial_seen_"+user.trim().toLowerCase(java.util.Locale.US),false);
  }

  void markTutorialSeen(String user){
    if(user==null||user.trim().isEmpty())return;
    getSharedPreferences("galactic_ui",MODE_PRIVATE).edit()
      .putBoolean("tutorial_seen_"+user.trim().toLowerCase(java.util.Locale.US),true).apply();
  }

  void showFirstAccountTutorial(String user){
    if(tutorialSeen(user))return;
    final String[] titles={
      "1 / 5  AIM YOUR SHOT",
      "2 / 5  MICRO AIM",
      "3 / 5  LOCK + ENGLISH",
      "4 / 5  POWER + CAMERA",
      "5 / 5  MATCH HUD"
    };
    final String[] messages={
      "Drag the lightsaber hilt around the cue ball to rotate your shot. The glowing predictor shows the cue-ball path and the projected object-ball path.",
      "Use the glowing LEFT and RIGHT aim buttons for precision. Tap for an ultra-fine adjustment; press and hold to sweep around the cue ball quickly. A held button can continue through a full 360 degrees.",
      "When your line is ready, tap LOCK. Then choose where the cue tip strikes the cue ball for English and lock that selection.",
      "Use the right CAMERA stick beside LOCK to swing around the table and change your viewing angle. Pinch with two fingers to zoom; drag with two fingers to pan the table while zoomed. After English, pull either the table hilt or the right-thumb saber to set power and release to shoot.",
      "The top HUD shows each team's remaining balls. Glowing rings on the table identify team balls. Open the side menu for your saber loadout, new rack, team controls, or to return to the Galactic Lobby."
    };
    showTutorialPage(user,titles,messages,0);
  }

  void showTutorialPage(String user,String[] titles,String[] messages,int step){
    AlertDialog dialog=new AlertDialog.Builder(this)
      .setTitle(titles[step])
      .setMessage(messages[step])
      .setPositiveButton(step==titles.length-1?"START PLAYING":"NEXT",null)
      .setNegativeButton("SKIP TUTORIAL",null)
      .create();
    dialog.setCanceledOnTouchOutside(false);
    dialog.setCancelable(false);
    dialog.setOnShowListener(d->{
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
        dialog.dismiss();
        if(step==titles.length-1){
          markTutorialSeen(user);
        }else{
          showTutorialPage(user,titles,messages,step+1);
        }
      });
      dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{
        markTutorialSeen(user);
        dialog.dismiss();
      });
    });
    dialog.show();
  }

  protected void onDestroy(){
    if(multiplayer!=null)multiplayer.disconnect();
    if(game!=null&&game.r!=null&&game.r.sfx!=null)game.r.sfx.shutdown();
    super.onDestroy();
  }

  void showMultiplayerDialog(){
    if(multiplayer==null)return;

    if(game!=null&&game.r!=null&&game.r.aiEnabled){
      game.queueEvent(()->{game.r.aiEnabled=false;game.r.aiThinking=false;game.r.resetRack();});
      showLobbyScreen();
      return;
    }

    if(multiplayer.inRoom){
      new AlertDialog.Builder(this)
        .setTitle(multiplayer.roomName)
        .setMessage("Signed in as "+multiplayer.username+"\n"+multiplayer.statusText())
        .setItems(new String[]{"LEAVE MATCH","CANCEL"},(d,which)->{
          if(which==0)multiplayer.leaveRoom();
        }).show();
      return;
    }

    showLobbyScreen();
  }

  void showAuthDialog(boolean register){
    LinearLayout box=new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    int pad=(int)(22*getResources().getDisplayMetrics().density);
    box.setPadding(pad,pad/2,pad,pad/2);

    EditText user=new EditText(this);
    user.setSingleLine(true);user.setHint("Username");
    user.setInputType(InputType.TYPE_CLASS_TEXT);
    box.addView(user,new LinearLayout.LayoutParams(-1,-2));

    EditText pass=new EditText(this);
    pass.setSingleLine(true);pass.setHint("Password");
    pass.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
    box.addView(pass,new LinearLayout.LayoutParams(-1,-2));

    new AlertDialog.Builder(this)
      .setTitle(register?"CREATE GALACTIC ACCOUNT":"GALACTIC LOGIN")
      .setMessage(register?"Username: 3-18 letters, numbers, or underscores. Password: at least 6 characters.":"Log in to see and join your friends' rooms.")
      .setView(box)
      .setPositiveButton(register?"CREATE ACCOUNT":"LOG IN",(d,w)->{
        String u=user.getText().toString().trim();
        String p=pass.getText().toString();
        if(register)multiplayer.register(u,p);else multiplayer.login(u,p);
      })
      .setNegativeButton("CANCEL",null)
      .show();
  }

  void showCreateRoomDialog(){
    final EditText input=new EditText(this);
    input.setSingleLine(true);
    input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    input.setHint("Friday Night Galactic Pool");
    input.setPadding(36,18,36,18);
    new AlertDialog.Builder(this)
      .setTitle("CREATE ONLINE ROOM")
      .setMessage("Give the room a name. Your friends will see it in the lobby and can join with one tap.")
      .setView(input)
      .setPositiveButton("CREATE",(d,w)->{
        String name=input.getText().toString().trim();
        if(!name.isEmpty())multiplayer.createNamedRoom(name);
      })
      .setNegativeButton("CANCEL",null)
      .show();
  }

  void showLobbyDialog(String[] labels,String[] ids,boolean[] full){
    if(labels==null||labels.length==0){
      new AlertDialog.Builder(this)
        .setTitle("ONLINE ROOMS")
        .setMessage("No public rooms are open right now.")
        .setPositiveButton("CREATE ROOM",(d,w)->showCreateRoomDialog())
        .setNeutralButton("REFRESH",(d,w)->multiplayer.requestLobby())
        .setNegativeButton("CLOSE",null)
        .show();
      return;
    }

    new AlertDialog.Builder(this)
      .setTitle("ONLINE ROOMS")
      .setItems(labels,(d,which)->{
        if(which<0||which>=ids.length)return;
        if(full[which])Toast.makeText(this,"That room is full.",Toast.LENGTH_SHORT).show();
        else multiplayer.joinRoom(ids[which]);
      })
      .setPositiveButton("CREATE ROOM",(d,w)->showCreateRoomDialog())
      .setNeutralButton("REFRESH",(d,w)->multiplayer.requestLobby())
      .setNegativeButton("CLOSE",null)
      .show();
  }

  void showServerDialog(){
    Toast.makeText(this,"Galactic Online is preconfigured.",Toast.LENGTH_SHORT).show();
  }


  static class GalacticLogoView extends View{
    final Paint p=new Paint(3),stroke=new Paint(3);
    final Path ship=new Path();
    GalacticLogoView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}

    protected void onMeasure(int ws,int hs){
      int w=MeasureSpec.getSize(ws);
      int desired=(int)(w*.46f);
      int h=resolveSize(Math.max(180,desired),hs);
      setMeasuredDimension(w,h);
    }

    protected void onDraw(Canvas c){
      super.onDraw(c);
      float w=getWidth(),h=getHeight(),cx=w*.5f,cy=h*.43f;
      float r=Math.min(w*.25f,h*.40f);

      // Nebula halo.
      p.setStyle(Paint.Style.FILL);
      p.setShader(new RadialGradient(cx,cy,r*1.45f,
        new int[]{0x00113866,0x66442299,0xAAE56C22,0x33193866,0x00000000},
        new float[]{0,.43f,.66f,.82f,1f},Shader.TileMode.CLAMP));
      c.drawCircle(cx,cy,r*1.45f,p);p.setShader(null);

      // Galaxy ring / metallic orbit.
      stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(Math.max(2f,r*.035f));
      stroke.setShader(new SweepGradient(cx,cy,new int[]{0xFF55D8FF,0xFFFFB84A,0xFF9C5CFF,0xFF55D8FF},null));
      stroke.setShadowLayer(r*.10f,0,0,0xAA58D6FF);
      c.save();c.scale(1f,.44f,cx,cy);c.drawCircle(cx,cy,r*1.18f,stroke);c.restore();
      stroke.clearShadowLayer();stroke.setShader(null);

      // Glossy 8-ball.
      p.setShader(new RadialGradient(cx-r*.23f,cy-r*.28f,r*1.02f,
        new int[]{0xFF596170,0xFF151920,0xFF030406},new float[]{0,.35f,1f},Shader.TileMode.CLAMP));
      p.setShadowLayer(r*.16f,0,r*.08f,0xDD000000);
      c.drawCircle(cx,cy,r*.72f,p);p.clearShadowLayer();p.setShader(null);

      p.setColor(0xFFF7F8FA);c.drawCircle(cx,cy-r*.02f,r*.255f,p);
      p.setColor(0xFF050608);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
      p.setTextAlign(Paint.Align.CENTER);p.setTextSize(r*.34f);
      c.drawText("8",cx,cy+r*.095f,p);

      // Small starfighter silhouette.
      float sx=cx+r*.78f,sy=cy-r*.70f,sc=r*.22f;
      ship.reset();
      ship.moveTo(sx+sc*.95f,sy);
      ship.lineTo(sx-sc*.45f,sy-sc*.18f);
      ship.lineTo(sx-sc*.88f,sy-sc*.62f);
      ship.lineTo(sx-sc*.18f,sy-sc*.30f);
      ship.lineTo(sx-sc*.82f,sy+sc*.55f);
      ship.lineTo(sx-sc*.35f,sy+sc*.20f);
      ship.close();
      p.setColor(0xFFE6EDF7);p.setShadowLayer(sc*.22f,0,0,0xAA55CFFF);c.drawPath(ship,p);p.clearShadowLayer();

      // Crossed saber accents.
      float saberY=cy+r*.94f,saberL=r*.76f;
      stroke.setStrokeCap(Paint.Cap.ROUND);stroke.setStrokeWidth(r*.075f);
      stroke.setShadowLayer(r*.09f,0,0,0xE8FF3040);stroke.setColor(0xFFFF5460);
      c.drawLine(cx-saberL*.72f,saberY+r*.20f,cx+saberL*.72f,saberY-r*.20f,stroke);
      stroke.clearShadowLayer();
      stroke.setShadowLayer(r*.09f,0,0,0xE849BFFF);stroke.setColor(0xFF74DFFF);
      c.drawLine(cx-saberL*.72f,saberY-r*.20f,cx+saberL*.72f,saberY+r*.20f,stroke);
      stroke.clearShadowLayer();stroke.setStrokeCap(Paint.Cap.BUTT);

      // Crisp scalable title: no bitmap upscaling at any screen density.
      p.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
      p.setTextAlign(Paint.Align.CENTER);
      p.setTextSize(Math.min(w*.082f,r*.42f));
      p.setLetterSpacing(.08f);
      p.setShader(new LinearGradient(cx,cy+r*.44f,cx,cy+r*.83f,
        new int[]{0xFFFFF1A7,0xFFFFC54D,0xFF9C5A10},null,Shader.TileMode.CLAMP));
      p.setShadowLayer(r*.055f,0,r*.025f,0xFF000000);
      c.drawText("GALACTIC 8-BALL",cx,cy+r*.72f,p);
      p.clearShadowLayer();p.setShader(null);p.setLetterSpacing(0f);
    }
  }

  static class GalacticOrbitView extends View{
    final Paint paint=new Paint(3),stroke=new Paint(3);
    final float[] starX=new float[72],starY=new float[72],starA=new float[72];
    GalacticOrbitView(Context c){
      super(c);setWillNotDraw(false);
      java.util.Random r=new java.util.Random(818L);
      for(int i=0;i<starX.length;i++){starX[i]=r.nextFloat();starY[i]=r.nextFloat();starA[i]=.22f+r.nextFloat()*.72f;}
    }
    protected void onDraw(Canvas c){
      super.onDraw(c);
      float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
      double t=System.nanoTime()/1_000_000_000.0;

      paint.setStyle(Paint.Style.FILL);
      for(int i=0;i<starX.length;i++){
        float tw=(float)(.45+.55*Math.sin(t*(.7+(i%5)*.12)+i));
        paint.setColor(Color.argb((int)(150*starA[i]*tw),180,222,255));
        c.drawCircle(starX[i]*w,starY[i]*h,1.0f+(i%3)*.55f,paint);
      }

      float cx=w*.5f,cy=h*.48f;
      stroke.setStyle(Paint.Style.STROKE);
      stroke.setStrokeWidth(1.2f);
      stroke.setColor(Color.argb(52,92,198,255));
      RectF o1=new RectF(cx-w*.34f,cy-h*.24f,cx+w*.34f,cy+h*.24f);
      c.drawOval(o1,stroke);
      stroke.setColor(Color.argb(38,163,104,255));
      RectF o2=new RectF(cx-w*.43f,cy-h*.32f,cx+w*.43f,cy+h*.32f);
      c.drawOval(o2,stroke);

      drawPlanet(c,cx+(float)Math.cos(t*.40)*w*.34f,cy+(float)Math.sin(t*.40)*h*.24f,18f,0xFF4CC9FF);
      drawPlanet(c,cx+(float)Math.cos(t*.27+2.2)*w*.43f,cy+(float)Math.sin(t*.27+2.2)*h*.32f,12f,0xFFA26BFF);
      drawPlanet(c,cx+(float)Math.cos(-t*.53+1.0)*w*.22f,cy+(float)Math.sin(-t*.53+1.0)*h*.15f,8f,0xFFFF675F);
      postInvalidateOnAnimation();
    }
    void drawPlanet(Canvas c,float x,float y,float r,int color){
      int rr=Color.red(color),gg=Color.green(color),bb=Color.blue(color);
      RadialGradient g=new RadialGradient(x-r*.35f,y-r*.38f,r*1.15f,
        new int[]{Color.rgb(Math.min(255,rr+75),Math.min(255,gg+75),Math.min(255,bb+75)),color,Color.rgb(rr/4,gg/4,bb/4)},
        new float[]{0,.48f,1f},Shader.TileMode.CLAMP);
      paint.setShader(g);paint.setShadowLayer(r*.7f,0,0,Color.argb(170,rr,gg,bb));
      c.drawCircle(x,y,r,paint);paint.clearShadowLayer();paint.setShader(null);
      stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(Math.max(1.2f,r*.10f));
      stroke.setColor(Color.argb(130,220,240,255));
      c.drawOval(new RectF(x-r*1.45f,y-r*.34f,x+r*1.45f,y+r*.34f),stroke);
    }
  }

  static class MultiplayerManager {
    final MainActivity activity; final GameView game; final HudView hud;
    final Handler main=new Handler(Looper.getMainLooper());
    final OkHttpClient http=new OkHttpClient.Builder()
      .pingInterval(20,TimeUnit.SECONDS)
      .connectTimeout(8,TimeUnit.SECONDS)
      .build();

    volatile boolean socketConnected=false,connecting=false,authenticated=false,inRoom=false;
    volatile boolean hosting=false,lobbyRequested=false,pendingRegistration=false;
    volatile int localPlayer=0;
    volatile String username="",roomId="",roomName="",status="OFFLINE";
    WebSocket socket;

    MultiplayerManager(MainActivity a,GameView g,HudView h){
      activity=a;game=g;hud=h;
      username=activity.getSharedPreferences("galactic_online",Context.MODE_PRIVATE).getString("username","");
    }

    boolean isConnected(){return socketConnected&&authenticated;}
    boolean isFollower(){return inRoom;}
    boolean canLocalControl(int activeShooter){return !inRoom||localPlayer==activeShooter;}

    String savedServerUrl(){
      return DEFAULT_SERVER_URL;
    }

    String savedToken(){
      return activity.getSharedPreferences("galactic_online",Context.MODE_PRIVATE).getString("auth_token","");
    }

    void saveLogin(String user,String token){
      username=user==null?"":user;
      activity.getSharedPreferences("galactic_online",Context.MODE_PRIVATE).edit()
        .putString("username",username).putString("auth_token",token==null?"":token).apply();
    }

    void clearLogin(){
      username="";
      activity.getSharedPreferences("galactic_online",Context.MODE_PRIVATE).edit()
        .remove("username").remove("auth_token").apply();
    }

    void setServerUrl(String url){
      // Server is intentionally baked into the app so players never configure it.
    }

    String serverDisplay(){
      return "galactic-8ball-server.onrender.com";
    }

    String websocketUrl(){
      return "wss://galactic-8ball-server.onrender.com/ws";
    }

    String statusText(){
      if(inRoom)return "ROOM • "+roomName+" • PLAYER "+localPlayer;
      if(authenticated)return "ONLINE • "+username;
      if(connecting)return "CONNECTING";
      return status;
    }

    String b64(String v){
      if(v==null)v="";
      return android.util.Base64.encodeToString(v.getBytes(StandardCharsets.UTF_8),
        android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING);
    }

    String unb64(String v){
      try{return new String(android.util.Base64.decode(v,android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP),StandardCharsets.UTF_8);}
      catch(Exception e){return "";}
    }

    void toast(String t){main.post(()->Toast.makeText(activity,t,Toast.LENGTH_LONG).show());}

    void autoConnect(){
      String token=savedToken();
      if(savedServerUrl().isEmpty()||token.isEmpty())return;
      pendingRegistration=false;
      connectThen("AUTH|"+token);
    }

    void register(String user,String pass){
      if(user==null||user.trim().isEmpty()||pass==null||pass.length()<6){
        toast("Enter a username and a password with at least 6 characters.");return;
      }
      pendingRegistration=true;
      connectThen("REGISTER|"+b64(user.trim())+"|"+b64(pass));
    }

    void login(String user,String pass){
      if(user==null||user.trim().isEmpty()||pass==null||pass.isEmpty()){
        toast("Enter your username and password.");return;
      }
      pendingRegistration=false;
      connectThen("LOGIN|"+b64(user.trim())+"|"+b64(pass));
    }

    void logout(){
      if(socketConnected&&socket!=null)socket.send("LOGOUT");
      clearLogin();
      disconnectSocket();
      authenticated=false;status="OFFLINE";
      if(hud!=null)main.post(hud::invalidate);
      main.post(activity::showHomeScreen);
    }

    void requestLobby(){
      if(!authenticated){toast("Log in first.");return;}
      lobbyRequested=true;
      send("LOBBY");
    }

    void createNamedRoom(String name){
      if(!authenticated){toast("Log in first.");return;}
      if(name==null||name.trim().isEmpty())return;
      send("CREATE_ROOM|"+b64(name.trim()));
    }

    void joinRoom(String id){
      if(!authenticated||id==null||id.isEmpty())return;
      send("JOIN_ROOM|"+id);
    }

    void leaveRoom(){
      if(!inRoom)return;
      send("LEAVE_ROOM");
    }

    synchronized void connectThen(String firstMessage){
      String url=websocketUrl();
      if(socketConnected&&socket!=null){
        socket.send(firstMessage);
        return;
      }

      disconnectSocket();
      connecting=true;status="CONNECTING";
      if(hud!=null)main.post(hud::invalidate);

      Request request=new Request.Builder().url(url).build();
      socket=http.newWebSocket(request,new WebSocketListener(){
        public void onOpen(WebSocket ws,Response response){
          socketConnected=true;connecting=false;status="ONLINE";
          ws.send(firstMessage);
          if(hud!=null)main.post(hud::invalidate);
        }

        public void onMessage(WebSocket ws,String msg){
          if(msg.startsWith("AUTH_OK|")){
            String[] p=msg.split("\\|",-1);
            if(p.length>=3){
              String user=unb64(p[1]);
              String token=p[2];
              saveLogin(user,token);
              authenticated=true;status="ONLINE";
              final boolean firstCreated=pendingRegistration;
              pendingRegistration=false;
              if(hud!=null)main.post(hud::invalidate);
              main.post(()->{
                activity.showLobbyScreen();
                if(firstCreated)activity.showFirstAccountTutorial(user);
              });
            }
          }else if(msg.startsWith("AUTH_FAIL|")){
            pendingRegistration=false;
            authenticated=false;
            String[] p=msg.split("\\|",-1);
            String why=p.length>1?unb64(p[1]):"Login failed";
            if(!savedToken().isEmpty())clearLogin();
            toast(why);
            if(hud!=null)main.post(hud::invalidate);
          }else if(msg.startsWith("LOBBY|")){
            parseLobby(msg);
          }else if(msg.startsWith("ROOM_JOINED|")){
            String[] p=msg.split("\\|",-1);
            if(p.length>=4){
              roomId=p[1];roomName=unb64(p[2]);
              try{localPlayer=Integer.parseInt(p[3]);}catch(Exception ignored){}
              inRoom=true;status="IN ROOM";
              game.queueEvent(()->{game.r.aiEnabled=false;game.r.aiThinking=false;});
              toast("Joined "+roomName+" as Player "+localPlayer);
              if(hud!=null)main.post(hud::invalidate);
              main.post(activity::showGameScreen);
            }
          }else if(msg.startsWith("ROOM_LEFT")){
            inRoom=false;localPlayer=0;roomId="";roomName="";status="ONLINE";
            game.queueEvent(()->game.r.resetRack());
            if(hud!=null)main.post(hud::invalidate);
            main.post(activity::showLobbyScreen);
          }else if(msg.startsWith("ROOM_CLOSED")){
            inRoom=false;localPlayer=0;roomId="";roomName="";status="ONLINE";
            toast("The room was closed.");
            game.queueEvent(()->game.r.resetRack());
            if(hud!=null)main.post(hud::invalidate);
            main.post(activity::showLobbyScreen);
          }else if(msg.startsWith("STATE|")){
            if(inRoom)game.queueEvent(()->game.r.applyNetworkState(msg));
          }else if(msg.startsWith("PLAYER_JOINED|")){
            String[] p=msg.split("\\|",-1);
            toast((p.length>1?unb64(p[1]):"Player 2")+" joined "+roomName);
          }else if(msg.startsWith("PLAYER_LEFT|")){
            toast("The other player left the room.");
          }else if(msg.startsWith("LOGGED_OUT")){
            authenticated=false;inRoom=false;localPlayer=0;roomId="";roomName="";
            if(hud!=null)main.post(hud::invalidate);
            main.post(activity::showHomeScreen);
          }else if(msg.startsWith("ERROR|")){
            String[] p=msg.split("\\|",-1);
            toast("Server: "+(p.length>1?unb64(p[1]):"Request failed"));
          }
        }

        public void onClosing(WebSocket ws,int code,String reason){ws.close(code,reason);}

        public void onClosed(WebSocket ws,int code,String reason){
          if(socket==ws){
            socketConnected=false;connecting=false;authenticated=false;inRoom=false;localPlayer=0;roomId="";roomName="";
            status="OFFLINE";
            if(hud!=null)main.post(hud::invalidate);
          }
        }

        public void onFailure(WebSocket ws,Throwable t,Response response){
          if(socket==ws){
            socketConnected=false;connecting=false;authenticated=false;inRoom=false;localPlayer=0;roomId="";roomName="";
            status="CONNECTION FAILED";
            toast("Could not reach multiplayer server: "+(t.getMessage()==null?"connection failed":t.getMessage()));
            if(hud!=null)main.post(hud::invalidate);
          }
        }
      });
    }

    void parseLobby(String msg){
      if(!lobbyRequested){
        main.post(activity::refreshLobbyScreen);
        return;
      }
      lobbyRequested=false;
      String payload=msg.length()>6?msg.substring(6):"";
      if(payload.isEmpty()){
        main.post(()->activity.showLobbyDialog(new String[0],new String[0],new boolean[0]));
        return;
      }
      ArrayList<String> labels=new ArrayList<>();
      ArrayList<String> ids=new ArrayList<>();
      ArrayList<Boolean> full=new ArrayList<>();
      String[] rows=payload.split(";");
      for(String row:rows){
        if(row.isEmpty())continue;
        String[] p=row.split(",",-1);
        if(p.length<5)continue;
        String id=p[0],name=unb64(p[1]),owner=unb64(p[2]);
        int count=0,max=2;
        try{count=Integer.parseInt(p[3]);max=Integer.parseInt(p[4]);}catch(Exception ignored){}
        labels.add(name+"   ["+count+"/"+max+"]\nHost: "+owner+(count>=max?"   • FULL":""));
        ids.add(id);full.add(count>=max);
      }
      String[] la=labels.toArray(new String[0]),ia=ids.toArray(new String[0]);
      boolean[] fa=new boolean[full.size()];for(int i=0;i<fa.length;i++)fa[i]=full.get(i);
      main.post(()->activity.showLobbyDialog(la,ia,fa));
    }

    synchronized void send(String line){
      if(!socketConnected||socket==null)return;
      socket.send(line);
    }

    synchronized void disconnectSocket(){
      WebSocket old=socket;socket=null;
      if(old!=null){try{old.close(1000,"bye");}catch(Exception ignored){}}
      socketConnected=false;connecting=false;authenticated=false;inRoom=false;localPlayer=0;roomId="";roomName="";hosting=false;status="OFFLINE";
      if(hud!=null)main.post(hud::invalidate);
    }

    void disconnect(){
      if(inRoom&&socketConnected&&socket!=null)socket.send("LEAVE_ROOM");
      disconnectSocket();
    }

    void onFrame(GameRenderer r){
      // The cloud server owns all authoritative physics and rules.
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
    RectF lockRect=new RectF(),saberMenuRect=new RectF(),rackRect=new RectF(),activeShooterRect=new RectF(),teamSwitchRect=new RectF(),multiplayerRect=new RectF(),saberPanelRect=new RectF(),confirmRect=new RectF(),cancelRect=new RectF(),microLeftRect=new RectF(),microRightRect=new RectF(),aimStickRect=new RectF(),cameraStickRect=new RectF(),sideMenuTabRect=new RectF(),sideMenuPanelRect=new RectF(),thumbHiltRect=new RectF(),thumbGrabRect=new RectF();
    RectF[] hiltChoices=new RectF[6],bladeChoices=new RectF[6];
    float englishCx,englishCy,englishR;
    boolean touchingEnglish=false,menuOpen=false,sideMenuOpen=false,camGesture=false,pullingHilt=false,pullingThumbHilt=false,aimingHilt=false,microHolding=false,aimStickActive=false,cameraStickActive=false;
    float camPrevDist=0,camPrevMidX=0,camPrevMidY=0,hiltPullStartX=0,hiltPullStartY=0,thumbPullStartY=0,lastAimTapX=0,lastAimTapY=0,aimStartFingerAngle=0,aimStartWorldAngle=0;
    float aimStickX=0,aimStickY=0,cameraStickX=0,cameraStickY=0;
    long lastAimTapMs=0;
    int microHoldDir=0,microHoldW=0,microHoldH=0;
    final Handler uiHandler=new Handler(Looper.getMainLooper());
    final Runnable microRepeat=new Runnable(){
      public void run(){
        if(!microHolding)return;
        game.queueEvent(()->game.r.microAimHoldStep(1.92f));
        uiHandler.postDelayed(this,32);
      }
    };
    final Runnable aimStickRepeat=new Runnable(){
      public void run(){
        if(!aimStickActive)return;
        final float axis=aimStickX;
        game.queueEvent(()->game.r.analogAim(axis,.020f));
        uiHandler.postDelayed(this,20);
      }
    };
    final Runnable cameraStickRepeat=new Runnable(){
      public void run(){
        if(!cameraStickActive)return;
        final float ax=cameraStickX,ay=cameraStickY;
        game.queueEvent(()->game.r.cameraOrbitAnalog(ax,ay,.020f));
        uiHandler.postDelayed(this,20);
      }
    };

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
      boolean portrait=h>w;
      float ui=portrait
        ? Math.max(.78f,Math.min(1.28f,Math.min(w/430f,h/900f)))
        : Math.max(.82f,Math.min(1.24f,Math.min(w/900f,h/500f)));

      if(portrait)lockRect.set(w-108*ui,h-126*ui,w-16*ui,h-34*ui);
      else lockRect.set(w-138*ui,h-162*ui,w-24*ui,h-48*ui);

      // Portrait and landscape have independent HUD geometry instead of stretching
      // one layout until controls become oversized or tiny after rotation.
      float tabW=(portrait?52:62)*ui,tabH=(portrait?66:78)*ui;
      float tabCY=portrait?h*.34f:h*.43f;
      sideMenuTabRect.set(10*ui,tabCY-tabH*.5f,10*ui+tabW,tabCY+tabH*.5f);
      if(sideMenuOpen){
        float panelW=(portrait?Math.min(w/ui-20,226):248)*ui;
        float itemH=(portrait?52:58)*ui,itemGap=(portrait?7:8)*ui;
        float panelTop=Math.max((portrait?86:72)*ui,h*.5f-(itemH*5+itemGap*4+34*ui)*.5f);
        sideMenuPanelRect.set(10*ui,panelTop,10*ui+panelW,panelTop+itemH*5+itemGap*4+34*ui);
        float bx=22*ui,by=panelTop+24*ui,bw=panelW-24*ui;
        saberMenuRect.set(bx,by,bx+bw,by+itemH);by+=itemH+itemGap;
        rackRect.set(bx,by,bx+bw,by+itemH);by+=itemH+itemGap;
        activeShooterRect.set(bx,by,bx+bw,by+itemH);by+=itemH+itemGap;
        teamSwitchRect.set(bx,by,bx+bw,by+itemH);by+=itemH+itemGap;
        multiplayerRect.set(bx,by,bx+bw,by+itemH);
      }else{
        sideMenuPanelRect.setEmpty();saberMenuRect.setEmpty();rackRect.setEmpty();
        activeShooterRect.setEmpty();teamSwitchRect.setEmpty();multiplayerRect.setEmpty();
      }

      p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);
      drawSideMenuTab(c,sideMenuTabRect,ui,sideMenuOpen);
      if(sideMenuOpen)drawSideMenu(c,ui,r);

      drawMatchHud(c,w,h,ui,r);

      if(r.state==GameRenderer.AIMING && !r.gameOver){
        drawWorldShotHilt(c,w,h,ui,r);
        drawCrosshairButton(c,lockRect,ui);
        if(r.localCanControl()){
          drawMicroAimControls(c,w,h,ui);
          drawCameraAnalogStick(c,w,h,ui);
        }
        if(!r.localCanControl()){
          p.setTextSize(19*ui);p.setColor(0xEEFFFFFF);
          c.drawText(r.aiEnabled?"GALACTIC AI THINKING":"WAITING FOR PLAYER "+r.activeShooter,w*.5f,42*ui,p);
        }
      } else if(r.gameOver){
        drawWinnerOverlay(c,w,h,ui,r);
      } else if(r.state==GameRenderer.SELECTING_ENGLISH){
        drawEnglish(c,w,h,ui,r);
      } else if(r.state==GameRenderer.CHARGING){
        drawWorldShotHilt(c,w,h,ui,r);
        if(r.localCanControl())drawThumbStrikeHilt(c,w,h,ui,r);
      } else {
        p.setTextSize(20*ui);p.setColor(0xEEFFFFFF);
        c.drawText("BALLS ROLLING",w*.5f,42*ui,p);
      }
      if(menuOpen)drawSaberMenu(c,w,h,ui,r);
      postInvalidateOnAnimation();
    }

    void drawButton(Canvas c,RectF rr,String text,float fs,int bg){
      p.setStyle(Paint.Style.FILL);p.setColor(bg);c.drawRoundRect(rr,14,14,p);
      stroke.setColor(0xAAFFFFFF);stroke.setStrokeWidth(2);c.drawRoundRect(rr,14,14,stroke);
      p.setColor(Color.WHITE);p.setTextSize(fs);p.setTextAlign(Paint.Align.CENTER);
      c.drawText(text,rr.centerX(),rr.centerY()+fs*.34f,p);
    }
    void drawSideMenuTab(Canvas c,RectF rr,float ui,boolean open){
      Path hex=new Path();float cx=rr.centerX(),cy=rr.centerY(),w=rr.width(),h=rr.height();
      hex.moveTo(cx-w*.34f,rr.top);hex.lineTo(cx+w*.34f,rr.top);hex.lineTo(rr.right,cy);
      hex.lineTo(cx+w*.34f,rr.bottom);hex.lineTo(cx-w*.34f,rr.bottom);hex.lineTo(rr.left,cy);hex.close();
      p.setStyle(Paint.Style.FILL);p.setShadowLayer(12*ui,0,0,0xAA38BDF8);p.setColor(0xB30A1220);c.drawPath(hex,p);p.clearShadowLayer();
      stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(2.2f*ui);stroke.setColor(0xDD5BD6FF);c.drawPath(hex,stroke);
      p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(22*ui);p.setColor(0xFFF1FBFF);
      c.drawText(open?"‹":"☰",cx,cy+7*ui,p);
    }

    void drawSideMenu(Canvas c,float ui,GameRenderer r){
      p.setStyle(Paint.Style.FILL);p.setColor(0xD6070D16);p.setShadowLayer(16*ui,4*ui,0,0xBB000000);
      c.drawRoundRect(sideMenuPanelRect,18*ui,18*ui,p);p.clearShadowLayer();
      stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(1.8f*ui);stroke.setColor(0x995BD6FF);c.drawRoundRect(sideMenuPanelRect,18*ui,18*ui,stroke);

      String mp=(net==null)?"OFFLINE":net.statusText();if(mp.length()>22)mp=mp.substring(0,22);
      drawPremiumButton(c,saberMenuRect,"SABER MENU","LOADOUT",ui,0xFF46C7FF,menuOpen);
      drawPremiumButton(c,rackRect,"NEW RACK","RESET TABLE",ui,0xFFFF6B55,false);
      drawPremiumButton(c,activeShooterRect,"ACTIVE SHOOTER","PLAYER "+r.activeShooter,ui,0xFFF4C542,r.localCanControl());
      drawPremiumButton(c,teamSwitchRect,"TEAM SWITCH","TEAM "+r.currentTeam,ui,r.currentTeam==1?0xFF66B7FF:0xFFFF7979,false);
      String title=r.aiEnabled?"BACK TO LOBBY":(net!=null&&net.inRoom?"LEAVE MATCH":"GALACTIC LOBBY");
      String sub=r.aiEnabled?"EXIT SINGLE PLAYER":mp;
      drawPremiumButton(c,multiplayerRect,title,sub,ui,0xFF65E4A5,r.aiEnabled||(net!=null&&net.isConnected()));
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

      // 3D planet badge gives every submenu action a distinct Galactic control.
      float orbR=16*ui,orbX=rr.left+25*ui,orbY=rr.centerY();
      int ar=Color.red(accent),ag=Color.green(accent),ab=Color.blue(accent);
      p.setShader(new RadialGradient(orbX-orbR*.35f,orbY-orbR*.38f,orbR*1.15f,
        new int[]{Color.rgb(Math.min(255,ar+90),Math.min(255,ag+90),Math.min(255,ab+90)),accent,Color.rgb(ar/5,ag/5,ab/5)},
        new float[]{0,.5f,1f},Shader.TileMode.CLAMP));
      p.setShadowLayer(8*ui,0,0,(accent&0x00FFFFFF)|0x99000000);
      c.drawCircle(orbX,orbY,orbR,p);p.clearShadowLayer();p.setShader(null);
      c.save();c.rotate((float)((System.nanoTime()/20_000_000L)%360),orbX,orbY);
      stroke.setStrokeWidth(1.2f*ui);stroke.setColor(0xCCECF8FF);
      c.drawOval(new RectF(orbX-orbR*1.48f,orbY-orbR*.28f,orbX+orbR*1.48f,orbY+orbR*.28f),stroke);
      c.restore();

      p.setColor((accent&0x00FFFFFF)|0x26000000);
      for(int k=0;k<3;k++){
        float yy=rr.top+(18+k*13)*ui;
        c.drawRect(rr.left+50*ui,yy,rr.right-10*ui,yy+1*ui,p);
      }

      p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.LEFT);
      p.setTextSize(15.5f*ui);p.setColor(0xFFF7FAFC);
      c.drawText(title,rr.left+52*ui,rr.top+25*ui,p);
      p.setTypeface(Typeface.DEFAULT);p.setTextSize(10.2f*ui);p.setColor(active?accent:0xFFB8C1CE);
      c.drawText(sub,rr.left+52*ui,rr.bottom-12*ui,p);

      p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(16*ui);p.setColor(accent);
      c.drawText("›",rr.right-18*ui,rr.centerY()+5*ui,p);
      p.setShader(null);
    }


    void drawAimAnalogStick(Canvas c,int w,int h,float ui){
      float r=68*ui,cx=88*ui,cy=h-88*ui;
      aimStickRect.set(cx-r,cy-r,cx+r,cy+r);
      drawAnalogStick(c,cx,cy,r,aimStickX,aimStickY,"AIM",ui,0xFF5BD6FF);
    }

    void drawCameraAnalogStick(Canvas c,int w,int h,float ui){
      boolean portrait=h>w;
      float r=(portrait?54:60)*ui;
      float cx=portrait?lockRect.centerX():lockRect.left-r-18*ui;
      float cy=portrait?lockRect.top-r-15*ui:lockRect.centerY();
      cameraStickRect.set(cx-r,cy-r,cx+r,cy+r);
      drawAnalogStick(c,cx,cy,r,cameraStickX,cameraStickY,"CAMERA",ui,0xFFB88CFF);
    }

    void drawAnalogStick(Canvas c,float cx,float cy,float r,float nx,float ny,String label,float ui,int accent){
      p.setStyle(Paint.Style.FILL);
      p.setShadowLayer(14*ui,0,0,(accent&0x00FFFFFF)|0x99000000);
      p.setColor(0x6A07111C);c.drawCircle(cx,cy,r,p);p.clearShadowLayer();
      stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(2.4f*ui);stroke.setColor((accent&0x00FFFFFF)|0xDD000000);c.drawCircle(cx,cy,r,stroke);
      stroke.setStrokeWidth(1.0f*ui);stroke.setColor(0x55FFFFFF);c.drawCircle(cx,cy,r*.68f,stroke);
      c.drawLine(cx-r*.72f,cy,cx+r*.72f,cy,stroke);
      c.drawLine(cx,cy-r*.72f,cx,cy+r*.72f,stroke);

      float kr=r*.34f,kx=cx+nx*r*.55f,ky=cy+ny*r*.55f;
      p.setShadowLayer(12*ui,0,0,(accent&0x00FFFFFF)|0xCC000000);
      p.setColor(0xD90D1825);c.drawCircle(kx,ky,kr,p);p.clearShadowLayer();
      stroke.setStrokeWidth(2.3f*ui);stroke.setColor(accent);c.drawCircle(kx,ky,kr,stroke);

      p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(10*ui);p.setColor((accent&0x00FFFFFF)|0xEE000000);
      c.drawText(label,cx,cy-r-7*ui,p);
    }

    void drawMicroAimControls(Canvas c,int w,int h,float ui){
      boolean portrait=h>w;

      // Give the precision buttons generous, clearly separated thumb targets.
      // The empty center gap is intentional so a slightly-off tap cannot land
      // on the opposite direction button.
      float size=(portrait?92:112)*ui;
      float gap=(portrait?36:42)*ui;
      float x=(portrait?14:20)*ui;
      float y=h-size-(portrait?14:18)*ui;

      microLeftRect.set(x,y,x+size,y+size);
      microRightRect.set(x+size+gap,y,x+size*2+gap,y+size);

      p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);
      p.setTextSize((portrait?10.5f:11.5f)*ui);p.setColor(0xA9D7F4FF);
      c.drawText("PRECISION AIM",x+size+gap*.5f,y-10*ui,p);

      drawMicroPolygon(c,microLeftRect,true,ui);
      drawMicroPolygon(c,microRightRect,false,ui);
    }

    void drawMicroPolygon(Canvas c,RectF rr,boolean left,float ui){
      float cx=rr.centerX(),cy=rr.centerY(),w=rr.width(),h=rr.height();
      Path poly=new Path();
      poly.moveTo(cx-w*.36f,rr.top);
      poly.lineTo(cx+w*.36f,rr.top);
      poly.lineTo(rr.right,cy);
      poly.lineTo(cx+w*.36f,rr.bottom);
      poly.lineTo(cx-w*.36f,rr.bottom);
      poly.lineTo(rr.left,cy);
      poly.close();

      p.setStyle(Paint.Style.FILL);
      p.setShadowLayer(15*ui,0,0,0xCC38BDF8);
      p.setColor(0x5A071827);
      c.drawPath(poly,p);
      p.clearShadowLayer();

      stroke.setStyle(Paint.Style.STROKE);
      stroke.setStrokeWidth(2.3f*ui);
      stroke.setColor(0xD659D6FF);
      c.drawPath(poly,stroke);
      stroke.setStrokeWidth(.9f*ui);
      stroke.setColor(0xA6FFFFFF);
      Path inner=new Path();
      float inset=6*ui;
      RectF ir=new RectF(rr.left+inset,rr.top+inset,rr.right-inset,rr.bottom-inset);
      float icx=ir.centerX(),icy=ir.centerY(),iw=ir.width();
      inner.moveTo(icx-iw*.34f,ir.top);
      inner.lineTo(icx+iw*.34f,ir.top);
      inner.lineTo(ir.right,icy);
      inner.lineTo(icx+iw*.34f,ir.bottom);
      inner.lineTo(icx-iw*.34f,ir.bottom);
      inner.lineTo(ir.left,icy);
      inner.close();
      c.drawPath(inner,stroke);

      float dir=left?-1f:1f;
      float aw=w*.18f,ah=h*.19f,tip=w*.13f;
      Path arrow=new Path();
      arrow.moveTo(cx+dir*aw,cy-ah);
      arrow.lineTo(cx-dir*tip,cy);
      arrow.lineTo(cx+dir*aw,cy+ah);
      arrow.close();
      p.setShadowLayer(12*ui,0,0,0xEE5DE6FF);
      p.setColor(0xE6DDF8FF);
      c.drawPath(arrow,p);
      p.clearShadowLayer();
    }

    void drawSaberMenu(Canvas c,int w,int h,float ui,GameRenderer r){
      boolean portrait=h>w;
      float pw=portrait?Math.min(w*.94f,430*ui):Math.min(w*.82f,1080*ui);
      float ph=portrait?Math.min(h*.72f,500*ui):Math.min(h*.64f,430*ui);
      float x=w*.5f-pw*.5f,y=h*.5f-ph*.5f;
      saberPanelRect.set(x,y,x+pw,y+ph);
      RectF panel=saberPanelRect;
      p.setStyle(Paint.Style.FILL);p.setShadowLayer(18*ui,0,8*ui,0xCC000000);
      p.setShader(new LinearGradient(panel.left,panel.top,panel.right,panel.bottom,0xF51B2531,0xFA070A0F,Shader.TileMode.CLAMP));
      c.drawRoundRect(panel,24*ui,24*ui,p);p.clearShadowLayer();p.setShader(null);
      stroke.setColor(0xFFE4B84D);stroke.setStrokeWidth(3*ui);c.drawRoundRect(panel,24*ui,24*ui,stroke);
      stroke.setColor(0x66FFFFFF);stroke.setStrokeWidth(1.2f*ui);c.drawRoundRect(new RectF(panel.left+6*ui,panel.top+6*ui,panel.right-6*ui,panel.bottom-6*ui),18*ui,18*ui,stroke);
      p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize((portrait?18:22)*ui);p.setColor(Color.WHITE);
      c.drawText("GALACTIC SABER LOADOUT",w*.5f,y+30*ui,p);

      if(portrait){
        float gap=8*ui,cw=(pw-36*ui-gap*2)/3f,ch=72*ui;
        p.setTextSize(12*ui);p.setColor(0xFFD1D5DB);p.setTextAlign(Paint.Align.LEFT);
        c.drawText("HILTS",x+18*ui,y+55*ui,p);
        float hs=y+64*ui;
        for(int i=0;i<6;i++){
          int col=i%3,row=i/3;float lx=x+18*ui+col*(cw+gap),ty=hs+row*(ch+7*ui);
          hiltChoices[i].set(lx,ty,lx+cw,ty+ch);
          drawLoadoutChoice(c,hiltChoices[i],hilts[i],hiltNames[i],i==r.hiltIndex,ui,true);
        }
        float bsY=hs+2*(ch+7*ui)+20*ui;
        p.setTextSize(12*ui);p.setColor(0xFFD1D5DB);p.setTextAlign(Paint.Align.LEFT);
        c.drawText("BLADES",x+18*ui,bsY,p);
        float bstart=bsY+8*ui;
        for(int i=0;i<6;i++){
          int col=i%3,row=i/3;float lx=x+18*ui+col*(cw+gap),ty=bstart+row*(ch+7*ui);
          bladeChoices[i].set(lx,ty,lx+cw,ty+ch);
          drawLoadoutChoice(c,bladeChoices[i],blades[i],bladeNames[i],i==r.bladeIndex,ui,false);
        }
      }else{
        p.setTextSize(14*ui);p.setColor(0xFFD1D5DB);p.setTextAlign(Paint.Align.CENTER);
        c.drawText("HILT",x+45*ui,y+75*ui,p);c.drawText("BLADE",x+45*ui,y+235*ui,p);
        float gap=12*ui,cw=(pw-56*ui-gap*5)/6f;
        for(int i=0;i<6;i++){
          float lx=x+28*ui+i*(cw+gap);
          hiltChoices[i].set(lx,y+88*ui,lx+cw,y+198*ui);
          bladeChoices[i].set(lx,y+248*ui,lx+cw,y+360*ui);
          drawLoadoutChoice(c,hiltChoices[i],hilts[i],hiltNames[i],i==r.hiltIndex,ui,true);
          drawLoadoutChoice(c,bladeChoices[i],blades[i],bladeNames[i],i==r.bladeIndex,ui,false);
        }
      }
      p.setTextAlign(Paint.Align.CENTER);p.setTextSize(11*ui);p.setColor(0xFFB9C1CC);
      c.drawText("Tap outside to close",w*.5f,y+ph-12*ui,p);
    }

    void drawLoadoutChoice(Canvas c,RectF rr,Bitmap bmp,String name,boolean selected,float ui,boolean hilt){
      p.setShader(new LinearGradient(rr.left,rr.top,rr.left,rr.bottom,
        selected?0xCC453711:0xB519222E,selected?0xDD171108:0xC7080C12,Shader.TileMode.CLAMP));
      c.drawRoundRect(rr,11*ui,11*ui,p);p.setShader(null);
      stroke.setColor(selected?0xFFF4C542:0x667A8494);stroke.setStrokeWidth(selected?2.6f*ui:1.2f*ui);c.drawRoundRect(rr,11*ui,11*ui,stroke);
      if(bmp!=null){
        RectF img;
        if(hilt)img=new RectF(rr.left+4*ui,rr.top+13*ui,rr.right-4*ui,rr.bottom-22*ui);
        else img=new RectF(rr.left+5*ui,rr.top+21*ui,rr.right-5*ui,rr.top+43*ui);
        c.drawBitmap(bmp,null,img,p);
      }
      p.setTextAlign(Paint.Align.CENTER);p.setTextSize(8.4f*ui);p.setColor(Color.WHITE);
      c.drawText(name,rr.centerX(),rr.bottom-6*ui,p);
    }

    void drawMatchHud(Canvas c,int w,int h,float ui,GameRenderer r){
      // Compact scoreboard hugs the very top and stays centered so it does not
      // cover useful table space at low camera angles.
      boolean portrait=h>w;
      float panelH=(portrait?58:70)*ui,top=(portrait?4:7)*ui;
      float centerW=Math.min((portrait?88:112)*ui,w*(portrait?.21f:.13f));
      float teamW=portrait?Math.max(92*ui,(w-centerW-10*ui)*.5f):Math.min(224*ui,(w-centerW-22*ui)*.5f);
      float mid=w*.5f,leftRight=mid-centerW*.5f,rightLeft=mid+centerW*.5f;
      RectF left=new RectF(leftRight-teamW,top,leftRight+1*ui,top+panelH);
      RectF right=new RectF(rightLeft-1*ui,top,rightLeft+teamW,top+panelH);
      RectF center=new RectF(leftRight,top,rightLeft,top+panelH);
      drawTeamCard(c,left,1,ui,r);
      drawTeamCard(c,right,2,ui,r);

      boolean activeReady=false;
      int activeSuit=r.teamSuit[Math.max(0,Math.min(1,r.currentTeam-1))];
      if(activeSuit!=0)activeReady=r.remainingForSuit(activeSuit)==0;

      p.setStyle(Paint.Style.FILL);p.setColor(0xE20A0F17);c.drawRoundRect(center,10*ui,10*ui,p);
      stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(activeReady?2.8f*ui:1.5f*ui);
      stroke.setColor(activeReady?0xFFF4C542:0x88778491);c.drawRoundRect(center,10*ui,10*ui,stroke);

      p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);
      p.setTextSize(11.5f*ui);p.setColor(0xFFF4C542);
      String centerText=r.gameOver?(r.aiEnabled?(r.winnerTeam==1?"YOU WIN":"AI WINS"):("TEAM "+r.winnerTeam+" WINS")):
        (r.aiEnabled?(r.currentTeam==1?"YOUR TURN":"AI TURN"):("TEAM "+r.currentTeam+" TURN"));
      c.drawText(centerText,center.centerX(),center.top+15*ui,p);

      float bx=center.centerX(),by=center.top+39*ui,br=12.5f*ui;
      if(activeReady){
        p.setShadowLayer(9*ui,0,0,0xFFF4C542);
        p.setColor(0xFF070707);c.drawCircle(bx,by,br,p);p.clearShadowLayer();
        stroke.setStrokeWidth(2.6f*ui);stroke.setColor(0xFFF4C542);c.drawCircle(bx,by,br,stroke);
      }else{
        p.setColor(0xFF070707);c.drawCircle(bx,by,br,p);
        stroke.setStrokeWidth(1.5f*ui);stroke.setColor(0xFFD8DEE7);c.drawCircle(bx,by,br,stroke);
      }
      p.setColor(Color.WHITE);p.setTextSize(10.5f*ui);c.drawText("8",bx,by+3.7f*ui,p);
      p.setTextSize(7.5f*ui);p.setColor(activeReady?0xFFF4C542:0xFF9FAABA);
      c.drawText(activeReady?"READY":"8 BALL",bx,center.bottom-6*ui,p);

      // Keep the rule message tiny and directly below the scoreboard.
      p.setTextSize(9.5f*ui);p.setColor(0xDDDCE4EE);
      c.drawText(r.ruleMessage==null?"":r.ruleMessage,w*.5f,top+panelH+11*ui,p);
    }

    void drawTeamCard(Canvas c,RectF rr,int team,float ui,GameRenderer r){
      boolean active=!r.gameOver&&r.currentTeam==team;
      int teamAccent=team==1?0xFF55B8FF:0xFFFF6262;
      int teamAccentDim=team==1?0x9955A8FF:0x99FF5F5F;
      p.setStyle(Paint.Style.FILL);
      p.setColor(active?(team==1?0xD9182B3A:0xD9361D24):0xC70F141C);
      c.drawRoundRect(rr,10*ui,10*ui,p);
      stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth((active?2.6f:1.2f)*ui);
      stroke.setColor(active?teamAccent:teamAccentDim);
      if(active){stroke.setShadowLayer(7*ui,0,0,teamAccent);}
      c.drawRoundRect(rr,10*ui,10*ui,stroke);
      stroke.clearShadowLayer();

      p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.LEFT);
      p.setTextSize(10.5f*ui);p.setColor(team==1?0xFF8CC8FF:0xFFFF9B9B);
      String teamLabel=r.aiEnabled?(team==1?"YOU":"GALACTIC AI"):("TEAM "+team);
      c.drawText(teamLabel,rr.left+8*ui,rr.top+13*ui,p);

      int suit=r.teamSuit[team-1];
      String suitText=suit==1?"SOLIDS":suit==2?"STRIPES":"OPEN";
      p.setTextAlign(Paint.Align.RIGHT);p.setTextSize(8.5f*ui);p.setColor(0xFFE7EDF5);
      c.drawText(suitText,rr.right-8*ui,rr.top+13*ui,p);

      if(suit==0){
        p.setTextAlign(Paint.Align.CENTER);p.setTextSize(9*ui);p.setColor(0xFFB8C2D0);
        c.drawText("FIRST GROUP CLAIMS",rr.centerX(),rr.centerY()+6*ui,p);
        return;
      }

      int start=suit==1?1:9,end=suit==1?7:15;
      ArrayList<Integer> remain=new ArrayList<>();
      for(int n=start;n<=end;n++)if(r.isBallOnTable(n))remain.add(n);

      // Seven compact spots in a 2-3-2 diamond cluster.
      boolean mini=rr.height()<64*ui;
      float cx=rr.centerX(),baseY=rr.top+(mini?25:30)*ui;
      float dx=(mini?12.5f:15)*ui,dy=(mini?10.5f:13)*ui,rad=(mini?5.3f:6.2f)*ui;
      float[][] spots={
        {-dx*.55f,0},{dx*.55f,0},
        {-dx,dy},{0,dy},{dx,dy},
        {-dx*.55f,dy*2},{dx*.55f,dy*2}
      };
      for(int i=0;i<7;i++){
        int n=start+i;
        boolean onTable=r.isBallOnTable(n);
        float x=cx+spots[i][0],y=baseY+spots[i][1];
        if(suit==1){
          p.setColor(onTable?0xFFE8B84C:0x333A3A3A);c.drawCircle(x,y,rad,p);
        }else{
          p.setColor(onTable?0xFFF5F5F5:0x333A3A3A);c.drawCircle(x,y,rad,p);
          stroke.setColor(onTable?0xFFE8B84C:0x33444444);stroke.setStrokeWidth(2.1f*ui);c.drawCircle(x,y,rad*.70f,stroke);
        }
        p.setTextAlign(Paint.Align.CENTER);p.setTextSize(5.6f*ui);p.setColor(onTable?0xFF111111:0x55888888);
        c.drawText(String.valueOf(n),x,y+2.0f*ui,p);
      }

      p.setTextAlign(Paint.Align.RIGHT);p.setTextSize(7.4f*ui);
      p.setColor(remain.isEmpty()?0xFFF4C542:0xFFB9C4D2);
      c.drawText(remain.isEmpty()?"8 READY":remain.size()+" LEFT",rr.right-7*ui,rr.bottom-5*ui,p);
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
      boolean portrait=h>w;
      englishR=portrait?Math.min(w*.225f,112*ui):Math.min(h*.205f,145*ui);
      englishCx=w-englishR-(portrait?18:34)*ui;
      englishCy=portrait?h*.46f:h*.43f;
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

      // Gameplay instructions live in the first-account tutorial instead of
      // permanently covering the hilt and predictor during every shot.
    }

    void drawThumbStrikeHilt(Canvas c,int w,int h,float ui,GameRenderer r){
      // The hilt/blade itself is rendered with the real 3D model by OpenGL.
      // HUD only supplies the touch target, subtle track, and power readout.
      boolean portrait=h>w;
      float baseW=(portrait?154:198)*ui,baseH=(portrait?104:122)*ui;
      float cx=w-(portrait?96:136)*ui;
      float baseCy=Math.max((portrait?132:142)*ui,h*(portrait?.36f:.39f));
      float maxTravel=Math.max((portrait?230:190)*ui,h*(portrait?.46f:.50f));
      float travel=Math.min(maxTravel,r.chargePullPx);
      float cy=baseCy+travel;
      thumbHiltRect.set(cx-baseW*.5f,cy-baseH*.58f,cx+baseW*.5f,cy+baseH*.58f);
      thumbGrabRect.set(cx-baseW*.80f,cy-baseH*.86f,cx+baseW*.80f,cy+baseH*.86f);

      float trackTop=baseCy-94*ui,trackBottom=Math.min(h-16*ui,baseCy+maxTravel);
      stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(2.4f*ui);
      stroke.setColor(0x4F5BD6FF);c.drawLine(cx,trackTop,cx,trackBottom,stroke);

      p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);
      p.setTextSize(9.5f*ui);p.setColor(0xA9DDF8FF);
      c.drawText("THUMB STRIKE",cx,trackTop-8*ui,p);
      p.setTextSize(13*ui);p.setColor(r.power>1f?0xFFF4C542:0xFFC1CDDA);
      c.drawText(Math.round(r.power)+"%",cx,Math.min(h-8*ui,trackBottom+17*ui),p);
    }

    public boolean onTouchEvent(MotionEvent e){
      final int a=e.getActionMasked();final float x=e.getX(),y=e.getY();final int w=getWidth(),h=getHeight();
      final float ui=Math.max(.90f,Math.min(w/900f,h/640f));
      final GameRenderer r=game.r;

      // Two-finger camera control: orbit + pinch zoom.
      if(e.getPointerCount()>=2 || camGesture){
        if((a==MotionEvent.ACTION_POINTER_DOWN||a==MotionEvent.ACTION_DOWN) && e.getPointerCount()>=2){
          camGesture=true;pullingHilt=false;
          endAimStick();endCameraStick();
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
          game.queueEvent(()->r.cameraPanZoomGesture(dragX,dragY,pinch,w,h));return true;
        }
        if(a==MotionEvent.ACTION_POINTER_UP||a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){
          if(e.getPointerCount()<=2)camGesture=false;return true;
        }
      }

      if(a==MotionEvent.ACTION_DOWN){
        // Saber loadout is a true modal. Tapping anywhere outside closes it.
        if(menuOpen){
          if(!saberPanelRect.contains(x,y)){menuOpen=false;invalidate();return true;}
          for(int i=0;i<6;i++){
            if(hiltChoices[i].contains(x,y)){final int k=i;game.queueEvent(()->r.userSelectHilt(k));return true;}
            if(bladeChoices[i].contains(x,y)){final int k=i;game.queueEvent(()->r.userSelectBlade(k));return true;}
          }
          return true;
        }

        if(sideMenuTabRect.contains(x,y)){
          sideMenuOpen=!sideMenuOpen;
          invalidate();
          return true;
        }

        // Tapping off the side menu closes it and consumes the tap so the player
        // never accidentally changes aim while dismissing the menu.
        if(sideMenuOpen&&!sideMenuPanelRect.contains(x,y)){
          sideMenuOpen=false;invalidate();return true;
        }

        if(r.state==GameRenderer.AIMING&&!r.gameOver&&r.localCanControl()&&!sideMenuOpen){
          if(microLeftRect.contains(x,y)){
            beginMicroHold(-1,w,h);
            return true;
          }
          if(microRightRect.contains(x,y)){
            beginMicroHold(1,w,h);
            return true;
          }
          if(cameraStickRect.contains(x,y)){
            cameraStickActive=true;
            updateCameraStick(x,y);
            uiHandler.removeCallbacks(cameraStickRepeat);
            uiHandler.post(cameraStickRepeat);
            return true;
          }
        }

        if(sideMenuOpen&&saberMenuRect.contains(x,y)){menuOpen=true;sideMenuOpen=false;invalidate();return true;}
        if(sideMenuOpen&&rackRect.contains(x,y)){sideMenuOpen=false;invalidate();pullingHilt=false;game.queueEvent(()->r.userResetRack());return true;}
        if(sideMenuOpen&&activeShooterRect.contains(x,y)){sideMenuOpen=false;invalidate();game.queueEvent(()->r.userToggleActiveShooter());return true;}
        if(sideMenuOpen&&teamSwitchRect.contains(x,y)){sideMenuOpen=false;invalidate();game.queueEvent(()->r.userSwitchTeam());return true;}
        if(sideMenuOpen&&multiplayerRect.contains(x,y)){
          sideMenuOpen=false;invalidate();
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
          if(thumbGrabRect.contains(x,y)){
            pullingThumbHilt=true;thumbPullStartY=y;
            game.queueEvent(()->r.beginWorldCharge());
            return true;
          }
          updateWorldHiltGeometry(w,h,r,r.chargePullPx);
          RectF hit=new RectF(worldHiltRect);hit.inset(-36*ui,-36*ui);
          if(hit.contains(x,y)){pullingHilt=true;hiltPullStartX=x;hiltPullStartY=y;game.queueEvent(()->r.beginWorldCharge());return true;}
        }
      }

      if(aimStickActive){
        if(a==MotionEvent.ACTION_MOVE){updateAimStick(x,y);return true;}
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL||a==MotionEvent.ACTION_POINTER_UP){endAimStick();return true;}
        return true;
      }

      if(cameraStickActive){
        if(a==MotionEvent.ACTION_MOVE){updateCameraStick(x,y);return true;}
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL||a==MotionEvent.ACTION_POINTER_UP){endCameraStick();return true;}
        return true;
      }

      if(microHolding){
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL||a==MotionEvent.ACTION_POINTER_UP){
          endMicroHold();
        }
        return true;
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

      if(pullingThumbHilt){
        if(a==MotionEvent.ACTION_MOVE){
          float pull=Math.max(0,y-thumbPullStartY);
          if(r.state==GameRenderer.CHARGING){
            final float fp=pull;game.queueEvent(()->r.updateWorldCharge(fp,h));
          }
          return true;
        }
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){
          pullingThumbHilt=false;
          if(r.state==GameRenderer.CHARGING)game.queueEvent(()->r.releaseWorldCharge());
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

    void updateAimStick(float x,float y){
      float cx=aimStickRect.centerX(),cy=aimStickRect.centerY(),r=aimStickRect.width()*.5f;
      float dx=(x-cx)/Math.max(1f,r),dy=(y-cy)/Math.max(1f,r);
      float d=(float)Math.sqrt(dx*dx+dy*dy);if(d>1f){dx/=d;dy/=d;}
      aimStickX=dx;aimStickY=dy;invalidate();
    }

    void endAimStick(){
      aimStickActive=false;aimStickX=aimStickY=0;
      uiHandler.removeCallbacks(aimStickRepeat);invalidate();
    }

    void updateCameraStick(float x,float y){
      float cx=cameraStickRect.centerX(),cy=cameraStickRect.centerY(),r=cameraStickRect.width()*.5f;
      float dx=(x-cx)/Math.max(1f,r),dy=(y-cy)/Math.max(1f,r);
      float d=(float)Math.sqrt(dx*dx+dy*dy);if(d>1f){dx/=d;dy/=d;}
      cameraStickX=dx;cameraStickY=dy;invalidate();
    }

    void endCameraStick(){
      cameraStickActive=false;cameraStickX=cameraStickY=0;
      uiHandler.removeCallbacks(cameraStickRepeat);invalidate();
    }

    void beginMicroHold(int dir,int w,int h){
      endMicroHold();
      microHolding=true;microHoldDir=dir;microHoldW=w;microHoldH=h;
      final boolean left=dir<0;
      // Lock the WORLD rotation direction once at press-down. The old code
      // re-evaluated "screen-left" every repeat; at the screen-space 180-degree
      // extremum the sign flipped back and forth and trapped the aim there.
      game.queueEvent(()->game.r.beginMicroAimHold(left,w,h));
      uiHandler.postDelayed(microRepeat,220);
    }

    void endMicroHold(){
      microHolding=false;microHoldDir=0;
      uiHandler.removeCallbacks(microRepeat);
      game.queueEvent(()->game.r.endMicroAimHold());
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
    Mesh sphere,hiltCylinder,hiltBox,teamAidRing,teamAidSegmentRing,thumbBladeMesh; Mesh[] realHiltMeshes=new Mesh[6]; int[] realHiltTextures=new int[6]; Mesh[] saberMeshes=new Mesh[6]; int[] saberTextures=new int[6]; int[] hiltTextures=new int[6]; HashMap<String,Integer> tex=new HashMap<>();
    Mesh[] ringMeshes=new Mesh[7*3];
    final ArrayList<float[]> predictorRails=new ArrayList<>();
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
    float aspect=16f/9f; int surfaceW=1,surfaceH=1; long last=0; float[] pvCache=new float[16];
    volatile float camYaw=180f,camPitch=46f,camDist=150f,camTargetX=0f,camTargetZ=0f;
    volatile float camGoalYaw=180f,camGoalPitch=43f,camGoalDist=132f,camGoalTargetX=0f,camGoalTargetZ=0f;
    volatile int state=AIMING,hiltIndex=0,bladeIndex=5;
    volatile int currentTeam=1,winnerTeam=0,activeShooter=1;
    final int[] teamSuit={0,0}; // 0=open, 1=solids, 2=stripes
    final ArrayList<Integer> ballsSunkThisShot=new ArrayList<>();
    volatile boolean tableOpen=true,gameOver=false;
    volatile boolean aiEnabled=false,aiThinking=false;
    volatile long aiReadyAt=0;
    volatile String ruleMessage="BREAK • TEAM 1";
    volatile float power=0,englishX=0,englishY=0;
    float aimX=1,aimZ=0,desiredAimX=1,desiredAimZ=0,chargeStartY=-1,sideSpin=0,topSpin=0;
    volatile float chargePullPx=0,chargePullWorld=0;
    volatile int microAimHoldSign=0;
    boolean breakAssistArmed=true;
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
    public void onSurfaceChanged(GL10 gl,int w,int h){GLES20.glViewport(0,0,w,h);surfaceW=Math.max(1,w);surfaceH=Math.max(1,h);aspect=(float)w/Math.max(1,h);}

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

      int skyTex=tex.getOrDefault("sky",0);
      if(skyTex!=0&&sphere!=null){
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);GLES20.glDepthMask(false);
        float[] SM=identity();
        android.opengl.Matrix.translateM(SM,0,cx,cy,cz);
        android.opengl.Matrix.rotateM(SM,0,112f,0,1,0);
        android.opengl.Matrix.scaleM(SM,0,138f,138f,138f);
        drawMesh(sphere,pvCache,SM,skyTex,new float[]{1f,1f,1f,1f});
        GLES20.glDepthMask(true);GLES20.glEnable(GLES20.GL_DEPTH_TEST);
      }

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
        drawTeamAidRing(pvCache,b);
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
      if(state==CHARGING&&!gameOver&&localCanControl())drawThumbStrike3D();
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
        try{tex.put("sky",loadTexture("environment/sky.jpg"));}catch(Exception ignored){}
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
        teamAidRing=makeRingMesh(1.62f,.18f,64);
        teamAidSegmentRing=makeSegmentedRingMesh(2.02f,.22f,24);
        thumbBladeMesh=makeThumbBladeMesh();
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

    Mesh makeThumbBladeMesh(){
      // Unit quad: local Y is blade length, local X is blade width.
      float[] p={
        -.5f,0,0,  .5f,0,0,  .5f,1,0,
        -.5f,0,0,  .5f,1,0, -.5f,1,0
      };
      // AssetBundle saber texture is vertical; V runs along blade length.
      float[] uv={0,1, 1,1, 1,0, 0,1, 1,0, 0,0};
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

    void drawThumbBladeOverlay(float[] pv,float x,float emitterY,float bladeLen,int texture,float width,float alpha){
      if(thumbBladeMesh==null||bladeLen<=.002f)return;
      float[] M=identity();
      android.opengl.Matrix.translateM(M,0,x,emitterY,0);
      android.opengl.Matrix.scaleM(M,0,width,bladeLen,1f);
      drawMesh(thumbBladeMesh,pv,M,texture,new float[]{1f,1f,1f,alpha});
    }

    void drawThumbStrike3D(){
      int hi=Math.max(0,Math.min(5,hiltIndex));
      Mesh authored=realHiltMeshes[hi];
      if(authored==null)return;

      // Orthographic overlay lets us reuse the exact selected authored hilt model
      // without tying this control to the table camera.
      float[] O=new float[16];
      android.opengl.Matrix.orthoM(O,0,-aspect,aspect,-1f,1f,-5f,5f);

      float pullNorm=Math.max(0f,Math.min(1f,power/100f));
      float x=aspect*.675f;
      float baseY=.10f;
      float hiltTravel=.72f*pullNorm;
      float hiltY=baseY-hiltTravel;
      float hiltScale=.54f;
      float emitterAtRest=baseY+hiltScale*.52f;
      float emitterY=hiltY+hiltScale*.52f;
      float bladeLen=Math.max(0f,(emitterAtRest-emitterY)*1.12f);

      GLES20.glDisable(GLES20.GL_DEPTH_TEST);GLES20.glDepthMask(false);

      // Growing blade stays connected to the moving emitter and reaches back
      // toward its rest position, matching the physical pull-back metaphor.
      int bladeTex=saberTextures[Math.max(0,Math.min(5,bladeIndex))];
      if(bladeLen>.004f){
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE);
        drawThumbBladeOverlay(O,x,emitterY,bladeLen,bladeTex,.145f,.32f);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
        drawThumbBladeOverlay(O,x,emitterY,bladeLen,bladeTex,.082f,1f);
      }

      float[] M=identity();
      android.opengl.Matrix.translateM(M,0,x,hiltY,0);
      android.opengl.Matrix.rotateM(M,0,90f,0,0,1);
      android.opengl.Matrix.rotateM(M,0,-16f,0,1,0);
      android.opengl.Matrix.rotateM(M,0,8f,1,0,0);
      android.opengl.Matrix.scaleM(M,0,hiltScale,hiltScale,hiltScale);
      drawLitMesh(authored,O,M,realHiltTextures[hi],new float[]{1f,1f,1f,1f});

      GLES20.glDepthMask(true);GLES20.glEnable(GLES20.GL_DEPTH_TEST);
      GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
    }

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

    Mesh makeSegmentedRingMesh(float radius,float thickness,int seg){
      float inner=Math.max(.02f,radius-thickness*.5f),outer=radius+thickness*.5f;
      ArrayList<Float> pp=new ArrayList<>(),uu=new ArrayList<>();
      for(int i=0;i<seg;i++){
        if((i&1)==1)continue;
        float a0=(float)(Math.PI*2*(i+.10f)/seg),a1=(float)(Math.PI*2*(i+.90f)/seg);
        float c0=(float)Math.cos(a0),s0=(float)Math.sin(a0),c1=(float)Math.cos(a1),s1=(float)Math.sin(a1);
        float[][] v={{inner,c0,s0},{outer,c0,s0},{outer,c1,s1},{inner,c0,s0},{outer,c1,s1},{inner,c1,s1}};
        for(int k=0;k<6;k++){
          float rad=v[k][0],cc=v[k][1],ss=v[k][2];
          pp.add(rad*cc);pp.add(0f);pp.add(rad*ss);uu.add(k==1||k==2||k==4?1f:0f);uu.add(0f);
        }
      }
      float[] p=new float[pp.size()],uv=new float[uu.size()];
      for(int i=0;i<p.length;i++)p[i]=pp.get(i);
      for(int i=0;i<uv.length;i++)uv[i]=uu.get(i);
      return new Mesh(p,uv);
    }

    void drawTeamAidRing(float[] pv,Ball b){
      if(teamAidRing==null||b.sinking||b.index==0)return;
      int team=0;
      int suit=suitForBall(b.index);
      if(suit!=0){
        if(teamSuit[0]==suit)team=1;
        else if(teamSuit[1]==suit)team=2;
      }

      boolean eightReady=false;
      if(b.index==8&&currentTeam>=1&&currentTeam<=2){
        int ss=teamSuit[currentTeam-1];
        eightReady=ss!=0&&remainingForSuit(ss)==0;
      }
      if(team==0&&!eightReady)return;

      float[] color;
      if(eightReady)color=new float[]{1f,.78f,.08f,1f};
      else if(team==1)color=new float[]{.08f,.72f,1f,1f};
      else color=new float[]{1f,.10f,.10f,1f};

      double time=System.nanoTime()/1_000_000_000.0;
      float pulse=1f+.075f*(float)Math.sin(time*5.2+b.index*.57);
      float bob=.035f*(float)Math.sin(time*4.1+b.index);
      float spin=(float)((time*(team==2?-105.0:105.0)+b.index*17.0)%360.0);

      float[] M=identity();
      android.opengl.Matrix.translateM(M,0,b.x,.98f+bob,b.z);
      android.opengl.Matrix.scaleM(M,0,pulse,1f,pulse);

      GLES20.glDepthMask(false);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE);

      // Hot inner ring plus multiple additive halos.
      for(int layer=0;layer<4;layer++){
        float sc=1f+layer*.095f;
        float[] L=M.clone();android.opengl.Matrix.scaleM(L,0,sc,1f,sc);
        float alpha=layer==0?.96f:(.62f/(layer+.35f));
        drawMesh(teamAidRing,pv,L,0,new float[]{color[0],color[1],color[2],alpha});
      }

      // Rotating broken energy ring makes team-owned balls easy to spot even
      // against bright planet textures and gives the indicator visible motion.
      if(teamAidSegmentRing!=null){
        float[] A=M.clone();android.opengl.Matrix.rotateM(A,0,spin,0,1,0);
        drawMesh(teamAidSegmentRing,pv,A,0,new float[]{color[0],color[1],color[2],.90f});
        float[] B=M.clone();android.opengl.Matrix.rotateM(B,0,-spin*.72f,0,1,0);android.opengl.Matrix.scaleM(B,0,1.16f,1f,1.16f);
        drawMesh(teamAidSegmentRing,pv,B,0,new float[]{color[0],color[1],color[2],.42f});
      }

      GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);GLES20.glDepthMask(true);
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

    void cameraOrbitAnalog(float ax,float ay,float dt){
      float dead=.07f;
      float mx=Math.abs(ax),my=Math.abs(ay);
      if(mx>dead){
        float e=(mx-dead)/(1f-dead);
        camYaw-=Math.signum(ax)*(28f+118f*e*e)*dt;
      }
      if(my>dead){
        float e=(my-dead)/(1f-dead);
        camPitch-=Math.signum(ay)*(18f+55f*e*e)*dt;
        camPitch=Math.max(18f,Math.min(76f,camPitch));
      }
    }

    void cameraPanZoomGesture(float dragX,float dragY,float pinch,int w,int h){
      if(pinch>.01f)camDist=Math.max(72f,Math.min(230f,camDist/pinch));

      // Two-finger drag pans the table in screen space. Horizontal drag follows
      // camera-right; vertical drag follows the camera's ground-plane forward.
      float yaw=(float)Math.toRadians(camYaw+(aspect<1f?90f:0f));
      float rx=(float)Math.cos(yaw),rz=-(float)Math.sin(yaw);
      float fx=-(float)Math.sin(yaw),fz=-(float)Math.cos(yaw);
      float scale=camDist*.86f;
      float sx=dragX/Math.max(1f,w)*scale;
      float sy=dragY/Math.max(1f,h)*scale;
      camTargetX-=rx*sx+fx*sy;
      camTargetZ-=rz*sx+fz*sy;
      camTargetX=Math.max(-34f,Math.min(34f,camTargetX));
      camTargetZ=Math.max(-17f,Math.min(17f,camTargetZ));
    }

    void autoFrameCue(){
      // Intentionally disabled. Camera ownership is entirely manual:
      // right analog stick = orbit/pitch; two fingers = pan/zoom.
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
      predictorRails.clear();
      world.setContinuousPhysics(true);world.setWarmStarting(true);
      BodyDef rbd=new BodyDef();rbd.type=BodyType.STATIC;railBody=world.createBody(rbd);

      boolean loadedTableCollider=false;
      try(BufferedReader br=new BufferedReader(new InputStreamReader(ctx.getAssets().open("extracted/table_collider_2d.txt")))){
        String line;
        while((line=br.readLine())!=null){
          line=line.trim();if(line.isEmpty()||line.startsWith("#"))continue;
          String[] q=line.split("\\s+");if(q.length<4)continue;
          float x1=Float.parseFloat(q[0]),z1=Float.parseFloat(q[1]),x2=Float.parseFloat(q[2]),z2=Float.parseFloat(q[3]);
          createRailEdge(x1,z1,x2,z2);
          predictorRails.add(new float[]{x1,z1,x2,z2});
          loadedTableCollider=true;
        }
      }catch(Exception e){e.printStackTrace();}

      // Fallback only if the extracted Unity MeshCollider could not be loaded.
      if(!loadedTableCollider){
        final float CX=42f,CZ=21f,CORNER=3.65f,SIDE=2.95f;
        float[][] fallback={
          {-CX,-CZ+CORNER,-CX,CZ-CORNER},{CX,-CZ+CORNER,CX,CZ-CORNER},
          {-CX+CORNER,-CZ,-SIDE,-CZ},{SIDE,-CZ,CX-CORNER,-CZ},
          {-CX+CORNER,CZ,-SIDE,CZ},{SIDE,CZ,CX-CORNER,CZ}
        };
        for(float[] e:fallback){
          createRailEdge(e[0],e[1],e[2],e[3]);
          predictorRails.add(e);
        }
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
      if(aiEnabled)return !gameOver&&currentTeam==1&&activeShooter==1;
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
      balls.clear();physicsAccum=0;aiThinking=false;aiReadyAt=0;resetRules();
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

    int screenMicroAimSign(boolean left,int w,int h){
      if(balls.isEmpty())return left?-1:1;
      Ball cue=balls.get(0);
      float base=(float)Math.atan2(aimZ,aimX);
      float probe=(float)Math.toRadians(1.0f);
      float am=base-probe,ap=base+probe;
      float[] pm=worldToScreen(cue.x+(float)Math.cos(am)*14f,2.22f,cue.z+(float)Math.sin(am)*14f,w,h);
      float[] pp=worldToScreen(cue.x+(float)Math.cos(ap)*14f,2.22f,cue.z+(float)Math.sin(ap)*14f,w,h);

      if(pm!=null&&pp!=null){
        float dx=pp[0]-pm[0];
        if(Math.abs(dx)>.12f){
          // +1 means increasing world angle. Pick it if that screen motion is
          // toward the requested left/right side at the START of the hold.
          if(left)return dx<0?1:-1;
          return dx>0?1:-1;
        }

        // Exactly at a horizontal screen-space extremum, use the projected Y
        // tangent only to choose a deterministic continuation direction.
        float dy=pp[1]-pm[1];
        if(Math.abs(dy)>.12f){
          if(left)return dy<0?-1:1;
          return dy<0?1:-1;
        }
      }
      return left?-1:1;
    }

    void beginMicroAimHold(boolean left,int w,int h){
      if(!localCanControl()||state!=AIMING||gameOver)return;
      microAimHoldSign=screenMicroAimSign(left,w,h);
      microAimByWorldSign(microAimHoldSign,.10f);
    }

    void endMicroAimHold(){
      microAimHoldSign=0;
    }

    void microAimHoldStep(float degrees){
      if(microAimHoldSign==0||!localCanControl()||state!=AIMING||gameOver)return;
      microAimByWorldSign(microAimHoldSign,degrees);
    }

    void microAimByWorldSign(int sign,float degrees){
      float base=(float)Math.atan2(aimZ,aimX);
      float step=(float)Math.toRadians(Math.max(.05f,degrees));
      float next=base+(sign<0?-step:step);
      // atan2/cos/sin naturally wrap through ±PI, so a held button can continue
      // indefinitely through 360 degrees with no 180-degree clamp or oscillation.
      previewAimAngle(next);
    }

    void microAimScreen(boolean left,int w,int h){
      if(!localCanControl()||state!=AIMING||gameOver)return;
      int sign=screenMicroAimSign(left,w,h);
      microAimByWorldSign(sign,.10f);
    }

    void microAimScreen(boolean left,int w,int h,float degrees){
      if(!localCanControl()||state!=AIMING||gameOver)return;
      int sign=screenMicroAimSign(left,w,h);
      microAimByWorldSign(sign,degrees);
    }

    void analogAim(float axis,float dt){
      if(!localCanControl()||state!=AIMING||gameOver)return;
      float a=Math.abs(axis),dead=.055f;
      if(a<=dead)return;
      float e=(a-dead)/(1f-dead);
      // Very gentle around center for sub-degree lining up, but full deflection
      // spins fast enough to sweep all the way around the cue ball.
      float degPerSec=4.0f+260f*e*e;
      float delta=(float)Math.toRadians(Math.signum(axis)*degPerSec*dt);
      float base=(float)Math.atan2(aimZ,aimX);
      previewAimAngle(base+delta);
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
      chargePullWorld=pullPx/Math.max(10f,h*.055f);
      power=Math.min(100f,pullPx/Math.max(120f,h*.50f)*100f);
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
        int previousState=state;
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

    boolean aiPathClear(float sx,float sz,float ex,float ez,int ignoreA,int ignoreB){
      float dx=ex-sx,dz=ez-sz,len2=dx*dx+dz*dz;
      if(len2<.01f)return true;
      for(Ball b:balls){
        if(!b.active||b.sinking||b.index==ignoreA||b.index==ignoreB)continue;
        float t=((b.x-sx)*dx+(b.z-sz)*dz)/len2;
        if(t<=.05f||t>=.95f)continue;
        float px=sx+dx*t,pz=sz+dz*t,ox=b.x-px,oz=b.z-pz;
        float clearance=PHYS_R*2.08f;
        if(ox*ox+oz*oz<clearance*clearance)return false;
      }
      return true;
    }

    ArrayList<Ball> aiTargets(){
      ArrayList<Ball> out=new ArrayList<>();
      int suit=teamSuit[1];
      boolean eightReady=suit!=0&&remainingForSuit(suit)==0;
      for(Ball b:balls){
        if(!b.active||b.sinking||b.index==0)continue;
        if(eightReady){
          if(b.index==8)out.add(b);
        }else if(b.index!=8&&(suit==0||suitForBall(b.index)==suit)){
          out.add(b);
        }
      }
      return out;
    }

    void performAiShot(){
      if(!aiEnabled||gameOver||currentTeam!=2||state!=AIMING||!allStopped())return;
      Ball cue=balls.get(0);
      if(cue==null||!cue.active)return;
      ArrayList<Ball> targets=aiTargets();
      if(targets.isEmpty())return;

      float[][] pockets={
        {MINX-1.0f,MINZ-1.0f},{0,MINZ-1.15f},{MAXX+1.0f,MINZ-1.0f},
        {MINX-1.0f,MAXZ+1.0f},{0,MAXZ+1.15f},{MAXX+1.0f,MAXZ+1.0f}
      };

      Ball best=null;float bestAimX=1,bestAimZ=0,bestScore=Float.MAX_VALUE,bestDist=0;
      for(Ball t:targets){
        for(float[] pocket:pockets){
          float pdx=pocket[0]-t.x,pdz=pocket[1]-t.z;
          float pd=(float)Math.sqrt(pdx*pdx+pdz*pdz);if(pd<.01f)continue;
          float ux=pdx/pd,uz=pdz/pd;
          float gx=t.x-ux*(PHYS_R*2.02f),gz=t.z-uz*(PHYS_R*2.02f);
          if(gx<MINX+R||gx>MAXX-R||gz<MINZ+R||gz>MAXZ-R)continue;
          float cdx=gx-cue.x,cdz=gz-cue.z,cd=(float)Math.sqrt(cdx*cdx+cdz*cdz);if(cd<.01f)continue;
          if(!aiPathClear(cue.x,cue.z,gx,gz,0,t.index))continue;
          if(!aiPathClear(t.x,t.z,pocket[0],pocket[1],t.index,0))continue;
          float ax=cdx/cd,az=cdz/cd;
          float cut=Math.max(-1f,Math.min(1f,ax*ux+az*uz));
          float cutPenalty=(1f-cut)*22f;
          float score=cd+pd*.72f+cutPenalty;
          if(score<bestScore){
            bestScore=score;best=t;bestAimX=ax;bestAimZ=az;bestDist=cd+pd;
          }
        }
      }

      if(best==null){
        for(Ball t:targets){
          float dx=t.x-cue.x,dz=t.z-cue.z,d=(float)Math.sqrt(dx*dx+dz*dz);
          if(d<bestScore&&d>.01f){
            bestScore=d;best=t;bestAimX=dx/d;bestAimZ=dz/d;bestDist=d;
          }
        }
      }
      if(best==null)return;

      float angle=(float)Math.atan2(bestAimZ,bestAimX);
      // Small human-like aim variation keeps the generic AI competitive without being perfect.
      float error=(float)Math.sin((System.nanoTime()&0xFFFF)*.0017f)*.012f;
      angle+=error;
      aimX=desiredAimX=(float)Math.cos(angle);
      aimZ=desiredAimZ=(float)Math.sin(angle);
      englishX=englishY=sideSpin=topSpin=0;
      power=Math.max(42f,Math.min(82f,44f+bestDist*.42f));
      activeShooter=2;currentTeam=2;
      ruleMessage="GALACTIC AI SHOOTS";
      executeShot();
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
          if(aiEnabled&&!gameOver&&currentTeam==2){
            aiThinking=true;
            aiReadyAt=System.currentTimeMillis()+1100;
            activeShooter=2;
            ruleMessage="GALACTIC AI THINKING";
          }else{
            aiThinking=false;
          }
        }
      }else{
        advanceSinks(dt);
        if(aiEnabled&&!gameOver&&currentTeam==2&&state==AIMING&&allStopped()){
          if(!aiThinking){
            aiThinking=true;
            aiReadyAt=System.currentTimeMillis()+1100;
            activeShooter=2;
            ruleMessage="GALACTIC AI THINKING";
          }else if(System.currentTimeMillis()>=aiReadyAt){
            aiThinking=false;
            performAiShot();
          }
        }
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

    boolean arcadePocketAt(float x,float z){
      final float PX=42.0f,PZ=21.0f;
      // Side pockets: wide forgiving mouth.
      float sideDz=Math.min(Math.abs(z-PZ),Math.abs(z+PZ));
      if(Math.abs(x)<=3.45f&&sideDz<=3.45f)return true;

      // Corner pockets: generous radial capture zone around each mouth.
      float cx=x>=0?PX:-PX,cz=z>=0?PZ:-PZ;
      float dx=x-cx,dz=z-cz;
      return dx*dx+dz*dz<=3.85f*3.85f;
    }

    void checkPocket(int index,Ball b){
      if(!b.active||b.sinking)return;

      // Arcade assist: once the center of a ball gets convincingly into a pocket
      // vicinity, commit the shot and preserve the existing roll/drop animation
      // instead of demanding a pixel-perfect jaw entry.
      if(arcadePocketAt(b.x,b.z)){
        final float PX=42.0f,PZ=21.0f;
        float px,pz;
        if(Math.abs(b.x)<=3.45f){
          px=0f;pz=b.z>=0?PZ:-PZ;
        }else{
          px=b.x>=0?PX:-PX;pz=b.z>=0?PZ:-PZ;
        }
        startPocketSink(index,b,px,pz);
        return;
      }

      // Safety catch if a fast body clears the table between fixed steps.
      float ax=Math.abs(b.x),az=Math.abs(b.z);
      if(ax>MAXX+4f||az>MAXZ+4f){
        float px=Math.abs(b.x)<8f?0:(b.x>0?42f:-42f);
        float pz=b.z>0?21f:-21f;
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

    static class PredictorRailHit{
      float t,nx,nz;
      PredictorRailHit(float T,float X,float Z){t=T;nx=X;nz=Z;}
    }

    float predictorRayCircleT(float x,float z,float dx,float dz,float cx,float cz,float radius){
      float ox=x-cx,oz=z-cz;
      float b=ox*dx+oz*dz;
      float c=ox*ox+oz*oz-radius*radius;
      float disc=b*b-c;
      if(disc<0)return Float.POSITIVE_INFINITY;
      float root=(float)Math.sqrt(disc);
      float t=-b-root;
      if(t>.045f)return t;
      t=-b+root;
      return t>.045f?t:Float.POSITIVE_INFINITY;
    }

    PredictorRailHit predictorCapsuleHit(float x,float z,float dx,float dz,float[] e,float radius){
      float ax=e[0],az=e[1],bx=e[2],bz=e[3];
      float sx=bx-ax,sz=bz-az,len=(float)Math.sqrt(sx*sx+sz*sz);
      if(len<.0001f)return null;
      float tx=sx/len,tz=sz/len,nx=-tz,nz=tx;
      float d0=(x-ax)*nx+(z-az)*nz;
      float dv=dx*nx+dz*nz;
      float best=Float.POSITIVE_INFINITY,bnx=0,bnz=0;

      if(Math.abs(dv)>.00001f){
        for(int side=-1;side<=1;side+=2){
          float t=(side*radius-d0)/dv;
          if(t>.045f&&t<best){
            float px=x+dx*t,pz=z+dz*t;
            float along=(px-ax)*tx+(pz-az)*tz;
            if(along>=0&&along<=len){
              best=t;bnx=nx*side;bnz=nz*side;
            }
          }
        }
      }

      float ta=predictorRayCircleT(x,z,dx,dz,ax,az,radius);
      if(ta<best){
        float px=x+dx*ta,pz=z+dz*ta;
        float qx=px-ax,qz=pz-az,qd=(float)Math.sqrt(qx*qx+qz*qz);
        if(qd>.0001f){best=ta;bnx=qx/qd;bnz=qz/qd;}
      }
      float tb=predictorRayCircleT(x,z,dx,dz,bx,bz,radius);
      if(tb<best){
        float px=x+dx*tb,pz=z+dz*tb;
        float qx=px-bx,qz=pz-bz,qd=(float)Math.sqrt(qx*qx+qz*qz);
        if(qd>.0001f){best=tb;bnx=qx/qd;bnz=qz/qd;}
      }
      return Float.isInfinite(best)?null:new PredictorRailHit(best,bnx,bnz);
    }

    PredictorRailHit predictorRailHit(float x,float z,float dx,float dz){
      PredictorRailHit best=null;
      float radius=PHYS_R*1.002f;
      for(float[] e:predictorRails){
        PredictorRailHit h=predictorCapsuleHit(x,z,dx,dz,e,radius);
        if(h!=null&&(best==null||h.t<best.t))best=h;
      }
      return best;
    }

    boolean predictorPocketAt(float x,float z){
      return arcadePocketAt(x,z);
    }

    float predictorPocketT(float x,float z,float dx,float dz){
      // Match the game's real pocket acceptance test rather than treating the
      // table as a rectangle. Fine stepping is only used through the pocket/jaw
      // zone and keeps the visual line consistent with checkPocket().
      for(float t=.12f;t<=95f;t+=.12f){
        if(predictorPocketAt(x+dx*t,z+dz*t))return t;
      }
      return Float.POSITIVE_INFINITY;
    }

    float predictorObjectBallT(float x,float z,float dx,float dz,Ball skip){
      float best=Float.POSITIVE_INFINITY;
      float rr=PHYS_R*2.0f;
      for(int i=1;i<balls.size();i++){
        Ball b=balls.get(i);
        if(!b.active||b.sinking||b==skip)continue;
        float t=predictorRayCircleT(x,z,dx,dz,b.x,b.z,rr);
        if(t<best)best=t;
      }
      return best;
    }

    float[] predictorRailBounce(float dx,float dz,float nx,float nz){
      float vn=dx*nx+dz*nz;
      if(vn>0f){nx=-nx;nz=-nz;vn=-vn;}

      float tx=dx-vn*nx,tz=dz-vn*nz;
      float tmag=(float)Math.sqrt(tx*tx+tz*tz);

      // JBox2D mixes restitution using the larger fixture restitution (.89 here)
      // and friction around .20. Model both instead of using a perfect mirror,
      // which was the main reason bank guides departed from the real bounce.
      final float restitution=.89f;
      final float friction=.20f;
      float normalOut=-vn*restitution;
      float tangentOut=tmag;
      if(tmag>.00001f){
        float frictionLoss=friction*(1f+restitution)*(-vn);
        tangentOut=Math.max(0f,tmag-frictionLoss);
        tx*=tangentOut/tmag;tz*=tangentOut/tmag;
      }

      float ox=tx+normalOut*nx,oz=tz+normalOut*nz;
      float m=(float)Math.sqrt(ox*ox+oz*oz);
      if(m<.00001f)return new float[]{-dx,-dz};
      return new float[]{ox/m,oz/m};
    }

    void drawPredictorFreePath(float[] pv,float x,float z,float dx,float dz,int blade,int maxBanks){
      float n=(float)Math.sqrt(dx*dx+dz*dz);if(n<.0001f)return;dx/=n;dz/=n;
      for(int bank=0;bank<=maxBanks;bank++){
        PredictorRailHit rail=predictorRailHit(x,z,dx,dz);
        float pocketT=predictorPocketT(x,z,dx,dz);
        float railT=rail==null?Float.POSITIVE_INFINITY:rail.t;

        if(pocketT<railT){
          drawSaberSegment(pv,x,z,x+dx*pocketT,z+dz*pocketT,blade);
          return;
        }
        if(rail==null){
          float len=70f;
          drawSaberSegment(pv,x,z,x+dx*len,z+dz*len,blade);
          return;
        }

        float ex=x+dx*rail.t,ez=z+dz*rail.t;
        drawSaberSegment(pv,x,z,ex,ez,blade);
        float[] bounce=predictorRailBounce(dx,dz,rail.nx,rail.nz);
        dx=bounce[0];dz=bounce[1];
        x=ex+dx*.08f;z=ez+dz*.08f;
        blade=predictorAltBlade(bank+3);
      }
    }

    void drawPredictor(float[] pv){
      if(balls.isEmpty()||!balls.get(0).active)return;
      Ball cue=balls.get(0);
      float x=cue.x,z=cue.z,dx=aimX,dz=aimZ;
      float dn=(float)Math.sqrt(dx*dx+dz*dz);if(dn<.0001f)return;dx/=dn;dz/=dn;
      int selected=Math.max(0,Math.min(5,bladeIndex));

      for(int bank=0;bank<6;bank++){
        PredictorRailHit rail=predictorRailHit(x,z,dx,dz);
        float pocketT=predictorPocketT(x,z,dx,dz);
        float railT=rail==null?Float.POSITIVE_INFINITY:rail.t;
        float limit=Math.min(railT,pocketT);

        Ball hit=null;float ballT=Float.POSITIVE_INFINITY;
        float rr=PHYS_R*2.0f;
        for(int i=1;i<balls.size();i++){
          Ball b=balls.get(i);if(!b.active||b.sinking)continue;
          float t=predictorRayCircleT(x,z,dx,dz,b.x,b.z,rr);
          if(t<ballT&&t<limit){ballT=t;hit=b;}
        }

        int pathBlade=(bank==0)?selected:predictorAltBlade(bank-1);
        if(hit!=null){
          float ex=x+dx*ballT,ez=z+dz*ballT;
          drawSaberSegment(pv,x,z,ex,ez,pathBlade);

          // Exact equal-ball collision normal. The object-ball predictor then
          // traces against the same extracted Unity cushion/jaw geometry used
          // by physics, so a jaw hit is shown as a jaw hit rather than a pocket.
          float nx=hit.x-ex,nz=hit.z-ez,nd=(float)Math.sqrt(nx*nx+nz*nz);
          if(nd>.0001f){nx/=nd;nz/=nd;}
          drawPredictorFreePath(pv,hit.x,hit.z,nx,nz,predictorAltBlade(2),2);

          // Cue-ball tangent after the collision, also traced against exact rails.
          float dot=dx*nx+dz*nz,cx=dx-dot*nx,cz=dz-dot*nz;
          float cd=(float)Math.sqrt(cx*cx+cz*cz);
          if(cd>.06f){
            cx/=cd;cz/=cd;
            drawPredictorFreePath(pv,ex+cx*.08f,ez+cz*.08f,cx,cz,predictorAltBlade(4),1);
          }
          return;
        }

        if(pocketT<railT){
          drawSaberSegment(pv,x,z,x+dx*pocketT,z+dz*pocketT,pathBlade);
          return;
        }

        if(rail==null){
          float len=70f;
          drawSaberSegment(pv,x,z,x+dx*len,z+dz*len,pathBlade);
          return;
        }

        float ex=x+dx*rail.t,ez=z+dz*rail.t;
        drawSaberSegment(pv,x,z,ex,ez,pathBlade);

        // Bounce from the exact cushion/jaw normal using the same restitution /
        // friction behavior as the live JBox2D contact instead of a perfect mirror.
        float[] bounce=predictorRailBounce(dx,dz,rail.nx,rail.nz);
        dx=bounce[0];dz=bounce[1];
        x=ex+dx*.08f;z=ez+dz*.08f;
      }
    }

    void drawSaberSegment(float[] pv,float x1,float z1,float x2,float z2,int bladeTextureIndex){
      int idx=Math.max(0,Math.min(5,bladeTextureIndex));
      float dx=x2-x1,dz=z2-z1,len=(float)Math.sqrt(dx*dx+dz*dz);if(len<.02f)return;
      float angle=(float)Math.toDegrees(Math.atan2(-dz,dx));
      int texture=saberTextures[idx];
      // The predictor represents the CENTER path of a pool ball. Drawing it above
      // that height creates a perspective/parallax offset at angled camera views,
      // which made the real ball look consistently left/right of the beam.
      float width=3.65f;
      final float pathY=2.22f;

      GLES20.glDepthMask(false);GLES20.glDisable(GLES20.GL_DEPTH_TEST);

      // Soft halo.
      GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE);
      drawTexturedBlade(texture,pv,x1,z1,len,angle,width*1.65f,.30f,pathY);

      // Bright native saber core centered exactly on the physical ball path.
      GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
      drawTexturedBlade(texture,pv,x1,z1,len,angle,width,1.0f,pathY);

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
