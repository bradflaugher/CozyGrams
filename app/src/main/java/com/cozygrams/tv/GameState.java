package com.cozygrams.tv;

public final class GameState {
    public Puzzle puzzle; public long seed; public int size; public int solved; public boolean storyMode; public int storyIndex;
    public final int[] cursorX={0,1}, cursorY={0,0};
    public final int[] moves={0,0};
    public GameState(long seed,int size) { this.seed=seed;this.size=size;puzzle=PuzzleGenerator.generate(seed,size); }
    public void move(int player,int dx,int dy) {
        cursorX[player]=Math.floorMod(cursorX[player]+dx,size);
        cursorY[player]=Math.floorMod(cursorY[player]+dy,size);
    }
    public void mark(int player,byte mark) {
        int x=cursorX[player],y=cursorY[player]; puzzle.marks[y][x]=puzzle.marks[y][x]==mark?0:mark;moves[player]++;
    }
    public boolean hint(int player){int start=cursorY[player]*size+cursorX[player];for(int i=0;i<size*size;i++){int at=(start+i)%(size*size),y=at/size,x=at%size;if(puzzle.solution[y][x]&&puzzle.marks[y][x]!=1){puzzle.marks[y][x]=1;cursorX[player]=x;cursorY[player]=y;moves[player]++;return true;}}return false;}
    public void startStory(int index){storyMode=true;storyIndex=Math.floorMod(index,PuzzleLibrary.count());puzzle=PuzzleLibrary.get(storyIndex);size=puzzle.size;cursorX[0]=0;cursorX[1]=1;cursorY[0]=cursorY[1]=0;moves[0]=moves[1]=0;}
    public void startEndless(long newSeed,int newSize){storyMode=false;seed=newSeed;size=newSize;puzzle=PuzzleGenerator.generate(seed,size);cursorX[0]=0;cursorX[1]=1;cursorY[0]=cursorY[1]=0;moves[0]=moves[1]=0;}
    public void next() {
        solved++;if(storyMode){storyIndex=(storyIndex+1)%PuzzleLibrary.count();puzzle=PuzzleLibrary.get(storyIndex);size=puzzle.size;}else{seed+=104729;puzzle=PuzzleGenerator.generate(seed,size);}cursorX[0]=cursorY[0]=cursorY[1]=0;cursorX[1]=1;moves[0]=moves[1]=0;
    }
}
