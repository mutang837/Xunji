package com.mutang.xunxi;

public class CollectorTest {
    static void check(boolean test,String label){if(!test)throw new AssertionError(label);System.out.println("PASS "+label);}
    public static void main(String[] args)throws Exception{
        Collector.Result html=Collector.parse("<html><head><title>某高校研究生院</title></head><body><nav><a href='/index.html'>学校相关重要页面链接</a></nav><ul><li><a href='../info/1001/2345.htm'>硕士研究生招生简章及考试科目</a><span>2026-11-19</span></li><li><a href='../info/1001/2345.htm#top'>重复的公告链接</a></li><li><a href='../info/1001/2346.htm' title='关于研究生复试工作安排的通知'>关于复试...</a> 2026年11月18日</li><li><a href='https://other.test/notice/1234'>无关网站的长标题测试链接</a></li><li><a href='javascript:void(0)'>这是一条无效链接的文字</a></li><li><a href='list_2.htm'>下一页</a></li></ul></body></html>","https://grad.example.edu.cn/notice/list.htm");
        check(html.items.size()==2,"HTML notices, duplicate removal and navigation exclusion");
        check(html.items.get(0).url.equals("https://grad.example.edu.cn/info/1001/2345.htm"),"Relative links resolved");
        check(html.items.get(0).date>0&&html.items.get(1).date>0,"Chinese and ISO date extraction");
        check(html.items.get(1).title.equals("关于研究生复试工作安排的通知"),"Full title attribute retained");
        Collector.Result rss=Collector.parse("<?xml version='1.0'?><rss version='2.0'><channel><title>财政公告</title><item><title>初级会计资格考试报名通知</title><link>https://finance.example.cn/123.html</link><description><![CDATA[<p>报名开始</p>]]></description><pubDate>Thu, 19 Nov 2026 09:00:00 +0800</pubDate></item><item><title>不完整条目</title></item></channel></rss>","https://finance.example.cn/rss.xml");
        check(rss.items.size()==1,"RSS parsing with invalid entry ignored");check(rss.items.get(0).summary.equals("报名开始")&&rss.items.get(0).date>0,"RSS content and timestamp");
        Collector.Result atom=Collector.parse("<feed xmlns='http://www.w3.org/2005/Atom'><title>信息</title><entry><title>新的招生信息</title><link href='post/1'/><updated>2026-11-19T08:00:00+08:00</updated></entry></feed>","https://example.cn/feed");check(atom.items.size()==1&&atom.items.get(0).url.equals("https://example.cn/post/1"),"Atom parsing");
        check("https://example.cn/a%2Fb?q=a%26b".equals(Collector.normalize("https://example.cn/a%2Fb?q=a%26b#x")),"URL normalization preserves encoded path and query");
        check(Collector.normalize("file:///etc/passwd")==null&&Collector.normalize("https://name@host.cn/")==null,"Unsupported URLs rejected");
        check(Collector.parse("<html><body><a href='/home'>首页</a></body></html>","https://example.cn/").items.isEmpty(),"Empty list does not invent posts");
        Collector.Result metadata=Collector.parse("<html><head><title>通知公告-某大学</title><meta property='og:site_name' content='某大学官网'><link rel='shortcut icon' href='../site.ico'><link rel='apple-touch-icon' href='/logo.png'></head></html>","https://example.cn/notice/list.html");
        check(metadata.name.equals("某大学官网"),"Automatic naming prefers original site metadata");
        check(metadata.icons.equals(java.util.Arrays.asList("https://example.cn/site.ico","https://example.cn/logo.png","https://example.cn/favicon.ico")),"Site icon discovery and fallback");
        check(metadata.items.isEmpty(),"Metadata is available even without announcements");
        check(Collector.parse("<title>考试动态 - 中国教育考试网</title>","https://example.cn/").name.equals("中国教育考试网"),"Section prefix removed from original name");
        Collector.Result bom=Collector.parse("\uFEFF<html><head><title>通知公告-某大学</title></head><body><a class='logo'><img src='/images/logo.png'></a></body></html>","https://example.cn/index/tzgg.htm");
        check(bom.name.equals("某大学")&&bom.icons.contains("https://example.cn/images/logo.png"),"UTF-8 BOM and website logo fallback");
        Collector.Result feedMeta=Collector.parse("<rss><channel><title>RSS公告</title><image><url>https://example.cn/feed.png</url></image></channel></rss>","https://example.cn/rss.xml");
        check(feedMeta.name.equals("RSS公告")&&feedMeta.icons.get(0).equals("https://example.cn/feed.png"),"RSS icon and title");
        com.sun.net.httpserver.HttpServer server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/notices",exchange->{byte[] bytes="<html><head><meta charset='GBK'><title>财政厅</title></head><body><ul><li><a href='/info/1001/1234.html'>会计专业技术资格考试报名通知</a><span>2026-11-19</span></li></ul></body></html>".getBytes(java.nio.charset.Charset.forName("GBK"));exchange.getResponseHeaders().set("Content-Type","text/html");exchange.sendResponseHeaders(200,bytes.length);try(java.io.OutputStream out=exchange.getResponseBody()){out.write(bytes);}});
        server.createContext("/portal",exchange->{byte[] bytes="<html><head><link rel='alternate' type='application/rss+xml' href='/rss.xml'></head><body>网站首页</body></html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);try(java.io.OutputStream out=exchange.getResponseBody()){out.write(bytes);}});
        server.createContext("/rss.xml",exchange->{byte[] bytes="<rss version='2.0'><channel><title>财政公告</title><item><title>新发布的会计考试信息</title><link>/info/1001/1235.html</link></item></channel></rss>".getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/rss+xml; charset=utf-8");exchange.sendResponseHeaders(200,bytes.length);try(java.io.OutputStream out=exchange.getResponseBody()){out.write(bytes);}});
        server.start();try{String base="http://127.0.0.1:"+server.getAddress().getPort();Collector.Result live=Collector.collect(base+"/notices");check(live.items.size()==1&&live.items.get(0).title.equals("会计专业技术资格考试报名通知"),"HTTP request and GBK meta charset detection");Collector.Result discovery=Collector.collect(base+"/portal");check(discovery.items.size()==1&&discovery.items.get(0).title.equals("新发布的会计考试信息"),"Automatic RSS discovery and HTTP feed fetch");}finally{server.stop(0);}
    }
}
