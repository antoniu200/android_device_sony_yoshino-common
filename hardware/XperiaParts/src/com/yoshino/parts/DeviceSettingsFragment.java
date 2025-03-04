/*
 * Copyright (c) 2020, Shashank Verma (shank03) <shashank.verma2002@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 */

package com.yoshino.parts;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.SwitchPreference;
import com.android.internal.telephony.RILConstants;

import static com.yoshino.parts.Constants.*;

public class DeviceSettingsFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {
    private static final String CS_PACKAGE_NAME = "com.sonymobile.customizationselector";
    private static final String PREF_EXTRA_KEY = "pref";
    private static final int PREF_IMS = 0;
    private static final int PREF_REAPPLY_MODEM = 1;

    private Intent make_CS_ActivityIntent(String className) {
        return new Intent().setClassName(CS_PACKAGE_NAME, CS_PACKAGE_NAME + "." + className);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String key) {
        addPreferencesFromResource(R.xml.device_settings);

        SwitchPreference glovePref = findPreference(GLOVE_MODE);
        assert glovePref != null;
        glovePref.setChecked(Settings.System.getInt(glovePref.getContext().getContentResolver(), GLOVE_MODE, 0) == 1);
        glovePref.setOnPreferenceChangeListener(this);

        SwitchPreference smartStaminPref = findPreference(SMART_STAMINA_MODE);
        assert smartStaminPref != null;
        smartStaminPref.setChecked(Settings.System.getInt(smartStaminPref.getContext().getContentResolver(), SMART_STAMINA_MODE, 0) == 1);
        smartStaminPref.setOnPreferenceChangeListener(this);

        SwitchPreference notificationPref = findPreference(CS_NOTIFICATION);
        assert notificationPref != null;
        notificationPref.setChecked(Settings.System.getInt(notificationPref.getContext().getContentResolver(),
                CS_NOTIFICATION, 1) == 1);
        notificationPref.setOnPreferenceChangeListener(this);

        Preference statusPref = findPreference(CS_STATUS);
        assert statusPref != null;
        statusPref.setOnPreferenceClickListener(preference -> {
            preference.getContext().startActivity(make_CS_ActivityIntent("StatusActivity"));
            return true;
        });

        Preference logPref = findPreference(CS_LOG);
        assert logPref != null;
        logPref.setOnPreferenceClickListener(preference -> {
            preference.getContext().startActivity(make_CS_ActivityIntent("LogActivity"));
            return true;
        });

        Preference msActPref = findPreference(MODEM_SWITCHER);
        assert msActPref != null;
        msActPref.setOnPreferenceClickListener(preference -> {
            preference.getContext().startActivity(make_CS_ActivityIntent("ModemSwitcherActivity"));
            return true;
        });

        Preference slotPref = findPreference(NS_SLOT);
        SwitchPreference nsService = findPreference(NS_SERVICE);
        Preference nsLowerNetwork = findPreference(NS_LOWER_NETWORK);
        if (slotPref != null && nsService != null) {
            if (getContext().getSystemService(TelephonyManager.class).getPhoneCount() > 1) {
                slotPref.setVisible(true);

                int slot = Settings.System.getInt(slotPref.getContext().getContentResolver(), NS_SLOT, -1);
                slotPref.setSummary(slotPref.getContext().getString(R.string.sim_slot_summary) + (slot == -1 ? " INVALID" : " " + (slot + 1)));

                slotPref.setOnPreferenceClickListener(preference -> {
                    final ContentResolver resolver = preference.getContext().getContentResolver();
                    int nSlot = Settings.System.getInt(resolver, NS_SLOT, -1);
                    switch (nSlot) {
                        case 0:
                            Settings.System.putInt(resolver, NS_SLOT, 1);
                            slotPref.setSummary(slotPref.getContext().getString(R.string.sim_slot_summary) + " 2");
                            nsService.setEnabled(true);
                            break;
                        case 1:
                            Settings.System.putInt(resolver, NS_SLOT, -1);
                            slotPref.setSummary(slotPref.getContext().getString(R.string.sim_slot_summary) + " INVALID");
                            nsService.setEnabled(false);
                            break;
                        case -1:
                            Settings.System.putInt(resolver, NS_SLOT, 0);
                            slotPref.setSummary(slotPref.getContext().getString(R.string.sim_slot_summary) + " 1");
                            nsService.setEnabled(true);
                            break;
                    }
                    return true;
                });

                nsService.setEnabled(slot != -1);
            } else {
                slotPref.setVisible(false);
                nsService.setEnabled(true);
            }
            nsService.setChecked(Settings.System.getInt(nsService.getContext().getContentResolver(), NS_SERVICE, 0) == 1);
            nsService.setOnPreferenceChangeListener(this);

            updateLowerNetworkPref(nsLowerNetwork, nsService.isChecked());
            nsLowerNetwork.setOnPreferenceClickListener(preference -> {
                final ContentResolver resolver = preference.getContext().getContentResolver();
                int network = getLowerNetwork(resolver);
                if(network == RILConstants.NETWORK_MODE_WCDMA_PREF)
                    network = RILConstants.NETWORK_MODE_GSM_ONLY;
                else if(network == RILConstants.NETWORK_MODE_GSM_ONLY)
                    network = RILConstants.NETWORK_MODE_GSM_UMTS;
                else
                    network = RILConstants.NETWORK_MODE_WCDMA_PREF;
                Settings.System.putInt(resolver, NS_LOWER_NETWORK, network);
                updateLowerNetworkPref(preference, true);
                return true;
            });
        }

        SwitchPreference imsPref = findPreference(CS_IMS);
        assert imsPref != null;
        if (Settings.System.getInt(imsPref.getContext().getContentResolver(), CS_IMS, 1) == 0) {
            imsPref.setChecked(false);
            notificationPref.setEnabled(false);
            msActPref.setEnabled(false);
        } else {
            imsPref.setChecked(true);
            notificationPref.setEnabled(true);
            msActPref.setEnabled(true);
        }
        imsPref.setOnPreferenceClickListener(preference -> {
            int ims = Settings.System.getInt(imsPref.getContext().getContentResolver(), CS_IMS, 1);
            if (ims == 1) {
                AlertDialog.Builder builder = new AlertDialog.Builder(imsPref.getContext());
                builder.setCancelable(false);
                builder.setTitle("Disable IMS features ?");
                builder.setMessage("You will lose all the carrier specific features such as VoLTE, VoWiFi and " +
                        "WiFi Calling; and Your device will switch to default modem config with basic mobile data feature.");
                builder.setPositiveButton("Disable", (dialogInterface, i) -> {
                    dialogInterface.dismiss();
                    Settings.System.putInt(imsPref.getContext().getContentResolver(), CS_IMS, 0);
                    imsPref.setChecked(false);
                    notificationPref.setEnabled(false);
                    msActPref.setEnabled(false);

                    sendBroadcast(preference.getContext(), CS_IMS);
                });
                builder.setNegativeButton(R.string.cancel_button_label, (dialogInterface, i) -> {
                    dialogInterface.dismiss();
                    imsPref.setChecked(true);
                });
                builder.create().show();
            }
            if (ims == 0) {
                AlertDialog.Builder builder = new AlertDialog.Builder(imsPref.getContext());
                builder.setCancelable(false);
                builder.setTitle("Enable IMS features?");
                builder.setMessage("This will allow you to use the provided carrier specific features such as VoLTE, " +
                        "VoWiFi and WiFi Calling; only if it worked on stock.\n\nYour device will prompt reboot if it " +
                        "found carrier specific modem.");
                builder.setPositiveButton("Enable", (dialogInterface, i) -> {
                    dialogInterface.dismiss();
                    Settings.System.putInt(imsPref.getContext().getContentResolver(), CS_IMS, 1);
                    imsPref.setChecked(true);
                    notificationPref.setEnabled(true);
                    msActPref.setEnabled(true);

                    sendBroadcast(preference.getContext(), CS_IMS);
                });
                builder.setNegativeButton(R.string.cancel_button_label, (dialogInterface, i) -> {
                    dialogInterface.dismiss();
                    imsPref.setChecked(false);
                });
                builder.create().show();
            }
            return true;
        });

        SwitchPreference modemPref = findPreference(CS_RE_APPLY_MODEM);
        assert modemPref != null;
        modemPref.setChecked(Settings.System.getInt(modemPref.getContext().getContentResolver(), CS_RE_APPLY_MODEM, 1) == 1);
        modemPref.setOnPreferenceClickListener(preference -> {
            int applyModem = Settings.System.getInt(modemPref.getContext().getContentResolver(), CS_RE_APPLY_MODEM, 1);

            AlertDialog.Builder builder = new AlertDialog.Builder(modemPref.getContext());
            builder.setCancelable(false);
            builder.setTitle("Reboot required");
            builder.setMessage("A reboot is required to " + (applyModem == 0 ? "enable" : "disable") + " this feature. Are you sure you want to reboot ?");
            builder.setPositiveButton("Reboot", (dialogInterface, i) -> {
                dialogInterface.dismiss();
                Settings.System.putInt(modemPref.getContext().getContentResolver(), CS_RE_APPLY_MODEM, (applyModem ^ 1));
                modemPref.setChecked(applyModem == 0);

                sendBroadcast(preference.getContext(), CS_RE_APPLY_MODEM);
            });
            builder.setNegativeButton(R.string.cancel_button_label, (dialogInterface, i) -> {
                dialogInterface.dismiss();
                modemPref.setChecked(applyModem == 1);
            });
            builder.create().show();
            return true;
        });
    }

    private static int getLowerNetwork(ContentResolver resolver)
    {
        return Settings.System.getInt(resolver, NS_LOWER_NETWORK, RILConstants.NETWORK_MODE_WCDMA_PREF);
    }

    private static String getNetworkName(int network) {
        switch(network) {
            case RILConstants.NETWORK_MODE_GSM_ONLY:
                return "2G";
            case RILConstants.NETWORK_MODE_WCDMA_PREF:
                return "3G";
            case RILConstants.NETWORK_MODE_GSM_UMTS:
                return "2G/3G";
            default:
                return "N/A";
        }
    }

    private static void updateLowerNetworkPref(Preference lnPref, boolean enabled) {
        int network = getLowerNetwork(lnPref.getContext().getContentResolver());
        lnPref.setSummary(lnPref.getContext().getString(R.string.lower_network_summary) + getNetworkName(network));
        lnPref.setEnabled(enabled);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
        boolean enabled = (boolean) o;
        switch (preference.getKey()) {
            case GLOVE_MODE:
                SystemProperties.set(GLOVE_PROP, (boolean) o ? "1" : "0");
                break;
            case SMART_STAMINA_MODE:
                SystemProperties.set(SMART_STAMINA_PROP, (boolean) o ? "1" : "0");
                break;
            case CS_NOTIFICATION:
                break;
            case NS_SERVICE:
                updateLowerNetworkPref(findPreference(NS_LOWER_NETWORK), enabled);
                break;
            default:
                return false;
        }
        Settings.System.putInt(preference.getContext().getContentResolver(), preference.getKey(), enabled ? 1 : 0);
        return true;
    }

    private void sendBroadcast(Context context, String pref) throws IllegalArgumentException{
        Intent broadcast = new Intent()
            .putExtra(pref, Settings.System.getInt(context.getContentResolver(), pref, 1))
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            .setComponent(new ComponentName(CS_PACKAGE_NAME, CS_PACKAGE_NAME + ".PreferenceReceiver"));
        if (pref.equals(CS_IMS))
            broadcast.putExtra(PREF_EXTRA_KEY, PREF_IMS);
        else if (pref.equals(CS_RE_APPLY_MODEM))
            broadcast.putExtra(PREF_EXTRA_KEY, PREF_REAPPLY_MODEM);
        else
            throw new IllegalArgumentException("Wrong preference: " + pref);
        context.sendBroadcast(broadcast);
    }
}
