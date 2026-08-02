package io.github.kotlin.fibonacci

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class AndroidFibiTest {

    @Test
    fun testThirdElement() {
        assertEquals(3, generateFibi().take(3).last())
    }
}