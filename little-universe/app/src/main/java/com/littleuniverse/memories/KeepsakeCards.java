package com.littleuniverse.memories;

import android.content.Context;
import android.graphics.*;
import android.media.ExifInterface;
import android.os.Build;
import java.io.*;
import java.util.*;

/** Offline photo-first collectible keepsake card renderer.
 * No third-party API, fonts, logins, tracking, advertisements or cloud.
 * Never modifies original photos; new PNG images are independent copies.
 */
public final class KeepsakeCards {
    private KeepsakeCards(){}
    public static final class Photo {
        public final String id,day,title,path;
        public Photo(String memoryId,String date,String name,String filename){
            id=memoryId;day=date;title=name;path=filename;
        }
    }
    public static final int BLUE=0, VIOLET=1, MIDNIGHT=2;
    public static final int PORTRAIT=0,SQUARE=1,STORY=2;
    public static String themeName(int theme){return theme==VIOLET?"Purple Velvet":theme==MIDNIGHT?"Midnight Storybook":"Blue & Purple Galaxy";}
    public static String sizeName(int format){return format==SQUARE?"Square · 1080 × 1080":format==STORY?"Story · 1080 × 1920":"Keepsake · 1080 × 1350";}
    public static int width(int format){return 1080;}
    public static int height(int format){return format==SQUARE?1080:format==STORY?1920:1350;}
    private static int base(int t){return t==VIOLET?0xff1A1038:t==MIDNIGHT?0xff090E20:0xff0A1334;}
    private static int accent(int t){return t==VIOLET?0xffC99DFF:t==MIDNIGHT?0xff99B4ED:0xff86B8FF;}
    private static final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private static void fill(Canvas c,Paint p,RectF rect,int start,int end){
        p.setShader(new LinearGradient(rect.left,rect.top,rect.right,rect.bottom,start,end,Shader.TileMode.CLAMP));
        c.drawRect(rect,p);p.setShader(null);
    }
    private static Bitmap decoded(File file,int limit)throws IOException{
        if(!file.isFile())throw new FileNotFoundException("The source photo cannot be found.");
        BitmapFactory.Options size=new BitmapFactory.Options();size.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getAbsolutePath(),size);
        if(size.outWidth<=0||size.outHeight<=0)throw new IOException("This image format cannot be opened on your phone.");
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inSampleSize=1;
        while(size.outWidth/opts.inSampleSize>limit*2||size.outHeight/opts.inSampleSize>limit*2)
            opts.inSampleSize*=2;
        opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap image=BitmapFactory.decodeFile(file.getAbsolutePath(),opts);
        if(image==null)throw new IOException("Unable to open one of the selected photos.");
        int rotation=0;
        if(Build.VERSION.SDK_INT>=24){
            try{
                int exif=new ExifInterface(file.getAbsolutePath()).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
                if(exif==ExifInterface.ORIENTATION_ROTATE_90)rotation=90;
                else if(exif==ExifInterface.ORIENTATION_ROTATE_180)rotation=180;
                else if(exif==ExifInterface.ORIENTATION_ROTATE_270)rotation=270;
            }catch(Exception ignored){}
        }
        if(rotation!=0){
            Matrix matrix=new Matrix();matrix.postRotate(rotation);
            Bitmap oriented=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),matrix,true);
            if(oriented!=image)image.recycle();
            image=oriented;
        }
        return image;
    }
    private static void cropped(Canvas c,Bitmap image,RectF dst,float radius,Paint p){
        c.save();Path rounded=new Path();rounded.addRoundRect(dst,radius,radius,Path.Direction.CW);c.clipPath(rounded);
        float sourceRatio=image.getWidth()/(float)image.getHeight();
        float destRatio=dst.width()/dst.height();
        Rect source;
        if(sourceRatio>destRatio){
            int visible=Math.max(1,(int)(image.getHeight()*destRatio));
            int left=Math.max(0,(image.getWidth()-visible)/2);
            source=new Rect(left,0,Math.min(image.getWidth(),left+visible),image.getHeight());
        }else{
            int visible=Math.max(1,(int)(image.getWidth()/destRatio));
            int top=Math.max(0,(image.getHeight()-visible)/2);
            source=new Rect(0,top,image.getWidth(),Math.min(image.getHeight(),top+visible));
        }
        p.setShader(null);p.setColor(Color.WHITE);
        c.drawBitmap(image,source,dst,p);
        c.restore();
    }
    private static void label(Canvas c,Paint p,String t,int color,float size,boolean bold,float x,float y){
        p.setColor(color);p.setShader(null);
        p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        p.setTextSize(size);p.setStyle(Paint.Style.FILL);
        c.drawText(t,x,y,p);
    }
    private static String trim(String text,int max){
        if(text==null)return "";
        String s=text.trim().replaceAll("[\\r\\n\\t]+"," ").replaceAll(" +"," ");
        return s.length()>max?s.substring(0,max-1).trim()+"…":s;
    }
    private static float lines(Canvas c,Paint p,String text,float x,float y,float width,
                               float fontsize,int color,int maxLines){
        p.setTextSize(fontsize);p.setTypeface(Typeface.create("serif",Typeface.BOLD));p.setColor(color);
        String[] words=trim(text,180).split(" ");
        StringBuilder row=new StringBuilder();int n=0;
        for(String word:words){
            String next=row.length()==0?word:row+" "+word;
            if(p.measureText(next)>width&&row.length()>0){
                c.drawText(row.toString(),x,y,p);
                n++;y+=fontsize*1.19f;row.setLength(0);
                if(n>=maxLines)return y;
            }
            if(row.length()>0)row.append(' ');row.append(word);
        }
        if(row.length()>0){c.drawText(row.toString(),x,y,p);y+=fontsize*1.19f;}
        return y;
    }
    public static Bitmap render(List<Photo> selected,String heading,String dedication,
                                int theme,int format,int w)throws IOException{
        if(selected==null||selected.isEmpty()||selected.size()>4)
            throw new IOException("Choose between one and four pictures.");
        if(w<320||w>1080)throw new IOException("Invalid card resolution.");
        int h=Math.round(w*(height(format)/(float)width(format)));
        Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(result);
        c.scale(w/1080f,w/1080f);
        float H=height(format);
        Paint p=new Paint(3);p.setDither(true);p.setFilterBitmap(true);
        fill(c,p,new RectF(0,0,1080,H),base(theme),
            theme==VIOLET?0xff241044:theme==MIDNIGHT?0xff161E39:0xff191244);

        p.setShader(new RadialGradient(920,120,960,
            new int[]{theme==MIDNIGHT?0x445B87B9:0x815C39C9,0x18558FE9,0x00000000},
            null,Shader.TileMode.CLAMP));
        c.drawRect(0,0,1080,H,p);p.setShader(null);
        p.setShader(new RadialGradient(160,H-60,850,
            new int[]{0x654478D6,0x114E389E,0x00000000},null,Shader.TileMode.CLAMP));
        c.drawRect(0,0,1080,H,p);p.setShader(null);

        // Small stars are discrete, not geometric wireframe decoration.
        Random stars=new Random(20261031L);
        for(int i=0;i<48;i++){
            float x=30+stars.nextFloat()*1020f;
            float y=22+stars.nextFloat()*Math.min(H*.45f,550);
            p.setColor(Color.argb(45+stars.nextInt(90),220,230,255));
            c.drawCircle(x,y,.75f+stars.nextFloat()*1.35f,p);
        }
        label(c,p,"OUR LITTLE UNIVERSE",0xffECE9FF,29,true,74,92);
        label(c,p,"A MEMORY MADE WITH LOVE",accent(theme),18,true,75,127);
        p.setColor(0x666C7CEC);c.drawRoundRect(new RectF(74,154,1006,157),2,2,p);

        float textY=204;
        textY=lines(c,p,heading==null||heading.trim().isEmpty()?"A little moment, a lifetime of love":heading,
             74,textY,925,58,Color.WHITE,3);
        String date=selected.get(0).day;
        label(c,p,trim(date,40)+"   ·   "+selected.size()+(selected.size()==1?" PHOTO":" PHOTOS"),
            accent(theme),21,true,75,textY+19);
        float galleryTop=Math.max(310,textY+63);
        float margin=55,gap=15;
        float reserved=(format==SQUARE?126:format==STORY?250:185);
        float availableHeight=H-galleryTop-reserved;
        if(availableHeight<245)availableHeight=245;
        ArrayList<RectF> boxes=new ArrayList<>();
        int n=selected.size();
        if(n==1)boxes.add(new RectF(margin,galleryTop,1080-margin,galleryTop+availableHeight));
        else if(n==2){
            float each=(1080-margin*2-gap)/2;
            boxes.add(new RectF(margin,galleryTop,margin+each,galleryTop+availableHeight));
            boxes.add(new RectF(margin+each+gap,galleryTop,1080-margin,galleryTop+availableHeight));
        }else{
            float each=(1080-margin*2-gap)/2;
            float half=(availableHeight-gap)/2;
            boxes.add(new RectF(margin,galleryTop,margin+each,galleryTop+half));
            boxes.add(new RectF(margin+each+gap,galleryTop,1080-margin,galleryTop+half));
            boxes.add(new RectF(margin,galleryTop+half+gap,margin+each,galleryTop+availableHeight));
            if(n==4)boxes.add(new RectF(margin+each+gap,galleryTop+half+gap,1080-margin,galleryTop+availableHeight));
            else boxes.set(2,new RectF(margin,galleryTop+half+gap,1080-margin,galleryTop+availableHeight));
        }
        try{
            for(int i=0;i<boxes.size();i++){
                Bitmap photo=decoded(new File(selected.get(i).path),1450);
                try{
                    RectF box=boxes.get(i);
                    p.setColor(0x99101840);
                    c.drawRoundRect(new RectF(box.left+4,box.top+12,box.right+4,box.bottom+14),22,22,p);
                    cropped(c,photo,box,22,p);
                    p.setShader(null);p.setColor(0x709FBAFF);p.setStrokeWidth(2);p.setStyle(Paint.Style.STROKE);
                    c.drawRoundRect(box,22,22,p);p.setStyle(Paint.Style.FILL);
                }finally{photo.recycle();}
            }
        }catch(Exception err){
            result.recycle();
            throw err instanceof IOException?(IOException)err:new IOException(err.getMessage(),err);
        }
        float foot=H-reserved+28;
        label(c,p,"♡",accent(theme),33,true,75,foot+1);
        String subtitle=trim(dedication==null?"":dedication,130);
        if(subtitle.isEmpty())subtitle="Our story is only just beginning.";
        lines(c,p,subtitle,132,foot,860,27,0xffE7DEFF,2);
        p.setColor(0x6688A0F5);
        c.drawRect(74,H-61,1006,H-59,p);
        label(c,p,"A LITTLE UNIVERSE. A LIFETIME OF MEMORIES.",0xffC2C7EC,17,false,76,H-30);
        return result;
    }
    public static File savePNG(Context ctx,Bitmap picture,String prefix)throws IOException{
        File directory=new File(ctx.getFilesDir(),"media");
        if(!directory.exists()&&!directory.mkdirs())throw new IOException("Media folder unavailable.");
        File file=new File(directory,UUID.randomUUID()+".png");
        boolean okay=false;
        try(OutputStream out=new BufferedOutputStream(new FileOutputStream(file))){
            okay=picture.compress(Bitmap.CompressFormat.PNG,100,out);
            out.flush();
        }finally{if(!okay)file.delete();}
        if(!okay)throw new IOException("Could not encode the keepsake card.");
        return file;
    }
}