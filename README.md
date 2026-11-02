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
- **Prompt on login (optional):** when XIVLauncher asks for a code, your watch buzzes and
  offers to send it.

XIVLauncher only listens for codes (on TCP port 4646) while its 2FA dialog is open. The app
sends `GET /ffxivlauncher/<code>` to that port.

## Setup

1. In XIVLauncher settings, turn on the XL Authenticator / OTP macro support option.
2. Allow XIVLauncher through the Windows firewall on your private network (port 4646).
3. Install the phone app on your phone and the watch app on your watch.
4. In the phone app, paste your 2FA key (the one Square Enix shows when you set up the
   software authenticator, or an `otpauth://` link) and enter your PC's IP address.
   You can enter several IPs separated by `;`.

## Prompt on login

This setting is off by default. When on, the phone checks whether XIVLauncher's port is
open by connecting and hanging up, which XIVLauncher ignores. To keep battery use low:

- **No background service:** there is no foreground service, wake lock or permanent
  notification. Each check is a single alarm and a sub-second connection attempt.
- **Home network only:** checks only run while the phone is on the same subnet as your PC.
  Away from home nothing runs; the app is woken only when Wi-Fi changes.
- **Adaptive checking:** every 5 s while your PC is on, backing off to every 3 min while it
  isn't answering.
- **Pauses in deep sleep:** when Android puts an idle phone into deep sleep (Doze), checks
  pause. Tapping Send on the watch always works.

The settings screen shows check counts and time spent checking, to help measure battery use.

## Building

```
./gradlew :phone:testDebugUnitTest :phone:assembleDebug :wear:assembleDebug
```

CI builds both debug APKs on every push.

## License

GPL-3.0, same as the original. See [LICENSE.md](LICENSE.md).
