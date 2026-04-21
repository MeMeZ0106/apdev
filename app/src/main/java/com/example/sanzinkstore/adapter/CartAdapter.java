package com.example.sanzinkstore.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.sanzinkstore.databinding.ItemCartBinding;
import com.example.sanzinkstore.model.CartItem;
import java.util.List;
import java.util.Locale;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.CartViewHolder> {

    private final List<CartItem> cartItems;
    private final OnCartChangeListener listener;

    public interface OnCartChangeListener {
        void onQuantityChanged(int position, int newQuantity);
        void onItemRemoved(int position);
    }

    public CartAdapter(List<CartItem> cartItems, OnCartChangeListener listener) {
        this.cartItems = cartItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCartBinding binding = ItemCartBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new CartViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CartViewHolder holder, int position) {
        holder.bind(cartItems.get(position), position, listener);
    }

    @Override
    public int getItemCount() {
        return cartItems.size();
    }

    static class CartViewHolder extends RecyclerView.ViewHolder {
        private final ItemCartBinding binding;

        public CartViewHolder(ItemCartBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(CartItem item, int position, OnCartChangeListener listener) {
            binding.cartProductName.setText(item.getProduct().getName());
            binding.cartProductPrice.setText(String.format(Locale.getDefault(), "₱%.2f", item.getTotalPrice()));
            binding.cartQuantity.setText(String.valueOf(item.getQuantity()));

            binding.btnPlus.setOnClickListener(v -> {
                listener.onQuantityChanged(position, item.getQuantity() + 1);
            });

            binding.btnMinus.setOnClickListener(v -> {
                if (item.getQuantity() > 1) {
                    listener.onQuantityChanged(position, item.getQuantity() - 1);
                } else {
                    listener.onItemRemoved(position);
                }
            });
        }
    }
}
