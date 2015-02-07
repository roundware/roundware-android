/**
 * Roundware Android code is released under the terms of the GNU General Public License.
 * See COPYRIGHT.txt, AUTHORS.txt, and LICENSE.txt in the project root directory for details.
 */
package org.roundware.service;

import android.content.Context;
import android.location.Criteria;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.util.Observable;


/**
 * Singleton class providing GPS/Location functionality. It extends Observable
 * so it can be observed to get updates on location changes. For testing
 * purposes, amongst others, it can be fixed at a specific location.
 * <p/>
 * For this singleton class you need to call the init method first, and then
 * can call the startLocationUpdates method to active the updates. Use the
 * stopLocationUpdates to cancel the updates (and save the battery).
 *
 * @author Rob Knapen
 */
public class RWLocationTracker extends Observable {

    // debugging
    private final static String TAG = "RWLocationTracker";
    private final static boolean D = false;

    private static RWLocationTracker mSingleton;

    private Context mContext;
    private LocationManager mLocationManager;
    private String mCoarseLocationProvider;
    private String mGpsLocationProvider;
    private boolean mGpsLocationAvailable;
    private boolean mUseGpsIfPossible;
    private long mMinUpdateTime;
    private float mMinUpdateDistance;
    private Location mLastLocation;
    private boolean mFixedLocation;
    private boolean mUsingGpsLocation;
    private boolean mUsingCoarseLocation;


    /**
     * LocationListener for the coarse (Network) location provider. Used
     * to get notified when a new location is available.
     */
    private final LocationListener mCoarseLocationProviderListener = new LocationListener() {

        public void onStatusChanged(String provider, int status, Bundle extras) {
            if (D) { Log.d(TAG, "Coarse location provider status changed"); }
        }

        public void onProviderEnabled(String provider) {
            if (D) { Log.d(TAG, "Coarse location provider enabled - finding last know location"); }
            gotoLastKnownLocation();
        }

        public void onProviderDisabled(String provider) {
            if (D) { Log.d(TAG, "Coarse location provider disabled"); }
            updateWithNewLocation(null);
        }

        public void onLocationChanged(Location location) {
            if (D) { Log.d(TAG, "Coarse location provider location changed"); }
            if (mUsingCoarseLocation) {
                gotoLastKnownLocation();
            }
        }
    };


    /**
     * LocationListener for the GPS location provider. Used to get
     * notified when a new location is available.
     */
    private final LocationListener mGpsLocationProviderListener = new LocationListener() {

        public void onStatusChanged(String provider, int status, Bundle extras) {
            if (D) { Log.d(TAG, "GPS location provider status changed"); }
        }

        public void onProviderEnabled(String provider) {
            if (D) { Log.d(TAG, "GPS location provider enabled"); }
            // not switching yet, waiting for first fix
        }

        public void onProviderDisabled(String provider) {
            if (D) { Log.d(TAG, "GPS location provider disabled"); }
        }

        public void onLocationChanged(Location location) {
            if (D) { Log.d(TAG, "GPS location provider location update"); }
            if (mUsingGpsLocation) {
                gotoLastKnownLocation();
            }
        }
    };


    /**
     * GPS status listener, used to get notified about GPS availability and
     * coordinate fixes. When GPS is available and has a first coordinate fix
     * we will start using it, if it is no we switch back to using the coarse
     * location provider.
     */
    private final GpsStatus.Listener mGpsStatusListener = new GpsStatus.Listener() {
        @Override
        public void onGpsStatusChanged(int event) {
            switch (event) {
                case GpsStatus.GPS_EVENT_FIRST_FIX:
                    if (D) { Log.d(TAG, "GPS first fix"); }
                    mGpsLocationAvailable = true;
                    swithToGpsLocationUpdates();
                    break;
                case GpsStatus.GPS_EVENT_STARTED:
                    if (D) { Log.d(TAG, "GPS started"); }
                    mGpsLocationAvailable = false;
                    break;
                case GpsStatus.GPS_EVENT_STOPPED:
                    if (D) { Log.d(TAG, "GPS stopped"); }
                    mGpsLocationAvailable = false;
                    switchToCoarseLocationUpdates();
                    break;
            }
        }
    };


    /**
     * Accesses the singleton instance.
     * 
     * @return singleton instance of the class
     */
    public static RWLocationTracker instance() {
        if (mSingleton == null) {
            mSingleton = new RWLocationTracker();
        }
        return mSingleton;
    }


    /**
     * Hidden constructor for the singleton class.
     */
    private RWLocationTracker() {
        mFixedLocation = false;
        mMinUpdateTime = -1;
        mMinUpdateDistance = -1;
        mUseGpsIfPossible = false;
        mUsingGpsLocation = false;
        mUsingCoarseLocation = false;
    }


    /**
     * Checks if a fixed location has been set for the location tracker. When
     * set all location updates will be ignored.
     * 
     * @return true is a fixed location is set
     */
    public boolean isUsingFixedLocation() {
        return mFixedLocation;
    }


    /**
     * Sets the specified fixed location for the location tracker. Location
     * updates will be ignored until the fixed location is released again.
     * 
     * @param latitude of the fixed location
     * @param longitude of the fixed location
     */
    public void fixLocationAt(Double latitude, Double longitude) {
        Location l = new Location(mCoarseLocationProvider);
        l.setLatitude(latitude);
        l.setLongitude(longitude);
        fixLocationAt(l);
    }


    /**
     * Sets the specified fixed location for the location tracker. Location
     * updates will be ignored until the fixed location is released again.
     * 
     * @param location to use as fixed location
     */
    public void fixLocationAt(Location location) {
        updateWithNewLocation(location);
        mFixedLocation = true;
    }


    /**
     * Releases the fixed location and returns to using regular location
     * updates.
     */
    public void releaseFixedLocation() {
        mFixedLocation = false;
        gotoLastKnownLocation();
    }


    /**
     * Checks if the GPS is enabled and available on the device.
     * 
     * @return true when GPS is available
     */
    public boolean isGpsEnabled() {
        LocationManager lm = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) {
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        }
        return false;
    }


    /**
     * Checks if the Network location provider is enabled on the device.
     * 
     * @return true when Network location is available
     */
    public boolean isNetworkLocationEnabled() {
        LocationManager lm = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) {
            return lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        }
        return false;
    }


    /**
     * Initializes the singleton instance for the specified context. This
     * context is used to access the location providers. The method checks
     * if at least one location provider is available and returns true if
     * it is, false otherwise (no location data available).
     * 
     * @param context to be used
     * @return true if successful and a location provider is available
     */
    public boolean init(Context context) {
        if (mLocationManager != null) {
            stopLocationUpdates();
            mLocationManager = null;
            mCoarseLocationProvider = null;
            mGpsLocationProvider = null;
        }

        mContext = context;

        mLocationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        if (mLocationManager == null) {
            Toast.makeText(context, R.string.roundware_no_location_service, Toast.LENGTH_SHORT).show();
            return false;
        }

        boolean rc = getLocationProviders();
        gotoLastKnownLocation();
        return rc;
    }


    /**
     * Collects information about the available location providers on the
     * device, and stores it in instance variables.
     * 
     * @return true if at least one location provider is available
     */
    public boolean getLocationProviders() {
        // get the GPS location provider info
        LocationProvider provider = mLocationManager.getProvider(LocationManager.GPS_PROVIDER);
        if (provider != null) {
            mGpsLocationProvider = provider.getName();
            if (D) { Log.d(TAG, "GPS location provider name : " + mGpsLocationProvider); }
        } else {
            if (D) { Log.d(TAG, "GPS location provider not found on this device"); }
            mGpsLocationProvider = null;
        }

        mGpsLocationAvailable = false;

        // get a coarse (usually the network) location provider as backup
        Criteria coarseCriteria = new Criteria();
        coarseCriteria.setAccuracy(Criteria.ACCURACY_COARSE);
        coarseCriteria.setAltitudeRequired(false);
        coarseCriteria.setBearingRequired(false);
        coarseCriteria.setCostAllowed(false);
        coarseCriteria.setPowerRequirement(Criteria.NO_REQUIREMENT);

        mCoarseLocationProvider = mLocationManager.getBestProvider(coarseCriteria, true);
        if (D) { Log.d(TAG, "Coarse location provider name : " + mCoarseLocationProvider); }

        // need to have at least one
        if ((mGpsLocationProvider == null) && (mCoarseLocationProvider == null)) {
            Toast.makeText(mContext, R.string.roundware_no_location_signal, Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }


    /**
     * Gets the last known location information. Note that this is not
     * Necessarily up-to-date.
     * 
     * @return Location with last know position
     */
    public Location getLastLocation() {
        return mLastLocation;
    }


    /**
     * Updates internal state according to the specified location.
     * 
     * @param location with new position data
     */
    private void updateWithNewLocation(Location location) {
        if (mFixedLocation) {
            return;
        }

        mLastLocation = location;

        if (D) {
            if (location != null) {
                String msg = String.format("%s: (%.6f, %.6f) %.1fm", location.getProvider(),
                        location.getLatitude(), location.getLongitude(), location.getAccuracy());

                Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(mContext, "No location info", Toast.LENGTH_SHORT).show();
            }
        }

        setChanged();
        notifyObservers();

    }


    /**
     * Accesses the most accurate available location provider on the device
     * to get its last know position and uses it to set the internal state.
     */
    public void gotoLastKnownLocation() {
        if (mLocationManager != null) {
            Location l;
            // check most accurate first
            if (mUsingGpsLocation && mGpsLocationAvailable && (mGpsLocationProvider != null)) {
                l = mLocationManager.getLastKnownLocation(mGpsLocationProvider);
                updateWithNewLocation(l);
                return;
            }

            // use less accurate network location
            if (mCoarseLocationProvider != null) {
                l = mLocationManager.getLastKnownLocation(mCoarseLocationProvider);
                updateWithNewLocation(l);
                return;
            }
        }

        if (mContext != null) {
            Toast.makeText(mContext, R.string.roundware_lost_location_signal, Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * Starts receiving location updates from the devices' location providers.
     * Initially the Network location will be used and a listener started to
     * wait for GPS availability and first coordinate fix, which then will be
     * switched to.
     * 
     * @param minTime (msec) allowed between location updates
     * @param minDistance (m) for location updates
     * @param useGps when available on the device
     */
    public void startLocationUpdates(long minTime, float minDistance, boolean useGps) {
        mMinUpdateTime = minTime;
        mMinUpdateDistance = minDistance;
        mUsingGpsLocation = false;
        mUsingCoarseLocation = false;
        mUseGpsIfPossible = useGps;
        switchToCoarseLocationUpdates();
        if ((mLocationManager != null) && (mUseGpsIfPossible)) {
            mLocationManager.addGpsStatusListener(mGpsStatusListener);
        }
    }


    /**
     * Switches from using Network location updates to GPS location updates.
     */
    public void swithToGpsLocationUpdates() {
        if (mUsingGpsLocation) {
            return;
        }
        if ((mLocationManager != null) && (mUseGpsIfPossible)) {
            if (D) {
                Log.d(TAG, "Using GPS location updates. minTime=" + mMinUpdateTime + ", " + "minDistance=" + mMinUpdateDistance);
            }
            // clean up first
            mLocationManager.removeUpdates(mCoarseLocationProviderListener);
            mLocationManager.removeUpdates(mGpsLocationProviderListener);
            mUsingGpsLocation = false;
            mUsingCoarseLocation = false;

            // set new listeners
            if (mGpsLocationProvider != null) {
                mLocationManager.requestLocationUpdates(mGpsLocationProvider, mMinUpdateTime, mMinUpdateDistance, mGpsLocationProviderListener);
                mUsingGpsLocation = true;
            }

            // update location info
            gotoLastKnownLocation();
        }
    }


    /**
     * Switches from using GPS location updates to Network location updates.
     */
    public void switchToCoarseLocationUpdates() {
        if (mUsingCoarseLocation) {
            return;
        }
        if (mLocationManager != null) {
            if (D) {
                Log.d(TAG, "Using coarse location updates and monitoring GPS " +
                        "status. minTime=" + mMinUpdateTime + ", " +
                        "minDistance=" + mMinUpdateDistance);
            }
            // clean up first
            mLocationManager.removeUpdates(mCoarseLocationProviderListener);
            mLocationManager.removeUpdates(mGpsLocationProviderListener);
            mUsingCoarseLocation = false;
            mUsingGpsLocation = false;

            // set new listeners
            if (mCoarseLocationProvider != null) {
                mLocationManager.requestLocationUpdates(mCoarseLocationProvider, mMinUpdateTime, mMinUpdateDistance, mCoarseLocationProviderListener);
                mUsingCoarseLocation = true;
            }

            if ((mGpsLocationProvider != null) && (mUseGpsIfPossible)) {
                mLocationManager.requestLocationUpdates(mGpsLocationProvider, mMinUpdateTime, mMinUpdateDistance, mGpsLocationProviderListener);
            }

            // update location info
            gotoLastKnownLocation();
        }
    }


    /**
     * Stops receiving location updates. Call this method when location info
     * is no longer needed, to reduce power consumption.
     */
    public void stopLocationUpdates() {
        mUsingGpsLocation = false;
        mUsingCoarseLocation = false;
        if (mLocationManager != null) {
            if (D) { Log.d(TAG, "Stopping coarse and GPS location updates"); }
            mLocationManager.removeUpdates(mCoarseLocationProviderListener);
            mLocationManager.removeUpdates(mGpsLocationProviderListener);
            mLocationManager.removeGpsStatusListener(mGpsStatusListener);
        }
    }

}
