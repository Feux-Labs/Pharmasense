package com.pharmasense.sync.dto;

import java.util.List;

public record SyncPullResponse(
        List<SyncChangeEntryDto> changes,
        long nextCursor,
        boolean hasMore) {
}
