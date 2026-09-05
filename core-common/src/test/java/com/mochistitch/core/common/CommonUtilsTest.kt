package com.mochistitch.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class CommonUtilsTest {
    @Test
    fun testGetVersionName() {
        assertEquals("1.0.0", CommonUtils.getVersionName())
    }
}
