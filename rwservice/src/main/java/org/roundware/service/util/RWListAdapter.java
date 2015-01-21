/**
 * Roundware Android code is released under the terms of the GNU General Public License.
 * See COPYRIGHT.txt, AUTHORS.txt, and LICENSE.txt in the project root directory for details.
 */
package org.roundware.service.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.roundware.service.RWTags.RWTag;

/**
 * Customized adapter for displaying items from a RWList, filtered by a
 * specified category (a property of the RWListItem).
 *
 * @author Rob Knapen
 */
public class RWListAdapter extends BaseAdapter {

    private Context mContext;
    private RWList mAllItems;
    private RWTag mdisplayedTag;
    private RWList mDisplayedItems;
    private int mListItemLayoutId;


    public RWListAdapter(Context context, RWList items, RWTag tag, int listItemLayoutId) {
        super();
        mContext = context;
        mAllItems = items;
        mdisplayedTag = tag;
        mListItemLayoutId = listItemLayoutId;
        initDisplayedItems();
    }


    private void initDisplayedItems() {
        mDisplayedItems = mAllItems.filter(mdisplayedTag);
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = vi.inflate(mListItemLayoutId, null);
        }

        ImageView image = (ImageView) v.findViewById(android.R.id.icon);
        TextView text = (TextView) v.findViewById(android.R.id.text1);

        RWListItem q = getItem(position);
        if (q != null) {
            if (text != null) {
                text.setText(q.getText());
            }

            boolean selected = q.isOn();
            if (image != null) {
                image.setSelected(selected);
            }
            if (text != null) {
                text.setSelected(selected);
            }
        }

        return v;
    }


    @Override
    public int getCount() {
        if (mDisplayedItems != null) {
            return mDisplayedItems.size();
        } else {
            return 0;
        }
    }


    @Override
    public RWListItem getItem(int index) {
        return mDisplayedItems.get(index);
    }


    @Override
    public long getItemId(int index) {
        // using the order in the sublist as id (for now)
        return index;
    }


    public void clearSelection() {
        if (mDisplayedItems != null) {
            mDisplayedItems.clearSelection();
        }
    }


    public void selectAll() {
        if (mDisplayedItems != null) {
            mDisplayedItems.selectAll();
        }
    }
    
    
    public RWList getAllItems() {
        return mAllItems;
    }
    
    
    public RWList getDisplayedItems() {
        return mDisplayedItems;
    }
}
