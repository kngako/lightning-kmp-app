package io.github.kotlin.fibonacci

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class AndroidFibiTest {

    @Test
    fun testThirdElement() {
        assertEquals(3, generateFibi().take(3).last())
    }
}