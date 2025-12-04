package com.maswadkar.developers.androidify

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.maswadkar.developers.androidify.data.Conversation

class ChatHistoryAdapter(
    private val onConversationClick: (Conversation) -> Unit,
    private val onDeleteClick: (Conversation) -> Unit
) : RecyclerView.Adapter<ChatHistoryAdapter.ConversationViewHolder>() {

    private var conversations: List<Conversation> = emptyList()

    fun updateConversations(newConversations: List<Conversation>) {
        val diffCallback = ConversationDiffCallback(conversations, newConversations)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        conversations = newConversations
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation_history, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.bind(conversations[position])
    }

    override fun getItemCount(): Int = conversations.size

    inner class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvConversationTitle)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
        private val cardView: View = itemView.findViewById(R.id.cardConversation)

        fun bind(conversation: Conversation) {
            tvTitle.text = conversation.title

            // Format timestamp as relative time (e.g., "2 hours ago")
            val timestamp = conversation.updatedAt ?: conversation.createdAt
            tvTimestamp.text = if (timestamp != null) {
                DateUtils.getRelativeTimeSpanString(
                    timestamp.toDate().time,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                )
            } else {
                ""
            }

            cardView.setOnClickListener {
                onConversationClick(conversation)
            }

            btnDelete.setOnClickListener {
                onDeleteClick(conversation)
            }
        }
    }

    private class ConversationDiffCallback(
        private val oldList: List<Conversation>,
        private val newList: List<Conversation>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}

