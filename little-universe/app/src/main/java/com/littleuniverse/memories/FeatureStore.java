package com.littleuniverse.memories;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;

/** Portable offline metadata and structured family records. No network services. */
public final class FeatureStore {
    private FeatureStore(){}
    public static void init(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS memory_details (id TEXT PRIMARY KEY, payload TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS keepsake_items (id TEXT PRIMARY KEY, kind TEXT NOT NULL, day TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, author TEXT NOT NULL, payload TEXT NOT NULL)");
    }
    public static String author(Context ctx){
        String name=ctx.getSharedPreferences("little_universe_profile",Context.MODE_PRIVATE).getString("contributorName","Dad");
        return name.trim().isEmpty()?"Family":name.trim();
    }
    public static String contributorId(Context ctx){
        SharedPreferences prefs=ctx.getSharedPreferences("little_universe_profile",Context.MODE_PRIVATE);
        String id=prefs.getString("contributorId","");
        if(id.isEmpty()){id=UUID.randomUUID().toString();prefs.edit().putString("contributorId",id).apply();}
        return id;
    }
    public static JSONObject detail(SQLiteDatabase db,String id){
        try(Cursor c=db.rawQuery("SELECT payload FROM memory_details WHERE id=?",new String[]{id})){
            if(c.moveToFirst())return new JSONObject(c.getString(0));
        }catch(Exception ignored){}
        return new JSONObject();
    }
    public static void putDetail(SQLiteDatabase db,String id,JSONObject json){
        if(id==null||id.isEmpty())return;
        db.execSQL("INSERT OR REPLACE INTO memory_details(id,payload) VALUES(?,?)",new Object[]{id,json.toString()});
    }
    public static void claim(Context ctx,SQLiteDatabase db,String id){
        JSONObject j=detail(db,id);
        if(j.optString("author","").isEmpty()){
            try{j.put("author",author(ctx));j.put("authorId",contributorId(ctx));}catch(Exception ignored){}
            putDetail(db,id,j);
        }
    }
    public static boolean starred(SQLiteDatabase db,String id){return detail(db,id).optBoolean("favorite",false);}
    public static void star(SQLiteDatabase db,String id){
        JSONObject d=detail(db,id);
        try{d.put("favorite",!d.optBoolean("favorite",false));}catch(Exception ignored){}
        putDetail(db,id,d);
    }
    public static final class Item{
        public String id,kind,day,title,body,author;
        public JSONObject extras=new JSONObject();
        public Item(){id=UUID.randomUUID().toString();}
    }
    public static void save(SQLiteDatabase db,Item m){
        db.execSQL("INSERT OR REPLACE INTO keepsake_items(id,kind,day,title,body,author,payload) VALUES(?,?,?,?,?,?,?)",
          new Object[]{m.id,m.kind,m.day,m.title,m.body,m.author,m.extras.toString()});
    }
    private static Item parse(Cursor c){
        Item i=new Item();i.id=c.getString(0);i.kind=c.getString(1);i.day=c.getString(2);
        i.title=c.getString(3);i.body=c.getString(4);i.author=c.getString(5);
        try{i.extras=new JSONObject(c.getString(6));}catch(Exception ignored){}
        return i;
    }
    public static List<Item> items(SQLiteDatabase db,String kind){
        ArrayList<Item> list=new ArrayList<>();
        String[] args=kind==null?null:new String[]{kind};
        try(Cursor c=db.rawQuery("SELECT id,kind,day,title,body,author,payload FROM keepsake_items"+
            (kind==null?"":" WHERE kind=?")+" ORDER BY day DESC, rowid DESC",args)){
            while(c.moveToNext())list.add(parse(c));
        }
        return list;
    }
    public static void delete(SQLiteDatabase db,String id){db.delete("keepsake_items","id=?",new String[]{id});}
    public static int count(SQLiteDatabase db,String kind){
        try(Cursor c=db.rawQuery("SELECT count(*) FROM keepsake_items WHERE kind=?",new String[]{kind})){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
    private static JSONArray union(JSONArray a,JSONArray b,String key) throws JSONException{
        JSONArray result=new JSONArray();Set<String> seen=new LinkedHashSet<>();
        for(JSONArray source:new JSONArray[]{a,b}) if(source!=null)for(int i=0;i<source.length();i++){
            Object entry=source.get(i);String signature=key==null?String.valueOf(entry):
                entry instanceof JSONObject?((JSONObject)entry).optString(key,""):String.valueOf(entry);
            if(seen.add(signature))result.put(entry);
        }
        return result;
    }
    private static JSONObject joinDetails(JSONObject local,JSONObject incoming) throws JSONException{
        JSONObject merged=new JSONObject(local.toString());
        for(String key:new String[]{"author","authorId","album","location","unlockDate","caption","mood","visibility"}){
            if(merged.optString(key,"").isEmpty()&&!incoming.optString(key,"").isEmpty())
                merged.put(key,incoming.get(key));
        }
        merged.put("favorite",local.optBoolean("favorite",false)||incoming.optBoolean("favorite",false));
        merged.put("tags",union(local.optJSONArray("tags"),incoming.optJSONArray("tags"),null));
        merged.put("comments",union(local.optJSONArray("comments"),incoming.optJSONArray("comments"),"id"));
        return merged;
    }
    public static JSONObject exportAll(SQLiteDatabase db,Set<String> allowed)throws JSONException{
        JSONObject root=new JSONObject();
        JSONObject details=new JSONObject();
        try(Cursor c=db.rawQuery("SELECT id,payload FROM memory_details",null)){
            while(c.moveToNext())try{
                if(allowed==null||allowed.contains(c.getString(0)))
                    details.put(c.getString(0),new JSONObject(c.getString(1)));
            }catch(JSONException ignored){}
        }
        root.put("details",details);
        JSONArray records=new JSONArray();
        for(Item i:items(db,null)){
            JSONObject j=new JSONObject();
            j.put("id",i.id);j.put("kind",i.kind);j.put("day",i.day);j.put("title",i.title);
            j.put("body",i.body);j.put("author",i.author);j.put("payload",i.extras);
            records.put(j);
        }
        root.put("items",records);
        return root;
    }
    public static void merge(SQLiteDatabase db,JSONObject from)throws JSONException{
        if(from==null)return;
        JSONObject details=from.optJSONObject("details");
        if(details!=null){
            Iterator<String> keys=details.keys();
            while(keys.hasNext()){
                String id=keys.next();
                if(id.length()>120||!id.matches("[a-zA-Z0-9_-]+"))continue;
                JSONObject incoming=details.optJSONObject(id);
                if(incoming==null)continue;
                // Imported metadata only for memories that exist in this archive.
                try(Cursor c=db.rawQuery("SELECT id FROM memories WHERE id=?",new String[]{id})){
                    if(!c.moveToFirst())continue;
                }
                putDetail(db,id,joinDetails(detail(db,id),incoming));
            }
        }
        JSONArray entries=from.optJSONArray("items");
        if(entries!=null){
            if(entries.length()>100000)throw new JSONException("Too many family records.");
            for(int i=0;i<entries.length();i++){
                JSONObject j=entries.optJSONObject(i);if(j==null)continue;
                String id=j.optString("id");
                if(!id.matches("[a-zA-Z0-9_-]{1,120}"))continue;
                Item item=new Item();item.id=id;
                item.kind=j.optString("kind","Note");item.day=j.optString("day","2026-10-31");
                item.title=j.optString("title");item.body=j.optString("body");
                item.author=j.optString("author","Family");
                item.extras=j.optJSONObject("payload");if(item.extras==null)item.extras=new JSONObject();
                if(item.title.length()>1000||item.body.length()>100000||
                   item.kind.length()>100||item.author.length()>200)continue;
                db.execSQL("INSERT OR IGNORE INTO keepsake_items(id,kind,day,title,body,author,payload) VALUES(?,?,?,?,?,?,?)",
                  new Object[]{id,item.kind,item.day,item.title,item.body,item.author,item.extras.toString()});
            }
        }
    }
}