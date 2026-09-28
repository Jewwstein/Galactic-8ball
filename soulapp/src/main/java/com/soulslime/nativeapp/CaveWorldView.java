package com.soulslime.nativeapp;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.util.*;

public class CaveWorldView extends View {
    public interface Listener { void onStateChanged(CaveWorldView world); }

    public static final int FORM_SLIME=0, FORM_MITE=1, FORM_SPIDER=2;
    private static final int ALIVE=0, DEFEATED=1, ABSORBED=2;

    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Listener listener;

    private final int slimeBody,slimeColorIndex,slimeEyes,slimeMarkings,slimeCore,slimeAlpha,slimeAura;
    private final String startingSkill;

    private final String[] roomNames={
        "AWAKENING HOLLOW",
        "CRYSTAL DESCENT",
        "FORKED GALLERY",
        "SILKEN NEST"
    };

    private Bitmap roomStart,roomCorridor,roomJunction,roomSpider;
    private Bitmap slimeMaster,miteMaster,spiderMaster;
    private Bitmap crystalSprite,essenceSprite,slashFx,impactFx,absorbFx,webFx;

    private int room=0;
    private int essence=0;
    private int currentForm=FORM_SLIME;
    private int miteState=ALIVE,spiderState=ALIVE;
    private int miteHp=5,spiderHp=8;
    private boolean miteAbsorbed=false,spiderAbsorbed=false;
    private boolean externalSlime3D=false;
    private boolean[] roomEssenceTaken=new boolean[5];

    private float playerX=-1,playerY=-1,moveX=0,moveY=0,lastFacing=1f;
    private float enemyX=-1,enemyY=-1,enemyDir=1f;
    private long lastFrame=0,transitionCooldown=0;
    private long attackStart=0,hitStart=0,absorbStart=0;
    private String message="Awakening stabilized. Collect the nearby mana essence.";

    // Walkable areas are hand-mapped to the painted floor in each background.
    private static final float[][] WALK0={
        {.055f,.17f},{.31f,.16f},{.43f,.29f},{.74f,.27f},{.94f,.40f},
        {.94f,.82f},{.77f,.90f},{.24f,.90f},{.07f,.68f}
    };
    private static final float[][] WALK1={
        {.42f,.10f},{.70f,.10f},{.78f,.22f},{.67f,.37f},{.79f,.50f},
        {.72f,.67f},{.58f,.90f},{.25f,.90f},{.30f,.72f},{.44f,.55f},
        {.36f,.40f},{.28f,.26f}
    };
    private static final float[][] WALK2={
        {.18f,.93f},{.82f,.93f},{.86f,.66f},{.73f,.52f},{.91f,.35f},
        {.83f,.10f},{.56f,.10f},{.50f,.34f},{.43f,.10f},{.18f,.10f},
        {.10f,.36f},{.27f,.54f},{.13f,.68f}
    };
    private static final float[][] WALK3={
        {.28f,.95f},{.72f,.95f},{.80f,.76f},{.90f,.63f},{.91f,.37f},
        {.79f,.25f},{.58f,.28f},{.48f,.36f},{.33f,.29f},{.17f,.39f},
        {.12f,.62f},{.22f,.76f}
    };

    private final float[][] startEssence={
        {.20f,.31f},{.34f,.55f},{.49f,.70f},{.66f,.58f},{.80f,.46f}
    };
    private final float[][] corridorEssence={
        {.54f,.24f},{.57f,.43f},{.55f,.63f},{.43f,.78f},{.61f,.82f}
    };
    private final float[][] junctionEssence={
        {.43f,.73f},{.51f,.57f},{.57f,.39f},{.66f,.27f},{.31f,.42f}
    };
    private final float[][] spiderEssence={
        {.33f,.67f},{.52f,.58f},{.72f,.48f},{.58f,.34f},{.27f,.46f}
    };

    public CaveWorldView(Context c,int body,int color,int eyes,int markings,int core,int alpha,int aura,String skill,Listener l){
        super(c);
        listener=l;
        slimeBody=body;slimeColorIndex=color;slimeEyes=eyes;slimeMarkings=markings;
        slimeCore=core;slimeAlpha=alpha;slimeAura=aura;
        startingSkill=skill==null?"UNASSIGNED":skill;
        stroke.setStyle(Paint.Style.STROKE);
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        loadArt();
    }

    private Bitmap load(int id){ return BitmapFactory.decodeResource(getResources(),id); }

    private void loadArt(){
        roomStart=load(R.drawable.room_start);
        roomCorridor=load(R.drawable.room_corridor);
        roomJunction=load(R.drawable.room_junction);
        roomSpider=load(R.drawable.room_spider);
        slimeMaster=load(R.drawable.slime_master);
        miteMaster=load(R.drawable.mite_master);
        spiderMaster=load(R.drawable.spider_master);
        crystalSprite=load(R.drawable.crystal_blue);
        essenceSprite=load(R.drawable.essence_orb);
        slashFx=load(R.drawable.magic_slash);
        impactFx=load(R.drawable.impact_burst);
        absorbFx=load(R.drawable.absorb_ring);
        webFx=load(R.drawable.web_burst);
    }

    public void start(){
        room=0;essence=0;currentForm=FORM_SLIME;
        miteState=ALIVE;spiderState=ALIVE;miteHp=5;spiderHp=8;
        miteAbsorbed=false;spiderAbsorbed=false;
        Arrays.fill(roomEssenceTaken,false);
        playerX=playerY=-1;enemyX=enemyY=-1;enemyDir=1f;
        moveX=moveY=0;lastFrame=0;transitionCooldown=0;
        attackStart=hitStart=absorbStart=0;
        message="Awakening stabilized. Collect the nearby mana essence.";
        notifyState();invalidate();
    }

    public void setMove(float x,float y){
        moveX=x;moveY=y;
        if(Math.abs(x)>.08f)lastFacing=Math.signum(x);
    }

    public void attack(){
        int state=currentEnemyState();
        if(state!=ALIVE){
            message="There is no active enemy to attack.";
            notifyState();return;
        }
        long now=SystemClock.uptimeMillis();
        if(now-attackStart<330)return;

        float range=Math.max(180f,getHeight()*.31f);
        if(distance(playerX,playerY,enemyX,enemyY)>range){
            message="Move closer before attacking.";
            notifyState();return;
        }

        attackStart=now;
        int damage=1;
        if(currentForm==FORM_MITE&&miteAbsorbed)damage=2;
        if(currentForm==FORM_SPIDER&&spiderAbsorbed)damage=3;

        if(room==0){
            miteHp=Math.max(0,miteHp-damage);
            if(miteHp==0){
                miteState=DEFEATED;
                message="Molten Mite defeated. Move close and ABSORB it to unlock its form.";
            }else message=(currentForm==FORM_MITE?"CARAPACE RAM":"MAGIC BURST")+" hit. Mite HP "+miteHp+"/5.";
        }else if(room==3){
            spiderHp=Math.max(0,spiderHp-damage);
            if(spiderHp==0){
                spiderState=DEFEATED;
                message="Abyss Weaver defeated. ABSORB it to gain Void Silk and Spider Morph.";
            }else message=(currentForm==FORM_SPIDER?"VOID SILK":"MAGIC BURST")+" hit. Spider HP "+spiderHp+"/8.";
        }
        hitStart=now;
        notifyState();invalidate();
    }

    public void absorb(){
        long now=SystemClock.uptimeMillis();
        absorbStart=now;
        collectNearbyEssence(true);

        if(room==0&&miteState==DEFEATED){
            if(distance(playerX,playerY,enemyX,enemyY)>Math.max(175f,getHeight()*.30f)){
                message="Move closer to the defeated mite before absorbing it.";
            }else{
                miteState=ABSORBED;miteAbsorbed=true;
                message="ABILITY ACQUIRED // MOLTEN CARAPACE. Mite Morph unlocked.";
            }
            notifyState();invalidate();return;
        }

        if(room==3&&spiderState==DEFEATED){
            if(distance(playerX,playerY,enemyX,enemyY)>Math.max(185f,getHeight()*.31f)){
                message="Move closer to the defeated Abyss Weaver before absorbing it.";
            }else{
                spiderState=ABSORBED;spiderAbsorbed=true;
                message="ABILITY ACQUIRED // VOID SILK. Spider Morph unlocked. Test section complete.";
            }
            notifyState();invalidate();return;
        }

        if(currentEnemyState()==ALIVE){
            message="The creature must be defeated before it can be absorbed.";
            notifyState();
        }
        invalidate();
    }

    public void befriend(){
        message="Befriending will be added after the combat/absorb loop is validated.";
        notifyState();
    }

    public void morph(){
        ArrayList<Integer> forms=new ArrayList<>();
        forms.add(FORM_SLIME);
        if(miteAbsorbed)forms.add(FORM_MITE);
        if(spiderAbsorbed)forms.add(FORM_SPIDER);
        int idx=forms.indexOf(currentForm);
        if(idx<0)idx=0;
        currentForm=forms.get((idx+1)%forms.size());
        message="MIMICRY // "+formName()+" form.";
        notifyState();invalidate();
    }

    public void setExternalSlime3D(boolean enabled){ externalSlime3D=enabled; invalidate(); }
    public float getPlayerXNorm(){ return getWidth()>0?playerX/getWidth():.5f; }
    public float getPlayerYNorm(){ return getHeight()>0?playerY/getHeight():.5f; }
    public float getPlayerDepth(){ return getHeight()>0?(.76f+.34f*Math.max(0f,Math.min(1f,playerY/getHeight()))):1f; }
    public float getMoveX(){ return moveX; }
    public float getMoveY(){ return moveY; }
    public int getCurrentForm(){ return currentForm; }

    public String roomName(){ return roomNames[room]; }
    public String message(){ return message; }

    public String formName(){
        if(currentForm==FORM_MITE)return "MOLTEN MITE";
        if(currentForm==FORM_SPIDER)return "ABYSS WEAVER";
        return "SLIME";
    }

    public String abilityText(){
        ArrayList<String> a=new ArrayList<>();
        a.add(startingSkill);
        if(miteAbsorbed)a.add("MOLTEN CARAPACE");
        if(spiderAbsorbed)a.add("VOID SILK");
        return join(a," • ");
    }

    public String statsText(){
        String enemy="";
        if(room==0&&miteState==ALIVE)enemy="  •  Mite "+miteHp+"/5";
        if(room==3&&spiderState==ALIVE)enemy="  •  Weaver "+spiderHp+"/8";
        return "Essence "+essence+"  •  Form "+formName()+enemy;
    }

    public String objectiveText(){
        if(room==0){
            if(essence<5)return "Collect the five mana essence points in the Awakening Hollow.";
            if(miteState==ALIVE)return "Approach and defeat the Molten Mite.";
            if(miteState==DEFEATED)return "Absorb the defeated mite to unlock Mite Morph.";
            return "The path east is open. Enter the Crystal Descent.";
        }
        if(room==1)return "Follow the painted stone path downward and gather essence.";
        if(room==2)return "Enter from below and follow the upper route into the Silken Nest.";
        if(room==3){
            if(spiderState==ALIVE)return "Defeat the Abyss Weaver.";
            if(spiderState==DEFEATED)return "Absorb the Abyss Weaver.";
            return "Spider Morph unlocked. Use MORPH to test it.";
        }
        return "";
    }

    public boolean canAttack(){ return currentEnemyState()==ALIVE; }
    public boolean canBefriend(){ return false; }
    public boolean canAbsorb(){ return currentEnemyState()==DEFEATED; }
    public boolean canMorph(){ return miteAbsorbed||spiderAbsorbed; }
    public boolean canExitCave(){ return room==3&&spiderAbsorbed; }

    public String absorbButtonText(){
        if(currentEnemyState()==DEFEATED)return "ABSORB\nCREATURE";
        return "ABSORB";
    }

    public String attackButtonText(){
        if(currentForm==FORM_MITE)return "CARAPACE\nRAM";
        if(currentForm==FORM_SPIDER)return "VOID\nSILK";
        return "MAGIC\nBURST";
    }

    public String morphButtonText(){ return "MORPH\n"+formName(); }

    private int currentEnemyState(){
        if(room==0)return miteState;
        if(room==3)return spiderState;
        return ABSORBED;
    }

    private int localEssenceCount(){
        int n=0;
        for(boolean b:roomEssenceTaken)if(b)n++;
        return n;
    }

    private void notifyState(){ if(listener!=null)listener.onStateChanged(this); }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        int w=getWidth(),h=getHeight();
        if(w<=0||h<=0){ postInvalidateDelayed(16); return; }
        long now=SystemClock.uptimeMillis();

        if(playerX<0){
            setSpawnForRoom(w,h);
            lastFrame=now;
        }
        float dt=Math.min(.033f,Math.max(0f,(now-lastFrame)/1000f));
        lastFrame=now;

        updateWorld(w,h,dt,now);
        drawWorld(c,w,h,now);
        postInvalidateDelayed(16);
    }

    private void updateWorld(int w,int h,float dt,long now){
        float mag=(float)Math.sqrt(moveX*moveX+moveY*moveY);
        float power=Math.min(1f,mag);
        float nx=mag>.01f?moveX/mag:0f;
        float ny=mag>.01f?moveY/mag:0f;
        float speed=Math.min(w,h)*(currentForm==FORM_SPIDER?.55f:currentForm==FORM_MITE?.48f:.51f);

        float proposedX=playerX+nx*speed*power*dt;
        float proposedY=playerY+ny*speed*power*dt;
        float px=proposedX/Math.max(1f,w),py=proposedY/Math.max(1f,h);

        if(isWalkable(room,px,py)){
            playerX=proposedX;playerY=proposedY;
        }else{
            // slide independently against rock/collision boundaries
            float pxOnly=proposedX/Math.max(1f,w);
            float pyOnly=playerY/Math.max(1f,h);
            if(isWalkable(room,pxOnly,pyOnly))playerX=proposedX;
            float px2=playerX/Math.max(1f,w);
            float py2=proposedY/Math.max(1f,h);
            if(isWalkable(room,px2,py2))playerY=proposedY;
        }

        updateEnemy(w,h,dt,now);
        collectNearbyEssence(false);
        handleTransitions(w,h,now);
    }

    private void handleTransitions(int w,int h,long now){
        if(now<transitionCooldown)return;

        if(room==0&&playerX>w*.91f){
            if(essence<5||miteState!=ABSORBED){
                playerX=w*.89f;
                message="The passage remains sealed until the five essence points are collected and the mite is absorbed.";
                notifyState();
            }else transitionTo(1,w*.53f,h*.17f,now);
        }else if(room==1&&playerY>h*.87f){
            transitionTo(2,w*.50f,h*.82f,now);
        }else if(room==1&&playerY<h*.12f){
            transitionTo(0,w*.87f,h*.48f,now);
        }else if(room==2&&playerY<h*.16f&&playerX>w*.42f){
            transitionTo(3,w*.49f,h*.83f,now);
        }else if(room==2&&playerY>h*.90f){
            transitionTo(1,w*.50f,h*.82f,now);
        }else if(room==3&&playerY>h*.91f){
            transitionTo(2,w*.55f,h*.18f,now);
        }
    }

    private void transitionTo(int next,float spawnX,float spawnY,long now){
        room=next;
        Arrays.fill(roomEssenceTaken,false);
        playerX=spawnX;playerY=spawnY;
        transitionCooldown=now+450;
        positionEnemy(getWidth(),getHeight());
        message="Entered "+roomNames[room]+".";
        notifyState();
    }

    private void setSpawnForRoom(int w,int h){
        if(room==0){ playerX=w*.105f;playerY=h*.245f; }
        else if(room==1){ playerX=w*.53f;playerY=h*.17f; }
        else if(room==2){ playerX=w*.50f;playerY=h*.82f; }
        else { playerX=w*.49f;playerY=h*.83f; }
        positionEnemy(w,h);
    }

    private void positionEnemy(int w,int h){
        if(room==0){ enemyX=w*.70f;enemyY=h*.55f;enemyDir=1f; }
        else if(room==3){ enemyX=w*.69f;enemyY=h*.39f;enemyDir=-1f; }
        else { enemyX=enemyY=-1; }
    }

    private void updateEnemy(int w,int h,float dt,long now){
        if(room==0&&miteState==ALIVE){
            enemyX+=enemyDir*w*.055f*dt;
            if(enemyX>w*.80f){enemyX=w*.80f;enemyDir=-1f;}
            if(enemyX<w*.57f){enemyX=w*.57f;enemyDir=1f;}
            enemyY=h*(.55f+.018f*(float)Math.sin(now/260.0));
        }else if(room==3&&spiderState==ALIVE){
            enemyX+=enemyDir*w*.035f*dt;
            if(enemyX>w*.78f){enemyX=w*.78f;enemyDir=-1f;}
            if(enemyX<w*.59f){enemyX=w*.59f;enemyDir=1f;}
            enemyY=h*(.40f+.012f*(float)Math.sin(now/330.0));
        }
    }

    private float[][] essenceLayout(){
        if(room==0)return startEssence;
        if(room==1)return corridorEssence;
        if(room==2)return junctionEssence;
        return spiderEssence;
    }

    private void collectNearbyEssence(boolean pulse){
        float[][] layout=essenceLayout();
        float radius=Math.max(pulse?160f:62f,getHeight()*(pulse?.24f:.085f));
        boolean changed=false;
        for(int i=0;i<layout.length;i++){
            if(roomEssenceTaken[i])continue;
            float x=layout[i][0]*getWidth(),y=layout[i][1]*getHeight();
            if(distance(playerX,playerY,x,y)<=radius){
                roomEssenceTaken[i]=true;essence++;changed=true;
                absorbStart=SystemClock.uptimeMillis();
            }
        }
        if(changed){
            message="Mana essence absorbed. Total essence: "+essence+".";
            notifyState();
        }
    }

    private boolean isWalkable(int room,float x,float y){
        if(x<.035f||x>.965f||y<.09f||y>.94f)return false;
        float[][] poly=room==0?WALK0:room==1?WALK1:room==2?WALK2:WALK3;
        return pointInPoly(poly,x,y);
    }

    private boolean pointInPoly(float[][] poly,float x,float y){
        boolean inside=false;
        for(int i=0,j=poly.length-1;i<poly.length;j=i++){
            float xi=poly[i][0],yi=poly[i][1],xj=poly[j][0],yj=poly[j][1];
            boolean intersect=((yi>y)!=(yj>y))&&(x<(xj-xi)*(y-yi)/(yj-yi+0.000001f)+xi);
            if(intersect)inside=!inside;
        }
        return inside;
    }

    private void drawWorld(Canvas c,int w,int h,long now){
        drawBackground(c,w,h,now);
        drawEssence(c,w,h,now);
        drawEnemy(c,w,h,now);
        drawPlayer(c,w,h,now);
        drawEffects(c,w,h,now);
    }

    private Bitmap roomBitmap(){
        if(room==0)return roomStart;
        if(room==1)return roomCorridor;
        if(room==2)return roomJunction;
        return roomSpider;
    }

    private void drawBackground(Canvas c,int w,int h,long now){
        Bitmap b=roomBitmap();
        if(b==null){ c.drawColor(Color.rgb(7,14,25)); return; }

        float zoom=1.018f+.006f*(float)Math.sin(now/5200.0);
        float dw=w*zoom,dh=h*zoom;
        float panX=(float)Math.sin(now/6800.0)*w*.005f;
        float panY=(float)Math.cos(now/7200.0)*h*.004f;
        RectF dst=new RectF((w-dw)/2f+panX,(h-dh)/2f+panY,(w+dw)/2f+panX,(h+dh)/2f+panY);
        p.setAlpha(255);p.setColorFilter(null);c.drawBitmap(b,null,dst,p);

        int ambient=room==3?Color.rgb(233,64,210):Color.rgb(91,210,255);
        float pulse=.68f+.32f*(float)Math.sin(now/760.0);
        drawGlow(c,w*.52f,h*.53f,Math.min(w,h)*.30f,
            Color.argb((int)(17+22*pulse),Color.red(ambient),Color.green(ambient),Color.blue(ambient)));

        for(int i=0;i<28;i++){
            float x=((i*127)+(now*.009f*(1+i%3)))%w;
            float y=((i*71)+(now*.003f*(1+i%2)))%Math.max(1,h-90)+45;
            p.setColor(Color.argb(22+(i%5)*7,Color.red(ambient),Color.green(ambient),Color.blue(ambient)));
            c.drawCircle(x,y,1f+(i%3)*.55f,p);
        }
    }

    private void drawEssence(Canvas c,int w,int h,long now){
        float[][] layout=essenceLayout();
        for(int i=0;i<layout.length;i++){
            if(roomEssenceTaken[i])continue;
            float x=layout[i][0]*w,y=layout[i][1]*h;
            float r=Math.min(w,h)*.033f;
            float bob=(float)Math.sin(now/240.0+i*.8)*r*.15f;
            drawGlow(c,x,y+bob,r*2.3f,Color.argb(55,91,225,255));
            Bitmap b=(i%2==0&&crystalSprite!=null)?crystalSprite:essenceSprite;
            drawSprite(c,b,x,y+bob,r,240,false);
        }
    }

    private void drawEnemy(Canvas c,int w,int h,long now){
        int state=currentEnemyState();
        if(state==ABSORBED||enemyX<0)return;
        Bitmap b=room==0?miteMaster:room==3?spiderMaster:null;
        if(b==null)return;

        float baseR=Math.min(w,h)*(room==0?.092f:.125f);
        float breathe=1f+.020f*(float)Math.sin(now/(room==0?280.0:350.0));
        float sway=(float)Math.sin(now/(room==0?620.0:880.0))*(room==0?2.2f:1.5f);
        int alpha=state==DEFEATED?125:255;

        drawActorShadow(c,enemyX,enemyY,baseR,room==0?.55f:.68f);

        c.save();
        c.translate(enemyX,enemyY+(float)Math.sin(now/300.0)*baseR*.035f);
        c.rotate(state==DEFEATED?8f:sway);
        c.scale(breathe,2f-breathe);
        if(enemyDir<0)c.scale(-1f,1f);
        p.setAlpha(alpha);p.setColorFilter(null);
        c.drawBitmap(b,null,new RectF(-baseR,-baseR,baseR,baseR),p);
        p.setAlpha(255);
        if(state==ALIVE){
            int glow=room==0?Color.rgb(255,86,30):Color.rgb(232,55,209);
            float gp=.58f+.42f*(float)Math.sin(now/180.0);
            drawGlow(c,0,-baseR*.18f,baseR*.55f,Color.argb((int)(28*gp),Color.red(glow),Color.green(glow),Color.blue(glow)));
        }
        c.restore();

        if(state==ALIVE){
            int hp=room==0?miteHp:spiderHp;
            int max=room==0?5:8;
            float bw=baseR*1.65f;
            p.setColor(Color.argb(180,6,12,20));
            c.drawRoundRect(new RectF(enemyX-bw/2,enemyY-baseR*1.25f,enemyX+bw/2,enemyY-baseR*1.15f),7,7,p);
            p.setColor(room==0?Color.rgb(247,91,49):Color.rgb(221,64,207));
            c.drawRoundRect(new RectF(enemyX-bw/2,enemyY-baseR*1.25f,enemyX-bw/2+bw*(hp/(float)max),enemyY-baseR*1.15f),7,7,p);
        }
    }

    private void drawPlayer(Canvas c,int w,int h,long now){
        float r=Math.min(w,h)*.070f;
        drawActorShadow(c,playerX,playerY,r,.52f);
        if(currentForm==FORM_MITE&&miteMaster!=null){
            drawSprite(c,miteMaster,playerX,playerY,r,255,lastFacing<0);
        }else if(currentForm==FORM_SPIDER&&spiderMaster!=null){
            drawSprite(c,spiderMaster,playerX,playerY,r*1.06f,255,lastFacing<0);
        }else{
            if(!externalSlime3D)drawLiveSlime(c,playerX,playerY,r,now);
        }
    }

    private void drawLiveSlime(Canvas c,float x,float y,float r,long now){
        if(slimeMaster==null){
            p.setColor(Color.rgb(73,194,255));c.drawCircle(x,y,r,p);return;
        }
        float mag=(float)Math.sqrt(moveX*moveX+moveY*moveY);
        float idle=(float)Math.sin(now/360.0);
        float hop=mag>.07f?Math.abs((float)Math.sin(now/118.0))*r*.10f:0f;
        float sx=1f+.022f*idle,sy=1f-.018f*idle;
        if(slimeBody==1){sx*=.92f;sy*=1.08f;}
        else if(slimeBody==2){sx*=1.10f;sy*=.93f;}

        int accent=slimeColor();
        float auraPower=new float[]{.20f,.42f,.62f,.82f,1f}[Math.max(0,Math.min(4,slimeAura))];
        drawGlow(c,x,y-hop,r*1.35f,Color.argb((int)(28+45*auraPower),Color.red(accent),Color.green(accent),Color.blue(accent)));

        c.save();
        c.translate(x,y-hop);
        c.rotate(Math.max(-6f,Math.min(6f,moveX*6f)));
        c.scale(sx,sy);
        p.setAlpha(new int[]{155,185,220,245}[Math.max(0,Math.min(3,slimeAlpha))]);
        p.setColorFilter(slimeFilter(slimeColorIndex));
        c.drawBitmap(slimeMaster,null,new RectF(-r,-r,r,r),p);
        p.setColorFilter(null);p.setAlpha(255);
        c.restore();
    }

    private ColorFilter slimeFilter(int idx){
        float[] m;
        switch(idx){
            case 1:m=new float[]{.95f,0,.48f,0,0, 0,.60f,.22f,0,0, .28f,0,1.22f,0,0, 0,0,0,1,0};break;
            case 2:m=new float[]{1.35f,.18f,0,0,0, .15f,.55f,0,0,0, 0,.04f,.48f,0,0, 0,0,0,1,0};break;
            case 3:m=new float[]{.42f,.08f,0,0,0, .08f,1.10f,.10f,0,0, 0,.24f,.70f,0,0, 0,0,0,1,0};break;
            case 4:m=new float[]{.92f,.22f,.22f,0,16, .22f,.92f,.22f,0,16, .22f,.22f,.92f,0,16, 0,0,0,1,0};break;
            case 5:m=new float[]{.42f,0,.16f,0,0, 0,.38f,.10f,0,0, .10f,0,.55f,0,0, 0,0,0,1,0};break;
            default:m=new float[]{1,0,0,0,0, 0,1,0,0,0, 0,0,1,0,0, 0,0,0,1,0};
        }
        return new ColorMatrixColorFilter(new ColorMatrix(m));
    }

    private int slimeColor(){
        int[] colors={Color.rgb(44,184,255),Color.rgb(149,87,255),Color.rgb(255,91,56),Color.rgb(63,214,133),Color.rgb(222,241,250),Color.rgb(50,42,78)};
        return colors[Math.max(0,Math.min(colors.length-1,slimeColorIndex))];
    }

    private void drawEffects(Canvas c,int w,int h,long now){
        long aa=now-attackStart;
        if(attackStart>0&&aa<350){
            float t=aa/350f;
            Bitmap fx=currentForm==FORM_SPIDER&&webFx!=null?webFx:slashFx;
            float size=Math.min(w,h)*(currentForm==FORM_SPIDER?.28f:.30f);
            float fxX=playerX+lastFacing*size*.62f;
            drawSprite(c,fx,fxX,playerY,size,(int)(235*(1f-t)),lastFacing<0);
        }

        long ha=now-hitStart;
        if(hitStart>0&&ha<300&&impactFx!=null&&enemyX>=0){
            float t=ha/300f;
            float rr=Math.min(w,h)*(.10f+.07f*t);
            drawSprite(c,impactFx,enemyX,enemyY,rr,(int)(255*(1f-t)),false);
        }

        long ab=now-absorbStart;
        if(absorbStart>0&&ab<650&&absorbFx!=null){
            float t=ab/650f;
            float rr=Math.min(w,h)*(.13f+.24f*t);
            drawSprite(c,absorbFx,playerX,playerY,rr,(int)(220*(1f-t)),false);
        }
    }

    private void drawActorShadow(Canvas c,float x,float y,float r,float width){
        p.setColor(Color.argb(86,0,0,0));
        c.drawOval(new RectF(x-r*width,y+r*.70f,x+r*width,y+r*.93f),p);
    }

    private void drawSprite(Canvas c,Bitmap b,float x,float y,float r,int alpha,boolean flip){
        if(b==null)return;
        c.save();
        if(flip)c.scale(-1f,1f,x,y);
        p.setAlpha(alpha);p.setColorFilter(null);
        c.drawBitmap(b,null,new RectF(x-r,y-r,x+r,y+r),p);
        p.setAlpha(255);
        c.restore();
    }

    private void drawGlow(Canvas c,float x,float y,float r,int color){
        int transparent=Color.argb(0,Color.red(color),Color.green(color),Color.blue(color));
        p.setShader(new RadialGradient(x,y,r,new int[]{color,transparent},null,Shader.TileMode.CLAMP));
        c.drawCircle(x,y,r,p);p.setShader(null);
    }

    private float distance(float ax,float ay,float bx,float by){
        float dx=ax-bx,dy=ay-by;return (float)Math.sqrt(dx*dx+dy*dy);
    }

    private String join(List<String> parts,String sep){
        StringBuilder sb=new StringBuilder();
        for(String x:parts){
            if(x==null||x.isEmpty())continue;
            if(sb.length()>0)sb.append(sep);
            sb.append(x);
        }
        return sb.toString();
    }
}
