package com.vision.app.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraVoiceCommandsTest {
    @Test fun englishAndHinglishCameraCommands() {
        assertEquals(CameraVoiceCommand.Start, CameraVoiceCommands.parse("Vision, start camera"))
        assertEquals(CameraVoiceCommand.Start, CameraVoiceCommands.parse("Camera chalu karo"))
        assertEquals(CameraVoiceCommand.Stop, CameraVoiceCommands.parse("Turn off the camera"))
        assertEquals(CameraVoiceCommand.Stop, CameraVoiceCommands.parse("Camera band karo"))
        assertEquals(CameraVoiceCommand.Status, CameraVoiceCommands.parse("Camera status"))
    }

    @Test fun announcementsAreDistinctFromCameraStop() {
        assertEquals(CameraVoiceCommand.EnableAnnouncements, CameraVoiceCommands.parse("Enable automatic announcements"))
        assertEquals(CameraVoiceCommand.DisableAnnouncements, CameraVoiceCommands.parse("Announcements band karo"))
        assertNull(CameraVoiceCommands.parse("Stop speaking"))
        assertNull(CameraVoiceCommands.parse("What do you see?"))
        assertNull(CameraVoiceCommands.parse("Can I safely cross the road?"))
    }
}
