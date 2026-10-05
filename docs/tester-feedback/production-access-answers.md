# Production access: draft answers

Draft answers for Google Play's production-access questionnaire, based on the
tester's template ([`com.cozygrams.tv_production.pdf`](com.cozygrams.tv_production.pdf))
but rewritten to match what actually changed. Brad: check every **[BRAD: ...]**
spot before pasting.

---

### 1. How did you recruit users for your closed test?

I used a paid testing provider so CozyGrams got a proper run on a range of
real phones, tablets and Android versions, with a written report at the end.
**[BRAD: if family or friends also played it on the TV, add one sentence, e.g.
"I also played it on the couch with family on an NVIDIA Shield to check the
two-controller co-op." Otherwise delete this note.]**

### 2. How easy was it to recruit testers for your app?

**[BRAD: pick one: Very easy / Easy / Neither easy nor difficult / Difficult / Very difficult.]**
(The template suggests "Easy". With a paid provider that's honest.)

### 3. Describe the engagement you received from testers during your closed test

Testers played through the game on several devices and Android versions:
the Story Book, Endless boards at different sizes, the Cozy Corner settings
and the touch controls. They reported no crashes, bugs or broken features.
Their feedback was about helping new players get started, showing the TV and
co-op side of the game better in the store, and small features around the
game such as sharing and sending feedback.

### 4. Provide a summary of the feedback that you received from testers. Include how you collected the feedback.

The feedback came as a written report from the testing provider at the end of
the test. It found no crashes or bugs. The suggestions were: an interactive
walkthrough for new players, a help section and first-time tooltips;
screenshots that show the Android TV experience and two-controller play, with
captions; a rate option and a share option in the settings; a way to send
feedback; and keeping the game accessible. They also suggested review prompts
and social media integration, which I chose not to add (see question 8).

### 5. Who is the intended audience for your app?

People who like relaxing logic puzzles, and especially couples, families and
friends who want something calm to solve together on the TV. Nonogram fans
get a deep, never-guess puzzle set; newcomers get a guided first puzzle. It
works for all ages, on Android TV with a gamepad or a plain remote, and on
phones and tablets by touch.

### 6. Describe how your app provides value to the users.

CozyGrams is a cozy nonogram game built for the couch: two controllers solve
one picture together with their own cursors, and there is no scoreboard.
There's a 24-chapter handmade Story Book and an endless deck of pictures,
every one checked so the clues alone are always enough. It has hand-painted
scenes, a music box score, comfort options (larger text, extra contrast,
color-blind-friendly player identity, reduced motion) and TalkBack support.
It's free, with no ads, no in-app purchases, no accounts and no network
access at all.

### 7. How many installs do you expect your app to have in your first year?

**[BRAD: pick a range. The template suggests 10K - 100K. For a TV-first
puzzle game with no marketing, something like 1K - 10K may be more realistic.
Your call.]**

### 8. What changes did you make to your app based on what you learned during your closed test?

- Added a guided first-evening tour that solves a small puzzle with the
  player step by step: it reads a clue, lets them fill a square and cross one
  out with their own controller, remote or finger, shows the finished
  picture, explains the controls and how a second controller joins, and
  points to the Story Book. It can be skipped at any step and replayed.
- Added one-time tips that point at key buttons the first time a player
  meets them, and an in-game How to play section with the rules, controls,
  co-op and common questions.
- Rebuilt the store screenshots around the TV and two-controller co-op, with
  captions for the co-op play, the painted scenes and music, and the Story
  Book, plus a new feature graphic.
- Added "Rate on Google Play", "Share CozyGrams" and "Send feedback" to the
  settings menu. The game never asks for a rating on its own: no pop-ups, no
  prompts after chapters, by design.
- Made TalkBack read each square with its clues, and checked every new
  screen with larger text, extra contrast and reduced motion.
- I didn't add social media integration or the In-App Review API: both go
  against the game's no-tracking, no-nagging promise.

### 9. How did you decide that your app is ready for production?

The closed test found no crashes or bugs on any device, every change since
then is covered by automated tests and rendered screen by screen at every
supported size, and the features testers asked for that fit the game are now
in. It runs the same on TVs, phones and tablets, and it keeps the promise of
no ads, no purchases and no data collection.

### 10. What did you do differently this time?

**[BRAD: if this is your first app on Play, say so plainly, e.g. "This is my
first game on Google Play, so this was the first closed test I've run." If
not, describe what you changed compared to the last one.]** I paid particular
attention to the first five minutes, since a nonogram can be confusing to
someone who has never seen one, and to showing the TV and co-op experience
rather than just the phone.
