package com.example.sanzinkstore;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.sanzinkstore.adapter.ProductAdapter;
import com.example.sanzinkstore.model.Product;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class InventoryActivity extends AppCompatActivity {

    private RecyclerView rvInventory;
    private ProductAdapter adapter;
    private List<Product> productList = new ArrayList<>();
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        db = FirebaseFirestore.getInstance();
        rvInventory = findViewById(R.id.rvInventory);
        rvInventory.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ProductAdapter(productList, true, product -> {
            Intent intent = new Intent(this, AdminProductActivity.class);
            intent.putExtra("PRODUCT_ID", product.getId());
            startActivity(intent);
        });
        rvInventory.setAdapter(adapter);

        findViewById(R.id.fabAdd).setOnClickListener(v -> {
            startActivity(new Intent(this, AdminProductActivity.class));
        });

        findViewById(R.id.toolbar).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProducts();
    }

    private void loadProducts() {
        db.collection("products")
                .addSnapshotListener((value, error) -> {
                    productList.clear();
                    // Always add dummy products first so the screen is never empty
                    addDummyProducts();

                    if (error != null) {
                        Log.e("InventoryActivity", "Firestore listen failed.", error);
                    } else if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            Product product = doc.toObject(Product.class);
                            product.setId(doc.getId());
                            productList.add(product);
                        }
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private void addDummyProducts() {
        productList.add(new Product("1", "Milktea Classic", "Sweet milktea", 85.0, "", "Milktea", true));
        productList.add(new Product("2", "Okinawa", "Brown sugar milktea", 95.0, "", "Milktea", true));
        productList.add(new Product("3", "Wintermelon", "Refreshing milktea", 90.0, "", "Milktea", true));
        productList.add(new Product("4", "Blueberry Soda", "Fizzy drink", 65.0, "", "Fruit Soda", true));
        productList.add(new Product("5", "Strawberry Soda", "Berry flavor", 65.0, "", "Fruit Soda", true));
        productList.add(new Product("6", "Green Apple Soda", "Tart and sweet", 65.0, "", "Fruit Soda", true));
        productList.add(new Product("7", "Fries", "Crispy potato", 45.0, "", "Food", true));
        productList.add(new Product("8", "Burger", "Juicy beef", 75.0, "", "Food", true));
        productList.add(new Product("9", "Popcorn", "Butter flavor", 35.0, "", "Snacks", true));
        productList.add(new Product("10", "Nachos", "Cheese dip", 55.0, "", "Snacks", true));
        productList.add(new Product("11", "Duo Deal", "Burger and Fries", 110.0, "", "Deals", true));
    }
}