package com.cozygrams.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import java.util.HashMap;
import java.util.Map;

public final class CozyGameView extends View {
    private final Paint p=new Paint(3); private final RectF rect=new RectF(); private final GameState game; private final CozyMusic music=new CozyMusic();
    private final Map<Integer,Integer> players=new HashMap<>(); private final Map<Integer,Long> lastMove=new HashMap<>(); private boolean won; private String toast="Connect a second controller for Player 2"; private long toastAt;
    private static final int BG=Color.rgb(43,37,62), CREAM=Color.rgb(255,243,220), PINK=Color.rgb(255,120,154), BLUE=Color.rgb(103,197,220), GRID=Color.rgb(104,91,126);
    private final String[] messages={"You make a lovely team!","Piece by piece, you can do anything.","Home is wherever we're together.","Two hearts, one cozy puzzle.","Small steps make beautiful pictures."};
    public CozyGameView(Context c) { super(c);setFocusable(true);setFocusableInTouchMode(true);requestFocus(); SharedPreferences s=c.getSharedPreferences("save",0);long seed=s.getLong("seed",System.currentTimeMillis());int size=s.getInt("size",5);game=new GameState(seed,size);game.solved=s.getInt("solved",0);String marks=s.getString("marks","");if(marks.length()==size*size)for(int i=0;i<marks.length();i++)game.puzzle.marks[i/size][i%size]=(byte)(marks.charAt(i)-'0'); }
    public void resume(){music.start();}
    public void pause(){music.stop();save();}
    private void save(){StringBuilder b=new StringBuilder(game.size*game.size);for(byte[] row:game.puzzle.marks)for(byte mark:row)b.append((char)('0'+mark));getContext().getSharedPreferences("save",0).edit().putLong("seed",game.seed).putInt("size",game.size).putInt("solved",game.solved).putString("marks",b.toString()).apply();}
    private int player(KeyEvent e){int id=e.getDeviceId(); if(!players.containsKey(id))players.put(id,players.size()%2);return players.get(id);}
    @Override public boolean onKeyDown(int key,KeyEvent e){ if(e.getRepeatCount()>0)return true;int who=player(e); if(won){next();return true;} switch(key){
        case KeyEvent.KEYCODE_DPAD_LEFT:game.move(who,-1,0);break; case KeyEvent.KEYCODE_DPAD_RIGHT:game.move(who,1,0);break;
        case KeyEvent.KEYCODE_DPAD_UP:game.move(who,0,-1);break;case KeyEvent.KEYCODE_DPAD_DOWN:game.move(who,0,1);break;
        case KeyEvent.KEYCODE_BUTTON_A:case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:game.mark(who,(byte)1);break;
        case KeyEvent.KEYCODE_BUTTON_B:case KeyEvent.KEYCODE_BACK:game.mark(who,(byte)2);break;
        case KeyEvent.KEYCODE_BUTTON_X:game.mark(who,(byte)2);break;case KeyEvent.KEYCODE_BUTTON_START:toast="A: fill  •  B/X: cross  •  D-pad/stick: move";toastAt=System.currentTimeMillis();break;
        default:return super.onKeyDown(key,e);}
        check();invalidate();return true;
    }
    @Override public boolean onGenericMotionEvent(MotionEvent e){if((e.getSource()&InputDevice.SOURCE_JOYSTICK)==0||e.getAction()!=MotionEvent.ACTION_MOVE)return super.onGenericMotionEvent(e);int id=e.getDeviceId();if(!players.containsKey(id))players.put(id,players.size()%2);int who=players.get(id);float x=e.getAxisValue(MotionEvent.AXIS_X),y=e.getAxisValue(MotionEvent.AXIS_Y);if(Math.abs(x)>.7f||Math.abs(y)>.7f){long now=System.currentTimeMillis();Long old=lastMove.get(id);if(old==null||now-old>180){lastMove.put(id,now);game.move(who,Math.abs(x)>.7f?(x>0?1:-1):0,Math.abs(y)>.7f?(y>0?1:-1):0);invalidate();}}return true;}
    private void check(){if(game.puzzle.complete()){won=true;save();}}
    private void next(){won=false;game.next();save();invalidate();}
    private void text(Canvas c,String s,float x,float y,float size,int color,Paint.Align align){p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(size);p.setTextAlign(align);p.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD));c.drawText(s,x,y,p);}
    private String joined(int[] clue){StringBuilder b=new StringBuilder();for(int n:clue){if(b.length()>0)b.append(' ');b.append(n);}return b.toString();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(BG);float w=getWidth(),h=getHeight();text(c,"COZYGRAMS",50,65,34,PINK,Paint.Align.LEFT);text(c,"Puzzle "+(game.solved+1)+"  •  "+game.size+"×"+game.size,50,105,20,CREAM,Paint.Align.LEFT);
        float clue=game.size<=10?110:150;float board=Math.min(h-120,w-420);float cell=board/game.size;float left=(w-board+clue)/2,top=(h-board+clue)/2;
        p.setStyle(Paint.Style.FILL);p.setColor(CREAM);rect.set(left-clue-8,top-clue-8,left+board+8,top+board+8);c.drawRoundRect(rect,16,16,p);
        for(int y=0;y<game.size;y++){text(c,joined(game.puzzle.rowClues(y)),left-12,top+(y+.68f)*cell,Math.min(20,cell*.42f),GRID,Paint.Align.RIGHT);}
        for(int x=0;x<game.size;x++){int[] cs=game.puzzle.colClues(x);for(int i=0;i<cs.length;i++)text(c,""+cs[i],left+(x+.5f)*cell,top-10-(cs.length-1-i)*Math.min(20,cell*.38f),Math.min(20,cell*.4f),GRID,Paint.Align.CENTER);}
        for(int y=0;y<game.size;y++)for(int x=0;x<game.size;x++){float l=left+x*cell,t=top+y*cell;p.setStyle(Paint.Style.FILL);p.setColor(game.puzzle.marks[y][x]==1?PINK:Color.rgb(255,250,239));c.drawRect(l,t,l+cell,t+cell,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth((x%5==0||y%5==0)?2.5f:1);p.setColor(GRID);c.drawRect(l,t,l+cell,t+cell,p);if(game.puzzle.marks[y][x]==2){p.setStrokeWidth(3);c.drawLine(l+cell*.25f,t+cell*.25f,l+cell*.75f,t+cell*.75f,p);c.drawLine(l+cell*.75f,t+cell*.25f,l+cell*.25f,t+cell*.75f,p);}}
        drawCursor(c,left,top,cell,0,PINK);drawCursor(c,left,top,cell,1,BLUE);
        float side=left+board+35;text(c,"PLAYER 1  ♥",side,top+25,20,PINK,Paint.Align.LEFT);text(c,"PLAYER 2  ♥",side,top+58,20,BLUE,Paint.Align.LEFT);text(c,"A  Fill",side,top+115,18,CREAM,Paint.Align.LEFT);text(c,"B / X  Cross",side,top+145,18,CREAM,Paint.Align.LEFT);text(c,"☰  Help",side,top+175,18,CREAM,Paint.Align.LEFT);
        if(System.currentTimeMillis()-toastAt<5000||toastAt==0)text(c,toast,w/2,h-28,17,CREAM,Paint.Align.CENTER);
        if(won){p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(230,43,37,62));rect.set(w*.18f,h*.22f,w*.82f,h*.78f);c.drawRoundRect(rect,32,32,p);text(c,"♥  "+game.puzzle.name+"  ♥",w/2,h*.39f,42,PINK,Paint.Align.CENTER);text(c,messages[game.solved%messages.length],w/2,h*.51f,26,CREAM,Paint.Align.CENTER);text(c,"Press any button for the next puzzle",w/2,h*.64f,19,BLUE,Paint.Align.CENTER);}
    }
    private void drawCursor(Canvas c,float l,float t,float cell,int who,int color){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(3,cell*.10f));p.setColor(color);float inset=who==0?3:8;rect.set(l+game.cursorX[who]*cell+inset,t+game.cursorY[who]*cell+inset,l+(game.cursorX[who]+1)*cell-inset,t+(game.cursorY[who]+1)*cell-inset);c.drawRoundRect(rect,5,5,p);}
}
