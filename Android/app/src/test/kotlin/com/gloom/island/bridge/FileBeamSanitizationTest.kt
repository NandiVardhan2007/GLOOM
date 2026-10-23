package com.gloom.island.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileBeamSanitizationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSanitizeFileName_unicodePreserved() {
        assertEquals("రిపోర్ట్.pdf", FileBeamReceiver.sanitizeFileName("రిపోర్ట్.pdf"))
        assertEquals("रिपोर्ट.pdf", FileBeamReceiver.sanitizeFileName("रिपोर्ट.pdf"))
    }

    @Test
    fun testSanitizeFileName_pathTraversalBlocked() {
        assertEquals("passwd", FileBeamReceiver.sanitizeFileName("../../etc/passwd"))
        assertEquals("c.txt", FileBeamReceiver.sanitizeFileName("a/b\\c.txt"))
        assertEquals("hosts", FileBeamReceiver.sanitizeFileName("..\\..\\windows\\system32\\drivers\\etc\\hosts"))
    }

    @Test
    fun testSanitizeFileName_leadingDotsStripped() {
        assertEquals("hidden", FileBeamReceiver.sanitizeFileName(".hidden"))
        assertEquals("hidden.txt", FileBeamReceiver.sanitizeFileName("...hidden.txt"))
        assertEquals("Received_File.bin", FileBeamReceiver.sanitizeFileName("..."))
        assertEquals("Received_File.bin", FileBeamReceiver.sanitizeFileName(""))
    }

    @Test
    fun testSanitizeFileName_controlCharsFiltered() {
        assertEquals("clean.txt", FileBeamReceiver.sanitizeFileName("\u0000clean\u001f.txt"))
    }

    @Test
    fun testResolveUniqueDestinationFile_noCollision() {
        val root = tempFolder.newFolder("downloads")
        val resolved = FileBeamReceiver.resolveUniqueDestinationFile(root, "document.pdf")
        assertEquals(File(root, "document.pdf").absolutePath, resolved.absolutePath)
        assertFalse(resolved.exists())
    }

    @Test
    fun testResolveUniqueDestinationFile_oneCollision() {
        val root = tempFolder.newFolder("downloads_one")
        val existing = File(root, "document.pdf")
        assertTrue(existing.createNewFile())

        val resolved = FileBeamReceiver.resolveUniqueDestinationFile(root, "document.pdf")
        assertEquals(File(root, "document (1).pdf").absolutePath, resolved.absolutePath)
    }

    @Test
    fun testResolveUniqueDestinationFile_manyCollisions() {
        val root = tempFolder.newFolder("downloads_many")
        File(root, "document.pdf").createNewFile()
        File(root, "document (1).pdf").createNewFile()
        File(root, "document (2).pdf").createNewFile()

        val resolved = FileBeamReceiver.resolveUniqueDestinationFile(root, "document.pdf")
        assertEquals(File(root, "document (3).pdf").absolutePath, resolved.absolutePath)
    }

    @Test
    fun testResolveUniqueDestinationFile_noExtension() {
        val root = tempFolder.newFolder("downloads_no_ext")
        File(root, "README").createNewFile()

        val resolved = FileBeamReceiver.resolveUniqueDestinationFile(root, "README")
        assertEquals(File(root, "README (1)").absolutePath, resolved.absolutePath)
    }
}
