package org.roundware.rwapp;

import android.app.Application;
import android.content.Context;

import org.roundware.rwapp.utils.ClassRegistry;


public class RwApplication extends Application {
    public static final String LOGTAG = RwApplication.class.getSimpleName();
    private static RwApplication mInstance = null;

    public static RwApplication getInstance() {
        return mInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mInstance = this;

        //Activity classes can be overwritten in RwApplication sub-classes.
        ClassRegistry.register("RwMainActivity", RwMainActivity.class);
        ClassRegistry.register("RwSpeakActivity", RwSpeakActivity.class);
        ClassRegistry.register("RwListenActivity", RwListenActivity.class);
        ClassRegistry.register("RwExploreActivity", RwExploreActivity.class);
    }

    // Src: http://stackoverflow.com/questions/2002288/static-way-to-get-context-on-android
    public static Context getAppContext() {
        return getInstance().getApplicationContext();
    }
}

