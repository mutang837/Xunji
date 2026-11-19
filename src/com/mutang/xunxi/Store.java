package com.mutang.xunxi;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

final class Store {
    static final String PREF="xunji.local";
    static final Object LOCK=new Object();
    static final class Source {
        String id,url,name,error,authPage; long checked; boolean initialized,autoName,authenticated; int count;
        Source(JSONObject j){id=j.optString("id");url=j.optString("url");name=j.optString("name");error=j.optString("error");checked=j.optLong("checked");initialized=j.optBoolean("initialized");count=j.optInt("count");autoName=j.optBoolean("autoName",name.equals(Collector.host(url)));authenticated=j.optBoolean("authenticated");authPage=j.optString("authPage");}
        JSONObject json(){JSONObject j=new JSONObject();try{j.put("id",id).put("url",url).put("name",name).put("error",error).put("checked",checked).put("initialized",initialized).put("count",count);}catch(JSONException ignored){}return j;}
    }
    static final class Post {
        String id,source,sourceName,title,url,summary,noticeKey;long date,found;boolean read,shortPost;
        Post(JSONObject j){id=j.optString("id");source=j.optString("source");sourceName=j.optString("sourceName");title=j.optString("title");url=j.optString("url");summary=j.optString("summary");date=j.optLong("date");found=j.optLong("found");read=j.optBoolean("read");shortPost=j.optBoolean("shortPost");noticeKey=j.optString("noticeKey");}
        long order(){return date>0?date:found;}
    }
    final SharedPreferences prefs;
    Store(Context c){prefs=c.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    JSONObject load(){try{return new JSONObject(prefs.getString("state","{}"));}catch(Exception e){return new JSONObject();}}
    JSONArray array(JSONObject o,String key){JSONArray a=o.optJSONArray(key);return a==null?new JSONArray():a;}
    void save(JSONObject o){prefs.edit().putString("state",o.toString()).putLong("revision",System.nanoTime()).commit();}
    long revision(){return prefs.getLong("revision",0);}
    List<Source> sources(){synchronized(LOCK){JSONArray a=array(load(),"sources");List<Source> result=new ArrayList<>();for(int i=0;i<a.length();i++)result.add(new Source(a.optJSONObject(i)));return result;}}
    List<Post> posts(){synchronized(LOCK){JSONArray a=array(load(),"posts");List<Post> result=new ArrayList<>();for(int i=0;i<a.length();i++){Post p=new Post(a.optJSONObject(i));if(!p.noticeKey.isEmpty()&&AuthNoticeParser.dateOnly(p.title))continue;result.add(p);}result.sort((x,y)->Long.compare(y.order(),x.order()));return result;}}
    String add(String name,String url){return add(name,url,false);}
    String add(String name,String url,boolean authenticated){synchronized(LOCK){JSONObject o=load();JSONArray a=array(o,"sources");for(int i=0;i<a.length();i++)if(a.optJSONObject(i).optString("url").equals(url))return "该网站已订阅";if(a.length()>=30)return "最多添加 30 个网站";try{addEntry(a,name,url);a.optJSONObject(a.length()-1).put("authenticated",authenticated);o.put("sources",a);save(o);return null;}catch(Exception e){return "保存失败";}}}
    void addEntry(JSONArray a,String name,String url)throws JSONException{JSONObject s=new JSONObject();boolean automatic=name.trim().isEmpty();s.put("id",UUID.randomUUID().toString()).put("url",url).put("name",automatic?Collector.host(url):name).put("autoName",automatic).put("error","").put("addedAt",System.currentTimeMillis());a.put(s);}
    Set<String> approvedOrigins(String id){synchronized(LOCK){Set<String> result=new LinkedHashSet<>();JSONArray sources=array(load(),"sources");for(int i=0;i<sources.length();i++){JSONObject s=sources.optJSONObject(i);if(s==null||!s.optBoolean("authenticated")||!id.equals(s.optString("id")))continue;JSONArray origins=s.optJSONArray("approvedOrigins");if(origins!=null)for(int j=0;j<origins.length();j++){String u=AuthUrl.origin(origins.optString(j));if(u!=null)result.add(u);}break;}return result;}}
    boolean approveOrigin(String id,String raw,boolean approved){String origin=AuthUrl.origin(raw);if(origin==null)return false;synchronized(LOCK){JSONObject o=load();JSONArray sources=array(o,"sources");for(int i=0;i<sources.length();i++){JSONObject s=sources.optJSONObject(i);if(s==null||!s.optBoolean("authenticated")||!id.equals(s.optString("id")))continue;Set<String> origins=approvedOrigins(id);if(approved){if(!origins.contains(origin)&&origins.size()>=8)return false;origins.add(origin);}else origins.remove(origin);try{s.put("approvedOrigins",new JSONArray(origins));o.put("sources",sources);save(o);return true;}catch(Exception e){return false;}}return false;}}
    boolean setAuthPage(String id,String page){synchronized(LOCK){JSONObject o=load();JSONArray sources=array(o,"sources");for(int i=0;i<sources.length();i++){JSONObject s=sources.optJSONObject(i);if(s==null||!s.optBoolean("authenticated")||!id.equals(s.optString("id")))continue;String url=page.isEmpty()?"":AuthUrl.stableList(page);if(url==null||(!url.isEmpty()&&!AuthUrl.trustedOrigin(s.optString("url"),approvedOrigins(id),url)))return false;try{s.put("authPage",url).put("checked",0);o.put("sources",sources);save(o);return true;}catch(Exception e){return false;}}return false;}}
    void delete(String id){synchronized(LOCK){JSONObject o=load();try{JSONArray a=array(o,"sources");JSONObject tomb=o.optJSONObject("tombstones");if(tomb==null)tomb=new JSONObject();for(int i=0;i<a.length();i++){JSONObject s=a.optJSONObject(i);if(s!=null&&!s.optBoolean("authenticated")&&id.equals(s.optString("id")))tomb.put(s.optString("url"),System.currentTimeMillis());}o.put("tombstones",tomb);}catch(Exception ignored){}for(String k:new String[]{"sources","posts"}){JSONArray a=array(o,k),b=new JSONArray();for(int i=0;i<a.length();i++){JSONObject j=a.optJSONObject(i);if(!j.optString(k.equals("sources")?"id":"source").equals(id))b.put(j);}try{o.put(k,b);}catch(Exception ignored){}}JSONObject seen=o.optJSONObject("seen");if(seen!=null)seen.remove(id);save(o);}}
    void repairStoredLinks(){synchronized(LOCK){JSONObject o=load();Map<String,String> origins=new HashMap<>();JSONArray ss=array(o,"sources"),pp=array(o,"posts");for(int i=0;i<ss.length();i++){JSONObject s=ss.optJSONObject(i);if(s!=null)origins.put(s.optString("id"),s.optString("url"));}boolean changed=false;Map<String,JSONObject> unique=new LinkedHashMap<>();try{for(int i=0;i<pp.length();i++){JSONObject p=pp.optJSONObject(i);if(p==null)continue;String src=p.optString("source"),url=p.optString("url"),fixed=Collector.repairPlaceholder(url,origins.get(src));if(!fixed.equals(url)){p.put("url",fixed);changed=true;}String key=src+"\n"+p.optString("noticeKey","");if(p.optString("noticeKey").isEmpty())key=src+"\n"+fixed;JSONObject old=unique.get(key);if(old==null)unique.put(key,p);else{old.put("read",old.optBoolean("read")||p.optBoolean("read"));changed=true;}}JSONObject seen=o.optJSONObject("seen");if(seen!=null)for(String id:origins.keySet()){JSONArray a=seen.optJSONArray(id);if(a==null)continue;LinkedHashSet<String> fixed=new LinkedHashSet<>();for(int j=0;j<a.length();j++){String u=a.optString(j),v=Collector.repairPlaceholder(u,origins.get(id));if(!u.equals(v))changed=true;fixed.add(v);}seen.put(id,new JSONArray(fixed));}if(changed){o.put("posts",new JSONArray(unique.values()));save(o);}}catch(Exception ignored){}}}
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
                Map<String,JSONObject> storedPosts=new HashMap<>();for(int j=0;j<posts.length();j++){JSONObject p=posts.optJSONObject(j);if(src.id.equals(p.optString("source"))){String key=p.optString("noticeKey");if(key.isEmpty())key=p.optString("url");seen.add(key);storedPosts.put(key,p);}}
                long now=System.currentTimeMillis();int offset=0;
                boolean shortInitialized=s.optBoolean("shortPostsInitialized"),foundShorts=false;
                for(Collector.Item item:result.items){if(item.shortPost)foundShorts=true;String itemKey=src.authenticated&&!item.noticeKey.isEmpty()?item.noticeKey:item.url;if(!seen.add(itemKey)){JSONObject oldPost=storedPosts.get(itemKey);if(src.authenticated&&!item.noticeKey.isEmpty()&&oldPost!=null)oldPost.put("url",item.url);if(item.shortPost&&oldPost!=null)oldPost.put("title",item.title).put("summary",item.summary).put("date",item.date).put("shortPost",true);continue;}
                    JSONObject p=new JSONObject();p.put("id",UUID.randomUUID().toString()).put("source",src.id).put("sourceName",s.optString("name")).put("title",item.title).put("url",item.url).put("summary",item.summary).put("date",item.date).put("found",now-offset++).put("read",false).put("shortPost",item.shortPost);if(src.authenticated&&!item.noticeKey.isEmpty())p.put("noticeKey",item.noticeKey);posts.put(p);if(initialized&&(!item.shortPost||shortInitialized))fresh.add(new Post(p));}
                if(foundShorts)s.put("shortPostsInitialized",true);
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
