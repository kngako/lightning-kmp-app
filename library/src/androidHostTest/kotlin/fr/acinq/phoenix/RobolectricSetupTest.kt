/*
 * Copyright 2025 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.phoenix.utils.getApplicationFilesDirectoryPath
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the Robolectric setup itself.
 *
 * Robolectric was previously declared against the `androidUnitTest` source set, which this module's android
 * target does not compile, so it silently sat off the test classpath and nothing failed to tell us. These
 * assertions fail loudly if that happens again: without a working Robolectric runtime, touching any android
 * API here raises "not mocked" (or the context is simply unavailable).
 *
 * This class deliberately uses the same [AndroidJUnit4] runner as the rest of the android tests rather than
 * naming [org.robolectric.RobolectricTestRunner] directly, so that it also covers the delegation: on the jvm
 * that runner resolves to Robolectric, and on a device to the instrumentation runner.
 */
@RunWith(AndroidJUnit4::class)
class RobolectricSetupTest {

    @Test
    fun android_runtime_is_available() {
        assertTrue(Build.VERSION.SDK_INT > 0, "android framework classes should be backed by Robolectric")
        println("robolectric is running against sdk=${Build.VERSION.SDK_INT}")
    }

    /** Robolectric stamps its own build fingerprint, so this proves the delegation actually landed on it. */
    @Test
    fun android_junit4_delegates_to_robolectric_on_the_jvm() {
        assertEquals(
            "robolectric", Build.FINGERPRINT,
            "expected AndroidJUnit4 to delegate to RobolectricTestRunner on the host jvm"
        )
    }

    @Test
    fun application_context_is_available() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(context.filesDir.exists() || context.filesDir.mkdirs(), "expected a usable filesDir")
    }

    /**
     * The seed and pin files must resolve under the app-private files directory, never under a cache or
     * temporary directory that the OS is free to purge.
     */
    @Test
    fun application_files_directory_is_not_a_cache_directory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val filesDir = getApplicationFilesDirectoryPath(PlatformContext(context))

        assertTrue(filesDir.isNotBlank(), "expected a non-blank files directory")
        assertTrue(
            filesDir == context.filesDir.absolutePath,
            "expected the app-private files dir, got $filesDir"
        )
        assertTrue(
            !filesDir.startsWith(context.cacheDir.absolutePath),
            "the files directory must not live under the cache directory ($filesDir)"
        )
    }
}
