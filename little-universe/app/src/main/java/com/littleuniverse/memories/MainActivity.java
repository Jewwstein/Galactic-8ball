package com.littleuniverse.memories;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.graphics.*;
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
    private static final int BACKGROUND=0xff11192c, PANEL=0xff202d45, PANEL2=0xff31405c;
    private static final int TEXT=0xfff9f4eb, MUTED=0xffb9c7da, GOLD=0xfff4d6a6, MINT=0xffb6e8dd;
    private static final int PICK_MEDIA=101, EXPORT=102, IMPORT=103;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private SQLiteDatabase db;
    private LinearLayout page;
    private String activeTab="Home", pendingMediaId;
    private android.content.SharedPreferences prefs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(0);
        prefs=getSharedPreferences("little_universe_profile",MODE_PRIVATE);
        db=new Store(this).getWritableDatabase();
        new File(getFilesDir(),"media").mkdirs();
        render();
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
    }
    private String today(){return new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}
    private int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}
    private GradientDrawable bg(int color,int radius) {
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;
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
        LinearLayout c=vertical(17);c.setBackground(bg(PANEL,19));put(page,c,0,13);return c;
    }
    private Button button(String title,boolean primary,Runnable r){
        Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setTextSize(14);
        b.setTextColor(primary?BACKGROUND:TEXT);
        b.setBackground(bg(primary?GOLD:PANEL2,13));b.setMinHeight(dp(49));
        b.setOnClickListener(v->r.run());return b;
    }
    private void line(LinearLayout c,String title,String subtitle){
        put(c,text(title,21,TEXT,true),3,3);
        if(!subtitle.isEmpty())put(c,text(subtitle,13,MUTED,false),0,10);
    }
    private void section(String s){put(page,text(s,22,TEXT,true),18,12);}
    private void hint(String s){put(page,text(s,13,MUTED,false),6,15);}
    private void render() {
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BACKGROUND);
        setContentView(root);
        LinearLayout head=vertical(14);
        head.setPadding(dp(20),dp(16),dp(20),dp(14));
        head.addView(text("☾  LITTLE UNIVERSE",17,GOLD,true));
        head.addView(text(prefs.getString("childName","Our little one")+" • A lifetime of love",12,MUTED,false));
        root.addView(head);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);
        page=vertical(18);page.setPadding(dp(18),dp(6),dp(18),dp(42));sc.addView(page);
        root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        switch(activeTab){
            case "Memories": showMemories(null);break;
            case "Milestones": showMilestones();break;
            case "Letters": showMemories("Letter");break;
            case "Settings": showSettings();break;
            default: showHome();
        }
        LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(3),dp(8),dp(3),dp(7));nav.setBackgroundColor(0xff19243a);
        String[] tabs={"Home","Memories","＋","Milestones","Settings"};
        for(String name:tabs){
            TextView item=text(name,12,name.equals(activeTab)?GOLD:MUTED,name.equals(activeTab));
            item.setGravity(Gravity.CENTER);item.setMinHeight(dp(45));
            nav.addView(item,new LinearLayout.LayoutParams(0,dp(49),1));
            item.setOnClickListener(v->{if(name.equals("＋"))editor(null,"Memory",false);
                else{activeTab=name;render();}});
        }
        root.addView(nav);
    }
    private void showHome() {
        put(page,text("The beginning of forever ♡",29,TEXT,true),7,7);
        hint("Every little moment deserves to be remembered.");
        LinearLayout hero=vertical(20);hero.setBackground(bg(0xff46405c,21));put(page,hero,4,17);
        put(hero,text("✧  OCTOBER 31, 2026",12,GOLD,true),0,9);
        put(hero,text("Today, everything changed.",30,TEXT,true),0,9);
        put(hero,text("The day you found out your family is growing. Start telling your little one's story.",14,0xfff4eaf3,false),0,17);
        put(hero,button("♡ Capture today's news",true,()->editor(null,"Journal",true)),0,0);
        LinearLayout quick=card();line(quick,"Save a little moment","No internet, account, or subscription needed.");
        put(quick,button("＋ Add photo, video or memory",true,()->editor(null,"Memory",false)),0,8);
        put(quick,button("✉ Write a letter to your child",false,()->editor(null,"Letter",false)),0,0);
        section("Your story so far");
        List<Memory> list=memories();
        if(list.isEmpty()){LinearLayout empty=card();line(empty,"A beautiful blank page","Your first entry can be about how you felt when you heard the news.");}
        else for(int i=0;i<Math.min(5,list.size());i++)drawEntry(list.get(i));
    }
    private void showMemories(String filter) {
        put(page,text(filter==null?"The memory timeline":"Letters to the future",29,TEXT,true),5,6);
        hint(filter==null?"A private scrapbook of everything that matters.":"Messages your child can read one day.");
        put(page,button(filter==null?"＋ Create a memory":"＋ Write a letter",true,()->editor(null,filter==null?"Memory":"Letter",false)),0,14);
        int shown=0;
        for(Memory m:memories()) if(filter==null||m.kind.equals(filter)){drawEntry(m);shown++;}
        if(shown==0){LinearLayout c=card();line(c,"Nothing here yet","Every great story starts with the first memory.");}
    }
    private static final String[][] FIRSTS={
        {"Heartbeat","First heartbeat"},{"Ultrasound","First ultrasound"},{"Announcement","Pregnancy announcement"},
        {"Gender reveal","Gender reveal"},{"First kick","First little kick"},{"Birth","Welcome to the world"},
        {"First smile","First smile"},{"First steps","First steps"},{"First words","First words"}
    };
    private void showMilestones() {
        put(page,text("All the little firsts",29,TEXT,true),5,5);
        hint("Tiny moments. Lifetime memories.");
        for(String[] template:FIRSTS){
            boolean done=false;
            for(Memory m:memories()) if(m.kind.equals("Milestone")&&m.title.equals(template[1])){done=true;break;}
            LinearLayout c=card();put(c,text((done?"✓  ":"✧  ")+template[1],18,done?MINT:TEXT,true),0,10);
            put(c,button(done?"Add another moment":"Record this milestone",false,()->editor(null,"Milestone",false,template[1])),0,0);
        }
    }
    private void drawEntry(Memory m) {
        LinearLayout c=card();put(c,text(m.kind.toUpperCase(Locale.US)+"   •   "+m.day,11,MINT,true),0,8);
        put(c,text(m.title,21,TEXT,true),0,7);
        if(!m.body.isEmpty())put(c,text(m.body,14,MUTED,false),0,12);
        for(String name:m.media){
            File f=new File(new File(getFilesDir(),"media"),name);if(!f.isFile())continue;
            if(name.endsWith(".mp4")||name.endsWith(".mov")||name.endsWith(".webm")||name.endsWith(".mkv")){
                put(c,button("▶ Play video",false,()->openMedia(f,true)),0,8);
            }else{
                try{
                    BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getAbsolutePath(),opts);
                    opts.inJustDecodeBounds=false;opts.inSampleSize=Math.max(1,Math.max(opts.outWidth/800,opts.outHeight/800));
                    Bitmap bm=BitmapFactory.decodeFile(f.getAbsolutePath(),opts);
                    if(bm!=null){ImageView image=new ImageView(this);image.setImageBitmap(bm);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        c.addView(image,new LinearLayout.LayoutParams(-1,dp(190)));image.setOnClickListener(v->openMedia(f,false));}
                }catch(Exception ignored){}
            }
        }
        LinearLayout controls=new LinearLayout(this);
        Button add=button("＋ Photo/video",false,()->chooseMedia(m.id));
        Button edit=button("Edit",false,()->editor(m,m.kind,false));
        Button del=button("Delete",false,()->confirmDelete(m));
        controls.addView(add,new LinearLayout.LayoutParams(0,dp(48),2));
        controls.addView(edit,new LinearLayout.LayoutParams(0,dp(48),1));
        controls.addView(del,new LinearLayout.LayoutParams(0,dp(48),1));
        put(c,controls,11,0);
    }
    private void openMedia(File f,boolean video) {
        if(video){
            VideoView vv=new VideoView(this);vv.setVideoPath(f.getAbsolutePath());
            LinearLayout wrap=vertical(8);wrap.addView(vv,new LinearLayout.LayoutParams(-1,dp(330)));
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("A memory to treasure").setView(wrap).setPositiveButton("Close",(d,w)->{}).create();
            dialog.setOnShowListener(d->{vv.setMediaController(new MediaController(this));vv.start();});dialog.setOnDismissListener(d->vv.stopPlayback());dialog.show();
        }else{
            ImageView img=new ImageView(this);img.setImageURI(Uri.fromFile(f));img.setAdjustViewBounds(true);
            LinearLayout wrap=vertical(8);wrap.addView(img);
            new AlertDialog.Builder(this).setView(wrap).setPositiveButton("Close",(d,w)->{}).show();
        }
    }
    private EditText input(String value,String hint,boolean multi){
        EditText e=new EditText(this);e.setSingleLine(!multi);e.setText(value);e.setTextColor(TEXT);e.setHintTextColor(MUTED);
        e.setHint(hint);e.setTextSize(15);if(multi){e.setMinLines(5);e.setGravity(Gravity.TOP);e.setInputType(131073);}
        return e;
    }
    private void editor(Memory original,String kind,boolean first){editor(original,kind,first,null);}
    private void editor(Memory original,String kind,boolean first,String suggested) {
        LinearLayout form=vertical(17);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        put(form,text("Title",13,GOLD,true),0,3);
        EditText title=input(original!=null?original.title:(suggested!=null?suggested:first?"The day we found out about you":""),"Name this memory",false);
        put(form,title,0,12);
        put(form,text("Date (YYYY-MM-DD)",13,GOLD,true),0,3);
        EditText day=input(original!=null?original.day:first?"2026-10-31":today(),"YYYY-MM-DD",false);
        put(form,day,0,12);
        put(form,text("Category",13,GOLD,true),0,3);
        String[] kinds={"Memory","Journal","Milestone","Letter"};
        Spinner type=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,kinds);type.setAdapter(adapter);
        String start=original!=null?original.kind:kind;
        for(int i=0;i<kinds.length;i++)if(kinds[i].equals(start))type.setSelection(i);
        put(form,type,0,12);
        put(form,text("Your words",13,GOLD,true),0,3);
        EditText body=input(original!=null?original.body:"","What happened? How did you feel? What do you want them to know?",true);put(form,body,0,9);
        AlertDialog d=new AlertDialog.Builder(this).setTitle(original==null?"Capture a memory":"Edit memory")
            .setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(x->{
            String t=title.getText().toString().trim(),date=day.getText().toString().trim();
            if(t.isEmpty()||!date.matches("\\d{4}-\\d{2}-\\d{2}")){Toast.makeText(this,"Enter a title and date (YYYY-MM-DD)",Toast.LENGTH_LONG).show();return;}
            Memory m=original==null?new Memory():original;
            m.title=t;m.day=date;m.kind=type.getSelectedItem().toString();m.body=body.getText().toString().trim();save(m);
            d.dismiss();activeTab="Memories";render();
            if(original==null)new AlertDialog.Builder(this).setTitle("Memory saved ♡")
                .setMessage("Add photos or videos to this memory now?")
                .setPositiveButton("Add photos / videos",(dd,which)->chooseMedia(m.id))
                .setNegativeButton("Not now",null).show();
            else Toast.makeText(this,"Memory saved on your phone",Toast.LENGTH_SHORT).show();
        }));
        d.show();
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
        }else if(request==EXPORT)exportTo(data.getData());
        else if(request==IMPORT)importFrom(data.getData());
    }
    private void confirmDelete(Memory m){
        new AlertDialog.Builder(this).setTitle("Delete this memory?")
            .setMessage("This also deletes its attached photos/videos from this app. This cannot be undone.")
            .setNegativeButton("Keep",null).setPositiveButton("Delete",(d,w)->{
                db.delete("memories","id=?",new String[]{m.id});
                for(String name:m.media)new File(new File(getFilesDir(),"media"),name).delete();
                render();
            }).show();
    }
    private void showSettings(){
        put(page,text("Your little universe",29,TEXT,true),4,5);
        hint("Make the story your family's own. Everything remains on this phone.");
        LinearLayout c=card();
        EditText name=input(prefs.getString("childName",""),"Baby's name (when you choose)",false);
        EditText parents=input(prefs.getString("parents",""),"Parent names",false);
        EditText due=input(prefs.getString("dueDate",""),"Due date (YYYY-MM-DD)",false);
        put(c,text("Baby's name",12,GOLD,true),0,4);put(c,name,0,12);
        put(c,text("Parents",12,GOLD,true),0,4);put(c,parents,0,12);
        put(c,text("Due date",12,GOLD,true),0,4);put(c,due,0,15);
        put(c,button("Save family details",true,()->{
            prefs.edit().putString("childName",name.getText().toString().trim())
                .putString("parents",parents.getText().toString().trim())
                .putString("dueDate",due.getText().toString().trim()).apply();
            render();message("Family details saved ♡");
        }),0,0);
        section("Protect your memories");
        LinearLayout backup=card();line(backup,"Full ZIP backup","Exports your entire journal AND original photos/videos. Save the ZIP outside your phone.");
        put(backup,button("↓ Export complete backup",true,()->{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/zip");i.putExtra(Intent.EXTRA_TITLE,"Little_Universe_Backup_"+today()+".zip");
            startActivityForResult(i,EXPORT);
        }),0,9);
        put(backup,button("↑ Restore from backup ZIP",false,()->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/zip");startActivityForResult(i,IMPORT);
        }),0,0);
        hint("No internet permission. No accounts. No cloud. Your phone is the only live copy until you export a backup.");
    }
    private void exportTo(Uri location){
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
                for(Memory m:memories())data.put(m.json());
                root.put("memories",data);
                byte[] payload=root.toString().getBytes("UTF-8");
                zip.putNextEntry(new ZipEntry("archive.json"));zip.write(payload);zip.closeEntry();
                File dir=new File(getFilesDir(),"media");
                File[] files=dir.listFiles();
                if(files!=null)for(File f:files)if(f.isFile()){
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
        new AlertDialog.Builder(this).setTitle("Restore memories?")
            .setMessage("This merges memories from the selected Little Universe ZIP into your phone's existing timeline.")
            .setNegativeButton("Cancel",null).setPositiveButton("Restore",(dd,w)->doImport(location)).show();
    }
    private void doImport(Uri location){
        message("Restoring backup…");
        io.execute(()->{
            String err=null;File tmp=new File(getCacheDir(),"restore_"+UUID.randomUUID());tmp.mkdirs();
            try(InputStream raw=getContentResolver().openInputStream(location);
                ZipInputStream zip=new ZipInputStream(new BufferedInputStream(raw))){
                ByteArrayOutputStream meta=null;ZipEntry entry;int files=0;
                while((entry=zip.getNextEntry())!=null){
                    if(++files>20000)throw new IOException("Too many files in backup");
                    String name=entry.getName();OutputStream out;
                    if(name.equals("archive.json")){meta=new ByteArrayOutputStream();out=meta;}
                    else if(name.matches("media/[a-zA-Z0-9_.-]{1,150}")){
                        out=new FileOutputStream(new File(tmp,name.substring(6)));
                    }else throw new IOException("Invalid archive entry");
                    byte[] buf=new byte[65536];int n;long total=0;
                    while((n=zip.read(buf))!=-1){total+=n;if(name.equals("archive.json")&&total>10_000_000)throw new IOException("Journal too large");out.write(buf,0,n);}
                    out.close();zip.closeEntry();
                }
                if(meta==null)throw new IOException("Not a Little Universe backup");
                JSONObject root=new JSONObject(meta.toString("UTF-8"));
                if(!"little-universe-android-v1".equals(root.optString("format")))throw new IOException("Unrecognized backup format");
                JSONArray memories=root.getJSONArray("memories");
                ArrayList<Memory> recovered=new ArrayList<>();
                for(int i=0;i<memories.length();i++){
                    Memory m=Memory.from(memories.getJSONObject(i));
                    for(String name:m.media){
                        if(!name.matches("[a-zA-Z0-9_.-]{1,150}"))throw new IOException("Invalid media name");
                        File from=new File(tmp,name),existing=new File(new File(getFilesDir(),"media"),name);
                        if(!from.isFile()&&!existing.isFile())throw new IOException("Missing media: "+name);
                    }
                    recovered.add(m);
                }
                File destDir=new File(getFilesDir(),"media");
                File[] extracted=tmp.listFiles();
                if(extracted!=null)for(File f:extracted) {
                    File to=new File(destDir,f.getName());
                    if(!to.exists()&&!f.renameTo(to)){
                        try(InputStream in=new FileInputStream(f);OutputStream out=new FileOutputStream(to)){
                            byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
                        }
                    }
                }
                for(Memory m:recovered)save(m);
                JSONObject settings=root.optJSONObject("settings");
                if(settings!=null)prefs.edit().putString("childName",settings.optString("childName",""))
                    .putString("parents",settings.optString("parents",""))
                    .putString("dueDate",settings.optString("dueDate","")).apply();
            }catch(Exception e){err=e.toString();}
            File[] remains=tmp.listFiles();if(remains!=null)for(File f:remains)f.delete();tmp.delete();
            final String result=err;
            runOnUiThread(()->{render();message(result==null?"Backup restored ♡":"Restore failed: "+result);});
        });
    }
    private void message(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onBackPressed(){if(!activeTab.equals("Home")){activeTab="Home";render();}else super.onBackPressed();}
    @Override protected void onDestroy(){io.shutdown();db.close();super.onDestroy();}
}