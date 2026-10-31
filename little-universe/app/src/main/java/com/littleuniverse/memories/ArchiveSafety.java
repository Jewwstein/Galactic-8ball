package com.littleuniverse.memories;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Offline safety net for memories. Recycle Bin records are preserved in full backups;
 * family-sharing exports never include private trashed content.
 */
public final class ArchiveSafety {
    private ArchiveSafety(){}
    public static final String FORMAT="little-universe-android-v1";
    private static final int MANIFEST_LIMIT=32*1024*1024;
    private static final int FILE_LIMIT=50000;
    private static final String SAFE_FILE="[a-zA-Z0-9_-][a-zA-Z0-9_.-]{0,149}";
    public static final class Deleted {
        public String id, title, day, kind;
        public long removed;
        public int attachments;
    }
    public static void init(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS recycle_bin (id TEXT PRIMARY KEY, payload TEXT NOT NULL, metadata TEXT NOT NULL, deleted_at INTEGER NOT NULL)");
    }
    private static JSONObject extract(Cursor c)throws JSONException{
        JSONObject m=new JSONObject();
        m.put("id",c.getString(0));m.put("day",c.getString(1));m.put("title",c.getString(2));
        m.put("kind",c.getString(3));m.put("body",c.getString(4));
        m.put("media",new JSONArray(c.getString(5)));
        return m;
    }
    public static boolean trashed(SQLiteDatabase db,String id){
        try(Cursor c=db.rawQuery("SELECT 1 FROM recycle_bin WHERE id=? LIMIT 1",new String[]{id})){
            return c.moveToFirst();
        }
    }
    public static boolean moveToTrash(SQLiteDatabase db,String id)throws JSONException{
        init(db);
        db.beginTransaction();
        try{
            JSONObject record=null;
            try(Cursor c=db.rawQuery("SELECT id,day,title,kind,body,media FROM memories WHERE id=?",
                new String[]{id})){
                if(c.moveToFirst())record=extract(c);
            }
            if(record==null)return false;
            ContentValues v=new ContentValues();
            v.put("id",id);v.put("payload",record.toString());
            v.put("metadata",FeatureStore.detail(db,id).toString());
            v.put("deleted_at",System.currentTimeMillis());
            db.insertWithOnConflict("recycle_bin",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            db.delete("memories","id=?",new String[]{id});
            db.setTransactionSuccessful();return true;
        }finally{db.endTransaction();}
    }
    public static List<Deleted> deleted(SQLiteDatabase db){
        init(db);
        List<Deleted> result=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT id,payload,deleted_at FROM recycle_bin ORDER BY deleted_at DESC",null)){
            while(c.moveToNext()){
                try{
                    JSONObject j=new JSONObject(c.getString(1));
                    Deleted d=new Deleted();d.id=c.getString(0);d.title=j.optString("title","Memory");
                    d.day=j.optString("day","");d.kind=j.optString("kind","Memory");
                    d.removed=c.getLong(2);
                    JSONArray media=j.optJSONArray("media");d.attachments=media==null?0:media.length();
                    result.add(d);
                }catch(Exception ignored){}
            }
        }return result;
    }
    public static boolean restore(SQLiteDatabase db,String id)throws Exception{
        init(db);
        db.beginTransaction();
        try{
            JSONObject j,meta;
            try(Cursor c=db.rawQuery("SELECT payload,metadata FROM recycle_bin WHERE id=?",new String[]{id})){
                if(!c.moveToFirst())return false;
                j=new JSONObject(c.getString(0));meta=new JSONObject(c.getString(1));
            }
            try(Cursor c=db.rawQuery("SELECT 1 FROM memories WHERE id=?",new String[]{id})){
                if(c.moveToFirst())return false;
            }
            ContentValues v=new ContentValues();
            for(String key:new String[]{"id","day","title","kind","body"})
                v.put(key,j.optString(key,""));
            JSONArray media=j.optJSONArray("media");
            v.put("media",media==null?"[]":media.toString());
            if(db.insertWithOnConflict("memories",null,v,SQLiteDatabase.CONFLICT_IGNORE)<0)
                return false;
            FeatureStore.putDetail(db,id,meta);
            db.delete("recycle_bin","id=?",new String[]{id});
            db.setTransactionSuccessful();return true;
        }finally{db.endTransaction();}
    }
    public static boolean deleteForever(Context context,SQLiteDatabase db,String id)throws JSONException{
        init(db);
        JSONObject j;
        try(Cursor c=db.rawQuery("SELECT payload FROM recycle_bin WHERE id=?",new String[]{id})){
            if(!c.moveToFirst())return false;
            j=new JSONObject(c.getString(0));
        }
        db.delete("recycle_bin","id=?",new String[]{id});
        db.delete("memory_details","id=?",new String[]{id});
        // Attachments are only removed when no remaining active or deleted memory refers to them.
        JSONArray media=j.optJSONArray("media");
        if(media!=null)for(int i=0;i<media.length();i++){
            String name=media.optString(i,"");
            if(!name.matches(SAFE_FILE)||name.contains("..")||stillReferenced(db,name))continue;
            File disk=new File(new File(context.getFilesDir(),"media"),name);
            if(disk.isFile())disk.delete();
        }
        return true;
    }
    private static boolean stillReferenced(SQLiteDatabase db,String name){
        try(Cursor c=db.rawQuery("SELECT media FROM memories",null)){
            while(c.moveToNext())if(contains(new JSONArray(c.getString(0)),name))return true;
        }catch(Exception ignored){return true;}
        try(Cursor c=db.rawQuery("SELECT payload FROM recycle_bin",null)){
            while(c.moveToNext()){
                JSONArray a=new JSONObject(c.getString(0)).optJSONArray("media");
                if(a!=null&&contains(a,name))return true;
            }
        }catch(Exception ignored){return true;}
        return false;
    }
    private static boolean contains(JSONArray a,String name){
        for(int i=0;i<a.length();i++)if(name.equals(a.optString(i)))return true;
        return false;
    }
    public static JSONArray exportTrash(SQLiteDatabase db)throws JSONException{
        init(db);
        JSONArray result=new JSONArray();
        try(Cursor c=db.rawQuery("SELECT id,payload,metadata,deleted_at FROM recycle_bin ORDER BY deleted_at DESC",null)){
            while(c.moveToNext()){
                JSONObject j=new JSONObject();
                j.put("memory",new JSONObject(c.getString(1)));
                j.put("details",new JSONObject(c.getString(2)));
                j.put("removedAt",c.getLong(3));
                result.put(j);
            }
        }
        return result;
    }
    public static void mergeTrash(SQLiteDatabase db,JSONArray incoming)throws JSONException{
        if(incoming==null)return;
        init(db);
        if(incoming.length()>100000)throw new JSONException("Too many deleted memories in backup.");
        for(int i=0;i<incoming.length();i++){
            JSONObject item=incoming.optJSONObject(i);
            if(item==null)continue;
            JSONObject memory=item.optJSONObject("memory");if(memory==null)continue;
            String id=memory.optString("id","");
            if(!id.matches("[a-zA-Z0-9_-]{1,120}")||trashed(db,id))continue;
            try(Cursor c=db.rawQuery("SELECT 1 FROM memories WHERE id=?",new String[]{id})){
                if(c.moveToFirst())continue;
            }
            JSONObject meta=item.optJSONObject("details");if(meta==null)meta=new JSONObject();
            ContentValues v=new ContentValues();
            v.put("id",id);v.put("payload",memory.toString());
            v.put("metadata",meta.toString());
            v.put("deleted_at",item.optLong("removedAt",System.currentTimeMillis()));
            db.insertWithOnConflict("recycle_bin",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        }
    }
    public static final class Health {
        public int memories, recycled, attachments, healthy, missing, unreadable;
        public long bytes;
        public final List<String> problems=new ArrayList<>();
        public String summary(){
            StringBuilder s=new StringBuilder();
            s.append(memories).append(" active memories\n")
             .append(recycled).append(" recently deleted memories\n")
             .append(attachments).append(" referenced attachments\n")
             .append(healthy).append(" original media files present\n")
             .append(missing).append(" missing files\n")
             .append(unreadable).append(" unreadable files\n")
             .append(String.format(Locale.US,"%.1f MB of available originals checked",bytes/1048576d));
            if(problems.isEmpty())
                s.append("\n\nAll referenced originals are present and readable.");
            else{
                s.append("\n\nProblems:\n");
                for(String problem:problems)s.append("• ").append(problem).append('\n');
                if(missing>problems.size())s.append("…and additional missing files.");
            }
            s.append("\n\nThis is a local file check. For protection against phone loss, export a full ZIP backup and verify it separately.");
            return s.toString();
        }
    }
    private static void collect(JSONObject m,Set<String> files){
        JSONArray a=m.optJSONArray("media");
        if(a!=null)for(int i=0;i<a.length();i++){
            String filename=a.optString(i);
            if(filename.matches(SAFE_FILE)&&!filename.contains(".."))files.add(filename);
        }
    }
    public static Set<String> requiredFiles(JSONObject archive){
        LinkedHashSet<String> fileNames=new LinkedHashSet<>();
        JSONArray memories=archive.optJSONArray("memories");
        if(memories!=null)for(int i=0;i<memories.length();i++){
            JSONObject j=memories.optJSONObject(i);if(j!=null)collect(j,fileNames);
        }
        JSONArray trash=archive.optJSONArray("recycleBin");
        if(trash!=null)for(int i=0;i<trash.length();i++){
            JSONObject j=trash.optJSONObject(i);
            JSONObject m=j==null?null:j.optJSONObject("memory");
            if(m!=null)collect(m,fileNames);
        }
        return fileNames;
    }
    public static Health scan(Context context,SQLiteDatabase db){
        init(db);
        Health result=new Health();
        Set<String> files=new LinkedHashSet<>();
        try(Cursor c=db.rawQuery("SELECT id,day,title,kind,body,media FROM memories",null)){
            while(c.moveToNext()){
                result.memories++;
                collect(extract(c),files);
            }
        }catch(Exception ignored){}
        try(Cursor c=db.rawQuery("SELECT payload FROM recycle_bin",null)){
            while(c.moveToNext()){
                result.recycled++;
                collect(new JSONObject(c.getString(0)),files);
            }
        }catch(Exception ignored){}
        result.attachments=files.size();
        File root=new File(context.getFilesDir(),"media");
        byte[] buf=new byte[65536];
        for(String file:files){
            File f=new File(root,file);
            if(!f.isFile()){
                result.missing++;
                if(result.problems.size()<12)result.problems.add("Missing: "+file);
                continue;
            }
            try(InputStream in=new BufferedInputStream(new FileInputStream(f))){
                int n;while((n=in.read(buf))!=-1)result.bytes+=n;
                result.healthy++;
            }catch(Exception error){
                result.unreadable++;
                if(result.problems.size()<12)result.problems.add("Unreadable: "+file);
            }
        }
        return result;
    }
    public static String sha256(File file)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))){
            byte[] b=new byte[65536];int n;
            while((n=in.read(b))!=-1)digest.update(b,0,n);
        }
        StringBuilder result=new StringBuilder(64);
        for(byte b:digest.digest())result.append(String.format(Locale.US,"%02x",b&255));
        return result.toString();
    }
    private static String hex(byte[] d){
        StringBuilder s=new StringBuilder(d.length*2);
        for(byte b:d)s.append(String.format(Locale.US,"%02x",b&255));
        return s.toString();
    }
    public static final class Verification {
        public int memories, trash, media;
        public long bytes;
        public boolean checksums;
        public String summary(){
            return memories+" saved memories\n"+trash+" recoverable deleted memories\n"+
                media+" original media files\n"+
                String.format(Locale.US,"%.1f MB of attachment data read",bytes/1048576d)+
                "\n"+(checksums?"SHA-256 checksums matched.":"All referenced files exist and ZIP CRCs passed; this older backup does not include SHA-256 checksums.")+
                "\n\nThis ZIP can be kept as an external backup.";
        }
    }
    public static Verification verifyArchive(Context context,Uri uri)throws Exception{
        try(InputStream raw=context.getContentResolver().openInputStream(uri)){
            if(raw==null)throw new IOException("Could not open selected ZIP.");
            try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(raw))){
                ZipEntry first=zip.getNextEntry();
                if(first==null||!"archive.json".equals(first.getName()))
                    throw new IOException("Not a Little Universe ZIP: archive.json must be first.");
                ByteArrayOutputStream manifest=new ByteArrayOutputStream();
                byte[] buffer=new byte[65536];int n;
                while((n=zip.read(buffer))!=-1){
                    if(manifest.size()+n>MANIFEST_LIMIT)throw new IOException("The backup index is too large.");
                    manifest.write(buffer,0,n);
                }
                zip.closeEntry();
                JSONObject header=new JSONObject(manifest.toString("UTF-8"));
                if(!FORMAT.equals(header.optString("format")))
                    throw new IOException("Unsupported or invalid backup format.");
                JSONArray memories=header.getJSONArray("memories");
                JSONArray recycle=header.optJSONArray("recycleBin");
                Set<String> required=requiredFiles(header);
                JSONObject expected=header.optJSONObject("mediaSha256");
                Set<String> seen=new HashSet<>();
                Verification v=new Verification();
                v.memories=memories.length();v.trash=recycle==null?0:recycle.length();
                v.checksums=expected!=null;
                int fileCount=0;
                ZipEntry entry;
                while((entry=zip.getNextEntry())!=null){
                    if(++fileCount>FILE_LIMIT)throw new IOException("Too many files in ZIP.");
                    String path=entry.getName();
                    if(entry.isDirectory()&&"media/".equals(path)){zip.closeEntry();continue;}
                    if(!path.startsWith("media/"))throw new IOException("Unexpected ZIP entry: "+path);
                    String name=path.substring(6);
                    if(!name.matches(SAFE_FILE)||name.contains("..")||!seen.add(name))
                        throw new IOException("Unsafe or duplicated media path in archive.");
                    MessageDigest sha=MessageDigest.getInstance("SHA-256");
                    while((n=zip.read(buffer))!=-1){sha.update(buffer,0,n);v.bytes+=n;}
                    // Reaching EOF validates the standard ZIP checksum in ZipInputStream.
                    zip.closeEntry();
                    if(expected!=null&&expected.has(name)&&!expected.getString(name).equalsIgnoreCase(hex(sha.digest())))
                        throw new IOException("Corrupted original in ZIP: "+name);
                    v.media++;
                }
                if(!seen.containsAll(required)){
                    Set<String> missing=new LinkedHashSet<>(required);missing.removeAll(seen);
                    throw new IOException("Backup is missing "+missing.size()+" original media file(s).");
                }
                if(expected!=null){
                    Iterator<String> keys=expected.keys();
                    while(keys.hasNext()){
                        String name=keys.next();
                        if(!seen.contains(name))throw new IOException("Backup checksum references missing file: "+name);
                    }
                }
                return v;
            }
        }
    }
}