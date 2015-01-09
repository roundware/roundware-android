package org.roundware.service;


import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Buffer input stream that pulls out icy meta data.
 * Adapted from shoutcast protocol indicated at:
 * http://www.smackfu.com/stuff/programming/shoutcast.html
 */
public class RWIcecastInputStream extends BufferedInputStream {
    private int metadataInterval;
    private int streamBytesRemaining;
    private IcyMetaDataListener listener = null;

    public RWIcecastInputStream(InputStream in, int size, int metadataInterval) {
        super(in, size);
        this.metadataInterval = streamBytesRemaining = metadataInterval;
    }

    public void setIcyMetaDataListener(IcyMetaDataListener listener){
        this.listener = listener;
    }

    @Override
    public synchronized int read() throws IOException {
        if (streamBytesRemaining == 0) {
            examineMetaData();
        }
        streamBytesRemaining--;
        return super.read();
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        return super.read(buffer, 0, buffer.length);
    }

    @Override
    public synchronized int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
        int bytesRead = 0;
        if(0 == streamBytesRemaining){
            examineMetaData();
        }
        bytesRead = super.read(buffer, byteOffset, Math.min(streamBytesRemaining, byteCount) );
        streamBytesRemaining = streamBytesRemaining - bytesRead;

        return bytesRead;
    }

    private void examineMetaData() throws IOException{
        streamBytesRemaining = this.metadataInterval;
        int len = super.read() * 16;
        if(len > 0){
            byte buffer[] = new byte[len];
            super.read(buffer, 0, len);
            String metadata = new String(buffer, "UTF-8");

            Log.v(this.getClass().getName(), "metadata: " + metadata);

            // notify listener
            if(listener != null){
               listener.OnMetaDataReceived(metadata);
            }
        }
    }


    /**
     * Splits a metadata value.
     *
     * Often a metadata value is formated like so: "Aritst - Title"
     * @param meta
     * @return
     */
    public static String[] splitMetaValue(String meta){
        return meta.split("\\s+-\\s+");
    }

    /**
     * Parse meta data into mapped name/value pairs
     * http://uniqueculture.net/2010/11/stream-metadata-plain-java/
     * @param metaString
     * @return
     */
    public static Map<String, String> parseMetadata(String metaString) {
        Map<String, String> metadata = new HashMap<String, String>();
        String[] metaParts = metaString.split(";");
        Pattern p = Pattern.compile("^([a-zA-Z]+)=\\'([^\\']*)\\'$");
        Matcher m;
        for (int i = 0; i < metaParts.length; i++) {
            m = p.matcher(metaParts[i]);
            if (m.find()) {
                metadata.put(m.group(1), m.group(2));
            }
        }

        return metadata;
    }

    public interface IcyMetaDataListener{
        public void OnMetaDataReceived(String metaData);
    }

}
