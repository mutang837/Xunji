package com.mutang.xunxi;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    static final int BG=0xfff5f7f8, CARD=0xffffffff, TEXT=0xff182830, MUTED=0xff697981, GREEN=0xff126e65, PALE=0xffe3f2ef, LINE=0xffe7ecee;
    Store store; AppUpdater updater; CloudSyncPanel cloud; LinearLayout root, top, page, nav; ScrollView scroll; FrameLayout shell, drawer; ScrollView drawerScroll;
    final Map<String,Bitmap> icons=new HashMap<>(); String selectedSource=null,iconTarget;static final int PICK_ICON=105,CROP_ICON=106;
    int tab=0; boolean unreadOnly=false, lastRunning=false; long revision=-1; float downY;
    Handler handler=new Handler(Looper.getMainLooper());
    final Runnable poll=new Runnable(){public void run(){boolean r=RefreshJob.running.get();if(store.revision()!=revision||r!=lastRunning){render(true);}handler.postDelayed(this,1200);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);if(state!=null)iconTarget=state.getString("iconTarget");store=new Store(this);store.repairStoredLinks();updater=new AppUpdater(this);cloud=new CloudSyncPanel(this);RefreshJob.channel(this);RefreshJob.schedule(this,false);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(CARD);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);build();render(false);}
    @Override protected void onResume(){super.onResume();render(true);handler.removeCallbacks(poll);handler.post(poll);handler.postDelayed(()->{if(!isFinishing()){updater.autoCheck();cloud.startup();}},1200);}
    @Override protected void onPause(){super.onPause();handler.removeCallbacks(poll);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);closeDrawer();selectedSource=null;tab=0;render(false);}
    @Override public void onBackPressed(){if(drawer!=null){closeDrawer();return;}if(tab==0&&selectedSource!=null){selectSource(null);return;}super.onBackPressed();}
    int d(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    TextView text(String t,float size,int color,boolean bold){TextView v=new TextView(this);v.setText(t);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);if(bold)v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return v;}
    GradientDrawable shape(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(d(radius));return g;}
    GradientDrawable outline(int color,int radius,int border){GradientDrawable g=shape(color,radius);g.setStroke(d(1),border);return g;}
    void pad(View v,int l,int t,int r,int b){v.setPadding(d(l),d(t),d(r),d(b));}
    void add(LinearLayout parent,View view){parent.addView(view,new LinearLayout.LayoutParams(-1,-2));}
    void gap(LinearLayout p,int dp){View v=new View(this);p.addView(v,new LinearLayout.LayoutParams(-1,d(dp)));}
    void line(LinearLayout p){View v=new View(this);v.setBackgroundColor(LINE);p.addView(v,new LinearLayout.LayoutParams(-1,d(1)));}
    android.graphics.drawable.Drawable touchShape(int bg,int radius){return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22126e65),shape(bg,radius),shape(0xffffffff,radius));}
    TextView action(String label,boolean primary,Runnable click){TextView v=text(label,13,primary?CARD:GREEN,true);v.setGravity(Gravity.CENTER);v.setMinHeight(d(40));pad(v,14,10,14,10);v.setBackground(touchShape(primary?GREEN:PALE,16));v.setOnClickListener(x->click.run());return v;}
    View iconAction(String kind,String description,boolean primary,Runnable click){FrameLayout button=new FrameLayout(this);button.setBackground(touchShape(primary?GREEN:CARD,16));button.setContentDescription(description);button.setOnClickListener(v->click.run());UiIcon icon=new UiIcon(this,kind,primary?CARD:GREEN,true);button.addView(icon,new FrameLayout.LayoutParams(d(23),d(23),Gravity.CENTER));button.setMinimumWidth(d(46));button.setMinimumHeight(d(46));return button;}
    View navItem(String glyph,String label,boolean active,Runnable click){LinearLayout item=column();item.setGravity(Gravity.CENTER);pad(item,4,6,4,5);item.setBackground(touchShape(CARD,20));FrameLayout slot=new FrameLayout(this);slot.setBackground(shape(active?PALE:CARD,17));UiIcon icon=new UiIcon(this,glyph,active?GREEN:MUTED,active);slot.addView(icon,new FrameLayout.LayoutParams(d(23),d(23),Gravity.CENTER));item.addView(slot,new LinearLayout.LayoutParams(d(60),d(34)));gap(item,4);TextView t=text(label,11,active?GREEN:MUTED,active);t.setGravity(Gravity.CENTER);add(item,t);item.setOnClickListener(v->click.run());item.setContentDescription(label+(active?"，已选中":""));return item;}
    void build(){
        shell=new FrameLayout(this);shell.setBackgroundColor(BG);root=column();root.setBackgroundColor(BG);shell.addView(root,new FrameLayout.LayoutParams(-1,-1));setContentView(shell);
        shell.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(i.left,i.top,i.right,i.bottom);}else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        top=column();pad(top,18,14,18,14);add(root,top);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);page=column();pad(page,16,0,16,18);scroll.addView(page,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        scroll.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN)downY=scroll.getScrollY()==0?e.getY():-10000;if(e.getAction()==MotionEvent.ACTION_UP&&tab==0&&downY>=0&&e.getY()-downY>d(100)){refresh(selectedSource);return true;}return false;});
        nav=row();nav.setBackground(outline(CARD,24,LINE));pad(nav,5,3,5,3);LinearLayout.LayoutParams bottom=new LinearLayout.LayoutParams(-1,-2);bottom.setMargins(d(14),d(7),d(14),d(9));root.addView(nav,bottom);
    }
    void render(boolean keep){
        if(root==null)return;int pos=keep?scroll.getScrollY():0;revision=store.revision();lastRunning=RefreshJob.running.get();top.removeAllViews();page.removeAllViews();nav.removeAllViews();
        Store.Source chosen=sourceById(selectedSource);if(chosen==null)selectedSource=null;
        LinearLayout titleRow=row(),titleBox=column();
        if(tab==0){titleRow.addView(iconAction("menu","打开网站侧边栏",false,()->showDrawer()),new LinearLayout.LayoutParams(d(46),d(46)));View sp=new View(this);titleRow.addView(sp,new LinearLayout.LayoutParams(d(13),1));}
        String main=tab==0?(chosen==null?"讯集":chosen.name):tab==1?"我的订阅":"设置";
        TextView title=text(main,chosen!=null&&tab==0?22:29,TEXT,true);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);add(titleBox,title);gap(titleBox,5);
        String sub=tab==0?(chosen==null?"你的个人信息流":"单个来源 · "+Collector.host(chosen.url)):tab==1?"管理你关注的来源":"个性化与同步";
        TextView st=text(sub,12,MUTED,false);st.setSingleLine(true);st.setEllipsize(android.text.TextUtils.TruncateAt.END);add(titleBox,st);titleRow.addView(titleBox,new LinearLayout.LayoutParams(0,-2,1));
        if(tab==0){View refresh=iconAction("refresh",lastRunning?"正在更新":"检查更新",false,()->refresh(selectedSource));titleRow.addView(refresh,new LinearLayout.LayoutParams(d(46),d(46)));if(lastRunning)refresh.setAlpha(.55f);}
        if(tab==1)titleRow.addView(iconAction("add","添加网站",true,()->addSource()),new LinearLayout.LayoutParams(d(46),d(46)));add(top,titleRow);
        if(tab==0)feed();else if(tab==1)sources();else settings();
        String[] labels={"首页","订阅","设置"},glyphs={"feed","bookmark","settings"};for(int i=0;i<3;i++){final int target=i;View item=navItem(glyphs[i],labels[i],tab==i,()->{tab=target;render(false);});nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));}
        scroll.post(()->scroll.scrollTo(0,pos));if(drawer!=null)fillDrawer();
    }
    Store.Source sourceById(String id){if(id==null)return null;for(Store.Source s:store.sources())if(id.equals(s.id))return s;return null;}
    View avatar(String id,String name,int size){Bitmap b=icons.get(id);if(b==null&&id!=null){b=SiteIcons.load(this,id);if(b!=null)icons.put(id,b);}if(b!=null){ImageView i=new ImageView(this);i.setImageBitmap(b);i.setScaleType(ImageView.ScaleType.FIT_CENTER);i.setBackground(outline(SiteIcons.needsDarkBackground(b)?GREEN:CARD,10,LINE));i.setClipToOutline(true);pad(i,3,3,3,3);return i;}String initial=name==null||name.isEmpty()?"讯":name.substring(0,name.offsetByCodePoints(0,1));TextView v=text(initial,size,GREEN,true);v.setGravity(Gravity.CENTER);v.setBackground(shape(PALE,10));return v;}
    void selectSource(String id){selectedSource=id;tab=0;unreadOnly=false;feedLimit=60;closeDrawer();render(false);}
    void showDrawer(){if(drawer!=null)return;drawer=new FrameLayout(this);View shade=new View(this);shade.setBackgroundColor(0x660c211b);shade.setOnClickListener(v->closeDrawer());drawer.addView(shade,new FrameLayout.LayoutParams(-1,-1));LinearLayout panel=column();panel.setBackground(shape(CARD,26));panel.setElevation(d(12));panel.setClickable(true);int width=Math.min(d(340),(int)(getResources().getDisplayMetrics().widthPixels*.84));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(width,-1,Gravity.START);drawer.addView(panel,lp);pad(panel,18,22,18,16);LinearLayout head=row();LinearLayout heading=column();add(heading,text("我的关注",26,TEXT,true));add(heading,text("选择一个网站，浏览专属信息流",12,MUTED,false));head.addView(heading,new LinearLayout.LayoutParams(0,-2,1));head.addView(action("×",false,()->closeDrawer()));add(panel,head);gap(panel,16);drawerScroll=new ScrollView(this);drawerScroll.setFillViewport(false);drawerScroll.setVerticalScrollBarEnabled(false);panel.addView(drawerScroll,new LinearLayout.LayoutParams(-1,0,1));gap(panel,12);add(panel,action("＋ 添加网站",true,()->{closeDrawer();addSource();}));gap(panel,8);add(panel,action("管理全部订阅",false,()->{closeDrawer();tab=1;render(false);}));shell.addView(drawer,new FrameLayout.LayoutParams(-1,-1));fillDrawer();panel.setTranslationX(-width);panel.animate().translationX(0).setDuration(180).start();}
    void closeDrawer(){if(drawer!=null){shell.removeView(drawer);drawer=null;drawerScroll=null;}}
    void fillDrawer(){if(drawerScroll==null)return;int y=drawerScroll.getScrollY();drawerScroll.removeAllViews();LinearLayout list=column();drawerScroll.addView(list,new ScrollView.LayoutParams(-1,-2));List<Store.Source> sources=store.sources();Map<String,Integer> unread=new HashMap<>();int total=0;for(Store.Post p:store.posts())if(!p.read){total++;unread.put(p.source,unread.getOrDefault(p.source,0)+1);}drawerRow(list,null,"全部信息",null,total);gap(list,14);add(list,text("订阅的网站 · "+sources.size(),12,MUTED,true));gap(list,7);for(Store.Source s:sources)drawerRow(list,s.id,s.name,Collector.host(s.url),unread.getOrDefault(s.id,0));drawerScroll.post(()->{if(drawerScroll!=null)drawerScroll.scrollTo(0,y);});}
    void drawerRow(LinearLayout list,String id,String name,String detail,int unread){boolean selected=Objects.equals(id,selectedSource);LinearLayout item=row();pad(item,10,10,10,10);if(selected)item.setBackground(shape(PALE,14));View icon=avatar(id,id==null?"全":name,19);item.addView(icon,new LinearLayout.LayoutParams(d(44),d(44)));LinearLayout label=column();pad(label,10,0,6,0);TextView title=text(name,15,selected?GREEN:TEXT,true);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);add(label,title);if(detail!=null){TextView sub=text(detail,11,MUTED,false);sub.setSingleLine(true);sub.setEllipsize(android.text.TextUtils.TruncateAt.END);add(label,sub);}item.addView(label,new LinearLayout.LayoutParams(0,-2,1));if(unread>0){TextView count=text(unread>99?"99+":String.valueOf(unread),11,CARD,true);pad(count,8,4,8,4);count.setBackground(shape(GREEN,12));item.addView(count);}item.setOnClickListener(v->selectSource(id));add(list,item);gap(list,5);}
    LinearLayout card(){LinearLayout c=column();c.setBackground(outline(CARD,22,LINE));pad(c,18,17,18,17);return c;}
    void feed(){
        List<Store.Post> posts=store.posts();List<Store.Source> src=store.sources();if(selectedSource!=null)posts.removeIf(p->!selectedSource.equals(p.source));int unread=0;for(Store.Post p:posts)if(!p.read)unread++;
        if(src.isEmpty()){empty("从一个网站开始","添加你关注的官网、博客、论坛或 RSS 订阅源，让消息主动找到你。","添加第一个网站",()->addSource());return;}
        LinearLayout overview=column();GradientDrawable hero=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff115d57,0xff25897b});hero.setCornerRadius(d(24));overview.setBackground(hero);pad(overview,20,17,20,17);
        LinearLayout infoRow=row(),stat=column();add(stat,text(selectedSource==null?"关注动态":"此来源的消息",12,0xffc6e4de,true));gap(stat,6);TextView count=text(String.valueOf(unread),34,CARD,true);add(stat,count);gap(stat,4);add(stat,text("条未读消息",12,0xffd1e8e4,false));infoRow.addView(stat,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout rhs=column();rhs.setGravity(Gravity.END);TextView badge=text((selectedSource==null?src.size():1)+" 个来源",12,CARD,true);pad(badge,12,7,12,7);badge.setBackground(shape(0x337fffff,14));rhs.addView(badge,new LinearLayout.LayoutParams(-2,-2));gap(rhs,10);Store.Source chosen=sourceById(selectedSource);long last=chosen==null?store.lastRefresh():chosen.checked;TextView state=text(lastRunning?"正在更新…":last>0?"更新于 "+format(last,"HH:mm"):"等待首次更新",11,0xffd1e8e4,false);rhs.addView(state,new LinearLayout.LayoutParams(-2,-2));infoRow.addView(rhs,new LinearLayout.LayoutParams(-2,-2));add(overview,infoRow);add(page,overview);gap(page,16);
        LinearLayout controls=row();LinearLayout segment=row();segment.setBackground(shape(0xffe9eef0,18));pad(segment,4,4,4,4);TextView all=action("全部 "+posts.size(),!unreadOnly,()->{unreadOnly=false;render(false);});all.setBackground(touchShape(!unreadOnly?CARD:0xffe9eef0,14));all.setTextColor(!unreadOnly?GREEN:MUTED);segment.addView(all,new LinearLayout.LayoutParams(-2,-2));
        TextView unseen=action("未读 "+unread,unreadOnly,()->{unreadOnly=true;render(false);});unseen.setBackground(touchShape(unreadOnly?CARD:0xffe9eef0,14));unseen.setTextColor(unreadOnly?GREEN:MUTED);segment.addView(unseen,new LinearLayout.LayoutParams(-2,-2));controls.addView(segment,new LinearLayout.LayoutParams(-2,-2));controls.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        TextView mark=text("全部已读",12,GREEN,true);mark.setGravity(Gravity.CENTER);pad(mark,8,10,3,10);mark.setOnClickListener(v->{store.readSource(selectedSource);render(true);});controls.addView(mark);add(page,controls);gap(page,12);
        List<Store.Post> visible=new ArrayList<>();for(Store.Post p:posts)if(!unreadOnly||!p.read)visible.add(p);
        if(visible.isEmpty()){empty(unreadOnly?"没有未读消息":"暂未收到消息",unreadOnly?"你已经读完了当前的消息。":"刷新获取公告，或在订阅页检查网站状态。",unreadOnly?"查看全部":"立即检查",()->{if(unreadOnly){unreadOnly=false;render(false);}else refresh(selectedSource);});return;}
        int limit=Math.min(feedLimit,visible.size());for(int n=0;n<limit;n++){
            Store.Post p=visible.get(n);LinearLayout c=card(), meta=row();View icon=avatar(p.source,p.sourceName,15);icon.setOnClickListener(v->selectSource(p.source));meta.addView(icon,new LinearLayout.LayoutParams(d(40),d(40)));
            LinearLayout names=column();pad(names,11,0,4,0);TextView name=text(p.sourceName,13,TEXT,true);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);add(names,name);gap(names,5);add(names,text(p.date>0?format(p.date,p.shortPost?"MM-dd HH:mm":"yyyy.MM.dd"):"收录于 "+format(p.found,"MM-dd HH:mm"),11,MUTED,false));meta.addView(names,new LinearLayout.LayoutParams(0,-2,1));names.setOnClickListener(v->selectSource(p.source));
            if(p.shortPost){TextView kind=text("动态",11,GREEN,true);pad(kind,8,5,8,5);kind.setBackground(shape(PALE,9));meta.addView(kind);View sp=new View(this);meta.addView(sp,new LinearLayout.LayoutParams(d(6),1));}
            if(!p.read){View dot=new View(this);dot.setBackground(shape(0xff24a397,5));dot.setContentDescription("未读");meta.addView(dot,new LinearLayout.LayoutParams(d(9),d(9)));}add(c,meta);gap(c,14);
            TextView title=text(p.title,18,p.read?0xff52636b:TEXT,true);title.setLineSpacing(d(4),1);title.setMaxLines(5);title.setEllipsize(android.text.TextUtils.TruncateAt.END);add(c,title);
            if(!p.summary.isEmpty()){gap(c,9);TextView desc=text(p.summary,14,MUTED,false);desc.setLineSpacing(d(4),1);desc.setMaxLines(p.shortPost?8:3);desc.setEllipsize(android.text.TextUtils.TruncateAt.END);add(c,desc);}
            gap(c,14);LinearLayout foot=row();TextView read=text("应用内阅读",12,GREEN,true);foot.addView(read);foot.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));UiIcon arrow=new UiIcon(this,"next",GREEN,false);foot.addView(arrow,new LinearLayout.LayoutParams(d(19),d(19)));add(c,foot);
            c.setForeground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x17126e65),null,shape(CARD,22)));c.setOnClickListener(v->{store.read(p.id);readPost(p);render(true);});add(page,c);gap(page,11);
        }if(limit<visible.size())add(page,action("加载更多",false,()->{feedLimit+=60;render(true);}));
    }
    int feedLimit=60;
    void empty(String title,String sub,String cta,Runnable click){LinearLayout c=card();pad(c,22,32,22,32);FrameLayout iconBox=new FrameLayout(this);iconBox.setBackground(shape(PALE,20));UiIcon icon=new UiIcon(this,"feed",GREEN,true);iconBox.addView(icon,new FrameLayout.LayoutParams(d(35),d(35),Gravity.CENTER));LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(d(67),d(67));ilp.gravity=Gravity.CENTER;c.addView(iconBox,ilp);gap(c,10);TextView a=text(title,20,TEXT,true);a.setGravity(Gravity.CENTER);add(c,a);gap(c,8);TextView b=text(sub,14,MUTED,false);b.setGravity(Gravity.CENTER);b.setLineSpacing(d(3),1);add(c,b);gap(c,20);add(c,action(cta,true,click));add(page,c);}
    void sources(){
        List<Store.Source> list=store.sources();if(list.isEmpty()){empty("订阅你的世界","复制官网的通知公告页、博客主页或 RSS 地址，讯集会持续关注更新。","添加网站",()->addSource());return;}
        TextView caption=text("订阅来源  ·  "+list.size(),12,MUTED,true);pad(caption,4,0,4,10);add(page,caption);
        for(Store.Source s:list){
            LinearLayout c=card(),header=row();View ic=avatar(s.id,s.name,17);ic.setOnClickListener(v->iconDialog(s.id));header.addView(ic,new LinearLayout.LayoutParams(d(48),d(48)));
            LinearLayout nameBox=column();pad(nameBox,12,0,6,0);TextView label=text(s.name,17,TEXT,true);label.setMaxLines(2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);add(nameBox,label);gap(nameBox,5);TextView host=text(Collector.host(s.url),11,MUTED,false);host.setSingleLine(true);host.setEllipsize(android.text.TextUtils.TruncateAt.END);add(nameBox,host);header.addView(nameBox,new LinearLayout.LayoutParams(0,-2,1));nameBox.setOnClickListener(v->selectSource(s.id));
            boolean ok=s.error.isEmpty();TextView status=text(ok?(s.initialized?"正常":"待检查"):"异常",11,ok?GREEN:0xffb14c3c,true);pad(status,9,6,9,6);status.setBackground(shape(ok?PALE:0xffffeeeb,10));header.addView(status);add(c,header);
            gap(c,13);TextView url=text(s.url,12,MUTED,false);url.setMaxLines(2);url.setEllipsize(android.text.TextUtils.TruncateAt.END);add(c,url);gap(c,8);
            String time=s.checked==0?"尚未检查":format(s.checked,"MM月dd日 HH:mm")+" · 已识别 "+s.count+" 条";add(c,text(ok?time:s.error,12,ok?MUTED:0xffb14c3c,false));gap(c,14);
            LinearLayout buttons=row();buttons.addView(action("检查",false,()->refresh(s.id)),new LinearLayout.LayoutParams(0,-2,1));View sp=new View(this);buttons.addView(sp,new LinearLayout.LayoutParams(d(7),1));buttons.addView(action("网页",false,()->open(s.url)),new LinearLayout.LayoutParams(0,-2,1));View sp2=new View(this);buttons.addView(sp2,new LinearLayout.LayoutParams(d(7),1));buttons.addView(action("更多",false,()->sourceMenu(s)),new LinearLayout.LayoutParams(0,-2,1));add(c,buttons);add(page,c);gap(page,11);
        }
    }
    final class MenuSheet {
        final Dialog dialog=new Dialog(MainActivity.this);
        final LinearLayout content=column();
        MenuSheet(String title,String sub,Store.Source source){
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout outer=column();pad(outer,10,0,10,10);content.setBackground(shape(CARD,28));pad(content,20,10,20,16);outer.addView(content,new LinearLayout.LayoutParams(-1,-2));dialog.setContentView(outer);dialog.setCanceledOnTouchOutside(true);
            View handle=new View(MainActivity.this);handle.setBackground(shape(0xffd3dddf,4));LinearLayout.LayoutParams h=new LinearLayout.LayoutParams(d(38),d(4));h.gravity=Gravity.CENTER;content.addView(handle,h);gap(content,18);
            LinearLayout header=row();if(source!=null){View icon=avatar(source.id,source.name,18);header.addView(icon,new LinearLayout.LayoutParams(d(51),d(51)));View sp=new View(MainActivity.this);header.addView(sp,new LinearLayout.LayoutParams(d(13),1));}
            LinearLayout words=column();TextView heading=text(title,20,TEXT,true);heading.setMaxLines(2);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);add(words,heading);if(sub!=null&&!sub.isEmpty()){gap(words,5);TextView desc=text(sub,12,MUTED,false);desc.setMaxLines(2);desc.setEllipsize(android.text.TextUtils.TruncateAt.END);add(words,desc);}header.addView(words,new LinearLayout.LayoutParams(0,-2,1));header.addView(iconAction("close","关闭菜单",false,()->dialog.dismiss()),new LinearLayout.LayoutParams(d(38),d(38)));add(content,header);gap(content,17);
        }
        void option(String glyph,String title,String subtitle,boolean danger,Runnable click){
            LinearLayout item=row();item.setBackground(touchShape(danger?0xfffff4f1:0xfff5f7f8,19));pad(item,12,12,12,12);FrameLayout tile=new FrameLayout(MainActivity.this);tile.setBackground(shape(danger?0xffffe4df:PALE,13));UiIcon icon=new UiIcon(MainActivity.this,glyph,danger?0xffb14c3c:GREEN,true);tile.addView(icon,new FrameLayout.LayoutParams(d(23),d(23),Gravity.CENTER));item.addView(tile,new LinearLayout.LayoutParams(d(43),d(43)));LinearLayout copy=column();pad(copy,12,0,8,0);add(copy,text(title,15,danger?0xffb14c3c:TEXT,true));gap(copy,5);add(copy,text(subtitle,11,MUTED,false));item.addView(copy,new LinearLayout.LayoutParams(0,-2,1));UiIcon arrow=new UiIcon(MainActivity.this,"next",danger?0xffb14c3c:MUTED,false);item.addView(arrow,new LinearLayout.LayoutParams(d(18),d(18)));item.setOnClickListener(v->{dialog.dismiss();click.run();});add(content,item);gap(content,8);
        }
        void show(){dialog.show();Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setDimAmount(.42f);w.setGravity(Gravity.BOTTOM);w.setLayout(-1,-2);}}
    }
    void sourceMenu(Store.Source s){
        MenuSheet menu=new MenuSheet(s.name,Collector.host(s.url),s);
        menu.option("feed","查看信息流","查看这个网站已收录的消息",false,()->selectSource(s.id));
        menu.option("image","自定义网站图标","选择相册图片，裁剪后保存",false,()->iconDialog(s.id));
        menu.option("trash","取消订阅","移除网站和本地收录的消息",true,()->confirmDelete(s));menu.show();
    }
    void confirmDelete(Store.Source s){
        MenuSheet menu=new MenuSheet("取消订阅？","将删除“"+s.name+"”及其已收录消息。",s);
        menu.option("trash","确认取消订阅","此操作会删除此订阅的本地记录",true,()->{store.delete(s.id);SiteIcons.remove(this,s.id);icons.remove(s.id);RefreshJob.schedule(this,true);render(false);});
        menu.option("close","保留订阅","不做任何更改",false,()->{});menu.show();
    }
    void settings(){
        TextView section=text("信息更新",12,MUTED,true);pad(section,4,0,0,9);add(page,section);
        LinearLayout c=card(),period=row();LinearLayout label=column();add(label,text("自动检查频率",16,TEXT,true));gap(label,6);add(label,text("系统省电策略可能推迟后台任务",12,MUTED,false));period.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        String[] options={"15 分钟","30 分钟","1 小时","3 小时","6 小时"};int[] intervals={15,30,60,180,360};int idx=0;for(int i=0;i<intervals.length;i++)if(intervals[i]==store.interval())idx=i;final int selected=idx;period.addView(action(options[idx]+"  ›",false,()->new AlertDialog.Builder(this).setTitle("自动检查频率").setSingleChoiceItems(options,selected,(dialog,i)->{store.setInterval(intervals[i]);RefreshJob.schedule(this,true);dialog.dismiss();render(true);}).setNegativeButton("取消",null).show()));add(c,period);gap(c,16);line(c);gap(c,16);
        LinearLayout nr=row(),nb=column();add(nb,text("新消息通知",16,TEXT,true));gap(nb,6);add(nb,text("首次导入历史公告时不通知",12,MUTED,false));nr.addView(nb,new LinearLayout.LayoutParams(0,-2,1));Switch sw=new Switch(this);sw.setChecked(store.notifyEnabled());sw.setOnCheckedChangeListener((v,b)->{store.setNotify(b);if(b)permission();});nr.addView(sw);add(c,nr);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){gap(c,12);add(c,text("系统通知权限尚未开启。",12,0xffb14c3c,false));gap(c,8);add(c,action("开启通知权限",false,()->permission()));}add(page,c);gap(page,20);
        TextView sync=text("数据与同步",12,MUTED,true);pad(sync,4,0,0,9);add(page,sync);cloud.card(page);gap(page,10);
        TextView app=text("关于应用",12,MUTED,true);pad(app,4,0,0,9);add(page,app);
        LinearLayout about=card(),ar=row(),av=column();FrameLayout brand=new FrameLayout(this);brand.setBackground(shape(GREEN,16));UiIcon logo=new UiIcon(this,"feed",CARD,true);brand.addView(logo,new FrameLayout.LayoutParams(d(32),d(32),Gravity.CENTER));ar.addView(brand,new LinearLayout.LayoutParams(d(56),d(56)));pad(av,13,0,0,0);add(av,text("讯集",20,TEXT,true));gap(av,6);add(av,text("版本 "+updater.installed()+" · "+updater.installedCode(),12,MUTED,false));ar.addView(av,new LinearLayout.LayoutParams(0,-2,1));add(about,ar);gap(about,15);add(about,text("让你关注的世界，尽在一个信息流。",13,MUTED,false));gap(about,14);add(about,action("检查应用更新",true,()->updater.check()));add(page,about);
    }
    void addSource(){LinearLayout box=column();pad(box,20,6,20,4);EditText url=new EditText(this);url.setSingleLine(true);url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);url.setHint("https://… 网站或订阅地址");url.setBackground(shape(BG,13));pad(url,13,12,13,12);url.setTextColor(TEXT);EditText name=new EditText(this);name.setSingleLine(true);name.setHint("网站名称（留空自动识别）");name.setBackground(shape(BG,13));pad(name,13,12,13,12);name.setTextColor(TEXT);add(box,url);gap(box,8);add(box,name);gap(box,8);TextView tip=text("网站名称留空，将自动获取原站名称及图标。支持公告列表、博客主页、说说栏目及 RSS 地址。",12,MUTED,false);add(box,tip);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("添加网站").setView(box).setNegativeButton("取消",null).setPositiveButton("添加",null).create();dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String u=Collector.normalize(url.getText().toString());if(u==null){url.setError("请输入有效的 http 或 https 网址");return;}String n=name.getText().toString().trim();if(n.length()>40){name.setError("名称最多 40 个字符");return;}saveSource(dialog,url,n,u);}));dialog.show();}
    void saveSource(AlertDialog dialog,EditText url,String n,String u){String error=store.add(n,u);if(error!=null){url.setError(error);return;}dialog.dismiss();RefreshJob.schedule(this,true);tab=1;render(false);if(store.sources().size()==1&&store.notifyEnabled())permission();String id=null;for(Store.Source s:store.sources())if(s.url.equals(u)){id=s.id;break;}refresh(id);}

    void refresh(String id){if(store.sources().isEmpty()){addSource();return;}if(RefreshJob.running.get()){Toast.makeText(this,"正在检查，请稍候",Toast.LENGTH_SHORT).show();return;}new Thread(()->{int result=RefreshJob.refresh(getApplicationContext(),id);runOnUiThread(()->{if(isFinishing()||isDestroyed())return;render(true);if(result>=0){int errors=0;for(Store.Source s:store.sources())if(!s.error.isEmpty())errors++;Toast.makeText(this,result>0?"收到 "+result+" 条新消息":errors>0?"检查完成，部分网站存在异常":"检查完成",Toast.LENGTH_SHORT).show();}});},"xunji-manual").start();handler.postDelayed(()->render(true),160);}
    void readPost(Store.Post post){Store.Source s=sourceById(post.source);Intent i=new Intent(this,ReaderActivity.class);i.putExtra("url",Collector.repairPlaceholder(post.url,s==null?null:s.url)).putExtra("title",post.title);startActivity(i);}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("iconTarget",iconTarget);}
    void iconDialog(String id){
        Store.Source s=sourceById(id);if(s==null)return;
        MenuSheet menu=new MenuSheet("网站图标",s.name,s);
        menu.option("image","从相册选择图片","进入正方形裁剪界面，再确认保存",false,()->pickIcon(id));
        if(SiteIcons.hasCustom(this,id))menu.option("refresh","恢复自动图标","使用网站自己的图标或文字头像",false,()->{SiteIcons.resetCustom(this,id);icons.remove(id);render(true);});
        menu.show();
    }
    void pickIcon(String id){iconTarget=id;Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");try{startActivityForResult(pick,PICK_ICON);}catch(Exception e){iconTarget=null;Toast.makeText(this,"无法打开图片选择器",Toast.LENGTH_SHORT).show();}}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request==AppUpdater.INSTALL_SETTINGS)updater.afterSettings();
        if(request==PICK_ICON){String id=iconTarget;iconTarget=null;if(result!=RESULT_OK||data==null||data.getData()==null||sourceById(id)==null)return;Intent edit=new Intent(this,IconCropActivity.class).setData(data.getData()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra("source",id);try{startActivityForResult(edit,CROP_ICON);}catch(Exception e){Toast.makeText(this,"无法打开裁剪界面",Toast.LENGTH_SHORT).show();}}
        if(request==CROP_ICON&&result==RESULT_OK&&data!=null){String id=data.getStringExtra("source");if(sourceById(id)!=null){icons.remove(id);render(true);Toast.makeText(this,"图标已更换",Toast.LENGTH_SHORT).show();}}
    }
    void open(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){Toast.makeText(this,"未找到浏览器",Toast.LENGTH_SHORT).show();}}
    void permission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){if(store.prefs.getBoolean("permissionAsked",false)&&!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)){startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));}else{store.prefs.edit().putBoolean("permissionAsked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);}}}
    String format(long date,String pattern){return new SimpleDateFormat(pattern,Locale.SIMPLIFIED_CHINESE).format(new Date(date));}
}
