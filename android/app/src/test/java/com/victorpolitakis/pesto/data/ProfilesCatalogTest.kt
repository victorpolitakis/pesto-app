package com.victorpolitakis.pesto.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Δ3: the app knows the ready-made profiles by id only, and the ids must be the backend's. */
class ProfilesCatalogTest {
    @Test
    fun `the catalog has exactly the profiles of the backend`() {
        // Like backend/src/pesto_server/llm/profiles.py: one .md per profile, _base.md is not one.
        val backend = File("../../backend/profiles").listFiles()!!
            .map { it.name }
            .filter { it.endsWith(".md") && !it.startsWith("_") }
            .map { it.removeSuffix(".md") }
            .toSet()
        assertEquals(backend, ProfilesCatalog.all.map { it.id }.toSet())
    }

    @Test
    fun `ids are unique, and raw is there for the fallback`() {
        assertEquals(ProfilesCatalog.all.size, ProfilesCatalog.all.map { it.id }.distinct().size)
        assertNotNull(ProfilesCatalog.find(ProfilesCatalog.RAW))
        assertNull(ProfilesCatalog.find("custom"))
        assertNull(ProfilesCatalog.find(null))
    }

    @Test
    fun `the profile of a dictation - the app's, else the default, else raw`() {
        val apps = mapOf("com.whatsapp" to "chatter")
        assertEquals("chatter", ProfilesRepository.choose("com.whatsapp", apps, default = "mail"))
        assertEquals("mail", ProfilesRepository.choose("com.google.android.gm", apps, default = "mail"))
        assertEquals("mail", ProfilesRepository.choose(null, apps, default = "mail"))
        assertEquals("raw", ProfilesRepository.choose("com.other", apps, default = "gone"))
    }
}
