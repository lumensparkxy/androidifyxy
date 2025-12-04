package com.maswadkar.developers.androidify

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.maswadkar.developers.androidify.auth.AuthRepository
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var authRepository: AuthRepository
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle

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

        setupToolbarAndDrawer()
        setupViews()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        // Check if we're loading a specific conversation from HistoryActivity
        val conversationId = intent.getStringExtra(HistoryActivity.EXTRA_CONVERSATION_ID)
        if (conversationId != null) {
            viewModel.loadConversation(conversationId)
        }
    }

    private fun setupToolbarAndDrawer() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawerLayout)
        toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.open_drawer,
            R.string.close_drawer
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Handle back press to close drawer
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        navigationView.setNavigationItemSelectedListener(this)

        // Setup navigation header with user info
        setupNavHeader(navigationView)
    }

    private fun setupNavHeader(navigationView: NavigationView) {
        val headerView = navigationView.getHeaderView(0)
        val ivUserPhoto = headerView.findViewById<ImageView>(R.id.ivUserPhoto)
        val tvUserName = headerView.findViewById<TextView>(R.id.tvUserName)
        val tvUserEmail = headerView.findViewById<TextView>(R.id.tvUserEmail)

        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let { user ->
            tvUserName.text = user.displayName ?: "User"
            tvUserEmail.text = user.email ?: ""

            // Load profile photo with Glide
            user.photoUrl?.let { photoUrl ->
                Glide.with(this)
                    .load(photoUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_default_avatar)
                    .into(ivUserPhoto)
            }
        }
    }

    private fun setupViews() {
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

        // Setup chat adapter
        chatAdapter = ChatAdapter(mutableListOf())
        rvChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
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

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_new_chat -> {
                viewModel.startNewConversation()
            }
            R.id.nav_history -> {
                startActivity(Intent(this, HistoryActivity::class.java))
            }
            R.id.nav_sign_out -> {
                signOut()
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onStop() {
        super.onStop()
        viewModel.saveCurrentConversation()
    }

    private fun setupExampleQuestions(etInput: EditText) {
        val exampleClickListener = View.OnClickListener { view ->
            val question = (view as TextView).text.toString()
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