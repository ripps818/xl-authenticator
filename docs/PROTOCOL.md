# Phone ↔ watch protocol

The watch app is a thin remote and is hard to update (no Play Store), so the protocol is built so
that the phone app can change freely without a watch update. The watch knows nothing about
XIVLauncher, its port, or what a code is: it asks the phone to "send", and shows what the phone says.

## Messages (Wear Data Layer `MessageClient`)

The watch finds the phone through the capability `xl_phone`, which the phone app declares in
`phone/src/main/res/values/wear.xml`. Never rename it.

| Path | From | Payload |
| --- | --- | --- |
| `/xl/send` | watch | The watch's protocol version as text, currently `1`. |
| `/xl/result` | phone | `{"ok": bool, "message": text}`. The watch shows `message` and uses `ok` for the ✓/✗ and haptic. |
| `/xl/prompt` | phone | `{"title", "text", "action", "timeout_ms"}`, used for the "XIVLauncher is waiting" notification. The watch falls back to built-in text if a field is missing. |

## Rules for changing it

- **Phone-only changes need no watch update.** Add new outcomes, reword messages, change how or
  where the code is sent, or support a different launcher entirely, all in the phone app. A new
  outcome is just a new `message` with `ok` true or false.
- **Never remove, rename or repurpose a path or a field.** Add new ones instead.
- **Each side ignores fields it doesn't know.** The watch shows an unrecognised reply as a failure
  with the phone's message, never as "no answer".
- **A breaking change is the last resort.** The protocol is version 1. If one is ever needed, bump
  the watch's `PROTOCOL_VERSION`, keep the phone answering older watches in the format they
  understand, and raise `MIN_WATCH_PROTOCOL` in the phone's `WatchLink` only when an old format
  can no longer be kept. A watch below the minimum gets an "Update the watch app" message.

## What does need a watch update

The tile and complication labels, the watch screens' layout, and the version number the watch
sends. Everything the user reads after pressing Send comes from the phone.
