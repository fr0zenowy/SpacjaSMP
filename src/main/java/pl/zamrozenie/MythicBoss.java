package pl.zamrozenie;

import java.util.Locale;

public enum MythicBoss {
    HOLLOWKNIGHT("hollowknight", "HollowKing", "Władca lochów", DropSource.WLADCA_LOCHOW, true),
    SPOREFANG("sporefang", "Sporefang", "Sporefang", null, false),
    STORMHERALD("stormherald", "StormHerald", "StormHerald", null, false);

    private final String id;
    private final String mythicId;
    private final String displayName;
    private final DropSource dropSource;
    private final boolean eventEnabled;

    MythicBoss(String id, String mythicId, String displayName, DropSource dropSource, boolean eventEnabled) {
        this.id = id;
        this.mythicId = mythicId;
        this.displayName = displayName;
        this.dropSource = dropSource;
        this.eventEnabled = eventEnabled;
    }

    public String getId() { return id; }
    public String getMythicId() { return mythicId; }
    public String getDisplayName() { return displayName; }
    public DropSource getDropSource() { return dropSource; }
    public boolean isEventEnabled() { return eventEnabled; }

    public static MythicBoss fromInput(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String n = DropSource.normalize(raw);
        for (MythicBoss boss : values()) {
            if (n.equals(boss.id)
                    || n.equals(DropSource.normalize(boss.mythicId))
                    || n.equals(DropSource.normalize(boss.displayName))) {
                return boss;
            }
        }
        if (DropSource.WLADCA_LOCHOW.matches(raw)) return HOLLOWKNIGHT;
        return null;
    }

    public static String names() {
        StringBuilder sb = new StringBuilder();
        for (MythicBoss boss : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(boss.mythicId);
        }
        return sb.toString();
    }

    public String configPath() {
        return "bosses." + id.toLowerCase(Locale.ROOT);
    }
}
