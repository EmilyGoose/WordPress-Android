package org.wordpress.android.ui.quickstart;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.analytics.AnalyticsTracker;
import org.wordpress.android.analytics.AnalyticsTracker.Stat;
import org.wordpress.android.fluxc.store.QuickStartStore;
import org.wordpress.android.fluxc.store.QuickStartStore.QuickStartNewSiteTask;
import org.wordpress.android.fluxc.store.QuickStartStore.QuickStartTask;
import org.wordpress.android.fluxc.store.QuickStartStore.QuickStartTaskType;
import org.wordpress.android.ui.ActionableEmptyView;
import org.wordpress.android.ui.FullScreenDialogFragment.FullScreenDialogContent;
import org.wordpress.android.ui.FullScreenDialogFragment.FullScreenDialogController;
import org.wordpress.android.ui.mysite.SelectedSiteRepository;
import org.wordpress.android.ui.quickstart.QuickStartAdapter.OnQuickStartAdapterActionListener;
import org.wordpress.android.util.AniUtils;
import org.wordpress.android.util.AniUtils.Duration;
import org.wordpress.android.util.QuickStartUtils;
import org.wordpress.android.widgets.WPSnackbar;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static org.wordpress.android.fluxc.store.QuickStartStore.QuickStartTaskType.CUSTOMIZE;
import static org.wordpress.android.fluxc.store.QuickStartStore.QuickStartTaskType.GET_TO_KNOW_APP;
import static org.wordpress.android.fluxc.store.QuickStartStore.QuickStartTaskType.GROW;

public class QuickStartFullScreenDialogFragment extends Fragment implements FullScreenDialogContent,
        OnQuickStartAdapterActionListener {
    private FullScreenDialogController mDialogController;
    private QuickStartAdapter mQuickStartAdapter;
    private ActionableEmptyView mQuickStartCompleteView;

    public static final String KEY_COMPLETED_TASKS_LIST_EXPANDED = "completed_tasks_list_expanded";
    public static final String EXTRA_TYPE = "EXTRA_TYPE";
    public static final String RESULT_TASK = "RESULT_TASK";

    private QuickStartTaskType mTasksType = CUSTOMIZE;

    @Inject protected QuickStartStore mQuickStartStore;
    @Inject protected SelectedSiteRepository mSelectedSiteRepository;

    public static Bundle newBundle(QuickStartTaskType type) {
        Bundle bundle = new Bundle();
        bundle.putSerializable(EXTRA_TYPE, type);
        return bundle;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((WordPress) requireActivity().getApplication()).component().inject(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.quick_start_dialog_fragment, container, false);

        if (getArguments() != null) {
            mTasksType = (QuickStartTaskType) getArguments().getSerializable(EXTRA_TYPE);
        }

        RecyclerView list = rootView.findViewById(R.id.list);
        List<QuickStartTask> tasksUncompleted = new ArrayList<>();
        List<QuickStartTask> tasksCompleted = new ArrayList<>();
        int selectedSiteLocalId = mSelectedSiteRepository.getSelectedSiteLocalId();

        mQuickStartCompleteView = rootView.findViewById(R.id.quick_start_complete_view);

        switch (mTasksType) {
            case CUSTOMIZE:
                tasksUncompleted.addAll(mQuickStartStore.getUncompletedTasksByType(selectedSiteLocalId, CUSTOMIZE));
                tasksCompleted.addAll(mQuickStartStore.getCompletedTasksByType(selectedSiteLocalId, CUSTOMIZE));
                setCompleteViewImage(R.drawable.img_illustration_site_brush_191dp);
                AnalyticsTracker.track(Stat.QUICK_START_TYPE_CUSTOMIZE_VIEWED);
                break;
            case GROW:
                tasksUncompleted.addAll(mQuickStartStore.getUncompletedTasksByType(selectedSiteLocalId, GROW));
                tasksCompleted.addAll(mQuickStartStore.getCompletedTasksByType(selectedSiteLocalId, GROW));
                setCompleteViewImage(R.drawable.img_illustration_site_about_182dp);
                AnalyticsTracker.track(Stat.QUICK_START_TYPE_GROW_VIEWED);
                break;
            case GET_TO_KNOW_APP: // TODO: ashiagr GET_TO_KNOW_APP add analytics
                tasksUncompleted
                        .addAll(mQuickStartStore.getUncompletedTasksByType(selectedSiteLocalId, GET_TO_KNOW_APP));
                tasksCompleted.addAll(mQuickStartStore.getCompletedTasksByType(selectedSiteLocalId, GET_TO_KNOW_APP));
                setCompleteViewImage(R.drawable.img_illustration_site_about_182dp);
                break;
            case UNKNOWN:
                tasksUncompleted.addAll(mQuickStartStore.getUncompletedTasksByType(selectedSiteLocalId, CUSTOMIZE));
                tasksCompleted.addAll(mQuickStartStore.getCompletedTasksByType(selectedSiteLocalId, CUSTOMIZE));
                setCompleteViewImage(R.drawable.img_illustration_site_brush_191dp);
                break;
        }

        boolean isCompletedTasksListExpanded = savedInstanceState != null
                                               && savedInstanceState.getBoolean(KEY_COMPLETED_TASKS_LIST_EXPANDED);

        mQuickStartAdapter = new QuickStartAdapter(
                requireContext(),
                tasksUncompleted,
                tasksCompleted,
                isCompletedTasksListExpanded);

        if (tasksUncompleted.isEmpty()) {
            mQuickStartCompleteView.setVisibility(!isCompletedTasksListExpanded ? View.VISIBLE : View.GONE);
        }

        mQuickStartAdapter.setOnTaskTappedListener(QuickStartFullScreenDialogFragment.this);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(mQuickStartAdapter);
        // Disable default change animations to avoid blinking effect when adapter data is changed.
        ((DefaultItemAnimator) list.getItemAnimator()).setSupportsChangeAnimations(false);

        return rootView;
    }

    @Override
    public void setController(final FullScreenDialogController controller) {
        mDialogController = controller;
    }

    @Override
    public boolean onConfirmClicked(FullScreenDialogController controller) {
        return true;
    }

    @Override
    public boolean onDismissClicked(FullScreenDialogController controller) {
        switch (mTasksType) {
            case CUSTOMIZE:
                AnalyticsTracker.track(Stat.QUICK_START_TYPE_CUSTOMIZE_DISMISSED);
                break;
            case GROW:
                AnalyticsTracker.track(Stat.QUICK_START_TYPE_GROW_DISMISSED);
                break;
            case GET_TO_KNOW_APP: // TODO: ashiagr GET_TO_KNOW_APP add analytics
                break;
            case UNKNOWN:
                // Do not track unknown.
                break;
        }

        controller.dismiss();
        return true;
    }

    @Override
    public void onTaskTapped(QuickStartTask task) {
        AnalyticsTracker.track(QuickStartUtils.getQuickStartListTappedTracker(task));
        if (!showSnackbarIfNeeded(task)) {
            Bundle result = new Bundle();
            result.putSerializable(RESULT_TASK, (Serializable) task);
            mDialogController.confirm(result);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mQuickStartAdapter != null) {
            outState.putBoolean(KEY_COMPLETED_TASKS_LIST_EXPANDED, mQuickStartAdapter.isCompletedTasksListExpanded());
        }
    }

    @Override
    public void onSkipTaskTapped(QuickStartTask task) {
        AnalyticsTracker.track(QuickStartUtils.getQuickStartListSkippedTracker(task));
        int selectedSiteLocalId = mSelectedSiteRepository.getSelectedSiteLocalId();
        mQuickStartStore.setDoneTask(selectedSiteLocalId, task, true);

        if (mQuickStartAdapter != null) {
            List<QuickStartTask> uncompletedTasks =
                    mQuickStartStore.getUncompletedTasksByType(selectedSiteLocalId, mTasksType);

            mQuickStartAdapter.updateContent(
                    uncompletedTasks,
                    mQuickStartStore.getCompletedTasksByType(selectedSiteLocalId, mTasksType));

            if (uncompletedTasks.isEmpty() && !mQuickStartAdapter.isCompletedTasksListExpanded()) {
                toggleCompletedView(true);
            }
        }
    }

    @Override
    public void onCompletedTasksListToggled(boolean isExpanded) {
        switch (mTasksType) {
            case CUSTOMIZE:
                AnalyticsTracker.track(isExpanded ? Stat.QUICK_START_LIST_CUSTOMIZE_EXPANDED
                        : Stat.QUICK_START_LIST_CUSTOMIZE_COLLAPSED);
                break;
            case GROW:
                AnalyticsTracker.track(isExpanded ? Stat.QUICK_START_LIST_GROW_EXPANDED
                        : Stat.QUICK_START_LIST_GROW_COLLAPSED);
                break;
            case GET_TO_KNOW_APP: // TODO: ashiagr GET_TO_KNOW_APP add analytics
                break;
            case UNKNOWN:
                // Do not track unknown.
                break;
        }

        if (mQuickStartStore.getUncompletedTasksByType(
                mSelectedSiteRepository.getSelectedSiteLocalId(),
                mTasksType
        ).isEmpty()) {
            toggleCompletedView(!isExpanded);
        }
    }

    private void setCompleteViewImage(int imageResourceId) {
        mQuickStartCompleteView.image.setImageResource(imageResourceId);
        mQuickStartCompleteView.image.setVisibility(View.VISIBLE);
    }

    private void toggleCompletedView(boolean isVisible) {
        if (isVisible) {
            AniUtils.fadeIn(mQuickStartCompleteView, Duration.SHORT);
        } else {
            AniUtils.fadeOut(mQuickStartCompleteView, Duration.SHORT);
        }
    }

    private boolean showSnackbarIfNeeded(QuickStartTask task) {
        if (task == QuickStartNewSiteTask.CREATE_SITE) {
            WPSnackbar.make(requireView(), R.string.quick_start_list_create_site_message, Snackbar.LENGTH_LONG).show();
            return true;
        } else {
            return false;
        }
    }
}
