package com.mutang.xunxi;

public final class UpdateClientTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);}
    static String release(String url){return "{\"tag_name\":\"v0.4.1\",\"draft\":false,\"prerelease\":false,\"body\":\"测试\",\"assets\":[{\"name\":\"Xunji-0.4.1.apk\",\"size\":123456,\"browser_download_url\":\""+url+"\"}]}";}
    static void expectFailure(String data,String message)throws Exception{try{UpdateClient.parse(data);throw new AssertionError(message);}catch(java.io.IOException|IllegalArgumentException expected){System.out.println("PASS "+message);}}
    public static void main(String[] args)throws Exception{
        check(UpdateClient.compare("v0.4.1","0.4.0")>0&&UpdateClient.compare("0.10.0","0.9.9")>0&&UpdateClient.compare("0.4.0","v0.4.0")==0,"Semantic version ordering");
        String url="https://github.com/mutang837/Xunji/releases/download/v0.4.1/Xunji-0.4.1.apk";UpdateClient.Release r=UpdateClient.parse(release(url));
        check(r.version.equals("0.4.1")&&r.url.equals(url)&&r.size==123456,"Official Release asset selected");
        expectFailure(release("https://example.cn/update.apk"),"Untrusted asset rejected");
        expectFailure(release(url).replace("\"prerelease\":false","\"prerelease\":true"),"Pre-release rejected");
        expectFailure(release(url).replace("123456","999999999"),"Oversized APK rejected");
        check(UpdateClient.trusted(url)&&UpdateClient.trusted("https://release-assets.githubusercontent.com/a")&&!UpdateClient.trusted("http://github.com/a")&&!UpdateClient.trusted("https://github.com.evil.example/a")&&!UpdateClient.trusted("https://github.com@evil.example/a"),"Redirects restricted to trusted HTTPS hosts");
    }
}
