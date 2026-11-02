# XL Authenticator (phone + Wear OS)

Send your Square Enix 2FA code to [XIVLauncher](https://github.com/goatcorp/FFXIVQuickLauncher)
with one tap, from your phone or your Wear OS watch.

A native Kotlin rewrite of [goaaats/xl-authenticator](https://github.com/goaaats/xl-authenticator),
the original Flutter app. Thanks to goaaats for the idea and the protocol.

## How it works

- **Phone app:** a 2FA token app. It holds your key and your PC's IP address, shows the
  current code, and sends it to XIVLauncher over your home network.
- **Watch app:** a remote button. It stores nothing. Tapping Send in the app, the tile or a
  watch-face complication asks the phone to send the code, then shows ✓ or ✗.
- **Prompt on login (optional):** when XIVLauncher asks for a code, your phone and watch show
  an "XIVLauncher is waiting" notification with a Send button.

XIVLauncher only listens for codes (on TCP port 4646) while its 2FA dialog is open. The phone
sends `GET /ffxivlauncher/<code>` to that port. Your key never leaves the phone.

## Install

1. Download both APKs from the latest [release](../../releases/latest):
   `ripps-xl-authenticator-phone-<version>.apk` and `ripps-xl-authenticator-wear-<version>.apk`.
   They must come from the **same release**: Wear OS only lets the phone and watch apps talk
   when both are signed with the same key.
2. Install the phone APK: open it on the phone and allow installing from that app when asked.
3. Install the watch APK over wireless debugging:
   ```
   adb -s <watch-ip:port> install ripps-xl-authenticator-wear-<version>.apk
   ```
   If your watch shows up twice in `adb devices`, `-s` picks the right one.

New releases install over the old ones and keep your settings. If Android says "app not
installed", the app on the device was signed with a different key (for example a build from
another source). Uninstall it once and install again.

## Updating

Update the phone app by installing the new APK over the old one; your settings stay. The watch
app is a thin remote and rarely changes. Each release's notes say whether the watch app changed,
so you only need the `adb` step when they say so. The phone app keeps working with older watch
apps. If the watch ever says "Update the watch app", install the wear APK from the latest release.

## Set up

1. In XIVLauncher settings, turn on the XL Authenticator / OTP macro support option.
2. Allow XIVLauncher through your PC's firewall on your private network (port 4646).
3. Open the phone app, paste your 2FA key (the one Square Enix shows when you set up the
   software authenticator, or an `otpauth://` link) and enter your PC's IP address.
   You can enter several IPs separated by `;`.
4. Allow **local network access** when the phone asks. Android 17 blocks apps from reaching
   your PC without it.
5. On the watch, open the app once and allow notifications. Add the **Send 2FA** tile and the
   **Send 2FA code** complication if you want one-tap access.

## Prompt on login

This setting is off by default. When on, the phone checks whether XIVLauncher's port is
open by connecting and hanging up, which XIVLauncher ignores. It asks for notification
permission, since the prompt is a notification.

To keep battery use low:

- **No background service:** there is no foreground service, wake lock or permanent
  notification. Each check is a single alarm and a sub-second connection attempt.
- **Home network only:** checks only run while the phone is on the same subnet as your PC.
  Away from home nothing runs; the app is woken only when Wi-Fi changes.
- **Adaptive checking:** every 5 s while your PC is on, backing off to every 3 min while it
  isn't answering.
- **Pauses in deep sleep:** when Android puts an idle phone into deep sleep (Doze), checks
  pause. Tapping Send on the watch always works.

The settings screen shows check counts and time spent checking, to help measure battery use.
Allowing "Alarms & reminders" lets the checks run on time.

## Troubleshooting

- **"Couldn't reach the PC":** check the IP, that the XIVLauncher setting above is on, and that
  the firewall allows port 4646. The PC must be on the same network as the phone. A VPN on the
  PC must allow local network connections.
- **"Android blocked the connection":** grant local network access to the phone app in
  Settings → Apps → XL Authenticator Phone → Permissions.
- **"The PC is on, but XIVLauncher isn't asking for a code":** open the launcher's 2FA prompt
  first. The port is only open while it is showing.
- **Watch says the phone isn't connected:** the two apps are signed with different keys, or
  the watch isn't connected to the phone. Install both from the same release.

## Building

```
./gradlew :phone:testDebugUnitTest :phone:assembleDebug :wear:assembleDebug
```

CI builds both debug APKs on every push and runs the tests.

### Signing

Wear OS needs the phone and watch apps signed with the same key, so CI signs with a fixed key
supplied through repository secrets. To use your own fork:

```
keytool -genkeypair -keystore xlauth.jks -alias xlauth -keyalg RSA -keysize 2048 \
  -validity 36500 -dname "CN=XL Authenticator"
base64 -w0 xlauth.jks | gh secret set XLAUTH_KEYSTORE_BASE64
gh secret set XLAUTH_KEYSTORE_PASSWORD
```

Keep `xlauth.jks` out of the repository (`.gitignore` already blocks key files, and CI
refuses to build if one is tracked). Use the same password for the store and the key. To build
signed locally, set `XLAUTH_KEYSTORE` to the key file's path and `XLAUTH_KEYSTORE_PASSWORD`.
Without them, builds fall back to the local debug key.

### Releasing

Push a tag to build signed, shrunk release APKs and publish them as a GitHub release:

```
git tag v1.0.1 && git push origin v1.0.1
```

The tag sets the version name and the commit count sets the version code, so every build
(debug or release) is newer than the last. The release workflow refuses to run without the signing secrets.

## License

GPL-3.0, same as the original. See [LICENSE.md](LICENSE.md).
