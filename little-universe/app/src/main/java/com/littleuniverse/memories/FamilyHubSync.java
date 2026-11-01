package com.littleuniverse.memories;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Private, offline-first sync with the family's Windows PC.
 * Uses the family's Tailscale HTTPS URL, never a cloud database.
 * File bytes and memory records remain on the PC and family phones.
 */
public final class FamilyHubSync {
    private FamilyHubSync(){}
    private static final String PREFS="little_universe_hub_v1";
    private static final int MAX_RESPONSE=32*1024*1024;
    private static final String SAFE="[a-zA-Z0-9_-][a-zA-Z0-9_.-]{0,149}";
    private static final String ID="[a-zA-Z0-9_-]{1,120}";
    private static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    public static boolean paired(Context c){return !prefs(c).getString("token","").isEmpty();}
    public static String lastStatus(Context c){return prefs(c).getString("lastStatus","Not connected yet");}
    public static String hubAddress(Context c){return prefs(c).getString("url","");}
    private static void saveStatus(Context c,String text){
        prefs(c).edit().putString("lastStatus",text).apply();
    }
    private static String safeUrl(String raw)throws Exception{
        String s=raw==null?"":raw.trim().replaceAll("/+$","");
        URL url=new URL(s);
        String host=url.getHost().toLowerCase(Locale.US);
        boolean local=("localhost".equals(host)||"127.0.0.1".equals(host))&&url.getProtocol().equals("http");
        if(!url.getProtocol().equals("https")&&!local)
            throw new IOException("Family Hub requires a private HTTPS URL from Tailscale.");
        if(url.getUserInfo()!=null||url.getQuery()!=null||url.getRef()!=null)
            throw new IOException("Invalid hub address");
        if(!url.getPath().isEmpty()&&!url.getPath().equals("/"))
            throw new IOException("Use the hub address without a path.");
        return url.getProtocol()+"://"+url.getAuthority();
    }
    private static byte[] read(InputStream stream,long max)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        byte[] buffer=new byte[65536];int n;
        while((n=stream.read(buffer))!=-1){
            if((long)b.size()+n>max)throw new IOException("The response exceeds the maximum safe size.");
            b.write(buffer,0,n);
        }return b.toByteArray();
    }
    private static HttpURLConnection open(Context c,String endpoint,String method)throws Exception{
        String base=hubAddress(c);
        if(base.isEmpty())throw new IOException("Pair this phone with the family hub first.");
        URL url=new URL(base+endpoint);
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000);conn.setReadTimeout(90000);
        conn.setUseCaches(false);
        conn.setRequestProperty("Cache-Control","no-store");
        String token=prefs(c).getString("token","");
        if(!token.isEmpty())conn.setRequestProperty("Authorization","Bearer "+token);
        return conn;
    }
    private static JSONObject jsonResponse(HttpURLConnection conn)throws Exception{
        int status=conn.getResponseCode();
        InputStream stream=status>=400?conn.getErrorStream():conn.getInputStream();
        byte[] bytes=stream==null?new byte[0]:read(stream,MAX_RESPONSE);
        JSONObject result=bytes.length==0?new JSONObject():new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(status<200||status>=300)
            throw new IOException(result.optString("error","Hub request failed (HTTP "+status+")"));
        return result;
    }
    private static JSONObject request(Context c,String endpoint,String method,JSONObject body)throws Exception{
        HttpURLConnection conn=open(c,endpoint,method);
        try{
            if(body!=null){
                byte[] payload=body.toString().getBytes(StandardCharsets.UTF_8);
                conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json");
                conn.setFixedLengthStreamingMode(payload.length);
                try(OutputStream out=conn.getOutputStream()){out.write(payload);}
            }
            return jsonResponse(conn);
        }finally{conn.disconnect();}
    }
    public static JSONObject pair(Context c,String base,String code,String label)throws Exception{
        String url=safeUrl(base);
        if(code==null||code.trim().length()<16)throw new IOException("Enter the one-time code from the PC.");
        HttpURLConnection conn=(HttpURLConnection)new URL(url+"/api/v1/pair").openConnection();
        conn.setRequestMethod("POST");conn.setConnectTimeout(15000);conn.setReadTimeout(30000);
        conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json");
        JSONObject body=new JSONObject();
        body.put("code",code.trim());body.put("label",label==null?"Family":label.trim());
        try{
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream out=conn.getOutputStream()){out.write(bytes);}
            JSONObject reply=jsonResponse(conn);
            String token=reply.optString("token","");
            if(token.length()<24)throw new IOException("The PC did not issue a valid device token.");
            prefs(c).edit().putString("url",url).putString("token",token)
                .putString("role",reply.optString("role","contributor"))
                .putString("lastStatus","Paired to family PC; sync pending").putLong("cursor",0L).apply();
            return reply;
        }finally{conn.disconnect();}
    }
    public static void disconnect(Context c){
        prefs(c).edit().clear().apply();
    }
    private static String digest(byte[] data)throws Exception{
        MessageDigest sha=MessageDigest.getInstance("SHA-256");
        byte[] out=sha.digest(data);
        StringBuilder result=new StringBuilder();
        for(byte b:out)result.append(String.format(Locale.US,"%02x",b&255));
        return result.toString();
    }
    private static void upload(Context c,File source,String name)throws Exception{
        if(!name.matches(SAFE)||name.contains("..")||!source.isFile())
            throw new IOException("Original media is missing: "+name);
        HttpURLConnection conn=open(c,"/api/v1/media/"+name,"PUT");
        try{
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/octet-stream");
            if(source.length()>1024L*1024L*1024L)throw new IOException("Media exceeds 1 GB hub limit.");
            conn.setFixedLengthStreamingMode(source.length());
            try(OutputStream out=new BufferedOutputStream(conn.getOutputStream());
                InputStream in=new BufferedInputStream(new FileInputStream(source))){
                byte[] buf=new byte[65536];int n;
                while((n=in.read(buf))!=-1)out.write(buf,0,n);
            }
            jsonResponse(conn);
        }finally{conn.disconnect();}
    }
    private static void download(Context c,File directory,String name)throws Exception{
        if(!name.matches(SAFE)||name.contains(".."))throw new IOException("Unsafe media filename.");
        File target=new File(directory,name);
        if(target.isFile())return;
        File temp=new File(directory,".sync-"+UUID.randomUUID());
        HttpURLConnection conn=open(c,"/api/v1/media/"+name,"GET");
        try{
            int status=conn.getResponseCode();
            if(status!=200){
                jsonResponse(conn);throw new IOException("Original not available");
            }
            if(conn.getContentLengthLong()>1024L*1024L*1024L)
                throw new IOException("Original exceeds the 1 GB limit");
            MessageDigest sha=MessageDigest.getInstance("SHA-256");
            long size=0;
            try(InputStream in=new BufferedInputStream(conn.getInputStream());
                OutputStream out=new BufferedOutputStream(new FileOutputStream(temp))){
                byte[] buf=new byte[65536];int n;
                while((n=in.read(buf))!=-1){
                    size+=n;if(size>1024L*1024L*1024L)throw new IOException("Original too large");
                    sha.update(buf,0,n);out.write(buf,0,n);
                }
            }
            String expected=conn.getHeaderField("X-File-SHA256");
            StringBuilder actual=new StringBuilder();
            for(byte x:sha.digest())actual.append(String.format(Locale.US,"%02x",x&255));
            if(expected!=null&&!expected.equalsIgnoreCase(actual.toString()))
                throw new IOException("Attachment checksum mismatch: "+name);
            if(!temp.renameTo(target)&&!target.isFile())throw new IOException("Unable to save original.");
        }finally{conn.disconnect();if(temp.exists())temp.delete();}
    }
    private static JSONObject memory(Cursor cursor)throws Exception{
        JSONObject m=new JSONObject();
        m.put("id",cursor.getString(0));m.put("day",cursor.getString(1));
        m.put("title",cursor.getString(2));m.put("kind",cursor.getString(3));
        m.put("body",cursor.getString(4));m.put("media",new JSONArray(cursor.getString(5)));
        return m;
    }
    private static boolean exists(SQLiteDatabase db,String id){
        try(Cursor c=db.rawQuery("SELECT 1 FROM memories WHERE id=?",new String[]{id})){
            return c.moveToFirst();
        }
    }
    private static void sendBatch(Context c,JSONArray memories,JSONArray items)throws Exception{
        if(memories.length()==0&&items.length()==0)return;
        JSONObject body=new JSONObject();body.put("memories",memories);body.put("items",items);
        request(c,"/api/v1/records","POST",body);
    }
    private static void push(Context c,SQLiteDatabase db,File dir)throws Exception{
        JSONArray allEntries=new JSONArray();
        LinkedHashSet<String> names=new LinkedHashSet<>();
        try(Cursor cursor=db.rawQuery("SELECT id,day,title,kind,body,media FROM memories",null)){
            while(cursor.moveToNext()){
                String id=cursor.getString(0);
                JSONObject details=FeatureStore.detail(db,id);
                if("private".equals(details.optString("visibility","family")))continue;
                JSONObject item=new JSONObject();
                JSONObject memory=memory(cursor);
                item.put("memory",memory);item.put("details",details);
                allEntries.put(item);
                JSONArray media=memory.optJSONArray("media");
                if(media!=null)for(int i=0;i<media.length();i++)names.add(media.getString(i));
            }
        }
        ArrayList<String> list=new ArrayList<>(names);
        for(int i=0;i<list.size();i+=300){
            JSONArray query=new JSONArray();
            for(int j=i;j<Math.min(list.size(),i+300);j++)query.put(list.get(j));
            JSONObject data=new JSONObject();data.put("names",query);
            JSONArray missing=request(c,"/api/v1/media/missing","POST",data).getJSONArray("missing");
            for(int j=0;j<missing.length();j++){
                String filename=missing.getString(j);
                upload(c,new File(dir,filename),filename);
            }
        }
        JSONArray items=FeatureStore.exportAll(db,null).optJSONArray("items");
        JSONArray allowed=new JSONArray();
        if(items!=null)for(int i=0;i<items.length();i++){
            JSONObject it=items.optJSONObject(i);if(it==null)continue;
            if("Reminder".equals(it.optString("kind"))||"Private".equals(it.optString("kind")))continue;
            allowed.put(it);
        }
        if(allEntries.length()==0){sendBatch(c,new JSONArray(),allowed);return;}
        for(int i=0;i<allEntries.length();i+=70){
            JSONArray batch=new JSONArray();
            for(int j=i;j<Math.min(i+70,allEntries.length());j++)batch.put(allEntries.getJSONObject(j));
            sendBatch(c,batch,i==0?allowed:new JSONArray());
        }
    }
    private static int pull(Context c,SQLiteDatabase db,File dir)throws Exception{
        long cursor=prefs(c).getLong("cursor",0L);
        JSONObject data=request(c,"/api/v1/changes?since="+cursor,"GET",null);
        JSONArray records=data.optJSONArray("memories");
        int fresh=0;
        if(records!=null)for(int i=0;i<records.length();i++){
            JSONObject entry=records.optJSONObject(i);if(entry==null)continue;
            JSONObject memory=entry.optJSONObject("memory");
            if(memory==null)continue;
            String id=memory.optString("id","");
            if(!id.matches(ID)||ArchiveSafety.trashed(db,id))continue;
            JSONArray media=memory.optJSONArray("media");if(media==null)media=new JSONArray();
            for(int j=0;j<media.length();j++){
                String name=media.optString(j);
                if(!name.matches(SAFE)||name.contains(".."))throw new IOException("Invalid original filename.");
                download(c,dir,name);
            }
            boolean already=exists(db,id);
            if(!already){
                ContentValues v=new ContentValues();
                for(String key:new String[]{"id","day","title","kind","body"})
                    v.put(key,memory.optString(key,""));
                v.put("media",media.toString());
                if(db.insertWithOnConflict("memories",null,v,SQLiteDatabase.CONFLICT_IGNORE)>=0)fresh++;
            }else{
                try(Cursor cursor2=db.rawQuery("SELECT media FROM memories WHERE id=?",new String[]{id})){
                    if(cursor2.moveToFirst()){
                        JSONArray own=new JSONArray(cursor2.getString(0));
                        HashSet<String> used=new HashSet<>();
                        for(int j=0;j<own.length();j++)used.add(own.getString(j));
                        for(int j=0;j<media.length();j++){
                            String next=media.getString(j);
                            if(used.add(next))own.put(next);
                        }
                        ContentValues v=new ContentValues();v.put("media",own.toString());
                        db.update("memories",v,"id=?",new String[]{id});
                    }
                }
            }
            JSONObject incoming=entry.optJSONObject("details");
            if(incoming!=null){
                JSONObject f=new JSONObject(),details=new JSONObject();
                details.put(id,incoming);f.put("details",details);f.put("items",new JSONArray());
                FeatureStore.merge(db,f);
            }
        }
        JSONArray items=data.optJSONArray("items");
        if(items!=null){
            JSONObject feature=new JSONObject();feature.put("items",items);
            FeatureStore.merge(db,feature);
        }
        prefs(c).edit().putLong("cursor",data.optLong("seq",cursor)).apply();
        return fresh;
    }
    public static final class Result{
        public int received;
        public String summary(){return "Family synchronized ♡ "+received+" new memories. Original media safely exchanged.";}
    }
    public static Result sync(Context c,SQLiteDatabase db)throws Exception{
        if(!paired(c))throw new IOException("Not paired to the family PC yet");
        File dir=new File(c.getFilesDir(),"media");dir.mkdirs();
        request(c,"/api/v1/info","GET",null);
        if(!"viewer".equals(prefs(c).getString("role","contributor")))push(c,db,dir);
        Result result=new Result();result.received=pull(c,db,dir);
        saveStatus(c,"Up to date · "+new java.text.SimpleDateFormat(
            "MMM d, h:mm a",Locale.US).format(new Date()));
        return result;
    }
    public static void error(Context c,Exception ex){
        saveStatus(c,"Waiting for hub · "+(ex.getMessage()==null?ex.toString():ex.getMessage()));
    }
}
