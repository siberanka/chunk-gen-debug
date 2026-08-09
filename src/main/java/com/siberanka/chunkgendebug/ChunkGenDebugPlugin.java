package com.siberanka.chunkgendebug;

import com.siberanka.chunkgendebug.attribution.AttributionEngine;
import com.siberanka.chunkgendebug.attribution.MechanismClassifier;
import com.siberanka.chunkgendebug.attribution.PluginIdentityIndex;
import com.siberanka.chunkgendebug.config.PluginSettings;
import com.siberanka.chunkgendebug.context.ListenerInventory;
import com.siberanka.chunkgendebug.context.WorldContextRegistry;
import com.siberanka.chunkgendebug.correlation.CorrelationTracker;
import com.siberanka.chunkgendebug.event.ChunkLifecycleListener;
import com.siberanka.chunkgendebug.event.EventIdGenerator;
import com.siberanka.chunkgendebug.event.ServerLifecycleListener;
import com.siberanka.chunkgendebug.log.AsyncLogWriter;
import com.siberanka.chunkgendebug.log.WriterStats;
import com.siberanka.chunkgendebug.version.MinecraftVersion;
import com.siberanka.chunkgendebug.version.PlatformCapabilities;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChunkGenDebugPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {
    private AsyncLogWriter writer;
    private PluginSettings settings;
    private AttributionEngine attribution;
    private CorrelationTracker correlations;
    private ListenerInventory listenerInventory;
    private ChunkLifecycleListener chunkListener;
    private WorldContextRegistry contexts;
    private boolean folia;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            settings = PluginSettings.load(getConfig(), message -> getLogger().warning(message));
            writer = new AsyncLogWriter(
                    getDataFolder().toPath(), settings.writer(), message -> getLogger().severe(message));
        } catch (IllegalArgumentException | IOException failure) {
            getLogger().severe("Fail-closed startup: diagnostics cannot initialize: " + failure.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        PluginIdentityIndex pluginIndex = new PluginIdentityIndex();
        pluginIndex.refresh(Bukkit.getPluginManager().getPlugins());
        attribution = new AttributionEngine(pluginIndex, new MechanismClassifier(), settings.diagnostics());
        correlations = new CorrelationTracker(
                settings.diagnostics().correlationTtlMillis(),
                settings.diagnostics().maxCorrelationEntries());
        listenerInventory = new ListenerInventory(settings.diagnostics().maxEventListeners());
        contexts = new WorldContextRegistry(writer, pluginIndex);
        EventIdGenerator eventIds = new EventIdGenerator();
        PlatformCapabilities platform = PlatformCapabilities.detect(getClass().getClassLoader());
        folia = platform.folia();
        chunkListener = new ChunkLifecycleListener(
                writer,
                attribution,
                correlations,
                listenerInventory,
                contexts,
                eventIds,
                settings.diagnostics(),
                platform.folia());

        Bukkit.getPluginManager().registerEvents(chunkListener, this);
        Bukkit.getPluginManager().registerEvents(new ServerLifecycleListener(pluginIndex, contexts), this);
        PluginCommand command = requireNonNullCommand();
        command.setExecutor(this);
        command.setTabCompleter(this);

        for (World world : Bukkit.getWorlds()) {
            contexts.refresh(world, "plugin-enable-existing-world");
        }
        reportVersionSupport();
        getLogger().info("Forensic chunk diagnostics enabled; session=" + eventIds.session()
                + ", queueCapacity=" + settings.writer().queueCapacity()
                + ", folia=" + platform.folia()
                + ", output=" + getDataFolder().getAbsolutePath());
    }

    @Override
    public void onDisable() {
        if (writer != null) {
            WriterStats before = writer.stats();
            writer.close();
            WriterStats after = writer.stats();
            getLogger().info("Diagnostic writer stopped: accepted=" + after.accepted()
                    + ", written=" + after.written() + ", dropped=" + after.dropped()
                    + ", writeFailures=" + after.writeFailures()
                    + ", queuedBeforeDrain=" + before.queued());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("status")) {
            WriterStats stats = writer.stats();
            sender.sendMessage("chunk-gen-debug: accepted=" + stats.accepted()
                    + ", written=" + stats.written() + ", dropped=" + stats.dropped()
                    + ", failures=" + stats.writeFailures() + ", queued=" + stats.queued());
            return true;
        }
        if (action.equals("reload")) {
            if (folia && sender instanceof Player) {
                sender.sendMessage("On Folia, configuration reload must be run from console/global context");
                return true;
            }
            return reloadSafely(sender);
        }
        if (action.equals("probe")) {
            if (folia && sender instanceof Player) {
                sender.sendMessage("On Folia, chunk probes must be started from console/global context");
                return true;
            }
            return runProbe(sender, label, args);
        }
        sender.sendMessage("Usage: /" + label + " [status|reload|probe <world> <chunk-x> <chunk-z>]");
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return java.util.List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Arrays.asList("status", "reload", "probe").stream()
                .filter(value -> value.startsWith(prefix))
                .toList();
    }

    private boolean runProbe(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("chunkgendebug.probe")) {
            sender.sendMessage("You do not have permission to run chunk probes");
            return true;
        }
        if (args.length != 4) {
            sender.sendMessage("Usage: /" + label + " probe <world> <chunk-x> <chunk-z>");
            return true;
        }
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            sender.sendMessage("Unknown loaded world: " + args[1]);
            return true;
        }
        final int chunkX;
        final int chunkZ;
        try {
            chunkX = parseChunkCoordinate(args[2]);
            chunkZ = parseChunkCoordinate(args[3]);
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage(invalid.getMessage());
            return true;
        }
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("plugin", getName());
        details.put("operation", "admin-probe-generate-load-unload");
        if (settings.diagnostics().includePlayerIdentities()) {
            details.put("requester", sender.getName());
        }
        correlations.record(new com.siberanka.chunkgendebug.correlation.ChunkKey(
                world.getUID(), chunkX, chunkZ), "ADMIN_PROBE", details);
        Bukkit.getRegionScheduler().execute(this, world, chunkX, chunkZ, () -> {
            world.getChunkAt(chunkX, chunkZ, true);
            Bukkit.getRegionScheduler().runDelayed(this, world, chunkX, chunkZ,
                    ignored -> world.unloadChunkRequest(chunkX, chunkZ), 5L);
        });
        sender.sendMessage("Probe scheduled for " + world.getKey() + " chunk " + chunkX + "," + chunkZ
                + "; this may generate terrain");
        return true;
    }

    private static int parseChunkCoordinate(String value) {
        final int coordinate;
        try {
            coordinate = Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Chunk coordinates must be integers", invalid);
        }
        if (coordinate < -1_875_000 || coordinate > 1_875_000) {
            throw new IllegalArgumentException("Chunk coordinate is outside the Minecraft world border");
        }
        return coordinate;
    }

    private boolean reloadSafely(CommandSender sender) {
        reloadConfig();
        final PluginSettings loaded;
        try {
            loaded = PluginSettings.load(getConfig(), message -> getLogger().warning(message));
        } catch (IllegalArgumentException failure) {
            sender.sendMessage("Reload rejected: " + failure.getMessage());
            return true;
        }
        if (!loaded.writer().equals(settings.writer())) {
            sender.sendMessage("Reload rejected: logging.* changes require a full plugin/server restart");
            return true;
        }
        settings = loaded;
        attribution.updateSettings(loaded.diagnostics());
        correlations.updateLimits(
                loaded.diagnostics().correlationTtlMillis(),
                loaded.diagnostics().maxCorrelationEntries());
        listenerInventory.updateLimit(loaded.diagnostics().maxEventListeners());
        chunkListener.updateSettings(loaded.diagnostics());
        for (World world : Bukkit.getWorlds()) {
            contexts.refresh(world, "config-reload");
        }
        sender.sendMessage("chunk-gen-debug diagnostics configuration reloaded atomically");
        return true;
    }

    private void reportVersionSupport() {
        String raw = Bukkit.getMinecraftVersion();
        try {
            MinecraftVersion version = MinecraftVersion.parse(raw);
            if (!version.isDeclaredSupported()) {
                getLogger().warning("Minecraft " + raw
                        + " is outside the verified 1.21.x/26.x support matrix; diagnostics will continue best-effort");
            }
        } catch (IllegalArgumentException failure) {
            getLogger().warning("Unrecognized Minecraft version string '" + raw
                    + "'; no brittle 1.x-only parsing assumption was applied");
        }
    }

    private PluginCommand requireNonNullCommand() {
        PluginCommand command = getCommand("chunkgendebug");
        if (command == null) {
            throw new IllegalStateException("plugin.yml command registration missing");
        }
        return command;
    }
}
