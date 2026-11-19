package com.mutang.xunxi;

import java.net.URI;
import java.util.Collection;

/** Login subscriptions retain SPA routes and only permit cleartext at the explicitly chosen origin. */
final class AuthUrl {
    static String normalize(String value){
        String base=Collector.normalize(value);if(base==null)return null;
        try{
            String raw=value.trim();if(!raw.matches("(?i)^https?://.*"))raw="https://"+raw;
            URI u=URI.create(raw);
            return u.getRawFragment()==null?base:URI.create(base+"#"+u.getRawFragment()).toASCIIString();
        }catch(Exception e){return null;}
    }
    static int port(URI u){return u.getPort()!=-1?u.getPort():"https".equalsIgnoreCase(u.getScheme())?443:80;}
    static boolean sameOrigin(String a,String b){
        try{URI x=URI.create(a),y=URI.create(b);return x.getUserInfo()==null&&y.getUserInfo()==null&&x.getHost()!=null&&x.getHost().equalsIgnoreCase(y.getHost())&&x.getScheme().equalsIgnoreCase(y.getScheme())&&port(x)==port(y);}catch(Exception e){return false;}
    }
    static boolean cleartext(String u){try{return "http".equalsIgnoreCase(URI.create(u).getScheme());}catch(Exception e){return false;}}
    static String origin(String raw){try{URI u=URI.create(raw);if(u.getHost()==null||u.getUserInfo()!=null||!("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme())))return null;return u.getScheme().toLowerCase(java.util.Locale.ROOT)+"://"+u.getHost().toLowerCase(java.util.Locale.ROOT)+":"+port(u);}catch(Exception e){return null;}}
    static boolean trustedOrigin(String source,Collection<String> approved,String target){String o=origin(target);return o!=null&&(sameOrigin(source,target)||approved.contains(o));}
    static boolean allowedNavigation(String source,Collection<String> approved,String target){return allowedNavigation(source,target)||(cleartext(target)&&trustedOrigin(source,approved,target));}
    static boolean allowedNavigation(String source,String target){
        try{URI u=URI.create(target);if(u.getUserInfo()!=null||u.getHost()==null)return false;
            return "https".equalsIgnoreCase(u.getScheme())||(cleartext(source)&&cleartext(target)&&sameOrigin(source,target));
        }catch(Exception e){return false;}
    }
    static boolean hasSessionParameter(String url){
        try{URI u=URI.create(url);String query=u.getRawQuery(),fragment=u.getRawFragment();String text=(query==null?"":query)+"&"+(fragment==null?"":fragment);
            return text.matches("(?is).*(?:^|[?&#;])(?:access_token|refresh_token|token|ticket|sessionid|jsessionid|password|authcode|authorization|code)=.*")||u.getRawPath().toLowerCase(java.util.Locale.ROOT).contains(";jsessionid=");
        }catch(Exception e){return true;}
    }
}
