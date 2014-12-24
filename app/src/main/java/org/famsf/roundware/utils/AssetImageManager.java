package org.famsf.roundware.utils;

import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import com.halseyburgund.rwframework.core.RWTags;
import com.halseyburgund.rwframework.util.RWList;
import com.halseyburgund.rwframework.util.RWUriHelper;

import org.famsf.roundware.Settings;

import java.util.Iterator;

/**
 * Manage assets that have an associated image with them
 * Created by Matt on 12/20/2014.
 */
public class AssetImageManager {

    private final int INITIAL_SIZE = 32;
    private final static String URL_PARAMETER_NAME = "image";
    public final static String PREFS_ARTWORK_PREFIX = "artwork_";

    private SparseArray<String> map = new SparseArray<String>(INITIAL_SIZE);
    private final String hostUrl;

    public AssetImageManager(String hostUrl){
        this.hostUrl = hostUrl;
    }

    public static boolean saveArtworkTags(SharedPreferences prefs, RWList list) {
        if (prefs != null) {
            SharedPreferences.Editor editor = prefs.edit();
            RWListArtworkIterator iterator = new RWListArtworkIterator(list);
            while(iterator.hasNext()){
                RWTags.RWOption item = iterator.next();
                editor.putString(makePrefsKey(item.tagId), item.data);
            }
            editor.commit();
            return true;
        }
        return false;
    }

    private static String makePrefsKey(int tagId){
        return PREFS_ARTWORK_PREFIX + tagId;
    }

    public void addTags(RWList list){
        RWListArtworkIterator iterator = new RWListArtworkIterator(list);
        while(iterator.hasNext()) {
            RWTags.RWOption item = iterator.next();
            map.put(item.tagId, item.data);
        }
    }

    public void addTag(int id, String data){
        Log.i("AssetMgr", "Added faux url " + id + " " + data);
        map.put(id, data);
    }

    /**
     * Get data corresponding to given tagId
     * @param tagId
     * @return
     */
    private String getData(int tagId){
        String out = map.get(tagId);
        if(TextUtils.isEmpty(out)){
            // fall back on SharedPreferences cache
            out = Settings.getSharedPreferences().getString(makePrefsKey(tagId), null);
        }
        return out;
    }

    public String getImageUrl(int tagId){
        String data = getData(tagId);
        if(!TextUtils.isEmpty(data)){
            Uri uri = RWUriHelper.parse(data);

            String suffix = RWUriHelper.getQueryParameter(uri, URL_PARAMETER_NAME);
            if(!TextUtils.isEmpty(suffix)){
                if(suffix.startsWith("/")) {
                    return hostUrl + suffix;
                }else{
                    return hostUrl + "/" + suffix;
                }
            }
        }
        return null;
    }

    private static class RWListArtworkIterator implements Iterator<RWTags.RWOption>{

        private int tagPos = 0;
        private int optionPos = 0;
        private RWList list;

        RWListArtworkIterator( RWList list ){
            this.list = list;
        }

        @Override
        public boolean hasNext() {
            while(tagPos < list.size()){
                RWTags.RWTag tag = getTag(tagPos);
                if(tag.isPhysicalObjectTag()){
                    if( optionPos < tag.options.size() ) {
                        return true;
                    }
                }
                tagPos++;
                optionPos = 0;
            }
            return false;
        }

        @Override
        public RWTags.RWOption next() {
            if(hasNext()){
                RWTags.RWTag tag = getTag(tagPos);
                int p = optionPos;
                optionPos++;
                return tag.options.get(p);
            }
            return null;
        }

        private RWTags.RWTag getTag(int i){
            return list.get(i).getTag();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

}
