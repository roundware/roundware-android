package org.roundware.rwapp.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The Class Registry keeps track of which Classes should be used. It allows registered Classes used
 * in a superclass to be overridden by a subclass.
 */
public class ClassRegistry {
    // Create a synchronized HashMap to store the actual data.
    private static Map<String, Class> mRegistry = Collections.synchronizedMap(new HashMap<String, Class>());

    private ClassRegistry() {}

    public static void register(String key, Class theClass) {
        mRegistry.put(key, theClass);
    }

    public static Class get(String key) {
        return mRegistry.get(key);
    }
}
