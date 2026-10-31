package com.littleuniverse.memories;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

/**
 * Instrumentation exercises the actual Android SQLite database and ContentResolver ZIP path.
 * There is no test-only server or network dependency in the installed application.
 */
@RunWith(AndroidJUnit4.class)
public final class FamilySyncInstrumentedTest {
    private static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    private static void write(File to,byte[] data) throws Exception{
        try(FileOutputStream out=new FileOutputStream(to)){out.write(data);}
    }
    private static JSONObject memory(String id,String title, String[] media) throws Exception{
        JSONObject j=new JSONObject();
        j.put("id",id);j.put("day","2026-10-31");
        j.put("title",title);j.put("kind","Journal");
        j.put("body","A family memory");
        j.put("media",new JSONArray(Arrays.asList(media)));return j;
    }
    private static File zip(Context ctx,JSONArray list,Map<String,byte[]> media) throws Exception{
        File file=new File(ctx.getCacheDir(),"sync_test_"+UUID.randomUUID()+".zip");
        JSONObject root=new JSONObject();
        root.put("format","little-universe-android-v1");
        root.put("settings",new JSONObject());
        root.put("memories",list);
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(file))){
            out.putNextEntry(new ZipEntry("archive.json"));
            out.write(bytes(root.toString()));out.closeEntry();
            for(Map.Entry<String,byte[]> entry:media.entrySet()){
                out.putNextEntry(new ZipEntry("media/"+entry.getKey()));
                out.write(entry.getValue());out.closeEntry();
            }
        }
        return file;
    }
    private static void insert(SQLiteDatabase db,JSONObject j) throws Exception{
        db.execSQL("INSERT INTO memories(id,day,title,kind,body,media) VALUES(?,?,?,?,?,?)",
            new Object[]{j.getString("id"),j.getString("day"),j.getString("title"),
                j.getString("kind"),j.getString("body"),j.getString("media")});
    }
    private static int count(SQLiteDatabase db){
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM memories",null)){
            assertTrue(c.moveToFirst());return c.getInt(0);
        }
    }
    @Test public void importingFamilyZipTwiceAddsOnlyMissingEntriesAndAttachments() throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SQLiteDatabase db=SQLiteDatabase.create(null);
        db.execSQL("CREATE TABLE memories (id TEXT PRIMARY KEY,day TEXT NOT NULL,title TEXT NOT NULL,kind TEXT NOT NULL,body TEXT NOT NULL,media TEXT NOT NULL)");
        String a=UUID.randomUUID().toString(),b=UUID.randomUUID().toString();
        String photo1=UUID.randomUUID()+".jpg",photo2=UUID.randomUUID()+".jpg",
            audio=UUID.randomUUID()+".m4a";
        File folder=new File(context.getFilesDir(),"media");folder.mkdirs();
        File existing=new File(folder,photo1);
        write(existing,bytes("original-photo-from-dad"));
        File archive=null;
        try{
            insert(db,memory(a,"Dad's original words",new String[]{photo1}));
            JSONArray exchange=new JSONArray();
            exchange.put(memory(a,"Mom's alternate words",new String[]{photo1,audio}));
            exchange.put(memory(b,"Grandma's new memory",new String[]{photo2}));
            Map<String,byte[]> attachments=new LinkedHashMap<>();
            attachments.put(photo1,bytes("original-photo-from-dad"));
            attachments.put(audio,bytes("grandma-voice"));
            attachments.put(photo2,bytes("new-photo"));
            archive=zip(context,exchange,attachments);
            FamilySync.Result first=FamilySync.merge(context,db,Uri.fromFile(archive));
            assertEquals(1,first.added);
            assertEquals(1,first.alreadyPresent);
            assertEquals(1,first.attached);
            assertEquals(2,first.filesCopied);
            assertEquals(1,first.localEditsPreserved);
            assertEquals(2,count(db));
            try(Cursor c=db.rawQuery("SELECT title,media FROM memories WHERE id=?",new String[]{a})){
                assertTrue(c.moveToFirst());assertEquals("Dad's original words",c.getString(0));
                JSONArray joined=new JSONArray(c.getString(1));
                assertEquals(2,joined.length());
                assertEquals(photo1,joined.getString(0));
                assertEquals(audio,joined.getString(1));
            }
            FamilySync.Result again=FamilySync.merge(context,db,Uri.fromFile(archive));
            assertEquals(0,again.added);
            assertEquals(2,again.alreadyPresent);
            assertEquals(0,again.attached);
            assertEquals(0,again.filesCopied);
            assertEquals(2,count(db));
            assertArrayEquals(bytes("original-photo-from-dad"),
                java.nio.file.Files.readAllBytes(existing.toPath()));
            assertArrayEquals(bytes("grandma-voice"),
                java.nio.file.Files.readAllBytes(new File(folder,audio).toPath()));
        }finally{
            db.close();
            if(archive!=null)archive.delete();
            existing.delete();
            new File(folder,photo2).delete();
            new File(folder,audio).delete();
        }
    }
    @Test public void mediaCollisionCancelsImportWithoutReplacingLocalFiles() throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SQLiteDatabase db=SQLiteDatabase.create(null);
        db.execSQL("CREATE TABLE memories (id TEXT PRIMARY KEY,day TEXT NOT NULL,title TEXT NOT NULL,kind TEXT NOT NULL,body TEXT NOT NULL,media TEXT NOT NULL)");
        String photo=UUID.randomUUID()+".jpg";
        File folder=new File(context.getFilesDir(),"media");folder.mkdirs();
        File existing=new File(folder,photo);
        write(existing,bytes("keep-this"));
        File archive=null;
        try{
            JSONArray a=new JSONArray();a.put(memory(UUID.randomUUID().toString(),"A new event",new String[]{photo}));
            Map<String,byte[]> files=new LinkedHashMap<>();files.put(photo,bytes("different-content"));
            archive=zip(context,a,files);
            try{
                FamilySync.merge(context,db,Uri.fromFile(archive));
                fail("Import should reject media collision.");
            }catch(IOException expected){
                assertTrue(expected.getMessage().contains("collision"));
            }
            assertEquals(0,count(db));
            assertArrayEquals(bytes("keep-this"),java.nio.file.Files.readAllBytes(existing.toPath()));
        }finally{
            db.close();existing.delete();if(archive!=null)archive.delete();
        }
    }
}