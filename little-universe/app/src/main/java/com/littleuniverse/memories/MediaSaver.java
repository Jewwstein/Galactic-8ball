package com.littleuniverse.memories;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Copy an original keepsake to a phone's public gallery / music library without
 * compression, cloud services, storage permissions or changing the private original.
 *
 * Android 10+: use MediaStore (visible to Gallery/Photos/Music/Files).
 * Android 8-9: the caller uses ACTION_CREATE_DOCUMENT (user selects destination).
 */
public final class MediaSaver {
    private MediaSaver(){}

    public static String mime(File file){
        String name=file.getName().toLowerCase(Locale.US);
        if(name.endsWith(".m4a"))return "audio/mp4";
        if(name.endsWith(".3gp"))return "audio/3gpp";
        if(name.endsWith(".mov"))return "video/quicktime";
        if(name.endsWith(".mkv"))return "video/x-matroska";
        if(name.endsWith(".webm"))return "video/webm";
        if(name.endsWith(".heic"))return "image/heic";
        if(name.endsWith(".heif"))return "image/heif";
        int dot=name.lastIndexOf('.');
        String ext=dot>=0?name.substring(dot+1):"";
        String type=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return type==null?"application/octet-stream":type;
    }

    public static String displayName(File source,String title){
        String original=source.getName();
        int dot=original.lastIndexOf('.');
        String extension=dot>=0?original.substring(dot).toLowerCase(Locale.US):"";
        if(!extension.matches("\\.[a-z0-9]{1,8}"))extension="";
        String name=(title==null?"":title).trim().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+"," ").replaceAll("\\s+"," ");
        if(name.isEmpty())name="Memory";
        if(name.length()>55)name=name.substring(0,55).trim();
        String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date());
        return "Our Little Universe - "+name+" - "+stamp+extension;
    }

    public static String folder(File source){
        String type=mime(source);
        if(type.startsWith("image/"))return "Pictures/Our Little Universe";
        if(type.startsWith("video/"))return "Movies/Our Little Universe";
        if(type.startsWith("audio/"))return "Music/Our Little Universe";
        return "Downloads/Our Little Universe";
    }

    public static Uri save(Context context,File source,String displayName)throws IOException {
        if(Build.VERSION.SDK_INT<29)throw new IOException("Choose a folder on this Android version.");
        if(!source.isFile())throw new FileNotFoundException("The original attachment was not found.");
        String type=mime(source);
        Uri collection;
        String directory;
        if(type.startsWith("image/")){
            collection=MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            directory=Environment.DIRECTORY_PICTURES;
        }else if(type.startsWith("video/")){
            collection=MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            directory=Environment.DIRECTORY_MOVIES;
        }else if(type.startsWith("audio/")){
            collection=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            directory=Environment.DIRECTORY_MUSIC;
        }else{
            collection=MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            directory=Environment.DIRECTORY_DOWNLOADS;
        }

        ContentValues values=new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE,type);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,directory+"/Our Little Universe");
        values.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri target=context.getContentResolver().insert(collection,values);
        if(target==null)throw new IOException("The phone could not create a destination file.");
        boolean success=false;
        try{
            try(InputStream in=new BufferedInputStream(new FileInputStream(source));
                OutputStream out=context.getContentResolver().openOutputStream(target,"w")){
                if(out==null)throw new IOException("Unable to open destination.");
                byte[] buffer=new byte[65536];int count;
                while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
                out.flush();
            }
            ContentValues published=new ContentValues();
            published.put(MediaStore.MediaColumns.IS_PENDING,0);
            if(context.getContentResolver().update(target,published,null,null)!=1)
                throw new IOException("The file was saved but could not be published to the gallery.");
            success=true;
            return target;
        }finally{
            if(!success)context.getContentResolver().delete(target,null,null);
        }
    }

    public static void saveAs(Context context,File source,Uri destination)throws IOException{
        if(!source.isFile())throw new FileNotFoundException("The original attachment was not found.");
        if(destination==null)throw new IOException("No destination was selected.");
        try(InputStream in=new BufferedInputStream(new FileInputStream(source));
            OutputStream out=context.getContentResolver().openOutputStream(destination,"w")){
            if(out==null)throw new IOException("Unable to write to the selected folder.");
            byte[] buffer=new byte[65536];int count;
            while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
            out.flush();
        }
    }
}