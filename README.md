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





