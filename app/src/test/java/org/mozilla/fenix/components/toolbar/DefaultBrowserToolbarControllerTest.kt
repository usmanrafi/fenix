/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.toolbar

import android.content.Context
import android.content.Intent
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.navigation.NavController
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import mozilla.components.browser.session.Session
import mozilla.components.browser.session.SessionManager
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.tabs.TabsUseCases
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.NavGraphDirections
import org.mozilla.fenix.R
import org.mozilla.fenix.browser.BrowserFragment
import org.mozilla.fenix.browser.BrowserFragmentDirections
import org.mozilla.fenix.browser.browsingmode.BrowsingMode
import org.mozilla.fenix.browser.browsingmode.BrowsingModeManager
import org.mozilla.fenix.collections.CreateCollectionViewModel
import org.mozilla.fenix.components.Analytics
import org.mozilla.fenix.components.metrics.Event
import org.mozilla.fenix.components.metrics.MetricController
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.nav
import org.mozilla.fenix.ext.toTab
import org.mozilla.fenix.home.sessioncontrol.Tab
import org.mozilla.fenix.home.sessioncontrol.TabCollection
import org.mozilla.fenix.quickactionsheet.QuickActionSheetBehavior
import org.mozilla.fenix.utils.deleteAndQuit

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class DefaultBrowserToolbarControllerTest {

    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    private var context: HomeActivity = mockk(relaxed = true)
    private var analytics: Analytics = mockk(relaxed = true)
    private val browsingModeManager: BrowsingModeManager = mockk(relaxed = true)
    private var navController: NavController = mockk(relaxed = true)
    private var findInPageLauncher: () -> Unit = mockk(relaxed = true)
    private val engineView: EngineView = mockk(relaxed = true)
    private val currentSession: Session = mockk(relaxed = true)
    private val viewModel: CreateCollectionViewModel = mockk(relaxed = true)
    private val getSupportUrl: () -> String = { "https://supportUrl.org" }
    private val openInFenixIntent: Intent = mockk(relaxed = true)
    private val currentSessionAsTab: Tab = mockk(relaxed = true)
    private val bottomSheetBehavior: QuickActionSheetBehavior<NestedScrollView> =
        mockk(relaxed = true)
    private val metrics: MetricController = mockk(relaxed = true)
    private val sessionUseCases: SessionUseCases = mockk(relaxed = true)
    private val scope: LifecycleCoroutineScope = mockk(relaxed = true)

    private lateinit var controller: DefaultBrowserToolbarController

    @Before
    fun setUp() {
        Dispatchers.setMain(mainThreadSurrogate)

        controller = DefaultBrowserToolbarController(
            context = context,
            navController = navController,
            browsingModeManager = browsingModeManager,
            findInPageLauncher = findInPageLauncher,
            engineView = engineView,
            customTabSession = null,
            viewModel = viewModel,
            getSupportUrl = getSupportUrl,
            openInFenixIntent = openInFenixIntent,
            bottomSheetBehavior = bottomSheetBehavior,
            scope = scope
        )

        mockkStatic(
            "org.mozilla.fenix.ext.SessionKt"
        )
        every { any<Session>().toTab(any()) } returns currentSessionAsTab

        mockkStatic(
            "org.mozilla.fenix.utils.DeleteAndQuitKt"
        )
        every { any<Context>().deleteAndQuit(any()) } just Runs

        every { context.components.analytics } returns analytics
        every { analytics.metrics } returns metrics
        every { context.components.useCases.sessionUseCases } returns sessionUseCases
        every { context.components.core.sessionManager.selectedSession } returns currentSession
    }

    @Test
    fun handleBrowserToolbarPaste() {
        every { currentSession.id } returns "1"

        val pastedText = "Mozilla"
        controller.handleToolbarPaste(pastedText)

        verify {
            navController.nav(
                R.id.browserFragment,
                BrowserFragmentDirections.actionBrowserFragmentToSearchFragment(
                    sessionId = currentSession.id,
                    pastedText = pastedText
                )
            )
        }
    }

    @Test
    fun handleBrowserToolbarPasteAndGo() {
        val pastedText = "Mozilla"

        controller.handleToolbarPasteAndGo(pastedText)
        verifyOrder {
            currentSession.searchTerms = ""
            sessionUseCases.loadUrl(pastedText)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain() // reset main dispatcher to the original Main dispatcher
        mainThreadSurrogate.close()
    }

    @Test
    fun handleToolbarClick() {
        every { currentSession.id } returns "1"

        controller.handleToolbarClick()

        verify { metrics.track(Event.SearchBarTapped(Event.SearchBarTapped.Source.BROWSER)) }
        verify {
            navController.nav(
                R.id.browserFragment,
                BrowserFragmentDirections.actionBrowserFragmentToSearchFragment(
                    sessionId = "1"
                )
            )
        }
    }

    @Test
    fun handleToolbarBackPress() {
        val item = ToolbarMenu.Item.Back

        controller.handleToolbarItemInteraction(item)

        verify { metrics.track(Event.BrowserMenuItemTapped(Event.BrowserMenuItemTapped.Item.BACK)) }
        verify { sessionUseCases.goBack }
    }

    @Test
    fun handleToolbarForwardPress() {
        val item = ToolbarMenu.Item.Forward

        controller.handleToolbarItemInteraction(item)

        verify { metrics.track(Event.BrowserMenuItemTapped(Event.BrowserMenuItemTapped.Item.FORWARD)) }
        verify { sessionUseCases.goForward }
    }

    @Test
    fun handleToolbarReloadPress() {
        val item = ToolbarMenu.Item.Reload

        every { context.components.useCases.sessionUseCases } returns sessionUseCases

        controller.handleToolbarItemInteraction(item)

        verify { metrics.track(Event.BrowserMenuItemTapped(Event.BrowserMenuItemTapped.Item.RELOAD)) }
        verify { sessionUseCases.reload }
    }

    @Test
    fun handleToolbarStopPress() {
        val item = ToolbarMenu.Item.Stop

        controller.handleToolbarItemInteraction(item)

        verify { metrics.track(Event.BrowserMenuItemTapped(Event.BrowserMenuItemTapped.Item.STOP)) }
        verify { sessionUseCases.stopLoading }
    }

    @Test
    fun handleToolbarSettingsPress() {
        val item = ToolbarMenu.Item.Settings

        controller.handleToolbarItemInteraction(item)

        verify { metrics.track(Event.BrowserMenuItemTapped(Event.BrowserMenuItemTapped.Item.SETTINGS)) }
        verify {
            navController.nav(
                R.id.settingsFragment,
                BrowserFragmentDirections.actionBrowserFragmentToSettingsFragment()
            )
        }
    }

    @Test
    fun handleToolbarLibraryPress() {
        val item = ToolbarMenu.Item.Library

        controller.handleToolbarItemInteraction(item)

        verify { metrics.track(Event.BrowserMenuItemTapped(Event.BrowserMenuItemTapped.Item.LIBRARY)) }
        verify {
            navController.nav(
                R.id.libraryFragment,
                BrowserFragmentDirections.actionBrowserFragmentToSettingsFragment()
            )
        }
    }

    @Test
    fun handleToolbarRequestDesktopOnPress() {
        val requestDesktopSiteUseCase: SessionUseCases.RequestDesktopSiteUseCase =
            mockk(relaxed = true)
        val item = ToolbarMenu.Item.RequestDesktop(true)

        every { sessionUseCases.requestDesktopSite } returns requestDesktopSiteUseCase

        controller.handleToolbarItemInteraction(item)

        verify { metrics.track(Event.BrowserMenuItemTapped(Event.BrowserMenuItemTapped.Item.DESKTOP_VIEW_ON)) }
        verify {
            requestDesktopSiteUseCase.invoke(
                true,
                currentSession
            )
        }
    }

    @Test
    fun handleToolbarRequestDesktopOffPress() {
        val requestDesktopSiteUseCase: SessionUseCases.RequestDesktopSiteUseCase =
            mockk(relaxed = true)
        val item = ToolbarMenu.Item.RequestDesktop(false)

        every { sessionUseCases.requestDesktopSite } returns requestDesktopSiteUseCase

        controller.handleToolbarItemInteraction(item)

        verify { metrics.track(Event.BrowserMenuItemTapped(Event.BrowserMenuItemTapped.Item.DESKTOP_VIEW_OFF)) }
        verify {
            requestDesktopSiteUseCase.invoke(
                false,
                currentSession
            )
        }
    }

    @Test
    fun handleToolbarAddToHomeScreenPress() {
        val item = ToolbarMenu.Item.AddToHomeScreen

        controller.handleToolbarItemInteraction(item)

        verify { metrics.track(Event.BrowserMenuItemTapped(Event.BrowserMenuItemTapped.Item.ADD_TO_HOMESCREEN)) }
    }

    @Test
    fun handleToolbarSharePress() {
        val item = ToolbarMenu.Item.Share

        every { currentSession.url } returns "https://mozilla.org"
        val directions = NavGraphDirections.actionGlobalShareFragment(currentSession.url)

        controller.handleToolbarItemInteraction(item)

        verify { metrics.track(Event.BrowserMenuItemTapped(Event.BrowserMenuItemTapped.Item.SHARE)) }
        verify { navController.navigate(directions) }
    }

    @Test
    fun handleToolbarNewPrivateTabPress() {
        val item = ToolbarMenu.Item.NewPrivateTab

        every { browsingModeManager.mode } returns BrowsingMode.Normal

        controller.handleToolbarItemInteraction(item)

        verify { metrics.track(Event.BrowserMenuItemTapped(Event.BrowserMenuItemTapped.Item.NEW_PRIVATE_TAB)) }
        verify {
            val directions = BrowserFragmentDirections
                .actionBrowserFragmentToSearchFragment(sessionId = null)
            navController.nav(R.id.browserFragment, directions)
        }
        verify { browsingModeManager.mode = BrowsingMode.Private }
    }

    @Test
    fun handleToolbarFindInPagePress() {
        val item = ToolbarMenu.Item.FindInPage

        controller.handleToolbarItemInteraction(item)

        verify { bottomSheetBehavior.state = QuickActionSheetBehavior.STATE_COLLAPSED }
        verify { findInPageLauncher() }
        verify { metrics.track(Event.FindInPageOpened) }
    }

    @Test
    fun handleToolbarReportIssuePress() {
        val tabsUseCases: TabsUseCases = mockk(relaxed = true)
        val addTabUseCase: TabsUseCases.AddNewTabUseCase = mockk(relaxed = true)

        val item = ToolbarMenu.Item.ReportIssue

        every { currentSession.id } returns "1"
        every { currentSession.url } returns "https://mozilla.org"
        every { context.components.useCases.tabsUseCases } returns tabsUseCases
        every { tabsUseCases.addTab } returns addTabUseCase

        controller.handleToolbarItemInteraction(item)

        verify { metrics.track(Event.BrowserMenuItemTapped(Event.BrowserMenuItemTapped.Item.REPORT_SITE_ISSUE)) }
        verify {
            // Hardcoded URL because this function modifies the URL with an apply
            addTabUseCase.invoke(
                String.format(
                    BrowserFragment.REPORT_SITE_ISSUE_URL,
                    "https://mozilla.org"
                )
            )
        }
    }

    @Test
    fun handleToolbarHelpPress() {
        val tabsUseCases: TabsUseCases = mockk(relaxed = true)
        val addTabUseCase: TabsUseCases.AddNewTabUseCase = mockk(relaxed = true)

        val item = ToolbarMenu.Item.Help

        every { context.components.useCases.tabsUseCases } returns tabsUseCases
        every { tabsUseCases.addTab } returns addTabUseCase

        controller.handleToolbarItemInteraction(item)

        verify { metrics.track(Event.BrowserMenuItemTapped(Event.BrowserMenuItemTapped.Item.HELP)) }
        verify {
            addTabUseCase.invoke(getSupportUrl())
        }
    }

    @Test
    fun handleToolbarNewTabPress() {
        val item = ToolbarMenu.Item.NewTab

        every { browsingModeManager.mode } returns BrowsingMode.Private

        controller.handleToolbarItemInteraction(item)

        verify { metrics.track(Event.BrowserMenuItemTapped(Event.BrowserMenuItemTapped.Item.NEW_TAB)) }
        verify {
            val directions = BrowserFragmentDirections
                .actionBrowserFragmentToSearchFragment(sessionId = null)
            navController.nav(R.id.browserFragment, directions)
        }
        verify { browsingModeManager.mode = BrowsingMode.Normal }
    }

    @Test
    fun handleToolbarSaveToCollectionPress() {
        val item = ToolbarMenu.Item.SaveToCollection
        val cachedTabCollections: List<TabCollection> = mockk(relaxed = true)
        every { context.components.useCases.sessionUseCases } returns sessionUseCases
        every { context.components.core.tabCollectionStorage.cachedTabCollections } returns cachedTabCollections

        controller.handleToolbarItemInteraction(item)

        verify { metrics.track(Event.BrowserMenuItemTapped(Event.BrowserMenuItemTapped.Item.SAVE_TO_COLLECTION)) }
        verify { metrics.track(Event.CollectionSaveButtonPressed(DefaultBrowserToolbarController.TELEMETRY_BROWSER_IDENTIFIER)) }
        verify {
            viewModel.saveTabToCollection(
                listOf(currentSessionAsTab),
                currentSessionAsTab,
                cachedTabCollections
            )
        }
        verify { viewModel.previousFragmentId = R.id.browserFragment }
        verify {
            val directions = BrowserFragmentDirections
                .actionBrowserFragmentToSearchFragment(sessionId = null)
            navController.nav(R.id.browserFragment, directions)
        }
    }

    @Test
    fun handleToolbarOpenInFenixPress() {
        controller = DefaultBrowserToolbarController(
            context = context,
            navController = navController,
            browsingModeManager = browsingModeManager,
            findInPageLauncher = findInPageLauncher,
            engineView = engineView,
            customTabSession = currentSession,
            viewModel = viewModel,
            getSupportUrl = getSupportUrl,
            openInFenixIntent = openInFenixIntent,
            bottomSheetBehavior = bottomSheetBehavior,
            scope = scope
        )

        val sessionManager: SessionManager = mockk(relaxed = true)
        val item = ToolbarMenu.Item.OpenInFenix

        every { context.components.core.sessionManager } returns sessionManager
        every { currentSession.customTabConfig } returns mockk()
        every { context.startActivity(any()) } just Runs

        controller.handleToolbarItemInteraction(item)

        verify { engineView.release() }
        verify { currentSession.customTabConfig = null }
        verify { sessionManager.select(currentSession) }
        verify { context.startActivity(openInFenixIntent) }
        verify { context.finish() }
    }

    @Test
    fun handleToolbarQuitPress() {
        val item = ToolbarMenu.Item.Quit

        controller.handleToolbarItemInteraction(item)

        verify { context.deleteAndQuit(scope) }
    }
}
