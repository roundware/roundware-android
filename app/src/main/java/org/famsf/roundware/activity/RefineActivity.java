/*
    STORIES FROM MAIN STREET
	Android client application
   	Copyright (C) 2008-2013 Halsey Solutions, LLC
	with contributions by Rob Knapen
	ALL RIGHTS RESERVED
*/
package org.famsf.roundware.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.google.android.gms.common.GooglePlayServicesUtil;
import com.halseyburgund.rwframework.core.RW;
import com.halseyburgund.rwframework.core.RWService;
import com.halseyburgund.rwframework.util.RWList;

import org.famsf.roundware.R;
import org.famsf.roundware.Settings;
import org.famsf.roundware.utils.Utils;

import java.io.IOException;
import java.util.UUID;

public class RefineActivity extends Activity {
    public static final String LOGTAG = RefineActivity.class.getSimpleName();

    // view references
    private ViewFlipper mViewFlipper;
    private TextView mCommunityView;
    private TextView mMuseumView;
    private View mCommunityOverlay;
    private View mMuseumOverlay;
    private SeekBar mSeekBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refine);


    }


    @Override
    protected void onPause() {
        super.onPause();
    }


    @Override
    protected void onResume() {
        super.onResume();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }
}
