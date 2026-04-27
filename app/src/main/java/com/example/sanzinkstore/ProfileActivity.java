package com.example.sanzinkstore;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.sanzinkstore.databinding.ActivityProfileBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ProfileActivity extends AppCompatActivity {
    private ActivityProfileBinding binding;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();

        if (user != null) {
            binding.tvProfileEmail.setText(user.getEmail());
            if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                binding.tvProfileName.setText(user.getDisplayName());
            }
        }

        binding.btnEditProfile.setOnClickListener(v -> {
            // Implementation for editing profile can be added later
        });
    }
}