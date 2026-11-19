package com.mutang.xunxi;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/** Public PDF reader: lazy rendering, local preferences and explicit file saving. */
public final class PdfActivity extends Activity {
    private static final long LIMIT=64L*1024*1024;
    private static final int SAVE=307;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean active=true;
    private PdfRenderer renderer; private File file;
    private boolean validPdf,continuous,fitWidth;
    private String url,referer,name;
    private TextView label,prev,next,save,message,retry;
    private PdfView view; private int[] widths,heights; private int page;
    private Uri pendingSave; private SharedPreferences prefs;
    int d(int n){return ReaderUi.dp(this,n);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);url=getIntent().getStringExtra("url");referer=getIntent().getStringExtra("referer");
        if(!ArticleReader.webUrl(url)){finish();return;}
        name=getIntent().getStringExtra("name");if(name==null||name.trim().isEmpty())name=android.webkit.URLUtil.guessFileName(url,null,"application/pdf");
        name=name.replaceAll("[/\\\\:*?\"<>|]","_");if(!name.toLowerCase(Locale.ROOT).endsWith(".pdf"))name+=".pdf";
        prefs=getSharedPreferences("pdf_reader",MODE_PRIVATE);continuous=prefs.getBoolean("continuous",true);fitWidth=prefs.getBoolean("fit_width",true);if(state!=null)page=state.getInt("page",0);
        getWindow().setStatusBarColor(MainActivity.BG);getWindow().setNavigationBarColor(MainActivity.CARD);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(MainActivity.BG);root.setOnApplyWindowInsetsListener((v,i)->{if(Build.VERSION.SDK_INT>=30){Insets p=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(p.left,p.top,p.right,p.bottom);}else v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});setContentView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(d(12),d(8),d(12),d(8));
        label=ReaderUi.button(this,"PDF",false);label.setOnClickListener(v->jumpDialog());addButton(top,label,true);
        TextView settings=ReaderUi.button(this,"阅读设置",false);settings.setOnClickListener(v->settingsDialog());addButton(top,settings,false);save=ReaderUi.button(this,"保存",true);save.setEnabled(false);save.setOnClickListener(v->chooseSave());addButton(top,save,false);root.addView(top);
        LinearLayout status=new LinearLayout(this);status.setOrientation(1);status.setPadding(d(16),d(12),d(16),d(12));status.setGravity(Gravity.CENTER);message=new TextView(this);message.setTextColor(MainActivity.MUTED);message.setGravity(Gravity.CENTER);message.setTextSize(14);status.addView(message,new LinearLayout.LayoutParams(-1,-2));retry=ReaderUi.button(this,"重新加载",false);retry.setOnClickListener(v->load());status.addView(retry);root.addView(status);
        view=new PdfView();root.addView(view,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bottom=new LinearLayout(this);bottom.setPadding(d(12),d(8),d(12),d(10));bottom.setBackgroundColor(MainActivity.CARD);TextView back=ReaderUi.button(this,"返回",false);back.setOnClickListener(v->finish());addButton(bottom,back,true);prev=ReaderUi.button(this,"上一页",false);prev.setOnClickListener(v->go(page-1));addButton(bottom,prev,true);next=ReaderUi.button(this,"下一页",true);next.setOnClickListener(v->go(page+1));addButton(bottom,next,true);root.addView(bottom);load();
    }
    private void addButton(LinearLayout row,TextView button,boolean weighted){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(weighted?0:-2,-2,weighted?1:0);if(row.getChildCount()>0)p.leftMargin=d(8);row.addView(button,p);}
    private void showMessage(String text,boolean canRetry){message.setText(text);((View)message.getParent()).setVisibility(View.VISIBLE);retry.setVisibility(canRetry?View.VISIBLE:View.GONE);}
    private void post(Runnable action){runOnUiThread(()->{if(active&&!isFinishing())action.run();});}
    private void load(){
        validPdf=false;save.setEnabled(false);widths=null;heights=null;view.clear();updateButtons();showMessage("正在下载并解析 PDF…",false);
        worker.execute(()->{boolean downloaded=false;try{
            closeDocument();file=File.createTempFile("notice-",".pdf",getCacheDir());String finalUrl=download(url,file,referer,LIMIT);PdfDocumentType.Kind type=normalize(file);
            // Ordinary public HTML viewer: follow one explicit same-origin PDF link only.
            if(type==PdfDocumentType.Kind.HTML&&file.length()<=1024*1024){String target=embeddedPdf(file,finalUrl);if(target!=null){download(target,file,finalUrl,LIMIT);type=normalize(file);}}
            if(type!=PdfDocumentType.Kind.PDF){final String reason=type==PdfDocumentType.Kind.HTML?"网站返回网页而非 PDF，请返回原网页查看附件。":"下载内容不是有效 PDF，可能是其他格式或错误响应。";post(()->showMessage(reason,true));return;}
            downloaded=true;post(()->{validPdf=true;save.setEnabled(true);if(pendingSave!=null){Uri dest=pendingSave;pendingSave=null;saveTo(dest);}});
            ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);try{renderer=new PdfRenderer(fd);}catch(Exception e){fd.close();throw e;}
            int count=renderer.getPageCount();if(count<=0||count>5000)throw new IOException("页数超出预览范围");int[] w=new int[count],h=new int[count];
            for(int i=0;i<count;i++){if(!active)return;try(PdfRenderer.Page p=renderer.openPage(i)){w[i]=Math.max(1,p.getWidth());h[i]=Math.max(1,p.getHeight());}catch(Exception e){w[i]=i==0?595:w[i-1];h[i]=i==0?842:h[i-1];}}
            post(()->{widths=w;heights=h;page=Math.max(0,Math.min(page,w.length-1));((View)message.getParent()).setVisibility(View.GONE);view.rebuild(page);updateButtons();});
        }catch(SecurityException e){post(()->showMessage("PDF 可能需要密码或受保护，无法预览。可保存后使用支持的阅读器打开。",true));}catch(Exception e){final String detail=e instanceof IOException&&e.getMessage()!=null?e.getMessage():"";final boolean got=downloaded;post(()->showMessage(got?"PDF 已下载，但系统无法解析（可能损坏、加密或格式不兼容）。可点击保存。":"PDF 加载失败"+(detail.isEmpty()?"，请检查网络后重试。":"："+detail),true));}});
    }
    static PdfDocumentType.Kind normalize(File file)throws IOException{
        PdfDocumentType.Kind kind=PdfDocumentType.identify(file);if(kind!=PdfDocumentType.Kind.GZIP)return kind;File tmp=File.createTempFile("pdf-unpack-",".tmp",file.getParentFile());
        try{try(InputStream in=new GZIPInputStream(new FileInputStream(file));OutputStream out=new FileOutputStream(tmp)){copy(in,out,LIMIT);}if(!tmp.renameTo(file))throw new IOException("无法解压文件");return PdfDocumentType.identify(file);}finally{tmp.delete();}
    }
    private static String embeddedPdf(File file,String base)throws IOException{Set<String> candidates=new LinkedHashSet<>();for(Element e:Jsoup.parse(file,null,base).select("iframe[src],embed[src],object[data],a[href]")){String u=e.absUrl(e.hasAttr("src")?"src":e.hasAttr("data")?"data":"href");if(ArticleReader.pdfUrl(u)&&ArticleReader.sameOrigin(base,u))candidates.add(u);}return candidates.size()==1?candidates.iterator().next():null;}
    static String download(String url,File file,String referer,long limit)throws Exception{
        for(int i=0;i<8;i++){if(!ArticleReader.webUrl(url))throw new IOException("无效的附件地址");HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setRequestProperty("User-Agent","Xunji Android Reader");c.setRequestProperty("Accept","*/*");if(ArticleReader.sameOrigin(referer,url))c.setRequestProperty("Referer",referer);
            try{int status=c.getResponseCode();if(status>=300&&status<400){String location=c.getHeaderField("Location");if(location==null)throw new IOException("无效的下载跳转");String u=URI.create(url).resolve(location).toString();if(url.startsWith("https:")&&!u.startsWith("https:"))throw new IOException("网站跳转至未加密地址");url=u;continue;}if(status!=200)throw new IOException("网站返回 HTTP "+status);if(c.getContentLengthLong()>limit)throw new IOException("文件超过大小限制");try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(file)){if(copy(in,out,limit)==0)throw new IOException("网站返回空文件");}return url;}finally{c.disconnect();}}
        throw new IOException("下载跳转次数过多");
    }
    private static long copy(InputStream in,OutputStream out,long limit)throws IOException{byte[] b=new byte[32768];long total=0;int n;while((n=in.read(b))!=-1){total+=n;if(total>limit)throw new IOException("文件超过大小限制");out.write(b,0,n);}return total;}
    private void settingsDialog(){LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(d(20),d(4),d(20),d(4));TextView a=new TextView(this);a.setText("翻页方式");body.addView(a);RadioGroup modes=new RadioGroup(this);RadioButton flow=new RadioButton(this);flow.setId(1);flow.setText("连续上下滚动");modes.addView(flow);RadioButton single=new RadioButton(this);single.setId(2);single.setText("逐页阅读（左右滑动翻页）");modes.addView(single);modes.check(continuous?1:2);body.addView(modes);
        TextView b=new TextView(this);b.setText("页面适应");b.setPadding(0,d(12),0,0);body.addView(b);RadioGroup fit=new RadioGroup(this);RadioButton width=new RadioButton(this);width.setId(3);width.setText("适应屏幕宽度");fit.addView(width);RadioButton whole=new RadioButton(this);whole.setId(4);whole.setText("整页显示");fit.addView(whole);fit.check(fitWidth?3:4);body.addView(fit);TextView hint=new TextView(this);hint.setText("双指缩放，双击恢复大小，点击顶部页码跳转。设置自动记住。");hint.setTextColor(MainActivity.MUTED);hint.setPadding(0,d(12),0,0);body.addView(hint);
        new AlertDialog.Builder(this).setTitle("PDF 阅读设置").setView(body).setNegativeButton("取消",null).setPositiveButton("应用",(dlg,w)->{continuous=modes.getCheckedRadioButtonId()==1;fitWidth=fit.getCheckedRadioButtonId()==3;prefs.edit().putBoolean("continuous",continuous).putBoolean("fit_width",fitWidth).apply();if(widths!=null){view.zoom=1;view.rebuild(page);}}).show();}
    private void jumpDialog(){if(widths==null)return;EditText input=new EditText(this);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setText(String.valueOf(page+1));input.selectAll();new AlertDialog.Builder(this).setTitle("跳转页码（1–"+widths.length+"）").setView(input).setNegativeButton("取消",null).setPositiveButton("跳转",(dlg,w)->{try{int p=Integer.parseInt(input.getText().toString());if(p>=1&&p<=widths.length){go(p-1);return;}}catch(Exception ignored){}Toast.makeText(this,"请输入有效页码",Toast.LENGTH_SHORT).show();}).show();}
    private void go(int p){if(widths!=null&&p>=0&&p<widths.length){page=p;view.rebuild(p);updateButtons();}}
    private void updateButtons(){boolean ready=widths!=null;prev.setEnabled(ready&&page>0);next.setEnabled(ready&&page+1<widths.length);label.setEnabled(ready);label.setText(ready?(page+1)+" / "+widths.length+" 页":"PDF");}
    private void chooseSave(){if(!validPdf)return;try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/pdf").putExtra(Intent.EXTRA_TITLE,name),SAVE);}catch(Exception e){Toast.makeText(this,"无法打开文件保存位置",Toast.LENGTH_SHORT).show();}}
    @Override protected void onActivityResult(int code,int result,Intent data){super.onActivityResult(code,result,data);if(code==SAVE&&result==RESULT_OK&&data!=null&&data.getData()!=null){if(validPdf)saveTo(data.getData());else pendingSave=data.getData();}}
    private void saveTo(Uri dest){worker.execute(()->{try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(dest,"w")){if(out==null)throw new IOException();copy(in,out,LIMIT);post(()->Toast.makeText(this,"PDF 已保存",Toast.LENGTH_LONG).show());}catch(Exception e){post(()->Toast.makeText(this,"保存失败，请重试",Toast.LENGTH_LONG).show());}});}
    private void closeDocument(){if(renderer!=null){try{renderer.close();}catch(Exception ignored){}renderer=null;}if(file!=null){file.delete();file=null;}}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("page",page);}
    @Override protected void onDestroy(){active=false;if(view!=null)view.clear();worker.execute(this::closeDocument);worker.shutdown();super.onDestroy();}

    private final class PdfView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final LinkedHashMap<Integer,Bitmap> cache=new LinkedHashMap<>(8,.75f,true);
        private final Set<Integer> pending=new HashSet<>(),failed=new HashSet<>();
        private final ScaleGestureDetector scale;private final GestureDetector gestures;private PdfLayout layout;
        private float zoom=1,x,y,lastX,lastY,startX,startY;private boolean pinched;private int generation;
        PdfView(){super(PdfActivity.this);setBackgroundColor(0xffe9edef);setContentDescription("PDF 阅读区域，双指缩放");scale=new ScaleGestureDetector(PdfActivity.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector s){pinched=true;zoomAt(s.getScaleFactor(),s.getFocusX(),s.getFocusY());return true;}});gestures=new GestureDetector(PdfActivity.this,new GestureDetector.SimpleOnGestureListener(){@Override public boolean onDown(MotionEvent e){return true;}@Override public boolean onDoubleTap(MotionEvent e){zoomAt(1/zoom,e.getX(),e.getY());return true;}});}
        void clear(){generation++;layout=null;for(Bitmap b:cache.values())b.recycle();cache.clear();pending.clear();failed.clear();invalidate();}
        void rebuild(int target){if(widths==null||getWidth()<=0||getHeight()<=0)return;layout=new PdfLayout(widths,heights,getWidth(),getHeight(),d(8),continuous,fitWidth,zoom,target);x=layout.clampX((layout.contentW-getWidth())/2);y=continuous?layout.clampY(layout.tops[target]-d(8)):0;invalidate();}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);rebuild(page);}
        private void zoomAt(float factor,float fx,float fy){if(layout==null)return;float old=zoom;zoom=Math.max(1,Math.min(4,zoom*factor));if(Math.abs(zoom-old)<.001)return;int at=layout.pageAt(y+fy);float rx=(x+fx-layout.left(at))/layout.widths[at],ry=(y+fy-layout.tops[at])/layout.heights[at];layout=new PdfLayout(widths,heights,getWidth(),getHeight(),d(8),continuous,fitWidth,zoom,page);x=layout.clampX(layout.left(at)+rx*layout.widths[at]-fx);y=layout.clampY(layout.tops[at]+ry*layout.heights[at]-fy);changed();}
        private void changed(){if(layout==null)return;int at=layout.pageAt(y+getHeight()/2f);if(at!=page){page=at;updateButtons();}invalidate();}
        @Override public boolean onTouchEvent(MotionEvent e){if(layout==null)return false;scale.onTouchEvent(e);gestures.onTouchEvent(e);switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:startX=lastX=e.getX();startY=lastY=e.getY();pinched=false;return true;
            case MotionEvent.ACTION_POINTER_DOWN:pinched=true;return true;
            case MotionEvent.ACTION_POINTER_UP:int remain=e.getActionIndex()==0?1:0;lastX=e.getX(remain);lastY=e.getY(remain);return true;
            case MotionEvent.ACTION_MOVE:if(!scale.isInProgress()&&e.getPointerCount()==1){float nx=e.getX(),ny=e.getY();x=layout.clampX(x+lastX-nx);y=layout.clampY(y+lastY-ny);lastX=nx;lastY=ny;changed();}return true;
            case MotionEvent.ACTION_UP:float dx=e.getX()-startX,dy=e.getY()-startY;if(!continuous&&!pinched&&zoom<=1.001&&Math.abs(dx)>d(55)&&Math.abs(dx)>Math.abs(dy)*1.4f)go(page+(dx<0?1:-1));return true;
            default:return true;}}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(layout==null)return;int first=continuous?layout.pageAt(y):page,last=continuous?layout.pageAt(y+getHeight()):page;for(int i=first;i<=last;i++){RectF r=new RectF(layout.left(i)-x,layout.tops[i]-y,layout.left(i)+layout.widths[i]-x,layout.tops[i]+layout.heights[i]-y);paint.setColor(Color.WHITE);canvas.drawRect(r,paint);Bitmap b=cache.get(i);if(b!=null&&!b.isRecycled())canvas.drawBitmap(b,null,r,paint);else{paint.setColor(MainActivity.MUTED);paint.setTextSize(d(14));paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(failed.contains(i)?"此页无法预览，可保存 PDF":"正在渲染第 "+(i+1)+" 页…",r.centerX(),Math.max(d(22),Math.min(r.centerY(),getHeight()-d(12))),paint);request(i);}}}
        private void request(int index){if(!active||pending.contains(index)||failed.contains(index)||pending.size()>=3)return;pending.add(index);int ticket=generation;worker.execute(()->{Bitmap b=null;try(PdfRenderer.Page p=renderer.openPage(index)){float s=Math.min(1600f/Math.max(1,p.getWidth()),2300f/Math.max(1,p.getHeight()));b=Bitmap.createBitmap(Math.max(1,Math.round(p.getWidth()*s)),Math.max(1,Math.round(p.getHeight()*s)),Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);}catch(Exception|OutOfMemoryError e){if(b!=null)b.recycle();b=null;}final Bitmap rendered=b;runOnUiThread(()->{if(!active||ticket!=generation){if(rendered!=null)rendered.recycle();return;}pending.remove(index);if(rendered==null)failed.add(index);else{cache.put(index,rendered);while(cache.size()>3){Iterator<Map.Entry<Integer,Bitmap>> it=cache.entrySet().iterator();Map.Entry<Integer,Bitmap> old=it.next();it.remove();old.getValue().recycle();}}invalidate();});});}
    }
}
