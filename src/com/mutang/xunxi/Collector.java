package com.mutang.xunxi;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.parser.Parser;
import java.net.URI;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;

/** Public announcement, blog, micro-post and RSS monitor. No login or script execution. */
public final class Collector {
    static final int MAX_POSTS = 70;
    static final Pattern DATE = Pattern.compile("(20\\d{2})(?:[-./年]|\\s+)(\\d{1,2})[-./月](\\d{1,2})日?");
    static final Pattern DATE_TIME = Pattern.compile("(20\\d{2})[-./年](\\d{1,2})[-./月](\\d{1,2})日?\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");
    static final Pattern ARTICLE = Pattern.compile("(?i)(?:/info/|/content/|/article/|/detail/|/notice/|/news/|/ART/|/art/|/t20\\d{6}_|/20\\d{2}/|/\\d{4,}\\.(?:html?|shtml|aspx)|/\\d{4,}/|_[0-9]{5,}|/[a-z]\\d{5,}\\.html)");
    static final Pattern NAV = Pattern.compile("^(首页|网站首页|学校首页|更多[>»…]*|了解更多|查看详情|上一页|下一页|末页|尾页|返回|登录|注册|联系我们|网站地图|通知公告|招生信息|新闻动态|信息公开|网站导航|列表|新闻|公告|全部|[0-9]+|[.]{3})$");
    public static final class Item {
        public final String title, url, summary, noticeKey; public final long date;public final boolean shortPost;
        Item(String title,String url,String summary,long date){this(title,url,summary,date,false);}
        Item(String title,String url,String summary,long date,boolean shortPost){this(title,url,summary,date,shortPost,"");}
        Item(String title,String url,String summary,long date,boolean shortPost,String noticeKey){this.title=title;this.url=url;this.summary=summary;this.date=date;this.shortPost=shortPost;this.noticeKey=noticeKey;}
    }
    public static final class Result {
        public final String name; public final List<Item> items; public final List<String> icons;
        Result(String n,List<Item> i,List<String> icons){name=n;items=i;this.icons=icons;}
    }
    public static String normalize(String input){
        try {
            String s=input.trim();if(s.isEmpty())return null;
            if(!s.matches("(?i)^https?://.*")){if(s.matches("(?i)^[a-z][a-z0-9+.-]*://.*"))return null;s="https://"+s;}
            URI u=URI.create(s).normalize();
            if(u.getHost()==null || u.getUserInfo()!=null || !("http".equalsIgnoreCase(u.getScheme()) || "https".equalsIgnoreCase(u.getScheme())))return null;
            return URI.create(u.getScheme().toLowerCase(Locale.ROOT)+"://"+u.getHost().toLowerCase(Locale.ROOT)+(u.getPort()==-1?"":":"+u.getPort())+(u.getRawPath().isEmpty()?"/":u.getRawPath())+(u.getRawQuery()==null?"":"?"+u.getRawQuery())).toASCIIString();
        } catch(Exception ex){return null;}
    }
    static String fetch(String url) throws Exception {
        return decode(Jsoup.connect(url).userAgent("Xunji/0.1 PersonalFeed (+public announcement monitoring)")
            .timeout(18000).maxBodySize(2*1024*1024).followRedirects(true).ignoreContentType(true).execute());
    }
    static String decode(org.jsoup.Connection.Response response)throws Exception{
        byte[] bytes=response.bodyAsBytes();
        Document doc=Jsoup.parse(new java.io.ByteArrayInputStream(bytes),response.charset(),response.url().toExternalForm());
        return new String(bytes,doc.charset());
    }
    public static Result collect(String url) throws Exception {
        org.jsoup.Connection.Response response=Jsoup.connect(url).userAgent("Xunji/0.1 PersonalFeed (+public announcement monitoring)")
            .timeout(18000).maxBodySize(2*1024*1024).followRedirects(true).ignoreContentType(true).execute();
        String base=response.url().toExternalForm(); String body=decode(response);
        Result result=parse(body,base);
        if(!looksLikeFeed(body)){
            List<Item> shorts=new ArrayList<>();for(Item i:result.items)if(i.shortPost)shorts.add(i);
            if(shortArchive(base)&&!shorts.isEmpty())return new Result(result.name,shorts,result.icons);
            Document doc=Jsoup.parse(body,base);
            for(Element a:doc.select("link[rel=alternate][type*=rss], link[rel=alternate][type*=atom]")){
                String feed=normalize(a.absUrl("href")); if(feed==null)continue;
                try{Result r=parse(fetch(feed),feed); if(!r.items.isEmpty()){
                    LinkedHashSet<String> icons=new LinkedHashSet<>(result.icons);icons.addAll(r.icons);
                    LinkedHashMap<String,Item> merged=new LinkedHashMap<>();for(Item i:shorts)merged.put(i.url,i);for(Item i:r.items)if(merged.size()<MAX_POSTS)merged.putIfAbsent(i.url,i);
                    return new Result(result.name.isEmpty()?r.name:result.name,new ArrayList<>(merged.values()),new ArrayList<>(icons));
                }}catch(Exception ignored){}
                break;
            }
        }
        return result;
    }
    static boolean looksLikeFeed(String body){return Pattern.compile("(?is)<(?:rss|feed|rdf:RDF)(?:\\s|>)").matcher(body.substring(0,Math.min(body.length(),1500))).find();}
    static boolean shortArchive(String url){try{String path=URI.create(url).getPath();return Pattern.compile("(?i)(?:^|/)(?:shuoshuo|moments?|microblog|statuses|status|说说|动态)(?:/|$)").matcher(path).find();}catch(Exception e){return false;}}
    public static Result parse(String body,String base){
        if(body.startsWith("\uFEFF"))body=body.substring(1);
        LinkedHashMap<String,Item> items=new LinkedHashMap<>();
        if(looksLikeFeed(body)){
            Document doc=Jsoup.parse(body,base,Parser.xmlParser());
            String name=doc.select("channel > title, feed > title").text();
            for(Element e:doc.select("item, entry")){
                String title=e.select("title").first()==null?"":e.select("title").first().text();
                String url="";
                for(Element link:e.select("link")){
                    if(link.hasAttr("href")&&(link.attr("rel").isEmpty()||link.attr("rel").equals("alternate"))){url=link.absUrl("href");break;}
                    if(!link.text().isEmpty()){url=link.text();break;}
                }
                if(url.isEmpty())url=e.select("guid").text();
                if(url.trim().isEmpty())continue;
                try{url=URI.create(base).resolve(url).toString();}catch(Exception ignored){continue;}
                String summary=e.select("description, summary, content").text();
                String date=e.select("pubDate, published, updated, date").text();
                put(items,title,repairPlaceholder(url,base),Jsoup.parse(summary).text(),parseDate(date));
                if(items.size()>=MAX_POSTS)break;
            }
            LinkedHashSet<String> icons=new LinkedHashSet<>();
            for(Element e:doc.select("channel > image > url, feed > icon, feed > logo"))addIcon(icons,base,e.text());
            addIcon(icons,base,"/favicon.ico");
            return new Result(name,new ArrayList<>(items.values()),new ArrayList<>(icons));
        }
        Document doc=Jsoup.parse(body,base);
        String name=siteName(doc);
        LinkedHashSet<String> icons=new LinkedHashSet<>();
        for(Element e:doc.select("link[rel][href]"))for(String rel:e.attr("rel").toLowerCase(Locale.ROOT).split("\\s+"))if(rel.equals("icon")||rel.equals("apple-touch-icon")||rel.equals("apple-touch-icon-precomposed")){addIcon(icons,base,e.absUrl("href"));break;}
        addIcon(icons,base,"/favicon.ico");
        for(Element e:doc.select(".logo img[src], .hd-logo img[src], #logo img[src], img[src*=logo]")){String logo=e.absUrl("src");if(sameSite(host(base),host(logo))){addIcon(icons,base,logo);break;}}
        doc.select("nav, header, footer, script, style, .nav, .navbar, .footer, #footer, #header, .breadcrumb, .pagination, .menu, .topnav").remove();
        String baseHost=host(base); String canonical=normalize(base);
        collectShorts(doc,base,items);
        for(Element a:doc.select("a[href]")){
            String title=a.attr("title").trim(); if(title.length()<5)title=a.text().trim();
            String url=normalize(a.absUrl("href"));
            if(url==null || items.containsKey(url) || url.equals(canonical) || !sameSite(baseHost,host(url)) || title.length()<7 || title.length()>260 || NAV.matcher(title).matches())continue;
            if(url.matches("(?i).*\\.(?:pdf|docx?|xlsx?|zip|rar|png|jpg|jpeg|gif|css|js)(?:[?].*)?$"))continue;
            long date=0; boolean list=false;
            Element parent=a.parent();
            for(int level=0;parent!=null&&level<3;level++,parent=parent.parent()){
                String tag=parent.tagName(); if(tag.equals("body")||tag.equals("html"))break;
                if(tag.equals("li")||tag.equals("tr")||tag.equals("article")||parent.className().matches("(?i).*(item|list|article|news|notice).*")&&parent.childrenSize()<12)list=true;
                if(parent.select("a[href]").size()<=3 && parent.text().length()<400){date=parseDate(parent.text());if(date!=0)break;}
            }
            String path=URI.create(url).getPath();
            boolean looksArticle=ARTICLE.matcher(path).find() || (title.length()>=12&&list&&path.matches("(?i).*/[^/]+\\.(?:html?|shtml|aspx|jsp)$")&&!path.matches("(?i).*/(?:index|list|default|more|main|home)[^/]*\\.(?:html?|shtml|aspx|jsp)$"));
            if(date==0 && !looksArticle && !(list && title.length()>=12))continue;
            put(items,title,url,"",date);
            if(items.size()>=MAX_POSTS)break;
        }
        return new Result(name,new ArrayList<>(items.values()),new ArrayList<>(icons));
    }
    static void collectShorts(Document doc,String base,Map<String,Item> out){
        for(Element card:doc.select("article.shuoshuo-item, .shuoshuo-item, article.format-status, article.format-aside, article.status, article.moment, article.microblog, article[data-post-type=status], .moment-item, .microblog-item")){
            Element link=card.selectFirst("a.shuoshuo-content-link[href], a.status-link[href], a.moment-link[href], a.microblog-link[href], a[rel=bookmark][href], .entry-title a[href], .post-title a[href], a.permalink[href], h2 a[href], h3 a[href]");
            if(link==null)continue;String url=normalize(link.absUrl("href"));if(url==null||url.equals(normalize(base))||!sameSite(host(base),host(url)))continue;
            Element content=card.selectFirst(".shuoshuo-content, .entry-content, .status-content, .moment-content, .microblog-content, .post-content, .entry-summary");
            String summary=content!=null?content.text().trim():link.text().trim();boolean media=content!=null&&!content.select("img, video, audio, picture").isEmpty();
            Element heading=card.selectFirst(".shuoshuo-title, .entry-title, .post-title, .status-title, .moment-title, h2, h3");String title=heading==null?"":heading.text().trim();
            Element time=card.selectFirst("time, .shuoshuo-date, .post-date, .entry-date, .status-date, .moment-date, .published, [itemprop=datePublished]");long date=0;if(time!=null){String raw=time.attr("datetime");if(raw.isEmpty())raw=time.attr("content");if(raw.isEmpty())raw=time.text();date=parseDate(raw);}
            if(date==0)continue; // Avoid treating menu links and undated profile cards as posts.
            if(summary.isEmpty()&&media)summary="图片或媒体动态，点击查看原文";
            if(summary.isEmpty()&&title.isEmpty())continue;
            if(title.isEmpty())title=summary.length()>24?summary.substring(0,24)+"…":summary;
            String display="说说 · "+title;if(display.length()>120)display=display.substring(0,120)+"…";
            if(summary.length()>360)summary=summary.substring(0,360)+"…";
            out.putIfAbsent(url,new Item(display,url,summary,date,true));if(out.size()>=MAX_POSTS)break;
        }
    }
    static String siteName(Document doc){
        for(String selector:new String[]{"meta[property=og:site_name]","meta[name=application-name]","meta[name=apple-mobile-web-app-title]","meta[name=SiteName]"}){
            Element e=doc.selectFirst(selector);if(e!=null&&!e.attr("content").trim().isEmpty())return e.attr("content").trim();
        }
        String title=doc.title().trim();String[] parts=title.split("\\s*[-—|｜_－]\\s*",2);
        if(parts.length==2&&parts[0].matches("通知公告|公告通知|通知|公告|考试动态|新闻动态|工作动态|工作通知|招生信息|首页|网站首页")&&!parts[1].isEmpty())return parts[1].trim();
        return title;
    }
    static void addIcon(Set<String> icons,String base,String ref){
        if(ref==null||ref.trim().isEmpty())return;
        try{String link=normalize(URI.create(base).resolve(ref.trim()).toString());if(link!=null)icons.add(link);}catch(Exception ignored){}
    }
    static void put(Map<String,Item> out,String title,String url,String summary,long date){
        String u=normalize(url),t=title.replaceAll("\\s+"," ").trim();
        if(u==null||t.length()<3||NAV.matcher(t).matches())return;
        if(summary.length()>180)summary=summary.substring(0,180)+"…";
        if(t.length()>260)t=t.substring(0,260);
        out.putIfAbsent(u,new Item(t,u,summary,date));
    }
    static String host(String url){try{return URI.create(url).getHost().toLowerCase(Locale.ROOT);}catch(Exception e){return "";}}
    static boolean placeholderHost(String h){return h.matches("(?i)(?:www\\.)?example\\.(com|org|net)");}
    // Some static blogs ship an Atom/RSS feed with the template's example.com origin.
    // Fix only those reserved placeholder domains, never rewrite a legitimate external link.
    static String repairPlaceholder(String article,String source){try{URI a=URI.create(article),s=URI.create(source);String h=host(article),origin=host(source);if(!placeholderHost(h)||placeholderHost(origin)||origin.isEmpty())return article;return URI.create(s.getScheme()+"://"+s.getRawAuthority()+(a.getRawPath().isEmpty()?"/":a.getRawPath())+(a.getRawQuery()==null?"":"?"+a.getRawQuery())).toASCIIString();}catch(Exception e){return article;}}
    static boolean sameSite(String a,String b){return a.equals(b)||a.equals("www."+b)||b.equals("www."+a);}
    static long parseDate(String s){
        if(s==null||s.isEmpty())return 0;
        try{return ZonedDateTime.parse(s,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();}catch(Exception ignored){}
        try{return Instant.parse(s).toEpochMilli();}catch(Exception ignored){}
        try{return OffsetDateTime.parse(s).toInstant().toEpochMilli();}catch(Exception ignored){}
        Matcher full=DATE_TIME.matcher(s);if(full.find())try{return LocalDateTime.of(Integer.parseInt(full.group(1)),Integer.parseInt(full.group(2)),Integer.parseInt(full.group(3)),Integer.parseInt(full.group(4)),Integer.parseInt(full.group(5)),full.group(6)==null?0:Integer.parseInt(full.group(6))).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();}catch(Exception ignored){}
        Matcher m=DATE.matcher(s);if(m.find())try{return LocalDate.of(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3))).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();}catch(Exception ignored){}
        return 0;
    }
}
