package com.mutang.xunxi;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;
import org.jsoup.Jsoup;
import java.io.*;
import java.util.*;

/** Fetch the site's own declared icon, then its favicon.ico; no third-party service. */
final class SiteIcons {
    static File file(Context c,String id){if(id==null||!id.matches("[a-fA-F0-9-]{36}"))return null;File dir=new File(c.getFilesDir(),"site-icons");dir.mkdirs();return new File(dir,id+".png");}
    static Bitmap load(Context c,String id){File f=file(c,id);return f!=null&&f.exists()?BitmapFactory.decodeFile(f.getAbsolutePath()):null;}
    static void remove(Context c,String id){File f=file(c,id);if(f!=null)new AtomicFile(f).delete();}
    static boolean exists(Context c,String id){File f=file(c,id);return f!=null&&f.isFile();}
    static void fetch(Context c,String id,List<String> icons){if(exists(c,id))return;List<String> candidates=new ArrayList<>();for(String u:icons){if(candidates.size()<3)candidates.add(u);}if(icons.size()>3&&!candidates.contains(icons.get(icons.size()-1)))candidates.add(icons.get(icons.size()-1));for(String url:candidates){if(Thread.currentThread().isInterrupted())return;try{
        byte[] bytes=Jsoup.connect(url).userAgent("Xunji/0.2 PersonalFeed").timeout(5500).maxBodySize(512*1024).ignoreContentType(true).followRedirects(true).execute().bodyAsBytes();
        Bitmap b=decode(bytes,false);if(b==null)continue;File f=file(c,id);if(f==null)return;AtomicFile atomic=new AtomicFile(f);FileOutputStream out=null;try{out=atomic.startWrite();b.compress(Bitmap.CompressFormat.PNG,100,out);atomic.finishWrite(out);}catch(Exception e){if(out!=null)atomic.failWrite(out);}finally{b.recycle();}return;
    }catch(Exception ignored){}}}
    static Bitmap decode(byte[] bytes,boolean crest){Bitmap bitmap=standard(bytes,0,bytes.length);if(bitmap==null)bitmap=ico(bytes);if(bitmap==null)return null;int w=bitmap.getWidth(),h=bitmap.getHeight();if(crest&&w>h*2.3){Bitmap square=Bitmap.createBitmap(bitmap,0,0,h,h);if(square!=bitmap)bitmap.recycle();bitmap=square;w=h;}if(Math.max(w,h)>96){Bitmap scaled=Bitmap.createScaledBitmap(bitmap,Math.max(1,w*96/Math.max(w,h)),Math.max(1,h*96/Math.max(w,h)),true);if(scaled!=bitmap)bitmap.recycle();bitmap=scaled;}return bitmap;}
    static boolean needsDarkBackground(Bitmap b){int clear=0,white=0,colored=0;for(int y=0;y<b.getHeight();y+=3)for(int x=0;x<b.getWidth();x+=3){int c=b.getPixel(x,y);if((c>>>24)<50){clear++;continue;}if(((c>>>16)&255)>205&&((c>>>8)&255)>205&&(c&255)>205)white++;else colored++;}return clear>0&&white>colored*3&&white>0;}
    static Bitmap standard(byte[] b,int start,int len){try{BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(b,start,len,o);if(o.outWidth<=0||o.outHeight<=0||o.outWidth>8192||o.outHeight>8192)return null;int sample=1;while(Math.max(o.outWidth,o.outHeight)/sample>192)sample*=2;o.inJustDecodeBounds=false;o.inSampleSize=sample;return BitmapFactory.decodeByteArray(b,start,len,o);}catch(Exception e){return null;}}
    static int u16(byte[] b,int p){return (b[p]&255)|((b[p+1]&255)<<8);}static long u32(byte[] b,int p){return (b[p]&255L)|((b[p+1]&255L)<<8)|((b[p+2]&255L)<<16)|((b[p+3]&255L)<<24);}
    static Bitmap ico(byte[] b){try{if(b.length<22||u16(b,0)!=0||u16(b,2)!=1)return null;int count=Math.min(50,u16(b,4));List<Integer> frames=new ArrayList<>();for(int i=0;i<count&&6+16*i+16<=b.length;i++)frames.add(6+i*16);frames.sort((a,z)->Integer.compare(Math.abs((b[a]&255)==0?256-64:(b[a]&255)-64),Math.abs((b[z]&255)==0?256-64:(b[z]&255)-64)));for(int frame:frames){long sz=u32(b,frame+8),off=u32(b,frame+12);if(sz<=0||off<0||off+sz>b.length||sz>Integer.MAX_VALUE)continue;Bitmap png=standard(b,(int)off,(int)sz);if(png!=null)return png;Bitmap dib=dib(b,(int)off,(int)sz);if(dib!=null)return dib;}}catch(Exception ignored){}return null;}
    static Bitmap dib(byte[] b,int p,int len){try{if(len<40)return null;int head=(int)u32(b,p),w=(int)u32(b,p+4),fullH=(int)u32(b,p+8),h=fullH/2,bpp=u16(b,p+14);if(head<40||head>len||w<=0||h<=0||w>256||h>256||(bpp!=32&&bpp!=24)||u32(b,p+16)!=0)return null;int stride=((w*bpp+31)/32)*4,start=p+head;if((long)start+stride*h>p+len)return null;int[] pixels=new int[w*h];boolean alpha=false;for(int y=0;y<h;y++)for(int x=0;x<w;x++){int q=start+(h-1-y)*stride+x*(bpp/8);int a=bpp==32?b[q+3]&255:255;if(a!=0)alpha=true;pixels[y*w+x]=(a<<24)|((b[q+2]&255)<<16)|((b[q+1]&255)<<8)|(b[q]&255);}int maskStart=start+stride*h,maskStride=((w+31)/32)*4;boolean mask=maskStart+maskStride*h<=p+len;for(int y=0;y<h;y++)for(int x=0;x<w;x++){int idx=y*w+x;if(!alpha)pixels[idx]|=0xff000000;if(mask&&((b[maskStart+(h-1-y)*maskStride+x/8]>>(7-x%8))&1)==1)pixels[idx]&=0x00ffffff;}return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);}catch(Exception e){return null;}}
}
