package com.ezsmb.app.core

import com.ezsmb.app.transfer.TransferState
import com.ezsmb.app.transfer.canTransitionTo
import org.junit.Assert.*
import org.junit.Test

class TransferStateTest {
    @Test fun cannotPublishBeforeFinalization() {
        listOf(TransferState.QUEUED, TransferState.PREPARING, TransferState.RUNNING).forEach { assertFalse(it.canTransitionTo(TransferState.COMPLETED)) }
        assertTrue(TransferState.FINALIZING.canTransitionTo(TransferState.COMPLETED))
    }
    @Test fun terminalResultsCannotBeChangedByLateNetworkCallbacks() {
        listOf(TransferState.CANCELLED, TransferState.FAILED, TransferState.INTERRUPTED, TransferState.COMPLETED).forEach {
            assertFalse(it.canTransitionTo(TransferState.RUNNING)); assertFalse(it.canTransitionTo(TransferState.FINALIZING))
        }
        assertFalse(TransferState.COMPLETED.canTransitionTo(TransferState.FAILED))
    }
    @Test fun cancellationCannotInterruptCommit() {
        assertTrue(TransferState.RUNNING.canTransitionTo(TransferState.CANCELLED))
        assertFalse(TransferState.FINALIZING.canTransitionTo(TransferState.CANCELLED))
    }
}
