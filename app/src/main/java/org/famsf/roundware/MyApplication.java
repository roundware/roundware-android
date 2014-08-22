package org.famsf.roundware;

import android.app.Application;
import android.content.Context;

/**
 * Created by jschnall on 8/13/14.
 */
public class MyApplication extends Application {
    public static final String LOGTAG = MyApplication.class.getSimpleName();

    private static MyApplication mInstance = null;

    public static MyApplication getInstance() {
        return mInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mInstance = this;
    }

    public static Context getAppContext() {
        return getInstance().getApplicationContext();
    }
}
