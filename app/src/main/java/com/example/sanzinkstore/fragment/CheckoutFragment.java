package com.example.sanzinkstore.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.sanzinkstore.MainActivity;
import com.example.sanzinkstore.R;
import com.example.sanzinkstore.databinding.FragmentCheckoutBinding;
import com.example.sanzinkstore.model.CartItem;
import java.util.List;
import java.util.Locale;

public class CheckoutFragment extends Fragment {

    private FragmentCheckoutBinding binding;
    private List<CartItem> cartItems;
    private double totalAmount;

    public static CheckoutFragment newInstance(List<CartItem> cart, double total) {
        CheckoutFragment fragment = new CheckoutFragment();
        fragment.cartItems = cart;
        fragment.totalAmount = total;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCheckoutBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        StringBuilder summary = new StringBuilder();
        for (CartItem item : cartItems) {
            summary.append(item.getQuantity())
                    .append("x ")
                    .append(item.getProduct().getName())
                    .append(" - ₱")
                    .append(String.format(Locale.getDefault(), "%.2f", item.getTotalPrice()))
                    .append("\n");
        }
        binding.tvOrderDetails.setText(summary.toString());
        binding.tvTotalAmount.setText(String.format(Locale.getDefault(), "Total: ₱%.2f", totalAmount));

        binding.btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        binding.btnPlaceOrder.setOnClickListener(v -> {
            int selectedId = binding.rgPaymentMethods.getCheckedRadioButtonId();
            if (selectedId == -1) {
                Toast.makeText(getContext(), "Please select a payment method", Toast.LENGTH_SHORT).show();
                return;
            }

            String paymentMethod = "";
            if (selectedId == binding.rbPayOnPickup.getId()) {
                paymentMethod = "Pay on Pickup";
            } else if (selectedId == binding.rbGCash.getId()) {
                paymentMethod = "GCash";
            } else if (selectedId == binding.rbMaya.getId()) {
                paymentMethod = "Maya";
            }

            showConfirmationDialog(paymentMethod);
        });
    }

    private void showConfirmationDialog(String paymentMethod) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_custom_confirmation, null);
        android.widget.TextView titleView = dialogView.findViewById(R.id.dialogTitle);
        android.widget.TextView msgView = dialogView.findViewById(R.id.dialogMessage);

        titleView.setText("Confirm Order");
        msgView.setText(String.format(Locale.getDefault(),
                "Are you sure you want to place this order?\n\nTotal: ₱%.2f\nPayment Method: %s",
                totalAmount, paymentMethod));

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        dialogView.findViewById(R.id.btnNo).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnYes).setOnClickListener(v -> {
            dialog.dismiss();
            if (getActivity() instanceof MainActivity) {
                String methodKey = paymentMethod.toUpperCase().replace(" ", "_");
                ((MainActivity) getActivity()).completeOrder(methodKey);

                // Show success dialog for offline payment
                if ("Pay on Pickup".equals(paymentMethod)) {
                    showSuccessDialog();
                } else {
                    // For online payment, we are leaving the app anyway, so just pop the stack
                    // so when they return they are at the dashboard
                    getActivity().getSupportFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
                }
            }
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showSuccessDialog() {
        View successView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_custom_confirmation, null);
        android.widget.TextView titleView = successView.findViewById(R.id.dialogTitle);
        android.widget.TextView msgView = successView.findViewById(R.id.dialogMessage);
        com.google.android.material.button.MaterialButton btnOk = successView.findViewById(R.id.btnYes);
        com.google.android.material.button.MaterialButton btnNo = successView.findViewById(R.id.btnNo);

        titleView.setText("Order Success");
        msgView.setText("Your order has been placed successfully!\nThank you for shopping with us.");
        btnOk.setText("OK");
        btnNo.setVisibility(View.GONE);

        androidx.appcompat.app.AlertDialog successDialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
                .setView(successView)
                .create();

        btnOk.setOnClickListener(v -> {
            successDialog.dismiss();
            if (getActivity() != null) {
                // Return to product list by clearing fragment stack
                getActivity().getSupportFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
            }
        });

        if (successDialog.getWindow() != null) {
            successDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        successDialog.show();
        if (successDialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
            successDialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
