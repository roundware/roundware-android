package org.roundware.rwapp;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Base class for the activities that hold a sole webview,
 * extends RwBoundActivity
 */
public abstract class RwWebActivity extends RwBoundActivity {
    public static final String LOGTAG = RwWebActivity.class.getSimpleName();
    private final static boolean D = true;
    protected WebView mWebView = null;

    /**
     * @return url for this activity, null does nothing
     */
    protected abstract String getUrl();

    /**
     * Override for additional url loading overriding, it os recommended the overriding method
     * should call super
     * @param uri
     * @return handled
     */
    protected boolean handleRoundwareUrl(Uri uri){
        String schemeSpecificPart = uri.getSchemeSpecificPart(); // everything from : to #
        if ("//webview_done".equalsIgnoreCase((schemeSpecificPart))) {
            finish();
            return true;
        }
        return false;
    }

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //?
        //getWindow().requestFeature(Window.FEATURE_PROGRESS);

        setContentView(R.layout.activity_web);
        mWebView = (WebView) findViewById(R.id.webView);

        String url = getUrl();
        if(!TextUtils.isEmpty(url)) {
            mWebView.loadUrl(url);
        }

        WebSettings webSettings = mWebView.getSettings();

        webSettings.setAppCachePath(this.getFilesDir().getAbsolutePath());
        webSettings.setAppCacheEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
        webSettings.setSupportMultipleWindows(false);
        webSettings.setSupportZoom(false);
        webSettings.setSavePassword(false);
        webSettings.setGeolocationDatabasePath(this.getFilesDir().getAbsolutePath());
        webSettings.setGeolocationEnabled(false);
        webSettings.setDatabaseEnabled(false);
        webSettings.setDomStorageEnabled(false);

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Log.d(LOGTAG, "shouldOverrideUrlLoading");
                Uri uri = Uri.parse(url);
                if (uri.getScheme().equals("roundware")) {
                    Log.d(LOGTAG, "Processing roundware uri: " + url);
                    handleRoundwareUrl(uri);
                    return true;
                }
                // open link in external browser
                return super.shouldOverrideUrlLoading(view, url);
            }

        });
    }
}

