package com.example.sanzinkstore;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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

public class LogsActivity extends BaseDrawerActivity {

    private static final String TAG = "LogsActivity";
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

        Toolbar toolbar = findViewById(R.id.toolbar);
        setupDrawer(toolbar, R.id.nav_logs);

        adapter = new OrderAdapter(orders);
        rvLogs.setAdapter(adapter);

        loadLogs();
    }

    private void loadLogs() {
        Log.d(TAG, "Loading transaction logs...");
        db.collection("orders")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error fetching logs", error);
                        Toast.makeText(this, "Failed to load logs: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (value != null) {
                        orders.clear();
                        Log.d(TAG, "Logs received: " + value.size() + " orders");
                        for (QueryDocumentSnapshot doc : value) {
                            try {
                                Order order = doc.toObject(Order.class);
                                orders.add(order);
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing order: " + doc.getId(), e);
                            }
                        }
                        adapter.notifyDataSetChanged();
                        
                        if (orders.isEmpty()) {
                            Toast.makeText(this, "No transaction history found.", Toast.LENGTH_SHORT).show();
                        }
                    }
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
            holder.tvOrderId.setText("Order Date: " + new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date(order.getTimestamp())));
            holder.tvTimestamp.setText("Time: " + new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(order.getTimestamp())));

            // Show customer info only for online (non-POS) orders
            String name  = order.getCustomerName()  != null ? order.getCustomerName().trim()  : "";
            String email = order.getCustomerEmail() != null ? order.getCustomerEmail().trim() : "";
            if (!name.isEmpty() || !email.isEmpty()) {
                String customerDisplay = "Customer: ";
                if (!name.isEmpty()) customerDisplay += name;
                if (!email.isEmpty()) customerDisplay += (!name.isEmpty() ? "  •  " : "") + email;
                holder.tvCustomer.setText(customerDisplay);
                holder.tvCustomer.setVisibility(View.VISIBLE);
            } else {
                holder.tvCustomer.setVisibility(View.GONE);
            }

            StringBuilder itemsStr = new StringBuilder();
            if (order.getItems() != null) {
                for (CartItem item : order.getItems()) {
                    if (item.getProduct() != null) {
                        itemsStr.append(item.getQuantity()).append("x ").append(item.getProduct().getName()).append(", ");
                    }
                }
            }
            if (itemsStr.length() > 2) itemsStr.setLength(itemsStr.length() - 2);
            else itemsStr.append("No items listed");
            
            holder.tvItems.setText(itemsStr.toString());
            
            holder.tvTotal.setText(String.format(Locale.getDefault(), "Total: PHP %.2f", order.getTotalAmount()));
            
            String status = order.getStatus() != null ? order.getStatus() : "PENDING";
            boolean isPaid = order.isPaid();
            
            holder.tvStatus.setText(status + (isPaid ? " (PAID)" : " (UNPAID)"));
            
            if (isPaid) {
                holder.tvStatus.setTextColor(0xFF4CAF50); // Green
            } else if ("CANCELLED".equals(status)) {
                holder.tvStatus.setTextColor(0xFFF44336); // Red
            } else {
                holder.tvStatus.setTextColor(0xFFFF9800); // Orange
            }
        }

        @Override
        public int getItemCount() {
            return orders.size();
        }

        static class OrderViewHolder extends RecyclerView.ViewHolder {
            TextView tvOrderId, tvTimestamp, tvCustomer, tvItems, tvTotal, tvStatus;

            public OrderViewHolder(@NonNull View itemView) {
                super(itemView);
                tvOrderId   = itemView.findViewById(R.id.tvOrderId);
                tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
                tvCustomer  = itemView.findViewById(R.id.tvCustomer);
                tvItems     = itemView.findViewById(R.id.tvItems);
                tvTotal     = itemView.findViewById(R.id.tvTotal);
                tvStatus    = itemView.findViewById(R.id.tvStatus);
            }
        }
    }
}