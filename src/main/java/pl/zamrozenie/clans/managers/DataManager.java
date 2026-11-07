package pl.zamrozenie.clans.managers;

import pl.zamrozenie.clans.models.*;
import pl.zamrozenie.clans.utils.ClanColors;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * DataManager zarządza operacjami na danych klanów.
 * Aktualnie używa pamięci (HashMap), ale może być rozszerzony do bazy danych.
 */
public class DataManager {
    
    private final Plugin plugin;
    
    // Cache danych w pamięci
    private final Map<Integer, Clan> clans = new ConcurrentHashMap<>();
    private final Map<String, Integer> clanTagIndex = new ConcurrentHashMap<>(); // tag -> clanId
    private final Map<String, Integer> clanNameIndex = new ConcurrentHashMap<>(); // name -> clanId
    private final Map<UUID, Integer> playerClanIndex = new ConcurrentHashMap<>(); // playerUUID -> clanId
    private final Map<Integer, Alliance> alliances = new ConcurrentHashMap<>();
    private final Map<UUID, LocalDateTime> playerChatToggle = new ConcurrentHashMap<>();
    private final Map<UUID, LocalDateTime> playerLastHomeUse = new ConcurrentHashMap<>();
    
    // Przechowywanie zawartości skarbców klanów (UUID skarbca -> lista itemów)
    private final Map<UUID, List<ItemStack>> treasuryContents = new ConcurrentHashMap<>();
    
    private int nextClanId = 1;
    private int nextAllianceId = 1;
    
    public DataManager(Plugin plugin) {
        this.plugin = plugin;
        loadData();
    }
    
    // ─── CLAN OPERATIONS ────────────────────────────────────────────────────────
    
    public Clan createClan(String tag, String name, UUID leaderUUID) {
        // Sprawdź czy tag i nazwa są zajęte
        if (clanTagIndex.containsKey(tag.toLowerCase())) {
            return null;
        }
        if (clanNameIndex.containsKey(name.toLowerCase())) {
            return null;
        }
        
        int clanId = nextClanId++;
        Clan clan = new Clan(clanId, tag, name, leaderUUID, LocalDateTime.now());
        
        // Dodaj lidera do klanu
        ClanMember leader = new ClanMember(leaderUUID, "Unknown", ClanRank.LEADER, LocalDateTime.now());
        clan.addMember(leader);
        
        // Zapisz do cache
        clans.put(clanId, clan);
        clanTagIndex.put(tag.toLowerCase(), clanId);
        clanNameIndex.put(name.toLowerCase(), clanId);
        playerClanIndex.put(leaderUUID, clanId);
        
        return clan;
    }
    
    public Clan getClanById(int clanId) {
        return clans.get(clanId);
    }
    
    public Clan getClanByTag(String tag) {
        Integer clanId = clanTagIndex.get(tag.toLowerCase());
        return clanId != null ? clans.get(clanId) : null;
    }
    
    public Clan getClanByName(String name) {
        Integer clanId = clanNameIndex.get(name.toLowerCase());
        return clanId != null ? clans.get(clanId) : null;
    }
    
    public Clan getPlayerClan(UUID playerUUID) {
        Integer clanId = playerClanIndex.get(playerUUID);
        return clanId != null ? clans.get(clanId) : null;
    }
    
    public void deleteClan(int clanId) {
        Clan clan = clans.remove(clanId);
        if (clan != null) {
            clanTagIndex.remove(clan.getTag().toLowerCase());
            clanNameIndex.remove(clan.getName().toLowerCase());
            
            for (UUID playerUUID : clan.getMembers().keySet()) {
                playerClanIndex.remove(playerUUID);
            }
        }
    }
    
    public Collection<Clan> getAllClans() {
        return new ArrayList<>(clans.values());
    }
    
    // ─── MEMBER OPERATIONS ──────────────────────────────────────────────────────
    
    public boolean addMemberToClan(UUID playerUUID, String playerName, int clanId) {
        Clan clan = clans.get(clanId);
        if (clan == null) return false;
        
        if (clan.hasMember(playerUUID)) return false;
        if (playerClanIndex.containsKey(playerUUID)) return false;
        
        ClanMember member = new ClanMember(playerUUID, playerName, ClanRank.MEMBER, LocalDateTime.now());
        clan.addMember(member);
        playerClanIndex.put(playerUUID, clanId);
        
        return true;
    }
    
    public boolean removeMemberFromClan(UUID playerUUID, int clanId) {
        Clan clan = clans.get(clanId);
        if (clan == null) return false;
        
        clan.removeMember(playerUUID);
        playerClanIndex.remove(playerUUID);
        
        return true;
    }
    
    public boolean updateMemberRank(UUID playerUUID, int clanId, ClanRank newRank) {
        Clan clan = clans.get(clanId);
        if (clan == null) return false;
        
        ClanMember member = clan.getMember(playerUUID);
        if (member == null) return false;
        
        member.setRank(newRank);
        return true;
    }
    
    // ─── ALLIANCE OPERATIONS ────────────────────────────────────────────────────
    
    public Alliance createAlliance(int clanId1, int clanId2, int requestedBy) {
        // Sprawdź czy klan2 już ma alliance z clanId1
        for (Alliance alliance : alliances.values()) {
            if ((alliance.getClanId1() == clanId1 && alliance.getClanId2() == clanId2) ||
                (alliance.getClanId1() == clanId2 && alliance.getClanId2() == clanId1)) {
                return null;
            }
        }
        
        int allianceId = nextAllianceId++;
        Alliance alliance = new Alliance(clanId1, clanId2, Alliance.AllianceStatus.PENDING, requestedBy, LocalDateTime.now());
        alliances.put(allianceId, alliance);
        
        return alliance;
    }
    
    public Alliance getAllianceBetweenClans(int clanId1, int clanId2) {
        for (Alliance alliance : alliances.values()) {
            if ((alliance.getClanId1() == clanId1 && alliance.getClanId2() == clanId2) ||
                (alliance.getClanId1() == clanId2 && alliance.getClanId2() == clanId1)) {
                return alliance;
            }
        }
        return null;
    }
    
    public List<Alliance> getClanAlliances(int clanId) {
        List<Alliance> result = new ArrayList<>();
        for (Alliance alliance : alliances.values()) {
            if (alliance.getClanId1() == clanId || alliance.getClanId2() == clanId) {
                result.add(alliance);
            }
        }
        return result;
    }
    
    public void deleteAlliance(Alliance alliance) {
        alliances.values().remove(alliance);
    }
    
    public Collection<Alliance> getAllAlliances() {
        return new ArrayList<>(alliances.values());
    }
    
    // ─── PLAYER DATA OPERATIONS ─────────────────────────────────────────────────
    
    public boolean isClanChatEnabled(UUID playerUUID) {
        return playerChatToggle.containsKey(playerUUID);
    }
    
    public void setClanChatEnabled(UUID playerUUID, boolean enabled) {
        if (enabled) {
            playerChatToggle.put(playerUUID, LocalDateTime.now());
        } else {
            playerChatToggle.remove(playerUUID);
        }
    }
    
    public void recordHomeUse(UUID playerUUID) {
        playerLastHomeUse.put(playerUUID, LocalDateTime.now());
    }
    
    public LocalDateTime getLastHomeUse(UUID playerUUID) {
        return playerLastHomeUse.get(playerUUID);
    }
    
    public void clearLastHomeUse(UUID playerUUID) {
        playerLastHomeUse.remove(playerUUID);
    }
    
    // ─── DATA PERSISTENCE ────────────────────────────────────────────────────────
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    /**
     * Zapisuje wszystkie dane klanów do pliku YAML
     */
    public void saveData() {
        try {
            File dataFolder = new File(plugin.getDataFolder(), "clans");
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            
            File dataFile = new File(dataFolder, "data.yml");
            FileConfiguration config = new YamlConfiguration();
            
            // Zapisz nextClanId i nextAllianceId
            config.set("nextClanId", nextClanId);
            config.set("nextAllianceId", nextAllianceId);
            
            // Zapisz klany
            for (Map.Entry<Integer, Clan> entry : clans.entrySet()) {
                Clan clan = entry.getValue();
                String path = "clans." + entry.getKey();
                
                config.set(path + ".tag", clan.getTag());
                config.set(path + ".name", clan.getName());
                config.set(path + ".color", clan.getColor().name());
                config.set(path + ".leaderUUID", clan.getLeaderUUID().toString());
                config.set(path + ".createdAt", clan.getCreatedAt().format(DATE_FORMATTER));
                config.set(path + ".treasuryId", clan.getTreasuryId().toString());
                
                // Zapisz home
                if (clan.hasHome()) {
                    config.set(path + ".home.world", clan.getHomeWorld());
                    config.set(path + ".home.x", clan.getHomeX());
                    config.set(path + ".home.y", clan.getHomeY());
                    config.set(path + ".home.z", clan.getHomeZ());
                    config.set(path + ".home.yaw", clan.getHomeYaw());
                    config.set(path + ".home.pitch", clan.getHomePitch());
                }
                
                // Zapisz członków
                for (Map.Entry<UUID, ClanMember> memberEntry : clan.getMembers().entrySet()) {
                    ClanMember member = memberEntry.getValue();
                    String memberPath = path + ".members." + memberEntry.getKey().toString();
                    
                    config.set(memberPath + ".name", member.getPlayerName());
                    config.set(memberPath + ".rank", member.getRank().name());
                    config.set(memberPath + ".joinedAt", member.getJoinedAt().format(DATE_FORMATTER));
                }
            }
            
            // Zapisz sojusze
            for (Map.Entry<Integer, Alliance> entry : alliances.entrySet()) {
                Alliance alliance = entry.getValue();
                String path = "alliances." + entry.getKey();
                
                config.set(path + ".clanId1", alliance.getClanId1());
                config.set(path + ".clanId2", alliance.getClanId2());
                config.set(path + ".status", alliance.getStatus().name());
                config.set(path + ".requestedBy", alliance.getRequestedBy());
                config.set(path + ".createdAt", alliance.getCreatedAt().format(DATE_FORMATTER));
            }
            
            config.save(dataFile);
            plugin.getLogger().info("Zapisano dane " + clans.size() + " klanów i " + alliances.size() + " sojuszy.");
            
        } catch (IOException e) {
            plugin.getLogger().severe("Nie można zapisać danych klanów!");
            e.printStackTrace();
        }
    }
    
    /**
     * Ładuje wszystkie dane klanów z pliku YAML
     */
    public void loadData() {
        try {
            File dataFile = new File(new File(plugin.getDataFolder(), "clans"), "data.yml");
            
            if (!dataFile.exists()) {
                plugin.getLogger().info("Brak pliku z danymi klanów - rozpoczynam od czystej bazy.");
                return;
            }
            
            FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
            
            // Wczytaj nextClanId i nextAllianceId
            nextClanId = config.getInt("nextClanId", 1);
            nextAllianceId = config.getInt("nextAllianceId", 1);
            
            // Wczytaj klany
            if (config.contains("clans")) {
                for (String clanIdStr : config.getConfigurationSection("clans").getKeys(false)) {
                    int clanId = Integer.parseInt(clanIdStr);
                    String path = "clans." + clanId;
                    
                    String tag = config.getString(path + ".tag");
                    String name = config.getString(path + ".name");
                    UUID leaderUUID = UUID.fromString(config.getString(path + ".leaderUUID"));
                    LocalDateTime createdAt = LocalDateTime.parse(config.getString(path + ".createdAt"), DATE_FORMATTER);
                    
                    Clan clan = new Clan(clanId, tag, name, leaderUUID, createdAt);
                    clan.setColor(ClanColors.fromStored(config.getString(path + ".color", "AQUA")));
                    
                    // Wczytaj treasuryId jeśli istnieje
                    if (config.contains(path + ".treasuryId")) {
                        UUID treasuryId = UUID.fromString(config.getString(path + ".treasuryId"));
                        clan.setTreasuryId(treasuryId);
                    }
                    
                    // Wczytaj home
                    if (config.contains(path + ".home")) {
                        String world = config.getString(path + ".home.world");
                        double x = config.getDouble(path + ".home.x");
                        double y = config.getDouble(path + ".home.y");
                        double z = config.getDouble(path + ".home.z");
                        float yaw = (float) config.getDouble(path + ".home.yaw");
                        float pitch = (float) config.getDouble(path + ".home.pitch");
                        clan.setHome(world, x, y, z, yaw, pitch);
                    }
                    
                    // Wczytaj członków
                    if (config.contains(path + ".members")) {
                        for (String memberUUIDStr : config.getConfigurationSection(path + ".members").getKeys(false)) {
                            UUID memberUUID = UUID.fromString(memberUUIDStr);
                            String memberPath = path + ".members." + memberUUIDStr;
                            
                            String memberName = config.getString(memberPath + ".name");
                            ClanRank rank = ClanRank.valueOf(config.getString(memberPath + ".rank"));
                            LocalDateTime joinedAt = LocalDateTime.parse(config.getString(memberPath + ".joinedAt"), DATE_FORMATTER);
                            
                            ClanMember member = new ClanMember(memberUUID, memberName, rank, joinedAt);
                            clan.addMember(member);
                            
                            // Zaktualizuj indeksy
                            playerClanIndex.put(memberUUID, clanId);
                        }
                    }
                    
                    // Dodaj klan do cache
                    clans.put(clanId, clan);
                    clanTagIndex.put(tag.toLowerCase(), clanId);
                    clanNameIndex.put(name.toLowerCase(), clanId);
                }
            }
            
            // Wczytaj sojusze
            if (config.contains("alliances")) {
                for (String allianceIdStr : config.getConfigurationSection("alliances").getKeys(false)) {
                    int allianceId = Integer.parseInt(allianceIdStr);
                    String path = "alliances." + allianceId;
                    
                    int clanId1 = config.getInt(path + ".clanId1");
                    int clanId2 = config.getInt(path + ".clanId2");
                    Alliance.AllianceStatus status = Alliance.AllianceStatus.valueOf(config.getString(path + ".status"));
                    int requestedBy = config.getInt(path + ".requestedBy");
                    LocalDateTime createdAt = LocalDateTime.parse(config.getString(path + ".createdAt"), DATE_FORMATTER);
                    
                    Alliance alliance = new Alliance(clanId1, clanId2, status, requestedBy, createdAt);
                    alliances.put(allianceId, alliance);
                }
            }
            
            plugin.getLogger().info("Wczytano " + clans.size() + " klanów i " + alliances.size() + " sojuszy.");
            
        } catch (Exception e) {
            plugin.getLogger().severe("Błąd podczas wczytywania danych klanów!");
            e.printStackTrace();
        }
    }
    
    // ─── TREASURY PERSISTENCE ────────────────────────────────────────────────────
    
    /**
     * Zapisuje zawartość skarbca klanu do pliku binarnego
     */
    public void saveTreasuryContents(UUID treasuryId, List<ItemStack> items) {
        treasuryContents.put(treasuryId, new ArrayList<>(items));
        
        try {
            File dataFolder = new File(plugin.getDataFolder(), "clans/treasuries");
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            
            File treasuryFile = new File(dataFolder, treasuryId.toString() + ".dat");
            
            try (FileOutputStream fos = new FileOutputStream(treasuryFile);
                 GZIPOutputStream gzos = new GZIPOutputStream(fos);
                 BukkitObjectOutputStream oos = new BukkitObjectOutputStream(gzos)) {
                
                oos.writeInt(items.size());
                for (ItemStack item : items) {
                    oos.writeObject(item);
                }
            }
            
        } catch (IOException e) {
            plugin.getLogger().severe("Nie można zapisać zawartości skarbca " + treasuryId);
            e.printStackTrace();
        }
    }
    
    /**
     * Wczytuje zawartość skarbca klanu z pliku binarnego
     */
    public List<ItemStack> loadTreasuryContents(UUID treasuryId) {
        // Sprawdź cache
        List<ItemStack> cached = treasuryContents.get(treasuryId);
        if (cached != null) {
            return new ArrayList<>(cached);
        }
        
        // Wczytaj z pliku
        try {
            File treasuryFile = new File(new File(plugin.getDataFolder(), "clans/treasuries"), treasuryId.toString() + ".dat");
            
            if (!treasuryFile.exists()) {
                return new ArrayList<>();
            }
            
            try (FileInputStream fis = new FileInputStream(treasuryFile);
                 GZIPInputStream gzis = new GZIPInputStream(fis);
                 BukkitObjectInputStream ois = new BukkitObjectInputStream(gzis)) {
                
                int size = ois.readInt();
                List<ItemStack> items = new ArrayList<>();
                
                for (int i = 0; i < size; i++) {
                    items.add((ItemStack) ois.readObject());
                }
                
                treasuryContents.put(treasuryId, items);
                return items;
            }
            
        } catch (IOException | ClassNotFoundException e) {
            plugin.getLogger().severe("Nie można wczytać zawartości skarbca " + treasuryId);
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    /**
     * Usuwa zawartość skarbca z cache i dysku
     */
    public void deleteTreasuryContents(UUID treasuryId) {
        treasuryContents.remove(treasuryId);
        
        File treasuryFile = new File(new File(plugin.getDataFolder(), "clans/treasuries"), treasuryId.toString() + ".dat");
        if (treasuryFile.exists()) {
            treasuryFile.delete();
        }
    }
}