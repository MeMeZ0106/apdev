package com.example.sanzinkstore;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.sanzinkstore.model.CartItem;
import com.example.sanzinkstore.model.Order;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogsActivity extends AppCompatActivity {

    private RecyclerView rvLogs;
    private FirebaseFirestore db;
    private List<Order> orders = new ArrayList<>();
    private OrderAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logs);

        db = FirebaseFirestore.getInstance();
        rvLogs = findViewById(R.id.rvLogs);
        rvLogs.setLayoutManager(new LinearLayoutManager(this));
        
        adapter = new OrderAdapter(orders);
        rvLogs.setAdapter(adapter);

        findViewById(R.id.toolbar).setOnClickListener(v -> finish());
        
        loadLogs();
    }

    private void loadLogs() {
        db.collection("orders")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    orders.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Order order = doc.toObject(Order.class);
                        orders.add(order);
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private static class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {
        private final List<Order> orders;

        public OrderAdapter(List<Order> orders) {
            this.orders = orders;
        }

        @NonNull
        @Override
        public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order, parent, false);
            return new OrderViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
            Order order = orders.get(position);
            holder.tvOrderId.setText("Order: " + order.getTimestamp());
            holder.tvTimestamp.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(order.getTimestamp())));
            
            StringBuilder itemsStr = new StringBuilder();
            for (CartItem item : order.getItems()) {
                itemsStr.append(item.getQuantity()).append("x ").append(item.getProduct().getName()).append(", ");
            }
            if (itemsStr.length() > 2) itemsStr.setLength(itemsStr.length() - 2);
            holder.tvItems.setText(itemsStr.toString());
            
            holder.tvTotal.setText(String.format("PHP %.2f", order.getTotalAmount()));
            holder.tvStatus.setText(order.isPaid() ? "PAID" : "UNPAID");
            holder.tvStatus.setTextColor(order.isPaid() ? 0xFF4CAF50 : 0xFFF44336);
        }

        @Override
        public int getItemCount() {
            return orders.size();
        }

        static class OrderViewHolder extends RecyclerView.ViewHolder {
            TextView tvOrderId, tvTimestamp, tvItems, tvTotal, tvStatus;

            public OrderViewHolder(@NonNull View itemView) {
                super(itemView);
                tvOrderId = itemView.findViewById(R.id.tvOrderId);
                tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
                tvItems = itemView.findViewById(R.id.tvItems);
                tvTotal = itemView.findViewById(R.id.tvTotal);
                tvStatus = itemView.findViewById(R.id.tvStatus);
            }
        }
    }
}