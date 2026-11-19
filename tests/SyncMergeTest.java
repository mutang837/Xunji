package com.mutang.xunxi;

import org.json.*;
public final class SyncMergeTest {
    static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);System.out.println("PASS "+m);}
    static JSONObject source(String id,String url,long added)throws Exception{return new JSONObject().put("id",id).put("url",url).put("name","示例订阅").put("addedAt",added).put("initialized",true);}
    static JSONObject post(String id,String source,String url,boolean read)throws Exception{return new JSONObject().put("id",id).put("source",source).put("url",url).put("title","测试文章").put("read",read).put("found",123);}
    static JSONObject state(JSONObject source,JSONObject post)throws Exception{return new JSONObject().put("sources",new JSONArray().put(source)).put("posts",post==null?new JSONArray():new JSONArray().put(post));}
    public static void main(String[] args)throws Exception{
        String blog="https://blog.test/",article="https://blog.test/one";JSONObject a=state(source("a",blog,10),post("p-a","a",article,false)),b=state(source("b",blog,10),post("p-b","b",article,true));JSONObject merged=SyncMerge.merge(a,b);
        check(merged.getJSONArray("sources").length()==1&&merged.getJSONArray("posts").length()==1,"Duplicate subscriptions and articles merge by URL");
        check(merged.getJSONArray("posts").getJSONObject(0).getBoolean("read"),"Read state merges across devices");
        JSONObject deleted=new JSONObject().put("tombstones",new JSONObject().put(blog,20));JSONObject gone=SyncMerge.merge(a,deleted);check(gone.getJSONArray("sources").length()==0&&gone.getJSONArray("posts").length()==0,"Deletion sync prevents old subscriptions returning");
        JSONObject readded=SyncMerge.merge(deleted,state(source("new",blog,30),null));check(readded.getJSONArray("sources").length()==1,"Intentional re-subscribe after deletion works");
        JSONObject safe=SyncMerge.merge(new JSONObject(),a.put("password","do-not-sync").put("token","secret"));check(!safe.has("password")&&!safe.has("token"),"Credentials and unrelated preferences are excluded");
        check(SyncMerge.unpack(SyncMerge.envelope(merged).toString()).getJSONArray("posts").length()==1,"Versioned sync file round-trip");
    }
}
