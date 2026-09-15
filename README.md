# ASTRA Visit Log — Android APK

Offline-first Android wrapper for the ASTRA Visit Log web app.

## Build free with GitHub Actions
1. Create a public GitHub repository named `ASTRA-Visit-Log` under your account.
2. Upload all files/folders from this project, preserving `.github/workflows/build-apk.yml`.
3. Open **Actions → Build ASTRA APK → Run workflow**.
4. After completion, open the workflow run and download the artifact **ASTRA-Visit-Log-APK**.
5. Extract the ZIP and install `app-debug.apk` on Android.

No domain is required. The APK runs the app locally and stores its data on the device. Internet is only useful for external services already used by the web app (for example map/geocoding features).
