package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                        report(R.string.toast_failure)
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

        // Navigation happens in the `finally` block, not here: starting MainActivity and
        // finishing straight away used to cancel this coroutine's result handling, so a
        // deep-link import completed silently. This is one of only two import routes in
        // compact round-screen mode, so it has to confirm what it did.
        lifecycleScope.launch {
            try {
                val (count, countSub) = withContext(Dispatchers.IO) {
                    // append = true: importing one profile must not wipe the default group.
                    // Upstream passes false here, which calls removeServerViaSubid("") first.
                    AngConfigManager.importBatchConfig(decodedUrl, "", true)
                }
                if (count + countSub > 0) {
                    report(R.string.import_subscription_success)
                } else {
                    report(R.string.import_subscription_failure)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Deep-link import failed", e)
                report(R.string.import_subscription_failure)
            } finally {
                openMainAndFinish()
            }
        }
    }

    /**
     * Deliberately a platform [Toast] rather than the app snackbar: this activity has no
     * UI of its own and finishes as soon as the import reports, so a snackbar hosted in
     * its window would be torn down before it could be read. A platform toast outlives
     * the activity and stays visible over [MainActivity].
     */
    private fun report(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /** Always reached exactly once, on every path including errors and empty URLs. */
    private fun openMainAndFinish() {
        if (isFinishing || isDestroyed) return
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
