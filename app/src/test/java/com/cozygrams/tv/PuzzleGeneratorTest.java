package com.cozygrams.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class PuzzleGeneratorTest {
    @Test public void deterministicAcrossAllSizes(){
        for(int size:new int[]{5,10,15,20}){
            Puzzle a=PuzzleGenerator.generate(42,size),b=PuzzleGenerator.generate(42,size);
            assertEquals(size,a.size);assertEquals(size,b.size);
            for(int y=0;y<size;y++)assertArrayEquals(a.solution[y],b.solution[y]);
        }
    }
    @Test public void generatedArtIsHorizontallySymmetricAndNonEmpty(){
        for(long seed=0;seed<100;seed++){Puzzle p=PuzzleGenerator.generate(seed,10);int n=0;
            for(int y=0;y<10;y++)for(int x=0;x<10;x++){assertEquals(p.solution[y][x],p.solution[y][9-x]);if(p.solution[y][x])n++;}
            assertTrue(n>=10);
        }
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsTinyBoards(){PuzzleGenerator.generate(1,4);}
}
