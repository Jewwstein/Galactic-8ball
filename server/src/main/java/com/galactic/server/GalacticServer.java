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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

public class GalacticServer {
  static final ConcurrentHashMap<String, Room> rooms=new ConcurrentHashMap<>();
  static final ConcurrentHashMap<WebSocketChannel, Client> clients=new ConcurrentHashMap<>();
  static final SecureRandom RNG=new SecureRandom();
  static final ScheduledExecutorService TICKER=Executors.newSingleThreadScheduledExecutor(r->{
    Thread t=new Thread(r,"galactic-physics");t.setDaemon(true);return t;
  });
  static final UserStore USERS=new UserStore();

  public static void main(String[] args){
    int port=Integer.parseInt(System.getenv().getOrDefault("PORT","10000"));

    HttpHandler ws=Handlers.websocket(new WebSocketConnectionCallback(){
      public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel){
        Client c=new Client(channel);clients.put(channel,c);
        channel.getReceiveSetter().set(new AbstractReceiveListener(){
          protected void onFullTextMessage(WebSocketChannel ch, BufferedTextMessage message){
            handle(c,message.getData());
          }
          protected void onCloseMessage(CloseMessage cm, WebSocketChannel ch){leave(c);}
          protected void onError(WebSocketChannel ch, Throwable error){leave(c);}
        });
        channel.addCloseTask(ch->{leave(c);clients.remove(ch);});
        channel.resumeReceives();
        send(channel,"WELCOME|GALACTIC-8BALL|SERVER-AUTH|LOBBY");
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
      String[] p=msg.split("\\|",-1);
      if(p.length==0)return;
      switch(p[0]){
        case "REGISTER" -> {
          if(p.length<3){error(c,"Missing username or password");return;}
          String username=decode(p[1]),password=decode(p[2]);
          UserStore.AuthResult ar=USERS.register(username,password);
          if(!ar.ok){authFail(c,ar.message);return;}
          authenticate(c,ar.user);
        }
        case "LOGIN" -> {
          if(p.length<3){error(c,"Missing username or password");return;}
          String username=decode(p[1]),password=decode(p[2]);
          UserStore.AuthResult ar=USERS.login(username,password);
          if(!ar.ok){authFail(c,ar.message);return;}
          authenticate(c,ar.user);
        }
        case "AUTH" -> {
          if(p.length<2){authFail(c,"Saved login expired");return;}
          User u=USERS.byToken(p[1]);
          if(u==null){authFail(c,"Saved login expired");return;}
          authenticate(c,u);
        }
        case "LOGOUT" -> {
          leave(c);c.username=null;c.authToken=null;
          send(c.channel,"LOGGED_OUT");
        }
        case "LOBBY" -> {
          if(!requireAuth(c))return;
          sendLobby(c);
        }
        case "CREATE_ROOM" -> {
          if(!requireAuth(c))return;
          if(p.length<2){error(c,"Room name required");return;}
          createRoom(c,decode(p[1]));
        }
        case "JOIN_ROOM" -> {
          if(!requireAuth(c))return;
          if(p.length<2){error(c,"Room unavailable");return;}
          joinRoom(c,p[1]);
        }
        case "LEAVE_ROOM" -> {
          if(!requireAuth(c))return;
          leaveRoomOnly(c,true);
        }
        case "PING" -> send(c.channel,"PONG");
        case "ARCADE_SCORE" -> {
          if(!requireAuth(c))return;
          int score=0;try{if(p.length>1)score=Math.max(0,Math.min(999999999,Integer.parseInt(p[1])));}catch(Exception ignored){}
          final int fs=score;arcadeScores.merge(c.username,fs,Math::max);
          sendArcadeLeaderboard(c);
        }
        case "ARCADE_BOARD" -> {
          if(!requireAuth(c))return;
          sendArcadeLeaderboard(c);
        }
        case "PROFILE" -> {
          if(!requireAuth(c))return;
          try{
            if(p.length>1)c.badgeMask=Math.max(0,Integer.parseInt(p[1]));
            if(p.length>2)c.unlockMask=Math.max(0,Integer.parseInt(p[2]));
          }catch(Exception ignored){}
          Room r=roomFor(c);
          if(r!=null)r.broadcast(r.profileState());
        }
        case "CMD" -> {
          if(!requireAuth(c))return;
          Room r=roomFor(c);
          if(r!=null)r.command(c,p);
        }
      }
    }catch(Exception e){
      error(c,"Bad message");
    }
  }

  static void sendArcadeLeaderboard(Client c){
    ArrayList<Map.Entry<String,Integer>> rows=new ArrayList<>(arcadeScores.entrySet());
    rows.sort((a,b)->Integer.compare(b.getValue(),a.getValue()));
    StringBuilder out=new StringBuilder("ARCADE_BOARD");
    for(int i=0;i<Math.min(10,rows.size());i++){Map.Entry<String,Integer> e=rows.get(i);out.append("|").append(encode(e.getKey())).append(",").append(e.getValue());}
    send(c.channel,out.toString());
  }

  static void authenticate(Client c,User u){
    c.username=u.username;c.authToken=u.token;
    send(c.channel,"AUTH_OK|"+encode(u.username)+"|"+u.token);
    sendLobby(c);
  }

  static boolean requireAuth(Client c){
    if(c.username!=null&&!c.username.isEmpty())return true;
    error(c,"Please log in first");
    return false;
  }

  static void authFail(Client c,String reason){send(c.channel,"AUTH_FAIL|"+encode(reason));}
  static void error(Client c,String reason){send(c.channel,"ERROR|"+encode(reason));}

  static Room roomFor(Client c){return c.roomId==null?null:rooms.get(c.roomId);}

  static void createRoom(Client c,String rawName){
    String name=sanitizeRoomName(rawName);
    if(name.isEmpty()){error(c,"Room name required");return;}
    leaveRoomOnly(c,false);
    String id=UUID.randomUUID().toString();
    Room room=new Room(id,name,c.username);
    rooms.put(id,room);
    room.player1=c;c.roomId=id;c.player=1;
    send(c.channel,"ROOM_JOINED|"+id+"|"+encode(name)+"|1");
    send(c.channel,room.snapshot());
    room.broadcast(room.profileState());
    broadcastLobby();
  }

  static void joinRoom(Client c,String id){
    Room room=rooms.get(id);
    if(room==null){error(c,"Room no longer exists");return;}
    synchronized(room){
      if(room.player2!=null&&room.player2.channel.isOpen()){
        error(c,"Room is full");return;
      }
      leaveRoomOnly(c,false);
      room.player2=c;c.roomId=id;c.player=2;
      send(c.channel,"ROOM_JOINED|"+id+"|"+encode(room.name)+"|2");
      if(room.player1!=null)send(room.player1.channel,"PLAYER_JOINED|"+encode(c.username));
      send(c.channel,room.snapshot());
      room.broadcast(room.profileState());
    }
    broadcastLobby();
  }

  static void leave(Client c){
    leaveRoomOnly(c,false);
  }

  static void leaveRoomOnly(Client c,boolean notifySelf){
    String id=c.roomId;
    c.roomId=null;c.player=0;
    if(id==null){
      if(notifySelf)send(c.channel,"ROOM_LEFT");
      return;
    }
    Room r=rooms.get(id);
    if(r==null){
      if(notifySelf)send(c.channel,"ROOM_LEFT");
      return;
    }
    synchronized(r){
      if(r.player1==c){
        Client guest=r.player2;
        r.player1=null;r.player2=null;
        rooms.remove(id);
        if(guest!=null){
          guest.roomId=null;guest.player=0;
          send(guest.channel,"ROOM_CLOSED|"+encode("Room owner left"));
        }
      }else if(r.player2==c){
        r.player2=null;
        if(r.player1!=null){
          send(r.player1.channel,"PLAYER_LEFT|"+encode(c.username==null?"Player 2":c.username));
          send(r.player1.channel,r.profileState());
        }
      }
    }
    if(notifySelf)send(c.channel,"ROOM_LEFT");
    broadcastLobby();
  }

  static String sanitizeRoomName(String s){
    if(s==null)return "";
    s=s.trim().replaceAll("\\s+"," ");
    if(s.length()>28)s=s.substring(0,28);
    return s.replace("|","");
  }

  static void sendLobby(Client c){
    send(c.channel,lobbyMessage());
  }

  static void broadcastLobby(){
    String msg=lobbyMessage();
    for(Client c:clients.values())if(c.username!=null)send(c.channel,msg);
  }

  static String lobbyMessage(){
    ArrayList<Room> list=new ArrayList<>(rooms.values());
    list.sort(Comparator.comparingLong(r->r.createdAt));
    StringBuilder sb=new StringBuilder("LOBBY|");
    for(Room r:list){
      int count=(r.player1!=null?1:0)+(r.player2!=null?1:0);
      sb.append(r.id).append(',').append(encode(r.name)).append(',').append(encode(r.owner)).append(',').append(count).append(",2;");
    }
    return sb.toString();
  }

  static String encode(String s){
    if(s==null)s="";
    return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
  }

  static String decode(String s){
    if(s==null||s.isEmpty())return "";
    try{return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8);}
    catch(Exception e){return "";}
  }

  static void send(WebSocketChannel ch,String msg){
    if(ch==null||!ch.isOpen())return;
    WebSockets.sendText(msg,ch,null);
  }

  static class Client{
    final WebSocketChannel channel;
    volatile String roomId;
    volatile int player;
    volatile String username;
    volatile String authToken;
    volatile int badgeMask=0;
    volatile int unlockMask=0;
    Client(WebSocketChannel c){channel=c;}
  }

  static class User{
    String username,salt,hash,token;
    User(String u,String s,String h,String t){username=u;salt=s;hash=h;token=t;}
  }

  static class UserStore{
    final HashMap<String,User> users=new HashMap<>();
    final Path file;
    final String supabaseUrl;
    final String supabaseKey;
    final boolean useSupabase;
    final HttpClient http=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(8)).build();
    final ObjectMapper json=new ObjectMapper();

    UserStore(){
      String dir=System.getenv().getOrDefault("DATA_DIR","data");
      file=Paths.get(dir,"users.db");
      supabaseUrl=trimSlash(System.getenv().getOrDefault("SUPABASE_URL",""));
      supabaseKey=System.getenv().getOrDefault("SUPABASE_SERVICE_ROLE_KEY","");
      useSupabase=!supabaseUrl.isEmpty()&&!supabaseKey.isEmpty();
      load();
    }

    static class AuthResult{
      final boolean ok;final String message;final User user;
      AuthResult(boolean o,String m,User u){ok=o;message=m;user=u;}
    }

    synchronized AuthResult register(String rawUser,String password){
      String username=normalizeUsername(rawUser);
      if(username==null)return new AuthResult(false,"Username must be 3-18 letters, numbers, or underscores",null);
      if(password==null||password.length()<6)return new AuthResult(false,"Password must be at least 6 characters",null);
      String key=username.toLowerCase(Locale.US);
      if(users.containsKey(key))return new AuthResult(false,"Username already exists",null);
      try{
        byte[] salt=new byte[16];RNG.nextBytes(salt);
        byte[] hash=pbkdf(password.toCharArray(),salt);
        String token=randomToken();
        User u=new User(username,b64(salt),b64(hash),token);
        users.put(key,u);saveUser(u);
        return new AuthResult(true,"OK",u);
      }catch(Exception e){
        users.remove(key);
        System.err.println("Account create failed: "+e.getMessage());
        return new AuthResult(false,"Could not create account",null);
      }
    }

    synchronized AuthResult login(String rawUser,String password){
      String username=normalizeUsername(rawUser);
      if(username==null)return new AuthResult(false,"Invalid username or password",null);
      User u=users.get(username.toLowerCase(Locale.US));
      if(u==null)return new AuthResult(false,"Invalid username or password",null);
      try{
        byte[] got=pbkdf(password.toCharArray(),Base64.getUrlDecoder().decode(u.salt));
        byte[] expected=Base64.getUrlDecoder().decode(u.hash);
        if(!MessageDigest.isEqual(got,expected))return new AuthResult(false,"Invalid username or password",null);
        u.token=randomToken();saveUser(u);
        return new AuthResult(true,"OK",u);
      }catch(Exception e){
        return new AuthResult(false,"Invalid username or password",null);
      }
    }

    synchronized User byToken(String token){
      if(token==null||token.isEmpty())return null;
      for(User u:users.values())if(token.equals(u.token))return u;
      return null;
    }

    String normalizeUsername(String v){
      if(v==null)return null;
      v=v.trim();
      return v.matches("[A-Za-z0-9_]{3,18}")?v:null;
    }

    byte[] pbkdf(char[] password,byte[] salt)throws Exception{
      PBEKeySpec spec=new PBEKeySpec(password,salt,120000,256);
      try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
      finally{spec.clearPassword();}
    }

    String randomToken(){
      byte[] b=new byte[32];RNG.nextBytes(b);return b64(b);
    }

    String b64(byte[] b){return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}

    synchronized void load(){
      if(useSupabase){
        try{
          loadSupabase();
          System.out.println("Loaded "+users.size()+" Galactic accounts from Supabase");
          return;
        }catch(Exception e){
          System.err.println("Supabase account load failed: "+e.getMessage());
        }
      }
      loadLocal();
    }

    void loadSupabase()throws Exception{
      HttpRequest req=baseRequest(supabaseUrl+"/rest/v1/galactic_users?select=username,salt,hash,token")
        .GET().build();
      HttpResponse<String> res=http.send(req,HttpResponse.BodyHandlers.ofString());
      if(res.statusCode()<200||res.statusCode()>=300)throw new IOException("HTTP "+res.statusCode()+" "+res.body());
      JsonNode root=json.readTree(res.body());
      if(!root.isArray())return;
      for(JsonNode n:root){
        String username=n.path("username").asText("");
        if(username.isEmpty())continue;
        users.put(username.toLowerCase(Locale.US),new User(username,n.path("salt").asText(),n.path("hash").asText(),n.path("token").asText()));
      }
    }

    void loadLocal(){
      try{
        if(!Files.exists(file))return;
        for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)){
          String[] p=line.split("\t",-1);
          if(p.length<4)continue;
          String username=decode(p[0]);
          if(username.isEmpty())continue;
          users.put(username.toLowerCase(Locale.US),new User(username,p[1],p[2],p[3]));
        }
        System.out.println("Loaded "+users.size()+" Galactic accounts from local fallback "+file);
      }catch(Exception e){
        System.err.println("Local user database load failed: "+e.getMessage());
      }
    }

    synchronized void saveUser(User u)throws Exception{
      if(useSupabase){
        String body=json.createObjectNode()
          .put("username",u.username)
          .put("salt",u.salt)
          .put("hash",u.hash)
          .put("token",u.token)
          .toString();
        HttpRequest req=baseRequest(supabaseUrl+"/rest/v1/galactic_users?on_conflict=username")
          .header("Content-Type","application/json")
          .header("Prefer","resolution=merge-duplicates,return=minimal")
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();
        HttpResponse<String> res=http.send(req,HttpResponse.BodyHandlers.ofString());
        if(res.statusCode()<200||res.statusCode()>=300)throw new IOException("HTTP "+res.statusCode()+" "+res.body());
      }else{
        saveLocal();
      }
    }

    HttpRequest.Builder baseRequest(String url){
      return HttpRequest.newBuilder(URI.create(url))
        .timeout(java.time.Duration.ofSeconds(10))
        .header("apikey",supabaseKey)
        .header("Authorization","Bearer "+supabaseKey);
    }

    synchronized void saveLocal()throws IOException{
      Files.createDirectories(file.getParent());
      Path tmp=file.resolveSibling(file.getFileName()+".tmp");
      ArrayList<String> lines=new ArrayList<>();
      for(User u:users.values())lines.add(encode(u.username)+"\t"+u.salt+"\t"+u.hash+"\t"+u.token);
      Collections.sort(lines);
      Files.write(tmp,lines,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
      try{Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
      catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
    }

    String trimSlash(String v){
      if(v==null)return "";
      v=v.trim();
      while(v.endsWith("/"))v=v.substring(0,v.length()-1);
      return v;
    }
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

    final String id,name,owner;
    final long createdAt=System.currentTimeMillis();
    Client player1,player2;
    final ArrayList<Ball> balls=new ArrayList<>();
    final ArrayList<Integer> ballsSunkThisShot=new ArrayList<>();
    final int[] teamSuit={0,0};
    World world;Body railBody;
    int state=AIMING,currentTeam=1,activeShooter=1,winnerTeam=0,hiltIndex=0,bladeIndex=5;
    boolean tableOpen=true,gameOver=false,englishObjectApplied=false;
    int englishRailCooldown=0;
    float aimX=1,aimZ=0,desiredAimX=1,desiredAimZ=0,power=0,chargePullPx=0,chargePullWorld=0,englishX=0,englishY=0,sideSpin=0,topSpin=0;
    String ruleMessage="BREAK • TEAM 1";
    int tickCounter=0;

    Room(String id,String name,String owner){this.id=id;this.name=name;this.owner=owner;resetRack();}

    synchronized void tick(){
      if(state==ROLLING){
        world.step(FIXED_DT,30,12);
        if(englishRailCooldown>0)englishRailCooldown--;
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
      if(!admin&&c.player!=activeShooter){error(c,"Not active shooter");return;}
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
          case "HILT" -> {if(p.length>2)hiltIndex=Math.max(0,Math.min(12,Integer.parseInt(p[2])));}
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
      sideSpin=englishX;topSpin=englishY;englishObjectApplied=false;englishRailCooldown=0;
      cue.body.setLinearVelocity(new Vec2(aimX*speed,aimZ*speed));
      cue.body.setAwake(true);
      state=ROLLING;power=chargePullPx=chargePullWorld=0;
    }

    void resetRack(){
      balls.clear();ballsSunkThisShot.clear();
      currentTeam=1;activeShooter=1;winnerTeam=0;teamSuit[0]=teamSuit[1]=0;tableOpen=true;gameOver=false;
      state=AIMING;aimX=desiredAimX=1;aimZ=desiredAimZ=0;power=chargePullPx=chargePullWorld=englishX=englishY=sideSpin=topSpin=0;englishObjectApplied=false;englishRailCooldown=0;
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
      world.setContactListener(new org.jbox2d.callbacks.ContactListener(){
        public void beginContact(org.jbox2d.dynamics.contacts.Contact contact){}
        public void endContact(org.jbox2d.dynamics.contacts.Contact contact){}
        public void preSolve(org.jbox2d.dynamics.contacts.Contact contact,org.jbox2d.collision.Manifold oldManifold){}
        public void postSolve(org.jbox2d.dynamics.contacts.Contact contact,org.jbox2d.callbacks.ContactImpulse impulse){
          if(state!=ROLLING)return;
          Ball cue=ball(0);if(cue==null||cue.body==null)return;
          Object ua=contact.getFixtureA().getBody().getUserData(),ub=contact.getFixtureB().getBody().getUserData();
          boolean cueA=ua==cue,cueB=ub==cue;if(!cueA&&!cueB)return;
          Object otherData=cueA?ub:ua;
          Ball other=otherData instanceof Ball?(Ball)otherData:null;
          Vec2 v=cue.body.getLinearVelocity();float speed=(float)Math.sqrt(v.x*v.x+v.y*v.y);
          if(speed<.08f)return;
          if(other!=null&&!englishObjectApplied){
            englishObjectApplied=true;
            float nx=other.x-cue.x,nz=other.z-cue.z,nd=(float)Math.sqrt(nx*nx+nz*nz);
            if(nd>.001f){nx/=nd;nz/=nd;}
            float tx=v.x+nx*(topSpin*speed*.34f),tz=v.y+nz*(topSpin*speed*.34f);
            float a=(float)Math.toRadians(sideSpin*7.0f),cs=(float)Math.cos(a),sn=(float)Math.sin(a);
            cue.body.setLinearVelocity(new Vec2(tx*cs-tz*sn,tx*sn+tz*cs));
          }else if(other==null&&englishRailCooldown<=0&&Math.abs(sideSpin)>.002f){
            englishRailCooldown=12;
            float a=(float)Math.toRadians(sideSpin*6.0f),cs=(float)Math.cos(a),sn=(float)Math.sin(a);
            cue.body.setLinearVelocity(new Vec2(v.x*cs-v.y*sn,v.x*sn+v.y*cs));
          }
        }
      });
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

    boolean arcadePocketAt(float x,float z){
      final float PX=42.0f,PZ=21.0f;
      float sideDz=Math.min(Math.abs(z-PZ),Math.abs(z+PZ));
      if(Math.abs(x)<=3.45f&&sideDz<=3.45f)return true;
      float cx=x>=0?PX:-PX,cz=z>=0?PZ:-PZ,dx=x-cx,dz=z-cz;
      return dx*dx+dz*dz<=3.85f*3.85f;
    }

    void checkPocket(Ball b){
      if(!b.active||b.sinking)return;
      if(arcadePocketAt(b.x,b.z)){
        final float PX=42.0f,PZ=21.0f;
        float px,pz;
        if(Math.abs(b.x)<=3.45f){px=0f;pz=b.z>=0?PZ:-PZ;}
        else{px=b.x>=0?PX:-PX;pz=b.z>=0?PZ:-PZ;}
        sink(b,px,pz);return;
      }
      float ax=Math.abs(b.x),az=Math.abs(b.z);
      if(ax>MAXX+4||az>MAXZ+4){
        float px=Math.abs(b.x)<8?0:(b.x>0?42f:-42f),pz=b.z>0?21f:-21f;sink(b,px,pz);
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

    String profileState(){
      int b1=player1==null?0:player1.badgeMask;
      int b2=player2==null?0:player2.badgeMask;
      int u1=player1==null?0:player1.unlockMask;
      int u2=player2==null?0:player2.unlockMask;
      return "PROFILESTATE|"+b1+"|"+b2+"|"+u1+"|"+u2;
    }

    void broadcast(String msg){
      if(player1!=null)send(player1.channel,msg);
      if(player2!=null)send(player2.channel,msg);
    }
  }
}
