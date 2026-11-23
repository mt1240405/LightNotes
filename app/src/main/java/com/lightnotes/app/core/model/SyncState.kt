package com.lightnotes.app.core.model

enum class SyncState {
    LOCAL_ONLY,
    PENDING_SYNC,
    SYNCHRONIZING,
    SYNCHRONIZED,
    SYNC_FAILED,
    CONFLICT_DETECTED
}
