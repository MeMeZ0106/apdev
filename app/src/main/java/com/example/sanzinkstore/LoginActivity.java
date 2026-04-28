package com.example.sanzinkstore;

import android.content.Intent;
import android.net.Uri;
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
import com.google.firebase.auth.ActionCodeSettings;
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
import java.util.Random;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG           = "LoginActivity";
    private static final int    MODE_LOGIN    = 0;
    private static final int    MODE_REGISTER = 1;

    private ActivityLoginBinding binding;
    private FirebaseAuth         auth;
    private FirebaseFirestore    db;
    private GoogleSignInClient   googleSignInClient;
    private BottomSheetDialog    currentSheet;

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

        // ── Handle password-reset deep link (sanzinkstore://reset_password?oobCode=...) ──
        Uri deepLink = getIntent().getData();
        if (deepLink != null
                && "sanzinkstore".equals(deepLink.getScheme())
                && "reset_password".equals(deepLink.getHost())) {
            String oobCode = deepLink.getQueryParameter("oobCode");
            handlePasswordResetDeepLink(oobCode);
            return;
        }

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
            if (TextUtils.isEmpty(email)) { tilEmail.setError("Email is required");       valid = false; }
            if (TextUtils.isEmpty(pass))  { tilPassword.setError("Password is required"); valid = false; }
            if (!valid) return;

            currentSheet.dismiss();
            setLoading(true);
            signInWithEmail(email, sha256(pass));
        });

        view.findViewById(R.id.btnForgotPassword).setOnClickListener(v -> {
            String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
            currentSheet.dismiss();
            showForgotPasswordStep1(email);
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

        TextInputLayout   tilFirstName = view.findViewById(R.id.tilFirstName);
        TextInputLayout   tilLastName  = view.findViewById(R.id.tilLastName);
        TextInputLayout   tilEmail     = view.findViewById(R.id.tilEmail);
        TextInputLayout   tilPass      = view.findViewById(R.id.tilPassword);
        TextInputLayout   tilConfirm   = view.findViewById(R.id.tilConfirmPassword);
        TextInputEditText etFirstName  = view.findViewById(R.id.etFirstName);
        TextInputEditText etLastName   = view.findViewById(R.id.etLastName);
        TextInputEditText etEmail      = view.findViewById(R.id.etEmail);
        TextInputEditText etPass       = view.findViewById(R.id.etPassword);
        TextInputEditText etConfirm    = view.findViewById(R.id.etConfirmPassword);

        view.findViewById(R.id.btnCreateAccount).setOnClickListener(v -> {
            String firstName = etFirstName.getText() != null ? etFirstName.getText().toString().trim() : "";
            String lastName  = etLastName.getText()  != null ? etLastName.getText().toString().trim()  : "";
            String email     = etEmail.getText()     != null ? etEmail.getText().toString().trim()     : "";
            String pass      = etPass.getText()      != null ? etPass.getText().toString()             : "";
            String confirm   = etConfirm.getText()   != null ? etConfirm.getText().toString()          : "";

            tilFirstName.setError(null); tilLastName.setError(null);
            tilEmail.setError(null);
            tilPass.setError(null); tilConfirm.setError(null);

            boolean valid = true;
            if (TextUtils.isEmpty(firstName)) { tilFirstName.setError("Required");            valid = false; }
            if (TextUtils.isEmpty(lastName))  { tilLastName.setError("Required");             valid = false; }
            if (TextUtils.isEmpty(email))     { tilEmail.setError("Email is required");       valid = false; }
            if (pass.length() < 6)            { tilPass.setError("Min. 6 characters");        valid = false; }
            if (!pass.equals(confirm))        { tilConfirm.setError("Passwords do not match"); valid = false; }
            if (!valid) return;

            currentSheet.dismiss();
            setLoading(true);
            registerWithEmail(firstName + " " + lastName, email, sha256(pass));
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

    // ─── Forgot Password — Step 1: enter email + send code ───────────────────────

    private void showForgotPasswordStep1(String prefillEmail) {
        android.widget.EditText etEmail = new android.widget.EditText(this);
        etEmail.setHint("Email address");
        etEmail.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        if (!TextUtils.isEmpty(prefillEmail)) etEmail.setText(prefillEmail);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        etEmail.setPadding(pad, pad, pad, pad);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Forgot Password")
                .setMessage("Enter your email. We'll send a 6-digit verification code.")
                .setView(etEmail)
                .setPositiveButton("Send Code", null)   // set below to prevent auto-dismiss
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            if (TextUtils.isEmpty(email)) {
                etEmail.setError("Email is required");
                return;
            }
            dialog.dismiss();
            sendForgotPasswordCode(email);
        });
    }

    /**
     * Generates a 6-digit OTP, stores it in Firestore, then fires Firebase's
     * password-reset email.  The email link redirects to
     * sanzinkstore://reset_password?oobCode=XXX  (intercepted in onCreate).
     * After verifying the OTP the user sets a new password via confirmPasswordReset.
     */
    private void sendForgotPasswordCode(String email) {
        setLoading(true);

        // 1. Generate & store OTP
        String otp = String.format("%06d", new Random().nextInt(1_000_000));
        long   expiresAt = System.currentTimeMillis() + 15 * 60 * 1000L; // 15 min

        Map<String, Object> otpData = new HashMap<>();
        otpData.put("otp", otp);
        otpData.put("expiresAt", expiresAt);
        otpData.put("email", email);

        db.collection("passwordResets").document(email)
                .set(otpData)
                .addOnSuccessListener(unused -> {
                    // 2. Build ActionCodeSettings so the Firebase reset link redirects into the app
                    ActionCodeSettings settings = ActionCodeSettings.newBuilder()
                            .setUrl("https://sanzinkstore-1184b.firebaseapp.com/reset" +
                                    "?otp=" + otp)   // OTP survives as continueUrl param
                            .setHandleCodeInApp(true)
                            .setAndroidPackageName(
                                    "com.example.sanzinkstore",
                                    /*installIfNotAvailable=*/ true,
                                    /*minimumVersion=*/ "1")
                            .build();

                    // 3. Firebase sends the reset email
                    auth.sendPasswordResetEmail(email, settings)
                            .addOnSuccessListener(v -> {
                                setLoading(false);
                                // 4. Show OTP entry dialog
                                showForgotPasswordStep2(email, otp);
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                Toast.makeText(this,
                                        "Could not send code: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // ─── Forgot Password — Step 2: enter code ────────────────────────────────────

    private void showForgotPasswordStep2(String email, String expectedOtp) {
        android.widget.EditText etCode = new android.widget.EditText(this);
        etCode.setHint("6-digit code");
        etCode.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        etCode.setPadding(pad, pad, pad, pad);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Enter Verification Code")
                .setMessage("A 6-digit code has been sent to " + email
                        + ".\n\nNote: The code is also embedded in the reset link in your email."
                        + " Enter the code below to proceed.")
                .setView(etCode)
                .setPositiveButton("Verify", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String entered = etCode.getText().toString().trim();
            if (entered.length() != 6) {
                etCode.setError("Enter the 6-digit code");
                return;
            }

            // Verify against Firestore (handles expiry)
            db.collection("passwordResets").document(email).get()
                    .addOnSuccessListener(doc -> {
                        if (!doc.exists()) {
                            etCode.setError("Code expired. Request a new one.");
                            return;
                        }
                        long expiresAt = doc.getLong("expiresAt") != null
                                ? doc.getLong("expiresAt") : 0L;
                        String storedOtp = doc.getString("otp");

                        if (System.currentTimeMillis() > expiresAt) {
                            etCode.setError("Code expired. Request a new one.");
                            db.collection("passwordResets").document(email).delete();
                            return;
                        }
                        if (!entered.equals(storedOtp)) {
                            etCode.setError("Incorrect code. Try again.");
                            return;
                        }

                        // Code correct — clean up and go to Step 3
                        db.collection("passwordResets").document(email).delete();
                        dialog.dismiss();
                        // The oobCode arrives when user clicks the Firebase email link
                        // (sanzinkstore://reset_password?oobCode=...).
                        // Since code is now verified, show instructions.
                        showForgotPasswordStep3Instructions(email);
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Verification failed: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show());
        });
    }

    private void showForgotPasswordStep3Instructions(String email) {
        new AlertDialog.Builder(this)
                .setTitle("Code Verified! ✓")
                .setMessage("Your identity has been verified.\n\n"
                        + "Now open the password reset email sent to " + email
                        + " and click the link inside. It will bring you back here to set "
                        + "your new password.")
                .setPositiveButton("OK", null)
                .show();
    }

    // ─── Handle Firebase reset deep link (sanzinkstore://reset_password?oobCode=X) ─

    private void handlePasswordResetDeepLink(String oobCode) {
        setupImmersiveMode();

        if (TextUtils.isEmpty(oobCode)) {
            Toast.makeText(this, "Invalid reset link.", Toast.LENGTH_LONG).show();
            recreate();
            return;
        }

        // Verify the oobCode is still valid before asking for new password
        auth.verifyPasswordResetCode(oobCode)
                .addOnSuccessListener(email ->
                        showSetNewPasswordDialog(oobCode, email))
                .addOnFailureListener(e -> {
                    Toast.makeText(this,
                            "Reset link expired or already used. Please request a new one.",
                            Toast.LENGTH_LONG).show();
                    recreate();
                });
    }

    private void showSetNewPasswordDialog(String oobCode, String email) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_set_new_password, null);
        TextInputLayout   tilNew     = view.findViewById(R.id.tilNewPassword);
        TextInputLayout   tilConfirm = view.findViewById(R.id.tilConfirmPassword);
        TextInputEditText etNew      = view.findViewById(R.id.etNewPassword);
        TextInputEditText etConfirm  = view.findViewById(R.id.etConfirmPassword);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Set New Password")
                .setMessage("Setting password for: " + email)
                .setView(view)
                .setPositiveButton("Reset Password", null)
                .setCancelable(false)
                .create();

        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newPass = etNew.getText()     != null ? etNew.getText().toString()     : "";
            String confirm = etConfirm.getText() != null ? etConfirm.getText().toString() : "";

            tilNew.setError(null); tilConfirm.setError(null);

            if (newPass.length() < 6)      { tilNew.setError("Min. 6 characters");        return; }
            if (!newPass.equals(confirm))  { tilConfirm.setError("Passwords do not match"); return; }

            auth.confirmPasswordReset(oobCode, sha256(newPass))
                    .addOnSuccessListener(unused -> {
                        dialog.dismiss();
                        Toast.makeText(this,
                                "Password reset successfully! Please log in.",
                                Toast.LENGTH_LONG).show();
                        auth.signOut();
                        recreate(); // go back to login screen
                    })
                    .addOnFailureListener(e -> Toast.makeText(this,
                            "Reset failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
        });
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

    private void checkRoleAndNavigate(FirebaseUser user) {
        if (user == null) { setLoading(false); return; }

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
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

    private void showInfoDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Close", null)
                .show();
    }

    private void setLoading(boolean loading) {
        if (binding == null) return;
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
