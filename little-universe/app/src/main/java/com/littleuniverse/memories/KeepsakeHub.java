package com.littleuniverse.memories;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * All features work with SQLite and private local files, without a server.
 * The existing v3 screen is unchanged; this adds a separate feature hub.
 */
public final class KeepsakeHub {
    public interface Actions {
        void create(String kind,String title);
        void edit(String id);
        void refresh();
        void exportPdf();
    }
    private final Activity a;private final SQLiteDatabase db;
    private final Actions actions;
    private Dialog screen;
    private LinearLayout content;
    private final int WHITE=0xffF3F1FF,SOFT=0xffB8B9EC,BLUE=0xff8CBAFF,PURPLE=0xffC0A3FF;
    private final int BG=0xff090F25;
    private final String[] questions={
       "What did you feel when you found out about me?",
       "What's one thing you hope I'll always know?",
       "Who was the first person you told about me?",
       "What song reminds you of this chapter?",
       "What was a small moment that made you smile?",
       "What's something you love about our family?",
       "What's the funniest thing I said recently?",
       "What do you want to remember about today?",
       "What's something you learned about being a parent?",
       "What was my favorite sound or word this week?",
       "What ordinary thing became a treasured moment?",
       "What's a family tradition you hope I'll carry forward?",
       "What would you tell your younger self about today?",
       "What was my favorite toy, place or food?",
       "What's something kind someone did for our family?",
       "What are you most excited to show me someday?",
       "What's a story about my grandparents I should know?",
       "What's the best advice you've received for parenting?",
       "What would you want me to remember about our home?",
       "What was different about me this month?"
    };
    private final String[] milestoneGroups={
       "Pregnancy · First ultrasound","Pregnancy · Heartbeat","Pregnancy · First kick",
       "Pregnancy · Announcement","Pregnancy · Baby shower","Pregnancy · Hospital bag",
       "Newborn · Welcome home","Newborn · First photo","Newborn · First bath",
       "Newborn · First smile","Newborn · First laugh","Newborn · First time rolling",
       "Infant · First tooth","Infant · Sitting independently","Infant · First crawl",
       "Infant · First solid food","Infant · First time standing","Infant · First steps",
       "Infant · First word","Infant · First birthday","Toddler · First drawing",
       "Toddler · First big hug","Toddler · First sentence","Toddler · Potty training",
       "Toddler · First playground","Childhood · First school day","Childhood · First bike ride",
       "Childhood · First friend","Childhood · First sleepover","Childhood · First lost tooth",
       "Childhood · First team or hobby","Childhood · First independent reading",
       "Teen · Graduation","Teen · First job","Teen · First trip independently"
    };
    public KeepsakeHub(Activity activity,SQLiteDatabase data,Actions callback){
        a=activity;db=data;actions=callback;FeatureStore.init(db);
    }
    private int px(float n){return (int)(n*a.getResources().getDisplayMetrics().density+.5f);}
    private TextView label(String str,int size,int color,boolean strong){
        TextView t=new TextView(a);t.setText(str);t.setTextSize(size);t.setTextColor(color);
        if(strong)t.setTypeface(null,1);t.setLineSpacing(px(3),1);
        return t;
    }
    private LinearLayout column(int padding){
        LinearLayout v=new LinearLayout(a);v.setOrientation(1);
        v.setPadding(px(padding),px(padding),px(padding),px(padding));return v;
    }
    private void add(LinearLayout p,View child,int top,int bottom){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,px(top),0,px(bottom));p.addView(child,lp);
    }
    private LinearLayout tile(){
        LinearLayout t=column(16);
        t.setBackground(CosmicUI.glass(a,21,0xff262E74,0xff101A44,0xff7C76E9));
        t.setElevation(px(8));add(content,t,0,13);return t;
    }
    private Button button(String value,Runnable fn){
        Button b=new Button(a);b.setText(value);b.setTextColor(WHITE);b.setTextSize(13);b.setAllCaps(false);
        b.setBackground(CosmicUI.glass(a,15,0xff5941B8,0xff203C89,0xffAE91FF));
        b.setOnClickListener(v->fn.run());return b;
    }
    private void action(LinearLayout l,String value,Runnable fn){add(l,button(value,fn),6,5);}
    private void section(String name){add(content,label(name,22,WHITE,true),13,13);}
    private void caption(String value){add(content,label(value,13,SOFT,false),0,13);}
    private EditText edit(String hint,String value,boolean multiline){
        EditText e=new EditText(a);e.setText(value);e.setHint(hint);e.setTextColor(WHITE);
        e.setHintTextColor(SOFT);e.setTextSize(15);
        if(multiline){e.setMinLines(3);e.setSingleLine(false);e.setGravity(Gravity.TOP);}
        else e.setSingleLine(true);
        return e;
    }
    private void notice(String title,String detail){
        new AlertDialog.Builder(a).setTitle(title).setMessage(detail).setPositiveButton("Got it",null).show();
    }
    private String date(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}
    private String prompt(){return questions[Math.abs(date().hashCode()%questions.length)];}
    private void page(String heading,String intro){
        if(screen!=null)screen.dismiss();
        screen=new Dialog(a,android.R.style.Theme_Material_NoActionBar);
        LinearLayout root=column(0);root.setBackgroundColor(BG);
        LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(px(17),px(16),px(17),px(11));
        TextView back=label("‹",40,PURPLE,true);back.setGravity(Gravity.CENTER);
        header.addView(back,new LinearLayout.LayoutParams(px(45),px(48)));
        back.setOnClickListener(v->open());
        LinearLayout names=column(0);add(names,label("OUR LITTLE UNIVERSE  ·  MEMORY STUDIO",10,BLUE,true),0,5);
        add(names,label(heading,21,WHITE,true),0,0);header.addView(names);
        root.addView(header);
        ScrollView scroll=new ScrollView(a);content=column(19);
        content.setPadding(px(19),px(10),px(19),px(42));scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(intro!=null&&!intro.isEmpty())caption(intro);
        screen.setContentView(root);screen.show();screen.getWindow().setLayout(-1,-1);
    }
    public void open(){
        page("The memory studio","Everything you need to preserve your family's story, from pregnancy to adulthood.");
        LinearLayout people=tile();
        add(people,label("♡  Our contributors",19,WHITE,true),0,7);
        add(people,label("Signed in on this phone as: "+FeatureStore.author(a),13,SOFT,false),0,7);
        action(people,"Manage who is writing  →",this::profiles);
        LinearLayout moment=tile();
        add(moment,label("A question for today",18,WHITE,true),0,8);
        add(moment,label(prompt(),15,BLUE,false),0,9);
        action(moment,"Answer in the journal",()->create("Journal",prompt()));
        section("Explore your memories");
        menu("⌕  Search & filter","Find a date, a word, a person or a memory type",this::search);
        menu("♡  Favorite moments","Your most-loved moments in one place",()->searchFiltered("favorites"));
        menu("▣  Photo & video gallery","Browse media captured in your memories",this::gallery);
        menu("✦  Keepsake Maker","Turn 1–4 photos into a polished announcement or collectible card",this::keepsakeMaker);
        menu("▶  Growing-up slideshow","Play photos from your timeline in date order",this::slideshow);
        menu("◷  On this day","Rediscover memories from the same day in past years",this::onThisDay);
        menu("▤  Monthly & yearly recaps","Revisit the chapters you recorded",this::recaps);
        menu("▦  Calendar","Jump to a date and see what happened",this::calendar);
        section("Our growing story");
        menu("✦  35 milestone ideas","Capture your child's firsts, from pregnancy onward",this::milestones);
        menu("↑  Height & weight","Keep a growth history and visual chart",this::growth);
        menu("☼  Pregnancy chapter","Track weeks, bump pictures and preparations",this::pregnancy);
        menu("☷  Birthday interviews","Ask the same questions as your child grows",this::birthdayInterviews);
        menu("✉  Future letters & time capsules","Plan special messages for milestones",this::timeCapsules);
        menu("☑  Family lists & traditions","Record wishlists, to-dos, traditions and stories",this::familyLists);
        menu("◴  Personal reminders","Schedule memories to capture and celebrations",this::reminders);
        section("Make it yours");
        menu("✧  Prompt library","Journaling ideas for any age",this::promptLibrary);
        menu("☷  Baby book PDF","Export a readable, printable keepsake",()->{screen.dismiss();actions.exportPdf();});
        menu("⚙  Family identities","Choose who contributes on this phone",this::profiles);
        caption("Everything stays on this device until you choose to export or send an archive.");
    }
    private void menu(String title,String description,Runnable tap){
        LinearLayout t=tile();
        add(t,label(title,18,WHITE,true),0,5);
        add(t,label(description,12,SOFT,false),0,0);
        t.setOnClickListener(v->tap.run());
    }
    private void create(String kind,String name){
        screen.dismiss();actions.create(kind,name);
    }
    public void profiles(){
        page("Contributors","Everyone can credit their own memories. Their names travel with shared ZIP archives.");
        LinearLayout c=tile();add(c,label("Who is using this phone?",17,WHITE,true),0,9);
        SharedPreferences prefs=a.getSharedPreferences("little_universe_profile",Context.MODE_PRIVATE);
        EditText name=edit("Your name",FeatureStore.author(a),false);add(c,name,0,7);
        caption("Change this when another relative uses the same device. Old entries keep their original author credit.");
        action(c,"Save contributor name",()->{
            String newName=name.getText().toString().trim();
            if(newName.isEmpty()){notice("A name is needed","Enter Dad, Mom, Grandma or any preferred name.");return;}
            prefs.edit().putString("contributorName",newName).apply();
            open();actions.refresh();
        });
        menu("Dad","Use Dad as this device's contributor",()->setContributor("Dad"));
        menu("Mariah","Use Mariah as this device's contributor",()->setContributor("Mariah"));
        menu("Grandma","Use Grandma as this device's contributor",()->setContributor("Grandma"));
        menu("Papa","Use Papa as this device's contributor",()->setContributor("Papa"));
    }
    private void setContributor(String value){
        a.getSharedPreferences("little_universe_profile",Context.MODE_PRIVATE).edit()
            .putString("contributorName",value).apply();open();actions.refresh();
    }
    public void search(){
        page("Find a memory","Search memories, quotes, author credits and your personal tags.");
        LinearLayout card=tile();
        EditText term=edit("Search words, tags, names…","",false);add(card,term,0,9);
        action(card,"Search across the archive",()->searchResults(term.getText().toString().trim(),"all"));
        action(card,"Favorites only",()->searchFiltered("favorites"));
        action(card,"Letters only",()->searchFiltered("letters"));
        action(card,"Voice memories",()->searchFiltered("voice"));
    }
    private void searchFiltered(String filter){searchResults("",filter);}
    private void searchResults(String query,String filter){
        page("Memory results",query.isEmpty()?"Browse your family's recorded moments":"Results for “"+query+"”");
        int found=0;
        try(Cursor c=db.rawQuery("SELECT id,day,title,kind,body,media FROM memories ORDER BY day DESC",null)){
            while(c.moveToNext()){
                String id=c.getString(0),day=c.getString(1),title=c.getString(2),kind=c.getString(3),body=c.getString(4);
                JSONObject d=FeatureStore.detail(db,id);
                String author=d.optString("author","Family");
                String tags=d.optJSONArray("tags")==null?"":d.optJSONArray("tags").toString();
                if(filter.equals("favorites")&&!d.optBoolean("favorite",false))continue;
                if(filter.equals("letters")&&!kind.equals("Letter"))continue;
                if(filter.equals("voice")&&!(kind.equals("Voice")||c.getString(5).contains(".m4a")))continue;
                String hay=(title+" "+body+" "+kind+" "+day+" "+author+" "+tags).toLowerCase(Locale.US);
                if(!hay.contains(query.toLowerCase(Locale.US)))continue;
                found++;
                LinearLayout card=tile();
                add(card,label(title,18,WHITE,true),0,7);
                add(card,label(day+"  ·  "+kind+"  ·  By "+author,12,BLUE,false),0,8);
                if(!body.isEmpty())add(card,label(body.length()>130?body.substring(0,130)+"…":body,13,SOFT,false),0,5);
                action(card,"Open / edit this moment",()->{screen.dismiss();actions.edit(id);});
            }
        }
        if(found==0)caption("No matching memories yet. Try a different word or filter.");
    }
    private void keepsakeMaker(){
        if(screen!=null)screen.dismiss();
        new KeepsakeMaker(a,db,()->{
            actions.refresh();
            open();
        }).open();
    }
    private List<String[]> photos(){
        List<String[]> photos=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT id,day,title,media FROM memories ORDER BY day ASC,rowid ASC",null)){
            File dir=new File(a.getFilesDir(),"media");
            while(c.moveToNext()){
                JSONArray array=new JSONArray(c.getString(3));
                for(int i=0;i<array.length();i++){
                    String name=array.getString(i).toLowerCase(Locale.US);
                    if(!(name.endsWith(".jpg")||name.endsWith(".jpeg")||name.endsWith(".png")||name.endsWith(".webp")||name.endsWith(".heic")))continue;
                    File f=new File(dir,array.getString(i));
                    if(f.isFile())photos.add(new String[]{c.getString(0),c.getString(1),c.getString(2),f.getAbsolutePath()});
                }
            }
        }catch(Exception ignored){}
        return photos;
    }
    public void gallery(){
        page("Photo & video gallery","Original pictures and clips from all your saved moments.");
        int count=0;
        try(Cursor c=db.rawQuery("SELECT id,day,title,media FROM memories ORDER BY day DESC",null)){
            while(c.moveToNext()){
                String id=c.getString(0),day=c.getString(1),title=c.getString(2);
                JSONArray media=new JSONArray(c.getString(3));
                for(int i=0;i<media.length();i++){
                    String name=media.getString(i).toLowerCase(Locale.US);
                    boolean video=name.endsWith(".mp4")||name.endsWith(".mov")||name.endsWith(".webm");
                    if(!video && !(name.endsWith(".jpg")||name.endsWith(".jpeg")||name.endsWith(".png")||name.endsWith(".webp")))continue;
                    count++;
                    LinearLayout card=tile();
                    if(!video){
                        File f=new File(new File(a.getFilesDir(),"media"),media.getString(i));
                        ImageView img=new ImageView(a);img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=3;
                        Bitmap bitmap=BitmapFactory.decodeFile(f.getAbsolutePath(),o);
                        if(bitmap!=null)img.setImageBitmap(bitmap);
                        card.addView(img,new LinearLayout.LayoutParams(-1,px(165)));
                    }
                    add(card,label((video?"▶ Video · ":"▣ Photo · ")+day,12,BLUE,true),7,5);
                    add(card,label(title,16,WHITE,true),0,4);
                    action(card,"Open original memory",()->{screen.dismiss();actions.edit(id);});
                }
            }
        }catch(Exception ignored){}
        if(count==0)caption("No photos or videos yet. Add one to a memory to see it here.");
        action(tile(),"Play photo slideshow",this::slideshow);
        action(tile(),"Create a photo keepsake",this::keepsakeMaker);
    }
    private void slideshow(){
        List<String[]> frames=photos();
        if(frames.isEmpty()){notice("No pictures yet","Add some pictures to your memory timeline first.");return;}
        if(screen!=null)screen.dismiss();
        Dialog player=new Dialog(a,android.R.style.Theme_Material_NoActionBar_Fullscreen);
        LinearLayout root=column(13);root.setBackgroundColor(BG);
        TextView heading=label("Our life in pictures",22,WHITE,true);add(root,heading,9,8);
        ImageView image=new ImageView(a);image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image,new LinearLayout.LayoutParams(-1,0,1));
        TextView caption=label("",14,SOFT,false);caption.setGravity(Gravity.CENTER);add(root,caption,11,13);
        final int[] index={0};
        Handler handler=new Handler(Looper.getMainLooper());
        Runnable update=()->{
            String[] item=frames.get(index[0]);
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;
            Bitmap bmp=BitmapFactory.decodeFile(item[3],options);
            if(bmp!=null)image.setImageBitmap(bmp);
            caption.setText((index[0]+1)+"/"+frames.size()+"  ·  "+item[1]+"\n"+item[2]);
        };
        update.run();
        Runnable ticker=new Runnable(){
            @Override public void run(){
                if(!player.isShowing())return;
                index[0]=(index[0]+1)%frames.size();update.run();
                handler.postDelayed(this,2800);
            }
        };
        LinearLayout buttons=new LinearLayout(a);
        Button prev=button("‹ Previous",()->{index[0]=(index[0]-1+frames.size())%frames.size();update.run();});
        Button pause=button("Pause",()->{handler.removeCallbacks(ticker);});
        Button next=button("Next ›",()->{index[0]=(index[0]+1)%frames.size();update.run();});
        buttons.addView(prev,new LinearLayout.LayoutParams(0,px(58),1));
        buttons.addView(pause,new LinearLayout.LayoutParams(0,px(58),1));
        buttons.addView(next,new LinearLayout.LayoutParams(0,px(58),1));add(root,buttons,0,15);
        Button close=button("Close slideshow",()->player.dismiss());add(root,close,0,6);
        player.setContentView(root);player.show();player.getWindow().setLayout(-1,-1);
        handler.postDelayed(ticker,2800);
        player.setOnDismissListener(d->{handler.removeCallbacks(ticker);open();});
    }
    private void onThisDay(){
        String monthDay=date().substring(5);
        page("On this day","Memories from "+monthDay+" in any year.");
        int found=0;
        try(Cursor c=db.rawQuery("SELECT id,day,title,kind FROM memories ORDER BY day DESC",null)){
            while(c.moveToNext()){
                if(!c.getString(1).endsWith(monthDay))continue;
                found++;String id=c.getString(0);
                menu(c.getString(2),c.getString(1)+" · "+c.getString(3),()->{screen.dismiss();actions.edit(id);});
            }
        }
        if(found==0)caption("No earlier memories on this calendar date yet. They will appear as your family archive grows.");
    }
    private void recaps(){
        page("Chapters & recaps","Explore every year and month represented in your timeline.");
        LinkedHashMap<String,Integer> counts=new LinkedHashMap<>();
        try(Cursor c=db.rawQuery("SELECT day FROM memories ORDER BY day DESC",null)){
            while(c.moveToNext()){
                String day=c.getString(0);if(day.length()<7)continue;
                String month=day.substring(0,7);counts.put(month,counts.containsKey(month)?counts.get(month)+1:1);
            }
        }
        for(Map.Entry<String,Integer> e:counts.entrySet()){
            String month=e.getKey();
            menu(month,e.getValue()+" saved moments",()->recapMonth(month));
        }
        if(counts.isEmpty())caption("Month-by-month chapters will appear as you add memories.");
    }
    private void recapMonth(String month){
        page("Chapter: "+month,"A collection of the moments this month.");
        int n=0;
        try(Cursor c=db.rawQuery("SELECT id,title,day,kind FROM memories WHERE day LIKE ? ORDER BY day ASC",new String[]{month+"%"})){
            while(c.moveToNext()){
                n++;String id=c.getString(0);
                menu(c.getString(1),c.getString(2)+" · "+c.getString(3),()->{screen.dismiss();actions.edit(id);});
            }
        }
        caption(n+" moments recorded in this chapter.");
    }
    private void calendar(){
        Calendar now=Calendar.getInstance();
        new DatePickerDialog(a,(picker,year,month,day)->{
            String selected=String.format(Locale.US,"%04d-%02d-%02d",year,month+1,day);
            page("Memories on "+selected,"A little window into that day.");
            int n=0;
            try(Cursor c=db.rawQuery("SELECT id,title,kind FROM memories WHERE day=? ORDER BY rowid DESC",new String[]{selected})){
                while(c.moveToNext()){
                    n++;String id=c.getString(0);menu(c.getString(1),c.getString(2),
                        ()->{screen.dismiss();actions.edit(id);});
                }
            }
            if(n==0)caption("Nothing saved for this date yet.");
            action(tile(),"Capture a new memory",()->create("Memory","A memory from "+selected));
        },now.get(Calendar.YEAR),now.get(Calendar.MONTH),now.get(Calendar.DAY_OF_MONTH)).show();
    }
    private void milestones(){
        page("Milestone treasury","A collection of 35 ideas, from pregnancy to graduation. Every child develops at their own pace.");
        int completed=0;
        for(String entry:milestoneGroups){
            String title=entry.substring(entry.indexOf("·")+1).trim();
            if(hasMilestone(title))completed++;
        }
        caption(completed+" of "+milestoneGroups.length+" recorded. These are memory prompts, not developmental deadlines.");
        for(String entry:milestoneGroups){
            String title=entry.substring(entry.indexOf("·")+1).trim();
            menu((hasMilestone(title)?"✓  ":"✧  ")+title,entry.substring(0,entry.indexOf("·")).trim(),
                ()->create("Milestone",title));
        }
    }
    private boolean hasMilestone(String title){
        try(Cursor c=db.rawQuery("SELECT 1 FROM memories WHERE kind='Milestone' AND title=? LIMIT 1",
            new String[]{title})){return c.moveToFirst();}
    }
    private void pregnancy(){
        page("Our pregnancy chapter","A personal journal for the weeks before your little one arrives.");
        SharedPreferences prefs=a.getSharedPreferences("little_universe_profile",Context.MODE_PRIVATE);
        String due=prefs.getString("dueDate","");
        LinearLayout overview=tile();
        if(due.matches("\\d{4}-\\d{2}-\\d{2}")){
            try{
                long remaining=(new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(due).getTime()-new Date().getTime())/86400000L;
                add(overview,label(remaining>=0?remaining+" days to the estimated due date":"Due date passed",25,WHITE,true),0,9);
                long daysPregnant=280-remaining;
                if(remaining>=0 && daysPregnant>0 && daysPregnant<=294)
                    add(overview,label("Approximate week "+(daysPregnant/7)+" · based on 40-week estimated due date",13,SOFT,false),0,8);
            }catch(Exception ignored){}
        }else add(overview,label("Add an estimated due date in the Vault to see a countdown.",14,SOFT,false),0,8);
        action(overview,"Record this week's pregnancy journal",()->create("Journal","Pregnancy check-in · "+date()));
        action(overview,"Save a bump photo memory",()->create("Memory","Baby bump · "+date()));
        action(overview,"Record an ultrasound memory",()->create("Milestone","Ultrasound"));
        for(String title:new String[]{"How we found out","Who we told first","First ultrasound","First heartbeat",
            "First kick","Baby name ideas","Things we're excited about","Nursery preparations",
            "Baby shower memories","Letters to our unborn baby"}){
            menu(title,"Add this chapter to the memory timeline",()->create("Journal",title));
        }
    }
    private void birthdayInterviews(){
        page("Birthday interviews","Ask the same questions every birthday. Compare the answers over the years.");
        final String[] q={"What's your favorite thing to do?","What makes you laugh?",
            "What's your favorite food?","Who's your best friend?","What are you proud of?",
            "What do you want to be when you grow up?","What's your favorite song?",
            "What's something you want to learn?","What do you love about your family?"};
        for(int i=0;i<q.length;i++){final int num=i+1;
            menu(q[i],"Record an answer from birthday interview question "+num,
                ()->create("Interview",q[num-1]+" · "+Calendar.getInstance().get(Calendar.YEAR)));
        }
        action(tile(),"Record this year's full interview",
            ()->create("Interview","Birthday interview · "+Calendar.getInstance().get(Calendar.YEAR)));
    }
    private void growth(){
        page("Growth journal","Track height and weight over time. Personal history only—not medical advice.");
        LinearLayout card=tile();
        action(card,"＋ Add a height measurement",()->newRecord("Height","Height in cm", "e.g. 75.5"));
        action(card,"＋ Add a weight measurement",()->newRecord("Weight","Weight in kg", "e.g. 9.2"));
        int count=0;
        for(String kind:new String[]{"Height","Weight"}){
            List<FeatureStore.Item> measurements=FeatureStore.items(db,kind);
            if(measurements.isEmpty())continue;
            count+=measurements.size();
            section(kind+" history");
            ArrayList<float[]> points=new ArrayList<>();
            ArrayList<FeatureStore.Item> ascending=new ArrayList<>(measurements);
            Collections.reverse(ascending);
            for(int i=0;i<ascending.size();i++){
                FeatureStore.Item m=ascending.get(i);
                try{points.add(new float[]{i,Float.parseFloat(m.body)});}catch(Exception ignored){}
            }
            if(points.size()>1){
                View chart=new View(a){
                    Paint p=new Paint(3);
                    @Override protected void onDraw(Canvas c){
                        float w=getWidth(),h=getHeight();float min=Float.MAX_VALUE,max=-Float.MAX_VALUE;
                        for(float[] v:points){min=Math.min(min,v[1]);max=Math.max(max,v[1]);}
                        if(max<=min)max=min+1;
                        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(px(3));p.setColor(0xff9F8BFF);
                        Path path=new Path();
                        for(int i=0;i<points.size();i++){
                            float x=px(18)+(w-px(36))*i/(points.size()-1f);
                            float y=px(15)+(h-px(34))*(1-(points.get(i)[1]-min)/(max-min));
                            if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
                        }
                        c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
                    }
                };
                LinearLayout box=tile();box.addView(chart,new LinearLayout.LayoutParams(-1,px(105)));
            }
            for(FeatureStore.Item m:measurements)
                menu(m.day+" · "+m.body+(kind.equals("Height")?" cm":" kg"),
                    m.author,()->editRecord(m));
        }
        if(count==0)caption("Measurements will appear here after you record the first one.");
    }
    private void newRecord(String kind,String title,String hint){
        LinearLayout form=column(16);
        EditText value=edit(hint,"",false);value.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|
            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        add(form,label(title,17,WHITE,true),0,9);add(form,value,0,7);
        Calendar c=Calendar.getInstance();
        new AlertDialog.Builder(a).setTitle("Save "+kind.toLowerCase(Locale.US))
            .setView(form).setNegativeButton("Cancel",null)
            .setPositiveButton("Save",(d,w)->{
                try{
                    double n=Double.parseDouble(value.getText().toString());
                    if(!Double.isFinite(n)||n<=0||n>1000){notice("Invalid value","Enter a positive measurement.");return;}
                    FeatureStore.Item item=new FeatureStore.Item();
                    item.kind=kind;item.day=date();item.title=title;
                    item.body=String.valueOf(n);item.author=FeatureStore.author(a);
                    FeatureStore.save(db,item);growth();
                }catch(Exception e){notice("Invalid value","Please enter a number.");}
            }).show();
    }
    private void editRecord(FeatureStore.Item record){
        new AlertDialog.Builder(a).setTitle(record.title)
            .setMessage(record.day+"\n"+record.body+"\nRecorded by "+record.author)
            .setPositiveButton("Keep",null)
            .setNegativeButton("Delete",(d,w)->{
                new AlertDialog.Builder(a).setTitle("Delete measurement?")
                  .setMessage("This removes this single recorded measurement.")
                  .setPositiveButton("Delete",(d2,w2)->{FeatureStore.delete(db,record.id);growth();})
                  .setNegativeButton("Cancel",null).show();
            }).show();
    }
    private void timeCapsules(){
        page("Letters to the future","Write letters, then add a reminder date for when you'd love them to be read.");
        caption("Time capsule dates are keepsake labels, not encryption or access locks.");
        action(tile(),"Write a letter",()->create("Letter","A letter for you someday"));
        try(Cursor c=db.rawQuery("SELECT id,day,title FROM memories WHERE kind='Letter' ORDER BY day DESC",null)){
            while(c.moveToNext()){
                String id=c.getString(0),title=c.getString(2);
                JSONObject details=FeatureStore.detail(db,id);
                menu("✉  "+title,details.optString("unlockDate","No opening date yet"),
                    ()->timeCapsuleDetail(id));
            }
        }
    }
    private void timeCapsuleDetail(String id){
        JSONObject meta=FeatureStore.detail(db,id);
        String when=meta.optString("unlockDate","");
        EditText date=edit("YYYY-MM-DD",when,false);
        LinearLayout form=column(17);add(form,label("Opening date",16,WHITE,true),0,8);add(form,date,0,0);
        new AlertDialog.Builder(a).setTitle("Time capsule reminder").setView(form)
            .setPositiveButton("Save date",(dialog,which)->{
                String text=date.getText().toString().trim();
                if(!text.isEmpty()&&!text.matches("\\d{4}-\\d{2}-\\d{2}")){
                    notice("Date format","Use YYYY-MM-DD.");return;
                }
                try{meta.put("unlockDate",text);}catch(Exception ignored){}
                FeatureStore.putDetail(db,id,meta);timeCapsules();
            }).setNeutralButton("Read letter",(d,w)->{screen.dismiss();actions.edit(id);})
            .setNegativeButton("Cancel",null).show();
    }
    private void familyLists(){
        page("Family traditions & lists","Create little lists, keepsake notes, family recipes, stories and traditions.");
        for(String type:new String[]{"Tradition","Recipe","Family Story","Name Idea","Wishlist","Packing List","Family To-do"}){
            menu("＋ "+type,"Create a local family entry",()->newGeneric(type));
        }
        section("Saved family records");
        for(FeatureStore.Item i:FeatureStore.items(db,null)){
            if(i.kind.equals("Height")||i.kind.equals("Weight")||i.kind.equals("Reminder"))continue;
            menu(i.title,i.kind+" · "+i.author,()->editRecord(i));
        }
    }
    private void newGeneric(String type){newGeneric(type,"");}
    private void newGeneric(String type,String defaultTitle){
        LinearLayout form=column(12);EditText title=edit("Name",defaultTitle,false);
        EditText body=edit("Details, notes or checklist items (one per line)","",true);
        add(form,title,0,7);add(form,body,0,6);
        new AlertDialog.Builder(a).setTitle("New "+type).setView(form)
            .setPositiveButton("Save",(dialog,which)->{
                String t=title.getText().toString().trim();if(t.isEmpty())return;
                FeatureStore.Item record=new FeatureStore.Item();
                record.kind=type;record.day=date();record.title=t;
                record.body=body.getText().toString();record.author=FeatureStore.author(a);
                FeatureStore.save(db,record);familyLists();
            }).setNegativeButton("Cancel",null).show();
    }
    private void reminders(){
        page("Family reminders","Local reminders for events, photos, memory prompts and celebrations.");
        action(tile(),"＋ Schedule a reminder",this::newReminder);
        for(FeatureStore.Item record:FeatureStore.items(db,"Reminder")){
            menu(record.title,record.day+" · "+record.body,
                ()->new AlertDialog.Builder(a).setTitle(record.title)
                    .setMessage(record.day+"\n"+record.body)
                    .setPositiveButton("Keep",null)
                    .setNegativeButton("Remove",(d,w)->{
                        ReminderReceiver.cancel(a,record.id);
                        FeatureStore.delete(db,record.id);reminders();
                    }).show());
        }
    }
    private void newReminder(){
        LinearLayout form=column(14);EditText name=edit("What do you want to remember?","Take a pregnancy photo",false);
        add(form,name,0,13);
        Calendar date=Calendar.getInstance();date.add(Calendar.DAY_OF_YEAR,1);
        final Calendar chosen=(Calendar)date.clone();
        Button pick=button("Choose date & time",()->new DatePickerDialog(a,(view,y,m,d)->{
            chosen.set(y,m,d);
            new TimePickerDialog(a,(view2,h,min)->{
                chosen.set(Calendar.HOUR_OF_DAY,h);chosen.set(Calendar.MINUTE,min);
                notice("Reminder time selected",new SimpleDateFormat("MMM d, yyyy · h:mm a",Locale.US).format(chosen.getTime()));
            },9,0,false).show();
        },chosen.get(Calendar.YEAR),chosen.get(Calendar.MONTH),chosen.get(Calendar.DAY_OF_MONTH)).show());
        add(form,pick,0,6);
        new AlertDialog.Builder(a).setTitle("New reminder").setView(form)
            .setPositiveButton("Save",(dialog,which)->{
                if(name.getText().toString().trim().isEmpty())return;
                FeatureStore.Item item=new FeatureStore.Item();
                item.kind="Reminder";item.day=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(chosen.getTime());
                item.title=name.getText().toString().trim();
                item.body=new SimpleDateFormat("h:mm a",Locale.US).format(chosen.getTime());
                item.author=FeatureStore.author(a);
                try{item.extras.put("trigger",chosen.getTimeInMillis());}catch(Exception ignored){}
                FeatureStore.save(db,item);
                ReminderReceiver.schedule(a,item);
                if(Build.VERSION.SDK_INT>=33&&a.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=
                   android.content.pm.PackageManager.PERMISSION_GRANTED)
                    a.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},7654);
                reminders();
            }).setNegativeButton("Cancel",null).show();
    }
    public void dueReminders(){
        long now=System.currentTimeMillis();int n=0;
        for(FeatureStore.Item i:FeatureStore.items(db,"Reminder")){
            long stamp=i.extras.optLong("trigger",0);
            if(stamp<=now&&stamp>=now-86400000L &&!i.extras.optBoolean("dismissed",false))n++;
        }
        if(n>0)notice("You have "+n+" reminder"+(n==1?"":"s"),
            "Open the Memory Studio → Personal reminders to review your saved events.");
    }
    private void promptLibrary(){
        page("Journal prompt library","A little inspiration when you don't know what to write.");
        int n=0;
        for(String q:questions){
            menu(String.format(Locale.US,"%02d  %s",++n,q),"Answer this as a new journal entry",()->create("Journal",q));
        }
    }
}