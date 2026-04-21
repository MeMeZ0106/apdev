package com.example.sanzinkstore;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import androidx.recyclerview.widget.GridLayoutManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sanzinkstore.adapter.ProductAdapter;
import com.example.sanzinkstore.model.Product;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class ProductListFragment extends Fragment {

    private static final String TAG = "ProductListFragment";
    private static final String ARG_CATEGORY = "category";
    private static final String ARG_IS_ADMIN = "is_admin";
    
    private String category;
    private boolean isAdmin;
    private RecyclerView recyclerView;
    private ProductAdapter adapter;
    private ChipGroup categoryChipGroup;
    private List<Product> allProducts = new ArrayList<>();
    private List<Product> productList = new ArrayList<>();
    private FirebaseFirestore db;

    public static ProductListFragment newInstance(String category, boolean isAdmin) {
        ProductListFragment fragment = new ProductListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CATEGORY, category);
        args.putBoolean(ARG_IS_ADMIN, isAdmin);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            category = getArguments().getString(ARG_CATEGORY);
            isAdmin = getArguments().getBoolean(ARG_IS_ADMIN);
        }
        db = FirebaseFirestore.getInstance();
        productList = new ArrayList<>();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_product_list, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        categoryChipGroup = view.findViewById(R.id.categoryChipGroup);

        int spanCount = isAdmin ? 3 : 2; // Seller gets more density, Customer gets Kiosk style
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), spanCount));
        
        adapter = new ProductAdapter(productList, isAdmin, product -> {
            if (isAdmin) {
                Intent intent = new Intent(getContext(), AdminProductActivity.class);
                intent.putExtra("PRODUCT_ID", product.getId());
                startActivity(intent);
            } else {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).addToCart(product);
                }
            }
        });
        
        recyclerView.setAdapter(adapter);
        loadProducts();

        return view;
    }

    private void setupCategories(List<Product> products) {
        if (categoryChipGroup == null) return;
        categoryChipGroup.removeAllViews();
        
        Set<String> categories = new HashSet<>();
        categories.add("All");
        for (Product p : products) {
            if (p.getCategory() != null) categories.add(p.getCategory());
        }

        for (String cat : categories) {
            Chip chip = new Chip(getContext());
            chip.setText(cat);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            
            // Custom Styling for Neon Pink & Black
            chip.setChipBackgroundColorResource(R.color.black);
            chip.setTextColor(getResources().getColor(R.color.white));
            chip.setChipStrokeColorResource(R.color.neon_pink);
            chip.setChipStrokeWidth(2f);
            
            if (cat.equals("All")) chip.setChecked(true);

            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    filterProducts(cat);
                    chip.setChipBackgroundColorResource(R.color.neon_pink);
                    chip.setTextColor(getResources().getColor(R.color.black));
                } else {
                    chip.setChipBackgroundColorResource(R.color.black);
                    chip.setTextColor(getResources().getColor(R.color.white));
                }
            });
            categoryChipGroup.addView(chip);
        }
    }

    private void filterProducts(String category) {
        productList.clear();
        if (category.equals("All")) {
            productList.addAll(allProducts);
        } else {
            for (Product p : allProducts) {
                if (category.equals(p.getCategory())) {
                    productList.add(p);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void loadProducts() {
        Log.d(TAG, "Loading products");
        
        // Add Dummy Data for Visualization
        if (allProducts.isEmpty()) {
            allProducts.add(new Product("1", "Milktea Classic", "Sweet milktea", 85.0, "", "Milktea", true));
            allProducts.add(new Product("2", "Okinawa", "Brown sugar milktea", 95.0, "", "Milktea", true));
            allProducts.add(new Product("3", "Wintermelon", "Refreshing milktea", 90.0, "", "Milktea", true));
            allProducts.add(new Product("4", "Blueberry Soda", "Fizzy drink", 65.0, "", "Fruit Soda", true));
            allProducts.add(new Product("5", "Strawberry Soda", "Berry flavor", 65.0, "", "Fruit Soda", true));
            allProducts.add(new Product("6", "Green Apple Soda", "Tart and sweet", 65.0, "", "Fruit Soda", true));
            allProducts.add(new Product("7", "Fries", "Crispy potato", 45.0, "", "Food", true));
            allProducts.add(new Product("8", "Burger", "Juicy beef", 75.0, "", "Food", true));
            allProducts.add(new Product("9", "Popcorn", "Butter flavor", 35.0, "", "Snacks", true));
            allProducts.add(new Product("10", "Nachos", "Cheese dip", 55.0, "", "Snacks", true));
            setupCategories(allProducts);
            filterProducts("All");
        }

        db.collection("products")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Listen failed.", error);
                        return;
                    }

                    if (value != null) {
                        allProducts.clear();
                        for (QueryDocumentSnapshot document : value) {
                            Product product = document.toObject(Product.class);
                            product.setId(document.getId());
                            allProducts.add(product);
                        }
                        setupCategories(allProducts);
                        filterProducts("All");
                        Log.d(TAG, "Loaded " + allProducts.size() + " total products");
                    }
                });
    }
}
