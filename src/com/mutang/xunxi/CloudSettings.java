package com.mutang.xunxi;

import android.content.*;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Credentials are device-local and encrypted by Android Keystore; never serialized into sync data. */
final class CloudSettings {
    static final String ALIAS="xunji.cloud.aes.v1";final SharedPreferences prefs;
    CloudSettings(Context c){prefs=c.getApplicationContext().getSharedPreferences("xunji.cloud",Context.MODE_PRIVATE);}
    SecretKey key()throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(!ks.containsAlias(ALIAS)){KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());return g.generateKey();}return (SecretKey)ks.getKey(ALIAS,null);}
    void save(CloudSyncClient.Config c)throws Exception{JSONObject o=new JSONObject().put("url",c.url).put("user",c.user).put("password",c.password);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());byte[] b=cipher.doFinal(o.toString().getBytes(StandardCharsets.UTF_8));if(!prefs.edit().putString("cipher",Base64.encodeToString(b,Base64.NO_WRAP)).putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).commit())throw new Exception("无法保存云同步配置");}
    CloudSyncClient.Config load()throws Exception{String b=prefs.getString("cipher",null),iv=prefs.getString("iv",null);if(b==null||iv==null)return null;Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));JSONObject o=new JSONObject(new String(c.doFinal(Base64.decode(b,Base64.NO_WRAP)),StandardCharsets.UTF_8));return new CloudSyncClient.Config(o.getString("url"),o.getString("user"),o.getString("password"));}
    boolean configured(){return prefs.contains("cipher");}boolean automatic(){return prefs.getBoolean("auto",false);}void auto(boolean on){prefs.edit().putBoolean("auto",on).apply();}long last(){return prefs.getLong("lastSync",0);}void synced(){prefs.edit().putLong("lastSync",System.currentTimeMillis()).apply();}void clear(){prefs.edit().clear().apply();}
}
