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
    Goal: ensure Android 13+ themed icons work for FluxLinux.

    Verification result: ALREADY IMPLEMENTED. No code work needed.

    Existing implementation:
    - `app/src/main/res/drawable/ic_launcher_monochrome.png` (1024x1024 RGBA, 230KB)
    - `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` adaptive-icon block
      includes `<monochrome android:drawable="@drawable/ic_launcher_monochrome"/>`.
    - Original commits: `e60f18a feat(icon): add monochrome layer for themed
      app icons on Android 13+` and follow-up `f04a043 feat: add monochrome
      layer to adaptive icon for themed icons`.

    Verification path:
    1. Confirm the monochrome drawable exists and is sized for adaptive icons
       (108x108 dp viewport = 1024x1024 px at xxxhdpi — correct).
    2. Confirm the adaptive-icon XML references it under `<monochrome>`.
    3. On a Pixel-class device running Android 13+ with themed icons enabled,
       the launcher icon should pick up the user's wallpaper-derived tint.

    Both pre-flight checks pass. Closes GH-2.
  note: Verified — already implemented. Closed without code changes.
