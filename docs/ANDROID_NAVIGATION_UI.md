# Navigation and accessibility UI pass

Keep the reviewed Material navigation bar at normal text sizes, with Today, History, Stats and You. At large text sizes, use wider labelled tab surfaces: two columns on a phone and four when the window has enough room. Let text and height expand; never reduce the user's font scale or hide destination names. Retain selected tab semantics, icon/text contrast and at least 48dp touch targets.

Use the same navigation callbacks, single-top behavior, saved destination state and Back handling. Editors and the active session keep their existing dedicated navigation. This pass changes presentation only.

Verify all four destinations, light/dark themes, 200% text, landscape, keyboard visibility and stored-data stability on the owned emulator. The maintained workflow covers editor conflicts, Activity recreation, imports/exports and reviewed data actions. Physical-phone and TalkBack acceptance remain separate; this checkpoint does not certify those.
