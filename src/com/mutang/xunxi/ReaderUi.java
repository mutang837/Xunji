package com.mutang.xunxi;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.TextView;

final class ReaderUi {
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable shape(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    static TextView button(Context c,String label,boolean primary){
        TextView v=new TextView(c);v.setText(label);v.setTextSize(13);v.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));v.setGravity(Gravity.CENTER);v.setIncludeFontPadding(false);v.setMinHeight(dp(c,44));v.setPadding(dp(c,13),dp(c,10),dp(c,13),dp(c,10));v.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{MainActivity.MUTED,primary?MainActivity.CARD:MainActivity.GREEN}));v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22126e65),shape(c,primary?MainActivity.GREEN:MainActivity.PALE,15),shape(c,0xffffffff,15)));return v;
    }
}
