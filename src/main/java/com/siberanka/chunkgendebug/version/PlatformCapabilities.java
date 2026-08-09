package com.siberanka.chunkgendebug.version;

public record PlatformCapabilities(boolean folia) {
    public static PlatformCapabilities detect(ClassLoader classLoader) {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false, classLoader);
            return new PlatformCapabilities(true);
        } catch (ClassNotFoundException unavailable) {
            return new PlatformCapabilities(false);
        }
    }
}
