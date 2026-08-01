package com.cozygrams.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** The entire lean-back experience, drawn as one lightweight hardware-accelerated view. */
public final class CozyGameView extends View {
    private static final int HOME = 0;
    private static final int GAME = 1;
    private static final int SETTINGS = 2;

    private static final int INK = Color.rgb(43, 37, 62);
    private static final int CREAM = Color.rgb(255, 243, 220);
    private static final int PAPER = Color.rgb(255, 250, 239);
    private static final int PINK = Color.rgb(255, 120, 154);
    private static final int PINK_DARK = Color.rgb(189, 70, 111);
    private static final int BLUE = Color.rgb(103, 197, 220);
    private static final int BLUE_DARK = Color.rgb(44, 126, 155);
    private static final int GRID = Color.rgb(104, 91, 126);
    private static final int SOFT_TEXT = Color.rgb(220, 211, 225);

    private static final long TOAST_DURATION_MS = 4000;
    private static final long FEEDBACK_DURATION_MS = 460;
    private static final long WIN_INPUT_DELAY_MS = 900;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final RectF rect = new RectF();
    private final Typeface bold = Typeface.create("sans", Typeface.BOLD);
    private final Typeface regular = Typeface.create("sans", Typeface.NORMAL);
    private final GameState game;
    private final CozyMusic music = new CozyMusic();
    private final CozySfx sfx = new CozySfx();
    private final Bitmap roomBackground;
    private final Bitmap gardenBackground;
    private final Map<Integer, Integer> players = new HashMap<>();
    private final Map<Integer, Long> lastMove = new HashMap<>();

    private int screen = HOME;
    private int menu;
    private boolean won;
    private boolean musicOn;
    private boolean sfxOn;
    private boolean gentleCheck;
    private boolean hintsOn;
    private String toast = "Press a button on each controller to join";
    private long toastAt;
    private long feedbackAt;
    private long winAt;
    private int feedbackX = -1;
    private int feedbackY = -1;
    private int feedbackColor = PINK;
    private int feedbackPlayer;

    private final String[] messages = {
            "You make a lovely team!",
            "Piece by piece, you can do anything.",
            "Home is wherever we're together.",
            "Two hearts, one cozy puzzle.",
            "Small steps make beautiful pictures.",
            "The best view is the one we share."
    };

    public CozyGameView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        roomBackground = BitmapFactory.decodeResource(getResources(), R.drawable.cozy_room);
        gardenBackground = BitmapFactory.decodeResource(getResources(), R.drawable.moon_garden);

        SharedPreferences save = context.getSharedPreferences("save", 0);
        long seed = save.getLong("seed", System.currentTimeMillis());
        int size = save.getInt("size", 5);
        musicOn = save.getBoolean("music", true);
        sfxOn = save.getBoolean("sfx", true);
        gentleCheck = save.getBoolean("gentle", false);
        hintsOn = save.getBoolean("hints", true);
        game = new GameState(seed, size);
        restoreGame(save);
        sfx.setEnabled(sfxOn);
    }

    private void restoreGame(SharedPreferences save) {
        game.solved = save.getInt("solved", 0);
        game.storyIndex = save.getInt("storyIndex", 0);
        game.storyMode = save.getBoolean("storyMode", false);
        if (game.storyMode) {
            game.startStory(game.storyIndex);
        }
        game.moves[0] = save.getInt("moves0", 0);
        game.moves[1] = save.getInt("moves1", 0);

        String marks = save.getString("marks", "");
        if (marks.length() == game.size * game.size) {
            for (int i = 0; i < marks.length(); i++) {
                game.puzzle.marks[i / game.size][i % game.size] =
                        (byte) (marks.charAt(i) - '0');
            }
        }
    }

    public void resume() {
        music.setEnabled(musicOn);
        sfx.setEnabled(sfxOn);
    }

    public void pause() {
        music.stop();
        save();
    }

    private void save() {
        StringBuilder marks = new StringBuilder(game.size * game.size);
        for (byte[] row : game.puzzle.marks) {
            for (byte mark : row) {
                marks.append((char) ('0' + mark));
            }
        }
        getContext().getSharedPreferences("save", 0).edit()
                .putLong("seed", game.seed)
                .putInt("size", game.size)
                .putInt("solved", game.solved)
                .putInt("moves0", game.moves[0])
                .putInt("moves1", game.moves[1])
                .putBoolean("music", musicOn)
                .putBoolean("sfx", sfxOn)
                .putBoolean("gentle", gentleCheck)
                .putBoolean("hints", hintsOn)
                .putBoolean("storyMode", game.storyMode)
                .putInt("storyIndex", game.storyIndex)
                .putString("marks", marks.toString())
                .apply();
    }

    private int player(KeyEvent event) {
        int deviceId = event.getDeviceId();
        if (!players.containsKey(deviceId)) {
            int player = players.size() % 2;
            players.put(deviceId, player);
            showToast((player == 0 ? "Rose" : "Sky") + " joined the puzzle  ♥");
            sfx.play(CozySfx.Sound.JOIN);
        }
        return players.get(deviceId);
    }

    private static boolean confirm(int key) {
        return key == KeyEvent.KEYCODE_BUTTON_A
                || key == KeyEvent.KEYCODE_DPAD_CENTER
                || key == KeyEvent.KEYCODE_ENTER;
    }

    @Override
    public boolean onKeyDown(int key, KeyEvent event) {
        if (event.getRepeatCount() > 0) {
            return true;
        }
        int who = player(event);
        if (screen == HOME) {
            return handleHomeKey(key, event);
        }
        if (screen == SETTINGS) {
            return handleSettingsKey(key);
        }
        return handleGameKey(key, who, event);
    }

    private boolean handleHomeKey(int key, KeyEvent event) {
        if (key == KeyEvent.KEYCODE_BACK) {
            save();
            return super.onKeyDown(key, event);
        }
        if (key == KeyEvent.KEYCODE_DPAD_UP) {
            moveMenu(-1);
        } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
            moveMenu(1);
        } else if (menu == 2 && (key == KeyEvent.KEYCODE_DPAD_LEFT
                || key == KeyEvent.KEYCODE_DPAD_RIGHT || confirm(key))) {
            changeEndlessSize(key == KeyEvent.KEYCODE_DPAD_LEFT ? -5 : 5);
        } else if (confirm(key)) {
            chooseHomeItem();
        } else {
            return true;
        }
        invalidate();
        return true;
    }

    private void moveMenu(int direction) {
        menu = Math.floorMod(menu + direction, 5);
        sfx.play(CozySfx.Sound.MOVE);
    }

    private void changeEndlessSize(int amount) {
        int size = game.storyMode ? 5 : game.size;
        size += amount;
        if (size > 20) size = 5;
        if (size < 5) size = 20;
        game.startEndless(System.currentTimeMillis(), size);
        showToast(size + " × " + size + " — a fresh cozy canvas");
        sfx.play(CozySfx.Sound.SELECT);
        save();
    }

    private void chooseHomeItem() {
        sfx.play(CozySfx.Sound.SELECT);
        if (menu == 0) {
            screen = GAME;
        } else if (menu == 1) {
            game.startStory(game.storyIndex);
            screen = GAME;
            save();
        } else if (menu == 3) {
            screen = SETTINGS;
            menu = 0;
        } else if (menu == 4) {
            game.solved = 0;
            game.startStory(0);
            screen = GAME;
            save();
        }
    }

    private boolean handleSettingsKey(int key) {
        if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
            moveMenu(key == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1);
        } else if (confirm(key)) {
            chooseSetting();
        } else if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_BUTTON_B) {
            screen = HOME;
            menu = 0;
        }
        invalidate();
        return true;
    }

    private void chooseSetting() {
        if (menu == 0) {
            musicOn = !musicOn;
            music.setEnabled(musicOn);
        } else if (menu == 1) {
            sfxOn = !sfxOn;
            sfx.setEnabled(sfxOn);
        } else if (menu == 2) {
            gentleCheck = !gentleCheck;
        } else if (menu == 3) {
            hintsOn = !hintsOn;
        } else {
            screen = HOME;
            menu = 0;
        }
        if (sfxOn) sfx.play(CozySfx.Sound.SELECT);
        save();
    }

    private boolean handleGameKey(int key, int who, KeyEvent event) {
        if (key == KeyEvent.KEYCODE_BUTTON_START) {
            screen = SETTINGS;
            menu = 0;
            invalidate();
            return true;
        }
        if (key == KeyEvent.KEYCODE_BACK) {
            screen = HOME;
            menu = 0;
            save();
            invalidate();
            return true;
        }
        if (won) {
            if (SystemClock.uptimeMillis() - winAt >= WIN_INPUT_DELAY_MS) nextPuzzle();
            return true;
        }

        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                moveCursor(who, -1, 0);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                moveCursor(who, 1, 0);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                moveCursor(who, 0, -1);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                moveCursor(who, 0, 1);
                break;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                fillCell(who);
                break;
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BUTTON_X:
                markCell(who, (byte) 2, CozySfx.Sound.CROSS, BLUE);
                break;
            case KeyEvent.KEYCODE_BUTTON_Y:
                useHint(who);
                break;
            default:
                return super.onKeyDown(key, event);
        }
        checkForWin();
        invalidate();
        return true;
    }

    private void moveCursor(int who, int dx, int dy) {
        game.move(who, dx, dy);
        sfx.play(CozySfx.Sound.MOVE);
    }

    private void fillCell(int who) {
        int x = game.cursorX[who];
        int y = game.cursorY[who];
        if (gentleCheck && !game.puzzle.solution[y][x]) {
            game.puzzle.marks[y][x] = 2;
            game.moves[who]++;
            showToast("Not this square — the picture is still taking shape  ♥");
            startFeedback(who, x, y, PINK_DARK);
            sfx.play(CozySfx.Sound.ERROR);
            return;
        }
        markCell(who, (byte) 1, CozySfx.Sound.FILL, who == 0 ? PINK : BLUE);
        int crossed = game.puzzle.autoCrossCompletedLines(x, y);
        if (crossed > 0) {
            showToast("Lovely — that line is complete  ✦");
            sfx.play(CozySfx.Sound.LINE);
        }
    }

    private void markCell(int who, byte mark, CozySfx.Sound sound, int color) {
        int x = game.cursorX[who];
        int y = game.cursorY[who];
        game.mark(who, mark);
        startFeedback(who, x, y, color);
        sfx.play(sound);
    }

    private void useHint(int who) {
        if (!hintsOn || !game.hint(who)) return;
        int x = game.cursorX[who];
        int y = game.cursorY[who];
        game.puzzle.autoCrossCompletedLines(x, y);
        showToast("A little starlight showed the way  ✦");
        startFeedback(who, x, y, CREAM);
        sfx.play(CozySfx.Sound.HINT);
    }

    private void startFeedback(int who, int x, int y, int color) {
        feedbackPlayer = who;
        feedbackX = x;
        feedbackY = y;
        feedbackColor = color;
        feedbackAt = SystemClock.uptimeMillis();
    }

    private void showToast(String message) {
        toast = message;
        toastAt = SystemClock.uptimeMillis();
    }

    private void checkForWin() {
        if (!won && game.puzzle.complete()) {
            won = true;
            winAt = SystemClock.uptimeMillis();
            sfx.play(CozySfx.Sound.WIN);
            save();
        }
    }

    private void nextPuzzle() {
        won = false;
        feedbackX = -1;
        sfx.play(CozySfx.Sound.SELECT);
        game.next();
        showToast(game.storyMode ? "A new page of your story…" : "A fresh picture is waiting…");
        save();
        invalidate();
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == 0
                || event.getAction() != MotionEvent.ACTION_MOVE) {
            return super.onGenericMotionEvent(event);
        }
        int id = event.getDeviceId();
        if (!players.containsKey(id)) {
            players.put(id, players.size() % 2);
        }
        float x = event.getAxisValue(MotionEvent.AXIS_X);
        float y = event.getAxisValue(MotionEvent.AXIS_Y);
        if (Math.abs(x) <= .7f && Math.abs(y) <= .7f) return true;

        long now = SystemClock.uptimeMillis();
        Long previous = lastMove.get(id);
        if (previous != null && now - previous <= 180) return true;
        lastMove.put(id, now);
        int direction = Math.abs(y) > .7f ? (y > 0 ? 1 : -1) : 0;
        if (screen == GAME) {
            int who = players.get(id);
            moveCursor(who, Math.abs(x) > .7f ? (x > 0 ? 1 : -1) : 0, direction);
        } else if (direction != 0) {
            moveMenu(direction);
        }
        invalidate();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        drawBackdrop(canvas, width, height);
        if (screen == HOME) {
            drawHome(canvas, width, height);
        } else if (screen == SETTINGS) {
            drawSettings(canvas, width, height);
        } else {
            drawGame(canvas, width, height);
        }
    }

    private void drawBackdrop(Canvas canvas, float width, float height) {
        rect.set(0, 0, width, height);
        Bitmap scene = screen == GAME && (game.solved / 2) % 2 == 1
                ? gardenBackground : roomBackground;
        canvas.drawBitmap(scene, null, rect, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(screen == GAME ? 158 : 68, 25, 18, 38));
        canvas.drawRect(0, 0, width, height, paint);

        // A quiet vignette keeps edge UI legible without hiding the illustrated rooms.
        paint.setColor(Color.argb(70, 18, 13, 30));
        canvas.drawRect(0, 0, width, height * .12f, paint);
        canvas.drawRect(0, height * .90f, width, height, paint);
    }

    private void drawHome(Canvas canvas, float width, float height) {
        panel(canvas, width * .265f, height * .045f, width * .735f, height * .955f, 229);
        text(canvas, "♥", width / 2, height * .145f, scaled(height, 31), PINK,
                Paint.Align.CENTER, true);
        text(canvas, "COZYGRAMS", width / 2, height * .225f, scaled(height, 48), CREAM,
                Paint.Align.CENTER, true);
        text(canvas, "Puzzles are better together", width / 2, height * .282f,
                scaled(height, 19), BLUE, Paint.Align.CENTER, false);

        String mode = game.storyMode
                ? "STORY  " + (game.storyIndex + 1) + " / " + PuzzleLibrary.count()
                : "ENDLESS  " + game.size + " × " + game.size;
        int endlessSize = game.storyMode ? 5 : game.size;
        String[] items = {
                "CONTINUE   •   " + mode,
                "STORY BOOK",
                "ENDLESS SIZE     ‹  " + endlessSize + " × " + endlessSize + "  ›",
                "SOUND, HINTS & HELP",
                "RESTART STORY"
        };
        for (int i = 0; i < items.length; i++) {
            float y = height * .39f + i * height * .078f;
            drawMenuItem(canvas, items[i], width, y, i == menu);
        }
        text(canvas, "D-pad / stick to move     •     A to choose", width / 2,
                height * .905f, scaled(height, 16), SOFT_TEXT, Paint.Align.CENTER, false);
    }

    private void drawSettings(Canvas canvas, float width, float height) {
        panel(canvas, width * .25f, height * .055f, width * .75f, height * .945f, 238);
        text(canvas, "COZY CORNER", width / 2, height * .17f, scaled(height, 38), CREAM,
                Paint.Align.CENTER, true);
        text(canvas, "Make the room feel just right", width / 2, height * .22f,
                scaled(height, 16), BLUE, Paint.Align.CENTER, false);
        String[] items = {
                "MUSIC     " + onOff(musicOn),
                "SOUND EFFECTS     " + onOff(sfxOn),
                "GENTLE MISTAKE CHECK     " + onOff(gentleCheck),
                "Y BUTTON HINTS     " + onOff(hintsOn),
                "BACK TO MENU"
        };
        for (int i = 0; i < items.length; i++) {
            float y = height * .325f + i * height * .077f;
            drawMenuItem(canvas, items[i], width, y, i == menu);
        }
        text(canvas, "A  fill     •     B / X  cross     •     Y  hint     •     ☰  pause",
                width / 2, height * .835f, scaled(height, 17), BLUE,
                Paint.Align.CENTER, true);
        text(canvas, "Two controllers get independent Rose and Sky cursors",
                width / 2, height * .89f, scaled(height, 15), CREAM,
                Paint.Align.CENTER, false);
    }

    private static String onOff(boolean enabled) {
        return enabled ? "♥  ON" : "○  OFF";
    }

    private void drawMenuItem(Canvas canvas, String label, float width, float y,
                              boolean focused) {
        if (focused) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(PINK);
            rect.set(width * .315f, y - 35, width * .685f, y + 17);
            canvas.drawRoundRect(rect, 18, 18, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            paint.setColor(Color.argb(190, 255, 243, 220));
            canvas.drawRoundRect(rect, 18, 18, paint);
        }
        text(canvas, focused ? "›  " + label + "  ‹" : label, width / 2, y,
                scaled(getHeight(), 18), focused ? INK : CREAM, Paint.Align.CENTER, true);
    }

    private void drawGame(Canvas canvas, float width, float height) {
        drawGameHeader(canvas, width, height);
        BoardLayout board = new BoardLayout(width, height, game.size);
        drawBoardFrame(canvas, board);
        drawActiveGuides(canvas, board);
        drawClues(canvas, board);
        drawCells(canvas, board);
        drawActionFeedback(canvas, board);
        drawCursor(canvas, board, 0, PINK, "R");
        drawCursor(canvas, board, 1, BLUE, "S");
        drawSidePanel(canvas, board, width, height);
        drawToast(canvas, width, height);

        if (won) {
            drawWin(canvas, width, height);
        } else if (feedbackX >= 0
                && SystemClock.uptimeMillis() - feedbackAt < FEEDBACK_DURATION_MS) {
            postInvalidateOnAnimation();
        }
    }

    private void drawGameHeader(Canvas canvas, float width, float height) {
        text(canvas, "COZYGRAMS", 38, 51, scaled(height, 28), PINK,
                Paint.Align.LEFT, true);
        String progress = game.storyMode
                ? "STORY  " + (game.storyIndex + 1) + " / " + PuzzleLibrary.count()
                : "ENDLESS  " + (game.solved + 1);
        text(canvas, progress + "   •   " + game.size + " × " + game.size,
                39, 82, scaled(height, 17), CREAM, Paint.Align.LEFT, true);
        text(canvas, game.puzzle.name, width - 38, 51, scaled(height, 22), CREAM,
                Paint.Align.RIGHT, true);
        text(canvas, "Take your time", width - 38, 80, scaled(height, 15), BLUE,
                Paint.Align.RIGHT, false);
    }

    private void drawBoardFrame(Canvas canvas, BoardLayout board) {
        shadow(canvas, board.left - board.clueWidth - 12, board.top - board.clueHeight - 12,
                board.right + 12, board.bottom + 12, 22);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(CREAM);
        rect.set(board.left - board.clueWidth - 8, board.top - board.clueHeight - 8,
                board.right + 8, board.bottom + 8);
        canvas.drawRoundRect(rect, 18, 18, paint);
    }

    private void drawActiveGuides(Canvas canvas, BoardLayout board) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(32, 255, 120, 154));
        float roseRow = board.top + game.cursorY[0] * board.cell;
        float roseCol = board.left + game.cursorX[0] * board.cell;
        canvas.drawRect(board.left - board.clueWidth, roseRow, board.right,
                roseRow + board.cell, paint);
        canvas.drawRect(roseCol, board.top - board.clueHeight, roseCol + board.cell,
                board.bottom, paint);

        paint.setColor(Color.argb(28, 103, 197, 220));
        float skyRow = board.top + game.cursorY[1] * board.cell;
        float skyCol = board.left + game.cursorX[1] * board.cell;
        canvas.drawRect(board.left - board.clueWidth, skyRow, board.right,
                skyRow + board.cell, paint);
        canvas.drawRect(skyCol, board.top - board.clueHeight, skyCol + board.cell,
                board.bottom, paint);
    }

    private void drawClues(Canvas canvas, BoardLayout board) {
        float rowTextSize = Math.min(22, Math.max(13, board.cell * .43f));
        for (int y = 0; y < game.size; y++) {
            boolean solved = game.puzzle.rowSolved(y);
            text(canvas, joined(game.puzzle.rowClues(y)), board.left - 13,
                    board.top + (y + .68f) * board.cell, rowTextSize,
                    solved ? PINK_DARK : GRID, Paint.Align.RIGHT, true);
            if (solved) drawClueTick(canvas, board.left - board.clueWidth + 10,
                    board.top + (y + .5f) * board.cell, PINK_DARK);
        }
        for (int x = 0; x < game.size; x++) {
            int[] clues = game.puzzle.colClues(x);
            float size = Math.min(21, Math.max(12, board.cell * .39f));
            int color = game.puzzle.colSolved(x) ? PINK_DARK : GRID;
            float spacing = Math.min(size * 1.02f, board.clueHeight / Math.max(1, clues.length));
            for (int i = 0; i < clues.length; i++) {
                float y = board.top - 12 - (clues.length - 1 - i) * spacing;
                text(canvas, String.valueOf(clues[i]), board.left + (x + .5f) * board.cell,
                        y, size, color, Paint.Align.CENTER, true);
            }
            if (game.puzzle.colSolved(x)) {
                drawClueTick(canvas, board.left + (x + .5f) * board.cell,
                        board.top - board.clueHeight + 10, PINK_DARK);
            }
        }
    }

    private void drawClueTick(Canvas canvas, float x, float y, int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(3);
        paint.setColor(color);
        canvas.drawLine(x - 4, y, x - 1, y + 4, paint);
        canvas.drawLine(x - 1, y + 4, x + 6, y - 5, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawCells(Canvas canvas, BoardLayout board) {
        for (int y = 0; y < game.size; y++) {
            for (int x = 0; x < game.size; x++) {
                float left = board.left + x * board.cell;
                float top = board.top + y * board.cell;
                byte mark = game.puzzle.marks[y][x];
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(mark == 1 ? blend(PINK, PINK_DARK, .17f) : PAPER);
                canvas.drawRect(left, top, left + board.cell, top + board.cell, paint);
                if (mark == 1) drawFilledCell(canvas, left, top, board.cell, x, y);
                if (mark == 2) drawCross(canvas, left, top, board.cell);
            }
        }
        drawGrid(canvas, board);
    }

    private void drawFilledCell(Canvas canvas, float left, float top, float cell, int x, int y) {
        float inset = Math.max(2, cell * .075f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blend(PINK, BLUE, ((x + y) % 5) * .035f));
        rect.set(left + inset, top + inset, left + cell - inset, top + cell - inset);
        canvas.drawRoundRect(rect, Math.max(3, cell * .14f), Math.max(3, cell * .14f), paint);
        paint.setColor(Color.argb(48, 255, 255, 255));
        rect.set(left + inset * 1.8f, top + inset * 1.8f, left + cell * .54f,
                top + cell * .30f);
        canvas.drawRoundRect(rect, cell * .1f, cell * .1f, paint);
    }

    private void drawCross(Canvas canvas, float left, float top, float cell) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(2.5f, cell * .075f));
        paint.setColor(Color.argb(205, 80, 112, 139));
        float inset = cell * .29f;
        canvas.drawLine(left + inset, top + inset, left + cell - inset,
                top + cell - inset, paint);
        canvas.drawLine(left + cell - inset, top + inset, left + inset,
                top + cell - inset, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawGrid(Canvas canvas, BoardLayout board) {
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i <= game.size; i++) {
            paint.setStrokeWidth(i % 5 == 0 ? 2.8f : 1.15f);
            paint.setColor(i % 5 == 0 ? Color.argb(225, 72, 60, 91)
                    : Color.argb(145, 104, 91, 126));
            float x = board.left + i * board.cell;
            float y = board.top + i * board.cell;
            canvas.drawLine(x, board.top, x, board.bottom, paint);
            canvas.drawLine(board.left, y, board.right, y, paint);
        }
    }

    private void drawCursor(Canvas canvas, BoardLayout board, int who, int color, String badge) {
        float pulse = who == feedbackPlayer
                && SystemClock.uptimeMillis() - feedbackAt < FEEDBACK_DURATION_MS ? 1 : .35f;
        float inset = Math.max(2, board.cell * (who == 0 ? .035f : .11f));
        float left = board.left + game.cursorX[who] * board.cell + inset;
        float top = board.top + game.cursorY[who] * board.cell + inset;
        float right = board.left + (game.cursorX[who] + 1) * board.cell - inset;
        float bottom = board.top + (game.cursorY[who] + 1) * board.cell - inset;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(4, board.cell * .105f));
        paint.setColor(Color.argb(90, 20, 15, 35));
        rect.set(left + 2, top + 3, right + 2, bottom + 3);
        canvas.drawRoundRect(rect, Math.max(5, board.cell * .15f),
                Math.max(5, board.cell * .15f), paint);
        paint.setStrokeWidth(Math.max(3.5f, board.cell * (.075f + pulse * .02f)));
        paint.setColor(color);
        rect.set(left, top, right, bottom);
        canvas.drawRoundRect(rect, Math.max(5, board.cell * .15f),
                Math.max(5, board.cell * .15f), paint);

        float badgeRadius = Math.max(8, Math.min(12, board.cell * .22f));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawCircle(left + badgeRadius * .65f, top + badgeRadius * .65f,
                badgeRadius, paint);
        text(canvas, badge, left + badgeRadius * .65f, top + badgeRadius * .65f + 4,
                Math.max(10, badgeRadius), INK, Paint.Align.CENTER, true);
    }

    @Override
    protected void onDetachedFromWindow() {
        music.stop();
        sfx.release();
        super.onDetachedFromWindow();
    }

    private void drawActionFeedback(Canvas canvas, BoardLayout board) {
        if (feedbackX < 0) return;
        float progress = (SystemClock.uptimeMillis() - feedbackAt) / (float) FEEDBACK_DURATION_MS;
        if (progress < 0 || progress >= 1) return;
        float cx = board.left + (feedbackX + .5f) * board.cell;
        float cy = board.top + (feedbackY + .5f) * board.cell;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2, board.cell * .07f * (1 - progress)));
        paint.setColor(withAlpha(feedbackColor, (int) (210 * (1 - progress))));
        canvas.drawCircle(cx, cy, board.cell * (.28f + progress * .72f), paint);

        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 7; i++) {
            double angle = i * Math.PI * 2 / 7 + feedbackPlayer * .4;
            float distance = board.cell * (.18f + progress * .58f);
            float x = cx + (float) Math.cos(angle) * distance;
            float y = cy + (float) Math.sin(angle) * distance;
            paint.setColor(withAlpha(feedbackColor, (int) (235 * (1 - progress))));
            canvas.drawCircle(x, y, Math.max(1.5f, board.cell * .055f * (1 - progress)), paint);
        }
    }

    private void drawSidePanel(Canvas canvas, BoardLayout board, float width, float height) {
        float left = board.right + 29;
        float available = width - left - 28;
        if (available < 150) return;
        float right = width - 25;
        panel(canvas, left, board.top - 4, right, Math.min(board.bottom, board.top + 265), 185);
        text(canvas, "PLAYING TOGETHER", left + 18, board.top + 30, scaled(height, 14),
                CREAM, Paint.Align.LEFT, true);
        drawPlayerCard(canvas, left + 14, board.top + 48, right - 14, 0,
                "ROSE", PINK);
        drawPlayerCard(canvas, left + 14, board.top + 102, right - 14, 1,
                "SKY", BLUE);
        text(canvas, "A", left + 19, board.top + 181, scaled(height, 15), PINK,
                Paint.Align.LEFT, true);
        text(canvas, "Fill", left + 50, board.top + 181, scaled(height, 15), CREAM,
                Paint.Align.LEFT, false);
        text(canvas, "B / X", left + 19, board.top + 211, scaled(height, 15), BLUE,
                Paint.Align.LEFT, true);
        text(canvas, "Cross", left + 75, board.top + 211, scaled(height, 15), CREAM,
                Paint.Align.LEFT, false);
        text(canvas, "Y", left + 19, board.top + 241, scaled(height, 15),
                hintsOn ? CREAM : GRID, Paint.Align.LEFT, true);
        text(canvas, hintsOn ? "Hint" : "Hints off", left + 50, board.top + 241,
                scaled(height, 15), hintsOn ? CREAM : GRID, Paint.Align.LEFT, false);
    }

    private void drawPlayerCard(Canvas canvas, float left, float top, float right,
                                int who, String name, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(45, Color.red(color), Color.green(color), Color.blue(color)));
        rect.set(left, top, right, top + 44);
        canvas.drawRoundRect(rect, 12, 12, paint);
        paint.setColor(color);
        canvas.drawCircle(left + 19, top + 22, 8, paint);
        text(canvas, name, left + 37, top + 28, 16, color, Paint.Align.LEFT, true);
        text(canvas, game.moves[who] + " moves", right - 10, top + 28, 14, CREAM,
                Paint.Align.RIGHT, false);
    }

    private void drawToast(Canvas canvas, float width, float height) {
        boolean visible = toastAt == 0
                || SystemClock.uptimeMillis() - toastAt < TOAST_DURATION_MS;
        if (!visible) return;
        float textWidth = measure(toast, scaled(height, 16), true);
        float left = Math.max(25, width / 2 - textWidth / 2 - 25);
        float right = Math.min(width - 25, width / 2 + textWidth / 2 + 25);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(205, 43, 37, 62));
        rect.set(left, height - 51, right, height - 15);
        canvas.drawRoundRect(rect, 18, 18, paint);
        text(canvas, toast, width / 2, height - 26, scaled(height, 16), CREAM,
                Paint.Align.CENTER, true);
    }

    private void drawWin(Canvas canvas, float width, float height) {
        float elapsed = SystemClock.uptimeMillis() - winAt;
        float entrance = easeOut(Math.min(1, elapsed / 520f));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb((int) (150 * entrance), 20, 14, 34));
        canvas.drawRect(0, 0, width, height, paint);

        float panelTop = lerp(height * .16f, height * .075f, entrance);
        panel(canvas, width * .17f, panelTop, width * .83f, height * .925f,
                (int) (246 * entrance));
        drawReveal(canvas, width / 2, height * .285f,
                Math.min(230, height * .31f) * entrance, elapsed);
        text(canvas, "PUZZLE COMPLETE", width / 2, height * .505f,
                scaled(height, 15), BLUE, Paint.Align.CENTER, true);
        text(canvas, "♥  " + game.puzzle.name + "  ♥", width / 2, height * .575f,
                scaled(height, 34), PINK, Paint.Align.CENTER, true);
        text(canvas, messages[game.solved % messages.length], width / 2, height * .65f,
                scaled(height, 22), CREAM, Paint.Align.CENTER, false);
        text(canvas, "Solved together in " + (game.moves[0] + game.moves[1])
                        + " cozy moves", width / 2, height * .71f,
                scaled(height, 16), SOFT_TEXT, Paint.Align.CENTER, false);
        if (elapsed >= WIN_INPUT_DELAY_MS) {
            text(canvas, "Press any button for the next cozy puzzle", width / 2,
                    height * .815f, scaled(height, 18), BLUE, Paint.Align.CENTER, true);
        } else {
            text(canvas, "Let the picture glow…", width / 2, height * .815f,
                    scaled(height, 16), SOFT_TEXT, Paint.Align.CENTER, false);
        }
        drawCelebration(canvas, width, height, elapsed);
        postInvalidateOnAnimation();
    }

    private void drawReveal(Canvas canvas, float cx, float cy, float size, float elapsed) {
        if (size <= 1) return;
        float cell = size / game.size;
        float left = cx - size / 2;
        float top = cy - size / 2;
        shadow(canvas, left - 12, top - 12, left + size + 12, top + size + 12, 18);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PAPER);
        rect.set(left - 8, top - 8, left + size + 8, top + size + 8);
        canvas.drawRoundRect(rect, 15, 15, paint);
        int visible = Math.min(game.size * game.size,
                Math.max(0, (int) ((elapsed - 140) / 9)));
        for (int y = 0; y < game.size; y++) {
            for (int x = 0; x < game.size; x++) {
                if (!game.puzzle.solution[y][x] || y * game.size + x > visible) continue;
                paint.setColor(blend(PINK, BLUE, (x + y) / (float) (game.size * 2) * .62f));
                float inset = Math.max(1, cell * .07f);
                rect.set(left + x * cell + inset, top + y * cell + inset,
                        left + (x + 1) * cell - inset, top + (y + 1) * cell - inset);
                canvas.drawRoundRect(rect, Math.max(1, cell * .14f),
                        Math.max(1, cell * .14f), paint);
            }
        }
    }

    private void drawCelebration(Canvas canvas, float width, float height, float elapsed) {
        Random random = new Random(game.seed ^ game.storyIndex * 7919L);
        for (int i = 0; i < 28; i++) {
            float delay = random.nextFloat() * 700;
            float life = (elapsed - delay) / (1500 + random.nextFloat() * 800);
            if (life < 0 || life > 1) continue;
            float originX = width * (.12f + random.nextFloat() * .76f);
            float x = originX + (float) Math.sin(life * 8 + i) * (12 + i % 5 * 4);
            float y = height * (.91f - life * .78f);
            int color = i % 3 == 0 ? BLUE : (i % 3 == 1 ? PINK : CREAM);
            int alpha = (int) (230 * Math.sin(Math.PI * life));
            if (i % 4 == 0) {
                text(canvas, "♥", x, y, 14 + i % 5 * 2, withAlpha(color, alpha),
                        Paint.Align.CENTER, true);
            } else {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(withAlpha(color, alpha));
                canvas.drawCircle(x, y, 3 + i % 4, paint);
            }
        }
    }

    private void text(Canvas canvas, String value, float x, float y, float size, int color,
                      Paint.Align align, boolean strong) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTextAlign(align);
        paint.setTypeface(strong ? bold : regular);
        canvas.drawText(value, x, y, paint);
    }

    private float measure(String value, float size, boolean strong) {
        paint.setTextSize(size);
        paint.setTypeface(strong ? bold : regular);
        return paint.measureText(value);
    }

    private static float scaled(float height, float at720) {
        return Math.max(at720 * .8f, at720 * height / 720f);
    }

    private static String joined(int[] clues) {
        StringBuilder value = new StringBuilder();
        for (int clue : clues) {
            if (value.length() > 0) value.append("  ");
            value.append(clue);
        }
        return value.toString();
    }

    private void panel(Canvas canvas, float left, float top, float right, float bottom,
                       int alpha) {
        shadow(canvas, left, top, right, bottom, 28);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(alpha, 43, 37, 62));
        rect.set(left, top, right, bottom);
        canvas.drawRoundRect(rect, 28, 28, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(Color.argb(145, 255, 243, 220));
        canvas.drawRoundRect(rect, 28, 28, paint);
    }

    private void shadow(Canvas canvas, float left, float top, float right, float bottom,
                        float radius) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(90, 11, 7, 20));
        rect.set(left + 5, top + 8, right + 5, bottom + 8);
        canvas.drawRoundRect(rect, radius, radius, paint);
    }

    private static int blend(int first, int second, float amount) {
        float safe = Math.max(0, Math.min(1, amount));
        return Color.rgb(
                (int) (Color.red(first) * (1 - safe) + Color.red(second) * safe),
                (int) (Color.green(first) * (1 - safe) + Color.green(second) * safe),
                (int) (Color.blue(first) * (1 - safe) + Color.blue(second) * safe));
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color),
                Color.green(color), Color.blue(color));
    }

    private static float easeOut(float value) {
        float inverse = 1 - value;
        return 1 - inverse * inverse * inverse;
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static final class BoardLayout {
        final float cell;
        final float left;
        final float top;
        final float right;
        final float bottom;
        final float clueWidth;
        final float clueHeight;

        BoardLayout(float width, float height, int size) {
            clueWidth = size <= 10 ? 105 : 145;
            clueHeight = size <= 10 ? 108 : 148;
            float boardSize = Math.min(height - clueHeight - 76, width - clueWidth - 390);
            boardSize = Math.max(250, boardSize);
            cell = boardSize / size;
            left = Math.max(clueWidth + 30, (width - boardSize + clueWidth - 80) / 2);
            top = Math.max(clueHeight + 18, (height - boardSize + clueHeight + 30) / 2);
            right = left + boardSize;
            bottom = top + boardSize;
        }
    }
}
