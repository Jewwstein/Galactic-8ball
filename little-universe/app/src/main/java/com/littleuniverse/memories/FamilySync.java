package com.littleuniverse.memories;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.StatFs;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Zero-cloud family archive exchange. All work happens on the device.
 * Old and new Little Universe exports use the same manifest format and stable memory UUIDs.
 * Existing title/body/date stay local. Newly added media on shared memories are unioned.
 * Reimporting a ZIP is idempotent, and no incoming record replaces a local one.
 */
public final class FamilySync {
    private FamilySync() {}
    private static final int MAX_ENTRIES = 25000;
    private static final int MAX_MEMORIES = 60000;
    private static final long MAX_MANIFEST_BYTES = 32L*1024L*1024L;
    private static final long FREE_SPACE_RESERVE = 32L*1024L*1024L;
    private static final String PROFILE="little_universe_profile";
    private static final String FORMAT="little-universe-android-v1";
    private static final String SAFE_NAME="[a-zA-Z0-9_-][a-zA-Z0-9_.-]{0,149}";

    public static final class Result {
        public final int added;
        public final int alreadyPresent;
        public final int attached;
        public final int filesCopied;
        public final int localEditsPreserved;
        Result(int newCount,int seen,int attachedCount,int copies,int conflictCount){
            added=newCount;alreadyPresent=seen;attached=attachedCount;
            filesCopied=copies;localEditsPreserved=conflictCount;
        }
        public String summary(){
            String result=added+" new memories added\n"+
                alreadyPresent+" already on this phone (no duplicates)\n"+
                attached+" new photos, videos or audio attachments\n"+
                filesCopied+" original media files copied";
            if(localEditsPreserved>0)
                result+="\n\n"+localEditsPreserved+
                    " shared memories were edited differently. Your local wording was preserved.";
            return result;
        }
    }
    private static final class Entry {
        String id,day,title,kind,body;
        ArrayList<String> media;
        Entry(JSONObject j) throws Exception{
            id=j.getString("id");day=j.getString("day");title=j.getString("title");
            kind=j.getString("kind");body=j.optString("body","");
            if(!id.matches("[a-zA-Z0-9_-]{1,120}") ||
                !day.matches("\\d{4}-\\d{2}-\\d{2}") ||
                title.length()>5000 || kind.length()>100 || body.length()>3_000_000)
                throw new IOException("Invalid memory metadata in ZIP.");
            JSONArray a=j.optJSONArray("media");
            media=new ArrayList<>();
            Set<String> unique=new HashSet<>();
            if(a!=null)for(int i=0;i<a.length();i++){
                String name=a.getString(i);
                if(!name.matches(SAFE_NAME)||name.contains("..")||
                    name.equals(".")||name.equals(".."))
                    throw new IOException("Unsafe attachment filename in ZIP.");
                if(unique.add(name))media.add(name);
            }
        }
    }
    private static final class Local {
        final String day,title,kind,body;
        final ArrayList<String> media;
        Local(Cursor c) throws Exception{
            day=c.getString(0);title=c.getString(1);kind=c.getString(2);body=c.getString(3);
            media=fromJson(c.getString(4));
        }
    }
    private static ArrayList<String> fromJson(String data) throws Exception{
        ArrayList<String> out=new ArrayList<>();
        JSONArray a=new JSONArray(data);
        for(int i=0;i<a.length();i++)out.add(a.getString(i));
        return out;
    }
    private static void addRecord(SQLiteDatabase db, Entry e){
        ContentValues values=new ContentValues();
        values.put("id",e.id);values.put("day",e.day);values.put("title",e.title);
        values.put("kind",e.kind);values.put("body",e.body);
        values.put("media",new JSONArray(e.media).toString());
        // Ignore a locally inserted ID rather than replacing it.
        db.insertWithOnConflict("memories",null,values,SQLiteDatabase.CONFLICT_IGNORE);
    }
    private static Local local(SQLiteDatabase db,String id) throws Exception{
        try(Cursor c=db.rawQuery("SELECT day,title,kind,body,media FROM memories WHERE id=?",
            new String[]{id})){
            return c.moveToFirst()?new Local(c):null;
        }
    }
    private static JSONObject readHeader(ZipInputStream zip) throws Exception{
        ZipEntry entry=zip.getNextEntry();
        if(entry==null||!"archive.json".equals(entry.getName()))
            throw new IOException("Not a Little Universe ZIP (archive.json must be first).");
        ByteArrayOutputStream meta=new ByteArrayOutputStream();
        byte[] buffer=new byte[65536];
        int n;long read=0;
        while((n=zip.read(buffer))!=-1){
            read+=n;if(read>MAX_MANIFEST_BYTES)
                throw new IOException("The archive's journal is too large.");
            meta.write(buffer,0,n);
        }
        zip.closeEntry();
        JSONObject json=new JSONObject(meta.toString("UTF-8"));
        if(!FORMAT.equals(json.optString("format")))
            throw new IOException("Unsupported Little Universe archive format.");
        return json;
    }
    private static String hash(File file) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))){
            byte[] buf=new byte[65536];int n;
            while((n=in.read(buf))!=-1)md.update(buf,0,n);
        }
        byte[] digest=md.digest();StringBuilder b=new StringBuilder();
        for(byte x:digest)b.append(String.format(Locale.US,"%02x",x & 0xff));
        return b.toString();
    }
    private static void copy(File source,File destination) throws IOException{
        try(InputStream in=new BufferedInputStream(new FileInputStream(source));
            OutputStream out=new BufferedOutputStream(new FileOutputStream(destination))){
            byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
        }
    }
    private static long freeSpace(File dir){
        return new StatFs(dir.getAbsolutePath()).getAvailableBytes();
    }
    private static void clean(File tmp){
        File[] a=tmp.listFiles();if(a!=null)for(File f:a)f.delete();
        tmp.delete();
    }

    public static Result merge(Context context,SQLiteDatabase db,Uri uri) throws Exception{
        File mediaFolder=new File(context.getFilesDir(),"media");
        if(!mediaFolder.exists()&&!mediaFolder.mkdirs())
            throw new IOException("Cannot access local media folder.");
        File temp=new File(context.getCacheDir(),"family_sync_"+UUID.randomUUID());
        if(!temp.mkdirs())throw new IOException("Cannot create temporary import folder.");
        ArrayList<File> newlyCopied=new ArrayList<>();
        boolean committed=false;
        try(InputStream raw=context.getContentResolver().openInputStream(uri)){
            if(raw==null)throw new IOException("Cannot open the selected family ZIP.");
            try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(raw))){
                JSONObject head=readHeader(zip);
                JSONArray records=head.getJSONArray("memories");
                if(records.length()>MAX_MEMORIES)
                    throw new IOException("Archive contains too many memories.");
                ArrayList<Entry> incoming=new ArrayList<>();
                Set<String> ids=new HashSet<>();
                Set<String> desiredFiles=new LinkedHashSet<>();
                int duplicates=0,changed=0,willAdd=0;
                for(int i=0;i<records.length();i++){
                    Entry e=new Entry(records.getJSONObject(i));
                    if(!ids.add(e.id))throw new IOException("Repeated memory ID in ZIP.");
                    incoming.add(e);
                    Local mine=local(db,e.id);
                    if(mine==null){
                        willAdd++;
                        desiredFiles.addAll(e.media);
                    }else{
                        duplicates++;
                        if(!mine.day.equals(e.day)||!mine.title.equals(e.title)||
                            !mine.kind.equals(e.kind)||!mine.body.equals(e.body))changed++;
                        for(String name:e.media){
                            if(!mine.media.contains(name)||
                                !new File(mediaFolder,name).isFile())desiredFiles.add(name);
                        }
                    }
                }
                // All media names must be verified before changing the database.
                HashSet<String> readFiles=new HashSet<>();
                int count=0;
                long tempBytes=0;
                ZipEntry entry;
                byte[] buffer=new byte[65536];
                while((entry=zip.getNextEntry())!=null){
                    if(++count>MAX_ENTRIES)throw new IOException("Too many files in ZIP.");
                    String zipName=entry.getName();
                    if("media/".equals(zipName)&&entry.isDirectory()){
                        zip.closeEntry();continue;
                    }
                    if(!zipName.startsWith("media/"))
                        throw new IOException("Unexpected file inside the family ZIP.");
                    String name=zipName.substring("media/".length());
                    if(!name.matches(SAFE_NAME)||name.contains(".."))
                        throw new IOException("Unsafe media path in ZIP.");
                    if(!readFiles.add(name))
                        throw new IOException("Duplicate media filename in ZIP.");
                    if(desiredFiles.contains(name)){
                        File extracted=new File(temp,name);
                        try(OutputStream out=new BufferedOutputStream(new FileOutputStream(extracted))){
                            int n;
                            while((n=zip.read(buffer))!=-1){
                                tempBytes+=n;
                                if(freeSpace(context.getCacheDir())<FREE_SPACE_RESERVE)
                                    throw new IOException("Not enough temporary storage for archive.");
                                out.write(buffer,0,n);
                            }
                        }
                    }
                    zip.closeEntry();
                }
                for(String name:desiredFiles){
                    File extracted=new File(temp,name);
                    File existing=new File(mediaFolder,name);
                    if(!extracted.isFile()&&!existing.isFile())
                        throw new IOException("Missing original photo, video or voice file: "+name);
                    if(extracted.isFile()&&existing.isFile()&&
                        !hash(extracted).equals(hash(existing)))
                        throw new IOException("Media filename collision. Import cancelled to protect originals.");
                }
                int copied=0;
                for(String name:desiredFiles){
                    File extracted=new File(temp,name);
                    File destination=new File(mediaFolder,name);
                    if(!destination.exists()){
                        if(!extracted.isFile())throw new IOException("Missing media in archive.");
                        newlyCopied.add(destination);
                        copy(extracted,destination);
                        copied++;
                    }
                }
                int added=0,attachments=0,seen=0,conflicts=0;
                db.beginTransaction();
                try{
                    for(Entry e:incoming){
                        Local mine=local(db,e.id);
                        if(mine==null){
                            addRecord(db,e);added++;
                        }else{
                            seen++;
                            if(!mine.day.equals(e.day)||!mine.title.equals(e.title)||
                                !mine.kind.equals(e.kind)||!mine.body.equals(e.body))conflicts++;
                            LinkedHashSet<String> unified=new LinkedHashSet<>(mine.media);
                            int before=unified.size();unified.addAll(e.media);
                            int difference=unified.size()-before;
                            if(difference>0){
                                ContentValues values=new ContentValues();
                                values.put("media",new JSONArray(unified).toString());
                                db.update("memories",values,"id=?",new String[]{e.id});
                                attachments+=difference;
                            }
                        }
                    }
                    db.setTransactionSuccessful();
                }finally{db.endTransaction();}
                committed=true;
                // Import profile metadata only when this phone hasn't set its own.
                JSONObject settings=head.optJSONObject("settings");
                if(settings!=null){
                    SharedPreferences prefs=context.getSharedPreferences(PROFILE,Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor=prefs.edit();
                    for(String key:new String[]{"childName","parents","dueDate"}){
                        if(prefs.getString(key,"").trim().isEmpty()&&
                            !settings.optString(key,"").trim().isEmpty())
                            editor.putString(key,settings.optString(key,""));
                    }
                    editor.apply();
                }
                return new Result(added,seen,attachments,copied,conflicts);
            }
        }catch(Exception failure){
            if(!committed)for(File f:newlyCopied)f.delete();
            throw failure;
        }finally{clean(temp);}
    }
}