package com.mutang.xunxi;

public final class PostSearchTest {
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        check(new PostSearch.Query("四级").matches("英语四级报名通知","","考试网","example.org"),"Chinese partial title");
        check(new PostSearch.Query("报名 河北").matches("初级会计报名","","河北省财政厅","example.org"),"multiple terms in different fields");
        check(new PostSearch.Query("CET ４").matches("cet 4","","",""),"case and full width");
        check(new PostSearch.Query("考试　　报名").matches("考试","报名",null,null),"ideographic spaces");
        check(new PostSearch.Query("github.io").matches("博客日记","","博客","example.github.io"),"domain match");
        check(!new PostSearch.Query("报名 结束").matches("报名开始","","考试网",""),"all keywords required");
        check(!new PostSearch.Query("  　").matches("anything","","",""),"empty search not all entries");
        check(PostSearch.inScope(null,"a")&&PostSearch.inScope("a","a")&&!PostSearch.inScope("a","b"),"source scope");
        System.out.println("Local search matching tests passed");
    }
}
