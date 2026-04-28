package com.example.sanzinkstore;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Base activity that provides a navigation drawer (hamburger menu) instead of a back arrow.
 * Extend this for all secondary screens that should use the side drawer navigation.
 *
 * Usage in subclass onCreate (after setContentView):
 *   setupDrawer(toolbar, R.id.nav_xxx);
 */
public abstract class BaseDrawerActivity extends AppCompatActivity {

    public static final String EXTRA_IS_ADMIN = "IS_ADMIN";

    protected DrawerLayout drawerLayout;
    protected NavigationView navigationView;
    protected boolean isAdmin;

    /**
     * Set up the side navigation drawer tied to the given toolbar.
     *
     * @param toolbar      Toolbar already present in the layout (id: toolbar)
     * @param currentNavId R.id.nav_xxx to pre-check in the drawer menu (pass 0 for none)
     */
    protected void setupDrawer(Toolbar toolbar, int currentNavId) {
        isAdmin = getIntent().getBooleanExtra(EXTRA_IS_ADMIN, false);

        drawerLayout   = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);

        setSupportActionBar(toolbar);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.app_name, R.string.app_name);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.getMenu().clear();
        navigationView.inflateMenu(isAdmin ? R.menu.drawer_menu : R.menu.customer_drawer_menu);
        if (currentNavId != 0) {
            navigationView.setCheckedItem(currentNavId);
        }

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            drawerLayout.closeDrawer(GravityCompat.START);
            // Small delay so the drawer close animation finishes first
            new Handler(Looper.getMainLooper()).postDelayed(() -> handleNavItem(id), 250);
            return true;
        });

        // Handle back gesture: close drawer if open, otherwise go back
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void handleNavItem(int id) {
        if (id == R.id.nav_logout) {
            FirebaseAuth.getInstance().signOut();
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build();
            GoogleSignIn.getClient(this, gso).signOut().addOnCompleteListener(t -> {
                startActivity(new Intent(this, LoginActivity.class));
                finishAffinity();
            });

        } else if (id == R.id.nav_pos || id == R.id.nav_home) {
            startActivity(new Intent(this, MainActivity.class)
                    .putExtra(EXTRA_IS_ADMIN, isAdmin)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));

        } else if (id == R.id.nav_profile) {
            if (!(this instanceof ProfileActivity))
                startActivity(new Intent(this, ProfileActivity.class)
                        .putExtra(EXTRA_IS_ADMIN, isAdmin));

        } else if (id == R.id.nav_settings) {
            if (!(this instanceof SettingsActivity))
                startActivity(new Intent(this, SettingsActivity.class)
                        .putExtra(EXTRA_IS_ADMIN, isAdmin));

        } else if (id == R.id.nav_inventory) {
            if (!(this instanceof InventoryActivity))
                startActivity(new Intent(this, InventoryActivity.class)
                        .putExtra(EXTRA_IS_ADMIN, true));

        } else if (id == R.id.nav_logs) {
            if (!(this instanceof LogsActivity))
                startActivity(new Intent(this, LogsActivity.class)
                        .putExtra(EXTRA_IS_ADMIN, true));

        } else if (id == R.id.nav_messages) {
            if (!(this instanceof SellerInboxActivity))
                startActivity(new Intent(this, SellerInboxActivity.class)
                        .putExtra(EXTRA_IS_ADMIN, true));

        } else if (id == R.id.nav_print_orders || id == R.id.nav_download_csv) {
            startActivity(new Intent(this, MainActivity.class)
                    .putExtra(EXTRA_IS_ADMIN, true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        }
    }
}
