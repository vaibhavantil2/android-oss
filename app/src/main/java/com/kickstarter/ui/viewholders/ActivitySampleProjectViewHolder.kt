package com.kickstarter.ui.viewholders

import com.kickstarter.R
import com.kickstarter.databinding.ActivitySampleProjectViewBinding
import com.kickstarter.libs.rx.transformers.Transformers
import com.kickstarter.libs.utils.ObjectUtils
import com.kickstarter.models.Activity
import com.kickstarter.models.Project
import com.kickstarter.viewmodels.ActivitySampleProjectViewHolderViewModel
import com.squareup.picasso.Picasso

class ActivitySampleProjectViewHolder(
    private val binding: ActivitySampleProjectViewBinding,
    private val delegate: Delegate
) : KSViewHolder(binding.root) {

    private val ksString = environment().ksString()
    private val vm: ActivitySampleProjectViewHolderViewModel.ViewModel =
        ActivitySampleProjectViewHolderViewModel.ViewModel(environment())

    interface Delegate {
        fun activitySampleProjectViewHolderSeeActivityClicked(viewHolder: ActivitySampleProjectViewHolder?)
        fun activitySampleProjectViewHolderProjectClicked(viewHolder: ActivitySampleProjectViewHolder, project: Project?)
        fun activitySampleProjectViewHolderUpdateClicked(viewHolder: ActivitySampleProjectViewHolder?, activity: Activity?)
    }

    @Throws(Exception::class)
    override fun bindData(data: Any?) {
        vm.inputs.configureWith(ObjectUtils.requireNonNull(data as Activity?, Activity::class.java))
    }

    init {

        val context = context()
        this.vm.outputs.bindActivity()
            .compose(bindToLifecycle())
            .compose(Transformers.observeForUI())
            .subscribe { activity ->
                activity.project()?.let { project ->
                    val photo = project.photo()
                    photo?.let {
                        Picasso.get()
                            .load(photo.little())
                            .into(binding.activityImage)
                    }
                    binding.activityTitle.text = project.name()
                    val activitySubtitleText = when (activity.category()) {
                        Activity.CATEGORY_FAILURE -> context.getString(R.string.activity_project_was_not_successfully_funded)
                        Activity.CATEGORY_CANCELLATION -> context.getString(R.string.activity_funding_canceled)
                        Activity.CATEGORY_LAUNCH ->
                            activity.user()?.let {
                                ksString.format(context.getString(R.string.activity_user_name_launched_project), "user_name", it.name())
                            }

                        Activity.CATEGORY_SUCCESS -> context.getString(R.string.activity_successfully_funded)
                        Activity.CATEGORY_UPDATE ->
                            activity.update()?.let { update ->
                                ksString.format(
                                    context.getString(R.string.activity_posted_update_number_title),
                                    "update_number", update.sequence().toString(),
                                    "update_title", update.title()
                                )
                            }

                        else -> ""
                    }
                    if (activitySubtitleText?.isNotBlank() == true) {
                        binding.activitySubtitle.text = activitySubtitleText
                    }
                }
                binding.seeActivityButton.setOnClickListener {
                    seeActivityOnClick()
                }
                binding.activityClickArea.setOnClickListener {
                    activityProjectOnClick(activity)
                }
            }
    }

    fun seeActivityOnClick() {
        delegate.activitySampleProjectViewHolderSeeActivityClicked(this)
    }

    fun activityProjectOnClick(activity: Activity) = if (activity?.category() == Activity.CATEGORY_UPDATE) {
        delegate.activitySampleProjectViewHolderUpdateClicked(this, activity)
    } else {
        delegate.activitySampleProjectViewHolderProjectClicked(this, activity?.project())
    }
}
