package com.littleuniverse.memories;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import java.io.*;
import java.util.*;

/** Offline printable baby-book PDF, original text and a selected image per memory. */
public final class KeepsakeBook {
    private KeepsakeBook(){}
    private static final int WIDTH=595,HEIGHT=842;
    private static final int NAVY=0xff12182F,VIOLET=0xff7253C9,BLUE=0xff4B80DA;
    public static int make(Context context,SQLiteDatabase db,Uri destination)throws Exception{
        PdfDocument document=new PdfDocument();
        int pageNumber=0,count=0;
        try{
            List<String[]> entries=new ArrayList<>();
            try(Cursor c=db.rawQuery("SELECT id,day,title,kind,body,media FROM memories ORDER BY day ASC,rowid ASC",null)){
                while(c.moveToNext())entries.add(new String[]{c.getString(0),c.getString(1),c.getString(2),
                  c.getString(3),c.getString(4),c.getString(5)});
            }
            int index=0;
            while(index<entries.size()||pageNumber==0){
                PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(WIDTH,HEIGHT,++pageNumber).create();
                PdfDocument.Page page=document.startPage(info);
                Canvas canvas=page.getCanvas();
                Paint paint=new Paint(3);
                paint.setColor(0xffF7F6FE);canvas.drawColor(0xffF7F6FE);
                paint.setColor(NAVY);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
                paint.setTextSize(19);
                canvas.drawText(pageNumber==1?"LITTLE UNIVERSE · OUR FAMILY STORY":"LITTLE UNIVERSE · MEMORIES",37,48,paint);
                paint.setColor(VIOLET);paint.setStrokeWidth(2);canvas.drawLine(37,61,WIDTH-37,61,paint);
                float y=90;
                if(entries.isEmpty()){
                    paint.setTextSize(16);paint.setColor(NAVY);
                    canvas.drawText("Every precious story has a beginning.",37,121,paint);
                }
                while(index<entries.size() && y<HEIGHT-195){
                    String[] e=entries.get(index++);
                    count++;
                    paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
                    paint.setColor(BLUE);paint.setTextSize(11);
                    canvas.drawText(clean(e[1])+"  ·  "+clean(e[3]),37,y,paint);y+=22;
                    paint.setTextSize(18);paint.setColor(NAVY);
                    String title=clean(e[2]);
                    y=wrap(canvas,paint,title,37,y,WIDTH-76,21,2);
                    String author=FeatureStore.detail(db,e[0]).optString("author","Family");
                    paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
                    paint.setColor(VIOLET);paint.setTextSize(10);
                    canvas.drawText("Preserved by "+clean(author),37,y+7,paint);y+=23;
                    paint.setColor(0xff252947);paint.setTextSize(11);
                    String body=clean(e[4]);if(body.length()>1100)body=body.substring(0,1100)+" (continued in the app)";
                    y=wrap(canvas,paint,body,37,y,WIDTH-76,15,12);
                    if(y>HEIGHT-200){y+=14;continue;}
                    try{
                        org.json.JSONArray media=new org.json.JSONArray(e[5]);
                        for(int i=0;i<media.length();i++){
                            String name=media.getString(i).toLowerCase(Locale.US);
                            if(!(name.endsWith(".jpg")||name.endsWith(".png")||name.endsWith(".webp")||name.endsWith(".jpeg")))continue;
                            File f=new File(new File(context.getFilesDir(),"media"),media.getString(i));
                            if(!f.isFile())continue;
                            BitmapFactory.Options opt=new BitmapFactory.Options();opt.inSampleSize=4;
                            Bitmap image=BitmapFactory.decodeFile(f.getAbsolutePath(),opt);
                            if(image!=null){
                                float maxW=240,maxH=125;float scale=Math.min(maxW/image.getWidth(),maxH/image.getHeight());
                                float h=image.getHeight()*scale,w=image.getWidth()*scale;
                                if(y+h<HEIGHT-75){canvas.drawBitmap(image,null,
                                    new RectF(37,y+3,37+w,y+h+3),new Paint(3));y+=h+12;}
                                image.recycle();
                            }
                            break;
                        }
                    }catch(Exception ignored){}
                    y+=24;
                }
                paint.setColor(0xff7B7FA3);paint.setTextSize(9);
                canvas.drawText("Made with love · Little Universe",37,HEIGHT-31,paint);
                canvas.drawText("Page "+pageNumber,WIDTH-95,HEIGHT-31,paint);
                document.finishPage(page);
                if(index>=entries.size())break;
            }
            try(OutputStream output=context.getContentResolver().openOutputStream(destination)){
                if(output==null)throw new IOException("Could not create the selected PDF.");
                document.writeTo(output);
            }
            return count;
        }finally{document.close();}
    }
    private static String clean(String s){return s==null?"":s.replaceAll("[\\r\\n\\t]+"," ").trim();}
    private static float wrap(Canvas c,Paint p,String s,float x,float y,float maxWidth,float step,int maxLines){
        if(s.isEmpty())return y;
        String[] words=s.split(" +");StringBuilder line=new StringBuilder();int lines=0;
        for(String word:words){
            String test=line.length()==0?word:line+" "+word;
            if(p.measureText(test)>maxWidth&&line.length()>0){
                c.drawText(line.toString(),x,y,p);y+=step;line.setLength(0);lines++;
                if(lines>=maxLines)return y;
            }
            if(line.length()>0)line.append(' ');line.append(word);
        }
        if(line.length()>0){c.drawText(line.toString(),x,y,p);y+=step;}
        return y;
    }
}