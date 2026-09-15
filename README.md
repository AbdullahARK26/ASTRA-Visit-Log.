# ASTRA Visit Log — Android fixed build

Fixes:
- Native Android save bridge for JSON backup and Excel export.
- Native Android file picker for JSON restore and Excel import.
- WebView Chrome/file chooser support.
- ASTRA naming in exports/backups.
- Keeps existing local-storage key so existing ASTRA data can migrate.
- Removes the old startup purge that could remove bundled clients.
- Ensures bundled clients are reseeded if the database is empty.

Build through GitHub Actions using `.github/workflows/build-apk.yml`.


## Version 2.1 native file I/O
Backup/restore and Excel import/export use the Android native file bridge when running in the APK. This avoids browser blob-download/file-picker limitations and does not require ExcelJS for APK Excel operations.


## Version 2.1 route-opening fix
- Saved Visit “Open route” now uses the native Android ACTION_VIEW handler.
- Google Maps/browser opens outside the WebView instead of relying on a WebView popup/tab.
- Existing saved route URLs and local visit data are unchanged.
- Also includes the required ByteArrayOutputStream import for clean Java compilation.
