package com.kickstarter.libs

import android.content.Context
import rx.subscriptions.CompositeSubscription
import android.content.Intent
import androidx.annotation.CallSuper
import android.os.Bundle
import android.util.Pair
import timber.log.Timber
import com.trello.rxlifecycle.ActivityEvent
import com.kickstarter.ui.data.ActivityResult
import rx.Observable
import rx.subjects.PublishSubject

open class ActivityViewModel<ViewType : ActivityLifecycleType>(environment: Environment) {
    private val viewChange: PublishSubject<ViewType> = PublishSubject.create()
    private val view = viewChange.filter { v: ViewType? -> v != null }
    private val subscriptions = CompositeSubscription()
    private val activityResult = PublishSubject.create<ActivityResult>()
    private val intent = PublishSubject.create<Intent>()
    @JvmField protected val analyticEvents: AnalyticEvents = environment.analytics()

    /**
     * Takes activity result data from the activity.
     */
    fun activityResult(activityResult: ActivityResult) {
        this.activityResult.onNext(activityResult)
    }

    /**
     * Takes intent data from the view.
     */
    fun intent(intent: Intent) {
        this.intent.onNext(intent)
    }

    @CallSuper
    fun onCreate(context: Context, savedInstanceState: Bundle?) {
        Timber.d("onCreate %s", this.toString())
        dropView()
    }

    @CallSuper
    fun onResume(view: ViewType) {
        Timber.d("onResume %s", this.toString())
        onTakeView(view)
    }

    @CallSuper
    fun onPause() {
        Timber.d("onPause %s", this.toString())
        dropView()
    }

    @CallSuper
    fun onDestroy() {
        Timber.d("onDestroy %s", this.toString())
        subscriptions.clear()
        viewChange.onCompleted()
    }

    private fun onTakeView(view: ViewType) {
        Timber.d("onTakeView %s %s", this.toString(), view.toString())
        viewChange.onNext(view)
    }

    private fun dropView() {
        Timber.d("dropView %s", this.toString())
        viewChange.onNext(null)
    }

    protected fun activityResult(): Observable<ActivityResult> {
        return activityResult
    }

    protected fun intent(): Observable<Intent> {
        return intent
    }

    /**
     * By composing this transformer with an observable you guarantee that every observable in your view model
     * will be properly completed when the view model completes.
     *
     * It is required that *every* observable in a view model do `.compose(bindToLifecycle())` before calling
     * `subscribe`.
     */
    fun <T> bindToLifecycle(): Observable.Transformer<T, T> {
        return Observable.Transformer { source: Observable<T> ->
            source.takeUntil(
                view.switchMap { v: ViewType ->
                    v?.lifecycle()?.map { e: ActivityEvent -> Pair.create(v, e) }
                }
                    .filter{
                        isFinished(
                            it.first,
                            it.second
                        )
                    }
            )
        }
    }

    /**
     * Determines from a view and lifecycle event if the view's life is over.
     */
    private fun isFinished(view: ViewType, event: ActivityEvent): Boolean {
        return if (view is BaseActivity<*>) {
            event == ActivityEvent.DESTROY && (view as BaseActivity<*>).isFinishing
        } else event == ActivityEvent.DESTROY
    }

}