package org.wordpress.android.viewmodel.pages

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import kotlinx.coroutines.CoroutineDispatcher
import org.wordpress.android.R
import org.wordpress.android.fluxc.model.page.PageModel
import org.wordpress.android.fluxc.model.page.PageStatus
import org.wordpress.android.modules.UI_THREAD
import org.wordpress.android.ui.pages.PageItem
import org.wordpress.android.ui.pages.PageItem.Action
import org.wordpress.android.ui.pages.PageItem.Divider
import org.wordpress.android.ui.pages.PageItem.DraftPage
import org.wordpress.android.ui.pages.PageItem.Empty
import org.wordpress.android.ui.pages.PageItem.Page
import org.wordpress.android.ui.pages.PageItem.PublishedPage
import org.wordpress.android.ui.pages.PageItem.ScheduledPage
import org.wordpress.android.ui.pages.PageItem.TrashedPage
import org.wordpress.android.viewmodel.ResourceProvider
import org.wordpress.android.viewmodel.ScopedViewModel
import org.wordpress.android.viewmodel.pages.PageListViewModel.PageListType
import org.wordpress.android.viewmodel.pages.PageListViewModel.PageListType.DRAFTS
import org.wordpress.android.viewmodel.pages.PageListViewModel.PageListType.PUBLISHED
import org.wordpress.android.viewmodel.pages.PageListViewModel.PageListType.SCHEDULED
import org.wordpress.android.viewmodel.pages.PageListViewModel.PageListType.TRASHED
import java.util.SortedMap
import javax.inject.Inject
import javax.inject.Named

class SearchListViewModel
@Inject constructor(
    private val createPageListItemLabelsUseCase: CreatePageListItemLabelsUseCase,
    private val postModelUploadUiStateUseCase: PostModelUploadUiStateUseCase,
    private val pageListItemActionsUseCase: CreatePageListItemActionsUseCase,
    private val pageItemProgressUiStateUseCase: PageItemProgressUiStateUseCase,
    private val resourceProvider: ResourceProvider,
    @Named(UI_THREAD) private val uiDispatcher: CoroutineDispatcher
) : ScopedViewModel(uiDispatcher) {
    private val _searchResult: MutableLiveData<List<PageItem>> = MutableLiveData()
    val searchResult: LiveData<List<PageItem>> = _searchResult

    private var isStarted: Boolean = false
    private lateinit var pagesViewModel: PagesViewModel

    fun start(pagesViewModel: PagesViewModel) {
        this.pagesViewModel = pagesViewModel

        if (!isStarted) {
            isStarted = true

            pagesViewModel.searchPages.observeForever(searchObserver)
        }
    }

    override fun onCleared() {
        pagesViewModel.searchPages.removeObserver(searchObserver)
    }

    private val searchObserver = Observer<SortedMap<PageListType, List<PageModel>>> { pages ->
        if (pages != null) {
            loadFoundPages(pages)

            pagesViewModel.checkIfNewPageButtonShouldBeVisible()
        } else {
            _searchResult.value = listOf(Empty(R.string.pages_search_suggestion, true))
        }
    }

    fun onMenuAction(action: Action, pageItem: Page, context: Context? = null): Boolean {
        return pagesViewModel.onMenuAction(action, pageItem, context)
    }

    fun onItemTapped(pageItem: Page) {
        pagesViewModel.onItemTapped(pageItem)
    }

    private fun loadFoundPages(pages: SortedMap<PageListType, List<PageModel>>) = launch {
        if (pages.isNotEmpty()) {
            val pageItems = pages
                    .map { (listType, results) ->
                        listOf(Divider(resourceProvider.getString(listType.title))) +
                                results.map { it.toPageItem(pagesViewModel.arePageActionsEnabled) }
                    }
                    .fold(mutableListOf()) { acc: MutableList<PageItem>, list: List<PageItem> ->
                        acc.addAll(list)
                        return@fold acc
                    }
            _searchResult.value = pageItems
        } else {
            _searchResult.value = listOf(Empty(R.string.pages_empty_search_result, true))
        }
    }

    private fun PageModel.toPageItem(areActionsEnabled: Boolean): PageItem {
        val uploadUiState = postModelUploadUiStateUseCase.createUploadUiState(
                this.post,
                pagesViewModel.site,
                pagesViewModel.uploadStatusTracker
        )
        val (progressBarUiState, showOverlay) = pageItemProgressUiStateUseCase.getProgressStateForPage(uploadUiState)
        val (labels, labelColor) = createPageListItemLabelsUseCase.createLabels(this.post, uploadUiState)

        return when (status) {
            PageStatus.PUBLISHED, PageStatus.PRIVATE ->
                PublishedPage(
                        remoteId = remoteId,
                        localId = pageId,
                        title = title,
                        date = date,
                        labels = labels,
                        labelsColor = labelColor,
                        actions = pageListItemActionsUseCase.setupPageActions(
                                PUBLISHED,
                                uploadUiState,
                                pagesViewModel.site,
                                remoteId
                        ),
                        actionsEnabled = areActionsEnabled,
                        progressBarUiState = progressBarUiState,
                        showOverlay = showOverlay,
                        author = post.authorDisplayName
                )
            PageStatus.DRAFT, PageStatus.PENDING -> DraftPage(
                    remoteId = remoteId,
                    localId = pageId,
                    title = title,
                    date = date,
                    labels = labels,
                    labelsColor = labelColor,
                    actions = pageListItemActionsUseCase.setupPageActions(
                            DRAFTS,
                            uploadUiState,
                            pagesViewModel.site,
                            remoteId
                    ),
                    actionsEnabled = areActionsEnabled,
                    progressBarUiState = progressBarUiState,
                    showOverlay = showOverlay,
                    author = post.authorDisplayName
            )
            PageStatus.TRASHED -> TrashedPage(
                    remoteId = remoteId,
                    localId = pageId,
                    title = title,
                    date = date,
                    labels = labels,
                    labelsColor = labelColor,
                    actions = pageListItemActionsUseCase.setupPageActions(
                            TRASHED,
                            uploadUiState,
                            pagesViewModel.site,
                            remoteId
                    ),
                    actionsEnabled = areActionsEnabled,
                    progressBarUiState = progressBarUiState,
                    showOverlay = showOverlay,
                    author = post.authorDisplayName
            )
            PageStatus.SCHEDULED -> ScheduledPage(
                    remoteId = remoteId,
                    localId = pageId,
                    title = title,
                    date = date,
                    labels = labels,
                    labelsColor = labelColor,
                    actions = pageListItemActionsUseCase.setupPageActions(
                            SCHEDULED,
                            uploadUiState,
                            pagesViewModel.site,
                            remoteId
                    ),
                    actionsEnabled = areActionsEnabled,
                    progressBarUiState = progressBarUiState,
                    showOverlay = showOverlay,
                    author = post.authorDisplayName
            )
        }
    }
}
