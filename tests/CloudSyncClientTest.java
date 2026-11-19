package com.mutang.xunxi;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.net.ssl.*;
import org.json.*;

/** Local HTTPS WebDAV-shaped test; the certificate is trusted only by the test JVM. */
public final class CloudSyncClientTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);}
    public static void main(String[] args)throws Exception{
        check(CloudSyncClient.folder("https://dav.example.test/folder").equals("https://dav.example.test/folder/"),"HTTPS folder normalization");
        for(String u:new String[]{"http://dav.example.test/","https://user@dav.example.test/","https://dav.example.test/?token=abc"}){try{CloudSyncClient.folder(u);throw new AssertionError("Unsafe DAV URL accepted");}catch(IllegalArgumentException expected){}}
        KeyStore key=KeyStore.getInstance("PKCS12");try(InputStream in=new FileInputStream(args[0])){key.load(in,args[1].toCharArray());}KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(key,args[1].toCharArray());SSLContext tls=SSLContext.getInstance("TLS");tls.init(km.getKeyManagers(),null,null);
        HttpsServer server=HttpsServer.create(new InetSocketAddress("localhost",0),0);server.setHttpsConfigurator(new HttpsConfigurator(tls));final byte[][] payload={null};final String[] etag={"\"first\""};server.createContext("/dav/Xunji-sync-v1.json",exchange->{try{String expected="Basic "+java.util.Base64.getEncoder().encodeToString("fixture:fixture-password".getBytes(StandardCharsets.UTF_8));if(!expected.equals(exchange.getRequestHeaders().getFirst("Authorization"))){exchange.sendResponseHeaders(401,-1);return;}if(exchange.getRequestMethod().equals("GET")){if(payload[0]==null){exchange.sendResponseHeaders(404,-1);return;}exchange.getResponseHeaders().set("ETag",etag[0]);exchange.sendResponseHeaders(200,payload[0].length);try(OutputStream out=exchange.getResponseBody()){out.write(payload[0]);}return;}if(exchange.getRequestMethod().equals("PUT")){String match=exchange.getRequestHeaders().getFirst("If-Match"),none=exchange.getRequestHeaders().getFirst("If-None-Match");if(payload[0]==null?!"*".equals(none):!etag[0].equals(match)){exchange.sendResponseHeaders(412,-1);return;}payload[0]=exchange.getRequestBody().readAllBytes();etag[0]="\"next\"";exchange.sendResponseHeaders(201,-1);return;}exchange.sendResponseHeaders(405,-1);}finally{exchange.close();}});server.start();try{CloudSyncClient.Config cfg=new CloudSyncClient.Config("https://localhost:"+server.getAddress().getPort()+"/dav/","fixture","fixture-password");CloudSyncClient.Remote empty=CloudSyncClient.get(cfg);check(!empty.exists,"New remote sync file missing");JSONObject state=SyncMerge.merge(new JSONObject(),new JSONObject());check(CloudSyncClient.put(cfg,empty,state),"First sync writes with If-None-Match");CloudSyncClient.Remote found=CloudSyncClient.get(cfg);check(found.exists&&"\"next\"".equals(found.etag),"Downloaded state and ETag");check(CloudSyncClient.put(cfg,found,state),"Conditional update with If-Match");check(!CloudSyncClient.put(cfg,empty,state),"Concurrent-write conflict refused");}finally{server.stop(0);}
    }
}
