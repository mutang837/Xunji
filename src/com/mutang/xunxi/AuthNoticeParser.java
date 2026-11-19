package com.mutang.xunxi;

import org.json.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Read visible notice titles and explicit links, never replay the site's actions or private APIs. */
final class AuthNoticeParser {
    static final String CAPTURE =
        "(function(){var out=[];function visible(e){if(!e)return false;var r=e.getBoundingClientRect(),s=e.ownerDocument.defaultView.getComputedStyle(e);return !!(r.width&&r.height&&s.visibility!=='hidden'&&s.display!=='none');}"+
        "function text(e){return ((e&&e.innerText)||'').replace(/\\s+/g,' ').trim();}"+
        "function scan(d,depth){if(depth>3||out.length>=150||d.querySelector('input[type=password]'))return;"+
        "var rows=d.querySelectorAll('tr,[role=row],.jqx-grid-row');for(var i=0;i<rows.length&&out.length<150;i++){var row=rows[i];if(!visible(row))continue;var cells=Array.from(row.querySelectorAll(':scope > td,:scope > [role=gridcell],:scope > [role=cell],:scope > .jqx-grid-cell'));if(cells.length<2)continue;"+
        "var detail=-1;for(var j=0;j<cells.length;j++){if(/^(详情|查看详情|查看|阅读)$/.test(text(cells[j]))){detail=j;break;}}if(detail<0)continue;"+
        "var scope=row.closest('table,[role=grid],.jqx-grid'),heads=scope?Array.from(scope.querySelectorAll('th,[role=columnheader]')):[],titleIndex=-1;for(var j=0;j<heads.length;j++)if(/^(标题|公告标题|通知标题|公告名称|通知名称)$/.test(text(heads[j]))) {titleIndex=j;break;}"+
        "var c=cells[titleIndex>=0&&titleIndex<cells.length?titleIndex:(detail===0?1:0)];if(!visible(c))continue;var title=c.getAttribute('title')||((c.querySelector('[title]')||{}).title)||text(c);if(title.length<4||title.length>260)continue;"+
        "var a=cells[detail].querySelector('a[href]')||c.querySelector('a[href]');var href=a&&/^https?:\\/\\//i.test(a.href)&&a.getAttribute('href')!=='#'?a.href:'';"+
        "out.push({title:title,url:href,row:true});}"+
        "var xs=d.querySelectorAll('a[href]');for(var k=0;k<xs.length&&out.length<150;k++){var x=xs[k],t=text(x);if(!visible(x)||t.length<6||t.length>260||!/^https?:\\/\\//i.test(x.href))continue;out.push({title:t,url:x.href,row:false});}"+
        "var fs=d.querySelectorAll('iframe,frame');for(var z=0;z<fs.length&&out.length<150;z++){if(!visible(fs[z]))continue;try{if(fs[z].contentDocument)scan(fs[z].contentDocument,depth+1);}catch(e){}}}scan(document,0);return JSON.stringify(out);})()";
    static String key(String title){try{byte[] d=MessageDigest.getInstance("SHA-256").digest(title.replaceAll("\\s+"," ").trim().getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder("row:");for(byte x:d)b.append(String.format(Locale.ROOT,"%02x",x&255));return b.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    static List<Collector.Item> parse(JSONArray rows,String current){
        List<Collector.Item> result=new ArrayList<>();Set<String> seen=new HashSet<>(),titles=new HashSet<>();String list=AuthUrl.normalize(current);
        if(list==null||AuthUrl.hasSessionParameter(list))return result;
        for(int i=0;i<rows.length()&&result.size()<70;i++){
            JSONObject o=rows.optJSONObject(i);if(o==null)continue;String title=o.optString("title").trim().replaceAll("\\s+"," ");boolean row=o.optBoolean("row");
            if(title.length()<4||title.length()>260||title.matches(".*(登录|退出|个人中心|密码|成绩查询|考试查询|选课系统).*")||(!row&&!title.matches(".*(通知|公告|公示|通告|安排|报名|考试|放假|选课|补考|重修|调课|成绩|教学).*")))continue;
            String url=AuthUrl.normalize(o.optString("url"));boolean direct=url!=null&&AuthUrl.sameOrigin(list,url)&&!AuthUrl.hasSessionParameter(url)&&!url.equals(list);
            if(!direct&&!row)continue;String id=direct?url:key(title);
            if(!seen.add(id)||!titles.add(title))continue;
            result.add(new Collector.Item(title,direct?url:list,direct?"":"教务系统列表公告 · 进入列表后点击详情",0,false,direct?"":id));
        }return result;
    }
}
