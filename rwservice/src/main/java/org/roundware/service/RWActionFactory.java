/**
 * Roundware Android code is released under the terms of the GNU General Public License.
 * See COPYRIGHT.txt, AUTHORS.txt, and LICENSE.txt in the project root directory for details.
 */
package org.roundware.service;

import android.annotation.SuppressLint;
import android.location.Location;

import org.roundware.service.util.RWList;
import org.roundware.service.util.RWListItem;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;


/**
 * Factory for creating standard RWAction instances based on the Roundware 2.0
 * protocol.
 * 
 * @author Rob Knapen
 */
public class RWActionFactory {

    // debugging
    // private final static String TAG = "RWActionFactory";

    // fields
    private RWService mService;


    /**
     * Creates a factory instance for the specified RWService. This
     * factory can be used for creating RWAction instances that can
     * be processed through the RWService's perform method. An RWAction
     * instance in general represents an HTTP GET or a multi-part HTTP
     * POST file upload call.
     * 
     * @param service to be used by the factory
     */
    public RWActionFactory(RWService service) {
        mService = service;
    }


    /**
     * Create a generic action with the specified properties, that, as
     * key - value pairs, hold the server url and the url arguments to
     * be used in the GET or POST calls.
     * 
     * Note that this method is mostly for internal use, unless you known
     * what you are doing.
     * 
     * @param p Properties for the action
     * @return RWAction instance
     */
    protected RWAction create(Properties p) {
        return new RWAction(mService, null, p);
    }


    /**
     * Creates a default RWAction instance with a default label, the server
     * url as defined by the RWService, and the default project ID retrieved
     * from the resources (rwconfig.xml). This can be overwritten later. When
     * addSessionId is true and a session ID is available from the RWService
     * configuration, it will be added too.
     * 
     * @param addSessionId from RWService configuration
     * @return RWAction instance
     */
    private RWAction createDefaultAction(boolean addSessionId) {
        RWAction action = new RWAction(mService);
        action.add(R.string.rw_key_label,
                R.string.roundware_notification_default_text)
                .add(R.string.rw_key_server_url, mService.getServerUrl())
                .add(R.string.rw_key_project_id, R.string.rw_spec_project_id);
        
        if (addSessionId) {
            String sessionId = mService.getConfiguration().getSessionId();
            if ((sessionId != null) && (sessionId.length() > 0)) {
                action.add(R.string.rw_key_session_id, sessionId);
            }
        }

        return action;
    }
    
    
    /**
     * Creates an action to retrieve the configuration parameters for the
     * specified project from the server. When a device ID is known it can
     * be given, otherwise the server will assign one and return it as
     * part of the configuration.
     * 
     * @param deviceId to be re-used, or null if not available
     * @param projectId to retrieve configuration parameters for
     * @return RWAction instance for the server call
     */
    public RWAction createRetrieveProjectConfigurationAction(String deviceId, String projectId) {
        RWAction action = createDefaultAction(true);
        action.add(R.string.rw_key_label,
                R.string.roundware_notification_retrieve_configuration)
                .add(R.string.rw_key_operation, R.string.rw_op_get_config)
                .add(R.string.rw_key_project_id, projectId);

        // add device info
        action.add(R.string.rw_key_client_type, android.os.Build.MANUFACTURER + " " + android.os.Build.DEVICE);
        action.add(R.string.rw_key_client_system, "Android " + android.os.Build.VERSION.RELEASE);
        action.add(R.string.rw_key_language, Locale.getDefault().getLanguage());
        
        // add unique id for app installation, if available
        if ((deviceId != null) && (deviceId.length() > 0)) {
            action.add(R.string.rw_key_device_id, deviceId);
        }

        return action;
    }


    /**
     * Creates an action to retrieve the tags (for marking, filtering and
     * selecting) for the specified project from the server.
     *
     * @param projectId to retrieve tags for
     * @return RWAction instance for the server call
     */
    public RWAction createRetrieveTagsForProjectAction(String projectId) {
        RWAction action = createDefaultAction(true);
        action.add(R.string.rw_key_label,
                R.string.roundware_notification_retrieving_tags)
                .add(R.string.rw_key_operation, R.string.rw_op_get_tags)
                .add(R.string.rw_key_project_id, projectId)
                .add(R.string.rw_key_language, Locale.getDefault().getLanguage());

        return action;
    }


    /**
     * Creates a heartbeat action. This can be used to let the server know
     * the client is still around (e.g. when listening to an audio stream),
     * or as a simple command to check if the server is still responding.
     *
     * @return RWAction instance for the server call
     */
    public RWAction createHeartbeatAction() {
        RWAction action = createDefaultAction(true);
        action.add(R.string.rw_key_label,
                R.string.roundware_notification_heartbeat)
                .add(R.string.rw_key_operation, R.string.rw_op_heartbeat);
        
        addCoordinates(action);

        return action;
    }
    
    
    /**
     * Creates an action to send a log event to the server.
     * 
     * @param eventTypeResId type of the log event
     * @param tags to send to the server as part of the log event, may be null
     * @param data to send to the server as part of the log event, may be null
     * 
     * @return RWAction instance for the server call
     */
    public RWAction createLogEventAction(int eventTypeResId, RWList tags, String data) {
        RWAction action = createDefaultAction(true);
        action.add(R.string.rw_key_label, R.string.roundware_notification_event)
                .add(R.string.rw_key_operation, R.string.rw_op_log_event)
                .add(R.string.rw_key_event_type, eventTypeResId);
        
        addCoordinates(action);
        addClientTime(action);

        if (tags != null) {
            addTags(action, tags);
        }
        if (data != null) {
            action.add(R.string.rw_key_data, data);
        }
        
        return action;
    }

    
    /**
     * Creates an action to retrieve information about the last streamed or
     * still streaming asset from the server. This will include the ID of the
     * asset and timing data. 
     * 
     * @return RWAction instance for the server call
     */
    public RWAction createRetrieveLastStreamedAssetInfoAction() {
        RWAction action = createDefaultAction(true);
        action.add(R.string.rw_key_label,
                R.string.roundware_notification_requesting_current_streaming_asset)
                .add(R.string.rw_key_operation, R.string.rw_op_get_current_streaming_asset);
        return action;
    }
    

    /**
     * Creates an action to retrieve information about a specific asset from
     * the server.
     * 
     * @param assetId to retrieve information for
     * @return RWAction instance for the server call
     */
    public RWAction createRetrieveAssetInfoAction(int assetId) {
        RWAction action = createDefaultAction(false);
        action.add(R.string.rw_key_label,
                R.string.roundware_notification_requesting_asset_info)
                .add(R.string.rw_key_operation, R.string.rw_op_get_asset_info)
                .add(R.string.rw_key_asset_id, String.valueOf(assetId));
        return action;
    }
    
    
    /**
     * Creates an action for voting on an asset. Valid vote types and vote
     * values are defined by the Roundware prototype, and might include types
     * such as 'like' and 'flag'. The vote value is optional and can be 
     * specified as null.
     * 
     * @param assetId to vote on
     * @param voteType for voting
     * @param voteValue for voting, null to ignore
     * @return RWAction instance for the server call
     */
    public RWAction createVoteAssetAction(int assetId, String voteType, String voteValue) {
        RWAction action = createDefaultAction(true);
        action.add(R.string.rw_key_label,
                R.string.roundware_notification_voting_on_asset)
                .add(R.string.rw_key_operation, R.string.rw_op_vote_asset)
                .add(R.string.rw_key_asset_id, String.valueOf(assetId))
                .add(R.string.rw_key_vote_type, voteType);
        
        if (voteValue != null) {
                action.add(R.string.rw_key_vote_value, voteValue);
        }
        
        return action;
    }

    
    /**
     * Creates an action to request the server to skip ahead to the next
     * asset and insert it into the audio stream.
     * 
     * @return RWAction instance for the server call
     */
    public RWAction createSkipAheadAction() {
        RWAction action = createDefaultAction(true);
        action.add(R.string.rw_key_label,
                R.string.roundware_notification_requesting_skip_ahead)
                .add(R.string.rw_key_operation, R.string.rw_op_skip_ahead);
        return action;
    }
    
    
    /**
     * Creates an action to request the server to insert the specified
     * asset into the audio stream.
     * 
     * @param assetId of recording to be inserted into the stream
     * @return RWAction instance for the server call
     */
    public RWAction createPlayAssetInStreamAction(int assetId) {
        RWAction action = createDefaultAction(true);
        action.add(R.string.rw_key_label,
                R.string.roundware_notification_requesting_asset_replay)
                .add(R.string.rw_key_operation, R.string.rw_op_play_asset_in_stream)
                .add(R.string.rw_key_asset_id, String.valueOf(assetId));
        return action;
    }
    
    
    /**
     * Creates an action to request an audio stream from the server, based
     * on the included tags selections. Coordinates of the current location
     * of the user / device will be included automatically if available.
     * 
     * @param tags to include in the call
     * @return RWAction instance for the server call
     */
    @SuppressLint("DefaultLocale") public RWAction createRequestStreamAction(RWList tags) {
        RWAction action = createDefaultAction(true);
        action.add(R.string.rw_key_label,
                R.string.roundware_notification_requesting_stream)
                .add(R.string.rw_key_operation, R.string.rw_op_get_stream)
                /*Valid options are: 64, 96, 112, 128, 160, 192, 256 and 320*/
                .add(R.string.rw_key_audio_bitrate, "128");

        addTags(action, tags);

        // can skip adding coordinates for debugging purposes
        String str = mService.getString(R.string.rw_debug_open_audio_stream_without_location_yn);
        if ((str != null) && ("N".equals(str.toUpperCase(Locale.US)))) {
            addCoordinates(action);
        }

        return action;
    }

    
    /**
     * Creates an action to modify the audio stream, based on the
     * coordinates of the current location of the user / device that
     * will be included automatically if available.
     * 
     * @return RWAction instance for the server call
     */
    public RWAction createModifyStreamAction() {
        RWAction action = createDefaultAction(true);
        action.add(R.string.rw_key_label,
                R.string.roundware_notification_modifying_stream)
                .add(R.string.rw_key_operation, R.string.rw_op_modify_stream);
        
        addCoordinates(action);

        return action;
    }
    

    /**
     * Creates an action to modify the audio stream, based on the included
     * tags selections and the coordinates of the current location of the
     * user / device that will be included automatically if available.
     * 
     * @param tags to include in the call
     * @return RWAction instance for the server call
     */
    public RWAction createModifyStreamAction(RWList tags) {
        RWAction action = createModifyStreamAction();
        addTags(action, tags);
        return action;
    }
    
    
    /**
     * Creates an action to create an envelope on the server, based on 
     * the included tags selections and the coordinates of the current 
     * location of the user / device that will be included automatically 
     * if available.
     * 
     * @param tags to include in the call
     * @return RWAction instance for the server call
     */
    public RWAction createCreateEnvelopeAction(RWList tags) {
        RWAction action = createDefaultAction(true);
        action.add(R.string.rw_key_label,
                R.string.roundware_notification_announcing_recording)
                .add(R.string.rw_key_operation, R.string.rw_op_create_envelope);
        addTags(action, tags);
        
        addCoordinates(action);

        return action;
    }

    
    /**
     * Creates an action to create an envelope on the server, based on 
     * the included tags selections and the coordinates of the current 
     * location of the user / device that will be included automatically 
     * if available.
     * 
     * @param selectedTagsOptions selected tag_ids, comma separated
     * @return RWAction instance for the server call
     */
    public RWAction createCreateEnvelopeAction(String selectedTagsOptions) {
        RWAction action = createDefaultAction(true);
        action.add(R.string.rw_key_label,
                R.string.roundware_notification_announcing_recording)
                .add(R.string.rw_key_operation, R.string.rw_op_create_envelope)
                .add(R.string.rw_key_tags, selectedTagsOptions);
        
        addCoordinates(action);
        
        return action;
    }
    

    /**
     * Creates an action to upload a file to the server and place it in
     * the specified envelope, based on the included tags selections and 
     * the coordinates of the current location of the user / device that 
     * will be included automatically if available.
     * 
     * @param tags to include in the call
     * @param envelopeId to include in the call
     * @param filename to include in the call
     * @param submitted for stream (Y) or not (N), null to ignore
     * @return RWAction instance for the server call
     */
    public RWAction createAddAssetToEnvelopeAction(RWList tags, int envelopeId, String filename, String submitted) {
        RWAction action = createDefaultAction(true);
        action.add(R.string.rw_key_label,
                R.string.roundware_notification_uploading_recording)
                .add(R.string.rw_key_operation, R.string.rw_op_add_asset_to_envelope)
                .add(R.string.rw_key_envelope_id, String.valueOf(envelopeId))
                .add(R.string.rw_key_filename, filename);
        
        if (submitted != null) {
            action.add(R.string.rw_key_submitted, submitted);
        }
        
        addTags(action, tags);
        addCoordinates(action);

        return action;
    }

    
    /**
     * Adds the current coordinates as properties to the specified action.
     * When no location is known nothing will be added, otherwise the
     * latitude and longitude, with 6 digits precision, and the accuracy
     * in meters.
     * 
     * @param action to add coordinates to
     * @return updated action
     */
    private RWAction addCoordinates(RWAction action) {
        if (action != null) {
            if ((mService != null) && (mService.getConfiguration().isUsingLocation())) {
                // get last known location from service
                Location loc = mService.getLastKnownLocation();
                if (loc != null) {
                    // 6 decimal places -> approx dec. degrees accuracy = 0
                    // .111 meters (with 5 = 1.11 meters)
                    String lon = String.format(Locale.US, "%.6f", loc.getLongitude());
                    String lat = String.format(Locale.US, "%.6f", loc.getLatitude());
                    String acc = String.format(Locale.US, "%.1f", loc.getAccuracy());
                    if (!("NaN".equals(lat) && "NaN".equals(lon))) {
                        action.add(R.string.rw_key_longitude, lon);
                        action.add(R.string.rw_key_latitude, lat);
                    }
                    action.add(R.string.rw_key_accuracy, acc);
                    action.add(R.string.rw_key_location_provider_name,
                            loc.getProvider());
                }
            }
        }
        return action;
    }
    
    
    /**
     * Adds the current client time as a property to the specified action.
     * 
     * Format: 2012-01-09 00:42:02 +0000
     * 
     * @param action to add client time to
     * @return updated action
     */
    private RWAction addClientTime(RWAction action) {
        if (action != null) {
            SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.Z", Locale.US);
            action.add(R.string.rw_key_client_time, sf.format(new Date()));
        }
        return action;
    }


    /**
     * Adds the selected tags as a property to the specified action.
     * 
     * Note: What is actually added to the property are the tag_id's of all
     * the tag options that are selected (isOn()) in the specified RWList.
     * 
     * @param action to add selected tags to
     * @return updated action
     */
    private RWAction addTags(RWAction action, RWList tags) {
        if ((action != null) && (tags != null) && (!tags.isEmpty())) {
            StringBuilder sb = new StringBuilder();
            for (RWListItem item : tags) {
                if (item.isOn()) {
                    appendWithSeparator(sb, String.valueOf(item.getTagId()));
                }
            }
            action.add(R.string.rw_key_tags, sb.toString());
        }
        return action;
    }


    /**
     * Append the specified text to the StringBuilder, inserting a tab
     * first if the StringBuilder is not empty.
     *
     * @param sb   StringBuilder to append text to
     * @param text to append
     * @return reference to the updated StringBuilder
     */
    private StringBuilder appendWithSeparator(StringBuilder sb, String text) {
        if ((sb != null) && (text != null)) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(text);
        }
        return sb;
    }

}
