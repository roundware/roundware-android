/**
 * Roundware Android code is released under the terms of the GNU General Public License.
 * See COPYRIGHT.txt, AUTHORS.txt, and LICENSE.txt in the project root directory for details.
 */
package org.roundware.service;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.StrictMode;
import android.text.TextUtils;
import android.util.Log;

import org.apache.http.HttpException;
import org.apache.http.HttpStatus;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.roundware.service.util.RWList;
import org.roundware.service.util.RWSharedPrefsHelper;
import org.roundware.service.util.RWUriHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

// import android.os.StrictMode;

/**
 * Service for background playback of Roundware sound stream and handle the
 * various server calls. Calls can either be queued that is checked at a
 * regular interval, or sent directly. 
 * 
 * Notes:
 * High level Roundware server communication related methods are all
 * prefixed with 'rw' (rwRequestStream, rwModifyStream, rwSubmit, etc.).
 * 
 * High level methods for controlling sound playback all are prefix with
 * 'playback' (playbackStart, playbackStop, playbackFadeIn, etc.).
 * 
 * @author Rob Knapen
 */
@SuppressLint("DefaultLocale") public class RWService extends Service implements Observer, RWIcecastInputStream.IcyMetaDataListener {
    
    // debugging
    private final static String TAG = "RWService";
    private final static boolean D = true;

    // playback notification
    private final static int NOTIFICATION_ID = 10001;
    private final static int ERROR_RETRY_COUNT = 3;

    /**
     * Connection states of the Roundware session.
     */
    public enum SessionState {
        /**
         * RWService session has not yet been successfully initialized. 
         */
        UNINITIALIZED,
        
        /**
         * RWService session is initializing.
         */
        INITIALIZING,
        
        /**
         * Session is on-line; project configuration has been retrieved
         * and a session ID is available. When required by the project
         * all web content files have been loaded too.
         */
        ON_LINE,
        
        /**
         * Session is off-line; last tried server operation timed out. 
         */
        OFF_LINE 
    }
    
    /**
     * Types of server messages that can be decoded.
     */
    private enum ServerMessageType { ERROR, USER, SHARING, TRACEBACK }
    
    // service binder
    private final IBinder mBinder = new RWServiceBinder();

    private RWActionFactory mActionFactory;

    private MediaPlayer mPlayer;
    private RWAudioManager mAudioManager;
    private int errorCount = 0;
    private boolean isPrepared = false;

    private RWStreamProxy mProxy;
    private WifiLock mWifiLock;
    private Timer mQueueTimer;
    private long mLastRequestMsec;
    private long mLastStateChangeMsec;
    
    private PendingIntent mNotificationPendingIntent;
    private Notification mRwNotification;
    private String mNotificationTitle;
    private String mNotificationDefaultText;
    private int mNotificationIconId;
    private Class<?> mNotificationActivity = null;

    private String mContentFilesLocalDir = null;
    private boolean mAlwaysDownloadContent = false;
    private boolean mUseExternalStorageForContent = false;
    
    private String mServerUrl;
    private String mStreamUrl;
    private boolean mShowDetailedMessages = false;
    private boolean mStartPlayingWhenReady = false;
    private boolean mOnlyConnectOverWiFi = false;
    private int mVolumeLevel = 0;
    private int mMinVolumeLevel = 0;
    private int mMaxVolumeLevel = 50;
    private float mVolumeStepMultiplier = 0.95f; // 1 dB = 0.89

    private SessionState mSessionState = SessionState.UNINITIALIZED;
    private long mStartTime = 0;
    private long mPrepareTime = 0;
    private RWConfiguration configuration;
    private RWTags tags;


    //TODO support telephony interruption support?
    /**
     * Service binder used to communicate with the Roundware service.
     * 
     * @author Rob Knapen
     */
    public class RWServiceBinder extends Binder {
        public RWService getService() {
            return RWService.this;
        }
    }

    
    /**
     * Retrieves the configuration settings for a project from
     * the server. When the request is successful it will also start the timer
     * that controls the queue processing and sending idle pings when playing
     * a stream and there is no other activity. 
     */
    void retrieveConfiguration(final Context context, final String deviceId, final String projectId) {
        debugLog("Retrieving configuration for project");
        perform(mActionFactory.createRetrieveProjectConfigurationAction(deviceId, projectId), true,
                new ServicePerformListener() {

                    @Override
                    public void onPerformComplete(String result) {
                        debugLog("Retrieve project configuration result: " + result);

                        // try to use cache when no server data received
                        boolean usingCache = false;
                        if (result == null) {
                            result = RWSharedPrefsHelper.loadJSONArray(context, RW.PROJECT_CONFIG_CACHE, projectId);
                            usingCache = true;
                        } else {
                            // cache current data
                            RWSharedPrefsHelper.saveJSONArray(context, RW.PROJECT_CONFIG_CACHE, projectId, result);
                        }

                        if (result == null) {
                            Log.i(TAG, "Could not retrieve configuration data from server and no cached data available!");
                            broadcast(RW.NO_CONFIGURATION);
                        } else {
                            configuration.assignFromJsonServerResponse(result, usingCache);
                            if (usingCache) {
                                configuration.setSessionId("-1");
                            }
                            broadcast(RW.CONFIGURATION_LOADED);
                        }
                    }
                }
        );
    }

    /**
     * Retrieves the configuration settings for a project from
     * the server. When the request is successful it will also start the timer
     * that controls the queue processing and sending idle pings when playing
     * a stream and there is no other activity. 
     */
    private void retrieveTags(final Context context, final String projectId) {
        perform(mActionFactory.createRetrieveTagsForProjectAction(projectId), true, new ServicePerformListener() {
            @Override
            public void onPerformComplete(String result) {
                debugLog("Retrieve project tags result: " + result);

                // try to use cache when no server data received
                boolean usingCache = false;
                if (result == null) {
                    result = RWSharedPrefsHelper.loadJSONObject(context, RW.PROJECT_TAGS_CACHE, projectId);
                    usingCache = true;
                } else {
                    // cache current data
                    RWSharedPrefsHelper.saveJSONObject(context, RW.PROJECT_TAGS_CACHE, projectId, result);
                }

                if (result == null) {
                    Log.w(TAG, "Could not retrieve tags data from server and no cached data available!");
                    broadcast(RW.NO_TAGS);
                } else {
                    tags.fromJson(result, usingCache ? RWTags.FROM_CACHE : RWTags.FROM_SERVER);
                    broadcast(RW.TAGS_LOADED);
                }
            }
        });
    }
    
    
    /**
     * Starts play back of a sound stream from the server. When the
     * request is successful it will also start the ping timer that sends heart
     * beats back to the server.
     */
    private void startPlayback(RWList tags) {
        RWList selections = tags;
        perform(mActionFactory.createRequestStreamAction(selections), true, new ServicePerformListener() {
            @Override
            public void onPerformComplete(String result) {
                debugLog("Starting Playback from Service result: " + result);
                // check for errors
                mStreamUrl = null;
                if (result == null) {
                    Log.e(TAG, "Operation failed, no response available to start audio stream from.", null);
                    broadcast(RW.UNABLE_TO_PLAY);
                } else {
                    String streamUrlKey = getString(R.string.rw_key_stream_url);
                    try {
                        JSONObject jsonObj = new JSONObject(result);
                        mStreamUrl = jsonObj.optString(streamUrlKey, null);
                    } catch (JSONException e) {
                        Log.e(TAG, "Invalid response from server", e);
                        broadcast(RW.UNABLE_TO_PLAY);
                    }

                    if ((mStreamUrl == null) || (mStreamUrl.length() == 0)) {
                        broadcast(RW.UNABLE_TO_PLAY);
                    } else {
                        preparePlayer(0);
                    }
                }
            }
        });
    }

    private void preparePlayer(int startingErrorCount ){
        this.errorCount = startingErrorCount;
        preparePlayer();
    }

    private void preparePlayer(){

        debugLog("Starting MediaPlayer for stream: " + mStreamUrl);
        stopPlayer();

        if( TextUtils.isEmpty(mStreamUrl) ) {
            Log.w(TAG, "preparePlayer with no url!");
        }else{
            //TODO if android 4.1+ use exoPlayer instead of mediaPlayer
            if (mProxy == null) {
                mProxy = new RWStreamProxy(this);
                mProxy.init();
                mProxy.start();
            }
            String playUrl = String.format("http://127.0.0.1:%d/%s",
                    mProxy.getPort(), mStreamUrl);

            try {
                debugLog("reset: " + playUrl);
                synchronized (this) {
                    if(mPlayer == null){
                        createPlayer();
                    }else {
                        mPlayer.reset();
                    }
                    mPlayer.setDataSource(playUrl);
                    mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    debugLog("Preparing: " + playUrl);
                    mPrepareTime = 0;
                    mStartTime = System.currentTimeMillis();
                    mPlayer.prepareAsync();
                }
                debugLog("Waiting for prepare");
                // get wifi lock, will be released when playback is stopped
                mWifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
                        .createWifiLock(WifiManager.WIFI_MODE_FULL, "roundware_playback_wifilock");

                mWifiLock.setReferenceCounted(false);
                mWifiLock.acquire();

                // send log message about stream started
                rwSendLogEvent(R.string.rw_et_start_listen, null, null, true);
            } catch (Exception ex) {
                if (mWifiLock != null) {
                    mWifiLock.release();
                }

                Log.e(TAG, ex.toString());
                broadcast(RW.UNABLE_TO_PLAY);

                // broadcast error message
                Intent intent = new Intent();
                String message = getString(R.string.roundware_error_mediaplayer_problem);
                message = message + "\n\nException: " + ex.getMessage();
                intent.setAction(RW.ERROR_MESSAGE);
                intent.putExtra(RW.EXTRA_SERVER_MESSAGE, message);
                debugLog("Going to send broadcast event, error message = " + message);
                sendBroadcast(intent);
            }
        }
    }

    private void stopPlayer(){
        debugLog("stop");
        mAudioManager.releaseAudioFocus();
        if(isPrepared) {
            if (mProxy != null) {
                mProxy.stop();
                mProxy = null;
            }
            mPlayer.stop();
            isPrepared = false;
        }
    }

    
    /**
     * Receiver for connectivity state events. Used to manage the session
     * on-line and off-line state.
     */
    private BroadcastReceiver connectivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean noConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
            if (noConnectivity) {
                if (mSessionState == SessionState.ON_LINE) {
                    manageSessionState(SessionState.OFF_LINE);
                }
            } else {
                if (mSessionState == SessionState.OFF_LINE) {
                    if (!mOnlyConnectOverWiFi) {
                        manageSessionState(SessionState.ON_LINE);
                    } else {
                        NetworkInfo currentNetworkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                        if (currentNetworkInfo != null) {
                            if ((currentNetworkInfo.isConnected()) && (currentNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI)) {
                                manageSessionState(SessionState.ON_LINE);
                            }
                        }
                    }
                }
            }
        }
    };
    
    
    /**
     * Receiver for own Roundware broadcasts. Used to manage the session
     * on-line and off-line state.
     */
    private BroadcastReceiver rwReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            debugLog("Received broadcast intent with action: " + intent.getAction());
            
            if (RW.CONFIGURATION_LOADED.equalsIgnoreCase(intent.getAction())) {
                if (mSessionState != SessionState.ON_LINE) {
                    if (configuration.getDataSource() != RWConfiguration.FROM_SERVER) {
                        // TODO Check if chached content and tags are available
                        // if so go to off_line state
                        // else go to unitialized state
                        manageSessionState(SessionState.OFF_LINE);
                    } else {
                        if (isContentDownloadRequired(context)) {
                            startContentDownload(context);
                        } else {
                            // otherwise can go to on_line now
                            broadcast(RW.CONTENT_LOADED);
                        }
                    }
                }
            } else if (RW.NO_CONFIGURATION.equalsIgnoreCase(intent.getAction())) {
                // loading configuration failed - switch to uninitialized if needed
                manageSessionState(SessionState.UNINITIALIZED);
            } else if (RW.CONTENT_LOADED.equalsIgnoreCase(intent.getAction())) {
                if (mSessionState != SessionState.ON_LINE) {
                    retrieveTags(context, configuration.getProjectId());
                }                
            } else if (RW.NO_CONTENT.equalsIgnoreCase(intent.getAction())) {
                manageSessionState(SessionState.UNINITIALIZED);
            } else if (RW.TAGS_LOADED.equalsIgnoreCase(intent.getAction())) {
                manageSessionState(SessionState.ON_LINE);
            } else if (RW.NO_TAGS.equalsIgnoreCase(intent.getAction())) {
                manageSessionState(SessionState.UNINITIALIZED);
            }
            
            // operation failed, if due to timeout (UknownHostException) switch to
            // from on-line to off-line
            if (intent.getAction().endsWith(RW.BROADCAST_FAILURE_POSTFIX)) {
                String eClass = intent.getStringExtra(RW.EXTRA_FAILURE_EXCEPTION_CLASS);
                Throwable e = (Throwable) intent.getExtras().get(RW.EXTRA_FAILURE_EXCEPTION);
                if (eClass.equals(UnknownHostException.class.getName())) {
                    if (mSessionState == SessionState.ON_LINE) {
                        manageSessionState(SessionState.OFF_LINE);
                    }
                } else {
                    // send error log back to server for later analysis
//                    final Writer result = new StringWriter();
//                    final PrintWriter printWriter = new PrintWriter(result);
//                    e.printStackTrace(printWriter);
                    Log.e(TAG, "Broadcast Failure Received: " + intent.getStringExtra(RW.EXTRA_FAILURE_EXCEPTION_MESSAGE));
                    // DO NOT DO THIS ON MAIN THREAD!!
                    //rwSendLogEvent(R.string.rw_et_client_error, null, result.toString(), true);
                }
            }

            // operation successful, if off-line switch to on-line
            // fail-safe, probably should never happen this way since
            // connectivity will be restored first and state already updated
            if (intent.getAction().endsWith(RW.BROADCAST_SUCCESS_POSTFIX)) {
                if (mSessionState == SessionState.OFF_LINE) {
                    NetworkInfo currentNetworkInfo = (NetworkInfo) intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                    if (currentNetworkInfo != null) {
                        if ((!mOnlyConnectOverWiFi) || (currentNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI)) {
                            manageSessionState(SessionState.ON_LINE);
                        }
                    }
                }
            }
        }
    };
    

    /**
     * Checks if there is any reason to download the content files for the
     * app anew.
     * 
     * @param context
     * @return true when downloading content files makes sense
     */
    private boolean isContentDownloadRequired(Context context) {
        debugLog("Checking if new content files need to be downloaded");

        // configuration does not exist or does not use content files
        if ((configuration == null) || (configuration.getContentFilesUrl() == null) || (configuration.getContentFilesVersion() < 0)) {
            return false;
        }
        
        // overrides to always download for testing and debugging
        if (mAlwaysDownloadContent || configuration.isContentFilesAlwaysDownload() || mContentFilesLocalDir == null) {
            return true;
        }
        
        // check last downloaded version numbers and file url
        String filesUrl = configuration.getContentFilesUrl();
        int filesVersion = configuration.getContentFilesVersion();
        
        RWSharedPrefsHelper.ContentFilesInfo currentFileInfo = RWSharedPrefsHelper.loadContentFilesInfo(context, RW.LAST_DOWNLOADED_CONTENT_FILES_INFO);
        if (currentFileInfo == null) {
            return true;
        } else if (currentFileInfo.filesVersion != filesVersion) {
            return true;
        } else if ((currentFileInfo.filesUrl == null) || (!currentFileInfo.filesUrl.equals(filesUrl))) {
            return true;
        }
        
        // check if last downloaded content files are still available
        // (at least the folder still exists and is not empty)
        String filesDirName = currentFileInfo.filesStorageDirName;
        File filesDir = new File(filesDirName);
        if ((!filesDir.exists()) || (!filesDir.isDirectory()) || (filesDir.list().length == 0)) {
            return true;
        }
        
        return false;
    }
    
    
    private void startContentDownload(Context context) {
        debugLog("Starting download of new content files");
        
        // figure out where to store the files
        final Context ctx = context;
        File filesDir;
        
        if (mUseExternalStorageForContent) {
            filesDir = ctx.getExternalFilesDir(null);
        } else {
            filesDir = ctx.getFilesDir();
        }
        final String targetDirName = filesDir.getAbsolutePath();

        // get current content file info from project configuration
        final String fileUrl = configuration.getContentFilesUrl();
        final int filesVersion = configuration.getContentFilesVersion();

        // start async task to download and unpack content file
        new RWZipDownloadingTask(fileUrl, targetDirName, new RWZipDownloadingTask.StateListener() {
            @Override
            public void downloadingStarted(long timeStampMsec) {
                // NOTE: remember this is called from an async background task
                // void - use for progress indicator if needed
            }

            @Override
            public void downloading(long timeStampMsec, long bytesProcessed, long totalBytes) {
                // NOTE: remember this is called from an async background task
                // void - use for progress indicator if needed
            }
            
            @Override
            public void downloadingFinished(long timeStampMsec, String targetDir) {
                RWSharedPrefsHelper.saveContentFilesInfo(ctx, RW.LAST_DOWNLOADED_CONTENT_FILES_INFO, 
                        new RWSharedPrefsHelper.ContentFilesInfo(fileUrl, filesVersion, targetDir)
                );
                mContentFilesLocalDir = targetDir;
                broadcast(RW.CONTENT_LOADED);
            }
            
            @Override
            public void downloadingFailed(long timeStampMsec, String errorMessage) {
                // TODO: pass error message in intent?
                mContentFilesLocalDir = null;
                broadcast(RW.NO_CONTENT);
            }
        }).execute();
    }
    
    
    /**
     * Adds to the specified IntentFilter the actions for the SUCCESS,
     * FAILURE, and QUEUED intent broadcasts for the server calls with the
     * given operationNames.
     * 
     * @param filter IntentFilter to add actions to
     * @param operationNames to create action filters for
     * @return updated IntentFilter
     */
    public static IntentFilter addOperationsToIntentFilter(IntentFilter filter, String... operationNames) {
        for (String opName : operationNames) {
            filter.addAction(RW.BROADCAST_PREFIX + opName + RW.BROADCAST_FAILURE_POSTFIX);
            filter.addAction(RW.BROADCAST_PREFIX + opName + RW.BROADCAST_SUCCESS_POSTFIX);
            filter.addAction(RW.BROADCAST_PREFIX + opName + RW.BROADCAST_QUEUED_POSTFIX);
        }
        
        return filter;
    }
    
    
    /**
     * Creates an IntentFilter for the SUCCESS, FAILURE and QUEUED broadcast
     * intents for all the current server calls.
     * 
     * @return IntentFilter to receive low level server call broadcasts
     */
    public IntentFilter createOperationsIntentFilter() {
        IntentFilter filter = new IntentFilter();
        return addOperationsToIntentFilter(filter,
                getString(R.string.rw_op_add_asset_to_envelope),
                getString(R.string.rw_op_create_envelope),
                getString(R.string.rw_op_get_config),                
                getString(R.string.rw_op_get_stream),
                getString(R.string.rw_op_get_tags),
                getString(R.string.rw_op_heartbeat),
                getString(R.string.rw_op_log_event),
                getString(R.string.rw_op_modify_stream)
                );
    }
    
    
    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    @Override
    public void onCreate() {
        super.onCreate();

        // set strict mode usage
        // StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectCustomSlowCalls().penaltyLog().build());

        // create default configuration and tags
        configuration = new RWConfiguration(this);
        tags = new RWTags();

        // get default server url from resources and reset other critical vars
        mServerUrl = getString(R.string.rw_spec_server_url);
        mStreamUrl = null;

        // create a factory for actions
        mActionFactory = new RWActionFactory(this);

        // create a queue for actions
        RWActionQueue.instance().init(this);

        // listen to connectivity state broadcasts
        registerReceiver(connectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        // listen to own server calls success and failure broadcasts
        IntentFilter filter = createOperationsIntentFilter();
        filter.addAction(RW.CONFIGURATION_LOADED);
        filter.addAction(RW.NO_CONFIGURATION);
        filter.addAction(RW.CONTENT_LOADED);
        filter.addAction(RW.NO_CONTENT);
        filter.addAction(RW.TAGS_LOADED);
        filter.addAction(RW.NO_TAGS);
        registerReceiver(rwReceiver, filter);
        
        // setup for GPS callback
        RWLocationTracker.instance().init(this);
        RWLocationTracker.instance().addObserver(this);
        

        // simple audio management
        mAudioManager = new RWAudioManager( getApplicationContext() );
    }

    /**
     * Checks if the device's GPS is available and switched on.
     * 
     * @return true when GPS is enabled
     */
    public boolean isGpsEnabled() {
        return RWLocationTracker.instance().isGpsEnabled();
    }

    
    /**
     * Checks if the device's Network based location service is
     * available and may be used.
     * 
     * @return true when Network location service is enabled
     */
    public boolean isNetworkLocationEnabled() {
        return RWLocationTracker.instance().isNetworkLocationEnabled();
    }


    /**
     * Request location updates from the most accurate available location
     * service (GPS, Network).
     */
    private void startLocationUpdates() {
        if ((configuration != null) && (configuration.isUsingLocation())) {
            RWLocationTracker.instance().startLocationUpdates(
                    configuration.getMinLocationUpdateTimeMSec(), 
                    (float)configuration.getMinLocationUpdateDistanceMeter(),
                    configuration.getUseGpsIfPossible());
        }
    }
    
    
    /**
     * Stop receiving location updates, and allows the device to shutdown the
     * location service providers to reduce power consumption.
     */
    private void stopLocationUpdates() {
        RWLocationTracker.instance().stopLocationUpdates();
    }

    
    /**
     * Retrieves the last known location by the location services of the
     * device. This does not force updating and might be out of date.
     * 
     * @return Location, last known by the location services
     */
    public Location getLastKnownLocation() {
        return RWLocationTracker.instance().getLastLocation();
    }

    
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // intent will be null on restart!
        if (intent != null) {
            getSettingsFromIntent(intent);
        }

        if( mNotificationActivity != null) {
            // create a pending intent to start the specified activity from the notification
            Intent ovIntent = new Intent(this, mNotificationActivity);
            //FIXME new_task?
            mNotificationPendingIntent = PendingIntent.getActivity(this, 0, ovIntent, Intent.FLAG_ACTIVITY_NEW_TASK);

            //FIXME remove notification onStop
            // create a notification and move service to foreground
            mRwNotification = new Notification(mNotificationIconId, "Roundware Service Started", System.currentTimeMillis());
            mRwNotification.number = 1;
            mRwNotification.flags = mRwNotification.flags
                    | Notification.FLAG_FOREGROUND_SERVICE
                    | Notification.FLAG_ONGOING_EVENT
                    | Notification.FLAG_NO_CLEAR;
            setNotificationText("");

            startForeground(NOTIFICATION_ID, mRwNotification);
        }

        // start initializing the Roundware session, this will attempt to get the configuration, tags and content
        manageSessionState(SessionState.INITIALIZING);
        
        return Service.START_STICKY;
    }



    /**
     * Parse the raw meta data and broadcast. This may likely be somewhat preliminary, so expect
     * this to change.
      * @param rawMetaData
     */
    @Override
    public void OnMetaDataReceived(String rawMetaData) {

        if(RW.DEBUG_W_FAUX_TAGS){
            Random random =new Random();
            if(rawMetaData.contains(RW.METADATA_URI_NAME_TAGS)){
                StringBuilder sb = new StringBuilder();
                String param = RW.METADATA_URI_NAME_TAGS + '=';
                sb.append(param);
                sb.append(Math.abs(random.nextInt() % 100) + 6).append(",");
                sb.append(Math.abs(random.nextInt() % 100) + 6).append(",");
                sb.append(Math.abs(random.nextInt() % 100) + 6).append(",");
                sb.append(Math.abs(random.nextInt() % 100) + 6).append(",");
                sb.append(RW.DEBUG_FAUX_TAG++ %6);

                rawMetaData = rawMetaData.replace(param, sb.toString());
                Log.i(TAG,"Pushing faux meta: " + rawMetaData);
            }
        }


        Map<String, String> map = RWIcecastInputStream.parseMetadata(rawMetaData);

        int asset = -1;
        int tags[] = new int[0];

        Intent intent = new Intent();
        intent.setAction(RW.STREAM_METADATA_UPDATED);
        // currently we dont care about the map's keys, just the values
        for(String value : map.values()) {
            String[] subvalues = RWIcecastInputStream.splitMetaValue(value);
            if (subvalues.length == 2) {
                // we want the second value, which contains the metadata
                if (!TextUtils.isEmpty(subvalues[1])) {
                    // structure is a uri
                    // there is just one uri expected
                    Uri uri = RWUriHelper.parse(subvalues[1]);
                    intent.putExtra(RW.EXTRA_STREAM_METADATA_URI, uri);
                }
            }
        }
        sendBroadcast(intent);
    }

    /**
     * When not already playing, create a music player and start its
     * initialization in the background. Use a broadcast receiver to
     * be notified of completion or errors. 
     * 
     * @param tags of tags options for the audio
     */
    public void playbackStart(RWList tags) {
        // FIXME: Not the correct way to handle this.  Don't run on the main thread!!!!!
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        debugLog("+++ playbackStart +++");
        if (!isPlaying()) {
            createPlayer();
            startPlayback(tags);
        }
    }
    

    /**
     * Stops music playback and releases the music player used, allowing the
     * device to free up the memory. Call this method when no longer interested
     * in listening to the audio stream.
     */
    public void playbackStop() {
        releasePlayer();
    }

    
    private void getSettingsFromIntent(Intent intent) {
        if ((intent != null) && (intent.getExtras() != null)) {
            // get device id from intent
            String deviceId = intent.getExtras().getString(RW.EXTRA_DEVICE_ID);
            if (deviceId != null) {
                configuration.setDeviceId(deviceId);
            }

            // get project id from intent
            String projectId = intent.getExtras().getString(RW.EXTRA_PROJECT_ID);
            if (projectId != null) {
                configuration.setProjectId(projectId);
            }
            
            // server url override (can be null)
            String serverUrlOverride = intent.getExtras().getString(RW.EXTRA_SERVER_URL_OVERRIDE);
            if ((serverUrlOverride != null) && (serverUrlOverride.length() > 0)) {
                mServerUrl = serverUrlOverride;
            }

            // app web content downloading
            mAlwaysDownloadContent = intent.getExtras().getBoolean(RW.EXTRA_WEB_CONTENT_ALWAYS_DOWNLOAD, false);
            mUseExternalStorageForContent = intent.getExtras().getBoolean(RW.EXTRA_WEB_CONTENT_EXTERNAL_STORAGE, false);
            
            // notification icon and handling class
            mNotificationTitle = intent.getExtras().getString(RW.EXTRA_NOTIFICATION_TITLE);
            if (mNotificationTitle == null) {
                mNotificationTitle = "Roundware";
            }
            mNotificationDefaultText = intent.getExtras().getString(RW.EXTRA_NOTIFICATION_DEFAULT_TEXT);
            if (mNotificationDefaultText == null) {
                mNotificationDefaultText = "Return to app";
            }
            mNotificationIconId = intent.getExtras().getInt(RW.EXTRA_NOTIFICATION_ICON_ID, R.drawable.status_icon);
            String className = intent.getExtras().getString(RW.EXTRA_NOTIFICATION_ACTIVITY_CLASS_NAME);
            try {
                if (className != null) {
                    mNotificationActivity = Class.forName(className);
                }
            } catch (Exception e) {
                try{
                    ClassLoader classLoader = Thread.currentThread().getContextClassLoader(); //getClassLoader();
                    mNotificationActivity = classLoader.loadClass(className);
                } catch (ClassNotFoundException e1) {
                    Log.e(TAG, "Unknown class specified for handling " + "notification: " + className);
                    mNotificationActivity = null;

                }
            }
        }
    }

    
    @Override
    public void onDestroy() {
        stopService();
        stopLocationUpdates();
        unregisterReceiver(connectivityReceiver);
        unregisterReceiver(rwReceiver);
        stopForeground(true);
        super.onDestroy();
    }

    
    /**
     * Shut down the RWService instance. Makes sure the location updates
     * are stopped, the background processing of the action queue is stopped,
     * the music player is stopped and released, and finally the service
     * itself is terminated. 
     */
    public void stopService() {
        stopQueueTimer();
        releasePlayer();
        stopSelf();
    }

    
    /**
     * Receives and handles location updates from RWLocationTracker.
     */
    @Override
    public void update(Observable observable, Object data) {
        Location location = RWLocationTracker.instance().getLastLocation();
        if (location == null) {
            debugLog( getString(R.string.roundware_no_location_info));
        } else {
            double lat = location.getLatitude();
            double lon = location.getLongitude();
            if (D) {
                Log.d(TAG, String.format(
                        "session id '%s': New position info lat=%.6f lon=%"
                                + ".6f" + " provider=%s accuracy=%.6fm",
                        configuration.getSessionId(), lat, lon, location.getProvider(),
                        location.getAccuracy()), null);
            }
            rwSendMoveListener(true);
            broadcastLocationUpdate(lat, lon, location.getProvider(), location.getAccuracy());
        }
    }

    
    /**
     * Updates the text in the RWService notification placed in the Android
     * notification bar on the screen of the device.
     * 
     * @param message to be displayed
     */
    public void setNotificationText(String message) {
        if ((mRwNotification != null) && (mNotificationPendingIntent != null)) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                if (message != null) {
                    boolean debugMsg = message.startsWith(".");
                    String msg = debugMsg ? message.subSequence(1, message.length()).toString() : message;

                    boolean defaultMsg = message.equalsIgnoreCase(mNotificationDefaultText);

                    if ((!debugMsg) || (mShowDetailedMessages)) {
                        mRwNotification.setLatestEventInfo(this, mNotificationTitle, msg, mNotificationPendingIntent);
                        if (!defaultMsg) {
                            mRwNotification.tickerText = msg;
                        } else {
                            mRwNotification.tickerText = "";
                        }
                    }
                }

                mRwNotification.when = System.currentTimeMillis();
                mRwNotification.number = RWActionQueue.instance().count();
                nm.notify(NOTIFICATION_ID, mRwNotification);
            }
        }
    }

    
    /**
     * Access the factory that can be used to create RWAction instances
     * that are usable with this RWService instance and the project it is
     * configured for.
     * 
     * @return RWActionFactory for creating RWAction instances
     */
    public RWActionFactory getActionFactory() {
        return mActionFactory;
    }

    
    /**
     * Access the configuration information of this RWService instance.
     * When it is started and initialized the first thing the RWService
     * does is to read the configuration info for a project from the
     * Roundware server. Various project specific settings are then made
     * available through this RWConfiguration instance.
     * 
     * Note: Use a broadcast receiver for the RW_SESSION_INITIALISED
     * intent to be notified when the configuration has been fully
     * received from the server.
     * 
     * @return RWConfiguration with Roundware project settings
     */
    public RWConfiguration getConfiguration() {
        return configuration;
    }
    

    /**
     * Access the tags information of this RWService instance. After
     * initializing and retrieving the project configuration from the
     * server, the RWService also retrieves the tags for the project.
     * Tags can be used to mark recordings, specify selections, etc.
     * 
     * Note: Use a broadcast receiver for the RW_TAGS_LOADED intent
     * to be notified when the tags have been fully received from the
     * server.
     * 
     * @return RWTags with the Roundware project tags
     */
    public RWTags getTags() {
        return tags;
    }
    

    /**
     * Returns the currently used URL to access the Roundware server.
     * 
     * @return URL for Roundware server
     */
    public String getServerUrl() {
        return mServerUrl;
    }

    
    /**
     * Returns the URL of the current audio stream (if any).
     * 
     * @return URL of current audio stream
     */
    public String getStreamUrl() {
        return mStreamUrl;
    }
    
    
    /**
     * Returns the local directory name where web content files have been
     * stored. These files are downloaded from the server for the project
     * and contain HTML/CSS/JS content to be displayed in web views for the
     * projects' Listen and Speak filters. NULL will be returned if no
     * content files are used or when download has failed. The broadcast
     * intent NO_CONTENT signals that download of the content files was
     * not possible.
     * 
     * @return path name where content files have been stored
     */
    public String getContentFilesDir() {
        return mContentFilesLocalDir;
    }


    /**
     * Reads the specified content file and returns its content as a single
     * String. Some content files might need further processing, e.g. to
     * replace markers with specific information. 
     *
     * @param contentFileName to read
     * @return String with content of the file
     * @throws IOException on error
     */
    public String readContentFile(String contentFileName) throws IOException {
        String data = "";
        BufferedReader br = new BufferedReader(new FileReader(contentFileName));
        try {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            data = sb.toString();
        } finally {
            br.close();
        }
        return data;
    }

    
    /**
     * Returns true when the framework is set to always download the content
     * files when it is required by an app. When not the download of content
     * depends on the content files version specified in the configuration of
     * the project (i.e. only newer version content will be downloaded).
     * 
     * @return true when download of web content is forced
     */
    public boolean isContentAlwaysDownloaded() {
        return mAlwaysDownloadContent;
    }
    
    
    /**
     * Returns true when the framework is set to store content files that are
     * downloaded for the app on external storage. Otherwise the more private
     * internal storage is used.
     * 
     * @return true when content files are saved on external storage
     */
    public boolean isContentStoredExternally() {
        return mUseExternalStorageForContent;
    }

    
    /**
     * Checks if the current session id indicates that the media player is
     * playing the static soundtrack from the server. This stream is e.g.
     * returned when the user starts the app outside the geographical range of
     * the project.
     * 
     * @return true when the media player is playing the static soundtrack
     */
    public boolean isPlayingStaticSoundtrack() {
        String val = getString(R.string.rw_spec_static_soundtrack_session_id);
        String sessionId = configuration.getSessionId();
        return (sessionId != null) && sessionId.equals(val);
    }


    /**
     * Returns true if the music player is currently playing the audio stream
     * and is not muted.
     * 
     * @return true if the player is playing and not muted
     */
    synchronized public boolean isPlaying() {
        return isPrepared && mPlayer != null && mPlayer.isPlaying() && (mVolumeLevel > 0);

    }
    
    
    /**
     * Returns true if the music player is currently playing the audio stream,
     * but the sound is muted.
     * 
     * Note: although resource inefficient muting the player to simulated a
     * paused state is currently easier than stopping the stream and later
     * trying to regain it with some kind of buffering in between. In the
     * future more advanced solutions might be implemented. 
     * 
     * @return true if the player is playing and the volume is muted
     */
    public boolean isPlayingMuted() {
        return mPlayer != null && isPrepared && mPlayer.isPlaying() && (mVolumeLevel == 0);
    }


    /**
     * Returns true if the RWService is displaying more detailed messages,
     * e.g. in its notifications. This is mostly for debugging purposes or
     * for advanced users.
     * 
     * @return true if detailed messages are being displayed
     */
    public boolean getShowDetailedMessages() {
        return mShowDetailedMessages;
    }

    
    /**
     * Specifies if the RWService should display more detailed messages,
     * e.g. in its notifications. This is mostly for debugging purposes or
     * for advanced users.
     * 
     * @param state set to true for detailed messages
     */
    public void setShowDetailedMessages(boolean state) {
        mShowDetailedMessages = state;
    }
    

    /**
     * Specifies if the RWService should only use WiFi connection, and not
     * mobile data connections for communicating with the server.
     * 
     * @param state set to true to only allow WiFi connection
     */
    public void setOnlyConnectOverWifi(boolean state) {
        mOnlyConnectOverWiFi = state;
        
        // check current connection and if not WiFi go to off-line (if on-line)
        if (mSessionState == SessionState.ON_LINE) {
            if (!isConnected()) {
                manageSessionState(SessionState.OFF_LINE);
            }
        }
    }
    

    /**
     * Checks if data connectivity is available, honoring the flag 
     * mOnlyConnectOverWifi to accept only WiFi and not mobile data
     * connections.
     * 
     * @return true if data connectivity is available
     */
    public boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if ((ni != null) && ni.isConnected()) {
            if (mOnlyConnectOverWiFi && ni.getType() != ConnectivityManager.TYPE_WIFI) {
                return false;
            }
            return true;
        }
        return false;
    }

    
    /**
     * Fixes the location of the device at the specified coordinates. Updates
     * from the GPS or other internal location providers will no longer have
     * effect, until releaseMockLocation is called. 
     */
    public void setMockLocation(String latitude, String longitude) {
        releaseMockLocation();
        if (!isEmptyOrNAInput(latitude) && !isEmptyOrNAInput(longitude)) {
            try {
                Double lat = Double.valueOf(latitude);
                Double lon = Double.valueOf(longitude);
                RWLocationTracker.instance().fixLocationAt(lat, lon);
            } catch (Exception e) {
                String msg = "Cannot set mock location to lat=" + latitude + ", lon=" + longitude + " : " + e.getMessage();
                Log.w(TAG, msg);
            }
        }
    }
    
    
    /**
     * Checks if the specified input string is empty or contains "N/A".
     * 
     * @param input to check
     * @return true if it is empty or "N/A" (case insensitive)
     */
    public boolean isEmptyOrNAInput(String input) {
        return (input == null) || (input.length() == 0) || ("N/A".equalsIgnoreCase(input));
    }
    
    
    /**
     * When a mock location has been set for the device, release it and return
     * to the normal location updates (e.g. GPS, Network).
     */
    public void releaseMockLocation() {
        if (RWLocationTracker.instance().isUsingFixedLocation()) {
            RWLocationTracker.instance().releaseFixedLocation();
        }
    }

    
    /**
     * Attempts to remove the persisted queue of pending server calls.
     * 
     * @return true when successful
     */
    public boolean deleteQueue() {
        return RWActionQueue.instance().deleteQueue();
    }
    
    
    /**
     * Returns the number of items currently in the queue awaiting
     * processing.
     * 
     * @return queue size
     */
    public int getQueueSize() {
        return RWActionQueue.instance().count();
    }

    
    private void startQueueTimer() {
        if (mQueueTimer != null) {
            stopQueueTimer();
        }

        debugLog("Starting queue processing");
        mQueueTimer = new Timer();
        TimerTask task = new TimerTask() {
            public void run() {
                queueCheck();
            }
        };
        mQueueTimer.scheduleAtFixedRate(task, 0, configuration.getQueueCheckIntervalSec() * 1000);
    }

    
    private void stopQueueTimer() {
        if (mQueueTimer != null) {
            debugLog("Stopping queue processing");
            mQueueTimer.cancel();
            mQueueTimer.purge();
            mQueueTimer = null;
        }
    }
    
    
    /**
     * Sends a call to the Roundware server with information about the
     * current location (latitude, longitude) of the device.
     * 
     * @param now True to sent immediately, false for queued processing 
     * @return server response, empty string when queued or on ui thread
     */
    public String rwSendMoveListener(boolean now) {
        if (configuration.getSessionId() != null && isPrepared) {
            return perform(mActionFactory.createModifyStreamAction(), now, null);
        }
        return null;
    }
    

    /**
     * Sends a heartbeat call to the Roundware server, to let it know we
     * are still active. Can be used when no other server calls have been
     * made for a while to prevent the server from closing our session and
     * starting cleaning it up.
     * 
     * @return server response
     */
    public String rwSendHeartbeat() {
        if (configuration.getSessionId() != null) {
            String response = perform(mActionFactory.createHeartbeatAction(), true, null);
            broadcast(RW.HEARTBEAT_SENT);
            return response;
        }
        return null;
    }

    
    /**
     * Sends a call to the Roundware server to notify it of a specific
     * event (user started listening, user started a recording, etc.).
     * 
     * @param eventTypeResId of type of log event 
     * @param tags to include in log event, may be null
     * @param data to include in log event, may be null
     * @param now True to sent immediately, false for queued processing 
     * @return server response, empty string when queued or on ui thread
     */
    public String rwSendLogEvent(int eventTypeResId, RWList tags, String data, boolean now) {
        return perform(mActionFactory.createLogEventAction(eventTypeResId, tags, data), now, null);
    }
    

    /**
     * Sends a call to the Roundware server for voting on a specific asset.
     * For valid vote type and vote value see the Roundware protocol 
     * description. Typical vote types might be 'like' and 'flag. The vote
     * value is optional and can be specified as null depending on the vote
     * type.
     * 
     * @param assetId of asset to vote on
     * @param voteType to apply
     * @param voteValue to apply, can be null
     * @param now True to sent immediately, false for queued processing 
     * @return server response, empty string when queued or on ui thread
     */
    public String rwSendVoteAsset(int assetId, String voteType, String voteValue, boolean now) {
        return perform(mActionFactory.createVoteAssetAction(assetId, voteType, voteValue), now, null);
    }


    /**
     * Sends a call to the Roundware server to request an audio stream
     * based on the specified selections of tags options.
     *
     * @param tags of tags options for the audio
     * @param now True to sent immediately, false for queued processing
     * @return server response, empty string when queued or on ui thread
     */
    public String rwRequestStream(RWList tags, boolean now) {
        return perform(mActionFactory.createRequestStreamAction(tags), now, null);
    }


    /**
     * Sends a call to the Roundware server to request modifying of the
     * already streaming audio stream based on the specified selections
     * of tags options.
     * 
     * @param tags of tags options for the audio
     * @param now True to sent immediately, false for queued processing 
     * @return server response, empty string when queued or on ui thread
     */
    public String rwModifyStream(RWList tags, boolean now) {
        return perform(mActionFactory.createModifyStreamAction(tags), now, null);
    }
    
    
    /**
     * Sends a call to the Roundware server to request skipping ahead to
     * the next asset and start playing it in the audio stream.
     * 
     * @return server response, see the Roundware protocol documentation
     */
    public String rwSkipAhead() {
        //TODO notify metadata listener that current metadata is null
        return perform(mActionFactory.createSkipAheadAction(), true, null);
    }
    
    
    /**
     * Sends a call to the Roundware server to request inserting the
     * specified asset into the audio stream.
     * 
     * @param assetId of recording to be inserted into the stream
     * @return server response, see the Roundware protocol documentation,
     * empty string when queued or on ui thread
     */
    public String rwPlayAssetInStream(int assetId) {
        return perform(mActionFactory.createPlayAssetInStreamAction(assetId), true, null);
    }

    
    /**
     * Sends calls to the Roundware server needed to announce and upload
     * the specified file, marked by the given selections of tags options.
     * 
     * An RW_SHARING_MESSAGE intent will be broadcasted after the operation
     * is completed and both the now and sharingBroadcast arguments are set
     * to true.
     * 
     * @param tags of tags options for the audio
     * @param filename of file to be uploaded to the server
     * @param submitted for stream (Y) or not (N), null to ignore
     * @param now True to sent immediately, false for queued processing
     * @param sharingBroadcast True to broadcast an RW_SHARING_MESSAGE
     * @return server response, empty string when queued or on ui thread
     * @throws Exception when temporary file could not be created
     */
    public String rwSubmit(RWList tags, String filename, String submitted, boolean now, boolean sharingBroadcast) throws Exception {
        // create an action to create an asset envelope and perform it directly
        int envelopeId = -1;
        if(Looper.myLooper() == Looper.getMainLooper()){
            throw new Exception("do not call on main thread!");
        }

        // create a temporary copy of the recording file
        File queueFile = RWActionQueue.instance().createTemporaryQueueFile(filename);

        // try to open an envelope on the server
        RWAction createEnvelopeAction = mActionFactory.createCreateEnvelopeAction(tags);
        String jsonResponse = perform(createEnvelopeAction, true, null);


        if (jsonResponse != null) {
            String envelopeKey = getString(R.string.rw_key_envelope_id);
            JSONObject jsonObj = new JSONObject(jsonResponse);
            envelopeId = jsonObj.optInt(envelopeKey, -1);
        }

        // create an upload asset action
        RWAction addAssetAction = mActionFactory.createAddAssetToEnvelopeAction(tags, envelopeId, queueFile.getAbsolutePath(), submitted);

        if (envelopeId == -1) {
            // envelope could not be created, queue action for later processing
            return perform(addAssetAction, false, null);
        } else {
            // when submitting directly, send out a sharing broadcast to apps
            if ((now) && (sharingBroadcast)) {
                String envId = String.valueOf(envelopeId);
                String msg = configuration.getSharingMessage();
                String url;

                // url might be encoded in the sharing message
                if ((msg != null) && (msg.contains("|"))) {
                    String[] parts = msg.split("\\|");
                    // use first part as message and last as url
                    msg = parts[0];
                    url = parts[parts.length-1];
                } else {
                    url = configuration.getSharingUrl();
                }

                // replace placeholder with evelope id
                url = url.replace("[id]", envId);

                // get location details
                Double lat = addAssetAction.getLatitude();
                Double lon = addAssetAction.getLongitude();
                Double acc = addAssetAction.getAccuracy();

                // send the broadcast message
                broadcastSharingMessage(msg, url, envId, lat, lon, acc);
            }
            // start the actual file upload, or place in queue
            return perform(addAssetAction, now, null);
        }
    }


    /**
     * Performs a server call for the specified RWAction instance. It can
     * either be handled directly (i.e. the request is created, sent to the
     * server, and the response is waited for), or put in the queue first.
     * This queue is processed in the background based on regular intervals,
     * at which the top item of the queue is 'performed'. Actions that are
     * performed directly and fail will not automatically be placed in the
     * queue. Actions that are placed in the queue and fail will stay in the
     * queue when it concerns file uploads.
     * 
     * @param action to perform as Roundware server call
     * @param now True is sent immediately on non-ui thread, false for queued processing
     * @param listener to update on result
     * @return server response, empty string when queued or on ui thread
     */
    protected String perform(final RWAction action, boolean now, final ServicePerformListener listener) {
        if (now) {
            if(Looper.myLooper() == Looper.getMainLooper()) {
                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        String result = perform(action);
                        if(listener != null){
                            listener.onPerformComplete(result);
                        }
                    }
                }).start();
                return "";
            }else{
                String result = perform(action);
                if(listener != null){
                    listener.onPerformComplete(result);
                }
                return result;
            }
        } else {
            RWActionQueue.instance().add(action.getProperties());
            setNotificationText(null);
            
            // broadcast operation QUEUED intent
            String msg = "Action placed in queued";
            Log.i(TAG, msg, null);
            broadcastActionQueued(action, TAG + ": " + msg, null);

            return "";
        }
    }

    
    /**
     * Handles the setting of notification texts and broadcasting intents
     * surround the calling of the action.perform() method (that does the
     * actual calling of the server). Performs on this thread!
     * @see org.roundware.service.RWService#perform(RWAction, boolean,
     * org.roundware.service.RWService.ServicePerformListener)
     * 
     * @param action to be executed
     * @return server response
     */
    protected String perform(RWAction action) {
        try {
            // update last request time
            mLastRequestMsec = System.currentTimeMillis();

            try {
                setNotificationText(action.getCaption());
            } catch (Exception e) {
                Log.e(TAG, "Could not update notification text!", e);
            }

            // no point in trying when not connected
            if (!isConnected()) {
                throw new UnknownHostException("No connectivity");
            }
            
            // always perform actions for the current session ID
            action.setSessionId(configuration.getSessionId());

            // create an envelope ID for a file upload if the action has none yet (created in off-line mode)
            if ((action.getFilename() != null) && ("-1".equals(action.getEnvelopeId()))) {
                // create an action to create an asset envelope and perform it directly
                RWAction createEnvelopeAction = mActionFactory.createCreateEnvelopeAction(action.getSelectedTagsOptions());
                String jsonResponse = createEnvelopeAction.perform(configuration.getHttpTimeOutSec());
                String envelopeKey = getString(R.string.rw_key_envelope_id);
                JSONObject jsonObj = new JSONObject(jsonResponse);
                int envelopeId = jsonObj.optInt(envelopeKey, -1);
                if (envelopeId == -1) {
                    throw new UnknownHostException("Just in time creation of envelope ID for file upload failed");
                } else {
                    action.setEnvelopeId(String.valueOf(envelopeId));
                }
            }

            // actually perform the action
            String result = action.perform(configuration.getHttpTimeOutSec());
            
            // when action is an upload a log event needs to be send now
            if (action.getFilename() != null) {
                rwSendLogEvent(R.string.rw_et_stop_upload, null, "true", true);
            }

            setNotificationText(mNotificationDefaultText);

            // broadcast operation SUCCESS intent
            broadcastActionSuccess(action, result);
            
            return broadcastServerMessages(result);
        } catch (UnknownHostException e) {
            String msg = "Unknown host error: " + e.getMessage();
            Log.e(TAG, msg, e);
            // broadcast operation FAILED intent
            broadcastActionFailure(action, TAG + ": " + msg, e);
            return null;
        } catch (HttpException e) {
            // expect http status code in exception message
            String msg = "HTTP error: " + e.getMessage();
            Log.e(TAG, msg, e);
            // broadcast operation FAILED intent
            // on server time out pass it as an UnknownHostException
            if (isHttpTimeOut(Integer.valueOf(e.getMessage()))) {
                broadcastActionFailure(action, TAG + ": " + msg, new UnknownHostException(msg));
            } else {
                broadcastActionFailure(action, TAG + ": " + msg, e);
            }
            return null;
        } catch (Exception e) {
            String msg = "Error: " + e.getMessage();
            Log.e(TAG, msg, e);
            // when action is an upload a log event needs to be send now
            if (action.getFilename() != null) {
                rwSendLogEvent(R.string.rw_et_stop_upload, null, "false", true);
            }

            // broadcast operation FAILED intent
            broadcastActionFailure(action, TAG + ": " + msg, e);
            return null;
        }
    }
    
    
    private boolean isHttpTimeOut(int httpStatusResponse) {
        if ((HttpStatus.SC_GATEWAY_TIMEOUT == httpStatusResponse) || 
            (HttpStatus.SC_REQUEST_TIMEOUT == httpStatusResponse) || 
            (HttpStatus.SC_SERVICE_UNAVAILABLE == httpStatusResponse)) {
            return true;
        }
        return false;
    }
    
    
    private String retrieveServerMessage(ServerMessageType messageType, String response) {
        // get keyword for message type
        String key = null;
        switch (messageType) {
            case ERROR:
                key = getString(R.string.rw_key_server_error_message);
                break;
            case TRACEBACK:
                key = getString(R.string.rw_key_server_error_traceback);
                break;
            case USER:
                key = getString(R.string.rw_key_server_result);
                break;
            case SHARING:
                key = getString(R.string.rw_key_server_sharing_message);
                break;
        }
        
        if (key != null) {
            try {
                Object json = new JSONTokener(response).nextValue();
                if (json instanceof JSONArray) {
                    JSONArray entries = (JSONArray) json;
                    for (int i = 0; i < entries.length(); i++) {
                        JSONObject jsonObj = entries.getJSONObject(i);
                        debugLog( jsonObj.toString());
                        if (jsonObj.has(key)) {
                            return jsonObj.getString(key);
                        }
                    }
                } else if (json instanceof JSONObject) {
                    JSONObject jsonObj = (JSONObject) json;
                    debugLog( jsonObj.toString());
                    if (jsonObj.has(key)) {
                        return jsonObj.getString(key);
                    }
                }
            } catch (JSONException e) {
                Log.w(TAG, "Could not get server message from response, probably a JSON array instead of a JSON object.");
            }
        }

        return null;
    }
    
    
    private String broadcastServerMessages(String response) {
        // check if response can be json
        if ((response == null) || (response.length() == 0)) {
            return response;
        }
        if (!(response.startsWith("{") || response.startsWith("["))) {
            return response;
        }
        if (!(response.endsWith("}") || response.endsWith("]"))) {
            return response;
        }
        
        String message;
        Intent intent = new Intent();
        
        // process none critical messages first

        message = retrieveServerMessage(ServerMessageType.USER, response);
        if (message != null) {
            intent.setAction(RW.USER_MESSAGE);
            intent.putExtra(RW.EXTRA_SERVER_MESSAGE, message);
            if (D) {
                Log.i(TAG, "Going to send broadcast event, user message = " + message, null);
            }
            sendBroadcast(intent);
        }

        // process critical messages that stopPlayer further handling of the response

        message = retrieveServerMessage(ServerMessageType.ERROR, response);
        if (message != null) {
            // see if there is additional traceback info
            String traceback = retrieveServerMessage(ServerMessageType.TRACEBACK, response); 
            if (traceback != null) {
                message = message + "\n\nTraceback: " + traceback;
            }
            intent.setAction(RW.ERROR_MESSAGE);
            intent.putExtra(RW.EXTRA_SERVER_MESSAGE, message);
            if (D) {
                Log.i(TAG, "Going to send broadcast event, error message = " + message, null);
            }
            sendBroadcast(intent);
            
            // return null to avoid further processing of server response
            return null;
        }

        return response;
    }

    
    /**
     * Broadcast an intent for a sharing message. ALl relevant info is based
     * as extras in the intent.
     * 
     * @param message to be included in the intent
     * @param url to be included in the intent
     * @param envelopeId to be included in the intent
     * @param latitude (decimal degrees) to be included in the intent
     * @param longitude (decimal degrees) to be included in the intent
     * @param accuracy (in meters) of location to be included in the intent
     */
    public void broadcastSharingMessage(String message, String url, String envelopeId, Double latitude, Double longitude, Double accuracy) {
        if (message != null) {
            Intent intent = new Intent();
            intent.setAction(RW.SHARING_MESSAGE);
            intent.putExtra(RW.EXTRA_SERVER_MESSAGE, message + " - " + url);
            intent.putExtra(RW.EXTRA_SHARING_MESSAGE, message);
            intent.putExtra(RW.EXTRA_SHARING_URL, url);
            intent.putExtra(RW.EXTRA_ENVELOPE_ID, envelopeId);
            
            if ((!Double.isNaN(latitude)) && (!Double.isNaN(longitude))) {
                intent.putExtra(RW.EXTRA_LOCATION_LAT, latitude);
                intent.putExtra(RW.EXTRA_LOCATION_LON, longitude);
                if (!Double.isNaN(accuracy)) {
                    intent.putExtra(RW.EXTRA_LOCATION_ACCURACY_M, accuracy);
                }
            }
            debugLog("Going to send broadcast event, sharing message = " + message + " url = " + url);
            sendBroadcast(intent);
        }
    }
    
    
    /**
     * Manage changing of the current session state to the indicated new
     * state. Intents will be broadcasted to notify receivers of the state
     * changes.
     * 
     * @param newState session state to switch to
     */
    public void manageSessionState(SessionState newState) {
        if (mSessionState.equals(newState)) {
            return;
        }
        mSessionState = newState;
        
        debugLog("Changing session state to: " + mSessionState);
        
        switch (mSessionState) {
            case UNINITIALIZED:
                stopQueueTimer();
                stopLocationUpdates();
                playbackStop();
                break;
            case INITIALIZING:
                retrieveConfiguration(this, configuration.getDeviceId(), configuration.getProjectId());
                break;
            case ON_LINE:
                // refresh configuration after threshold time so session ID can be refreshed
                // TODO Better to have a task that only updates the session ID?
                long millis = System.currentTimeMillis();
                if ((millis - mLastStateChangeMsec) > (configuration.getHeartbeatTimerSec() * 5)) {
                    // project ID assumed to be already in configuration and not changing!
                    retrieveConfiguration(this, configuration.getDeviceId(), configuration.getProjectId());
                }
                // refresh tags
                if (tags.getDataSource() != RWTags.FROM_SERVER) {
                    retrieveTags(this, configuration.getProjectId());
                }
                startQueueTimer();
                startLocationUpdates();
                broadcast(RW.SESSION_ON_LINE);
                break;
            case OFF_LINE:
                // keep queue timer running to periodically retry the connection
                startQueueTimer();
                playbackStop();
                broadcast(RW.SESSION_OFF_LINE);
                break;
        }
        
        mLastStateChangeMsec = System.currentTimeMillis();
    }


    /**
     * Broadcast an intent with the specified action.
     * 
     * @param action intent action (not a RWAction)
     */
    private void broadcast(String action) {
        Intent intent = new Intent();
        intent.setAction(action);
        debugLog("Going to send broadcast event, action=" + action);
        sendBroadcast(intent);
    }
    
    
    @SuppressLint("DefaultLocale")
    private void broadcastActionSuccess(RWAction action, String result) {
        Intent intent = new Intent();
        String actionName = RW.BROADCAST_PREFIX + action.getOperation().toLowerCase() + RW.BROADCAST_SUCCESS_POSTFIX;
        intent.setAction(actionName);
        intent.putExtra(RW.EXTRA_ACTION_PROPERTIES, action.getProperties());
        intent.putExtra(RW.EXTRA_SUCCESS_RESULT, result);
        debugLog("Going to send broadcast event, action = " + actionName);
        sendBroadcast(intent);
    }
    

    @SuppressLint("DefaultLocale")
    private void broadcastActionFailure(RWAction action, String reason, Throwable e) {
        Intent intent = new Intent();
        String actionName = RW.BROADCAST_PREFIX + action.getOperation().toLowerCase() + RW.BROADCAST_FAILURE_POSTFIX;
        intent.setAction(actionName);
        intent.putExtra(RW.EXTRA_ACTION_PROPERTIES, action.getProperties());
        intent.putExtra(RW.EXTRA_FAILURE_REASON, reason);
        if (e != null) {
            intent.putExtra(RW.EXTRA_FAILURE_EXCEPTION_CLASS, e.getClass().getName());
            intent.putExtra(RW.EXTRA_FAILURE_EXCEPTION_MESSAGE, e.getMessage());
        }
        debugLog("Going to send broadcast event, action = " + actionName);
        sendBroadcast(intent);
    }
    

    @SuppressLint("DefaultLocale")
    private void broadcastActionQueued(RWAction action, String reason, Throwable e) {
        Intent intent = new Intent();
        String actionName = RW.BROADCAST_PREFIX + action.getOperation().toLowerCase() + RW.BROADCAST_QUEUED_POSTFIX;
        intent.setAction(actionName);
        intent.putExtra(RW.EXTRA_ACTION_PROPERTIES, action.getProperties());
        intent.putExtra(RW.EXTRA_FAILURE_REASON, reason);
        if (e != null) {
            intent.putExtra(RW.EXTRA_FAILURE_EXCEPTION_CLASS, e.getClass().getName());
            intent.putExtra(RW.EXTRA_FAILURE_EXCEPTION_MESSAGE, e.getMessage());
        }
        debugLog("Going to send broadcast event, action = " + actionName);
        sendBroadcast(intent);
    }
    
    
    private void broadcastLocationUpdate(double latitude, double longitude, String provider, float accuracy) {
        if (D) {
            Log.d(TAG, String.format(
                    "Going to send broadcast event, location updated lat=%.6f lon=%.6f "
                            + "provider=%s accuracy=%.6fm", latitude,
                    longitude, provider, accuracy), null);
        }
        Intent intent = new Intent();
        intent.setAction(RW.LOCATION_UPDATED);
        intent.putExtra(RW.EXTRA_LOCATION_LAT, latitude);
        intent.putExtra(RW.EXTRA_LOCATION_LON, longitude);
        intent.putExtra(RW.EXTRA_LOCATION_PROVIDER, provider);
        intent.putExtra(RW.EXTRA_LOCATION_ACCURACY_M, accuracy);
        sendBroadcast(intent);
    }

    
    private void queueCheck() {
        int count = RWActionQueue.instance().count();
        if (count == 0) {
            // nothing to do, send ping if idle time threshold exceeded - also used to check on-line status
            long currentMillis = System.currentTimeMillis();
            if ((currentMillis - mLastRequestMsec) > (configuration.getHeartbeatTimerSec() * 1000)) {
                if ((SessionState.OFF_LINE.equals(mSessionState)) ||
                                (SessionState.ON_LINE.equals(mSessionState) && isPlaying())) {
                    rwSendHeartbeat();
                }
            }

            setNotificationText(null);
            return;
        }

        RWAction action = RWActionQueue.instance().get();
        if (action != null) {
            if (perform(action) != null) {
                RWActionQueue.instance().delete(action);
                setNotificationText(null);
            } else {
                setNotificationText(getString(R.string.roundware_notification_request_failed));
                // remove failing action from queue, unless it is a file upload
                if (action.getFilename() == null) {
                    RWActionQueue.instance().delete(action);
                }
            }
        }
    }
    

    /**
     * Creates a media player for sound playback, with initial volume of 0.
     */
    private void createPlayer() {
        debugLog("+++ createPlayer +++");
        if (mPlayer == null) {
            mPlayer = new MediaPlayer();


            mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    debugLog("MediaPlayer prepared event");
                    synchronized (this) {
                        isPrepared = true;
                        mPrepareTime = System.currentTimeMillis() - mStartTime;
                    }
                    rwSendMoveListener(false);
                    broadcast(RW.READY_TO_PLAY);
                    if (mStartPlayingWhenReady) {
                        playbackFadeIn(mVolumeLevel);
                    }
                }
            });

            mPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer mp, int what, int extra) {
                    if (D) {
                        Log.i(TAG, "MediaPlayer info event: " + what);
                    }
                    switch(what) {
                        case MediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                            if (D) {
                                Log.i(TAG, "MediaPlayer metadata updated, extra = " + extra);
                            }
                            break;
                        case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                            broadcast(RW.STREAM_BUFFERING_END);
                            break;
                        case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                            // prepare time may be unreliable now
                            broadcast(RW.STREAM_BUFFERING_START);
                            break;
                    }
                    return true;
                }
            });

            mPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    debugLog("MediaPlayer error(" + what + ", " + extra + ")");
                    if(!isPrepared){
                        Log.e(TAG, "mPlayer error before prepared!");
                    }else {
                        isPrepared = false;
                    }
                    mPlayer.reset();
                    errorCount++;
                    if(errorCount < ERROR_RETRY_COUNT){
                        preparePlayer();
                    }
                    return true;
                }
            });

            mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    debugLog("MediaPlayer completion event");
                    mp.stop();
                    broadcast(RW.PLAYBACK_FINISHED);
                }
            });
            
            mPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
                @Override
                public void onBufferingUpdate(MediaPlayer mp, int percent) {
                    //debugLog("MediaPlayer buffering event, %=" + percent);
                }
            });
            mPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);

            float volume = (float) 0.0;
            mPlayer.setVolume(volume, volume);
            mVolumeLevel = 0;
            if(isPrepared && mPlayer.isPlaying()) {
                mPlayer.pause();
            }
        }
    }

    
    /**
     * Releases the media player after fading out the sounds.
     */
    private void releasePlayer() {
        if (mPlayer != null) {
            playbackFadeOut();
            stopPlayer();
            mPlayer.release();
            mPlayer = null;
        }
        
        // release wifi radio if a lock on it was aquired for playback
        if (mWifiLock != null) {
            mWifiLock.release();
        }
    }

    
    /**
     * Fade out the volume of the music player, until it reaches 0.
     */
    public void playbackFadeOut() {
        // let server know user is no longer listening
        rwSendLogEvent(R.string.rw_et_stop_listen, null, null, true);

        setVolumeLevel(0, true);
        mStartPlayingWhenReady = false;
        setNotificationText(".Audio muted");
    }

    
    /**
     * Fade in the volume of the music player, until it reaches the level
     * specified.
     * 
     * @param endVolumeLevel (0 - 100)
     */
    public void playbackFadeIn(int endVolumeLevel) {
        mStartPlayingWhenReady = true;
        if (mPlayer != null) {
            if(isPrepared) {
                try {
                    mPlayer.start();
                } catch (Exception ex) {
                    Log.i(TAG, "Fade in to volume level " + endVolumeLevel + " caused " + "MediaPlayer exception, delaying!", ex);
                    setVolumeLevel(endVolumeLevel, true);
                }
            }

            // let server know user started listening
            rwSendLogEvent(R.string.rw_et_start_listen, null, null, true);

            setVolumeLevel(endVolumeLevel, true);
            setNotificationText(".Audio unmuted");
        } else {
            Log.i(TAG, "Fade in to volume level " + endVolumeLevel + " ignored, " + "MediaPlayer not initialized!", null);
            setVolumeLevel(endVolumeLevel, true);
        }
    }

    
    private float calcVolumeScalar(int volumeLevel) {
        float volume = 1.0f;
        if (volumeLevel < mMinVolumeLevel) {
            volume = 0.0f;
        } else {
            if (volumeLevel < mMaxVolumeLevel) {
                for (int i = mMaxVolumeLevel; i > volumeLevel; i--) {
                    volume *= mVolumeStepMultiplier;
                }
            }
        }
        return volume;
    }

    
    /**
     * Returns the current volume level of the music player.
     * 
     * @return volume level (0 - 100)
     */
    public int getVolumeLevel() {
        return mVolumeLevel;
    }

    /**
     * Returns the time required to prepare the mediaplayer
     * @return
     */
    public long getPrepareTime(){
        return mPrepareTime;
    }
    
    /**
     * Sets a new volume level for the music player. The change in volume
     * level can be made abruptly or through fading.
     * 
     * @param newVolumeLevel for the music player
     * @param fade change level by fading or not
     */
    @SuppressLint("DefaultLocale") public void setVolumeLevel(int newVolumeLevel, boolean fade) {
        int oldVolumeLevel = mVolumeLevel;
        mVolumeLevel = newVolumeLevel;
        if (mVolumeLevel < mMinVolumeLevel) {
            mVolumeLevel = mMinVolumeLevel;
        } else if (mVolumeLevel > mMaxVolumeLevel) {
            mVolumeLevel = mMaxVolumeLevel;
        }

        float oldVolume = calcVolumeScalar(oldVolumeLevel);
        float newVolume = calcVolumeScalar(mVolumeLevel);

        // fail-safe when volume should be totally muted
        if (mVolumeLevel == 0) {
            newVolume = 0.0f;
        }

        // gradually modify the volume when playing and set to fade
        if (mPlayer != null) {
            if (D) {
                String msg = String.format(Locale.US, "Changing volume from level %d (%1.5f) to level %d (%1.5f)", oldVolumeLevel, oldVolume, mVolumeLevel, newVolume);
                Log.d(TAG, msg);
            }
            if (fade) {
                if (oldVolume < newVolume) {
                    for (float v = oldVolume; v < newVolume; v += 0.001f) {
                        mPlayer.setVolume(v, v);
                    }
                } else {
                    for (float v = oldVolume; v > newVolume; v -= 0.001f) {
                        mPlayer.setVolume(v, v);
                    }
                }
                // make sure to reach the final new volume
                mPlayer.setVolume(newVolume, newVolume);
            } else {
                mPlayer.setVolume(newVolume, newVolume);
            }
        } else {
                debugLog( String.format(Locale.US,
                        "Volume set to level %d (%1.5f) but MediaPlayer not initialized!",
                        mVolumeLevel, newVolume) );
        }
    }

    private void debugLog(String msg){
        if(D){
            Log.d(TAG, msg);
        }
    }

    private interface ServicePerformListener{
        public void onPerformComplete(String result);
    }
}
