package com.kickstarter.ui.activities

import com.kickstarter.databinding.ActivityCreatorBioBinding
import com.kickstarter.libs.BaseActivity
import com.kickstarter.libs.qualifiers.RequiresActivityViewModel
import com.kickstarter.viewmodels.CreatorBioViewModel

@RequiresActivityViewModel(CreatorBioViewModel.ViewModel::class)
class DetailCampaignImageActivity: BaseActivity<CreatorBioViewModel.ViewModel>() {
    private lateinit var binding: ActivityCreatorBioBinding
}
