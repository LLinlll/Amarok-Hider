package deltazero.amarok.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.hjq.permissions.OnPermissionCallback;

import java.util.List;

import deltazero.amarok.BuildConfig;
import deltazero.amarok.Hider;
import deltazero.amarok.PrefMgr;
import deltazero.amarok.QuickHideService;
import deltazero.amarok.R;
import deltazero.amarok.utils.AppCenterUtil;
import deltazero.amarok.utils.HashUtil;
import deltazero.amarok.utils.LauncherIconController;
import deltazero.amarok.utils.PermissionUtil;
import deltazero.amarok.utils.SwitchLocaleUtil;

public class SettingsActivity extends AppCompatActivity {

    private PrefMgr prefMgr;
    private Context context;
    private String appVersionName;
    private MaterialSwitch swAnalytics, swAutoUpdate, swPanicButton, swQuickHideNotification, swAppLock, swBiometricAuth, swDynamicColor, swDisguise;
    private MaterialToolbar tbToolBar;
    private TextView tvCurrAppHider;
    private TextView tvCurrFileHider;
    private TextView tvCurrVer;
    private RelativeLayout rlDebugInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefMgr = new PrefMgr(this);
        context = this;

        // Get app version
        try {
            appVersionName = this.getPackageManager()
                    .getPackageInfo(this.getPackageName(), PackageManager.GET_ACTIVITIES).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            appVersionName = null;
            // Make compiler happy
        }
        assert appVersionName != null;

        tvCurrAppHider = findViewById(R.id.settings_tv_curr_app_hider);
        tvCurrFileHider = findViewById(R.id.settings_tv_curr_file_hider);
        tvCurrVer = findViewById(R.id.settings_tv_curr_ver);
        swAppLock = findViewById(R.id.settings_sw_amarok_lock);
        swDisguise = findViewById(R.id.settings_sw_disguise);
        swBiometricAuth = findViewById(R.id.settings_sw_biometric_auth);
        swQuickHideNotification = findViewById(R.id.settings_sw_quick_hide_notification);
        swPanicButton = findViewById(R.id.settings_sw_panic_button);
        swAnalytics = findViewById(R.id.settings_sw_analytics);
        swAutoUpdate = findViewById(R.id.settings_sw_auto_update);
        swDynamicColor = findViewById(R.id.settings_sw_dynamic_color);
        tbToolBar = findViewById(R.id.settings_tb_toolbar);
        rlDebugInfo = findViewById(R.id.settings_rl_debug_info);

        // Init view
        updateUI();

        // Setup Listeners
        swQuickHideNotification.setOnCheckedChangeListener(((buttonView, isChecked) -> {
            if (!buttonView.isPressed())
                return; // Triggered by setCheck

            if (isChecked) {
                PermissionUtil.requestNotificationPermission(this, new OnPermissionCallback() {
                    @Override
                    public void onGranted(List<String> permissions, boolean all) {
                        Log.d("QuickHideNotification", "Granted: NOTIFICATION");
                        prefMgr.setEnableQuickHideService(true);
                        QuickHideService.startService(context);
                        updateUI();
                    }

                    @Override
                    public void onDenied(List<String> permissions, boolean never) {
                        Log.w("QuickHideNotification", "User denied: NOTIFICATION");
                        Toast.makeText(context, R.string.notification_permission_denied, Toast.LENGTH_LONG).show();

                        prefMgr.setEnableQuickHideService(false);
                        prefMgr.setEnablePanicButton(false);
                        updateUI();
                    }
                });
            } else {
                prefMgr.setEnableQuickHideService(false);
                prefMgr.setEnablePanicButton(false);
                QuickHideService.stopService(this);
                updateUI();
            }
        }));

        swAppLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                new SetPasswordFragment()
                        .setCallback(password -> {
                            prefMgr.setAmarokPassword(password == null ? null : HashUtil.calculateHash(password));
                            updateUI();
                        })
                        .show(getSupportFragmentManager(), null);
            } else {
                prefMgr.setAmarokPassword(null);
                updateUI();
            }
        });

        swDisguise.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefMgr.setDoShowQuitDisguiseInstuct(true);
            prefMgr.setEnableDisguise(isChecked);
            LauncherIconController.switchDisguise(this, isChecked);
        });

        swBiometricAuth.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefMgr.setEnableAmarokBiometricAuth(isChecked);
        });

        swPanicButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed())
                return; // Triggered by setCheck

            if (isChecked) {
                PermissionUtil.requestSystemAlertPermission(this, new OnPermissionCallback() {
                    @Override
                    public void onGranted(List<String> permissions, boolean all) {
                        Log.d("PanicButton", "Granted: SYSTEM_ALERT_WINDOW");
                        prefMgr.setEnablePanicButton(true);
                        QuickHideService.startService(context);
                        updateUI();
                    }

                    @Override
                    public void onDenied(List<String> permissions, boolean never) {
                        Log.w("PanicButton", "User denied: SYSTEM_ALERT_WINDOW");
                        Toast.makeText(context, R.string.alert_permission_denied, Toast.LENGTH_LONG).show();
                        prefMgr.setEnablePanicButton(false);
                        updateUI();
                    }
                });
            } else {
                prefMgr.setEnablePanicButton(false);
                QuickHideService.startService(context); // Restart service
                updateUI();
            }
        });

        swAnalytics.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppCenterUtil.setAnalyticsEnabled(isChecked);
            Toast.makeText(context, R.string.apply_on_restart, Toast.LENGTH_SHORT).show();
        });

        swAutoUpdate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefMgr.setEnableAutoUpdate(isChecked);

            if (isChecked) {
                AppCenterUtil.cleanUpdatePostpone();
            }

            Toast.makeText(context, R.string.apply_on_restart, Toast.LENGTH_SHORT).show();
        });

        swDynamicColor.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefMgr.setEnableDynamicColor(isChecked);
                onThemeSwitchClick(buttonView);
            Toast.makeText(context, R.string.apply_on_restart, Toast.LENGTH_SHORT).show();
        });

        // Enable back button
        tbToolBar.setNavigationOnClickListener(v -> finish());

        // Show debug button in debug mode
        if (BuildConfig.DEBUG) {
            rlDebugInfo.setVisibility(View.VISIBLE);
        }
    }

    private void updateUI() {
        tvCurrAppHider.setText(getString(R.string.current_mode, prefMgr.getAppHider().getName()));
        tvCurrFileHider.setText(getString(R.string.current_mode,
                (prefMgr.getFileHider().getName())));

        tvCurrVer.setText(getString(R.string.check_update_description, appVersionName));

        swAppLock.setChecked(prefMgr.getAmarokPassword() != null);
        swBiometricAuth.setChecked(prefMgr.getEnableAmarokBiometricAuth());
        swDisguise.setChecked(prefMgr.getEnableDisguise());
        swQuickHideNotification.setChecked(prefMgr.getEnableQuickHideService());
        swPanicButton.setChecked(prefMgr.getEnablePanicButton());
        swDynamicColor.setChecked(prefMgr.getEnableDynamicColor());

        swPanicButton.setEnabled(prefMgr.getEnableQuickHideService());
        swBiometricAuth.setEnabled(prefMgr.getAmarokPassword() != null);

        if (AppCenterUtil.isAvailable()) {
            swAnalytics.setChecked(AppCenterUtil.isAnalyticsEnabled());
            swAutoUpdate.setChecked(prefMgr.getEnableAutoUpdate());
        } else {
            swAnalytics.setEnabled(false);
            swAutoUpdate.setEnabled(false);
        }

    }


    @Override
    public void onResume() {
        super.onResume();
        updateUI();
    }
    
     public void onThemeSwitchClick(View view) {

        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;

        if (currentNightMode == Configuration.UI_MODE_NIGHT_NO) {
            setTheme(R.style.Theme_Amarok_Dark);
        } else {
            setTheme(R.style.Theme_Amarok);
        }

        recreate();
    }


    public void switchAppHider(View view) {
        startActivity(new Intent(this, SwitchAppHiderActivity.class));
    }

    public void switchFileHider(View view) {
        if (prefMgr.getIsHidden() || Boolean.TRUE.equals(Hider.isProcessing.getValue())) {
            Toast.makeText(this, R.string.option_unava_when_hidden, Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(this, SwitchFileHiderActivity.class));
    }

    public void checkUpdate(View view) {
        if (AppCenterUtil.isAvailable()) {
            AppCenterUtil.checkUpdate();
        } else {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/deltazefiro/Amarok-Hider/releases")));
        }
    }

    public void openGithub(View view) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/deltazefiro/Amarok-Hider")));
    }

    public void openHelp(View view) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.doc_url))));
    }

    public void joinDevGroup(View view) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/amarok_dev")));
    }

    public void forceUnhide(View view) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.force_unhide)
                .setMessage(R.string.force_unhide_confirm_msg)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    new Hider(this).forceUnhide();
                    Toast.makeText(this, R.string.performing_force_unhide, Toast.LENGTH_LONG).show();
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public void switchLanguage(View view) {
        SwitchLocaleUtil.switchLocale(this);
    }

    public void participateTranslation(View view) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://hosted.weblate.org/engage/amarok-hider/")));
    }

    public void showDebugInfo(View view) {
        startActivity(new Intent(this, CalendarActivity.class));
    }
}

