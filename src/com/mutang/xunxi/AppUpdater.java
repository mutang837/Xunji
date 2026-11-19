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
        Uri uri=Uri.parse("content://"+activity.getPackageName()+".updates/latest.apk");Intent intent=new Intent(Intent.ACTION_VIEW);intent.setDataAndType(uri,"application/vnd.android.package-archive");intent.setClipData(ClipData.newRawUri("verified update",uri));intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);activity.startActivity(intent);
    }catch(Exception e){message("无法安装更新",detail(e));}}
    void afterSettings(){if(activity.getPackageManager().canRequestPackageInstalls())install();else Toast.makeText(activity,"安装权限未开启，本次未更新",Toast.LENGTH_SHORT).show();}
}
