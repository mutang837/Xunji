package com.mutang.xunxi;

public final class ArticleReaderTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);}
    public static void main(String[] args){
        String base="https://example.cn/news/123.html";
        ArticleReader.Result r=ArticleReader.extract("<html><head><title>公告</title></head><body><nav>网站首页导航</nav><div class='article-content'><h2>事项</h2><p>请在规定的时间内完成报名。</p><p><img data-src='../image.png' src='placeholder.png' onerror='alert(1)'></p><a href='../../files/notice.pdf'>下载附件</a><script>alert(1)</script><a href='javascript:alert(1)'>不可信链接</a></div><footer>页脚</footer></body></html>",base);
        check(r!=null&&r.html.contains("请在规定的时间内完成报名"),"Article body extracted");
        check(r.html.contains("https://example.cn/image.png")&&r.html.contains("https://example.cn/../files/notice.pdf")==false&&r.html.contains("notice.pdf"),"Images and attachment links retained");
        check(!r.html.contains("<script")&&!r.html.contains("onerror")&&!r.html.contains("javascript:")&&!r.html.contains("网站首页导航")&&!r.html.contains("页脚"),"Active content and navigation stripped");
        check(ArticleReader.extract("<article class='shuoshuo-item'><div class='shuoshuo-content'><p>今天很好。</p></div></article>",base)!=null,"Short moments are readable");
        check(ArticleReader.extract("<div class='entry-content'><img src='/photo.jpg'></div>",base)!=null,"Image-only moments are readable");
        check(ArticleReader.extract("<html><body><form><input type='password'></form></body></html>",base)==null,"Login page falls back to original");
        check(ArticleReader.extract("<div class='entry-content'><p>公开文章正文</p></div><form><input id='captcha'></form>",base)!=null,"Public article remains readable when comments require CAPTCHA");
        check(ArticleReader.extract("<form><div class='v_news_content'><p>实际通知文字</p></div></form>",base)!=null,"Article wrapped in a CMS form stays readable");
        check(ArticleReader.extract("<div class='v_news_content'><script>embedPdf()</script></div><div><img src='/unrelated-logo.png'></div>",base)==null,"Script-only attachment falls back instead of displaying unrelated page content");
        ArticleReader.Result school=ArticleReader.extract("<div id='vsb_content'><p><script>var vsb_pdf_image_data = [\"/files/page1.jpg\",\"/files/page2.jpg\"];showVsbpdfIframe(\"/files/notice.pdf\",\"100%\")</script></p></div>",base);
        check(school!=null&&school.html.contains("page1.jpg")&&school.html.contains("page2.jpg")&&school.html.contains("notice.pdf")&&!school.html.contains("<script"),"School PDF preview pages retained without executing scripts");
        ArticleReader.Result embed=ArticleReader.extract("<div class='entry-content'><iframe src='/files/test.pdf'></iframe></div>",base);
        check(embed!=null&&embed.html.contains("test.pdf"),"Embedded PDF becomes a readable attachment link");
        String cms="<html><head><title>学生通知</title></head><body><nav><a href='/unrelated.pdf'>网站导航资料</a></nav><div class='main'><div id='vsb_content'><p>请查看附件中的详细名单。</p><p>&nbsp;</p><p><span>　&nbsp;</span><br></p><p style='text-align: right'>教务部门</p><p align='right'>2026年9月20日</p></div><ul class='attachlist'><li>附件【<a href='/system/_content/download.jsp?urltype=news.DownloadAttachUrl&amp;wbfileid=123'>公示名单.pdf</a>】已下载 37 次</li><li>附件【<a href='/files/form.docx' title='报名表.docx'>报名表.docx</a>】</li></ul></div><footer><a href='/old.pdf'>旧文件</a></footer></body></html>";
        ArticleReader.Result attachment=ArticleReader.extract(cms,base,"学生通知");
        check(attachment!=null&&attachment.attachments.size()==2,"CMS attachments outside the body retained, unrelated navigation and footer excluded");
        check(attachment.attachments.get(0).pdf()&&attachment.attachments.get(0).url.contains("download.jsp")&&attachment.attachment(attachment.attachments.get(0).url)!=null,"PDF named link with dynamic download endpoint recognized");
        check(attachment.html.contains("reader-files")&&attachment.html.contains("公示名单.pdf")&&attachment.html.contains("报名表.docx"),"Readable file cards appended to article");
        check(!attachment.html.contains("&nbsp;")&&!attachment.html.contains("<p></p>")&&attachment.html.contains("reader-right"),"Empty CMS paragraphs removed and right-aligned signatures preserved");
        ArticleReader.Result table=ArticleReader.extract("<div class='article-content'><p>考试时间</p><table><tr><td>时间</td><td>日期</td></tr></table></div>",base);
        check(table!=null&&table.html.contains("reader-table")&&table.html.contains("<table"),"Tables preserve structure inside a responsive scroll wrapper");
        check(ArticleReader.sameOrigin("https://example.cn/news/123.html","https://example.cn/system/_content/download.jsp")&&!ArticleReader.sameOrigin("https://example.cn/a","https://other.cn/b")&&!ArticleReader.sameOrigin("https://example.cn/a","http://example.cn/a"),"Attachment referer is scoped to the original origin");
        check(ArticleReader.extract("<nav><a href='/'>返回</a></nav><body>请启用脚本</body>",base)==null,"Unrecognized page falls back to original");
        String heading="全国普通高中学业水平合格性考试考前提示";
        check(ArticleReader.extract("<html><head><title>"+heading+"</title></head><body><main><img src='/header-logo.png'><p>"+heading+"</p><p>"+heading+"</p><iframe src='/actual-body.html'></iframe></main></body></html>",base,heading)==null,"Duplicated title and masthead are not mistaken for article body");
        check(ArticleReader.extract("<div class='article-content'><h1>"+heading+"</h1><p>请在规定日期参加考试，提前准备准考证和身份证。</p></div>",base,heading)!=null,"Article title plus genuine text stays readable");
        check(!ArticleReader.webUrl("file:///etc/passwd")&&!ArticleReader.webUrl("javascript:alert(1)")&&!ArticleReader.webUrl("https://name@example.cn"),"Reader rejects non-web URLs");
        if(args.length>0){try{String html=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[0])),java.nio.charset.StandardCharsets.UTF_8);ArticleReader.Result fixture=ArticleReader.extract(html,base);check(fixture!=null&&fixture.html.contains(".pdf")&&fixture.html.contains(".jpg"),"Real captured CMS notice has document image and PDF link");}catch(java.io.IOException e){throw new AssertionError(e);}}
    }
}
