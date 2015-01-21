/**
 * Roundware Android code is released under the terms of the GNU General Public License.
 * See COPYRIGHT.txt, AUTHORS.txt, and LICENSE.txt in the project root directory for details.
 */
package org.roundware.service.util;

import org.roundware.service.RWTags.RWTag;

/**
 * A selectable item to be used in a RWList.
 * 
 * @author Rob Knapen
 */
public class RWListItem {

    // fields
    private RWTag mTag;
    private String mText;
    private boolean mOn;
    private int mTagId;


    /**
     * Creates a new list item with the specified parameters.
     * 
     * @param tag the list item is for
     * @param tagId of the tag option represented by the list item
     * @param text to display for the list item
     * @param on if the item is selected
     * @return the created RWListItem
     */
    public static RWListItem create(RWTag tag, int tagId, String text, boolean on) {
        RWListItem result = new RWListItem(tag, tagId, text);
        result.set(on);
        return result;
    }


    /**
     * Creates a new list item with values based on the specified template.
     * 
     * @param template to copy values from
     * @return the created RWListItem
     */
    public static RWListItem create(RWListItem template) {
        RWListItem result = new RWListItem(template.getTag(), template.getTagId(), template.getText());
        result.set(template.isOn());
        return result;
    }


    /**
     * Creates a new list item with the specified parameters. The initial
     * tagId will be set to -1 and the selected state to false (off).
     * 
     * @param tag the list item is for
     * @param text to display for the list item
     * @return the created RWListItem
     */
    public static RWListItem create(RWTag tag, String text) {
        return create(tag, -1, text, false);
    }


    /**
     * Creates an instance with the specified parameters. The initial
     * selected state will be set to false (off).
     * 
     * @param tag the list item is for
     * @param tagId of the tag option represented by the list item
     * @param text to display for the list item
     */
    public RWListItem(RWTag tag, int tagId, String text) {
        mTag = tag;
        mTagId = tagId;
        mText = text;
        mOn = false;
    }


    public RWTag getTag() {
        return mTag;
    }


    public int getTagId() {
        return mTagId;
    }


    public String getText() {
        return mText;
    }


    public boolean isOn() {
        return mOn;
    }


    public void setOn() {
        mOn = true;
    }


    public void setOff() {
        mOn = false;
    }


    public void set(boolean val) {
        if (val)
            setOn();
        else
            setOff();
    }

}
