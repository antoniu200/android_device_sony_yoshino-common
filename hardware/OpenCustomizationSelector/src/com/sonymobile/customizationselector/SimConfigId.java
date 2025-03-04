package com.sonymobile.customizationselector;

import android.content.Context;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import com.sonymobile.customizationselector.Parser.ServiceProvidersParser;

import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import static com.sonymobile.customizationselector.Parser.XmlConstants.*;

public class SimConfigId {

    private static final String TAG = "SimConfigId";

    private static final int MCC_LENGTH = 3;

    private final Context mContext;

    public SimConfigId(Context context) {
        mContext = context;
    }

    public static HashMap<String, String> extractSimInfo(TelephonyManager tm, int subID) {
        HashMap<String, String> hashMap = new HashMap<>();

        String simOperator = tm.getSimOperator(subID);
        String simOperatorName = tm.getSimOperatorName(subID);
        String subscriberId = tm.getSubscriberId(subID);
        String groupIdLevel1 = tm.getGroupIdLevel1(subID);
        String simSerialNumber = tm.getSimSerialNumber(subID);

        if (!TextUtils.isEmpty(simOperator) && !TextUtils.isEmpty(subscriberId)) {
            simOperatorName = simOperatorName != null ? simOperatorName.replaceAll("[\n\r]", "") : "";
            if (groupIdLevel1 == null)
                groupIdLevel1 = "";
            if (simSerialNumber == null)
                simSerialNumber = "";
            hashMap.put(MCC, simOperator.substring(0, MCC_LENGTH));
            hashMap.put(MNC, simOperator.substring(MCC_LENGTH));
            hashMap.put(SP, simOperatorName.trim());
            hashMap.put(IMSI, subscriberId);
            hashMap.put(GID1, groupIdLevel1);
            hashMap.put(ICCID, simSerialNumber);
            hashMap.put(SUBSCRIPTION, String.valueOf(subID));
        }
        return hashMap;
    }

    private String getIdFromSimValues(HashMap<String, String> hashMap) {
        return getMappingMatch(ServiceProvidersParser.getServiceProviders(mContext), hashMap);
    }

    private String getMappingMatch(List<SimCombination> list, HashMap<String, String> simParams) {
        String simConfigId = null;
        int numberOfMatches = 0;
        for (SimCombination simCombo : list) {
            int count = 0;
            if (simCombo.getMCC() != null) {
                if (simCombo.getMCC().equals(simParams.get(MCC))) {
                    if (simCombo.getMNC() != null) {
                        if (simCombo.getMNC().equals(simParams.get(MNC))) {
                            CSLog.d(TAG, "getMappingMatch - mcc: " + simCombo.getMCC() + " mnc: " + simCombo.getMNC() + " for: " + simCombo.getSimConfigId());
                            count++;
                            if (simCombo.getServiceProvider() != null) {
                                if (!matchOnSP(simCombo.getServiceProvider(), simParams.get(SP))) {
                                    CSLog.d(TAG, "getMappingMatch - Go to next simCombination since there is no match on Service provider for: "
                                            + simCombo.getSimConfigId());
                                    count--;
                                } else {
                                    CSLog.d(TAG, "getMappingMatch - sp: " + simCombo.getServiceProvider() + " for: " + simCombo.getSimConfigId());
                                    count++;
                                }
                            }
                            if (simCombo.getIMSI() != null) {
                                if (!matchOnImsi(simCombo.getIMSI(), simParams.get(IMSI))) {
                                    CSLog.d(TAG, "getMappingMatch - Go to next simCombination since there is no match on IMSI for: " + simCombo.getSimConfigId());
                                    count--;
                                } else {
                                    CSLog.d(TAG, "getMappingMatch - imsi: " + simCombo.getIMSI() + " for: " + simCombo.getSimConfigId());
                                    count++;
                                }
                            }
                            if (simCombo.getGid1() != null) {
                                if (simParams.get(GID1) == null || !simParams.get(GID1).toLowerCase().startsWith(simCombo.getGid1().toLowerCase())) {
                                    CSLog.d(TAG, "getMappingMatch - Go to next simCombination since there is no match on GID1 for: " + simCombo.getGid1());
                                    count--;
                                } else {
                                    CSLog.d(TAG, "getMappingMatch - gid1: " + simCombo.getGid1() + " for: " + simCombo.getSimConfigId());
                                    count++;
                                }
                            }
                        }
                    }
                }
            }
            if (count > numberOfMatches) {
                numberOfMatches = count;
                simConfigId = simCombo.getSimConfigId();
                CSLog.d(TAG, "Saving id: " + simConfigId + " - nbr matches: " + numberOfMatches);
            }
        }
        return simConfigId;
    }

    private boolean matchOnImsi(String pattern, String s) {
        return s != null && Pattern.compile(pattern).matcher(s).matches();
    }

    private boolean matchOnSP(String pattern, String s) {
        if (NULL_VALUE.equalsIgnoreCase(pattern))
            return TextUtils.isEmpty(s) || NULL_VALUE.equalsIgnoreCase(s);
        else
            return s != null && Pattern.compile(pattern).matcher(s).matches();
    }

    public String getId() {
        String id = "";
        TelephonyManager tm = mContext.getSystemService(TelephonyManager.class);
        int subId = CommonUtil.getDefaultSubId(mContext);

        if (tm != null && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            HashMap<String, String> simInfo = extractSimInfo(tm, subId);
            CSLog.d(TAG, "***********************************");
            CSLog.d(TAG, "extractSimInfo: " + simInfo.toString());
            CSLog.d(TAG, "***********************************");
            id = getIdFromSimValues(simInfo);
        }

        CSLog.d(TAG, "***********************************");
        StringBuilder sb = new StringBuilder();
        sb.append("Best SIM configuration id= ");
        if (TextUtils.isEmpty(id))
            sb.append("NOT FOUND - RETURNING \"\"");
        else
            sb.append(id);
        CSLog.d(TAG, sb.toString());
        CSLog.d(TAG, "***********************************");
        return id;
    }
}
