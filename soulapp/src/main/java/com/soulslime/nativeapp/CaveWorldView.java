package com.soulslime.nativeapp;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.util.*;

public class CaveWorldView extends View {
    public interface Listener { void onStateChanged(CaveWorldView world); }

    public static final int M_NONE=0, M_MITE=1, M_SPIDER=2, M_LIZARD=3, M_DRAGON=4;
    public static final int S_ALIVE=0, S_DEFEATED=1, S_FRIEND=2, S_ABSORBED=3;

    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Listener listener;

    private final int slimeBody,slimeColorIndex,slimeEyes,slimeMarkings,slimeCore,slimeAlpha,slimeAura;
    private final String startingSkill;

    private final String[] roomNames={
        "AWAKENING HOLLOW",      // 0
        "OBSIDIAN PASSAGE",      // 1
        "MANA RESERVOIR",        // 2
        "FORKED GALLERY",        // 3
        "SILKEN NEST",           // 4
        "SCALE DEN",             // 5
        "CRYSTAL CRYPT",         // 6
        "ECHO SHAFT",            // 7
        "SHARD BRIDGE",          // 8
        "SUNKEN GROTTO",         // 9
        "FORGOTTEN VAULT",       // 10
        "MANA CHASM",            // 11
        "ANCIENT GATE",          // 12
        "DRAGON APPROACH",       // 13
        "DRAGON SANCTUM",        // 14
        "SUNLIT RIFT"            // 15
    };

    // A real grid/maze with multiple loops, side routes and dead-end exploration.
    private final int[] left = {-1,0,1,2,7,-1,-1,6,3,5,4,9,8,10,12,14};
    private final int[] right= {1,2,3,8,10,9,7,4,12,11,13,-1,14,-1,15,-1};
    private final int[] up   = {-1,6,7,4,-1,2,-1,-1,10,3,-1,8,13,-1,-1,-1};
    private final int[] down = {-1,-1,5,9,3,-1,1,2,11,-1,8,-1,-1,12,-1,-1};

    private final int[] mapX={0,1,2,3,3,2,1,2,4,3,4,4,5,5,6,7};
    private final int[] mapY={3,3,3,3,2,4,2,2,3,4,2,4,3,2,3,3};

    private final int[] monsterType={
        M_MITE,M_NONE,M_NONE,M_NONE,
        M_SPIDER,M_LIZARD,M_MITE,M_LIZARD,
        M_NONE,M_SPIDER,M_MITE,M_NONE,
        M_NONE,M_NONE,M_DRAGON,M_NONE
    };
    private final int[] monsterMaxHp={5,0,0,0,8,8,9,9,0,10,11,0,0,0,99,0};
    private final int[] monsterState=new int[16];
    private final int[] monsterHp=new int[16];
    private final int[] essenceMask=new int[16];
    private final boolean[] visited=new boolean[16];

    private final boolean[] crystalTaken=new boolean[5];
    private final float[][] startCrystals={{.20f,.67f},{.34f,.38f},{.49f,.71f},{.65f,.42f},{.81f,.67f}};

    private int room=0,crystals=0,essence=0,currentForm=0;
    private int shellMastery=0,silkMastery=0,scaleMastery=0;
    private boolean spiderFriend=false,lizardFriend=false;
    private boolean spiderAbsorbed=false,lizardAbsorbed=false;
    private boolean dragonBonded=false,dragonAbsorbed=false,stormHeart=false,dragonSealAwake=false;

    private float playerX=-1,playerY=-1,monsterX=-1,monsterY=-1;
    private float moveX=0,moveY=0,lastFacing=1f;
    private float monsterVx=0,monsterVy=0;
    private long lastFrame=0,transitionCooldown=0;
    private long attackStart=0,hitStart=0,absorbStart=0;
    private String message="Awakening complete. Gather the five crystals.";

    private Bitmap caveBase,caveObsidian,caveMana,caveCross,caveWeb,caveScale,caveCrypt,caveEcho,caveShard,caveGrotto,caveVault,caveChasm,caveGate,caveApproach,caveDragon,caveExit;
    private Bitmap mid,fg,crystal,essenceOrb,mite,spider,lizard,dragon,slash,impact,absorbFx,webFx,stormFx;
    private Bitmap paintedAwakening,paintedSpider,paintedLizard,paintedDragon,masterSlime,masterDragon;

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
        // Painted v7 masters reconstructed by the build pipeline from the uploaded references.
        paintedAwakening=load(R.drawable.room_painted_awakening);
        paintedSpider=load(R.drawable.room_painted_spider);
        paintedLizard=load(R.drawable.room_painted_lizard);
        paintedDragon=load(R.drawable.room_painted_dragon);
        masterSlime=load(R.drawable.slime_master);
        masterDragon=load(R.drawable.dragon_master);

        // Existing resources remain as fallbacks/effects and for non-final side creatures.
        caveBase=load(R.drawable.cave_bg);
        caveObsidian=load(R.drawable.cave_obsidian);
        caveMana=load(R.drawable.cave_mana);
        caveCross=load(R.drawable.cave_crossroads);
        caveWeb=load(R.drawable.cave_web);
        caveScale=load(R.drawable.cave_scale);
        caveCrypt=load(R.drawable.cave_crypt);
        caveEcho=load(R.drawable.cave_echo);
        caveShard=load(R.drawable.cave_shard);
        caveGrotto=load(R.drawable.cave_grotto);
        caveVault=load(R.drawable.cave_vault);
        caveChasm=load(R.drawable.cave_chasm);
        caveGate=load(R.drawable.cave_gate);
        caveApproach=load(R.drawable.cave_dragon_approach);
        caveDragon=load(R.drawable.cave_dragon);
        caveExit=load(R.drawable.cave_exit);
        mid=load(R.drawable.cave_mid); fg=load(R.drawable.cave_fg);
        crystal=load(R.drawable.crystal_blue); essenceOrb=load(R.drawable.essence_orb);
        mite=load(R.drawable.cave_mite); spider=load(R.drawable.anime_spider);
        lizard=load(R.drawable.anime_lizard); dragon=masterDragon!=null?masterDragon:load(R.drawable.ancient_dragon);
        slash=load(R.drawable.magic_slash); impact=load(R.drawable.impact_burst);
        absorbFx=load(R.drawable.absorb_ring); webFx=load(R.drawable.web_burst); stormFx=load(R.drawable.storm_burst);
    }

    private Bitmap load(int id){ return BitmapFactory.decodeResource(getResources(),id); }

    public void start(){
        room=0;crystals=0;essence=0;currentForm=0;
        shellMastery=silkMastery=scaleMastery=0;
        spiderFriend=lizardFriend=spiderAbsorbed=lizardAbsorbed=false;
        dragonBonded=dragonAbsorbed=stormHeart=dragonSealAwake=false;
        Arrays.fill(essenceMask,0);Arrays.fill(crystalTaken,false);Arrays.fill(visited,false);
        for(int i=0;i<monsterState.length;i++){
            monsterState[i]=monsterType[i]==M_NONE?S_ABSORBED:S_ALIVE;
            monsterHp[i]=monsterMaxHp[i];
        }
        visited[0]=true;
        playerX=playerY=-1;lastFrame=0;transitionCooldown=0;
        attackStart=hitStart=absorbStart=0;
        message="Awakening complete. Gather the five crystals.";
        notifyState();invalidate();
    }

    public void setMove(float x,float y){
        moveX=x;moveY=y;
        if(Math.abs(x)>.08f)lastFacing=Math.signum(x);
    }

    public void attack(){
        if(!canAttack()){
            message=monsterType[room]==M_DRAGON?"The ancient dragon's aura rejects violence.":"There is no hostile target in this chamber.";
            notifyState();return;
        }
        long now=SystemClock.uptimeMillis();
        if(now-attackStart<340)return;
        attackStart=now;
        float range=Math.max(190,getHeight()*.35f);
        if(distance(playerX,playerY,monsterX,monsterY)>range){
            message="Move closer to "+monsterLabel()+" before attacking.";
            notifyState();return;
        }

        int damage=1;
        String attackName="Magic Burst";
        if(currentForm==M_SPIDER&&spiderAbsorbed){damage=2+Math.min(1,silkMastery/2);attackName="Silk Lance";}
        if(currentForm==M_LIZARD&&lizardAbsorbed){damage=2+Math.min(1,scaleMastery/2);attackName="Scale Rend";}
        if(stormHeart){damage=Math.max(damage,3);attackName="Storm Burst";}

        monsterHp[room]=Math.max(0,monsterHp[room]-damage);
        hitStart=now;
        monsterVx=lastFacing*getWidth()*.13f;
        monsterVy=-getHeight()*.045f;

        if(monsterHp[room]<=0){
            monsterState[room]=S_DEFEATED;
            message=monsterLabel()+" defeated. Move close and ABSORB it to analyze its trait.";
        }else message=attackName+" hit for "+damage+".";
        notifyState();invalidate();
    }

    public void absorb(){
        long now=SystemClock.uptimeMillis();
        absorbStart=now;
        collectNearbyEssence(true);
        if(room==0)collectNearbyCrystals(true);

        if(room==14&&dragonBonded&&!dragonAbsorbed){
            dragonAbsorbed=true;stormHeart=true;monsterState[room]=S_ABSORBED;
            message="UNIQUE ABILITY // STORM HEART acquired. The surface route has opened.";
            notifyState();invalidate();return;
        }

        if(monsterState[room]==S_DEFEATED){
            float range=Math.max(175,getHeight()*.30f);
            if(distance(playerX,playerY,monsterX,monsterY)>range){
                message="Move closer to the defeated "+monsterLabel()+" before absorbing it.";
                notifyState();invalidate();return;
            }
            monsterState[room]=S_ABSORBED;
            int type=monsterType[room];
            if(type==M_SPIDER){
                silkMastery++;
                if(!spiderAbsorbed){
                    spiderAbsorbed=true;
                    message="ABILITY ACQUIRED // SILK THREAD. Crystal Spider morph unlocked.";
                }else message="Silk Thread strengthened. Silk mastery "+silkMastery+".";
            }else if(type==M_LIZARD){
                scaleMastery++;
                if(!lizardAbsorbed){
                    lizardAbsorbed=true;
                    message="ABILITY ACQUIRED // SCALE GUARD. Scale Lizard morph unlocked.";
                }else message="Scale Guard strengthened. Scale mastery "+scaleMastery+".";
            }else if(type==M_MITE){
                shellMastery++;
                message=shellMastery==1?"ABILITY ACQUIRED // OBSIDIAN SHELL.":"Obsidian Shell strengthened. Shell mastery "+shellMastery+".";
            }
            notifyState();invalidate();
        }
    }

    public void befriend(){
        if(!canBefriend())return;
        float range=Math.max(200,getHeight()*.36f);
        if(distance(playerX,playerY,monsterX,monsterY)>range){
            message="Move closer before trying to befriend "+monsterLabel()+".";
            notifyState();return;
        }
        if(room==14){
            dragonBonded=true;monsterState[room]=S_FRIEND;
            message="The ancient dragon accepts the soul-link and offers a core echo instead of its life.";
        }else if(room==4){
            spiderFriend=true;monsterState[room]=S_FRIEND;
            message="The Abyss Weaver accepts you. It will follow you through the cave.";
        }else if(room==5){
            lizardFriend=true;monsterState[room]=S_FRIEND;
            message="The Scale Stalker lowers its crest and joins you.";
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

    public String roomName(){return roomNames[room];}
    public String message(){return message;}
    public String formName(){
        if(currentForm==M_SPIDER)return "CRYSTAL SPIDER";
        if(currentForm==M_LIZARD)return "SCALE LIZARD";
        return "SLIME";
    }
    public String abilityText(){
        ArrayList<String> a=new ArrayList<>();
        a.add(startingSkill);
        if(shellMastery>0)a.add("OBSIDIAN SHELL"+(shellMastery>1?" "+shellMastery:""));
        if(spiderAbsorbed)a.add("SILK THREAD"+(silkMastery>1?" "+silkMastery:""));
        if(lizardAbsorbed)a.add("SCALE GUARD"+(scaleMastery>1?" "+scaleMastery:""));
        if(stormHeart)a.add("STORM HEART");
        return join(a," • ");
    }
    public String statsText(){
        String hp=(monsterType[room]!=M_NONE&&monsterType[room]!=M_DRAGON&&monsterState[room]==S_ALIVE)
            ?"  •  Enemy "+monsterHp[room]+"/"+monsterMaxHp[room]:"";
        return "Essence "+essence+"  •  Crystals "+crystals+"/5  •  Form "+formName()+hp;
    }

    public String objectiveText(){
        switch(room){
            case 0:
                if(crystals<5)return "Gather all five awakening crystals.";
                if(monsterState[0]==S_ALIVE)return "Defeat the armored Cave Mite.";
                return "The eastern passage has opened.";
            case 1:return "The cave branches above. Explore instead of following a single path.";
            case 2:return "Mana is dense here. Several routes reconnect deeper in the cave.";
            case 3:
                if(!spiderResolved()||!lizardResolved())return "Resolve both the Silken Nest and Scale Den before approaching the Ancient Gate.";
                return "Both creature branches are resolved. Find the Ancient Gate.";
            case 4:return monsterState[4]==S_ALIVE?"Abyss Weaver: BEFRIEND it, or fight and ABSORB it.":"The Silken Nest is resolved.";
            case 5:return monsterState[5]==S_ALIVE?"Scale Stalker: BEFRIEND it, or fight and ABSORB it.":"The Scale Den is resolved.";
            case 6:return "Optional crypt loop. Hunt the armored mite and gather essence.";
            case 7:return "Echo Shaft loops back toward the Mana Reservoir and Silken Nest.";
            case 8:
                if(!spiderResolved()||!lizardResolved())return "The Ancient Gate rejects you. Resolve both primary creature chambers.";
                return "The Shard Bridge leads toward the inner ruins.";
            case 9:return "Sunken Grotto: a stronger Weaver guards extra essence.";
            case 10:return "Forgotten Vault: search the upper ruins for the Dragon Approach.";
            case 11:return "Mana Chasm: a lower loop reconnects with the Shard Bridge.";
            case 12:
                if(!dragonSealAwake)return "The seal is dormant. Find the Dragon Approach above.";
                if(essence<18)return "The awakened seal requires 18 essence.";
                return "The Ancient Gate is open. Enter the Dragon Sanctum.";
            case 13:return "The dragon sigil awakens. Return to the Ancient Gate when your essence is sufficient.";
            case 14:
                if(!dragonBonded)return "Ancient Dragon encountered. Violence will fail—approach and BEFRIEND it.";
                if(!dragonAbsorbed)return "Soul-link accepted. ABSORB the offered core echo.";
                return "Storm Heart acquired. The surface passage is open.";
            case 15:return "Daylight is ahead. Exit the cave.";
        }
        return "";
    }

    public boolean canAttack(){
        return monsterType[room]!=M_NONE&&monsterType[room]!=M_DRAGON&&monsterState[room]==S_ALIVE;
    }
    public boolean canBefriend(){
        return (room==4||room==5||room==14)&&monsterState[room]==S_ALIVE;
    }
    public boolean canAbsorb(){
        if(room==14&&dragonBonded&&!dragonAbsorbed)return true;
        return monsterState[room]==S_DEFEATED;
    }
    public boolean canMorph(){return spiderAbsorbed||lizardAbsorbed;}
    public boolean canExitCave(){return room==15&&dragonAbsorbed;}
    public String absorbButtonText(){
        if(room==14&&dragonBonded&&!dragonAbsorbed)return "ABSORB\nCORE";
        if(canAbsorb())return "ABSORB\nCREATURE";
        return "ABSORB";
    }
    public String attackButtonText(){
        if(currentForm==M_SPIDER)return "SILK\nLANCE";
        if(currentForm==M_LIZARD)return "SCALE\nREND";
        if(stormHeart)return "STORM\nBURST";
        return "MAGIC\nBURST";
    }
    public String morphButtonText(){return "MORPH\n"+formName();}

    private boolean spiderResolved(){return monsterState[4]!=S_ALIVE;}
    private boolean lizardResolved(){return monsterState[5]!=S_ALIVE;}
    private void notifyState(){if(listener!=null)listener.onStateChanged(this);}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        int w=getWidth(),h=getHeight();
        if(w<=0||h<=0){postInvalidateDelayed(16);return;}
        long now=SystemClock.uptimeMillis();
        if(playerX<0){
            playerX=w*.16f;playerY=h*.65f;positionMonster(w,h);lastFrame=now;
        }
        float dt=Math.min(.033f,Math.max(0,(now-lastFrame)/1000f));
        lastFrame=now;
        updateWorld(w,h,dt,now);
        drawWorld(c,w,h,now);
        postInvalidateDelayed(16);
    }

    private void updateWorld(int w,int h,float dt,long now){
        float mag=(float)Math.sqrt(moveX*moveX+moveY*moveY);
        float power=Math.min(1f,mag);
        float nx=mag>.01f?moveX/mag:0f,ny=mag>.01f?moveY/mag:0f;
        float speed=Math.min(w,h)*(currentForm==M_LIZARD?.58f:currentForm==M_SPIDER?.52f:.49f);
        playerX+=nx*speed*power*dt;
        playerY+=ny*speed*power*dt;

        if(now>transitionCooldown){
            if(playerX>w*.955f&&right[room]>=0){
                int to=right[room];
                if(canTraverse(room,to))transitionTo(to,"right",w,h,now);
                else{playerX=w*.93f;lockedMessage(room,to);}
            }else if(playerX<w*.045f&&left[room]>=0){
                int to=left[room];
                if(canTraverse(room,to))transitionTo(to,"left",w,h,now);
                else{playerX=w*.07f;lockedMessage(room,to);}
            }else if(playerY<h*.17f&&up[room]>=0){
                int to=up[room];
                if(canTraverse(room,to))transitionTo(to,"up",w,h,now);
                else{playerY=h*.20f;lockedMessage(room,to);}
            }else if(playerY>h*.86f&&down[room]>=0){
                int to=down[room];
                if(canTraverse(room,to))transitionTo(to,"down",w,h,now);
                else{playerY=h*.83f;lockedMessage(room,to);}
            }
        }
        playerX=clamp(playerX,w*.045f,w*.955f);
        playerY=clamp(playerY,h*.17f,h*.86f);

        updateMonster(w,h,dt);
        if(room==0)collectNearbyCrystals(false);
        collectNearbyEssence(false);
    }

    private boolean canTraverse(int from,int to){
        if(from==0&&to==1)return crystals>=5&&monsterState[0]!=S_ALIVE;
        if(from==8&&to==12)return spiderResolved()&&lizardResolved();
        if(from==12&&to==14)return dragonSealAwake&&essence>=18;
        if(from==14&&to==15)return dragonAbsorbed;
        return true;
    }

    private void lockedMessage(int from,int to){
        if(from==0)message="The passage is sealed: collect five crystals and defeat the Cave Mite.";
        else if(from==8&&to==12)message="The Ancient Gate rejects you. Resolve the Silken Nest and Scale Den first.";
        else if(from==12&&to==14&&!dragonSealAwake)message="The gate is dormant. Find the Dragon Approach in the upper ruins.";
        else if(from==12&&to==14)message="The gate requires 18 essence. Current essence: "+essence+".";
        else if(from==14&&to==15)message="The surface route remains sealed until you accept the dragon's core echo.";
        notifyState();
    }

    private void transitionTo(int next,String direction,int w,int h,long now){
        room=next;visited[room]=true;transitionCooldown=now+420;monsterVx=monsterVy=0;
        if(direction.equals("right")){playerX=w*.075f;playerY=h*.63f;}
        else if(direction.equals("left")){playerX=w*.925f;playerY=h*.63f;}
        else if(direction.equals("up")){playerX=w*.50f;playerY=h*.82f;}
        else{playerX=w*.50f;playerY=h*.22f;}
        if(room==13)dragonSealAwake=true;
        positionMonster(w,h);
        message="Entered "+roomNames[room]+".";
        notifyState();
    }

    private void positionMonster(int w,int h){
        monsterX=w*(room==14?.67f:.73f);
        monsterY=h*(room==14?.50f:.58f);
    }

    private void updateMonster(int w,int h,float dt){
        if(monsterType[room]==M_NONE||monsterState[room]!=S_ALIVE||monsterType[room]==M_DRAGON)return;
        float dx=playerX-monsterX,dy=playerY-monsterY;
        float d=Math.max(1f,distance(playerX,playerY,monsterX,monsterY));
        float chase=monsterType[room]==M_MITE?.033f:monsterType[room]==M_SPIDER?.025f:.029f;
        monsterVx+=dx/d*w*chase*dt;
        monsterVy+=dy/d*h*chase*dt;
        monsterVx*=.962f;monsterVy*=.962f;
        monsterX+=monsterVx*dt;monsterY+=monsterVy*dt;
        monsterX=clamp(monsterX,w*.10f,w*.90f);
        monsterY=clamp(monsterY,h*.25f,h*.80f);
    }

    private void collectNearbyCrystals(boolean pulse){
        if(room!=0)return;
        float radius=Math.max(pulse?160:78,getHeight()*(pulse?.26f:.105f));
        boolean changed=false;
        for(int i=0;i<startCrystals.length;i++){
            if(crystalTaken[i])continue;
            float x=startCrystals[i][0]*getWidth(),y=startCrystals[i][1]*getHeight();
            if(distance(playerX,playerY,x,y)<=radius){
                crystalTaken[i]=true;crystals++;changed=true;absorbStart=SystemClock.uptimeMillis();
            }
        }
        if(changed){message="Crystal essence absorbed. "+crystals+"/5";notifyState();}
    }

    private void collectNearbyEssence(boolean pulse){
        if(room==0||room==14||room==15)return;
        float radius=Math.max(pulse?170:72,getHeight()*(pulse?.27f:.098f));
        boolean changed=false;
        for(int i=0;i<3;i++){
            int bit=1<<i;
            if((essenceMask[room]&bit)!=0)continue;
            float x=essenceX(room,i)*getWidth(),y=essenceY(room,i)*getHeight();
            if(distance(playerX,playerY,x,y)<=radius){
                essenceMask[room]|=bit;essence++;changed=true;absorbStart=SystemClock.uptimeMillis();
            }
        }
        if(changed){message="Mana essence integrated. Total essence: "+essence;notifyState();}
    }

    private float essenceX(int r,int i){
        float[][] xs={{.24f,.49f,.78f},{.20f,.55f,.82f},{.29f,.62f,.77f},{.18f,.46f,.73f}};
        return xs[r%4][i];
    }
    private float essenceY(int r,int i){
        float[][] ys={{.43f,.70f,.39f},{.69f,.37f,.66f},{.35f,.67f,.48f},{.58f,.36f,.70f}};
        return ys[(r+1)%4][i];
    }

    private void drawWorld(Canvas c,int w,int h,long now){
        drawRoomBackground(c,w,h,now);
        drawDoorHints(c,w,h);

        if(room==0){
            for(int i=0;i<startCrystals.length;i++){
                if(!crystalTaken[i])drawSpriteDepth(c,crystal,startCrystals[i][0]*w,startCrystals[i][1]*h,Math.min(w,h)*.061f,255,false,h);
            }
        }else if(room!=14&&room!=15){
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

    private Bitmap roomBitmap(){
        // Every chamber is now staged from the painted concept art rather than procedural geometry.
        switch(room){
            case 0: return paintedAwakening!=null?paintedAwakening:caveBase;
            case 1: return paintedAwakening!=null?paintedAwakening:caveObsidian;
            case 2: return paintedAwakening!=null?paintedAwakening:caveMana;
            case 3: return paintedAwakening!=null?paintedAwakening:caveCross;
            case 4: return paintedSpider!=null?paintedSpider:caveWeb;
            case 5: return paintedLizard!=null?paintedLizard:caveScale;
            case 6: return paintedAwakening!=null?paintedAwakening:caveCrypt;
            case 7: return paintedLizard!=null?paintedLizard:caveEcho;
            case 8: return paintedAwakening!=null?paintedAwakening:caveShard;
            case 9: return paintedSpider!=null?paintedSpider:caveGrotto;
            case 10:return paintedDragon!=null?paintedDragon:caveVault;
            case 11:return paintedAwakening!=null?paintedAwakening:caveChasm;
            case 12:return paintedDragon!=null?paintedDragon:caveGate;
            case 13:return paintedDragon!=null?paintedDragon:caveApproach;
            case 14:return paintedDragon!=null?paintedDragon:caveDragon;
            case 15:return paintedDragon!=null?paintedDragon:caveExit;
        }
        return paintedAwakening!=null?paintedAwakening:caveBase;
    }

    private void drawRoomBackground(Canvas c,int w,int h,long now){
        Bitmap b=roomBitmap();
        if(b==null){c.drawColor(Color.rgb(7,12,22));return;}

        // "Live illustration" camera: preserve the painting, only breathe/pan it by a few percent.
        float phase=(float)Math.sin(now/5400.0);
        float phase2=(float)Math.cos(now/6600.0);
        float zoom=1.045f+.012f*(float)Math.sin(now/4100.0);
        float dw=w*zoom,dh=h*zoom;
        float panX=phase*w*.012f + (playerX/Math.max(1f,w)-.5f)*-w*.018f;
        float panY=phase2*h*.010f + (playerY/Math.max(1f,h)-.5f)*-h*.012f;
        RectF dst=new RectF((w-dw)/2f+panX,(h-dh)/2f+panY,(w+dw)/2f+panX,(h+dh)/2f+panY);
        p.setAlpha(255);
        c.drawBitmap(b,null,dst,p);

        // Atmospheric layers make the still painting feel alive without flattening the art.
        int ambient=(room==4||room==9)?Color.rgb(224,55,79)
            :(room==5||room==7)?Color.rgb(85,226,157)
            :(room>=10&&room<=15)?Color.rgb(215,73,44)
            :Color.rgb(84,218,255);

        float pulse=.70f+.30f*(float)Math.sin(now/760.0);
        drawGlow(c,w*.52f,h*.53f,Math.min(w,h)*.34f,
            Color.argb((int)(20+24*pulse),Color.red(ambient),Color.green(ambient),Color.blue(ambient)));

        // Slow layered mist strip.
        p.setShader(new LinearGradient(0,h*.56f,0,h,
            new int[]{Color.TRANSPARENT,Color.argb(34,12,20,30),Color.argb(64,3,8,14)},
            null,Shader.TileMode.CLAMP));
        c.drawRect(0,h*.55f,w,h,p);p.setShader(null);

        // Mana embers/dust drift in front of the painting.
        for(int i=0;i<42;i++){
            float x=((i*113)+(now*.010f*(1+i%4)))%w;
            float y=((i*67)+(now*.004f*(1+i%3)))%Math.max(1,h-90)+45;
            float rr=1f+(i%4)*.6f;
            p.setColor(Color.argb(25+(i%5)*9,Color.red(ambient),Color.green(ambient),Color.blue(ambient)));
            c.drawCircle(x,y,rr,p);
        }

        // Subtle foreground vignette adds depth without drawing over the scene.
        p.setShader(new RadialGradient(w*.5f,h*.50f,Math.max(w,h)*.70f,
            new int[]{Color.TRANSPARENT,Color.argb(72,0,0,0)},new float[]{.48f,1f},Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);p.setShader(null);
    }

    private void drawPerspective(Canvas c,Bitmap b,int w,int h,float offX,float offY,int alpha,float inset){
        if(b==null){c.drawColor(Color.rgb(7,12,22));return;}
        float[] src={0,0,b.getWidth(),0,b.getWidth(),b.getHeight(),0,b.getHeight()};
        float topInset=w*inset;
        float[] dst={
            topInset+offX,h*.015f+offY,
            w-topInset+offX,h*.015f+offY,
            w+w*.035f+offX,h*1.035f+offY,
            -w*.035f+offX,h*1.035f+offY
        };
        Matrix m=new Matrix();
        m.setPolyToPoly(src,0,dst,0,4);
        p.setAlpha(alpha);c.drawBitmap(b,m,p);p.setAlpha(255);
    }

    private void drawDoorHints(Canvas c,int w,int h){
        if(right[room]>=0)drawDoorArrow(c,w*.93f,h*.56f,0,canTraverse(room,right[room]));
        if(left[room]>=0)drawDoorArrow(c,w*.07f,h*.56f,180,canTraverse(room,left[room]));
        if(up[room]>=0)drawDoorArrow(c,w*.50f,h*.20f,270,canTraverse(room,up[room]));
        if(down[room]>=0)drawDoorArrow(c,w*.50f,h*.82f,90,canTraverse(room,down[room]));
    }

    private void drawDoorArrow(Canvas c,float x,float y,float rot,boolean open){
        c.save();c.translate(x,y);c.rotate(rot);
        p.setColor(open?Color.argb(190,215,248,255):Color.argb(190,226,79,74));
        Path a=new Path();a.moveTo(-18,-24);a.lineTo(31,0);a.lineTo(-18,24);a.close();c.drawPath(a,p);
        stroke.setStrokeWidth(3);stroke.setColor(open?Color.argb(190,124,195,219):Color.argb(230,255,207,171));c.drawPath(a,stroke);
        if(!open)c.drawLine(-20,-27,25,27,stroke);
        c.restore();
    }

    private void drawMonster(Canvas c,int w,int h,long now){
        int type=monsterType[room],state=monsterState[room];
        if(type==M_NONE||state==S_ABSORBED)return;
        if(state==S_FRIEND&&type!=M_DRAGON)return;

        Bitmap b=type==M_MITE?mite:type==M_SPIDER?spider:type==M_LIZARD?lizard:(masterDragon!=null?masterDragon:dragon);
        float rr=Math.min(w,h)*(type==M_DRAGON?.30f:type==M_MITE?.10f:.125f);
        float bob=(float)Math.sin(now/(type==M_DRAGON?620.0:310.0))*rr*.030f;

        drawActorShadow(c,monsterX,monsterY,rr,h,type==M_DRAGON?.75f:.50f);

        if(type==M_DRAGON&&b!=null){
            // Live-illustration dragon: breath, head/body sway illusion, wing-scale pulse, eye glow.
            float breathe=1f+.018f*(float)Math.sin(now/520.0);
            float sway=(float)Math.sin(now/1700.0)*1.3f;
            float ds=depthScale(monsterY,h);
            float r2=rr*ds;
            c.save();
            c.translate(monsterX,monsterY+bob+(state==S_DEFEATED?r2*.20f:0));
            c.rotate(sway);
            c.scale(breathe,1f+(1f-breathe)*.55f);
            p.setAlpha(state==S_DEFEATED?125:255);
            c.drawBitmap(b,null,new RectF(-r2,-r2,r2,r2),p);
            p.setAlpha(255);
            float eyePulse=.6f+.4f*(float)Math.sin(now/180.0);
            drawGlow(c,-r2*.19f,-r2*.36f,r2*.055f,Color.argb((int)(95*eyePulse),255,75,28));
            c.restore();
        }else{
            // Side creatures keep their painted sprite but receive the same live-illustration motion language.
            float breathe=1f+.026f*(float)Math.sin(now/330.0+(room*.7));
            float ds=depthScale(monsterY,h);
            float r2=rr*ds;
            c.save();
            c.translate(monsterX,monsterY+bob+(state==S_DEFEATED?r2*.22f:0));
            c.scale(breathe,2f-breathe);
            if(state==S_DEFEATED)c.rotate(7f);
            p.setAlpha(state==S_DEFEATED?125:255);
            c.drawBitmap(b,null,new RectF(-r2,-r2,r2,r2),p);
            p.setAlpha(255);
            c.restore();
        }

        if(state==S_ALIVE&&type!=M_DRAGON){
            float hp=Math.max(0,monsterHp[room]),max=Math.max(1,monsterMaxHp[room]);
            float scale=depthScale(monsterY,h),bw=rr*1.65f*scale;
            p.setColor(Color.argb(185,7,12,19));
            c.drawRoundRect(new RectF(monsterX-bw/2,monsterY-rr*1.34f*scale,monsterX+bw/2,monsterY-rr*1.23f*scale),7,7,p);
            p.setColor(Color.rgb(205,61,64));
            c.drawRoundRect(new RectF(monsterX-bw/2,monsterY-rr*1.34f*scale,monsterX-bw/2+bw*(hp/max),monsterY-rr*1.23f*scale),7,7,p);
        }
        if(type==M_DRAGON&&dragonBonded&&!dragonAbsorbed)
            drawGlow(c,monsterX,monsterY,rr*1.80f,Color.argb(38,157,220,255));
    }

    private void drawCompanions(Canvas c,int w,int h,long now){
        float rr=Math.min(w,h)*.052f;
        if(spiderFriend){
            float x=playerX-rr*2.1f,y=playerY+rr*.9f+(float)Math.sin(now/260.0)*4;
            drawActorShadow(c,x,y,rr,h,.42f);drawSpriteDepth(c,spider,x,y,rr,220,false,h);
        }
        if(lizardFriend){
            float x=playerX+rr*2.1f,y=playerY+rr*.9f+(float)Math.sin(now/300.0+1)*4;
            drawActorShadow(c,x,y,rr,h,.42f);drawSpriteDepth(c,lizard,x,y,rr,220,false,h);
        }
    }

    private void drawPlayer(Canvas c,int w,int h,long now){
        float rr=Math.min(w,h)*.088f;
        drawActorShadow(c,playerX,playerY,rr,h,.52f);
        if(currentForm==M_SPIDER&&spider!=null)drawSpriteDepth(c,spider,playerX,playerY,rr,255,lastFacing<0,h);
        else if(currentForm==M_LIZARD&&lizard!=null)drawSpriteDepth(c,lizard,playerX,playerY,rr,255,lastFacing<0,h);
        else drawLivePaintedSlime(c,playerX,playerY,rr*depthScale(playerY,h),now);
        if(stormHeart)drawGlow(c,playerX,playerY,rr*1.9f,Color.argb(34,166,128,255));
    }

    private void drawEffects(Canvas c,int w,int h,long now){
        long aa=now-attackStart;
        if(attackStart>0&&aa<360){
            float t=aa/360f;
            Bitmap fx=currentForm==M_SPIDER&&webFx!=null?webFx:slash;
            if(stormHeart&&stormFx!=null)fx=stormFx;
            float size=Math.min(w,h)*(currentForm==M_SPIDER?.31f:stormHeart?.43f:.35f);
            float x=playerX+lastFacing*size*.65f;
            drawSpriteDepth(c,fx,x,playerY,size,(int)(245*(1-t)),lastFacing<0,h);
        }
        long ha=now-hitStart;
        if(hitStart>0&&ha<300&&impact!=null){
            float t=ha/300f,rr=Math.min(w,h)*(.12f+.08f*t);
            drawSpriteDepth(c,impact,monsterX,monsterY,rr,(int)(255*(1-t)),false,h);
        }
        long ab=now-absorbStart;
        if(absorbStart>0&&ab<650&&absorbFx!=null){
            float t=ab/650f,rr=Math.min(w,h)*(.15f+.30f*t);
            drawSpriteDepth(c,absorbFx,playerX,playerY,rr,(int)(220*(1-t)),false,h);
        }
    }

    private void drawEssence(Canvas c,float x,float y,float r,long now){
        float bob=(float)Math.sin(now/250.0)*r*.13f;
        drawGlow(c,x,y+bob,r*2.2f,Color.argb(45,86,215,255));
        drawSpriteDepth(c,essenceOrb,x,y+bob,r,245,false,getHeight());
    }

    private void drawMiniMap(Canvas c,int w,int h){
        float unit=Math.min(w,h)*.040f;
        float ox=w-unit*8.2f,oy=h*.105f+unit*.7f;
        p.setColor(Color.argb(112,2,8,17));
        c.drawRoundRect(new RectF(ox-unit*.7f,oy-unit*1.0f,ox+unit*8.0f,oy+unit*5.2f),15,15,p);

        for(int a=0;a<roomNames.length;a++){
            if(!visited[a]&&a!=room)continue;
            for(int b:new int[]{left[a],right[a],up[a],down[a]}){
                if(b<0||b<a||(!visited[b]&&b!=room))continue;
                stroke.setStrokeWidth(3);stroke.setColor(Color.argb(115,125,189,214));
                c.drawLine(ox+mapX[a]*unit,oy+mapY[a]*unit,ox+mapX[b]*unit,oy+mapY[b]*unit,stroke);
            }
        }
        for(int i=0;i<roomNames.length;i++){
            if(!visited[i]&&i!=room)continue;
            float x=ox+mapX[i]*unit,y=oy+mapY[i]*unit;
            p.setColor(i==room?Color.rgb(229,251,255):Color.argb(205,76,126,153));
            c.drawCircle(x,y,i==room?7:4.5f,p);
        }
    }

    private void drawActorShadow(Canvas c,float x,float y,float r,int h,float widthFactor){
        float s=depthScale(y,h);
        p.setColor(Color.argb(85,0,0,0));
        c.drawOval(new RectF(x-r*widthFactor*s,y+r*.73f*s,x+r*widthFactor*s,y+r*.94f*s),p);
    }

    private float depthScale(float y,int h){
        return .72f+.38f*clamp(y/Math.max(1f,h),0f,1f);
    }

    private void drawSpriteDepth(Canvas c,Bitmap b,float x,float y,float r,int alpha,boolean flip,int h){
        if(b==null)return;
        float ds=depthScale(y,h);
        float rr=r*ds;
        c.save();if(flip)c.scale(-1f,1f,x,y);
        RectF dst=new RectF(x-rr,y-rr,x+rr,y+rr);
        p.setAlpha(alpha);c.drawBitmap(b,null,dst,p);p.setAlpha(255);c.restore();
    }

    private void drawLivePaintedSlime(Canvas c,float cx,float cy,float r,long now){
        if(masterSlime==null){ drawAnimeSlime(c,cx,cy,r,now); return; }

        float mag=(float)Math.sqrt(moveX*moveX+moveY*moveY);
        float idle=(float)Math.sin(now/350.0);
        float hop=mag>.06f?Math.abs((float)Math.sin(now/118.0))*r*.10f:0f;
        float sx=1f+.025f*idle,sy=1f-.018f*idle;
        float rot=Math.max(-7f,Math.min(7f,moveX*7f));

        if(slimeBody==1){sx*=.92f;sy*=1.09f;}
        else if(slimeBody==2){sx*=1.12f;sy*=.92f;}
        else if(slimeBody==3){sx*=.98f;sy*=1.05f;rot-=2f;}

        int accent=slimeColor();
        float auraPower=new float[]{.20f,.42f,.62f,.82f,1f}[Math.max(0,Math.min(4,slimeAura))];
        drawGlow(c,cx,cy-hop,r*1.32f,Color.argb((int)(34+46*auraPower),Color.red(accent),Color.green(accent),Color.blue(accent)));

        c.save();
        c.translate(cx,cy-hop);
        c.rotate(rot);
        c.scale(sx,sy);
        p.setAlpha(new int[]{155,185,220,245}[Math.max(0,Math.min(3,slimeAlpha))]);
        p.setColorFilter(slimePaintFilter(slimeColorIndex));
        c.drawBitmap(masterSlime,null,new RectF(-r,-r,r,r),p);
        p.setColorFilter(null);p.setAlpha(255);

        // Bubble drift + core breathing retain the painting while adding live motion.
        float pulse=.68f+.32f*(float)Math.sin(now/270.0);
        drawGlow(c,0,r*.18f,r*.30f,Color.argb((int)(45*pulse),210,252,255));
        for(int i=0;i<6;i++){
            double a=now*.00065+i*.82;
            float bx=(float)Math.cos(a*1.4+i)*r*(.20f+i*.045f);
            float by=(float)Math.sin(a+i*.41)*r*.34f+r*.06f;
            p.setColor(Color.argb(50+(i%3)*18,235,253,255));
            c.drawCircle(bx,by,Math.max(1.3f,r*(.012f+(i%2)*.005f)),p);
        }

        if(slimeCore!=3){
            int cc=slimeCore==1?Color.rgb(255,222,112):slimeCore==2?Color.rgb(223,229,255):Color.rgb(142,247,255);
            drawGlow(c,0,r*.19f,r*.28f,Color.argb(86,Color.red(cc),Color.green(cc),Color.blue(cc)));
        }
        if(slimeMarkings==1){
            stroke.setStrokeWidth(Math.max(2f,r*.013f));stroke.setColor(Color.argb(125,215,251,255));
            c.drawArc(new RectF(-r*.22f,-r*.29f,r*.22f,r*.10f),205,130,false,stroke);
        }else if(slimeMarkings==3){
            stroke.setStrokeWidth(Math.max(3f,r*.017f));stroke.setColor(Color.argb(120,194,246,255));
            c.drawArc(new RectF(-r*.45f,r*.05f,r*.45f,r*.65f),205,130,false,stroke);
        }

        int eyeGlow=slimeEyes==1?Color.rgb(115,235,255):slimeEyes==2?Color.rgb(183,94,255):slimeEyes==3?Color.rgb(255,229,121):Color.rgb(190,247,255);
        float ep=.72f+.28f*(float)Math.sin(now/205.0);
        drawGlow(c,-r*.27f,-r*.18f,r*.092f,Color.argb((int)(50*ep),Color.red(eyeGlow),Color.green(eyeGlow),Color.blue(eyeGlow)));
        drawGlow(c, r*.27f,-r*.18f,r*.092f,Color.argb((int)(50*ep),Color.red(eyeGlow),Color.green(eyeGlow),Color.blue(eyeGlow)));
        c.restore();
    }

    private ColorFilter slimePaintFilter(int idx){
        float[] m;
        switch(idx){
            case 1:m=new float[]{.95f,0,.48f,0,0, 0,.60f,.22f,0,0, .28f,0,1.22f,0,0, 0,0,0,1,0};break;
            case 2:m=new float[]{1.35f,.18f,0,0,0, .15f,.55f,0,0,0, 0,.04f,.48f,0,0, 0,0,0,1,0};break;
            case 3:m=new float[]{.42f,.08f,0,0,0, .08f,1.10f,.10f,0,0, 0,.24f,.70f,0,0, 0,0,0,1,0};break;
            case 4:m=new float[]{.92f,.22f,.22f,0,18, .22f,.92f,.22f,0,18, .22f,.22f,.92f,0,18, 0,0,0,1,0};break;
            case 5:m=new float[]{.42f,0,.16f,0,0, 0,.38f,.10f,0,0, .10f,0,.55f,0,0, 0,0,0,1,0};break;
            default:m=new float[]{1,0,0,0,0, 0,1,0,0,0, 0,0,1,0,0, 0,0,0,1,0};
        }
        return new ColorMatrixColorFilter(new ColorMatrix(m));
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

    private void drawGlow(Canvas c,float x,float y,float r,int color){
        int transparent=Color.argb(0,Color.red(color),Color.green(color),Color.blue(color));
        p.setShader(new RadialGradient(x,y,r,new int[]{color,transparent},null,Shader.TileMode.CLAMP));
        c.drawCircle(x,y,r,p);p.setShader(null);
    }

    private String monsterLabel(){
        if(room==0)return "Cave Mite";
        if(room==4)return "Abyss Weaver";
        if(room==5)return "Scale Stalker";
        if(room==6)return "Crypt Mite";
        if(room==7)return "Echo Drake";
        if(room==9)return "Grotto Weaver";
        if(room==10)return "Vault Mite";
        if(room==14)return "Ancient Dragon";
        return monsterType[room]==M_SPIDER?"Abyss Weaver":monsterType[room]==M_LIZARD?"Scale Stalker":"Armored Mite";
    }

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
