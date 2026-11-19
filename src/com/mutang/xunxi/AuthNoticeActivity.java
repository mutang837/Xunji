package com.mutang.xunxi;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.webkit.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** User-controlled import of visible notice links from a normal authenticated webpage. */
public final class AuthNoticeActivity extends Activity {
    Store store;Store.Source source;WebView web;TextView location;Button capture;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new Store(this);String id=getIntent().getStringExtra("source");
        for(Store.Source s:store.sources())if(s.id.equals(id)&&s.authenticated)source=s;
        if(source==null||!source.url.startsWith("https://")){finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(8,24,8,8);setContentView(root);
        LinearLayout bar=new LinearLayout(this);Button back=new Button(this);back.setText("返回");back.setOnClickListener(v->finish());bar.addView(back);Button reload=new Button(this);reload.setText("刷新");reload.setOnClickListener(v->web.reload());bar.addView(reload);capture=new Button(this);capture.setText("收录当前公告");capture.setOnClickListener(v->importVisible());bar.addView(capture);root.addView(bar);
        TextView help=new TextView(this);help.setText("请在学校官方页面自行登录并进入公告列表，再点击收录。不会读取账号密码；仅本机保存公告。");root.addView(help);location=new TextView(this);location.setText(source.url);location.setMaxLines(2);root.addView(location);
        web=new WebView(this);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSafeBrowsingEnabled(true);s.setSupportMultipleWindows(false);s.setJavaScriptCanOpenWindowsAutomatically(false);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return !ArticleReader.webUrl(r.getUrl().toString())||!"https".equalsIgnoreCase(r.getUrl().getScheme());}
            @Override public void onPageStarted(WebView w,String u,android.graphics.Bitmap b){location.setText(u);capture.setEnabled(false);}
            @Override public void onPageFinished(WebView w,String u){location.setText(u);capture.setEnabled(true);}
        });web.setWebChromeClient(new WebChromeClient());root.addView(web,new LinearLayout.LayoutParams(-1,0,1));web.loadUrl(source.url);
    }
    void importVisible(){
        String current=web.getUrl();if(current==null||!current.startsWith("https://")||!Collector.host(source.url).equals(Collector.host(current))){Toast.makeText(this,"请回到所订阅学校网站的公告页面",Toast.LENGTH_LONG).show();return;}
        // Only visible anchors, no form fields, cookies, hidden elements or API calls.
        String script="(function(){if(document.querySelector('input[type=password]'))return '[]';var a=[],xs=document.querySelectorAll('a[href]');for(var i=0;i<xs.length&&a.length<150;i++){var x=xs[i],r=x.getBoundingClientRect(),s=getComputedStyle(x),t=(x.innerText||'').trim();if(!r.width||!r.height||s.visibility==='hidden'||s.display==='none'||t.length<6||t.length>260||!/^https:\\/\\//i.test(x.href))continue;a.push({title:t,url:x.href});}return JSON.stringify(a);})()";
        web.evaluateJavascript(script,value->{if(isFinishing()||!current.equals(web.getUrl()))return;try{
            Object raw=new JSONTokener(value).nextValue();if(!(raw instanceof String))return;JSONArray a=new JSONArray((String)raw);List<Collector.Item> items=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(int i=0;i<a.length()&&items.size()<70;i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;String t=o.optString("title"),u=Collector.normalize(o.optString("url"));if(u==null||!u.startsWith("https://")||!Collector.host(current).equals(Collector.host(u))||u.equals(current)||!seen.add(u))continue;
                if(!t.matches(".*(通知|公告|公示|通告|安排|报名|考试|放假|选课|补考|重修|调课|成绩|教学).*")||t.matches(".*(登录|退出|个人中心|密码|成绩查询|考试查询|选课系统).*"))continue;items.add(new Collector.Item(t,u,"",0));
            }
            if(items.isEmpty()){Toast.makeText(this,"未识别到可直接打开的公告，请进入具体公告列表。",Toast.LENGTH_LONG).show();return;}
            String[] labels=new String[items.size()];boolean[] selected=new boolean[items.size()];for(int i=0;i<items.size();i++){labels[i]=items.get(i).title;selected[i]=true;}
            new AlertDialog.Builder(this).setTitle("选择要收录的公告").setMultiChoiceItems(labels,selected,(d,i,b)->selected[i]=b).setNegativeButton("取消",null).setPositiveButton("收录",(d,w)->{List<Collector.Item> chosen=new ArrayList<>();for(int i=0;i<items.size();i++)if(selected[i])chosen.add(items.get(i));if(chosen.isEmpty())return;List<Store.Post> fresh=store.merge(source,new Collector.Result(source.name,chosen,Collections.emptyList()),null);Toast.makeText(this,"已收录 "+chosen.size()+" 条公告",Toast.LENGTH_LONG).show();}).show();
        }catch(Exception e){Toast.makeText(this,"无法识别当前页面",Toast.LENGTH_SHORT).show();}});
    }
    @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){if(web!=null){web.stopLoading();web.destroy();}CookieManager.getInstance().flush();super.onDestroy();}
}
