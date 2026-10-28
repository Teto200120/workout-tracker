# Android 15 window recovery — v33

On Android 15, recreating the activity at the same screen size could restore content fitted between the system bars. Compose then received zero keyboard insets even while Android reported a visible keyboard. The workout completion button stayed visible and used 88dp that should have been available for entering sets. A normal cold launch and rotation did not reproduce this failure.

After state restoration, MainActivity restores the full-window layout flags on Android 15. This uses the existing Android compatibility approach, with no new dependency. The change applies only to restored API-35 activities; first launches and other Android versions retain their existing window setup. Both workout views continue to use the existing Compose keyboard handling.

The same viewport assertion reproduces the failure with the native activity alone, without replacing its UI for testing. It compares the list's bottom edge with the actual keyboard's top edge. The full workflow also checks entered values, saved drafts, drag order, cancellation and focused exercise interactions. Test content is installed on the UI thread, and keyboard failures now capture their geometry and a screenshot before dismissing the keyboard.

v33 retains the [read-only draft recovery](ANDROID_DRAFT_RECOVERY.md) and [v31 performance improvements](ANDROID_NATIVE_PERFORMANCE.md). Those performance figures belong to their recorded workloads; this correction has separate window and upgrade checks. Physical-phone and TalkBack acceptance remain unverified.

The window flags follow the compatibility handling in [AndroidX WindowCompat](https://github.com/androidx/androidx/blob/androidx-main/core/core/src/main/java/androidx/core/view/WindowCompat.java). Insets remain handled by Compose according to [Android's insets guidance](https://developer.android.com/develop/ui/compose/system/insets-ui).
