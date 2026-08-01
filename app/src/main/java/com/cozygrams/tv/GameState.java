package com.cozygrams.tv;

public final class GameState {
    public Puzzle puzzle; public long seed; public int size; public int solved;
    public final int[] cursorX={0,1}, cursorY={0,0};
    public GameState(long seed,int size) { this.seed=seed;this.size=size;puzzle=PuzzleGenerator.generate(seed,size); }
    public void move(int player,int dx,int dy) {
        cursorX[player]=Math.floorMod(cursorX[player]+dx,size);
        cursorY[player]=Math.floorMod(cursorY[player]+dy,size);
    }
    public void mark(int player,byte mark) {
        int x=cursorX[player],y=cursorY[player]; puzzle.marks[y][x]=puzzle.marks[y][x]==mark?0:mark;
    }
    public void next() {
        solved++; seed+=104729; if(solved>0 && solved%3==0 && size<20) size+= size<10?5:5;
        puzzle=PuzzleGenerator.generate(seed,size); cursorX[0]=cursorY[0]=cursorY[1]=0;cursorX[1]=1;
    }
}
