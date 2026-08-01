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
    @Test public void generatedArtIsNonEmptyAndHasUsefulClues(){
        for(int size:new int[]{5,10,15,20})for(long seed=0;seed<250;seed++){Puzzle p=PuzzleGenerator.generate(seed,size);int n=0;
            for(int y=0;y<size;y++)for(int x=0;x<size;x++)if(p.solution[y][x])n++;
            assertTrue("seed="+seed+", size="+size,n>=Math.max(3,size/2));
            for(int y=0;y<size;y++)assertNotNull(p.rowClues(y));
            for(int x=0;x<size;x++)assertNotNull(p.colClues(x));
        }
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsTinyBoards(){PuzzleGenerator.generate(1,4);}
}
