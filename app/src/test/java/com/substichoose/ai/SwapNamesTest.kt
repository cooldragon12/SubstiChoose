package com.substichoose.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwapNamesTest {

    @Test
    fun sameSwapUnderAnotherName() {
        assertTrue(SwapNames.isSameSwap("Aquafaba", "Aquafaba (chickpea liquid)"))
        assertTrue(SwapNames.isSameSwap("Ground flaxseed mixed with water", "Flax egg"))
        assertTrue(SwapNames.isSameSwap("Mashed Banana", "Mashed ripe banana"))
        assertTrue(SwapNames.isSameSwap("Applesauce", "applesauce"))
    }

    @Test
    fun differentSwapsStayApart() {
        assertFalse(SwapNames.isSameSwap("Applesauce", "Flax egg"))
        assertFalse(SwapNames.isSameSwap("Oat milk", "Soy milk"))
        assertFalse(SwapNames.isSameSwap("Coconut oil", "Olive oil"))
        assertFalse(SwapNames.isSameSwap("Mashed avocado", "Mashed ripe banana"))
    }
}
