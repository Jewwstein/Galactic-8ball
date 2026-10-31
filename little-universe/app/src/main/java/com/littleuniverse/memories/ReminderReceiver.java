package com.littleuniverse.memories;

import android.app.*;
import android.content.*;
import android.os.Build;
import java.util.*;

public final class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL="little_universe_memory_reminders";
    public static void schedule(Context context,FeatureStore.Item item){
        long when=item.extras.optLong("trigger",0);
        if(when<=System.currentTimeMillis())return;
        Intent intent=new Intent(context,ReminderReceiver.class);
        intent.setAction("com.littleuniverse.memories.REMINDER");
        intent.putExtra("id",item.id);intent.putExtra("title",item.title);
        PendingIntent pi=PendingIntent.getBroadcast(context,item.id.hashCode(),intent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarms=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(alarms!=null)alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
    }
    public static void cancel(Context context,String id){
        Intent i=new Intent(context,ReminderReceiver.class);i.setAction("com.littleuniverse.memories.REMINDER");
        PendingIntent pi=PendingIntent.getBroadcast(context,id.hashCode(),i,
            PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
        if(pi!=null){
            AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
            if(alarm!=null)alarm.cancel(pi);pi.cancel();
        }
    }
    @Override public void onReceive(Context context,Intent intent){
        if("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())){
            try{
                android.database.sqlite.SQLiteDatabase db=android.database.sqlite.SQLiteDatabase.openDatabase(
                    context.getDatabasePath("little_universe_v1.db").getAbsolutePath(),null,0);
                FeatureStore.init(db);
                for(FeatureStore.Item m:FeatureStore.items(db,"Reminder"))schedule(context,m);
                db.close();
            }catch(Exception ignored){}
            return;
        }
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
          !=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel channel=new NotificationChannel(CHANNEL,"Little Universe memories",
                NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Reminders to treasure little moments");
            manager.createNotificationChannel(channel);
        }
        Intent launch=new Intent(context,MainActivity.class);
        PendingIntent open=PendingIntent.getActivity(context,0,launch,
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?
            new Notification.Builder(context,CHANNEL):new Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.ic_menu_my_calendar)
         .setContentTitle("Little Universe ♡")
         .setContentText(intent.getStringExtra("title"))
         .setAutoCancel(true).setContentIntent(open);
        manager.notify(intent.getStringExtra("id")==null?1:intent.getStringExtra("id").hashCode(),b.build());
    }
}