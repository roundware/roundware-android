# ROUNDWARE ANDROID FRAMEWORK

Use this framework when building Android apps that use the Roundware participatory, location-sensitive audio platform (see www.roundware.org).

Roundware is a flexible, distributed framework which collects, stores, organizes and re-presents audio content. Basically, it lets you collect audio from anyone with a smartphone or web access, upload it to a central repository along with its metadata and then filter it and play it back collectively in continuous audio streams.

This guide provides the elemental code snippets for instantiating RWFramework in an Android project and making core RW API calls to the framework.

## Android project setup
Basic hardware requirements in AndroidManifest.xml (see file for complete list)

```
<uses-feature android:name="android.hardware.location" android:required="false" />
<uses-feature android:name="android.hardware.microphone" android:required="false" />
```

Basic user permissions in AndroidManifest.xml (see file for complete list)

```
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
```
```rwframework.jar``` added to project as library

include in the build path of the project

* httpmime-4.0.1.jar
* apache-mime4j-0.6.jar
* commons-io-1.4.jar

Setup project over-writeable default values in ```rwconfig.xml```

```
<string name="rw_spec_server_url">http://rw.roundware.org/roundware/</string>
<string name="rw_spec_project_id">1</string>
<string name="rw_spec_heartbeat_interval_in_sec">30</string>
<string name="rw_spec_max_recording_time_in_sec">45</string>
<string name="rw_spec_min_location_update_distance_meters">3</string>
```
## Setup Roundware service and connections
*(see RWExampleActivity.java and RWService.java)*

Make connection to rwframework background service

```
private ServiceConnection rwServiceConnection = new ServiceConnection() {
	@Override
	public void onServiceConnected(ComponentName className, IBinder service) {
		// called when the connection is made
		rwServiceBinder = ((RWService.RWServiceBinder) service).getService();
		updateServerForPreferences();
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		// received when the service unexpectedly disconnects
		rwServiceBinder = null;
		setConnected(false);
	}
};
```
Setup handler for RWFramework service events

```
private BroadcastReceiver connectedStateReceiver = new BroadcastReceiver() {
	@Override
	public void onReceive(Context context, Intent intent) {
		if (RWService.ACTION_IS_CONNECTED.equals(intent.getAction())) {
			// connection to server has been established
			setConnected(true);
			updateServerForPreferences();
			updateCurrentVersion();
		} else if (RWService.ACTION_IS_DISCONNECTED.equals(intent.getAction())) {
			// connection to server has been lost
			setConnected(false);
		} else if (RWService.ACTION_HEARTBEAT_SEND.equals(intent.getAction())) {
			Toast.makeText(context, "ping", Toast.LENGTH_SHORT).show();
		} else if (RWService.ACTION_MESSAGE.equals(intent.getAction())) {
			// message to user from the server - display in simple dialog
			String msg = intent.getStringExtra(RWService.INTENT_EXTRA_SERVER_MESSAGE);
			showMessageDialog(msg, false);
		} else if (RWService.ACTION_ERROR.equals(intent.getAction())) {
			// error message from server - log and update state
			String msg = intent.getStringExtra(RWService.INTENT_EXTRA_SERVER_MESSAGE);
			Log.e(TAG, msg);
			showMessageDialog(msg, true);
		}
	}
};
```
Start RW service example

```
// create connection to the RW service
Intent bindIntent = new Intent(RWExampleActivity.this, RWService.class);
bindService(bindIntent, rwServiceConnection, Context.BIND_AUTO_CREATE);

// create the intent to start the RW service
rwService = new Intent(this, RWService.class);
rwService.putExtra(RWService.INTENT_EXTRA_PROJECT_ID, projectId);

// start the service
startService(rwService);
```
Example of making asynchronous call to RW service - in this case to retrieve the current number of recordings available and display the results on the screen

```
private class RetrieveCurrentVersionTask extends AsyncTask<Void, Void, String> {
	public RetrieveCurrentVersionTask() {
		TextView tv = (TextView) findViewById(R.id.header_line1_textview);
		tv.setText("Updating...");
	}
	@Override
	protected String doInBackground(Void... params) {
		if (rwServiceBinder != null) {
			return rwServiceBinder.performCurrentVersion(true);
		} else {
			return null;
		}
	}
	@Override
	protected void onPostExecute(String result) {
		super.onPostExecute(result);
		TextView tv1 = (TextView) findViewById(R.id.header_line1_textview);
		if (result == null) {
			tv1.setText("Not Connected");
		} else {
		tv1.setText(String.format(getString(R.string.current_version_STRING),result));
		}
		TextView tv2 = (TextView) findViewById(R.id.header_line2_textview);
		RWConfiguration config = rwServiceBinder.getConfiguration();
		if (config != null) {
			tv2.setText(config.getProjectName());
		} else {
			tv1.setText("Unknown Project");
		}
	}
}
}
```
## Roundware Listen functions
*(see RWListenActivity.java)*

Instantiate Record button to make recording

```
final Button recordButton = (Button) findViewById(R.id.speak_record_button);
recordButton.setOnClickListener(new View.OnClickListener() {
   	public void onClick(View v) {
   		if ((recordingTask != null) && (recordingTask.isRecording())) {
   		    recordingTask.stopRecording();
   		    recordButton.setText("Record");
   		    submitButton.setEnabled(true);
   		} else {
   			startRecording();
   			recordButton.setText("Stop");
   			submitButton.setEnabled(false);
   		}
   	}
});
```
Submit recording to RW server

```
RWAction action = rwServiceBinder.createUploadRecordingAction(tagsList, recordingFileName);
rwServiceBinder.perform(action, false);
```
