package org.roundware.service.util;

import android.net.Uri;
import android.text.TextUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A little Uri convenience
 * Created by Matt on 12/23/2014.
 */
public class RWUriHelper {

    /**
     * Placates Uri by adding a question mark if needed, then drives a Uri.parse
     *
     * @param queryUri
     * @return
     */
    public static Uri parse(String queryUri) {
        if (!queryUri.contains("?")) {
            // Uri requires the question mark to be considered a query
            queryUri = "?" + queryUri;
        }
        return Uri.parse(queryUri);
    }

    /**
     * @param uri
     * @param key
     * @return all query parameters and it's deliminated values, Url-decoded
     */
    public static List<String> getQueryParameterValues(Uri uri, String key) {

        List<String> params = getQueryParameters(uri, key);
        ArrayList<String> out = new ArrayList<String>(2 * params.size());
        for (String param : params) {
            if (!TextUtils.isEmpty(param)) {
                String tags[] = param.split(",|;|:|\\+");
                Collections.addAll(out, tags);
            }
        }
        return out;
    }

    /**
     *
     * @param uri
     * @param key
     * @return parameter URL-decoded
     */
    public static String getQueryParameter(Uri uri, String key) {
        return decode( uri.getQueryParameter(key) );
    }

    /**
     *
     * @param uri
     * @param key
     * @return parameters URL-decoded   
     */
    public static List<String> getQueryParameters(Uri uri, String key){
        List<String> params = uri.getQueryParameters(key);
        ArrayList<String> out = new ArrayList<String>(params.size());
        for(String param : params){
            out.add(decode(param));
        }
        return out;
    }


    private static String decode(String encoded){
        if (!TextUtils.isEmpty(encoded)) {
            try {
                return URLDecoder.decode(encoded, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}