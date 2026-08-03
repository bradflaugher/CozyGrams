/**
 * CozyGrams — a cozy two-player nonogram for the sofa.
 *
 * <p>The house rules, in the order a new contributor trips over them. Every class here has
 * its own Javadoc explaining <em>why</em> it is the shape it is; this page is the map, not
 * the manual.
 *
 * <h2>One Canvas, no layouts</h2>
 *
 * <p>There is exactly one {@code View} in the app — {@link com.cozygrams.tv.CozyGameView} —
 * and no XML layout, no {@code RecyclerView}, no Leanback fragment. Every pixel is drawn by
 * {@link com.cozygrams.tv.Renderer} onto one {@code android.graphics.Canvas} through the
 * primitives in {@link com.cozygrams.tv.Draw}. That is not minimalism for its own sake: it
 * is what makes the second rule possible.
 *
 * <h2>Rendering code touches no View, no Context and no input</h2>
 *
 * <p>{@code Renderer} and every scene under it are handed a canvas, a
 * {@link com.cozygrams.tv.GameState}, a {@link com.cozygrams.tv.UiState} and a clock, and
 * produce a picture. Nothing in that path reads a system service, a resource or a key event,
 * so a desktop JVM can render real frames through Java2D-backed {@code android.graphics}
 * stubs. Run it:
 *
 * <pre>
 *   COZY_BUILD_DIR=&lt;a scratch dir&gt; tools/preview/render.sh &lt;out dir&gt; 1920 1080
 *   COZY_BUILD_DIR=&lt;a scratch dir&gt; tools/preview/record.sh &lt;clip dir&gt; 1920 1080 30
 * </pre>
 *
 * <p>Break the rule and the harness stops compiling, which is the point: it is a compile
 * error rather than a convention. Frames carry a provenance stamp in the bottom-left corner
 * drawn outside the app's own code, so a screenshot can always be traced to the sources that
 * made it — it is the only part of a frame that is not the interface, and it is the only
 * part that changes when nothing else does.
 *
 * <h2>Measure it, do not assert it</h2>
 *
 * <p>This is the rule the codebase lives by. A claim about pixels — a contrast ratio, a
 * width, an arcminute figure — is written down only once somebody has taken it, and the
 * number goes in the Javadoc with it. Several comments here say what a previous approach
 * measured and why it was wrong; that is deliberate, and it is worth more than a tidy
 * sentence. Prose that has drifted ahead of the code is worse than no prose.
 *
 * <p>The corollary is a hard limit on unit tests. The test source set runs with
 * {@code returnDefaultValues}, so {@code Paint.measureText} answers zero and every
 * {@code android.graphics.Color} static answers zero — which is why
 * {@link com.cozygrams.tv.Theme#relativeLuminance(int)} and
 * {@link com.cozygrams.tv.Draw#blend(int, int, float)} do their own channel arithmetic with
 * shifts. Anything that depends on how wide a string comes out has to be checked by
 * rendering, not by asserting.
 *
 * <h2>Theme owns the tokens</h2>
 *
 * <p>Colours, the type scale, corner radii, stroke weights, the safe area and the durations
 * the whole interface breathes at are declared once in {@link com.cozygrams.tv.Theme} and
 * read from there. A hand-spelled colour or a bare {@code Math.max(1.5f, scale(2))} is a bug
 * report waiting to happen: two files disagreeing about the same number is how the paper
 * card and the co-op rail once ended up with different corners. Every colour pair the game
 * actually draws is asserted in {@code ThemeTest}.
 *
 * <h2>And five smaller ones</h2>
 *
 * <ul>
 *   <li><b>Nothing below the safe line.</b> {@link com.cozygrams.tv.Theme#SAFE_AREA} is 5%
 *       of each edge, because living-room panels overscan.</li>
 *   <li><b>Nothing is a scoreboard.</b> No timer, no rank, no per-player total on screen.
 *       See the notes on {@link com.cozygrams.tv.HudScene}.</li>
 *   <li><b>Every animation ends.</b> {@code Renderer.animating} has to agree with what the
 *       scenes actually draw in both directions — frames nobody needs burn a television's
 *       panel all evening, and frames somebody needed freeze a cursor mid-breath.</li>
 *   <li><b>Drawing allocates nothing.</b> One {@code Paint}, one {@code RectF}, one
 *       {@code Path}, and preallocated primitive arrays in
 *       {@link com.cozygrams.tv.Effects}.</li>
 *   <li><b>Comfort is honoured by whatever makes the movement.</b>
 *       {@link com.cozygrams.tv.Comfort#calmMotion} is checked by the emitter for particles
 *       and by the scene for a breath, so nothing is left pulsing because one call site
 *       forgot. It never takes a <em>confirmation</em> away — see the notes on
 *       {@link com.cozygrams.tv.Effects}.</li>
 * </ul>
 */
package com.cozygrams.tv;
