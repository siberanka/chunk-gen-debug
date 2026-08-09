package com.siberanka.chunkgendebug.correlation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class CorrelationTracker {
    private final ConcurrentHashMap<ChunkKey, CausalMarker> markers = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Entry> insertionOrder = new ConcurrentLinkedQueue<>();
    private volatile long ttlNanos;
    private volatile int maximumEntries;

    public CorrelationTracker(long ttlMillis, int maximumEntries) {
        updateLimits(ttlMillis, maximumEntries);
    }

    public void updateLimits(long ttlMillis, int maximumEntries) {
        this.ttlNanos = Math.multiplyExact(ttlMillis, 1_000_000L);
        this.maximumEntries = maximumEntries;
        trim(System.nanoTime());
    }

    public void record(ChunkKey key, String type, Map<String, Object> details) {
        long now = System.nanoTime();
        CausalMarker marker = new CausalMarker(now, type, details);
        markers.put(key, marker);
        insertionOrder.add(new Entry(key, marker));
        trim(now);
    }

    public CausalMarker find(ChunkKey key) {
        long now = System.nanoTime();
        CausalMarker marker = markers.get(key);
        if (marker == null) {
            return null;
        }
        if (now - marker.createdNanos() > ttlNanos) {
            markers.remove(key, marker);
            return null;
        }
        return marker;
    }

    int size() {
        return markers.size();
    }

    private void trim(long now) {
        while (markers.size() > maximumEntries) {
            removeOldest();
        }
        Entry head;
        while ((head = insertionOrder.peek()) != null
                && now - head.marker().createdNanos() > ttlNanos) {
            removeOldest();
        }
    }

    private void removeOldest() {
        Entry oldest = insertionOrder.poll();
        if (oldest != null) {
            markers.remove(oldest.key(), oldest.marker());
        }
    }

    private record Entry(ChunkKey key, CausalMarker marker) {
    }
}
