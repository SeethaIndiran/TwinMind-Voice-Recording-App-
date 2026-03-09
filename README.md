# Twin Mind Recording App

A powerful Android application that allows users to **record voice**, **transcribe it into text**, and **generate AI-based summaries** using OpenAI’s APIs.  
Built with **Jetpack Compose**, **MVVM**, **Dagger Hilt**, **Kotlin Coroutines**, and **Clean Architecture** — this app demonstrates scalable Android development with real-world use cases.

---

## Features

- **Voice Recording** – Record audio seamlessly with start, pause, and stop functionality.
- **Foreground Notification** – Shows active recording status with quick action.
- **Transcription** – Converts recorded audio into text using **OpenAI Whisper API**.  
- **AI-Powered Summary** – Generates a short, clear summary of the transcript using **OpenAI GPT model**.  
- **Local Storage** – Saves recordings and summaries in local Room Database.  
- **Real-Time Updates** – Displays transcription and summary updates live.  
- **Modern UI** – Built entirely with **Jetpack Compose** for smooth, declarative UI.  
- **Modular Clean Architecture** – Separation of concerns with clear layers for UI, domain, and data.  

---

## Tech Stack

| Layer | Technology |
|-------|-------------|
| **UI** | Jetpack Compose, Material 3 |
| **Architecture** | MVVM + Clean Architecture |
| **Dependency Injection** | Dagger Hilt |
| **Async Handling** | Kotlin Coroutines, Flows |
| **Database** | Room |
| **Network** | Retrofit / OkHttp |
| **AI/LLM Integration** | OpenAI Whisper & GPT models |

---

## Setup Instructions

Follow these steps to get the project running on your local machine.

### 1. Clone the Repository
```bash
git clone https://github.com/SeethaIndiran/Twin-Mind-Assignment-Recording-App.git
cd Twin-Mind-Assignment-Recording-App
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
1. Open the project in **Android Studio (Ladybug or newer)**.
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

2. **Transcription**
   - Once a chunk is saved, `TranscriptionWorker` sends the WAV file to the **OpenAI Whisper API**.
   - The returned text is stored in the local database and updated in the UI.

3. **Summary Generation**
   - After the recording stops and all chunks are transcribed, `SummaryWorker` sends the full transcript to **OpenAI GPT-4**.
   - A structured summary (Title, Key Points, Action Items) is generated and displayed.

4. **Local Persistence**
   - All data is stored locally in **Room**, allowing users to view previous recordings and summaries offline.

---

## Future Improvements

- Add **Cloud Sync** for recordings using Firebase.  
- Support **Multi-language** transcription.  
- Implement **Sharing** functionality for summaries.  
- Add **Search** and filtering for saved recordings.  
- Improve **UI/UX** with dark mode support.
