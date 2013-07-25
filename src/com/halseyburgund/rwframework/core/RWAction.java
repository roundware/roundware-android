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

import android.content.Context;
import android.util.Log;

import com.halseyburgund.rwframework.R;

import java.io.File;
import java.util.Properties;


/**
 * Class that represents an Action, which is a unit of communication with
 * the Roundware server. After an action is created, preferably by using
 * the RWActionFactory, it's perform method can be called to execute it
 * directly. Alternatively (and preferred) it can be passed to the RWService
 * perform method to be executed in the background. The RWService also puts 
 * failed actions into a queue and retries them later and will overwrite the 
 * URL specified for the action if the user has altered them in the app.
 *
 * @author Rob Knapen, Dan Latham
 */
public class RWAction {

    // debugging
    private final static String TAG = "RWAction";
    private final static boolean D = false;

    // fields
    private Long mDbId;
    private Context mContext;
    private Properties mProperties;

    
    /**
     * Creates an instance that will use the specified context for looking
     * up resources (e.g. from the rwconfig.xml).
     * 
     * @param context to be used
     */
    public RWAction(Context context) {
        mDbId = null;
        mContext = context;
        mProperties = new Properties();
    }


    /**
     * Creates a new instance with the specified parameters.
     * 
     * @param context to be used for accessing resources
     * @param databaseId reference to queue database record
     * @param p Properties to be included in the action
     */
    public RWAction(Context context, Long databaseId, Properties p) {
        mDbId = databaseId;
        mContext = context;
        mProperties = new Properties();
        if (p != null) {
            for (Object key : p.keySet()) {
                mProperties.put(key, p.get(key));
            }
        }
    }
    
    
    @Override
    public String toString() {
    	if (mProperties != null) {
    		return mProperties.toString();
    	} else {
    		return super.toString();
    	}
    }


    /**
     * Executes the action. When a filename is defined in the properties of
     * the action, a file upload to the server will be attempted based on the
     * properties. Otherwise they will be translated into a HTTP GET call to
     * the specified URL (in the properties). The server response is waited
     * for and returned. 
     * 
     * @param timeOutSec timeout in seconds for performing the action
     * @return server response resulting from the action call
     * @throws Exception caused by processing the action (I/O, HTTP)
     */
    public String perform(int timeOutSec) throws Exception {
        String filename = getFilename();
        if (filename != null) {
            if (D) { Log.d(TAG, "Uploading file: " + filename, null); }
            Properties p = getServerProperties();
            String response = RWHttpManager.uploadFile(getUrl(), p, getStringForResId(R.string.rw_key_file), filename, timeOutSec);
            if (D) { Log.d(TAG, "Server response: " + response, null); }
            File noteFile = new File(filename);
            noteFile.delete();
            return "";
        } else {
            if (D) { Log.d(TAG, "Sending GET to : " + getUrl(), null); }
            String response = RWHttpManager.doGet(getUrl(), getServerProperties(), timeOutSec);
            return response;
        }
    }

    
    /**
     * Adds to the action's properties the key-value pair based on the
     * specified resource ID's. The context is used to retrieve the values
     * from the resources.
     * 
     * @param keyResId String resource ID of the key
     * @param valueResId String resource ID of the value
     * @return Self, with key-value added
     */
    public RWAction add(int keyResId, int valueResId) {
        String key = mContext.getString(keyResId);
        String value = mContext.getString(valueResId);
        return add(key, value);
    }


    /**
     * Adds to the action's properties the key-value pair based on the
     * specified resource ID and value. The context is used to retrieve
     * the key from the resources.
     * 
     * @param keyResId String resource ID of the key
     * @param value
     * @return Self, with key-value added
     */
    public RWAction add(int keyResId, String value) {
        String key = mContext.getString(keyResId);
        return add(key, value);
    }


    /**
     * Adds to the action's properties the key-value pair based on the
     * specified parameters.
     * 
     * @param key (can not be null)
     * @param value (can not be null)
     * @return Self, with key-value added
     */
    public RWAction add(String key, String value) {
        if ((key != null) && (value != null)) {
            mProperties.put(key, value);
        } else {
            if (D) { Log.d(TAG, String.format("property not added, key='%s' value='%s'", (key == null) ? "null" : key, (value == null) ? "null" : value), null); }
        }
        return this;
    }


    /**
     * Removes the property with the specified key from the action.
     * 
     * @param key of key-value pair to be removed
     * @return Self, with key-value removed
     */
    public RWAction remove(String key) {
        mProperties.remove(key);
        return this;
    }


    /**
     * Returns the value for the key specified by a string resource ID.
     * 
     * @param keyResId String resource ID of the key
     * @return value stored for the key
     */
    public Object get(int keyResId) {
        String key = mContext.getString(keyResId);
        return get(key);
    }


    /**
     * Returns the value for the specified key.
     * 
     * @param key to get value for
     * @return value stored for the key
     */
    public Object get(String key) {
        return mProperties.get(key);
    }


    /**
     * Retrieves from the string resources the one with the specified ID.
     * 
     * @param resId String resource ID
     * @return String
     */
    public String getStringForResId(int resId) {
        return mContext.getString(resId);
    }


    /**
     * Returns the value for the key specified by the resource ID, if
     * defined for this action. When it has no matching key-value pair, 
     * the default value with the given string resource ID will be returned.
     * 
     * @param keyResId String resource ID of the key
     * @param defaultValueResId String resource ID of the default value
     * @return value for key, or the default value
     */
    public String getStr(int keyResId, int defaultValueResId) {
        String key = mContext.getString(keyResId);
        String defaultValue = mContext.getString(defaultValueResId);
        return getStr(key, defaultValue);
    }


    /**
     * Returns the value for the key specified by the resource ID, if
     * defined for this action. When it has no matching key-value pair, 
     * the defaultValue will be returned.
     * 
     * @param keyResId String resource ID of the key
     * @param defaultValue to return when key is not defined
     * @return value for key, or the defaultValue
     */
    public String getStr(int keyResId, String defaultValue) {
        String key = mContext.getString(keyResId);
        return getStr(key, defaultValue);
    }


    /**
     * Returns the value for the specified key, if defined for this action.
     * When it has no matching key-value pair, the defaultValue will be
     * returned.
     * 
     * @param key to get value for
     * @param defaultValue to return when key is not defined
     * @return value for key, or the defaultValue
     */
    public String getStr(String key, String defaultValue) {
        Object value = mProperties.get(key);
        if (value != null) {
            return value.toString();
        } else {
            return defaultValue;
        }
    }


    /**
     * Returns the properties collection for this action.
     * 
     * @return Properties
     */
    public Properties getProperties() {
        return mProperties;
    }


    /**
     * Returns all the properties defined for the action that are intended
     * for a server call. When the key is a string starting with a '_'
     * character, it will not be included. These are for internal use.
     * 
     * @return Properties with only the server relevant properties
     */
    public Properties getServerProperties() {
        Properties result = new Properties();
        for (Object key : mProperties.keySet()) {
            if (!(key instanceof String) || (!(((String) key).startsWith("_")))) {
                result.put(key, mProperties.get(key));
            }
        }
        return result;
    }
    

    /**
     * Returns the ID of the queue database record that stores it. When the
     * action has not been queued (yet), the ID will be null.
     *  
     * @return database ID, can be null
     */
    public Long getDatabaseId() {
        return mDbId;
    }


    /**
     * Gets the URL of the server this action is intended for. When the
     * action is performed, this is the server that will be called.
     * 
     * This method is a convenience method that uses the keywords defined
     * in the resources (e.g. rwconfig.xml) to retrieve the value from the
     * properties.
     * 
     * @return server URL, or null when not available
     */
    public String getUrl() {
        Object value = get(R.string.rw_key_server_url);
        if (value != null) {
            return value.toString();
        }
        return null;
    }


    /**
     * Gets the session ID parameter for this action.
     * 
     * This method is a convenience method that uses the keywords defined
     * in the resources (e.g. rwconfig.xml) to retrieve the value from the
     * properties.
     * 
     * @return session ID, or null when not available
     */
    public String getSessionId() {
        Object value = get(R.string.rw_key_session_id);
        if (value != null) {
            return value.toString();
        }
        return null;
    }
    
    
    /**
     * Sets (adds or overwrites) the session ID parameter for this action.
     * This might be useful when the action has been created in off-line
     * mode when no session ID was known, and it can be added later.
     * 
     * @param sessionId to set for the action
     */
    public void setSessionId(String sessionId) {
    	add(R.string.rw_key_session_id, sessionId);
    }


    /**
     * Gets the project ID parameter for this action.
     * 
     * This method is a convenience method that uses the keywords defined
     * in the resources (e.g. rwconfig.xml) to retrieve the value from the
     * properties.
     * 
     * @return project ID, or null when not available
     */
    public String getProjectId() {
        Object value = get(R.string.rw_key_project_id);
        if (value != null) {
            return value.toString();
        }
        return null;
    }


    /**
     * Gets the envelope ID parameter for this action.
     * 
     * This method is a convenience method that uses the keywords defined
     * in the resources (e.g. rwconfig.xml) to retrieve the value from the
     * properties.
     * 
     * @return envelope ID, or null when not available
     */
    public String getEnvelopeId() {
        Object value = get(R.string.rw_key_envelope_id);
        if (value != null) {
        	return value.toString();
        }
        return null;
    }

    
    /**
     * Sets (adds or overwrites) the envelope ID parameter for this action.
     * This might be useful when the action has been created in off-line
     * mode when no envelope ID was known, and it can be added later.
     * 
     * @param envelopeId to set for the action
     */
    public void setEnvelopeId(String envelopeId) {
    	add(R.string.rw_key_envelope_id, envelopeId);
    }
    
    
    /**
     * Gets the operation parameter for this action.
     * 
     * This method is a convenience method that uses the keywords defined
     * in the resources (e.g. rwconfig.xml) to retrieve the value from the
     * properties.
     * 
     * @return operation, or null when not available
     */
    public String getOperation() {
        Object value = get(R.string.rw_key_operation);
        if (value != null) {
            return value.toString();
        }
        return null;
    }


    /**
     * Gets the filename parameter for this action.
     * 
     * This method is a convenience method that uses the keywords defined
     * in the resources (e.g. rwconfig.xml) to retrieve the value from the
     * properties.
     * 
     * @return filename, or null when not available
     */
    public String getFilename() {
        Object value = get(R.string.rw_key_filename);
        if (value != null) {
            return value.toString();
        }
        return null;
    }


    /**
     * Gets the caption for this action.
     * 
     * This method is a convenience method that uses the keywords defined
     * in the resources (e.g. rwconfig.xml) to retrieve the value from the
     * properties.
     * 
     * @return caption, or null when not available
     */
    public String getCaption() {
        Object value = get(R.string.rw_key_label);
        if (value != null) {
            return value.toString();
        }
        return null;
    }


    /**
     * Gets the latitude for this action.
     * 
     * This method is a convenience method that uses the keywords defined
     * in the resources (e.g. rwconfig.xml) to retrieve the value from the
     * properties.
     * 
     * @return latitude, or NaN when not available
     */
    public Double getLatitude() {
        Object value = get(R.string.rw_key_latitude);
        if (value != null) {
            return Double.valueOf(value.toString());
        }
        return Double.NaN;
    }


    /**
     * Gets the longitude for this action.
     * 
     * This method is a convenience method that uses the keywords defined
     * in the resources (e.g. rwconfig.xml) to retrieve the value from the
     * properties.
     * 
     * @return longitude, or NaN when not available
     */
    public Double getLongitude() {
        Object value = get(R.string.rw_key_longitude);
        if (value != null) {
            return Double.valueOf(value.toString());
        }
        return Double.NaN;
    }
    
    
    /**
     * Gets the horizontal accuracy of the location information.
     * 
     * This method is a convenience method that uses the keywords defined
     * in the resources (e.g. rwconfig.xml) to retrieve the value from the
     * properties.
     * 
     * @return accuracy (in meters), or NaN when not available
     */
    public Double getAccuracy() {
    	Object value = get(R.string.rw_key_accuracy);
    	if (value != null) {
    		return Double.valueOf(value.toString());
    	}
    	return Double.NaN;
    }
    
    
    public String getSelectedTagsOptions() {
    	return (String) get(R.string.rw_key_tags);
    }
}
