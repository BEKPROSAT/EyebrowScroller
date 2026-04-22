# 👁 EyebrowScroll — Setup Guide

## What This App Does
Control scrolling on ANY Android app (TikTok, Instagram, YouTube, etc.) using just your face:
- 🤨 **Raise eyebrow** → Scroll DOWN
- 😉 **Wink (either eye)** → Scroll UP

---

## Step 1 — Open in Android Studio

1. Extract this ZIP file somewhere on your computer
2. Open **Android Studio**
3. Click **"Open"** (not "New Project")
4. Select the `EyebrowScroll` folder
5. Wait for Gradle to sync (first time takes 2–5 minutes, it downloads dependencies)

> ✅ You should see no red errors in the Project panel when sync finishes.

---

## Step 2 — Connect Your S23 Ultra

1. On your phone go to **Settings → About Phone → Software Information**
2. Tap **Build Number** 7 times to enable Developer Options
3. Go to **Settings → Developer Options → USB Debugging** → turn it ON
4. Connect your phone via USB
5. Accept the "Allow USB debugging?" popup on your phone

---

## Step 3 — Build & Install

1. In Android Studio, select your phone from the device dropdown (top toolbar)
2. Click the green ▶ **Run** button
3. The app installs automatically on your phone

---

## Step 4 — First Launch Setup (on your phone)

When you open EyebrowScroll you'll see two red dots. Fix them in order:

### 4a. Enable Accessibility Service
1. Tap **"Enable"** next to Accessibility Service
2. Android Settings opens — find **"EyebrowScroll"** in the list
3. Tap it → toggle it **ON**
4. Accept the permission dialog
5. Come back to the app — dot turns green ✓

### 4b. Grant Camera Permission
1. Tap **"Grant"** next to Camera Permission
2. Tap **"Allow"** on the popup
3. Dot turns green ✓

---

## Step 5 — Start Using It!

1. Tap **"START EYEBROW CONTROL"**
2. Minimize the app (go to your home screen)
3. Open TikTok, Instagram, YouTube — anything
4. **Raise your eyebrow** → scroll down
5. **Wink** → scroll up

The app runs in the background (you'll see a notification).

---

## Tips for Best Detection

- **Good lighting** on your face makes a big difference
- Hold the phone at a natural angle — front camera should see your face
- If gestures feel too sensitive, lower the slider in the app
- If gestures aren't triggering, raise the sensitivity slider

---

## Troubleshooting

| Problem | Fix |
|---|---|
| Gradle sync fails | File → Invalidate Caches → Restart |
| "SDK location not found" | Android Studio will auto-detect, just wait for sync |
| App installed but scroll doesn't work | Make sure Accessibility Service is ON (green dot) |
| Eyebrow gesture triggers too easily | Lower sensitivity in the app |
| Wink not detected | Try closing eye more fully, adjust lighting |

---

## Permissions Explained

| Permission | Why |
|---|---|
| CAMERA | To see your face with the front camera |
| FOREGROUND_SERVICE | To keep detecting while you use other apps |
| BIND_ACCESSIBILITY_SERVICE | To perform scroll gestures in other apps |
