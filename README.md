# Krishi AI (Androidify)

**Krishi AI** is a comprehensive Android application designed to empower farmers and agricultural stakeholders in India. It bridges the gap between farmers and market data by providing real-time **Mandi (Market) Prices** sourced directly from government databases, alongside an intelligent **AI Assistant** for personalized agricultural advice.

## 🚀 Features

*   **Real-time Mandi Prices**: Access daily market rates for various commodities across different states and districts, synced from [data.gov.in](https://data.gov.in).
*   **AI Chat Assistant**: A smart, conversational AI powered by Firebase Genkit/Vertex AI to answer farming queries, provide crop advice, and interpret market trends.
*   **Live Audio Conversation**: Voice-enabled interaction allows users to converse naturally with the assistant in multiple languages (Hindi, Marathi, Telugu, Tamil, etc.), making technology accessible even while working in the field.
*   **Secure Authentication**: Seamless login experience using Google Sign-In via Firebase Authentication.
*   **History & Offline Support**: Save important conversations and access recently viewed prices.
*   **Clean & Modern UI**: Built with Jetpack Compose and Material 3 for a fluid and intuitive user experience.

## 🛠 Tech Stack

### Android App (`:app`)
*   **Language**: Kotlin
*   **UI Toolkit**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3)
*   **Architecture**: MVVM (Model-View-ViewModel) with Clean Architecture principles.
*   **Navigation**: Jetpack Navigation Compose.
*   **Data & Network**:
    *   **Firebase Firestore**: For real-time data storage.
    *   **Coil**: For asynchronous image loading.
*   **AI & ML**:
    *   **Firebase AI / Genkit**: For generative AI capabilities.
    *   **Google Identity Services**: For secure authentication.
*   **Tools**: Gradle (Kotlin DSL), Coroutines, Flow.

### Backend (`/functions`)
*   **Platform**: Firebase Cloud Functions (Node.js 22).
*   **Core Logic**: Automated daily sync jobs that fetch data from the Open Government Data (OGD) platform and update Firestore.
*   **Database**: Cloud Firestore with optimized indexing for fast queries.
*   See [functions/README.md](functions/README.md) for detailed backend documentation.

### Web (`/website`)
*   **Framework**: [Next.js](https://nextjs.org/) 15 with React 19.
*   **Styling**: [Tailwind CSS](https://tailwindcss.com/) for utility-first styling.
*   **Language**: TypeScript.
*   **Purpose**: Landing page / Companion web interface.

## 📂 Project Structure

```
androidifyxy/
├── app/                # Android Application source code
├── functions/          # Firebase Cloud Functions (Backend)
├── website/            # Next.js Web Application
├── gradle/             # Gradle wrapper files
└── build.gradle.kts    # Root build configuration
```

## 📦 Releases

See `docs/ANDROID_RELEASE.md` for Android production release guidance (branching, versioning, signing, and Play Store notes).

## 🏁 Getting Started

### Prerequisites
*   [Android Studio Ladybug](https://developer.android.com/studio) or newer.
*   JDK 11 or newer.
*   Node.js 22 (for Cloud Functions).
*   Firebase Project with **Blaze** (Pay-as-you-go) plan (required for external API calls in Cloud Functions).
*   API Key from [data.gov.in](https://data.gov.in).

### Installation & Setup

1.  **Clone the Repository**
    ```bash
    git clone https://github.com/yourusername/androidifyxy.git
    cd androidifyxy
    ```

2.  **Firebase Configuration**
    *   Create a project in the [Firebase Console](https://console.firebase.google.com/).
    *   **Android App**: Register an app with package `com.maswadkar.developers.androidify`. Download `google-services.json` and place it in the `app/` directory.
    *   **Authentication**: Enable **Google** provider in the Authentication section.
    *   **Firestore**: Create a Firestore database.
    *   **AI**: Enable necessary Vertex AI / Genkit APIs if applicable.

3.  **Backend Setup**
    *   Navigate to `functions/` and install dependencies:
        ```bash
        cd functions
        npm install
        ```
    *   Deploy the functions and indexes:
        ```bash
        firebase deploy --only functions,firestore:indexes
        ```
    *   *Note*: Refer to `functions/README.md` for scheduling and API key configuration.

4.  **Run the App**
    *   Open the project in Android Studio.
    *   Sync Gradle with Project.
    *   Select the `app` configuration and click **Run**.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

This project is licensed under the [MIT License](LICENSE).
