package com.siberanka.chunkgendebug.attribution;

public record StackFrameView(
        int index,
        String className,
        String methodName,
        String fileName,
        int lineNumber,
        String moduleName,
        String plugin) {
}
