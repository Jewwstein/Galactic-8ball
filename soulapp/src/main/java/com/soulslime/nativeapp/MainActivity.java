package com.soulslime.nativeapp;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    enum Scene { DEATH, REBIRTH, CREATOR, CAVE, COMPLETE }

    static final int CYAN = Color.rgb(92, 226, 255);
    static final int CYAN_BRIGHT = Color.rgb(205, 250, 255);
    static final int PANEL = Color.argb(224, 6, 27, 46);
    static final int PANEL_SOFT = Color.argb(188, 8, 47, 70);

    final Random rng = new Random();
    FrameLayout root;
    SoulCanvas canvas;
    Scene scene = Scene.DEATH;
    SharedPreferences prefs;

    int body = 0, material = 0, color = 0, eyes = 0, markings = 1, core = 0, alpha = 2, aura = 2;
    String creatorCategory = "BODY";
    String selectedSkill = "";
    String deathDescription = "";
    int selectedSkillIndex = -1;
    Skill[] currentSkills = new Skill[3];

    LinearLayout optionsContainer;
    TextView creatorStatus;
    TextView caveRoomText, caveObjective, caveStats;
    Button caveContinue, caveAttack, caveAbsorb, caveBefriend, caveMorph;
    CaveWorldView caveWorld;
    Slime3DView creator3D, cave3D;
    Runnable cave3DSync;
    final ArrayList<Button> categoryButtons = new ArrayList<>();

    static final String[] DEATHS = {
        "A delivery van loses control on a rain-slick street. Headlights fill your vision.",
        "A transformer erupts during a storm. Blue-white light swallows the night.",
        "A construction scaffold gives way above a crowded sidewalk. There is no time to move.",
        "The last train screams into the station as the platform edge disappears beneath you.",
        "A sudden collision turns an ordinary night drive into one blinding instant."
    };

    static class Skill {
        final String name, desc;
        Skill(String n, String d){ name=n; desc=d; }
    }

    static final Skill[] SKILL_POOL = {
        new Skill("MAGIC SENSE", "Detect mana, spells, living signatures, and hidden magical traces."),
        new Skill("ACCELERATED THOUGHT", "Process danger, analysis, and decisions at supernatural speed."),
        new Skill("REGENERATION", "Recover lost slime mass and repair your vessel over time."),
        new Skill("SPATIAL STORAGE", "Store absorbed matter inside a hidden personal space."),
        new Skill("HEAT RESISTANCE", "Reduce damage from fire, heat, and extreme temperature."),
        new Skill("NIGHT VISION", "See clearly in caves, darkness, and low-light environments."),
        new Skill("PREDATOR", "Analyze and absorb defeated creatures more efficiently."),
        new Skill("WATER AFFINITY", "Gain an early advantage with water magic and fluid control.")
    };

    static final String[] BODY = {"STANDARD","TALL CREST","WIDE FORM"};
    static final String[] MATERIAL = {"COSMIC FLOW","ARCANE VEINS","BUBBLE GLASS"};
    static final String[] COLOR = {"AZURE","AMETHYST","EMBER","JADE","PEARL","SHADOW"};
    static final String[] EYES = {"CALM","SHARP","VOID","STAR"};
    static final String[] MARK = {"NONE","RUNE","SPECKLES","CREST"};
    static final String[] CORE = {"SOUL","STAR","MOON","NONE"};
    static final String[] ALPHA = {"55%","68%","82%","92%"};
    static final String[] AURA = {"20%","42%","62%","82%","100%"};

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("soul_slime_native",MODE_PRIVATE);
        loadCreator();
        immersive();

        root=new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        canvas=new SoulCanvas(this);
        root.addView(canvas,new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);

        showDeath();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus) immersive();
    }

    void immersive(){
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if(android.os.Build.VERSION.SDK_INT>=28){
            getWindow().getAttributes().layoutInDisplayCutoutMode=
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    int dp(float v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }

    int screenW(){ return getResources().getDisplayMetrics().widthPixels; }
    int screenH(){ return getResources().getDisplayMetrics().heightPixels; }

    void clearOverlay(){
        root.setOnTouchListener(null);
        if(creator3D!=null){ try{creator3D.onPause();}catch(Exception ignored){} creator3D=null; }
        if(cave3D!=null){ try{cave3D.onPause();}catch(Exception ignored){} cave3D=null; }
        if(cave3DSync!=null){ root.removeCallbacks(cave3DSync); cave3DSync=null; }
        while(root.getChildCount()>1) root.removeViewAt(1);
        categoryButtons.clear();
        optionsContainer=null;
        creatorStatus=null;
        caveRoomText=null;caveObjective=null;caveStats=null;
        caveContinue=null;caveAttack=null;caveAbsorb=null;caveBefriend=null;caveMorph=null;
        caveWorld=null;
    }

    GradientDrawable panelDrawable(int fill, int stroke, float radiusDp){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{fill, Color.argb(242,3,13,25)});
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1.2f),stroke);
        return g;
    }

    Drawable buttonDrawable(int accent, boolean selected){
        int r=Color.red(accent), g=Color.green(accent), b=Color.blue(accent);
        GradientDrawable normal=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            selected
                ? new int[]{Color.argb(255,30,112,137),Color.argb(255,8,57,78)}
                : new int[]{Color.argb(255,21,52,73),Color.argb(255,5,27,43)});
        normal.setCornerRadius(dp(12));
        normal.setStroke(dp(selected?2.0f:1.25f),Color.argb(selected?255:210,r,g,b));
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(95,r,g,b)),normal,null);
    }

    TextView text(String t,float sp,int color){
        TextView v=new TextView(this);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        v.setIncludeFontPadding(false);
        return v;
    }

    Button button(String t,int accent,boolean selected){
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(t);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8),dp(4),dp(8),dp(4));
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setBackground(buttonDrawable(accent,selected));
        return b;
    }

    LinearLayout card(){
        LinearLayout p=new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setGravity(Gravity.CENTER_HORIZONTAL);
        p.setPadding(dp(22),dp(18),dp(22),dp(18));
        p.setBackground(panelDrawable(PANEL,Color.argb(195,80,220,250),22));
        return p;
    }

    void addSpace(LinearLayout p,int h){
        Space s=new Space(this);
        p.addView(s,new LinearLayout.LayoutParams(1,dp(h)));
    }

    void showDeath(){
        canvas.setVisibility(View.VISIBLE);
        scene=Scene.DEATH;
        clearOverlay();
        canvas.setScene(scene);
        deathDescription=DEATHS[rng.nextInt(DEATHS.length)];

        LinearLayout p=card();
        int width=Math.min(dp(760),screenW()-dp(48));
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(width,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        fp.setMargins(dp(24),dp(24),dp(24),dp(28));

        TextView kicker=text("FINAL MOMENTS",12,CYAN);
        TextView title=text("A LIFE ENDS",27,Color.WHITE);
        TextView desc=text(deathDescription,15,Color.rgb(220,239,247));
        desc.setGravity(Gravity.CENTER);
        TextView hint=text("The world disappears. Something else is listening.",12,Color.rgb(139,191,211));
        Button continueBtn=button("ACCEPT FATE  •  TAP TO CONTINUE",CYAN,true);
        continueBtn.setTextSize(14);

        p.addView(kicker,new LinearLayout.LayoutParams(-1,dp(28)));
        p.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));
        addSpace(p,4);
        p.addView(desc,new LinearLayout.LayoutParams(-1,dp(54)));
        p.addView(hint,new LinearLayout.LayoutParams(-1,dp(34)));
        addSpace(p,8);
        p.addView(continueBtn,new LinearLayout.LayoutParams(-1,dp(54)));

        root.addView(p,fp);
        continueBtn.setOnClickListener(v->showRebirth());

        // Fail-safe: any tap on otherwise empty death-scene space also advances.
        root.setOnTouchListener((v,e)->{
            if(scene==Scene.DEATH && e.getAction()==MotionEvent.ACTION_UP){
                showRebirth();
                return true;
            }
            return scene==Scene.DEATH;
        });
    }

    void chooseSkills(){
        ArrayList<Skill> pool=new ArrayList<>(Arrays.asList(SKILL_POOL));
        Collections.shuffle(pool,rng);
        for(int i=0;i<3;i++) currentSkills[i]=pool.get(i);
        selectedSkillIndex=-1;
        selectedSkill="";
    }

    void showRebirth(){
        canvas.setVisibility(View.VISIBLE);
        scene=Scene.REBIRTH;
        clearOverlay();
        canvas.setScene(scene);
        chooseSkills();

        LinearLayout p=card();
        int width=Math.min(dp(1050),screenW()-dp(44));
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(width,-2,Gravity.CENTER);
        fp.setMargins(dp(22),dp(16),dp(22),dp(16));

        p.addView(text("SOUL ANALYSIS // CONSCIOUSNESS DETECTED",12,CYAN),new LinearLayout.LayoutParams(-1,dp(28)));
        p.addView(text("REINCARNATION PARAMETERS",25,Color.WHITE),new LinearLayout.LayoutParams(-1,dp(45)));
        TextView info=text("Your memories remain. Your former body does not. Choose one starting ability before your new vessel is formed.",12,Color.rgb(186,224,237));
        p.addView(info,new LinearLayout.LayoutParams(-1,dp(45)));
        addSpace(p,6);

        LinearLayout skillRow=new LinearLayout(this);
        skillRow.setOrientation(LinearLayout.HORIZONTAL);
        skillRow.setGravity(Gravity.CENTER);
        Button[] skillButtons=new Button[3];
        for(int i=0;i<3;i++){
            final int idx=i;
            Skill sk=currentSkills[i];
            Button b=button(sk.name+"\n\n"+sk.desc,CYAN,false);
            b.setTextSize(12);
            b.setLines(6);
            b.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(160),1f);
            if(i>0) lp.leftMargin=dp(10);
            skillRow.addView(b,lp);
            skillButtons[i]=b;
        }
        p.addView(skillRow,new LinearLayout.LayoutParams(-1,dp(160)));
        addSpace(p,8);

        TextView status=text("Choose one skill.",12,Color.rgb(151,218,237));
        p.addView(status,new LinearLayout.LayoutParams(-1,dp(30)));
        Button form=button("FORM NEW VESSEL",CYAN,false);
        form.setEnabled(false);
        form.setAlpha(.45f);
        p.addView(form,new LinearLayout.LayoutParams(Math.min(dp(320),width-dp(44)),dp(52)));

        for(int i=0;i<3;i++){
            final int idx=i;
            skillButtons[i].setOnClickListener(v->{
                selectedSkillIndex=idx;
                selectedSkill=currentSkills[idx].name;
                for(int j=0;j<3;j++) skillButtons[j].setBackground(buttonDrawable(CYAN,j==idx));
                status.setText("SELECTED // "+selectedSkill);
                form.setEnabled(true);
                form.setAlpha(1f);
                canvas.pulseCore();
            });
        }
        form.setOnClickListener(v->{
            if(selectedSkillIndex>=0){
                prefs.edit().putString("starting_skill",selectedSkill).apply();
                showCreator();
            }
        });

        root.addView(p,fp);
    }

    void showCreator(){
        canvas.setVisibility(View.VISIBLE);
        scene=Scene.CREATOR;
        clearOverlay();
        canvas.setScene(scene);

        TextView title=text("ANALYSIS SPACE // SOUL VESSEL",18,Color.WHITE);
        title.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams((int)(screenW()*.50f),dp(44),Gravity.TOP|Gravity.LEFT);
        tp.setMargins(dp(24),dp(16),0,0);
        root.addView(title,tp);

        TextView sub=text("Starting skill: "+(selectedSkill.isEmpty()?prefs.getString("starting_skill","UNASSIGNED"):selectedSkill),11,CYAN);
        sub.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams((int)(screenW()*.50f),dp(34),Gravity.TOP|Gravity.LEFT);
        sp.setMargins(dp(24),dp(54),0,0);
        root.addView(sub,sp);

        creator3D=new Slime3DView(this);
        creator3D.setInteractive(true);
        creator3D.setPreviewMode();
        FrameLayout.LayoutParams glp=new FrameLayout.LayoutParams((int)(screenW()*.50f),Math.max(dp(260),screenH()-dp(105)),Gravity.LEFT|Gravity.BOTTOM);
        glp.setMargins(dp(10),0,0,dp(8));
        root.addView(creator3D,glp);
        syncCreator3D();

        int panelWidth=(int)(screenW()*.44f);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackground(panelDrawable(Color.argb(224,5,24,41),Color.argb(200,82,225,255),20));
        scroll.setPadding(dp(14),dp(12),dp(14),dp(12));

        LinearLayout p=new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(p,new ScrollView.LayoutParams(-1,-1));

        p.addView(text("1  SELECT PARAMETER",12,CYAN),new LinearLayout.LayoutParams(-1,dp(28)));
        LinearLayout cats=new LinearLayout(this);
        cats.setOrientation(LinearLayout.VERTICAL);
        p.addView(cats,new LinearLayout.LayoutParams(-1,-2));
        buildCategoryButtons(cats);

        addSpace(p,6);
        p.addView(text("2  SELECT OPTION",12,CYAN),new LinearLayout.LayoutParams(-1,dp(28)));
        optionsContainer=new LinearLayout(this);
        optionsContainer.setOrientation(LinearLayout.VERTICAL);
        p.addView(optionsContainer,new LinearLayout.LayoutParams(-1,-2));
        rebuildOptions();

        addSpace(p,8);
        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button random=button("RANDOMIZE",Color.rgb(99,185,255),false);
        Button save=button("SAVE",Color.rgb(71,224,165),false);
        Button lock=button("LOCK FORM",Color.rgb(196,111,255),false);
        actions.addView(random,new LinearLayout.LayoutParams(0,dp(46),1f));
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(0,dp(46),1f); slp.leftMargin=dp(7);
        actions.addView(save,slp);
        LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(0,dp(46),1f); llp.leftMargin=dp(7);
        actions.addView(lock,llp);
        p.addView(actions,new LinearLayout.LayoutParams(-1,dp(46)));

        creatorStatus=text("Touch a category, then the exact option you want.",11,Color.rgb(151,218,237));
        p.addView(creatorStatus,new LinearLayout.LayoutParams(-1,dp(34)));

        random.setOnClickListener(v->{
            body=rng.nextInt(BODY.length);
            material=rng.nextInt(MATERIAL.length);
            color=rng.nextInt(COLOR.length);
            eyes=rng.nextInt(EYES.length);
            markings=rng.nextInt(MARK.length);
            core=rng.nextInt(CORE.length);
            alpha=rng.nextInt(ALPHA.length);
            aura=rng.nextInt(AURA.length);
            rebuildOptions();
            canvas.react(true);
            syncCreator3D();
            if(creator3D!=null)creator3D.react();
            creatorStatus.setText("RANDOM 3D SOUL FORM GENERATED.");
        });
        save.setOnClickListener(v->{
            saveCreator();
            creatorStatus.setText("PROFILE SAVED.");
        });
        lock.setOnClickListener(v->{
            saveCreator();
            showCave();
        });

        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(panelWidth,-1,Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        fp.setMargins(dp(12),dp(14),dp(18),dp(14));
        root.addView(scroll,fp);
    }

    void buildCategoryButtons(LinearLayout parent){
        String[] cats={"BODY","MATERIAL","COLOR","EYES","MARKINGS","SOUL CORE","TRANSPARENCY","AURA"};
        categoryButtons.clear();
        for(int i=0;i<cats.length;i+=2){
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for(int j=0;j<2;j++){
                int k=i+j;
                if(k>=cats.length){
                    Space blank=new Space(this);
                    LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(0,dp(40),1f);
                    row.addView(blank,blp);
                    continue;
                }
                String cat=cats[k];
                Button b=button(cat,CYAN,cat.equals(creatorCategory));
                b.setTextSize(11);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1f);
                if(j==1) lp.leftMargin=dp(7);
                row.addView(b,lp);
                categoryButtons.add(b);
                b.setOnClickListener(v->{
                    creatorCategory=cat;
                    for(Button cb:categoryButtons){
                        boolean sel=cb.getText().toString().equals(creatorCategory);
                        cb.setBackground(buttonDrawable(CYAN,sel));
                    }
                    rebuildOptions();
                });
            }
            LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,dp(40));
            if(i>0) rlp.topMargin=dp(6);
            parent.addView(row,rlp);
        }
    }

    String[] optionNames(){
        switch(creatorCategory){
            case "MATERIAL": return MATERIAL;
            case "COLOR": return COLOR;
            case "EYES": return EYES;
            case "MARKINGS": return MARK;
            case "SOUL CORE": return CORE;
            case "TRANSPARENCY": return ALPHA;
            case "AURA": return AURA;
            default: return BODY;
        }
    }

    int selectedOption(){
        switch(creatorCategory){
            case "MATERIAL": return material;
            case "COLOR": return color;
            case "EYES": return eyes;
            case "MARKINGS": return markings;
            case "SOUL CORE": return core;
            case "TRANSPARENCY": return alpha;
            case "AURA": return aura;
            default: return body;
        }
    }

    void setSelectedOption(int idx){
        switch(creatorCategory){
            case "MATERIAL": material=idx; break;
            case "COLOR": color=idx; break;
            case "EYES": eyes=idx; break;
            case "MARKINGS": markings=idx; break;
            case "SOUL CORE": core=idx; break;
            case "TRANSPARENCY": alpha=idx; break;
            case "AURA": aura=idx; break;
            default: body=idx; break;
        }
    }

    void rebuildOptions(){
        if(optionsContainer==null) return;
        optionsContainer.removeAllViews();
        String[] opts=optionNames();
        int selected=selectedOption();
        for(int i=0;i<opts.length;i+=2){
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for(int j=0;j<2;j++){
                int k=i+j;
                if(k>=opts.length){
                    Space blank=new Space(this);
                    row.addView(blank,new LinearLayout.LayoutParams(0,dp(42),1f));
                    continue;
                }
                final int idx=k;
                Button b=button((k==selected?"✓ ":"")+opts[k],CYAN,k==selected);
                b.setTextSize(11);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1f);
                if(j==1) lp.leftMargin=dp(7);
                row.addView(b,lp);
                b.setOnClickListener(v->{
                    setSelectedOption(idx);
                    rebuildOptions();
                    canvas.react(false);
                    syncCreator3D();
                    if(creator3D!=null)creator3D.react();
                    if(creatorStatus!=null) creatorStatus.setText("SELECTED // "+optionNames()[idx]);
                });
            }
            LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,dp(42));
            if(i>0) rlp.topMargin=dp(6);
            optionsContainer.addView(row,rlp);
        }
        canvas.invalidate();
        syncCreator3D();
    }

    void syncCreator3D(){
        if(creator3D!=null)creator3D.setConfig(body,material,color,markings,core,alpha,aura);
    }


    void showCave(){
        scene=Scene.CAVE;
        clearOverlay();

        // The creator/analysis canvas must not remain visible underneath the cave.
        canvas.setVisibility(View.GONE);

        caveWorld=new CaveWorldView(
            this,body,color,eyes,markings,core,alpha,aura,
            prefs.getString("starting_skill","UNASSIGNED"),
            world->updateCaveHud()
        );
        root.addView(caveWorld,1,new FrameLayout.LayoutParams(-1,-1));
        caveWorld.setExternalSlime3D(true);
        caveWorld.setExternalCreature3D(true);

        cave3D=new Slime3DView(this);
        cave3D.setInteractive(false);
        cave3D.setConfig(body,material,color,markings,core,alpha,aura);
        cave3D.setPreviewMode();
        root.addView(cave3D,new FrameLayout.LayoutParams(-1,-1));
        startCave3DSync();

        // Thin, nearly transparent full-width HUD so gameplay remains visible.
        LinearLayout hud=new LinearLayout(this);
        hud.setOrientation(LinearLayout.VERTICAL);
        hud.setPadding(dp(12),dp(4),dp(12),dp(4));
        GradientDrawable hudBg=new GradientDrawable();
        hudBg.setColor(Color.argb(142,3,16,29));
        hudBg.setCornerRadius(dp(10));
        hudBg.setStroke(dp(1),Color.argb(105,106,225,255));
        hud.setBackground(hudBg);

        LinearLayout topRow=new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        caveRoomText=text("",11.5f,CYAN_BRIGHT);
        caveRoomText.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        caveStats=text("",9.2f,Color.rgb(174,226,241));
        caveStats.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);

        topRow.addView(caveRoomText,new LinearLayout.LayoutParams(0,dp(25),.44f));
        topRow.addView(caveStats,new LinearLayout.LayoutParams(0,dp(25),.56f));

        caveObjective=text("",9.8f,Color.WHITE);
        caveObjective.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        caveObjective.setSingleLine(true);
        caveObjective.setEllipsize(android.text.TextUtils.TruncateAt.END);

        caveContinue=button("TEST COMPLETE",Color.rgb(126,238,255),true);
        caveContinue.setTextSize(10);
        caveContinue.setVisibility(View.GONE);
        caveContinue.setOnClickListener(v->showComplete());

        LinearLayout lowerRow=new LinearLayout(this);
        lowerRow.setOrientation(LinearLayout.HORIZONTAL);
        lowerRow.setGravity(Gravity.CENTER_VERTICAL);
        lowerRow.addView(caveObjective,new LinearLayout.LayoutParams(0,dp(30),1f));
        lowerRow.addView(caveContinue,new LinearLayout.LayoutParams(dp(118),dp(30)));

        hud.addView(topRow,new LinearLayout.LayoutParams(-1,dp(25)));
        hud.addView(lowerRow,new LinearLayout.LayoutParams(-1,dp(31)));

        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-1,dp(64),Gravity.TOP);
        hp.setMargins(dp(8),dp(6),dp(8),0);
        root.addView(hud,hp);

        AnalogJoystickView stick=new AnalogJoystickView(this,(x,y)->{
            if(scene==Scene.CAVE&&caveWorld!=null)caveWorld.setMove(x,y);
        });
        FrameLayout.LayoutParams stickLp=new FrameLayout.LayoutParams(dp(224),dp(224),Gravity.BOTTOM|Gravity.LEFT);
        stickLp.setMargins(dp(18),0,0,dp(12));
        root.addView(stick,stickLp);

        caveAttack=button("MAGIC\nBURST",Color.rgb(107,178,255),true);
        caveAttack.setTextSize(13);
        caveAttack.setOnClickListener(v->{if(caveWorld!=null)caveWorld.attack();});
        FrameLayout.LayoutParams attackLp=new FrameLayout.LayoutParams(dp(116),dp(82),Gravity.BOTTOM|Gravity.RIGHT);
        attackLp.setMargins(0,0,dp(26),dp(28));
        root.addView(caveAttack,attackLp);

        caveAbsorb=button("ABSORB",Color.rgb(97,232,178),false);
        caveAbsorb.setTextSize(11);
        caveAbsorb.setOnClickListener(v->{if(caveWorld!=null)caveWorld.absorb();});
        FrameLayout.LayoutParams absorbLp=new FrameLayout.LayoutParams(dp(112),dp(60),Gravity.BOTTOM|Gravity.RIGHT);
        absorbLp.setMargins(0,0,dp(151),dp(38));
        root.addView(caveAbsorb,absorbLp);

        caveBefriend=button("BEFRIEND",Color.rgb(255,191,104),false);
        caveBefriend.setTextSize(11);
        caveBefriend.setOnClickListener(v->{if(caveWorld!=null)caveWorld.befriend();});
        caveBefriend.setVisibility(View.GONE);
        FrameLayout.LayoutParams friendLp=new FrameLayout.LayoutParams(dp(112),dp(56),Gravity.BOTTOM|Gravity.RIGHT);
        friendLp.setMargins(0,0,dp(151),dp(106));
        root.addView(caveBefriend,friendLp);

        caveMorph=button("MORPH",Color.rgb(196,125,255),false);
        caveMorph.setTextSize(10);
        caveMorph.setOnClickListener(v->{if(caveWorld!=null)caveWorld.morph();});
        FrameLayout.LayoutParams morphLp=new FrameLayout.LayoutParams(dp(116),dp(58),Gravity.BOTTOM|Gravity.RIGHT);
        morphLp.setMargins(0,0,dp(26),dp(119));
        root.addView(caveMorph,morphLp);

        caveWorld.start();
        updateCaveHud();
    }


    void startCave3DSync(){
        cave3DSync=new Runnable(){
            @Override public void run(){
                if(scene!=Scene.CAVE||caveWorld==null||cave3D==null)return;
                cave3D.setGameplayState(
                    caveWorld.getPlayerXNorm(),
                    caveWorld.getPlayerYNorm(),
                    caveWorld.getPlayerDepth(),
                    caveWorld.getMoveX(),
                    caveWorld.getMoveY(),
                    caveWorld.getCurrentForm()
                );
                cave3D.setEnemyState(
                    caveWorld.getEnemyType(),
                    caveWorld.getEnemyXNorm(),
                    caveWorld.getEnemyGroundYNorm(),
                    caveWorld.getEnemyDepth(),
                    caveWorld.getEnemyFacing(),
                    caveWorld.getEnemyStateFor3D(),
                    caveWorld.getEnemyAttackPulse(),
                    caveWorld.getEnemyHitPulse(),
                    caveWorld.isEnemy3DVisible()
                );
                root.postDelayed(this,16);
            }
        };
        root.post(cave3DSync);
    }

    void bindMove(Button b,float x,float y){
        b.setOnTouchListener((v,e)->{
            if(scene!=Scene.CAVE||caveWorld==null)return false;
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                caveWorld.setMove(x,y);
                v.setScaleX(.94f);v.setScaleY(.94f);
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){
                caveWorld.setMove(0,0);
                v.setScaleX(1f);v.setScaleY(1f);
                return true;
            }
            return true;
        });
    }

    void updateCaveHud(){
        if(caveWorld==null||caveStats==null)return;
        caveRoomText.setText("CRYSTAL CAVE  //  "+caveWorld.roomName());
        String msg=caveWorld.message();
        String objective=caveWorld.objectiveText();
        if(msg!=null&&!msg.isEmpty()&&!msg.startsWith("Entered ")){
            caveObjective.setText(objective+"  •  "+msg);
        }else caveObjective.setText(objective);
        caveStats.setText(caveWorld.statsText()+"   |   "+caveWorld.abilityText());

        boolean atk=caveWorld.canAttack();
        caveAttack.setEnabled(atk);
        caveAttack.setAlpha(atk?1f:.46f);
        caveAttack.setText(caveWorld.attackButtonText());

        boolean friend=caveWorld.canBefriend();
        caveBefriend.setEnabled(friend);
        caveBefriend.setAlpha(friend?1f:.42f);

        caveAbsorb.setText(caveWorld.absorbButtonText());
        caveAbsorb.setEnabled(true);
        caveAbsorb.setAlpha(caveWorld.canAbsorb()?1f:.78f);

        boolean morph=caveWorld.canMorph();
        caveMorph.setEnabled(morph);
        caveMorph.setAlpha(morph?1f:.38f);
        caveMorph.setText(caveWorld.morphButtonText());

        caveContinue.setVisibility(caveWorld.canExitCave()?View.VISIBLE:View.GONE);
    }

    void showComplete(){
        canvas.setVisibility(View.VISIBLE);
        scene=Scene.COMPLETE;
        clearOverlay();
        canvas.setScene(scene);

        LinearLayout p=card();
        int width=Math.min(dp(660),screenW()-dp(48));
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(width,-2,Gravity.CENTER);
        p.addView(text("MATERIALIZATION COMPLETE",13,CYAN),new LinearLayout.LayoutParams(-1,dp(32)));
        p.addView(text("SOUL VESSEL LOCKED",26,Color.WHITE),new LinearLayout.LayoutParams(-1,dp(52)));
        p.addView(text("Painted cave test complete. The mite and Abyss Weaver absorption/morph loop is working through the Silken Nest.",13,Color.rgb(190,226,239)),
            new LinearLayout.LayoutParams(-1,dp(70)));
        Button replay=button("REPLAY TEST",CYAN,false);
        p.addView(replay,new LinearLayout.LayoutParams(-1,dp(52)));
        replay.setOnClickListener(v->showDeath());
        root.addView(p,fp);
    }

    void saveCreator(){
        prefs.edit()
            .putInt("body",body).putInt("material",material).putInt("color",color).putInt("eyes",eyes)
            .putInt("markings",markings).putInt("core",core)
            .putInt("alpha",alpha).putInt("aura",aura).apply();
    }

    void loadCreator(){
        body=prefs==null?0:Math.min(2,prefs.getInt("body",0));
        material=prefs==null?0:prefs.getInt("material",0);
        color=prefs==null?0:prefs.getInt("color",0);
        eyes=prefs==null?0:prefs.getInt("eyes",0);
        markings=prefs==null?1:prefs.getInt("markings",1);
        core=prefs==null?0:prefs.getInt("core",0);
        alpha=prefs==null?2:prefs.getInt("alpha",2);
        aura=prefs==null?2:prefs.getInt("aura",2);
    }

    @Override public void onBackPressed(){
        if(scene==Scene.CAVE){ showCreator(); return; }
        if(scene==Scene.CREATOR){ showRebirth(); return; }
        if(scene==Scene.REBIRTH){ showDeath(); return; }
        if(scene==Scene.COMPLETE){ showCave(); return; }
        super.onBackPressed();
    }

    class SoulCanvas extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
        final Random prng=new Random(77);
        final float[] px=new float[90], py=new float[90], ps=new float[90];
        Scene drawScene=Scene.DEATH;
        long reactionStart=0, corePulseStart=0;
        boolean fullSpin=false;

        float moveX=0,moveY=0,playerX=-1,playerY=-1,enemyX=-1,enemyY=-1;
        float enemyVx=0,enemyVy=0,lastFacing=1f;
        int enemyHp=5,caveCrystals=0;
        final boolean[] crystalTaken=new boolean[5];
        final float[][] crystalNorm={{.22f,.66f},{.39f,.34f},{.57f,.72f},{.74f,.42f},{.86f,.66f}};
        long lastFrame=0,attackStart=0,enemyHitStart=0,absorbStart=0,playerTrailStart=0;
        boolean caveInitialized=false;
        Bitmap caveBg,caveMid,caveFg,crystalSprite,caveMiteSprite,slashSprite,impactSprite,absorbSprite,masterSlime;

        SoulCanvas(Context c){
            super(c);
            setLayerType(View.LAYER_TYPE_HARDWARE,null);
            stroke.setStyle(Paint.Style.STROKE);
            for(int i=0;i<px.length;i++){
                px[i]=prng.nextFloat(); py[i]=prng.nextFloat(); ps[i]=1f+prng.nextFloat()*3f;
            }
            caveBg=BitmapFactory.decodeResource(getResources(),R.drawable.cave_bg);
            caveMid=BitmapFactory.decodeResource(getResources(),R.drawable.cave_mid);
            caveFg=BitmapFactory.decodeResource(getResources(),R.drawable.cave_fg);
            crystalSprite=BitmapFactory.decodeResource(getResources(),R.drawable.crystal_blue);
            caveMiteSprite=BitmapFactory.decodeResource(getResources(),R.drawable.cave_mite);
            slashSprite=BitmapFactory.decodeResource(getResources(),R.drawable.magic_slash);
            impactSprite=BitmapFactory.decodeResource(getResources(),R.drawable.impact_burst);
            absorbSprite=BitmapFactory.decodeResource(getResources(),R.drawable.absorb_ring);
            masterSlime=BitmapFactory.decodeResource(getResources(),R.drawable.slime_master);
        }

        void setScene(Scene s){ drawScene=s; reactionStart=0; invalidate(); }
        void react(boolean spin){ reactionStart=SystemClock.uptimeMillis(); fullSpin=spin; invalidate(); }
        void pulseCore(){ corePulseStart=SystemClock.uptimeMillis(); invalidate(); }

        void startCave(){
            setScene(Scene.CAVE);
            caveInitialized=false;
            Arrays.fill(crystalTaken,false);
            caveCrystals=0;
            enemyHp=5;
            moveX=moveY=0;
            attackStart=enemyHitStart=absorbStart=0;
            lastFrame=0;
        }

        void setMove(float x,float y){
            moveX=x;moveY=y;
            if(Math.abs(x)>.01f)lastFacing=Math.signum(x);
        }

        void attack(){
            if(drawScene!=Scene.CAVE)return;
            long now=SystemClock.uptimeMillis();
            if(now-attackStart<420)return;
            attackStart=now;
            float dx=enemyX-playerX,dy=enemyY-playerY;
            float dist=(float)Math.sqrt(dx*dx+dy*dy);
            float range=Math.max(120,getHeight()*.26f);
            boolean inFront=(lastFacing>=0?dx>-40:dx<40);
            if(enemyHp>0&&dist<range&&inFront){
                enemyHp=Math.max(0,enemyHp-1);
                enemyHitStart=now;
                enemyVx=lastFacing*getWidth()*.12f;
                enemyVy=-getHeight()*.045f;
                runOnUiThread(()->updateCaveHud());
            }
            invalidate();
        }

        void absorbPulse(){
            if(drawScene!=Scene.CAVE)return;
            absorbStart=SystemClock.uptimeMillis();
            float radius=Math.max(120,getHeight()*.25f);
            for(int i=0;i<crystalNorm.length;i++){
                if(crystalTaken[i])continue;
                float cx=crystalNorm[i][0]*getWidth(),cy=crystalNorm[i][1]*getHeight();
                float dx=cx-playerX,dy=cy-playerY;
                if(dx*dx+dy*dy<=radius*radius){
                    crystalTaken[i]=true;caveCrystals++;
                }
            }
            runOnUiThread(()->updateCaveHud());
            invalidate();
        }

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            int w=getWidth(),h=getHeight();
            if(w<=0||h<=0)return;
            long now=SystemClock.uptimeMillis();
            if(drawScene==Scene.DEATH) drawDeath(c,w,h,now);
            else if(drawScene==Scene.REBIRTH) drawAnalysis(c,w,h,now,w*.5f,h*.48f,true);
            else if(drawScene==Scene.CREATOR){
                // 3D prototype: keep only the analysis-space backdrop here.
                // The slime itself is rendered exclusively by Slime3DView.
                drawAnalysis(c,w,h,now,w*.29f,h*.52f,false);
            }else if(drawScene==Scene.CAVE) drawCaveGameplay(c,w,h,now);
            else drawCave(c,w,h,now);
            postInvalidateDelayed(16);
        }

        void drawDeath(Canvas c,int w,int h,long now){
            p.setShader(new LinearGradient(0,0,0,h,Color.rgb(5,9,17),Color.rgb(12,28,43),Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p); p.setShader(null);

            int base=(int)(h*.58f);
            p.setColor(Color.rgb(10,21,34));
            for(int i=0;i<10;i++){
                float bw=w/10f+12;
                float bh=h*(.22f+.22f*((i*37)%100)/100f);
                c.drawRect(i*w/10f,base-bh,i*w/10f+bw,base,p);
                p.setColor(Color.argb(95,105,190,220));
                for(float yy=base-bh+18;yy<base-12;yy+=34){
                    for(float xx=i*w/10f+15;xx<i*w/10f+bw-10;xx+=28){
                        if((((int)xx+(int)yy)/11)%4==0)c.drawRect(xx,yy,xx+6,yy+9,p);
                    }
                }
                p.setColor(Color.rgb(10,21,34));
            }

            Path road=new Path();
            road.moveTo(0,base); road.lineTo(w,base); road.lineTo(w,h); road.lineTo(0,h); road.close();
            p.setColor(Color.rgb(4,13,22)); c.drawPath(road,p);
            p.setColor(Color.argb(120,220,230,235));
            c.drawLine(w*.42f,h,w*.48f,base,p);
            c.drawLine(w*.60f,h,w*.53f,base,p);

            float pulse=.75f+.25f*(float)Math.sin(now/260.0);
            drawGlow(c,w*.49f,base+10,Math.min(w,h)*.14f,Color.argb((int)(140*pulse),255,235,185));
            drawGlow(c,w*.57f,base+10,Math.min(w,h)*.14f,Color.argb((int)(140*pulse),255,235,185));

            p.setStrokeWidth(1.5f);
            float off=(now%900)/900f*h;
            for(int i=0;i<120;i++){
                float x=px[i%px.length]*w;
                float y=(py[i%py.length]*h+off)%h;
                p.setColor(Color.argb(55+(i%5)*8,160,218,238));
                c.drawLine(x,y,x-5,y+14+ps[i%ps.length]*3,p);
            }
        }

        void drawAnalysis(Canvas c,int w,int h,long now,float cx,float cy,boolean centered){
            p.setShader(new LinearGradient(0,0,w,h,Color.rgb(4,24,42),Color.rgb(9,76,101),Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p); p.setShader(null);

            float rad=Math.max(w,h)*.7f;
            p.setShader(new RadialGradient(cx,cy,rad,
                new int[]{Color.argb(150,98,224,247),Color.argb(65,25,119,155),Color.TRANSPARENT},
                new float[]{0f,.32f,1f},Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p); p.setShader(null);

            p.setStrokeWidth(1f);
            p.setColor(Color.argb(30,110,220,244));
            int grid=Math.max(42,w/24);
            for(int x=0;x<w;x+=grid)c.drawLine(x,0,x,h,p);
            for(int y=0;y<h;y+=grid)c.drawLine(0,y,w,y,p);

            float phase=(now%8000)/8000f*360f;
            float maxR=Math.min(w,h)*.48f;
            for(int i=0;i<6;i++){
                float rr=maxR*(.30f+i*.12f);
                stroke.setStrokeWidth(i<2?4f:2f);
                stroke.setColor(Color.argb(155-i*18,155,244,255));
                RectF oval=new RectF(cx-rr,cy-rr,cx+rr,cy+rr);
                for(int s=0;s<5;s++) c.drawArc(oval,phase+i*13+s*72,34+(s%2)*12,false,stroke);
            }

            for(int i=0;i<px.length;i++){
                float x=px[i]*w,y=py[i]*h;
                p.setColor(Color.argb(70+(i%4)*16,165,247,255));
                float sz=ps[i];
                if(i%3==0)c.drawRect(x,y,x+sz*2,y+sz*2,p); else c.drawCircle(x,y,sz,p);
            }

            long age=now-corePulseStart;
            if(age<500){
                float t=age/500f;
                float rr=(.05f+.14f*t)*Math.min(w,h);
                stroke.setStrokeWidth(5);
                stroke.setColor(Color.argb((int)(220*(1-t)),220,255,255));
                c.drawCircle(cx,cy,rr,stroke);
            }
        }

        void drawSlime(Canvas c,int w,int h,long now){
            float cx=w*.29f, cy=h*.56f;
            float r=Math.min(w*.18f,h*.34f);
            drawAnimeSlime(c,cx,cy,r,now,0,0,false);
        }

        void drawAnimeSlime(Canvas c,float cx,float cy,float r,long now,float vx,float vy,boolean gameplay){
            // v7: the uploaded painted slime illustration is the master art.
            if(masterSlime==null){
                p.setColor(slimeColor());
                c.drawCircle(cx,cy,r,p);
                return;
            }

            float speed=(float)Math.sqrt(vx*vx+vy*vy);
            float idle=(float)Math.sin(now/360.0);
            float hop=gameplay&&speed>.01f?Math.abs((float)Math.sin(now/115.0))*r*.08f:0f;
            float sx=1f+.024f*idle;
            float sy=1f-.018f*idle;
            float rot=0f;

            // Creator body choice changes the painted silhouette only through subtle deformation,
            // preserving all original painted detail.
            if(body==1){ sx*=.91f; sy*=1.10f; }
            else if(body==2){ sx*=1.13f; sy*=.91f; }
            else if(body==3){ sx*=.97f; sy*=1.05f; rot=-2.5f; }

            if(gameplay&&speed>.01f){
                float lean=Math.max(-1f,Math.min(1f,vx/Math.max(1f,getWidth()*.25f)));
                rot+=lean*6.5f;
                sx*=1.04f; sy*=.965f;
            }

            long age=now-reactionStart;
            if(!gameplay&&age>=0&&age<520){
                float t=age/520f;
                float wave=(float)Math.sin(Math.PI*t);
                sx*=1f+.10f*wave; sy*=1f-.09f*wave;
                rot+=fullSpin?360f*t:10f*(float)Math.sin(Math.PI*2*t);
            }

            float auraA=new float[]{.20f,.42f,.62f,.82f,1f}[Math.max(0,Math.min(4,aura))];
            int accent=slimeColor();
            for(int i=4;i>=1;i--){
                stroke.setStrokeWidth(2+i*2.2f);
                stroke.setColor(Color.argb((int)(22*auraA*i),Color.red(accent),Color.green(accent),Color.blue(accent)));
                c.drawCircle(cx,cy-hop,r*(1.02f+i*.085f),stroke);
            }
            drawGlow(c,cx,cy-hop,r*1.28f,Color.argb((int)(35+45*auraA),Color.red(accent),Color.green(accent),Color.blue(accent)));

            c.save();
            c.translate(cx,cy-hop);
            c.rotate(rot);
            c.scale(sx,sy);

            RectF dst=new RectF(-r,-r,r,r);
            p.setAlpha(new int[]{155,185,220,245}[Math.max(0,Math.min(3,alpha))]);
            p.setColorFilter(slimePaintFilter(color));
            c.drawBitmap(masterSlime,null,dst,p);
            p.setColorFilter(null);
            p.setAlpha(255);
