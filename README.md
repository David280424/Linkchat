# TextMeMail 📱

TextMeMail is an **Android messaging + video call app** built with **Kotlin**, **Jetpack Compose**, **Firebase**, and **Agora RTC**.  
Current version includes authentication, account management, real‑time chat, and basic one‑to‑one video calls (test mode – no tokens required yet).

---

## 🚀 Features

### Authentication & Accounts
* Email / password sign up & login
* Email verification flow
* Password & email change
* Preferred language (EN / ES) persisted (DataStore + Firestore)

### Messaging
* Real‑time 1:1 messaging using Firestore snapshot listeners
* Basic contact list (all verified users except self)
* Reactive Compose UI for new messages

### Video Calls (Implemented)
* One‑to‑one video call using **Agora RTC SDK (v4)**
* Dynamic channel naming per user pair
* Camera / microphone permission handling
* Local + remote video renderers
* Test / development mode (App ID only, no token)

### UI / UX
* 100% Jetpack Compose + Material 3
* Light / Dark theme aware
* Simple, clean, mobile‑first layouts

### Internationalization
* Runtime language switching (EN / ES)

---

## 🛠 Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Auth | Firebase Authentication |
| Data | Cloud Firestore (NoSQL) |
| Preferences | Jetpack DataStore (Proto / Preferences) |
| Realtime Chat | Firestore snapshot listeners |
| Video | Agora RTC SDK (4.x) |
| Build | Gradle (KTS) |

---

## 📂 Simplified Project Structure

```
app/
 └── src/main/java/com/example/textmemail/
    ├── MainActivity.kt                # Entry + navigation + listeners
    ├── auth/                          # EmailAuthManager & related
    ├── ui_auth/                       # Auth / verification screens
    ├── ui_chat/                       # Chat + contacts + compose screens
    ├── VideoCallActivity.kt           # Agora video call screen
    └── ui/theme/                      # Theming (colors, typography)
```

---

## ⚙️ Setup & Configuration

### 1. Firebase
1. Create a Firebase project
2. Enable: Authentication (Email/Password) & Firestore
3. Download `google-services.json` → place in `app/`
4. (Optional) Set Firestore rules for authenticated access only

### 2. Agora (Video Calls)
1. Create an Agora account & project (Test mode for now)
2. Copy your **App ID**
3. Set the App ID inside `VideoCallActivity.kt`:
  ```kotlin
  private val appId = "<YOUR_AGORA_APP_ID>"
  ```
4. (Later) For production switch to secured mode with token server

### 3. Build & Run
Android Studio (Giraffe+). Just sync & run:
```
./gradlew assembleDebug
```

### 4. Permissions
The app requests CAMERA + RECORD_AUDIO at runtime for video calls.

---

## 📖 Functional Flow

1. User registers (email, password, name, language) → Firestore `users/{uid}`
2. Email verification required before chat access
3. Contact list loads all other users (excluding current)
4. Chat screen listens to message thread via Firestore collection(s)
5. Video call button creates / joins an Agora channel derived from both UIDs

---

## 🔒 Security (Current State)
* Firebase Auth handles credentials + email verification
* Firestore rules should restrict reads/writes to authenticated users
* (Planned) Per‑chat access rules & message validation
* (Planned) Token server for Agora (production hardening)

---

## 📌 Roadmap

| Status | Item |
|--------|------|
| ✅ | Firebase Auth (register / login / verify) |
| ✅ | Profile & language preference persistence |
| ✅ | Real‑time messaging (Firestore) |
| ✅ | Basic 1:1 video call (Agora test mode) |
| 🔜 | Incoming call invitations (ring / accept / reject) |
| 🔜 | Push notifications (FCM) for new messages / calls |
| 🔜 | Call history & missed call states |
| 🔜 | Secure Agora token backend |
| 🔜 | Message delivery & read receipts |

Legend: ✅ Done · 🔜 Planned

---

## 🤔 Troubleshooting

| Issue | Hint |
|-------|------|
| Video engine init error 101 | Check Agora App ID value & network |
| Error 110 | Using secure mode without token – switch to test or add token server |
| No remote video | Ensure both users joined same channel name |
| Empty contacts | Verify Firestore `users` documents exist & you’re verified |

---

## 🧱 Next Improvements (Suggested)
* Introduce call invitation collection + listener
* Add FCM integration for background call / message alerts
* Migrate video call configuration to a central manager
* Replace hardcoded App ID with BuildConfig + local gradle property

---

## 📝 License

Feel free to use, modify, and distribute for personal or academic projects.

---

## 🙌 Contribution
Open to lightweight academic / learning contributions. PRs welcome (small, focused changes preferred).

