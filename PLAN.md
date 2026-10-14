# Android Browser: Project Structure and Build Plan

Status: **built (steps 1–14 written; compiles locally). Waiting on GitHub setup and phone testing.**

### Changes made while building (2026-10-14)
- **Added:** ad/tracker blocking stats screen (uBlock Origin's per-page count plus
  Firefox tracking protection), with a per-page shield badge in the address bar.
- **Removed:** forced dark mode for websites (your request).
- **Minimum Android 10** (not 8). This lets downloads use the modern Downloads folder
  API with no storage permission.
- **uBlock Origin** is installed from addons.mozilla.org on first launch (not bundled),
  so it's always the latest version. If the phone is offline, it retries on the next launch.
- **Storage:** plain SQLite instead of Room (fewer build tools, same result).
- **Passwords:** the app doesn't register as an Android autofill *service*. Instead,
  GeckoView supports Android autofill, so Google Password Manager fills inside Driftwood,
  and imported passwords fill through Firefox's built-in login filling with the
  exact-domain check.
- **Updates:** every GitHub build is signed with one permanent key and published as a
  GitHub Release, so you update by installing the newest APK over the old one.
- **Versions:** GeckoView 158, Android Gradle Plugin 9.4.1, Kotlin 2.4.21, compiled against
  Android API 37.2.

---

## 1. How you will get the APK (no coding tools needed on your PC)

I will write all the code. You will not need Android Studio. The app is built on
GitHub's free servers (GitHub Actions), and you download the APK from the website.

What you need to do yourself (one time):

1. Create a free GitHub account at https://github.com (if you don't have one).
2. Create a new **private** repository named e.g. `android-browser`.
3. Upload the project to it. I will tell you the exact steps once the code exists
   (either by uploading a zip through the website or by using Git).
4. Go to the repo's **Actions** tab, click the build, and download the `app-debug.apk`
   file from the **Artifacts** section.
5. On your phone, open the APK and allow "Install unknown apps" for your browser
   or file manager. Android will warn you because the app isn't from the Play Store.
   That is expected.

Build type: I plan to produce a **debug-signed APK**. It installs and works the same
for personal use. Proper release signing (a keystore kept as a GitHub secret) can be
added later if you want it.

Size warning: GeckoView is Firefox's engine, so the APK will be large (roughly
30-60 MB).

---

## 2. Technology choices

| Area | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3 base, heavily customised) |
| Web engine | GeckoView (from Mozilla's Maven repository) |
| Extensions | Firefox WebExtensions via GeckoView's WebExtension API |
| Local data | Room (bookmarks, history, downloads, tabs) |
| Settings | Jetpack DataStore |
| Passwords | Android Keystore (AES-GCM), Room table, BiometricPrompt |
| Autofill | Android Autofill framework (`AutofillService`) |
| Font | Montserrat (TTF, OFL licence), bundled in `res/font` |
| Icons | Tabler Icons (MIT), converted to Android Vector Drawables |
| Build | Gradle (Kotlin DSL), GitHub Actions workflow |
| Min Android | Proposed: Android 8.0 (API 26). See questions. |
| Target | Latest stable Android API |

---

## 3. Project structure

```
android-browser/
├── PLAN.md                         (this file)
├── README.md                       (how to build and install, in plain steps)
├── .github/
│   └── workflows/
│       └── build-apk.yml           (GitHub builds the APK for you)
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   └── libs.versions.toml          (all library versions in one place)
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml     (permissions, autofill service, no-backup rules)
        ├── assets/
        │   └── extensions/
        │       └── ublock_origin.xpi   (bundled so it installs even offline)
        ├── res/
        │   ├── font/               (Montserrat .ttf files)
        │   ├── drawable/           (Tabler icons as Vector Drawables)
        │   ├── xml/
        │   │   ├── autofill_service_config.xml
        │   │   ├── backup_rules.xml        (excludes passwords from backup)
        │   │   └── data_extraction_rules.xml
        │   └── values/strings.xml
        └── java/com/example/browser/     (package name: placeholder, see questions)
            ├── MainActivity.kt         (entry point, sets up Compose)
            ├── BrowserApp.kt           (app-wide setup: GeckoRuntime, DI-style holder)
            ├── engine/
            │   ├── GeckoRuntimeHolder.kt   (one shared engine for the whole app)
            │   ├── SessionController.kt    (creates/closes/switches GeckoSessions)
            │   ├── DownloadHandler.kt      (saves downloads, records them)
            │   └── ExtensionManager.kt     (installs uBO on first run, lists extensions)
            ├── tabs/
            │   ├── TabState.kt         (data model: url, title, incognito flag)
            │   └── TabManager.kt       (list of tabs, active tab, incognito split)
            ├── ui/
            │   ├── theme/
            │   │   ├── Palettes.kt     (Gruvbox + Rosé Pine hex codes)
            │   │   ├── Theme.kt        (builds Material colours from a palette)
            │   │   └── Type.kt         (Montserrat text styles)
            │   ├── browser/
            │   │   ├── BrowserScreen.kt        (page + bars + gestures)
            │   │   ├── BottomAddressBar.kt     (hides on scroll down, shows on up)
            │   │   ├── TopAddressBar.kt        (optional top mode, hides on scroll up)
            │   │   └── ScrollBarController.kt  (decides bar visibility from scroll)
            │   ├── tabs/
            │   │   ├── TabSwitcherScreen.kt
            │   │   └── TabCard.kt
            │   ├── home/
            │   │   ├── HomeScreen.kt           (clean page, shortcut grid)
            │   │   └── ShortcutEditor.kt
            │   ├── bookmarks/BookmarksScreen.kt
            │   ├── history/HistoryScreen.kt
            │   ├── downloads/DownloadsScreen.kt
            │   ├── extensions/ExtensionsScreen.kt
            │   ├── passwords/
            │   │   ├── PasswordsScreen.kt      (list, locked behind biometrics)
            │   │   └── ImportPasswordsScreen.kt
            │   └── settings/
            │       ├── SettingsScreen.kt
            │       ├── ThemePickerScreen.kt
            │       ├── SearchEngineScreen.kt
            │       └── ImportBookmarksScreen.kt
            ├── data/
            │   ├── AppDatabase.kt          (Room database)
            │   ├── bookmarks/ (Bookmark.kt, BookmarkDao.kt)
            │   ├── history/   (HistoryEntry.kt, HistoryDao.kt)
            │   ├── downloads/ (DownloadEntry.kt, DownloadDao.kt)
            │   ├── shortcuts/ (Shortcut.kt, ShortcutDao.kt)
            │   ├── passwords/ (SavedPassword.kt, PasswordDao.kt, PasswordRepository.kt)
            │   └── settings/  (SettingsStore.kt, SettingsModel.kt)
            ├── security/
            │   ├── KeystoreCipher.kt       (AES-GCM key in Android Keystore)
            │   └── BiometricGate.kt        (fingerprint or PIN before viewing)
            ├── importer/
            │   ├── ChromeBookmarkParser.kt (reads Netscape bookmark HTML)
            │   └── ChromePasswordParser.kt (reads Chrome CSV, validates columns)
            ├── autofill/
            │   ├── BrowserAutofillService.kt   (answers Android autofill requests)
            │   └── DomainMatcher.kt            (exact host match only)
            └── search/
                └── SearchEngines.kt        (Google, DuckDuckGo, Bing URLs)
```

Notes on this layout:
- Each folder has one job, so a change to bookmarks never touches passwords.
- Files are small and named after what they do, which makes it easier for you to
  ask for changes later ("change the bottom bar", "edit the Gruvbox colours").

---

## 4. Feature-by-feature approach

### Browsing core
- One GeckoView engine for the whole app; each tab is a `GeckoSession`.
- Incognito tabs use a session in private mode, and their history, downloads list
  and cookies are kept out of the normal storage.
- Address bar sits at the bottom. It is driven by scroll direction from GeckoView's
  scroll events: hides when scrolling down, reappears when scrolling up.
- Optional top address bar: same logic, reversed (hides when scrolling up).
- Tab switcher: grid of tab cards with thumbnails and a close button.

### Homepage
- Plain screen: a search box and a grid of shortcut tiles.
- Shortcuts are stored in Room; you add, rename, reorder and delete them.

### Search
- Google is the default. Settings lets you pick Google, DuckDuckGo or Bing.
- Typing something that is not a URL sends it to the chosen engine.

### Ad blocking
- uBlock Origin is bundled in the app and installed on first launch, so it works
  even if the phone is offline at that moment.
- A later update check can pull newer versions from addons.mozilla.org.
- Extensions screen lists installed extensions, lets you enable or disable them, and
  lets you install a new one from an `.xpi` file or an addons.mozilla.org link.

### Bookmarks, history, downloads
- Bookmarks: add from the page menu, edit, delete, search.
- History: grouped by day, searchable, clearable. Incognito tabs never add entries.
- Downloads: GeckoView download requests are saved to the Downloads folder and
  recorded. Tapping an entry opens the file.

### Themes
- Palettes: Gruvbox Dark, Gruvbox Light, Rosé Pine, Rosé Pine Dawn.
- The hex codes will be copied from each theme's **official repository** (not from
  memory), then listed in `Palettes.kt` with a source comment so you can check them.
- "Follow system" option: you pick one light theme and one dark theme. The app uses
  whichever matches the phone's current mode.

### Chrome bookmark import
- Chrome exports bookmarks as an HTML file in a format called "Netscape Bookmark".
- The parser reads folders and links and imports them into your bookmarks, skipping
  duplicates.

### Passwords (the security-sensitive part)
- **Storage:** each password is encrypted with an AES-GCM key that lives in the
  Android Keystore. The key cannot be exported from the phone, and the encrypted
  data is useless without it.
- **Viewing:** the passwords list and the "show password" button require a fingerprint
  or your phone PIN/pattern (BiometricPrompt with device credential fallback).
- **Backups:** `android:allowBackup="false"` plus explicit exclusion rules, so the
  password table is not included in cloud or ADB backups.
- **Autofill:** the app registers as an Android autofill service. Google Password
  Manager is one of the providers that can fill from it. Android lets you choose
  which autofill service is active, so you can keep Google's as well.
- **Exact domain matching:** a saved login for `example.com` fills only on
  `example.com`. It does not fill on `login.example.com` or `example.com.evil.net`.
  This is the exact-host check in `DomainMatcher.kt`.
- **CSV import:** reads Chrome's export (columns: name, url, username, password, note),
  checks the header, encrypts each row, and then shows a reminder:
  "Delete your CSV file now. It is stored in plain text." The app will not keep a copy
  of the CSV.

Limitation to know: Android autofill only works in apps and browsers that support it
well. GeckoView supports Android autofill, but I will test it on your phone and tell
you what works. I cannot test on a real phone myself, so I will rely on your test
results for this part.

---

## 5. Build plan (in order)

Each step ends with something you can check.

| Step | What gets built | How you check it |
|---|---|---|
| **0. Your setup** | GitHub account, empty private repo | Repo page exists |
| **1. Skeleton** | Gradle project, empty Compose screen, GitHub build workflow | Actions build is green; you download and install a blank app |
| **2. Engine** | GeckoView loads a web page with a fixed address bar | You open a website and it displays |
| **3. Bottom bar + scroll hiding** | Address bar that hides on scroll down, shows on scroll up | Scroll a page: bar moves |
| **4. Top bar option** | Optional top address bar, reversed scroll logic | Toggle in settings works |
| **5. Tabs** | Tab switcher, new/close tab, incognito tabs | Open 3 tabs, one incognito, switch between them |
| **6. Storage** | Room database: bookmarks, history, downloads, shortcuts | Bookmark a page; it persists after restart |
| **7. Homepage + search** | Home screen with shortcuts, search engine choice | Search from the bar uses the chosen engine |
| **8. Settings + themes** | Settings screen, four palettes, system follow with light/dark choice | Switch themes; follow system mode |
| **9. Extensions** | uBO installs on first launch; extension manager | uBO shows in the list; ads reduced on a news site |
| **10. Design polish** | Montserrat font, Tabler icons everywhere | Visual check |
| **11. Bookmark import** | Chrome HTML import | Import your Chrome export; bookmarks appear |
| **12. Passwords** | Keystore encryption, biometric gate, autofill service, exact-domain matching, backup exclusion | Passwords hidden until unlock; Google Password Manager fills on the exact site |
| **13. Password import** | Chrome CSV import with delete reminder | Import CSV; reminder shown; entries appear masked |
| **14. Final check** | Full checklist walk-through, README written | You test the list in section 6 |

Steps 1-5 come first because each later step depends on them.

---

## 6. Test checklist I will hand you at the end

- [ ] Bottom bar hides on scroll down and returns on scroll up
- [ ] Top bar option works in reverse
- [ ] Incognito tab does not appear in history
- [ ] Bookmarks and history survive an app restart
- [ ] Downloads appear in the Downloads screen
- [ ] Google, DuckDuckGo, and Bing each search correctly
- [ ] Each of the four themes looks right; system-follow switches correctly
- [ ] uBlock Origin is listed as installed and active
- [ ] Chrome bookmark import works with your real export
- [ ] Passwords screen asks for fingerprint/PIN before showing anything
- [ ] Autofill fills only on the exact saved domain
- [ ] Exported password backup does not contain the password database
- [ ] CSV import shows the delete reminder

---

## 7. Things I cannot do for you, and things that need your OK

**Needs your permission (I will ask before each):**
- Downloading Montserrat (font files) from Google Fonts.
- Downloading Tabler Icons.
- Downloading the uBlock Origin `.xpi` file from addons.mozilla.org.
- Downloading the official Gruvbox and Rosé Pine palette files to copy hex codes from.

**Things only you can do:**
- Create the GitHub account and repository.
- Upload the code and run the build (I will give you the exact clicks).
- Install the APK on your phone and test on the real device.
- Export your Chrome bookmarks and passwords (I will explain how).
- Delete the Chrome CSV file after import.

**Limits I need you to know about:**
- I cannot compile or run the app on this PC, so the first real build result comes from
  GitHub. If the build fails, I will read the error log and fix it.
- Password autofill depends on Android and GeckoView support. I will test what I can
  and tell you the results.

---

## 8. Questions (defaults I will use if you don't answer)

1. **App name:** Driftwood (chosen). Icon source is in the `icon/` folder.
2. **Package name** (internal ID, like `com.yourname.browser`): default
   `com.example.browser`. It cannot be changed later without starting over, so
   tell me your preferred one now if you have one.
3. **Minimum Android version:** default Android 8.0. Older phones will not install it.
4. **Icon:** Driftwood icon (a log over two waves on sand). Files are in `icon/`. Adjust colours or shapes there.

---

## 9. Next step

Once you say "go" (or answer the questions above), I will:
1. Create the Gradle project and GitHub build workflow (step 1).
2. Write the README with the exact upload and download steps.
3. Continue through the build plan, checking in only where a choice is yours.
