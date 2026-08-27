/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.ads.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import app.atf.media.ads.domain.SupportPageOpenResult
import app.atf.media.ads.domain.SupportPageRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class BrowserSupportPageRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SupportPageRepository {
        override fun openSupportPage(): SupportPageOpenResult =
            try {
                val customTabsIntent =
                    CustomTabsIntent
                        .Builder()
                        .setShowTitle(false)
                        .build()
                        .apply { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                customTabsIntent.launchUrl(context, SUPPORT_PAGE_URI)
                SupportPageOpenResult.Opened
            } catch (exception: ActivityNotFoundException) {
                supportPageUnavailable(exception)
            } catch (exception: SecurityException) {
                supportPageUnavailable(exception)
            }

        private fun supportPageUnavailable(exception: RuntimeException): SupportPageOpenResult {
            Timber.w(exception, "Unable to open the ArchiveTune support page")
            return SupportPageOpenResult.Unavailable
        }

        private companion object {
            val SUPPORT_PAGE_URI: Uri = Uri.parse("https://github.com/4nx3b/ArchiveTune")
        }
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SupportPageDataModule {
    @Binds
    @Singleton
    abstract fun bindSupportPageRepository(repository: BrowserSupportPageRepository): SupportPageRepository
}
