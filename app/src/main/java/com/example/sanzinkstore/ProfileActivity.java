package com.example.sanzinkstore;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.sanzinkstore.databinding.ActivityProfileBinding;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.firestore.FirebaseFirestore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ProfileActivity extends AppCompatActivity {
    private ActivityProfileBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v ->
                getOnBackPressedDispatcher().onBackPressed());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        binding.tvProfileEmail.setText(user.getEmail());
        if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
            binding.tvProfileName.setText(user.getDisplayName());
        }

        // ── Detect sign-in provider ───────────────────────────────────────────────
        boolean isGoogleUser = user.getProviderData().stream()
                .anyMatch(p -> "google.com".equals(p.getProviderId()));

        binding.chipProvider.setText(isGoogleUser ? "Signed in via Google" : "Email / Password");
        binding.chipProvider.setVisibility(View.VISIBLE);

        // Show Change Password only for email/password users
        if (!isGoogleUser) {
            binding.btnChangePassword.setVisibility(View.VISIBLE);
            binding.btnChangePassword.setOnClickListener(v -> showChangePasswordDialog(user));
        }

        // ── Role from Firestore ───────────────────────────────────────────────────
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    boolean isSeller = doc.exists() &&
                            Boolean.TRUE.equals(doc.getBoolean("isSeller"));
                    binding.chipRole.setText(isSeller ? "Admin / Seller" : "Customer");
                    binding.chipRole.setVisibility(View.VISIBLE);
                });

        binding.btnEditProfile.setOnClickListener(v -> {
            // Future: edit profile name
        });
    }

    // ── Change Password dialog ────────────────────────────────────────────────────
    private void showChangePasswordDialog(FirebaseUser user) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null);
        TextInputLayout   tilCurrent = view.findViewById(R.id.tilCurrentPassword);
        TextInputLayout   tilNew     = view.findViewById(R.id.tilNewPassword);
        TextInputLayout   tilConfirm = view.findViewById(R.id.tilConfirmNewPassword);
        TextInputEditText etCurrent  = view.findViewById(R.id.etCurrentPassword);
        TextInputEditText etNew      = view.findViewById(R.id.etNewPassword);
        TextInputEditText etConfirm  = view.findViewById(R.id.etConfirmNewPassword);

        // Use setPositiveButton(null) + override after show() to PREVENT auto-dismiss on errors
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Change Password")
                .setView(view)
                .setPositiveButton("Update", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();

        // Override the positive button AFTER show() so we control dismissal
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String current = etCurrent.getText() != null ? etCurrent.getText().toString() : "";
            String newPass = etNew.getText()     != null ? etNew.getText().toString()     : "";
            String confirm = etConfirm.getText() != null ? etConfirm.getText().toString() : "";

            tilCurrent.setError(null); tilNew.setError(null); tilConfirm.setError(null);

            if (TextUtils.isEmpty(current)) { tilCurrent.setError("Required");               return; }
            if (newPass.length() < 6)       { tilNew.setError("Min. 6 characters");          return; }
            if (!newPass.equals(confirm))   { tilConfirm.setError("Passwords do not match"); return; }

            String email = user.getEmail() != null ? user.getEmail() : "";
            AuthCredential credential = EmailAuthProvider.getCredential(email, sha256(current));

            user.reauthenticate(credential)
                    .addOnSuccessListener(unused -> user.updatePassword(sha256(newPass))
                            .addOnSuccessListener(v2 -> {
                                dialog.dismiss();
                                Toast.makeText(this, "Password updated successfully!",
                                        Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Update failed: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show()))
                    .addOnFailureListener(e ->
                            tilCurrent.setError("Current password is incorrect"));
        });
    }

    private static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return text;
        }
    }
}