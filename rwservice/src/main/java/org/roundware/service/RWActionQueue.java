/**
 * Roundware Android code is released under the terms of the GNU General Public License.
 * See COPYRIGHT.txt, AUTHORS.txt, and LICENSE.txt in the project root directory for details.
 */
package org.roundware.service;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.roundware.service.R;


/**
 * Queue of Action items, provided as access to a local database.
 * 
 * Note: Currently the queue and database (?) will be shared between apps
 * using RWFramework. This might causes problems and apps need to have a
 * unique instance. The database might be ok but the shared folder might
 * cause the most interference, in particular for the deleteQueue method.
 *
 * @author Rob Knapen, Dan Latham
 */
public class RWActionQueue {

    // debugging
    private final static String TAG = "RWActionQueue";
    private final static boolean D = false;

    // external storage location for Roundware purposes
    public final static String STORAGE_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/roundware/";
    // folder to store queued files, waiting for processing
    public final static String NOTE_QUEUE_PATH = STORAGE_PATH + "queue/";

    private static RWActionQueue mInstance;
    private Context mContext;


    /**
     * Hidden constructor, use class as singleton.
     */
    private RWActionQueue() {
        // void
    }


    /**
     * Accesses the singleton instance of this class.
     * 
     * @return singleton instance
     */
    public static RWActionQueue instance() {
        if (mInstance == null) {
            mInstance = new RWActionQueue();
        }
        return mInstance;
    }


    /**
     * Initialize the queue for the specified context.
     * 
     * @param context to be used by the queue instance
     */
    public void init(Context context) {
        createDir(NOTE_QUEUE_PATH);
        this.mContext = context;
    }


    /**
     * Returns the number of items currently in the queue.
     * 
     * @return number of items in the queue
     */
    public int count() {
        RWDbAdapter db = null;
        int count = 0;
        try {
            db = new RWDbAdapter(mContext);
            count = db.count();
        } finally {
            if (db != null) {
                db.close();
            }
        }
        return count;
    }


    /**
     * Creates a new entry in the queue and fills it according to the
     * specified properties.
     * 
     * @param props with info for the queue entry
     */
    public void add(Properties props) {
        RWDbAdapter db = null;
        try {
            db = new RWDbAdapter(mContext);
            db.insert(props);
        } finally {
            if (db != null) {
                db.close();
            }
        }
    }


    /**
     * Retrieves the first item from the queue.
     * 
     * @return RWAction created from the first queue item
     */
    public RWAction get() {
        RWDbAdapter db = null;
        RWAction action = null;
        try {
            db = new RWDbAdapter(mContext);
            action = db.getAction();
        } finally {
            if (db != null) {
                db.close();
            }
        }
        return action;
    }


    /**
     * Removes the specified action from the queue. This will delete its entry
     * in the queue, and also remove any temporary file associated with it.
     * 
     * @param action to be removed
     */
    public void delete(RWAction action) {
        String filename = action.getFilename();
        if (filename != null) {
            File noteFile = new File(filename);
            noteFile.delete();
        }

        RWDbAdapter db = null;
        try {
            db = new RWDbAdapter(mContext);
            db.delete(action.getDatabaseId());
        } finally {
            if (db != null) {
                db.close();
            }
        }

    }


    /**
     * Deletes the queue database and the folder holding all the temporary
     * files.
     * 
     * @return true when successful
     */
    public boolean deleteQueue() {
        boolean bReturn = RWDbAdapter.drop(mContext);
        bReturn = bReturn && deleteDir(new File(STORAGE_PATH));
        return bReturn;
    }


    /**
     * Deletes all files and (sub)folders) in the specified directory. If
     * a deletion fails, the method stops attempting to delete and returns
     * false. 
     * 
     * @param dir to be deleted
     * @return true if all deletions were successful
     */
    private boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (String aChildren : children) {
                boolean success = deleteDir(new File(dir, aChildren));
                if (!success) {
                    return false;
                }
            }
        }
        // The directory is now empty so delete it
        return dir.delete();
    }


    /**
     * Creates the specified folder, unless it already exists.
     * 
     * @param path of folder to be created
     */
    private void createDir(String path) {
        File queue = new File(path);
        if (!queue.exists()) {
            queue.mkdirs();
        }
    }
    
    
    /**
     * Moves the file with the specified filename into a temporary place
     * (the queued folder) so that it remains available for queued processing,
     * and avoid overwriting of the original.
     * 
     * @param filename of file to be moved to a temporary location
     * @return File instance for the moved file
     * @throws IOException on failure
     */
    protected File createTemporaryQueueFile(String filename) throws IOException {
        File scratchFile = new File(filename);

        int numQueued;
        String queueFilename;
        File dir = new File(RWActionQueue.NOTE_QUEUE_PATH);
        if (!dir.exists()) {
            numQueued = 0;
            dir.mkdirs();
        } else {
            numQueued = dir.listFiles().length;
        }

        // Move scratch file to new directory with a unique name
        String queuedFileBaseName = mContext.getString(R.string.rw_spec_queued_file_basename);
        String queuedFileExtension = mContext.getString(R.string.rw_spec_queued_file_extension);
        queueFilename = queuedFileBaseName + String.valueOf(numQueued) + queuedFileExtension;

        File queueFile = new File(dir, queueFilename);
        boolean success = scratchFile.renameTo(queueFile);

        if (!success) {
            String msg = mContext.getString(R.string.roundware_error_could_not_create_temp_queue_file);
            Log.e(TAG, msg, null);
            Log.e(TAG, "Name of file attempted to create: " + queueFilename, null);
            throw new IOException(msg);
        } else {
            if (D) { Log.d(TAG, "Temporary file created for queued action: " + queueFilename); }
        }

        return queueFile;
    }
    
}