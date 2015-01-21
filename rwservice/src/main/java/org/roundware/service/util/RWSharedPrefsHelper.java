/**
 * Roundware Android code is released under the terms of the GNU General Public License.
 * See COPYRIGHT.txt, AUTHORS.txt, and LICENSE.txt in the project root directory for details.
 */
package org.roundware.service.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.roundware.service.RWConfiguration;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


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

    // prefix used in keys in the shared preferences for JSON data caching
    private static final String JSON_DATA_PREFIX = "json_";

    private static final String CONTENT_FILES_URL_KEY = "files_url";
    private static final String CONTENT_FILES_VERSION_KEY = "files_version";
    private static final String CONTENT_FILES_STORAGE_DIR_NAME_KEY = "files_storage_dir_name";


    public static class ContentFilesInfo {
        public String filesUrl;
        public int filesVersion;
        public String filesStorageDirName;

        public ContentFilesInfo() {
            this(null, -1, null);
        }

        public ContentFilesInfo(String url, int version, String dirName) {
            filesUrl = url;
            filesVersion = version;
            filesStorageDirName = dirName;
        }
    }


    public static void saveContentFilesInfo(Context context, String preferencesName, ContentFilesInfo info) {
        if ((context != null) && (info != null)) {
            SharedPreferences settings = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(RWSharedPrefsHelper.CONTENT_FILES_URL_KEY, info.filesUrl);
            editor.putInt(RWSharedPrefsHelper.CONTENT_FILES_VERSION_KEY, info.filesVersion);
            editor.putString(RWSharedPrefsHelper.CONTENT_FILES_STORAGE_DIR_NAME_KEY, info.filesStorageDirName);
            editor.commit();
        }
    }


    public static ContentFilesInfo loadContentFilesInfo(Context context, String preferencesName) {
        ContentFilesInfo filesInfo = new ContentFilesInfo();
        if (context != null) {
            SharedPreferences settings = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE);
            filesInfo.filesUrl = settings.getString(RWSharedPrefsHelper.CONTENT_FILES_URL_KEY, null);
            filesInfo.filesVersion = settings.getInt(RWSharedPrefsHelper.CONTENT_FILES_VERSION_KEY, -1);
            filesInfo.filesStorageDirName = settings.getString(RWSharedPrefsHelper.CONTENT_FILES_STORAGE_DIR_NAME_KEY, null);
        }
        return filesInfo;
    }


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
            editor.putString(RWSharedPrefsHelper.JSON_DATA_PREFIX + key, object.toString());
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
            editor.putString(RWSharedPrefsHelper.JSON_DATA_PREFIX + key, array.toString());
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
            JSONObject object = new JSONObject(settings.getString(RWSharedPrefsHelper.JSON_DATA_PREFIX + key, "{}"));
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
        JSONArray array = new JSONArray(settings.getString(RWSharedPrefsHelper.JSON_DATA_PREFIX + key, "[]"));
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
        if (settings.contains(RWSharedPrefsHelper.JSON_DATA_PREFIX + key)) {
            SharedPreferences.Editor editor = settings.edit();
            editor.remove(RWSharedPrefsHelper.JSON_DATA_PREFIX + key);
            editor.commit();
        }
    }

}
