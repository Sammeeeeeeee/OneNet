# OneNet

A Pixel-style **Internet** tile for Samsung One UI 9: one quick-panel tile for Wi-Fi and mobile data. Stretch it to 2× wide to see the Wi-Fi name, or the carrier and network type.

| Connected via | Tile shows |
| --- | --- |
| Wi-Fi | Network name · Wi-Fi |
| Mobile data | Carrier · 5G / 4G |
| Nothing | Internet · Off / Not connected / Flight mode |

Settings has a switch for Pixel wording ("Internet" with the network name below).

- **Tap:** system internet panel (stand-in until OneNet's own One UI panel lands)
- **Long-press:** Wi-Fi settings

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

Needs JDK 21 and the Android 17 SDK (`platforms;android-37.0`).
