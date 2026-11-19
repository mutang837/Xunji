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
        check(ArticleReader.extract("<nav><a href='/'>返回</a></nav><body>请启用脚本</body>",base)==null,"Unrecognized page falls back to original");
        check(!ArticleReader.webUrl("file:///etc/passwd")&&!ArticleReader.webUrl("javascript:alert(1)")&&!ArticleReader.webUrl("https://name@example.cn"),"Reader rejects non-web URLs");
        if(args.length>0){try{String html=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[0])),java.nio.charset.StandardCharsets.UTF_8);ArticleReader.Result fixture=ArticleReader.extract(html,base);check(fixture!=null&&fixture.html.contains(".pdf")&&fixture.html.contains(".jpg"),"Real captured CMS notice has document image and PDF link");}catch(java.io.IOException e){throw new AssertionError(e);}}
    }
}
