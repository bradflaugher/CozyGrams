package com.cozygrams.tv;

import java.util.ArrayList;
import java.util.List;

public final class Puzzle {
    public final int size;
    public final boolean[][] solution;
    public final byte[][] marks; // 0 unknown, 1 filled, 2 crossed
    public final String name;

    public Puzzle(boolean[][] solution, String name) {
        this.size = solution.length; this.solution = solution; this.name = name;
        this.marks = new byte[size][size];
    }
    public static int[] clues(boolean[] line) {
        List<Integer> result = new ArrayList<>(); int run = 0;
        for (boolean value : line) {
            if (value) run++; else if (run > 0) { result.add(run); run = 0; }
        }
        if (run > 0) result.add(run);
        if (result.isEmpty()) result.add(0);
        int[] out = new int[result.size()];
        for (int i=0;i<out.length;i++) out[i]=result.get(i);
        return out;
    }
    public int[] rowClues(int row) { return clues(solution[row]); }
    public int[] colClues(int col) {
        boolean[] line = new boolean[size]; for(int i=0;i<size;i++) line[i]=solution[i][col];
        return clues(line);
    }
    public boolean complete() {
        // Validate the visible clue solution, not a hidden bitmap identity. This
        // correctly accepts any alternate solution if a generated board has one.
        for(int y=0;y<size;y++) {
            boolean[] line=new boolean[size];for(int x=0;x<size;x++)line[x]=marks[y][x]==1;
            if(!java.util.Arrays.equals(clues(line),rowClues(y)))return false;
        }
        for(int x=0;x<size;x++) {
            boolean[] line=new boolean[size];for(int y=0;y<size;y++)line[y]=marks[y][x]==1;
            if(!java.util.Arrays.equals(clues(line),colClues(x)))return false;
        }
        return true;
    }
    public int mistakes() {
        int n=0; for(int y=0;y<size;y++) for(int x=0;x<size;x++)
            if (marks[y][x]==1 && !solution[y][x]) n++;
        return n;
    }
}
