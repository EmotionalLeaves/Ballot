# Release signing key

`ballot-release.jks` signs every release APK (alias `ballot`, password `ballot-public`).
It is committed on purpose, by the owner's choice (DECISIONS.md, 2026-11-15): one fixed key means each
release installs over the last one and keeps the data on the phone.

Never replace or regenerate it. A different key makes Android refuse the update, and the only way back is
export, uninstall, reinstall, import. `FINGERPRINT` holds its SHA-256, and release.yml fails if they differ.

Because the repo is public, anyone can sign an APK with this key. Only install Ballot from this repo's
Releases page.
