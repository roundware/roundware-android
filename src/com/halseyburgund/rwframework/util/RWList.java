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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.util.Log;

import com.halseyburgund.rwframework.core.RWTags;
import com.halseyburgund.rwframework.core.RWTags.RWOption;
import com.halseyburgund.rwframework.core.RWTags.RWTag;


/**
 * The RWList contains all the selectable options for all tags. It is a
 * convenience mechanism to pass this tag data to views and to the action
 * factory.
 *  
 * @author Rob Knapen
 */
public class RWList extends ArrayList<RWListItem> {

	// serializable UID of this implementation version
    private final static long serialVersionUID = 1L;

    // debugging
    private final static String TAG = "RWList";
    private final static boolean D = true;

	// json parsing error message
	private final static String JSON_SYNTAX_ERROR_MESSAGE = "Invalid JSON data!";
    
    // fields
    private RWTags mTags;
    private int mMinSelectionRequired = 1;
    private int mMaxSelectionAllowed = 1;
    
    
    /**
     * Creates an empty instance.
     */
    public RWList() {
    	super();
    	mTags = new RWTags();
    }
    
    
    /**
     * Creates an instance based on the specified tags.
     * 
     * @param tags to include in the RWList
     */
    public RWList(RWTags tags) {
    	super();
    	initFromTags(tags);
    }
    
    
    /**
     * Initializes the RWList instance from the specified tags. Any previous
     * content will be erased, and new RWListItem instances added based on
     * the tags. It will also set the information about the minimum required
     * number of selected items, and the maximum allowed.
     * 
     * @param tags to initialize the list with
     */
    public void initFromTags(RWTags tags) {
    	mTags = new RWTags();
    	clear();
    	if (tags != null) {
    		mTags.fromJson(tags.toJsonString(), tags.getDataSource());
    		for (RWTag tag : tags.getTags()) {
    			if (tag.options != null) {
    				// assume options are already in the right order
    				for (RWOption option : tag.options) {
    					add(RWListItem.create(tag, option.tagId, option.value, option.selectByDefault));
    				}
    			}
    		}
    	}

    	if ((tags != null) && (tags.getTags().size() == 1)) {
	    	mMinSelectionRequired = tags.getTags().get(0).getMinSelectedOptions();
	    	mMaxSelectionAllowed = tags.getTags().get(0).getMaxSelectedOptions();
    	} else {
	    	mMinSelectionRequired = 0;
	    	mMaxSelectionAllowed = this.size();
    	}
    }
    
    
	public String toJsonForWebView(String type) {
		if (mTags != null) {
			// create json from tags (with original defaults)
			JSONObject root = mTags.toJson();
			
			// overwrite defaults with current selected options
			try {
				JSONArray entries = root.getJSONArray(type);
				if (entries != null) {
			        for (int i = 0; i < entries.length(); i++) {
			        	JSONObject jsonObj = entries.getJSONObject(i);
			        	
			        	String tagCode = jsonObj.optString(RWTags.JSON_KEY_TAG_CODE);
			        	// String tagSelect = jsonObj.getString(RWTags.JSON_KEY_TAG_SELECTION_TYPE);
	
						JSONArray newDefaults = new JSONArray();
				
						for (RWListItem item : this) {
							RWTag tag = item.getTag();
							if (tag.code.equals(tagCode) && item.isOn()) {
								newDefaults.put(item.getTagId());
							}
						}
						
						jsonObj.put(RWTags.JSON_KEY_TAG_DEFAULT_OPTIONS, newDefaults);
					}
				}
				return "Roundware.tags = " + root.toString() + ";";
			} catch (JSONException e) {
				Log.e(TAG, JSON_SYNTAX_ERROR_MESSAGE + " - " + e.getMessage());
			}
		}

		return "Roundware.tags = {}";
	}
	
	
	public void setSelectionFromWebViewMessageUri(Uri webViewMessageUri) {
		// format: roundware://project?demographic=35,36&question=38,40
		String query = webViewMessageUri.getQuery(); // everything after ? to #
		if ((query != null) && (query.length() > 0)) {
			String[] parameters = query.split("&");
			for (String parameter : parameters) {
				String parameterName = parameter.substring(0, parameter.lastIndexOf("="));
				String parameterValues = parameter.substring(parameter.lastIndexOf("=") + 1);
				Log.d(TAG, "Parameter name: " + parameterName + " values: " + parameterValues);
				String selectedIds[] = parameterValues.split(",");
				
				for (RWListItem item : this) {
					item.setOff();
					RWTag tag = item.getTag();
					String tagId = String.valueOf(item.getTagId());
					if (tag.code.equals(parameterName)) {
						for (String id : selectedIds) {
							if (tagId.equals(id)) {
								item.setOn();
								break;
							}
						}
					}
				}
			}
		}
	}

    
    /**
     * Creates a new RWList instance by filtering the items in the list on
     * the specified tag. The RWListItems are shared between the lists, no
     * copies are created!
     * 
     * @param tag to filter on
     * @return RWList with shared RWListItems matching the specified tag
     */
    public RWList filter(RWTag tag) {
        RWList result = new RWList();
        if (tag != null) {
            for (RWListItem item : this) {
                if (tag.equals(item.getTag())) {
                    result.add(item);
                }
            }
            result.mMinSelectionRequired = tag.getMinSelectedOptions();
            result.mMaxSelectionAllowed = tag.getMaxSelectedOptions();
        }
        return result;
    }


    /**
     * Creates a new RWList instance with new RWListItems for all the
     * items in the list that match the specified tag.
     * 
     * @param tag to filter on
     * @return RWList with new RWListItems matching the specified tag
     */
    public RWList createSublist(RWTag tag) {
        RWList result = new RWList();
        if (tag != null) {
            for (RWListItem item : this) {
                if (tag.equals(item.getTag())) {
                    result.add(RWListItem.create(item));
                }
            }
            result.mMinSelectionRequired = tag.getMinSelectedOptions();
            result.mMaxSelectionAllowed = tag.getMaxSelectedOptions();
        }
        return result;
    }


    /**
     * Removes all items from the list that match the specified tag.
     * 
     * @param tag to remove list items for, null will clear all
     */
    public void removeAll(RWTag tag) {
        if (tag == null) {
            this.clear();
        } else {
            Iterator<RWListItem> iterator = this.iterator();
            while (iterator.hasNext()) {
                RWListItem item = iterator.next();
                if (tag.equals(item.getTag())) {
                    iterator.remove();
                }
            }
        }
    }


    /**
     * Deselects all items. Note that this does not respect the minimum
     * selection size requirement.
     * 
     * @return self, with all items deselected
     */
    public RWList clearSelection() {
        for (RWListItem item : this) {
            item.setOff();
        }
        return this;
    }


    /**
     * Deselects all items that match the specified tag. Note that this does
     * not respect the minimum selection size requirement.
     * 
     * @param tag to deselect items for
     * @return self, with all items for the give tag deselected
     */
    public RWList clearSelection(RWTag tag) {
        if (tag == null) {
            clearSelection();
        } else {
            for (RWListItem item : this) {
                if (tag.equals(item.getTag())) {
                    item.setOff();
                }
            }
        }
        return this;
    }

    
    /**
     * Returns the number of selected items in the list for all tags.
     * 
     * @return number of selected items
     */
    public int getSelectedCount() {
    	int result = 0;
    	for (RWListItem item : this) {
    		if (item.isOn()) {
    			result++;
    		}
    	}
    	return result;
    }

    
    /**
     * Returns the number of selected items in the list for a given tag.
     * 
     * @param tag to count selected items for
     * @return number of selected items
     */
    public int getSelectedCount(RWTag tag) {
    	int result = 0;
    	for (RWListItem item : this) {
            if (tag.equals(item.getTag())) {
	    		if (item.isOn()) {
	    			result++;
	    		}
            }
    	}
    	return result;
    }
    
    
    /**
     * Returns true if the list is a single select list, i.e. there must be
     * only one item selected at all times.
     * 
     * @return true if the list is single select
     */
    public boolean isSingleSelect(RWTag tag) {
    	RWList sublist = filter(tag);
    	return ((sublist.mMinSelectionRequired == 1) && (sublist.mMaxSelectionAllowed == 1));
    }
    
    
    /**
     * Deselects the first item found in the list that is selected. Used
     * internally to allow selection of a new item, when the maximum number
     * of allowed selected items is reached.
     */
    private void deselectFirstSelected(RWTag tag) {
    	RWList sublist = filter(tag);
    	for (RWListItem item : sublist) {
    		if (item.isOn()) {
    			item.setOff();
    			return;
    		}
    	}
    }
    
    
    /**
     * Sets the specified item to selected, if allowed by the mode and
     * selection requirements of the list. This method will not allow
     * exceeding the set maximum number of selected items, and if the
     * list is single select automatically switch the selected items.
     * 
     * @param item to be selected
     * @return true if the item is set to selected
     */
    public boolean select(RWListItem item) {
    	RWTag tag = item.getTag();
    	RWList sublist = filter(tag);
    	if ((item != null) && (!item.isOn())) {
	    	// only allow if does not break max selections
	    	if (sublist.getSelectedCount(tag) >= sublist.mMaxSelectionAllowed) {
	    		if (sublist.isSingleSelect(tag)) {
	    			// for single select auto switch the selection
	    			sublist.deselectFirstSelected(tag);
	    			item.setOn();
	    			return true;
	    		}
	    	} else {
	    		item.setOn();
	    		return true;
	    	}
    	}
    	return false;
    }
    
    
    /**
     * Sets the specified item to not selected, if allowed by the mode and
     * selection requirements of the list. This method will not allow breaking
     * the set minimum required selected items.
     * 
     * @param item to set to not selected
     * @return true if the item is set to not selected
     */
    public boolean deselect(RWListItem item) {
    	RWTag tag = item.getTag();
    	if ((item != null) && (item.isOn())) {
    		// only allow if does not break min selections
    		if (getSelectedCount(tag) > mMinSelectionRequired) {
    			item.setOff();
    			return true;
    		}
    	}
    	return false;
    }
    
    
    /**
     * Creates a new RWList with all the selected items.
     * 
     * @return RWList with the selected RWListItems
     */
    public RWList getSelectedItems() {
    	RWList result = new RWList();
    	for (RWListItem item : this) {
    		if (item.isOn()) {
    			result.add(item);
    		}
    	}
    	return result;
    }

    
    /**
     * Creates a new RWList with all the selected items.
     * 
     * @param tag to get all selected items for
     * @return RWList with the selected RWListItems
     */
    public RWList getSelectedItems(RWTag tag) {
    	RWList result = new RWList();
    	for (RWListItem item : this) {
    		if ((tag.equals(item.getTag())) && (item.isOn())) {
    			result.add(item);
    		}
    	}
    	return result;
    }
    
    
    /**
     * Forces all items in the list to be set to selected. This might break
     * the set minimum and maximum requirements for the selection.
     * 
     * @return Self, with all items set to selected
     */
    public RWList selectAll() {
        for (RWListItem item : this) {
            item.setOn();
        }
        return this;
    }


    /**
     * Forces all items in the list matching the specified tag, to be set to 
     * selected. This might break the set minimum and maximum requirements
     * for the selection.
     * 
     * @param tag to set all matching items to selected for
     * @return Self, with all items matching the tag set to selected
     */
    public RWList selectAll(RWTag tag) {
        if (tag == null) {
            selectAll();
        } else {
            for (RWListItem item : this) {
                if (tag.equals(item.getTag())) {
                    item.setOn();
                }
            }
        }
        return this;
    }


    /**
     * For each of the specified tags check if there is only one option
     * available in the list and if so set it to selected.
     * 
     * @param tags to auto select single options for
     * @return Self, with single option tags selected
     */
    public RWList autoSelectSingleItemForTags(List<RWTag> tags) {
        for (RWTag tag : tags) {
            autoSelectSingleItemForTag(tag);
        }
        return this;
    }


    /**
     * For the specified tag check if there is only one option available
     * in the list and if so set it to selected.
     * 
     * @param tag to auto select single options for
     * @return Self, with single option tags selected
     */
    public RWList autoSelectSingleItemForTag(RWTag tag) {
        RWList tagItems = new RWList();
        if (tag != null) {
            for (RWListItem item : this) {
                if (tag.equals(item.getTag())) {
                    tagItems.add(item);
                }
            }
            if (tagItems.size() == 1) {
                tagItems.get(0).setOn();
            }
        }
        return this;
    }

    
    /**
     * Returns a list of all RWTags currently references by all the items
     * (RWListItem) in the list.
     * 
     * @return list of all RWTag instances referenced
     */
    public RWTag[] getAllTags() {
    	List<RWTag> result = new ArrayList<RWTag>();
    	for (RWListItem item : this) {
    		if (!result.contains(item.getTag())) {
    			result.add(item.getTag());
    		}
    	}
    	return result.toArray(new RWTag[]{});
    }
    
    
    /**
     * Checks for the specified tags if the list contains valid selections,
     * i.e. for each tag the number of selected items is no less than the
     * minimum number of required selected items, and not more than the
     * maximum number of allowed selected items.
     * 
     * @param tags to validate selections for
     * @return true if for each of the tags there is a valid selection
     */
    public boolean hasValidSelectionsForTags(RWTag... tags) {
    	for (RWTag tag : tags) {
    		RWList sublist = filter(tag);
    		int selected = sublist.getSelectedCount();
    		if ((selected < sublist.mMinSelectionRequired) || (selected > sublist.mMaxSelectionAllowed)) {
    			if (D) { Log.d(TAG, "Invalid selection for tag " + tag + " " + sublist.mMinSelectionRequired + " < " + selected + " < " + sublist.mMaxSelectionAllowed); }
    			return false;
    		}
    	}
    	return true;
    }

    
    /**
     * Checks for all tags reference in the list if there are valid
     * selections, i.e. for each tag the number of selected items is no
     * less than the minimum number of required selected items, and not
     * more than the maximum of allowed selected items.
     * 
     * @return true if the list has valid selections for all tags
     */
    public boolean hasValidSelectionsForTags() {
    	return hasValidSelectionsForTags(getAllTags());
    }
    

    /**
     * Saves the current selection state of the list to the specified shared
     * preferences.
     * 
     * @param prefs to store list selection state in
     * @return true when successful
     */
    public boolean saveSelectionState(SharedPreferences prefs) {
    	if (prefs != null) {
    		Editor editor = prefs.edit();
    		for (RWListItem item : this) {
    			String key = item.getTag().type + "_" + item.getTag().code + "_" + item.getTagId();
    			editor.putBoolean(key, item.isOn());
    		}
    		editor.commit();
    		return true;
    	}
    	return false;
    }
    
    
    /**
     * Restores the selection state of the list from the specified shared
     * preferences.
     * 
     * @param prefs to restore list selection state from
     * @return true when successful
     */
    public boolean restoreSelectionState(SharedPreferences prefs) {
    	if (prefs != null) {
    		for (RWListItem item : this) {
    			String key = item.getTag().type + "_" + item.getTag().code + "_" + item.getTagId();
    			item.set(prefs.getBoolean(key, item.isOn()));
    		}
    		return true;
    	}
    	return false;
    }


	public int getMinSelectionRequired() {
		return mMinSelectionRequired;
	}


	public void setMinSelectionRequired(int minSelectionRequired) {
		mMinSelectionRequired = minSelectionRequired;
	}


	public int getMaxSelectionAllowed() {
		return mMaxSelectionAllowed;
	}


	public void setMaxSelectionAllowed(int maxSelectionAllowed) {
		mMaxSelectionAllowed = maxSelectionAllowed;
	}
    
}
