package com.mutang.xunxi;

import android.webkit.*;
import android.widget.FrameLayout;
import org.json.*;
import java.util.*;

/** Check a user-selected list through the existing WebView session while the app is foregrounded. */
final class AuthForegroundCheck {
    final MainActivity activity;WebView view;Store.Source source;Set<String> origins;long generation;boolean foreground;String blocked;
    AuthForegroundCheck(MainActivity a){activity=a;}
    void resume(){foreground=true;}
    void pause(){foreground=false;stop();}
    void tick(String id,boolean force){
        if(!foreground||view!=null||activity.isFinishing()||!activity.hasWindowFocus())return;
        long now=System.currentTimeMillis();
        for(Store.Source s:activity.store.sources()){
            if(!s.authenticated||s.authPage.isEmpty()||(id!=null&&!id.equals(s.id)))continue;
            if(!force&&s.checked>0&&now-s.checked<activity.store.interval()*60_000L)continue;
            Set<String> approved=activity.store.approvedOrigins(s.id);
            if(!AuthUrl.trustedOrigin(s.url,approved,s.authPage)||AuthUrl.hasSessionParameter(s.authPage)){activity.store.merge(s,null,"自动订阅地址已失效，请重新设置");continue;}
            start(s,approved);return;
        }
    }
    void start(Store.Source s,Set<String> approved){
        source=s;origins=approved;blocked=null;long run=++generation;
        WebView v=new WebView(activity);view=v;v.setAlpha(0f);v.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        activity.shell.addView(v,0,new FrameLayout.LayoutParams(-1,-1));
        WebSettings settings=v.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSafeBrowsingEnabled(true);settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);CookieManager.getInstance().setAcceptThirdPartyCookies(v,false);
        v.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){String u=r.getUrl().toString();if(r.isForMainFrame()&&!AuthUrl.trustedOrigin(s.url,approved,u)){finish(run,null,"登录已过期或页面转到其他来源，请打开网站重新登录");return true;}return !"about:blank".equals(u)&&!AuthUrl.allowedNavigation(s.url,approved,u);}
            @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest r){String u=r.getUrl().toString();if(AuthUrl.cleartext(u)&&!AuthUrl.allowedNavigation(s.url,approved,u)){activity.runOnUiThread(()->{if(run==generation)blocked=AuthUrl.origin(u);});return AuthNoticeActivity.blocked();}return null;}
            @Override public void onReceivedError(WebView w,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())finish(run,null,"公告检查失败，请打开网站查看");}
            @Override public void onReceivedHttpError(WebView w,WebResourceRequest r,WebResourceResponse e){if(r.isForMainFrame())finish(run,null,"公告检查返回 HTTP "+e.getStatusCode());}
            @Override public void onPageFinished(WebView w,String u){if(run==generation){activity.handler.postDelayed(()->scan(run),1400);activity.handler.postDelayed(()->scan(run),4500);activity.handler.postDelayed(()->scan(run),9000);}}
        });
        activity.handler.postDelayed(()->{if(run==generation)finish(run,null,blocked==null?"未识别到公告或登录已过期，请打开网站检查":"需要授权额外来源："+blocked);},18000);
        v.loadUrl(s.authPage);
    }
    void scan(long run){
        if(run!=generation||!foreground||view==null)return;
        WebView v=view;String current=v.getUrl();if(current==null||!AuthUrl.trustedOrigin(source.url,origins,current)||!AuthUrl.sameList(source.authPage,current))return;
        v.evaluateJavascript(AuthNoticeParser.CAPTURE,value->{if(run!=generation||view!=v||!current.equals(v.getUrl()))return;try{
            Object raw=new JSONTokener(value).nextValue();if(!(raw instanceof String))return;
            List<Collector.Item> items=AuthNoticeParser.parse(new JSONArray((String)raw),current,source.authPage);
            if(!items.isEmpty())finish(run,items,null);
        }catch(Exception ignored){}});
    }
    void finish(long run,List<Collector.Item> items,String error){
        if(run!=generation||view==null)return;
        Store.Source s=source;stop();List<Store.Post> fresh=activity.store.merge(s,items==null?null:new Collector.Result(s.name,items,Collections.emptyList()),error);if(!fresh.isEmpty())RefreshJob.notifyNew(activity,activity.store,fresh);
    }
    void stop(){generation++;WebView v=view;view=null;source=null;if(v!=null){activity.shell.removeView(v);v.stopLoading();v.destroy();}}
}
