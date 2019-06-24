package org.wordpress.android.ui

import android.app.Activity
import android.content.Context
import org.wordpress.android.fluxc.model.PostModel
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.ui.posts.RemotePreviewLogicHelper.RemotePreviewType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injectable wrapper around ActivityLauncher.
 *
 * ActivityLauncher interface is consisted of static methods, which make the client code difficult to test/mock.
 * Main purpose of this wrapper is to make testing easier.
 *
 */
@Singleton
class ActivityLauncherWrapper @Inject constructor() {
    fun showActionableEmptyView(
        context: Context,
        actionableState: WPWebViewActivity.ActionableReusableState
    ) = ActivityLauncher.showActionableEmptyView(context, actionableState)

    fun previewPostOrPageForResult(
        activity: Activity,
        site: SiteModel,
        post: PostModel,
        remotePreviewType: RemotePreviewType
    ) = ActivityLauncher.previewPostOrPageForResult(activity, site, post, remotePreviewType)
}
