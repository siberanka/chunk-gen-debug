package com.siberanka.chunkgendebug.attribution;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import org.bukkit.plugin.Plugin;

public final class PluginIdentityIndex {
    private volatile Map<ClassLoader, Identity> byClassLoader = Map.of();

    public void refresh(Plugin[] plugins) {
        IdentityHashMap<ClassLoader, Identity> fresh = new IdentityHashMap<>();
        for (Plugin plugin : plugins) {
            fresh.put(plugin.getClass().getClassLoader(),
                    new Identity(plugin.getName(), plugin.getPluginMeta().getVersion()));
        }
        byClassLoader = Collections.unmodifiableMap(fresh);
    }

    public Identity find(Class<?> type) {
        return byClassLoader.get(type.getClassLoader());
    }

    public record Identity(String name, String version) {
    }
}
