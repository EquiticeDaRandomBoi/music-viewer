// common-src/com/evand/musicplayer/config/ModConfig.java
package com.evand.musicplayer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
        FabricLoader.getInstance().getConfigDir().resolve("musicplayer.json");

    public float pillXFrac = 0.5f;
    public float pillYFrac = 0.02f;
    public String dragKey  = "key.keyboard.left.alt";

    public static ModConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                return GSON.fromJson(json, ModConfig.class);
            } catch (IOException | com.google.gson.JsonParseException e) {
                return new ModConfig();
            }
        }
        ModConfig cfg = new ModConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            // Non-fatal: position just won't persist this session
        }
    }
}
