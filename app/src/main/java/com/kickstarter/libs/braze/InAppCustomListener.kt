package com.kickstarter.libs.braze

import android.view.View
import com.braze.models.inappmessage.IInAppMessage
import com.braze.models.inappmessage.MessageButton
import com.braze.ui.inappmessage.InAppMessageCloser
import com.braze.ui.inappmessage.InAppMessageOperation
import com.braze.ui.inappmessage.listeners.IInAppMessageManagerListener
import com.kickstarter.libs.Build
import com.kickstarter.libs.CurrentUserType
import timber.log.Timber

/**
 * Listener for the Braze InAppMessages
 * All business logic will be delegated to the `InAppCustomListenerHandler`
 * this class is meant to override the necessary methods for InAppMessages
 * for now we just need `beforeInAppMessageDisplayed`.
 */
class InAppCustomListener(
    loggedInUser: CurrentUserType,
    private val build: Build
) : IInAppMessageManagerListener {

    private var handler: InAppCustomListenerHandler

    init {
        if (build.isDebug) Timber.d("${this.javaClass.canonicalName} Init block custom listener")
        handler = InAppCustomListenerHandler(loggedInUser)
    }

    /**
     * Callback method call everytime the app receives and InAppMessage from Braze before displaying it on the screen:
     * In case the user is logged in, and the
     * feature flag is active
     * @return InAppMessageOperation.DISPLAY_NOW
     *
     * In case no user logged in or the feature flag not active
     * feature
     * @return InAppMessageOperation.DISCARD
     */
    override fun beforeInAppMessageDisplayed(inAppMessage: IInAppMessage?): InAppMessageOperation {
        if (build.isDebug) Timber.d("${this.javaClass.canonicalName} beforeInAppMessageDisplayed: ${inAppMessage?.toString()}")

        val shouldShowMessage = if (handler.shouldShowMessage())
            InAppMessageOperation.DISPLAY_NOW
        else InAppMessageOperation.DISCARD

        if (build.isDebug) Timber.d("${this.javaClass.canonicalName} beforeInAppMessageDisplayed: $shouldShowMessage")
        return shouldShowMessage
    }

    /**
     * Callback method called everytime the in app message is clicked. This gives the option of
     * overriding braze's default behavior.
     * If this method returns true, braze will only log a click and nothing else. If false, braze
     * will log a click and close the in-app message automatically.

     * @return false
     */
    override fun onInAppMessageClicked(
        inAppMessage: IInAppMessage?,
        inAppMessageCloser: InAppMessageCloser?
    ): Boolean {
        return false
    }

    /**
     * Callback method called everytime the in app message button is clicked. This gives the option of
     * overriding braze's default behavior.
     * If this method returns true, braze will only log a click and nothing else. If false, braze
     * will log a click and close the in-app message automatically.
     *
     * @return false
     */
    override fun onInAppMessageButtonClicked(
        inAppMessage: IInAppMessage?,
        button: MessageButton?,
        inAppMessageCloser: InAppMessageCloser?
    ): Boolean {
        return false
    }

    override fun onInAppMessageDismissed(inAppMessage: IInAppMessage?) {
        if (build.isDebug) Timber.d("${this.javaClass.canonicalName} onInAppMessageDismissed: ${inAppMessage?.toString()}")
    }

    override fun beforeInAppMessageViewOpened(
        inAppMessageView: View?,
        inAppMessage: IInAppMessage?
    ) {
        if (build.isDebug) Timber.d("${this.javaClass.canonicalName} beforeInAppMessageViewOpened: ${inAppMessage?.toString()}")
    }

    override fun afterInAppMessageViewOpened(
        inAppMessageView: View?,
        inAppMessage: IInAppMessage?
    ) {
        if (build.isDebug) Timber.d("${this.javaClass.canonicalName} afterInAppMessageViewOpened: ${inAppMessage?.toString()}")
    }

    override fun beforeInAppMessageViewClosed(
        inAppMessageView: View?,
        inAppMessage: IInAppMessage?
    ) {
        if (build.isDebug) Timber.d("${this.javaClass.canonicalName} beforeInAppMessageViewClosed: ${inAppMessage?.toString()}")
    }

    override fun afterInAppMessageViewClosed(inAppMessage: IInAppMessage?) {
        if (build.isDebug) Timber.d("${this.javaClass.canonicalName} afterInAppMessageViewClosed: ${inAppMessage?.toString()}")
    }
}
