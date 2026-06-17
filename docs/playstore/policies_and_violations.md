# FluxLinux — Google Play Store Policies & Violations Reference

> **Scope.** This document is the canonical reference for everything in this
> repo that may collide with the Google Play Developer Program Policies. It
> covers the four classes of issues called out in the original report
> (in-app APK installation, download links for other apps, external links,
> embedded YouTube playback) plus every other common rejection category that
> is plausibly relevant to FluxLinux.
>
> **Sources.** Citations are inline. Policy text is paraphrased from the
> official Google Play Console Help pages and Play Console developer
> documentation. Where a third-party summary is more precise than the
> official page (the official pages are very high-level), both are linked.
>
> **Status legend (per finding).**
> - `present` — code/asset in this repo triggers the issue.
> - `risk` — code/asset is acceptable today but on the policy edge; will
>   need a declaration or change before a future Play policy tightening.
> - `clean` — no occurrence found.
> - `unknown` — needs manual review (cannot be statically detected).

---

## 1. TL;DR for FluxLinux

> **Note.** The table below is the v1 inventory against `main`. After the
> v2 playstore-branch rebase (see §7), the §2.x issues
> (`ApkDownloader.kt`, in-app APK download + install, XDA `*.zip`
> attachment link, `REQUEST_INSTALL_PACKAGES`, the false
> AccessibilityService claim, the sourceless Termux/Termux:X11
> requirements line) are **`clean`** on the playstore branch. The
> foreground-service rows remain `present` by design (declaration form
> is filled in Play Console).

| # | Issue | Severity | Status on `main` | Status on `playstore` |
|---|-------|----------|------------------|-----------------------|
| 1 | In-app APK installation of other apps (Termux, Termux:X11) | **Critical** | `present` | `clean` |
| 2 | Direct download links to other apps' APKs | **Critical** | `present` | `clean` |
| 3 | External links opened via `Intent.ACTION_VIEW` to non-Play pages | High | `present` | `present` (developer-profile / docs only — safe) |
| 4 | Embedded YouTube playback inside the app | Medium | `clean` | `clean` |
| 5 | `REQUEST_INSTALL_PACKAGES` permission without proper declaration | **Critical** | `present` | `clean` |
| 6 | Foreground service type `dataSync` on API 34+ | High | `present` | `present` (kept on purpose; declaration form required) |
| 7 | "AccessibilityService" claim in store description but not implemented | Medium | `present` | `clean` |
| 8 | WebView / browser redirect chain via `fluxlinux://` deep link | Low | `present` (informational) | `present` (informational) |
| 9 | Linking to F-Droid from Play-listed assets | Medium | `present` (README + fastlane metadata) | `present` (README — acceptable per §2.3) |
| 10 | Targeting/SDK 34 foreground service policy | High | `present` | `present` |

A new policy-by-policy matrix is in **§4** and a per-file remediation list
is in **§5**.

---

## 2. The Four Flagged Issues (Detailed)

### 2.1 Apps must not install other apps (most problematic)

**Policy.** Apps that download and install other Android packages from
outside the Play Store violate multiple Play policies:

- **Malware policy / Mobile Unwanted Software** — an app whose primary
  effect is to install other apps is classified as a "Hostile Downloader"
  and is not permitted on Play.
- **"Install other apps" eligibility for `REQUEST_INSTALL_PACKAGES`** —
  Google restricts this permission to a narrow set of core use cases:
  web browsers, file managers, communication apps handling file
  attachments, enterprise MDM, and device migration/backup. An app that
  *is itself* a Linux distribution manager is **not** in that list, and
  using the permission to install Termux + Termux:X11 is treated as
  sideloading other apps.
- **"Deceptive Behavior" / Behavior Transparency** — the app would be
  loading an APK from a third-party (GitHub releases of termux-app,
  termux-x11) and triggering the system installer, which is exactly the
  pattern reviewers flag as hidden/undocumented behavior.

> **Sources.**
> - <https://support.google.com/googleplay/android-developer/answer/9888077?hl=en> (Deceptive Behavior, Behavior Transparency)
> - `REQUEST_INSTALL_PACKAGES` policy declaration guidance (Play Console → Policy → App content → Permissions)

**Where it lives in this repo.**

- `app/src/main/AndroidManifest.xml:9` — declares
  `android.permission.REQUEST_INSTALL_PACKAGES`.
- `app/src/main/kotlin/com/ivarna/fluxlinux/core/utils/ApkDownloader.kt`
  - `getInstallIntent(...)` (lines 63-72) — builds an `Intent.ACTION_VIEW`
    with `application/vnd.android.package-archive` and launches the
    system installer via `FileProvider`.
  - `canInstallPackages(...)` (lines 74-80) — probes
    `packageManager.canRequestPackageInstalls()`.
  - `openInstallPermissionSettings(...)` (lines 82-90) — sends the user
    to **Settings → Install unknown apps** for our package, asking the
    user to grant *us* the right to install APKs.
- `app/src/main/kotlin/com/ivarna/fluxlinux/ui/screens/PrerequisitesScreen.kt:387-446`
  — `downloadTermux()` and `downloadX11()` HTTP-GET the APK, and
  `installApk(...)` hands it to the system installer.

**Why this is a guaranteed rejection.** Per the policy, the only accepted
use cases for `REQUEST_INSTALL_PACKAGES` are listed above, and "install a
*different* app from the Play Store" is not on the list. The Play review
team will reject on first review and will not grant an appeal.

**Remediation paths (any one is acceptable to Play).**

1. **Drop the sideload path entirely.** Open Termux's Play Store
   listing in the system browser (no download, no install) and let the
   user install it through Play. This is the only fully policy-clean
   approach. See §2.2 for the deep-link style.
2. **Declare the permission and add a Play-Store-style disclosure.**
   The `REQUEST_INSTALL_PACKAGES` Permissions Declaration form in Play
   Console asks for a *justified* use case; the answer for a Linux
   container app is "I am a file manager / package manager" which Play
   will likely reject on its own. (i.e. this path usually fails.)
3. **Build the prerequisite install into a single non-APK flow.** If
   Termux could be acquired via the Play Store and Termux:X11 via F-Droid
   (the only legitimate distribution channels for those apps), then
   FluxLinux does not need the permission at all. F-Droid links are
   addressable via a normal `https://` URL Play accepts.

The cleanest fix is **(1) + (3)**: deep-link to Termux on Play Store and
Termux:X11 on F-Droid, with no `REQUEST_INSTALL_PACKAGES` in the
manifest, no `ApkDownloader` at all, and no GitHub-released APK downloads
of other apps.

---

### 2.2 Apps must not link directly to download links for other apps

**Policy.** Even if you do not install the APK yourself, surfacing a
*download link* to another app's APK is treated the same as installing it
under the "Hostile Downloader" / "sideloading" line of policies. Play
expects all inter-app promotion to go through Play Store links
(`https://play.google.com/store/apps/details?id=...`).

External download links (e.g. direct `.apk` URLs on `github.com`,
`f-droid.org`, or anywhere else) are *not* a sanctioned distribution
mechanism for other apps from inside a Play-listed app.

> **Sources.**
> - <https://play.google.com/about/developer-content-policy/> (Developer
>   Program Policies — Inter-app / Sideloading)
> - <https://support.google.com/googleplay/android-developer/answer/9888077?hl=en>

**Where it lives in this repo.**

- `app/src/main/kotlin/com/ivarna/fluxlinux/ui/screens/PrerequisitesScreen.kt:393`
  — hard-coded direct APK URL for Termux.
- `app/src/main/kotlin/com/ivarna/fluxlinux/ui/screens/PrerequisitesScreen.kt:418`
  — hard-coded direct APK URL for Termux:X11 nightly.
- `README.md:218-220` — `Install Termux from F-Droid` and
  `Install Termux:X11` are linked as download steps in the user-facing
  README (not in the Play listing, but the same F-Droid/GitHub
  promotion).
- `fastlane/metadata/android/en-US/full_description.txt:10` — says
  "Requirements: Termux, Termux:X11" without supplying the install
  source. Reviewers will check the linked assets and the in-app
  flow.

**Remediation.**

- Replace both GitHub-URL APK downloads with deep links to the apps'
  Play Store listings (Termux is on Play; Termux:X11 is not, so for
  Termux:X11 a `https://f-droid.org/packages/com.termux.x11/` link is
  acceptable because Play allows `https` links to F-Droid — the policy
  only blocks *APK* downloads and *sideloading*).
- Remove the `downloadTermux()` / `downloadX11()` functions in
  `PrerequisitesScreen.kt` and the `ApkDownloader` utility.
- Update the store description so the requirement text is followed by
  the Play/F-Droid link, not a "Download from GitHub" flow.

---

### 2.3 External links to other pages are risky

**Policy.** Google Play does not ban *all* external links. What is
restricted is:

- Direct download links to other APKs (covered in §2.2).
- Links that circumvent Play's billing/payment for in-app purchases
  (Play Billing policy).
- Links that lead to deceptive or harmful content (Deceptive Behavior
  policy).
- Links presented as the *primary* value of the app when the actual
  functionality could live on Play.

What is *allowed*:

- Linking to the developer's own site, GitHub, Discord, support pages.
- Linking to other apps' **Play Store** pages.
- Linking to F-Droid and other open-source distribution for the *user's
  own convenience* (no monetization, no bypass of Play billing, no
  "click here to get the ad-free version" pattern).

The risk is therefore not "having external links" — it's the *category*
and *framing* of the link. Several `Intent.ACTION_VIEW` calls in this
repo will trigger manual review if framed as the install/update path.

**Where it lives in this repo.**

- `app/src/main/kotlin/com/ivarna/fluxlinux/ui/screens/SettingsScreen.kt:957-963`
  — `openUrl()` helper used to open external URLs.
- `app/src/main/kotlin/com/ivarna/fluxlinux/ui/screens/SettingsScreen.kt`
  - L753 — `https://github.com/abhay-byte/fluxlinux#readme`
  - L789 — `https://discord.gg/tag9kXAs2x`
  - L864 — `https://github.com/abhay-byte`
  - L899-903 — `SocialLink` list: GitHub, LinkedIn, Portfolio,
    Instagram, X (Twitter). All `openUrl()` calls.
  - L923 — `https://github.com/abhay-byte/FluxLinux`
- `app/src/main/kotlin/com/ivarna/fluxlinux/ui/screens/PrerequisitesScreen.kt:2109`
  — `https://xdaforums.com/attachments/update-busybox-installer-v1-36-1-all-signed-zip.6000117/`
  (a direct link to a `.zip` file on XDA; this is a downloadable asset
  from inside the app, see §3.4).
- `app/src/main/kotlin/com/ivarna/fluxlinux/ui/screens/PrerequisitesScreen.kt:2224-2293`
  — opens the in-repo tutorial, PRoot guide, Chroot guide, and Discord.
- `app/src/main/kotlin/com/ivarna/fluxlinux/ui/screens/PrerequisitesScreen.kt:1181`
  — opens `https://support.google.com/android/answer/12623953?hl=en`
  (Google's own help page — safe, but illustrative of the pattern).
- `README.md:9-11` — Play Store badge image + F-Droid badge image.

**Verdict for this repo.**

- **Safe** — the developer-profile links (GitHub, LinkedIn, Discord,
  Portfolio) and the in-repo docs links. They are *user-initiated*
  opens from a "Settings" screen, not the install/update path.
- **Risky** — the XDA `.zip` link in `PrerequisitesScreen.kt:2109`. A
  direct link to a downloadable file is treated similarly to a direct
  APK link by some reviewers. Change this to a `https://xdaforums.com/...`
  *thread* link (not the attachment URL), or remove it.
- **Safe but needs review** — the Play Store badge image in the README
  is in the GitHub README, not the Play store listing. The fastlane
  metadata (`fastlane/metadata/android/en-US/`) is what Play actually
  shows. Recheck that no Play-facing asset has the F-Droid badge as a
  primary CTA.

**Remediation.**

- Audit every `openUrl(context, ...)` call site and confirm the
  destination is either a Play Store link or a general web page (no
  `.apk`/`.zip` extension, no auto-download on click).
- For the XDA link, change the URL from the *attachment* URL to the
  *thread* URL.

---

### 2.4 Apps must not embed YouTube videos playing inside the app

**Policy.** Two layers apply:

1. **YouTube API Services Terms of Service** (developer-facing)
   - Videos must stop when the app is backgrounded.
   - The native player UI must not be obscured, framed, or overlaid.
   - You may not place your own ads (e.g. AdMob) on a screen that is
     primarily serving a YouTube video.
   - You may not force a "subscribe", "download another app", or
     similar gate before playback.
   - You must not market a YouTube player as "ad-free".
2. **Google Play Device and Network Abuse**
   - Background audio playback of YouTube content not obtained through
     the official YouTube APIs is a frequent rejection reason.
   - A "YouTube player" app that re-encodes or re-streams YouTube
     content is treated as deceptive.

> **Sources.**
> - <https://developers.google.com/youtube/terms/api-services-terms-of-service>
> - Play Console Help → Device and Network Abuse policy

**Where it lives in this repo.**

- **No in-app YouTube player is currently implemented.** The grep for
  `YouTube|youtube|player|video|exo|mediaplayer|webview|loadUrl|WebView`
  inside `app/src/main/kotlin` returns *only* a `video_editing` script
  reference in `DistroRepository.kt` and `ComponentData.kt` (a Debian
  package install for video editing tools, not a YouTube player).
- `README.md:75` does include a markdown YouTube embed thumbnail
  (`markdown-videos-api.jorgenkh.no/youtube/BXRzlJnaiLU`). This is
  GitHub-README-only and is not in the Play store listing.

**Status: `clean` for the in-app surface.** A markdown embed in the
GitHub README is fine — Play does not render it. The risk is only if a
future feature (e.g. "Watch tutorial" button inside the app) uses a
WebView with a YouTube URL. The mitigation in that case is to open the
URL in the system browser via `Intent.ACTION_VIEW` and *not* embed the
player in a WebView. (i.e. the current "open URL in browser" pattern
the app already uses for other links is the right shape.)

**Do not (future).**

- Do not add a `WebView` that loads `youtube.com` or `youtube.com/embed/...`.
- Do not background-play YouTube audio.
- Do not place an AdMob banner over a YouTube surface.
- Do not require a "subscribe" click before allowing video playback.

---

## 3. Comprehensive Google Play Policy Reference (All Categories)

These are the policies a Linux-on-Android app is most likely to be
reviewed against. Each row links the policy text and notes how it
applies to FluxLinux.

### 3.1 Permissions & Restricted Permissions

| Permission / Policy | What Play says | FluxLinux status |
|---|---|---|
| `REQUEST_INSTALL_PACKAGES` | Allowed only for browsers, file managers, comms apps handling attachments, enterprise MDM, device migration/backup. | **Used (line 9 of `AndroidManifest.xml`).** Not in allowed list. Must be removed. |
| `BIND_ACCESSIBILITY_SERVICE` | Must be a true accessibility tool *or* carry an in-app disclosure + Play Console declaration. | Not declared in manifest. But the store description claims "Uses AccessibilityService API" — see §4. |
| `QUERY_ALL_PACKAGES` | Disallowed unless core to app (e.g. launcher, file manager). | Not declared. `clean`. |
| `SYSTEM_ALERT_WINDOW` | Disallowed unless a documented use case (screen overlay, screen share). | Not declared. `clean`. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | For API 34+ the foreground service type must be declared in the manifest, in the `startForeground()` call, and on the Play Console. | **Used (manifest lines 12-13, service on line 58-61).** Need declaration form filled. |
| `POST_NOTIFICATIONS` | Needs runtime request on API 33+. | Used. Will need to actually request at runtime before posting. |
| `com.termux.permission.RUN_COMMAND` | Custom permission; not a Play policy issue, but the Play listing must clearly explain the user must also have Termux installed. | Used. Listing must mention. |

### 3.2 Deceptive Behavior

Covers: hidden/undocumented features, manipulated media, behavior
transparency, impersonation, misleading claims.

- **Applies to FluxLinux.** The Termux/Termux:X11 sideload path is a
  "hidden" install feature from a reviewer's perspective because the
  store description does not mention it.
- **Store description claim.** "Uses AccessibilityService API for
  system navigation actions (Back/Home) from the UI." This is in
  `fastlane/metadata/android/en-US/full_description.txt:11`. Grepping
  the Kotlin code for `AccessibilityService` returns no matches, and
  the manifest does not declare a service. If the app does not actually
  use the API, **this is a deceptive claim and a rejection trigger**.
  Either implement and declare the service, or remove the line from
  the listing.

### 3.3 User Data & Privacy

- **Privacy policy URL is mandatory** for apps that request certain
  permissions or handle user data. The Play Console "App content" page
  requires it. `docs/playstore/` is a good place to host one; this
  directory already exists but is currently empty.
- **Data safety form** must be filled in. FluxLinux downloads APKs to
  the app cache and writes install state. The "data collected" answer
  should be: *no personal data; app does not transmit data off-device*
  (the SHA-256 verification is local; the APK is HTTPS to GitHub but
  contains no personal info).

### 3.4 Hostile Downloaders / Sideloading / "Install other apps"

Covered in §2.1. A Linux-distro manager is not in the allowed list for
`REQUEST_INSTALL_PACKAGES`.

### 3.5 Device and Network Abuse

- No use of `WebView.loadUrl("youtube.com/...")` (covered in §2.4).
- No use of `WebView` to load untrusted URLs without a safe-browsing
  check.
- No SMS / call-log / contact-list abuse (none of those permissions
  are declared — `clean`).

### 3.6 Spam & Minimum Functionality

- The app must provide a "minimum degree of functionality and value".
  FluxLinux clearly does. `clean` on this axis.
- "Spam" covers duplicate submissions, misleading metadata, etc. The
  app has one Play listing. `clean`.

### 3.7 Metadata — Store Listing

- Title, short description, full description, icon, screenshots, feature
  graphic must all be present and consistent.
- The current `short_description.txt` is one line — good.
- The current `full_description.txt` claims "Uses AccessibilityService"
  (false claim — see §3.2) and lists "Termux, Termux:X11" as
  requirements without sourcing. Add Play Store links to the
  requirements.
- Screenshots: there are 7 phone screenshots in
  `fastlane/metadata/android/en-US/images/phoneScreenshots/`. They
  should not include any "downloaded APK file" or "Sideloading"
  imagery.

### 3.8 Impersonation

- App name "FluxLinux" and icon are not impersonating another product.
  `clean`.
- Make sure the developer name on Play Console matches the brand.
  README credits "Abhay Raj" / `abhay-byte` — keep the Play Console
  developer name consistent.

### 3.9 Payments & Subscriptions

- FluxLinux has no in-app purchases. Play Billing policy is not
  triggered. `clean`.
- The current fastlane metadata does not mention any monetization. `clean`.

### 3.10 Family Policy

- App is not marked as "Family" or "Designed for Families". `clean`.

### 3.11 Privacy & Data Safety

- Play Console requires a "Data safety" form. FluxLinux:
  - Collects no personal data.
  - Downloads APKs from `github.com` (Termux, Termux:X11) and stores
    them in app cache.
  - Opens user-chosen `Intent.ACTION_VIEW` URLs.
  - The "data shared" answer should be: *no data shared with third
    parties*.

### 3.12 Store Listing Experiments / Promotional Content

- Currently no in-app experiments. `clean`.

### 3.13 Restricted Content (Child endangerment, financial services,
gambling, illegal activities, user-generated content, health, blockchain,
AI-generated content, age-restricted content)

- FluxLinux is none of these. `clean`.

### 3.14 Background Execution & Foreground Services

- API 34 (Android 14) introduced strict foreground service type
  requirements. The manifest currently declares
  `FOREGROUND_SERVICE_DATA_SYNC` and the service sets
  `android:foregroundServiceType="dataSync"`. This is allowed, but
  the Play Console "Foreground service permissions declaration" form
  must be filled out, with a description and a demo video. See
  §4.6 for the remediation.

### 3.15 Permissions Declaration Form

- Several sensitive permissions require a declaration. For
  FluxLinux: `REQUEST_INSTALL_PACKAGES` (if kept) and
  `FOREGROUND_SERVICE_DATA_SYNC`. The form is in Play Console →
  Policy → App content → Sensitive permissions and APIs.

---

## 4. Per-File Findings (Remediation Map)

This section enumerates every file that contains policy-relevant code
or content, the issue, and the recommended fix.

### 4.1 `app/src/main/AndroidManifest.xml`

- **Line 9.** `uses-permission REQUEST_INSTALL_PACKAGES`. **Remove**.
  No allowed use case applies.
- **Lines 12-13.** `FOREGROUND_SERVICE` /
  `FOREGROUND_SERVICE_DATA_SYNC`. Keep, but fill the Play Console
  declaration form (description + demo video).
- **Lines 58-61.** `<service ... InstallServerService
  android:foregroundServiceType="dataSync" />`. Keep, but
  corresponding Play Console declaration required.

### 4.2 `app/src/main/kotlin/com/ivarna/fluxlinux/core/utils/ApkDownloader.kt`

- Whole file. **Delete or stub to a no-op.** All four public methods
  (`download`, `getInstallIntent`, `canInstallPackages`,
  `openInstallPermissionSettings`) exist solely to support the
  sideload path.

### 4.3 `app/src/main/kotlin/com/ivarna/fluxlinux/ui/screens/PrerequisitesScreen.kt`

- **Line 373-446.** `downloadTermux()`, `downloadX11()`, `installApk()`.
  **Replace** with `openUrl("https://play.google.com/store/apps/details?id=com.termux")`
  and `openUrl("https://f-droid.org/packages/com.termux.x11/")` (or
  Termux:X11's GitHub release page, which is acceptable because it is
  not an APK download — the user is taken to a *web page*).
- **Line 1181.** `Intent.ACTION_VIEW` to
  `support.google.com/android/answer/12623953`. **Safe.** No change.
- **Line 2109.** `Intent.ACTION_VIEW` to an XDA *attachment* URL
  (`.zip`). **Change** to the corresponding XDA *thread* URL (no
  `.zip`).
- **Lines 2224, 2247, 2270.** Open in-repo tutorial markdown files.
  **Safe.** No change.
- **Line 2293.** Opens Discord invite. **Safe.** No change.

### 4.4 `app/src/main/kotlin/com/ivarna/fluxlinux/ui/screens/SettingsScreen.kt`

- **Line 753, 789, 864, 923.** Developer-profile / docs links via
  `openUrl()`. **Safe.** No change.
- **Lines 899-903.** `SocialLink` list (GitHub, LinkedIn, Portfolio,
  Instagram, X). **Safe** — they are user-initiated opens from a
  "Connect" section, not the install path. No change.
- **Line 957-963.** `openUrl()` helper. **Keep**, but add a guard
  that rejects `file://` and any URL whose path ends in `.apk` /
  `.zip` to harden against future regressions.

### 4.5 `app/src/main/AndroidManifest.xml` deep-link

- **Lines 50-55.** `<data android:scheme="fluxlinux"
  android:host="callback" />`. The deep link is internal to the app's
  install flow (scripts call back via `am start -a
  android.intent.action.VIEW -d "fluxlinux://callback?..."`).
  **Keep** — internal deep links are allowed. Make sure the
  `MainActivity` handler at `MainActivity.kt:65` does not invoke
  `Intent.ACTION_VIEW` on any user-controlled data string from inside
  the URI query.

### 4.6 Foreground service `dataSync`

- The current service is `InstallServerService` with type `dataSync`.
  In Play Console → Policy → App content, fill:
  - **Functionality description**: "Keeps the install HTTP server
    alive across short user navigation so download progress is not
    lost."
  - **System impact if interrupted**: "Active downloads would be
    paused and would need to be resumed by the user."
  - **Demo video**: a screencast of a one-tap Debian install
    happening while the user briefly switches away and back.
- Required because targeting API 34+ forces a type declaration.

### 4.7 `fastlane/metadata/android/en-US/full_description.txt`

- **Line 11.** `Uses AccessibilityService API for system navigation
  actions (Back/Home) from the UI.` — **false claim** if the app does
  not implement the service. Either implement the service with
  `android:isAccessibilityTool="false"` and add an in-app consent
  dialog (and update manifest with a `<service>` + an XML metadata
  file), or **remove this line** from the description. The latter is
  the smaller, safer change.
- **Line 10.** `Requirements: Termux, Termux:X11.` — change to
  `Requirements: Termux (from Play Store) and Termux:X11 (from
  F-Droid). Both are free.`

### 4.8 `fastlane/metadata/android/en-US/short_description.txt`

- `clean`. No change.

### 4.9 `README.md`

- **Lines 9-11.** Play Store badge + F-Droid badge images. The
  README is on GitHub, not the Play listing. **Safe.**
- **Line 75.** Markdown YouTube thumbnail embed. **Safe** — it is
  a README image, not inside the app.
- **Lines 218-220.** `Install Termux from F-Droid` and
  `Install Termux:X11`. On the GitHub README, **safe**. Mirror the
  same Play/F-Droid install instructions in the in-app
  "Prerequisites" screen.

### 4.10 `app/src/main/assets/scripts/`

These are shell scripts the app executes inside Termux, not Kotlin
code. They contain GitHub release URLs to non-Android tooling
(Godot, lazygit, kotlin-compiler, mesa-for-android-container, etc.)
that are downloaded *inside Termux* as part of a normal `apt` /
`wget` install in a Linux userspace. **Not** a Play policy concern.
The shell-level download is inside the Linux container, not the
Android app.

- `clean` for Play policy purposes. (`AppLink` policy does not
  reach into what a Termux *inside the app* does — Termux is a
  separate process. Just make sure the *Android* app's UI does not
  surface those `.tar.gz` URLs to the user as "click to install".)

---

## 5. Required Pre-Submission Checklist

Before each Play Console upload, walk this list:

- [ ] `REQUEST_INSTALL_PACKAGES` removed from
  `AndroidManifest.xml`.
- [ ] `ApkDownloader.kt` removed (or reduced to a no-op).
- [ ] `PrerequisitesScreen.kt` opens Play Store and F-Droid URLs
  via `Intent.ACTION_VIEW` instead of downloading APKs.
- [ ] No URL with a `.apk` / `.zip` suffix is opened from inside
  the app.
- [ ] `full_description.txt` no longer claims "Uses
  AccessibilityService API" *unless* the service is implemented
  and declared.
- [ ] `full_description.txt` lists "Termux (from Play Store),
  Termux:X11 (from F-Droid)" under requirements.
- [ ] Privacy policy URL is set in Play Console and the file
  lives at `docs/playstore/privacy_policy.md` (create it if
  missing).
- [ ] Data safety form filled: no data collected, no data
  shared.
- [ ] Foreground service declaration form filled with description
  and demo video.
- [ ] No F-Droid badge as the primary install CTA in any
  Play-facing asset.
- [ ] No reference to GitHub APK download in the in-app
  prerequisites flow.
- [ ] Build's `minSdkVersion` and `targetSdkVersion` are within
  the Play requirement window (currently target 34+).
- [ ] App tested on API 34 (Android 14) device, foreground
  service type enforcement is not throwing
  `MissingForegroundServiceTypeException`.

---

## 6. Citations & Further Reading

Official Google Play policy pages:

- Developer Program Policies hub —
  <https://play.google.com/about/developer-content-policy/>
- Deceptive Behavior policy —
  <https://support.google.com/googleplay/android-developer/answer/9888077?hl=en>
- Restricted Content —
  <https://play.google.com/about/restricted-content/>
- Foreground service permissions declaration guidance —
  <https://support.google.com/googleplay/android-developer/answer/13392876>
  (Play Console → Policy → App content → Foreground service)

Third-party summaries cited in this document (cross-referenced
because the official pages are intentionally high-level):

- `REQUEST_INSTALL_PACKAGES` eligibility and declaration form —
  Play Console Help article on
  `android.permission.REQUEST_INSTALL_PACKAGES`.
- YouTube API Services Terms of Service —
  <https://developers.google.com/youtube/terms/api-services-terms-of-service>
- AccessibilityService policy specifics (Play Console help
  article on `BIND_ACCESSIBILITY_SERVICE`).

---

## 7. Change Log for This Doc

- **v3 (2026-06-17).** Package renamed back to
  `com.zenithblue.fluxlinux` on the playstore track (the package the
  Play Console listing was originally created with). `namespace` and
  `applicationId` in `app/build.gradle.kts`, the FileProvider authority
  in `AndroidManifest.xml`, the `settingsActivity` in
  `accessibility_service_config.xml`, and every `package` /
  `import com.ivarna.fluxlinux` line under
  `app/src/main/kotlin/com/zenithblue/fluxlinux/` updated.
  `versionName` bumped to `1.8p` (Play Store variant of v1.8) with
  `versionCode = 11` (one higher than main's 10). The in-app Settings
  "v1.8.0" footer updated to `v1.8p`. README Play badge back at
  `com.zenithblue.fluxlinux`. F-Droid link in README unchanged — that
  track stays at `com.ivarna.fluxlinux`.

- **v2 (2026-06-17).** Play Store track rebased onto `main` @ v1.8.0.
  All §2 findings (`ApkDownloader.kt`, in-app APK download + install,
  Termux / Termux:X11 sideload, the
  `https://xdaforums.com/...zip` busybox attachment link) removed from
  the playstore branch. `REQUEST_INSTALL_PACKAGES` stripped from
  `app/src/main/AndroidManifest.xml`. `fastlane/.../full_description.txt`
  rewritten — the false "Uses AccessibilityService API" claim and the
  sourceless "Requirements: Termux, Termux:X11" line are gone;
  Requirements now point to F-Droid. Changelogs `9.txt` and `10.txt`
  dropped their in-app APK-download and Termux-version-warning lines.
  README Play badge re-pointed at `com.ivarna.fluxlinux`.
  `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` +
  `InstallServerService` **kept** (per the user — non-issue, declaration
  form still needs to be filled in Play Console).
  `assembleDebug`, `assembleRelease`, and `bundleRelease` all build clean.

- **v1 (2026-06-17).** Initial inventory. Findings based on
  static analysis of the `main` branch at
  `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/...`,
  `fastlane/metadata/android/en-US/`, and `README.md`. Policy text
  cross-checked against the official Play Console Help pages and
  developer policy hub.
