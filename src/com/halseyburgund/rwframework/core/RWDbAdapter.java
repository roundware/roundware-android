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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Properties;


/**
 * Database for storing Roundware action items.
 * 
 * @author Rob Knapen, Dan Latham
 */
public class RWDbAdapter {

	public static final String KEY_ROWID = "_id";
	public static final String PARAMS = "params";

	private static final String TAG = "RWDbAdapter";

	/**
	 * Database creation SQL statement
	 */
	private static final String DATABASE_NAME = "RoundwareDB";
	private static final String DATABASE_TABLE = "actions";
	private static final int DATABASE_VERSION = 3;

	private static final String DATABASE_CREATE = "create table " + DATABASE_TABLE
			+ " (_id integer primary key autoincrement, " + PARAMS + " TEXT not null)";

	private static final String DATABASE_COUNT = "select _id from " + DATABASE_TABLE;

	private Context mContext;
	private SQLiteDatabase mDb;


	private static class DatabaseHelper extends SQLiteOpenHelper {
		DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}


		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(DATABASE_CREATE);
		}


		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.i(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion
					+ ", which will destroy all old data");
			db.execSQL("DROP TABLE IF EXISTS " + DATABASE_TABLE);
			onCreate(db);
		}
	}


	/**
	 * Constructor - takes the context to allow the database to be
	 * opened/created
	 * 
	 * @param ctx
	 *            the Context within which to work
	 */
	public RWDbAdapter(Context ctx) {
		mContext = ctx;
		DatabaseHelper dbHelper = new DatabaseHelper(mContext);
		mDb = dbHelper.getWritableDatabase();
	}


	public void close() {
		mDb.close();
	}


	public boolean insert(Properties props) throws SQLException {
		boolean bReturn = true;

		try {
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			props.storeToXML(stream, null);

			ContentValues initialValues = new ContentValues();
			initialValues.put(PARAMS, stream.toString());

			long id = mDb.insert(DATABASE_TABLE, null, initialValues);

			bReturn = (id > 0);
		} catch (Exception ex) {
			bReturn = false;
		}

		return (bReturn);
	}


	/**
	 * Delete the note with the given rowId
	 * 
	 * @param rowId
	 *            id of note to delete
	 * @return true if deleted, false otherwise
	 */
	public boolean delete(Long rowId) {
		int st = mDb.delete(DATABASE_TABLE, KEY_ROWID + "=" + rowId, null);
		return st > 0;
	}


	public static boolean drop(Context context) {
		return context.deleteDatabase(DATABASE_NAME);
	}


	/**
	 * Return a Cursor over the list of all notes in the database
	 * 
	 * @return Cursor over all notes
	 */
	public RWAction getAction() {
		RWAction action = null;
		Cursor cursor = null;

		try {
			cursor = mDb.query(DATABASE_TABLE, new String[] { KEY_ROWID, PARAMS }, null, null, null, null, null);

			if ((cursor != null) && (cursor.moveToFirst())) {
				int id = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_ROWID));
				String params = cursor.getString(cursor.getColumnIndexOrThrow(PARAMS));
				cursor.close();

				ByteArrayInputStream stream = new ByteArrayInputStream(params.getBytes());
				Properties props = new Properties();
				props.loadFromXML(stream);
				action = new RWAction(mContext, (long) id, props);
			}
		} catch (Exception ex) {
			// not really an error to report
			ex.printStackTrace();
			Log.e(TAG, ex.getMessage(), ex);
		}

		return action;
	}


	public int count() {
		String[] args = new String[0];

		Cursor cursor = mDb.rawQuery(DATABASE_COUNT, args);
		int count = cursor.getCount();
		cursor.close();

		return count;
	}

}
