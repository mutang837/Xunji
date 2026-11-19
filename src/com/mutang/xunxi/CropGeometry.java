package com.mutang.xunxi;

/** Image transform in crop-frame coordinates. The square can never expose empty space. */
final class CropGeometry {
    final float width,height,side,minimum;
    float scale,x,y;
    CropGeometry(float w,float h,float frame){if(w<=0||h<=0||frame<=0)throw new IllegalArgumentException();width=w;height=h;side=frame;minimum=Math.max(side/width,side/height);reset();}
    void reset(){scale=minimum;x=(side-width*scale)/2;y=(side-height*scale)/2;clamp();}
    void pan(float dx,float dy){x+=dx;y+=dy;clamp();}
    void zoom(float factor,float fx,float fy){if(!Float.isFinite(factor)||factor<=0)return;float next=Math.max(minimum,Math.min(minimum*8,scale*factor));float ratio=next/scale;x=fx-(fx-x)*ratio;y=fy-(fy-y)*ratio;scale=next;clamp();}
    void clamp(){x=Math.min(0,Math.max(side-width*scale,x));y=Math.min(0,Math.max(side-height*scale,y));}
    float factor(){return scale/minimum;}
    float centerX(){return (side/2-x)/(width*scale);}
    float centerY(){return (side/2-y)/(height*scale);}
    void restore(float factor,float cx,float cy){if(!Float.isFinite(factor)||!Float.isFinite(cx)||!Float.isFinite(cy))return;scale=minimum*Math.max(1,Math.min(8,factor));x=side/2-cx*width*scale;y=side/2-cy*height*scale;clamp();}
}
