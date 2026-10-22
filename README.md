# OneNet

Pixel's combined **Internet** tile (Android 12–16) for Samsung One UI 9: one quick-panel tile for Wi-Fi and mobile data, in One UI's style and words. Stretch it to 2× wide to see which network you're on.

| Connected via | Tile shows |
| --- | --- |
| Wi-Fi | Wi-Fi · *Wi-Fi name* |
| Mobile data | Mobile data · *Carrier, 5G* |
| Nothing | Wi-Fi · Networks available / Networks unavailable / Off / Flight mode |

Pixel's rules, One UI's tile names. If your quick panel shows only one line, turn on **Network name as title** in the app.

- **Tap:** Pixel's Wi-Fi toggle. On Wi-Fi it pauses Wi-Fi so mobile data takes over (the radio stays on). Tap again and it searches, names the network as it connects, then shows it in use. If Wi-Fi is off, tap turns it on.
- **Long-press:** Pixel's internet panel: mobile data and Wi-Fi switches, the connected network, and nearby networks to join. Tap the carrier to switch to mobile data.

Behaviour follows the code Pixels run (AOSP Android 17). Pixels on Android 17 split the tile into separate Wi-Fi and mobile data tiles; OneNet keeps one tile. Changing networks needs Shizuku.

## Requirements

- Android 17 (One UI 9)
- [Shizuku](https://shizuku.rikka.app/), to read the Wi-Fi name and switch networks. Android doesn't let a tile do either otherwise.

## Install

1. Download the APK from [Releases](../../releases/latest), or the latest [Build](../../actions/workflows/build.yml) run (artifact `onenet-<n>`).
2. Open OneNet, allow Shizuku, then tap **Add Internet tile**. You can also add it from the quick panel: Edit › Add a control.
3. In the quick panel's Edit mode, drag the tile's right edge to make it 2× wide.

Every build is signed with the key in `keystore/`, so new builds install over old ones. That key is public; replace it before distributing builds to other people.

## Build

```sh
./gradlew testDebugUnitTest assembleRelease
```

Needs JDK 21 and the Android 17 SDK (`platforms;android-37.0`). Tile icons are generated: `python3 tools/gen_icons.py app/src/main/res/drawable`.
