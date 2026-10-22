# OneNet

Pixel's combined **Internet** tile (Android 12–16) for Samsung One UI 9: one quick-panel tile for Wi-Fi and mobile data, drawn in One UI's style. Stretch it to 2× wide to see which network you're on.

| Connected via | Tile shows |
| --- | --- |
| Wi-Fi | Internet · *Wi-Fi name* |
| Mobile data | Internet · *Carrier, 5G* |
| Nothing | Internet · Networks available / Networks unavailable / Flight mode |

These are the same rules Pixels use. If your quick panel shows only one line, turn on **Network name as title** in the app.

- **Tap:** opens the system internet panel for now. OneNet's own Pixel-style panel is next.
- **Long-press:** opens Wi-Fi settings, One UI's nearest match to the Internet page Pixels open.

Pixels on Android 17 split this tile into separate Wi-Fi and mobile data tiles. OneNet keeps the combined one.

## Requirements

- Android 17 (One UI 9)
- [Shizuku](https://shizuku.rikka.app/), for the Wi-Fi name. Android doesn't let a tile read it otherwise.

## Install

1. Download the APK from the latest [Build](../../actions/workflows/build.yml) run (artifact `onenet-<n>`).
2. Open OneNet, allow Shizuku, then tap **Add Internet tile**. You can also add it from the quick panel: Edit › Add a control.
3. In the quick panel's Edit mode, drag the tile's right edge to make it 2× wide.

Every build is signed with the key in `keystore/`, so new builds install over old ones. That key is public; replace it before distributing builds to other people.

## Build

```sh
./gradlew testDebugUnitTest assembleRelease
```

Needs JDK 21 and the Android 17 SDK (`platforms;android-37.0`). Tile icons are generated: `python3 tools/gen_icons.py app/src/main/res/drawable`.
