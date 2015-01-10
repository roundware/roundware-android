/*
    STORIES FROM MAIN STREET
    Android client application
       Copyright (C) 2008-2013 Halsey Solutions, LLC
    with contributions by Rob Knapen
    ALL RIGHTS RESERVED
*/
package org.roundware.rwapp.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.app.Activity;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.Menu;

import org.roundware.rwapp.R;

/**
 * Activity to display application preferences and allow the user to edit
 * them.
 *
 * @author Rob Knapen
 */
public class PrefsActivity extends PreferenceActivity {
    public static final String LOGTAG = PrefsActivity.class.getSimpleName();

    public final static String CONNECT_TO_SERVER = "connectToServerPref";
    public final static String SHOW_DETAILED_MESSAGES = "showDetailedMessagesPref";
    public final static String SERVER_NAME = "serverNamePref";
    public final static String SERVER_PAGE = "serverPagePref";
    public final static String MOCK_LATITUDE = "mockLocationLatitudePref";
    public final static String MOCK_LONGITUDE = "mockLocationLongitudePref";
    public final static String USE_ONLY_WIFI = "useOnlyWiFiPref";
    public final static String AVERAGE_BUFFER_LENGTH_MSEC = "averageBufferLengthPref";
    public final static String ROUNDWARE_DEVICE_ID = "roundwareDeviceIdPref";
    public final static String ALWAYS_DOWNLOAD_WEB_CONTENT = "alwaysDownloadWebContent";
    public final static String USE_EXTERNAL_STORAGE_FOR_WEB_CONTENT = "useExternalStorageForWebContent";


    // TODO: Use current standard for Android preferences
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_prefs);
//    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        // display the current device ID
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String deviceId = prefs.getString(ROUNDWARE_DEVICE_ID, "Not Assigned Yet");
        Preference p = findPreference(ROUNDWARE_DEVICE_ID);
        p.setSummary(deviceId);
    }

}
