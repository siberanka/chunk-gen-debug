package com.siberanka.chunkgendebug.attribution;

public record PluginCandidate(
        String plugin,
        String version,
        String evidence,
        String confidence,
        int firstFrame) {
}
