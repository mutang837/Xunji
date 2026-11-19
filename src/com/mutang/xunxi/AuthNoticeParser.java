package com.mutang.xunxi;

import org.json.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Reads visible table cells and actual links. Never invokes a site's private APIs. */
final class AuthNoticeParser {
    static final String ROWS =
        "function visible(e){if(!e)return false;var r=e.getBoundingClientRect(),s=e.ownerDocument.defaultView.getComputedStyle(e);return !!(r.width&&r.height&&s.visibility!=='hidden'&&s.display!=='none');}"+
        "function text(e){return ((e&&e.innerText)||'').replace(/\\s+/g,' ').trim();}"+
        "function cellText(c){return (c.getAttribute('title')||((c.querySelector('[title]')||{}).title)||text(c)).replace(/\\s+/g,' ').trim();}"+
        "function good(t){return t.length>=4&&t.length<=260&&!/^(?:已读|未读|查看详情|详情|考务管理|调停补课|教务处|系统消息|发布单位|通知类型|全部|\\d+)$/.test(t)&&!/^\\d{4}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?(?:[ T]\\d{1,2}:\\d{2}(?::\\d{2})?)?$/.test(t);}"+
        "function scan(d,depth){if(depth>3||out.length>=150||d.querySelector('input[type=password]'))return;"+
        "var rows=d.querySelectorAll('tr,[role=row],.jqx-grid-row,.datagrid-row,.k-table-row');for(var i=0;i<rows.length&&out.length<150;i++){var row=rows[i];if(!visible(row))continue;var cells=Array.from(row.querySelectorAll(':scope > td,:scope > [role=gridcell],:scope > [role=cell],:scope > .jqx-grid-cell,:scope > .datagrid-cell'));if(cells.length<2)continue;"+
        "var detail=-1;for(var j=0;j<cells.length;j++){if(/^(详情|查看详情|查看|阅读)$/.test(text(cells[j]))){detail=j;break;}}if(detail<0)continue;"+
        "var scope=row.closest('table,[role=grid],.jqx-grid,.datagrid'),heads=scope?Array.from(scope.querySelectorAll('th,[role=columnheader],.jqx-grid-column-header')):[],titleHeads=heads.filter(function(h){return /^(标题|公告标题|通知标题|公告名称|通知名称|消息标题|信息标题|消息内容|主题)$/.test(text(h));}),c=null;"+
        "for(var j=0;j<titleHeads.length&&!c;j++){var h=titleHeads[j],hr=h.getBoundingClientRect(),best=-1;for(var m=0;m<cells.length;m++){var cell=cells[m],cr=cell.getBoundingClientRect(),col=h.getAttribute('aria-colindex'),field=h.getAttribute('data-field')||h.getAttribute('datafield'),score=0;if(col&&col===cell.getAttribute('aria-colindex'))score+=1000;if(field&&field===(cell.getAttribute('data-field')||cell.getAttribute('datafield')))score+=1000;if(h.cellIndex>=0&&h.cellIndex===cell.cellIndex)score+=100;var overlap=Math.max(0,Math.min(hr.right,cr.right)-Math.max(hr.left,cr.left));if(hr.width&&cr.width)score+=100*overlap/Math.min(hr.width,cr.width);if(m!==detail&&good(cellText(cell))&&score>0&&score>best){best=score;c=cell;}}}"+
        "if(!c){var best=-1;for(var m=0;m<cells.length;m++){if(m===detail||!visible(cells[m]))continue;var v=cellText(cells[m]);if(!good(v))continue;var score=(cells[m].getAttribute('title')?30:0)+(m===detail+1?18:0)+Math.min(v.length,50);if(score>best){c=cells[m];best=score;}}}if(!visible(c))continue;var title=cellText(c);if(!good(title))continue;"+
        "var a=cells[detail].querySelector('a[href]')||c.querySelector('a[href]');var href=a&&/^https?:\\/\\//i.test(a.href)&&a.getAttribute('href')!=='#'?a.href:'';"+
        "var dt='';for(var q=0;q<cells.length;q++){var val=cellText(cells[q]);if(/^\\d{4}[-/.年]\\d{1,2}[-/.月]\\d{1,2}/.test(val)&&!good(val)){dt=val;break;}}out.push({title:title,url:href,row:true,page:d.location.href,date:dt,element:row,detail:cells[detail]});}"+
        "var xs=d.querySelectorAll('a[href]');for(var k=0;k<xs.length&&out.length<150;k++){var x=xs[k],t=text(x);if(!visible(x)||!good(t)||!/^https?:\\/\\//i.test(x.href))continue;out.push({title:t,url:x.href,row:false,page:d.location.href});}"+
        "var fs=d.querySelectorAll('iframe,frame');for(var z=0;z<fs.length&&out.length<150;z++){if(!visible(fs[z]))continue;try{if(fs[z].contentDocument)scan(fs[z].contentDocument,depth+1);}catch(e){}}}";
    static final String CAPTURE="(function(){var out=[];"+ROWS+"scan(document,0);return JSON.stringify(out.map(function(r){return {title:r.title,url:r.url,row:r.row,page:r.page,date:r.date};}));})()";
    static String locate(String title){return "(function(){var out=[];"+ROWS+"scan(document,0);var match=out.filter(function(r){return r.row&&r.title==="+JSONObject.quote(title.replaceAll("\\s+"," ").trim())+";});if(match.length!==1)return match.length===0?'not-found':'ambiguous';var r=match[0];r.element.style.outline='3px solid #24745f';r.detail.style.outline='3px solid #24745f';r.detail.scrollIntoView({block:'center',inline:'nearest'});return 'found';})()";}
    static String key(String title){try{byte[] d=MessageDigest.getInstance("SHA-256").digest(title.replaceAll("\\s+"," ").trim().getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder("row:");for(byte x:d)b.append(String.format(Locale.ROOT,"%02x",x&255));return b.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    static boolean dateOnly(String title){return title!=null&&title.trim().matches("(?i)\\d{4}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?(?:[ T]\\d{1,2}:\\d{2}(?::\\d{2})?)?");}
    static List<Collector.Item> parse(JSONArray rows,String current){return parse(rows,current,current);}
    static List<Collector.Item> parse(JSONArray rows,String current,String fallback){
        List<Collector.Item> result=new ArrayList<>();Set<String> seen=new HashSet<>(),titles=new HashSet<>();String list=AuthUrl.normalize(current);
        if(list==null)return result;String safe=AuthUrl.stableList(list);if(safe==null)safe=AuthUrl.normalize(fallback);if(safe==null||AuthUrl.hasSessionParameter(safe))return result;
        for(int i=0;i<rows.length()&&result.size()<70;i++){
            JSONObject o=rows.optJSONObject(i);if(o==null)continue;String title=o.optString("title").trim().replaceAll("\\s+"," ");boolean row=o.optBoolean("row");
            if(title.length()<4||title.length()>260||dateOnly(title)||title.matches(".*(登录|退出|个人中心|密码|成绩查询|考试查询|选课系统).*")||(!row&&!title.matches(".*(通知|公告|公示|通告|安排|报名|考试|放假|选课|补考|重修|调课|成绩|教学).*")))continue;
            String url=AuthUrl.normalize(o.optString("url"));boolean direct=url!=null&&AuthUrl.sameOrigin(list,url)&&!AuthUrl.hasSessionParameter(url)&&!url.equals(list);
            if(!direct&&!row)continue;String id=direct?url:key(title);
            if(!seen.add(id)||!titles.add(title))continue;
            result.add(new Collector.Item(title,direct?url:safe,direct?"":"列表公告 · 点击后定位对应详情",Collector.parseDate(o.optString("date")),false,direct?"":id));
        }return result;
    }
}
