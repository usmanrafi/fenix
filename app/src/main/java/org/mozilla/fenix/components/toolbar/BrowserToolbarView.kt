package org.mozilla.fenix.components.toolbar

import android.content.ClipData
import android.content.ClipDescription.MIMETYPE_TEXT_PLAIN
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.browser_toolbar_popup_window.view.*
import mozilla.components.browser.domains.autocomplete.ShippedDomainsProvider
import mozilla.components.browser.session.Session
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.support.ktx.android.util.dpToFloat
import mozilla.components.support.ktx.android.util.dpToPx
import org.jetbrains.anko.dimen
import org.mozilla.fenix.R
import org.mozilla.fenix.customtabs.CustomTabToolbarMenu
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.theme.ThemeManager

interface BrowserToolbarViewInteractor {
    fun onBrowserToolbarPaste(text: String)
    fun onBrowserToolbarPasteAndGo(text: String)
    fun onBrowserToolbarClicked()
    fun onBrowserToolbarMenuItemTapped(item: ToolbarMenu.Item)
}

class BrowserToolbarView(
    private val container: ViewGroup,
    private val interactor: BrowserToolbarViewInteractor,
    private val customTabSession: Session?
) : LayoutContainer {

    override val containerView: View?
        get() = container

    private val urlBackground = LayoutInflater.from(container.context)
        .inflate(R.layout.layout_url_background, container, false)

    val view: BrowserToolbar = LayoutInflater.from(container.context)
        .inflate(R.layout.component_search, container, true)
        .findViewById(R.id.toolbar)

    val toolbarIntegration: ToolbarIntegration

    init {
        val isCustomTabSession = customTabSession != null

        view.setOnUrlLongClickListener {
            val clipboard = view.context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val customView = LayoutInflater.from(view.context).inflate(R.layout.browser_toolbar_popup_window, null)
            val popupWindow = PopupWindow(customView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                view.context.dimen(R.dimen.context_menu_height),
                true
            )

            popupWindow.showAsDropDown(view, view.context.dimen(R.dimen.context_menu_x_offset), 0, Gravity.START)

            customView.paste.isVisible = clipboard.containsText() && !isCustomTabSession
            customView.paste_and_go.isVisible = clipboard.containsText() && !isCustomTabSession

            customView.copy.setOnClickListener {
                popupWindow.dismiss()
                clipboard.primaryClip = ClipData.newPlainText("Text", view.url.toString())
            }

            customView.paste.setOnClickListener {
                popupWindow.dismiss()
                interactor.onBrowserToolbarPaste(clipboard.primaryClip?.getItemAt(0)?.text.toString())
            }

            customView.paste_and_go.setOnClickListener {
                popupWindow.dismiss()
                interactor.onBrowserToolbarPasteAndGo(clipboard.primaryClip?.getItemAt(0)?.text.toString())
            }

            true
        }

        with(container.context) {
            val sessionManager = components.core.sessionManager

            view.apply {
                elevation = TOOLBAR_ELEVATION.dpToFloat(resources.displayMetrics)

                onUrlClicked = {
                    interactor.onBrowserToolbarClicked()
                    false
                }

                browserActionMargin = browserActionMarginDp.dpToPx(resources.displayMetrics)

                urlBoxView = if (isCustomTabSession) null else urlBackground
                progressBarGravity = if (isCustomTabSession) PROGRESS_BOTTOM else PROGRESS_TOP

                textColor = ContextCompat.getColor(context, R.color.photonGrey30)

                hint = context.getString(R.string.search_hint)

                suggestionBackgroundColor = ContextCompat.getColor(
                    container.context,
                    R.color.suggestion_highlight_color
                )

                textColor = ContextCompat.getColor(
                    container.context,
                    ThemeManager.resolveAttribute(R.attr.primaryText, container.context)
                )

                hintColor = ContextCompat.getColor(
                    container.context,
                    ThemeManager.resolveAttribute(R.attr.secondaryText, container.context)
                )
            }

            val menuToolbar = if (isCustomTabSession) {
                CustomTabToolbarMenu(
                    this,
                    sessionManager,
                    customTabSession?.id,
                    onItemTapped = {
                        interactor.onBrowserToolbarMenuItemTapped(it)
                    }
                )
            } else {
                DefaultToolbarMenu(
                    this,
                    hasAccountProblem = components.backgroundServices.accountManager.accountNeedsReauth(),
                    requestDesktopStateProvider = {
                        sessionManager.selectedSession?.desktopMode ?: false
                    },
                    onItemTapped = { interactor.onBrowserToolbarMenuItemTapped(it) }
                )
            }

            toolbarIntegration = ToolbarIntegration(
                this,
                view,
                container,
                menuToolbar,
                ShippedDomainsProvider().also { it.initialize(this) },
                components.core.historyStorage,
                components.core.sessionManager,
                customTabSession?.id,
                customTabSession?.private ?: sessionManager.selectedSession?.private ?: false
            )
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun update(state: BrowserFragmentState) {
        // Intentionally leaving this as a stub for now since we don't actually want to update currently
    }

    private fun ClipboardManager.containsText(): Boolean {
        return (primaryClipDescription?.hasMimeType(MIMETYPE_TEXT_PLAIN) ?: false && primaryClip?.itemCount != 0)
    }

    companion object {
        private const val TOOLBAR_ELEVATION = 16
        private const val PROGRESS_BOTTOM = 0
        private const val PROGRESS_TOP = 1
        const val browserActionMarginDp = 8
    }
}
