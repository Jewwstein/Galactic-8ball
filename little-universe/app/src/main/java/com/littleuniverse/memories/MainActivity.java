package com.littleuniverse.memories;

import android.app.*;
import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.media.MediaPlayer;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.os.*;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.graphics.*;
import android.graphics.Shader;
import android.graphics.RadialGradient;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

public final class MainActivity extends Activity {
    private static final int BACKGROUND=0xff090F25, PANEL=0xff171E43, PANEL2=0xff29275A;
    private static final int TEXT=0xffF4F3FF, MUTED=0xffB9B6E6, GOLD=0xffC0A6FF, MINT=0xff74BFFF, ROSE=0xffDCA8FF;
    private static final int PICK_MEDIA=101, EXPORT=102, IMPORT=103;
    private MediaRecorder recorder;
    private MediaPlayer player;
    private SpeechRecognizer speechRecognizer;
    private File recordingFile;
    private String recordingFor;
    private Runnable afterMicPermission;
    private AlertDialog recordingDialog;
    private android.os.Handler recordHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean isRecording=false;
    private long recordingStart=0L;
    private static final int REQUEST_MIC=104;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final android.os.Handler familySyncHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable familySyncTimer=new Runnable(){
        @Override public void run(){
            if(FamilyHubSync.paired(MainActivity.this))requestFamilyHubSync(false);
            familySyncHandler.postDelayed(this,150000);
        }
    };
    private SQLiteDatabase db;
    private KeepsakeHub studio;
    private static final int EXPORT_PDF=120, EXPORT_FAMILY=121, SAVE_MEDIA_AS=122, VERIFY_BACKUP=123;
    private String pendingExportPath;
    private LinearLayout page;
    private String activeTab="Home", pendingMediaId;
    private android.content.SharedPreferences prefs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xff090F25);
        getWindow().setNavigationBarColor(0xff090F25);
        getWindow().getDecorView().setSystemUiVisibility(0);
        prefs=getSharedPreferences("little_universe_profile",MODE_PRIVATE);
        db=new Store(this).getWritableDatabase();
        FeatureStore.init(db);
        ArchiveSafety.init(db);
        studio=new KeepsakeHub(this,db,new KeepsakeHub.Actions(){
            public void create(String kind,String title){editor(null,kind,false,title);}
            public void edit(String id){Memory m=load(id);if(m!=null)editor(m,m.kind,false);}
            public void refresh(){render();}
            public void exportPdf(){startPdfExport();}
        });
        new File(getFilesDir(),"media").mkdirs();
        render();
        for(FeatureStore.Item reminder:FeatureStore.items(db,"Reminder"))ReminderReceiver.schedule(this,reminder);
        getWindow().getDecorView().post(()->processFamilyInvitation(getIntent()));
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        processFamilyInvitation(intent);
    }
    private void processFamilyInvitation(Intent intent){
        if(intent==null||intent.getData()==null)return;
        Uri uri=intent.getData();
        if(!"jewwstein.github.io".equalsIgnoreCase(uri.getHost()))return;
        String fragment=uri.getEncodedFragment();
        if(fragment==null||!fragment.startsWith("join="))return;
        try{
            String text=java.net.URLDecoder.decode(fragment.substring(5),"UTF-8");
            String[] parts=text.split("\\|",3);
            if(parts.length<2||!parts[0].startsWith("https://"))return;
            String label=parts.length>2?parts[2]:"Family member";
            LinearLayout layout=vertical(12);
            put(layout,text("You've been invited to your family's private memory archive. No additional applications are required.",14,MUTED,false),0,12);
            EditText name=input(label,"Your name",false);
            put(layout,name,0,10);
            new AlertDialog.Builder(this).setTitle("♡ Join Our Little Universe")
                .setView(layout).setNegativeButton("Not now",null)
                .setPositiveButton("Join my family",(d,w)->
                    pairWithHub(parts[0],parts[1],name.getText().toString())).show();
        }catch(Exception problem){
            message("Could not open this family invitation.");
        }
    }
    @Override protected void onResume(){
        super.onResume();
        familySyncHandler.removeCallbacks(familySyncTimer);
        familySyncHandler.postDelayed(familySyncTimer,1500);
    }
    @Override protected void onPause(){
        familySyncHandler.removeCallbacks(familySyncTimer);
        super.onPause();
    }
    private void requestFamilyHubSync(boolean showResult){
        if(!FamilyHubSync.paired(this)){
            if(showResult)message("Pair your phone with the family PC first.");
            return;
        }
        if(showResult)message("Synchronizing with the family PC…");
        io.execute(()->{
            try{
                FamilyHubSync.Result result=FamilyHubSync.sync(getApplicationContext(),db);
                runOnUiThread(()->{
                    if(result.received>0)render();
                    if(showResult||result.received>0)message(result.summary());
                });
            }catch(Exception error){
                FamilyHubSync.error(getApplicationContext(),error);
                if(showResult)runOnUiThread(()->message("Family PC unavailable: "+error.getMessage()));
            }
        });
    }
    private void pairWithHub(String address,String code,String label){
        message("Securing your private connection to the family PC…");
        io.execute(()->{
            try{
                FamilyHubSync.pair(getApplicationContext(),address,code,label);
                runOnUiThread(()->{
                    message("Paired! Checking for new family memories…");
                    requestFamilyHubSync(true);
                });
            }catch(Exception error){
                runOnUiThread(()->new AlertDialog.Builder(this)
                    .setTitle("Could not connect to your family PC")
                    .setMessage(error.getMessage()+"\n\nMake sure the Windows Family Hub is on, remote access is enabled, and your invitation is valid.")
                    .setPositiveButton("OK",null).show());
            }
        });
    }
    private static final class Store extends SQLiteOpenHelper {
        Store(Context c){super(c,"little_universe_v1.db",null,1);}
        @Override public void onCreate(SQLiteDatabase d) {
            d.execSQL("CREATE TABLE memories (id TEXT PRIMARY KEY,day TEXT NOT NULL,title TEXT NOT NULL,kind TEXT NOT NULL,body TEXT NOT NULL,media TEXT NOT NULL)");
        }
        @Override public void onUpgrade(SQLiteDatabase d,int from,int to){}
    }
    private static final class Memory {
        String id,day,title,kind,body;
        ArrayList<String> media=new ArrayList<>();
        Memory(){id=UUID.randomUUID().toString();day="2026-10-31";title="";kind="Memory";body="";}
        JSONObject json() throws JSONException {
            JSONObject j=new JSONObject();j.put("id",id);j.put("day",day);j.put("title",title);j.put("kind",kind);j.put("body",body);
            j.put("media",new JSONArray(media));return j;
        }
        static Memory from(JSONObject j) throws JSONException {
            Memory m=new Memory();m.id=j.getString("id");m.day=j.getString("day");m.title=j.getString("title");
            m.kind=j.getString("kind");m.body=j.optString("body","");
            JSONArray a=j.optJSONArray("media");if(a!=null)for(int i=0;i<a.length();i++)m.media.add(a.getString(i));
            return m;
        }
    }
    private Memory row(Cursor c){
        Memory m=new Memory();m.id=c.getString(0);m.day=c.getString(1);
        m.title=c.getString(2);m.kind=c.getString(3);m.body=c.getString(4);
        try{JSONArray a=new JSONArray(c.getString(5));for(int i=0;i<a.length();i++)m.media.add(a.getString(i));}
        catch(Exception ignored){}return m;
    }
    private List<Memory> memories(){
        ArrayList<Memory> out=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT id,day,title,kind,body,media FROM memories ORDER BY day DESC,rowid DESC",null)){
            while(c.moveToNext())out.add(row(c));
        }
        return out;
    }
    private Memory load(String id){
        try(Cursor c=db.rawQuery("SELECT id,day,title,kind,body,media FROM memories WHERE id=?",new String[]{id})){
            return c.moveToFirst()?row(c):null;
        }
    }
    private void save(Memory m){
        android.content.ContentValues v=new android.content.ContentValues();
        v.put("id",m.id);v.put("day",m.day);v.put("title",m.title);v.put("kind",m.kind);
        v.put("body",m.body);v.put("media",new JSONArray(m.media).toString());
        db.insertWithOnConflict("memories",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        FeatureStore.claim(this,db,m.id);
        if(FamilyHubSync.paired(this)){
            familySyncHandler.removeCallbacks(familySyncTimer);
            familySyncHandler.postDelayed(familySyncTimer,3000);
        }
    }
    private String today(){return new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}
    private int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}
    private GradientDrawable bg(int color,int radius) {
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;
    }
    private GradientDrawable gradient(int radius,int... colors){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors);d.setCornerRadius(dp(radius));return d;
    }
    private GradientDrawable outlined(int color,int radius,int edge){
        GradientDrawable d=bg(color,radius);d.setStroke(dp(1),edge);return d;
    }
    private TextView headline(String s,int sp){
        TextView t=text(s,sp,TEXT,true);t.setTypeface(Typeface.create("serif",Typeface.BOLD));
        t.setLetterSpacing(-0.025f);return t;
    }
    private TextView eyebrow(String s,int color){
        TextView t=text(s,11,color,true);t.setLetterSpacing(.18f);return t;
    }
    private class Sky extends View {
        private Paint paint=new Paint(3);
        Sky(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight();if(w<1||h<1)return;
            paint.setShader(new RadialGradient(w*.79f,h*.23f,dp(135),
                new int[]{0x773F5E83,0x003F5E83},null,Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,paint);paint.setShader(null);
            java.util.Random rng=new java.util.Random(729);
            for(int i=0;i<70;i++){
                paint.setColor(i%6==0?0xAAFFE3C7:0x77FFFFFF);
                c.drawCircle(w*rng.nextFloat(),h*rng.nextFloat(),dp(i%7==0?1.4f:.6f),paint);
            }
            float x=w*.79f,y=h*.25f,rr=dp(37);
            paint.setColor(0x55F5D1B0);c.drawCircle(x,y,rr*1.35f,paint);
            paint.setColor(0xffF9DFC5);c.drawCircle(x,y,rr,paint);
            paint.setColor(0xff373E60);c.drawCircle(x+dp(20),y-dp(17),rr*.9f,paint);
        }
    }
    private LinearLayout horizontal(){
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;
    }
    private void quickButton(LinearLayout row,String s,boolean primary,Runnable action){
        Button b=button(s,primary,action);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(49),1);
        lp.setMargins(dp(2),0,dp(2),0);row.addView(b,lp);
    }
    private TextView text(String t,int sp,int color,boolean bold) {
        TextView v=new TextView(this);v.setText(t);v.setTextSize(sp);v.setTextColor(color);
        v.setLineSpacing(dp(2),1f);
        if(bold)v.setTypeface(null,1);return v;
    }
    private void put(LinearLayout p,View v,int top,int bottom){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(top),0,dp(bottom));p.addView(v,lp);
    }
    private LinearLayout vertical(int pad){
        LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(dp(pad),dp(pad),dp(pad),dp(pad));return l;
    }
    private LinearLayout card(){
        LinearLayout c=vertical(19);
        c.setBackground(CosmicUI.glass(this,23,0xff24295D,0xff111C44,0xff796AE8));
        c.setElevation(dp(10));put(page,c,0,17);
        CosmicUI.enter(c,this,Math.min(6,page.getChildCount()));
        return c;
    }
    
        private Button button(String title,boolean primary,Runnable r){
        Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setTextSize(14);
        b.setTypeface(null,android.graphics.Typeface.BOLD);
        b.setTextColor(TEXT);b.setPadding(dp(12),dp(4),dp(12),dp(4));
        b.setBackground(CosmicUI.glass(this,17,
            primary?0xff704AF0:0xff30367C,
            primary?0xff35229C:0xff1A2A61,
            primary?0xffC9A5FF:0xff5D8EF9));
        b.setMinHeight(dp(51));CosmicUI.lift(b,this);
        b.setOnClickListener(v->r.run());return b;
    }
        private void line(LinearLayout c,String title,String subtitle){
        put(c,text(title,21,TEXT,true),3,3);
        if(!subtitle.isEmpty())put(c,text(subtitle,13,MUTED,false),0,10);
    }
    private void section(String s){put(page,headline(s,23),18,12);}
    private void hint(String s){put(page,text(s,13,MUTED,false),6,15);}
    private void render(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackground(gradient(0xff090F25==0?0:0,0xff090F25,0xff15123C,0xff080D20));setContentView(root);
        LinearLayout head=vertical(0);head.setPadding(dp(22),dp(19),dp(22),dp(14));
        LinearLayout header=horizontal();
        LinearLayout brand=vertical(0);put(brand,headline("✦ Our Little Universe",21),0,5);
        String baby=prefs.getString("childName","").trim();put(brand,text((baby.isEmpty()?"Our little one":baby)+"'s story",13,MUTED,false),0,0);
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView star=text("✧",27,GOLD,false);star.setGravity(Gravity.CENTER);
        star.setBackground(CosmicUI.sphere(this,CosmicUI.VIOLET,CosmicUI.BLUE));CosmicUI.pulse(star);
        header.addView(star,new LinearLayout.LayoutParams(dp(46),dp(46)));
        head.addView(header);root.addView(head);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        page=vertical(0);page.setPadding(dp(20),dp(6),dp(20),dp(38));
        scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        switch(activeTab){
            case "Memories":showMemories(null);break;
            case "Milestones":showMilestones();break;
            case "Letters":showMemories("Letter");break;
            case "Settings":showSettings();break;
            case "RecentlyDeleted":showRecycleBin();break;
            default:showHome();
        }
        LinearLayout nav=horizontal();nav.setPadding(dp(7),dp(6),dp(7),dp(4));nav.setBackground(CosmicUI.glass(this,22,0xff222456,0xff0D1331,0xff6758D6));nav.setElevation(dp(18));
        String[] names={"Home","Moments","＋","Firsts","Vault"};
        String[] marks={"⌂","▣","＋","✧","◇"};
        String[] routes={"Home","Memories","Create","Milestones","Settings"};
        for(int i=0;i<names.length;i++){
            final String route=routes[i];
            boolean selected=activeTab.equals(route);
            LinearLayout item=vertical(0);item.setGravity(Gravity.CENTER);
            TextView glyph=text(marks[i],i==2?27:23,i==2?BACKGROUND:selected?GOLD:MUTED,true);glyph.setGravity(Gravity.CENTER);
            if(i==2){
                glyph.setTextColor(TEXT);glyph.setBackground(CosmicUI.sphere(this,CosmicUI.VIOLET,CosmicUI.BLUE));item.addView(glyph,new LinearLayout.LayoutParams(dp(52),dp(49)));CosmicUI.pulse(glyph);
            }else item.addView(glyph);
            TextView label=text(names[i],10,selected?TEXT:MUTED,selected);label.setGravity(Gravity.CENTER);
            put(item,label,2,0);nav.addView(item,new LinearLayout.LayoutParams(0,dp(68),1));
            item.setOnClickListener(v->{if(route.equals("Create"))editor(null,"Memory",false);else{activeTab=route;render();}});
        }
        root.addView(nav);
    }
    
        private void showHome(){
        FrameLayout hero=new FrameLayout(this);
        hero.setBackground(CosmicUI.glass(this,29,0xff373177,0xff111C48,0xff9681FE));hero.setClipToOutline(true);hero.setElevation(dp(12));
        hero.addView(new CosmicUI.Galaxy(this),new FrameLayout.LayoutParams(-1,-1));
        LinearLayout overlay=vertical(23);overlay.setGravity(Gravity.BOTTOM);
        put(overlay,eyebrow("✦  OUR FIRST CHAPTER  ·  OCTOBER 31, 2026",MINT),0,8);
        put(overlay,headline("Before we met you,\nwe loved you.",31),0,11);
        put(overlay,text("A little universe. A lifetime of memories.",13,0xffEBE2ED,false),0,0);
        hero.addView(overlay,new FrameLayout.LayoutParams(-1,-1,Gravity.BOTTOM));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(286));hp.setMargins(0,dp(5),0,dp(22));page.addView(hero,hp);
        put(page,eyebrow("✦   YOUR STORY BEGINS HERE",MINT),0,13);
        LinearLayout actions=horizontal();
        LinearLayout write=vertical(13);write.setBackground(CosmicUI.glass(this,22,0xff214DD1,0xff122F78,0xff69ADFF));write.setElevation(dp(13));
        put(write,text("✧  ✎",25,MINT,true),0,3);put(write,text("Tell their story",15,TEXT,true),0,7);
        put(write,text("Write or dictate",12,MUTED,false),0,0);
        LinearLayout voice=vertical(13);voice.setBackground(CosmicUI.glass(this,22,0xff6B38CD,0xff382376,0xffC596FF));voice.setElevation(dp(13));
        put(voice,text("◉  ♡",25,ROSE,true),0,3);put(voice,text("Record your voice",15,TEXT,true),0,7);
        put(voice,text("No typing. Just talk.",12,0xffF3DBE3,false),0,0);
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(126),1);a.setMargins(0,0,dp(6),0);actions.addView(write,a);
        LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,dp(126),1);b.setMargins(dp(6),0,0,0);actions.addView(voice,b);
        CosmicUI.lift(write,this);write.setOnClickListener(v->editor(null,"Journal",true));
        CosmicUI.lift(voice,this);voice.setOnClickListener(v->startVoice(null));
        put(page,actions,0,18);
        LinearLayout chapter=card();
        put(chapter,eyebrow("✧  OCTOBER 31, 2026  ·  CHAPTER ONE",MINT),0,9);
        put(chapter,headline("The day everything changed",23),0,9);
        put(chapter,text("October 31, 2026. The first page of their story. Preserve how you felt when you learned you were becoming parents.",14,MUTED,false),0,15);
        put(chapter,button("♡  Capture today's announcement",true,()->editor(null,"Journal",true)),0,0);
        java.util.List<Memory> recent=memories();
        LinearLayout stats=horizontal();
        int media=0;for(Memory m:recent)media+=m.media.size();
        summary(stats,String.valueOf(recent.size()),"MOMENTS");
        summary(stats,String.valueOf(media),"KEEPSAKES");
        summary(stats,"∞","LOVE");
        put(page,stats,15,10);
        LinearLayout studioCard=card();
        put(studioCard,eyebrow("MEMORY STUDIO",MINT),0,8);
        put(studioCard,headline("A lifetime of chapters",24),0,8);
        put(studioCard,text("Search memories, revisit photo stories, track growth, create time capsules and more.",14,MUTED,false),0,12);
        put(studioCard,button("Explore your Memory Studio  →",true,()->studio.open()),0,0);
        section("Recently treasured");
        if(recent.isEmpty()){
            LinearLayout empty=card();put(empty,text("✧",28,GOLD,false),0,9);
            put(empty,headline("Your story starts today",23),0,9);
            put(empty,text("One day your child will look back on this day through your own words.",14,MUTED,false),0,0);
        }else {
            for(int i=0;i<Math.min(3,recent.size());i++)drawEntry(recent.get(i));
            put(page,button("Explore the memory garden  →",false,()->{activeTab="Memories";render();}),3,0);
        }
    }
    private void summary(LinearLayout row,String count,String label){
        LinearLayout tile=vertical(7);tile.setGravity(Gravity.CENTER);
        put(tile,headline(count,22),0,2);put(tile,eyebrow(label,MUTED),0,0);
        row.addView(tile,new LinearLayout.LayoutParams(0,dp(69),1));
    }
    
        private void showMemories(String filter){
        put(page,eyebrow("A GROWING COLLECTION OF LOVE",MINT),8,10);
        put(page,headline(filter==null?"The memory garden":"Letters for later",32),0,7);
        hint(filter==null?"Photos, videos, voice notes and little moments, all in one beautiful timeline.":"Words you'll want your child to hear years from now.");
        put(page,button(filter==null?"＋  Capture another moment":"＋  Write a new letter",true,
            ()->editor(null,filter==null?"Memory":"Letter",false)),5,17);
        put(page,button("⌕   Search, filter & favorites",false,()->studio.search()),0,14);
        int shown=0;
        for(Memory m:memories())if(filter==null||m.kind.equals(filter)){drawEntry(m);shown++;}
        if(shown==0){
            LinearLayout empty=card();put(empty,text("✧",30,GOLD,false),0,10);
            put(empty,headline("So much is still to come",23),0,8);
            put(empty,text("Your first memory becomes the opening page of their lifetime story.",13,MUTED,false),0,0);
        }
    }
    
        private static final String[][] FIRSTS={
        {"Heartbeat","First heartbeat"},{"Ultrasound","First ultrasound"},{"Announcement","Pregnancy announcement"},
        {"Gender reveal","Gender reveal"},{"First kick","First little kick"},{"Birth","Welcome to the world"},
        {"First smile","First smile"},{"First steps","First steps"},{"First words","First words"}
    };
    private void showMilestones(){
        put(page,eyebrow("THE BEAUTIFUL FIRSTS",MINT),8,10);
        put(page,headline("Little firsts.\nBig feelings.",34),0,9);
        hint("From their first heartbeat to their first steps.");
        int completed=0;
        for(String[] t:FIRSTS)for(Memory m:memories())if(m.kind.equals("Milestone")&&m.title.equals(t[1])){completed++;break;}
        LinearLayout progress=card();
        put(progress,headline(completed+" of "+FIRSTS.length,31),0,4);
        put(progress,text("moments you've captured",13,MUTED,false),0,12);
        LinearLayout rail=horizontal();rail.setBackground(CosmicUI.glass(this,10,0xff313577,0xff202B62,0xff7E8FFF));
        View fill=new View(this);fill.setBackground(gradient(8,CosmicUI.BLUE,CosmicUI.VIOLET));
        rail.addView(fill,new LinearLayout.LayoutParams(0,dp(7),Math.max(0.01f,(float)completed/FIRSTS.length)));
        rail.addView(new View(this),new LinearLayout.LayoutParams(0,dp(7),Math.max(0.01f,1f-(float)completed/FIRSTS.length)));
        put(progress,rail,0,0);
        int num=0;
        for(String[] t:FIRSTS){
            boolean done=false;
            for(Memory m:memories())if(m.kind.equals("Milestone")&&m.title.equals(t[1])){done=true;break;}
            LinearLayout c=card();LinearLayout line=horizontal();
            TextView symbol=text(done?"✓":"✧",25,done?MINT:GOLD,true);
            symbol.setGravity(Gravity.CENTER);symbol.setBackground(CosmicUI.sphere(this,done?0xff3986EC:0xff8646E8,CosmicUI.BLUE));
            line.addView(symbol,new LinearLayout.LayoutParams(dp(54),dp(54)));
            LinearLayout info=vertical(0);
            put(info,eyebrow(String.format(Locale.US,"MILESTONE %02d",++num),MUTED),0,4);
            put(info,text(t[1],18,TEXT,true),0,0);
            LinearLayout.LayoutParams il=new LinearLayout.LayoutParams(0,-2,1);il.setMargins(dp(13),0,0,0);line.addView(info,il);
            put(c,line,0,14);
            put(c,button(done?"Add another memory  →":"Capture this first  →",false,()->editor(null,"Milestone",false,t[1])),0,0);
        }
    }
    
        private void drawEntry(Memory m){
        LinearLayout c=card();LinearLayout top=horizontal();
        TextView kind=eyebrow("✧  "+m.kind.toUpperCase(Locale.US),m.kind.equals("Letter")?ROSE:MINT);
        top.addView(kind,new LinearLayout.LayoutParams(0,-2,1));
        top.addView(text(m.day,12,MUTED,false));put(c,top,0,12);
        put(c,headline(m.title,24),0,5);
        JSONObject by=FeatureStore.detail(db,m.id);
        put(c,text("Preserved by "+by.optString("author","Family")+(by.optString("album","").isEmpty()?"":"  ·  "+by.optString("album")),12,MINT,false),0,9);
        if(!m.body.isEmpty()){
            String excerpt=m.body.length()>430?m.body.substring(0,430)+"…":m.body;
            TextView desc=text(excerpt,14,0xffD7DFEC,false);desc.setLineSpacing(dp(4),1);
            put(c,desc,0,12);
        }
        int photos=0;
        for(String name:m.media) {
            File f=new File(new File(getFilesDir(),"media"),name);if(!f.isFile())continue;
            if(name.endsWith(".m4a")||name.endsWith(".aac")||name.endsWith(".3gp")){
                LinearLayout audio=horizontal();audio.setPadding(dp(11),dp(8),dp(11),dp(8));
                audio.setBackground(CosmicUI.glass(this,16,0xff3D2B81,0xff1A2F76,0xffAA73FF));
                audio.addView(text("◉  A voice to remember",13,TEXT,true),new LinearLayout.LayoutParams(0,-2,1));
                Button play=button("▶ Play",true,()->playVoice(f));audio.addView(play,new LinearLayout.LayoutParams(dp(92),dp(44)));
                put(c,audio,2,10);
            }else if(name.endsWith(".mp4")||name.endsWith(".mov")||name.endsWith(".webm")||name.endsWith(".mkv")){
                put(c,button("▶  Watch this moment",false,()->openMedia(f,true)),0,10);
            }else if(photos++<4){
                try{
                    BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
                    BitmapFactory.decodeFile(f.getAbsolutePath(),o);o.inJustDecodeBounds=false;
                    o.inSampleSize=Math.max(1,Math.max(o.outWidth/900,o.outHeight/900));
                    Bitmap imageData=BitmapFactory.decodeFile(f.getAbsolutePath(),o);
                    if(imageData!=null){
                        ImageView image=new ImageView(this);image.setImageBitmap(imageData);
                        image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(bg(PANEL2,15));
                        image.setClipToOutline(true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(196));
                        lp.setMargins(0,dp(2),0,dp(9));c.addView(image,lp);
                        image.setOnClickListener(v->openMedia(f,false));
                    }
                }catch(Exception ignored){}
            }
        }
        LinearLayout controls=horizontal();
        quickButton(controls,"＋ Media",false,()->chooseMedia(m.id));
        quickButton(controls,"✎ Edit",false,()->editor(m,m.kind,false));put(c,controls,6,6);
        LinearLayout extras=horizontal();
        quickButton(extras,FeatureStore.starred(db,m.id)?"♥ Favorite":"♡ Favorite",false,()->{FeatureStore.star(db,m.id);render();});
        quickButton(extras,"Details & comments",false,()->memoryDetails(m.id));
        put(c,extras,0,7);
        TextView record=text("●  Add a voice message",13,ROSE,true);record.setGravity(Gravity.CENTER);
        record.setPadding(0,dp(8),0,dp(7));put(c,record,0,0);
        record.setOnClickListener(v->startVoice(m.id));
        c.setOnLongClickListener(v->{confirmDelete(m);return true;});
    }
    
        private void openMedia(File f,boolean video) {
        if(video){
            VideoView vv=new VideoView(this);
            vv.setVideoPath(f.getAbsolutePath());
            LinearLayout wrap=vertical(8);
            wrap.addView(vv,new LinearLayout.LayoutParams(-1,dp(330)));
            AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("A memory to treasure")
                .setView(wrap)
                .setPositiveButton("Save to Phone",(d,w)->saveOriginalMedia(f))
                .setNeutralButton("Choose Folder",(d,w)->chooseMediaExportFolder(f))
                .setNegativeButton("Close",null)
                .create();
            dialog.setOnShowListener(d->{
                vv.setMediaController(new MediaController(this));
                vv.start();
            });
            dialog.setOnDismissListener(d->vv.stopPlayback());
            dialog.show();
        }else{
            ImageView img=new ImageView(this);
            img.setImageURI(Uri.fromFile(f));
            img.setAdjustViewBounds(true);
            LinearLayout wrap=vertical(8);
            wrap.addView(img);
            new AlertDialog.Builder(this)
                .setTitle("A memory to treasure")
                .setView(wrap)
                .setPositiveButton("Save to Phone",(d,w)->saveOriginalMedia(f))
                .setNeutralButton("Choose Folder",(d,w)->chooseMediaExportFolder(f))
                .setNegativeButton("Close",null)
                .show();
        }
    }
    private String memoryTitleFor(File f){
        try(Cursor cursor=db.rawQuery(
            "SELECT title FROM memories WHERE media LIKE ? ORDER BY day DESC LIMIT 1",
            new String[]{"%"+f.getName()+"%"})){
            if(cursor.moveToFirst())return cursor.getString(0);
        }catch(Exception ignored){}
        return "Memory";
    }
    private void saveOriginalMedia(File f){
        if(!f.isFile()){
            message("The original attachment is missing.");
            return;
        }
        if(Build.VERSION.SDK_INT<29){
            chooseMediaExportFolder(f);
            return;
        }
        final String filename=MediaSaver.displayName(f,memoryTitleFor(f));
        message("Saving original-quality file to your phone…");
        io.execute(()->{
            String error=null;
            try{MediaSaver.save(getApplicationContext(),f,filename);}
            catch(Exception e){error=e.getMessage()==null?e.toString():e.getMessage();}
            final String problem=error;
            runOnUiThread(()->{
                if(problem==null){
                    new AlertDialog.Builder(this).setTitle("Saved to your phone")
                        .setMessage("Your original-quality file was saved in:\n"+MediaSaver.folder(f)+
                            "\n\nYou can find photos and videos in your phone's Gallery or Photos app.")
                        .setPositiveButton("Done",null).show();
                }else new AlertDialog.Builder(this).setTitle("Could not save attachment")
                    .setMessage(problem+"\n\nYou can also use Choose Folder to save a copy manually.")
                    .setPositiveButton("OK",null).show();
            });
        });
    }
    private void chooseMediaExportFolder(File file){
        if(!file.isFile()){message("Original attachment not found.");return;}
        try{
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.setType(MediaSaver.mime(file));
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_TITLE,MediaSaver.displayName(file,memoryTitleFor(file)));
            pendingExportPath=file.getAbsolutePath();
            startActivityForResult(intent,SAVE_MEDIA_AS);
        }catch(Exception error){
            message("Couldn't open phone storage: "+error.getMessage());
        }
    }
    private void saveSelectedMedia(Uri destination){
        final String selected=pendingExportPath;
        pendingExportPath=null;
        if(destination==null||selected==null){message("No media selected to save.");return;}
        final File source=new File(selected);
        message("Saving original file to selected folder…");
        io.execute(()->{
            String problem=null;
            try{MediaSaver.saveAs(getApplicationContext(),source,destination);}
            catch(Exception e){problem=e.getMessage()==null?e.toString():e.getMessage();}
            final String error=problem;
            runOnUiThread(()->{
                if(error==null)message("Original-quality media saved to your chosen folder.");
                else new AlertDialog.Builder(this).setTitle("Save failed")
                     .setMessage(error).setPositiveButton("OK",null).show();
            });
        });
    }
    private EditText input(String value,String hint,boolean multi){
        EditText e=new EditText(this);e.setSingleLine(!multi);e.setText(value);e.setTextColor(TEXT);e.setHintTextColor(MUTED);
        e.setHint(hint);e.setTextSize(15);if(multi){e.setMinLines(5);e.setGravity(Gravity.TOP);e.setInputType(131073);}
        return e;
    }
    private void editor(Memory original,String kind,boolean first){
        editor(original,kind,first,null);
    }
    private void editor(Memory original,String kind,boolean first,String suggested){
        Dialog dialog=new Dialog(this,android.R.style.Theme_Material_NoActionBar);
        LinearLayout screen=vertical(0);screen.setBackgroundColor(BACKGROUND);
        LinearLayout toolbar=horizontal();toolbar.setPadding(dp(20),dp(20),dp(20),dp(16));
        TextView back=text("‹",39,GOLD,false);back.setGravity(Gravity.CENTER);
        toolbar.addView(back,new LinearLayout.LayoutParams(dp(47),dp(47)));
        back.setOnClickListener(v->dialog.dismiss());
        LinearLayout toolbarInfo=vertical(0);
        put(toolbarInfo,eyebrow("A NEW PAGE IN THEIR STORY",GOLD),0,5);
        put(toolbarInfo,text(original==null?"Capture a memory":"Edit cherished memory",17,TEXT,true),0,0);
        LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(0,-2,1);tl.setMargins(dp(9),0,0,0);toolbar.addView(toolbarInfo,tl);
        put(screen,toolbar,0,0);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
        LinearLayout form=vertical(20);form.setPadding(dp(23),dp(7),dp(23),dp(35));
        scroll.addView(form);screen.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        FrameLayout art=new FrameLayout(this);art.setBackground(CosmicUI.glass(this,25,0xff292D78,0xff142B62,0xff9887FF));
        art.addView(new CosmicUI.Galaxy(this),new FrameLayout.LayoutParams(-1,-1));
        TextView artText=headline("A moment worth\nkeeping forever.",29);
        artText.setPadding(dp(19),dp(52),dp(12),dp(12));
        art.addView(artText,new FrameLayout.LayoutParams(-1,-1,Gravity.BOTTOM));
        put(form,art,0,21);
        art.getLayoutParams().height=dp(164);
        put(form,eyebrow("NAME YOUR MEMORY",MINT),0,6);
        EditText title=input(original!=null?original.title:(suggested!=null?suggested:first?"The day we found out about you":""),"Give this moment a title",false);
        title.setBackground(outlined(PANEL2,14,0xff435572));title.setPadding(dp(15),dp(12),dp(15),dp(12));
        put(form,title,0,8);
        put(form,button("◉   Speak the title",false,()->dictateInto(title)),0,18);
        put(form,eyebrow("WHEN DID IT HAPPEN?",MINT),0,6);
        EditText day=input(original!=null?original.day:first?"2026-10-31":today(),"Choose date",false);
        day.setFocusable(false);day.setClickable(true);
        day.setBackground(outlined(PANEL2,14,0xff435572));
        day.setPadding(dp(15),dp(12),dp(15),dp(12));
        put(form,day,0,10);
        day.setOnClickListener(v->{
            Calendar date=Calendar.getInstance();
            try{String[] p=day.getText().toString().split("-");date.set(Integer.parseInt(p[0]),Integer.parseInt(p[1])-1,Integer.parseInt(p[2]));}catch(Exception ignored){}
            new DatePickerDialog(this,(picker,y,month,d)->day.setText(String.format(Locale.US,"%04d-%02d-%02d",y,month+1,d)),
              date.get(Calendar.YEAR),date.get(Calendar.MONTH),date.get(Calendar.DAY_OF_MONTH)).show();
        });
        put(form,eyebrow("CHAPTER TYPE",MINT),11,6);
        String[] kinds={"Memory","Journal","Milestone","Letter","Interview","Quote","Gratitude","Tradition","Voice"};
        Spinner type=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,kinds);type.setAdapter(adapter);
        String initial=original==null?kind:original.kind;
        for(int i=0;i<kinds.length;i++)if(kinds[i].equals(initial))type.setSelection(i);
        put(form,type,0,15);
        put(form,eyebrow("THE STORY IN YOUR OWN WORDS",MINT),0,7);
        EditText body=input(original!=null?original.body:"","What happened? How did you feel? What would you want your child to know?",true);
        body.setBackground(outlined(PANEL,17,0xff46516A));
        body.setPadding(dp(15),dp(15),dp(15),dp(15));body.setMinLines(7);
        put(form,body,0,11);
        put(form,button("◉   Dictate my story • on-device",true,()->dictateInto(body)),0,8);
        put(form,text("No need to type. Android's offline dictation can fill this in, or you can save your actual voice as an audio memory.",12,MUTED,false),0,14);
        JSONObject oldMeta=original==null?new JSONObject():FeatureStore.detail(db,original.id);
        put(form,eyebrow("PRESERVED BY",MINT),0,7);
        put(form,text(oldMeta.optString("author",FeatureStore.author(this)),15,TEXT,true),0,12);
        put(form,eyebrow("TAGS (SEPARATE WITH COMMAS)",MINT),0,7);
        JSONArray oldTags=oldMeta.optJSONArray("tags");
        StringBuilder textTags=new StringBuilder();
        if(oldTags!=null)for(int i=0;i<oldTags.length();i++){if(i>0)textTags.append(", ");textTags.append(oldTags.optString(i));}
        EditText tags=input(textTags.toString(),"e.g. Family, Firsts, Holiday",false);
        put(form,tags,0,12);
        put(form,eyebrow("ALBUM / COLLECTION",MINT),0,7);
        EditText album=input(oldMeta.optString("album",""),"e.g. Pregnancy, Year One",false);
        put(form,album,0,11);
        CheckBox share=new CheckBox(this);
        share.setText("Include in family-sharing ZIPs");
        share.setTextColor(TEXT);
        share.setChecked(!oldMeta.optString("visibility","family").equals("private"));
        put(form,share,0,15);
        LinearLayout footer=vertical(16);footer.setBackground(CosmicUI.glass(this,18,0xff27255D,0xff161940,0xff6559CF));
        Button save=button("♡   Save this cherished moment",true,()->{
            String heading=title.getText().toString().trim(),when=day.getText().toString().trim();
            if(heading.isEmpty()||!when.matches("\\d{4}-\\d{2}-\\d{2}")){
                message("Please give this memory a title and date");return;
            }
            Memory m=original==null?new Memory():original;
            m.title=heading;m.day=when;m.kind=type.getSelectedItem().toString();
            m.body=body.getText().toString().trim();save(m);
            JSONObject meta=FeatureStore.detail(db,m.id);
            try{
                JSONArray labelList=new JSONArray();
                for(String tag:tags.getText().toString().split(",")){
                    String t=tag.trim();if(!t.isEmpty()&&t.length()<=80)labelList.put(t);
                }
                meta.put("tags",labelList);meta.put("album",album.getText().toString().trim());
                meta.put("visibility",share.isChecked()?"family":"private");
                FeatureStore.putDetail(db,m.id,meta);
            }catch(Exception ignored){}
            dialog.dismiss();activeTab="Memories";render();
            if(original==null){
                new AlertDialog.Builder(this).setTitle("♡ Another moment saved")
                  .setMessage("Would you like to attach the photos, videos or your voice to this memory?")
                  .setPositiveButton("Add photos or video",(d,w)->chooseMedia(m.id))
                  .setNeutralButton("Record my voice",(d,w)->startVoice(m.id))
                  .setNegativeButton("Finished",null).show();
            }else message("Your story is safely saved on this phone.");
        });
        put(footer,save,0,5);
        TextView note=text("Only on your device • Export backups from the Vault",11,MUTED,false);
        note.setGravity(Gravity.CENTER);put(footer,note,0,0);
        put(screen,footer,0,0);
        dialog.setContentView(screen);dialog.show();
        dialog.getWindow().setLayout(-1,-1);
    }
    
        private void chooseMedia(String id) {
        pendingMediaId=id;
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"});
        try{startActivityForResult(i,PICK_MEDIA);}catch(Exception e){message("Photo picker unavailable: "+e.getMessage());}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null)return;
        if(request==PICK_MEDIA){
            ArrayList<Uri> items=new ArrayList<>();
            if(data.getClipData()!=null){for(int j=0;j<data.getClipData().getItemCount();j++)items.add(data.getClipData().getItemAt(j).getUri());}
            else if(data.getData()!=null)items.add(data.getData());
            String target=pendingMediaId;
            Toast.makeText(this,"Saving "+items.size()+" original file(s)…",Toast.LENGTH_LONG).show();
            io.execute(()->{
                ArrayList<String> names=new ArrayList<>();
                String problem=null;
                for(Uri uri:items){
                    File dest=null;
                    try{
                        String mime=getContentResolver().getType(uri);
                        if(mime==null||(!mime.startsWith("image/")&&!mime.startsWith("video/")))throw new IOException("Select an image or video");
                        String ext=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                        if(ext==null||ext.isEmpty())ext=mime.startsWith("video/")?"mp4":"jpg";
                        String name=UUID.randomUUID().toString()+"."+ext.toLowerCase(Locale.US);
                        dest=new File(new File(getFilesDir(),"media"),name);
                        try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(dest)){
                            if(in==null)throw new IOException("Cannot open selected file");
                            byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
                        }
                        names.add(name);
                    }catch(Exception e){if(dest!=null)dest.delete();problem=e.getMessage();break;}
                }
                final String err=problem;
                runOnUiThread(()->{
                    Memory m=load(target);
                    if(m!=null){m.media.addAll(names);save(m);}
                    activeTab="Memories";render();
                    if(err!=null)message("Some attachments were not saved: "+err);
                    else message(names.size()+" original file(s) saved locally ♡");
                });
            });
        }else if(request==EXPORT)exportTo(data.getData(),false);
        else if(request==EXPORT_FAMILY)exportTo(data.getData(),true);
        else if(request==EXPORT_PDF)pdfTo(data.getData());
        else if(request==SAVE_MEDIA_AS)saveSelectedMedia(data.getData());
        else if(request==VERIFY_BACKUP)checkExternalBackup(data.getData());
        else if(request==IMPORT)importFrom(data.getData());
    }
    private void confirmDelete(Memory m){
        new AlertDialog.Builder(this).setTitle("Move this memory to Recently Deleted?")
            .setMessage("This safely removes it from the timeline without deleting the original photos, videos or voice recordings. You can restore it from the Vault.")
            .setNegativeButton("Keep memory",null)
            .setPositiveButton("Move to Recently Deleted",(dialog,which)->{
                try{
                    if(ArchiveSafety.moveToTrash(db,m.id)){
                        render();
                        message("Memory moved to Recently Deleted. It can be restored anytime.");
                    }else message("The memory was not found.");
                }catch(Exception error){message("Could not protect this memory: "+error.getMessage());}
            }).show();
    }
    private void showRecycleBin(){
        put(page,eyebrow("SAFELY RECOVERABLE",MINT),8,9);
        put(page,headline("Recently Deleted",32),0,8);
        hint("Deleted memories stay here, including their original photos, videos and voice recordings, until you permanently delete them. They're also included in full backups.");
        List<ArchiveSafety.Deleted> list=ArchiveSafety.deleted(db);
        if(list.isEmpty()){
            LinearLayout empty=card();
            put(empty,headline("Nothing has been deleted",22),0,8);
            put(empty,text("Your recovered and current memories remain safe in the timeline.",14,MUTED,false),0,0);
            return;
        }
        for(ArchiveSafety.Deleted item:list){
            LinearLayout tile=card();
            put(tile,eyebrow(item.kind.toUpperCase(Locale.US)+"  ·  "+item.day,MINT),0,9);
            put(tile,headline(item.title,22),0,8);
            put(tile,text(item.attachments+" saved original attachment(s)",13,MUTED,false),0,12);
            put(tile,button("Restore this memory",true,()->{
                try{
                    if(ArchiveSafety.restore(db,item.id)){
                        showRecycleBinRefresh();
                        message("Memory, voice and photos restored to your timeline ♡");
                    }else message("This memory could not be restored.");
                }catch(Exception error){message("Restore failed: "+error.getMessage());}
            }),0,8);
            put(tile,button("Permanently delete",false,()->{
                new AlertDialog.Builder(this).setTitle("Permanently erase this memory?")
                    .setMessage("This removes the memory from the recycle bin and deletes original attachments that no other memory uses. This cannot be undone unless you have an external backup.")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Erase forever",(d,w)->{
                        try{ArchiveSafety.deleteForever(this,db,item.id);showRecycleBinRefresh();}
                        catch(Exception e){message("Could not delete safely: "+e.getMessage());}
                    }).show();
            }),0,0);
        }
    }
    private void showRecycleBinRefresh(){
        activeTab="RecentlyDeleted";render();
    }
    private void checkMyArchive(){
        message("Checking every saved photo, video and recording…");
        io.execute(()->{
            ArchiveSafety.Health health=ArchiveSafety.scan(getApplicationContext(),db);
            runOnUiThread(()->new AlertDialog.Builder(this)
                .setTitle("Your memory archive · health check")
                .setMessage(health.summary())
                .setPositiveButton("Done",null).show());
        });
    }
    private void pickBackupToVerify(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        startActivityForResult(intent,VERIFY_BACKUP);
    }
    private void checkExternalBackup(Uri location){
        if(location==null)return;
        message("Verifying archive and original file checksums…");
        io.execute(()->{
            String result;boolean okay;
            try{
                ArchiveSafety.Verification verification=ArchiveSafety.verifyArchive(getApplicationContext(),location);
                result=verification.summary();okay=true;
            }catch(Exception problem){
                result="This backup did not pass verification:\n"+
                    (problem.getMessage()==null?problem.toString():problem.getMessage())+
                    "\n\nDo not delete your current memories. Create a new full backup and verify it.";
                okay=false;
            }
            final boolean success=okay;
            final String summary=result;
            runOnUiThread(()->new AlertDialog.Builder(this)
                .setTitle(success?"Backup verified ♡":"Backup needs attention")
                .setMessage(summary).setPositiveButton("OK",null).show());
        });
    }
    private void showSettings(){
        put(page,eyebrow("MADE FOR ONE EXTRAORDINARY LIFE",MINT),6,10);
        put(page,headline("The keepsake vault",32),0,8);
        hint("Make this universe your child's own. Preserve every chapter.");
        LinearLayout letter=card();
        put(letter,eyebrow("✉  MESSAGES TO THE FUTURE",ROSE),0,10);
        put(letter,headline("Someday they'll read these.",24),0,9);
        put(letter,text("Letters filled with love, advice and stories — written before they were old enough to understand.",14,MUTED,false),0,13);
        put(letter,button("Open the letters  →",true,()->{activeTab="Letters";render();}),0,0);
        section("Discover every chapter");
        LinearLayout features=card();
        put(features,headline("The Memory Studio",23),0,8);
        put(features,text("Contributor credits, growth charts, pregnancy memories, letters, interviews, calendars, photo shows and more.",14,MUTED,false),0,14);
        put(features,button("Open all family features",true,()->studio.open()),0,0);
        section("Make it yours");
        LinearLayout profile=card();
        put(profile,text("Your little one's name",13,GOLD,true),0,5);
        EditText name=input(prefs.getString("childName",""),"Add baby's name when you know it",false);
        put(profile,name,0,13);
        put(profile,text("Parents",13,GOLD,true),0,5);
        EditText parents=input(prefs.getString("parents",""),"Names of Mom and Dad",false);
        put(profile,parents,0,13);
        put(profile,text("Expected due date",13,GOLD,true),0,5);
        EditText due=input(prefs.getString("dueDate",""),"YYYY-MM-DD",false);
        due.setFocusable(false);due.setClickable(true);
        due.setOnClickListener(v->{
            Calendar c=Calendar.getInstance();
            try{String[] x=due.getText().toString().split("-");c.set(Integer.parseInt(x[0]),Integer.parseInt(x[1])-1,Integer.parseInt(x[2]));}catch(Exception ignored){}
            new DatePickerDialog(this,(view,y,m,d)->due.setText(String.format(Locale.US,"%04d-%02d-%02d",y,m+1,d)),
              c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
        });
        put(profile,due,0,15);
        put(profile,button("♡   Save family details",true,()->{
            prefs.edit().putString("childName",name.getText().toString().trim())
              .putString("parents",parents.getText().toString().trim())
              .putString("dueDate",due.getText().toString().trim()).apply();
            render();message("Your family details are saved.");
        }),0,0);
        section("Private family sync");
        LinearLayout hub=card();
        put(hub,eyebrow("♡  YOUR WINDOWS FAMILY HUB",MINT),0,9);
        put(hub,headline("Everyone's memories. Together.",23),0,9);
        put(hub,text("Pair once with your private family PC. Afterward, this phone exchanges new memories and original media automatically while Our Little Universe is open—even on mobile data.",14,MUTED,false),0,10);
        put(hub,text("Connection: "+FamilyHubSync.lastStatus(this),12,MINT,false),0,12);
        put(hub,text("PC's private HTTPS address",13,GOLD,true),0,5);
        EditText hubUrl=input(FamilyHubSync.hubAddress(this),"https://your-home-public-address",false);
        put(hub,hubUrl,0,12);
        put(hub,text("One-time invitation code from your PC",13,GOLD,true),0,5);
        EditText invitation=input("","Paste the code from the Windows app",false);
        put(hub,invitation,0,12);
        put(hub,text("My contributor name",13,GOLD,true),0,5);
        EditText hubName=input(FeatureStore.author(this),"Dad, Mariah, Grandma…",false);
        put(hub,hubName,0,12);
        put(hub,button("♡   Pair with the family PC",true,()->pairWithHub(
            hubUrl.getText().toString(),invitation.getText().toString(),hubName.getText().toString())),0,9);
        put(hub,button("↻   Synchronize family memories now",false,()->requestFamilyHubSync(true)),0,9);
        put(hub,button("Disconnect this phone",false,()->{
            new AlertDialog.Builder(this).setTitle("Disconnect this phone?")
                .setMessage("You will keep every memory saved locally, but automatic updates will stop until you pair again.")
                .setNegativeButton("Keep connected",null)
                .setPositiveButton("Disconnect",(d,w)->{
                    FamilyHubSync.disconnect(this);render();
                }).show();
        }),0,5);
        section("Keep it forever");
        LinearLayout vault=card();
        put(vault,eyebrow("✧  YOUR PRIVATE ARCHIVE",MINT),0,10);
        put(vault,headline("Every memory. Safely yours.",24),0,8);
        put(vault,text("Export your complete memory collection as a ZIP. Use the full ZIP for private backup. For relatives, use the separate family-only ZIP below; it excludes memories marked private.",14,MUTED,false),0,14);
        put(vault,button("↓   Full backup ZIP (includes private)",true,()->{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");
            i.putExtra(Intent.EXTRA_TITLE,"Our_Little_Universe_Backup_"+today()+".zip");
            startActivityForResult(i,EXPORT);
        }),0,9);
        put(vault,button("↓   Export family ZIP (private excluded)",false,()->{
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_TITLE,"Our_Little_Universe_Family_"+today()+".zip");
            startActivityForResult(intent,EXPORT_FAMILY);
        }),0,9);
        put(vault,button("♡   Recently Deleted ("+ArchiveSafety.deleted(db).size()+")",false,()->showRecycleBinRefresh()),0,9);
        put(vault,button("✓   Check my memories and media",false,()->checkMyArchive()),0,9);
        put(vault,button("✓   Verify an exported backup ZIP",false,()->pickBackupToVerify()),0,9);
        put(vault,button("↑   Merge a family ZIP (no duplicates)",false,()->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");
            startActivityForResult(i,IMPORT);
        }),0,0);
        put(page,text("Your family stays private. No cloud account, no ads and no upload of recordings or media.",12,MUTED,false),5,8);
    }
    
        private void exportTo(Uri location,boolean shareOnly){
        if(location==null)return;
        message("Creating full backup…");
        io.execute(()->{
            String err=null;
            try(OutputStream raw=getContentResolver().openOutputStream(location);
                ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(raw))){
                JSONObject root=new JSONObject();root.put("format","little-universe-android-v1");
                JSONObject settings=new JSONObject();settings.put("childName",prefs.getString("childName",""));
                settings.put("parents",prefs.getString("parents",""));settings.put("dueDate",prefs.getString("dueDate",""));
                root.put("settings",settings);
                JSONArray data=new JSONArray();
                Set<String> selectedFiles=new LinkedHashSet<>();
                Set<String> selectedIds=new LinkedHashSet<>();
                for(Memory m:memories()){
                    if(shareOnly&&"private".equals(FeatureStore.detail(db,m.id).optString("visibility","family")))continue;
                    data.put(m.json());selectedIds.add(m.id);selectedFiles.addAll(m.media);
                }
                root.put("memories",data);
                root.put("features",FeatureStore.exportAll(db,shareOnly?selectedIds:null));
                if(!shareOnly)root.put("recycleBin",ArchiveSafety.exportTrash(db));
                selectedFiles.addAll(ArchiveSafety.requiredFiles(root));
                // Record the SHA-256 of every original in the full/family backup manifest.
                JSONObject checksums=new JSONObject();
                File originals=new File(getFilesDir(),"media");
                for(String name:selectedFiles){
                    File source=new File(originals,name);
                    if(!source.isFile())throw new IOException("Backup cancelled: missing original "+name);
                    checksums.put(name,ArchiveSafety.sha256(source));
                }
                root.put("mediaSha256",checksums);
                byte[] payload=root.toString().getBytes("UTF-8");
                zip.putNextEntry(new ZipEntry("archive.json"));zip.write(payload);zip.closeEntry();
                File dir=new File(getFilesDir(),"media");
                File[] files=dir.listFiles();
                if(files!=null)for(File f:files)if(f.isFile()&&selectedFiles.contains(f.getName())){
                    zip.putNextEntry(new ZipEntry("media/"+f.getName()));
                    try(InputStream in=new BufferedInputStream(new FileInputStream(f))){
                        byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)zip.write(b,0,n);
                    }zip.closeEntry();
                }
            }catch(Exception e){err=e.toString();}
            final String result=err;
            runOnUiThread(()->message(result==null?"Full backup exported. Keep it somewhere safe. ♡":"Backup failed: "+result));
        });
    }
    private void importFrom(Uri location){
        if(location==null)return;
        new AlertDialog.Builder(this)
            .setTitle("Merge memories from your family?")
            .setMessage("Import new moments from another family member's Our Little Universe ZIP. "+
                "Memories already on your phone won't be duplicated or overwritten. "+
                "New attachments to existing memories will be added, too.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Merge memories",(dialog,which)->doImport(location))
            .show();
    }
    private void doImport(Uri location){
        message("Checking family ZIP and safely merging memories…");
        io.execute(()->{
            FamilySync.Result summary=null;
            String problem=null;
            try{
                summary=FamilySync.merge(getApplicationContext(),db,location);
            }catch(Exception failure){
                problem=failure.getMessage()==null?failure.toString():failure.getMessage();
            }
            final FamilySync.Result result=summary;
            final String error=problem;
            runOnUiThread(()->{
                if(error!=null){
                    new AlertDialog.Builder(this)
                        .setTitle("Family ZIP wasn't imported")
                        .setMessage(error+"\n\nYour existing memories have not been overwritten.")
                        .setPositiveButton("OK",null).show();
                }else{
                    render();
                    new AlertDialog.Builder(this)
                        .setTitle("Family memories merged ♡")
                        .setMessage(result.summary()+
                            "\n\nYou can export another full ZIP from the Vault to share your combined collection.")
                        .setPositiveButton("Lovely",null).show();
                }
            });
        });
    }
    private void memoryDetails(String id){
        Memory memory=load(id);
        if(memory==null)return;
        JSONObject meta=FeatureStore.detail(db,id);
        LinearLayout panel=vertical(18);
        ScrollView scroll=new ScrollView(this);scroll.addView(panel);
        put(panel,headline("A story with many voices",24),0,10);
        put(panel,text(memory.title,17,TEXT,true),0,12);
        EditText by=input(meta.optString("author",""),"Contributor (Dad, Mariah, Grandma…)",false);
        put(panel,eyebrow("WHO PRESERVED THIS?",MINT),0,7);put(panel,by,0,15);
        EditText caption=input(meta.optString("caption",""),"Additional caption / context",true);
        put(panel,eyebrow("ADDITIONAL CAPTION",MINT),0,7);put(panel,caption,0,13);
        EditText location=input(meta.optString("location",""),"Optional place name (manual, no GPS)",false);
        put(panel,eyebrow("WHERE DID IT HAPPEN?",MINT),0,7);put(panel,location,0,13);
        JSONArray existing=meta.optJSONArray("comments");
        put(panel,eyebrow("FAMILY COMMENTS & REACTIONS",MINT),0,8);
        if(existing!=null)for(int i=0;i<existing.length();i++){
            JSONObject item=existing.optJSONObject(i);if(item==null)continue;
            put(panel,text(item.optString("by","Family")+": "+item.optString("text"),14,TEXT,false),0,7);
        }
        EditText comment=input("","Say something loving, or react with an emoji…",true);
        put(panel,comment,8,7);
        LinearLayout reaction=horizontal();
        quickButton(reaction,"♥ Love",false,()->{comment.append(" ❤️");});
        quickButton(reaction,"✧ Magic",false,()->{comment.append(" ✨");});
        quickButton(reaction,"☺ Joy",false,()->{comment.append(" 😊");});
        put(panel,reaction,0,7);
        CheckBox visibility=new CheckBox(this);visibility.setText("Include in shared family ZIPs");
        visibility.setTextColor(TEXT);
        visibility.setChecked(!"private".equals(meta.optString("visibility","family")));
        put(panel,visibility,0,6);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("Memory details & contributors").setView(scroll)
            .setNegativeButton("Cancel",null).setPositiveButton("Save details",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(v2->{
            try{
                String author=by.getText().toString().trim();
                if(!author.isEmpty())meta.put("author",author);
                meta.put("caption",caption.getText().toString());
                meta.put("location",location.getText().toString().trim());
                meta.put("visibility",visibility.isChecked()?"family":"private");
                String note=comment.getText().toString().trim();
                if(!note.isEmpty()){
                    JSONArray all=meta.optJSONArray("comments");if(all==null)all=new JSONArray();
                    JSONObject next=new JSONObject();
                    next.put("id",UUID.randomUUID().toString());
                    next.put("by",FeatureStore.author(this));next.put("text",note);
                    next.put("day",today());all.put(next);meta.put("comments",all);
                }
                FeatureStore.putDetail(db,id,meta);dialog.dismiss();render();
            }catch(Exception err){message("Could not save: "+err.getMessage());}
        }));
        dialog.show();
    }
    private void startPdfExport(){
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE,"Our_Little_Universe_Baby_Book_"+today()+".pdf");
        startActivityForResult(intent,EXPORT_PDF);
    }
    private void pdfTo(Uri destination){
        if(destination==null)return;
        message("Creating your keepsake book…");
        io.execute(()->{
            String result;
            try{
                int count=KeepsakeBook.make(getApplicationContext(),db,destination);
                result=count+" memories exported to your printable baby-book PDF.";
            }catch(Exception error){result="PDF export failed: "+error.getMessage();}
            final String notice=result;runOnUiThread(()->new AlertDialog.Builder(this)
                .setTitle("Our Little Universe book").setMessage(notice)
                .setPositiveButton("OK",null).show());
        });
    }

    private void withMic(Runnable action){
        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            afterMicPermission=action;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQUEST_MIC);
        }else action.run();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request!=REQUEST_MIC)return;
        Runnable next=afterMicPermission;afterMicPermission=null;
        if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED&&next!=null)next.run();
        else message("Microphone permission is needed to record your voice.");
    }
    private void dictateInto(EditText target){
        withMic(()->{
            if(Build.VERSION.SDK_INT<31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)){
                new AlertDialog.Builder(this).setTitle("Offline dictation not available")
                  .setMessage("This phone doesn't currently have Android's on-device voice transcription available. You can still record a real voice message with the Record button — nothing will be uploaded.")
                  .setPositiveButton("Got it",null).show();return;
            }
            try{
                if(speechRecognizer!=null){speechRecognizer.cancel();speechRecognizer.destroy();}
                speechRecognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
                speechRecognizer.setRecognitionListener(new RecognitionListener(){
                    @Override public void onReadyForSpeech(Bundle b){message("Listening. Tell your story…");}
                    @Override public void onBeginningOfSpeech(){}
                    @Override public void onRmsChanged(float rms){}
                    @Override public void onBufferReceived(byte[] b){}
                    @Override public void onEndOfSpeech(){}
                    @Override public void onError(int code){
                        message("Offline speech recognition couldn't capture that. Try again or record a voice note.");
                    }
                    @Override public void onResults(Bundle results){
                        ArrayList<String> words=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if(words!=null&&!words.isEmpty()){
                            String existing=target.getText().toString();
                            if(!existing.isEmpty()&&!Character.isWhitespace(existing.charAt(existing.length()-1)))target.append(" ");
                            target.append(words.get(0));target.setSelection(target.length());
                        }
                    }
                    @Override public void onPartialResults(Bundle partial){}
                    @Override public void onEvent(int type,Bundle params){}
                });
                Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault().toLanguageTag());
                speechRecognizer.startListening(intent);
            }catch(Exception e){message("Offline dictation unavailable: "+e.getMessage());}
        });
    }
    private void startVoice(String id){
        withMic(()->beginVoice(id));
    }
    private void beginVoice(String id){
        if(isRecording){message("Finish your current voice note first");return;}
        recordingFor=id;
        recordingFile=new File(new File(getFilesDir(),"media"),UUID.randomUUID().toString()+".m4a");
        try{
            recorder=new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(recordingFile.getAbsolutePath());
            recorder.prepare();recorder.start();isRecording=true;
            recordingStart=android.os.SystemClock.elapsedRealtime();
        }catch(Exception e){
            releaseRecorder();if(recordingFile!=null)recordingFile.delete();
            message("Could not start voice recording: "+e.getMessage());return;
        }
        LinearLayout frame=vertical(24);
        frame.setBackground(CosmicUI.glass(this,24,0xff3B2A82,0xff142967,0xffB17CFF));
        TextView glow=text("◉",62,TEXT,true);glow.setGravity(Gravity.CENTER);glow.setBackground(CosmicUI.sphere(this,CosmicUI.VIOLET,CosmicUI.BLUE));glow.setMinHeight(dp(165));CosmicUI.pulse(glow);
        put(frame,glow,3,7);
        TextView title=headline("Your voice is a memory.",25);title.setGravity(Gravity.CENTER);
        put(frame,title,0,6);
        TextView sub=text("Talk about today, how you feel, or anything you want your child to hear someday.",14,TEXT,false);
        sub.setGravity(Gravity.CENTER);put(frame,sub,0,16);
        TextView counter=headline("00:00",33);counter.setGravity(Gravity.CENTER);put(frame,counter,0,16);
        Button stop=button("■   Stop & save memory",true,()->finishVoice(true));
        put(frame,stop,0,10);
        Button cancel=button("Discard recording",false,()->finishVoice(false));put(frame,cancel,0,0);
        recordingDialog=new AlertDialog.Builder(this).setView(frame).create();
        recordingDialog.setOnCancelListener(d->finishVoice(false));
        recordingDialog.setCanceledOnTouchOutside(false);
        recordingDialog.show();
        Runnable ticker=new Runnable(){
            @Override public void run(){
                if(!isRecording)return;
                int sec=(int)((android.os.SystemClock.elapsedRealtime()-recordingStart)/1000L);
                counter.setText(String.format(Locale.US,"%02d:%02d",sec/60,sec%60));
                recordHandler.postDelayed(this,400);
            }
        };recordHandler.post(ticker);
    }
    private void releaseRecorder(){
        try{if(recorder!=null)recorder.release();}catch(Exception ignored){}
        recorder=null;
    }
    private void finishVoice(boolean saveIt){
        if(!isRecording)return;
        isRecording=false;boolean valid=saveIt;
        try{recorder.stop();}catch(Exception e){valid=false;}
        releaseRecorder();
        if(recordingDialog!=null){recordingDialog.setOnCancelListener(null);recordingDialog.dismiss();recordingDialog=null;}
        if(!valid){
            if(recordingFile!=null)recordingFile.delete();
            message(saveIt?"Recording was too short. Please try again.":"Recording discarded");return;
        }
        Memory memory=recordingFor==null?new Memory():load(recordingFor);
        if(memory==null)memory=new Memory();
        if(recordingFor==null || memory.title.isEmpty()){
            memory.title="A voice from "+new java.text.SimpleDateFormat("MMMM d, yyyy",Locale.US).format(new Date());
            memory.kind="Voice";memory.day=today();
        }
        memory.media.add(recordingFile.getName());save(memory);
        activeTab="Memories";render();
        message("Your original voice recording was saved privately ♡");
    }
    private void playVoice(File file){
        try{
            if(player!=null){player.release();player=null;}
            player=new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());player.prepare();player.start();
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("♡ A voice to remember")
               .setMessage("Playing this cherished recording…")
               .setPositiveButton("Save to Phone",(d,w)->saveOriginalMedia(file))
               .setNeutralButton("Choose Folder",(d,w)->chooseMediaExportFolder(file))
               .setNegativeButton("Close",null).create();
            player.setOnCompletionListener(mp->{if(dialog.isShowing())dialog.dismiss();});
            dialog.setOnDismissListener(d->{if(player!=null){player.release();player=null;}});
            dialog.show();
        }catch(Exception e){message("Unable to play recording: "+e.getMessage());}
    }
    private void message(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onBackPressed(){if(!activeTab.equals("Home")){activeTab="Home";render();}else super.onBackPressed();}
    @Override protected void onDestroy(){
        if(isRecording){
            try{recorder.stop();}catch(Exception ignored){}
            isRecording=false;releaseRecorder();
        }
        if(speechRecognizer!=null){speechRecognizer.cancel();speechRecognizer.destroy();}
        if(player!=null)player.release();
        io.shutdown();db.close();super.onDestroy();
    }
}