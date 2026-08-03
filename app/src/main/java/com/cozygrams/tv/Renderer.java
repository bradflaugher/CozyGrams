package com.cozygrams.tv;

import android.graphics.Bitmap;
import android.graphics.Canvas;

/**
 * Draws one complete frame of CozyGrams.
 *
 * <p>Deliberately free of {@code View}, {@code Context} and any input handling: given a
 * canvas, a {@link GameState}, a {@link UiState} and a clock, it produces the picture.
 * That keeps the whole look of the game reproducible outside a running device.
 */
public final class Renderer {

    private final Draw draw = new Draw();
    private final Backdrop backdrop = new Backdrop();
    private final BoardRenderer boardRenderer = new BoardRenderer(draw);
    private final CursorRenderer cursorRenderer = new CursorRenderer(draw);
    private final HudScene hud = new HudScene(draw);
    private final HomeScene home = new HomeScene(draw);
    private final SettingsScene settings = new SettingsScene(draw);
    private final WinScene win = new WinScene(draw);

    private BoardLayout lastBoard;

    /**
     * The board this renderer last drew, and when it noticed a different one.
     *
     * <p>Kept here rather than on {@link UiState} because it is a fact about what has been
     * drawn, not about what the game is: the page turn is owed to whoever was watching the
     * previous page. A renderer that has never drawn a board starts no handover, so the
     * first frame after a launch or a restore is never washed over.
     */
    private Puzzle lastPuzzle;
    private long handoverAt;

    public void setScenes(Bitmap room, Bitmap garden) {
        backdrop.setScenes(room, garden);
    }

    /** Geometry from the most recent frame, for effects that need board coordinates. */
    public BoardLayout board() {
        return lastBoard;
    }

    public void draw(Canvas canvas, float width, float height, GameState game, UiState ui,
                     Effects effects, long now) {
        // LARGER TEXT is a setting about type, so it is applied to type. It used to be
        // applied by telling Theme the screen was 16% taller, which scales *everything* —
        // every padding, stroke, reserve and clue gutter — so asking for bigger words made
        // the board smaller: measured at 1920x1080, the 15x15 cell went 43 px to 42 and its
        // clue ink 31.8 px to 31.1. An accessibility setting that shrinks the numbers is the
        // setting failing at the one job it is named for.
        Theme.setScreenHeight(height);
        Theme.setTextScale(ui.textScale());
        backdrop.draw(canvas, width, height, ui, game);

        switch (ui.screen) {
            case UiState.HOME:
                home.draw(canvas, width, height, game, ui, effects, now);
                break;
            case UiState.SETTINGS:
                settings.draw(canvas, width, height, ui, now);
                break;
            default:
                drawGame(canvas, width, height, game, ui, effects, now);
                break;
        }
    }

    private void drawGame(Canvas canvas, float width, float height, GameState game,
                          UiState ui, Effects effects, long now) {
        BoardLayout board = new BoardLayout(width, height, game.puzzle, true);
        lastBoard = board;
        notePuzzle(game.puzzle, now);

        // The board and its furniture stop being drawn once the win card's paper has come
        // up over them. Left up, they ghosted through the celebration: clue ticks, crosses
        // and the rail's own labels were all legible as half-words around the card,
        // framing the reward with the workings that produced it.
        if (!ui.won || now - ui.winAt < WinScene.BOARD_GONE_MS) {
            boardRenderer.draw(canvas, board, game, ui, effects, now);
            cursorRenderer.draw(canvas, board, game, ui, now);
            hud.draw(canvas, width, height, board, game, ui, now);
        }

        // Particles are drawn inside BoardRenderer, beneath the marks and the cursors —
        // see the note there. The only pass that draws them on top is the win card, where
        // there is no board left to bury and the drift is the point.
        if (ui.won) {
            win.draw(canvas, width, height, game, ui, effects, now);
        } else {
            win.drawHandover(canvas, width, height, now - handoverAt);
        }
    }

    /**
     * Notices that a different picture is on the table and starts the page turn.
     *
     * <p>Compared by identity: every path that deals a new board — the next chapter, the
     * next endless seed, a size change, restarting the book — builds a new {@link Puzzle},
     * and nothing that leaves the same one on the table mutates the reference.
     */
    private void notePuzzle(Puzzle puzzle, long now) {
        if (lastPuzzle != null && lastPuzzle != puzzle) {
            handoverAt = now;
        }
        lastPuzzle = puzzle;
    }

    /**
     * True while something on screen still needs another frame.
     *
     * <p>This has to agree with what the scenes actually draw, in both directions. Asking
     * for frames nobody needs burns a TV's power all evening; not asking for them is
     * worse, because a cursor whose breath is drawn but not driven simply freezes at
     * whatever phase it happened to reach — which reads as a bug rather than as calm.
     */
    public boolean animating(GameState game, UiState ui, Effects effects, long now) {
        if (ui.won) {
            // The win card is a fixed choreography with a stated end, so it is asked when
            // it stops rather than guessed at. Folded in with the menus it answered
            // "!calmMotion || effects.busy" — which froze the celebration part way through
            // its entrance for anyone with Calmer Animation on, and repainted a finished
            // static card at 60 fps for ever for everyone else.
            return effects.busy(now) || now - ui.winAt < WinScene.ambientUntilMs();
        }
        if (ui.screen != UiState.GAME) {
            // Both menus carry a gentle idle pulse, unless the player has asked for calm —
            // in which case they are genuinely still and can stop.
            return !Comfort.get().calmMotion || effects.busy(now);
        }
        if (handoverAt > 0 && now - handoverAt < WinScene.HANDOVER_MS) {
            return true;
        }
        if (effects.busy(now) || ui.cursorsSettling(game) || hud.toastVisible(ui, now)) {
            return true;
        }
        // The cursors breathe, and adjacent or shared squares carry a beating heart.
        return !Comfort.get().calmMotion;
    }
}
