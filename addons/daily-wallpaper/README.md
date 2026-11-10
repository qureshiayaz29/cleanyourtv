# Daily Wallpaper

Bing's picture of the day as the wallpaper of the [Clean Your TV](../../README.md) launcher. The add-on downloads
the picture in the background once a day, darkens it so the clock and the tiles stay readable, and hands it to
the launcher as its `wallpaper.jpg`. Home is never changed while you are looking at it: the new picture is
there when Home next starts.

It declares a [module card](../../docs/modules.md), so once you answer **Show card** its tile reads
"Daily Wallpaper". The card's screen says when the picture last changed and what it shows, and has three buttons:

- **Update now** downloads today's picture at once.
- **Darken the picture** steps through 0, 15, 30 (the default), 45 and 60 %. The shade is painted into the
  picture, so it shows with the next download or with Update now.
- **Daily update** switches the background job off and on.

## How it works

- **The picture** comes from `https://www.bing.com/HPImageArchive.aspx`, the feed behind Bing's home page, in
  the edition for the TV's language and country (`en-US` when Bing has none), at 1920x1080. Nothing else is
  contacted, and nothing about the TV is sent beyond the request itself. No account, no key.
- **The download** is a JobScheduler job that Android runs about every six hours while the TV has a network.
  Most runs only look at the clock; one a day reads the feed, and the picture is fetched only when the feed
  names a new one. No service stays running in between, and one picture replaces the last, so nothing piles up.
- **The hand-over** uses the launcher's own theme provider (`content://com.gathin.tvhome.theme/wallpaper.jpg`),
  the route `tools/theme.sh` takes. Nothing under `launcher/` changes and the launcher still has no network access.
- **Home picks it up when it starts.** The launcher reads its wallpaper once, when Home's process starts, so
  the new picture shows at Home's next start: a restart of the TV, Android reclaiming Home's memory while
  another app is on screen (often, on a 1 GB TV), or a theme push. The add-on cannot restart Home itself. A
  launcher that re-read a replaced `wallpaper.jpg` as Home comes forward would show it the next time Home is
  opened; that is a launcher change and is proposed separately.

## What it needs from the theme

The launcher draws `wallpaper.jpg` only when the theme allows a wallpaper and has no rotating collection of
its own, so the theme decides whether the picture is seen:

- **Works as it is:** `ruska`, `red-rain`, `cinematic`, `christmas`. Each has one picture of its own, which the
  add-on replaces, and no colour effect over it.
- **Shows the picture with a tint:** `dock-glass`, `aurora`, `glass` (see the two settings below).
- **Shows no wallpaper at all:** `default`, `minimal`, `six-columns` set `"wallpaper": false`.
- **Keeps its own pictures:** every theme with a `wallpapers/` folder and `wallpaper_rotation_minutes` above 0
  (`istanbul`, `scenes`, `stockholm`, ...), and a picture picked under Launcher settings > Wallpaper.

Pushing a theme with `tools/theme.sh` replaces `wallpaper.jpg`; press **Update now** afterwards, or wait for
the next day's picture.

A theme's colour effects are drawn over the photo: `ambient_glow` washes it in the focused app's colour and
`sky_intensity` lays a time-of-day gradient on it. For the picture as it is, set `"ambient_glow": false` and
`"sky_intensity": 0` in the theme's `config.json`.

The pictures are Bing's and stay on your TV as your wallpaper; none is in this repository.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | `www.bing.com` only: the picture-of-the-day feed and the picture, at most once a day. |
| `WRITE_SECURE_SETTINGS` | The launcher's theme provider takes a wallpaper only from a caller that holds it. Android lets only ADB grant it; `install.sh` does. No setting is read or written. Without it the card says what to run. |
| `RECEIVE_BOOT_COMPLETED` | Keeps the daily job registered across a restart of the TV. The app itself is not started at boot. |

Package visibility is limited to `com.gathin.tvhome`. A background job, no foreground service. No accounts,
telemetry, ads or libraries.

## Build and install

JDK 17 and Android SDK platform 34 with build-tools 34.0.0, as for the launcher. From the repo root, with ADB on:

```bash
TV_HOST=<tv ip> addons/daily-wallpaper/install.sh
```

It builds the add-on, installs it, grants the permission for the hand-over and opens it. Press **Update now**
for the first picture, then Home. To build only: `cd addons/daily-wallpaper && ./gradlew assembleRelease`;
the JVM tests run with `./gradlew testReleaseUnitTest`.

The release build is signed with the debug key so it installs over ADB like the launcher. Sign your own builds
with your own key if you hand them out; the launcher pins its Show answer to the signing key.

## Limits

- **Only this launcher.** It writes through `com.gathin.tvhome`'s provider; a launcher built with another
  `launcherApplicationId` is not found, and on any other home screen the card says so.
- **A TV that sleeps without Wi-Fi** gets its picture soon after it wakes, so the new one shows the time after.
- **One size.** The picture is 1920x1080; the launcher scales it to the screen.

## Tested on

Changhong AI PONT (ikebukuro, MediaTek, 0.9 GB), Android TV 11, firmware `RTO2.250804.001`, over ADB on Wi-Fi:

- `install.sh` built and installed the add-on and granted `WRITE_SECURE_SETTINGS`; the card screen opened.
- **Update now** fetched the day's picture in about a second, the status read "Picture updated" with Bing's
  caption under it, and the launcher's `wallpaper.jpg` was replaced (about 320 KB).
- The daily job was registered as periodic (6 h) and persisted, and its first run finished in about a second.
- With the `ruska` theme, Home showed the picture as it is, in place of the theme's own, after Home restarted.
  On a launcher build with a re-read check in `WallpaperPlayer.start` (proposed separately), it showed the
  next time Home was opened, with no restart.
- With `dock-glass`, Home showed the picture under the theme's colour glow, as described above.
- With `istanbul_v2` (a rotating collection), Home kept the theme's pictures, as described above.

Not tested: a day-to-day change of picture by the job alone, a restart of the TV, other TVs or Android versions.
An earlier build asked Android to drop Home's background process (`killBackgroundProcesses`) so Home would
start again; this TV ignored that request from an app, so it was removed.

## Maintainer

[@qureshiayaz29](https://github.com/qureshiayaz29)
