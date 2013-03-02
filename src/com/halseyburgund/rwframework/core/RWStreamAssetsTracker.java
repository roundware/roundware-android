/*
    ROUNDWARE
	a participatory, location-aware media platform
	Android client library
   	Copyright (C) 2008-2012 Halsey Solutions, LLC
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONObject;

import android.content.Intent;
import android.util.Log;

import com.halseyburgund.rwframework.R;

/**
 * Class that tracks assets streamed by the Roundware server.
 *
 * Due to limitations of the current Android MediaPlayer (Android 2.1) stream
 * metadata tags can not be retrieved directly from the stream itself. Instead
 * a server polling approach is used to retrieve information about the assets
 * streamed by the server and build up a list, which is then kept in sync with
 * the audio buffering on the device as good as possible. A broadcast intent
 * is send when the tracker expects a new asset to started playing on the
 * device (compensating for the audio buffer as best as possible).
 * 
 * @author Rob Knapen
 */
public class RWStreamAssetsTracker {

	// debugging
	private final static String TAG = "RWStreamAssetsTracker";
	private final static boolean D = false;

	// fields for caching to speed things up a little
	private SimpleDateFormat mServerTimeDateFormat;
	private String mAssetIdJsonKey;
	private String mCurrentServerTimeJsonKey;
	private String mStartTimeJsonKey;
	private String mDurationInMsJsonKey;
	private String mDurationInStreamMsJsonKey;
	
	// the RWService to use for server operations
	private RWService mRwService;
	
	// timer for the main loop
	private Timer mMetadataReaderTimer;
	
	// time of next expected server asset change
	private long mNextUpdateTimeMsec = -1;
	
	// estimated buffer length (currently about 6 sec on Android) 
	private int mAverageBufferLengthMsec = 6000;
	
	// tracking of last played and current asset on device
	private int mPreviouslyPlayedAssetId;
	private int mCurrentlyPlayingAssetId;

	/**
	 * Data class to track asset info retrieved from the server.
	 */
	public class StreamedAssetInfo {
		public int assetId;
		public long assetDurationMs;
		public long assetDurationInStreamMs;
		public long lastClientUpdateTimeMs;
		public long startedPlayingOnServerTimeMs;
		public long hasBeenPlayingOnServerForTimeMs;
		public long estimatedStartedPlayingTimeMs;
	}
	
	// list of assets streamed by server and playing or in buffer on the device
	private ArrayList<StreamedAssetInfo> mAssets;
	
	
	/**
	 * Creates an instance for tracking assets streamed by the server that
	 * is accessed by the specified RWService. Use the start(), stop() and
	 * reset() methods to control the tracking process.
	 * 
	 * @param service to use to access Roundware server streaming the assets
	 */
	public RWStreamAssetsTracker(RWService service) {
		mRwService = service;
		mAssets = new ArrayList<RWStreamAssetsTracker.StreamedAssetInfo>();
		
		// cache some resources
		mCurrentServerTimeJsonKey = mRwService.getString(R.string.rw_key_server_current_server_time);
		mStartTimeJsonKey = mRwService.getString(R.string.rw_key_server_start_time);
		mAssetIdJsonKey = mRwService.getString(R.string.rw_key_server_asset_id);
		mDurationInMsJsonKey = mRwService.getString(R.string.rw_key_server_duration_in_ms);
		mDurationInStreamMsJsonKey = mRwService.getString(R.string.rw_key_server_duration_in_stream);
		
		// create date formatters
		mServerTimeDateFormat = new SimpleDateFormat(mRwService.getString(R.string.rw_fmt_server_time));
		
		// init
		mCurrentlyPlayingAssetId = -1;
		mPreviouslyPlayedAssetId = -1;
	}
	
	
	/**
	 * Starts tracking the assets streamed by the server. This basically
	 * handles everything and does an intent broadcast when it estimates
	 * the asset being played on the device is changed. I.e. it has passed
	 * from the server stream and made it to the front of the audio buffer
	 * on the device and the user started hearing it play.
	 * 
	 * The project configuration (will be retrieved from the RWService) is
	 * used to get the streamMetadataEnabled property to decide if the
	 * server will be sending metadata and using this class is possible. The
	 * streamMetadataTimerIntervalMSec property specifies the timer interval
	 * that will be used.
	 * 
	 * The broadcasted intent has the following properties:
	 * Action: RW.STREAM_METADATA_UPDATED
	 * Extra : RW.EXTRA_STREAM_METADATA_CURRENT_ASSET_ID (int)
	 * Extra : RW.EXTRA_STREAM_METADATA_PREVIOUS_ASSET_ID (int)
	 * Extra : RW.EXTRA_STREAM_METADATA_TITLE (String)
	 * 
	 * Call stop() when no longer interested in the metadata. This will
	 * stop the tracking loop and free some resources.
	 */
	public void start() {
		if (mMetadataReaderTimer != null) {
			stop();
		}

		RWConfiguration config = mRwService.getConfiguration();
		
		if ((config != null) && (config.isStreamMetadataEnabled())) {
			if (D) { Log.d(TAG, "Starting stream metadata reader", null); }
			mMetadataReaderTimer = new Timer();
			updateStreamMetadata(-1, "");
			mMetadataReaderTimer.schedule(new TimerTask() {
				public void run() {
					streamMetadataUpdate();
				}
			}, 0, config.getStreamMetadataTimerIntervalMSec());
		}
	}
	

	/**
	 * Stops tracking the assets streamed by the server. A final broadcast
	 * intent will be send with -1 for the current asset id and an empty
	 * song title.
	 */
	public void stop() {
		if (mMetadataReaderTimer != null) {
			if (D) { Log.d(TAG, "Stopping stream metadata reader", null); }
			mMetadataReaderTimer.cancel();
			mMetadataReaderTimer.purge();
			mMetadataReaderTimer = null;
			updateStreamMetadata(-1, "");
		}
	}
	

	/**
	 * Forces the tracking loop to reset and try to figure out what is
	 * being played anew. Note that the first few broadcast intents after
	 * this might be incorrect until the audio buffer, the server stream
	 * and the tracking are back in sync again.
	 */
	public void reset() {
		mNextUpdateTimeMsec = -1;
	}
	
	
	/**
	 * Called based on the timer, this method tracks the assets streamed by
	 * the server by using the rwGetCurrentStreamingAsset and rwGetAssetInfo
	 * methods of the RWService. New assets that start streaming are added
	 * to an array list. The asset duration is used to predict when the next
	 * asset should start and another server call is needed.
	 * 
	 * Assets in the array list are judged by their stored timing info to
	 * decide if they have slipped through (in case of very short assets), or
	 * could have started playing on the device (reach the front of the audio
	 * buffer). By lack of better control over the buffering on the device an
	 * estimation of the buffer length is used for this.
	 */
	private void streamMetadataUpdate() {
		long currentMillis = System.currentTimeMillis();
		RWConfiguration config = mRwService.getConfiguration();

		// check if stream metadata needs to be monitored
		if (!config.isStreamMetadataEnabled() || !mRwService.isPlaying() || mRwService.isPlayingStaticSoundtrack()) {
			mNextUpdateTimeMsec = -1;
			return;
		}

		// check if server started streaming a new asset
		if ((mNextUpdateTimeMsec == -1) || ((mNextUpdateTimeMsec - currentMillis) <= 0)) {
			try {
				// retrieve info about current playing (or last played) asset
				String jsonResponse = mRwService.rwGetCurrentStreamingAsset();
				if (jsonResponse != null) {
			        JSONObject jsonObj = new JSONObject(jsonResponse);
			        int assetId = jsonObj.optInt(mAssetIdJsonKey, -1);
	
			        // calculate already playing time
			        String currentServerTimeStr = jsonObj.optString(mCurrentServerTimeJsonKey, "");
			        String startTimeStr = jsonObj.optString(mStartTimeJsonKey, "");
			        long currentServerTimeMs = mServerTimeDateFormat.parse(currentServerTimeStr).getTime();
			        long assetStartTimeMs = mServerTimeDateFormat.parse(startTimeStr).getTime();
			        long assetPlayedTimeMs = currentServerTimeMs - assetStartTimeMs;
			        
			        // update info in list or add entry
			        StreamedAssetInfo info = assetInfoForId(assetId);
			        if (info == null) {
			        	info = new StreamedAssetInfo();
			        	info.assetId = assetId;
			        	info.assetDurationInStreamMs = jsonObj.optInt(mDurationInStreamMsJsonKey, 0);
	
				        // get asset duration information
			        	jsonResponse = mRwService.rwGetAssetInfo(assetId);
				        jsonObj = new JSONObject(jsonResponse);
				        info.assetDurationMs = jsonObj.optInt(mDurationInMsJsonKey, -1);

				        info.estimatedStartedPlayingTimeMs = currentMillis + mAverageBufferLengthMsec;
	
				        // check if still worth to add to list
				        if (info.assetDurationInStreamMs + mAverageBufferLengthMsec - assetPlayedTimeMs > 0) {
				        	mAssets.add(info);
				        }
			        }
			        // update the stored info
			        info.lastClientUpdateTimeMs = currentMillis;
			        info.startedPlayingOnServerTimeMs = assetStartTimeMs;
			        info.hasBeenPlayingOnServerForTimeMs = assetPlayedTimeMs;
			        
			        // calculate expected next update time and send broadcast
			        if (info.hasBeenPlayingOnServerForTimeMs > info.assetDurationInStreamMs) {
			        	mNextUpdateTimeMsec = -1; // on next interval
			        } else {
			        	mNextUpdateTimeMsec = currentMillis + info.assetDurationInStreamMs - assetPlayedTimeMs;
			        }
				}
			} catch (Exception e) {
				Log.w(TAG, "Error processing server stream metadata", e);
			}
		}
		
		// update metadata based on first asset that should still be playing (on the client)
		Iterator<StreamedAssetInfo> iter = mAssets.iterator();
		int assetId = -1;
		String meta = "";
		while (iter.hasNext()) {
			StreamedAssetInfo info = iter.next();
			if ((info.estimatedStartedPlayingTimeMs + info.assetDurationInStreamMs) < currentMillis) {
				iter.remove();
			} else {
				assetId = info.assetId;
				// make up a title for the new asset (we could pass this as asset info?)
				meta = String.format("Asset: %d (%.2fs)", info.assetId, (float)info.assetDurationInStreamMs / 1000.0);
				break;
			}
		}
		// trigger a broadcast intent if needed
    	updateStreamMetadata(assetId, meta);
	}
	
	
	/**
	 * When needed update the info about the asset being played based on the
	 * specified new asset ID and metadata (title).
	 *  
	 * @param newAssetId of asset that started playing on the device
	 * @param newMetadata about asset that started playing on the device
	 */
	private void updateStreamMetadata(int newAssetId, String newMetadata) {
		if (mCurrentlyPlayingAssetId != newAssetId) {
			mPreviouslyPlayedAssetId = mCurrentlyPlayingAssetId;
			mCurrentlyPlayingAssetId = newAssetId;
			if (!mRwService.isPlayingMuted()) {
				broadcastStreamMetadataUpdate(mCurrentlyPlayingAssetId, mPreviouslyPlayedAssetId, newMetadata);
			}
		}
	}
	
	
	/**
	 * Sends a RW.STREAM_METADATA_UPDATED broadcast intent that apps can
	 * listen for to be notified about changes in the asset being played.
	 * 
	 * @param currentAssetId of asset that started playing
	 * @param previousAssetId of asset that finished playing
	 * @param meta Title of the asset
	 */
	private void broadcastStreamMetadataUpdate(int currentAssetId, int previousAssetId, String meta) {
		Intent intent = new Intent();
		intent.setAction(RW.STREAM_METADATA_UPDATED);
		intent.putExtra(RW.EXTRA_STREAM_METADATA_CURRENT_ASSET_ID, currentAssetId);
		intent.putExtra(RW.EXTRA_STREAM_METADATA_PREVIOUS_ASSET_ID, previousAssetId);
		intent.putExtra(RW.EXTRA_STREAM_METADATA_TITLE, meta);
		if (D) { Log.d(TAG, "Going to send broadcast event, action = " + intent.getAction(), null); }
		mRwService.sendBroadcast(intent);
	}
	
	
	/**
	 * Returns the timing info stored internally for tracking the asset with
	 * the specified ID. Null if it does not exist.
	 * 
	 * @param assetId to retrieve timing info for
	 * @return StreamedAssetInfo instance for the asset, or null
	 */
	public StreamedAssetInfo assetInfoForId(int assetId) {
		for (StreamedAssetInfo item : mAssets) {
			if (item.assetId == assetId) {
				return item;
			}
		}
		return null;
	}
	
	
	/**
	 * Returns the average buffer length value (in msec) currently used for
	 * tracking assets.
	 * 
	 * @return average buffer length in msec
	 */
	public int getAverageStreamBufferLength() {
		return mAverageBufferLengthMsec;
	}
	

	/**
	 * Sets the average buffer length value used for tracking assets. The
	 * specified value should be >= 0.
	 * 
	 * @param averageBufferLengthMsec to use
	 */
	public void setAverageStreamBufferLength(int averageBufferLengthMsec) {
		if (averageBufferLengthMsec >= 0) {
			mAverageBufferLengthMsec = averageBufferLengthMsec;
		}
	}
	
}
