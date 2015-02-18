/**
 * Roundware Android code is released under the terms of the GNU General Public License.
 * See COPYRIGHT.txt, AUTHORS.txt, and LICENSE.txt in the project root directory for details.
 */
package org.roundware.rwapp;

import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.media.AudioManager;
import android.os.Bundle;
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
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.google.android.gms.common.GooglePlayServicesUtil;

import org.roundware.rwapp.utils.ClassRegistry;
import org.roundware.service.RW;
import org.roundware.service.RWService;
import org.roundware.service.util.RWList;

import org.roundware.rwapp.utils.AssetImageManager;
import org.roundware.rwapp.utils.Utils;

import java.util.UUID;

public class RwMainActivity extends Activity {
    public static final String LOGTAG = RwMainActivity.class.getSimpleName();
    private final static boolean D = true;

    // menu items
    private final static int MENU_ITEM_INFO = Menu.FIRST;
    private final static int MENU_ITEM_PREFERENCES = Menu.FIRST + 1;
    private final static int MENU_ITEM_EXIT = Menu.FIRST + 2;

    // view references
    private Animation mFadeInAnimation;
    private Animation mFadeOutAnimation;
    private ViewFlipper mViewFlipper;
    protected ImageView mBackgroundImageView;
    private Button mListenButton;
    private Button mSpeakButton;
    private ImageButton mInfoButton;
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
     * connect to. Since most operations of the service involve making calls
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
                RWList allTags = new RWList(mRwBinder.getTags());
                SharedPreferences prefs = Settings.getSharedPreferences();
                if (mRwBinder.getConfiguration().isResetTagsDefaultOnStartup()) {
                    allTags.saveSelectionState(prefs);
                }
                AssetImageManager.saveArtworkTags(prefs, allTags);
            } else if (RW.CONTENT_LOADED.equals(intent.getAction())) {
                //String contentFileName = mRwBinder.getContentFilesDir() + "home-a.html";
                //try {
                //    String data = mRwBinder.readContentFile(contentFileName);
                //    mHiddenWebView.loadDataWithBaseURL("file://" + contentFileName, data, null, null, null);
                //} catch (IOException e) {
                //    e.printStackTrace();
                //    Log.e(LOGTAG, "Problem loading content file: " + contentFileName);
                    // TODO: dialog?? error??
                //}
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
        // Why would we want this reset every time the app starts???
        //resetLegalNoticeSetting();

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
        RWService.addOperationsToIntentFilter(filter, "get_tags");

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
        prefsEditor.putString(RwPrefsActivity.ROUNDWARE_DEVICE_ID, mDeviceId);
        prefsEditor.apply();

        // add the option menu items
        menu.add(0, MENU_ITEM_INFO, Menu.NONE, R.string.legal_notice_title)
                .setShortcut('1', 'i')
                .setIcon(android.R.drawable.ic_menu_info_details);

        if (D) {
            menu.add(0, MENU_ITEM_PREFERENCES, Menu.NONE, R.string.preferences).setShortcut('4', 'p')
                    .setIcon(android.R.drawable.ic_menu_preferences);
        }

        // TODO: The Exit item should be removed and confirmedExit() code called in onBackPressed()
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
                Intent settingsActivity = new Intent(getBaseContext(), RwPrefsActivity.class);
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
            Intent bindIntent = new Intent(RwMainActivity.this, RWService.class);
            bindService(bindIntent, rwConnection, Context.BIND_AUTO_CREATE);

            // create the intent to start the RW service
            mRwService = new Intent(this, RWService.class);
            mRwService.putExtra(RW.EXTRA_DEVICE_ID, mDeviceId);
            mRwService.putExtra(RW.EXTRA_PROJECT_ID, mProjectId);

            // notification customizations
            mRwService.putExtra(RW.EXTRA_NOTIFICATION_TITLE, getString(R.string.notification_title));
            mRwService.putExtra(RW.EXTRA_NOTIFICATION_DEFAULT_TEXT, getString(R.string.notification_default_text));
            mRwService.putExtra(RW.EXTRA_NOTIFICATION_ICON_ID, R.drawable.status_icon);
            //FIXME use the listen activity instead
            mRwService.putExtra(RW.EXTRA_NOTIFICATION_ACTIVITY_CLASS_NAME, ClassRegistry.get("RwListenActivity").getName());

            //FIXME bind service instead
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
    //FIXME this shouldn't be needed
    private boolean stopRWService() {
        if (mRwBinder != null) {
            mRwBinder.stopService();
            unbindService(rwConnection);
        }

        if (mRwService != null) {
            return stopService(mRwService);
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
        mBackgroundImageView = (ImageView)findViewById(R.id.background);
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
                startActivity(new Intent(getApplicationContext(), ClassRegistry.get("RwListenActivity")));
            }
        });

        mSpeakButton = (Button) findViewById(R.id.speakButton);
        mSpeakButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RwSpeakActivity.showLegalDialogIfNeeded(RwMainActivity.this, mRwBinder);
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
                Intent intent = new Intent(getApplicationContext(), ClassRegistry.get("RwSpeakActivity"));
                intent.setAction(RwSpeakActivity.ACTION_RECORD_FEEDBACK);
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
        SharedPreferences prefs = Settings.getSharedPreferences();
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putBoolean(RwSpeakActivity.PREFS_KEY_LEGAL_NOTICE_ACCEPTED, false);
        prefsEditor.apply();
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


    /**
     * Restores app settings from shared preferences.
     */
    private void restoreRoundwareDeviceIdSetting() {
        SharedPreferences prefs = Settings.getSharedPreferences();
        mDeviceId = prefs.getString(Settings.PREFS_KEY_RW_DEVICE_ID, mDeviceId);
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
        } catch (Exception e) {
            Log.e(LOGTAG, "Unable to show version dialog!", e);
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
            boolean showDetailedMessages = prefs.getBoolean(RwPrefsActivity.SHOW_DETAILED_MESSAGES, false);
            mRwBinder.setShowDetailedMessages(showDetailedMessages);

            String mockLat = prefs.getString(RwPrefsActivity.MOCK_LATITUDE, "");
            String mockLon = prefs.getString(RwPrefsActivity.MOCK_LONGITUDE, "");
            mRwBinder.setMockLocation(mockLat, mockLon);

            boolean useOnlyWiFi = prefs.getBoolean(RwPrefsActivity.USE_ONLY_WIFI, false);
            mRwBinder.setOnlyConnectOverWifi(useOnlyWiFi);
            updateUIState(mRwBinder.isConnected());
        }
    }

}
