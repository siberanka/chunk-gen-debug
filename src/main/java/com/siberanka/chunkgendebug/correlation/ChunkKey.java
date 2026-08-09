package com.siberanka.chunkgendebug.correlation;

import java.util.UUID;

public record ChunkKey(UUID worldId, int x, int z) {
}
