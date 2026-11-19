package com.mutang.xunxi;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Local keyword matching; all whitespace-separated terms must appear in the stored fields. */
final class PostSearch {
    private static final Pattern SPACE=Pattern.compile("[\\s\\p{Z}]+");
    static String normalize(String s){return s==null?"":SPACE.matcher(Normalizer.normalize(s,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)).replaceAll(" ").trim();}
    static final class Query {
        private final String[] words;
        Query(String input){String q=normalize(input);words=q.isEmpty()?new String[0]:q.split(" ");}
        boolean isEmpty(){return words.length==0;}
        boolean matches(String title,String summary,String source,String host){
            if(isEmpty())return false;
            String text=normalize(title)+"\n"+normalize(summary)+"\n"+normalize(source)+"\n"+normalize(host);
            for(String word:words)if(!text.contains(word))return false;
            return true;
        }
    }
    static boolean inScope(String scope,String source){return scope==null||scope.equals(source);}
}
