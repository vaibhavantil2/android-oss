package com.kickstarter.viewmodels

import com.kickstarter.ui.data.ProjectData.Companion.builder
import com.kickstarter.libs.utils.extensions.isTrue
import com.kickstarter.ui.adapters.DiscoveryAdapter
import com.kickstarter.services.DiscoveryParams
import com.kickstarter.ui.data.Editorial
import kotlin.Triple
import com.kickstarter.ui.fragments.DiscoveryFragment
import com.kickstarter.services.ApiClientType
import com.kickstarter.libs.preferences.IntPreferenceType
import android.content.SharedPreferences
import android.util.Pair
import com.kickstarter.libs.*
import com.kickstarter.libs.models.OptimizelyFeature
import com.kickstarter.services.apiresponses.ActivityEnvelope
import com.kickstarter.libs.utils.ListUtils
import com.kickstarter.libs.utils.ObjectUtils
import com.kickstarter.libs.rx.transformers.Transformers
import com.kickstarter.ui.viewholders.ActivitySampleFriendBackingViewHolder
import com.kickstarter.ui.viewholders.ActivitySampleFriendFollowViewHolder
import com.kickstarter.ui.viewholders.ActivitySampleProjectViewHolder
import com.kickstarter.ui.viewholders.DiscoveryOnboardingViewHolder
import rx.functions.Func2
import com.kickstarter.services.apiresponses.DiscoverEnvelope
import rx.functions.Action1
import com.kickstarter.libs.utils.EventContextValues
import com.kickstarter.libs.utils.RefTagUtils
import com.kickstarter.ui.data.ProjectData
import com.kickstarter.libs.utils.ExperimentData
import com.kickstarter.libs.utils.extensions.combineProjectsAndParams
import com.kickstarter.libs.utils.extensions.fillRootCategoryForFeaturedProjects
import com.kickstarter.models.Activity
import com.kickstarter.models.Category
import com.kickstarter.models.Project
import com.kickstarter.models.User
import rx.Observable
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import java.net.CookieManager

interface DiscoveryFragmentViewModel {
    interface Inputs : DiscoveryAdapter.Delegate {
        /** Call when the page content should be cleared.   */
        fun clearPage()

        /** Call when user clicks hearts to start animation.   */
        fun heartContainerClicked()

        /** Call for project pagination.  */
        fun nextPage()

        /** Call when params from Discovery Activity change.  */
        fun paramsFromActivity(params: DiscoveryParams)

        /** Call when the projects should be refreshed.  */
        fun refresh()

        /**  Call when we should load the root categories.  */
        fun rootCategories(rootCategories: List<Category>)
    }

    interface Outputs {
        /**  Emits an activity for the activity sample view.  */
        fun activity(): Observable<Activity>

        /** Emits a boolean indicating whether projects are being fetched from the API.  */
        fun isFetchingProjects(): Observable<Boolean>

        /** Emits a list of projects to display. */
        fun projectList(): Observable<List<Pair<Project, DiscoveryParams>>>

        /** Emits a boolean that determines if an editorial should be shown.  */
        fun shouldShowEditorial(): Observable<Editorial?>

        /** Emits a boolean that determines if the saved empty view should be shown.  */
        fun shouldShowEmptySavedView(): Observable<Boolean>

        /** Emits a boolean that determines if the onboarding view should be shown.  */
        fun shouldShowOnboardingView(): Observable<Boolean>

        /** Emits when the activity feed should be shown.  */
        fun showActivityFeed(): Observable<Boolean>

        /** Emits when the login tout activity should be shown.  */
        fun showLoginTout(): Observable<Boolean>

        /** Emits when the heart animation should play.  */
        fun startHeartAnimation(): Observable<Void>

        /** Emits an Editorial when we should start the [com.kickstarter.ui.activities.EditorialActivity].  */
        fun startEditorialActivity(): Observable<Editorial>

        /** Emits a Project and RefTag pair when we should start the [com.kickstarter.ui.activities.ProjectActivity].  */
        fun startProjectActivity(): Observable<Triple<Project, RefTag, Boolean>>

        /** Emits an activity when we should start the [com.kickstarter.ui.activities.UpdateActivity].  */
        fun startUpdateActivity(): Observable<Activity>
    }

    class ViewModel(environment: Environment) : FragmentViewModel<DiscoveryFragment>(environment),
        Inputs, Outputs {
        @JvmField
        val inputs: Inputs = this
        @JvmField
        val outputs: Outputs = this

        private val apiClient: ApiClientType = environment.apiClient()
        private val currentUser: CurrentUserType = environment.currentUser()
        private val activitySamplePreference: IntPreferenceType = environment.activitySamplePreference()
        private val optimizely: ExperimentsClientType = environment.optimizely()
        private val sharedPreferences: SharedPreferences = environment.sharedPreferences()
        private val cookieManager: CookieManager = environment.cookieManager()

        private val activityClick = PublishSubject.create<Boolean>()
        private val activitySampleProjectClick = PublishSubject.create<Project>()
        private val activityUpdateClick = PublishSubject.create<Activity>()
        private val clearPage = PublishSubject.create<Void>()
        private val discoveryOnboardingLoginToutClick = PublishSubject.create<Boolean>()
        private val editorialClicked = PublishSubject.create<Editorial>()
        private val nextPage = PublishSubject.create<Void>()
        private val paramsFromActivity = PublishSubject.create<DiscoveryParams>()
        private val projectCardClicked = PublishSubject.create<Project>()
        private val refresh = PublishSubject.create<Void>()
        private val rootCategories = PublishSubject.create<List<Category>>()
        private val activity = BehaviorSubject.create<Activity?>()
        private val heartContainerClicked = BehaviorSubject.create<Void>()
        private val projectList = BehaviorSubject.create<List<Pair<Project, DiscoveryParams>>>()
        private val showActivityFeed: Observable<Boolean>
        private val showLoginTout: Observable<Boolean>
        private val shouldShowEditorial = BehaviorSubject.create<Editorial>()
        private val shouldShowEmptySavedView = BehaviorSubject.create<Boolean>()
        private val shouldShowOnboardingView = BehaviorSubject.create<Boolean>()
        private val startEditorialActivity = PublishSubject.create<Editorial>()
        private val startProjectActivity: Observable<Triple<Project, RefTag, Boolean>>
        private val startUpdateActivity: Observable<Activity>
        private val startHeartAnimation = BehaviorSubject.create<Void>()
        private val isFetchingProjects = BehaviorSubject.create<Boolean>()

        init {

            val changedUser = currentUser.observable()
                .distinctUntilChanged()

            val userIsLoggedIn = currentUser.isLoggedIn
                .distinctUntilChanged()

            val selectedParams = Observable.combineLatest(
                changedUser,
                paramsFromActivity.distinctUntilChanged()
            ) { _: User?, params: DiscoveryParams? -> params }
                .filter { ObjectUtils.isNotNull(it) }
                .map { requireNotNull(it) }

            val startOverWith = Observable.merge(
                selectedParams,
                selectedParams.compose(Transformers.takeWhen(refresh))
            )

            val paginator = ApiPaginator.builder<Project, DiscoverEnvelope, DiscoveryParams?>()
                .nextPage(nextPage)
                .startOverWith(startOverWith)
                .envelopeToListOfData { obj: DiscoverEnvelope ->
                    obj.projects()
                }
                .envelopeToMoreUrl { env: DiscoverEnvelope ->
                    env.urls().api().moreProjects()
                }
                .loadWithParams { params: DiscoveryParams ->
                    apiClient.fetchProjects(params)
                }
                .loadWithPaginationPath { paginationUrl: String ->
                    apiClient.fetchProjects(paginationUrl)
                }
                .clearWhenStartingOver(false)
                .concater { xs: List<Project>, ys: List<Project> ->
                    ListUtils.concatDistinct(xs, ys)
                }
                .build()

            paginator.isFetching
                .compose(bindToLifecycle())
                .subscribe(isFetchingProjects)

            projectList
                .compose(Transformers.ignoreValues())
                .compose(bindToLifecycle())
                .subscribe {  isFetchingProjects.onNext(false) }

            val activitySampleProjectClick = activitySampleProjectClick
                .map<Pair<Project, RefTag>> { p: Project ->
                    Pair.create(
                        p,
                        RefTag.activitySample()
                    )
                }

            projectCardClicked
                .compose(bindToLifecycle())
                .subscribe { p: Project ->
                    analyticEvents.trackProjectCardClicked(
                        p, EventContextValues.ContextPageName.DISCOVER.contextName
                    )
                }
            paramsFromActivity
                .compose(Transformers.takePairWhen(projectCardClicked))
                .compose(bindToLifecycle())
                .subscribe { it: Pair<DiscoveryParams, Project> ->
                    val refTag =
                        RefTagUtils.projectAndRefTagFromParamsAndProject(it.first, it.second)
                    val cookieRefTag = RefTagUtils.storedCookieRefTagForProject(
                        it.second,
                        cookieManager,
                        sharedPreferences
                    )
                    val projectData = builder()
                        .refTagFromIntent(refTag.second)
                        .refTagFromCookie(cookieRefTag)
                        .project(it.second)
                        .build()
                    analyticEvents.trackDiscoverProjectCtaClicked(it.first, projectData)
                }

            val projectCardClick = paramsFromActivity
                .compose(Transformers.takePairWhen(projectCardClicked))
                .map { pp: Pair<DiscoveryParams, Project> ->
                    RefTagUtils.projectAndRefTagFromParamsAndProject(
                        pp.first, pp.second
                    )
                }

            val projects = Observable.combineLatest(
                paginator.paginatedData(),
                rootCategories) { listProjects: List<Project>, categories ->
                listProjects.fillRootCategoryForFeaturedProjects(categories)
            }

            Observable.combineLatest(
                projects,
                selectedParams.distinctUntilChanged()) { projects: List<Project>, params: DiscoveryParams ->
                return@combineLatest combineProjectsAndParams(projects, params)
            }
                .compose(bindToLifecycle())
                .subscribe {
                    this.projectList
                }

            showActivityFeed = activityClick
            startUpdateActivity = activityUpdateClick
            showLoginTout = discoveryOnboardingLoginToutClick

            val isProjectPageEnabled = Observable.just(
                optimizely.isFeatureEnabled(
                    OptimizelyFeature.Key.PROJECT_PAGE_V2
                )
            )

            startProjectActivity = Observable.merge(
                activitySampleProjectClick,
                projectCardClick
            )
                .withLatestFrom(isProjectPageEnabled) { a: Pair<Project, RefTag>, b: Boolean ->
                    Triple(
                        a.first,
                        a.second,
                        b
                    )
                }

            clearPage
                .compose(bindToLifecycle())
                .subscribe { _: Void? ->
                    shouldShowOnboardingView.onNext(false)
                    activity.onNext(null)
                    projectList.onNext(emptyList())
                }

            val userWhenOptimizelyReady = Observable.merge(
                changedUser,
                changedUser.compose(Transformers.takeWhen(optimizelyReady))
            )

            val lightsOnEnabled = userWhenOptimizelyReady
                .map { user: User? ->
                    optimizely.isFeatureEnabled(
                        OptimizelyFeature.Key.LIGHTS_ON,
                        ExperimentData(user, null, null)
                    )
                }
                .distinctUntilChanged()

            currentUser.observable()
                .compose(Transformers.combineLatestPair(paramsFromActivity))
                .compose(Transformers.combineLatestPair(lightsOnEnabled))
                .map { defaultParamsAndEnabled: Pair<Pair<User, DiscoveryParams>, Boolean> ->
                    isDefaultParams(
                        defaultParamsAndEnabled.first
                    ) && defaultParamsAndEnabled.second.isTrue()
                }
                .map { shouldShow: Boolean -> if (shouldShow) Editorial.LIGHTS_ON else null }
                .compose(bindToLifecycle())
                .subscribe(shouldShowEditorial)

            editorialClicked
                .compose(bindToLifecycle())
                .subscribe(startEditorialActivity)

            paramsFromActivity
                .compose(Transformers.combineLatestPair(userIsLoggedIn))
                .map { pu: Pair<DiscoveryParams, Boolean> ->
                    isOnboardingVisible(
                        pu.first,
                        pu.second
                    )
                }
                .compose(bindToLifecycle())
                .subscribe(shouldShowOnboardingView)

            paramsFromActivity
                .map { params: DiscoveryParams -> isSavedVisible(params) }
                .compose<Pair<Boolean, List<Pair<Project, DiscoveryParams>>>>(
                    Transformers.combineLatestPair(
                        projectList
                    )
                )
                .map { savedAndProjects: Pair<Boolean, List<Pair<Project, DiscoveryParams>>> ->
                    savedAndProjects.first && savedAndProjects.second.isEmpty()
                }
                .compose(bindToLifecycle())
                .distinctUntilChanged()
                .subscribe(shouldShowEmptySavedView)
            shouldShowEmptySavedView
                .filter{ it == true }
                .map<Any> { null }
                .mergeWith(heartContainerClicked)
                .subscribe {
                    startHeartAnimation.onNext(null)
                }

            val loggedInUserAndParams = currentUser.loggedInUser()
                .distinctUntilChanged()
                .compose(Transformers.combineLatestPair(paramsFromActivity))

            // Activity should show on the user's default params
            loggedInUserAndParams
                .filter { userAndParams: Pair<User, DiscoveryParams> ->
                    isDefaultParams(
                        userAndParams
                    )
                }
                .flatMap { fetchActivity() }
                .filter { activity: Activity? -> activityHasNotBeenSeen(activity) }
                .doOnNext { activity: Activity? -> saveLastSeenActivityId(activity) }
                .compose(bindToLifecycle())
                .subscribe(activity)

            // Clear activity sample when params change from default
            loggedInUserAndParams
                .filter { userAndParams: Pair<User, DiscoveryParams> ->
                    !isDefaultParams(
                        userAndParams
                    )
                }
                .map { null }
                .compose(bindToLifecycle())
                .subscribe(activity)

            paramsFromActivity
                .compose(
                    Transformers.combineLatestPair(
                        paginator.loadingPage().distinctUntilChanged()
                    )
                )
                .filter { paramsAndPage: Pair<DiscoveryParams?, Int> -> paramsAndPage.second == 1 }
                .compose(bindToLifecycle())
                .subscribe { paramsAndLoggedIn: Pair<DiscoveryParams, Int> ->
                    analyticEvents.trackDiscoveryPageViewed(
                        paramsAndLoggedIn.first
                    )
                }

            discoveryOnboardingLoginToutClick
                .compose(bindToLifecycle())
                .subscribe {
                    analyticEvents.trackLoginOrSignUpCtaClicked(
                        null,
                        EventContextValues.ContextPageName.DISCOVER.contextName
                    )
                }
        }

        private fun activityHasNotBeenSeen(activity: Activity?): Boolean {
            return activity != null && activity.id() != activitySamplePreference.get().toLong()
        }

        private fun fetchActivity(): Observable<Activity?> {
            return apiClient.fetchActivities(1)
                .map {  it.activities() }
                .map { ListUtils.first(it) }
                .filter { ObjectUtils.isNotNull(it) }
                .compose(Transformers.neverError())
        }

        private fun isDefaultParams(userAndParams: Pair<User, DiscoveryParams>): Boolean {
            val discoveryParams = userAndParams.second
            val user = userAndParams.first
            return discoveryParams == DiscoveryParams.getDefaultParams(user)
        }

        private fun isOnboardingVisible(params: DiscoveryParams, isLoggedIn: Boolean): Boolean {
            val sort = params.sort()
            val isSortHome = DiscoveryParams.Sort.MAGIC == sort
            return params.isAllProjects.isTrue() && isSortHome && !isLoggedIn
        }

        private fun isSavedVisible(params: DiscoveryParams): Boolean {
            return params.isSavedProjects
        }

        private fun saveLastSeenActivityId(activity: Activity?) {
            if (activity != null) {
                activitySamplePreference.set(activity.id().toInt())
            }
        }

        // - inputs
        override fun activitySampleFriendBackingViewHolderProjectClicked(
            viewHolder: ActivitySampleFriendBackingViewHolder,
            project: Project?
        ) {
            activitySampleProjectClick.onNext(project)
        }

        override fun activitySampleFriendBackingViewHolderSeeActivityClicked(viewHolder: ActivitySampleFriendBackingViewHolder) {
            activityClick.onNext(true)
        }

        override fun activitySampleFriendFollowViewHolderSeeActivityClicked(viewHolder: ActivitySampleFriendFollowViewHolder) {
            activityClick.onNext(true)
        }

        override fun activitySampleProjectViewHolderProjectClicked(
            viewHolder: ActivitySampleProjectViewHolder,
            project: Project?
        ) {
            activitySampleProjectClick.onNext(project)
        }

        override fun activitySampleProjectViewHolderSeeActivityClicked(viewHolder: ActivitySampleProjectViewHolder) {
            activityClick.onNext(true)
        }

        override fun activitySampleProjectViewHolderUpdateClicked(
            viewHolder: ActivitySampleProjectViewHolder,
            activity: Activity?
        ) {
            activityUpdateClick.onNext(activity)
        }

        override fun editorialViewHolderClicked(editorial: Editorial) {
            editorialClicked.onNext(editorial)
        }

        override fun projectCardViewHolderClicked(project: Project?) {
            projectCardClicked.onNext(project)
        }

        override fun refresh() {
            refresh.onNext(null)
        }

        override fun rootCategories(rootCategories: List<Category>) {
            this.rootCategories.onNext(rootCategories)
        }

        override fun clearPage() {
            clearPage.onNext(null)
        }

        override fun heartContainerClicked() {
            heartContainerClicked.onNext(null)
        }

        override fun discoveryOnboardingViewHolderLoginToutClick(viewHolder: DiscoveryOnboardingViewHolder?) {
            discoveryOnboardingLoginToutClick.onNext(true)
        }

        override fun nextPage() {
            nextPage.onNext(null)
        }

        override fun paramsFromActivity(params: DiscoveryParams) {
            paramsFromActivity.onNext(params)
        }


        // - Outputs

        override fun activity(): Observable<Activity> {
            return activity
        }

        override fun isFetchingProjects(): Observable<Boolean> {
            return this.isFetchingProjects
        }

        override fun projectList(): Observable<List<Pair<Project, DiscoveryParams>>> {
            return projectList
        }

        override fun showActivityFeed(): Observable<Boolean> {
            return showActivityFeed
        }

        override fun showLoginTout(): Observable<Boolean> {
            return showLoginTout
        }

        override fun shouldShowEditorial(): Observable<Editorial?> {
            return shouldShowEditorial
        }

        override fun shouldShowEmptySavedView(): Observable<Boolean> {
            return shouldShowEmptySavedView
        }

        override fun startHeartAnimation(): Observable<Void> {
            return startHeartAnimation
        }

        override fun startEditorialActivity(): Observable<Editorial> {
            return startEditorialActivity
        }

        override fun startProjectActivity(): Observable<Triple<Project, RefTag, Boolean>> {
            return startProjectActivity
        }

        override fun shouldShowOnboardingView(): Observable<Boolean> {
            return shouldShowOnboardingView
        }

        override fun startUpdateActivity(): Observable<Activity> {
            return startUpdateActivity
        }
    }
}