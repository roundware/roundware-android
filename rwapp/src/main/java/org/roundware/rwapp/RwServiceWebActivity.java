package org.roundware.rwapp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import org.roundware.service.RWService;

/**
 * Extends RwWebActivity to bind RwService
 */
public abstract class RwServiceWebActivity extends RwWebActivity{

    protected RWService mRwBinder = null;

    /**
     * Override to do additional handling of onServiceConnected
     * @param service
     */
    protected void handleOnServiceConnected(RWService service) {}

    private ServiceConnection rwConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mRwBinder = ((RWService.RWServiceBinder) service).getService();
            handleOnServiceConnected(mRwBinder);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mRwBinder = null;
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Intent bindIntent = new Intent(this, RWService.class);
            Log.d(LOGTAG, "binding");
            bindService(bindIntent, rwConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        if (rwConnection != null) {
            unbindService(rwConnection);
        }
        super.onDestroy();
    }
}
