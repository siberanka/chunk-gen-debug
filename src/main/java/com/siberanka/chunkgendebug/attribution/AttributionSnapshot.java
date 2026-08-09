package com.siberanka.chunkgendebug.attribution;

import java.util.List;

public record AttributionSnapshot(
        String attributionStatus,
        Mechanism mechanism,
        List<PluginCandidate> pluginCandidates,
        List<StackFrameView> stack) {
}
