package org.roundware.rwapp;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;

import org.roundware.service.RW;
import org.roundware.service.RWService;

import java.util.UUID;

/**
 * Extends Activity to bind RwService
 */
public abstract class RwBoundActivity extends Activity {

    protected RWService mRwBinder = null;
    private static String mDeviceId = null;
    private static String mProjectId = null;

    /**
     * Override to do additional handling of onServiceConnected
     * @param service
     */
    protected abstract void handleOnServiceConnected(RWService service);
    protected void handleOnServiceDisconnected() { }

    private ServiceConnection rwConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mRwBinder = ((RWService.RWServiceBinder) service).getService();
            mRwBinder.bindActivity(RwBoundActivity.this);
            handleOnServiceConnected(mRwBinder);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mRwBinder = null;
        }
    };


    @Override
    protected void onStart() {
        super.onStart();
        try {
            bindService(getRwServiceIntent(), rwConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected Intent getRwServiceIntent(){
        Intent intent = new Intent(this, RWService.class);
        if(mDeviceId == null){
            restoreRoundwareDeviceIdSetting();
        }
        if(mDeviceId == null){
            mDeviceId = UUID.randomUUID().toString();
            saveRoundwareDeviceIdSetting();
        }
        intent.putExtra(RW.EXTRA_DEVICE_ID, mDeviceId);
        if(mProjectId == null){
            mProjectId = getString(R.string.rw_spec_project_id);
        }
        intent.putExtra(RW.EXTRA_PROJECT_ID, mProjectId);

        // notification customizations
        intent.putExtra(RW.EXTRA_NOTIFICATION_TITLE, getString(R.string.notification_title));
        intent.putExtra(RW.EXTRA_NOTIFICATION_DEFAULT_TEXT, getString(R.string.notification_default_text));
        intent.putExtra(RW.EXTRA_NOTIFICATION_ICON_ID, R.drawable.status_icon);
        intent.putExtra(RW.EXTRA_NOTIFICATION_COLOR, getResources().getColor(R.color.bg_notification));

        return intent;
    }

    @Override
    protected void onStop() {
        mRwBinder.unbindActivity();
        if (rwConnection != null) {
            unbindService(rwConnection);
        }
        super.onStop();
    }


    /**
     * Restores app settings from shared preferences.
     */
    private void restoreRoundwareDeviceIdSetting() {
        SharedPreferences prefs = Settings.getSharedPreferences();
        mDeviceId = prefs.getString(Settings.PREFS_KEY_RW_DEVICE_ID, mDeviceId);
    }
    /**
     * Saves app settings as shared preferences.
     */
    private void saveRoundwareDeviceIdSetting() {
        SharedPreferences prefs = Settings.getSharedPreferences();
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putString(Settings.PREFS_KEY_RW_DEVICE_ID, mDeviceId);
        prefsEditor.apply();
    }

}
