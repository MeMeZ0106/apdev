package com.example.sanzinkstore;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.util.TypedValue;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.view.GravityCompat;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.example.sanzinkstore.api.PayMongoService;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sanzinkstore.adapter.CartAdapter;
import com.example.sanzinkstore.api.CloudinaryHelper;
import com.example.sanzinkstore.databinding.ActivityMainBinding;
import com.example.sanzinkstore.model.CartItem;
import com.example.sanzinkstore.model.Order;
import com.example.sanzinkstore.model.Product;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import android.os.Handler;
import android.os.Looper;
import com.example.sanzinkstore.fragment.CartFragment;
import com.example.sanzinkstore.fragment.CheckoutFragment;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private List<CartItem> cart;
    private double totalAmount = 0;
    private boolean isAdmin = false;
    private boolean isGuest = false;
    private String csvContentToSave = "";
    private PayMongoService payMongoService;
    private static final String CART_PREFS = "cart_prefs";
    private static final String KEY_CART = "saved_cart";

    // Replace with your actual PayMongo Public Key
    private static final String PAYMONGO_PUBLIC_KEY = "pk_test_your_key_here";
    private final Handler hideHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideRunnable = this::hideSystemUI;

    private final ActivityResultLauncher<String> createDocumentLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"),
            uri -> {
                if (uri != null) {
                    saveCsvToUri(uri);
                }
            }
    );

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        String cartJson = new Gson().toJson(cart);
        getSharedPreferences(CART_PREFS, MODE_PRIVATE).edit().putString(KEY_CART, cartJson).apply();
    }

    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_DARK_MODE = "dark_mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Initialise Cloudinary (uses unsigned preset — no secret in app)
        CloudinaryHelper.init(this);
        
        isAdmin = getIntent().getBooleanExtra("IS_ADMIN", false);
        isGuest = getIntent().getBooleanExtra("IS_GUEST", false);
        Log.d(TAG, "Starting MainActivity. Is Admin: " + isAdmin);

        // Restore cart from SharedPreferences on a background thread
        cart = new ArrayList<>();
        new Thread(() -> {
            String savedCartJson = getSharedPreferences(CART_PREFS, MODE_PRIVATE).getString(KEY_CART, null);
            if (savedCartJson != null) {
                try {
                    List<CartItem> restoredCart = new Gson().fromJson(savedCartJson, new TypeToken<ArrayList<CartItem>>(){}.getType());
                    if (restoredCart != null) {
                        runOnUiThread(() -> {
                            cart.addAll(restoredCart);
                            updateCartTotal();
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error restoring cart", e);
                }
            }
        }).start();

        setSupportActionBar(binding.toolbar);
        setupViewPager();
        setupUI();
        setupPayMongo();
        updateCartTotal();

        handleIntent(getIntent());
        
        setupImmersiveMode();
    }

    private void setupImmersiveMode() {
        WindowInsetsControllerCompat windowInsetsController =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        windowInsetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        
        hideSystemUI();
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

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        Uri data = intent.getData();
        if (data != null && "sanzinkstore".equals(data.getScheme())) {
            String sourceId = data.getQueryParameter("source_id");
            String orderId = data.getQueryParameter("order_id");
            if (sourceId != null && orderId != null) {
                verifyPayment(sourceId, orderId);
            }
        }
    }

    private void verifyPayment(String sourceId, String orderId) {
        String authHeader = "Basic " + Base64.encodeToString((PAYMONGO_PUBLIC_KEY + ":").getBytes(), Base64.NO_WRAP);
        payMongoService.getSource(authHeader, sourceId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody != null) {
                            String body = responseBody.string();
                            JSONObject json = new JSONObject(body);
                            String status = json.getJSONObject("data").getJSONObject("attributes").getString("status");

                            if ("chargeable".equals(status)) {
                                // Payment is authorized/successful
                                db.collection("orders").document(orderId)
                                        .update("paid", true)
                                        .addOnSuccessListener(aVoid -> Toast.makeText(MainActivity.this, "Payment Successful!", Toast.LENGTH_LONG).show());
                            } else {
                                Toast.makeText(MainActivity.this, "Payment not completed: " + status, Toast.LENGTH_LONG).show();
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing payment response", e);
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Toast.makeText(MainActivity.this, "Verification failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupPayMongo() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.paymongo.com/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        payMongoService = retrofit.create(PayMongoService.class);
    }

    private void setupUI() {
        // Setup common drawer toggle
        binding.toolbar.setNavigationIcon(R.drawable.ic_menu);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, binding.drawerLayout, binding.toolbar,
                R.string.app_name, R.string.app_name);
        binding.drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        if (isAdmin) {
            binding.fabCart.setVisibility(View.GONE);
            binding.bottomNavigation.setVisibility(View.GONE);
            binding.bottomPanel.setVisibility(View.VISIBLE);
            binding.toolbar.setTitle(getString(R.string.seller_access));
            
            binding.navigationView.getMenu().clear();
            binding.navigationView.inflateMenu(R.menu.drawer_menu);
            binding.navigationView.setCheckedItem(R.id.nav_pos);
            binding.navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_logout) {
                    logout();
                } else if (id == R.id.nav_pos) {
                    // Already here
                } else if (id == R.id.nav_profile) {
                    startActivity(new Intent(this, ProfileActivity.class)
                            .putExtra(BaseDrawerActivity.EXTRA_IS_ADMIN, true));
                } else if (id == R.id.nav_inventory) {
                    startActivity(new Intent(this, InventoryActivity.class)
                            .putExtra(BaseDrawerActivity.EXTRA_IS_ADMIN, true));
                } else if (id == R.id.nav_logs) {
                    startActivity(new Intent(this, LogsActivity.class)
                            .putExtra(BaseDrawerActivity.EXTRA_IS_ADMIN, true));
                } else if (id == R.id.nav_settings) {
                    startActivity(new Intent(this, SettingsActivity.class)
                            .putExtra(BaseDrawerActivity.EXTRA_IS_ADMIN, true));
                } else if (id == R.id.nav_messages) {
                    startActivity(new Intent(this, SellerInboxActivity.class)
                            .putExtra(BaseDrawerActivity.EXTRA_IS_ADMIN, true));
                } else if (id == R.id.nav_print_orders) {
                    fetchOrdersAndPrint();
                } else if (id == R.id.nav_download_csv) {
                    fetchOrdersAndDownloadCsv();
                }
                binding.drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            });

            binding.btnCancel.setOnClickListener(v -> {
                cart.clear();
                updateCartTotal();
            });

            binding.btnEditCart.setOnClickListener(v -> showEditCartDialog());
            
            binding.btnAccept.setOnClickListener(v -> {
                if (!cart.isEmpty()) {
                    processOrder("CASH_POS", true, null);
                } else {
                    Toast.makeText(this, "Cart is empty!", Toast.LENGTH_SHORT).show();
                }
            });

        } else {
            binding.fabCart.setVisibility(View.VISIBLE);
            binding.bottomNavigation.setVisibility(View.VISIBLE);
            binding.bottomPanel.setVisibility(View.GONE);
            binding.fabCart.setOnClickListener(v -> handleCheckout());
            // Visual hint for guests that the cart requires login
            if (isGuest) {
                binding.toolbar.setSubtitle("Browsing as Guest — Login to buy");
            }
            binding.toolbar.setTitle(getString(R.string.app_name));
            
            binding.navigationView.getMenu().clear();
            binding.navigationView.inflateMenu(R.menu.customer_drawer_menu);
            binding.navigationView.setCheckedItem(R.id.nav_home);
            binding.navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_logout) {
                    logout();
                } else if (id == R.id.nav_home) {
                    // Already here
                } else if (id == R.id.nav_profile) {
                    startActivity(new Intent(this, ProfileActivity.class)
                            .putExtra(BaseDrawerActivity.EXTRA_IS_ADMIN, false));
                } else if (id == R.id.nav_settings) {
                    startActivity(new Intent(this, SettingsActivity.class)
                            .putExtra(BaseDrawerActivity.EXTRA_IS_ADMIN, false));
                }
                binding.drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            });

            binding.bottomNavigation.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_contact) {
                    Intent chatIntent = new Intent(this, ChatActivity.class);
                    chatIntent.putExtra(ChatActivity.EXTRA_IS_SELLER, false);
                    startActivity(chatIntent);
                    return true;
                } else if (id == R.id.nav_cart) {
                    handleCheckout();
                    return true;
                }
                return false;
            });
            
            updateCartTotal();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem themeItem = menu.findItem(R.id.action_theme_toggle);
        if (themeItem != null) {
            boolean isDarkMode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_DARK_MODE, true);
            // Show sun in day mode, moon in night mode (represents the current mode)
            themeItem.setIcon(isDarkMode ? R.drawable.ic_moon : R.drawable.ic_sun);

            // Use colorOnSurface so the icon is always visible regardless of toolbar background
            TypedValue tv = new TypedValue();
            getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tv, true);
            int onSurfaceColor = tv.data;
            if (themeItem.getIcon() != null) {
                themeItem.getIcon().setTintList(ColorStateList.valueOf(onSurfaceColor));
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (isAdmin) {
            getMenuInflater().inflate(R.menu.admin_menu, menu);
        } else {
            getMenuInflater().inflate(R.menu.main_menu, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_theme_toggle) {
            boolean isDarkMode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_DARK_MODE, true);
            boolean newDarkMode = !isDarkMode;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_DARK_MODE, newDarkMode).apply();

            if (newDarkMode) {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void logout() {
        auth.signOut();
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        @SuppressWarnings("deprecation")
        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, gso);
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        });
    }

    public void handleCheckout() {
        if (isGuest) {
            showGuestPurchaseBlockedDialog();
            return;
        }
        if (cart.isEmpty()) {
            Toast.makeText(this, "Your cart is empty!", Toast.LENGTH_SHORT).show();
            return;
        }

        CartFragment cartFragment = CartFragment.newInstance(cart);
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, cartFragment)
                .addToBackStack(null)
                .commit();
    }

    /** Shown when a guest tries to purchase anything. */
    private void showGuestPurchaseBlockedDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Login Required")
                .setMessage(getString(R.string.guest_purchase_blocked))
                .setPositiveButton("Login", (d, w) -> {
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void initiateOnlinePayment(String type) {
        // Create order first with Unpaid status
        String orderId = db.collection("orders").document().getId();
        processOrder(type.toUpperCase(), false, orderId);

        try {
            JSONObject root = createPaymentRequestJson(type, orderId);

            @SuppressWarnings("deprecation")
            RequestBody body = RequestBody.create(root.toString(), MediaType.parse("application/json"));
            String authHeader = "Basic " + Base64.encodeToString((PAYMONGO_PUBLIC_KEY + ":").getBytes(), Base64.NO_WRAP);

            payMongoService.createSource(authHeader, body).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    if (response.isSuccessful()) {
                        try (ResponseBody responseBody = response.body()) {
                            if (responseBody != null) {
                                String bodyString = responseBody.string();
                                JSONObject jsonResponse = new JSONObject(bodyString);
                                String sourceId = jsonResponse.getJSONObject("data").getString("id");
                                String checkoutUrl = jsonResponse.getJSONObject("data")
                                        .getJSONObject("attributes")
                                        .getJSONObject("redirect")
                                        .getString("checkout_url");

                                // Append source_id to return URL for verification
                                String finalCheckoutUrl = checkoutUrl + "&source_id=" + sourceId;

                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(finalCheckoutUrl));
                                startActivity(intent);
                            }

                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "Error processing payment request", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Payment service unavailable", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    Toast.makeText(MainActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error initiating payment", e);
        }
    }

    @NonNull
    private JSONObject createPaymentRequestJson(String type, String orderId) throws org.json.JSONException {
        JSONObject data = new JSONObject();
        JSONObject attributes = new JSONObject();
        attributes.put("amount", (int) (totalAmount * 100));
        attributes.put("type", type);
        attributes.put("currency", "PHP");

        JSONObject redirect = new JSONObject();
        // Deep link back to app with identifiers
        String returnUrl = "sanzinkstore://payment_return?order_id=" + orderId;
        redirect.put("success", returnUrl);
        redirect.put("failed", returnUrl);
        attributes.put("redirect", redirect);

        data.put("attributes", attributes);
        JSONObject root = new JSONObject();
        root.put("data", data);
        return root;
    }

    private void processOrder(String paymentMethod, boolean isPaid, String existingOrderId) {
        String userId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "anonymous";

        // Include customer identity for online orders; leave blank for in-store POS
        String customerName  = "";
        String customerEmail = "";
        if (!isAdmin && auth.getCurrentUser() != null) {
            customerName  = auth.getCurrentUser().getDisplayName() != null
                    ? auth.getCurrentUser().getDisplayName() : "";
            customerEmail = auth.getCurrentUser().getEmail() != null
                    ? auth.getCurrentUser().getEmail() : "";
        }

        Order order = new Order(
                userId,
                customerName,
                customerEmail,
                new ArrayList<>(cart),
                totalAmount,
                System.currentTimeMillis(),
                "PENDING",
                paymentMethod,
                isPaid
        );

        if (existingOrderId != null) {
            db.collection("orders").document(existingOrderId).set(order)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Order updated!", Toast.LENGTH_SHORT).show();
                        if (isAdmin) {
                            cart.clear();
                            updateCartTotal();
                        }
                    });
        } else {
            db.collection("orders").add(order)
                    .addOnSuccessListener(documentReference -> {
                        Toast.makeText(this, "Order placed successfully!", Toast.LENGTH_SHORT).show();
                        cart.clear();
                        updateCartTotal();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to save order: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void fetchOrdersAndPrint() {
        db.collection("orders")
                .whereEqualTo("status", "PENDING")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(this, "No pending orders to print.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String currentDate = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
                    StringBuilder html = new StringBuilder();
                    html.append("<html><body>");
                    html.append("<h1 style='text-align:center;'>Sanzin K-Store - Daily Orders</h1>");
                    html.append("<p style='text-align:center;'><b>Generated on: ").append(currentDate).append("</b></p>");
                    html.append("<hr>");

                    List<String> orderIds = new ArrayList<>();
                    double grandTotal = 0;

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Order order = doc.toObject(Order.class);
                        orderIds.add(doc.getId());
                        
                        html.append("<h3>Order ID: ").append(doc.getId()).append("</h3>");
                        html.append("<p>Time: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(order.getTimestamp()))).append("</p>");
                        html.append("<p>Payment: ").append(order.getPaymentMethod()).append(" (").append(order.isPaid() ? "PAID" : "UNPAID").append(")</p>");
                        html.append("<ul>");
                        for (CartItem ci : order.getItems()) {
                            html.append("<li>").append(ci.getQuantity()).append("x ").append(ci.getProduct().getName())
                                    .append(" - PHP ").append(String.format(Locale.getDefault(), "%.2f", ci.getTotalPrice())).append("</li>");
                        }
                        html.append("</ul>");
                        html.append("<p><b>Order Total: PHP ").append(String.format(Locale.getDefault(), "%.2f", order.getTotalAmount())).append("</b></p>");
                        html.append("<hr>");
                        grandTotal += order.getTotalAmount();
                    }

                    html.append("<h2>Grand Total: PHP ").append(String.format(Locale.getDefault(), "%.2f", grandTotal)).append("</h2>");
                    html.append("</body></html>");

                    doPrint(html.toString(), orderIds);
                });
    }

    private void fetchOrdersAndDownloadCsv() {
        db.collection("orders")
                .whereEqualTo("status", "PENDING")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(this, "No pending orders to download.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String currentDate = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
                    StringBuilder csv = new StringBuilder();
                    csv.append("Sanzin K-Store Daily Report\n");
                    csv.append("Report Date,").append(currentDate).append("\n\n");
                    csv.append("Order ID,Timestamp,Payment Method,Payment Status,Product,Quantity,Unit Price,Total Price\n");

                    double grandTotal = 0;
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Order order = doc.toObject(Order.class);
                        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(order.getTimestamp()));
                        String paymentStatus = order.isPaid() ? "PAID" : "UNPAID";
                        
                        for (CartItem ci : order.getItems()) {
                            csv.append(doc.getId()).append(",")
                               .append(time).append(",")
                               .append(order.getPaymentMethod()).append(",")
                               .append(paymentStatus).append(",")
                               .append(ci.getProduct().getName().replace(",", " ")).append(",")
                               .append(ci.getQuantity()).append(",")
                               .append(String.format(Locale.getDefault(), "%.2f", ci.getProduct().getPrice())).append(",")
                               .append(String.format(Locale.getDefault(), "%.2f", ci.getTotalPrice())).append("\n");
                        }
                        grandTotal += order.getTotalAmount();
                    }
                    csv.append(",,,,,,,Grand Total,").append(String.format(Locale.getDefault(), "%.2f", grandTotal)).append("\n");

                    csvContentToSave = csv.toString();
                    createDocumentLauncher.launch("Sanzin_Daily_Report_" + currentDate + ".csv");
                });
    }

    private void saveCsvToUri(Uri uri) {
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            if (outputStream != null) {
                outputStream.write(csvContentToSave.getBytes());
                Toast.makeText(this, "Report downloaded successfully!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save CSV", e);
            Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void doPrint(String htmlContent, List<String> orderIds) {
        WebView webView = new WebView(this);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                createWebPrintJob(view, orderIds);
            }
        });
        webView.loadDataWithBaseURL(null, htmlContent, "text/HTML", "UTF-8", null);
    }

    private void createWebPrintJob(WebView webView, List<String> orderIds) {
        PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        String jobName = getString(R.string.app_name) + " Orders Document";
        PrintDocumentAdapter printAdapter = webView.createPrintDocumentAdapter(jobName);

        printManager.print(jobName, printAdapter, new PrintAttributes.Builder().build());

        // After printing, update status of these orders to COMPLETED
        for (String id : orderIds) {
            db.collection("orders").document(id).update("status", "COMPLETED");
        }
    }

    private void setupViewPager() {
        FragmentStateAdapter adapter = new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return ProductListFragment.newInstance("All", isAdmin);
            }

            @Override
            public int getItemCount() {
                return 1;
            }
        };

        binding.viewPager.setAdapter(adapter);
    }

    public void addToCart(Product product) {
        if (isGuest) {
            showGuestPurchaseBlockedDialog();
            return;
        }
        for (CartItem item : cart) {
            if (item.getProduct().getId().equals(product.getId())) {
                item.setQuantity(item.getQuantity() + 1);
                updateCartTotal();
                if (isAdmin) binding.lastAddedValue.setText(product.getName());
                return;
            }
        }
        cart.add(new CartItem(product, 1));
        if (isAdmin) binding.lastAddedValue.setText(product.getName());
        updateCartTotal();
    }

    public void onCartUpdated() {
        updateCartTotal();
    }

    public void navigateToCheckout() {
        // Transition to CheckoutFragment
        CheckoutFragment checkoutFragment = CheckoutFragment.newInstance(cart, totalAmount);
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, checkoutFragment)
                .addToBackStack(null)
                .commit();
    }

    public void completeOrder(String paymentMethod) {
        if ("PAY_ON_PICKUP".equals(paymentMethod)) {
            processOrder("PAY_ON_PICKUP", false, null);
        } else if ("GCASH".equals(paymentMethod)) {
            initiateOnlinePayment("gcash");
        } else if ("MAYA".equals(paymentMethod)) {
            initiateOnlinePayment("paymaya");
        }
    }

    private void updateCartTotal() {
        totalAmount = 0;
        int totalItems = 0;
        for (CartItem item : cart) {
            totalAmount += item.getTotalPrice();
            totalItems += item.getQuantity();
        }
        
        if (isAdmin) {
            binding.itemsCountValue.setText(String.valueOf(totalItems));
            binding.totalValue.setText(String.format(Locale.getDefault(), "%.2f", totalAmount));
            if (cart.isEmpty()) binding.lastAddedValue.setText("None");
        } else {
            String cartText = "Cart (" + totalItems + ") - PHP " + String.format(Locale.getDefault(), "%.2f", totalAmount);
            binding.fabCart.setText(cartText);
        }
    }

    private void showEditCartDialog() {
        if (cart.isEmpty()) {
            Toast.makeText(this, "Cart is empty!", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_cart, null);
        RecyclerView rv = dialogView.findViewById(R.id.rvEditCart);
        rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit Cart")
                .setView(dialogView)
                .setPositiveButton("Done", null)
                .create();

        CartAdapter cartAdapter = new CartAdapter(cart, new CartAdapter.OnCartChangeListener() {
            @Override
            public void onQuantityChanged(int position, int newQuantity) {
                cart.get(position).setQuantity(newQuantity);
                updateCartTotal();
                rv.getAdapter().notifyItemChanged(position);
            }

            @Override
            public void onItemRemoved(int position) {
                cart.remove(position);
                updateCartTotal();
                rv.getAdapter().notifyItemRemoved(position);
                if (cart.isEmpty()) dialog.dismiss();
            }
        });

        rv.setAdapter(cartAdapter);
        dialog.show();
    }
}