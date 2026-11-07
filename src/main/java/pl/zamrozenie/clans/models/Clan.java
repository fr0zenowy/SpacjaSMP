package pl.zamrozenie.clans.models;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import java.time.LocalDateTime;
import java.util.*;

public class Clan {
    private final int id;
    private final String tag;
    private String name;
    private ChatColor color = ChatColor.AQUA;
    private final UUID leaderUUID;
    private final LocalDateTime createdAt;
    private final Map<UUID, ClanMember> members;
    
    // Treasury ID - unikalny identyfikator skarbca klanu
    private UUID treasuryId;
    
    // Home location
    private String homeWorld;
    private double homeX;
    private double homeY;
    private double homeZ;
    private float homeYaw;
    private float homePitch;
    private boolean hasHome;

    public Clan(int id, String tag, String name, UUID leaderUUID, LocalDateTime createdAt) {
        this.id = id;
        this.tag = tag;
        this.name = name;
        this.leaderUUID = leaderUUID;
        this.createdAt = createdAt;
        this.members = new HashMap<>();
        this.hasHome = false;
        this.treasuryId = UUID.randomUUID(); // Generuj unikalny ID skarbca
    }

    public int getId() {
        return id;
    }

    public String getTag() {
        return tag;
    }

    public ChatColor getColor() {
        return color != null ? color : ChatColor.AQUA;
    }

    public void setColor(ChatColor color) {
        this.color = color != null ? color : ChatColor.AQUA;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getLeaderUUID() {
        return leaderUUID;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Map<UUID, ClanMember> getMembers() {
        return members;
    }

    public void addMember(ClanMember member) {
        members.put(member.getPlayerUUID(), member);
    }

    public void removeMember(UUID playerUUID) {
        members.remove(playerUUID);
    }

    public ClanMember getMember(UUID playerUUID) {
        return members.get(playerUUID);
    }

    public boolean hasMember(UUID playerUUID) {
        return members.containsKey(playerUUID);
    }

    public int getMemberCount() {
        return members.size();
    }

    public List<ClanMember> getMembersByRank(ClanRank rank) {
        List<ClanMember> result = new ArrayList<>();
        for (ClanMember member : members.values()) {
            if (member.getRank() == rank) {
                result.add(member);
            }
        }
        return result;
    }

    public ClanMember getLeader() {
        return members.get(leaderUUID);
    }

    // Home methods
    public boolean hasHome() {
        return hasHome;
    }

    public void setHome(Location location) {
        this.homeWorld = location.getWorld().getName();
        this.homeX = location.getX();
        this.homeY = location.getY();
        this.homeZ = location.getZ();
        this.homeYaw = location.getYaw();
        this.homePitch = location.getPitch();
        this.hasHome = true;
    }

    public void setHome(String world, double x, double y, double z, float yaw, float pitch) {
        this.homeWorld = world;
        this.homeX = x;
        this.homeY = y;
        this.homeZ = z;
        this.homeYaw = yaw;
        this.homePitch = pitch;
        this.hasHome = true;
    }

    public String getHomeWorld() {
        return homeWorld;
    }

    public double getHomeX() {
        return homeX;
    }

    public double getHomeY() {
        return homeY;
    }

    public double getHomeZ() {
        return homeZ;
    }

    public float getHomeYaw() {
        return homeYaw;
    }

    public float getHomePitch() {
        return homePitch;
    }
    
    // Treasury methods
    public UUID getTreasuryId() {
        return treasuryId;
    }
    
    public void setTreasuryId(UUID treasuryId) {
        this.treasuryId = treasuryId;
    }
}