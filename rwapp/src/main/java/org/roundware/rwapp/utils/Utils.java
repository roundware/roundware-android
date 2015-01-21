/**
 * Roundware Android code is released under the terms of the GNU General Public License.
 * See COPYRIGHT.txt, AUTHORS.txt, and LICENSE.txt in the project root directory for details.
 */
package org.roundware.rwapp.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;

import org.roundware.rwapp.R;


/**
 * Utility methods shared between activities in this app.
 * 
 * @author Rob Knapen
 */
public class Utils {

    /**
     * Shows a standardized message dialog based on the specified parameters.
     *
     * @param activity to close when a fatal message is displayed
     * @param message to be displayed in the dialog
     * @param isError for error messages, informational otherwise
     * @param isFatal for fatal messages that finish the activity
     */
    public static void showMessageDialog(final Activity activity, String message, final boolean isError, final boolean isFatal) {
        Builder alertBox;
        alertBox = new AlertDialog.Builder(activity);
        if (isError) {
            alertBox.setTitle(R.string.error_title);
        } else {
            alertBox.setTitle(R.string.message_title);
        }
        alertBox.setMessage(message);

        String buttonLabel;
        if (!isFatal) {
            buttonLabel = activity.getString(android.R.string.ok);
        } else {
            buttonLabel = activity.getString(R.string.exit);
        }

        alertBox.setPositiveButton(buttonLabel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                if (isFatal) {
                    activity.finish();
                }
            }
        });
        alertBox.show();
    }


    /**
     * Shows a standardized progress dialog based on the specified parameters.
     *
     * @param c Context to reference
     * @param title for the progress dialog
     * @param message to display in the progress dialog
     * @param indeterminate setting for the new progress dialog
     * @param cancelable setting for the new progress dialog
     * @return the ProgressDialog displayed
     */
    //FIXME this leaks windows!
    public static ProgressDialog showProgressDialog(Context c, String title, String message, boolean indeterminate, boolean cancelable) {
        return ProgressDialog.show(c, title, message, indeterminate, cancelable);
    }



}
