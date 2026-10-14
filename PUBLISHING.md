# Publishing and updates

This project lives at https://github.com/phsrp/Driftwood-browser. Releases are under
[Releases](https://github.com/phsrp/Driftwood-browser/releases/latest).

How it works: each time a version tag (like `v1.0.1`) is pushed, GitHub builds the APK by itself
(about 5 minutes) and publishes it as a **Release** with the file `Driftwood-1.0.1.apk`.

---

## Sending an update

**The easy way:** tell Claude what you want changed and then say "push an update". Claude raises the version
number, writes the release notes, commits, pushes, tags it, and checks that GitHub's build finishes and the APK appears.

**By hand:**
1. Make the change. In `app/build.gradle.kts` raise `val appVersion`, for example from `1.0.0` to `1.0.1`.
2. Rewrite `RELEASE_NOTES.md` (short bullet points). It becomes the text of the release.
3. Commit and push to `main`.
4. Create the tag `v1.0.1` (the letter `v` plus the exact version) and push it.
5. On github.com open the **Actions** tab and wait for the green tick, then check **Releases**.

---

## Signing key

Android only installs an update over the existing app if both are signed with the **same key**. The key is in the
`signing/` folder on the computer it was made on (`driftwood.jks` + `keystore.properties`). That folder is
**never uploaded** (it's in `.gitignore`). GitHub gets it through four repository secrets
(repo → Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `DRIFTWOOD_KEYSTORE_BASE64` | `signing/driftwood.jks`, base64-encoded |
| `DRIFTWOOD_STORE_PASSWORD` | `storePassword` from `signing/keystore.properties` |
| `DRIFTWOOD_KEY_ALIAS` | `keyAlias` from `signing/keystore.properties` |
| `DRIFTWOOD_KEY_PASSWORD` | `keyPassword` from `signing/keystore.properties` |

> **Keep a backup of the `signing/` folder** somewhere safe and private. If it's lost, new versions can't update
> the installed app: you'd have to uninstall it and lose saved data.

---

## Good to know

- **Version numbers must go up**, and the tag must match `appVersion` (tag `v1.0.1` means version `1.0.1`).
  The build stops with an error if they don't match.
- **Old versions stay** on the Releases page, so you can always download an earlier one.
- **Build on your own PC instead** (no GitHub): the build tools are in the hidden `.tools/` folder (never uploaded).
  In Git Bash, from the project folder: `. .tools/env.sh && .tools/gradle-9.8.1/bin/gradle :app:assembleRelease`.
  The APK appears in `app/build/outputs/apk/release/`.
- **The README banner** is made by `scripts/make-banner.js` (see the comment at its top).
- **Icons** are generated from the Tabler SVGs in `third_party/tabler-icons/` by `third_party/gen-tabler-icons.js`.
