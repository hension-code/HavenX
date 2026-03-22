package com.hension.havenx

import org.junit.Assert.assertEquals
import org.junit.Test
import com.hension.havenx.navigation.Screen

class ScreenTest {

    @Test
    fun `all screens have unique routes`() {
        val routes = Screen.entries.map { it.route }
        assertEquals(
            "All screen routes should be unique",
            routes.size, routes.toSet().size
        )
    }

    @Test
    fun `all screens have non-blank labels`() {
        Screen.entries.forEach { screen ->
            assert(screen.label.isNotBlank()) {
                "Screen ${screen.name} has blank label"
            }
        }
    }

    @Test
    fun `there are exactly 6 screens`() {
        assertEquals(
            "Navigation should have 6 tabs",
            6, Screen.entries.size
        )
    }

    @Test
    fun `connections is first screen`() {
        assertEquals(
            "Connections should be the first tab",
            Screen.Connections, Screen.entries.first()
        )
    }
}
