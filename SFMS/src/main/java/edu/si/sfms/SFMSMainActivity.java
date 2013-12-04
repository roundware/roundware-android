/*
    STORIES FROM MAIN STREET
	Android client application
   	Copyright (C) 2008-2013 Halsey Solutions, LLC
	with contributions by Rob Knapen
	ALL RIGHTS RESERVED
*/
package edu.si.sfms;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.google.android.gms.common.GooglePlayServicesUtil;
import com.halseyburgund.rwframework.core.RW;
import com.halseyburgund.rwframework.core.RWService;
import com.halseyburgund.rwframework.util.RWList;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import edu.si.sfms.utils.Utils;
import edu.si.sfms.utils.VersionDialog;

public class SFMSMainActivity extends Activity {

    // name of shared preferences used by all activities in the app
    public final static String APP_SHARED_PREFS = "edu.si.sfms.preferences";

    // preferences keys for parameter storage
    public final static String PREFS_KEY_RW_DEVICE_ID = "SavedRoundwareDeviceId";

    // menu items
    private final static int MENU_ITEM_INFO = Menu.FIRST;
    private final static int MENU_ITEM_PREFERENCES = Menu.FIRST + 1;
    private final static int MENU_ITEM_EXIT = Menu.FIRST + 2;

    // debugging
    private final static String TAG = "SFMSMainActivity";
    private final static boolean D = true;

    // view references
    private Animation mFadeInAnimation;
    private Animation mFadeOutAnimation;
    private ViewFlipper mViewFlipper;
    private Button mListenButton;
    private Button mSpeakButton;
    private ImageButton mInfoButton;
    private ImageButton mWebButton;
    private ImageButton mInfoCloseButton;
    private Button mFeedbackButton;
    private WebView mHiddenWebView;

    // fields
    private ProgressDialog mProgressDialog;
    private Intent mRwService;
    private RWService mRwBinder;
    private String mDeviceId;
    private String mProjectId;
    private boolean mIsConnected;


    /**
     * Handles connection state to an RWService Android Service. In this
     * activity it is assumed that the service has already been started
     * by another activity and we only need to connect to it.
     */
    private ServiceConnection rwConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mRwBinder = ((RWService.RWServiceBinder) service).getService();
            updateServerForPreferences();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mRwBinder = null;
            updateUIState(false);
        }
    };


    /**
     * Handles events received from the RWService Android Service that we
     * connect to. Sinds most operations of the service involve making calls
     * to the Roundware server, the response is handle asynchronously with
     * results passed back as broadcast intents. An IntentFilter is set up
     * in the onResume method of this activity and controls which intents
     * from the RWService will be received and processed here.
     */
    private BroadcastReceiver rwReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (RW.SESSION_ON_LINE.equals(intent.getAction())) {
                updateUIState(true);
                updateServerForPreferences();
                if (mProgressDialog != null) {
                    mProgressDialog.dismiss();
                }
            } else if (RW.SESSION_OFF_LINE.equals(intent.getAction())) {
                updateUIState(false);
                if (mProgressDialog != null) {
                    mProgressDialog.dismiss();
                }
            } else if (RW.CONFIGURATION_LOADED.equals(intent.getAction())) {
                updateUIState(mIsConnected);
            } else if (RW.NO_CONFIGURATION.equals(intent.getAction())) {
                updateUIState(false);
                showMessage(getString(R.string.unable_to_retrieve_configuration), true, true);
            } else if (RW.TAGS_LOADED.equals(intent.getAction())) {
                if (mRwBinder.getConfiguration().isResetTagsDefaultOnStartup()) {
                    RWList allTags = new RWList(mRwBinder.getTags());
                    allTags.saveSelectionState(getSharedPreferences(APP_SHARED_PREFS, MODE_PRIVATE));
                }
            } else if (RW.CONTENT_LOADED.equals(intent.getAction())) {
                String contentFileName = mRwBinder.getContentFilesDir() + "home-a.html";
                try {
                    String data = mRwBinder.readContentFile(contentFileName);
                    mHiddenWebView.loadDataWithBaseURL("file://" + contentFileName, data, null, null, null);
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Problem loading content file: " + contentFileName);
                    // TODO: dialog?? error??
                }
            } else if (RW.USER_MESSAGE.equals(intent.getAction())) {
                showMessage(intent.getStringExtra(RW.EXTRA_SERVER_MESSAGE), false, false);
            } else if (RW.ERROR_MESSAGE.equals(intent.getAction())) {
                if ((mRwBinder != null) && (mRwBinder.getShowDetailedMessages())) {
                    showMessage(intent.getStringExtra(RW.EXTRA_SERVER_MESSAGE), true, false);
                }
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        resetLegalNoticeSetting();

        initUIWidgets();
        updateUIState(false);

        // create session start unless one is passed in
        startRWService(getIntent());
    }


    @Override
    protected void onPause() {
        unregisterReceiver(rwReceiver);
        super.onPause();
    }


    @Override
    protected void onResume() {
        // set up filter for the RWFramework events this activity is interested in
        IntentFilter filter = new IntentFilter();

        // get the operation name and add the intents to the filter
        String opName = getString(R.string.rw_op_get_tags);
        RWService.addOperationsToIntentFilter(filter, opName);

        // add predefined (high-level) intents
        filter.addAction(RW.SESSION_ON_LINE);
        filter.addAction(RW.SESSION_OFF_LINE);
        filter.addAction(RW.CONFIGURATION_LOADED);
        filter.addAction(RW.NO_CONFIGURATION);
        filter.addAction(RW.TAGS_LOADED);
        filter.addAction(RW.CONTENT_LOADED);
        filter.addAction(RW.ERROR_MESSAGE);
        filter.addAction(RW.USER_MESSAGE);

        registerReceiver(rwReceiver, filter);

        updateServerForPreferences();
        updateUIState(mIsConnected);
        super.onResume();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRWService();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        // TODO change to use the standard Android way:
        // Inflate the menu; this adds items to the action bar if it is present.
        // getMenuInflater().inflate(R.menu.sfmsmain, menu);

        // update settings to show current Roundware Device ID
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putString(SFMSPrefsActivity.ROUNDWARE_DEVICE_ID, mDeviceId);
        prefsEditor.commit();

        // add the option menu items
        menu.add(0, MENU_ITEM_INFO, Menu.NONE, R.string.info)
                .setShortcut('1', 'i')
                .setIcon(android.R.drawable.ic_menu_info_details);

        if (D) {
            menu.add(0, MENU_ITEM_PREFERENCES, Menu.NONE, R.string.preferences).setShortcut('4', 'p')
                    .setIcon(android.R.drawable.ic_menu_preferences);
        }

        menu.add(0, MENU_ITEM_EXIT, Menu.NONE, R.string.exit).setShortcut('3', 'e')
                .setIcon(android.R.drawable.ic_menu_close_clear_cancel);

        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ITEM_INFO: {
                showVersionDialog(true);
                return true;
            }
            case MENU_ITEM_PREFERENCES: {
                // start the standard preferences activity
                Intent settingsActivity = new Intent(getBaseContext(), SFMSPrefsActivity.class);
                startActivity(settingsActivity);
                return true;
            }
            case MENU_ITEM_EXIT: {
                confirmedExit();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }


    /**
     * Starts (or restarts) a RWService Android Service.
     *
     * @param intent
     */
    private void startRWService(Intent intent) {
        // try to restore a previously assigned device ID
        restoreRoundwareDeviceIdSetting();

        // see if an other source for the device ID must be used
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mDeviceId = extras.getString(RW.EXTRA_DEVICE_ID);
        }
        if (mDeviceId == null) {
            mDeviceId = UUID.randomUUID().toString();
        }

        mProjectId = getString(R.string.rw_spec_project_id);

        showProgress(getString(R.string.initializing), getString(R.string.connecting_to_server_message), true, false);
        try {
            // create connection to the RW service
            Intent bindIntent = new Intent(SFMSMainActivity.this, RWService.class);
            bindService(bindIntent, rwConnection, Context.BIND_AUTO_CREATE);

            // create the intent to start the RW service
            mRwService = new Intent(this, RWService.class);
            mRwService.putExtra(RW.EXTRA_DEVICE_ID, mDeviceId);
            mRwService.putExtra(RW.EXTRA_PROJECT_ID, mProjectId);

            // notification customizations
            mRwService.putExtra(RW.EXTRA_NOTIFICATION_TITLE, getString(R.string.notification_title));
            mRwService.putExtra(RW.EXTRA_NOTIFICATION_DEFAULT_TEXT, getString(R.string.notification_default_text));
            mRwService.putExtra(RW.EXTRA_NOTIFICATION_ICON_ID, R.drawable.status_icon);
            mRwService.putExtra(RW.EXTRA_NOTIFICATION_ACTIVITY_CLASS_NAME, this.getClass().getName());

            // start the service
            startService(mRwService);

            // successfully started service, make a note of the device ID
            saveRoundwareDeviceIdSetting();

        } catch (Exception ex) {
            showMessage(getString(R.string.connection_to_server_failed) + " " + ex.getMessage(), true, true);
        }
    }


    /**
     * Stops the RWService Android Service.
     *
     * @return true when successful
     */
    private boolean stopRWService() {
        if (mRwBinder != null) {
            mRwBinder.stopService();
            unbindService(rwConnection);
        } else {
            if (mRwService != null) {
                return stopService(mRwService);
            }
        }
        return true;
    }


    /**
     * Main UI initialization.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void initUIWidgets() {
        // set up animations
        mFadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        mFadeOutAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_out);

        mViewFlipper = (ViewFlipper) findViewById(R.id.viewFlipper);

        mHiddenWebView = (WebView) findViewById(R.id.hiddenWebView);

        // set-up the webview
        WebSettings webSettings = mHiddenWebView.getSettings();

        webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH);

        webSettings.setAppCachePath(this.getFilesDir().getAbsolutePath());
        webSettings.setAppCacheEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);

        webSettings.setSupportMultipleWindows(false);
        webSettings.setSupportZoom(false);
        webSettings.setSavePassword(false);
        webSettings.setGeolocationDatabasePath(this.getFilesDir().getAbsolutePath());
        webSettings.setGeolocationEnabled(false);
        webSettings.setDatabaseEnabled(false);
        webSettings.setDomStorageEnabled(false);

        mListenButton = (Button) findViewById(R.id.listenButton);
        mListenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), SFMSListenActivity.class));
            }
        });

        mSpeakButton = (Button) findViewById(R.id.speakButton);
        mSpeakButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), SFMSSpeakActivity.class));
            }
        });

        mInfoButton = (ImageButton) findViewById(R.id.infoButton);
        mInfoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WebView wv = (WebView)findViewById(R.id.infoWebView);
                wv.loadUrl(getString(R.string.app_info_url));
                mViewFlipper.setInAnimation(mFadeInAnimation);
                mViewFlipper.setOutAnimation(mFadeOutAnimation);
                mViewFlipper.showNext();
            }
        });

        mWebButton = (ImageButton) findViewById(R.id.wwwButton);
        mWebButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(getString(R.string.app_web_url)));
                startActivity(intent);
            }
        });

        mInfoCloseButton = (ImageButton) findViewById(R.id.infoCloseButton);
        mInfoCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mViewFlipper.setInAnimation(mFadeInAnimation);
                mViewFlipper.setOutAnimation(mFadeOutAnimation);
                mViewFlipper.showPrevious();
            }
        });

        mFeedbackButton = (Button)findViewById(R.id.infoFeedbackButton);
        mFeedbackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mViewFlipper.showPrevious();
                Intent intent = new Intent(getApplicationContext(), SFMSSpeakActivity.class);
                intent.setAction(SFMSSpeakActivity.ACTION_RECORD_FEEDBACK);
                startActivity(intent);
            }
        });
    }


    /**
     * Resets some stored settings when the app is started. Currently this
     * assures the legal agreement about recordings a user makes is shown
     * once every time the app is run and the Speak functionality is used.
     */
    private void resetLegalNoticeSetting() {
        SharedPreferences prefs = getSharedPreferences(APP_SHARED_PREFS, MODE_PRIVATE);
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putBoolean(SFMSSpeakActivity.PREFS_KEY_LEGAL_NOTICE_ACCEPTED, false);
        prefsEditor.commit();
    }


    /**
     * Saves app settings as shared preferences.
     */
    private void saveRoundwareDeviceIdSetting() {
        SharedPreferences prefs = getSharedPreferences(APP_SHARED_PREFS, MODE_PRIVATE);
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putString(PREFS_KEY_RW_DEVICE_ID, mDeviceId);
        prefsEditor.commit();
    }


    /**
     * Restores app settings from shared preferences.
     */
    private void restoreRoundwareDeviceIdSetting() {
        SharedPreferences prefs = getSharedPreferences(APP_SHARED_PREFS, MODE_PRIVATE);
        mDeviceId = prefs.getString(PREFS_KEY_RW_DEVICE_ID, mDeviceId);
    }


    /**
     * Updates the state of the primary UI widgets based on current
     * connection state and other state variables.
     */
    private void updateUIState(boolean connectedState) {
        mIsConnected = connectedState;

        if (mListenButton != null) {
            mListenButton.setEnabled(mIsConnected);
        }
        if (mSpeakButton != null) {
            mSpeakButton.setEnabled(true);
        }
    }


    /**
     * Creates a confirmation dialog when exiting the app but there are
     * still some items in the queue awaiting processing. The user can
     * choose to leave them in the queue so that they will be processed
     * the next time the app is started, or to erase the queue.
     */
    private void confirmedExit() {
        if (mRwBinder != null) {
            int itemsInQueue = mRwBinder.getQueueSize();
            if (itemsInQueue > 0) {
                AlertDialog.Builder alertBox;
                alertBox = new AlertDialog.Builder(this);
                alertBox.setTitle(R.string.confirm_exit);
                alertBox.setMessage(R.string.confirm_exit_message);

                alertBox.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        Toast.makeText(getApplicationContext(), R.string.thank_you_for_participating, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });

                alertBox.setNegativeButton(R.string.erase, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        if (!mRwBinder.deleteQueue()) {
                            Toast.makeText(getApplicationContext(), R.string.cannot_delete_queue, Toast.LENGTH_SHORT).show();
                        }
                        finish();
                    }
                });

                alertBox.show();
            } else {
                Toast.makeText(getApplicationContext(), R.string.thank_you_for_participating, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }


    /**
     * Displays the version info dialog for this app. The dialog remembers
     * when it has been shown and normally will only be displayed once for
     * each new version of the app, unless forced is set to true.
     *
     * @param forced true to always display the dialog
     */
    private void showVersionDialog(boolean forced) {
        try {
            StringBuilder license = new StringBuilder();
            license.append(getResources().getString(R.string.version_text));
            String osLicense = GooglePlayServicesUtil.getOpenSourceSoftwareLicenseInfo(getApplicationContext());
            if (osLicense != null) {
                license.append(osLicense);
            } else {
                license.append(R.string.play_services_not_installed);
            }
            VersionDialog.show(this, "edu.si.sfms", R.layout.version_dialog, license.toString(), forced);
        } catch (Exception e) {
            Log.e(TAG, "Unable to show version dialog!", e);
        }
    }


    /**
     * Shows a standardized message dialog for the specified conditions.
     *
     * @param message to be displayed
     * @param isError type of notification
     * @param isFatal dialog exits activity
     */
    private void showMessage(String message, boolean isError, boolean isFatal) {
        Utils.showMessageDialog(this, message, isError, isFatal);
    }


    /**
     * Shows a standardized progress dialog for the specified conditions.
     *
     * @param title to be displayed
     * @param message to be displayed
     * @param isIndeterminate setting for the progress dialog
     * @param isCancelable setting for the progress dialog
     */
    private void showProgress(String title, String message, boolean isIndeterminate, boolean isCancelable) {
        if (mProgressDialog == null) {
            mProgressDialog = Utils.showProgressDialog(this, title, message, isIndeterminate, isCancelable);
        }
    }


    /**
     * Updates settings of the RWService Service from the preferences.
     */
    private void updateServerForPreferences() {
        if (mRwBinder != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            boolean showDetailedMessages = prefs.getBoolean(SFMSPrefsActivity.SHOW_DETAILED_MESSAGES, false);
            mRwBinder.setShowDetailedMessages(showDetailedMessages);

            String mockLat = prefs.getString(SFMSPrefsActivity.MOCK_LATITUDE, "");
            String mockLon = prefs.getString(SFMSPrefsActivity.MOCK_LONGITUDE, "");
            mRwBinder.setMockLocation(mockLat, mockLon);

            boolean useOnlyWiFi = prefs.getBoolean(SFMSPrefsActivity.USE_ONLY_WIFI, false);
            mRwBinder.setOnlyConnectOverWifi(useOnlyWiFi);
            updateUIState(mRwBinder.isConnected());

            String bufferLength = prefs.getString(SFMSPrefsActivity.AVERAGE_BUFFER_LENGTH_MSEC, "4000");
            try {
                mRwBinder.setAverageStreamBufferLength(Integer.valueOf(bufferLength));
            } catch (NumberFormatException e) {
                // skip
            }
        }
    }

}
