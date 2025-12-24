# UI Design System: FluxLinux Glassmorphism

**Vision:** A futuristic, fluid interface that mimics the properties of glass (translucency, blur, light refraction). It should feel like a premium, floating layer above the Android system.

## 1. Core Design Principals
*   **Translucency & Blur:** The primary background for cards, dialogs, and navigation bars is NOT solid. It is a semi-transparent surface with a high backdrop blur (`filter: blur(20px)` equivalent).
*   **Layering:** Interface elements float on top of a dynamic, abstract gradient background. This depth is essential for the glass effect to be visible.
*   **Soft Borders:** Glass panes are defined by subtle, semi-transparent white borders (`1px solid rgba(255, 255, 255, 0.3)`) to mimic light catching the edge.

## 2. Color Palette & Gradients
*   **Background:** Deep, rich dark gradients (Midnight Blue -> Purple -> Black) to make the glass pop.
    *   *Hex Ideas:* `#0f0c29` -> `#302b63` -> `#24243e`
*   **Glass Surface:**
    *   `Color: LinearGradient(TopLeft -> BottomRight)`
    *   `Start: rgba(255, 255, 255, 0.15)`
    *   `End: rgba(255, 255, 255, 0.05)`
*   **Text:** Pure White (`#FFFFFF`) for headers, muted grey (`#DDDDDD`) for secondary text.
*   **Accents:** Neon Cyan (`#00E5FF`) and Magenta (`#FF00E6`) for active states.

## 3. Component Specs

### 3.1 Floating Bottom Navigation (Reference Image)
Instead of a flat bar glued to the bottom, the nav bar **floats** 20dp above the bottom edge.
*   **Shape:** Rounded Pill / Stadia (Corner Radius: 20dp-50dp).
*   **Material:** High Blur Glass.
*   **Icons:** 
    *   *Inactive:* Thin outline, semi-transparent white.
    *   *Active:* Filled, glowing, or using the accent gradient.
*   **Reference:** Similar to the uploaded image, but with a frosted glass texture instead of solid white.

### 3.2 Glass Cards (Dashboard Items)
*   **Container:** Rounded Rect (Corner Radius: 24dp).
*   **Stroke:** 1dp Gradient Stroke (White -> Transparent).
*   **Shadow:** Soft, diffused shadow (`Color: #000000`, `Alpha: 0.2`, `Blur: 20dp`).
*   **Content:**
    *   **Distro Cards:** Show the Logo floating in the center.
    *   **Status Card:** Shows CPU/RAM usage with a circular progress indicator (glass track, neon fill).

### 3.3 Buttons (The "Crystal" Button)
*   **Shape:** Fully Rounded Caps.
*   **Background:** deeply blurred, slightly higher opacity than cards.
*   **Click Effect:** "Ripple" that looks like a water droplet hitting the glass (distortion).
*   **Border:** slightly thicker (1.5dp) to encourage interaction.

## 4. Implementation in Jetpack Compose
To achieve "Real" blur on Android:
*   **Android 12+ (API 31):** Native `RenderEffect.createBlurEffect`.
*   **Android 8-11:** Fallback to a high-quality semi-transparent gradient or use `Haze` / `Cloudy` libraries (Toolkit).
*   **Library Recommendation:** `dev.chrisbanes.haze:haze` for performant, backward-compatible blur effects in Compose.

## 5. Animations
*   **Transitions:** Elements shouldn't just "appear". They should **fade and scale up** (as if coming closer through the glass mist).
*   **Parallax:** The background gradient should shift slightly as the user tilts the device (Gyroscope) or scrolls, enhancing the depth effect.
