package com.littleuniverse.memories;

import android.app.*;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.*;
import android.net.Uri;
import android.os.Build;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Actual usable in-app keepsake-card maker; no cloud, templates or assets to download. */
public final class KeepsakeMaker {
    private final Activity activity;
    private final SQLiteDatabase database;
    private final Runnable onClose;
    private final ArrayList<KeepsakeCards.Photo> allPhotos=new ArrayList<>();
    private final ArrayList<KeepsakeCards.Photo> chosen=new ArrayList<>();
    private final int WHITE=0xffF2ECFF,SOFT=0xffBCC0E5,BLUE=0xff91B7FF;
    private Dialog editor,preview;
    private LinearLayout content;
    private LinearLayout photoRows;
    private TextView counter;
    private EditText heading,dedication;
    private Button themeButton,formatButton;
    private int theme=KeepsakeCards.BLUE,format=KeepsakeCards.PORTRAIT;
    private int shown=0;
    private final int PAGE=24;

    public KeepsakeMaker(Activity owner,SQLiteDatabase data,Runnable callback){
        activity=owner;database=data;onClose=callback;
    }
    private int dp(float v){return (int)(activity.getResources().getDisplayMetrics().density*v+.5f);}
    private LinearLayout vertical(int padding){
        LinearLayout l=new LinearLayout(activity);l.setOrientation(1);
        l.setPadding(dp(padding),dp(padding),dp(padding),dp(padding));return l;
    }
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(activity);t.setText(s);t.setTextColor(color);t.setTextSize(size);
        if(bold)t.setTypeface(null,1);return t;
    }
    private void add(LinearLayout parent,View view,int gap){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(gap),0,0);parent.addView(view,p);
    }
    private LinearLayout card(){
        LinearLayout box=vertical(13);
        box.setBackground(CosmicUI.glass(activity,17,0xff292B72,0xff11183B,0xff706DE0));
        box.setElevation(dp(5));add(content,box,13);
        return box;
    }
    private Button button(String title,Runnable action){
        Button b=new Button(activity);b.setAllCaps(false);b.setText(title);b.setTextColor(WHITE);
        b.setTextSize(13);b.setBackground(CosmicUI.glass(activity,15,0xff4B3B99,0xff183477,0xffA18CFA));
        b.setOnClickListener(v->action.run());return b;
    }
    private EditText input(String hint,String value){
        EditText t=new EditText(activity);t.setHint(hint);t.setText(value);t.setTextSize(15);
        t.setSingleLine(false);t.setMaxLines(3);
        t.setTextColor(WHITE);t.setHintTextColor(SOFT);
        return t;
    }
    private void alert(String title,String message){
        new AlertDialog.Builder(activity).setTitle(title).setMessage(message)
            .setPositiveButton("OK",null).show();
    }
    private ArrayList<KeepsakeCards.Photo> photos(){
        ArrayList<KeepsakeCards.Photo> out=new ArrayList<>();
        try(Cursor c=database.rawQuery(
            "SELECT id,day,title,media FROM memories ORDER BY day DESC,rowid DESC",null)){
            while(c.moveToNext()){
                org.json.JSONArray files=new org.json.JSONArray(c.getString(3));
                for(int i=0;i<files.length();i++){
                    String name=files.optString(i);
                    String lower=name.toLowerCase(Locale.US);
                    if(!(lower.endsWith(".jpg")||lower.endsWith(".jpeg")||lower.endsWith(".png")||
                        lower.endsWith(".webp")||lower.endsWith(".heic")||lower.endsWith(".heif")))continue;
                    File image=new File(new File(activity.getFilesDir(),"media"),name);
                    if(image.isFile())out.add(new KeepsakeCards.Photo(
                        c.getString(0),c.getString(1),c.getString(2),image.getAbsolutePath()));
                }
            }
        }catch(Exception ignored){}
        return out;
    }
    public void open(){
        allPhotos.clear();allPhotos.addAll(photos());
        if(allPhotos.isEmpty()){
            alert("Add a photo first","Create a memory with a picture, then return to the Keepsake Maker.");
            if(onClose!=null)onClose.run();
            return;
        }
        if(editor!=null&&editor.isShowing())editor.dismiss();
        editor=new Dialog(activity,android.R.style.Theme_Material_NoActionBar);
        LinearLayout root=vertical(0);root.setBackgroundColor(0xff090F25);
        LinearLayout top=new LinearLayout(activity);top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16),dp(13),dp(16),dp(9));
        TextView back=text("‹",38,BLUE,true);back.setGravity(Gravity.CENTER);
        top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(55)));
        back.setOnClickListener(v->{editor.dismiss();if(onClose!=null)onClose.run();});
        LinearLayout topLabels=vertical(0);
        add(topLabels,text("OUR LITTLE UNIVERSE",10,BLUE,true),0);
        add(topLabels,text("Keepsake Maker",24,WHITE,true),4);
        top.addView(topLabels);root.addView(top);
        ScrollView scroll=new ScrollView(activity);content=vertical(19);
        content.setPadding(dp(19),dp(8),dp(19),dp(32));scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        add(content,text("Turn the photos you've already saved into something beautiful—an announcement, photo collage or storybook card.",14,SOFT,false),4);
        LinearLayout info=card();
        add(info,text("YOUR KEEPSAKE'S TITLE",11,BLUE,true),0);
        heading=input("What should this keepsake say?",chosen.isEmpty()?"The day everything changed":heading==null?"":heading.getText().toString());
        add(info,heading,6);
        add(info,text("A NOTE FROM THE HEART",11,BLUE,true),10);
        dedication=input("Something you always want to remember",
            chosen.isEmpty()?"Before we met you, we loved you.":dedication==null?"":dedication.getText().toString());
        add(info,dedication,6);
        LinearLayout options=card();
        themeButton=button("Colors: "+KeepsakeCards.themeName(theme),()->{
            theme=(theme+1)%3;themeButton.setText("Colors: "+KeepsakeCards.themeName(theme));
        });
        formatButton=button("Format: "+KeepsakeCards.sizeName(format),()->{
            format=(format+1)%3;formatButton.setText("Format: "+KeepsakeCards.sizeName(format));
        });
        add(options,themeButton,4);add(options,formatButton,8);
        add(content,text("CHOOSE 1–4 PHOTOS",17,WHITE,true),18);
        counter=text("0 selected · Up to 4 photos",13,BLUE,true);add(content,counter,5);
        shown=0;
        photoRows=vertical(0);
        add(content,photoRows,9);
        loadMore();
        add(content,button("Preview my keepsake  →",this::showPreview),20);
        editor.setContentView(root);editor.show();editor.getWindow().setLayout(-1,-1);
    }
    private void loadMore(){
        int target=Math.min(allPhotos.size(),shown+PAGE);
        while(shown<target){
            KeepsakeCards.Photo image=allPhotos.get(shown++);
            LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12),dp(9),dp(12),dp(9));
            row.setBackground(CosmicUI.glass(activity,14,0xff242456,0xff131D49,0xff6B62BF));
            LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2);
            rowParams.setMargins(0,dp(9),0,0);photoRows.addView(row,rowParams);
            ImageView thumbnail=new ImageView(activity);thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=8;
            Bitmap bitmap=BitmapFactory.decodeFile(image.path,options);
            if(bitmap!=null)thumbnail.setImageBitmap(bitmap);
            row.addView(thumbnail,new LinearLayout.LayoutParams(dp(65),dp(70)));
            LinearLayout details=vertical(8);
            TextView title=text(image.title,14,WHITE,true);
            title.setMaxLines(2);details.addView(title);
            add(details,text(image.day,12,SOFT,false),3);
            row.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            CheckBox checkbox=new CheckBox(activity);
            checkbox.setButtonTintList(android.content.res.ColorStateList.valueOf(0xffAB8FFF));
            checkbox.setChecked(chosen.contains(image));
            row.addView(checkbox);
            checkbox.setOnCheckedChangeListener((control,checked)->{
                if(checked){
                    if(chosen.size()>=4){
                        control.setChecked(false);
                        alert("Four photos maximum","Create a second keepsake for additional pictures.");
                        return;
                    }
                    if(!chosen.contains(image))chosen.add(image);
                }else chosen.remove(image);
                counter.setText(chosen.size()+" selected · Up to 4 photos");
            });
            row.setOnClickListener(v->checkbox.setChecked(!checkbox.isChecked()));
        }
        if(shown<allPhotos.size()){
            // Append the continuation after this page. Existing selected images remain selected.
            Button more=button("Show more saved photos",()->{
                ViewGroup container=(ViewGroup)photoRows;
                View last=container.getChildAt(container.getChildCount()-1);
                if(last instanceof Button && ((Button)last).getText().toString().equals("Show more saved photos"))
                    container.removeView(last);
                loadMore();
            });
            add(photoRows,more,12);
        }
    }
    private void showPreview(){
        if(chosen.isEmpty()){alert("Choose a photo","Select at least one of your saved pictures.");return;}
        final ArrayList<KeepsakeCards.Photo> selected=new ArrayList<>(chosen);
        final String title=heading.getText().toString().trim();
        final String note=dedication.getText().toString().trim();
        final int chosenTheme=theme,chosenFormat=format;
        alertOrPreview(selected,title,note,chosenTheme,chosenFormat);
    }
    private void alertOrPreview(ArrayList<KeepsakeCards.Photo> photos,
                                String title,String note,int style,int layout){
        Toast.makeText(activity,"Making your real photo keepsake…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            Bitmap bmp=null;String error=null;
            try{bmp=KeepsakeCards.render(photos,title,note,style,layout,540);}
            catch(Exception e){error=e.getMessage();}
            final Bitmap result=bmp;final String message=error;
            activity.runOnUiThread(()->{
                if(message!=null){alert("Could not create preview",message);return;}
                if(preview!=null&&preview.isShowing())preview.dismiss();
                preview=new Dialog(activity,android.R.style.Theme_Material_NoActionBar);
                LinearLayout root=vertical(18);root.setBackgroundColor(0xff090F25);
                add(root,text("Your keepsake",25,WHITE,true),4);
                ImageView image=new ImageView(activity);image.setImageBitmap(result);
                image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                ScrollView scroll=new ScrollView(activity);
                scroll.addView(image);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
                add(root,button("Save to phone's gallery",()->makeFull(photos,title,note,style,layout,"phone")),7);
                add(root,button("Add this keepsake to the family timeline",()->makeFull(photos,title,note,style,layout,"timeline")),6);
                add(root,button("Share with someone",()->makeFull(photos,title,note,style,layout,"share")),6);
                add(root,button("Back to editor",()->preview.dismiss()),6);
                preview.setContentView(root);preview.show();preview.getWindow().setLayout(-1,-1);
                preview.setOnDismissListener(d->{
                    if(!result.isRecycled())result.recycle();
                });
            });
        },"KeepsakePreview").start();
    }
    private void makeFull(ArrayList<KeepsakeCards.Photo> photos,String title,String note,int style,int layout,String action){
        Toast.makeText(activity,"Preparing high-quality keepsake…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            String err=null,success=null;Uri shared=null;
            File source=null;boolean retained=false;
            try{
                Bitmap bitmap=KeepsakeCards.render(photos,title,note,style,layout,1080);
                try{source=KeepsakeCards.savePNG(activity,bitmap,"keepsake");}
                finally{bitmap.recycle();}
                if("timeline".equals(action)){
                    String memoryId=UUID.randomUUID().toString();
                    String date=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());
                    ContentValues values=new ContentValues();
                    values.put("id",memoryId);values.put("day",date);
                    values.put("title",title.isEmpty()?"Our family keepsake":title);
                    values.put("kind","Memory");values.put("body",note);
                    values.put("media",new org.json.JSONArray(Collections.singletonList(source.getName())).toString());
                    database.insertOrThrow("memories",null,values);
                    FeatureStore.claim(activity,database,memoryId);
                    retained=true;
                    success="Saved as a new memory in your family timeline. Your source photos are unchanged.";
                }else{
                    if(Build.VERSION.SDK_INT<29)
                        throw new IOException("Gallery saving requires Android 10 or newer on this phone.");
                    shared=MediaSaver.save(activity,source,
                        MediaSaver.displayName(source,title.isEmpty()?"Keepsake":title));
                    success="Your keepsake was saved to Pictures → Our Little Universe.";
                }
            }catch(Exception failure){err=failure.getMessage()==null?failure.toString():failure.getMessage();}
            finally{if(source!=null&&!retained)source.delete();}
            final String problem=err,good=success;final Uri uri=shared;
            activity.runOnUiThread(()->{
                if(problem!=null){alert("Could not create keepsake",problem);return;}
                if("share".equals(action)&&uri!=null){
                    try{
                        Intent intent=new Intent(Intent.ACTION_SEND);
                        intent.setType("image/png");intent.putExtra(Intent.EXTRA_STREAM,uri);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        activity.startActivity(Intent.createChooser(intent,"Share your keepsake"));
                    }catch(Exception e){alert("Saved to gallery",good+"\n\nSharing did not open: "+e.getMessage());}
                }else alert("A memory worth keeping",good);
                if("timeline".equals(action)){
                    if(preview!=null&&preview.isShowing())preview.dismiss();
                    if(editor!=null&&editor.isShowing())editor.dismiss();
                    if(onClose!=null)onClose.run();
                }
            });
        },"KeepsakeRender").start();
    }
}