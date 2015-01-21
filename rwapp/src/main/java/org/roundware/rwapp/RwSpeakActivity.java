/**
 * Roundware Android code is released under the terms of the GNU General Public License.
 * See COPYRIGHT.txt, AUTHORS.txt, and LICENSE.txt in the project root directory for details.
 */
package org.roundware.rwapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.ViewFlipper;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.roundware.rwapp.utils.ClassRegistry;
import org.roundware.service.RW;
import org.roundware.service.RWRecordingTask;
import org.roundware.service.RWService;
import org.roundware.service.RWTags;
import org.roundware.service.util.RWList;
import org.roundware.service.util.RWListItem;

import org.roundware.rwapp.utils.LevelMeterView;
import org.roundware.rwapp.utils.Utils;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class RwSpeakActivity extends Activity {
    public static final String LOGTAG = RwSpeakActivity.class.getSimpleName();

    // intent actions to select recording type when starting the activity
    public final static String ACTION_RECORD_FEEDBACK = "org.roundware.rwapp.record_feedback";
    public final static String ACTION_RECORD_CONTRIBUTION = "org.roundware.rwapp.record_contribution";

    // settings for storing recording as file
    private final static String STORAGE_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/org.roundware.rwapp/";

    // preferences keys for state storage
    public final static String PREFS_KEY_LEGAL_NOTICE_ACCEPTED = "SavedLegalNoticeAccepted";

    // tag values for user feedback recording
    private final static int FEEDBACK_QUESTION_TAG_ID = 21;
    private final static String FEEDBACK_SUBMITTED_VALUE = "N";

    // submitted value for user contribution recording (null will not send this optional param)
    private final static String CONTRIBUTION_SUBMITTED_VALUE = null;

    // debugging
    private final static boolean D = false;
    private final static String TAG = "SFMSSpeakActivity";
    private final static boolean SUBMIT_RECORDING = true;

    // pause time to allow final lead-in sound to fade out before starting recording
    private final static int RECORDING_START_DELAY_MSEC = 400;

    // Roundware tag type used in this activity
    private final static String ROUNDWARE_TAGS_TYPE = "speak";


    // NOTE: Order must match R.layout.activity_speak
    private static final int RECORD_LAYOUT = 0;
    private static final int FILTER_LAYOUT = 1;
    private static final int THANKS_LAYOUT = 2;

    // fields
    private ViewFlipper mViewFlipper;
    protected ImageView mBackgroundImageView;
    private WebView mWebView;
    private Button mAgreeButton;
    private Button mDeclineButton;
    private View mSpeakInstructionsView;
    private TextView mRecordingTimeText;
    private LinearLayout mRecordingLevelMeterLayout;
    private LevelMeterView mRecordingLevelMeterView;
    private ToggleButton mRecordButton;
    private Button mRerecordButton;
    private Button mUploadButton;
    private Button mListenMoreButton;
    private Button mSpeakMoreButton;
    private RWService mRwBinder;
    private RWTags mProjectTags;
    private RWList mTagsList;
    private RWRecordingTask mRecordingTask;
    private boolean mHasRecording = false;
    private Timer mRecordingLeadInTimer;
    private int mLeadInCounter;
    private SoundPool mSoundPool;
    private MediaPlayer mPlayer;
    private int[] mLeadInSoundIds;
    private String[] mLeadInText;
    private Handler mPlaybackHandler = null;
    private int mPlaybackTimerCount = 0;
    private boolean mIsRecordingGeneralFeedback = false;
    private String mContentFileDir;
    private MapView mMapView;
    private GoogleMap mGoogleMap;

    // Custom ActionBar
    private TextView mTitleView;
    private Button mLeftTitleButton;
    private Button mRightTitleButton;

    // click listeners
    private View.OnClickListener mRecordListener;
    private View.OnClickListener mRerecordListener;
    private View.OnClickListener mSubmitListener;
    private View.OnClickListener mCancelListener;

    /**
     * Handles connection state to an RWService Android Service. In this
     * activity it is assumed that the service has already been started
     * by another activity and we only need to connect to it.
     */
    private ServiceConnection rwConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mRwBinder = ((RWService.RWServiceBinder) service).getService();

            // make sure audio is not playing when recording
            mRwBinder.playbackStop();

            // create a tags list for display and selection
            mProjectTags = mRwBinder.getTags().filterByType(ROUNDWARE_TAGS_TYPE);
            mTagsList = new RWList(mProjectTags);
            mTagsList.restoreSelectionState(Settings.getSharedPreferences());

            // get the folder where the web content files are stored
            mContentFileDir = mRwBinder.getContentFilesDir();
            if ((mWebView != null) && (mContentFileDir != null)) {
                String contentFileName = mRwBinder.getContentFilesDir() + "speak.html";
                try {
                    String data = mRwBinder.readContentFile(contentFileName);
                    data = data.replace("/*%roundware_tags%*/", mTagsList.toJsonForWebView(ROUNDWARE_TAGS_TYPE));
                    mWebView.loadDataWithBaseURL("file://" + contentFileName, data, null, null, null);
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Problem loading content file: " + contentFileName);
                    // TODO: dialog?? error??
                }
            }
            updateUIState();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mRwBinder = null;
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
            //updateUIState();
            if (RW.SESSION_OFF_LINE.equals(intent.getAction())) {
                showMessage(getString(R.string.connection_to_server_lost_record), false, false);
            } else if (RW.USER_MESSAGE.equals(intent.getAction())) {
                showMessage(intent.getStringExtra(RW.EXTRA_SERVER_MESSAGE), false, false);
            } else if (RW.ERROR_MESSAGE.equals(intent.getAction())) {
                if ((mRwBinder != null) && (mRwBinder.getShowDetailedMessages())) {
                    showMessage(intent.getStringExtra(RW.EXTRA_SERVER_MESSAGE), true, false);
                }
            } else if (RW.SHARING_MESSAGE.equals(intent.getAction())) {
                confirmSharingMessage(intent.getStringExtra(RW.EXTRA_SERVER_MESSAGE));
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speak);

        mMapView = (MapView) findViewById(R.id.map);
        mMapView.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        final String action = intent.getAction();

        if (ACTION_RECORD_FEEDBACK.equals(action)) {
            mIsRecordingGeneralFeedback = true;
        } else {
            mIsRecordingGeneralFeedback = false;
        }

        initListeners();
        initUIWidgets();
        initLeadIn();
        initMapIfNeeded();

        // connect to service started by other activity
        try {
            Intent bindIntent = new Intent(this, RWService.class);
            bindService(bindIntent, rwConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception ex) {
            showMessage(getString(R.string.connection_to_server_failed) + " " + ex.getMessage(), true, true);
        }
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // TODO: activity is reused for a new task
        // TODO: check if this can be used?
        // TODO: or select other launchMode in manifest
        // TODO: reset the filter webview?
        // TODO: test (back) navigation, define tasks and activity stacks

        if (mViewFlipper != null) {
            //showRecord();
            showFilters();
        }
    }


    @Override
    protected void onPause() {
        mMapView.onPause();
        unregisterReceiver(rwReceiver);
        if (mTagsList != null) {
            mTagsList.saveSelectionState(Settings.getSharedPreferences());
        }
        super.onPause();
    }


    @Override
    protected void onResume() {
        super.onResume();

        initMapIfNeeded();
        //mMapView.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(RW.SESSION_ON_LINE);
        filter.addAction(RW.SESSION_OFF_LINE);
        filter.addAction(RW.TAGS_LOADED);
        filter.addAction(RW.ERROR_MESSAGE);
        filter.addAction(RW.USER_MESSAGE);
        filter.addAction(RW.SHARING_MESSAGE);
        registerReceiver(rwReceiver, filter);

        updateUIState();
    }


    @Override
    protected void onDestroy() {
        mMapView.onDestroy();

        if (rwConnection != null) {
            unbindService(rwConnection);
        }

        mSoundPool.release();
        mSoundPool = null;

        super.onDestroy();
    }


    @Override
    public void onLowMemory() {
        mMapView.onLowMemory();
        super.onLowMemory();
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mMapView.onSaveInstanceState(outState);
    }


    private void initMapIfNeeded() {
        if (mGoogleMap == null) {
            mGoogleMap = ((MapView) findViewById(R.id.map)).getMap();
            if (mGoogleMap != null) {
                setUpMap();
            }
        }
    }

    private void setUpMap() {
        mGoogleMap.getUiSettings().setMyLocationButtonEnabled(false);
        mGoogleMap.getUiSettings().setZoomControlsEnabled(false);
        mGoogleMap.getUiSettings().setAllGesturesEnabled(true);
        mGoogleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        mGoogleMap.setMyLocationEnabled(false);
    }


    private void showMarkerOnMap(double lat, double lon) {
        if (mGoogleMap == null) {
            return;
        }

        MapsInitializer.initialize(this);

        // add marker on map for recording location
        mGoogleMap.addMarker(new MarkerOptions().position(new LatLng(lat, lon)).title("Your Recording"));

        // Updates the location and zoom of the MapView
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lon), 10);
        mGoogleMap.animateCamera(cameraUpdate);
    }


    /**
     * Initializes lead in (3-2-1-beep) audio.
     */
    private void initLeadIn() {
        mSoundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 0);
        mLeadInSoundIds = new int[4];
        mLeadInText = new String[]{"3", "2", "1", ""};

        // load audio fragments
        mLeadInSoundIds[0] = mSoundPool.load(this, R.raw.en3, 1);
        mLeadInSoundIds[1] = mSoundPool.load(this, R.raw.en2, 1);
        mLeadInSoundIds[2] = mSoundPool.load(this, R.raw.en1, 1);
        mLeadInSoundIds[3] = mSoundPool.load(this, R.raw.beep, 1);
    }


    /**
     * Initializes click listeners for divers actions.
     */
    private void initListeners() {
        mRecordListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mHasRecording) {
                    if (mPlayer != null) {
                        stopRecordingPlayback();
                    } else {
                        startRecordingPlayback();
                    }
                } else {
                    if ((mRecordingTask != null) && (mRecordingTask.isRecording())) {
                        stopRecording();
                    } else {
                        startRecordingLeadIn();
                    }
                }
            }
        };

        mRerecordListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetRecording();
            }
        };

        mSubmitListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitRecording();
            }
        };

        mCancelListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancel();
            }
        };
    }


    /**
     * Sets up the primary UI widgets (spinner and buttons), and how to
     * handle interactions.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void initUIWidgets() {
        mViewFlipper = (ViewFlipper) findViewById(R.id.viewFlipper);
        mBackgroundImageView = (ImageView) findViewById(R.id.background);

        mTitleView = (TextView) findViewById(R.id.title);
        mLeftTitleButton = (Button) findViewById(R.id.left_title_button);
        mRightTitleButton = (Button) findViewById(R.id.right_title_button);

        // webview for showing tag filter web content
        mWebView = (WebView) findViewById(R.id.speakFilterWebView);
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH);

        webSettings.setAppCachePath(this.getFilesDir().getAbsolutePath());
        webSettings.setAppCacheEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
        webSettings.setSupportMultipleWindows(false);
        webSettings.setSupportZoom(false);
        webSettings.setSavePassword(false);
        webSettings.setGeolocationEnabled(false);
        webSettings.setDatabaseEnabled(false);
        webSettings.setDomStorageEnabled(false);

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                if (uri.getScheme().equals("roundware")) {
                    Log.d(TAG, "Processing roundware uri: " + url);
                    String schemeSpecificPart = uri.getSchemeSpecificPart(); // everything from : to #
                    if ("//speak_cancel".equalsIgnoreCase(schemeSpecificPart)) {
                        cancel();
                    } else {
                        if (mTagsList != null) {
                            boolean done = mTagsList.setSelectionFromWebViewMessageUri(uri);
                            if (done) {
                                updateScreenForSelectedTags();
                                showRecord();
                            }
                        }
                    }
                    return true;
                }
                // open link in external browser
                return super.shouldOverrideUrlLoading(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (mAgreeButton != null) {
                    mAgreeButton.setEnabled(true);
                }
                super.onPageFinished(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (mAgreeButton != null) {
                    mAgreeButton.setEnabled(false);
                }
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e(TAG, "Page load error: " + description);
                super.onReceivedError(view, errorCode, description, failingUrl);
            }
        });

        mRecordingTimeText = (TextView) findViewById(R.id.recordingTimeTextView);
        mSpeakInstructionsView = findViewById(R.id.instruction_layout);
        mRecordingLevelMeterLayout = (LinearLayout) findViewById(R.id.recordingLevelMeterLinearLayout);
        mRecordingLevelMeterView = (LevelMeterView) findViewById(R.id.recordingLevelMeterView);

        mRecordButton = (ToggleButton) findViewById(R.id.speakRecordPauseToggleButton);
        mRecordButton.setOnClickListener(mRecordListener);

        mUploadButton = (Button) findViewById(R.id.speakUploadButton);
        mUploadButton.setOnClickListener(mSubmitListener);

        mRerecordButton = (Button) findViewById(R.id.speakReRecordButton);
        mRerecordButton.setOnClickListener(mRerecordListener);

        mListenMoreButton = (Button) findViewById(R.id.speakHeadphonesButton);
        mListenMoreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: quick hack - not the best way to do it
                startActivity(new Intent(getApplicationContext(), ClassRegistry.get("RwListenActivity")));
            }
        });

        mSpeakMoreButton = (Button) findViewById(R.id.speakMicButton);
        mSpeakMoreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: quick hack - not the best way to do it
                startActivity(new Intent(getApplicationContext(), ClassRegistry.get("RwSpeakActivity")));
            }
        });
    }


    private void cancel() {
        stopRecording();
        stopRecordingPlayback();
        finish();
    }


    /**
     * Updates the state of the primary UI widgets based on current
     * connection state and other state variables.
     */
    private void updateUIState() {
        if (mRwBinder == null) {
            // not connected to RWService
            mRecordButton.setEnabled(false);
            mRerecordButton.setEnabled(false);
            mUploadButton.setEnabled(false);
        } else {
            // connected to RWService

            // Show LegalDialog if needed
            mViewFlipper.setVisibility(View.VISIBLE);
            if (mIsRecordingGeneralFeedback) {
                showGeneralFeedback();
            } else {
                showFilters();
            }

            mRecordButton.setEnabled(true);
            if (mPlayer != null) {
                changeToRecordingPlaybackUIState();
            } else {
                if ((mRecordingTask != null) && (mRecordingTask.isRecording())) {
                    changeToRecordingUIState();
                } else {
                    changeToNotRecordingUIState();
                }
            }
        }
        updateScreenForSelectedTags();
    }


    /**
     * Updates the UI for recording playback state.
     */
    private void changeToRecordingPlaybackUIState() {
        showRecord();

        // change record toggle button
        Drawable img = getResources().getDrawable(R.drawable.speak_play_pause_button);
        mRecordButton.setCompoundDrawablesWithIntrinsicBounds(null, img, null, null);
        mRecordButton.setEnabled(true);
        mRecordButton.setChecked(false);

        // update counter, level meter and instructions
        mSpeakInstructionsView.setVisibility(View.INVISIBLE);
        mRecordingTimeText.setVisibility(View.VISIBLE);
        mRecordingTimeText.setText(R.string.record_counter_zero);
        mRecordingLevelMeterLayout.setVisibility(View.INVISIBLE);

        // set other button states and handlers
        mRerecordButton.setEnabled(false);
        mUploadButton.setEnabled(false);
    }


    /**
     * Updates the UI for recording lead-in playback state.
     */
    private void changeToRecordingLeadInUIState() {
        showRecord();

        // change record toggle button
        Drawable img = getResources().getDrawable(R.drawable.speak_record_pause_button);
        mRecordButton.setCompoundDrawablesWithIntrinsicBounds(null, img, null, null);
        mRecordButton.setEnabled(false);
        mRecordButton.setChecked(true);

        // update counter, level meter and instructions
        mSpeakInstructionsView.setVisibility(View.INVISIBLE);
        mRecordingTimeText.setText("");
        mRecordingTimeText.setVisibility(View.VISIBLE);
        mRecordingLevelMeterLayout.setVisibility(View.INVISIBLE);

        // set other button states and handlers
        mRerecordButton.setEnabled(false);
        mRerecordButton.setOnClickListener(null);
        mUploadButton.setEnabled(false);
        mUploadButton.setOnClickListener(null);
    }


    /**
     * Updates the UI for recording in progress state.
     */
    private void changeToRecordingUIState() {
        showRecord();

        // change record toggle button
        Drawable img = getResources().getDrawable(R.drawable.speak_record_pause_button);
        mRecordButton.setCompoundDrawablesWithIntrinsicBounds(null, img, null, null);
        mRecordButton.setEnabled(true);
        mRecordButton.setChecked(true);

        // update counter, level meter and instructions
        mSpeakInstructionsView.setVisibility(View.INVISIBLE);
        mRecordingTimeText.setVisibility(View.VISIBLE);
        mRecordingLevelMeterLayout.setVisibility(View.VISIBLE);

        // set other button states and handlers
        mRerecordButton.setEnabled(false);
        mRerecordButton.setOnClickListener(null);
        mUploadButton.setEnabled(false);
        mUploadButton.setOnClickListener(null);
    }


    /**
     * Updates the UI for not recording state.
     */
    private void changeToNotRecordingUIState() {
        // update counter, level meter and instructions
        mSpeakInstructionsView.setVisibility(View.VISIBLE);
        mRecordingTimeText.setVisibility(View.INVISIBLE);
        mRecordingTimeText.setText("");
        mRecordingLevelMeterLayout.setVisibility(View.INVISIBLE);

        // set button states and handlers
        mRerecordButton.setEnabled(mHasRecording);
        mUploadButton.setEnabled(mHasRecording);

        mRecordButton.setEnabled(true);
        mRecordButton.setChecked(false);
        if (mHasRecording) {
            Drawable img = getResources().getDrawable(R.drawable.speak_play_pause_button);
            mRecordButton.setCompoundDrawablesWithIntrinsicBounds(null, img, null, null);
            mRerecordButton.setOnClickListener(new ConfirmDelete(new ContinueRerecord()));
            mUploadButton.setOnClickListener(mSubmitListener);
        } else {
            Drawable img = getResources().getDrawable(R.drawable.speak_record_pause_button);
            mRecordButton.setCompoundDrawablesWithIntrinsicBounds(null, img, null, null);
            mRerecordButton.setOnClickListener(null);
            mUploadButton.setOnClickListener(mSubmitListener);
        }
    }


    private void updateScreenForSelectedTags() {
        if ((mProjectTags == null) || (mTagsList == null)) {
            return;
        }

        // update title
        RWList questions = mTagsList.filter(mProjectTags.findTagByCodeAndType("question", ROUNDWARE_TAGS_TYPE));
        TextView tv = (TextView) findViewById(R.id.speakTitleTextView);
        if (questions.getSelectedCount() == 1) {
            RWListItem selected = questions.getSelectedItems().get(0);
            tv.setText(selected.getText());
        }

        // update background
        RWList exhibits = mTagsList.filter(mProjectTags.findTagByCodeAndType("exhibit", ROUNDWARE_TAGS_TYPE));
        if (exhibits.getSelectedCount() == 1) {
            RWListItem selected = exhibits.getSelectedItems().get(0);
            int exhibitId = selected.getTagId();
            if (mViewFlipper != null) {
                String imageName = "bg_" + exhibitId;
                int resId = getResources().getIdentifier(imageName, "drawable", "org.roundware.rwapp");
                if (resId != 0) {
                    mBackgroundImageView.setImageResource(resId);
                } else {
                    //mBackgroundImageView.setImageResource(R.drawable.bg_speak_dy);
                }
            }
        }
    }


    private void startRecordingLeadIn() {
        if (D) { Log.d(TAG, "startRecordingLeadIn"); }

        final Handler handler = new Handler();

        changeToRecordingLeadInUIState();

        // start timer that plays the four sounds in the SoundPool
        if (mRecordingLeadInTimer != null) {
            stopRecordingLeadIn();
        }
        mRecordingLeadInTimer = new Timer();
        mLeadInCounter = 0;
        mRecordingLeadInTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (mLeadInCounter < mLeadInSoundIds.length) {
                    if (D) { Log.d(TAG, "playing lead in sound " + mLeadInCounter); }
                    mSoundPool.play(mLeadInSoundIds[mLeadInCounter], 1, 1, 0, 0, 1);
                }
                mLeadInCounter++;

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mLeadInCounter < mLeadInText.length) {
                            if (D) { Log.d(TAG, "displaying lead in text '" + mLeadInText[mLeadInCounter-1] + "'"); }
                            mRecordingTimeText.setText(mLeadInText[mLeadInCounter-1]);
                        }
                    }
                });

                if (mLeadInCounter >= mLeadInText.length) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (D) { Log.d(TAG, "stopping lead in"); }
                            stopRecordingLeadIn();
                            startRecording();
                        }
                    });
                }
            }
        }, 0, 1000);
    }


    private void stopRecordingLeadIn() {
        if (mRecordingLeadInTimer != null) {
            mRecordingLeadInTimer.cancel();
            mRecordingLeadInTimer.purge();
            mRecordingLeadInTimer = null;
            mLeadInCounter = 0;
        }
    }


    /**
     * Starts making a recording using the RWRecordingTask of the Roundware
     * framework. A listener implementation is used to receive callbacks
     * during recording and update the UI using runnables posted to the
     * UI thread (the callbacks will be made from a background thread).
     */
    private void startRecording() {
        if (D) { Log.d(TAG, "startRecording"); }

        final Handler handler = new Handler();
        resetLevelMeter();
        changeToRecordingUIState();

        // pause time to let final lead-in sound die out
        try {
            Thread.sleep(RECORDING_START_DELAY_MSEC);
        } catch (InterruptedException ex) {
            // void;
        }

        mRecordingTask = new RWRecordingTask(mRwBinder, STORAGE_PATH, new RWRecordingTask.StateListener() {
            private int maxTimeSec = mRwBinder.getConfiguration().getMaxRecordingTimeSec();
            private long startTimeStampMillis;

            public void recording(long timeStampMillis, short [] samples) {
                int remainingTimeSec = maxTimeSec - (int)Math.round((timeStampMillis - startTimeStampMillis) / 1000.0);
                if (remainingTimeSec < 0) {
                    remainingTimeSec = 0;
                }
                final int min = remainingTimeSec / 60;
                final int sec = remainingTimeSec - (min * 60);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (D) { Log.d(TAG, "recording time: " + String.format("%1d:%02d", min, sec)); }
                        mRecordingTimeText.setText(String.format("%1d:%02d", min, sec));
                    }
                });

                updateLevelMeter(samples);

                if (remainingTimeSec <= 0) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            stopRecording();
                        }
                    });
                }
            }

            public void recordingStarted(long timeStampMillis) {
                if (D) { Log.d(TAG, "recordingStarted"); }
                if (mRwBinder != null) {
                    maxTimeSec = mRwBinder.getConfiguration().getMaxRecordingTimeSec();
                }
                startTimeStampMillis = timeStampMillis;
                mRecordingTimeText.post(new Runnable() {
                    public void run() {
                        mRecordingTimeText.setText("");
                    }
                });
            }

            public void recordingStopped(long timeStampMillis) {
                if (D) { Log.d(TAG, "recordingStopped"); }
                mRecordingTimeText.post(new Runnable() {
                    public void run() {
                        mRecordingTimeText.setText("");
                        mRecordingLevelMeterView.reset();
                    }
                });
            }
        });

        mRecordingTask.execute();
    }


    /**
     * Updates the level meter.
     *
     * @param samples
     */
    public synchronized void updateLevelMeter(short [] samples) {
        mRecordingLevelMeterView.setLevel(samples);
        mRecordingLevelMeterView.postInvalidate();
    }


    /**
     * Resets the level meter.
     */
    public synchronized void resetLevelMeter() {
        mRecordingLevelMeterView.reset();
    }


    /**
     * Stops the recording task if it is running.
     */
    private void stopRecording() {
        if ((mRecordingTask != null) && (mRecordingTask.isRecording())) {
            mRecordingTask.stopRecording();
            mHasRecording = true;
            changeToNotRecordingUIState();
        }
    }


    /**
     * Starts playback of a recording that has been made.
     */
    private void startRecordingPlayback() {
        stopRecording();
        if ((mRecordingTask != null) && (mHasRecording)) {
            String recordingFileName = mRecordingTask.getRecordingFileName();
            if (mPlayer != null) {
                stopRecordingPlayback();
            }

            mPlaybackTimerCount = 0;

            mPlayer = new MediaPlayer();
            mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    stopRecordingPlayback();
                }
            });

            try {
                mPlayer.setDataSource(recordingFileName);
                mPlayer.prepare();
                mPlayer.start();

                mPlaybackHandler = new Handler();
                mPlaybackHandler.postDelayed(updatePreviewStatus, 1000);
                changeToRecordingPlaybackUIState();
            } catch (IOException e) {
                Log.e(TAG, "Can not play back the recording!", e);
            }
        }
    }


    private Runnable updatePreviewStatus = new Runnable() {
        public void run() {
            mPlaybackTimerCount++;
            int min = mPlaybackTimerCount / 60;
            int sec = mPlaybackTimerCount - (min * 60);
            mRecordingTimeText.setText(String.format("%1d:%02d", min, sec));
            if (mPlaybackHandler != null) {
                mPlaybackHandler.postDelayed(updatePreviewStatus, 1000);
            }
        }
    };


    /**
     * Stops recording playback.
     */
    private void stopRecordingPlayback() {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }

        if (mPlaybackHandler != null) {
            mPlaybackHandler.removeCallbacks(updatePreviewStatus);
            mPlaybackHandler = null;
            mPlaybackTimerCount = 0;
        }

        changeToNotRecordingUIState();
    }


    /**
     * Resets recording state back to initial state.
     */
    private void resetRecording() {
        if (mRecordingTask != null) {
            stopRecording();
            mRecordingTask.resetRecording();
            mHasRecording = false;
        }
        changeToNotRecordingUIState();
    }


    /**
     * Adds a tag with the specified option to the given tags. This is to
     * create single tag values to be included when submitting a feedback
     * recording, required since these tag options do not appear in the
     * list of speak tags returned by the server with the get_tags call.
     *
     * @param tags to add created tag to
     * @param code of new tag to add
     * @param name of new tag to add
     * @param tagId of new tag option to add
     * @param value of new tag option to add
     * @param selectByDefault value for new tag option
     * @return tags with new tag added
     */
    private RWTags addTagOption(RWTags tags, String code, String name, int tagId, String value, boolean selectByDefault) {
        if (tags != null) {
            RWTags.RWTag tag = tags.new RWTag();
            tag.code = code;
            tag.name = name;
            RWTags.RWOption option = tags.new RWOption();
            option.tagId = tagId;
            option.value = value;
            option.selectByDefault = selectByDefault;
            tag.options.add(option);
            tags.getTags().add(tag);
        }
        return tags;
    }


    /**
     * Submits a contribution recording or a feedback recording to the server.
     */
    private void submitRecording() {
        if (mRecordingTask != null) {
            stopRecording();
            mHasRecording = false;
            mUploadButton.setEnabled(false);
            changeToNotRecordingUIState();

            if (mIsRecordingGeneralFeedback) {
                // feedback recording submit
                RWTags tags = new RWTags();
                addTagOption(tags, "question", "", FEEDBACK_QUESTION_TAG_ID, "", true);
                RWList feedbackTagsList = new RWList(tags);

                new SubmitTask(feedbackTagsList, mRecordingTask.getRecordingFileName(),
                        FEEDBACK_SUBMITTED_VALUE, getString(R.string.recording_submit_problem)).execute();

                showFeedbackSubmittedDialog();
            } else {
                // normal recording submit
                new SubmitTask(mTagsList, mRecordingTask.getRecordingFileName(),
                        CONTRIBUTION_SUBMITTED_VALUE, getString(R.string.recording_submit_problem)).execute();

                // open Thank You screen
                LinearLayout ll = (LinearLayout) findViewById(R.id.speakMapViewLinearLayout);
                if (mGoogleMap != null) {
                    ll.setVisibility(View.VISIBLE);
                    Location loc = mRwBinder.getLastKnownLocation();
                    showMarkerOnMap(loc.getLatitude(), loc.getLongitude());
                    mViewFlipper.setDisplayedChild(THANKS_LAYOUT);
                } else {
                    // no map available, hide view to show background
                    ll.setVisibility(View.INVISIBLE);
                    mViewFlipper.showNext();
                    // showRecordingSubmittedDialog();
                }
            }
        }
    }


    private void showRecordingSubmittedDialog() {
        AlertDialog.Builder alertBox;
        alertBox = new AlertDialog.Builder(this);
        alertBox.setTitle(R.string.speak_submitted_header_text);
        alertBox.setMessage(R.string.speak_submitted_recording_text);
        alertBox.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                finish();
            }
        });
        alertBox.setCancelable(true);
        alertBox.show();
    }


    private void showFeedbackSubmittedDialog() {
        AlertDialog.Builder alertBox;
        alertBox = new AlertDialog.Builder(this);
        alertBox.setTitle(R.string.feedback_submitted_title);
        alertBox.setMessage(R.string.feedback_submitted_message);
        alertBox.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                finish();
            }
        });
        alertBox.setCancelable(true);
        alertBox.show();
    }


    /**
     * Displays a dialog for a (social media) sharing message that was
     * sent back to the app by the framework after succesfully submitting
     * a recording. When confirmed an ACTION_SEND intent is created and
     * used to allow the user to select a matching activity (app) to
     * handle it. This can be Facebook, Twitter, email, etc., whatever
     * matching app that is installed on the device.
     *
     * @param message to be shared
     */
    private void confirmSharingMessage(final String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.confirm_sharing_title)
                .setMessage(message)
                .setCancelable(true)
                .setPositiveButton(R.string.confirm_sharing_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.putExtra(Intent.EXTRA_SUBJECT, R.string.sharing_subject);
                        intent.putExtra(Intent.EXTRA_TEXT, message);
                        intent.setType("text/plain");
                        startActivity(Intent.createChooser(intent, getString(R.string.sharing_chooser_title)));
                    }
                })
                .setNegativeButton(R.string.confirm_sharing_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        builder.create().show();
    }


    private class ConfirmDelete implements View.OnClickListener {
        DialogInterface.OnClickListener continueListener;

        public ConfirmDelete(DialogInterface.OnClickListener listener) {
            continueListener = listener;
        }

        public void onClick(View v) {
            AlertDialog.Builder alertBox;

            alertBox = new AlertDialog.Builder(RwSpeakActivity.this);
            alertBox.setTitle(R.string.confirm_title);
            alertBox.setMessage(R.string.confirm_message);
            alertBox.setPositiveButton(android.R.string.yes, continueListener);
            alertBox.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // void
                }
            });

            alertBox.show();
        }
    }


    private class ContinueRerecord implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int whichButton) {
            dialog.cancel();
            resetRecording();
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
     * Async task that calls rwSubmit for direct processing, but in the
     * background for Android to keep the UI responsive.
     *
     * @author Rob Knapen
     */
    private class SubmitTask extends AsyncTask<Void, Void, String> {

        private RWList selections;
        private String filename;
        private String submitted;
        private String errorMessage;

        public SubmitTask(RWList selections, String filename, String submitted, String errorMessage) {
            this.selections = selections;
            this.filename = filename;
            this.submitted = submitted;
            this.errorMessage = errorMessage;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                if (SUBMIT_RECORDING) {
                    mRwBinder.rwSubmit(selections, filename, submitted, true, !mIsRecordingGeneralFeedback);
                } else {
                    Log.d(TAG, "Submitting recording disabled in source code.");
                }
                return null;
            } catch (Exception e) {
                return errorMessage;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            if (result != null) {
                showMessage(result, true, false);
            }
        }
    }

    public static void showLegalDialogIfNeeded(final Context context, RWService rwService) {
        String legalText = rwService.getConfiguration().getLegalAgreement();
        boolean accepted = Settings.getSharedPreferences().getBoolean(PREFS_KEY_LEGAL_NOTICE_ACCEPTED, false);

        if (accepted) {
            context.startActivity(new Intent(context, ClassRegistry.get("RwSpeakActivity")));
        } else {
            AlertDialog.Builder builder;
            builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.agreement);
            builder.setMessage(legalText);
            builder.setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Settings.getSharedPreferences().edit().putBoolean(PREFS_KEY_LEGAL_NOTICE_ACCEPTED, true).commit();
                    context.startActivity(new Intent(context, ClassRegistry.get("RwSpeakActivity")));
                    dialog.dismiss();
                }
            });
            builder.setNegativeButton(R.string.decline, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            builder.setCancelable(true);
            builder.show();
        }
    }

    private void showRecord() {
        mViewFlipper.setDisplayedChild(RECORD_LAYOUT);

        mTitleView.setText(R.string.record_button);
        mLeftTitleButton.setVisibility(View.INVISIBLE);

        mRightTitleButton.setVisibility(View.VISIBLE);
        mRightTitleButton.setText(R.string.cancel);
        mRightTitleButton.setOnClickListener(mCancelListener);
    }

    private void showFilters() {
        mViewFlipper.setDisplayedChild(FILTER_LAYOUT);
    }

    private void showThanks() {
        mViewFlipper.setDisplayedChild(THANKS_LAYOUT);
    }

    private void showGeneralFeedback() {
        mViewFlipper.setDisplayedChild(RECORD_LAYOUT);

        mTitleView.setText(R.string.speak_feedback_header_text);

        mRightTitleButton.setVisibility(View.VISIBLE);
        mRightTitleButton.setText(R.string.cancel);
        mRightTitleButton.setOnClickListener(mCancelListener);
    }
}
