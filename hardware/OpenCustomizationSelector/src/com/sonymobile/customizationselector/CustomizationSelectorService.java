package com.sonymobile.customizationselector;

import android.app.IntentService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserManager;
import android.provider.Settings.Secure;

public class CustomizationSelectorService extends IntentService {

    private static final String TAG = "CustomizationSelectorService";
    public static final String EVALUATE_ACTION = "evaluate_action";

    public CustomizationSelectorService() {
        super(CustomizationSelectorService.class.getName());
    }

    public static synchronized void evaluateCarrierBundle(Context context) {
        try {
            CSLog.logVersion(context, TAG);
            CSLog.logSimValues(context, TAG);

            if (!CommonUtil.isDirectBootEnabled()) {
                UserManager userManager = context.getSystemService(UserManager.class);
                if (userManager != null && !userManager.isUserUnlocked()) {
                    CSLog.d(TAG, "user is locked. private app data storage is not available.");
                    return;
                }
            }

            Configurator configurator = new Configurator(context, CommonUtil.getCarrierBundle(context));
            if (configurator.isNewConfigurationNeeded()) {
                context.getPackageManager().setComponentEnabledSetting(new ComponentName(context, CustomizationSelectorActivity.class),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.COMPONENT_ENABLED_STATE_ENABLED);

                if (isUserSetupComplete(context)) {
                    CSLog.d(TAG, "evaluateCarrierBundle - Need to reboot, starting dialog.");
                    Intent intent = new Intent(Intent.ACTION_MAIN, null)
                        .addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    context.startActivity(intent);
                } else
                    CSLog.d(TAG, "evaluateCarrierBundle - Need to reboot, user setup not complete");
            } else {
                configurator.saveConfigurationKey();
                CSLog.d(TAG, "evaluateCarrierBundle - No new configuration.");

                ModemSwitcher.reApplyModem(context);
            }
        } catch (Exception e) {
            CSLog.e(TAG, "evaluateCarrierBundle - ERROR: ", e);
        }
    }

    private static boolean isUserSetupComplete(Context context) {
        return Secure.getInt(context.getContentResolver(), Secure.USER_SETUP_COMPLETE, 0) != 0;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (EVALUATE_ACTION.equals(intent != null ? intent.getAction() : ""))
            evaluateCarrierBundle(this);
    }
}
