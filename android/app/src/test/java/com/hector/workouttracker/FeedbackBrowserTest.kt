package com.hector.workouttracker

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.hector.workouttracker.feature.feedback.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FeedbackBrowserTest {
    private val base: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun opensOnlyTheFixedHostedPageWithoutAttachingUserData() {
        var launched: Intent? = null
        val context =
            object : ContextWrapper(base) {
                override fun startActivity(intent: Intent) {
                    launched = intent
                }
            }
        assertTrue(openFeedbackBrowser(context))
        val intent = requireNotNull(launched)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(
            "https://teto200120.github.io/workout-tracker/?feedback=android",
            intent.dataString,
        )
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertNull(intent.extras)
        assertNull(intent.clipData)
        assertNull(intent.component)
        assertNull(intent.`package`)
    }

    @Test
    fun reportsMissingOrRestrictedBrowserWithoutCrashing() {
        for (failure in listOf(ActivityNotFoundException(), SecurityException())) {
            val context =
                object : ContextWrapper(base) {
                    override fun startActivity(intent: Intent) {
                        throw failure
                    }
                }
            assertFalse(openFeedbackBrowser(context))
        }
    }
}
