package com.siberanka.chunkgendebug.event;

import com.siberanka.chunkgendebug.attribution.PluginIdentityIndex;
import com.siberanka.chunkgendebug.context.WorldContextRegistry;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerLoadEvent;

public final class ServerLifecycleListener implements Listener {
    private final PluginIdentityIndex pluginIndex;
    private final WorldContextRegistry contexts;

    public ServerLifecycleListener(PluginIdentityIndex pluginIndex, WorldContextRegistry contexts) {
        this.pluginIndex = pluginIndex;
        this.contexts = contexts;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        pluginIndex.refresh(Bukkit.getPluginManager().getPlugins());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        pluginIndex.refresh(Bukkit.getPluginManager().getPlugins());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerLoad(ServerLoadEvent event) {
        pluginIndex.refresh(Bukkit.getPluginManager().getPlugins());
        for (World world : Bukkit.getWorlds()) {
            contexts.refresh(world, "ServerLoadEvent:" + event.getType().name());
        }
    }
}
