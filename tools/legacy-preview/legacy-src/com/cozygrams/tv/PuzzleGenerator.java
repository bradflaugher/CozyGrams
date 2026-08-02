package com.cozygrams.tv;

import java.util.Random;

public final class PuzzleGenerator {
    private static final String[] NAMES={"Sweetheart","Sleepy Kitty","Our Little Home","Cocoa Date","Moonlit Garden","Love in Bloom","Starlight Wish","Love Birds"};
    private PuzzleGenerator() {}
    public static Puzzle generate(long seed, int size) {
        if (size < 5 || size > 20) throw new IllegalArgumentException("size must be 5..20");
        Random r=new Random(seed); boolean[][] cells=new boolean[size][size];int kind=Math.floorMod((int)seed,NAMES.length);
        for(int y=0;y<size;y++)for(int x=0;x<size;x++){
            double nx=(x+.5)/size*2-1,ny=(y+.5)/size*2-1;boolean on;
            switch(kind){
                case 0: on=Math.pow(nx*nx+ny*ny-0.38,3)-nx*nx*ny*ny*ny<0;break; // heart
                case 1: on=(nx*nx+Math.pow(ny+.05,2)<.52)||(ny<-.42&&Math.abs(nx)>.35&&Math.abs(nx)<.72);break; // cat
                case 2: on=(Math.abs(nx)<.68&&ny>-.18&&ny<.72)||(ny>=-.68&&ny<=-.18&&Math.abs(nx)<.75-ny*.55);on&=!(ny>.18&&Math.abs(nx)<.17);break; // home
                case 3: on=(Math.abs(nx)<.55&&ny>-.3&&ny<.55)||(nx>.45&&nx<.85&&Math.abs(ny)<.3);on&=!(Math.abs(nx)<.34&&ny<.25);break; // mug
                case 4: on=(nx*nx+ny*ny<.72)&&!((nx-.28)*(nx-.28)+(ny+.08)*(ny+.08)<.43);break; // moon
                case 5: double a=Math.atan2(ny,nx)*5;on=nx*nx+ny*ny<.18||((nx*nx+ny*ny)<.68+.16*Math.cos(a)&&(nx*nx+ny*ny)>.22);break; // flower
                case 6: on=Math.abs(nx)<.09||Math.abs(ny)<.09||Math.abs(nx-ny)<.09||Math.abs(nx+ny)<.09;on&=nx*nx+ny*ny<.72;break; // starburst
                default: on=((nx+.38)*(nx+.38)+(ny+.05)*(ny+.05)<.22)||((nx-.38)*(nx-.38)+(ny+.05)*(ny+.05)<.22)||(Math.abs(nx)<.52&&ny>.05&&ny<.25);break; // birds
            }
            cells[y][x]=on;
        }
        // Seeded tiny accents make repeat subjects distinct without damaging silhouettes.
        if(size>=10)for(int i=0;i<size/5;i++){int x=1+r.nextInt(size-2),y=1+r.nextInt(size-2);if(cells[y][x])cells[y][x]=false;}
        return new Puzzle(cells,NAMES[kind]);
    }
}
