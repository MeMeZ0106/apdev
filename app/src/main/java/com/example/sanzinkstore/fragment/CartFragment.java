package com.example.sanzinkstore.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.sanzinkstore.MainActivity;
import com.example.sanzinkstore.adapter.CartAdapter;
import com.example.sanzinkstore.databinding.FragmentCartBinding;
import com.example.sanzinkstore.model.CartItem;
import java.util.List;
import java.util.Locale;

public class CartFragment extends Fragment {

    private FragmentCartBinding binding;
    private List<CartItem> cartItems;
    private CartAdapter adapter;

    public static CartFragment newInstance(List<CartItem> cart) {
        CartFragment fragment = new CartFragment();
        fragment.cartItems = cart;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCartBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.rvCartItems.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CartAdapter(cartItems, new CartAdapter.OnCartChangeListener() {
            @Override
            public void onQuantityChanged(int position, int newQuantity) {
                cartItems.get(position).setQuantity(newQuantity);
                updateUI();
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).onCartUpdated();
                }
            }

            @Override
            public void onItemRemoved(int position) {
                cartItems.remove(position);
                adapter.notifyItemRemoved(position);
                updateUI();
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).onCartUpdated();
                }
            }
        });
        binding.rvCartItems.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        binding.btnCheckout.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateToCheckout();
            }
        });

        updateUI();
    }

    private void updateUI() {
        double total = 0;
        for (CartItem item : cartItems) {
            total += item.getTotalPrice();
        }
        binding.tvTotalPrice.setText(String.format(Locale.getDefault(), "₱%.2f", total));
        adapter.notifyDataSetChanged();
        
        if (cartItems.isEmpty()) {
            binding.btnCheckout.setEnabled(false);
            binding.btnCheckout.setAlpha(0.5f);
        } else {
            binding.btnCheckout.setEnabled(true);
            binding.btnCheckout.setAlpha(1.0f);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
