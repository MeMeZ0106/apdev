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
import androidx.recyclerview.widget.LinearLayoutManager;
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
    private List<Product> productList;
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
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
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

    private void loadProducts() {
        Log.d(TAG, "Loading products for category: " + category);
        
        // Listen for all products in this category
        db.collection("products")
                .whereEqualTo("category", category)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Listen failed.", error);
                        return;
                    }

                    if (value != null) {
                        productList.clear();
                        for (QueryDocumentSnapshot document : value) {
                            Product product = document.toObject(Product.class);
                            product.setId(document.getId());
                            
                            // For customers, maybe we should only show available items?
                            // But for now, let's show everything to see if visibility is fixed.
                            productList.add(product);
                        }
                        Log.d(TAG, "Loaded " + productList.size() + " products");
                        adapter.notifyDataSetChanged();
                    }
                });
    }
}
