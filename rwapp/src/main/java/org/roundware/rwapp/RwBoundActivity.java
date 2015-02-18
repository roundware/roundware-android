package org.roundware.rwapp;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import org.roundware.service.RWService;

/**
 * Extends Activity to bind RwService
 */
public abstract class RwBoundActivity extends Activity {

    protected RWService mRwBinder = null;

    /**
     * Override to do additional handling of onServiceConnected
     * @param service
     */
    protected abstract void handleOnServiceConnected(RWService service);

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
            Intent bindIntent = new Intent(this, RWService.class);
            bindService(bindIntent, rwConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStop() {
        mRwBinder.unbindActivity();
        if (rwConnection != null) {
            unbindService(rwConnection);
        }
        super.onStop();
    }

}
