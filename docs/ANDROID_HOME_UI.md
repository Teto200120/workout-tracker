# Home UI — controlled PWA import

Scope: Home only, following the rendered PWA Today screen and its selection/preview/start flow. The reference is `src/js/screens/today.js` and `src/styles/today-components.css`. Keep native Compose and the established Material Expressive theme.

## Composition

1. Compact greeting with the name accented inline, date underneath and a small streak pill.
2. Compact weekly card with seven circular day markers, the actual planned-day completion count, explicit current-day treatment and accessible date/status labels.
3. One workout card: status, selected or active workout, supporting copy, real exercise/set counts, optional exercise preview and a compact selection row.
4. Quiet routine/schedule tools and optional Home guidance.
5. One Start/Resume action, fixed above native navigation. Reserve its layout space so scrolling controls cannot sit underneath it.

The previous Saved workouts/Routines metric tiles and repeated Today's plan card are removed from Home. Their underlying records and editor routes remain available. Use 20dp page gutters, 20dp section spacing, a 28dp workout surface, compact 20dp secondary surfaces, a 34/38sp workout title and a minimum 64dp primary action. Surfaces use navy/coral tonal roles in dark mode and readable tonal light equivalents. Use actual scalable text and content height, not a fixed-height mockup.

## Interaction

Change opens a native routine chooser. Selection updates the preview without creating a draft; Start begins that selection. A Quick workout option remains available. Preview lists the selected routine's exercises in their original order. Resume opens the durable active draft. Selecting the scheduled routine keeps the existing schedule/date validation before starting. Imported typed IDs, exact exercise names and routine order are retained.

Start/Resume stays reachable while Home scrolls. At larger text sizes and in landscape, the content scrolls independently above it. No fabricated duration or muscle-group estimates are shown.

## Material adaptation

Keep a clear typography hierarchy, tonal grouping, rounded shape hierarchy, a single prominent primary action and standard native controls. Guidance and exercise details open only on request. Honor the existing Expressive theme, system theme, motion/haptic preferences and native system insets. This pass does not change global typography, navigation or other screens.

References: [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3), [Material typography](https://m3.material.io/styles/typography/applying-type).

## Review cadence

Continue the visual port one screen at a time. Inspect that screen's rendered PWA states and source, specify the native hierarchy and interactions, implement only that scope, and compare real emulator screenshots before delivery. Preserve data assertions when adapting tests to intentional UI changes. Deliver a versioned signed APK, GitHub Release and screenshots so the user can review the result before choosing the next screen.
