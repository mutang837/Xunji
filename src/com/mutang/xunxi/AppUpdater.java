package com.mutang.xunxi;

import android.app.*;
import android.content.*;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

final class AppUpdater {
    static final int INSTALL_SETTINGS=104;final MainActivity activity;boolean busy;
    AppUpdater(MainActivity a){activity=a;}
    String installed(){try{return activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName;}catch(Exception e){return "0.0.0";}}
    boolean active(){return !activity.isFinishing()&&!activity.isDestroyed();}
    void autoCheck(){long now=System.currentTimeMillis();long last=activity.store.prefs.getLong("lastAutoUpdateCheck",0);if(busy||now-last<6*60*60*1000L||!active()||!activity.hasWindowFocus())return;busy=true;new Thread(()->{try{UpdateClient.Release r=UpdateClient.latest();activity.runOnUiThread(()->{busy=false;if(!active()||!activity.hasWindowFocus())return;activity.store.prefs.edit().putLong("lastAutoUpdateCheck",System.currentTimeMillis()).apply();if(UpdateClient.compare(r.version,installed())>0)offer(r);});}catch(Exception ignored){activity.runOnUiThread(()->busy=false);}},"xunji-auto-update").start();}
    void offer(UpdateClient.Release r){String notes=r.notes.trim();if(notes.length()>1800)notes=notes.substring(0,1800)+"…";new AlertDialog.Builder(activity).setTitle("发现新版本 "+r.version).setMessage("当前版本："+installed()+"\n安装包："+String.format(java.util.Locale.ROOT,"%.2f MB",r.size/1048576.0)+"\n\n"+(notes.isEmpty()?"点击下载更新。":notes)).setNegativeButton("暂不更新",null).setPositiveButton("下载更新",(d,w)->download(r)).show();}
    void message(String title,String message){if(active())new AlertDialog.Builder(activity).setTitle(title).setMessage(message).setPositiveButton("确定",null).show();}
    void check(){if(busy)return;busy=true;ProgressDialog dialog=new ProgressDialog(activity);dialog.setMessage("正在检查 GitHub 正式版本…");dialog.setCancelable(false);dialog.show();new Thread(()->{try{UpdateClient.Release r=UpdateClient.latest();activity.runOnUiThread(()->{busy=false;if(!active())return;dialog.dismiss();if(UpdateClient.compare(r.version,installed())<=0){message("已是最新版本","当前版本："+installed());return;}String notes=r.notes.trim();if(notes.length()>1800)notes=notes.substring(0,1800)+"…";new AlertDialog.Builder(activity).setTitle("发现新版本 "+r.version).setMessage("当前版本："+installed()+"\n安装包："+String.format(java.util.Locale.ROOT,"%.2f MB",r.size/1048576.0)+"\n\n"+(notes.isEmpty()?"点击下载更新。":notes)).setNegativeButton("暂不更新",null).setPositiveButton("下载更新",(d,w)->download(r)).show();});}catch(Exception e){activity.runOnUiThread(()->{busy=false;if(active()){dialog.dismiss();message("检查更新失败",detail(e));}});}},"xunji-update-check").start();}
    String detail(Exception e){String m=e.getMessage();return m==null||m.trim().isEmpty()?"请检查网络后重试。":m+"\n请检查网络后重试。";}
    void download(UpdateClient.Release r){if(busy)return;busy=true;ProgressDialog dialog=new ProgressDialog(activity);dialog.setTitle("下载讯集 "+r.version);dialog.setMessage("下载并验证签名…");dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);dialog.setMax(100);dialog.setCancelable(false);dialog.show();new Thread(()->{try{UpdateClient.download(activity.getApplicationContext(),r,p->activity.runOnUiThread(()->{if(active())dialog.setProgress(p);}));activity.runOnUiThread(()->{busy=false;if(active()){dialog.dismiss();install();}});}catch(Exception e){activity.runOnUiThread(()->{busy=false;if(active()){dialog.dismiss();message("下载更新失败",detail(e));}});}},"xunji-update-download").start();}
    void install(){try{UpdateClient.verify(activity,UpdateClient.apk(activity),null);if(!activity.getPackageManager().canRequestPackageInstalls()){new AlertDialog.Builder(activity).setTitle("允许讯集安装更新").setMessage("安卓需要你允许讯集安装应用。仅用于安装已校验签名的讯集更新；安装前仍会由系统确认。你也可以在系统设置中随时关闭此权限。").setNegativeButton("取消",null).setPositiveButton("前往设置",(d,w)->{try{activity.startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())),INSTALL_SETTINGS);}catch(Exception e){message("无法打开安装设置","请在系统应用设置中允许讯集安装未知应用。");}}).show();return;}
        stageInstall();
    }catch(Exception e){message("无法安装更新",detail(e));}}
    void stageInstall(){
        if(busy)return;busy=true;ProgressDialog progress=new ProgressDialog(activity);progress.setMessage("正在为系统准备独立安装会话…");progress.setCancelable(false);progress.show();
        new Thread(()->{android.content.pm.PackageInstaller installer=activity.getPackageManager().getPackageInstaller();int id=-1;try{
            java.io.File apk=UpdateClient.apk(activity);UpdateClient.verify(activity,apk,null);
            android.content.pm.PackageInstaller.SessionParams params=new android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(activity.getPackageName());params.setSize(apk.length());if(android.os.Build.VERSION.SDK_INT>=31)params.setRequireUserAction(android.content.pm.PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
            id=installer.createSession(params);try(android.content.pm.PackageInstaller.Session session=installer.openSession(id)){
                try(java.io.InputStream in=new java.io.FileInputStream(apk);java.io.OutputStream out=session.openWrite("base.apk",0,apk.length())){byte[] buffer=new byte[32768];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);session.fsync(out);}
                Intent callback=new Intent(activity,UpdateInstallReceiver.class).setAction("com.mutang.xunxi.UPDATE_INSTALL_RESULT."+id);
                int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(android.os.Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
                session.commit(PendingIntent.getBroadcast(activity,id,callback,flags).getIntentSender());
            }activity.runOnUiThread(()->{busy=false;if(active())progress.dismiss();});
        }catch(Exception e){if(id>=0)try{installer.abandonSession(id);}catch(Exception ignored){}activity.runOnUiThread(()->{busy=false;if(active()){progress.dismiss();message("无法准备系统安装",detail(e)+"\n也可以从 GitHub 下载正式安装包覆盖安装。");}});}},"xunji-install").start();
    }
    void afterSettings(){if(activity.getPackageManager().canRequestPackageInstalls())install();else Toast.makeText(activity,"安装权限未开启，本次未更新",Toast.LENGTH_SHORT).show();}
}
