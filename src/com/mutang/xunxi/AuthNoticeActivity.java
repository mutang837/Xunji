package com.mutang.xunxi;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Message;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** User-controlled browser and import of visible notices; never reads login fields. */
public final class AuthNoticeActivity extends Activity {
    Store store;Store.Source source;WebView web;TextView location,help,diagnostic;Button capture,originButton,subpage;FrameLayout frame;final Set<String> approved=new java.util.concurrent.CopyOnWriteArraySet<>();String pendingOrigin;
    final ArrayList<WebView> windows=new ArrayList<>();String noticeTitle="";volatile boolean active=true;boolean showedBlocked=false;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new Store(this);String id=getIntent().getStringExtra("source");
        for(Store.Source s:store.sources())if(s.id.equals(id)&&s.authenticated)source=s;
        if(source==null||!ArticleReader.webUrl(source.url)){finish();return;}
        approved.addAll(store.approvedOrigins(source.id));noticeTitle=getIntent().getStringExtra("noticeTitle");if(noticeTitle==null)noticeTitle="";
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setOnApplyWindowInsetsListener((v,i)->{if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets p=i.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(p.left,p.top,p.right,p.bottom);}else v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});setContentView(root);
        LinearLayout bar=new LinearLayout(this);Button back=new Button(this);back.setText("返回");back.setOnClickListener(v->goBack());bar.addView(back);
        Button reload=new Button(this);reload.setText("刷新");reload.setOnClickListener(v->{if(web!=null){showedBlocked=false;pendingOrigin=null;originButton.setText("来源授权");diagnostic.setVisibility(View.GONE);web.reload();}});bar.addView(reload);
        capture=new Button(this);capture.setText("收录当前公告");capture.setOnClickListener(v->importVisible());bar.addView(capture);root.addView(bar);
        help=new TextView(this);help.setText((AuthUrl.cleartext(source.url)?"⚠ HTTP 未加密，请使用可信网络。\n":"")+(noticeTitle.isEmpty()?"进入公告列表后点击收录，支持表格公告。仅本机保存。":"请在列表中找到并点击「详情」：\n"+noticeTitle));root.addView(help);
        location=new TextView(this);location.setMaxLines(2);root.addView(location);
        diagnostic=new TextView(this);diagnostic.setTextColor(0xffa64b32);diagnostic.setVisibility(View.GONE);root.addView(diagnostic);LinearLayout extras=new LinearLayout(this);originButton=new Button(this);originButton.setText("来源授权");originButton.setOnClickListener(v->manageOrigins());extras.addView(originButton);subpage=new Button(this);subpage.setText("打开公告子页");subpage.setOnClickListener(v->openSubpage());extras.addView(subpage);root.addView(extras);
        frame=new FrameLayout(this);root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        String start=getIntent().getStringExtra("startUrl");if(!trusted(start)||AuthUrl.hasSessionParameter(start))start=source.url;
        newWindow().loadUrl(start);
    }
    WebView newWindow(){
        WebView w=new WebView(this);if(web!=null)web.setVisibility(View.GONE);web=w;windows.add(w);frame.addView(w,new FrameLayout.LayoutParams(-1,-1));capture.setEnabled(false);
        WebSettings s=w.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSafeBrowsingEnabled(true);s.setSupportMultipleWindows(true);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setUseWideViewPort(true);s.setLoadWithOverviewMode(true);s.setBuiltInZoomControls(true);s.setDisplayZoomControls(false);CookieManager.getInstance().setAcceptThirdPartyCookies(w,false);
        w.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest r){String u=r.getUrl().toString();if("about:blank".equals(u))return false;if(!allowed(u)){reportBlocked(u);return true;}return false;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest r){String u=r.getUrl().toString();if(AuthUrl.cleartext(u)&&!allowed(u)){reportBlocked(u);return blocked();}return null;}
            @Override public void onPageStarted(WebView view,String u,android.graphics.Bitmap b){if(!"about:blank".equals(u)&&!allowed(u)){view.stopLoading();reportBlocked(u);return;}if(view==web){location.setText(u);capture.setEnabled(false);}}
            @Override public void onPageFinished(WebView view,String u){if(view==web){location.setText(u);capture.setEnabled(trusted(u));}}
            @Override public void doUpdateVisitedHistory(WebView view,String u,boolean reload){if(view==web){location.setText(u);capture.setEnabled(trusted(u));}}
            @Override public void onReceivedError(WebView view,WebResourceRequest r,WebResourceError e){if(view==web&&r.isForMainFrame())showError("页面加载失败："+e.getDescription());}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest r,WebResourceResponse e){if(view==web&&r.isForMainFrame())showError("网站返回 HTTP "+e.getStatusCode());}
        });
        w.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onCreateWindow(WebView view,boolean dialog,boolean userGesture,Message result){
                if(view!=web||!userGesture||windows.size()>=5){showError("网页请求打开子窗口，但没有检测到直接点击，或窗口数量过多。");return false;}
                WebView child=newWindow();WebView.WebViewTransport transport=(WebView.WebViewTransport)result.obj;transport.setWebView(child);result.sendToTarget();return true;
            }
            @Override public void onCloseWindow(WebView win){if(win==web&&windows.size()>1)closeWindow();}
        });
        w.setDownloadListener((u,a,c,m,l)->showError("该公告包含下载附件，请使用学校网页提供的预览。"));
        return w;
    }
    void reportBlocked(String u){if(!active)return;runOnUiThread(()->{if(!active||showedBlocked)return;String org=AuthUrl.origin(u);if(org==null||!AuthUrl.cleartext(org)){showError("已阻止不支持的页面地址");return;}if(allowed(u))return;showedBlocked=true;pendingOrigin=org;showError("已阻止未授权 HTTP 来源："+org+"。如确认是学校公告站，可点击「来源授权」。");originButton.setText("授权此来源");});}
    void showError(String value){if(!active)return;diagnostic.setText(value);diagnostic.setVisibility(View.VISIBLE);}
    void closeWindow(){if(windows.size()<=1)return;WebView old=windows.remove(windows.size()-1);frame.removeView(old);old.stopLoading();old.destroy();web=windows.get(windows.size()-1);web.setVisibility(View.VISIBLE);location.setText(web.getUrl());capture.setEnabled(trusted(web.getUrl()));diagnostic.setVisibility(View.GONE);showedBlocked=false;pendingOrigin=null;originButton.setText("来源授权");}
    void goBack(){if(windows.size()>1){closeWindow();return;}if(web!=null&&web.canGoBack()){web.goBack();return;}finish();}
    void importVisible(){
        WebView currentWeb=web;String current=currentWeb.getUrl();if(!trusted(current)){Toast.makeText(this,"请回到所订阅学校网站的公告页面",Toast.LENGTH_LONG).show();return;}
        currentWeb.evaluateJavascript(AuthNoticeParser.CAPTURE,value->{if(isFinishing()||web!=currentWeb||!current.equals(web.getUrl()))return;try{
            Object raw=new JSONTokener(value).nextValue();if(!(raw instanceof String))return;List<Collector.Item> items=AuthNoticeParser.parse(new JSONArray((String)raw),current);
            if(items.isEmpty()){openSubpage();return;}
            String[] labels=new String[items.size()];boolean[] selected=new boolean[items.size()];for(int i=0;i<items.size();i++){labels[i]=items.get(i).title;selected[i]=true;}
            new AlertDialog.Builder(this).setTitle("选择要收录的公告").setMultiChoiceItems(labels,selected,(d,i,b)->selected[i]=b).setNegativeButton("取消",null).setPositiveButton("收录",(d,w)->{List<Collector.Item> chosen=new ArrayList<>();for(int i=0;i<items.size();i++)if(selected[i])chosen.add(items.get(i));if(chosen.isEmpty())return;store.merge(source,new Collector.Result(source.name,chosen,Collections.emptyList()),null);Toast.makeText(this,"已收录 "+chosen.size()+" 条公告",Toast.LENGTH_LONG).show();}).show();
        }catch(Exception e){Toast.makeText(this,"无法识别当前页面",Toast.LENGTH_SHORT).show();}});
    }
    boolean trusted(String url){return AuthUrl.trustedOrigin(source.url,approved,url);}
    boolean allowed(String url){return AuthUrl.allowedNavigation(source.url,approved,url);}
    void manageOrigins(){
        if(pendingOrigin!=null&&!approved.contains(pendingOrigin)){
            String origin=pendingOrigin;
            new AlertDialog.Builder(this).setTitle("授权额外 HTTP 来源？").setMessage(origin+"\n\n此地址是当前网页发起的另一个来源请求。仅当你确认它确为学校官方系统时才允许。HTTP 不加密，网络中的第三方可能截获或篡改内容。此次授权只作用于此订阅，可随时撤销。").setNegativeButton("取消",null).setPositiveButton("确认允许",(d,w)->{if(!store.approveOrigin(source.id,origin,true)){Toast.makeText(this,"授权失败或已达到上限",Toast.LENGTH_SHORT).show();return;}approved.add(origin);pendingOrigin=null;showedBlocked=false;originButton.setText("来源授权");diagnostic.setVisibility(View.GONE);new AlertDialog.Builder(this).setMessage("来源已授权，请刷新并重新点击公告栏目。若公告仍在嵌入子页，可点击「打开公告子页」。").setPositiveButton("刷新",(x,y)->web.reload()).setNegativeButton("稍后",null).show();}).show();return;
        }
        String[] origins=approved.toArray(new String[0]);if(origins.length==0){Toast.makeText(this,"没有额外授权来源",Toast.LENGTH_SHORT).show();return;}
        new AlertDialog.Builder(this).setTitle("选择要撤销的来源").setItems(origins,(d,i)->new AlertDialog.Builder(this).setTitle("撤销来源授权？").setMessage(origins[i]).setNegativeButton("取消",null).setPositiveButton("撤销",(a,b)->{if(store.approveOrigin(source.id,origins[i],false)){approved.remove(origins[i]);pendingOrigin=null;showedBlocked=false;diagnostic.setVisibility(View.GONE);if(!allowed(web.getUrl())){if(windows.size()>1)closeWindow();else web.loadUrl(source.url);}else web.reload();}}).show()).setNegativeButton("关闭",null).show();
    }
    void openSubpage(){
        WebView current=web;String page=current.getUrl();if(!trusted(page)){Toast.makeText(this,"请先回到已授权的公告页面",Toast.LENGTH_SHORT).show();return;}
        // Only actual visible iframe src attributes. Do not inspect cross-origin document contents.
        String js="(function(){var out=[];function scan(d,n){if(n>3)return;d.querySelectorAll('iframe,frame').forEach(function(f){var r=f.getBoundingClientRect(),s=d.defaultView.getComputedStyle(f);if(r.width&&r.height&&s.display!=='none'&&s.visibility!=='hidden'){if(f.src)out.push(f.src);try{if(f.contentDocument)scan(f.contentDocument,n+1);}catch(e){}}});}scan(document,0);return JSON.stringify(out.slice(0,30));})()";
        current.evaluateJavascript(js,value->{if(!active||current!=web||!page.equals(web.getUrl()))return;try{Object raw=new JSONTokener(value).nextValue();if(!(raw instanceof String))return;JSONArray rows=new JSONArray((String)raw);LinkedHashSet<String> valid=new LinkedHashSet<>();for(int i=0;i<rows.length();i++){String uri=AuthUrl.normalize(rows.optString(i));if(uri!=null&&trusted(uri)&&!AuthUrl.hasSessionParameter(uri)&&!uri.equals(page))valid.add(uri);}String[] urls=valid.toArray(new String[0]);if(urls.length==0){Toast.makeText(this,"没有发现可打开的已授权子页面。请确认公告已加载、来源已授权。",Toast.LENGTH_LONG).show();return;}new AlertDialog.Builder(this).setTitle("打开公告子页面").setItems(urls,(d,i)->{if(windows.size()<5&&trusted(urls[i]))newWindow().loadUrl(urls[i]);else Toast.makeText(this,"请关闭其他子窗口后重试",Toast.LENGTH_SHORT).show();}).setNegativeButton("取消",null).show();}catch(Exception e){Toast.makeText(this,"无法识别子页面",Toast.LENGTH_SHORT).show();}});
    }
    static WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",403,"Forbidden",Collections.emptyMap(),new java.io.ByteArrayInputStream(new byte[0]));}
    @Override public void onBackPressed(){goBack();}
    @Override protected void onDestroy(){active=false;for(WebView w:windows){w.stopLoading();frame.removeView(w);w.destroy();}windows.clear();CookieManager.getInstance().flush();super.onDestroy();}
}
