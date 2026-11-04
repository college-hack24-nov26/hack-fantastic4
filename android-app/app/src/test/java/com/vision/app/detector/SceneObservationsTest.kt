package com.vision.app.detector

import com.vision.app.domain.voice.AutoAnnouncementPolicy
import com.vision.app.domain.voice.VisualQuestionRouter
import com.vision.app.domain.voice.SpeechPriority
import com.vision.app.domain.voice.SpeechPriorityCoordinator
import com.vision.app.settings.UiLanguage
import org.junit.Assert.*
import org.junit.Test

class SceneObservationsTest {
    private fun box(label: String = "chair", score: Float = 0.7f, left: Float = 0.1f) =
        Detection(if (label == "chair") 5 else 1, label, score, left, 0.2f, left + 0.2f, 0.6f)

    @Test fun stabilityRequiresMatchingClassAndTwoFrames() {
        val scenes = SceneObservations()
        scenes.start(1)
        scenes.publish(1, listOf(box()), 900, 1000)
        assertFalse(scenes.read(1000).observations.single().stable)
        scenes.publish(1, listOf(box("person")), 1400, 1500)
        assertFalse(scenes.read(1500).observations.single().stable)
        scenes.publish(1, listOf(box("person", left = 0.12f)), 1900, 2000)
        assertTrue(scenes.read(2000).observations.single().stable)
    }

    @Test fun confidencePositionExpiryAndSessionInvalidation() {
        val scenes = SceneObservations()
        scenes.start(11)
        scenes.publish(11, listOf(box(score = 0.49f), box(left = 0.75f)), 100, 200)
        assertEquals(HorizontalPosition.Right, scenes.read(200).observations.single().position)
        assertFalse(scenes.read(1700).observations.single().current)
        scenes.stop(11)
        scenes.start(12)
        scenes.publish(11, listOf(box()), 1900, 2000)
        assertTrue(scenes.read(2000).observations.isEmpty())
        assertEquals(12L, scenes.read(2000).sessionId)
    }

    @Test fun autoAnnouncementsRespectStabilityCooldownAndChanges() {
        val scenes = SceneObservations()
        val policy = AutoAnnouncementPolicy()
        scenes.start(1)
        scenes.publish(1, listOf(box()), 900, 1000)
        assertNull(policy.candidate(scenes.read(1000), 1000))
        scenes.publish(1, listOf(box()), 1400, 1500)
        val key = policy.candidate(scenes.read(1500), 1500)
        assertNotNull(key)
        policy.markAnnounced(key!!, 1500)
        assertNull(policy.candidate(scenes.read(1500), 1500))
        scenes.publish(1, listOf(box(left = 0.7f)), 1900, 2000)
        assertNull(policy.candidate(scenes.read(2000), 2000))
        scenes.publish(1, listOf(box(left = 0.7f)), 9400, 9500)
        assertEquals("chair:Right", policy.candidate(scenes.read(9500), 9500))
        scenes.stop(1)
        assertNull(policy.candidate(scenes.read(9600), 9600))
    }

    @Test fun visualRoutingIsCautiousAndNeverUsesQwenForSafety() {
        val router = VisualQuestionRouter()
        val scenes = SceneObservations()
        assertTrue(router.respond("What do you see?", UiLanguage.English, scenes.read(0), 0)!!.contains("off"))
        scenes.start(2)
        assertTrue(router.respond("Describe the scene", UiLanguage.English, scenes.read(100), 100)!!.contains("recent"))
        scenes.publish(2, listOf(box()), 900, 1000)
        assertTrue(router.respond("What do you see?", UiLanguage.English, scenes.read(1000), 1000)!!.contains("can't confidently"))
        scenes.publish(2, listOf(box()), 1400, 1500)
        assertTrue(router.respond("What do you see?", UiLanguage.English, scenes.read(1500), 1500)!!.contains("Possible chair"))
        assertTrue(router.respond("Camera mein kya dikh raha hai?", UiLanguage.Hinglish, scenes.read(1500), 1500)!!.contains("shayad"))
        assertTrue(router.respond("Can I safely cross the road?", UiLanguage.English, scenes.read(1500), 1500)!!.contains("cannot verify"))
        assertTrue(router.respond("What color is the chair?", UiLanguage.English, scenes.read(1500), 1500)!!.contains("cannot verify color"))
        assertNull(router.respond("Tell me a joke", UiLanguage.English, scenes.read(1500), 1500))
    }

    @Test fun userSpeechPreemptsAndCancelsAutomaticSpeech() {
        val coordinator = SpeechPriorityCoordinator()
        val automatic = coordinator.begin(SpeechPriority.AutomaticObservation)!!
        assertNull(coordinator.begin(SpeechPriority.AutomaticObservation))
        val user = coordinator.begin(SpeechPriority.UserResponse)!!
        assertFalse(coordinator.isCurrent(automatic))
        assertNull(coordinator.begin(SpeechPriority.AutomaticObservation))
        coordinator.complete(automatic)
        assertTrue(coordinator.isCurrent(user))
        coordinator.cancel()
        assertFalse(coordinator.isCurrent(user))
        assertNotNull(coordinator.begin(SpeechPriority.AutomaticObservation))
    }
}
