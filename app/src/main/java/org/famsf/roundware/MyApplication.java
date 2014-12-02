package org.famsf.roundware;

import android.app.Application;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

public class MyApplication extends Application {
    private static MyApplication mInstance = null;

    public static MyApplication getInstance() {
        return mInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mInstance = this;
    }
    // Src: http://stackoverflow.com/questions/2002288/static-way-to-get-context-on-android
    public static Context getAppContext() {
        return getInstance().getApplicationContext();
    }
}
