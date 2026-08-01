package com.cozygrams.tv;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class PuzzleLibraryTest {
    @Test public void everyStoryPuzzleIsSquareNamedAndPlayable(){Set<String> names=new HashSet<>();assertTrue(PuzzleLibrary.count()>=16);for(int i=0;i<PuzzleLibrary.count();i++){Puzzle p=PuzzleLibrary.get(i);assertNotNull(p.name);assertTrue(names.add(p.name));assertTrue(p.size==5||p.size==10);int filled=0;for(boolean[] row:p.solution){assertEquals(p.size,row.length);for(boolean cell:row)if(cell)filled++;}assertTrue(filled>=3);for(int y=0;y<p.size;y++)assertTrue(p.rowClues(y).length>0);for(int x=0;x<p.size;x++)assertTrue(p.colClues(x).length>0);}}
    @Test public void libraryWrapsCleanly(){assertEquals(PuzzleLibrary.get(0).name,PuzzleLibrary.get(PuzzleLibrary.count()).name);assertEquals(PuzzleLibrary.get(PuzzleLibrary.count()-1).name,PuzzleLibrary.get(-1).name);}
    @Test public void authoredSolutionsSatisfyEveryClue(){for(int i=0;i<PuzzleLibrary.count();i++){Puzzle p=PuzzleLibrary.get(i);for(int y=0;y<p.size;y++)for(int x=0;x<p.size;x++)p.marks[y][x]=p.solution[y][x]?(byte)1:(byte)2;assertTrue("story puzzle "+i,p.complete());for(int n=0;n<p.size;n++){assertTrue(p.rowSolved(n));assertTrue(p.colSolved(n));}}}
}
