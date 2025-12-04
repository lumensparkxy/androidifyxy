package com.maswadkar.developers.androidify

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.maswadkar.developers.androidify.auth.AuthRepository
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Initialize auth repository
        authRepository = AuthRepository(this)

        // Check if user is logged in
        if (FirebaseAuth.getInstance().currentUser == null) {
            navigateToLogin()
            return
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.systemBars())
            v.setPadding(imeInsets.left, imeInsets.top, imeInsets.right, imeInsets.bottom)
            insets
        }

        val rvChat = findViewById<RecyclerView>(R.id.rvChat)
        val etInput = findViewById<EditText>(R.id.etInput)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val welcomeView = findViewById<View>(R.id.welcomeView)

        // Setup example question click listeners
        setupExampleQuestions(etInput)

        chatAdapter = ChatAdapter(mutableListOf())
        rvChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // Start from bottom like a chat
        }
        rvChat.adapter = chatAdapter

        // Observe messages from ViewModel
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { messages ->
                    chatAdapter.updateMessages(messages)

                    // Show/hide welcome view based on messages
                    if (messages.isEmpty()) {
                        welcomeView.visibility = View.VISIBLE
                        rvChat.visibility = View.GONE
                    } else {
                        welcomeView.visibility = View.GONE
                        rvChat.visibility = View.VISIBLE
                        rvChat.scrollToPosition(messages.size - 1)
                    }
                }
            }
        }

        btnSend.setOnClickListener {
            sendMessage(etInput)
        }
    }

    private fun setupExampleQuestions(etInput: EditText) {
        val exampleClickListener = View.OnClickListener { view ->
            val question = (view as TextView).text.toString()
            // Remove the quotes from the example question
            val cleanQuestion = question.trim('"')
            etInput.setText(cleanQuestion)
            sendMessage(etInput)
        }

        findViewById<TextView>(R.id.tvExample1).setOnClickListener(exampleClickListener)
        findViewById<TextView>(R.id.tvExample2).setOnClickListener(exampleClickListener)
        findViewById<TextView>(R.id.tvExample3).setOnClickListener(exampleClickListener)
    }

    private fun sendMessage(etInput: EditText) {
        val userText = etInput.text.toString().trim()
        if (userText.isNotEmpty()) {
            viewModel.sendMessage(userText)
            etInput.text.clear()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sign_out -> {
                signOut()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun signOut() {
        lifecycleScope.launch {
            authRepository.signOut()
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}