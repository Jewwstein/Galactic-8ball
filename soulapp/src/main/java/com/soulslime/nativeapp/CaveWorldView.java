package com.soulslime.nativeapp;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.util.*;

public class CaveWorldView extends View {
    public interface Listener {
        void onStateChanged(CaveWorldView world);
    }

    public static final int M_NONE=0, M_MITE=1, M_SPIDER=2, M_LIZARD=3, M_DRAGON=4;
    public static final int S_ALIVE=0, S_DEFEATED=1, S_FRIEND=2, S_ABSORBED=3;

    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random=new Random(92627);
    private final Listener listener;

    private final int slimeBody, slimeColorIndex, slimeEyes, slimeMarkings, slimeCore, slimeAlpha, slimeAura;
    private final String startingSkill;

    private final String[] roomNames={
        "AWAKENING HOLLOW",
        "AZURE PASSAGE",
        "MANA CONDUIT",
        "FORKED GALLERY",
        "SILKEN NEST",
        "SCALE DEN",
        "ANCIENT RESERVOIR",
        "DRAGON SANCTUM",
        "SUNLIT RIFT"
    };

    private final int[] left = {-1,0,1,2,-1,-1,3,6,7};
    private final int[] right= {1,2,3,6,-1,-1,7,8,-1};
    private final int[] up   = {-1,-1,-1,4,-1,3,-1,-1,-1};
    private final int[] down = {-1,-1,-1,5,3,-1,-1,-1,-1};

    // 0 none, 1 mite, 2 spider, 3 lizard, 4 dragon.
    private final int[] monsterType={M_MITE,M_NONE,M_NONE,M_NONE,M_SPIDER,M_LIZARD,M_NONE,M_DRAGON,M_NONE};
    private final int[] monsterState={S_ALIVE,S_ABSORBED,S_ABSORBED,S_ABSORBED,S_ALIVE,S_ALIVE,S_ABSORBED,S_ALIVE,S_ABSORBED};
    private final int[] monsterHp={5,0,0,0,5,6,0,99,0};
    private final int[] essenceMask=new int[9];
    private final boolean[] crystalTaken=new boolean[5];
    private final float[][] startCrystals={{.20f,.66f},{.35f,.38f},{.50f,.70f},{.66f,.42f},{.82f,.66f}};

    private int room=0;
    private int crystals=0;
    private int essence=0;
    private int currentForm=0; // 0 slime, 1 spider, 2 lizard
    private boolean spiderFriend=false,lizardFriend=false;
    private boolean spiderAbsorbed=false,lizardAbsorbed=false;
    private boolean dragonBonded=false,dragonAbsorbed=false;
    private boolean stormHeart=false;

    private float playerX=-1,playerY=-1,monsterX=-1,monsterY=-1;
    private float moveX=0,moveY=0,lastFacing=1;
    private float monsterVx=0,monsterVy=0;
    private long lastFrame=0,transitionCooldown=0;
    private long attackStart=0,hitStart=0,absorbStart=0,webStart=0,stormStart=0;
    private String message="Awakening complete. Gather the five crystals.";

    private Bitmap caveBase,caveCorridor,caveMana,caveCross,caveWeb,caveScale,caveGate,caveDragon,caveExit;
    private Bitmap mid,fg,crystal,essenceOrb,mite,spider,lizard,dragon,slash,impact,absorbFx,webFx,stormFx;

    public CaveWorldView(Context c,int body,int color,int eyes,int markings,int core,int alpha,int aura,String skill,Listener l){
        super(c);
        listener=l;
        slimeBody=body;slimeColorIndex=color;slimeEyes=eyes;slimeMarkings=markings;slimeCore=core;slimeAlpha=alpha;slimeAura=aura;
        startingSkill=skill==null?"UNASSIGNED":skill;
        stroke.setStyle(Paint.Style.STROKE);
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        loadArt();
    }

    private void loadArt(){
        caveBase=load(R.drawable.cave_bg);
        caveCorridor=load(R.drawable.cave_corridor);
        caveMana=load(R.drawable.cave_mana);
        caveCross=load(R.drawable.cave_crossroads);
        caveWeb=load(R.drawable.cave_web);
        caveScale=load(R.drawable.cave_scale);
        caveGate=load(R.drawable.cave_gate);
        caveDragon=load(R.drawable.cave_dragon);
        caveExit=load(R.drawable.cave_exit);
        mid=load(R.drawable.cave_mid);
        fg=load(R.drawable.cave_fg);
        crystal=load(R.drawable.crystal_blue);
        essenceOrb=load(R.drawable.essence_orb);
        mite=load(R.drawable.cave_mite);
        spider=load(R.drawable.anime_spider);
        lizard=load(R.drawable.anime_lizard);
        dragon=load(R.drawable.ancient_dragon);
        slash=load(R.drawable.magic_slash);
        impact=load(R.drawable.impact_burst);
        absorbFx=load(R.drawable.absorb_ring);
        webFx=load(R.drawable.web_burst);
        stormFx=load(R.drawable.storm_burst);
    }

    private Bitmap load(int id){ return BitmapFactory.decodeResource(getResources(),id); }

    public void start(){
        room=0;crystals=0;essence=0;currentForm=0;
        spiderFriend=lizardFriend=spiderAbsorbed=lizardAbsorbed=dragonBonded=dragonAbsorbed=stormHeart=false;
        Arrays.fill(essenceMask,0);Arrays.fill(crystalTaken,false);
        monsterState[0]=S_ALIVE;monsterState[4]=S_ALIVE;monsterState[5]=S_ALIVE;monsterState[7]=S_ALIVE;
        monsterHp[0]=5;monsterHp[4]=5;monsterHp[5]=6;monsterHp[7]=99;
        playerX=playerY=-1;lastFrame=0;transitionCooldown=0;
        message="Awakening complete. Gather the five crystals.";
        notifyState();
        invalidate();
    }

    public void setMove(float x,float y){
        moveX=x;moveY=y;
        if(Math.abs(x)>.05f)lastFacing=Math.signum(x);
    }

    public void attack(){
        if(!hasActiveMonster()||monsterType[room]==M_DRAGON){
            message=monsterType[room]==M_DRAGON?"Its ancient aura rejects violence. Try another approach.":"No hostile target is close enough.";
            notifyState();return;
        }
        long now=SystemClock.uptimeMillis();
        if(now-attackStart<380)return;
        attackStart=now;
        float d=distance(playerX,playerY,monsterX,monsterY);
        float range=Math.max(180,getHeight()*.33f);
        if(d>range){
            message="Move closer to "+monsterLabel()+" before attacking.";
            notifyState();return;
        }
        int damage=1;
        if(currentForm==M_SPIDER&&spiderAbsorbed){ damage=2;webStart=now; }
        if(currentForm==M_LIZARD&&lizardAbsorbed){ damage=2; }
        if(stormHeart){ damage=Math.max(damage,2);stormStart=now; }
        monsterHp[room]=Math.max(0,monsterHp[room]-damage);
        hitStart=now;
        monsterVx=lastFacing*getWidth()*.14f;
        monsterVy=-getHeight()*.05f;
        if(monsterHp[room]<=0){
            monsterState[room]=S_DEFEATED;
            message=monsterLabel()+" defeated. You can ABSORB it for its trait.";
        }else{
            message=(currentForm==M_SPIDER?"Silk Shot":currentForm==M_LIZARD?"Scale Strike":"Magic Burst")+" hit for "+damage+".";
        }
        notifyState();invalidate();
    }

    public void absorb(){
        long now=SystemClock.uptimeMillis();
        absorbStart=now;
        collectNearbyEssence(true);
        if(room==0)collectNearbyCrystals(true);

        if(monsterType[room]==M_DRAGON&&dragonBonded&&!dragonAbsorbed){
            dragonAbsorbed=true;stormHeart=true;monsterState[room]=S_ABSORBED;
            message="ANALYSIS COMPLETE // Storm Heart acquired. The path to daylight is open.";
            notifyState();invalidate();return;
        }

        if(monsterState[room]==S_DEFEATED){
            float range=Math.max(160,getHeight()*.28f);
            if(distance(playerX,playerY,monsterX,monsterY)>range){
                message="Move closer to the defeated "+monsterLabel()+" before absorbing it.";
                notifyState();invalidate();return;
            }
            monsterState[room]=S_ABSORBED;
            if(monsterType[room]==M_SPIDER){
                spiderAbsorbed=true;
                message="ABILITY ACQUIRED // Silk Thread. Spider morph unlocked.";
            }else if(monsterType[room]==M_LIZARD){
                lizardAbsorbed=true;
                message="ABILITY ACQUIRED // Scale Guard. Lizard morph unlocked.";
            }else{
                message="ABILITY ACQUIRED // Violet Shell. Physical resistance increased.";
            }
            notifyState();
        }
        invalidate();
    }

    public void befriend(){
        if(!canBefriend())return;
        float range=Math.max(185,getHeight()*.34f);
        if(distance(playerX,playerY,monsterX,monsterY)>range){
            message="Move closer before trying to befriend "+monsterLabel()+".";
            notifyState();return;
        }
        int t=monsterType[room];
        if(t==M_DRAGON){
            dragonBonded=true;
            monsterState[room]=S_FRIEND;
            message="The ancient dragon accepts your soul-link. It offers a core echo to carry beyond the cave.";
        }else if(t==M_SPIDER){
            spiderFriend=true;monsterState[room]=S_FRIEND;
            message="The Crystal Weaver joins you. Its threads now mark safe paths through the cave.";
        }else if(t==M_LIZARD){
            lizardFriend=true;monsterState[room]=S_FRIEND;
            message="The Scale Runner trusts you. It will follow you through the tunnels.";
        }
        notifyState();invalidate();
    }

    public void morph(){
        ArrayList<Integer> forms=new ArrayList<>();
        forms.add(0);
        if(spiderAbsorbed)forms.add(M_SPIDER);
        if(lizardAbsorbed)forms.add(M_LIZARD);
        int idx=forms.indexOf(currentForm);
        currentForm=forms.get((idx+1)%forms.size());
        message="MIMICRY // "+formName()+" form.";
        notifyState();invalidate();
    }

    public String roomName(){ return roomNames[room]; }
    public String message(){ return message; }
    public int essence(){ return essence; }
    public int crystals(){ return crystals; }
    public int roomIndex(){ return room; }
    public String formName(){
        if(currentForm==M_SPIDER)return "CRYSTAL SPIDER";
        if(currentForm==M_LIZARD)return "SCALE LIZARD";
        return "SLIME";
    }
    public String abilityText(){
        ArrayList<String> a=new ArrayList<>();
        a.add(startingSkill);
        if(monsterState[0]==S_ABSORBED)a.add("VIOLET SHELL");
        if(spiderAbsorbed)a.add("SILK THREAD");
        if(lizardAbsorbed)a.add("SCALE GUARD");
        if(stormHeart)a.add("STORM HEART");
        return join(a," • ");
    }
    public String statsText(){
        String hp=hasActiveMonster()&&monsterType[room]!=M_DRAGON?"  Enemy "+monsterHp[room]+"/"+maxHp(monsterType[room]):"";
        return "Essence "+essence+"  •  Crystals "+crystals+"/5  •  Form "+formName()+hp;
    }
    public String objectiveText(){
        switch(room){
            case 0:
                if(crystals<5)return "Gather all five crystals. Then defeat the Cave Mite.";
                if(monsterState[0]==S_ALIVE)return "Defeat the Cave Mite to open the eastern passage.";
                return "The eastern passage is open.";
            case 1:return "Follow the mana current east. Essence strengthens your developing core.";
            case 2:return "Mana density is rising. Reach the branching gallery.";
            case 3:
                if(!spiderResolved()||!lizardResolved())return "Explore the upper and lower branches. Resolve both creature encounters.";
                return "Both branches resonate. The ancient route east has opened.";
            case 4:
                if(monsterState[4]==S_ALIVE)return "Crystal Weaver encountered: BEFRIEND it, or fight and ABSORB it.";
                return "Silken Nest resolved. Return to the Forked Gallery.";
            case 5:
                if(monsterState[5]==S_ALIVE)return "Scale Runner encountered: BEFRIEND it, or fight and ABSORB it.";
                return "Scale Den resolved. Return to the Forked Gallery.";
            case 6:
                if(essence<12)return "The ancient seal needs 12 essence. Explore and collect more mana essence.";
                return "The seal recognizes your core. Enter the Dragon Sanctum.";
            case 7:
                if(!dragonBonded)return "An ancient dragon waits. Approach it and choose BEFRIEND.";
                if(!dragonAbsorbed)return "Soul-link established. ABSORB the offered core echo.";
                return "Storm Heart acquired. The sunlit passage to the east is open.";
            case 8:return "Daylight is ahead. Leave the cave when you are ready.";
        }
        return "";
    }
    public boolean canAttack(){
        return monsterType[room]!=M_NONE && monsterType[room]!=M_DRAGON && monsterState[room]==S_ALIVE;
    }
    public boolean canBefriend(){
        int t=monsterType[room];
        return monsterState[room]==S_ALIVE&&(t==M_SPIDER||t==M_LIZARD||t==M_DRAGON);
    }
    public boolean canAbsorb(){
        if(room==7&&dragonBonded&&!dragonAbsorbed)return true;
        return monsterState[room]==S_DEFEATED;
    }
    public boolean canMorph(){ return spiderAbsorbed||lizardAbsorbed; }
    public boolean canExitCave(){ return room==8&&dragonAbsorbed; }
    public String absorbButtonText(){
        if(room==7&&dragonBonded&&!dragonAbsorbed)return "ABSORB\nCORE";
        if(canAbsorb())return "ABSORB\nCREATURE";
        return "ABSORB";
    }
    public String attackButtonText(){
        if(currentForm==M_SPIDER)return "SILK\nSHOT";
        if(currentForm==M_LIZARD)return "SCALE\nSTRIKE";
        if(stormHeart)return "STORM\nBURST";
        return "MAGIC\nBURST";
    }
    public String morphButtonText(){ return "MORPH\n"+formName(); }

    private boolean spiderResolved(){ return monsterState[4]!=S_ALIVE; }
    private boolean lizardResolved(){ return monsterState[5]!=S_ALIVE; }
    private boolean hasActiveMonster(){ return monsterType[room]!=M_NONE&&(monsterState[room]==S_ALIVE||monsterState[room]==S_DEFEATED||monsterState[room]==S_FRIEND); }

    private void notifyState(){ if(listener!=null)listener.onStateChanged(this); }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        int w=getWidth(),h=getHeight();
        if(w<=0||h<=0){postInvalidateDelayed(16);return;}
        long now=SystemClock.uptimeMillis();
        if(playerX<0){
            playerX=w*.16f;playerY=h*.63f;
            positionMonster(w,h);
            lastFrame=now;
        }
        float dt=Math.min(.033f,Math.max(0,(now-lastFrame)/1000f));
        lastFrame=now;
        updateWorld(w,h,dt,now);
        drawWorld(c,w,h,now);
        postInvalidateDelayed(16);
    }

    private void updateWorld(int w,int h,float dt,long now){
        float mag=(float)Math.sqrt(moveX*moveX+moveY*moveY);
        float nx=mag>.01f?moveX/mag:0, ny=mag>.01f?moveY/mag:0;
        float speed=Math.min(w,h)*(currentForm==M_LIZARD?0.57f:currentForm==M_SPIDER?0.50f:0.47f);
        float vx=nx*speed,vy=ny*speed;
        playerX+=vx*dt;playerY+=vy*dt;

        // Edge-to-edge maze navigation.
        if(now>transitionCooldown){
            if(playerX>w*.955f&&right[room]>=0){
                if(canTraverse(room,right[room]))transitionTo(right[room],"right",w,h,now);
                else {playerX=w*.93f;lockedMessage();}
            }else if(playerX<w*.045f&&left[room]>=0){
                transitionTo(left[room],"left",w,h,now);
            }else if(playerY<h*.17f&&up[room]>=0){
                if(canTraverse(room,up[room]))transitionTo(up[room],"up",w,h,now);
                else {playerY=h*.20f;lockedMessage();}
            }else if(playerY>h*.86f&&down[room]>=0){
                if(canTraverse(room,down[room]))transitionTo(down[room],"down",w,h,now);
                else {playerY=h*.83f;lockedMessage();}
            }
        }
        playerX=clamp(playerX,w*.045f,w*.955f);
        playerY=clamp(playerY,h*.17f,h*.86f);

        updateMonster(w,h,dt);
        if(room==0)collectNearbyCrystals(false);
        collectNearbyEssence(false);
    }

    private void lockedMessage(){
        if(room==0)message="The passage is sealed. Gather five crystals and defeat the Cave Mite.";
        else if(room==3)message="The ancient route will not open until both side chambers are resolved.";
        else if(room==6)message="The inner seal requires at least 12 essence.";
        else if(room==7)message="The exit remains sealed until the dragon's core echo joins yours.";
        notifyState();
    }

    private boolean canTraverse(int from,int to){
        if(from==0&&to==1)return crystals>=5&&monsterState[0]!=S_ALIVE;
        if(from==3&&to==6)return spiderResolved()&&lizardResolved();
        if(from==6&&to==7)return essence>=12&&spiderResolved()&&lizardResolved();
        if(from==7&&to==8)return dragonAbsorbed;
        return true;
    }

    private void transitionTo(int next,String direction,int w,int h,long now){
        room=next;transitionCooldown=now+520;monsterVx=monsterVy=0;
        if(direction.equals("right")){playerX=w*.08f;playerY=h*.61f;}
        else if(direction.equals("left")){playerX=w*.91f;playerY=h*.61f;}
        else if(direction.equals("up")){playerX=w*.50f;playerY=h*.82f;}
        else {playerX=w*.50f;playerY=h*.22f;}
        positionMonster(w,h);
        message="Entered "+roomNames[room]+".";
        notifyState();
    }

    private void positionMonster(int w,int h){
        monsterX=w*(room==7?.67f:.73f);
        monsterY=h*(room==7?.52f:.56f);
    }

    private void updateMonster(int w,int h,float dt){
        if(monsterType[room]==M_NONE||monsterState[room]!=S_ALIVE||monsterType[room]==M_DRAGON)return;
        float dx=playerX-monsterX,dy=playerY-monsterY;
        float d=Math.max(1,distance(playerX,playerY,monsterX,monsterY));
        float chase=monsterType[room]==M_MITE?.030f:monsterType[room]==M_SPIDER?.022f:.026f;
        monsterVx+=dx/d*w*chase*dt;
        monsterVy+=dy/d*h*chase*dt;
        monsterVx*=.965f;monsterVy*=.965f;
        monsterX+=monsterVx*dt;monsterY+=monsterVy*dt;
        monsterX=clamp(monsterX,w*.10f,w*.90f);
        monsterY=clamp(monsterY,h*.25f,h*.80f);
    }

    private void collectNearbyCrystals(boolean pulse){
        if(room!=0)return;
        float radius=Math.max(pulse?155:75,getHeight()*(pulse?.25f:.10f));
        boolean changed=false;
        for(int i=0;i<startCrystals.length;i++){
            if(crystalTaken[i])continue;
            float x=startCrystals[i][0]*getWidth(), y=startCrystals[i][1]*getHeight();
            if(distance(playerX,playerY,x,y)<=radius){
                crystalTaken[i]=true;crystals++;changed=true;
                absorbStart=SystemClock.uptimeMillis();
            }
        }
        if(changed){message="Crystal essence absorbed. "+crystals+"/5";notifyState();}
    }

    private void collectNearbyEssence(boolean pulse){
        if(room==0||room==8)return;
        float radius=Math.max(pulse?165:70,getHeight()*(pulse?.26f:.095f));
        boolean changed=false;
        for(int i=0;i<3;i++){
            int bit=1<<i;
            if((essenceMask[room]&bit)!=0)continue;
            float x=essenceX(room,i)*getWidth(),y=essenceY(room,i)*getHeight();
            if(distance(playerX,playerY,x,y)<=radius){
                essenceMask[room]|=bit;essence++;changed=true;
                absorbStart=SystemClock.uptimeMillis();
            }
        }
        if(changed){message="Mana essence integrated. Total essence: "+essence;notifyState();}
    }

    private float essenceX(int room,int i){
        float[][] xs={{.25f,.50f,.76f},{.20f,.52f,.82f},{.26f,.62f,.78f}};
        return xs[room%3][i];
    }
    private float essenceY(int room,int i){
        float[][] ys={{.43f,.70f,.38f},{.68f,.37f,.66f},{.35f,.66f,.48f}};
        return ys[(room+1)%3][i];
    }

    private void drawWorld(Canvas c,int w,int h,long now){
        drawRoomBackground(c,w,h,now);
        drawDoorHints(c,w,h);

        if(room==0){
            for(int i=0;i<startCrystals.length;i++){
                if(!crystalTaken[i])drawSprite(c,crystal,startCrystals[i][0]*w,startCrystals[i][1]*h,Math.min(w,h)*.060f,255);
            }
        }else if(room!=8){
            for(int i=0;i<3;i++){
                if((essenceMask[room]&(1<<i))==0)drawEssence(c,essenceX(room,i)*w,essenceY(room,i)*h,Math.min(w,h)*.038f,now+i*120);
            }
        }

        drawMonster(c,w,h,now);
        drawCompanions(c,w,h,now);
        drawPlayer(c,w,h,now);
        drawEffects(c,w,h,now);
        drawMiniMap(c,w,h);
    }

    private void drawRoomBackground(Canvas c,int w,int h,long now){
        Bitmap b=caveBase;
        if(room==1)b=caveCorridor;
        else if(room==2)b=caveMana;
        else if(room==3)b=caveCross;
        else if(room==4)b=caveWeb;
        else if(room==5)b=caveScale;
        else if(room==6)b=caveGate;
        else if(room==7)b=caveDragon;
        else if(room==8)b=caveExit;
        if(b==null)b=caveBase;
        drawCover(c,b,w,h,0,0,255);
        if(mid!=null&&room!=7&&room!=8)drawCover(c,mid,w,h,(playerX/w-.5f)*-18f,(playerY/h-.5f)*-7f,210);

        float pulse=.72f+.28f*(float)Math.sin(now/850.0);
        int glow=room==4?Color.rgb(194,137,255):room==5?Color.rgb(107,245,173):room==7?Color.rgb(167,112,255):Color.rgb(106,220,255);
        drawGlow(c,w*.54f,h*.45f,Math.min(w,h)*.30f,withAlpha(glow,(int)(24*pulse)));
        if(fg!=null)drawCover(c,fg,w,h,(playerX/w-.5f)*-28f,(playerY/h-.5f)*-10f,225);
    }

    private void drawDoorHints(Canvas c,int w,int h){
        stroke.setStrokeWidth(4);
        if(right[room]>=0)drawDoorArrow(c,w*.925f,h*.53f,0,canTraverse(room,right[room]));
        if(left[room]>=0)drawDoorArrow(c,w*.075f,h*.53f,180,true);
        if(up[room]>=0)drawDoorArrow(c,w*.50f,h*.215f,270,canTraverse(room,up[room]));
        if(down[room]>=0)drawDoorArrow(c,w*.50f,h*.815f,90,canTraverse(room,down[room]));
    }

    private void drawDoorArrow(Canvas c,float x,float y,float rot,boolean open){
        c.save();c.translate(x,y);c.rotate(rot);
        p.setColor(open?Color.argb(205,180,250,255):Color.argb(180,245,116,130));
        Path a=new Path();a.moveTo(-18,-25);a.lineTo(30,0);a.lineTo(-18,25);a.close();c.drawPath(a,p);
        if(!open){stroke.setColor(Color.argb(220,255,205,210));stroke.setStrokeWidth(5);c.drawLine(-20,-28,25,28,stroke);}
        c.restore();
    }

    private void drawMonster(Canvas c,int w,int h,long now){
        int type=monsterType[room];
        int state=monsterState[room];
        if(type==M_NONE||state==S_ABSORBED)return;
        if(state==S_FRIEND&&type!=M_DRAGON)return; // companion is drawn near player
        Bitmap b=type==M_MITE?mite:type==M_SPIDER?spider:type==M_LIZARD?lizard:dragon;
        float rr=Math.min(w,h)*(type==M_DRAGON?.27f:type==M_MITE?.095f:.115f);
        if(state==S_DEFEATED){
            p.setAlpha(135);drawSprite(c,b,monsterX,monsterY+rr*.25f,rr,135);p.setAlpha(255);
        }else{
            float bob=(float)Math.sin(now/(type==M_DRAGON?520.0:290.0))*rr*.055f;
            drawSprite(c,b,monsterX,monsterY+bob,rr,255);
        }

        if(state==S_ALIVE&&type!=M_DRAGON){
            float max=maxHp(type);
            float hp=Math.max(0,monsterHp[room]);
            float bw=rr*1.55f;
            p.setColor(Color.argb(180,9,16,28));c.drawRoundRect(new RectF(monsterX-bw/2,monsterY-rr*1.25f,monsterX+bw/2,monsterY-rr*1.15f),8,8,p);
            p.setColor(Color.rgb(244,103,121));c.drawRoundRect(new RectF(monsterX-bw/2,monsterY-rr*1.25f,monsterX-bw/2+bw*(hp/max),monsterY-rr*1.15f),8,8,p);
        }
        if(type==M_DRAGON&&dragonBonded&&!dragonAbsorbed){
            drawGlow(c,monsterX,monsterY,rr*1.7f,Color.argb(35,154,225,255));
        }
    }

    private void drawCompanions(Canvas c,int w,int h,long now){
        float rr=Math.min(w,h)*.050f;
        if(spiderFriend)drawSprite(c,spider,playerX-rr*2.0f,playerY+rr*.9f+(float)Math.sin(now/260.0)*5,rr,225);
        if(lizardFriend)drawSprite(c,lizard,playerX+rr*2.0f,playerY+rr*.9f+(float)Math.sin(now/300.0+1)*5,rr,225);
    }

    private void drawPlayer(Canvas c,int w,int h,long now){
        float rr=Math.min(w,h)*.085f;
        if(currentForm==M_SPIDER&&spider!=null){
            drawGlow(c,playerX,playerY,rr*1.65f,Color.argb(45,148,215,255));
            drawSprite(c,spider,playerX,playerY,rr,255);
        }else if(currentForm==M_LIZARD&&lizard!=null){
            drawGlow(c,playerX,playerY,rr*1.65f,Color.argb(45,96,255,174));
            drawSprite(c,lizard,playerX,playerY,rr,255);
        }else drawAnimeSlime(c,playerX,playerY,rr,now);
        if(stormHeart)drawGlow(c,playerX,playerY,rr*1.8f,Color.argb(34,170,132,255));
    }

    private void drawEffects(Canvas c,int w,int h,long now){
        long aa=now-attackStart;
        if(attackStart>0&&aa<360){
            float t=aa/360f;
            Bitmap fx=currentForm==M_SPIDER&&webFx!=null?webFx:slash;
            if(stormHeart&&stormFx!=null)fx=stormFx;
            float size=Math.min(w,h)*(currentForm==M_SPIDER?.30f:stormHeart?.42f:.34f);
            float x=playerX+lastFacing*size*.65f;
            drawSpriteAlpha(c,fx,x,playerY,size,(int)(245*(1-t)),lastFacing<0);
        }
        long ha=now-hitStart;
        if(hitStart>0&&ha<300&&impact!=null){
            float t=ha/300f,rr=Math.min(w,h)*(.12f+.08f*t);
            drawSpriteAlpha(c,impact,monsterX,monsterY,rr,(int)(255*(1-t)),false);
        }
        long ab=now-absorbStart;
        if(absorbStart>0&&ab<650&&absorbFx!=null){
            float t=ab/650f,rr=Math.min(w,h)*(.15f+.30f*t);
            drawSpriteAlpha(c,absorbFx,playerX,playerY,rr,(int)(220*(1-t)),false);
        }
    }

    private void drawEssence(Canvas c,float x,float y,float r,long now){
        float bob=(float)Math.sin(now/250.0)*r*.14f;
        drawGlow(c,x,y+bob,r*2.2f,Color.argb(48,94,230,255));
        drawSprite(c,essenceOrb,x,y+bob,r,245);
    }

    private void drawMiniMap(Canvas c,int w,int h){
        float ox=w*.755f,oy=h*.085f,unit=Math.min(w,h)*.052f;
        p.setColor(Color.argb(120,3,12,24));c.drawRoundRect(new RectF(ox-unit*2.0f,oy-unit*.7f,ox+unit*4.1f,oy+unit*4.6f),18,18,p);
        int[][] pos={{0,2},{1,2},{2,2},{3,2},{3,1},{3,3},{4,2},{5,2},{6,2}};
        for(int a=0;a<roomNames.length;a++){
            int[] pa=pos[a];
            for(int b:new int[]{left[a],right[a],up[a],down[a]}){
                if(b<0||b<a)continue;
                int[] pb=pos[b];
                stroke.setStrokeWidth(4);stroke.setColor(Color.argb(120,132,210,238));
                c.drawLine(ox+pa[0]*unit,oy+pa[1]*unit,ox+pb[0]*unit,oy+pb[1]*unit,stroke);
            }
        }
        for(int i=0;i<pos.length;i++){
            float x=ox+pos[i][0]*unit,y=oy+pos[i][1]*unit;
            p.setColor(i==room?Color.rgb(215,253,255):Color.argb(185,78,141,177));
            c.drawCircle(x,y,i==room?9:6,p);
        }
    }

    private void drawAnimeSlime(Canvas c,float cx,float cy,float r,long now){
        float sx=1f+.022f*(float)Math.sin(now/360.0),sy=2f-sx;
        c.save();c.translate(cx,cy);c.scale(sx,sy);
        Path shape=slimePath(r);
        int base=slimeColor(),light=blend(base,Color.WHITE,.58f),dark=blend(base,Color.rgb(8,25,53),.45f);
        stroke.setStrokeWidth(Math.max(5f,r*.055f));stroke.setColor(Color.argb(235,17,39,72));c.drawPath(shape,stroke);
        int a=new int[]{165,190,220,242}[Math.max(0,Math.min(3,slimeAlpha))];
        p.setShader(new LinearGradient(-r,-r,r,r,new int[]{withAlpha(light,a),withAlpha(base,a),withAlpha(dark,a)},new float[]{0,.56f,1},Shader.TileMode.CLAMP));
        c.drawPath(shape,p);p.setShader(null);
        c.save();c.clipPath(shape);
        p.setColor(Color.argb(145,255,255,255));c.drawOval(new RectF(-r*.57f,-r*.68f,r*.08f,-r*.35f),p);
        p.setColor(Color.argb(55,4,18,44));c.drawOval(new RectF(-r*.15f,r*.10f,r*.85f,r*.85f),p);
        c.restore();
        drawSlimeEyes(c,r);
        stroke.setStrokeWidth(Math.max(2.5f,r*.024f));stroke.setColor(Color.argb(170,16,31,55));
        c.drawArc(new RectF(-r*.13f,r*.07f,r*.13f,r*.23f),15,150,false,stroke);
        if(slimeCore!=3){
            p.setColor(Color.rgb(142,245,255));c.drawCircle(0,r*.38f,r*.095f,p);
            stroke.setStrokeWidth(3);stroke.setColor(Color.WHITE);c.drawCircle(0,r*.38f,r*.095f,stroke);
        }
        c.restore();
    }

    private Path slimePath(float r){
        Path q=new Path();
        if(slimeBody==1){
            q.moveTo(0,-r*.95f);q.cubicTo(r*.30f,-r*.62f,r*.72f,-r*.18f,r*.74f,r*.30f);
            q.cubicTo(r*.78f,r*.82f,r*.38f,r,0,r);q.cubicTo(-r*.38f,r,-r*.78f,r*.82f,-r*.74f,r*.30f);
            q.cubicTo(-r*.72f,-r*.18f,-r*.30f,-r*.62f,0,-r*.95f);
        }else{
            float wide=slimeBody==2?.88f:.74f;
            q.moveTo(-r*wide,r*.46f);q.cubicTo(-r*.86f,-r*.28f,-r*.46f,-r*.82f,0,-r*.82f);
            q.cubicTo(r*.46f,-r*.82f,r*.86f,-r*.28f,r*wide,r*.46f);
            q.cubicTo(r*.64f,r*.92f,-r*.64f,r*.92f,-r*wide,r*.46f);
        }
        q.close();return q;
    }

    private void drawSlimeEyes(Canvas c,float r){
        float y=-r*.12f,dx=r*.25f;
        if(slimeEyes==1){
            p.setColor(Color.rgb(8,18,37));
            Path l=new Path();l.moveTo(-dx-r*.14f,y-r*.04f);l.lineTo(-dx+r*.13f,y-r*.11f);l.lineTo(-dx+r*.08f,y+r*.12f);l.lineTo(-dx-r*.12f,y+r*.08f);l.close();c.drawPath(l,p);
            Path rr=new Path();rr.moveTo(dx+r*.14f,y-r*.04f);rr.lineTo(dx-r*.13f,y-r*.11f);rr.lineTo(dx-r*.08f,y+r*.12f);rr.lineTo(dx+r*.12f,y+r*.08f);rr.close();c.drawPath(rr,p);
        }else{
            p.setColor(Color.rgb(8,18,37));
            c.drawOval(new RectF(-dx-r*.115f,y-r*.165f,-dx+r*.115f,y+r*.165f),p);
            c.drawOval(new RectF(dx-r*.115f,y-r*.165f,dx+r*.115f,y+r*.165f),p);
            p.setColor(Color.rgb(225,253,255));
            c.drawCircle(-dx-r*.035f,y-r*.060f,r*.038f,p);c.drawCircle(dx-r*.035f,y-r*.060f,r*.038f,p);
        }
    }

    private int slimeColor(){
        int[] colors={Color.rgb(44,184,255),Color.rgb(149,87,255),Color.rgb(255,91,56),Color.rgb(63,214,133),Color.rgb(222,241,250),Color.rgb(50,42,78)};
        return colors[Math.max(0,Math.min(colors.length-1,slimeColorIndex))];
    }

    private void drawCover(Canvas c,Bitmap b,int w,int h,float offX,float offY,int alpha){
        if(b==null){c.drawColor(Color.rgb(8,18,36));return;}
        float s=Math.max(w/(float)b.getWidth(),h/(float)b.getHeight());
        float dw=b.getWidth()*s,dh=b.getHeight()*s;
        RectF dst=new RectF((w-dw)/2f+offX,(h-dh)/2f+offY,(w+dw)/2f+offX,(h+dh)/2f+offY);
        p.setAlpha(alpha);c.drawBitmap(b,null,dst,p);p.setAlpha(255);
    }

    private void drawSprite(Canvas c,Bitmap b,float x,float y,float r,int alpha){
        drawSpriteAlpha(c,b,x,y,r,alpha,false);
    }
    private void drawSpriteAlpha(Canvas c,Bitmap b,float x,float y,float r,int alpha,boolean flip){
        if(b==null)return;
        c.save();if(flip)c.scale(-1,1,x,y);
        RectF dst=new RectF(x-r,y-r,x+r,y+r);
        p.setAlpha(alpha);c.drawBitmap(b,null,dst,p);p.setAlpha(255);c.restore();
    }

    private void drawGlow(Canvas c,float x,float y,float r,int color){
        int transparent=Color.argb(0,Color.red(color),Color.green(color),Color.blue(color));
        p.setShader(new RadialGradient(x,y,r,new int[]{color,transparent},null,Shader.TileMode.CLAMP));
        c.drawCircle(x,y,r,p);p.setShader(null);
    }

    private int maxHp(int type){return type==M_MITE?5:type==M_SPIDER?5:type==M_LIZARD?6:99;}
    private String monsterLabel(){return monsterType[room]==M_MITE?"Cave Mite":monsterType[room]==M_SPIDER?"Crystal Weaver":monsterType[room]==M_LIZARD?"Scale Runner":"Ancient Dragon";}
    private float distance(float ax,float ay,float bx,float by){float dx=ax-bx,dy=ay-by;return (float)Math.sqrt(dx*dx+dy*dy);}
    private float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    private int withAlpha(int color,int a){return Color.argb(a,Color.red(color),Color.green(color),Color.blue(color));}
    private int blend(int a,int b,float t){
        return Color.rgb((int)(Color.red(a)*(1-t)+Color.red(b)*t),(int)(Color.green(a)*(1-t)+Color.green(b)*t),(int)(Color.blue(a)*(1-t)+Color.blue(b)*t));
    }
    private String join(List<String> parts,String sep){
        StringBuilder sb=new StringBuilder();
        for(String x:parts){if(x==null||x.isEmpty())continue;if(sb.length()>0)sb.append(sep);sb.append(x);}
        return sb.toString();
    }
}
