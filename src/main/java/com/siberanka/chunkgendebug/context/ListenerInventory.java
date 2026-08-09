package com.siberanka.chunkgendebug.context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.event.Event;
import org.bukkit.plugin.RegisteredListener;

public final class ListenerInventory {
    private volatile int maximumListeners;

    public ListenerInventory(int maximumListeners) {
        this.maximumListeners = maximumListeners;
    }

    public void updateLimit(int maximumListeners) {
        this.maximumListeners = maximumListeners;
    }

    public Map<String, Object> inspect(Event event) {
        RegisteredListener[] registered = event.getHandlers().getRegisteredListeners();
        int limit = Math.min(registered.length, maximumListeners);
        ArrayList<Map<String, Object>> listeners = new ArrayList<>(limit);
        for (int index = 0; index < limit; index++) {
            RegisteredListener listener = registered[index];
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("order", index);
            item.put("plugin", listener.getPlugin().getName());
            item.put("pluginVersion", listener.getPlugin().getPluginMeta().getVersion());
            item.put("priority", listener.getPriority().name());
            item.put("ignoreCancelled", listener.isIgnoringCancelled());
            item.put("listenerClass", listener.getListener().getClass().getName());
            listeners.add(Map.copyOf(item));
        }
        return Map.of(
                "registeredCount", registered.length,
                "capturedCount", limit,
                "truncated", registered.length > limit,
                "listeners", List.copyOf(listeners));
    }
}
