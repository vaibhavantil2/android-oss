package com.kickstarter.viewmodels.projectpage

import androidx.annotation.NonNull
import com.kickstarter.libs.ActivityViewModel
import com.kickstarter.libs.Environment
import com.kickstarter.ui.activities.DetailCampaignImageActivity

class DetailCampaignImageActivityViewModel {
    interface Inputs {
    }

    interface Outputs {
    }

    class ViewModel(@NonNull val environment: Environment) : ActivityViewModel<DetailCampaignImageActivity>(environment), Inputs, Outputs {
    }
}