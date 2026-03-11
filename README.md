# Twin Mind Recording App

A powerful Android application that allows users to **record voice**, **transcribe it into text**, and **generate AI-based summaries** using OpenAI’s APIs.  
Built with **Jetpack Compose**, **MVVM**, **Dagger Hilt**, **Kotlin Coroutines**, and **Clean Architecture** — this app demonstrates scalable Android development with real-world use cases.

---

## Features

- **Voice Recording** – Record audio seamlessly with start, pause, and stop functionality.
- **Foreground Notification** – Shows active recording status with quick actions directly from the notification drawer.
- **Transcription** – Converts recorded audio into text using **OpenAI Whisper API**.  
- **AI-Powered Summary** – Generates a short, clear summary of the transcript using **OpenAI GPT-4**.  
- **Local Storage** – Saves recordings and summaries in local Room Database.  
- **Real-Time Updates** – Displays transcription and summary updates live.  
- **Modern UI** – Built entirely with **Jetpack Compose** with a forced **Dark Theme** for a sleek look.  
- **Modular Clean Architecture** – Separation of concerns with clear layers for UI, domain, and data.  

---

## Tech Stack

| Layer | Technology |
|-------|-------------|
| **UI** | Jetpack Compose, Material 3 |
| **Architecture** | MVVM + Clean Architecture |
| **Dependency Injection** | Dagger Hilt |
| **Async Handling** | Kotlin Coroutines, Flows |
| **Background Tasks** | WorkManager |
| **Database** | Room |
| **Network** | Retrofit / OkHttp |
| **AI Integration** | OpenAI Whisper & GPT-4 |

---

## Setup Instructions

Follow these steps to get the project running on your local machine.

### 1. Clone the Repository
```bash
git clone https://github.com/SeethaIndiran/Twin-Mind-Assignment-Recording-App-.git

```

### 2. Configure OpenAI API Key
The app requires an OpenAI API key for transcription and summarization. 

1. Create an OpenAI account and generate an API key at [platform.openai.com](https://platform.openai.com/api-keys).
2. Open the `local.properties` file in the root directory of the project.
3. Add your API key at the end of the file:
   ```properties
   OPENAI_API_KEY=your_actual_api_key_here
   ```
   *(Note: `local.properties` is ignored by Git, so your key will remain private.)*

### 3. Build and Run
1. Open the project in **Android Studio**.
2. Sync the project with Gradle files.
3. Select an Android device or emulator (API Level 24+).
4. Click **Run**.

---

## Project Structure

```text
com.example.twinmindrecordingapphomeassignment/
│
├── di/                    # Hilt dependency injection modules
├── data/
│   ├── local/             # Room database, DAOs, and Entities
│   ├── remote/            # Retrofit/OkHttp API services and DTOs
│   └── repository/        # Repository implementations (Data Layer)
│
├── domain/
│   └── model/             # Domain models / POJOs
│
├── presentation/          # UI Layer
│   ├── viewmodel/         # ViewModels for each feature
│   └── ui/                # Jetpack Compose screens and components
│
├── service/               # Android Services (RecordingService)
└── worker/                # WorkManager background tasks
```

---

## How the App Works

1. **Start Recording**
   - User taps the **Record** button.
   - `RecordingService` starts in the foreground to capture audio via `AudioRecord`.
   - Audio is saved as headered **WAV** files in chunks to ensure compatibility with OpenAI.

2. **Background Processing (WorkManager)**
   - As soon as a chunk is saved, a `TranscriptionWorker` is enqueued.
   - WorkManager handles the upload to OpenAI Whisper API in the background, ensuring the UI remains responsive and recording is never interrupted by network latency.

3. **Summary Generation**
   - Once all chunks are transcribed, `SummaryWorker` sends the full transcript to **OpenAI GPT-4**.
   - A structured summary (Title, Key Points, Action Items) is generated and displayed.

4. **Local Persistence**
   - All data is stored locally in **Room**, allowing users to view previous recordings and summaries offline.

---

## Edge Case Handling

This app is built to be resilient and handle common interruptions gracefully:

- **Phone Calls**: The app listens for call states. If a call starts, recording pauses automatically with the status **"Paused - Phone call"** and resumes when the call ends.
- **Audio Focus**: If another app (like YouTube) starts playing audio, the recording pauses with the status **"Paused – Audio focus lost"** and resumes once focus is regained.
- **Low Storage**: Before and during recording, the app checks for available space. If storage drops below **50MB**, the recording stops gracefully to prevent data corruption.
- **Process Death**: The recording session state is persisted in Room. If the app crashes or is killed, a `RecordingTerminationWorker` runs on the next launch to finalize unprocessed chunks.
- **Noisy Silence Detection**: The app includes a strict filter for silent or noisy recordings. If the final transcript is empty or too short, it displays **"No speech detected"** instead of generating a generic AI summary.
- **Device Changes**: Recording continues uninterrupted when switching between the phone microphone and Bluetooth/wired headsets.

---

## Future Improvements

- Add **Cloud Sync** for recordings using Firebase.  
- Support **Multi-language** transcription.  
- Implement **Sharing** functionality for summaries.  
- Add **Search** and filtering for saved recordings.
