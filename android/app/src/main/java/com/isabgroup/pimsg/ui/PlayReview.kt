package com.isabgroup.pimsg.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory

object PlayReview {
    fun request(activity: Activity, onFallback: () -> Unit = {}) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (request.isSuccessful) {
                manager.launchReviewFlow(activity, request.result)
                    .addOnFailureListener { onFallback() }
            } else {
                onFallback()
            }
        }
    }

    fun openStoreListing(activity: Activity) {
        val uri = Uri.parse("market://details?id=com.pimsg.isabstudio")
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.pimsg.isabstudio")))
        }
    }
}
