package com.galactic.eightball;
import android.app.*;import android.os.*;import android.graphics.*;import android.view.*;import android.content.*;import java.io.*;import java.util.*;
public class MainActivity extends Activity{
 public void onCreate(Bundle b){super.onCreate(b);getWindow().setFlags(1024,1024);setContentView(new V(this));}
 class V extends View{
  Paint p=new Paint(3); Bitmap tex; String msg="Loading extracted original table texture…",file="";
  V(Context c){super(c);setBackgroundColor(Color.rgb(2,4,14));load();}
  void load(){try{String[] a=getAssets().list("extracted");if(a==null||a.length==0){msg="Bundle extracted, but no Texture2D image was found";return;}Arrays.sort(a);for(String n:a)if(n.toLowerCase().endsWith(".png")){try(InputStream in=getAssets().open("extracted/"+n)){Bitmap b=BitmapFactory.decodeStream(in);if(b!=null&&(tex==null||b.getWidth()*b.getHeight()>tex.getWidth()*tex.getHeight())){tex=b;file=n;}}}if(tex!=null)msg="ORIGINAL UNITY TABLE TEXTURE";else msg="No renderable Texture2D found in table bundle";}catch(Exception e){msg=e.getClass().getSimpleName()+": "+e.getMessage();}}
  protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();if(tex!=null){float pad=h*.08f;RectF dst=new RectF(pad,pad,w-pad,h-pad);p.setColor(Color.WHITE);c.drawBitmap(tex,null,dst,p);p.setColor(Color.argb(155,0,0,0));c.drawRect(0,0,w,h*.10f,p);}p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(h*.045f);p.setColor(Color.WHITE);c.drawText(msg,w/2,h*.065f,p);if(tex==null){p.setTextSize(h*.03f);p.setColor(Color.LTGRAY);c.drawText("This APK is reading the recovered Unity AssetBundle directly.",w/2,h*.55f,p);}else{p.setTextSize(h*.022f);p.setTypeface(Typeface.MONOSPACE);c.drawText(tex.getWidth()+"×"+tex.getHeight()+"  "+file,w/2,h*.97f,p);}}
 }
}