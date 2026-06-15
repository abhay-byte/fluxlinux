---
- id: T6
  title: Add monochrome icon for Android theming
  type: feature
  priority: low
  difficulty: easy
  why: Modern Android supports themed icons; monochrome layer lets icon adapt to user's theme
  really_needed: Cosmetic polish, not blocking
  impact: Assets (monochrome PNG/vector + adaptive icon config)
  followups: null
  images: https://github.com/user-attachments/assets/3b15435d-8e54-4d58-88ed-b85b722d8e90
  github_ref: GH-2
  plan: |
    Goal: ship a Material You–compatible monochrome icon for the adaptive
    launcher icon. Android 13+ themed icons pick up the wallpaper-derived
    tint and apply it to a single-colour silhouette the app provides.

    What was already there:
    - mipmap-anydpi-v26/ic_launcher.xml had the <monochrome> element
      wired to @drawable/ic_launcher_monochrome.
    - The referenced asset was a 1024x1024 RGBA PNG of the rendered 3D
      penguin mascot, converted to grayscale. It had multiple shades
      (sampled 49 unique colours in a 200-pixel random sample) so the
      system-tint pass could not flatten it to a single hue. The result
      was an off-tint, slightly muddy icon under themed-icon launchers
      on Android 13+.

    What T6 actually changes:
    - NEW  app/src/main/res/drawable/ic_launcher_monochrome.xml
            Vector drawable, single fillColor #FFFFFFFF (white). All
            paths use evenOdd fill so the eye dots and beak punch
            through as transparent holes, giving a clean silhouette
            that takes any tint cleanly.
            Layout matches the safe-zone guidance: 108dp canvas, the
            penguin sits in the 66dp safe zone (head/body 18..74
            vertical, box + flaps 56..86).
    - DEL  app/src/main/res/drawable/ic_launcher_monochrome.png
            Replaced by the vector; AAPT no longer ships a 230 KB
            raster for what is effectively a flat silhouette.
    - The adaptive-icon XML at mipmap-anydpi-v26/ic_launcher.xml is
      unchanged — the drawable resource name (ic_launcher_monochrome)
      is identical, so the existing reference still resolves.

    Verification:
    - Build ./gradlew assembleDebug → 0 errors, the adaptive-icon
      manifest merger resolves @drawable/ic_launcher_monochrome to the
      new vector.
    - Install on the test devices, look at the home screen with
      themed icons enabled (Pixel Launcher or similar). The launcher
      icon should pick up the wallpaper tint.
    - On a non-themed launcher (or pre-Android 13 device) the icon
      renders the white silhouette at 100% alpha — looks correct on
      a dark or light background, doesn't compete with the foreground
      logo.

    Closes GH-2.
  note: Original e60f18a + f04a043 commits shipped a raster PNG, which
        the system-tint pass can't flatten. T6 replaces it with a
        true single-colour vector silhouette.