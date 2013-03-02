/*
    ROUNDWARE
	a participatory, location-aware media platform
	Android client library
   	Copyright (C) 2008-2012 Halsey Solutions, LLC
	with contributions by Rob Knapen (shuffledbits.com) and Dan Latham
	http://roundware.org | contact@roundware.org

	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

 	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 	GNU General Public License for more details.

 	You should have received a copy of the GNU General Public License
 	along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/ 		
package com.halseyburgund.rwframework.util;

import com.halseyburgund.rwframework.core.RWTags.RWTag;

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
