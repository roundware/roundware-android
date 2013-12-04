/*
    ROUNDWARE
	a participatory, location-aware media platform
	Android client library
   	Copyright (C) 2008-2013 Halsey Solutions
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
package edu.si.sfms.utils;

import edu.si.sfms.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;


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
	public static ProgressDialog showProgressDialog(Context c, String title, String message, boolean indeterminate, boolean cancelable) {
		return ProgressDialog.show(c, title, message, indeterminate, cancelable);
	}

}
