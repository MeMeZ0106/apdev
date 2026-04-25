/*package com.example.sanzinkstore;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
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

public class LoginActivityCopy extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final int RC_SIGN_IN = 9001;
    private ActivityLoginBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private GoogleSignInClient googleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        binding.googleSignInButton.setOnClickListener(v -> signIn());
        binding.adminLoginButton.setOnClickListener(v -> adminLogin());

        // Check if already logged in and navigate accordingly
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            checkUserRoleAndNavigate(currentUser);
        }
    }

    private void signIn() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private void adminLogin() {
        String inputEmail = binding.adminEmail.getText().toString().trim();
        String password = binding.adminPassword.getText().toString().trim();

        if (TextUtils.isEmpty(inputEmail) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please enter Admin and password", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check for hardcoded "Admin" keyword
        if (inputEmail.equalsIgnoreCase("Admin") && password.equals("password")) {
            Log.d(TAG, "Master Admin credentials detected");
            // If already logged in as master, just go. Otherwise, try to login or bootstrap.
            FirebaseUser user = auth.getCurrentUser();
            if (user != null && "admin@sanzinkstore.com".equals(user.getEmail())) {
                navigateToMain(true);
            } else {
                auth.signInWithEmailAndPassword("admin@sanzinkstore.com", "password")
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            saveAdminToFirestore(auth.getCurrentUser(), "admin@sanzinkstore.com");
                        } else {
                            bootstrapAdmin("admin@sanzinkstore.com", "password");
                        }
                    });
            }
            return;
        }

        // Normal email/pass login
        auth.signInWithEmailAndPassword(inputEmail, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        checkUserRoleAndNavigate(auth.getCurrentUser());
                    } else {
                        Toast.makeText(this, "Login failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void bootstrapAdmin(String email, String password) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        saveAdminToFirestore(auth.getCurrentUser(), email);
                    } else {
                        // If already exists but login failed above for some reason
                        Toast.makeText(this, "Admin creation failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveAdminToFirestore(FirebaseUser user, String email) {
        if (user == null) return;
        Map<String, Object> adminData = new HashMap<>();
        adminData.put("role", "super_admin");
        adminData.put("email", email);

        db.collection("admins").document(user.getUid()).set(adminData)
                .addOnSuccessListener(aVoid -> navigateToMain(true))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firestore write failed, but granting access based on credentials", e);
                    navigateToMain(true);
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Toast.makeText(this, "Google sign in failed: " + e.getStatusCode(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        checkUserRoleAndNavigate(auth.getCurrentUser());
                    } else {
                        Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkUserRoleAndNavigate(FirebaseUser user) {
        if (user == null) return;

        String email = user.getEmail();
        if (email != null && email.equals("admin@sanzinkstore.com")) {
            navigateToMain(true);
            return;
        }

        db.collection("admins").document(user.getUid()).get()
                .addOnCompleteListener(task -> {
                    boolean isAdmin = task.isSuccessful() && task.getResult() != null && task.getResult().exists();
                    navigateToMain(isAdmin);
                });
    }

    private void navigateToMain(boolean isAdmin) {
        if (isAdmin) {
            Toast.makeText(this, "Welcome Admin!", Toast.LENGTH_SHORT).show();
        }
        Intent intent = new Intent(LoginActivityCopy.this, MainActivity.class);
        intent.putExtra("IS_ADMIN", isAdmin);
        startActivity(intent);
        finish();
    }
}
*/