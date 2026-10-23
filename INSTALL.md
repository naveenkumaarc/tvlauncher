# Install R10 Home on Acer R10G

Keep Google TV Home enabled until R10 Home has been tested through multiple reboots.

## Install

Download the APK from the latest GitHub Actions run artifact named `R10Home-v1.0-debug` and extract it.

```cmd
adb install -r R10Home-v1.0-debug.apk
adb shell am start -n com.nk.r10home/.MainActivity
```

Test D-pad navigation, OK, Back, Settings, TV / Inputs, app launching and long-press OK for Favorites.

When satisfied, select it as HOME:

```cmd
adb shell cmd package set-home-activity --user 0 com.nk.r10home/.MainActivity
adb reboot
```

## Roll back to Google TV Home

```cmd
adb shell pm enable --user 0 com.google.android.apps.tv.launcherx
adb shell cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity
```

Optionally uninstall R10 Home afterward:

```cmd
adb uninstall com.nk.r10home
```

Do not disable `com.google.android.apps.tv.launcherx` during initial testing.
