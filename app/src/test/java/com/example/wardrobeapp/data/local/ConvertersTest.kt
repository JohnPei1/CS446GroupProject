package com.example.wardrobeapp.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun roundTripsAListOfTags() {
        val tags = listOf("Formal", "Business Casual", "Has, Comma")
        val encoded = converters.fromTagList(tags)
        val decoded = converters.toTagList(encoded)
        assertEquals(tags, decoded)
    }

    @Test
    fun roundTripsAnEmptyList() {
        val encoded = converters.fromTagList(emptyList())
        assertEquals(emptyList<String>(), converters.toTagList(encoded))
    }

    @Test
    fun blankStringDecodesToEmptyList() {
        assertEquals(emptyList<String>(), converters.toTagList(""))
    }
}
