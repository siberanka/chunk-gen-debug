package com.siberanka.chunkgendebug.attribution;

import com.siberanka.chunkgendebug.config.DiagnosticSettings;
import java.lang.StackWalker.Option;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AttributionEngine {
    private static final String SELF_PACKAGE = "com.siberanka.chunkgendebug.";
    private final StackWalker walker = StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE);
    private final PluginIdentityIndex pluginIndex;
    private final MechanismClassifier classifier;
    private volatile DiagnosticSettings settings;

    public AttributionEngine(
            PluginIdentityIndex pluginIndex,
            MechanismClassifier classifier,
            DiagnosticSettings settings) {
        this.pluginIndex = pluginIndex;
        this.classifier = classifier;
        this.settings = settings;
    }

    public void updateSettings(DiagnosticSettings settings) {
        this.settings = settings;
    }

    public AttributionSnapshot capture() {
        DiagnosticSettings snapshot = settings;
        if (!snapshot.captureStackTraces()) {
            return new AttributionSnapshot("UNAVAILABLE_STACK_CAPTURE_DISABLED", Mechanism.UNKNOWN, List.of(), List.of());
        }
        List<StackWalker.StackFrame> raw = walker.walk(stream -> stream
                .filter(frame -> !frame.getClassName().startsWith(SELF_PACKAGE))
                .limit(snapshot.maxStackFrames())
                .toList());
        ArrayList<StackFrameView> frames = new ArrayList<>(raw.size());
        ArrayList<PluginCandidate> candidates = new ArrayList<>();
        ArrayList<String> classNames = new ArrayList<>(raw.size());
        Set<String> seenPlugins = new HashSet<>();
        for (int index = 0; index < raw.size(); index++) {
            StackWalker.StackFrame frame = raw.get(index);
            PluginIdentityIndex.Identity identity = pluginIndex.find(frame.getDeclaringClass());
            String pluginName = identity == null ? null : identity.name();
            frames.add(new StackFrameView(
                    index,
                    frame.getClassName(),
                    frame.getMethodName(),
                    frame.getFileName(),
                    frame.getLineNumber(),
                    frame.getDeclaringClass().getModule().getName(),
                    pluginName));
            classNames.add(frame.getClassName());
            if (identity != null && seenPlugins.add(identity.name())) {
                candidates.add(new PluginCandidate(
                        identity.name(), identity.version(), "plugin-owned class in active call stack",
                        "HIGH_CANDIDATE_NOT_PROOF", index));
            }
        }
        Mechanism mechanism = classifier.classify(classNames, !candidates.isEmpty());
        String status = candidates.isEmpty() ? "NO_PLUGIN_CANDIDATE" : "STACK_CANDIDATE";
        return new AttributionSnapshot(status, mechanism, List.copyOf(candidates), List.copyOf(frames));
    }
}
