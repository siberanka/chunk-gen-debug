package com.siberanka.chunkgendebug.version;

import java.util.ArrayList;
import java.util.List;

public record MinecraftVersion(String raw, List<Integer> components) implements Comparable<MinecraftVersion> {
    public MinecraftVersion {
        components = List.copyOf(components);
        if (components.size() < 2) {
            throw new IllegalArgumentException("Version must contain at least two numeric components: " + raw);
        }
    }

    public static MinecraftVersion parse(String raw) {
        String core = raw.split("[-+]", 2)[0];
        String[] pieces = core.split("\\.");
        ArrayList<Integer> components = new ArrayList<>(pieces.length);
        for (String piece : pieces) {
            if (piece.isEmpty() || !piece.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("Invalid Minecraft version: " + raw);
            }
            components.add(Integer.parseInt(piece));
        }
        return new MinecraftVersion(raw, components);
    }

    public boolean isDeclaredSupported() {
        return components.getFirst() == 26 || components.getFirst() == 1 && components.get(1) == 21;
    }

    @Override
    public int compareTo(MinecraftVersion other) {
        int size = Math.max(components.size(), other.components.size());
        for (int index = 0; index < size; index++) {
            int left = index < components.size() ? components.get(index) : 0;
            int right = index < other.components.size() ? other.components.get(index) : 0;
            int comparison = Integer.compare(left, right);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }
}
