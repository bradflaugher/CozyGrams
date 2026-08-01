package com.cozygrams.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class PuzzleTest {
    @Test public void cluesHandleRunsAndEmptyLines(){
        assertArrayEquals(new int[]{2,1},Puzzle.clues(new boolean[]{true,true,false,true}));
        assertArrayEquals(new int[]{0},Puzzle.clues(new boolean[]{false,false}));
    }
    @Test public void completeIgnoresCorrectCrosses(){
        Puzzle p=new Puzzle(new boolean[][]{{true,false},{false,true}},"test");
        p.marks[0][0]=1;p.marks[0][1]=2;p.marks[1][0]=2;p.marks[1][1]=1;
        assertTrue(p.complete()); assertEquals(0,p.mistakes());
    }
    @Test public void wrongFillPreventsCompletion(){
        Puzzle p=new Puzzle(new boolean[][]{{false}},"test");p.marks[0][0]=1;
        assertFalse(p.complete());assertEquals(1,p.mistakes());
    }
    @Test public void alternateClueEquivalentSolutionIsAccepted(){
        Puzzle p=new Puzzle(new boolean[][]{{true,false},{false,true}},"ambiguous");
        p.marks[0][1]=1;p.marks[1][0]=1;
        assertTrue(p.complete());
    }
    @Test public void completedLinesAutoCrossUnknownCells(){Puzzle p=new Puzzle(new boolean[][]{{true,false},{false,true}},"auto");p.marks[0][0]=1;assertTrue(p.rowSolved(0));assertTrue(p.colSolved(0));assertEquals(2,p.autoCrossCompletedLines(0,0));assertEquals(2,p.marks[0][1]);assertEquals(2,p.marks[1][0]);}
}
