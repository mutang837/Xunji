package com.mutang.xunxi;

import android.content.*;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** Private callback from the platform installer. Never bypasses its confirmation step. */
public final class UpdateInstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
            Intent confirmation=intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if(confirmation!=null)try{confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(confirmation);}catch(Exception e){Toast.makeText(context,"系统未能打开安装确认，请重新检查更新",Toast.LENGTH_LONG).show();}
        }else if(status==PackageInstaller.STATUS_SUCCESS){
            Toast.makeText(context,"讯集更新安装成功",Toast.LENGTH_LONG).show();
        }else{
            String detail=intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Toast.makeText(context,"更新未安装"+(detail==null?"":": "+detail),Toast.LENGTH_LONG).show();
        }
    }
}
