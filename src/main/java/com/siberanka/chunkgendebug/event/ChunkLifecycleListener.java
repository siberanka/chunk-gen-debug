package com.siberanka.chunkgendebug.event;

import com.siberanka.chunkgendebug.attribution.AttributionEngine;
import com.siberanka.chunkgendebug.attribution.AttributionSnapshot;
import com.siberanka.chunkgendebug.config.DiagnosticSettings;
import com.siberanka.chunkgendebug.context.ListenerInventory;
import com.siberanka.chunkgendebug.context.WorldContextRegistry;
import com.siberanka.chunkgendebug.context.WorldContextRegistry.ContextRef;
import com.siberanka.chunkgendebug.correlation.CausalMarker;
import com.siberanka.chunkgendebug.correlation.ChunkKey;
import com.siberanka.chunkgendebug.correlation.CorrelationTracker;
import com.siberanka.chunkgendebug.log.AsyncLogWriter;
import com.siberanka.chunkgendebug.log.DiagnosticRecord;
import com.siberanka.chunkgendebug.log.LogStream;
import com.siberanka.chunkgendebug.log.LogTarget;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public final class ChunkLifecycleListener implements Listener {
    private final ThreadLocal<IdentityHashMap<Event, Pending>> pending =
            ThreadLocal.withInitial(IdentityHashMap::new);
    private final AsyncLogWriter writer;
    private final AttributionEngine attribution;
    private final CorrelationTracker correlations;
    private final ListenerInventory listenerInventory;
    private final WorldContextRegistry contexts;
    private final EventIdGenerator eventIds;
    private final boolean folia;
    private volatile DiagnosticSettings settings;

    public ChunkLifecycleListener(
            AsyncLogWriter writer,
            AttributionEngine attribution,
            CorrelationTracker correlations,
            ListenerInventory listenerInventory,
            WorldContextRegistry contexts,
            EventIdGenerator eventIds,
            DiagnosticSettings settings,
            boolean folia) {
        this.writer = writer;
        this.attribution = attribution;
        this.correlations = correlations;
        this.listenerInventory = listenerInventory;
        this.contexts = contexts;
        this.eventIds = eventIds;
        this.settings = settings;
        this.folia = folia;
    }

    public void updateSettings(DiagnosticSettings settings) {
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChunkLoadStart(ChunkLoadEvent event) {
        begin(event, event.isNewChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoadEnd(ChunkLoadEvent event) {
        Pending observation = end(event);
        DiagnosticRecord record = base("chunk_load", event, observation)
                .put("newChunk", event.isNewChunk())
                .put("apiFact", event.isNewChunk()
                        ? "ChunkLoadEvent confirms this chunk was newly created"
                        : "ChunkLoadEvent confirms load of an existing chunk")
                .build();
        submit(event.getWorld(), LogStream.LOAD, record);
        if (event.isNewChunk()) {
            DiagnosticRecord generation = base("generation_detected", event, observation)
                    .put("newChunk", true)
                    .put("stage", "CHUNK_LOAD_NEW_FLAG")
                    .build();
            submit(event.getWorld(), LogStream.GENERATION, generation);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChunkUnloadStart(ChunkUnloadEvent event) {
        begin(event, event.isSaveChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnloadEnd(ChunkUnloadEvent event) {
        Pending observation = end(event);
        DiagnosticRecord record = base("chunk_unload", event, observation)
                .put("initialSaveChunk", observation.initialBoolean())
                .put("finalSaveChunk", event.isSaveChunk())
                .put("saveFlagChangedByListeners", observation.initialBoolean() != event.isSaveChunk())
                .build();
        submit(event.getWorld(), LogStream.UNLOAD, record);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkPopulate(ChunkPopulateEvent event) {
        Pending observation = snapshot(event, false);
        DiagnosticRecord record = base("population_complete", event, observation)
                .put("stage", "CHUNK_POPULATE_EVENT")
                .put("apiFact", "newly generated chunk finished Bukkit population stage")
                .build();
        submit(event.getWorld(), LogStream.GENERATION, record);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        submitEntities(event, event.getEntities(), LogStream.LOAD, "entities_load");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        submitEntities(event, event.getEntities(), LogStream.UNLOAD, "entities_unload");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldInit(WorldInitEvent event) {
        contexts.refresh(event.getWorld(), "WorldInitEvent");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        contexts.refresh(event.getWorld(), "WorldLoadEvent");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldSave(WorldSaveEvent event) {
        writeWorldLifecycle(event, event.getWorld(), "world_save", false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        writeWorldLifecycle(event, event.getWorld(), "world_unload", event.isCancelled());
        if (!event.isCancelled()) {
            contexts.remove(event.getWorld());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("teleportCause", event.getCause().name());
        details.put("fromWorld", event.getFrom().getWorld().getKey().toString());
        if (settings.includePlayerIdentities()) {
            details.put("playerUuid", event.getPlayer().getUniqueId().toString());
            details.put("playerName", event.getPlayer().getName());
        }
        int x = event.getTo().getBlockX() >> 4;
        int z = event.getTo().getBlockZ() >> 4;
        correlations.record(new ChunkKey(event.getTo().getWorld().getUID(), x, z),
                "PLAYER_TELEPORT_DESTINATION", details);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        World world = event.getPlayer().getWorld();
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("fromWorld", event.getFrom().getKey().toString());
        details.put("toWorld", world.getKey().toString());
        if (settings.includePlayerIdentities()) {
            details.put("playerUuid", event.getPlayer().getUniqueId().toString());
            details.put("playerName", event.getPlayer().getName());
        }
        int x = event.getPlayer().getLocation().getBlockX() >> 4;
        int z = event.getPlayer().getLocation().getBlockZ() >> 4;
        correlations.record(new ChunkKey(world.getUID(), x, z), "PLAYER_WORLD_CHANGE", details);
    }

    private void begin(Event event, boolean initialBoolean) {
        pending.get().put(event, snapshot(event, initialBoolean));
    }

    private Pending end(Event event) {
        IdentityHashMap<Event, Pending> local = pending.get();
        Pending result = local.remove(event);
        if (local.isEmpty()) {
            pending.remove();
        }
        return result != null ? result : snapshot(event, false);
    }

    private Pending snapshot(Event event, boolean initialBoolean) {
        return new Pending(
                eventIds.next(),
                System.nanoTime(),
                initialBoolean,
                attribution.capture(),
                listenerInventory.inspect(event));
    }

    private DiagnosticRecord.Builder base(String kind, Event event, Pending observation) {
        Chunk chunk = chunkOf(event);
        ContextRef context = contexts.getOrCreate(chunk.getWorld());
        long now = System.nanoTime();
        CausalMarker marker = correlations.find(new ChunkKey(
                chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()));
        return DiagnosticRecord.builder(kind, observation.eventId())
                .put("contextId", context.contextId())
                .put("eventClass", event.getClass().getName())
                .put("asynchronousEvent", event.isAsynchronous())
                .put("observerSpanMicros", Math.max(0, now - observation.startedNanos()) / 1_000L)
                .put("thread", ObservationMapper.thread())
                .put("worldKey", chunk.getWorld().getKey().toString())
                .put("worldUuid", chunk.getWorld().getUID().toString())
                .put("chunk", ObservationMapper.chunk(chunk, folia))
                .put("attribution", ObservationMapper.attribution(observation.attribution()))
                .put("recentCause", ObservationMapper.correlation(marker, now))
                .put("listenerPipeline", observation.listenerPipeline());
    }

    private void submitEntities(Event event, List<Entity> entities, LogStream stream, String kind) {
        Pending observation = snapshot(event, false);
        DiagnosticRecord record = base(kind, event, observation)
                .put("entities", entitySummary(entities))
                .build();
        submit(chunkOf(event).getWorld(), stream, record);
    }

    private Map<String, Object> entitySummary(List<Entity> entities) {
        EnumMap<org.bukkit.entity.EntityType, Integer> counts = new EnumMap<>(org.bukkit.entity.EntityType.class);
        ArrayList<Map<String, Object>> details = new ArrayList<>();
        int limit = Math.min(entities.size(), settings.maxEntityDetails());
        for (int index = 0; index < entities.size(); index++) {
            Entity entity = entities.get(index);
            counts.merge(entity.getType(), 1, Integer::sum);
            if (index < limit) {
                details.add(Map.of(
                        "uuid", entity.getUniqueId().toString(),
                        "type", entity.getType().getKey().toString(),
                        "persistent", entity.isPersistent(),
                        "valid", entity.isValid()));
            }
        }
        LinkedHashMap<String, Integer> byType = new LinkedHashMap<>();
        counts.forEach((type, count) -> byType.put(type.getKey().toString(), count));
        return Map.of(
                "total", entities.size(),
                "byType", Map.copyOf(byType),
                "detailLimit", settings.maxEntityDetails(),
                "detailsTruncated", entities.size() > limit,
                "details", List.copyOf(details));
    }

    private void writeWorldLifecycle(Event event, World world, String kind, boolean cancelled) {
        ContextRef context = contexts.getOrCreate(world);
        Pending observation = snapshot(event, cancelled);
        DiagnosticRecord record = DiagnosticRecord.builder(kind, observation.eventId())
                .put("contextId", context.contextId())
                .put("eventClass", event.getClass().getName())
                .put("cancelled", cancelled)
                .put("thread", ObservationMapper.thread())
                .put("worldKey", world.getKey().toString())
                .put("worldUuid", world.getUID().toString())
                .put("attribution", ObservationMapper.attribution(observation.attribution()))
                .put("listenerPipeline", observation.listenerPipeline())
                .build();
        for (LogStream stream : LogStream.values()) {
            submit(world, stream, record);
        }
    }

    private void submit(World world, LogStream stream, DiagnosticRecord record) {
        ContextRef context = contexts.getOrCreate(world);
        writer.submit(new LogTarget(context.worldDirectory(), stream), record);
    }

    private static Chunk chunkOf(Event event) {
        if (event instanceof org.bukkit.event.world.ChunkEvent chunkEvent) {
            return chunkEvent.getChunk();
        }
        throw new IllegalArgumentException("Not a chunk event: " + event.getClass().getName());
    }

    private record Pending(
            String eventId,
            long startedNanos,
            boolean initialBoolean,
            AttributionSnapshot attribution,
            Map<String, Object> listenerPipeline) {
    }
}
