package com.mutang.xunxi;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Consistent 24dp outline icons, drawn at the current display density. */
final class UiIcon extends View {
    final String kind;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final int color;final boolean selected;
    UiIcon(Context c,String k,int tint,boolean active){super(c);kind=k;color=tint;selected=active;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float size=Math.min(getWidth(),getHeight());canvas.save();canvas.translate((getWidth()-size)/2,(getHeight()-size)/2);canvas.scale(size/24f,size/24f);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(selected?2.15f:1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        switch(kind){
            case "feed":
                canvas.drawRoundRect(4,3,20,21,3,3,p);canvas.drawLine(8,8,16,8,p);canvas.drawLine(8,12,16,12,p);canvas.drawLine(8,16,13,16,p);break;
            case "bookmark":{
                Path q=new Path();q.moveTo(6,20);q.lineTo(6,5);q.quadTo(6,3,8,3);q.lineTo(16,3);q.quadTo(18,3,18,5);q.lineTo(18,20);q.lineTo(12,16);q.close();canvas.drawPath(q,p);break;}
            case "settings":
                canvas.drawCircle(12,12,3,p);canvas.drawCircle(12,12,7,p);for(int i=0;i<8;i++){float a=(float)(Math.PI*i/4);float x=(float)Math.cos(a),y=(float)Math.sin(a);canvas.drawLine(12+x*7,12+y*7,12+x*9,12+y*9,p);}break;
            case "menu":canvas.drawLine(4,7,20,7,p);canvas.drawLine(4,12,17,12,p);canvas.drawLine(4,17,13,17,p);break;
            case "refresh":
                canvas.drawArc(4,4,20,20,30,300,false,p);canvas.drawLine(20,4,20,10,p);canvas.drawLine(20,10,14,10,p);break;
            case "add":canvas.drawLine(12,5,12,19,p);canvas.drawLine(5,12,19,12,p);break;
            case "back":canvas.drawLine(14.5f,5,7.5f,12,p);canvas.drawLine(7.5f,12,14.5f,19,p);break;
            case "next":canvas.drawLine(9,6,15,12,p);canvas.drawLine(15,12,9,18,p);break;
            case "external":canvas.drawLine(7,17,17,7,p);canvas.drawLine(10,7,17,7,p);canvas.drawLine(17,7,17,14,p);break;
            case "image":canvas.drawRoundRect(3,4,21,20,3,3,p);canvas.drawCircle(8,9,1.5f,p);canvas.drawLine(3,17,9,12,p);canvas.drawLine(9,12,13,16,p);canvas.drawLine(13,16,17,11,p);canvas.drawLine(17,11,21,15,p);break;
            case "trash":canvas.drawLine(4,6,20,6,p);canvas.drawLine(9,6,9,3,p);canvas.drawLine(9,3,15,3,p);canvas.drawLine(15,3,15,6,p);canvas.drawRoundRect(6,6,18,21,2,2,p);canvas.drawLine(10,10,10,17,p);canvas.drawLine(14,10,14,17,p);break;
            case "check":canvas.drawLine(5,12,10,17,p);canvas.drawLine(10,17,19,7,p);break;
            case "close":canvas.drawLine(6,6,18,18,p);canvas.drawLine(18,6,6,18,p);break;
            default:canvas.drawRoundRect(5,5,19,19,4,4,p);break;
        }canvas.restore();
    }
}
