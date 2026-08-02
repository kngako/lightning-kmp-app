package io.github.kotlin.fibonacci

import kotlin.test.Test
import kotlin.test.assertEquals

class FibiTest {

    // `commonTest` is also compiled into the instrumented test apk, and dex rejects spaces in a method name
    // below api 30 -- so no backticked name with spaces here.
    @Test
    fun test_3rd_element() {
        assertEquals(firstElement + secondElement, generateFibi().take(3).last())
    }
}