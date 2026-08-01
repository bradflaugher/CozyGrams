package com.cozygrams.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class GameStateTest {
    @Test public void cursorWrapsAndPlayersMarkIndependently(){GameState g=new GameState(7,5);g.move(0,-1,-1);assertEquals(4,g.cursorX[0]);assertEquals(4,g.cursorY[0]);g.mark(0,(byte)1);assertEquals(1,g.puzzle.marks[4][4]);g.mark(0,(byte)1);assertEquals(0,g.puzzle.marks[4][4]);}
    @Test public void endlessModeKeepsChosenDifficulty(){GameState g=new GameState(7,15);g.next();g.next();g.next();assertEquals(15,g.size);assertFalse(g.storyMode);}
    @Test public void hintFindsAndFillsARealSolutionCell(){GameState g=new GameState(7,5);assertTrue(g.hint(0));assertEquals(1,g.puzzle.marks[g.cursorY[0]][g.cursorX[0]]);assertTrue(g.puzzle.solution[g.cursorY[0]][g.cursorX[0]]);}
    @Test public void storyAdvancesThroughAuthoredPuzzles(){GameState g=new GameState(7,5);g.startStory(7);assertTrue(g.storyMode);assertEquals("Moon Kiss",g.puzzle.name);g.next();assertEquals(8,g.storyIndex);assertEquals("Love Birds",g.puzzle.name);assertEquals(10,g.size);}
}
