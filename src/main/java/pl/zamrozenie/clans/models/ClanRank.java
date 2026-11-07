package pl.zamrozenie.clans.models;

public enum ClanRank {
    LEADER("LIDER"),
    ADMIN("ADMIN"),
    MEMBER("CZLONEK");

    private final String displayName;

    ClanRank(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ClanRank fromString(String rank) {
        try {
            return ClanRank.valueOf(rank.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MEMBER;
        }
    }
}