# Workout motion and card clarity — v34

Dragging a workout card now keeps its original row composed while a lifted copy follows the finger. Neighboring rows move without fading, and the lifted card settles into the measured destination before it is removed. Moving the first visible card keeps the viewport in place. Edge scrolling follows display frames, and only dropping commits the order; cancellation still changes no workout data.

Card disclosure, exercise details, Log/Guide and navigation use short, consistent transitions. The animation preference remains respected. New blank workouts retain their name and overview on entry; workouts with entries resume at the current exercise.

Exercise cards use quieter numbers, borders and grips. Weight, reps and RPE are grouped where width permits; narrow screens and large text retain stacked controls. Details place logging before supporting metrics, with one grouped summary and unavailable metrics omitted. Typing, steppers, warm-ups, completion, guides and set selection remain available.

The source was checked with 114 unit tests and the full workout flow plus four drag tests. The drag tests use a controlled motion scale so their manual clock behaves consistently when CI disables system animations, following [Android's testing guidance](https://developer.android.com/reference/kotlin/androidx/compose/ui/MotionDurationScale). Release checks and APK details are recorded in [verification](verification/android-v34-workout-motion.json).

The single Android 16 emulator comparison used two warm-up drags and six measured drags per build with screen recording enabled. Missed frame deadlines changed from 39.06% to 33.57%; median time changed from 28ms to 27ms, 90th percentile from 48ms to 53ms, and 99th from 97ms to 81ms. Results are mixed and do not establish physical-phone smoothness. Rendered checks include dark/light modes, 200% text, 320dp width and the real keyboard. Physical-phone and TalkBack acceptance remain outstanding.

No dependency, database schema or backup-format changes are included. The permanent signing identity and existing GitHub prerelease download process are retained.
