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
import com.google.firebase.vertexai.vertexAI
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter

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
        val model = Firebase.vertexAI.generativeModel("gemini-2.0-flash")

        btnSend.setOnClickListener {
            val userText = etInput.text.toString().trim()
            if (userText.isNotEmpty()) {
                // Add user message
                messages.add(ChatMessage(userText, true))
                chatAdapter.notifyItemInserted(messages.size - 1)
                rvChat.scrollToPosition(messages.size - 1)
                etInput.text.clear()

                // Call AI
                lifecycleScope.launch {
                    try {
                        val response = model.generateContent(userText)
                        val modelText = response.text ?: "No response"
                        messages.add(ChatMessage(modelText, false))
                        chatAdapter.notifyItemInserted(messages.size - 1)
                        rvChat.scrollToPosition(messages.size - 1)
                    } catch (e: Exception) {
                        messages.add(ChatMessage("Error: ${e.message}", false))
                        chatAdapter.notifyItemInserted(messages.size - 1)
                        rvChat.scrollToPosition(messages.size - 1)
                    }
                }
            }
        }
    }
}