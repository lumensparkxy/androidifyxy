package com.maswadkar.developers.androidify

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private lateinit var markwon: Markwon

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_MODEL = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_MODEL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (!::markwon.isInitialized) {
            markwon = Markwon.create(parent.context)
        }
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            val view = inflater.inflate(R.layout.item_message_user, parent, false)
            UserMessageViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_message_model, parent, false)
            ModelMessageViewHolder(view, markwon)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder is UserMessageViewHolder) {
            holder.bind(message)
        } else if (holder is ModelMessageViewHolder) {
            holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    class UserMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvUserMessage: TextView = itemView.findViewById(R.id.tvUserMessage)
        fun bind(message: ChatMessage) {
            tvUserMessage.text = message.text
        }
    }

    class ModelMessageViewHolder(itemView: View, private val markwon: Markwon) : RecyclerView.ViewHolder(itemView) {
        private val tvModelMessage: TextView = itemView.findViewById(R.id.tvModelMessage)
        fun bind(message: ChatMessage) {
            markwon.setMarkdown(tvModelMessage, message.text)
        }
    }
}