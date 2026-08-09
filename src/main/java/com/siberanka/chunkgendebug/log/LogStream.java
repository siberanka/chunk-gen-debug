package com.siberanka.chunkgendebug.log;

public enum LogStream {
    GENERATION("chunk-gen.log"),
    LOAD("chunk-load.log"),
    UNLOAD("chunk-unload.log");

    private final String fileName;

    LogStream(String fileName) {
        this.fileName = fileName;
    }

    public String fileName() {
        return fileName;
    }
}
