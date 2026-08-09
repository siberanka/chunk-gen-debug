package com.siberanka.chunkgendebug.log;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;

public final class WorldDirectoryResolver {
    private static final int MAX_BASE_LENGTH = 80;
    private final Path root;

    public WorldDirectoryResolver(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public String directoryName(String worldName) {
        String normalized = Normalizer.normalize(worldName, Normalizer.Form.NFKC);
        StringBuilder safe = new StringBuilder(Math.min(normalized.length(), MAX_BASE_LENGTH));
        boolean changed = false;
        for (int index = 0; index < normalized.length() && safe.length() < MAX_BASE_LENGTH; index++) {
            char character = normalized.charAt(index);
            if (isSafe(character)) {
                safe.append(character);
            } else {
                safe.append('_');
                changed = true;
            }
        }
        changed |= normalized.length() > MAX_BASE_LENGTH;
        String base = safe.toString().replaceAll("[. ]+$", "");
        if (base.isBlank() || base.equals(".") || base.equals("..")) {
            base = "world";
            changed = true;
        }
        if (changed || !normalized.equals(base)) {
            base = base + "--" + shortHash(worldName);
        }
        Path candidate = root.resolve(base).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("World path escaped plugin data folder");
        }
        return base;
    }

    public Path resolve(LogTarget target) {
        Path candidate = root.resolve(target.worldDirectory()).resolve(target.stream().fileName()).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("Log path escaped plugin data folder");
        }
        return candidate;
    }

    public void verifySafeParent(Path path) throws IOException {
        Path parent = path.getParent();
        Files.createDirectories(parent);
        Path realParent = parent.toRealPath();
        Path realRoot = root.toRealPath();
        if (!realParent.startsWith(realRoot) || Files.isSymbolicLink(path)) {
            throw new IOException("Log target resolves outside the plugin data folder: " + path);
        }
    }

    private static boolean isSafe(char character) {
        return character >= 'a' && character <= 'z'
                || character >= 'A' && character <= 'Z'
                || character >= '0' && character <= '9'
                || character == '-' || character == '_' || character == '.';
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
