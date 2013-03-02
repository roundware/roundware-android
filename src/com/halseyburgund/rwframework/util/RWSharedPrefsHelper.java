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
package com.halseyburgund.rwframework.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.halseyburgund.rwframework.core.RWConfiguration;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;


/**
 * Helper methods for saving and loading JSON data in Shared Preferences. It
 * can be used to create a cache of data retrieved from the server for use in
 * an off-line state.
 * 
 * @author Rob Knapen
 */
public class RWSharedPrefsHelper {

	// debugging
	private final static String TAG = "RWSharedPrefsHelper";
	
	// prefix used in keys in the shared preferences
	private static final String PREFIX = "json_";


	/**
	 * Parses the specified JSON data into a JSON Object and stores its
	 * string representation into the shared preferences with the specified
	 * name.
	 *  
	 * @param context to be used to access shared preferences
	 * @param preferencesName of shared preferences to be used
	 * @param key for storing the data
	 * @param jsonData to be stored
	 */
	public static void saveJSONObject(Context context, String preferencesName, String key, String jsonData) {
		try {
			JSONObject object = new JSONObject(jsonData);
			SharedPreferences settings = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE);
			SharedPreferences.Editor editor = settings.edit();
			editor.putString(RWSharedPrefsHelper.PREFIX + key, object.toString());
			editor.commit();
		} catch (JSONException e) {
			Log.e(TAG, RWConfiguration.JSON_SYNTAX_ERROR_MESSAGE, e);
		}
	}


	/**
	 * Parses the specified JSON data into a JSON Array and stores its
	 * string representation into the shared preferences with the specified
	 * name.
	 *  
	 * @param context to be used to access shared preferences
	 * @param preferencesName of shared preferences to be used
	 * @param key for storing the data
	 * @param jsonData to be stored
	 */
	public static void saveJSONArray(Context context, String preferencesName, String key, String jsonData) {
		try {
			JSONArray array = new JSONArray(jsonData);
			SharedPreferences settings = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE);
			SharedPreferences.Editor editor = settings.edit();
			editor.putString(RWSharedPrefsHelper.PREFIX + key, array.toString());
			editor.commit();
		} catch (JSONException e) {
			Log.e(TAG, RWConfiguration.JSON_SYNTAX_ERROR_MESSAGE, e);
		}
	}


	/**
	 * Retrieves the data for the specified key from the shared preferences
	 * and tries to create a JSON Object from it, which string representation
	 * will be returned
	 * 
	 * @param context to be used to access shared preferences
	 * @param preferencesName of shared preferences to be used
	 * @param key for retrieving the data
	 * @return string representation of the JSON object data retrieved
	 */
	public static String loadJSONObject(Context context, String preferencesName, String key) {
		try {
			SharedPreferences settings = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE);
			JSONObject object = new JSONObject(settings.getString(RWSharedPrefsHelper.PREFIX + key, "{}"));
			return object.toString();
		} catch (JSONException e) {
			Log.e(TAG, RWConfiguration.JSON_SYNTAX_ERROR_MESSAGE, e);
			return null;
		}
	}


	/**
	 * Retrieves the data for the specified key from the shared preferences
	 * and tries to create a JSON Array from it, which string representation
	 * will be returned
	 * 
	 * @param context to be used to access shared preferences
	 * @param preferencesName of shared preferences to be used
	 * @param key for retrieving the data
	 * @return string representation of the JSON array data retrieved
	 */
	public static String loadJSONArray(Context context, String preferencesName, String key) {
		try {
		SharedPreferences settings = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE);
		JSONArray array = new JSONArray(settings.getString(RWSharedPrefsHelper.PREFIX + key, "[]"));
		return array.toString();
		} catch (JSONException e) {
			Log.e(TAG, RWConfiguration.JSON_SYNTAX_ERROR_MESSAGE, e);
			return null;
		}
	}


	/**
	 * Removes the data with the specified key from the indicated shared
	 * preferences.
	 * 
	 * @param context to be used to access shared preferences
	 * @param preferencesName of shared preferences to be used
	 * @param key for data to be deleted
	 */
	public static void remove(Context context, String preferencesName, String key) {
		SharedPreferences settings = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE);
		if (settings.contains(RWSharedPrefsHelper.PREFIX + key)) {
			SharedPreferences.Editor editor = settings.edit();
			editor.remove(RWSharedPrefsHelper.PREFIX + key);
			editor.commit();
		}
	}

}
