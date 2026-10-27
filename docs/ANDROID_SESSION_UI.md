# Active workout UI — controlled PWA import

Reference: the rendered PWA live-session view and `src/styles/session-components.css`. Focus this checkpoint on active logging.

Use a compact editable workout header with elapsed time, Back and explicit Finish review. Show progress and the exercise picker above the exercise cards. Each expanded exercise defaults to a single set with vertically grouped weight/reps/RPE controls. A native set selector and All sets view retain direct access to completed and upcoming rows. Keep completion and warm-up controls explicit.

The bottom primary action completes the displayed set, advances to the next incomplete set, or offers Next exercise / Review workout. Its subtitle identifies the exercise. Reserve layout space for this action and hide it while the IME is visible. The focused exercise/set determines the action; it must never silently complete another visible exercise's set.

Changing the selected set or opening All sets changes presentation only. Set edits remain immediate durable writes through the existing ViewModel. Completion advances presentation after persisting the set's state; unchecking and editing completed sets remain possible. Keep the original working-set metrics, RPE provenance, typed IDs, set keys, exercise ordering, save/discard confirmations, copy-set confirmation and epoch/revision fences.

Elapsed time uses the existing monotonic/reboot-aware calculation. Rendering the timer never writes draft data. Observe it only while resumed. At large text sizes, numeric entry expands to full width with its step controls underneath. Native dialogs and the exercise list remain scrollable.

Material adaptation: navy tonal surfaces, coral emphasis, a restrained shape hierarchy, large numeric values, native text input and 48dp control targets. Inline guidance, exercise tools and reordering remain below entry. Existing interaction preferences apply.

The v26 expressive treatment follows the shared navy/coral shapes, tonal depth and compact/summary heading hierarchy. Existing flows and data contracts remain intact. See [remaining-screen polish](ANDROID_REMAINING_LOOK_UI.md).
