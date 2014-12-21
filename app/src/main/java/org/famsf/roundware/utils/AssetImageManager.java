package org.famsf.roundware.utils;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import com.halseyburgund.rwframework.core.RWTags;
import com.halseyburgund.rwframework.util.RWList;
import com.halseyburgund.rwframework.util.RWListItem;

import org.famsf.roundware.Settings;

import java.util.Iterator;

/**
 * Manage assets that have an associated image with them
 * Created by Matt on 12/20/2014.
 */
public class AssetImageManager {

    private final int INITIAL_SIZE = 32;
    public final static String PREFS_ARTWORK_PREFIX = "artwork_";
    private SparseArray<String> map = new SparseArray<String>(INITIAL_SIZE);


    public static boolean saveArtworkTags(SharedPreferences prefs, RWList list) {
        if (prefs != null) {
            SharedPreferences.Editor editor = prefs.edit();
            RWListArtworkIterator iterator = new RWListArtworkIterator(list);
            while(iterator.hasNext()){
                RWListItem item = iterator.next();
                RWTags.RWTag tag = item.getTag();
                editor.putString(makePrefsKey(item.getTagId()), tag.data);
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
            RWListItem item = iterator.next();
            RWTags.RWTag tag = item.getTag();
            map.put(item.getTagId(), tag.data);
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
    public String getData(int tagId){
        String out = map.get(tagId);
        if(TextUtils.isEmpty(out)){
            // fall back on SharedPreferences cache
            out = Settings.getSharedPreferences().getString(makePrefsKey(tagId), null);
        }
        return out;
    }

    private static class RWListArtworkIterator implements Iterator<RWListItem>{

        private int pos = 0;
        private RWList list;

        RWListArtworkIterator( RWList list ){
            this.list = list;
        }

        @Override
        public boolean hasNext() {
            while(pos < list.size()){
                if(list.get(pos).getTag().isArtworkTag()){
                    return true;
                }
                pos++;
            }
            return false;
        }

        @Override
        public RWListItem next() {
            if(hasNext()){
                int p = pos;
                pos++;
                return list.get(p);
            }
            return null;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

}
