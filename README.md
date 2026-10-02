# AI Camera App

Android camera app with on-device auto photo enhancement and an optional
cloud "AI Edit" step that you power with your own API key (Google Gemini,
OpenAI, DeepSeek, or any custom HTTP endpoint).

## Features
- Full camera controls: capture, pinch-to-zoom, flash modes, HDR toggle,
  grid overlay, self-timer, front/back camera switch.
- Automatic on-device enhancement applied to every shot (contrast, sharpening,
  saturation auto-levels) - fast, free, works offline.
- Optional "AI Edit" button on the preview screen that sends the photo to the
  AI provider configured in Settings for a stronger, cloud-grade enhancement.
- Settings screen to pick a provider (Gemini / OpenAI / DeepSeek / Custom),
  paste your API key, set a custom endpoint URL, and toggle a free-tier mode.

## Build
Open in Android Studio (Koala+) or build from the command line:

```
gradle assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.
A GitHub Actions workflow (`.github/workflows/build-apk.yml`) builds this
automatically on every push and uploads the APK as a workflow artifact and
as a GitHub Release asset on the `main` branch.

## Setting up your AI key
1. Open the app -> tap the gear icon -> Settings.
2. Choose a provider:
   - **Gemini** - paste a Google AI Studio API key (free tier available).
   - **OpenAI** - paste an OpenAI API key (images edit endpoint).
   - **DeepSeek** - paste a DeepSeek API key (used for AI-guided enhancement
     suggestions; DeepSeek does not currently offer an image-edit endpoint).
   - **Custom** - enter any HTTP endpoint that accepts a base64 image + prompt
     and returns an edited image, e.g. your own server or another provider.
3. Enable "Use AI Edit over Wi-Fi/mobile data" and save.
4. On the photo preview screen, tap **AI Edit** to send the shot to your
   configured provider. Tap **Auto Enhance** for the free, instant, on-device
   version instead.

No API key is bundled with the app. All cloud AI usage is billed by the
provider to your own key.
