package com.cozygrams.tv;

import java.util.Random;

public final class PuzzleGenerator {
    private static final String[] NAMES={"Two Hearts","Warm Hug","Moonlit Garden","Sweet Home","Love Birds","Cocoa Date","Starlight","Together"};
    private PuzzleGenerator() {}
    public static Puzzle generate(long seed, int size) {
        if (size < 5 || size > 20) throw new IllegalArgumentException("size must be 5..20");
        Random r=new Random(seed); boolean[][] cells=new boolean[size][size];
        // Mirrored cellular art makes recognizable, pleasing silhouettes while remaining endless.
        int half=(size+1)/2;
        for(int y=0;y<size;y++) for(int x=0;x<half;x++) {
            double dx=(x-half*.65)/(half*.75), dy=(y-size*.5)/(size*.55);
            boolean inBlob=dx*dx+dy*dy < 1.0;
            boolean on=inBlob && r.nextDouble() < .58;
            cells[y][x]=on; cells[y][size-1-x]=on;
        }
        // Smooth isolated noise and guarantee every row/column has useful information.
        for(int pass=0;pass<2;pass++) {
            boolean[][] next=new boolean[size][size];
            for(int y=0;y<size;y++) for(int x=0;x<size;x++) {
                int n=0; for(int yy=Math.max(0,y-1);yy<=Math.min(size-1,y+1);yy++) for(int xx=Math.max(0,x-1);xx<=Math.min(size-1,x+1);xx++) if(cells[yy][xx])n++;
                next[y][x]=n>=4;
            } cells=next;
        }
        int filled=0; for(boolean[] row:cells) for(boolean c:row) if(c)filled++;
        if(filled < size) return generate(seed+7919,size);
        return new Puzzle(cells,NAMES[Math.floorMod((int)seed,NAMES.length)]);
    }
}
