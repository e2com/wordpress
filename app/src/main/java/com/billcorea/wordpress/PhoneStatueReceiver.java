package com.billcorea.wordpress;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;

public class PhoneStatueReceiver extends BroadcastReceiver {
    String TAG = "PhoneStatueReceiver";
    @Override
    public void onReceive(Context context, Intent intent) {

        Bundle extras = intent.getExtras();
        if(extras == null)
            return;

        String name = intent.getAction() ;
        if (name.equals("android.intent.action.MEDIA_SCANNER_FINISHED")) {
            Log.d(TAG, "미디어 검색 종료") ;
        }

        String state = extras.getString(TelephonyManager.EXTRA_STATE);
        Log.d(TAG, state);

        if (state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
            String phoneNumber = extras
                    .getString(TelephonyManager.EXTRA_INCOMING_NUMBER);
            Log.d(TAG, phoneNumber);
        }
    }
}
