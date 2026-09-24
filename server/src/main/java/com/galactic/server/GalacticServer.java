package com.galactic.server;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.*;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.jbox2d.collision.shapes.CircleShape;
import org.jbox2d.collision.shapes.EdgeShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

public class GalacticServer {
  static final ConcurrentHashMap<String, Room> rooms=new ConcurrentHashMap<>();
  static final ConcurrentHashMap<WebSocketChannel, Client> clients=new ConcurrentHashMap<>();
  static final SecureRandom RNG=new SecureRandom();
  static final String CODE_CHARS="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  static final ScheduledExecutorService TICKER=Executors.newSingleThreadScheduledExecutor(r->{
    Thread t=new Thread(r,"galactic-physics");t.setDaemon(true);return t;
  });

  public static void main(String[] args){
    int port=Integer.parseInt(System.getenv().getOrDefault("PORT","10000"));

    HttpHandler ws=Handlers.websocket(new WebSocketConnectionCallback(){
      public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel){
        Client c=new Client(channel);clients.put(channel,c);
        channel.getReceiveSetter().set(new AbstractReceiveListener(){
          protected void onFullTextMessage(WebSocketChannel ch, BufferedTextMessage message){
            handle(c,message.getData());
          }
          protected void onCloseMessage(CloseMessage cm, WebSocketChannel ch){
            leave(c);
          }
          protected void onError(WebSocketChannel ch, Throwable error){
            leave(c);
          }
        });
        channel.addCloseTask(ch->leave(c));
        channel.resumeReceives();
        send(channel,"WELCOME|GALACTIC-8BALL|SERVER-AUTH");
      }
    });

    PathHandler paths=Handlers.path()
      .addExactPath("/health",ex->{
        ex.getResponseHeaders().put(io.undertow.util.Headers.CONTENT_TYPE,"text/plain");
        ex.getResponseSender().send("Galactic 8-Ball server OK");
      })
      .addPrefixPath("/ws",ws)
      .addPrefixPath("/",ex->{
        ex.getResponseHeaders().put(io.undertow.util.Headers.CONTENT_TYPE,"text/plain");
        ex.getResponseSender().send("Galactic 8-Ball authoritative multiplayer server");
      });

    Undertow.builder().addHttpListener(port,"0.0.0.0").setHandler(paths).build().start();

    TICKER.scheduleAtFixedRate(()->{
      for(Room r:rooms.values()){
        try{r.tick();}catch(Throwable t){t.printStackTrace();}
      }
    },0,4_166_667,TimeUnit.NANOSECONDS);

    System.out.println("Galactic 8-Ball server listening on "+port);
  }

  static void handle(Client c,String msg){
    try{
      String[] p=msg.split("\\|");
      if(p.length==0)return;
      switch(p[0]){
        case "CREATE" -> createRoom(c);
        case "JOIN" -> {if(p.length>1)joinRoom(c,p[1].trim().toUpperCase(Locale.US));}
        case "LEAVE" -> leave(c);
        case "PING" -> send(c.channel,"PONG");
        case "CMD" -> {
          Room r=roomFor(c);
          if(r!=null)r.command(c,p);
        }
      }
    }catch(Exception e){
      send(c.channel,"ERROR|BAD_MESSAGE");
    }
  }

  static Room roomFor(Client c){return c.roomCode==null?null:rooms.get(c.roomCode);}

  static void createRoom(Client c){
    leave(c);
    String code;
    do{code=randomCode();}while(rooms.containsKey(code));
    Room room=new Room(code);
    rooms.put(code,room);
    room.player1=c;c.roomCode=code;c.player=1;
    send(c.channel,"ROOM|"+code+"|1");
    send(c.channel,room.snapshot());
  }

  static void joinRoom(Client c,String code){
    Room room=rooms.get(code);
    if(room==null){send(c.channel,"ERROR|ROOM_NOT_FOUND");return;}
    synchronized(room){
      if(room.player2!=null&&room.player2.channel.isOpen()){
        send(c.channel,"ERROR|ROOM_FULL");return;
      }
      leave(c);
      room.player2=c;c.roomCode=code;c.player=2;
      send(c.channel,"ROOM|"+code+"|2");
      if(room.player1!=null)send(room.player1.channel,"PLAYER_JOINED|2");
      send(c.channel,room.snapshot());
    }
  }

  static void leave(Client c){
    String code=c.roomCode;
    c.roomCode=null;c.player=0;
    if(code==null)return;
    Room r=rooms.get(code);
    if(r==null)return;
    synchronized(r){
      if(r.player1==c)r.player1=null;
      if(r.player2==c)r.player2=null;
      if(r.player1==null&&r.player2==null){
        rooms.remove(code);
      }else{
        Client survivor=r.player1!=null?r.player1:r.player2;
        if(survivor!=null)send(survivor.channel,"PLAYER_LEFT");
      }
    }
  }

  static String randomCode(){
    StringBuilder s=new StringBuilder(6);
    for(int i=0;i<6;i++)s.append(CODE_CHARS.charAt(RNG.nextInt(CODE_CHARS.length())));
    return s.toString();
  }

  static void send(WebSocketChannel ch,String msg){
    if(ch==null||!ch.isOpen())return;
    WebSockets.sendText(msg,ch,null);
  }

  static class Client{
    final WebSocketChannel channel;
    volatile String roomCode;
    volatile int player;
    Client(WebSocketChannel c){channel=c;}
  }

  static class Ball{
    int index;boolean active=true,sinking=false;float x,z,vx,vz,spin;
    float qx=0,qy=0,qz=0,qw=1;
    float sinkT=0,sinkStartX,sinkStartZ,sinkX,sinkZ;
    Body body;
    Ball(int i,float x,float z){index=i;this.x=x;this.z=z;}
  }

  static class Room{
    static final int AIMING=0,SELECTING_ENGLISH=1,CHARGING=2,ROLLING=3;
    static final float FIXED_DT=1f/240f;
    static final float TTS_MASS=.375f,TTS_DRAG=.50f,TTS_ANGULAR_DRAG=.45f,TTS_DYNAMIC_FRICTION=.20f;
    static final float STOP_SPEED=.20f,ROLL_DECEL_FAST=1.60f,ROLL_DECEL_SLOW=5.25f;
    static final float R=1.192f,PHYS_R=1.1900001f,MINX=-40.808f,MAXX=40.808f,MINZ=-19.808f,MAXZ=19.808f;

    final String code;
    Client player1,player2;
    final ArrayList<Ball> balls=new ArrayList<>();
    final ArrayList<Integer> ballsSunkThisShot=new ArrayList<>();
    final int[] teamSuit={0,0};
    World world;Body railBody;
    int state=AIMING,currentTeam=1,activeShooter=1,winnerTeam=0,hiltIndex=0,bladeIndex=5;
    boolean tableOpen=true,gameOver=false;
    float aimX=1,aimZ=0,desiredAimX=1,desiredAimZ=0,power=0,chargePullPx=0,chargePullWorld=0,englishX=0,englishY=0;
    String ruleMessage="BREAK • TEAM 1";
    int tickCounter=0;

    Room(String c){code=c;resetRack();}

    synchronized void tick(){
      if(state==ROLLING){
        world.step(FIXED_DT,30,12);
        syncBodies();
        for(Ball b:balls)checkPocket(b);
        advanceSinks(FIXED_DT);
        if(allStopped()){
          resolveShotRules();
          state=AIMING;englishX=englishY=0;power=chargePullPx=chargePullWorld=0;
        }
      }else{
        advanceSinks(FIXED_DT);
      }
      tickCounter++;
      if(tickCounter%8==0)broadcast(snapshot());
    }

    synchronized void command(Client c,String[] p){
      if(c.player<=0)return;
      String op=p.length>1?p[1]:"";
      boolean admin="RACK".equals(op)||"SHOOTER".equals(op)||"TEAM".equals(op);
      if(!admin&&c.player!=activeShooter){send(c.channel,"ERROR|NOT_ACTIVE_SHOOTER");return;}
      try{
        switch(op){
          case "AIM" -> {if(p.length>2&&state==AIMING)setAim(Float.parseFloat(p[2]));}
          case "LOCK" -> {if(state==AIMING&&!gameOver){state=SELECTING_ENGLISH;power=0;englishX=englishY=0;}}
          case "ENG" -> {if(p.length>3&&state==SELECTING_ENGLISH){englishX=clamp(Float.parseFloat(p[2]),-1,1);englishY=clamp(Float.parseFloat(p[3]),-1,1);}}
          case "CONFIRM" -> {if(state==SELECTING_ENGLISH){state=CHARGING;power=chargePullPx=chargePullWorld=0;}}
          case "CANCEL" -> {if(state==SELECTING_ENGLISH){state=AIMING;power=chargePullPx=chargePullWorld=englishX=englishY=0;}}
          case "BEGIN" -> {if(state==CHARGING){power=chargePullPx=chargePullWorld=0;}}
          case "POWER" -> {
            if(p.length>3&&state==CHARGING){
              float pull=Math.max(0,Float.parseFloat(p[2]));int h=Math.max(1,Integer.parseInt(p[3]));
              chargePullPx=pull;chargePullWorld=pull/Math.max(10f,h*.030f);
              power=Math.min(100f,pull/Math.max(85f,h*.18f)*100f);
            }
          }
          case "RELEASE" -> {if(state==CHARGING)releaseShot();}
          case "HILT" -> {if(p.length>2)hiltIndex=Math.max(0,Math.min(5,Integer.parseInt(p[2])));}
          case "BLADE" -> {if(p.length>2)bladeIndex=Math.max(0,Math.min(5,Integer.parseInt(p[2])));}
          case "RACK" -> resetRack();
          case "SHOOTER" -> {activeShooter=activeShooter==1?2:1;ruleMessage="PLAYER "+activeShooter+" ACTIVE SHOOTER";}
          case "TEAM" -> {currentTeam=currentTeam==1?2:1;ruleMessage="TEAM "+currentTeam+" ACTIVE";}
        }
      }catch(Exception ignored){}
      broadcast(snapshot());
    }

    void setAim(float a){aimX=desiredAimX=(float)Math.cos(a);aimZ=desiredAimZ=(float)Math.sin(a);}

    void releaseShot(){
      if(power<5){state=AIMING;power=chargePullPx=chargePullWorld=0;return;}
      Ball cue=ball(0);
      if(!cue.active){
        cue.active=true;cue.x=-20;cue.z=0;cue.body.setActive(true);cue.body.setTransform(new Vec2(-20,0),0);
      }
      float pn=clamp(power/100f,0,1);
      float androidScale=.60f+.18f*pn*pn;
      float speed=power*1.65f*1.25f*androidScale;
      cue.spin=englishX*speed*.06f;
      cue.body.setLinearVelocity(new Vec2(aimX*speed,aimZ*speed));
      cue.body.setAwake(true);
      state=ROLLING;power=chargePullPx=chargePullWorld=0;
    }

    void resetRack(){
      balls.clear();ballsSunkThisShot.clear();
      currentTeam=1;activeShooter=1;winnerTeam=0;teamSuit[0]=teamSuit[1]=0;tableOpen=true;gameOver=false;
      state=AIMING;aimX=desiredAimX=1;aimZ=desiredAimZ=0;power=chargePullPx=chargePullWorld=englishX=englishY=0;
      ruleMessage="BREAK • TEAM 1";
      balls.add(new Ball(0,-20f,0f));
      float[][] p={
        {20.000f,0.000f},{22.090f,-1.214f},{22.092f,1.207f},{24.178f,-2.421f},{24.184f,0.004f},{24.180f,2.417f},
        {26.271f,-3.635f},{26.267f,-1.205f},{26.274f,1.216f},{26.269f,3.626f},{28.355f,-4.846f},{28.364f,-2.414f},
        {28.357f,-0.006f},{28.366f,2.427f},{28.360f,4.836f}
      };
      float pack=.987f;
      for(int i=0;i<15;i++)balls.add(new Ball(i+1,20f+(p[i][0]-20f)*pack,p[i][1]*pack));
      buildWorld();
    }

    void buildWorld(){
      world=new World(new Vec2(0,0));world.setContinuousPhysics(true);world.setWarmStarting(true);
      BodyDef rbd=new BodyDef();rbd.type=BodyType.STATIC;railBody=world.createBody(rbd);
      boolean loaded=false;
      try(BufferedReader br=new BufferedReader(new InputStreamReader(
        Objects.requireNonNull(GalacticServer.class.getResourceAsStream("/table_collider_2d.txt")),StandardCharsets.UTF_8))){
        String line;
        while((line=br.readLine())!=null){
          line=line.trim();if(line.isEmpty()||line.startsWith("#"))continue;
          String[] q=line.split("\\s+");if(q.length<4)continue;
          rail(Float.parseFloat(q[0]),Float.parseFloat(q[1]),Float.parseFloat(q[2]),Float.parseFloat(q[3]));
          loaded=true;
        }
      }catch(Exception ignored){}
      if(!loaded){
        float CX=42,CZ=21,CORNER=3.65f,SIDE=2.95f;
        rail(-CX,-CZ+CORNER,-CX,CZ-CORNER);rail(CX,-CZ+CORNER,CX,CZ-CORNER);
        rail(-CX+CORNER,-CZ,-SIDE,-CZ);rail(SIDE,-CZ,CX-CORNER,-CZ);
        rail(-CX+CORNER,CZ,-SIDE,CZ);rail(SIDE,CZ,CX-CORNER,CZ);
      }
      float density=(float)(TTS_MASS/(Math.PI*PHYS_R*PHYS_R));
      for(Ball b:balls){
        BodyDef bd=new BodyDef();bd.type=BodyType.DYNAMIC;bd.position.set(b.x,b.z);bd.linearDamping=TTS_DRAG;bd.angularDamping=TTS_ANGULAR_DRAG;bd.bullet=b.index==0;bd.allowSleep=true;
        b.body=world.createBody(bd);
        CircleShape cs=new CircleShape();cs.m_radius=PHYS_R;
        FixtureDef fd=new FixtureDef();fd.shape=cs;fd.density=density;fd.friction=TTS_DYNAMIC_FRICTION;fd.restitution=.89f;
        b.body.createFixture(fd);b.body.setUserData(b);
      }
    }

    void rail(float x1,float z1,float x2,float z2){
      EdgeShape e=new EdgeShape();e.set(new Vec2(x1,z1),new Vec2(x2,z2));
      FixtureDef fd=new FixtureDef();fd.shape=e;fd.friction=TTS_DYNAMIC_FRICTION;fd.restitution=.74f;railBody.createFixture(fd);
    }

    void syncBodies(){
      for(Ball b:balls){
        if(!b.active||b.sinking||b.body==null)continue;
        Vec2 p=b.body.getPosition(),v=b.body.getLinearVelocity();
        float ox=b.x,oz=b.z;b.x=p.x;b.z=p.y;rollQuat(b,b.x-ox,b.z-oz);
        float sp=(float)Math.sqrt(v.x*v.x+v.y*v.y);
        if(sp>0){
          float slowBlend=1f-Math.min(1f,sp/11f);
          float decel=ROLL_DECEL_FAST+(ROLL_DECEL_SLOW-ROLL_DECEL_FAST)*slowBlend;
          float ns=Math.max(0,sp-decel*FIXED_DT);
          if(ns<STOP_SPEED){b.body.setLinearVelocity(new Vec2(0,0));b.body.setAngularVelocity(0);b.vx=b.vz=0;}
          else{float q=ns/sp;b.body.setLinearVelocity(new Vec2(v.x*q,v.y*q));b.vx=v.x*q;b.vz=v.y*q;}
        }else b.vx=b.vz=0;
      }
    }

    void rollQuat(Ball b,float dx,float dz){
      float dist=(float)Math.sqrt(dx*dx+dz*dz);if(dist<1e-6)return;
      float ax=-dz/dist,az=dx/dist,half=(dist/R)*.5f,sh=(float)Math.sin(half),ch=(float)Math.cos(half);
      float rx=ax*sh,ry=0,rz=az*sh,rw=ch;
      float nx=rw*b.qx+rx*b.qw+ry*b.qz-rz*b.qy;
      float ny=rw*b.qy-rx*b.qz+ry*b.qw+rz*b.qx;
      float nz=rw*b.qz+rx*b.qy-ry*b.qx+rz*b.qw;
      float nw=rw*b.qw-rx*b.qx-ry*b.qy-rz*b.qz;
      float n=(float)Math.sqrt(nx*nx+ny*ny+nz*nz+nw*nw);
      if(n>.00001f){b.qx=nx/n;b.qy=ny/n;b.qz=nz/n;b.qw=nw/n;}
    }

    void checkPocket(Ball b){
      if(!b.active||b.sinking)return;
      float ax=Math.abs(b.x),az=Math.abs(b.z);
      if(az>MAXZ+.18f&&Math.abs(b.x)<2.35f){sink(b,0,b.z>0?MAXZ:MINZ);return;}
      if(ax>MAXX-.55f&&az>MAXZ-.55f){
        float px=b.x>0?MAXX:MINX,pz=b.z>0?MAXZ:MINZ,dx=b.x-px,dz=b.z-pz;
        if(dx*dx+dz*dz<2.35f*2.35f&&(ax>MAXX+.08f||az>MAXZ+.08f)){sink(b,px,pz);return;}
      }
      if(ax>MAXX+4||az>MAXZ+4){
        float px=Math.abs(b.x)<8?0:(b.x>0?MAXX:MINX),pz=b.z>0?MAXZ:MINZ;sink(b,px,pz);
      }
    }

    void sink(Ball b,float px,float pz){
      if(b.sinking)return;
      if(!ballsSunkThisShot.contains(b.index))ballsSunkThisShot.add(b.index);
      if(b.index==8&&!gameOver){winnerTeam=currentTeam;gameOver=true;ruleMessage="8 BALL • TEAM "+currentTeam+" WINS";}
      b.sinking=true;b.sinkT=0;b.sinkStartX=b.x;b.sinkStartZ=b.z;b.sinkX=px;b.sinkZ=pz;b.vx=b.vz=b.spin=0;
      b.body.setLinearVelocity(new Vec2(0,0));b.body.setAngularVelocity(0);b.body.setActive(false);
    }

    void advanceSinks(float dt){
      for(Ball b:balls)if(b.active&&b.sinking){
        b.sinkT=Math.min(1,b.sinkT+dt*3.2f);
        float e=1-(1-b.sinkT)*(1-b.sinkT);
        b.x=b.sinkStartX+(b.sinkX-b.sinkStartX)*e;b.z=b.sinkStartZ+(b.sinkZ-b.sinkStartZ)*e;
        if(b.sinkT>=1){
          b.sinking=false;b.sinkT=0;b.vx=b.vz=b.spin=0;
          if(b.index==0){b.x=-20;b.z=0;b.active=true;b.body.setActive(true);b.body.setTransform(new Vec2(-20,0),0);b.body.setLinearVelocity(new Vec2(0,0));}
          else b.active=false;
        }
      }
    }

    boolean allStopped(){
      for(Ball b:balls){
        if(!b.active)continue;if(b.sinking)return false;
        if(b.body!=null&&b.body.isActive()){
          Vec2 v=b.body.getLinearVelocity();if(v.x*v.x+v.y*v.y>STOP_SPEED*STOP_SPEED)return false;
        }
      }
      return true;
    }

    int suit(int idx){return idx>=1&&idx<=7?1:(idx>=9&&idx<=15?2:0);}
    boolean onTable(int idx){Ball b=ball(idx);return b!=null&&(b.active||b.sinking);}
    int remaining(int suit){int a=suit==1?1:9,b=suit==1?7:15,n=0;for(int i=a;i<=b;i++)if(onTable(i))n++;return n;}

    void resolveShotRules(){
      if(gameOver){ballsSunkThisShot.clear();return;}
      boolean scratch=false,valid=false;int teamIdx=currentTeam-1;
      for(int idx:ballsSunkThisShot){
        if(idx==0){scratch=true;continue;}if(idx==8)continue;
        int type=suit(idx);if(type==0)continue;
        if(tableOpen){tableOpen=false;teamSuit[teamIdx]=type;teamSuit[1-teamIdx]=type==1?2:1;valid=true;ruleMessage="TEAM "+currentTeam+" CLAIMED "+(type==1?"SOLIDS":"STRIPES");}
        else if(teamSuit[teamIdx]==type)valid=true;
      }
      if(scratch){currentTeam=currentTeam==1?2:1;ruleMessage="SCRATCH • TEAM "+currentTeam+" TURN";}
      else if(valid){int remain=remaining(teamSuit[currentTeam-1]);ruleMessage=remain==0?"TEAM "+currentTeam+" • 8 BALL READY":"TEAM "+currentTeam+" CONTINUES";}
      else{currentTeam=currentTeam==1?2:1;ruleMessage="TEAM "+currentTeam+" TURN";}
      activeShooter=currentTeam;ballsSunkThisShot.clear();
    }

    Ball ball(int idx){for(Ball b:balls)if(b.index==idx)return b;return null;}
    float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}

    String snapshot(){
      String rule=Base64.getEncoder().withoutPadding().encodeToString(ruleMessage.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb=new StringBuilder(1800);
      sb.append("STATE|").append(state).append('|').append(currentTeam).append('|').append(activeShooter)
        .append('|').append(hiltIndex).append('|').append(bladeIndex).append('|').append(teamSuit[0]).append('|').append(teamSuit[1])
        .append('|').append(tableOpen?1:0).append('|').append(gameOver?1:0).append('|').append(winnerTeam)
        .append('|').append(aimX).append('|').append(aimZ).append('|').append(desiredAimX).append('|').append(desiredAimZ)
        .append('|').append(power).append('|').append(chargePullPx).append('|').append(chargePullWorld)
        .append('|').append(englishX).append('|').append(englishY).append('|').append(rule).append('|');
      for(Ball b:balls){
        sb.append(b.index).append(',').append(b.active?1:0).append(',').append(b.sinking?1:0).append(',')
          .append(b.x).append(',').append(b.z).append(',').append(b.vx).append(',').append(b.vz).append(',')
          .append(b.qx).append(',').append(b.qy).append(',').append(b.qz).append(',').append(b.qw).append(',')
          .append(b.sinkT).append(',').append(b.spin).append(';');
      }
      return sb.toString();
    }

    void broadcast(String msg){
      if(player1!=null)send(player1.channel,msg);
      if(player2!=null)send(player2.channel,msg);
    }
  }
}
