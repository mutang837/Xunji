package com.mutang.xunxi;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Do not mistake an HTML error page or download wrapper for a PDF. */
final class PdfDocumentType {
    enum Kind { PDF, HTML, GZIP, OTHER, EMPTY }
    static Kind identify(File file)throws IOException{
        byte[] header=new byte[1024];int n;
        try(InputStream in=new FileInputStream(file)){n=in.read(header);}
        return identify(header,n);
    }
    static Kind identify(byte[] header,int n){
        if(n<=0)return Kind.EMPTY;
        if(n>=2&&(header[0]&255)==0x1f&&(header[1]&255)==0x8b)return Kind.GZIP;
        String s=new String(header,0,n,StandardCharsets.ISO_8859_1);
        if(s.startsWith("%PDF-"))return Kind.PDF;
        String lower=s.trim().toLowerCase(Locale.ROOT);
        if(lower.startsWith("<!doctype html")||lower.startsWith("<html")||lower.startsWith("<head")||lower.startsWith("<body")||lower.startsWith("<script")||lower.contains("<html"))return Kind.HTML;
        if(s.contains("%PDF-"))return Kind.PDF;
        return Kind.OTHER;
    }
}
