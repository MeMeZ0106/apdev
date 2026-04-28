package com.example.sanzinkstore;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
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
    private ChipGroup subCategoryChipGroup;
    private List<Product> allProducts = new ArrayList<>();
    private List<Product> productList = new ArrayList<>();
    private FirebaseFirestore db;

    private String selectedTag = "Goods";
    private String selectedSubTag = "All";

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
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_product_list, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        categoryChipGroup = view.findViewById(R.id.categoryChipGroup);
        subCategoryChipGroup = view.findViewById(R.id.subCategoryChipGroup);

        int spanCount = isAdmin ? 3 : 2;
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), spanCount);
        recyclerView.setLayoutManager(layoutManager);
        
        adapter = new ProductAdapter(productList, isAdmin, product -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).addToCart(product);
            }
        });
        
        recyclerView.setAdapter(adapter);
        setupMainTags();
        loadProducts();

        return view;
    }

    private void setupMainTags() {
        String[] tags = {"Goods", "Meals", "Beverages"};
        categoryChipGroup.removeAllViews();
        
        for (String tag : tags) {
            Chip chip = new Chip(getContext());
            chip.setText(tag);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChipBackgroundColorResource(R.color.background);
            chip.setTextColor(ContextCompat.getColor(getContext(), R.color.on_background));
            chip.setChipStrokeColorResource(R.color.outline);
            chip.setChipStrokeWidth(2f);

            if (tag.equals(selectedTag)) {
                chip.setChecked(true);
                chip.setChipBackgroundColorResource(R.color.primary);
                chip.setTextColor(ContextCompat.getColor(getContext(), R.color.on_primary));
                setupSubTags(tag);
            }

            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedTag = tag;
                    selectedSubTag = "All";
                    setupSubTags(tag);
                    filterProducts();
                    chip.setChipBackgroundColorResource(R.color.primary);
                    chip.setTextColor(ContextCompat.getColor(getContext(), R.color.on_primary));
                } else {
                    chip.setChipBackgroundColorResource(R.color.background);
                    chip.setTextColor(ContextCompat.getColor(getContext(), R.color.on_background));
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
            Chip chip = new Chip(getContext());
            chip.setText(subTag);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChipBackgroundColorResource(R.color.surface_variant);
            chip.setTextColor(ContextCompat.getColor(getContext(), R.color.on_surface_variant));
            
            if (subTag.equals(selectedSubTag)) {
                chip.setChecked(true);
                chip.setChipBackgroundColorResource(R.color.primary_container);
                chip.setTextColor(ContextCompat.getColor(getContext(), R.color.on_primary_container));
            }

            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedSubTag = subTag;
                    filterProducts();
                    chip.setChipBackgroundColorResource(R.color.primary_container);
                    chip.setTextColor(ContextCompat.getColor(getContext(), R.color.on_primary_container));
                } else {
                    chip.setChipBackgroundColorResource(R.color.surface_variant);
                    chip.setTextColor(ContextCompat.getColor(getContext(), R.color.on_surface_variant));
                }
            });
            subCategoryChipGroup.addView(chip);
        }
    }

    private void filterProducts() {
        productList.clear();
        for (Product p : allProducts) {
            boolean tagMatch = selectedTag.equals(p.getCategory());
            boolean subTagMatch = selectedSubTag.equals("All") || selectedSubTag.equals(p.getSubCategory());
            
            if (tagMatch && subTagMatch) {
                productList.add(p);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void loadProducts() {
        db.collection("products")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Firestore listen failed.", error);
                        return;
                    }
                    allProducts.clear();
                    if (value != null) {
                        for (QueryDocumentSnapshot document : value) {
                            Product product = document.toObject(Product.class);
                            product.setId(document.getId());
                            allProducts.add(product);
                        }
                    }
                    filterProducts();
                });
    }
}
