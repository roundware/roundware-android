/**
 * Roundware Android code is released under the terms of the GNU General Public License.
 * See COPYRIGHT.txt, AUTHORS.txt, and LICENSE.txt in the project root directory for details.
 */
package org.roundware.rwapp;

import android.content.Context;
import android.content.SharedPreferences;

/**
 *
 */
public class Settings {
    // name of shared preferences used by all activities in the app
    public final static String APP_SHARED_PREFS = "org.roundware.rwapp.preferences";

    // preferences keys for parameter storage
    public final static String PREFS_KEY_RW_DEVICE_ID = "SavedRoundwareDeviceId";

    private Settings() {
    }

    public static SharedPreferences getSharedPreferences() {
        return RwApplication.getAppContext().getSharedPreferences(APP_SHARED_PREFS, Context.MODE_PRIVATE);
    }
}
