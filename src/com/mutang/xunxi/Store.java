package com.mutang.xunxi;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

final class Store {
    static final String PREF="xunji.local";
    static final Object LOCK=new Object();
    static final class Source {
        String id,url,name,error; long checked; boolean initialized,autoName; int count;
        Source(JSONObject j){id=j.optString("id");url=j.optString("url");name=j.optString("name");error=j.optString("error");checked=j.optLong("checked");initialized=j.optBoolean("initialized");count=j.optInt("count");autoName=j.optBoolean("autoName",name.equals(Collector.host(url)));}
        JSONObject json(){JSONObject j=new JSONObject();try{j.put("id",id).put("url",url).put("name",name).put("error",error).put("checked",checked).put("initialized",initialized).put("count",count);}catch(JSONException ignored){}return j;}
    }
    static final class Post {
        String id,source,sourceName,title,url,summary;long date,found;boolean read;
        Post(JSONObject j){id=j.optString("id");source=j.optString("source");sourceName=j.optString("sourceName");title=j.optString("title");url=j.optString("url");summary=j.optString("summary");date=j.optLong("date");found=j.optLong("found");read=j.optBoolean("read");}
        long order(){return date>0?date:found;}
    }
    final SharedPreferences prefs;
    Store(Context c){prefs=c.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    JSONObject load(){try{return new JSONObject(prefs.getString("state","{}"));}catch(Exception e){return new JSONObject();}}
    JSONArray array(JSONObject o,String key){JSONArray a=o.optJSONArray(key);return a==null?new JSONArray():a;}
    void save(JSONObject o){prefs.edit().putString("state",o.toString()).putLong("revision",System.nanoTime()).commit();}
    long revision(){return prefs.getLong("revision",0);}
    List<Source> sources(){synchronized(LOCK){JSONArray a=array(load(),"sources");List<Source> result=new ArrayList<>();for(int i=0;i<a.length();i++)result.add(new Source(a.optJSONObject(i)));return result;}}
    List<Post> posts(){synchronized(LOCK){JSONArray a=array(load(),"posts");List<Post> result=new ArrayList<>();for(int i=0;i<a.length();i++)result.add(new Post(a.optJSONObject(i)));result.sort((x,y)->Long.compare(y.order(),x.order()));return result;}}
    String add(String name,String url){synchronized(LOCK){JSONObject o=load();JSONArray a=array(o,"sources");for(int i=0;i<a.length();i++)if(a.optJSONObject(i).optString("url").equals(url))return "该网站已订阅";if(a.length()>=30)return "最多添加 30 个网站";try{addEntry(a,name,url);o.put("sources",a);save(o);return null;}catch(Exception e){return "保存失败";}}}
    void addEntry(JSONArray a,String name,String url)throws JSONException{JSONObject s=new JSONObject();boolean automatic=name.trim().isEmpty();s.put("id",UUID.randomUUID().toString()).put("url",url).put("name",automatic?Collector.host(url):name).put("autoName",automatic).put("error","");a.put(s);}
    void delete(String id){synchronized(LOCK){JSONObject o=load();for(String k:new String[]{"sources","posts"}){JSONArray a=array(o,k),b=new JSONArray();for(int i=0;i<a.length();i++){JSONObject j=a.optJSONObject(i);if(!j.optString(k.equals("sources")?"id":"source").equals(id))b.put(j);}try{o.put(k,b);}catch(Exception ignored){}}JSONObject seen=o.optJSONObject("seen");if(seen!=null)seen.remove(id);save(o);}}
    List<Post> merge(Source src,Collector.Result result,String error){synchronized(LOCK){JSONObject o=load();JSONArray sources=array(o,"sources"),posts=array(o,"posts");List<Post> fresh=new ArrayList<>();try{
        for(int i=0;i<sources.length();i++){
            JSONObject s=sources.optJSONObject(i);if(!s.optString("id").equals(src.id))continue;
            boolean initialized=s.optBoolean("initialized");s.put("checked",System.currentTimeMillis()).put("error",error==null?"":error);
            if(result!=null){
                if(new Source(s).autoName&&!result.name.trim().isEmpty()){
                    String n=result.name.trim().replaceAll("\\s+"," ");if(n.length()>80)n=n.substring(0,80);s.put("name",n).put("autoName",true);
                    for(int j=0;j<posts.length();j++){JSONObject p=posts.optJSONObject(j);if(src.id.equals(p.optString("source")))p.put("sourceName",n);}
                }
            }
            if(result!=null&&!result.items.isEmpty()){
                JSONObject seenAll=o.optJSONObject("seen");if(seenAll==null)seenAll=new JSONObject();
                JSONArray old=seenAll.optJSONArray(src.id);if(old==null)old=new JSONArray();
                LinkedHashSet<String> seen=new LinkedHashSet<>();for(int j=0;j<old.length();j++)seen.add(old.optString(j));
                // Existing displayed articles also prevent duplicates after old seen entries expire.
                for(int j=0;j<posts.length();j++){JSONObject p=posts.optJSONObject(j);if(src.id.equals(p.optString("source")))seen.add(p.optString("url"));}
                long now=System.currentTimeMillis();int offset=0;
                for(Collector.Item item:result.items){if(!seen.add(item.url))continue;
                    JSONObject p=new JSONObject();p.put("id",UUID.randomUUID().toString()).put("source",src.id).put("sourceName",s.optString("name")).put("title",item.title).put("url",item.url).put("summary",item.summary).put("date",item.date).put("found",now-offset++).put("read",false);posts.put(p);if(initialized)fresh.add(new Post(p));}
                JSONArray updated=new JSONArray();List<String> list=new ArrayList<>(seen);for(int j=Math.max(0,list.size()-3000);j<list.size();j++)updated.put(list.get(j));seenAll.put(src.id,updated);o.put("seen",seenAll);
                s.put("initialized",true).put("count",result.items.size());
            }
            o.put("sources",sources);
            if(posts.length()>1600){List<JSONObject> sorted=new ArrayList<>();for(int j=0;j<posts.length();j++)sorted.add(posts.optJSONObject(j));sorted.sort((a,b)->Long.compare(Math.max(b.optLong("date"),b.optLong("found")),Math.max(a.optLong("date"),a.optLong("found"))));posts=new JSONArray();for(int j=0;j<1600;j++)posts.put(sorted.get(j));}
            o.put("posts",posts);save(o);break;
        }
    }catch(Exception ignored){}return fresh;}}
    void read(String id){synchronized(LOCK){JSONObject o=load();JSONArray a=array(o,"posts");try{for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(id==null||id.equals(p.optString("id")))p.put("read",true);}o.put("posts",a);save(o);}catch(Exception ignored){}}}
    void readSource(String source){synchronized(LOCK){JSONObject o=load();JSONArray a=array(o,"posts");try{for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(source==null||source.equals(p.optString("source")))p.put("read",true);}o.put("posts",a);save(o);}catch(Exception ignored){}}}
    int interval(){return prefs.getInt("interval",60);}
    void setInterval(int n){prefs.edit().putInt("interval",n).apply();}
    boolean notifyEnabled(){return prefs.getBoolean("notifications",true);}
    void setNotify(boolean b){prefs.edit().putBoolean("notifications",b).apply();}
    long lastRefresh(){return prefs.getLong("lastRefresh",0);}
    void setLastRefresh(){prefs.edit().putLong("lastRefresh",System.currentTimeMillis()).apply();}
}
