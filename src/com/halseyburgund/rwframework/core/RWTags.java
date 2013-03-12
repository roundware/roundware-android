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
package com.halseyburgund.rwframework.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;


/**
 * Project tags data. Consists of a number of tags for the active modes
 * (listen, speak) and all types (questions, gender, age, etc.). Each
 * tag has a number of options, of which none, one or multiple can be
 * selected. The tag_id of selected options is supposed to be unique and
 * included in server calls.
 * 
 * @author Rob Knapen
 */
public class RWTags {

	// debugging
	private final static String TAG = "RWTags";
	private final static boolean D = true;

	// data source
	public final static int DEFAULTS = 0;
	public final static int FROM_CACHE = 1;
	public final static int FROM_SERVER = 2;
	
	// json names
	public final static String JSON_KEY_MODE_SPEAK = "speak";
	public final static String JSON_KEY_MODE_LISTEN = "listen";
	public final static String JSON_KEY_TAG_CODE = "code";
	public final static String JSON_KEY_TAG_NAME = "name";
	public final static String JSON_KEY_TAG_ORDER = "order";
	public final static String JSON_KEY_TAG_SELECTION_TYPE = "select";
	public final static String JSON_VALUE_SELECT_SINGLE = "single";
	// private final static String JSON_VALUE_SELECT_MULTIPLE = "multi";
	public final static String JSON_VALUE_SELECT_AT_LEAST_ONE = "multi_at_least_one";
	public final static String JSON_KEY_TAG_DEFAULT_OPTIONS = "defaults";
	public final static String JSON_KEY_TAG_OPTIONS = "options";
	public final static String JSON_KEY_TAG_OPTION_ORDER = "order";
	public final static String JSON_KEY_TAG_OPTION_DATA = "data";
	public final static String JSON_KEY_TAG_OPTION_ID = "tag_id";
	public final static String JSON_KEY_TAG_OPTION_VALUE = "value";

	// json parsing error message
	private final static String JSON_SYNTAX_ERROR_MESSAGE = "Invalid JSON data!";
	
	private int mDataSource = DEFAULTS;
	
	// collection of all tags
	private List<RWTag> mAllTags = new ArrayList<RWTag>();
	
	// data for a single tag, including all options
	public class RWTag {
		public String code; // e.g. demo, age, ques
		public String name;
		public int order;
		public String select; // e.g. single, multi, multi_at_least_one
		public String type; // e.g. listen, speak
		public List<RWOption> options = new ArrayList<RWOption>();
		public List<Integer> defaultOptionsTagIds = new ArrayList<Integer>();
		
		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return name;
		}


		/**
		 * Returns the option for the specified order value, if it exists.
		 * 
		 * @param order to return option for
		 * @return matching option, or null
		 */
		public RWOption getOptionByOrder(int order) {
			for (RWOption o : options) {
				if (o.order == order) {
					return o;
				}
			}
			return null;
		}

		
		/**
		 * Returns the minimum order value of the tag's options.
		 * 
		 * @return min order value, or Integer.MAX_VALUE
		 */
		public int getLowestOptionOrder() {
			int minOrder = Integer.MAX_VALUE;
			for (RWOption o : options) {
				if (o.order < minOrder) {
					minOrder = o.order;
				}
			}
			return minOrder;
		}
		
		
		/**
		 * Returns the maximum order value of the tag's options.
		 * 
		 * @return max order value, or Integer.MIN_VALUE
		 */
		public int getHighestOptionOrder() {
			int maxOrder = Integer.MIN_VALUE;
			for (RWOption o : options) {
				if (o.order > maxOrder) {
					maxOrder = o.order;
				}
			}
			return maxOrder;
		}

		
		/**
		 * Returns the minimum number of options that can be selected for
		 * the tag, depending on the "select" selection type.
		 * 
		 * @return minimum number of options that should be selected
		 */
		public int getMinSelectedOptions() {
			if (JSON_VALUE_SELECT_SINGLE.equalsIgnoreCase(select)) {
				return 1;
			} else if (JSON_VALUE_SELECT_AT_LEAST_ONE.equalsIgnoreCase(select)) {
				return 1;
			}
			// default
			return 0;
		}

	
		/**
		 * Returns the maximum number of options that can be selected for
		 * the tag, depending on the "select" selection type.
		 * 
		 * @return maximum number of options that can be selected
		 */
		public int getMaxSelectedOptions() {
			if (JSON_VALUE_SELECT_SINGLE.equalsIgnoreCase(select)) {
				return 1;
			}
			// default
			return options.size();
		}
	
	}
	
	
	// single option for a tag
	public class RWOption {
		public String data;
		public int order;
		public int tagId;
		public String value;
		public boolean selectByDefault;

		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return value;
		}
	}
	
	
	/**
	 * Creates an empty instance.
	 */
	public RWTags() {
		super();
		mDataSource = DEFAULTS;
	}
	
	
	/**
	 * Overwrites configuration values from the specified key-value map.
	 * If there is no matching key in the map, the value will remain
	 * unchanged.
	 * 
	 * @param jsonResponse to process
	 * @param dataSource of json data (DEFAULTS, FROM_CACHE, FROM_SERVER)
	 */
	public void fromJson(String jsonResponse, int dataSource) {
		mAllTags.clear();
		mDataSource = dataSource;
		
		if (D) { Log.d(TAG, "Creating tags from json: " + jsonResponse); }
		
		try {
			JSONObject root = new JSONObject(jsonResponse);
			if (root != null) {
	 			parseTagsFromJson(JSON_KEY_MODE_LISTEN, root);
	 			parseTagsFromJson(JSON_KEY_MODE_SPEAK, root);
			}
		} catch (JSONException e) {
			Log.e(TAG, JSON_SYNTAX_ERROR_MESSAGE, e);
		}
	}
	
	
	/**
	 * Creates a JSON object for the tags data.
	 *   
	 * @return JSONObject for tags
	 */
	public JSONObject toJson() {
		JSONArray listenEntries = new JSONArray();
		JSONArray speakEntries = new JSONArray();
		
		// create json data for each tag
		for (RWTag tag : mAllTags) {
			JSONObject jsonEntry = new JSONObject();
			try {
				// store the basic properties
				jsonEntry.put(JSON_KEY_TAG_CODE, tag.code);
				jsonEntry.put(JSON_KEY_TAG_NAME, tag.name);
				jsonEntry.put(JSON_KEY_TAG_ORDER, tag.order);
				jsonEntry.put(JSON_KEY_TAG_SELECTION_TYPE, tag.select);
				
				// parse default options
				JSONArray defaults = new JSONArray();
				for (Integer value : tag.defaultOptionsTagIds) {
					defaults.put(value);
				}
				jsonEntry.put(JSON_KEY_TAG_DEFAULT_OPTIONS, defaults);
				
				// add data for all RWOptions
				JSONArray options = new JSONArray();
				for (RWOption option : tag.options) {
					JSONObject jsonOption = new JSONObject();
					jsonOption.put(JSON_KEY_TAG_OPTION_ID, option.tagId);
					jsonOption.put(JSON_KEY_TAG_OPTION_ORDER, option.order);
					jsonOption.put(JSON_KEY_TAG_OPTION_DATA, option.data);
					jsonOption.put(JSON_KEY_TAG_OPTION_VALUE, option.value);
					options.put(jsonOption);
				}
				jsonEntry.put(JSON_KEY_TAG_OPTIONS, options);
				
			} catch (JSONException e) {
				Log.e(TAG, JSON_SYNTAX_ERROR_MESSAGE, e);
			}
			
			// store json entry in type specific collections
			if (JSON_KEY_MODE_LISTEN.equalsIgnoreCase(tag.type)) {
				listenEntries.put(jsonEntry);
			}
			if (JSON_KEY_MODE_SPEAK.equalsIgnoreCase(tag.type)){
				speakEntries.put(jsonEntry);
			}
		}
		
		// gather all the entries with a json root object
		JSONObject root = new JSONObject();
		try {
			if (listenEntries.length() > 0) {
				root.put(JSON_KEY_MODE_LISTEN, listenEntries);
			}
			if (speakEntries.length() > 0) {
				root.put(JSON_KEY_MODE_SPEAK, speakEntries);
			}
		} catch (JSONException e) {
			Log.e(TAG, JSON_SYNTAX_ERROR_MESSAGE, e);
		}

		return root;
	}
	
	
	/**
	 * Creates a String with JSON data for all the tags.
	 *   
	 * @return JSON string
	 */
	public String toJsonString() {
		String result = toJson().toString();
		if (D) {
			Log.d(TAG, "Created json from tags: " + result);
		}
		return result;
	}
	

	/**
	 * Parse a hierarchical subsection with data for a tag type of the JSON
	 * data.
	 * 
	 * @param type to create tags for
	 * @param root of the JSON data returned by the server
	 * @throws JSONException on parse problems
	 */
	private void parseTagsFromJson(String type, JSONObject root) throws JSONException {
		if (root.has(type)) {
			JSONArray entries = root.getJSONArray(type);
	        for (int i = 0; i < entries.length(); i++) {
	        	JSONObject jsonObj = entries.getJSONObject(i);
	        	RWTag tag = new RWTag();
	        	tag.type = type;
	        	tag.code = jsonObj.optString(JSON_KEY_TAG_CODE);
	        	tag.name = jsonObj.optString(JSON_KEY_TAG_NAME);
	        	tag.order = jsonObj.optInt(JSON_KEY_TAG_ORDER);
	        	tag.select = jsonObj.getString(JSON_KEY_TAG_SELECTION_TYPE);

	        	// retrieve tagIds of options selected by default
	        	tag.defaultOptionsTagIds.clear();
	        	JSONArray defaults = jsonObj.getJSONArray(JSON_KEY_TAG_DEFAULT_OPTIONS);
	        	for (int j = 0; j < defaults.length(); j++) {
	        		tag.defaultOptionsTagIds.add(defaults.getInt(j));
	        	}
	        	
	        	JSONArray options = jsonObj.getJSONArray(JSON_KEY_TAG_OPTIONS);
	        	for (int j = 0; j < options.length(); j++) {
	        		JSONObject option = options.getJSONObject(j);
	        		RWOption o = new RWOption();
	        		o.order = option.getInt(JSON_KEY_TAG_OPTION_ORDER);
	        		o.data = option.getString(JSON_KEY_TAG_OPTION_DATA);
	        		o.tagId = option.getInt(JSON_KEY_TAG_OPTION_ID);
	        		o.value = option.getString(JSON_KEY_TAG_OPTION_VALUE);
	        		
	        		// check if option needs to be selected by default
	        		o.selectByDefault = tag.defaultOptionsTagIds.contains(o.tagId);

	        		tag.options.add(o);
	        	}
	        	
	        	sortOptionsByOrder(tag);
	        	mAllTags.add(tag);
	        }
		}
	}
	
	
	/**
	 * Sorts the tags on the order field.
	 */
	public void sortByOrder() {
		Collections.sort(mAllTags, new TagOrderComparator());
	}

	
	/**
	 * Sorts the options of the tag on the order field.
	 * 
	 * @param tag to sort options of
	 */
	public void sortOptionsByOrder(RWTag tag) {
		Collections.sort(tag.options, new OptionOrderComparator());
	}
	
	
	/**
	 * Sort comparator for RWOption instances, to sort on order field.
	 */
	private class OptionOrderComparator implements Comparator<RWOption> {
		@Override
		public int compare(RWOption lhs, RWOption rhs) {
			if (lhs.order == rhs.order) {
				return 0;
			}
			return (lhs.order < rhs.order) ? -1 : 1; 
		}
	}

	
	/**
	 * Sort comparator for RWTag instances, to sort on order field.
	 */
	private class TagOrderComparator implements Comparator<RWTag> {
		@Override
		public int compare(RWTag lhs, RWTag rhs) {
			if (lhs.order == rhs.order) {
				return 0;
			}
			return (lhs.order < rhs.order) ? -1 : 1; 
		}
	}

	
	/**
	 * Returns the data of all stored tags.
	 * 
	 * @return all tags as a list
	 */
	public List<RWTag> getTags() {
		return mAllTags;
	}
	
	
	/**
	 * Returns the data source the tags are created from. Currently this
	 * can be either defaults, from cache, or from server.
	 * 
	 * @return data source of tags data
	 */
	public int getDataSource() {
		return mDataSource;
	}


	/**
	 * Returns a subset of the tags in a new RWTags instance for the specified
	 * type.
	 * 
	 * @param type of tag to create the subset for
	 * @return new RWTags instance with the matching tags
	 */
	public RWTags filterByType(String type) {
		RWTags result = new RWTags();
		for (RWTag t : mAllTags) {
			if ((type != null) && (type.equalsIgnoreCase(t.type))) {
				result.mAllTags.add(t);
			}
		}
		return result;
	}
	

	/**
	 * Returns a subset of the tags in a new RWTags instance for the specified
	 * code and type.
	 * 
	 * @param code of tag to create the subset for
	 * @param type of tag to create the subset for
	 * @return new RWTags instance with the matching tags
	 */
	public RWTags filterByCodeAndType(String code, String type) {
		RWTags result = new RWTags();
		for (RWTag t : mAllTags) {
			if ((code != null) && (code.equalsIgnoreCase(t.code))) {
				if ((type != null) && (type.equalsIgnoreCase(t.type))) {
					result.mAllTags.add(t);
				}
			}
		}
		return result;
	}
	

	/**
	 * Returns the tag with the specified order value, if it exists.
	 * 
	 * @param order to return tag for
	 * @return tag with matching order value, or null
	 */
	public RWTag findTagByOrder(int order) {
		for (RWTag t : mAllTags) {
			if (t.order == order) {
				return t;
			}
		}
		return null;
	}
	

	/**
	 * Returns the tag for a given code and type, if it exists.
	 * 
	 * @param code to find tag for
	 * @param type to find tag for
	 * @return tag with matching code and type values, or null
	 */
	public RWTag findTagByCodeAndType(String code, String type) {
		for (RWTag t : mAllTags) {
			if ((code != null) && (code.equalsIgnoreCase(t.code))) {
				if ((type != null) && (type.equalsIgnoreCase(t.type))) {
					return t;
				}
			}
		}
		return null;
	}
	
}
