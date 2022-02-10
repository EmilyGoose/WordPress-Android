package org.wordpress.android.ui.comments.viewmodels

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doNothing
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.InternalCoroutinesApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.wordpress.android.BaseUnitTest
import org.wordpress.android.R
import org.wordpress.android.TEST_DISPATCHER
import org.wordpress.android.datasets.wrappers.NotificationsTableWrapper
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.persistence.comments.CommentsDao.CommentEntity
import org.wordpress.android.fluxc.store.CommentStore.CommentError
import org.wordpress.android.fluxc.store.CommentStore.CommentErrorType.GENERIC_ERROR
import org.wordpress.android.fluxc.store.CommentsStore
import org.wordpress.android.fluxc.store.CommentsStore.CommentsActionPayload
import org.wordpress.android.fluxc.store.CommentsStore.CommentsData.CommentsActionData
import org.wordpress.android.models.Note
import org.wordpress.android.models.usecases.LocalCommentCacheUpdateHandler
import org.wordpress.android.test
import org.wordpress.android.ui.comments.unified.CommentEssentials
import org.wordpress.android.ui.comments.unified.CommentIdentifier.NotificationCommentIdentifier
import org.wordpress.android.ui.comments.unified.CommentIdentifier.ReaderCommentIdentifier
import org.wordpress.android.ui.comments.unified.CommentIdentifier.SiteCommentIdentifier
import org.wordpress.android.ui.comments.unified.UnifiedCommentsEditViewModel
import org.wordpress.android.ui.comments.unified.UnifiedCommentsEditViewModel.EditCommentActionEvent
import org.wordpress.android.ui.comments.unified.UnifiedCommentsEditViewModel.EditCommentActionEvent.CANCEL_EDIT_CONFIRM
import org.wordpress.android.ui.comments.unified.UnifiedCommentsEditViewModel.EditCommentActionEvent.CLOSE
import org.wordpress.android.ui.comments.unified.UnifiedCommentsEditViewModel.EditCommentActionEvent.DONE
import org.wordpress.android.ui.comments.unified.UnifiedCommentsEditViewModel.EditCommentUiState
import org.wordpress.android.ui.comments.unified.UnifiedCommentsEditViewModel.FieldType
import org.wordpress.android.ui.comments.unified.UnifiedCommentsEditViewModel.FieldType.USER_EMAIL
import org.wordpress.android.ui.comments.unified.usecase.GetCommentUseCase
import org.wordpress.android.ui.pages.SnackbarMessageHolder
import org.wordpress.android.ui.utils.UiString.UiStringRes
import org.wordpress.android.util.NetworkUtilsWrapper
import org.wordpress.android.viewmodel.ResourceProvider

@InternalCoroutinesApi
class UnifiedCommentsEditViewModelTest : BaseUnitTest() {
    @Mock lateinit var commentsStore: CommentsStore
    @Mock lateinit var resourceProvider: ResourceProvider
    @Mock lateinit var networkUtilsWrapper: NetworkUtilsWrapper
    @Mock lateinit var getCommentUseCase: GetCommentUseCase
    @Mock lateinit var notificationsTableWrapper: NotificationsTableWrapper
    @Mock private lateinit var localCommentCacheUpdateHandler: LocalCommentCacheUpdateHandler

    private lateinit var viewModel: UnifiedCommentsEditViewModel

    private var uiState: MutableList<EditCommentUiState> = mutableListOf()
    private var uiActionEvent: MutableList<EditCommentActionEvent> = mutableListOf()
    private var onSnackbarMessage: MutableList<SnackbarMessageHolder> = mutableListOf()

    private val site = SiteModel().apply {
        id = LOCAL_SITE_ID
    }

    private val localCommentId = 1000
    private val remoteCommentId = 4321L
    private val siteCommentIdentifier = SiteCommentIdentifier(localCommentId, remoteCommentId)
    private val notificationCommentIdentifier = NotificationCommentIdentifier("noteId", remoteCommentId)

    @Before
    fun setup() = test {
        whenever(networkUtilsWrapper.isNetworkAvailable())
                .thenReturn(true)
        whenever(getCommentUseCase.execute(site, remoteCommentId))
                .thenReturn(COMMENT_ENTITY)

        viewModel = UnifiedCommentsEditViewModel(
                mainDispatcher = TEST_DISPATCHER,
                bgDispatcher = TEST_DISPATCHER,
                commentsStore = commentsStore,
                resourceProvider = resourceProvider,
                networkUtilsWrapper = networkUtilsWrapper,
                localCommentCacheUpdateHandler = localCommentCacheUpdateHandler,
                getCommentUseCase = getCommentUseCase,
                notificationsTableWrapper = notificationsTableWrapper
        )

        setupObservers()
    }

    @Test
    fun `watchers are init on view recreation`() {
        viewModel.start(site, siteCommentIdentifier)

        viewModel.start(site, siteCommentIdentifier)

        assertThat(uiState.first().shouldInitWatchers).isFalse
        assertThat(uiState.last().shouldInitWatchers).isTrue
    }

    @Test
    fun `Should display error SnackBar if mapped CommentEssentials is NOT VALID`() = test {
        whenever(getCommentUseCase.execute(site, remoteCommentId))
                .thenReturn(null)
        viewModel.start(site, siteCommentIdentifier)
        assertThat(onSnackbarMessage.firstOrNull()).isNotNull
    }

    @Test
    fun `Should display correct SnackBar error message if mapped CommentEssentials is NOT VALID`() = test {
        whenever(getCommentUseCase.execute(site, remoteCommentId))
                .thenReturn(null)
        viewModel.start(site, siteCommentIdentifier)
        val expected = UiStringRes(R.string.error_load_comment)
        val actual = onSnackbarMessage.first().message
        assertEquals(expected, actual)
    }

    @Test
    fun `Should show and hide progress after start`() = test {
        viewModel.start(site, siteCommentIdentifier)

        assertThat(uiState[0].showProgress).isTrue
        assertThat(uiState[2].showProgress).isFalse
    }

    @Test
    fun `Should get comment from GetCommentUseCase`() = test {
        viewModel.start(site, siteCommentIdentifier)
        verify(getCommentUseCase).execute(site, remoteCommentId)
    }

    @Test
    fun `Should map CommentIdentifier to CommentEssentials`() = test {
        viewModel.start(site, siteCommentIdentifier)
        assertThat(uiState[1].editedComment).isEqualTo(COMMENT_ESSENTIALS)
    }

    @Test
    fun `Should map CommentIdentifier to default CommentEssentials if CommentIdentifier comment not found`() = test {
        whenever(getCommentUseCase.execute(site, remoteCommentId))
                .thenReturn(null)
        viewModel.start(site, siteCommentIdentifier)
        assertThat(uiState[1].editedComment).isEqualTo(CommentEssentials())
    }

    @Test
    fun `Should map CommentIdentifier to default CommentEssentials if CommentIdentifier not handled`() = test {
        // ReaderCommentIdentifier is not supported by this class yet
        viewModel.start(site, ReaderCommentIdentifier(0L, 0L, 0L))
        assertThat(uiState[1].editedComment).isEqualTo(CommentEssentials())
    }

    @Test
    fun `onActionMenuClicked triggers snackbar if no network`() = test {
        whenever(networkUtilsWrapper.isNetworkAvailable())
                .thenReturn(false)
        viewModel.onActionMenuClicked()
        assertThat(onSnackbarMessage.firstOrNull()).isNotNull
    }

    @Test
    fun `onActionMenuClicked triggers snackbar if comment update error`() = test {
        whenever(commentsStore.getCommentByLocalSiteAndRemoteId(site.id, remoteCommentId))
                .thenReturn(listOf(COMMENT_ENTITY))
        whenever(commentsStore.updateEditComment(eq(site), any()))
                .thenReturn(CommentsActionPayload(CommentError(GENERIC_ERROR, "error")))
        viewModel.start(site, siteCommentIdentifier)
        viewModel.onActionMenuClicked()
        assertThat(onSnackbarMessage.firstOrNull()).isNotNull
    }

    @Test
    fun `onActionMenuClicked triggers DONE action if comment update successfully`() = test {
        whenever(commentsStore.getCommentByLocalSiteAndRemoteId(site.id, remoteCommentId))
                .thenReturn(listOf(COMMENT_ENTITY))
        whenever(commentsStore.updateEditComment(eq(site), any()))
                .thenReturn(CommentsActionPayload(CommentsActionData(emptyList(), 0)))
        viewModel.start(site, siteCommentIdentifier)
        viewModel.onActionMenuClicked()
        assertThat(uiActionEvent.firstOrNull()).isEqualTo(DONE)
        verify(localCommentCacheUpdateHandler).requestCommentsUpdate()
    }

    @Test
    fun `onBackPressed triggers CLOSE when no edits`() {
        viewModel.start(site, siteCommentIdentifier)
        viewModel.onBackPressed()
        assertThat(uiActionEvent.firstOrNull()).isEqualTo(CLOSE)
    }

    @Test
    fun `onBackPressed triggers CANCEL_EDIT_CONFIRM when edits are present`() {
        val emailFieldType: FieldType = mock()
        whenever(emailFieldType.matches(USER_EMAIL))
                .thenReturn(true)
        whenever(emailFieldType.isValid)
                .thenReturn { true }

        viewModel.start(site, siteCommentIdentifier)
        viewModel.onValidateField("edited user email", emailFieldType)
        viewModel.onBackPressed()

        assertThat(uiActionEvent.firstOrNull()).isEqualTo(CANCEL_EDIT_CONFIRM)
    }

    @Test
    fun `onConfirmEditingDiscard triggers CLOSE`() {
        viewModel.onConfirmEditingDiscard()
        assertThat(uiActionEvent.firstOrNull()).isEqualTo(CLOSE)
    }

    //    private fun mapInputSettings() = InputSettings(
//            enableEditName = commentIdentifier !is NotificationCommentIdentifier,
//            enableEditUrl = commentIdentifier !is NotificationCommentIdentifier,
//            enableEditEmail = commentIdentifier !is NotificationCommentIdentifier,
//            enableEditComment = true
//    )

    @Test
    fun `Should ENABLE edit name for SiteCommentIdentifier`() {
        viewModel.start(site, SiteCommentIdentifier(0, 0L))
        assertThat(uiState.first().inputSettings.enableEditName).isTrue
    }

    @Test
    fun `Should DISABLE edit name for NotificationCommentIdentifier`() {
        viewModel.start(site, NotificationCommentIdentifier("id", 0L))
        assertThat(uiState.first().inputSettings.enableEditName).isFalse
    }

    @Test
    fun `Should ENABLE edit URL for SiteCommentIdentifier`() {
        viewModel.start(site, SiteCommentIdentifier(0, 0L))
        assertThat(uiState.first().inputSettings.enableEditUrl).isTrue
    }

    @Test
    fun `Should DISABLE edit URL for NotificationCommentIdentifier`() {
        viewModel.start(site, NotificationCommentIdentifier("id", 0L))
        assertThat(uiState.first().inputSettings.enableEditUrl).isFalse
    }

    @Test
    fun `Should ENABLE edit email for SiteCommentIdentifier`() {
        viewModel.start(site, SiteCommentIdentifier(0, 0L))
        assertThat(uiState.first().inputSettings.enableEditEmail).isTrue
    }

    @Test
    fun `Should DISABLE edit email for NotificationCommentIdentifier`() {
        viewModel.start(site, NotificationCommentIdentifier("id", 0L))
        assertThat(uiState.first().inputSettings.enableEditEmail).isFalse
    }

    @Test
    fun `Should ENABLE edit comment content for SiteCommentIdentifier`() {
        viewModel.start(site, SiteCommentIdentifier(0, 0L))
        assertThat(uiState.first().inputSettings.enableEditComment).isTrue
    }

    @Test
    fun `Should ENABLE edit comment content for NotificationCommentIdentifier`() {
        viewModel.start(site, NotificationCommentIdentifier("id", 0L))
        assertThat(uiState.first().inputSettings.enableEditComment).isTrue
    }

    @Test
    fun `Should get note from local DB for NotificationCommentIdentifier when onActionMenuClicked is called`() = test {
        val note = mock<Note>()
        whenever(commentsStore.updateEditComment(eq(site), any()))
                .thenReturn(CommentsActionPayload(CommentsActionData(emptyList(), 0)))
        whenever(commentsStore.getCommentByLocalSiteAndRemoteId(site.id, remoteCommentId))
                .thenReturn(listOf(COMMENT_ENTITY))
        whenever(notificationsTableWrapper.getNoteById(notificationCommentIdentifier.noteId))
                .thenReturn(note)
        doNothing().whenever(note).setCommentText(any())
        viewModel.start(site, notificationCommentIdentifier)
        viewModel.onActionMenuClicked()
        verify(notificationsTableWrapper).updateNote(note)
    }

    @Test
    fun `Should update note in local DB for NotificationCommentIdentifier when onActionMenuClicked is called`() = test {
        whenever(commentsStore.updateEditComment(eq(site), any()))
                .thenReturn(CommentsActionPayload(CommentsActionData(emptyList(), 0)))
        whenever(commentsStore.getCommentByLocalSiteAndRemoteId(site.id, remoteCommentId))
                .thenReturn(listOf(COMMENT_ENTITY))
        viewModel.start(site, notificationCommentIdentifier)
        viewModel.onActionMenuClicked()
        verify(notificationsTableWrapper).getNoteById(notificationCommentIdentifier.noteId)
    }

    @Test
    fun `Should NOT update note in local DB for NotificationCommentIdentifier when note is null`() = test {
        whenever(commentsStore.getCommentByLocalSiteAndRemoteId(site.id, remoteCommentId))
                .thenReturn(listOf(COMMENT_ENTITY))
        viewModel.start(site, notificationCommentIdentifier)
        viewModel.onActionMenuClicked()
        verify(notificationsTableWrapper, times(0)).updateNote(any())
    }

    @Test
    fun `Should NOT get note for SiteCommentIdentifier when onActionMenuClicked is called`() = test {
        whenever(commentsStore.getCommentByLocalSiteAndRemoteId(site.id, remoteCommentId))
                .thenReturn(listOf(COMMENT_ENTITY))
        viewModel.start(site, siteCommentIdentifier)
        viewModel.onActionMenuClicked()
        verify(notificationsTableWrapper, times(0)).getNoteById(notificationCommentIdentifier.noteId)
    }

    @Test
    fun `Should NOT update note for SiteCommentIdentifier when onActionMenuClicked is called`() = test {
        viewModel.onActionMenuClicked()
        verify(notificationsTableWrapper, times(0)).getNoteById(notificationCommentIdentifier.noteId)
    }

    private fun setupObservers() {
        uiState.clear()
        uiActionEvent.clear()
        onSnackbarMessage.clear()

        viewModel.uiState.observeForever {
            uiState.add(it)
        }

        viewModel.uiActionEvent.observeForever {
            it.applyIfNotHandled {
                uiActionEvent.add(this)
            }
        }

        viewModel.onSnackbarMessage.observeForever {
            it.applyIfNotHandled {
                onSnackbarMessage.add(this)
            }
        }
    }

    companion object {
        private const val LOCAL_SITE_ID = 123

        private val COMMENT_ENTITY = CommentEntity(
                id = 1000,
                remoteCommentId = 0,
                remotePostId = 0,
                remoteParentCommentId = 0,
                localSiteId = LOCAL_SITE_ID,
                remoteSiteId = 0,
                authorUrl = "authorUrl",
                authorName = "authorName",
                authorEmail = "authorEmail",
                authorProfileImageUrl = null,
                postTitle = null,
                status = null,
                datePublished = null,
                publishedTimestamp = 0,
                content = "content",
                url = null,
                hasParent = false,
                parentId = 0,
                iLike = false
        )

        private val COMMENT_ESSENTIALS = CommentEssentials(
                commentId = COMMENT_ENTITY.id,
                userName = COMMENT_ENTITY.authorName!!,
                commentText = COMMENT_ENTITY.content!!,
                userUrl = COMMENT_ENTITY.authorUrl!!,
                userEmail = COMMENT_ENTITY.authorEmail!!
        )
    }
}
