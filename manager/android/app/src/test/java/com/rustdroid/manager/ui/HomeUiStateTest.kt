package com.rustdroid.manager.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateTest {
    @Test
    fun patchIsEnabledOnlyForNativeReadyPatchableImage() {
        val state = HomeUiState(
            nativeStatus = NativeStatusUiState(level = NativeStatusLevel.Ready),
            bootImage = BootImageUiState(filePath = "/tmp/boot.img", status = BootImageStatus.Patchable)
        )

        assertTrue(state.canPatch)
        assertEquals("Ready", state.patchEngineLabel)
        assertNull(state.patchDisabledReason)
    }

    @Test
    fun patchDisabledReasonExplainsNativeUnavailable() {
        val state = HomeUiState(
            nativeStatus = NativeStatusUiState(level = NativeStatusLevel.Unavailable),
            bootImage = BootImageUiState(filePath = "/tmp/boot.img", status = BootImageStatus.Patchable)
        )

        assertFalse(state.canPatch)
        assertEquals("Blocked", state.patchEngineLabel)
        assertEquals("Native layer is unavailable.", state.patchDisabledReason)
    }

    @Test
    fun patchDisabledReasonExplainsAlreadyPatchedImage() {
        val state = HomeUiState(
            nativeStatus = NativeStatusUiState(level = NativeStatusLevel.Ready),
            bootImage = BootImageUiState(filePath = "/tmp/boot.img", status = BootImageStatus.AlreadyPatched)
        )

        assertFalse(state.canPatch)
        assertEquals("This image already appears patched.", state.patchDisabledReason)
    }
}
