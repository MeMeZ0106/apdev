package com.example.sanzinkstore;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sanzinkstore.adapter.MessageAdapter;
import com.example.sanzinkstore.model.Message;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ChatActivity - Used by BOTH customer and seller for 1-on-1 messaging.
 *
 * Extras expected:
 *   EXTRA_IS_SELLER   (boolean) – true if the current user is the seller
 *   EXTRA_CUSTOMER_ID (String)  – the customer's Firebase UID (required for seller)
 *   EXTRA_CUSTOMER_NAME (String)– display name shown in toolbar (required for seller)
 */
public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_IS_SELLER    = "EXTRA_IS_SELLER";
    public static final String EXTRA_CUSTOMER_ID  = "EXTRA_CUSTOMER_ID";
    public static final String EXTRA_CUSTOMER_NAME = "EXTRA_CUSTOMER_NAME";

    private static final String SELLER_ID = "seller";

    private FirebaseFirestore db;
    private List<Message> messageList;
    private MessageAdapter adapter;
    private RecyclerView rvMessages;
    private EditText etMessage;

    private String conversationId;  // = customer Firebase UID
    private String currentSenderId; // customer UID or "seller"
    private boolean isSeller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        db = FirebaseFirestore.getInstance();
        isSeller = getIntent().getBooleanExtra(EXTRA_IS_SELLER, false);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (isSeller) {
            // Seller opens a specific customer's conversation
            conversationId  = getIntent().getStringExtra(EXTRA_CUSTOMER_ID);
            currentSenderId = SELLER_ID;
        } else {
            // Customer: use their Firebase UID (anonymous or Google)
            if (user == null) {
                Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            conversationId  = user.getUid();
            currentSenderId = user.getUid();
            ensureConversationExists(user);
        }

        // Derive name for toolbar title
        String chatTitle;
        if (isSeller) {
            chatTitle = getIntent().getStringExtra(EXTRA_CUSTOMER_NAME);
            if (chatTitle == null) chatTitle = "Customer";
        } else {
            chatTitle = "Seller";
        }

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(chatTitle);
        }

        // RecyclerView
        rvMessages = findViewById(R.id.rvMessages);
        messageList = new ArrayList<>();
        adapter = new MessageAdapter(messageList, currentSenderId);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(adapter);

        // Send button
        etMessage = findViewById(R.id.etMessage);
        ImageButton btnSend = findViewById(R.id.btnSend);
        btnSend.setOnClickListener(v -> sendMessage());

        listenToMessages();
        markMessagesAsRead();
    }

    /** Create the conversation document the first time a customer opens the chat. */
    private void ensureConversationExists(FirebaseUser user) {
        // Determine a human-readable name
        String displayName = user.getDisplayName();
        if (displayName == null || displayName.isEmpty()) {
            displayName = "Customer-" + user.getUid().substring(0, 6);
        }
        final String name = displayName;

        db.collection("conversations").document(conversationId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("customerId",       conversationId);
                        data.put("customerName",     name);
                        data.put("lastMessage",      "");
                        data.put("lastMessageTime",  System.currentTimeMillis());
                        data.put("unreadBySeller",   0L);
                        data.put("unreadByCustomer", 0L);
                        db.collection("conversations").document(conversationId).set(data);
                    }
                });
    }

    private void listenToMessages() {
        db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    messageList.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Message msg = doc.toObject(Message.class);
                        msg.setId(doc.getId());
                        messageList.add(msg);
                    }
                    adapter.notifyDataSetChanged();
                    if (!messageList.isEmpty()) {
                        rvMessages.scrollToPosition(messageList.size() - 1);
                    }
                });
    }

    private void sendMessage() {
        String text = etMessage.getText() != null
                ? etMessage.getText().toString().trim() : "";
        if (TextUtils.isEmpty(text)) return;

        long now = System.currentTimeMillis();
        Message message = new Message(currentSenderId, text, now);

        db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .add(message)
                .addOnSuccessListener(ref -> {
                    etMessage.setText("");
                    updateConversationMeta(text, now);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Send failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void updateConversationMeta(String lastMsg, long time) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastMessage",     lastMsg);
        updates.put("lastMessageTime", time);
        // Increment the OTHER party's unread count
        if (isSeller) {
            updates.put("unreadByCustomer", FieldValue.increment(1));
        } else {
            updates.put("unreadBySeller", FieldValue.increment(1));
        }
        db.collection("conversations").document(conversationId).update(updates);
    }

    private void markMessagesAsRead() {
        String field = isSeller ? "unreadBySeller" : "unreadByCustomer";
        db.collection("conversations").document(conversationId)
                .update(field, 0L);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
