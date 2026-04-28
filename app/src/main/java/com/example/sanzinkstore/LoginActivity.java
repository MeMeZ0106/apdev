package com.example.sanzinkstore;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
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
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG           = "LoginActivity";
    private static final int    MODE_LOGIN    = 0;
    private static final int    MODE_REGISTER = 1;

    private ActivityLoginBinding binding;
    private FirebaseAuth         auth;
    private FirebaseFirestore    db;
    private GoogleSignInClient   googleSignInClient;
    private BottomSheetDialog    currentSheet;

    // Track whether the Google launcher was triggered from Login or Register
    private int    googleMode   = MODE_LOGIN;
    private String pendingName  = "";
    private String pendingEmail = "";

    // ── Google Sign-In result handler ────────────────────────────────────────────
    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Task<GoogleSignInAccount> task =
                        GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                try {
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    handleGoogleCredential(account);
                } catch (ApiException e) {
                    setLoading(false);
                    Toast.makeText(this, "Google sign-in failed: " + e.getStatusCode(),
                            Toast.LENGTH_SHORT).show();
                }
            }
    );

    // ─────────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        // Auto-login: skip screen if session exists
        FirebaseUser current = auth.getCurrentUser();
        if (current != null) {
            checkRoleAndNavigate(current);
            return;
        }

        // Google Sign-In client
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .requestProfile()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // ── Buttons ──────────────────────────────────────────────────────────────
        binding.btnGuest.setOnClickListener(v -> navigateToMain(false, true));
        binding.btnLogin.setOnClickListener(v -> showLoginSheet());
        binding.btnRegister.setOnClickListener(v -> showRegisterSheet());

        binding.btnAboutUs.setOnClickListener(v ->
                showInfoDialog(getString(R.string.about_us_title),
                               getString(R.string.about_us_content)));

        binding.btnPrivacyPolicy.setOnClickListener(v ->
                showInfoDialog(getString(R.string.privacy_policy_title),
                               getString(R.string.privacy_policy_content)));

        setupImmersiveMode();
    }

    // ─── Login Bottom Sheet ───────────────────────────────────────────────────────

    private void showLoginSheet() {
        currentSheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_login, null);
        currentSheet.setContentView(view);

        TextInputLayout   tilEmail    = view.findViewById(R.id.tilEmail);
        TextInputLayout   tilPassword = view.findViewById(R.id.tilPassword);
        TextInputEditText etEmail     = view.findViewById(R.id.etEmail);
        TextInputEditText etPassword  = view.findViewById(R.id.etPassword);

        view.findViewById(R.id.btnSignIn).setOnClickListener(v -> {
            String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
            String pass  = etPassword.getText() != null ? etPassword.getText().toString() : "";

            tilEmail.setError(null);
            tilPassword.setError(null);

            boolean valid = true;
            if (TextUtils.isEmpty(email))  { tilEmail.setError("Email is required");    valid = false; }
            if (TextUtils.isEmpty(pass))   { tilPassword.setError("Password is required"); valid = false; }
            if (!valid) return;

            currentSheet.dismiss();
            setLoading(true);
            signInWithEmail(email, sha256(pass));
        });

        view.findViewById(R.id.btnGoogleSignIn).setOnClickListener(v -> {
            googleMode   = MODE_LOGIN;
            pendingName  = "";
            pendingEmail = "";
            currentSheet.dismiss();
            setLoading(true);
            googleSignInClient.signOut().addOnCompleteListener(t ->
                    googleSignInLauncher.launch(googleSignInClient.getSignInIntent()));
        });

        currentSheet.show();
    }

    // ─── Register Bottom Sheet ────────────────────────────────────────────────────

    private void showRegisterSheet() {
        currentSheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_register, null);
        currentSheet.setContentView(view);

        TextInputLayout   tilName    = view.findViewById(R.id.tilName);
        TextInputLayout   tilEmail   = view.findViewById(R.id.tilEmail);
        TextInputLayout   tilPass    = view.findViewById(R.id.tilPassword);
        TextInputLayout   tilConfirm = view.findViewById(R.id.tilConfirmPassword);
        TextInputEditText etName     = view.findViewById(R.id.etName);
        TextInputEditText etEmail    = view.findViewById(R.id.etEmail);
        TextInputEditText etPass     = view.findViewById(R.id.etPassword);
        TextInputEditText etConfirm  = view.findViewById(R.id.etConfirmPassword);

        view.findViewById(R.id.btnCreateAccount).setOnClickListener(v -> {
            String name    = etName.getText()    != null ? etName.getText().toString().trim()    : "";
            String email   = etEmail.getText()   != null ? etEmail.getText().toString().trim()   : "";
            String pass    = etPass.getText()    != null ? etPass.getText().toString()           : "";
            String confirm = etConfirm.getText() != null ? etConfirm.getText().toString()        : "";

            tilName.setError(null); tilEmail.setError(null);
            tilPass.setError(null); tilConfirm.setError(null);

            boolean valid = true;
            if (TextUtils.isEmpty(name))    { tilName.setError("Full name is required");    valid = false; }
            if (TextUtils.isEmpty(email))   { tilEmail.setError("Email is required");       valid = false; }
            if (pass.length() < 6)          { tilPass.setError("Min. 6 characters");        valid = false; }
            if (!pass.equals(confirm))      { tilConfirm.setError("Passwords do not match"); valid = false; }
            if (!valid) return;

            currentSheet.dismiss();
            setLoading(true);
            registerWithEmail(name, email, sha256(pass));
        });

        view.findViewById(R.id.btnGoogleRegister).setOnClickListener(v -> {
            googleMode   = MODE_REGISTER;
            pendingName  = "";
            pendingEmail = "";
            currentSheet.dismiss();
            setLoading(true);
            googleSignInClient.signOut().addOnCompleteListener(t ->
                    googleSignInLauncher.launch(googleSignInClient.getSignInIntent()));
        });

        currentSheet.show();
    }

    // ─── Email / Password auth ────────────────────────────────────────────────────

    private void signInWithEmail(String email, String hashedPassword) {
        auth.signInWithEmailAndPassword(email, hashedPassword)
                .addOnSuccessListener(r -> checkRoleAndNavigate(r.getUser()))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Login failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void registerWithEmail(String name, String email, String hashedPassword) {
        auth.createUserWithEmailAndPassword(email, hashedPassword)
                .addOnSuccessListener(r -> saveUserProfile(r.getUser(), name, email, "email"))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Registration failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    // ─── Google auth ──────────────────────────────────────────────────────────────

    private void handleGoogleCredential(GoogleSignInAccount account) {
        pendingName  = account.getDisplayName() != null ? account.getDisplayName() : "";
        pendingEmail = account.getEmail()        != null ? account.getEmail()        : "";

        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        auth.signInWithCredential(credential)
                .addOnSuccessListener(r -> {
                    FirebaseUser user = r.getUser();
                    if (googleMode == MODE_REGISTER) {
                        saveUserProfile(user, pendingName, pendingEmail, "google");
                    } else {
                        checkRoleAndNavigate(user);
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Google auth failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    // ─── Firestore ────────────────────────────────────────────────────────────────

    /**
     * Creates / overwrites the user profile in Firestore.
     * isSeller defaults to false — only changed via admin tools.
     */
    private void saveUserProfile(FirebaseUser user, String name, String email, String provider) {
        if (user == null) { setLoading(false); return; }

        Map<String, Object> data = new HashMap<>();
        data.put("displayName", !TextUtils.isEmpty(name)  ? name  : (user.getDisplayName() != null ? user.getDisplayName() : ""));
        data.put("email",       !TextUtils.isEmpty(email) ? email : (user.getEmail() != null ? user.getEmail() : ""));
        data.put("isSeller",    false);
        data.put("provider",    provider);
        data.put("createdAt",   new Date());

        db.collection("users").document(user.getUid())
                .set(data)
                .addOnCompleteListener(t -> navigateToMain(false, false));
    }

    /**
     * Reads users/{uid}.isSeller from Firestore to decide the navigation target.
     */
    private void checkRoleAndNavigate(FirebaseUser user) {
        if (user == null) { setLoading(false); return; }

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        // First login via Google without prior registration — create profile
                        saveUserProfile(user,
                                user.getDisplayName() != null ? user.getDisplayName() : "",
                                user.getEmail() != null ? user.getEmail() : "",
                                "google");
                        return;
                    }
                    boolean isSeller = Boolean.TRUE.equals(doc.getBoolean("isSeller"));
                    navigateToMain(isSeller, false);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Role check failed: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    // ─── Navigation ───────────────────────────────────────────────────────────────

    private void navigateToMain(boolean isAdmin, boolean isGuest) {
        setLoading(false);
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("IS_ADMIN", isAdmin);
        intent.putExtra("IS_GUEST", isGuest);
        startActivity(intent);
        finish();
    }

    // ─── Utility ──────────────────────────────────────────────────────────────────

    /** Returns the SHA-256 hex digest of the given text. */
    private static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return text; // should never happen on Android
        }
    }

    private void showInfoDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Close", null)
                .show();
    }

    private void setLoading(boolean loading) {
        binding.btnGuest.setEnabled(!loading);
        binding.btnLogin.setEnabled(!loading);
        binding.btnRegister.setEnabled(!loading);
        binding.loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    // ─── Immersive mode ───────────────────────────────────────────────────────────

    private void setupImmersiveMode() {
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setSystemBarsBehavior(
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
