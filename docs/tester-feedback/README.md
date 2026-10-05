# Closed-test feedback

Before production access, CozyGrams went through a closed test with a paid
testing provider. Their two reports are here as they arrived:

- [`com.cozygrams.tv_feedback.pdf`](com.cozygrams.tv_feedback.pdf): the test
  report. No crashes, bugs or broken features on any device or SDK they tried,
  plus a list of suggested improvements.
- [`com.cozygrams.tv_production.pdf`](com.cozygrams.tv_production.pdf): template
  answers for Google Play's production-access questionnaire. Our own answers,
  accurate to what we actually changed, are in
  [`production-access-answers.md`](production-access-answers.md).

## What we did with each suggestion

| Suggestion | What we did | Where |
|---|---|---|
| **Dynamic walkthrough**: interactive onboarding with illustrations or animation | A first-evening **guided tour** that solves a real 5x5 heart with the player in eight short steps: it reads a clue out loud and fills the row it gives away (animated, square by square), hands the player one square to fill and one to cross out with whatever is in their hands (A / B on a gamepad, OK on a bare remote, tap and hold on a touch screen), shows the finished picture with every line ticked, then the controls, how a second controller joins as Sky, and where to go next (the Story Book and the Cozy Corner). Skip tour, Back and Next are D-pad focusable buttons with a visible focus ring; Back or Menu skips at any step; every step is read out by TalkBack; Reduce motion makes it still. It is offered once, only on a genuine first evening, and can be taken again from How to play. Screenshot and test runs skip it (`--ez skip_welcome true`; the preview harness never shows it unless a frame asks). | `Tutorial.java`, `TutorialScene.java`, `CozyGameView.java` (the tour section), `MainActivity.java` |
| **Help section** for later reference | **How to play**, a five-page help screen in the Cozy Corner: the basics, the controls (named for the gamepad, remote or touch screen actually in use), playing together as Rose and Sky, the Story Book and Endless, and questions (stuck, saving, privacy, seeing it better, ideas and bugs). Left and right or tabs turn the pages; A takes the tour again. | `HelpScene.java` |
| **Tooltips** for first-time users on key icons and buttons | One-time tips, a small bubble beside the control: the Story Book row and the Cozy Corner on the title screen; the clues, the FILL / CROSS pen and HINT (touch) or the button legend (controller), and how Sky joins, on the first puzzle. Each shows once per save, one at a time, never takes a press for itself (the next press does what it always does and puts the tip away), and fades on its own after seven seconds. Players from before the tips existed see none. | `Tips.java`, `Renderer.tipAnchor`, `CozyGameView.updateTips` |
| **Play Store screenshots**: show Android TV, captions on dual-controller play, visual styles and ambiance | The TV set is now seven captioned shots led by **two-controller co-op** (both cursors on the board), then the title screen, the moonlit garden (the second of the game's two painted rooms, with the music box score called out), the Story Book together, a finished chapter, the tour and How to play. The phone and both tablet sets now lead with a co-op shot too: a paired controller playing Sky beside the finger's Rose, which is exactly what those devices do. All captions are drawn by the preview harness in the game's own type and colours; the README shots stay uncaptioned. New feature graphic. The TV banner (320x180 in the app, 1280x720 in the listing) was checked and already reads well. | `tools/preview/Preview.java` (`STORE_CAPTIONS`, `captioned`, `featureGraphic`), `fastlane/metadata/android/en-US/images/` |
| **Video preview** | **Not done yet.** A Play promo video has to be a YouTube link, which Brad would upload. `tools/preview/record.sh` already renders gameplay clips (MP4) from the real renderer that could be the starting point. | `tools/preview/record.sh` |
| **Rate Your App** button in settings | Added **Rate on Google Play** to the Cozy Corner, next to Share and Send feedback. It opens the game's Play Store page (`market://`, falling back to the web page) and is hidden on a device with neither. | `SettingsScene.ITEM_RATE`, `CozyGameView.actOnRequest` |
| **In-App Review API** and **prompts at neutral moments** | **Not done, on purpose.** No review API, no new dependency, and nothing ever asks for a rating: not after a chapter, not after a number of evenings, not ever. The Rate row is there for anyone who goes looking for it. | none |
| **Share App** in settings | Added **Share CozyGrams** to the Cozy Corner: the Android share sheet with one warm line and the Play link. No permission needed. On a television with no share sheet the row says to find CozyGrams on Google Play instead. | `CozyGameView.shareIntent` |
| **Customizable share message** | The share sheet already lets people edit the text before sending, so there is no extra UI for it. | none |
| **Social media integration** | **Not done, on purpose.** No social SDKs: they would need network access and tracking, and the share sheet already reaches every app on the phone. | none |
| **Feedback mechanism** | Added **Send feedback**: it opens the GitHub new-issue page in a browser. No email address in the app. A television with no browser shows the address in words instead of failing. | `CozyGameView.feedbackIntent` |
| **Regular updates**, **community engagement** | Not code. New puzzles and features keep shipping through the same release pipeline; there is no social presence planned. | none |
| **Accessibility** | TalkBack now reads each square together with the row and column clues that cross it, every How to play page, every tour step and every tip. All new screens respect Larger text, the device font size and Extra contrast, keep the 48dp touch floor, and are fully D-pad navigable with visible focus. Rose and Sky already differ by letter (R and S) as well as colour, and Player colors deepens Sky's teal and dashes her ring. | `CozyGameView.describeSquare`, `HelpScene`, `TutorialScene`, `Tips` |
| **Graphics optimization** | The harness renders every new screen at 720p, 1080p, 4K-class tablet sizes, portrait and with Larger text, so layout regressions show up as pictures. The tour and tips only ask for frames while something on them moves, and Reduce motion stops that entirely. | `tools/preview/` |

## Unchanged promises

CozyGrams still has no `INTERNET` permission and collects nothing. Share,
Send feedback and Rate hand an intent to the share sheet, the browser or the
Play Store, and those apps do any networking. The `<queries>` entries in the
manifest only let the game ask whether such an app exists.
