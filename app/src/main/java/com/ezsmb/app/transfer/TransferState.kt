package com.ezsmb.app.transfer

enum class TransferState { QUEUED, PREPARING, RUNNING, FINALIZING, COMPLETED, FAILED, CANCELLED, INTERRUPTED }

fun TransferState.canTransitionTo(next: TransferState): Boolean {
    if (this == next) return true
    return when (this) {
        TransferState.QUEUED -> next in setOf(TransferState.PREPARING, TransferState.CANCELLED, TransferState.FAILED, TransferState.INTERRUPTED)
        TransferState.PREPARING -> next in setOf(TransferState.RUNNING, TransferState.CANCELLED, TransferState.FAILED, TransferState.INTERRUPTED)
        TransferState.RUNNING -> next in setOf(TransferState.FINALIZING, TransferState.CANCELLED, TransferState.FAILED, TransferState.INTERRUPTED)
        TransferState.FINALIZING -> next in setOf(TransferState.COMPLETED, TransferState.FAILED, TransferState.INTERRUPTED)
        else -> false
    }
}
