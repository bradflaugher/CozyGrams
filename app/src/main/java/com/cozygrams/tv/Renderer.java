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

    public void setScenes(Bitmap room, Bitmap garden) {
        backdrop.setScenes(room, garden);
    }

    /** Geometry from the most recent frame, for effects that need board coordinates. */
    public BoardLayout board() {
        return lastBoard;
    }

    public void draw(Canvas canvas, float width, float height, GameState game, UiState ui,
                     Effects effects, long now) {
        Theme.setScreenHeight(height * (ui.bigTextOn ? 1.16f : 1f));
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

        // The board and its furniture stop being drawn once the win card has finished
        // lifting the picture off them. Left up, they ghosted through the celebration:
        // clue ticks, crosses and the rail's own labels were all legible as half-words
        // around the card, framing the reward with the workings that produced it.
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
        }
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
        if (ui.won || ui.screen != UiState.GAME) {
            // The win card and both menus carry a gentle idle pulse, unless the player has
            // asked for calm — in which case they are genuinely still and can stop.
            return !Comfort.get().calmMotion || effects.busy(now);
        }
        if (effects.busy(now) || ui.cursorsSettling(game) || hud.toastVisible(ui, now)) {
            return true;
        }
        // The cursors breathe, and adjacent or shared squares carry a beating heart.
        return !Comfort.get().calmMotion;
    }
}
