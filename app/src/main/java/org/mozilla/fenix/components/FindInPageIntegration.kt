/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.view.View
import android.view.ViewStub
import mozilla.components.browser.session.SessionManager
import mozilla.components.browser.session.runWithSessionIdOrSelected
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.findinpage.FindInPageFeature
import mozilla.components.feature.findinpage.view.FindInPageView
import mozilla.components.support.base.feature.LifecycleAwareFeature
import org.mozilla.fenix.test.Mockable

@Mockable
class FindInPageIntegration(
    private val sessionManager: SessionManager,
    private val sessionId: String? = null,
    stub: ViewStub,
    private val engineView: EngineView,
    private val toolbar: BrowserToolbar
) : InflationAwareFeature(stub) {
    override fun onViewInflated(view: View): LifecycleAwareFeature {
        return FindInPageFeature(sessionManager, view as FindInPageView, engineView) {
            toolbar.visibility = View.VISIBLE
            view.visibility = View.GONE
        }
    }

    override fun onLaunch(view: View, feature: LifecycleAwareFeature) {
        sessionManager.runWithSessionIdOrSelected(sessionId) { session ->
            if (!session.isCustomTabSession()) {
                toolbar.visibility = View.GONE
            }
            view.visibility = View.VISIBLE
            (feature as FindInPageFeature).bind(session)
        }
    }
}
