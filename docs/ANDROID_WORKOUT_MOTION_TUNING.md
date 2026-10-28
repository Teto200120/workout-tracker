# Workout motion tuning - v36

Reviewed the user's 17.864-second recording of repeated reordering and opening workout cards. This update keeps the v35 workout presentation, icons, controls and data behavior.

Disclosure reveals the body from the top. Compose's default bottom alignment made entry fields travel through the header as the bounds grew or shrank. The 12dp header/body gap now belongs to the animated body, avoiding its abrupt removal after collapse. The small whole-card settling transform also uses a top origin. The existing 280ms PWA disclosure curve is retained.

Reordering compares the dragged card's center with neighboring centers. The old leading-edge thresholds could swap a card before its center crossed, then swap it back after a small finger reversal. Neighbor movement and release settling use the same 220ms control curve. Release captures the visible card position synchronously, so starting the animation cannot show its previous stored offset for a frame. Accessible move actions also keep the moved card visible, correcting the v35 GitHub test failure. The existing boundary assertion is retained and strengthened to require visibility. The list still owns the gesture; only a completed drop saves the order, and cancellation restores the saved order.

Automatic focus scrolling now follows the measured card position each frame. A fixed scroll distance calculated while the previous card was still shrinking became stale and could overshoot. Manual input can still cancel automatic scrolling. Motion-disabled behavior remains immediate.

Validation: 114 unit tests, 12 device tests, formatting, debug/release builds and zero lint errors. Four new regressions check center crossing/reversal, immediate drop continuity, top-anchored fields, and monotonic focus while the previous card collapses. Existing drag cancellation, edge scrolling, disabled-animation, numeric entry, workout data and lifecycle checks remain. The permanently signed upgrade preserves all synthetic record/document/draft payloads and revisions. The final signed v36 replacement was checked again against the original v35 database through a cold restart; installed APK bytes and real keyboard entry are verified.

Release-build frame samples use the same freshly started Android 15 emulator, synthetic 13-exercise workout, full compilation, 1x animations and gestures for both versions, after compilation work had finished. Screen recording is excluded from timing. One run per version is recorded in [the verification JSON](verification/android-v36-motion-tuning.json); a 30-second Perfetto trace was also captured for each drag flow. Earlier emulator runs varied substantially, including the unchanged v35 baseline. These samples help investigate delays and do not prove a phone frame-rate improvement.

- Drag: v35 102/244 late frames (41.8%), v36 69/307 (22.48%). 95th percentile: 61ms to 48ms.
- Disclosure: v35 54/302 late frames (17.88%), v36 38/321 (11.84%). 95th percentile: 46ms to 34ms.

Physical-phone timing, animation feel and TalkBack remain unverified. No dependencies, database schemas, backup formats or permissions change.

Reference: [Compose vertical reveal defaults](https://developer.android.com/reference/kotlin/androidx/compose/animation/package-summary#expandVertically(androidx.compose.animation.core.FiniteAnimationSpec,androidx.compose.ui.Alignment.Vertical,kotlin.Boolean,kotlin.Function1)) and [animation work by Compose phase](https://developer.android.com/develop/ui/compose/performance/phases). Animation positions remain read in layout/drawing code.
