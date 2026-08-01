package com.cozygrams.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class GameStateTest {
    @Test public void cursorWrapsAndPlayersMarkIndependently(){GameState g=new GameState(7,5);g.move(0,-1,-1);assertEquals(4,g.cursorX[0]);assertEquals(4,g.cursorY[0]);g.mark(0,(byte)1);assertEquals(1,g.puzzle.marks[4][4]);g.mark(0,(byte)1);assertEquals(0,g.puzzle.marks[4][4]);}
    @Test public void difficultyGrowsEveryThreePuzzles(){GameState g=new GameState(7,5);g.next();g.next();assertEquals(5,g.size);g.next();assertEquals(10,g.size);}
}
