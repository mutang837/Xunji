package com.mutang.xunxi;

import android.Manifest;
import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class RefreshJob extends JobService {
    private static final int JOB_ID=42019;
    private static final String CHANNEL="xunji_updates";
    static final AtomicBoolean running=new AtomicBoolean(false);
    private Thread worker;
    static void schedule(Context c,boolean replace){
        Store s=new Store(c);JobScheduler scheduler=c.getSystemService(JobScheduler.class);
        if(s.sources().isEmpty()){scheduler.cancel(JOB_ID);return;}
        if(!replace&&scheduler.getPendingJob(JOB_ID)!=null)return;
        JobInfo info=new JobInfo.Builder(JOB_ID,new ComponentName(c,RefreshJob.class)).setPersisted(true).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(s.interval()*60_000L).build();scheduler.schedule(info);
    }
    static int refresh(Context context,String onlyId){
        if(!running.compareAndSet(false,true))return -1;
        Store store=new Store(context);List<Store.Post> fresh=Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool=Executors.newFixedThreadPool(3);
        try{
            List<Callable<Void>> jobs=new ArrayList<>();
            for(Store.Source src:store.sources())if(!src.authenticated&&(onlyId==null||onlyId.equals(src.id)))jobs.add(()->{
                try{Collector.Result r=Collector.collect(src.url);if(!Thread.currentThread().isInterrupted()){SiteIcons.fetch(context,src.id,r.icons);fresh.addAll(store.merge(src,r,r.items.isEmpty()?"未识别到公告；请添加具体的公告列表页或 RSS 地址":null));}}
                catch(Exception e){if(!Thread.currentThread().isInterrupted())store.merge(src,null,error(e));}return null;});
            pool.invokeAll(jobs,6,TimeUnit.MINUTES);store.setLastRefresh();
            if(!fresh.isEmpty())notifyNew(context,store,new ArrayList<>(fresh));return fresh.size();
        }catch(InterruptedException e){Thread.currentThread().interrupt();return 0;}
        finally{pool.shutdownNow();running.set(false);}
    }
    static String error(Exception e){
        if(e instanceof org.jsoup.HttpStatusException)return "网站返回 HTTP "+((org.jsoup.HttpStatusException)e).getStatusCode();
        if(e instanceof java.net.SocketTimeoutException)return "连接超时，请稍后重试";
        if(e instanceof java.net.UnknownHostException)return "无法解析网站域名";
        if(e instanceof javax.net.ssl.SSLException)return "网站 HTTPS 证书或连接异常";
        if(e instanceof IllegalStateException)return e.getMessage();return "抓取失败，请检查网络和网址";
    }
    static void channel(Context c){NotificationManager m=c.getSystemService(NotificationManager.class);NotificationChannel ch=new NotificationChannel(CHANNEL,"订阅更新",NotificationManager.IMPORTANCE_DEFAULT);ch.setDescription("关注网站出现新的公告时通知");m.createNotificationChannel(ch);}
    static void notifyNew(Context c,Store store,List<Store.Post> fresh){
        if(!store.notifyEnabled()||(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED))return;
        channel(c);NotificationManager nm=c.getSystemService(NotificationManager.class);
        Intent i=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi=PendingIntent.getActivity(c,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.InboxStyle style=new Notification.InboxStyle().setBigContentTitle(fresh.size()+" 条新的订阅消息");
        for(int n=0;n<Math.min(6,fresh.size());n++)style.addLine(fresh.get(n).sourceName+" · "+fresh.get(n).title);
        Notification notification=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_notice).setContentTitle(fresh.size()+" 条新消息").setContentText(fresh.get(0).title).setStyle(style).setContentIntent(pi).setAutoCancel(true).build();nm.notify(1001,notification);
    }
    @Override public boolean onStartJob(JobParameters p){worker=new Thread(()->{try{refresh(getApplicationContext(),null);}finally{jobFinished(p,false);}},"xunji-background");worker.start();return true;}
    @Override public boolean onStopJob(JobParameters p){if(worker!=null)worker.interrupt();return true;}
}
