package pl.zamrozenie.clans.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;

public class ConfigUtil {
    
    private final Plugin plugin;
    private File configFile;
    private FileConfiguration config;
    
    public ConfigUtil(Plugin plugin, String fileName) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), fileName);
        
        if (!configFile.exists()) {
            plugin.saveResource(fileName, false);
        }
        
        this.config = YamlConfiguration.loadConfiguration(configFile);
    }
    
    public FileConfiguration getConfig() {
        return config;
    }
    
    public void reload() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }
    
    public void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie można zapisać pliku: " + configFile.getName());
            e.printStackTrace();
        }
    }
    
    public void set(String path, Object value) {
        config.set(path, value);
        save();
    }
    
    public String getString(String path, String defaultValue) {
        return config.getString(path, defaultValue);
    }
    
    public int getInt(String path, int defaultValue) {
        return config.getInt(path, defaultValue);
    }
    
    public boolean getBoolean(String path, boolean defaultValue) {
        return config.getBoolean(path, defaultValue);
    }
    
    public double getDouble(String path, double defaultValue) {
        return config.getDouble(path, defaultValue);
    }
}