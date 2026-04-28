package com.example.sanzinkstore;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.sanzinkstore.databinding.ActivityLoginBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    // Hardcoded seller credentials (shorthand for quick login)
    private static final String ADMIN_EMAIL    = "admin@sanzinkstore.com";
    private static final String ADMIN_KEYWORD  = "Admin";

    private ActivityLoginBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private GoogleSignInClient googleSignInClient;

    // ── Modern replacement for deprecated startActivityForResult ────────────
    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Task<GoogleSignInAccount> task =
                        GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                try {
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    firebaseAuthWithGoogle(account.getIdToken());
                } catch (ApiException e) {
                    setLoading(false);
                    Toast.makeText(this, "Google sign-in failed: " + e.getStatusCode(),
                            Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        // Auto-login: if a session already exists, skip the login screen
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            checkUserRoleAndNavigate(currentUser);
            return;  // don't set up UI listeners — navigating away anyway
        }

        // Set up Google Sign-In for customers
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // Customer button → Google Sign-In
        binding.googleSignInButton.setOnClickListener(v -> {
            setLoading(true);
            googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
        });

        // Seller button → email / password login
        binding.adminLoginButton.setOnClickListener(v -> adminLogin());

        setupImmersiveMode();
    }

    // ── Seller login ─────────────────────────────────────────────────────────

    private void adminLogin() {
        String inputEmail = binding.adminEmail.getText() != null
                ? binding.adminEmail.getText().toString().trim() : "";
        String password = binding.adminPassword.getText() != null
                ? binding.adminPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(inputEmail) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        // Shorthand: typing "Admin" + "password" maps to the real admin email
        String emailToUse = inputEmail.equalsIgnoreCase(ADMIN_KEYWORD)
                ? ADMIN_EMAIL : inputEmail;

        auth.signInWithEmailAndPassword(emailToUse, password)
                .addOnSuccessListener(result -> {
                    saveAdminToFirestore(result.getUser(), emailToUse);
                })
                .addOnFailureListener(e -> {
                    // Account might not exist yet → create it (first-run bootstrap)
                    if (emailToUse.equals(ADMIN_EMAIL)) {
                        bootstrapAdmin(emailToUse, password);
                    } else {
                        setLoading(false);
                        Toast.makeText(this, "Login failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** Creates the admin Firebase account on first run. */
    private void bootstrapAdmin(String email, String password) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result ->
                        saveAdminToFirestore(result.getUser(), email))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Admin setup failed: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    /** Ensures the admin document exists in Firestore, then navigates. */
    private void saveAdminToFirestore(FirebaseUser user, String email) {
        if (user == null) { setLoading(false); return; }

        Map<String, Object> adminData = new HashMap<>();
        adminData.put("role",  "super_admin");
        adminData.put("email", email);

        db.collection("admins").document(user.getUid()).set(adminData)
                .addOnCompleteListener(task -> {
                    // Navigate regardless — Firestore might be offline
                    navigateToMain(true);
                });
    }

    // ── Google Sign-In (Customer) ─────────────────────────────────────────────

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnSuccessListener(result ->
                        checkUserRoleAndNavigate(result.getUser()))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Authentication failed: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    // ── Role detection ────────────────────────────────────────────────────────

    /**
     * Checks Firestore's `admins` collection.
     * If the user's UID is there (or their email is the master admin), they go to seller view.
     */
    private void checkUserRoleAndNavigate(FirebaseUser user) {
        if (user == null) { setLoading(false); return; }

        // Fast-path: known admin email
        if (ADMIN_EMAIL.equals(user.getEmail())) {
            navigateToMain(true);
            return;
        }

        // Firestore lookup for any other email-based admins
        db.collection("admins").document(user.getUid()).get()
                .addOnCompleteListener(task -> {
                    boolean isAdmin = task.isSuccessful()
                            && task.getResult() != null
                            && task.getResult().exists();
                    navigateToMain(isAdmin);
                });
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void navigateToMain(boolean isAdmin) {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.putExtra("IS_ADMIN", isAdmin);
        startActivity(intent);
        finish();
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void setLoading(boolean loading) {
        binding.googleSignInButton.setEnabled(!loading);
        binding.adminLoginButton.setEnabled(!loading);
        binding.loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void setupImmersiveMode() {
        WindowInsetsControllerCompat c =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        c.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        hideSystemUI();
    }

    private void hideSystemUI() {
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .hide(WindowInsetsCompat.Type.systemBars());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }
}
