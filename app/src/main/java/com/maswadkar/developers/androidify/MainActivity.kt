package com.maswadkar.developers.androidify

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter
    
    private val funnyLoadingMessages = listOf(
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.systemBars())
            v.setPadding(imeInsets.left, imeInsets.top, imeInsets.right, imeInsets.bottom)
            insets
        }

        val rvChat = findViewById<RecyclerView>(R.id.rvChat)
        val etInput = findViewById<EditText>(R.id.etInput)
        val btnSend = findViewById<Button>(R.id.btnSend)

        chatAdapter = ChatAdapter(messages)
        rvChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // Start from bottom like a chat
        }
        rvChat.adapter = chatAdapter

        // Initialize Firebase AI with Gemini model
        val model = Firebase.ai(backend = GenerativeBackend.vertexAI())
            .generativeModel("gemini-2.5-flash")

        btnSend.setOnClickListener {
            val userText = etInput.text.toString().trim()
            if (userText.isNotEmpty()) {
                // Add user message
                messages.add(ChatMessage(userText, true))
                chatAdapter.notifyItemInserted(messages.size - 1)
                rvChat.scrollToPosition(messages.size - 1)
                etInput.text.clear()

                // Add placeholder loading message
                val loadingMessage = ChatMessage(funnyLoadingMessages.first(), false, true)
                messages.add(loadingMessage)
                val loadingIndex = messages.size - 1
                chatAdapter.notifyItemInserted(loadingIndex)
                rvChat.scrollToPosition(loadingIndex)

                // Call AI
                lifecycleScope.launch {
                    val animationJob = launch {
                        while (isActive) {
                            delay(5000)
                            loadingMessage.text = funnyLoadingMessages.random()
                            chatAdapter.notifyItemChanged(loadingIndex)
                        }
                    }

                    try {
                        val response = model.generateContent(userText)
                        val modelText = response.text ?: "No response"
                        
                        animationJob.cancel()
                        
                        // Update loading message with real response
                        loadingMessage.text = modelText
                        chatAdapter.notifyItemChanged(loadingIndex)
                        rvChat.scrollToPosition(loadingIndex)
                    } catch (e: Exception) {
                        animationJob.cancel()
                        loadingMessage.text = "Error: ${e.message}"
                        chatAdapter.notifyItemChanged(loadingIndex)
                        rvChat.scrollToPosition(loadingIndex)
                    }
                }
            }
        }
    }
}