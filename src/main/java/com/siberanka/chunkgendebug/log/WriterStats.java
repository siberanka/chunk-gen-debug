package com.siberanka.chunkgendebug.log;

public record WriterStats(long accepted, long written, long dropped, long writeFailures, int queued) {
}
