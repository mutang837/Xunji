package com.mutang.xunxi;
import org.json.*;
import java.util.*;
public final class AuthNoticeParserTest {
    static void check(boolean yes,String name){if(!yes)throw new AssertionError(name);System.out.println("PASS "+name);}
    public static void main(String[] args)throws Exception{
        if(args.length>0&&"--capture".equals(args[0])){System.out.print(AuthNoticeParser.CAPTURE);return;}
        if(args.length>1&&"--locate".equals(args[0])){System.out.print(AuthNoticeParser.locate(args[1]));return;}
        String list="http://portal.test:1009/jwapp#/notice";
        JSONArray rows=new JSONArray().put(new JSONObject().put("title","学生选课手册新").put("row",true).put("url",""))
            .put(new JSONObject().put("title","关于大学生安全教育的通知").put("row",true).put("url","javascript:;"))
            .put(new JSONObject().put("title","学生选课手册新").put("row",true).put("url",""))
            .put(new JSONObject().put("title","考试通知").put("row",false).put("url","http://portal.test:1009/jwapp#/notice/23"))
            .put(new JSONObject().put("title","退出登录").put("row",true).put("url",""))
            .put(new JSONObject().put("title","公告通知").put("row",false).put("url","http://other.test/notice"));
        List<Collector.Item> items=AuthNoticeParser.parse(rows,list);
        check(items.size()==3,"Visible table rows and direct notice links are collected; navigation and duplicates rejected");
        check(items.get(0).url.equals(list)&&items.get(1).url.equals(list)&&!items.get(0).noticeKey.equals(items.get(1).noticeKey),"Multiple titles sharing one list address have independent stable identities");
        check(items.get(2).url.endsWith("#/notice/23")&&items.get(2).noticeKey.isEmpty(),"Real detail URL is preserved");
        check(AuthNoticeParser.parse(rows,list+"?token=example").isEmpty(),"Session parameter is not saved as a list URL");
        JSONArray dateRows=new JSONArray().put(new JSONObject().put("title","2023-12-03 09:51:31").put("row",true))
            .put(new JSONObject().put("title","2026年秋季学期网上选课通知").put("row",true).put("date","2026-11-19 09:51:31"));
        List<Collector.Item> corrected=AuthNoticeParser.parse(dateRows,list);
        check(corrected.size()==1&&corrected.get(0).date>0&&corrected.get(0).title.startsWith("2026年"),"Date-only rows rejected; actual year-prefixed title and publication date preserved");
    }
}
