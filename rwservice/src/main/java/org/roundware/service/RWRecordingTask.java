/**
 * Roundware Android code is released under the terms of the GNU General Public License.
 * See COPYRIGHT.txt, AUTHORS.txt, and LICENSE.txt in the project root directory for details.
 */
package org.roundware.service;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Asynchronous task that handles creating an audio recording, suitable for
 * use with Roundware. Recording runs in the background, and a callback
 * interface (StateListener) is provided to be able to e.g. update the UI
 * and keep the app responding to input.
 * 
 * @author Rob Knapen
 */
public class RWRecordingTask extends AsyncTask<Void, Void, String> {

    // debugging
    private final static String TAG = "RWRecordingTask";
    private final static boolean D = false;

    // settings for creating an audio recording
    private static final String RECORDING_FILE_NAME = "rwaudio.wav";
    private static final int RECORDING_EVENT_INTERVAL_MSEC = 100; // 0.1 sec between updates
    private static final int RECORDING_SAMPLE_RATE = 22050; // 44100, 22050, 11025
    private static final int EMULATOR_SAMPLE_RATE = 8000; // leave at 8K, currently something else crashes the app in the emulator

    // fields
    private RWService mRwServiceBinder;
    private int mSampleRate = RECORDING_SAMPLE_RATE;
    private boolean mIsRecording = false;
    private String mTempDirName = null;
    private String mScratchFileName = null;
    private long mLastRecordingEventMsec = 0;
    private StateListener mListener;


    /**
     * Listener interface for callbacks during recording
     * 
     * @author Rob Knapen
     */
    public interface StateListener {
        /**
         * Callback when audio recording has started. Methods handling the
         * callback should not perform lengthy tasks and make sure that UI
         * updating is done in the UI thread, since the callback will be
         * made from a background thread.
         *
         * @param timeStampMsec of start of recording
         */
        public void recordingStarted(long timeStampMsec);

        /**
         * Callback made at regular intervals during audio recording.
         * The interval is defined with RECORDING_EVENT_INTERVAL_MSEC, this
         * should not be too frequent and processing of the callback not too
         * lengthy to prevent the device from become unresponsive. It can
         * be used to update a UI, in this case make sure the updating is
         * done in the UI thread since the callback is made from a background
         * task.
         *
         * @param timeStampMsec of recording progress
         * @param samples latest 10 audio samples made
         */
        public void recording(long timeStampMsec, short [] samples);

        /**
         * Callback when audio recording has ended. Methods handling the
         * callback should not perform lengthy tasks and make sure that UI
         * updating is done in the UI thread, since the callback will be
         * made from a background thread.
         *
         * @param timeStampMsec of end of recording
         */
        public void recordingStopped(long timeStampMsec);
    }


    /**
     * Creates an instance of the recording task with the specified parameters.
     * 
     * @param rwServiceBinder RWService to be used
     * @param tempDirName for creating the audio recording file
     * @param listener to use for callbacks
     */
    public RWRecordingTask(RWService rwServiceBinder, String tempDirName, StateListener listener) {
        mRwServiceBinder = rwServiceBinder;
        mListener = listener;

        if ((tempDirName == null) || (!tempDirName.endsWith(File.separator))) {
            mTempDirName = tempDirName + File.separator;
        } else {
            mTempDirName = tempDirName;
        }
        mScratchFileName = mTempDirName + RECORDING_FILE_NAME;

        // ensure scratch folder exists
        File scratch = new File(mTempDirName);
        if (!scratch.exists()) {
            scratch.mkdirs();
        }
    }


    /**
     * Checks if audio recording is in progress.
     *
     * @return true when recording
     */
    public boolean isRecording() {
        return mIsRecording;
    }

    
    /**
     * Gets the name of the file with the audio recording, if available.
     * 
     * @return filename of the created recording
     */
    public String getRecordingFileName() {
        return mScratchFileName;
    }

    
    /**
     * Resets the recording task and removes any previous recording file
     * made (there can be only one at the same time).
     */
    public synchronized void resetRecording() {
        mIsRecording = false;
        File file = new File(mScratchFileName);
        if (file.exists()) {
            file.delete();
        }
    }
    

    /**
     * Stops the audio recording and will cause the audio recording file to
     * be written.
     */
    public synchronized void stopRecording() {
        // send non critical notification to server when possible
        if (mRwServiceBinder != null) {
            mRwServiceBinder.rwSendLogEvent(R.string.rw_et_stop_record, null, null, true);
        } else {
            if (D) { Log.d(TAG, "RWServiceBinder is null, can not send log event: stop record"); }
        }
        mIsRecording = false;
    }


    /**
     * Making an audio recording in a background thread.
     */
    @Override
    protected String doInBackground(Void... params) {
        // send non critical notification to server when possible
        if (mRwServiceBinder != null) {
            mRwServiceBinder.rwSendLogEvent(R.string.rw_et_start_record, null, null, true);
        } else {
            if (D) { Log.d(TAG, "RWServiceBinder is null, can not send log event: start record"); }
        }
        mIsRecording = true;

        int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
        int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;

        if ("google_sdk".equalsIgnoreCase(Build.MODEL) || "sdk".equalsIgnoreCase(Build.MODEL)) {
            mSampleRate = EMULATOR_SAMPLE_RATE;
        } else {
            mSampleRate = RECORDING_SAMPLE_RATE;
        }

        // We're important...
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

        // Allocate Recorder and Start Recording...
        int minBufferSize = AudioRecord.getMinBufferSize(mSampleRate, channelConfiguration, audioEncoding);
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Bad value, could not create audio buffer!");
            return null;
        } else if (minBufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "System Error, could not create audio buffer!");
            return null;
        }

        int bufferSize = 2 * minBufferSize;
        AudioRecord recordInstance = new AudioRecord(MediaRecorder.AudioSource.MIC, mSampleRate, channelConfiguration, audioEncoding, bufferSize);

        byte[] data = new byte[bufferSize];
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        short[] samples = new short[10];
        int offset;

        recordInstance.startRecording();
        if (mListener != null) {
            long currentMillis = System.currentTimeMillis();
            mListener.recordingStarted(currentMillis);
        }

        try  {
            while (mIsRecording) {
                recordInstance.read(data, 0, bufferSize);
                bytesOut.write(data);

                offset = 0;
                for(int i = 0; i < 10; i++) {
                    samples[i] = (short) (((data[offset+1] << 8)) | ((data[offset] & 0xff)));
                    offset += 2;
                }

                if (mListener != null) {
                    long currentMillis = System.currentTimeMillis();
                    if ((currentMillis - mLastRecordingEventMsec) > RECORDING_EVENT_INTERVAL_MSEC) {
                        mLastRecordingEventMsec = currentMillis;
                        mListener.recording(currentMillis, samples);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        } catch (OutOfMemoryError om) {
            Log.e(TAG, "Record - Out of memory", om);
        }

        recordInstance.stop();
        recordInstance.release();
        save(mTempDirName + RECORDING_FILE_NAME, bytesOut);


        if (mListener != null) {
            long currentMillis = System.currentTimeMillis();
            mListener.recordingStopped(currentMillis);
        }

        return mTempDirName + RECORDING_FILE_NAME;
    }


    /**
     * Saves the supplied byte stream as a WAV file.
     *  @param name The desired filename
     * @param bytesOut
     */
    public void save(String name, ByteArrayOutputStream bytesOut) {
        File file = new File(name);
        if (file.exists()) {
            file.delete();
        }
        BufferedOutputStream fileOut;
        try{
            file.createNewFile();
            fileOut = new BufferedOutputStream(new FileOutputStream(file));
        } catch (IOException e) {
            Log.e(TAG, "Error saving WAV file: " + e.getMessage(), e);
            return;
        }

        try {
            byte[] header = createHeader(bytesOut.size());
            fileOut.write(header);
            bytesOut.writeTo(fileOut);
            fileOut.flush();

        } catch (Exception e) {
            Log.e(TAG, "Error saving WAV file: " + e.getMessage(), e);
        }finally {
            if(fileOut != null) {
                try {
                    fileOut.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error saving WAV file: " + e.getMessage(), e);
                }
            }
            System.gc();
        }
    }


    /**
     * Creates a valid WAV header for the given bytes, using the class-wide
     * sample rate.
     *
     * @param len The length of the sound data to be appraised
     * @return The header, ready to be written to a file
     */
    public byte[] createHeader(int len) {
        int totalLength = len + 4 + 24 + 8;
        byte[] lengthData = intToBytes(totalLength);
        byte[] samplesLength = intToBytes(len);
        byte[] bitRate = intToBytes(mSampleRate);
        byte[] bytesPerSecond = intToBytes(mSampleRate*2);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            out.write(new byte[] {'R','I','F','F'});
            out.write(lengthData);
            out.write(new byte[] {'W','A','V','E'});

            out.write(new byte[] {'f','m','t',' '});
            out.write(new byte[] {0x10,0x00,0x00,0x00}); // 16 bit chunks
            out.write(new byte[] {0x01,0x00,0x01,0x00}); // mono
            out.write(bitRate); // sampling rate
            out.write(bytesPerSecond); // bytes per second
            out.write(new byte[] {0x02,0x00,0x10,0x00}); // 2 bytes per sample

            out.write(new byte[] {'d','a','t','a'});
            out.write(samplesLength);
        } catch (IOException e) {
            Log.e(TAG, "Error saving WAV file: " + e.getMessage(), e);
        }

        return out.toByteArray();
    }


    /**
     * Turns an integer into its little-endian four-byte representation.
     *
     * @param in The integer to be converted
     * @return The bytes representing this integer
     */
    public byte[] intToBytes(int in) {
        byte[] bytes = new byte[4];
        for (int i=0; i<4; i++) {
            bytes[i] = (byte) ((in >>> i*8) & 0xFF);
        }
        return bytes;
    }


    /**
     * Post execute - currently nothing to be done here.
     */
    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
    }

}
