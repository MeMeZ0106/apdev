package com.example.sanzinkstore;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.sanzinkstore.adapter.ProductAdapter;
import com.example.sanzinkstore.model.Product;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class InventoryActivity extends AppCompatActivity {

    private RecyclerView rvInventory;
    private ProductAdapter adapter;
    private ChipGroup categoryChipGroup;
    private ChipGroup subCategoryChipGroup;
    private List<Product> allProducts = new ArrayList<>();
    private List<Product> filteredList = new ArrayList<>();
    private FirebaseFirestore db;

    private String selectedTag = "Goods";
    private String selectedSubTag = "All";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        db = FirebaseFirestore.getInstance();
        rvInventory = findViewById(R.id.rvInventory);
        categoryChipGroup = findViewById(R.id.categoryChipGroup);
        subCategoryChipGroup = findViewById(R.id.subCategoryChipGroup);
        
        // Fix: Use 2 columns for mobile view to save space
        rvInventory.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 2));

        adapter = new ProductAdapter(filteredList, true, new ProductAdapter.OnProductClickListener() {
            @Override
            public void onProductClick(Product product) {
                Intent intent = new Intent(InventoryActivity.this, AdminProductActivity.class);
                intent.putExtra("PRODUCT_ID", product.getId());
                startActivity(intent);
            }

            @Override
            public void onAvailabilityChanged(Product product, boolean isAvailable) {
                if (product.getId() != null && !product.getId().startsWith("G") && !product.getId().startsWith("M") && !product.getId().startsWith("B")) {
                    db.collection("products").document(product.getId())
                            .update("available", isAvailable)
                            .addOnSuccessListener(aVoid -> Log.d("Inventory", "Availability updated"))
                            .addOnFailureListener(e -> Log.e("Inventory", "Update failed", e));
                }
            }
        });
        rvInventory.setAdapter(adapter);

        findViewById(R.id.fabAdd).setOnClickListener(v -> {
            startActivity(new Intent(this, AdminProductActivity.class));
        });

        findViewById(R.id.toolbar).setOnClickListener(v -> finish());

        setupMainTags();
    }

    private void setupMainTags() {
        String[] tags = {"Goods", "Meals", "Beverages"};
        categoryChipGroup.removeAllViews();
        
        for (String tag : tags) {
            Chip chip = new Chip(this);
            chip.setText(tag);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChipBackgroundColorResource(R.color.background);
            chip.setTextColor(ContextCompat.getColor(this, R.color.on_background));
            chip.setChipStrokeColorResource(R.color.outline);
            chip.setChipStrokeWidth(2f);

            if (tag.equals(selectedTag)) {
                chip.setChecked(true);
                chip.setChipBackgroundColorResource(R.color.primary);
                chip.setTextColor(ContextCompat.getColor(this, R.color.on_primary));
                setupSubTags(tag);
            }

            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedTag = tag;
                    selectedSubTag = "All";
                    setupSubTags(tag);
                    filterProducts();
                    chip.setChipBackgroundColorResource(R.color.primary);
                    chip.setTextColor(ContextCompat.getColor(this, R.color.on_primary));
                } else {
                    chip.setChipBackgroundColorResource(R.color.background);
                    chip.setTextColor(ContextCompat.getColor(this, R.color.on_background));
                }
            });
            categoryChipGroup.addView(chip);
        }
    }

    private void setupSubTags(String mainTag) {
        String[] subTags;
        switch (mainTag) {
            case "Goods":
                subTags = new String[]{"All", "Ramen", "Buldak", "Seaweed", "Teokbokki", "Drinks"};
                break;
            case "Meals":
                subTags = new String[]{"All", "Buldak", "K-Ramen", "Cheese Ramen", "Samyang Omolette", "Rice Meals", "Stations Specials", "Side Dish"};
                break;
            case "Beverages":
                subTags = new String[]{"All", "Milktea", "Fruit Soda", "Korean Abrica"};
                break;
            default:
                subTags = new String[]{"All"};
                break;
        }

        subCategoryChipGroup.removeAllViews();
        for (String subTag : subTags) {
            Chip chip = new Chip(this);
            chip.setText(subTag);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChipBackgroundColorResource(R.color.surface_variant);
            chip.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
            
            if (subTag.equals(selectedSubTag)) {
                chip.setChecked(true);
                chip.setChipBackgroundColorResource(R.color.primary_container);
                chip.setTextColor(ContextCompat.getColor(this, R.color.on_primary_container));
            }

            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedSubTag = subTag;
                    filterProducts();
                    chip.setChipBackgroundColorResource(R.color.primary_container);
                    chip.setTextColor(ContextCompat.getColor(this, R.color.on_primary_container));
                } else {
                    chip.setChipBackgroundColorResource(R.color.surface_variant);
                    chip.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
                }
            });
            subCategoryChipGroup.addView(chip);
        }
    }

    private void filterProducts() {
        filteredList.clear();
        for (Product p : allProducts) {
            boolean tagMatch = selectedTag.equals(p.getCategory());
            boolean subTagMatch = selectedSubTag.equals("All") || selectedSubTag.equals(p.getSubCategory());
            
            if (tagMatch && subTagMatch) {
                filteredList.add(p);
            }
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProducts();
    }

    private void loadProducts() {
        db.collection("products")
                .addSnapshotListener((value, error) -> {
                    allProducts.clear();
                    addDummyProducts();

                    if (error != null) {
                        Log.e("InventoryActivity", "Firestore listen failed.", error);
                    } else if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            Product product = doc.toObject(Product.class);
                            product.setId(doc.getId());
                            allProducts.add(product);
                        }
                    }
                    filterProducts();
                });
    }

    private void addDummyProducts() {
        // Goods
        allProducts.add(new Product("G1", "Shin Ramyun", "Classic Korean Ramen", 65.0, "", "Goods", "Ramen", true));
        allProducts.add(new Product("G2", "Buldak Original", "Spicy Chicken Noodles", 85.0, "", "Goods", "Buldak", true));
        allProducts.add(new Product("G3", "Roasted Seaweed", "Crispy snack", 35.0, "", "Goods", "Seaweed", true));
        
        // Meals
        allProducts.add(new Product("M1", "Beef Bulgogi Rice", "Tender beef meal", 145.0, "", "Meals", "Rice Meals", true));
        allProducts.add(new Product("M2", "Cheese Ramyun", "Creamy noodles", 95.0, "", "Meals", "Cheese Ramen", true));
        allProducts.add(new Product("M3", "Kimchi", "Traditional side dish", 25.0, "", "Meals", "Side Dish", true));

        // Beverages
        allProducts.add(new Product("B1", "Okinawa Milktea", "Brown sugar milktea", 95.0, "", "Beverages", "Milktea", true));
        allProducts.add(new Product("B2", "Strawberry Soda", "Fizzy berry drink", 65.0, "", "Beverages", "Fruit Soda", true));
        allProducts.add(new Product("B3", "Korean Coffee", "Iced abrica coffee", 75.0, "", "Beverages", "Korean Abrica", false));
    }
}
