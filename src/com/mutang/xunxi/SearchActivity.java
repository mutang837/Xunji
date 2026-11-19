package com.mutang.xunxi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Search the local feed without fetching webpages or changing the main feed filter. */
public final class SearchActivity extends Activity {
    private Store store;
    private String scope;
    private List<Store.Post> posts=Collections.emptyList(),results=Collections.emptyList();
    private final Map<String,Store.Source> sources=new LinkedHashMap<>();
    private EditText input;
    private TextView scopeButton,count,emptyTitle,emptyDetail;
    private FrameLayout clear;
    private ListView list;
    private ResultAdapter adapter;
    private long revision=-1;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable search=()->filter(false);
    private final Runnable poll=new Runnable(){public void run(){if(store.revision()!=revision){snapshot();filter(true);}handler.postDelayed(this,1800);}};
    private final SimpleDateFormat dateFormat=new SimpleDateFormat("yyyy.MM.dd",Locale.CHINA);
    private int d(int n){return ReaderUi.dp(this,n);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);if(bold)v.setTypeface(Typeface.create("sans-serif-medium",0));return v;}
    private void pad(View v,int l,int t,int r,int b){v.setPadding(d(l),d(t),d(r),d(b));}
    private android.graphics.drawable.GradientDrawable card(){android.graphics.drawable.GradientDrawable bg=ReaderUi.shape(this,MainActivity.CARD,18);bg.setStroke(d(1),MainActivity.LINE);return bg;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new Store(this);scope=state==null?getIntent().getStringExtra("source"):state.getString("scope");
        getWindow().setStatusBarColor(MainActivity.BG);getWindow().setNavigationBarColor(MainActivity.CARD);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root=column();root.setBackgroundColor(MainActivity.BG);root.setOnApplyWindowInsetsListener((v,i)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=i.getInsets(WindowInsets.Type.systemBars()),ime=i.getInsets(WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));}else v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});setContentView(root);
        LinearLayout header=row();pad(header,16,12,16,12);TextView back=ReaderUi.button(this,"返回",false);back.setOnClickListener(v->finish());header.addView(back);TextView title=text("搜索信息",23,MainActivity.TEXT,true);pad(title,14,0,0,0);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
        LinearLayout tools=column();pad(tools,16,0,16,10);
        LinearLayout field=row();field.setBackground(card());pad(field,13,1,6,1);UiIcon icon=new UiIcon(this,"search",MainActivity.MUTED,false);field.addView(icon,new LinearLayout.LayoutParams(d(22),d(22)));
        input=new EditText(this);input.setSingleLine(true);input.setTextSize(16);input.setTextColor(MainActivity.TEXT);input.setHintTextColor(MainActivity.MUTED);input.setHint("搜索标题、摘要、网站");input.setBackgroundColor(android.graphics.Color.TRANSPARENT);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);input.setSelectAllOnFocus(false);pad(input,12,11,4,11);field.addView(input,new LinearLayout.LayoutParams(0,d(52),1));
        clear=new FrameLayout(this);clear.setContentDescription("清空搜索");clear.setBackground(ReaderUi.shape(this,MainActivity.PALE,12));UiIcon cross=new UiIcon(this,"close",MainActivity.GREEN,false);clear.addView(cross,new FrameLayout.LayoutParams(d(19),d(19),Gravity.CENTER));clear.setOnClickListener(v->{input.setText("");input.requestFocus();showKeyboard();});field.addView(clear,new LinearLayout.LayoutParams(d(38),d(38)));tools.addView(field);
        LinearLayout control=row();pad(control,0,10,0,0);scopeButton=ReaderUi.button(this,"全部订阅  ▾",false);scopeButton.setSingleLine(true);scopeButton.setEllipsize(TextUtils.TruncateAt.END);scopeButton.setOnClickListener(v->chooseScope());control.addView(scopeButton,new LinearLayout.LayoutParams(0,-2,1));count=text("",12,MainActivity.MUTED,false);pad(count,10,0,2,0);control.addView(count);tools.addView(control);root.addView(tools);
        FrameLayout content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));list=new ListView(this);list.setDivider(null);list.setVerticalScrollBarEnabled(false);list.setClipToPadding(false);list.setPadding(d(16),0,d(16),d(12));list.setSelector(android.R.color.transparent);adapter=new ResultAdapter();list.setAdapter(adapter);content.addView(list,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout empty=column();empty.setGravity(Gravity.CENTER);pad(empty,26,16,26,16);FrameLayout badge=new FrameLayout(this);badge.setBackground(ReaderUi.shape(this,MainActivity.PALE,20));badge.addView(new UiIcon(this,"search",MainActivity.GREEN,true),new FrameLayout.LayoutParams(d(34),d(34),Gravity.CENTER));empty.addView(badge,new LinearLayout.LayoutParams(d(65),d(65)));emptyTitle=text("搜索已收录的信息",20,MainActivity.TEXT,true);emptyTitle.setGravity(Gravity.CENTER);pad(emptyTitle,0,15,0,8);empty.addView(emptyTitle);emptyDetail=text("输入关键词查找历史公告、博客文章和动态。",14,MainActivity.MUTED,false);emptyDetail.setGravity(Gravity.CENTER);emptyDetail.setLineSpacing(d(4),1);empty.addView(emptyDetail);content.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int c,int after){}public void onTextChanged(CharSequence s,int start,int before,int c){clear.setVisibility(s.length()==0?View.INVISIBLE:View.VISIBLE);handler.removeCallbacks(search);handler.postDelayed(search,140);}public void afterTextChanged(Editable e){}});
        input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH||(event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER&&event.getAction()==KeyEvent.ACTION_DOWN)){handler.removeCallbacks(search);filter(false);hideKeyboard();input.clearFocus();return true;}return false;});
        list.setOnItemClickListener((parent,v,position,id)->{if(position>=results.size())return;Store.Post p=results.get(position);Store.Source s=sources.get(p.source);hideKeyboard();input.clearFocus();store.read(p.id);startActivity(new Intent(this,ReaderActivity.class).putExtra("url",Collector.repairPlaceholder(p.url,s==null?null:s.url)).putExtra("title",p.title));});
        snapshot();if(state!=null){input.setText(state.getString("query",""));input.setSelection(input.length());}clear.setVisibility(input.length()==0?View.INVISIBLE:View.VISIBLE);handler.removeCallbacks(search);filter(false);if(state!=null){list.setSelectionFromTop(state.getInt("position"),state.getInt("offset"));}else{input.requestFocus();input.postDelayed(this::showKeyboard,180);}
    }
    @Override protected void onResume(){super.onResume();if(store.revision()!=revision){snapshot();filter(true);}handler.removeCallbacks(poll);handler.postDelayed(poll,1800);}
    @Override protected void onPause(){handler.removeCallbacks(poll);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("scope",scope);state.putString("query",input.getText().toString());state.putInt("position",list.getFirstVisiblePosition());View first=list.getChildAt(0);state.putInt("offset",first==null?0:first.getTop());}
    private void showKeyboard(){if(!isFinishing()&&input.hasFocus())((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);}
    private void hideKeyboard(){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);}
    private void snapshot(){synchronized(Store.LOCK){posts=store.posts();sources.clear();for(Store.Source s:store.sources())sources.put(s.id,s);revision=store.revision();}if(scope!=null&&!sources.containsKey(scope))scope=null;}
    private void chooseScope(){hideKeyboard();ArrayList<String> ids=new ArrayList<>(),labels=new ArrayList<>();ids.add(null);labels.add("全部订阅");for(Store.Source s:sources.values()){ids.add(s.id);labels.add(s.name);}new AlertDialog.Builder(this).setTitle("搜索范围").setSingleChoiceItems(labels.toArray(new String[0]),ids.indexOf(scope),(dialog,which)->{scope=ids.get(which);dialog.dismiss();handler.removeCallbacks(search);filter(false);}).setNegativeButton("取消",null).show();}
    private void filter(boolean keepPosition){
        if(input==null)return;int oldPos=list.getFirstVisiblePosition();View first=list.getChildAt(0);int oldTop=first==null?0:first.getTop();PostSearch.Query query=new PostSearch.Query(input.getText().toString());
        ArrayList<Store.Post> found=new ArrayList<>();if(!query.isEmpty())for(Store.Post p:posts){if(!PostSearch.inScope(scope,p.source))continue;Store.Source site=sources.get(p.source);if(query.matches(p.title,p.summary,site==null?p.sourceName:site.name,site==null?Collector.host(p.url):Collector.host(site.url)))found.add(p);}
        results=found;scopeButton.setText((scope==null?"全部订阅":sources.get(scope).name)+"  ▾");count.setText(query.isEmpty()?"本地历史":"找到 "+found.size()+" 条");emptyTitle.setText(query.isEmpty()?"搜索已收录的信息":"没有找到匹配的信息");emptyDetail.setText(query.isEmpty()?"输入关键词查找历史公告、博客文章和动态。":"试试换个关键词、减少关键词，或切换为全部订阅。");adapter.notifyDataSetChanged();list.setSelectionFromTop(keepPosition?Math.min(oldPos,Math.max(0,results.size()-1)):0,keepPosition?oldTop:0);
    }
    private final class ResultAdapter extends BaseAdapter{
        @Override public int getCount(){return results.size();}
        @Override public Store.Post getItem(int p){return results.get(p);}
        @Override public long getItemId(int p){return p;}
        @Override public View getView(int position,View convert,android.view.ViewGroup parent){
            Holder h;if(convert==null){h=new Holder();LinearLayout outer=column();pad(outer,0,0,0,10);LinearLayout c=column();c.setBackground(card());pad(c,16,15,16,15);outer.addView(c,new LinearLayout.LayoutParams(-1,-2));LinearLayout meta=row();h.site=text("",12,MainActivity.GREEN,true);h.site.setSingleLine(true);h.site.setEllipsize(TextUtils.TruncateAt.END);meta.addView(h.site,new LinearLayout.LayoutParams(0,-2,1));h.date=text("",11,MainActivity.MUTED,false);meta.addView(h.date);c.addView(meta);h.title=text("",17,MainActivity.TEXT,true);h.title.setMaxLines(3);h.title.setEllipsize(TextUtils.TruncateAt.END);h.title.setLineSpacing(d(3),1);pad(h.title,0,9,0,0);c.addView(h.title);h.summary=text("",13,MainActivity.MUTED,false);h.summary.setMaxLines(3);h.summary.setEllipsize(TextUtils.TruncateAt.END);h.summary.setLineSpacing(d(3),1);pad(h.summary,0,7,0,0);c.addView(h.summary);outer.setTag(h);convert=outer;}else h=(Holder)convert.getTag();Store.Post p=getItem(position);Store.Source s=sources.get(p.source);h.site.setText((s==null?p.sourceName:s.name)+(p.shortPost?" · 动态":""));h.date.setText((p.read?"":"●  ")+dateFormat.format(new Date(p.order())));h.title.setText(p.title);h.title.setTextColor(p.read?0xff52636b:MainActivity.TEXT);h.summary.setText(p.summary);h.summary.setVisibility(p.summary.isEmpty()?View.GONE:View.VISIBLE);return convert;
        }
    }
    private static final class Holder{TextView site,date,title,summary;}
}
