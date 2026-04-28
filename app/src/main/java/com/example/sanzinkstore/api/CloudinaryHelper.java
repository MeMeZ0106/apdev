package com.example.sanzinkstore.api;

import android.content.Context;
import android.net.Uri;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.example.sanzinkstore.BuildConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * CloudinaryHelper — singleton wrapper around the Cloudinary Android SDK.
 *
 * Credentials are injected at build-time from local.properties via BuildConfig.
 * The API Secret is NEVER stored in the app — unsigned upload presets are used instead.
 *
 * Setup steps:
 *  1. In Cloudinary dashboard → Settings → Upload → Upload presets
 *     → Add preset → Signing mode: Unsigned → copy the preset name.
 *  2. Paste it as CLOUDINARY_UPLOAD_PRESET in local.properties.
 *  3. Call CloudinaryHelper.init(context) once (e.g. in MainActivity.onCreate).
 */
public class CloudinaryHelper {

    private static boolean initialised = false;

    /** Initialise the Cloudinary SDK. Call once before any upload. */
    public static void init(Context context) {
        if (initialised) return;

        HashMap<String, String> config = new HashMap<>();
        config.put("cloud_name", BuildConfig.CLOUDINARY_CLOUD_NAME);
        config.put("api_key",    BuildConfig.CLOUDINARY_API_KEY);
        // api_secret intentionally omitted — unsigned preset handles auth

        MediaManager.init(context.getApplicationContext(), config);
        initialised = true;
    }

    /**
     * Upload a product image from a local Uri to Cloudinary.
     *
     * @param imageUri Local content Uri (e.g. from an image picker).
     * @param listener Callbacks: onStart, onProgress, onSuccess (secureUrl), onError.
     * @return Cloudinary request ID (pass to MediaManager.get().cancelRequest() to abort).
     */
    public static String uploadImage(Uri imageUri, UploadListener listener) {
        return MediaManager.get()
                .upload(imageUri)
                .unsigned(BuildConfig.CLOUDINARY_UPLOAD_PRESET)
                .option("folder", "products")
                .callback(new UploadCallback() {

                    @Override
                    public void onStart(String requestId) {
                        if (listener != null) listener.onStart(requestId);
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                        if (listener != null) {
                            int pct = totalBytes > 0 ? (int) (bytes * 100 / totalBytes) : 0;
                            listener.onProgress(pct);
                        }
                    }

                    @Override
                    @SuppressWarnings("rawtypes")
                    public void onSuccess(String requestId, Map resultData) {
                        String secureUrl = (String) resultData.get("secure_url");
                        if (listener != null) listener.onSuccess(secureUrl != null ? secureUrl : "");
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        if (listener != null) listener.onError(error.getDescription());
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                        if (listener != null) listener.onError("Upload rescheduled: " + error.getDescription());
                    }
                })
                .dispatch();
    }

    /** Result callbacks for image uploads. */
    public interface UploadListener {
        void onStart(String requestId);
        void onProgress(int percent);
        /** Called with the HTTPS URL ready to save in Firestore and display via Picasso. */
        void onSuccess(String secureUrl);
        void onError(String errorMessage);
    }
}
