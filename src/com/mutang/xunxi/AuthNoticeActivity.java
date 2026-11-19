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
    Store store;Store.Source source;WebView web;TextView location,help,diagnostic;Button capture;FrameLayout frame;
    final ArrayList<WebView> windows=new ArrayList<>();String noticeTitle="";volatile boolean active=true;boolean showedBlocked=false;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new Store(this);String id=getIntent().getStringExtra("source");
        for(Store.Source s:store.sources())if(s.id.equals(id)&&s.authenticated)source=s;
        if(source==null||!ArticleReader.webUrl(source.url)){finish();return;}
        noticeTitle=getIntent().getStringExtra("noticeTitle");if(noticeTitle==null)noticeTitle="";
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setOnApplyWindowInsetsListener((v,i)->{if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets p=i.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(p.left,p.top,p.right,p.bottom);}else v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});setContentView(root);
        LinearLayout bar=new LinearLayout(this);Button back=new Button(this);back.setText("返回");back.setOnClickListener(v->goBack());bar.addView(back);
        Button reload=new Button(this);reload.setText("刷新");reload.setOnClickListener(v->{if(web!=null){showedBlocked=false;diagnostic.setVisibility(View.GONE);web.reload();}});bar.addView(reload);
        capture=new Button(this);capture.setText("收录当前公告");capture.setOnClickListener(v->importVisible());bar.addView(capture);root.addView(bar);
        help=new TextView(this);help.setText((AuthUrl.cleartext(source.url)?"⚠ HTTP 未加密，请使用可信网络。\n":"")+(noticeTitle.isEmpty()?"进入公告列表后点击收录，支持表格公告。仅本机保存。":"请在列表中找到并点击「详情」：\n"+noticeTitle));root.addView(help);
        location=new TextView(this);location.setMaxLines(2);root.addView(location);
        diagnostic=new TextView(this);diagnostic.setTextColor(0xffa64b32);diagnostic.setVisibility(View.GONE);root.addView(diagnostic);
        frame=new FrameLayout(this);root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        String start=getIntent().getStringExtra("startUrl");if(!AuthUrl.sameOrigin(source.url,start)||AuthUrl.hasSessionParameter(start))start=source.url;
        newWindow().loadUrl(start);
    }
    WebView newWindow(){
        WebView w=new WebView(this);if(web!=null)web.setVisibility(View.GONE);web=w;windows.add(w);frame.addView(w,new FrameLayout.LayoutParams(-1,-1));capture.setEnabled(false);
        WebSettings s=w.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSafeBrowsingEnabled(true);s.setSupportMultipleWindows(true);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setUseWideViewPort(true);s.setLoadWithOverviewMode(true);s.setBuiltInZoomControls(true);s.setDisplayZoomControls(false);CookieManager.getInstance().setAcceptThirdPartyCookies(w,false);
        w.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest r){String u=r.getUrl().toString();if("about:blank".equals(u))return false;if(!AuthUrl.allowedNavigation(source.url,u)){reportBlocked(u);return true;}return false;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest r){String u=r.getUrl().toString();if(AuthUrl.cleartext(u)&&!AuthUrl.allowedNavigation(source.url,u)){reportBlocked(u);return blocked();}return null;}
            @Override public void onPageStarted(WebView view,String u,android.graphics.Bitmap b){if(!"about:blank".equals(u)&&!AuthUrl.allowedNavigation(source.url,u)){view.stopLoading();reportBlocked(u);return;}if(view==web){location.setText(u);capture.setEnabled(false);}}
            @Override public void onPageFinished(WebView view,String u){if(view==web){location.setText(u);capture.setEnabled(AuthUrl.sameOrigin(source.url,u));}}
            @Override public void doUpdateVisitedHistory(WebView view,String u,boolean reload){if(view==web){location.setText(u);capture.setEnabled(AuthUrl.sameOrigin(source.url,u));}}
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
    void reportBlocked(String u){if(!active)return;runOnUiThread(()->{if(!active||showedBlocked)return;showedBlocked=true;try{java.net.URI uri=java.net.URI.create(u);showError("已阻止未授权 HTTP 来源："+uri.getHost()+(uri.getPort()>0?":"+uri.getPort():"")+"。如页面空白，可将此提示发给开发者。");}catch(Exception ignored){showError("已阻止不支持的页面地址");}});}
    void showError(String value){if(!active)return;diagnostic.setText(value);diagnostic.setVisibility(View.VISIBLE);}
    void closeWindow(){if(windows.size()<=1)return;WebView old=windows.remove(windows.size()-1);frame.removeView(old);old.stopLoading();old.destroy();web=windows.get(windows.size()-1);web.setVisibility(View.VISIBLE);location.setText(web.getUrl());capture.setEnabled(AuthUrl.sameOrigin(source.url,web.getUrl()));diagnostic.setVisibility(View.GONE);showedBlocked=false;}
    void goBack(){if(windows.size()>1){closeWindow();return;}if(web!=null&&web.canGoBack()){web.goBack();return;}finish();}
    void importVisible(){
        WebView currentWeb=web;String current=currentWeb.getUrl();if(!AuthUrl.sameOrigin(source.url,current)){Toast.makeText(this,"请回到所订阅学校网站的公告页面",Toast.LENGTH_LONG).show();return;}
        currentWeb.evaluateJavascript(AuthNoticeParser.CAPTURE,value->{if(isFinishing()||web!=currentWeb||!current.equals(web.getUrl()))return;try{
            Object raw=new JSONTokener(value).nextValue();if(!(raw instanceof String))return;List<Collector.Item> items=AuthNoticeParser.parse(new JSONArray((String)raw),current);
            if(items.isEmpty()){Toast.makeText(this,"未识别到公告，请进入有标题和详情的公告列表。",Toast.LENGTH_LONG).show();return;}
            String[] labels=new String[items.size()];boolean[] selected=new boolean[items.size()];for(int i=0;i<items.size();i++){labels[i]=items.get(i).title;selected[i]=true;}
            new AlertDialog.Builder(this).setTitle("选择要收录的公告").setMultiChoiceItems(labels,selected,(d,i,b)->selected[i]=b).setNegativeButton("取消",null).setPositiveButton("收录",(d,w)->{List<Collector.Item> chosen=new ArrayList<>();for(int i=0;i<items.size();i++)if(selected[i])chosen.add(items.get(i));if(chosen.isEmpty())return;store.merge(source,new Collector.Result(source.name,chosen,Collections.emptyList()),null);Toast.makeText(this,"已收录 "+chosen.size()+" 条公告",Toast.LENGTH_LONG).show();}).show();
        }catch(Exception e){Toast.makeText(this,"无法识别当前页面",Toast.LENGTH_SHORT).show();}});
    }
    static WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",403,"Forbidden",Collections.emptyMap(),new java.io.ByteArrayInputStream(new byte[0]));}
    @Override public void onBackPressed(){goBack();}
    @Override protected void onDestroy(){active=false;for(WebView w:windows){w.stopLoading();frame.removeView(w);w.destroy();}windows.clear();CookieManager.getInstance().flush();super.onDestroy();}
}
