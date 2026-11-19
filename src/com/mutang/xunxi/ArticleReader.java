package com.mutang.xunxi;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Safelist;
import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extract visible article text, links and images. Never execute scripts or bypass access controls. */
final class ArticleReader {
    static final String CONTENT=".shuoshuo-content, .entry-content, .post-content, .article-content, .article-body, .article-entry, .news-content, .news_content, .newsdetail, .newsDetail, .content-detail, .detail-content, .v_news_content, #vsb_content, #vsb_content_2, .TRS_Editor, .trs_editor, #zoom, #articleContent, #article-content, [itemprop=articleBody], .markdown-body, #article-container";
    static final String JUNK="script, style, noscript, iframe, object, embed, input, button, textarea, select, nav, aside, header, footer, .breadcrumb, .pagination, .comments, #comments, .comment, .comment-list, .related-posts, .post-navigation, .share, .social-share, .advertisement, .ads, .ad, [hidden]";
    static final class Attachment {
        final String title,url,type;
        Attachment(String title,String url,String type){this.title=title;this.url=url;this.type=type;}
        boolean pdf(){return "pdf".equals(type);}
    }
    static final class Result {
        final String title,html,url;final List<Attachment> attachments;
        Result(String t,String h,String u,List<Attachment> files){title=t;html=h;url=u;attachments=Collections.unmodifiableList(new ArrayList<>(files));}
        Attachment attachment(String link){for(Attachment a:attachments){if(a.url.equals(link))return a;try{if(URI.create(a.url).normalize().toASCIIString().equals(URI.create(link).normalize().toASCIIString()))return a;}catch(Exception ignored){}}return null;}
    }
    static final Pattern FILE_EXT=Pattern.compile("(?i)\\.(pdf|docx?|xlsx?|pptx?|ofd|zip|rar|7z|csv|txt)(?=$|[\\s?#&）)】\\]、，。:：])");
    static String fileType(String value){if(value==null)return "";Matcher m=FILE_EXT.matcher(value);return m.find()?m.group(1).toLowerCase(Locale.ROOT):"";}
    static boolean webUrl(String url){try{URI u=URI.create(url);return u.getHost()!=null&&u.getUserInfo()==null&&("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme()));}catch(Exception e){return false;}}
    static boolean sameOrigin(String first,String second){try{URI a=URI.create(first),b=URI.create(second);return webUrl(first)&&webUrl(second)&&a.getScheme().equalsIgnoreCase(b.getScheme())&&a.getHost().equalsIgnoreCase(b.getHost())&&(a.getPort()<0?("https".equalsIgnoreCase(a.getScheme())?443:80):a.getPort())==(b.getPort()<0?("https".equalsIgnoreCase(b.getScheme())?443:80):b.getPort());}catch(Exception e){return false;}}
    static boolean pdfUrl(String url){return webUrl(url)&&url.matches("(?i).*\\.pdf(?:[?#].*)?$");}
    static boolean directFileUrl(String url){return webUrl(url)&&url.matches("(?i).*\\.(pdf|docx?|xlsx?|pptx?|ofd|zip|rar|7z|csv|txt)(?:[?#].*)?$");}
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
    static boolean nearAttachment(Element a){
        for(Element e=a;e!=null&&e!=a.ownerDocument().body();e=e.parent()){
            String attrs=(e.id()+" "+e.className()).toLowerCase(Locale.ROOT);
            if(attrs.matches(".*(?:attach|download|附件).*"))return true;
            String text=e.text();
            if(text.length()<450&&(text.startsWith("附件")||text.contains("附件下载")||text.contains("已下载")||text.contains("下载次数")))return true;
            if(e==a.parent()&&e.parent()!=null&&e.parent()!=a.ownerDocument().body()&&e.parent().text().length()>600)break;
        }return false;
    }
    static boolean within(Element root,Element child){for(Element e=child;e!=null;e=e.parent())if(root==e)return true;return false;}
    static List<Attachment> findAttachments(Document doc,Element picked){
        LinkedHashMap<String,Attachment> files=new LinkedHashMap<>();
        for(Element a:doc.select("a[href]")){
            String href=a.absUrl("href");if(!webUrl(href)||href.length()>4096)continue;
            String label=a.text().trim(),hint=a.attr("title").trim(),download=a.attr("download").trim();
            String name=!fileType(hint).isEmpty()?hint:!fileType(label).isEmpty()?label:!download.isEmpty()?download:!label.isEmpty()?label:hint;
            String type=fileType(name);if(type.isEmpty())type=fileType(href);
            boolean endpoint=href.toLowerCase(Locale.ROOT).matches(".*(?:downloadattachurl|/download\\.(?:jsp|do|php|aspx)|/download/|/attachments?/).*");
            boolean explicit=nearAttachment(a)||a.hasAttr("download")||endpoint;
            if(!within(picked,a)&&!explicit)continue;
            if(type.isEmpty()&&!explicit)continue;
            if(type.isEmpty()&&!endpoint&&!a.hasAttr("download"))continue;
            if(name.isEmpty()||name.equals("下载")||name.equals("附件")||name.equals("下载附件")){
                try{String p=URI.create(href).getPath();String f=p.substring(p.lastIndexOf('/')+1);if(!fileType(f).isEmpty())name=f;}catch(Exception ignored){}
            }
            if(name.isEmpty())name="附件文件";
            name=name.replaceFirst("^附件[：: ]*[【\\[]?\\s*","").replaceFirst("[】\\]]?\\s*已下载\\s*\\d+次.*$","").trim();
            if(name.isEmpty())name="附件文件";if(name.length()>220)name=name.substring(0,220);
            Attachment old=files.get(href);if(old==null||old.type.isEmpty()&&!type.isEmpty()||old.title.startsWith("在应用内查看")&&!name.startsWith("在应用内查看"))files.put(href,new Attachment(name,href,type));
            if(files.size()>=40)break;
        }
        return new ArrayList<>(files.values());
    }
    static void normalizeLayout(Element content){
        content.select("[data-reader-align]").removeAttr("data-reader-align");
        for(Element p:content.select("p,div,h1,h2,h3,h4,h5,h6")){
            String align=p.attr("align").trim().toLowerCase(Locale.ROOT),style=p.attr("style").toLowerCase(Locale.ROOT);
            if("right".equals(align)||style.matches(".*text-align\\s*:\\s*right.*"))p.attr("data-reader-align","right");
            else if("center".equals(align)||style.matches(".*text-align\\s*:\\s*center.*"))p.attr("data-reader-align","center");
        }
        ArrayList<Element> blocks=new ArrayList<>(content.select("p,div,section"));Collections.reverse(blocks);
        for(Element e:blocks){if(e==content)continue;if(e.text().replaceAll("[\\s\\u00a0\\u3000\\u200b]","").isEmpty()&&e.select("img,a[href],table,hr,pre,video,audio").isEmpty())e.remove();}
        for(Element e:content.select("p,div")){for(TextNode node:e.textNodes()){String v=node.getWholeText();node.text(v.replaceAll("^[\\u00a0\\u3000]+","").replaceAll("[\\u00a0\\u3000]+$","").replaceAll("[\\u00a0\\u3000]{2,}"," "));}
            for(Element br:e.select("br + br"))br.remove();
        }
    }
    static String renderAttachments(List<Attachment> files){if(files.isEmpty())return "";Element section=new Element("section").addClass("reader-files");section.appendElement("h2").text("附件 ("+files.size()+")");for(Attachment file:files){Element a=section.appendElement("a").addClass("reader-file").attr("href",file.url);a.appendElement("span").addClass("file-type").text(file.type.isEmpty()?"文件":file.type.toUpperCase(Locale.ROOT));Element copy=a.appendElement("span").addClass("file-copy");copy.appendElement("strong").text(file.title);copy.appendElement("small").text(file.pdf()?"应用内预览 PDF":"保存附件");a.appendElement("span").addClass("file-arrow").text("›");}return section.outerHtml();}
    static Result read(String url)throws Exception{
        return read(url,"");
    }
    static Result read(String url,String expectedTitle)throws Exception{
        if(!webUrl(url)||url.matches("(?i).*\\.(pdf|docx?|xlsx?|pptx?|zip|rar)(?:[?#].*)?$"))throw new IllegalArgumentException("附件或非网页内容");
        org.jsoup.Connection.Response r=Jsoup.connect(url).userAgent("Xunji/0.4 Reader").timeout(18000).maxBodySize(4*1024*1024).followRedirects(true).ignoreContentType(true).execute();
        String type=r.contentType();if(type!=null&&!type.toLowerCase(Locale.ROOT).contains("html")&&!type.toLowerCase(Locale.ROOT).contains("text/plain"))throw new IllegalArgumentException("非 HTML 内容");
        Result result=extract(Collector.decode(r),r.url().toExternalForm(),expectedTitle);if(result==null)throw new IllegalStateException("无法可靠识别正文");return result;
    }
    static Result extract(String html,String base){
        return extract(html,base,"");
    }
    static Result extract(String html,String base,String expectedTitle){
        if(!webUrl(base))return null;
        Document doc=Jsoup.parse(html.startsWith("\uFEFF")?html.substring(1):html,base);
        // A comment form may also contain a CAPTCHA. Only gate the generic fallback;
        // a separately visible article body is safe to extract without interacting with the form.
        boolean gated=!doc.select("input[type=password], .g-recaptcha, .h-captcha, #captcha, #challenge-form").isEmpty();
        String title=doc.select("meta[property=og:title]").attr("content").trim();if(title.isEmpty()){Element h=doc.selectFirst("h1");title=h==null?doc.title():h.text();}
        preserveAttachments(doc,base);
        doc.select(JUNK).remove();
        // Logos, mastheads and duplicated titles are not article content.
        for(Element img:doc.select("img")){String attrs=(img.attr("src")+" "+img.attr("alt")+" "+img.attr("title")+" "+img.className()).toLowerCase(Locale.ROOT);if(attrs.matches(".*(?:logo|masthead|site-banner|header-banner|网站标志|网站标识).*"))img.remove();}
        Element picked=best(doc,CONTENT,true);if(picked==null&&(gated||!doc.select(CONTENT).isEmpty()))return null;if(picked==null)picked=best(doc,"article, main, [role=main], #content",false);if(picked==null)picked=best(doc,"body",false);
        if(picked==null)return null;
        List<Attachment> files=findAttachments(doc,picked);
        Element content=picked.clone();content.select(JUNK).remove();
        normalizeLayout(content);
        for(Element image:content.select("img")){
            String src="";for(String attr:new String[]{"data-original","data-src","data-lazy-src","src"}){String candidate=image.absUrl(attr);if(webUrl(candidate)){src=candidate;break;}}
            if(src.isEmpty()){image.remove();continue;}image.attr("src",src);image.removeAttr("srcset");image.removeAttr("style");image.removeAttr("width");image.removeAttr("height");
        }
        for(Element link:content.select("a[href]")){String href=link.absUrl("href");if(webUrl(href))link.attr("href",href);else link.removeAttr("href");}
        Safelist safe=Safelist.relaxed().addTags("article","section","figure","figcaption","h1","h2","h3","h4","h5","h6","hr","mark","sub","sup","del","s","details","summary").addAttributes("img","alt","title").removeAttributes(":all","style","id","class","align").addAttributes("p","data-reader-align").addAttributes("div","data-reader-align").addProtocols("a","href","http","https").addProtocols("img","src","http","https");
        String clean=Jsoup.clean(content.html(),base,safe,new Document.OutputSettings().prettyPrint(false));
        Document checked=Jsoup.parseBodyFragment(clean);if(checked.body().text().trim().isEmpty()&&checked.select("img").isEmpty())return null;
        String body=compact(checked.body().text()),t=compact(title),expected=compact(expectedTitle);
        String stripped=body;if(!t.isEmpty()&&t.length()>=6)stripped=stripped.replace(t,"");if(!expected.isEmpty()&&expected.length()>=6)stripped=stripped.replace(expected,"");
        if((t.length()>=6||expected.length()>=6)&&stripped.length()<8&&(body.contains(t)&&!t.isEmpty()||body.contains(expected)&&!expected.isEmpty())&&checked.select("a[href]").isEmpty()&&checked.select("img").size()<=1)return null;
        for(Element el:checked.select("[data-reader-align]")){String alignment=el.attr("data-reader-align");if(alignment.equals("right")||alignment.equals("center"))el.addClass("reader-"+alignment);el.removeAttr("data-reader-align");}
        for(Element table:checked.select("table"))table.wrap("<div class='reader-table'></div>");
        return new Result(title.trim(),checked.body().html()+renderAttachments(files),base,files);
    }
    static String compact(String text){return text==null?"":text.replaceAll("[\\s\\p{Punct}，。！？：；、（）【】《》]+","");}
    static Element best(Document doc,String selectors,boolean explicit){Element picked=null;double best=-1;for(Element e:doc.select(selectors)){
        String text=e.text().trim();int len=text.length(),links=e.select("a[href]").text().length(),images=e.select("img").size();
        if(explicit){if(len<3&&images==0)continue;if(len>20&&links>len*.85&&images==0)continue;}
        else{if(len<70&&images==0)continue;if(e.select("p,blockquote,pre,table").isEmpty()&&images==0)continue;if(len>0&&links>len*.50)continue;if(e.select("article").size()>2)continue;}
        double score=Math.min(len,20000)+Math.min(images,15)*75.0-links*.65+e.select("p").size()*18.0;
        if(score>best){best=score;picked=e;}
    }return picked;}
}
