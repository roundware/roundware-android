package org.roundware.rwapp;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import org.roundware.service.RWService;
import org.roundware.service.util.RWList;

import java.io.IOException;

/**
 * Refine Activity
 */
public class RwRefineActivity extends RwWebActivity {
    public final static String RWREFINE_TAG_URI = "taguri";
    private final static String LOGTAG = RwRefineActivity.class.getSimpleName();
    private final static String ROUNDWARE_TAGS_TYPE = "listen";
    private RWList mTagsList;
    private boolean mTagsChanged = false;


    @Override
    protected String getUrl() {
        return null;
    }

    @Override
    protected void handleOnServiceConnected(RWService service) {
        mTagsList = new RWList(mRwBinder.getTags().filterByType(ROUNDWARE_TAGS_TYPE));
        mTagsList.cullNonWebTags();
        mTagsList.restoreSelectionState(Settings.getSharedPreferences());

        // get the folder where the web content files are stored
        String contentFileDir = mRwBinder.getContentFilesDir();
        if ((mWebView != null) && (contentFileDir != null)) {
            String contentFileName = contentFileDir + "listen.html";
            try {
                String data = mRwBinder.readContentFile(contentFileName);
                data = data.replace("/*%roundware_tags%*/", mTagsList.toJsonForWebView(ROUNDWARE_TAGS_TYPE));
                mWebView.loadDataWithBaseURL("file://" + contentFileName, data, null, null, null);
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(LOGTAG, "Problem loading content file: listen.html");
            }
        }
    }

    @Override
    protected boolean handleRoundwareUrl(Uri uri) {
        if( super.handleRoundwareUrl(uri) ){
            return true;
        }else {
            String schemeSpecificPart = uri.getSchemeSpecificPart(); // everything from : to #
            if ("//listen_done".equalsIgnoreCase(schemeSpecificPart)) {
                // request update of audio stream directly when needed
                handleComplete();
                finish();
                return true;
            } else {
                if (mTagsList != null) {
                    mTagsList.setSelectionFromWebViewMessageUri(uri);

                    mTagsChanged = true;
                    //notify calling activity of uri effects
                    Intent intent = new Intent();
                    intent.putExtra(RWREFINE_TAG_URI, uri.toString());
                    setResult(RESULT_OK, intent);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        handleComplete();
        super.onBackPressed();
    }

    private void handleComplete(){
        if ( mTagsChanged && mRwBinder != null && mTagsList != null && mTagsList.hasValidSelectionsForTags()) {
            if (mRwBinder.isPlaying()) {
                new ModifyStreamTask(getApplicationContext(), mTagsList, getString(R.string.modify_stream_problem)).execute();
            }
        }
    }

    /**
     * Async task that calls rwModifyStream for direct processing, but in
     * the background for Android to keep the UI responsive.
     *
     * @author Rob Knapen
     */
    private class ModifyStreamTask extends AsyncTask<Void, Void, String> {

        private RWList selections;
        private String errorMessage;
        private Context context;

        public ModifyStreamTask(Context context, RWList selections, String errorMessage) {
            this.context = context;
            this.selections = selections;
            this.errorMessage = errorMessage;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                mRwBinder.rwModifyStream(selections, true);
                return null;
            } catch (Exception e) {
                return errorMessage;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            if (result != null) {
                Toast.makeText(context, result, Toast.LENGTH_SHORT);
            }
        }
    }
}
