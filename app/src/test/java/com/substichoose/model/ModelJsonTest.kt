package com.substichoose.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelJsonTest {

    @Test
    fun substitutesAsRequestedObject() {
        val reply = """{"ingredient": "egg", "options": [{"name": "Applesauce", "ratio": "1 egg = 1/4 cup"}]}"""
        val r = ModelJson.decodeSubstitutesOrNull(reply, "egg")!!
        assertEquals(listOf("Applesauce"), r.options.map { it.name })
    }

    @Test
    fun substitutesAsListOfObjects() {
        // Seen on the phone: one object per suggestion, wrapped in a fenced list.
        val reply = "```json\n[\n" +
            """{"ingredient": "Cinnamon Stick", "options": [{"name": "Cinnamon Stick", "ratio": "1 = 1"}]},""" +
            """{"ingredient": "Thyme", "options": [{"name": "Dried thyme", "ratio": "1 = 1/4 tsp"}]}""" +
            "\n]\n```"
        val r = ModelJson.decodeSubstitutesOrNull(reply, "bay leaves")!!
        assertEquals("bay leaves", r.ingredient)
        assertEquals(listOf("Cinnamon Stick", "Dried thyme"), r.options.map { it.name })
    }

    @Test
    fun substitutesAsBareListOfOptions() {
        val reply = """[{"name": "Oregano", "ratio": "1 = 1/2 tsp"}, {"name": "Thyme"}]"""
        val r = ModelJson.decodeSubstitutesOrNull(reply, "bay leaves")!!
        assertEquals(listOf("Oregano", "Thyme"), r.options.map { it.name })
    }

    @Test
    fun notJson() {
        assertNull(ModelJson.decodeSubstitutesOrNull("Sorry, I can't help with that.", "egg"))
    }
}
