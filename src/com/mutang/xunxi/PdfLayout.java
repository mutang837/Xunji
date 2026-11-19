package com.mutang.xunxi;

/** Pixel geometry shared by both reading modes; independent of Android. */
final class PdfLayout {
    final float[] widths, heights, tops;
    final float viewportW, viewportH, gap;
    final boolean continuous;
    final int selected;
    float contentW, contentH;

    PdfLayout(int[] w,int[] h,float vw,float vh,float margin,boolean continuous,boolean fitWidth,float zoom,int selected){
        this.continuous=continuous;this.selected=selected;viewportW=vw;viewportH=vh;gap=margin;
        widths=new float[w.length];heights=new float[w.length];tops=new float[w.length];
        contentW=vw;float bottom=margin;
        for(int i=0;i<w.length;i++){
            float fit=Math.max(1,vw-2*margin)/Math.max(1,w[i]);
            if(!fitWidth)fit=Math.min(fit,Math.max(1,vh-2*margin)/Math.max(1,h[i]));
            float scale=fit*zoom;
            widths[i]=w[i]*scale;heights[i]=h[i]*scale;
            if(continuous||i==selected)contentW=Math.max(contentW,widths[i]+2*margin);
            if(continuous){tops[i]=bottom;bottom+=heights[i]+margin;}
        }
        if(continuous)contentH=Math.max(vh,bottom);
        else{contentH=Math.max(vh,heights[selected]+2*margin);tops[selected]=(contentH-heights[selected])/2;}
    }
    float left(int page){return (contentW-widths[page])/2;}
    float clampX(float x){return Math.max(0,Math.min(x,contentW-viewportW));}
    float clampY(float y){return Math.max(0,Math.min(y,contentH-viewportH));}
    int pageAt(float y){
        if(!continuous)return selected;
        int lo=0,hi=tops.length-1;
        while(lo<hi){int mid=(lo+hi)/2;if(y<tops[mid]+heights[mid]+gap/2)hi=mid;else lo=mid+1;}
        return lo;
    }
}
