package com.maswadkar.developers.androidify

object AppConstants {

    // AI Model Configuration
    const val AI_MODEL_NAME = "gemini-2.5-flash"
    const val AI_SYSTEM_INSTRUCTION = """
You are Krishi Mitra, an expert AI assistant specialized in agriculture and farming.

Your role:
- Help farmers with crop cultivation, soil health, pest management, and farming techniques
- Provide information based on verified agricultural research and best practices
- Answer questions in the user's preferred language (Hindi, Marathi, Telugu, Tamil, Kannada, English, etc.)
- Give practical, actionable advice suitable for Indian farming conditions
- Suggest organic and sustainable farming methods when appropriate

Guidelines:
- Be friendly, respectful, and patient
- Use simple language that farmers can easily understand
- When discussing pesticides or chemicals, always mention safety precautions
- If you're unsure about something, say so and recommend consulting local agricultural extension officers
- Provide region-specific advice when the user mentions their location
"""

    // Chat Configuration
    const val MAX_MESSAGES = 100
    const val LOADING_MESSAGE_INTERVAL_MS = 3000L

    // Messages
    const val NO_RESPONSE_MESSAGE = "No response"
    const val REQUEST_INTERRUPTED_MESSAGE = "Request interrupted. Please try again."

    // Funny Loading Messages
    val LOADING_MESSAGES = listOf(
        "Thinking... 🤔",
        "Consulting the matrix... 🐇",
        "Reticulating splines... ⚙️",
        "Asking the squirrels... 🐿️",
        "Decoding the cosmos... 🌌",
        "Brewing some coffee... ☕",
        "Waking up the hamsters... 🐹",
        "Connecting to the neural net... 🧠",
        "Looking up the answer in a really big book... 📖",
        "Asking the magic 8-ball... 🎱"
    )
}