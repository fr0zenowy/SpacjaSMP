package pl.zamrozenie.anticheat;

/** Lista checkow + waga VL za jedno naruszenie. */
public enum Check {
    REACH("Reach", "hitboxy / zasieg", 4, true),
    WALL_HIT("WallHit", "bicie przez sciane (fullbox)", 4, true),
    AIM("Aim", "atak bez patrzenia na cel (killaura)", 3, true),
    MULTI_AURA("MultiAura", "wiele celow w jednym momencie", 3, true),
    MACE_KILLER("MaceKiller", "zawyzone obrazenia z mace", 6, true),
    AUTO_CLICKER("AutoClicker", "podejrzane CPS", 1, false),
    FLY("Fly", "lot / hover / brak grawitacji", 4, true),
    SPEED("Speed", "za szybki ruch", 3, true),
    BLINK("Blink", "teleport bez teleportu", 5, true),
    BAD_PACKET("BadPacket", "nieprawidlowe pakiety (NaN / pitch / spam)", 6, true),
    TIMER("Timer", "za duzo pakietow ruchu na sekunde", 2, false),
    FAST_PLACE("FastPlace", "za szybkie stawianie blokow", 1, false),
    VEHICLE_FLY("VehicleFly", "lot lodka / wagonikiem", 4, true),
    NUKER("Nuker", "za szybkie / za dalekie kopanie", 3, true),
    FREECAM("Freecam", "interakcja z blokiem poza zasiegiem / bez patrzenia", 3, true),
    AURA("KillAura", "analiza rotacji i celowania (killaura)", 5, true);

    public final String id;
    public final String description;
    public final double weight;
    /** Czy naruszenie jest blokowane (event anulowany / setback), czy tylko alert. */
    public final boolean blocks;

    Check(String id, String description, double weight, boolean blocks) {
        this.id = id;
        this.description = description;
        this.weight = weight;
        this.blocks = blocks;
    }

    public static Check byId(String s) {
        for (Check c : values()) if (c.id.equalsIgnoreCase(s) || c.name().equalsIgnoreCase(s)) return c;
        return null;
    }
}
