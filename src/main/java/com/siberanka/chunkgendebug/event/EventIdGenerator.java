package com.siberanka.chunkgendebug.event;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class EventIdGenerator {
    private final String session = UUID.randomUUID().toString();
    private final AtomicLong sequence = new AtomicLong();

    public String next() {
        return session + ":" + sequence.incrementAndGet();
    }

    public String session() {
        return session;
    }
}
