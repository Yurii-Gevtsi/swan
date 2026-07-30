package com.gysignalstudio.blackswan.engagement

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory

object InAppReviewManager {
    fun launch(activity: Activity, onComplete: (Boolean) -> Unit) {
        val reviewManager = ReviewManagerFactory.create(activity)
        reviewManager.requestReviewFlow().addOnCompleteListener { requestTask ->
            if (!requestTask.isSuccessful) {
                onComplete(false)
                return@addOnCompleteListener
            }

            val reviewInfo = requestTask.result
            reviewManager.launchReviewFlow(activity, reviewInfo).addOnCompleteListener {
                onComplete(true)
            }
        }
    }
}
