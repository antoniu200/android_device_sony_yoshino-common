package com.sonymobile.customizationselector;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;

import static android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
import static android.view.WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG;

public class CustomizationSelectorActivity extends Activity implements OnClickListener {

    private static final String TAG = "CustomizationSelectorActivity";

    private AlertDialog mAlertDialog;
    private Configurator mConfigurator;
    private UserPresentReceiver mUserPresentReceiver;

    private void disableActivity() {
        getPackageManager().setComponentEnabledSetting(new ComponentName(this, CustomizationSelectorActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
    }

    private void startDialog() {
        if (mAlertDialog == null) {
            Builder builder = new Builder(this);
            builder.setCancelable(false);
            builder.setMessage(R.string.customization_restart_desc_txt);
            builder.setPositiveButton(R.string.ok_button_label, this);
            mAlertDialog = builder.create();
            mAlertDialog.setCanceledOnTouchOutside(false);
            mAlertDialog.setCancelable(false);
            mAlertDialog.getWindow().setType(TYPE_KEYGUARD_DIALOG);
        }
        if (!mAlertDialog.isShowing()) {
            CSLog.d(TAG, "Show dialog");
            mAlertDialog.show();
        }
    }

    public void disableUI() {
        getWindow().addFlags(FLAG_DISMISS_KEYGUARD);
        getSystemService(StatusBarManager.class).disable(StatusBarManager.DISABLE_MASK);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        CSLog.d(TAG, "onClick - Reboot");
        disableActivity();
        mConfigurator.set();
        Log.i(getString(R.string.app_name), getString(R.string.customization_restart_desc_txt));
        getSystemService(PowerManager.class).reboot(getString(R.string.reboot_reason));
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        CSLog.d(TAG, "onCreate()");
        mConfigurator = new Configurator(this, CommonUtil.getCarrierBundle(this));

        if (CommonUtil.isSIMLoaded(this, CommonUtil.getDefaultSubId(this)) && mConfigurator.isNewConfigurationNeeded()) {
            disableUI();
            setFinishOnTouchOutside(false);
            setupUserPresent();
            startDialog();
        } else {
            disableActivity();
            Intent intent = new Intent()
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        CSLog.d(TAG, "onDestroy()");
        if (mUserPresentReceiver != null)
            unregisterReceiver(mUserPresentReceiver);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        startDialog();
    }

    private void setupUserPresent() {
        mUserPresentReceiver = new UserPresentReceiver();
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(mUserPresentReceiver, intentFilter);
    }
}
