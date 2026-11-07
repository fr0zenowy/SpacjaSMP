package pl.zamrozenie.clans.models;

import java.util.UUID;
import java.time.LocalDateTime;

public class ClanMember {
    private final UUID playerUUID;
    private String playerName;
    private ClanRank rank;
    private final LocalDateTime joinedAt;

    public ClanMember(UUID playerUUID, String playerName, ClanRank rank, LocalDateTime joinedAt) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.rank = rank;
        this.joinedAt = joinedAt;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public ClanRank getRank() {
        return rank;
    }

    public void setRank(ClanRank rank) {
        this.rank = rank;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public boolean isLeader() {
        return rank == ClanRank.LEADER;
    }

    public boolean isAdmin() {
        return rank == ClanRank.ADMIN;
    }

    public boolean canManageMembers() {
        return rank == ClanRank.LEADER || rank == ClanRank.ADMIN;
    }
}