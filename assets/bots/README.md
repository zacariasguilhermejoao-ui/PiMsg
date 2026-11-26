# PiMsg bot visual assets

- `bot_icon.svg`: supplied bot artwork.
- `bot_working.svg`: supplied working/active bot artwork.

The two uploaded SVG files were byte-identical. Both currently contain the same CSS animations: gentle floating, blinking eyes, and pulsing antenna lights. They are retained as supplied, under separate names so the app can assign each role.

Android's native drawable/vector resource system does not execute CSS keyframe animations inside an SVG. These are source assets, not yet wired into the Android UI. Before UI integration, render them with a compatible SVG/WebView approach or convert the animation into an Android-supported animated asset while preserving the supplied appearance.
