package com.mutang.xunxi;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.widget.*;
import java.io.*;
import java.net.*;

/** Render a public PDF in a private temporary file without an external browser. */
public final class PdfActivity extends Activity {
    volatile boolean active=true; File file; PdfRenderer renderer; ImageView image;
    TextView label; Button prev,next; int page; Bitmap bitmap;
    @Override public void onCreate(Bundle state){
        super.onCreate(state); String url=getIntent().getStringExtra("url");
        if(!ArticleReader.webUrl(url)){finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(12,24,12,12);setContentView(root);
        LinearLayout bar=new LinearLayout(this);Button back=new Button(this);back.setText("返回");back.setOnClickListener(v->finish());bar.addView(back);prev=new Button(this);prev.setText("上一页");prev.setEnabled(false);prev.setOnClickListener(v->render(page-1));bar.addView(prev);
        next=new Button(this);next.setText("下一页");next.setEnabled(false);next.setOnClickListener(v->render(page+1));bar.addView(next);root.addView(bar);
        label=new TextView(this);label.setText("正在加载 PDF 文件…");root.addView(label);ScrollView scroll=new ScrollView(this);image=new ImageView(this);image.setAdjustViewBounds(true);scroll.addView(image,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        new Thread(()->{File tmp=null;try{
            tmp=File.createTempFile("notice-",".pdf",getCacheDir());
            try{download(url,tmp);}catch(Exception e){tmp.delete();throw e;}
            final File loaded=tmp;runOnUiThread(()->{if(!active){loaded.delete();return;}file=loaded;try{ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);try{renderer=new PdfRenderer(fd);}catch(Exception e){fd.close();throw e;}render(0);}catch(Exception e){label.setText("无法预览 PDF（文件可能加密或格式不受支持）");}});
        }catch(Exception e){if(tmp!=null)tmp.delete();runOnUiThread(()->{if(active)label.setText("无法加载 PDF，请返回原文重试。");});}},"xunji-pdf").start();
    }
    static void download(String url,File file)throws Exception{
        for(int i=0;i<5;i++){if(!ArticleReader.webUrl(url))throw new IOException();HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(20000);
            try{int status=c.getResponseCode();if(status>=300&&status<400){String location=c.getHeaderField("Location");if(location==null)throw new IOException();String u=URI.create(url).resolve(location).toString();if(url.startsWith("https:")&&!u.startsWith("https:"))throw new IOException();url=u;continue;}if(status!=200||c.getContentLengthLong()>24L*1024*1024)throw new IOException();
                try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(file)){byte[] b=new byte[16384];long total=0;int n;while((n=in.read(b))!=-1){total+=n;if(total>24L*1024*1024)throw new IOException();out.write(b,0,n);}}return;
            }finally{c.disconnect();}
        }throw new IOException();
    }
    void render(int p){if(renderer==null||p<0||p>=renderer.getPageCount())return;try(PdfRenderer.Page view=renderer.openPage(p)){
        float scale=Math.min(1400f/view.getWidth(),2000f/view.getHeight());Bitmap b=Bitmap.createBitmap(Math.max(1,Math.round(view.getWidth()*scale)),Math.max(1,Math.round(view.getHeight()*scale)),Bitmap.Config.ARGB_8888);b.eraseColor(android.graphics.Color.WHITE);view.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);image.setImageBitmap(b);if(bitmap!=null)bitmap.recycle();bitmap=b;page=p;label.setText("第 "+(p+1)+" / "+renderer.getPageCount()+" 页");prev.setEnabled(p>0);next.setEnabled(p+1<renderer.getPageCount());
    }catch(Exception e){label.setText("此页无法显示");}}
    @Override protected void onDestroy(){active=false;if(renderer!=null)renderer.close();if(bitmap!=null)bitmap.recycle();if(file!=null)file.delete();super.onDestroy();}
}
