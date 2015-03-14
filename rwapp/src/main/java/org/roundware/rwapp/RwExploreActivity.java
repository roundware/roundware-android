/**
 * Roundware Android code is released under the terms of the GNU General Public License.
 * See COPYRIGHT.txt, AUTHORS.txt, and LICENSE.txt in the project root directory for details.
 */
package org.roundware.rwapp;

import org.roundware.service.RWService;

/**
 * Explore activity
 */
public class RwExploreActivity extends RwWebActivity {
    public static final String LOGTAG = RwExploreActivity.class.getSimpleName();
    private final static boolean D = false;

    @Override
    protected String getUrl() {
        return getString(R.string.explore_url);
    }

    @Override
    protected void handleOnServiceConnected(RWService service) {
        // nada
    }
}

