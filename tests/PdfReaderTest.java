package com.mutang.xunxi;

import java.nio.charset.StandardCharsets;

public final class PdfReaderTest {
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    static PdfDocumentType.Kind type(String s){byte[] bytes=s.getBytes(StandardCharsets.UTF_8);return PdfDocumentType.identify(bytes,bytes.length);}
    public static void main(String[] args){
        check(type("%PDF-1.7\n<html") == PdfDocumentType.Kind.PDF,"valid header even with HTML inside PDF");
        check(type("  <!DOCTYPE html><html>error</html>") == PdfDocumentType.Kind.HTML,"HTML download response");
        check(type("PK\u0003\u0004") == PdfDocumentType.Kind.OTHER,"zip is not PDF");
        check(type("") == PdfDocumentType.Kind.EMPTY,"empty response");
        check(PdfDocumentType.identify(new byte[]{31,(byte)139},2)==PdfDocumentType.Kind.GZIP,"gzip signature");
        int[] widths={600,600,900},heights={900,900,500};
        PdfLayout layout=new PdfLayout(widths,heights,400,700,8,true,true,1,0);
        check(layout.widths[0]==384,"fit width");
        check(layout.tops[1]>layout.tops[0]+layout.heights[0],"continuous gap");
        check(layout.pageAt(layout.tops[1]+10)==1,"page location");
        check(layout.clampY(100000)==layout.contentH-700,"scroll bound");
        PdfLayout single=new PdfLayout(widths,heights,400,700,8,false,false,1,2);
        check(single.pageAt(0)==2&&single.contentH==700,"whole-page mode");
        PdfLayout zoom=new PdfLayout(widths,heights,400,700,8,false,true,3,0);
        check(zoom.contentW>400&&zoom.contentH>700,"zoom and pan area");
        System.out.println("PDF reader geometry and file identification passed");
    }
}
