# Active workout - touch, drag order and focused exercise logging

Reference: `src/js/screens/active-workout.js` (`startExerciseDrag`, `moveExerciseDrag`, `endExerciseDrag`, `openExerciseDetail`, `renderExerciseLogContent`) and the rendered PWA at 412 x 915. Preserve the established navy/coral Material surfaces, native controls, input sizes and completion feedback preferences.

## Touch interaction

The whole exercise header expands or collapses its card. A tap anywhere on the collapsed card body also opens it. The arrow is a visual cue, rather than the sole target. Keep independent touch handling for Exercise Details, reordering, set inputs and controls. A header exposes one named button with expanded/collapsed state and its exercise position/progress; its text remains available to accessibility services.

## Exercise order

A Reorder exercises action above the cards and a grip action on every card open a compact native order page. This adaptation avoids dragging an expanded logging form through other full forms. Touch and hold a handle, then drag the card to its destination. The moving card is elevated and highlighted; other cards move to show the preview. Scrolling near the list edges brings further rows into reach. Motion follows the existing animation preference.

Only a completed drop commits the preview. Cancellation, Back or interruption discard it. The commit sends a complete permutation of stable exercise keys through the existing draft write queue. Reject stale, incomplete, duplicate or foreign key lists so a concurrent insertion/removal cannot lose an exercise. Preserve each row's notes, sets, flags, origin routine and identity, including repeated names. Native Move up/Move down accessibility actions work in the order page; the previous inline move buttons remain available.

## Focused exercise view

Exercise Details opens a full page for that exact exercise key, with a pinned Back action, position/completion summary, Log/Guide selection and an independent scroll area. The Log view includes real last/target/PR summaries, current-set selection and All sets, the existing weight/reps/RPE controls, completion/warm-up controls, add/remove set, undo last warm-up, exercise notes, detailed training guidance and guarded previous/target-set copying. Preserve the existing offline guide, catalog matching, provenance, fallback and licensing in the Guide view; do not invent muscle or experience metadata.

Notes write to the exercise's existing notes field and follow the backup codec's 4,000-character limit. Removing a set with entered values or a completed flag asks for confirmation and retains at least one set, matching the native draft contract. Unchecking a warm-up does not erase other values.

A coral bottom action completes the displayed set, chooses the next incomplete set, advances to the next exercise or opens the existing finish review. Hide that dock during text entry so the IME cannot cover fields. Compact the toolbar in short windows, hide the tabs during editing and give the editor the full remaining area when the keyboard leaves very little height. Keep focused notes visible after the IME resizes the page. Previous/Next, selected sets and notes refer to stable keys and the current exercise order. Returning to the workout preserves its position. Focused exercise, tab, selected set and entered data survive activity recreation; committed order and edits survive a cold restart. Finish/copy/discard confirmations and revision/epoch fences remain unchanged. No persistence schema or export format changes.

## Verification

The maintained end-to-end flow checks name-area and collapsed-body taps, focused set/notes writes, Guide browsing without draft writes, recreation with the focused tab retained, adding/removing a blank set, previous/next navigation, accessible reordering and exact export/draft/history preservation. Unit tests cover arbitrary permutations, repeated names, invalid/stale key orders, round-trip drafts and note boundaries.

Host-driven emulator checks use actual touch events for first/last drops, a cancelled moving preview and edge auto-scroll with a larger repeated-name fixture. Compare stored rows and complete draft contents, including blank versus zero inputs, notes, warm-ups and completed sets. Check dark/light, 200% text, landscape and narrow widths. Verify the delivered permanent-key v28-to-v29 APK upgrade, installed bytes, non-debuggable flag, cold recovery and a real keyboard. Physical-device and TalkBack acceptance remain separate from these emulator checks.
