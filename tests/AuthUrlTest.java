package com.mutang.xunxi;
public final class AuthUrlTest {
    static void check(boolean yes,String label){if(!yes)throw new AssertionError(label);System.out.println("PASS "+label);}
    public static void main(String[] args){
        String origin="http://portal.test:1009/jwapp#/";
        String route="http://portal.test:1009/jwapp/sys/homeapp/home/index.html?av=123&contextPath=/jwapp#/";
        check(route.equals(AuthUrl.normalize(route)),"HTTP non-default port, query and hash route are preserved");
        String notice="http://portal.test:1009/jwapp#/notices/42";
        check(notice.equals(AuthUrl.normalize(notice))&&AuthUrl.sameOrigin(origin,notice),"Same-origin hash notice links survive import");
        check(AuthUrl.allowedNavigation(origin,notice)&&AuthUrl.allowedNavigation(origin,"https://login.test/"),"Selected HTTP origin and normal HTTPS login navigation work");
        check(!AuthUrl.allowedNavigation(origin,"http://portal.test:1010/jwapp")&&!AuthUrl.allowedNavigation(origin,"http://other.test:1009/jwapp"),"Different HTTP ports and hosts rejected");
        check(!AuthUrl.allowedNavigation("https://portal.test/","http://portal.test/")&&!AuthUrl.allowedNavigation(origin,"file:///etc/passwd"),"HTTPS source cannot silently downgrade; non-web links rejected");
        check(AuthUrl.hasSessionParameter(origin+"?token=example")&&AuthUrl.hasSessionParameter("http://portal.test:1009/jwapp?ticket=example")&&!AuthUrl.hasSessionParameter(route),"Session-token links excluded; ordinary query parameters retained");
        check("http://portal.test:1009/jwapp".equals(Collector.normalize(origin)),"Public collector behavior remains unchanged");
    }
}
