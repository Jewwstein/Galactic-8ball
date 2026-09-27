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
    enum Scene { DEATH, REBIRTH, CREATOR, COMPLETE }

    static final int CYAN = Color.rgb(92, 226, 255);
    static final int CYAN_BRIGHT = Color.rgb(205, 250, 255);
    static final int PANEL = Color.argb(224, 6, 27, 46);
    static final int PANEL_SOFT = Color.argb(188, 8, 47, 70);

    final Random rng = new Random();
    FrameLayout root;
    SoulCanvas canvas;
    Scene scene = Scene.DEATH;
    SharedPreferences prefs;

    int body = 0, color = 0, eyes = 0, markings = 1, core = 0, alpha = 2, aura = 2;
    String creatorCategory = "BODY";
    String selectedSkill = "";
    String deathDescription = "";
    int selectedSkillIndex = -1;
    Skill[] currentSkills = new Skill[3];

    LinearLayout optionsContainer;
    TextView creatorStatus;
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

    static final String[] BODY = {"ROUND CORE","DROPLET","WIDE FORM","CREST FORM"};
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
        while(root.getChildCount()>1) root.removeViewAt(1);
        categoryButtons.clear();
        optionsContainer=null;
        creatorStatus=null;
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
            color=rng.nextInt(COLOR.length);
            eyes=rng.nextInt(EYES.length);
            markings=rng.nextInt(MARK.length);
            core=rng.nextInt(CORE.length);
            alpha=rng.nextInt(ALPHA.length);
            aura=rng.nextInt(AURA.length);
            rebuildOptions();
            canvas.react(true);
            creatorStatus.setText("RANDOM SOUL FORM GENERATED.");
        });
        save.setOnClickListener(v->{
            saveCreator();
            creatorStatus.setText("PROFILE SAVED.");
        });
        lock.setOnClickListener(v->{
            saveCreator();
            showComplete();
        });

        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(panelWidth,-1,Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        fp.setMargins(dp(12),dp(14),dp(18),dp(14));
        root.addView(scroll,fp);
    }

    void buildCategoryButtons(LinearLayout parent){
        String[] cats={"BODY","COLOR","EYES","MARKINGS","SOUL CORE","TRANSPARENCY","AURA"};
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
                    if(creatorStatus!=null) creatorStatus.setText("SELECTED // "+optionNames()[idx]);
                });
            }
            LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,dp(42));
            if(i>0) rlp.topMargin=dp(6);
            optionsContainer.addView(row,rlp);
        }
        canvas.invalidate();
    }

    void showComplete(){
        scene=Scene.COMPLETE;
        clearOverlay();
        canvas.setScene(scene);

        LinearLayout p=card();
        int width=Math.min(dp(660),screenW()-dp(48));
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(width,-2,Gravity.CENTER);
        p.addView(text("MATERIALIZATION COMPLETE",13,CYAN),new LinearLayout.LayoutParams(-1,dp(32)));
        p.addView(text("SOUL VESSEL LOCKED",26,Color.WHITE),new LinearLayout.LayoutParams(-1,dp(52)));
        p.addView(text("Starting skill: "+prefs.getString("starting_skill","UNASSIGNED")+"\nYour next milestone is awakening inside the Crystal Cave.",13,Color.rgb(190,226,239)),
            new LinearLayout.LayoutParams(-1,dp(70)));
        Button replay=button("REPLAY OPENING",CYAN,false);
        p.addView(replay,new LinearLayout.LayoutParams(-1,dp(52)));
        replay.setOnClickListener(v->showDeath());
        root.addView(p,fp);
    }

    void saveCreator(){
        prefs.edit()
            .putInt("body",body).putInt("color",color).putInt("eyes",eyes)
            .putInt("markings",markings).putInt("core",core)
            .putInt("alpha",alpha).putInt("aura",aura).apply();
    }

    void loadCreator(){
        body=prefs==null?0:prefs.getInt("body",0);
        color=prefs==null?0:prefs.getInt("color",0);
        eyes=prefs==null?0:prefs.getInt("eyes",0);
        markings=prefs==null?1:prefs.getInt("markings",1);
        core=prefs==null?0:prefs.getInt("core",0);
        alpha=prefs==null?2:prefs.getInt("alpha",2);
        aura=prefs==null?2:prefs.getInt("aura",2);
    }

    @Override public void onBackPressed(){
        if(scene==Scene.CREATOR){ showRebirth(); return; }
        if(scene==Scene.REBIRTH){ showDeath(); return; }
        if(scene==Scene.COMPLETE){ showCreator(); return; }
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

        SoulCanvas(Context c){
            super(c);
            setLayerType(View.LAYER_TYPE_HARDWARE,null);
            stroke.setStyle(Paint.Style.STROKE);
            for(int i=0;i<px.length;i++){
                px[i]=prng.nextFloat(); py[i]=prng.nextFloat(); ps[i]=1f+prng.nextFloat()*3f;
            }
        }

        void setScene(Scene s){ drawScene=s; reactionStart=0; invalidate(); }
        void react(boolean spin){ reactionStart=SystemClock.uptimeMillis(); fullSpin=spin; invalidate(); }
        void pulseCore(){ corePulseStart=SystemClock.uptimeMillis(); invalidate(); }

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            int w=getWidth(),h=getHeight();
            if(w<=0||h<=0)return;
            long now=SystemClock.uptimeMillis();
            if(drawScene==Scene.DEATH) drawDeath(c,w,h,now);
            else if(drawScene==Scene.REBIRTH) drawAnalysis(c,w,h,now,w*.5f,h*.48f,true);
            else if(drawScene==Scene.CREATOR){
                drawAnalysis(c,w,h,now,w*.29f,h*.52f,false);
                drawSlime(c,w,h,now);
            }else drawCave(c,w,h,now);
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
            float breath=1f+.018f*(float)Math.sin(now/420.0);
            float sx=breath, sy=2f-breath, rot=0;

            long age=now-reactionStart;
            if(age>=0 && age<520){
                float t=age/520f;
                float wave=(float)Math.sin(Math.PI*t);
                sx*=1f+.11f*wave;
                sy*=1f-.13f*wave;
                rot=fullSpin?360f*t:14f*(float)Math.sin(Math.PI*2*t);
            }

            c.save();
            c.translate(cx,cy);
            c.rotate(rot);
            c.scale(sx,sy);

            float auraA=new float[]{.20f,.42f,.62f,.82f,1f}[Math.max(0,Math.min(4,aura))];
            for(int i=3;i>=1;i--){
                stroke.setStrokeWidth(3+i*2);
                stroke.setColor(Color.argb((int)(42*auraA*i),90,235,255));
                c.drawCircle(0,0,r*(1.00f+i*.10f),stroke);
            }

            Path shape=slimePath(r);
            int baseColor=slimeColor();
            int bodyA=new int[]{140,173,209,235}[Math.max(0,Math.min(3,alpha))];
            int light=blend(baseColor,Color.WHITE,.42f);
            int dark=blend(baseColor,Color.BLACK,.38f);
            p.setShader(new RadialGradient(-r*.32f,-r*.42f,r*1.45f,
                new int[]{Color.argb(bodyA,Color.red(light),Color.green(light),Color.blue(light)),
                          Color.argb(bodyA,Color.red(baseColor),Color.green(baseColor),Color.blue(baseColor)),
                          Color.argb(bodyA,Color.red(dark),Color.green(dark),Color.blue(dark))},
                new float[]{0f,.52f,1f},Shader.TileMode.CLAMP));
            c.drawPath(shape,p); p.setShader(null);

            c.save();
            c.clipPath(shape);
            p.setColor(Color.argb(86,255,255,255));
            c.drawOval(new RectF(-r*.58f,-r*.68f,r*.12f,-r*.30f),p);
            p.setColor(Color.argb(48,255,255,255));
            c.drawOval(new RectF(-r*.72f,-r*.26f,-r*.42f,-r*.06f),p);
            c.restore();

            drawMarkings(c,r);
            drawCore(c,r);
            drawEyes(c,r);

            stroke.setStrokeWidth(3.5f);
            stroke.setColor(Color.argb(185,198,247,255));
            c.drawPath(shape,stroke);

            c.restore();
        }

        Path slimePath(float r){
            Path q=new Path();
            if(body==1){
                q.moveTo(0,-r*.95f);
                q.cubicTo(r*.30f,-r*.62f,r*.72f,-r*.18f,r*.74f,r*.30f);
                q.cubicTo(r*.78f,r*.82f,r*.38f,r,0,r);
                q.cubicTo(-r*.38f,r,-r*.78f,r*.82f,-r*.74f,r*.30f);
                q.cubicTo(-r*.72f,-r*.18f,-r*.30f,-r*.62f,0,-r*.95f);
            }else if(body==2){
                q.moveTo(-r*.88f,r*.32f);
                q.cubicTo(-r*.92f,-r*.48f,-r*.42f,-r*.78f,0,-r*.80f);
                q.cubicTo(r*.42f,-r*.78f,r*.92f,-r*.48f,r*.88f,r*.32f);
                q.cubicTo(r*.80f,r*.87f,r*.28f,r*.96f,0,r*.96f);
                q.cubicTo(-r*.28f,r*.96f,-r*.80f,r*.87f,-r*.88f,r*.32f);
            }else if(body==3){
                q.moveTo(-r*.70f,r*.58f);
                q.cubicTo(-r*.84f,-r*.20f,-r*.48f,-r*.70f,-r*.24f,-r*.62f);
                q.lineTo(-r*.10f,-r*.95f);
                q.lineTo(r*.08f,-r*.64f);
                q.lineTo(r*.30f,-r*.98f);
                q.lineTo(r*.38f,-r*.58f);
                q.cubicTo(r*.72f,-r*.48f,r*.84f,.0f,r*.70f,r*.58f);
                q.cubicTo(r*.48f,r*.95f,-r*.48f,r*.95f,-r*.70f,r*.58f);
            }else{
                q.moveTo(-r*.74f,r*.46f);
                q.cubicTo(-r*.86f,-r*.28f,-r*.46f,-r*.82f,0,-r*.82f);
                q.cubicTo(r*.46f,-r*.82f,r*.86f,-r*.28f,r*.74f,r*.46f);
                q.cubicTo(r*.64f,r*.92f,-r*.64f,r*.92f,-r*.74f,r*.46f);
            }
            q.close(); return q;
        }

        int slimeColor(){
            int[] colors={
                Color.rgb(44,184,255),Color.rgb(149,87,255),Color.rgb(255,91,56),
                Color.rgb(63,214,133),Color.rgb(222,241,250),Color.rgb(50,42,78)
            };
            return colors[Math.max(0,Math.min(colors.length-1,color))];
        }

        void drawEyes(Canvas c,float r){
            float y=-r*.12f, dx=r*.25f;
            if(eyes==2){
                for(int s=-1;s<=1;s+=2){
                    stroke.setStrokeWidth(5);
                    stroke.setColor(Color.rgb(175,92,255));
                    c.drawCircle(s*dx,y,r*.12f,stroke);
                    p.setColor(Color.rgb(17,8,31));c.drawCircle(s*dx,y,r*.095f,p);
                    p.setColor(Color.WHITE);c.drawCircle(s*dx-r*.025f,y-r*.030f,r*.025f,p);
                }
            }else if(eyes==1){
                p.setColor(Color.rgb(6,15,29));
                Path l=new Path();l.moveTo(-dx-r*.13f,y-r*.03f);l.lineTo(-dx+r*.12f,y-r*.10f);l.lineTo(-dx+r*.08f,y+r*.11f);l.lineTo(-dx-r*.11f,y+r*.08f);l.close();c.drawPath(l,p);
                Path rr=new Path();rr.moveTo(dx+r*.13f,y-r*.03f);rr.lineTo(dx-r*.12f,y-r*.10f);rr.lineTo(dx-r*.08f,y+r*.11f);rr.lineTo(dx+r*.11f,y+r*.08f);rr.close();c.drawPath(rr,p);
                p.setColor(CYAN_BRIGHT);c.drawCircle(-dx,y,r*.026f,p);c.drawCircle(dx,y,r*.026f,p);
            }else if(eyes==3){
                p.setColor(Color.rgb(255,231,112));
                drawStar(c,-dx,y,r*.12f,p);drawStar(c,dx,y,r*.12f,p);
            }else{
                p.setColor(Color.rgb(5,16,30));
                c.drawOval(new RectF(-dx-r*.10f,y-r*.14f,-dx+r*.10f,y+r*.14f),p);
                c.drawOval(new RectF(dx-r*.10f,y-r*.14f,dx+r*.10f,y+r*.14f),p);
                p.setColor(Color.WHITE);
                c.drawCircle(-dx-r*.03f,y-r*.05f,r*.030f,p);
                c.drawCircle(dx-r*.03f,y-r*.05f,r*.030f,p);
            }
        }

        void drawMarkings(Canvas c,float r){
            if(markings==0)return;
            if(markings==1){
                stroke.setStrokeWidth(5);
                stroke.setColor(Color.argb(170,225,253,255));
                RectF a=new RectF(-r*.24f,-r*.48f,r*.24f,0);
                c.drawArc(a,205,130,false,stroke);
                c.drawLine(0,-r*.55f,0,-r*.35f,stroke);
            }else if(markings==2){
                p.setColor(Color.argb(110,244,255,255));
                for(int i=0;i<16;i++){
                    float a=i*.87f;
                    float rr=r*(.18f+.42f*((i*31)%100)/100f);
                    c.drawCircle((float)Math.cos(a)*rr,(float)Math.sin(a)*rr+r*.16f,2+(i%4),p);
                }
            }else{
                stroke.setStrokeWidth(7);
                stroke.setColor(Color.argb(150,220,252,255));
                c.drawArc(new RectF(-r*.46f,r*.08f,r*.46f,r*.84f),205,130,false,stroke);
            }
        }

        void drawCore(Canvas c,float r){
            if(core==3)return;
            float x=0,y=r*.38f;
            drawGlow(c,x,y,r*.19f,Color.argb(100,120,244,255));
            if(core==1){
                p.setColor(Color.rgb(255,223,83));drawStar(c,x,y,r*.11f,p);
            }else if(core==2){
                p.setColor(Color.rgb(235,247,255));c.drawCircle(x,y,r*.105f,p);
                p.setColor(blend(slimeColor(),Color.BLACK,.25f));c.drawCircle(x+r*.052f,y-r*.018f,r*.095f,p);
            }else{
                p.setColor(Color.rgb(131,246,255));c.drawCircle(x,y,r*.105f,p);
                stroke.setStrokeWidth(3);stroke.setColor(Color.WHITE);c.drawCircle(x,y,r*.105f,stroke);
                p.setColor(Color.WHITE);c.drawCircle(x-r*.032f,y-r*.035f,r*.026f,p);
            }
        }

        void drawCave(Canvas c,int w,int h,long now){
            p.setShader(new LinearGradient(0,0,0,h,Color.rgb(2,8,16),Color.rgb(7,28,42),Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);p.setShader(null);
            drawGlow(c,w*.5f,h*.38f,Math.min(w,h)*.28f,Color.argb(90,80,221,255));
            p.setColor(Color.rgb(3,11,18));
            Path top=new Path();top.moveTo(0,0);top.lineTo(w,0);top.lineTo(w,h*.16f);
            for(int i=0;i<10;i++) top.lineTo(w-i*w/9f,h*(.15f+.18f*((i*17)%100)/100f));
            top.lineTo(0,h*.22f);top.close();c.drawPath(top,p);
            p.setColor(Color.rgb(4,17,25));c.drawRect(0,h*.72f,w,h,p);
        }

        void drawGlow(Canvas c,float x,float y,float r,int color){
            int transparent=Color.argb(0,Color.red(color),Color.green(color),Color.blue(color));
            p.setShader(new RadialGradient(x,y,r,new int[]{color,transparent},null,Shader.TileMode.CLAMP));
            c.drawCircle(x,y,r,p);p.setShader(null);
        }

        void drawStar(Canvas c,float x,float y,float r,Paint paint){
            Path s=new Path();
            for(int i=0;i<10;i++){
                float rr=(i%2==0)?r:r*.44f;
                double a=-Math.PI/2+i*Math.PI/5;
                float xx=x+(float)Math.cos(a)*rr, yy=y+(float)Math.sin(a)*rr;
                if(i==0)s.moveTo(xx,yy); else s.lineTo(xx,yy);
            }
            s.close();c.drawPath(s,paint);
        }

        int blend(int a,int b,float t){
            int r=(int)(Color.red(a)*(1-t)+Color.red(b)*t);
            int g=(int)(Color.green(a)*(1-t)+Color.green(b)*t);
            int bl=(int)(Color.blue(a)*(1-t)+Color.blue(b)*t);
            return Color.rgb(r,g,bl);
        }
    }
}
