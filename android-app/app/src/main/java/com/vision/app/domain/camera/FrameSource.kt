package com.vision.app.domain.camera

/** Future camera adapter boundary. No camera is opened in Phase 1A. */
interface FrameSource {
    suspend fun start()
    suspend fun stop()
}
