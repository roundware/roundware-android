/*
    ROUNDWARE
	a participatory, location-aware media platform
	Android client library
   	Copyright (C) 2008-2013 Halsey Solutions, LLC
	with contributions by Rob Knapen (shuffledbits.com) and Dan Latham
	http://roundware.org | contact@roundware.org

	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

 	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 	GNU General Public License for more details.

 	You should have received a copy of the GNU General Public License
 	along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.halseyburgund.rwframework.core;

import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.halseyburgund.rwframework.R;

import android.content.Context;
import android.util.Log;


/**
 * Project configuration parameters.
 * 
 * Note: the use of getters and setters in this class is deliberate, the
 * Behavior and access to the parameters of the configuration might be
 * changed in the future.
 * 
 * @author Rob Knapen
 */
public class RWConfiguration {
	
	// debugging
	private final static String TAG = "RWConfiguration";
	private final static boolean D = false;
	
	// data source
	public final static int DEFAULTS = 0;
	public final static int FROM_CACHE = 1;
	public final static int FROM_SERVER = 2;
	
	// json names
	private final static String JSON_KEY_CONFIG_SECTION_DEVICE = "device";
	private final static String JSON_KEY_CONFIG_DEVICE_ID = "device_id";

	private final static String JSON_KEY_CONFIG_SECTION_SESSION = "session";
	private final static String JSON_KEY_CONFIG_SESSION_ID = "session_id";
	
	private final static String JSON_KEY_CONFIG_SECTION_PROJECT = "project";
	private final static String JSON_KEY_CONFIG_PROJECT_ID = "project_id";
	private final static String JSON_KEY_CONFIG_PROJECT_NAME = "project_name";
	private final static String JSON_KEY_CONFIG_HEARTBEAT_TIMER_SEC = "heartbeat_timer";
	private final static String JSON_KEY_CONFIG_MAX_RECORDING_LENGTH_SEC = "max_recording_length";
	private final static String JSON_KEY_CONFIG_SHARING_MESSAGE = "sharing_message";
	private final static String JSON_KEY_CONFIG_SHARING_URL = "sharing_url";
	private final static String JSON_KEY_CONFIG_LEGAL_AGREEMENT = "legal_agreement";
	private final static String JSON_KEY_CONFIG_DYNAMIC_LISTEN_TAGS = "listen_questions_dynamic";
	private final static String JSON_KEY_CONFIG_DYNAMIC_SPEAK_TAGS = "speak_questions_dynamic";
	private final static String JSON_KEY_CONFIG_GEO_LISTEN_ENABLED = "geo_listen_enabled";
	private final static String JSON_KEY_CONFIG_GEO_SPEAK_ENABLED = "geo_speak_enabled";
	private final static String JSON_KEY_CONFIG_LISTEN_ENABLED = "listen_enabled";
	private final static String JSON_KEY_CONFIG_SPEAK_ENABLED = "speak_enabled";
	private final static String JSON_KEY_CONFIG_FILES_URL = "files_url";
	private final static String JSON_KEY_CONFIG_FILES_VERSION = "files_version";
	
	private final static String JSON_KEY_CONFIG_SECTION_SERVER = "server";
	private final static String JSON_KEY_CONFIG_CURRENT_VERSION = "version";

	// not yet returned by server:
	private final static String JSON_KEY_CONFIG_STREAM_METADATA_ENABLED = "stream_metadata_enabled";
	private final static String JSON_KEY_CONFIG_RESET_TAG_DEFAULTS_ON_STARTUP = "reset_tag_defaults_on_startup";
	private final static String JSON_KEY_CONFIG_MIN_LOCATION_UPDATE_TIME_MSEC = "min_location_update_time_msec";
	private final static String JSON_KEY_CONFIG_MIN_LOCATION_UPDATE_DISTANCE_METER = "min_location_update_distance_meter";
	private final static String JSON_KEY_CONFIG_HTTP_TIMEOUT_SEC = "http_timeout_sec";
	
	// json parsing error message
	public final static String JSON_SYNTAX_ERROR_MESSAGE = "Invalid server response received!";
	
	private int mDataSource = DEFAULTS;
	
	private String mDeviceId = UUID.randomUUID().toString();
	private String mProjectId  = null;
	
	private String mSessionId = null;
	private String mProjectName = null;

	// (web) content file management
	private String mContentFilesUrl = null;
	private int mContentFilesVersion = -1;
	private boolean mContentFilesAlwaysDownload = false;
	
	// timing variables
	private int mHeartbeatTimerSec = 15;
	private int mQueueCheckIntervalSec = 60;
	private int mStreamMetadataTimerIntervalMSec = 2000;
	private int mMaxRecordingTimeSec = 30;
	
	// social sharing
	private String mSharingUrl = null;
	private String mSharingMessage = null;
	private String mLegalAgreement = null;
	
	// listen questions can change (e.g. based on location)
	private boolean mDynamicListenQuestions = false;
	// speak questions can change (e.g. based on location)
	private boolean mDynamicSpeakQuestions = false;

	// allow audio playing functionality for the project
	private boolean mListenEnabled = true;
	// allow recording functionality for the project
	private boolean mSpeakEnabled = true;
	// enable sending location updates while stream is playing
	private boolean mGeoListenEnabled = false;
	// enable adding location (one-shot update) to recording
	private boolean mGeoSpeakEnabled = false;
	// use defaults for tag selection on app startup, do not restore them
	private boolean mResetTagDefaultsOnStartup = true;
	// enabled tracking of stream metadata
	private boolean mStreamMetadataEnabled = false;

	/* The frequency of notification or new locations may be controlled using
	 * the minTime and minDistance parameters. If minTime is greater than 0, the 
	 * LocationManager could potentially rest for minTime milliseconds between 
	 * location updates to conserve power. If minDistance is greater than 0, a 
	 * location will only be broadcast if the device moves by minDistance meters. 
	 * To obtain notifications as frequently as possible, set both parameters to 0. 
	 */
	private long mMinLocationUpdateTimeMSec = 60000;
	private double mMinLocationUpdateDistanceMeter = 5.0;
	
	// http call timeout
	private int mHttpTimeOutSec = 45;

	// current Roundware software version on the server
	private String mServerVersion = null;
	
	
	/**
	 * Creates an instance and fills it with default values read from the
	 * resources.
	 */
	public RWConfiguration(Context context) {
		mDataSource = DEFAULTS;
		
		// overwrite defaults from resources
		if (context != null) {
			String val;
			val = context.getString(R.string.rw_spec_heartbeat_interval_in_sec);
			mHeartbeatTimerSec = Integer.valueOf(val);
			
			val = context.getString(R.string.rw_spec_queue_check_interval_in_sec);
			mQueueCheckIntervalSec = Integer.valueOf(val);
			
			val = context.getString(R.string.rw_spec_stream_metadata_timer_interval_in_msec);
			mStreamMetadataTimerIntervalMSec = Integer.valueOf(val);
			
			val = context.getString(R.string.rw_spec_min_location_update_time_msec);
			mMinLocationUpdateTimeMSec = Long.valueOf(val);

			val = context.getString(R.string.rw_spec_min_location_update_distance_meters);
			mMinLocationUpdateDistanceMeter = Double.valueOf(val);
			
			val = context.getString(R.string.rw_spec_files_url);
			mContentFilesUrl = val;
			
			val = context.getString(R.string.rw_spec_files_version);
			if (val != null) {
				mContentFilesVersion = Integer.valueOf(val);
			}
			
			mContentFilesAlwaysDownload = "Y".equalsIgnoreCase(context.getString(R.string.rw_spec_files_always_download));

			mListenEnabled = "Y".equalsIgnoreCase(context.getString(R.string.rw_spec_listen_enabled_yn));
			mGeoListenEnabled = "Y".equalsIgnoreCase(context.getString(R.string.rw_spec_geo_listen_enabled_yn));
			mSpeakEnabled = "Y".equalsIgnoreCase(context.getString(R.string.rw_spec_speak_enabled_yn));
			mGeoSpeakEnabled = "Y".equalsIgnoreCase(context.getString(R.string.rw_spec_geo_speak_enabled_yn));
			mStreamMetadataEnabled = "Y".equalsIgnoreCase(context.getString(R.string.rw_spec_stream_metadata_enabled_yn));
			mResetTagDefaultsOnStartup = "Y".equalsIgnoreCase(context.getString(R.string.rw_spec_reset_tag_defaults_on_startup_yn));
		}
	}


	/**
	 * Overwrites configuration values from the specified key-value map.
	 * If there is no matching key in the map, the value will remain
	 * unchanged.
	 * 
	 * @param jsonResponse to process
	 * @param fromCache true if using cached data
	 */
	public void assignFromJsonServerResponse(String jsonResponse, boolean fromCache) {
        JSONObject specs = null;
        
		try {
			JSONArray entries = new JSONArray(jsonResponse);
	        for (int i = 0; i < entries.length(); i++) {
	        	JSONObject jsonObj = entries.getJSONObject(i);
	        	if (D) { Log.d(TAG, jsonObj.toString()); }
	        	if (jsonObj.has(JSON_KEY_CONFIG_SECTION_DEVICE)) {
	        		// decode section with device specific settings
	        		specs = jsonObj.getJSONObject(JSON_KEY_CONFIG_SECTION_DEVICE);
		        	setDeviceId(specs.optString(JSON_KEY_CONFIG_DEVICE_ID, getDeviceId()));
	        	} else if (jsonObj.has(JSON_KEY_CONFIG_SECTION_SESSION)) {
	        		// decode section with session specific settings
	        		specs = jsonObj.getJSONObject(JSON_KEY_CONFIG_SECTION_SESSION);
		        	setSessionId(specs.optString(JSON_KEY_CONFIG_SESSION_ID, getSessionId()));
	        	} else if (jsonObj.has(JSON_KEY_CONFIG_SECTION_PROJECT)) {
	        		// decode section with project specific settings
	        		specs = jsonObj.getJSONObject(JSON_KEY_CONFIG_SECTION_PROJECT);
		        	setProjectId(specs.optString(JSON_KEY_CONFIG_PROJECT_ID, getProjectId()));
		        	setProjectName(specs.optString(JSON_KEY_CONFIG_PROJECT_NAME, getProjectName()));
		        	setContentFilesUrl(specs.optString(JSON_KEY_CONFIG_FILES_URL, getContentFilesUrl()));
		        	setContentFilesVersion(specs.optInt(JSON_KEY_CONFIG_FILES_VERSION, getContentFilesVersion()));
		        	setHeartbeatTimerSec(specs.optInt(JSON_KEY_CONFIG_HEARTBEAT_TIMER_SEC, getHeartbeatTimerSec()));
		        	setMaxRecordingTimeSec(specs.optInt(JSON_KEY_CONFIG_MAX_RECORDING_LENGTH_SEC, getMaxRecordingTimeSec()));
		        	setSharingMessage(specs.optString(JSON_KEY_CONFIG_SHARING_MESSAGE, getSharingMessage()));
		        	setSharingUrl(specs.optString(JSON_KEY_CONFIG_SHARING_URL, getSharingUrl()));
		        	setLegalAgreement(specs.optString(JSON_KEY_CONFIG_LEGAL_AGREEMENT, getLegalAgreement()));
		        	setDynamicListenQuestions(specs.optBoolean(JSON_KEY_CONFIG_DYNAMIC_LISTEN_TAGS, isDynamicListenQuestions()));
		        	setDynamicSpeakQuestions(specs.optBoolean(JSON_KEY_CONFIG_DYNAMIC_SPEAK_TAGS, isDynamicSpeakQuestions()));
		        	setListenEnabled(specs.optBoolean(JSON_KEY_CONFIG_LISTEN_ENABLED, isListenEnabled()));
		        	setGeoListenEnabled(specs.optBoolean(JSON_KEY_CONFIG_GEO_LISTEN_ENABLED, isGeoListenEnabled()));
		        	setSpeakEnabled(specs.optBoolean(JSON_KEY_CONFIG_SPEAK_ENABLED, isSpeakEnabled()));
		        	setGeoSpeakEnabled(specs.optBoolean(JSON_KEY_CONFIG_GEO_SPEAK_ENABLED, isGeoSpeakEnabled()));
		        	setResetTagsDefaultsOnStartup(specs.optBoolean(JSON_KEY_CONFIG_RESET_TAG_DEFAULTS_ON_STARTUP, isResetTagsDefaultOnStartup()));
		        	setStreamMetadataEnabled(specs.optBoolean(JSON_KEY_CONFIG_STREAM_METADATA_ENABLED, isStreamMetadataEnabled()));
		        	setMinLocationUpdateTimeMSec(specs.optLong(JSON_KEY_CONFIG_MIN_LOCATION_UPDATE_TIME_MSEC, getMinLocationUpdateTimeMSec()));
		        	setMinLocationUpdateDistanceMeter(specs.optDouble(JSON_KEY_CONFIG_MIN_LOCATION_UPDATE_DISTANCE_METER, getMinLocationUpdateDistanceMeter()));
		        	setHttpTimeOutSec(specs.optInt(JSON_KEY_CONFIG_HTTP_TIMEOUT_SEC, getHttpTimeOutSec()));
	        	} else if (jsonObj.has(JSON_KEY_CONFIG_SECTION_SERVER)) {
	        		specs = jsonObj.getJSONObject(JSON_KEY_CONFIG_SECTION_SERVER);
	        		setServerVersion(specs.optString(JSON_KEY_CONFIG_CURRENT_VERSION, getServerVersion()));
	        	}
	        }
	        
	        mDataSource = fromCache ? FROM_CACHE : FROM_SERVER;
	        if (fromCache) {
	        	setSessionId(null);
	        }
	        
		} catch (JSONException e) {
			Log.e(TAG, JSON_SYNTAX_ERROR_MESSAGE, e);
		}
	}
	

	/**
	 * Returns true if any of the configuration parameters related to the use
	 * of device location is true.
	 * 
	 * @return true if configuration requires use of device location services
	 */
	public boolean isUsingLocation() {
		return isUsingLocationBasedListen() || isUsingLocationBasedSpeak();
	}

	
	/***
	 * Returns true if any of the configuration parameters related to Listen
	 * functionality require use of device positioning.
	 * 
	 * @return true if positioning is needed for Listen functionality
	 */
	public boolean isUsingLocationBasedListen() {
		return isGeoListenEnabled() || isDynamicListenQuestions();
	}
	
	
	/***
	 * Returns true if any of the configuration parameters related to Speak
	 * functionality require use of device positioning.
	 * 
	 * @return true if positioning is needed for Speak functionality
	 */
	public boolean isUsingLocationBasedSpeak() {
		return isGeoSpeakEnabled() || isDynamicSpeakQuestions();
	}

	
	public String getDeviceId() {
		return mDeviceId;
	}

	
	public void setDeviceId(String deviceId) {
		mDeviceId = deviceId;
	}

	
	public String getProjectId() {
		return mProjectId;
	}

	
	public void setProjectId(String projectId) {
		mProjectId = projectId;
	}
	

	public String getSessionId() {
		return mSessionId;
	}

	
	public void setSessionId(String sessionId) {
		mSessionId = sessionId;
	}

	
	public String getProjectName() {
		return mProjectName;
	}

	
	public void setProjectName(String projectName) {
		mProjectName = projectName;
	}

	
	public int getHeartbeatTimerSec() {
		return mHeartbeatTimerSec;
	}

	
	public void setHeartbeatTimerSec(int heartbeatTimerSec) {
		mHeartbeatTimerSec = heartbeatTimerSec;
	}

	
	public int getQueueCheckIntervalSec() {
		return mQueueCheckIntervalSec;
	}

	
	public void setQueueCheckIntervalSec(int queueCheckIntervalSec) {
		mQueueCheckIntervalSec = queueCheckIntervalSec;
	}
	
	
	public int getStreamMetadataTimerIntervalMSec() {
		return mStreamMetadataTimerIntervalMSec;
	}

	
	public void setStreamMetadataTimerIntervalMSec(int metadataTimerIntervalMSec) {
		mStreamMetadataTimerIntervalMSec = metadataTimerIntervalMSec;
	}
	
	
	public int getMaxRecordingTimeSec() {
		return mMaxRecordingTimeSec;
	}

	
	public void setMaxRecordingTimeSec(int maxRecordingTimeSec) {
		mMaxRecordingTimeSec = maxRecordingTimeSec;
	}

	
	public String getSharingUrl() {
		return mSharingUrl;
	}

	
	public void setSharingUrl(String sharingUrl) {
		mSharingUrl = sharingUrl;
	}

	
	public String getSharingMessage() {
		return mSharingMessage;
	}

	
	public void setSharingMessage(String sharingMessage) {
		mSharingMessage = sharingMessage;
	}
	
	
	public String getLegalAgreement() {
		return mLegalAgreement;
	}
	
	
	public void setLegalAgreement(String legalAgreement) {
		mLegalAgreement = legalAgreement;
	}


	public boolean isDynamicListenQuestions() {
		return mDynamicListenQuestions;
	}


	public void setDynamicListenQuestions(boolean dynamicListenQuestions) {
		mDynamicListenQuestions = dynamicListenQuestions;
	}


	public boolean isDynamicSpeakQuestions() {
		return mDynamicSpeakQuestions;
	}


	public void setDynamicSpeakQuestions(boolean dynamicSpeakQuestions) {
		mDynamicSpeakQuestions = dynamicSpeakQuestions;
	}
	
	
	public boolean isGeoListenEnabled() {
		return mGeoListenEnabled;
	}
	
	
	public void setGeoListenEnabled(boolean geoListenEnabled) {
		mGeoListenEnabled = geoListenEnabled;
	}

	
	public boolean isSpeakEnabled() {
		return mSpeakEnabled;
	}
	
	
	public void setSpeakEnabled(boolean speakEnabled) {
		mSpeakEnabled = speakEnabled;
	}
	
	
	public boolean isGeoSpeakEnabled() {
		return mGeoSpeakEnabled;
	}

	
	public void setGeoSpeakEnabled(boolean geoSpeakEnabled) {
		mGeoSpeakEnabled = geoSpeakEnabled;
	}
	
	
	public boolean isResetTagsDefaultOnStartup() {
		return mResetTagDefaultsOnStartup;
	}
	
	
	public void setResetTagsDefaultsOnStartup(boolean resetTagsDefaultsOnStartup) {
		mResetTagDefaultsOnStartup = resetTagsDefaultsOnStartup;
	}


	public long getMinLocationUpdateTimeMSec() {
		return mMinLocationUpdateTimeMSec;
	}


	public void setMinLocationUpdateTimeMSec(long minLocationUpdateTimeMSec) {
		mMinLocationUpdateTimeMSec = minLocationUpdateTimeMSec;
	}


	public double getMinLocationUpdateDistanceMeter() {
		return mMinLocationUpdateDistanceMeter;
	}


	public void setMinLocationUpdateDistanceMeter(double minLocationUpdateDistanceMeter) {
		mMinLocationUpdateDistanceMeter = minLocationUpdateDistanceMeter;
	}


	public int getHttpTimeOutSec() {
		return mHttpTimeOutSec;
	}


	public void setHttpTimeOutSec(int httpTimeOutSec) {
		mHttpTimeOutSec = httpTimeOutSec;
	}


	public int getDataSource() {
		return mDataSource;
	}


	public String getServerVersion() {
		return mServerVersion;
	}


	public void setServerVersion(String serverVersion) {
		mServerVersion = serverVersion;
	}


	public boolean isListenEnabled() {
		return mListenEnabled;
	}


	public void setListenEnabled(boolean listenEnabled) {
		mListenEnabled = listenEnabled;
	}


	public boolean isStreamMetadataEnabled() {
		return mStreamMetadataEnabled;
	}


	public void setStreamMetadataEnabled(boolean streamMetadataEnabled) {
		mStreamMetadataEnabled = streamMetadataEnabled;
	}


	public String getContentFilesUrl() {
		return mContentFilesUrl;
	}


	public void setContentFilesUrl(String filesUrl) {
		mContentFilesUrl = filesUrl;
	}


	public int getContentFilesVersion() {
		return mContentFilesVersion;
	}


	public void setContentFilesVersion(int filesVersion) {
		mContentFilesVersion = filesVersion;
	}


	public boolean isContentFilesAlwaysDownload() {
		return mContentFilesAlwaysDownload;
	}


	public void setContentFilesAlwaysDownload(boolean contentFilesAlwaysDownload) {
		mContentFilesAlwaysDownload = contentFilesAlwaysDownload;
	}
	
}
