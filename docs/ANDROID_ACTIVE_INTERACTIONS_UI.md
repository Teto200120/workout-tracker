# Active workout - touch, drag order and focused exercise logging

Reference: `src/js/screens/active-workout.js` (`startExerciseDrag`, `moveExerciseDrag`, `endExerciseDrag`, `openExerciseDetail`, `renderExerciseLogContent`) and the rendered PWA at 412 x 915. Preserve the established navy/coral Material surfaces, native controls, input sizes and completion feedback preferences.

## Touch interaction

The whole exercise header expands or collapses its card. A tap anywhere on the collapsed card body also opens it. The arrow is a visual cue, rather than the sole target. Keep independent touch handling for Exercise Details, reordering, set inputs and controls. A header exposes one named button with expanded/collapsed state and its exercise position/progress; its text remains available to accessibility services.

## Exercise order

Drag lives directly in the active workout list. Touch the 48dp grip on a card and move it immediately, then release at the destination. There is no ordering page, mode switch or required long press. The actual card lifts above a same-height placeholder while neighboring cards move aside. Edge scrolling continues through a long list even after the source row leaves lazy composition. The list owns the pointer stream; scrolling and input outside a grip retain normal behavior. Motion follows the existing animation preference.

Only a completed drop commits the preview. Cancellation, Back, rotation or a changed viewport discard it. Synthesized consumed cancellation releases must never count as a physical lift. The commit sends a complete permutation of stable exercise keys through the existing draft write queue. Reject stale, incomplete, duplicate or foreign key lists so a concurrent insertion/removal cannot lose an exercise. Preserve each row's notes, sets, flags, origin routine and identity, including repeated names. Native Move up/Move down accessibility actions are attached to each actual card and grip.

## Current exercise and compact cards

Routine and scheduled-routine starts persist only the first exercise expanded. Opening another card collapses the previous one. Adding an exercise activates that new row. When displaying a legacy draft with several expanded exercises, show only the first previously expanded row. Opening or resuming the app leaves its saved payload and revision untouched. An explicit edit uses that same presentation before applying and saving the change. Preserve an intentional all-collapsed draft, valid current selection, every entered value and the interchange format. See [draft recovery](ANDROID_DRAFT_RECOVERY.md).

On load, header touch or Next, place the expanded card in the usable list area above the completion dock. Fitting cards center at 44 percent of the remaining space, matching the PWA's focus band; tall cards align to the top. Measure after expansion, use stable keys and do not recenter during a drag, manual scrolling, set entry or timer ticks.

A collapsed card shows its order badge, name, short completed/total working-set count, grip and expansion cue. Expanded cards retain current/all-set entry and a compact action row: focused details, add set, next/review and overflow. The overflow retains guide, training guidance and exercise removal. Detailed completed rows are available through All sets and the focused view instead of filling every collapsed card. The primary dock remains the prominent completion action. Action rows wrap at large text sizes.

## Focused exercise view

Exercise Details opens a full page for that exact exercise key, with a pinned Back action, position/completion summary, Log/Guide selection and an independent scroll area. The Log view includes real last/target/PR summaries, current-set selection and All sets, the existing weight/reps/RPE controls, completion/warm-up controls, add/remove set, undo last warm-up, exercise notes, detailed training guidance and guarded previous/target-set copying. Preserve the existing offline guide, catalog matching, provenance, fallback and licensing in the Guide view; do not invent muscle or experience metadata.

Notes write to the exercise's existing notes field and follow the backup codec's 4,000-character limit. Removing a set with entered values or a completed flag asks for confirmation and retains at least one set, matching the native draft contract. Unchecking a warm-up does not erase other values.

A coral bottom action completes the displayed set, chooses the next incomplete set, advances to the next exercise or opens the existing finish review. Hide that dock during text entry so the IME cannot cover fields. Compact the toolbar in short windows, hide the tabs during editing and give the editor the full remaining area when the keyboard leaves very little height. Keep focused notes visible after the IME resizes the page. Previous/Next, selected sets and notes refer to stable keys and the current exercise order. Returning to the workout preserves its position. Focused exercise, tab, selected set and entered data survive activity recreation; committed order and edits survive a cold restart. Finish/copy/discard confirmations and revision/epoch fences remain unchanged. No persistence schema or export format changes.

## Verification

The maintained end-to-end flow checks real immediate in-list drops with one persisted revision and synthetic pointer cancellation without writes, as well as name-area and collapsed-card taps, focused set/notes writes, Guide browsing without draft writes, recreation with the focused tab retained, adding/removing a blank set, previous/next navigation, accessible reordering and exact export/draft/history preservation. Unit tests cover arbitrary permutations, repeated names, invalid/stale key orders, round-trip drafts and note boundaries.

Host-driven emulator checks use actual touch events for immediate inline drops for collapsed and expanded cards, initial focus placement, legacy repair, a cancelled moving preview and edge auto-scroll with a larger repeated-name fixture. Compare stored rows and complete draft contents, including blank versus zero inputs, notes, warm-ups and completed sets. Check dark/light, 200% text, landscape and narrow widths. Verify the delivered permanent-key v29-to-v30 APK upgrade, installed bytes, non-debuggable flag, cold recovery and a real keyboard. Physical-device and TalkBack acceptance remain separate from these emulator checks.

## Implementation reference check

Compared the native flow with the Apache-2.0 [Calvin-LL/Reorderable](https://github.com/Calvin-LL/Reorderable) design (immediate handles, keyed variable-height rows, edge scrolling and animateItem) and Apache-2.0 [ComposeReorderable](https://github.com/aclassen/ComposeReorderable) (list-owned state/gesture modifiers, keyed item rendering). The former was non-archived and last pushed April 20, 2026 when checked; the latter documents a first-visible-item animation limitation. The existing pinned Compose APIs implement this narrow vertical workout flow without another dependency or copied library source. Explicit drop-only persistence remains specific to this app's draft contract.

The Reorderable issue reports about [dragged rows leaving composition](https://github.com/Calvin-LL/Reorderable/issues/103), [start-time list updates](https://github.com/Calvin-LL/Reorderable/issues/107) and [stale callbacks](https://github.com/Calvin-LL/Reorderable/issues/102) informed the checks: the stable list owns the gesture, a separate lifted card survives virtualization, starting does not write/change the persisted list and pointer callbacks read current state. These are reported cases used as test prompts, not claims that the external library is unusable.
