package com.example.sanzinkstore;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.sanzinkstore.databinding.ActivityLoginBinding;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final int RC_SIGN_IN = 9001;
    private ActivityLoginBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private GoogleSignInClient googleSignInClient;

    private final Handler hideHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideRunnable = this::hideSystemUI;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnContinueCustomer.setOnClickListener(v -> navigateToMain(false));
        binding.btnContinueSeller.setOnClickListener(v -> navigateToMain(true));

        setupImmersiveMode();
    }

    private void setupImmersiveMode() {
        WindowInsetsControllerCompat windowInsetsController =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        windowInsetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        hideSystemUI();

        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(visibility -> {
            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                hideHandler.removeCallbacks(hideRunnable);
                hideHandler.postDelayed(hideRunnable, 3000);
            }
        });
    }

    private void hideSystemUI() {
        WindowInsetsControllerCompat windowInsetsController =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void navigateToMain(boolean isAdmin) {
        binding.btnContinueCustomer.setEnabled(false);
        binding.btnContinueSeller.setEnabled(false);
        binding.loadingIndicator.setVisibility(View.VISIBLE);

        if (isAdmin) {
            Toast.makeText(this, "Welcome Seller!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Welcome Customer!", Toast.LENGTH_SHORT).show();
        }

        // Simulate a small delay for the loading animation to be seen
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.putExtra("IS_ADMIN", isAdmin);
            startActivity(intent);
            finish();
        }, 800);
    }
}
