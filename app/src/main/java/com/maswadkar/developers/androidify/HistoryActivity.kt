package com.maswadkar.developers.androidify

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.maswadkar.developers.androidify.data.ChatRepository
import com.maswadkar.developers.androidify.data.Conversation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var historyAdapter: ChatHistoryAdapter
    private val chatRepository = ChatRepository.getInstance()

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_history)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupRecyclerView()
        observeConversations()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        val rvHistory = findViewById<RecyclerView>(R.id.rvHistory)

        historyAdapter = ChatHistoryAdapter(
            onConversationClick = { conversation ->
                openConversation(conversation)
            },
            onDeleteClick = { conversation ->
                showDeleteConfirmation(conversation)
            }
        )

        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = historyAdapter
    }

    private fun observeConversations() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        lifecycleScope.launch {
            chatRepository.getConversationsFlow(userId).collectLatest { conversations ->
                historyAdapter.updateConversations(conversations)
                updateEmptyState(conversations.isEmpty())
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        val rvHistory = findViewById<RecyclerView>(R.id.rvHistory)
        val emptyState = findViewById<View>(R.id.emptyState)

        if (isEmpty) {
            rvHistory.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            rvHistory.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
        }
    }

    private fun openConversation(conversation: Conversation) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_CONVERSATION_ID, conversation.id)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun showDeleteConfirmation(conversation: Conversation) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_conversation)
            .setMessage("Are you sure you want to delete \"${conversation.title}\"?")
            .setPositiveButton(android.R.string.ok) { _, _ ->
                deleteConversation(conversation.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteConversation(conversationId: String) {
        lifecycleScope.launch {
            chatRepository.deleteConversation(conversationId)
        }
    }
}

