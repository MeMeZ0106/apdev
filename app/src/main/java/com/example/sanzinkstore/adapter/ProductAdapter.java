package com.example.sanzinkstore.adapter;

import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.sanzinkstore.R;
import com.example.sanzinkstore.databinding.ItemProductBinding;
import com.example.sanzinkstore.model.Product;
import com.squareup.picasso.Picasso;
import java.util.List;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    private final List<Product> products;
    private final boolean isAdmin;
    private final OnProductClickListener listener;

    public interface OnProductClickListener {
        void onProductClick(Product product);
    }

    public ProductAdapter(List<Product> products, boolean isAdmin, OnProductClickListener listener) {
        this.products = products;
        this.isAdmin = isAdmin;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemProductBinding binding = ItemProductBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        
        // Ensure standard margins are used for the 3-column grid to prevent overlapping or weird spacing
        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) binding.getRoot().getLayoutParams();
        if (layoutParams == null) {
            layoutParams = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        int margin = (int) (4 * parent.getContext().getResources().getDisplayMetrics().density);
        layoutParams.setMargins(margin, margin, margin, margin);
        binding.getRoot().setLayoutParams(layoutParams);

        return new ProductViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        holder.bind(products.get(position), isAdmin, listener);
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    static class ProductViewHolder extends RecyclerView.ViewHolder {
        private final ItemProductBinding binding;

        public ProductViewHolder(ItemProductBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Product product, boolean isAdmin, OnProductClickListener listener) {
            binding.productName.setText(product.getName());
            binding.productPrice.setText(String.format("₱%.2f", product.getPrice()));
            
            if (isAdmin) {
                // Seller View: Show small images, category-coded backgrounds
                binding.productImage.setVisibility(View.VISIBLE);
                // Adjust height for POS style grid (smaller)
                ViewGroup.LayoutParams params = binding.productImage.getLayoutParams();
                params.height = (int) (80 * itemView.getContext().getResources().getDisplayMetrics().density);
                binding.productImage.setLayoutParams(params);
                
                int bgColor = getCategoryColor(product.getCategory());
                binding.getRoot().setCardBackgroundColor(bgColor);
                
                // Set text colors to black for readability on light backgrounds
                binding.productName.setTextColor(Color.BLACK);
                binding.productPrice.setTextColor(Color.DKGRAY);
                
                binding.addToCartButton.setVisibility(View.GONE);
                
                if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
                    Picasso.get().load(product.getImageUrl()).placeholder(R.drawable.logo2).into(binding.productImage);
                } else {
                    binding.productImage.setImageResource(R.drawable.logo2);
                }

                binding.getRoot().setOnClickListener(v -> listener.onProductClick(product));
            } else {
                // Customer View: Show images, standard branding
                binding.productImage.setVisibility(View.VISIBLE);
                binding.getRoot().setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.black));
                binding.productName.setTextColor(Color.WHITE);
                binding.productPrice.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.neon_pink));

                float baseAlpha = 1.0f;
                if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
                    Picasso.get().load(product.getImageUrl()).placeholder(R.drawable.logo2).into(binding.productImage);
                } else {
                    binding.productImage.setImageResource(R.drawable.logo2);
                    baseAlpha = 0.5f;
                }

                if (product.isAvailable()) {
                    binding.addToCartButton.setEnabled(true);
                    binding.productImage.setColorFilter(null);
                    binding.productImage.setAlpha(baseAlpha);
                } else {
                    binding.addToCartButton.setEnabled(false);
                    ColorMatrix matrix = new ColorMatrix();
                    matrix.setSaturation(0);
                    binding.productImage.setColorFilter(new ColorMatrixColorFilter(matrix));
                    binding.productImage.setAlpha(baseAlpha * 0.6f);
                }

                binding.addToCartButton.setVisibility(View.VISIBLE);
                binding.getRoot().setOnClickListener(v -> {
                    if (product.isAvailable()) {
                        listener.onProductClick(product);
                    }
                });
                binding.addToCartButton.setOnClickListener(v -> listener.onProductClick(product));
            }
        }

        private int getCategoryColor(String category) {
            if (category == null) return ContextCompat.getColor(itemView.getContext(), R.color.cat_default);
            
            switch (category.toLowerCase()) {
                case "milktea":
                case "milk tea":
                    return ContextCompat.getColor(itemView.getContext(), R.color.cat_milktea);
                case "fruit soda":
                case "soda":
                    return ContextCompat.getColor(itemView.getContext(), R.color.cat_fruit_soda);
                case "food":
                    return ContextCompat.getColor(itemView.getContext(), R.color.cat_food);
                case "snacks":
                    return ContextCompat.getColor(itemView.getContext(), R.color.cat_snacks);
                case "deals":
                    return ContextCompat.getColor(itemView.getContext(), R.color.cat_deals);
                default:
                    return ContextCompat.getColor(itemView.getContext(), R.color.cat_default);
            }
        }
    }
}
