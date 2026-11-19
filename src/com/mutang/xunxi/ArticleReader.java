package com.mutang.xunxi;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extract visible article text, links and images. Never execute scripts or bypass access controls. */
final class ArticleReader {
    static final String CONTENT=".shuoshuo-content, .entry-content, .post-content, .article-content, .article-body, .article-entry, .news-content, .news_content, .newsdetail, .newsDetail, .content-detail, .detail-content, .v_news_content, #vsb_content, #vsb_content_2, .TRS_Editor, .trs_editor, #zoom, #articleContent, #article-content, [itemprop=articleBody], .markdown-body, #article-container";
    static final String JUNK="script, style, noscript, iframe, object, embed, input, button, textarea, select, nav, aside, header, footer, .breadcrumb, .pagination, .comments, #comments, .comment, .comment-list, .related-posts, .post-navigation, .share, .social-share, .advertisement, .ads, .ad, [hidden]";
    static final class Result {final String title,html,url;Result(String t,String h,String u){title=t;html=h;url=u;}}
    static boolean webUrl(String url){try{URI u=URI.create(url);return u.getHost()!=null&&u.getUserInfo()==null&&("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme()));}catch(Exception e){return false;}}
    static boolean pdfUrl(String url){return webUrl(url)&&url.matches("(?i).*\\.pdf(?:[?#].*)?$");}
    static String resolve(String base,String path){try{String u=URI.create(base).resolve(path).toASCIIString();return webUrl(u)?u:"";}catch(Exception e){return "";}}
    static void appendPdf(Element el,String url){if(pdfUrl(url)&&!el.select("a[href]").stream().anyMatch(a->url.equals(a.absUrl("href"))))el.appendElement("p").appendElement("a").attr("href",url).text("在应用内查看 PDF 文件");}
    // Preserve document previews using literal values only; never execute page scripts.
    static void preserveAttachments(Document doc,String base){
        for(Element el:doc.select("iframe[src], embed[src], object[data]")){
            String u=el.absUrl(el.hasAttr("src")?"src":"data");if(pdfUrl(u))appendPdf(el.parent(),u);
        }
        Pattern images=Pattern.compile("vsb_pdf_image_data\\s*=\\s*\\[([^\\]]{0,50000})\\]");
        Pattern quote=Pattern.compile("[\"']([^\"'\\r\\n]{1,2048})[\"']");
        Pattern pdf=Pattern.compile("showVsbpdfIframe\\s*\\(\\s*[\"']([^\"']+)[\"']");
        for(Element script:doc.select(CONTENT+"").select("script")){
            Element parent=script.parent();String body=script.data();Matcher pages=images.matcher(body);int count=0;
            if(pages.find()){Matcher values=quote.matcher(pages.group(1));while(values.find()&&count<50){String u=resolve(base,values.group(1));if(u.matches("(?i).*\\.(jpg|jpeg|png|webp)(?:[?#].*)?$")&&Collector.host(base).equals(Collector.host(u))){parent.appendElement("p").appendElement("img").attr("src",u).attr("alt","文件第 "+(++count)+" 页");}}}
            Matcher file=pdf.matcher(body);if(file.find())appendPdf(parent,resolve(base,file.group(1)));
        }
    }
    static Result read(String url)throws Exception{
        if(!webUrl(url)||url.matches("(?i).*\\.(pdf|docx?|xlsx?|pptx?|zip|rar)(?:[?#].*)?$"))throw new IllegalArgumentException("附件或非网页内容");
        org.jsoup.Connection.Response r=Jsoup.connect(url).userAgent("Xunji/0.4 Reader").timeout(18000).maxBodySize(4*1024*1024).followRedirects(true).ignoreContentType(true).execute();
        String type=r.contentType();if(type!=null&&!type.toLowerCase(Locale.ROOT).contains("html")&&!type.toLowerCase(Locale.ROOT).contains("text/plain"))throw new IllegalArgumentException("非 HTML 内容");
        Result result=extract(Collector.decode(r),r.url().toExternalForm());if(result==null)throw new IllegalStateException("无法可靠识别正文");return result;
    }
    static Result extract(String html,String base){
        if(!webUrl(base))return null;
        Document doc=Jsoup.parse(html.startsWith("\uFEFF")?html.substring(1):html,base);
        // A comment form may also contain a CAPTCHA. Only gate the generic fallback;
        // a separately visible article body is safe to extract without interacting with the form.
        boolean gated=!doc.select("input[type=password], .g-recaptcha, .h-captcha, #captcha, #challenge-form").isEmpty();
        String title=doc.select("meta[property=og:title]").attr("content").trim();if(title.isEmpty()){Element h=doc.selectFirst("h1");title=h==null?doc.title():h.text();}
        preserveAttachments(doc,base);
        doc.select(JUNK).remove();
        Element picked=best(doc,CONTENT,true);if(picked==null&&(gated||!doc.select(CONTENT).isEmpty()))return null;if(picked==null)picked=best(doc,"article, main, [role=main], #content",false);if(picked==null)picked=best(doc,"body",false);
        if(picked==null)return null;
        Element content=picked.clone();content.select(JUNK).remove();
        for(Element image:content.select("img")){
            String src="";for(String attr:new String[]{"data-original","data-src","data-lazy-src","src"}){String candidate=image.absUrl(attr);if(webUrl(candidate)){src=candidate;break;}}
            if(src.isEmpty()){image.remove();continue;}image.attr("src",src);image.removeAttr("srcset");image.removeAttr("style");image.removeAttr("width");image.removeAttr("height");
        }
        for(Element link:content.select("a[href]")){String href=link.absUrl("href");if(webUrl(href))link.attr("href",href);else link.removeAttr("href");}
        Safelist safe=Safelist.relaxed().addTags("article","section","figure","figcaption","h1","h2","h3","h4","h5","h6","hr","mark","sub","sup","del","s","details","summary").addAttributes("img","alt","title").removeAttributes(":all","style","id","class").addProtocols("a","href","http","https").addProtocols("img","src","http","https");
        String clean=Jsoup.clean(content.html(),base,safe,new Document.OutputSettings().prettyPrint(false));
        Document checked=Jsoup.parseBodyFragment(clean);if(checked.body().text().trim().isEmpty()&&checked.select("img").isEmpty())return null;
        return new Result(title.trim(),clean,base);
    }
    static Element best(Document doc,String selectors,boolean explicit){Element picked=null;double best=-1;for(Element e:doc.select(selectors)){
        String text=e.text().trim();int len=text.length(),links=e.select("a[href]").text().length(),images=e.select("img").size();
        if(explicit){if(len<3&&images==0)continue;if(len>20&&links>len*.85&&images==0)continue;}
        else{if(len<70&&images==0)continue;if(e.select("p,blockquote,pre,table").isEmpty()&&images==0)continue;if(len>0&&links>len*.50)continue;if(e.select("article").size()>2)continue;}
        double score=Math.min(len,20000)+Math.min(images,15)*75.0-links*.65+e.select("p").size()*18.0;
        if(score>best){best=score;picked=e;}
    }return picked;}
}
