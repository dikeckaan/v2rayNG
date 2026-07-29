package com.v2ray.ang.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.Composable
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.net.URLDecoder

class UrlSchemeActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val shareUrl: String?
            val fragment: String?

            if (intent.action == Intent.ACTION_SEND && "text/plain" == intent.type) {
                shareUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
                fragment = null
            } else if (intent.action == Intent.ACTION_VIEW) {
                val uri: Uri? = intent.data
                when (uri?.host) {
                    "install-config", "install-sub" -> {
                        shareUrl = uri.getQueryParameter("url").orEmpty()
                        fragment = uri.fragment
                    }

                    else -> {
                        report(applicationContext, R.string.toast_failure)
                        shareUrl = null
                        fragment = null
                    }
                }
            } else {
                shareUrl = null
                fragment = null
            }

            parseUri(shareUrl, fragment)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Error processing URL scheme", e)
            openMainAndFinish()
        }
    }

    @Composable
    override fun ScreenContent() {
    }

    private fun parseUri(uriString: String?, fragment: String?) {
        if (uriString.isNullOrEmpty()) {
            openMainAndFinish()
            return
        }
        LogUtil.i(AppConfig.TAG, uriString)

        var decodedUrl = URLDecoder.decode(uriString, "UTF-8")
        val uri = Uri.parse(decodedUrl)
        if (uri == null) {
            openMainAndFinish()
            return
        }
        if (uri.fragment.isNullOrEmpty() && !fragment.isNullOrEmpty()) {
            decodedUrl += "#${fragment}"
        }
        LogUtil.i(AppConfig.TAG, decodedUrl)

        // MainActivity is launched and this activity finishes immediately below, not after
        // the import completes: this activity has no UI of its own, so keeping it alive
        // until the import returns leaves the user staring at a blank window for as long as
        // the import takes (up to 30s per configured subscription when the deep link is a
        // subscription URL). The import instead runs on an application-scoped coroutine that
        // outlives this activity; its result is still reported via a platform Toast (see
        // `report`), which is why it's safe to finish before the import completes.
        //
        // `appContext` is captured here, before `launch`, rather than reading
        // `applicationContext` inside the coroutine body: `report` is a top-level function,
        // not an instance method, so the launched closure only captures this plain Context,
        // never `this@UrlSchemeActivity`. That keeps the finished/destroyed activity
        // unreachable from the application-scoped job for the duration of the import.
        val appContext = applicationContext
        AngApplication.applicationScope.launch {
            try {
                // append = true: importing one profile must not wipe the default group.
                // Upstream passes false here, which calls removeServerViaSubid("") first.
                val (count, countSub) = AngConfigManager.importBatchConfig(decodedUrl, "", true)
                if (count + countSub > 0) {
                    report(appContext, R.string.import_subscription_success)
                } else {
                    report(appContext, R.string.import_subscription_failure)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Deep-link import failed", e)
                report(appContext, R.string.import_subscription_failure)
            }
        }
        openMainAndFinish()
    }

    /** Always reached exactly once, on every path including errors and empty URLs. */
    private fun openMainAndFinish() {
        if (isFinishing || isDestroyed) return
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

/**
 * Deliberately a platform [Toast] rather than the app snackbar: [UrlSchemeActivity] finishes
 * immediately, well before the import (running on [AngApplication.applicationScope]) reports
 * its result, so a snackbar hosted in its window would already be torn down. A platform toast
 * outlives the activity and stays visible over [MainActivity]. Takes [context] explicitly
 * (callers pass `applicationContext`) rather than being an instance method of the activity:
 * the deep-link import path calls this from a coroutine on [AngApplication.applicationScope],
 * and an instance method would capture `UrlSchemeActivity.this`, keeping the finished activity
 * reachable for as long as the import runs. Posts to the main thread since
 * [AngApplication.applicationScope] is backed by `Dispatchers.IO` and has no `Looper` for
 * `Toast` to attach to.
 */
private fun report(context: Context, message: Int) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
