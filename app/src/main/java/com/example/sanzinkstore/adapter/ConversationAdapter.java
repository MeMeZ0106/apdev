package com.example.sanzinkstore.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sanzinkstore.R;
import com.example.sanzinkstore.model.Conversation;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {

    public interface OnConversationClickListener {
        void onClick(Conversation conversation);
    }

    private final List<Conversation> conversations;
    private final OnConversationClickListener listener;

    public ConversationAdapter(List<Conversation> conversations, OnConversationClickListener listener) {
        this.conversations = conversations;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Conversation conv = conversations.get(position);

        holder.tvName.setText(conv.getCustomerName() != null ? conv.getCustomerName() : "Customer");
        holder.tvLastMessage.setText(conv.getLastMessage());

        // Format timestamp
        if (conv.getLastMessageTime() > 0) {
            String formatted = formatTime(conv.getLastMessageTime());
            holder.tvTime.setText(formatted);
        }

        // Unread dot
        boolean hasUnread = conv.getUnreadBySeller() > 0;
        holder.viewUnreadDot.setVisibility(hasUnread ? View.VISIBLE : View.INVISIBLE);
        if (hasUnread) {
            holder.tvUnreadCount.setText(String.valueOf(conv.getUnreadBySeller()));
            holder.tvUnreadCount.setVisibility(View.VISIBLE);
        } else {
            holder.tvUnreadCount.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onClick(conv));
    }

    private String formatTime(long timestamp) {
        Date msgDate = new Date(timestamp);
        Date now = new Date();
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d", Locale.getDefault());

        // Same day → show time, otherwise show date
        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        if (dayFormat.format(msgDate).equals(dayFormat.format(now))) {
            return timeFormat.format(msgDate);
        } else {
            return dateFormat.format(msgDate);
        }
    }

    @Override
    public int getItemCount() { return conversations.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View viewUnreadDot;
        TextView tvName, tvLastMessage, tvTime, tvUnreadCount;

        ViewHolder(View itemView) {
            super(itemView);
            viewUnreadDot = itemView.findViewById(R.id.viewUnreadDot);
            tvName = itemView.findViewById(R.id.tvConversationName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvTime = itemView.findViewById(R.id.tvConversationTime);
            tvUnreadCount = itemView.findViewById(R.id.tvUnreadCount);
        }
    }
}

