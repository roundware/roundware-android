package org.famsf.roundware;

import android.app.Application;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

/**
 * Created by jschnall on 8/13/14.
 */
public class MyApplication extends Application {
    public static final String LOGTAG = MyApplication.class.getSimpleName();

    // Sites
    public final static int DE_YOUNG = 0;
    public final static int LEGION_OF_HONOR = 1;

    public final static Double DY_LATITUDE = 37.7715173;
    public final static Double DY_LONGITUDE = -122.46873260000001;

    public final static Double LOH_LATITUDE = 37.7844661;
    public final static Double LOH_LONGITUDE = -122.50084190000001;


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

    public static int getSite(Location location) {
        Location dy = new Location("");
        dy.setLatitude(DY_LATITUDE);
        dy.setLongitude((DY_LONGITUDE));

        Location loh = new Location("");
        loh.setLatitude(LOH_LATITUDE);
        loh.setLongitude(LOH_LONGITUDE);

        float dyDist = location.distanceTo(dy);
        float lohDist = location.distanceTo(loh);

        if (dyDist < lohDist) {
            return DE_YOUNG;
        }
        return LEGION_OF_HONOR;
    }
}
