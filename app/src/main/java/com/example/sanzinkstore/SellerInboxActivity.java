package com.example.sanzinkstore;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.TextView;

import com.example.sanzinkstore.adapter.ConversationAdapter;
import com.example.sanzinkstore.model.Conversation;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * SellerInboxActivity — Shows all customer conversations to the seller.
 * Displays unread dot, 2-line message preview, and timestamp.
 */
public class SellerInboxActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private List<Conversation> conversationList;
    private ConversationAdapter adapter;
    private TextView tvTotalUnread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seller_inbox);

        db = FirebaseFirestore.getInstance();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Customer Messages");
        }

        tvTotalUnread = findViewById(R.id.tvTotalUnread);

        RecyclerView rvConversations = findViewById(R.id.rvConversations);
        conversationList = new ArrayList<>();
        adapter = new ConversationAdapter(conversationList, this::openChat);
        rvConversations.setLayoutManager(new LinearLayoutManager(this));
        rvConversations.addItemDecoration(
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        rvConversations.setAdapter(adapter);

        listenToConversations();
    }

    private void listenToConversations() {
        db.collection("conversations")
                .orderBy("lastMessageTime", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    conversationList.clear();
                    long totalUnread = 0;

                    for (QueryDocumentSnapshot doc : snapshots) {
                        Conversation conv = doc.toObject(Conversation.class);
                        if (conv.getCustomerId() == null) {
                            conv.setCustomerId(doc.getId());
                        }
                        conversationList.add(conv);
                        totalUnread += conv.getUnreadBySeller();
                    }

                    adapter.notifyDataSetChanged();

                    if (totalUnread > 0) {
                        tvTotalUnread.setText(totalUnread + " unread message" + (totalUnread > 1 ? "s" : ""));
                    } else {
                        tvTotalUnread.setText("");
                    }
                });
    }

    private void openChat(Conversation conv) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_IS_SELLER, true);
        intent.putExtra(ChatActivity.EXTRA_CUSTOMER_ID, conv.getCustomerId());
        intent.putExtra(ChatActivity.EXTRA_CUSTOMER_NAME,
                conv.getCustomerName() != null ? conv.getCustomerName() : "Customer");
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

