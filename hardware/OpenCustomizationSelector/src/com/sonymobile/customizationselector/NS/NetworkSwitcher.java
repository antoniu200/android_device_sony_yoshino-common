package com.sonymobile.customizationselector.NS;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.telephony.RadioAccessFamily;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.RILConstants;
import com.sonymobile.customizationselector.CSLog;
import com.sonymobile.customizationselector.CommonUtil;

/**
 * Service to handle Network mode
 * - On boot if preference network is set to LTE switch to the selected lower network (e.g. 3G)
 * - When the device is unlocked and the SIM has got service connection
 *   switch back to the previous network and exit
 *
 * @author shank03
 */
public class NetworkSwitcher extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static final String TAG = "NetworkSwitcher";
    private static final String NS_LOWER_NETWORK = "ns_lowNet";
    private static final String NS_PREFERRED = "ns_preferred";
    
    private TelephonyManager tm;
    private AirplaneModeObserver mAirplaneModeObserver;
    private SimServiceObserver mSimServiceObserver;
    // Set until the phone is unlocked
    private BroadcastReceiver mUnlockObserver;

    @Override
    public void onCreate() {
        d("onCreate");
        final Context appContext = getApplicationContext();

        mAirplaneModeObserver = new AirplaneModeObserver(appContext, new Handler(getMainLooper()));
        mSimServiceObserver = new SimServiceObserver(appContext);
        mUnlockObserver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                unregisterReceiver(mUnlockObserver);
                mUnlockObserver = null;
            }
        };
        registerReceiver(mUnlockObserver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));

        // Start process
        try {
            if (CommonUtil.isDualSim(appContext))
                d("device is dual sim");
            else
                d("single sim device");

            int subID = CommonUtil.getSubID(appContext);
            if (subID == SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                new SubIdObserver(appContext).register(this::initProcess);
            else
                initProcess(subID);
        } catch (Exception e) {
            CSLog.e(TAG, "Error: ", e);
        }

        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void initProcess(int subID) {
        final Context appContext = getApplicationContext();
        if (CommonUtil.isSIMLoaded(appContext, subID)) {
        int currentNetwork = getPreferredNetwork(subID);
            if (!isLTE(currentNetwork) && CommonUtil.isIMSEnabledBySetting(appContext)) {
            	int mRetryCount;
            	
            	// Waiting for VoLTE provisioning, trying 5 times
            	for (mRetryCount = 0; !tm.isVolteAvailable() && mRetryCount < 5; mRetryCount++)
	            new Handler(getMainLooper()).postDelayed(() -> {;}, 1400);
	        if (tm.isVolteAvailable())
	            d("initProcess: VoLTE provisioned after " + (char)(mRetryCount + '0') + " / 5 attempt(s).");
	        else d("initProcess: Cannot obtain VoLTE provisioning.");
	        
	        // Waiting for VoWiFi provisioning, trying 5 times
	        for (mRetryCount = 0; !tm.isWifiCallingAvailable() && mRetryCount < 5; mRetryCount++)
	            new Handler(getMainLooper()).postDelayed(() -> {;}, 1400);
	        if (tm.isWifiCallingAvailable())
	            d("initProcess: VoWiFi provisioned after " + (char)(mRetryCount + '0') + " / 5 attempt(s).");
	        else d("initProcess: Cannot obtain VoWiFi provisioning.");
	        
	        // Waiting for ViLTE provisioning, trying 5 times
	        for (mRetryCount = 0; !tm.isVideoTelephonyAvailable() && mRetryCount < 5; mRetryCount++)
	            new Handler(getMainLooper()).postDelayed(() -> {;}, 1400);
	        if (tm.isVideoTelephonyAvailable())
	            d("initProcess: ViLTE provisioned after " + (char)(mRetryCount + '0') + " / 5 attempt(s).");
	        else d("initProcess: Cannot obtain ViLTE provisioning.");
	        
	        switchDown(subID);
            }
            else new Handler(getMainLooper()).postDelayed(() -> switchDown(subID), 1400);
            
        }
        else {
            new SlotObserver(appContext).register(subID,
                    () -> new Handler(getMainLooper()).postDelayed(() -> switchDown(subID), 1400));
        }
    }

    private void switchDown(int subID) {
        if (subID < 0) {
            d("switchDown: Error, invalid subID");
            stopSelf();
            return;
        }

        int currentNetwork = getPreferredNetwork(subID);
        if (isLTE(currentNetwork)) {
            tm = getSystemService(TelephonyManager.class).createForSubscriptionId(subID);

            setOriginalNetwork(subID, currentNetwork);
            changeNetwork(tm, subID, getLowerNetwork());

            if (CommonUtil.isDirectBootEnabled() && mUnlockObserver != null) {
                // Delay resetting the network until phone is unlocked.
                // The current unlock observer is no longer required
                unregisterReceiver(mUnlockObserver);
                mUnlockObserver = null;
                registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        unregisterReceiver(this);
                        handleConnection(tm, subID);
                    }
                }, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
            } else
                handleConnection(tm, subID);
        } else {
            d("Network is not LTE, no work.");
            stopSelf();
        }
    }

    private void handleConnection(TelephonyManager tm, int subID) {
        if (isAirplaneModeOn()) {
            mAirplaneModeObserver.register(() -> {
                if (isAirplaneModeOn())
                    mSimServiceObserver.unregister();
                else {
                    mSimServiceObserver.register(subID, () -> {
                        mAirplaneModeObserver.unregister();
                        changeNetwork(tm, subID, getOriginalNetwork(subID));
                        stopSelf();
                    });
                }
            });
        } else {
            if (CommonUtil.hasSignal(tm)) {
                changeNetwork(tm, subID, getOriginalNetwork(subID));
                stopSelf();
            } else {
                mSimServiceObserver.register(subID, () -> {
                    changeNetwork(tm, subID, getOriginalNetwork(subID));
                    stopSelf();
                });
            }
        }
    }

    /**
     * The method to change the network
     *
     * @param tm             {@link TelephonyManager} specific to subID
     * @param subID          the subscription ID from [subscriptionsChangedListener]
     * @param newNetwork     network to change to
     */
    private void changeNetwork(TelephonyManager tm, int subID, int newNetwork) {
        d("changeNetwork: To be changed to = " + networkToString(newNetwork));

        if (tm.setPreferredNetworkTypeBitmask(RadioAccessFamily.getRafFromNetworkType(newNetwork))) {
            Settings.Global.putInt(getApplicationContext().getContentResolver(), Settings.Global.PREFERRED_NETWORK_MODE + subID, newNetwork);
            d("changeNetwork: Successfully changed to " + networkToString(newNetwork));
        }
    }

    /**
     * Get the current in-use network mode preference
     *
     * @return default 3G {@link RILConstants#NETWORK_MODE_WCDMA_PREF} if no pref stored
     */
    private int getPreferredNetwork(int subID) {
        return Settings.Global.getInt(getApplicationContext().getContentResolver(),
                Settings.Global.PREFERRED_NETWORK_MODE + subID, RILConstants.NETWORK_MODE_WCDMA_PREF);
    }

    /**
     * Get the original network mode preference
     *
     * @return Stored value, defaults to {@link RILConstants#NETWORK_MODE_LTE_GSM_WCDMA}
     */
    private int getOriginalNetwork(int subID) {
        return Settings.System.getInt(getApplicationContext().getContentResolver(), NS_PREFERRED + subID,
                                      RILConstants.NETWORK_MODE_LTE_GSM_WCDMA);
    }

    private void setOriginalNetwork(int subID, int network) {
        Settings.System.putInt(getApplicationContext().getContentResolver(), NS_PREFERRED + subID, network);
    }

    /**
     * Returns whether @param network is LTE or not
     */
    private boolean isLTE(int network) {
        int lteMask = RadioAccessFamily.RAF_LTE | RadioAccessFamily.RAF_LTE_CA;
        return (RadioAccessFamily.getRafFromNetworkType(network) & lteMask) != 0;
    }

    /**
     * This method returns the lower network to switch to
     */
    private int getLowerNetwork() {
        return Settings.System.getInt(getApplicationContext().getContentResolver(), NS_LOWER_NETWORK, RILConstants.NETWORK_MODE_WCDMA_PREF);
    }

    /**
     * Get the string version of the variables.
     * <p>
     * Too lazy to refer the {@link RILConstants}
     */
    private String networkToString(int network) {
        switch (network) {
            case RILConstants.NETWORK_MODE_WCDMA_PREF:
                return "NETWORK_MODE_WCDMA_PREF";
            case RILConstants.NETWORK_MODE_WCDMA_ONLY:
                return "NETWORK_MODE_WCDMA_ONLY";
            case RILConstants.NETWORK_MODE_GSM_UMTS:
                return "NETWORK_MODE_GSM_UMTS";
            case RILConstants.NETWORK_MODE_GSM_ONLY:
                return "NETWORK_MODE_GSM_ONLY";
            case RILConstants.NETWORK_MODE_LTE_GSM_WCDMA:
                return "NETWORK_MODE_LTE_GSM_WCDMA";
            case RILConstants.NETWORK_MODE_LTE_ONLY:
                return "NETWORK_MODE_LTE_ONLY";
            case RILConstants.NETWORK_MODE_LTE_WCDMA:
                return "NETWORK_MODE_LTE_WCDMA";
            case RILConstants.NETWORK_MODE_GLOBAL:
                return "NETWORK_MODE_GLOBAL";
            case RILConstants.NETWORK_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                return "NETWORK_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA";
            case RILConstants.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                return "NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA";
            default:
                return "N/A(" + network + ")";
        }
    }

    /**
     * Gets the state of Airplane Mode.
     *
     * @return true if enabled.
     */
    private boolean isAirplaneModeOn() {
        return Settings.System.getInt(getApplicationContext().getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    private void d(String msg) {
        CSLog.d(TAG, msg);
    }
}
