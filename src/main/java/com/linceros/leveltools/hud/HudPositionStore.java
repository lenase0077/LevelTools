package com.linceros.leveltools.hud;

import com.hypixel.hytale.logger.HytaleLogger;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Persists per-player HUD positions to disk so they survive server restarts.
 * Stored as JSON at: &lt;dataDirectory&gt;/hud_positions.json
 * Format: {"&lt;uuid&gt;": [x, y], ...}
 */
public class HudPositionStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Path filePath;
    private final Map<UUID, int[]> positions = new ConcurrentHashMap<>();

    public HudPositionStore(Path dataDirectory) {
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).log("[LevelTools] Could not create data directory: " + e.getMessage());
        }
        this.filePath = dataDirectory.resolve("hud_positions.json");
        load();
    }

    /**
     * Returns the saved position for this player, or null if not set.
     */
    public int[] get(UUID uuid) {
        return positions.get(uuid);
    }

    /**
     * Save a custom position for this player and persist to disk.
     */
    public void set(UUID uuid, int x, int y) {
        positions.put(uuid, new int[]{x, y});
        save();
    }

    /**
     * Remove the custom position for this player and persist to disk.
     */
    public void remove(UUID uuid) {
        if (positions.remove(uuid) != null) {
            save();
        }
    }

    private void load() {
        if (!Files.exists(filePath)) {
            return;
        }
        try {
            String json = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            BsonDocument doc = BsonDocument.parse(json);
            for (Map.Entry<String, BsonValue> entry : doc.entrySet()) {
                try {
                    BsonArray arr = entry.getValue().asArray();
                    int x = arr.get(0).asInt32().getValue();
                    int y = arr.get(1).asInt32().getValue();
                    positions.put(UUID.fromString(entry.getKey()), new int[]{x, y});
                } catch (Exception ignored) {
                    // Skip malformed entry
                }
            }
            LOGGER.at(Level.INFO).log("[LevelTools] Loaded %d HUD positions.", positions.size());
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("[LevelTools] Failed to load HUD positions: " + e.getMessage());
        }
    }

    private synchronized void save() {
        try {
            BsonDocument doc = new BsonDocument();
            for (Map.Entry<UUID, int[]> entry : positions.entrySet()) {
                BsonArray arr = new BsonArray();
                arr.add(new BsonInt32(entry.getValue()[0]));
                arr.add(new BsonInt32(entry.getValue()[1]));
                doc.put(entry.getKey().toString(), arr);
            }
            Files.write(filePath, doc.toJson().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).log("[LevelTools] Failed to save HUD positions: " + e.getMessage());
        }
    }
}
