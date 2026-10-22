# OneNet

Pixel's combined **Internet** tile (Android 12–16) for Samsung One UI 9: one quick-panel tile for Wi-Fi and mobile data, drawn in One UI's style. Stretch it to 2× wide to see which network you're on.

| Connected via | Tile shows |
| --- | --- |
| Wi-Fi | Internet · *Wi-Fi name* |
| Mobile data | Internet · *Carrier, 5G* |
| Nothing | Internet · Networks available / Networks unavailable / Flight mode |

These are the same rules Pixels use. If your quick panel shows only one line, turn on **Network name as title** in the app.

- **Tap:** Pixel's Wi-Fi toggle. On Wi-Fi it pauses Wi-Fi so mobile data takes over (the radio stays on); tap again to bring Wi-Fi back. If Wi-Fi is off, it turns it on.
- **Long-press:** Pixel's internet panel in One UI style: mobile data and Wi-Fi switches, the connected network, and nearby networks to join. Tap the carrier to switch to mobile data.

Behaviour follows the code Pixels run (AOSP Android 17): the toggle from the Wi-Fi tile, the panel and its wording from the internet dialog. Pixels on Android 17 split the tile into separate Wi-Fi and mobile data tiles; OneNet keeps one tile and puts the panel on long-press. Everything that changes networks needs Shizuku.

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
