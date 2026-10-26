package com.gloom.island.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAssistantRoutingTest {

    @Test
    fun cleanSpeechStripsCommonUserFillers() {
        val timeQuery = CommandRouter.cleanSpeech("could you tell me what is the current time")
        assertTrue(timeQuery.contains("time", ignoreCase = true))

        val pdfQuery = CommandRouter.cleanSpeech("I am asking you to tell me the list of the all the pdf that are available")
        assertTrue(pdfQuery.contains("pdf", ignoreCase = true))
        assertTrue(pdfQuery.contains("list", ignoreCase = true))

        val volQuery = CommandRouter.cleanSpeech("could you please increase the volume to 100%")
        assertTrue(volQuery.contains("volume", ignoreCase = true))
        assertTrue(volQuery.contains("100%", ignoreCase = true))

        val wifiQuery = CommandRouter.cleanSpeech("could you tell me for which Wi-Fi I am connecting")
        assertTrue(wifiQuery.contains("wi-fi", ignoreCase = true))
    }

    @Test
    fun detectsVolumeCommandsProperly() {
        assertTrue(CommandRouter.isVolumeCommand("increase the volume to 100%"))
        assertTrue(CommandRouter.isVolumeCommand("set volume to 50%"))
        assertTrue(CommandRouter.isVolumeCommand("volume up"))
        assertTrue(CommandRouter.isVolumeCommand("turn volume down"))
        assertTrue(CommandRouter.isVolumeCommand("mute volume"))
        assertTrue(CommandRouter.isVolumeCommand("unmute volume"))
        assertTrue(CommandRouter.isVolumeCommand("what is the volume"))
        assertFalse(CommandRouter.isVolumeCommand("tell me a story"))
    }

    @Test
    fun detectsWifiQueriesProperly() {
        assertTrue(CommandRouter.isWifiQuery("for which wi-fi i am connecting"))
        assertTrue(CommandRouter.isWifiQuery("what wifi am i connected to"))
        assertTrue(CommandRouter.isWifiQuery("wifi status"))
        assertTrue(CommandRouter.isWifiQuery("which wifi is connected"))
        assertFalse(CommandRouter.isWifiQuery("open instagram"))
    }

    @Test
    fun detectsPdfQueriesProperly() {
        assertTrue(CommandRouter.isPdfQuery("the list of the all the pdf that are available"))
        assertTrue(CommandRouter.isPdfQuery("list all pdfs"))
        assertTrue(CommandRouter.isPdfQuery("show available pdf files"))
        assertTrue(CommandRouter.isPdfQuery("find pdf for receipt"))
        assertFalse(CommandRouter.isPdfQuery("call mom"))
    }

    @Test
    fun aiPlannerRecognizesHardwareAndFileActions() {
        assertTrue(AiPlanner.looksLikeAction("increase the volume to 100%"))
        assertTrue(AiPlanner.looksLikeAction("mute volume"))
        assertTrue(AiPlanner.looksLikeAction("list all pdfs"))
    }

    @Test
    fun liveTimeAndDateProvidesValidInformation() {
        val live = LocalBrain.liveTimeAndDate()
        assertNotNull(live)
        assertTrue(live.contains("AM") || live.contains("PM"))
        assertTrue(live.contains("2026") || live.contains("202"))
    }

    @Test
    fun recognizesUnlockCommandsProperly() {
        assertTrue(CommandRouter.isCommand("unlock"))
        assertTrue(CommandRouter.isCommand("unlock phone"))
        assertTrue(CommandRouter.isCommand("unlock my phone"))
        assertTrue(CommandRouter.isCommand("unlock device"))
        assertTrue(CommandRouter.isCommand("draw pattern"))
        assertTrue(CommandRouter.isCommand("replay pattern"))
        assertTrue(CommandRouter.isCommand("pattern unlock"))

        val cleaned = CommandRouter.cleanSpeech("hey gloom please unlock my phone")
        assertEquals("unlock my phone", cleaned)
        assertTrue(CommandRouter.isCommand(cleaned))

        val cleanedPattern = CommandRouter.cleanSpeech("could you please draw the pattern")
        assertEquals("draw the pattern", cleanedPattern)
        assertTrue(CommandRouter.isCommand(cleanedPattern))
    }
}
