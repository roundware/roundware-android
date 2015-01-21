/**
 * Roundware Android code is released under the terms of the GNU General Public License.
 * See COPYRIGHT.txt, AUTHORS.txt, and LICENSE.txt in the project root directory for details.
 */
package org.roundware.rwapp.utils;

/**
 * A simple class with two strings
 */
public class AssetData {
    public final String url;
    public final String description;

    public AssetData(String url, String description){
        this.url = url;
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof AssetData){
            AssetData other = (AssetData)o;
            boolean urlTest = other.url == null ? this.url == null : other.url.equals(this.url);
            boolean descriptionTest = other.description == null ? this.description == null :
                other.description.equals(this.description);
            return urlTest && descriptionTest;
        }
        return false;
    }
}

