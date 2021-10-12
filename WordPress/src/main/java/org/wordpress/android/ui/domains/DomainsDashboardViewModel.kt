package org.wordpress.android.ui.domains

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import org.wordpress.android.Constants
import org.wordpress.android.R
import org.wordpress.android.R.string
import org.wordpress.android.analytics.AnalyticsTracker.Stat.DOMAIN_CREDIT_REDEMPTION_TAPPED
import org.wordpress.android.fluxc.network.rest.wpcom.site.Domain
import org.wordpress.android.fluxc.store.SiteStore
import org.wordpress.android.modules.UI_THREAD
import org.wordpress.android.ui.domains.DomainsDashboardItem.Action
import org.wordpress.android.ui.domains.DomainsDashboardItem.Action.CHANGE_SITE_ADDRESS
import org.wordpress.android.ui.domains.DomainsDashboardItem.AddDomain
import org.wordpress.android.ui.domains.DomainsDashboardItem.DomainBlurb
import org.wordpress.android.ui.domains.DomainsDashboardItem.PrimaryDomain
import org.wordpress.android.ui.domains.DomainsDashboardItem.PurchaseDomain
import org.wordpress.android.ui.domains.DomainsDashboardItem.SiteDomains
import org.wordpress.android.ui.domains.DomainsDashboardItem.SiteDomainsHeader
import org.wordpress.android.ui.domains.DomainsDashboardNavigationAction.ClaimDomain
import org.wordpress.android.ui.domains.DomainsDashboardNavigationAction.GetDomain
import org.wordpress.android.ui.domains.DomainsDashboardNavigationAction.OpenManageDomains
import org.wordpress.android.ui.mysite.SelectedSiteRepository
import org.wordpress.android.ui.mysite.cards.domainregistration.DomainRegistrationHandler
import org.wordpress.android.ui.utils.HtmlMessageUtils
import org.wordpress.android.ui.utils.ListItemInteraction
import org.wordpress.android.ui.utils.UiString.UiStringRes
import org.wordpress.android.ui.utils.UiString.UiStringResWithParams
import org.wordpress.android.ui.utils.UiString.UiStringText
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T
import org.wordpress.android.util.SiteUtils
import org.wordpress.android.util.analytics.AnalyticsTrackerWrapper
import org.wordpress.android.viewmodel.Event
import org.wordpress.android.viewmodel.ScopedViewModel
import javax.inject.Inject
import javax.inject.Named

@Suppress("TooManyFunctions")
class DomainsDashboardViewModel @Inject constructor(
    private val siteStore: SiteStore,
    private val analyticsTrackerWrapper: AnalyticsTrackerWrapper,
    selectedSiteRepository: SelectedSiteRepository,
    domainRegistrationHandler: DomainRegistrationHandler,
    private val htmlMessageUtils: HtmlMessageUtils,
    @Named(UI_THREAD) private val uiDispatcher: CoroutineDispatcher
) : ScopedViewModel(uiDispatcher) {
    private val _onNavigation = MutableLiveData<Event<DomainsDashboardNavigationAction>>()
    val onNavigation = _onNavigation

    private val _uiModel = MutableLiveData<List<DomainsDashboardItem>>()
    val uiModel = _uiModel

    val siteUrl: String = SiteUtils.getHomeURLOrHostName(selectedSiteRepository.selectedSiteChange.value)
    val selectedSite = requireNotNull(selectedSiteRepository.getSelectedSite())
    private val domainCreditAvailable =
            domainRegistrationHandler.buildSource(viewModelScope, selectedSite.id).distinctUntilChanged()

    private val hasDomainCredit = domainCreditAvailable.value?.isDomainCreditAvailable == true
    private val hasCustomDomain = SiteUtils.hasCustomDomain(selectedSite)

    private var isStarted: Boolean = false

    fun start() {
        if (isStarted) {
            return
        }
        isStarted = true
        _uiModel.value = buildSiteDomainsList()
    }

    private fun buildSiteDomainsList(): List<DomainsDashboardItem> = when {
        hasCustomDomain -> manageDomainsItems()
        hasDomainCredit -> claimDomainItems()
        else -> getDomainItems()
    }

    private fun getDomainItems(): List<DomainsDashboardItem> {
        val listItems = mutableListOf<DomainsDashboardItem>()

        listItems += PrimaryDomain(UiStringText(siteUrl), this::onChangeSiteClick)
        // for v1 release image/anim is de-scoped, set the image visibility to gone in layout for now.
        listItems +=
            PurchaseDomain(
                    R.drawable.media_image_placeholder,
                    UiStringRes(string.domains_free_plan_get_your_domain_title),
                    UiStringText(htmlMessageUtils.getHtmlMessageFromStringFormatResId(
                            string.domains_free_plan_get_your_domain_caption, siteUrl)),
                    ListItemInteraction.create(this::onGetDomainClick)
            )

        return listItems
    }

    private fun claimDomainItems(): List<DomainsDashboardItem> {
        val listItems = mutableListOf<DomainsDashboardItem>()

        listItems += PrimaryDomain(UiStringText(siteUrl), this::onChangeSiteClick)

        listItems +=
            PurchaseDomain(
                    R.drawable.media_image_placeholder,
                    UiStringRes(string.domains_paid_plan_claim_your_domain_title),
                    UiStringRes(string.domains_paid_plan_claim_your_domain_caption),
                    ListItemInteraction.create(this::onClaimDomainClick)
            )

        return listItems
    }

    // if site has a registered domain then show Site Domains, Add Domain and Manage Domains
    private fun manageDomainsItems(): List<DomainsDashboardItem> {
        launch {
            val result = siteStore.fetchSiteDomains(selectedSite)
            when {
                result.isError -> {
                    AppLog.e(T.DOMAIN_REGISTRATION, "An error occurred while fetching site domains")
                }
                else -> {
                    _uiModel.value = manageDomainsListItems(result.domains)
                }
            }
        }

        return manageDomainsListItems(null)
    }

    private fun manageDomainsListItems(domains: List<Domain>?): List<DomainsDashboardItem> {
        val listItems = mutableListOf<DomainsDashboardItem>()

        listItems += PrimaryDomain(UiStringText(siteUrl), this::onChangeSiteClick)

        listItems += SiteDomainsHeader(UiStringRes(string.domains_site_domains))

        domains?.forEach {
            if (!it.wpcomDomain && !it.isWpcomStagingDomain) {
                listItems += SiteDomains(
                        UiStringText(it.domain.toString()),
                        if (it.expirySoon) {
                            UiStringText(
                                    htmlMessageUtils.getHtmlMessageFromStringFormatResId(
                                            string.domains_site_domain_expires_soon, it.expiry.toString()))
                        } else {
                            UiStringResWithParams(
                                    string.domains_site_domain_expires, listOf(UiStringText(it.expiry.toString())))
                        }
                )
            }
        }

        // if site has redirected domain then show this blurb
        if (!hasCustomDomain) {
            listItems += DomainBlurb(
                    UiStringText(
                            htmlMessageUtils.getHtmlMessageFromStringFormatResId(
                                    string.domains_redirected_domains_blurb, siteUrl
                            )
                    )
            )
        }

        listItems += AddDomain(ListItemInteraction.create(this::onAddDomainClick))

//        NOTE: Manage domains option is de-scoped for v1 release
//        listItems += ManageDomains(ListItemInteraction.create(this::onManageDomainClick))

        return listItems
    }

    private fun onGetDomainClick() {
        analyticsTrackerWrapper.track(DOMAIN_CREDIT_REDEMPTION_TAPPED, selectedSite)
        _onNavigation.value = Event(GetDomain(selectedSite))
    }

    private fun onClaimDomainClick() {
        // TODO Add tracking
        _onNavigation.value = Event(ClaimDomain(selectedSite))
    }

    private fun onAddDomainClick() {
        onClaimDomainClick()
    }

    private fun onManageDomainClick() {
        _onNavigation.postValue(Event(OpenManageDomains("${Constants.URL_MANAGE_DOMAINS}/${selectedSite.siteId}")))
    }

//  NOTE: Change site option is de-scoped for v1 release
    private fun onChangeSiteClick(action: Action): Boolean {
        when (action) {
            CHANGE_SITE_ADDRESS -> {} // TODO: next PR
        }
        return true
    }
}
