/**
 * Roundware Android code is released under the terms of the GNU General Public License.
 * See COPYRIGHT.txt, AUTHORS.txt, and LICENSE.txt in the project root directory for details.
 */
package org.roundware.service;

import android.util.Log;

import org.roundware.service.util.RWMultipartEntity;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;


/**
 * General HTTP data transfer handler.
 */
public class RWHttpManager {

    // debugging
    private final static String TAG = "RWHttpManager";
    private final static boolean D = false;

    private final static String POST_MIME_TYPE = "application/x-www-form-urlencoded";

    
    public static String doGet(String page, Properties props, int timeOutSec) throws Exception {
        HttpParams httpParams = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParams, timeOutSec * 1000);
        HttpConnectionParams.setSoTimeout(httpParams, timeOutSec * 1000);

        HttpClient httpClient = new DefaultHttpClient(httpParams);

        StringBuilder uriBuilder = new StringBuilder(page);

        StringBuffer sbResponse = new StringBuffer();

        Enumeration<Object> enumProps = props.keys();
        String key, value = null;

        uriBuilder.append('?');

        while (enumProps.hasMoreElements()) {
            key = enumProps.nextElement().toString();
            value = props.get(key).toString();
            uriBuilder.append(key);
            uriBuilder.append('=');
            uriBuilder.append(java.net.URLEncoder.encode(value, "UTF-8"));
            if (enumProps.hasMoreElements()) {
                uriBuilder.append('&');
            }
        }

        if (D) { Log.d(TAG, "GET request: " + uriBuilder.toString(), null); }

        HttpGet request = new HttpGet(uriBuilder.toString());
        HttpResponse response = httpClient.execute(request);

        int status = response.getStatusLine().getStatusCode();

        // we assume that the response body contains the error message
        if (status != HttpStatus.SC_OK) {
            ByteArrayOutputStream ostream = new ByteArrayOutputStream();
            response.getEntity().writeTo(ostream);
            Log.e(TAG, "GET ERROR: " + ostream.toString(), null);
            throw new HttpException(String.valueOf(status));
        } else {
            InputStream content = response.getEntity().getContent();
            BufferedReader reader = new BufferedReader(new InputStreamReader(content));
            String line;
            while ((line = reader.readLine()) != null) {
                sbResponse.append(line);
            }
            content.close(); // this will also close the connection
        }

        if (D) { Log.d(TAG, "GET response: " + sbResponse.toString(), null); }

        return sbResponse.toString();
    }


    public static String doPost(String page, Properties props, int timeOutSec) throws Exception {
        HttpParams httpParams = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParams, timeOutSec * 1000);
        HttpConnectionParams.setSoTimeout(httpParams, timeOutSec * 1000);

        HttpClient httpClient = new DefaultHttpClient(httpParams);

        HttpPost request = new HttpPost(page);
        HttpResponse response;
        HttpEntity entity;

        StringBuffer sbResponse = new StringBuffer();

        Enumeration<Object> enumProps = props.keys();
        String key, value = null;

        List<NameValuePair> nvps = new ArrayList<NameValuePair>();

        while (enumProps.hasMoreElements()) {
            key = (String) enumProps.nextElement();
            value = (String) props.get(key);
            nvps.add(new BasicNameValuePair(key, value));
        }

        UrlEncodedFormEntity uf = new UrlEncodedFormEntity(nvps, HTTP.UTF_8);
        request.setEntity(uf);
        request.setHeader("Content-Type", POST_MIME_TYPE);

        // Post, check and show the result (not really spectacular, but works):
        response = httpClient.execute(request);
        entity = response.getEntity();

        int status = response.getStatusLine().getStatusCode();

        // we assume that the response body contains the error message
        if (status != HttpStatus.SC_OK) {
            ByteArrayOutputStream ostream = new ByteArrayOutputStream();
            entity.writeTo(ostream);

            Log.e(TAG, "Error status code = " + status, null);
            Log.e(TAG, ostream.toString(), null);
            throw new HttpException(String.valueOf(status));
        } else {
            InputStream content = response.getEntity().getContent();
            BufferedReader reader = new BufferedReader(new InputStreamReader (content));
            String line;

            while ((line = reader.readLine()) != null) {
                sbResponse.append(line);
            }
            content.close(); // this will also close the connection

            return sbResponse.toString();
        }
    }
    
    
    public static String uploadFile(String page, Properties properties, String fileParam, String file, int timeOutSec) throws Exception {
        if (D) { Log.d(TAG, "Starting upload of file: " + file, null); }

        // build GET-like page name that includes the RW operation
        Enumeration<Object> enumProps = properties.keys();
        StringBuilder uriBuilder = new StringBuilder(page).append('?');
        while (enumProps.hasMoreElements()) {
            String key = enumProps.nextElement().toString();
            String value = properties.get(key).toString();
            if ("operation".equals(key)) {
                uriBuilder.append(key);
                uriBuilder.append('=');
                uriBuilder.append(java.net.URLEncoder.encode(value, "UTF-8"));
                break;
            }
        }
        
        if (D) { Log.d(TAG, "GET request: " + uriBuilder.toString(), null); }

        HttpParams httpParams = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParams, timeOutSec * 1000);
        HttpConnectionParams.setSoTimeout(httpParams, timeOutSec * 1000);

        HttpClient httpClient = new DefaultHttpClient(httpParams);
        HttpPost request = new HttpPost(uriBuilder.toString());
        RWMultipartEntity entity = new RWMultipartEntity();

        Iterator<Map.Entry<Object, Object>> i = properties.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry<Object, Object> entry = (Map.Entry<Object, Object>) i.next();
            String key = (String) entry.getKey();
            String val = (String) entry.getValue();
            entity.addPart(key, val);
            if (D) { Log.d(TAG, "Added StringBody multipart for: '" + key + "' = '" + val + "'", null); }
        }

        File upload = new File(file);
        entity.addPart(fileParam, upload);
        if (D) {
            String msg = "Added FileBody multipart for: '" + fileParam + "' =" +
                    " <'" + upload.getAbsolutePath() + ", " +
                    "size: " + upload.length() + " bytes >'";
            Log.d(TAG, msg, null);
        }

        request.setEntity(entity);

        if (D) { Log.d(TAG, "Sending HTTP request...", null); }

        HttpResponse response = httpClient.execute(request);

        int st = response.getStatusLine().getStatusCode();

        if (st == HttpStatus.SC_OK) {
            StringBuffer sbResponse = new StringBuffer();
            InputStream content = response.getEntity().getContent();
            BufferedReader reader = new BufferedReader(new InputStreamReader(content));
            String line;
            while ((line = reader.readLine()) != null) {
                sbResponse.append(line);
            }
            content.close(); // this will also close the connection

            if (D) {
                Log.d(TAG, "Upload successful (HTTP code: " + st + ")", null);
                Log.d(TAG, "Server response: " + sbResponse.toString(), null);
            }

            return sbResponse.toString();
        } else {
            ByteArrayOutputStream ostream = new ByteArrayOutputStream();
            entity.writeTo(ostream);
            Log.e(TAG, "Upload failed (http code: " + st + ")", null);
            Log.e(TAG, "Server response: " + ostream.toString(), null);
            throw new HttpException(String.valueOf(st));
        }
    }
}
