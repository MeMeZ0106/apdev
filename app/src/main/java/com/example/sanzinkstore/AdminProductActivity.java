package com.example.sanzinkstore;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.example.sanzinkstore.databinding.ActivityAdminProductBinding;
import com.example.sanzinkstore.model.Product;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.squareup.picasso.Picasso;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AdminProductActivity extends AppCompatActivity {

    private static final String TAG = "AdminProductActivity";
    private ActivityAdminProductBinding binding;
    private FirebaseFirestore db;
    private String productId = null;
    private Uri selectedImageUri = null;
    private String currentImageUrl = null;

    // Standard Imgur Client ID
    private static final String IMGUR_CLIENT_ID = "546c25a59c58ad7";

    private final ActivityResultLauncher<String> getContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    binding.imagePreview.setImageURI(uri);
                    binding.btnSelectImage.setVisibility(View.GONE);
                    // Clear manual URL if gallery image is selected
                    binding.editImageUrl.setText("");
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminProductBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();

        // Back button on toolbar
        com.google.android.material.appbar.MaterialToolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        setupCategoryDropdown();

        if (getIntent().hasExtra("PRODUCT_ID")) {
            productId = getIntent().getStringExtra("PRODUCT_ID");
            loadProductData();
            binding.btnDelete.setVisibility(View.VISIBLE);
        }

        binding.btnSelectImage.setVisibility(View.VISIBLE);
        binding.btnSelectImage.setOnClickListener(v -> getContent.launch("image/*"));
        binding.imagePreview.setOnClickListener(v -> getContent.launch("image/*"));
        
        binding.btnSave.setOnClickListener(v -> handleSave());
        
        binding.btnDelete.setOnClickListener(v -> deleteProduct());
    }

    private void setupCategoryDropdown() {
        String[] mainCategories = {"Goods", "Meals", "Beverages"};
        ArrayAdapter<String> mainAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, mainCategories);
        binding.editCategory.setAdapter(mainAdapter);

        binding.editCategory.setOnItemClickListener((parent, view, position, id) -> {
            String selectedMain = mainCategories[position];
            updateSubCategoryDropdown(selectedMain);
        });
    }

    private void updateSubCategoryDropdown(String mainCategory) {
        String[] subCategories;
        switch (mainCategory) {
            case "Goods":
                subCategories = new String[]{"All", "Ramen", "Buldak", "Seaweed", "Teokbokki", "Drinks"};
                break;
            case "Meals":
                subCategories = new String[]{"All", "Buldak", "K-Ramen", "Cheese Ramen", "Samyang Omolette", "Rice Meals", "Stations Specials", "Side Dish"};
                break;
            case "Beverages":
                subCategories = new String[]{"All", "Milktea", "Fruit Soda", "Korean Abrica"};
                break;
            default:
                subCategories = new String[]{"All"};
                break;
        }
        ArrayAdapter<String> subAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, subCategories);
        binding.editSubCategory.setAdapter(subAdapter);
        binding.editSubCategory.setText("All", false); // Default to All
    }

    private void loadProductData() {
        binding.uploadProgress.setVisibility(View.VISIBLE);
        db.collection("products").document(productId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    binding.uploadProgress.setVisibility(View.GONE);
                    Product product = documentSnapshot.toObject(Product.class);
                    if (product != null) {
                        binding.editName.setText(product.getName());
                        binding.editDescription.setText(product.getDescription());
                        binding.editPrice.setText(String.valueOf(product.getPrice()));
                        binding.editCategory.setText(product.getCategory(), false);
                        
                        updateSubCategoryDropdown(product.getCategory());
                        binding.editSubCategory.setText(product.getSubCategory(), false);
                        
                        binding.switchAvailable.setChecked(product.isAvailable());
                        currentImageUrl = product.getImageUrl();
                        binding.editImageUrl.setText(currentImageUrl);
                        if (currentImageUrl != null && !currentImageUrl.isEmpty()) {
                            Picasso.get().load(currentImageUrl).placeholder(R.drawable.ic_food).into(binding.imagePreview);
                            binding.btnSelectImage.setVisibility(View.GONE);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    binding.uploadProgress.setVisibility(View.GONE);
                    Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void handleSave() {
        String name = binding.editName.getText().toString().trim();
        String priceStr = binding.editPrice.getText().toString().trim();
        String category = binding.editCategory.getText().toString().trim();
        String subCategory = binding.editSubCategory.getText().toString().trim();
        String manualUrl = binding.editImageUrl.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(priceStr) || TextUtils.isEmpty(category)) {
            Toast.makeText(this, "Please fill required fields (Name, Price, Category)", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedImageUri != null) {
            // Upload selected gallery image
            uploadToImgur(selectedImageUri);
        } else if (!TextUtils.isEmpty(manualUrl)) {
            // Use pasted URL
            saveProductToFirestore(manualUrl);
        } else {
            // Use existing URL (might be null for new product)
            saveProductToFirestore(currentImageUrl);
        }
    }

    private void uploadToImgur(Uri uri) {
        binding.btnSave.setEnabled(false);
        binding.uploadProgress.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                if (bitmap == null) throw new Exception("Failed to decode image");

                // Compress image to ensure it's not too large
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream);
                byte[] imageBytes = outputStream.toByteArray();

                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build();

                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("image", "product.jpg",
                                RequestBody.create(imageBytes, MediaType.parse("image/jpeg")))
                        .build();

                Request request = new Request.Builder()
                        .url("https://api.imgur.com/3/image")
                        .header("Authorization", "Client-ID " + IMGUR_CLIENT_ID)
                        .post(requestBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String responseData = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "Imgur Response: " + responseData);

                    if (response.isSuccessful()) {
                        JSONObject json = new JSONObject(responseData);
                        String uploadedUrl = json.getJSONObject("data").getString("link");
                        runOnUiThread(() -> saveProductToFirestore(uploadedUrl));
                    } else {
                        throw new Exception("Imgur Error " + response.code() + ": " + response.message());
                    }
                }

            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.btnSave.setEnabled(true);
                    binding.uploadProgress.setVisibility(View.GONE);
                    Log.e(TAG, "Upload failed", e);
                    Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void saveProductToFirestore(String imageUrl) {
        String name = binding.editName.getText().toString().trim();
        String description = binding.editDescription.getText().toString().trim();
        String priceStr = binding.editPrice.getText().toString().trim();
        String category = binding.editCategory.getText().toString().trim();
        String subCategory = binding.editSubCategory.getText().toString().trim();

        if (imageUrl == null) imageUrl = "";

        double price;
        try {
            price = Double.parseDouble(priceStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid price format", Toast.LENGTH_SHORT).show();
            binding.btnSave.setEnabled(true);
            binding.uploadProgress.setVisibility(View.GONE);
            return;
        }

        binding.btnSave.setEnabled(false);
        binding.uploadProgress.setVisibility(View.VISIBLE);

        Product product = new Product(productId, name, description, price, imageUrl, category, subCategory, binding.switchAvailable.isChecked());

        Task<Void> task;
        if (productId == null) {
            // Adding new product
            String id = db.collection("products").document().getId();
            product.setId(id);
            task = db.collection("products").document(id).set(product);
        } else {
            // Updating existing product
            product.setId(productId);
            task = db.collection("products").document(productId).set(product);
        }

        task.addOnSuccessListener(aVoid -> {
            Log.d(TAG, "Product saved successfully");
            Toast.makeText(this, "Product Saved!", Toast.LENGTH_SHORT).show();
            finish();
        }).addOnFailureListener(e -> {
            binding.btnSave.setEnabled(true);
            binding.uploadProgress.setVisibility(View.GONE);
            Log.e(TAG, "Firestore Error", e);
            Toast.makeText(this, "Database Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void deleteProduct() {
        if (productId != null) {
            binding.uploadProgress.setVisibility(View.VISIBLE);
            db.collection("products").document(productId).delete()
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Product Deleted!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        binding.uploadProgress.setVisibility(View.GONE);
                        Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }
}
