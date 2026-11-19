package com.mutang.xunxi;

import java.util.Random;
public final class CropGeometryTest {
    static void check(boolean yes,String what){if(!yes)throw new AssertionError(what);}
    static void covered(CropGeometry g){float e=.01f;check(g.x<=e&&g.y<=e&&g.x+g.width*g.scale>=g.side-e&&g.y+g.height*g.scale>=g.side-e,"Crop frame never exposes empty image edges");}
    public static void main(String[] args){
        for(float[] shape:new float[][]{{400,200},{200,400},{300,300},{8,2048},{2048,8}}){
            CropGeometry g=new CropGeometry(shape[0],shape[1],280);covered(g);
            check(Math.abs(g.centerX()-.5f)<.001f&&Math.abs(g.centerY()-.5f)<.001f,"Initial crop centered");
            Random random=new Random(5);for(int i=0;i<500;i++){g.pan(random.nextInt(900)-450,random.nextInt(900)-450);g.zoom(.3f+random.nextFloat()*2.5f,random.nextFloat()*280,random.nextFloat()*280);covered(g);}
            g.reset();g.zoom(2,140,140);g.pan(-40,30);CropGeometry re=new CropGeometry(shape[0],shape[1],400);re.restore(g.factor(),g.centerX(),g.centerY());covered(re);check(Math.abs(g.centerX()-re.centerX())<.002f&&Math.abs(g.centerY()-re.centerY())<.002f,"Restored crop normalized to new viewport");
            g.zoom(.0001f,140,140);covered(g);check(Math.abs(g.factor()-1)<.001f,"Zoom-out stops at cover scale");
        }System.out.println("PASS crop bounds, portrait/landscape, extreme aspect ratios, zoom limits and rotation restoration");
    }
}
