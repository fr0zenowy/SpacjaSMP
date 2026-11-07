package pl.zamrozenie.clans.models;

import java.time.LocalDateTime;

public class Alliance {
    private final int clanId1;
    private final int clanId2;
    private AllianceStatus status;
    private final int requestedBy;
    private final LocalDateTime createdAt;

    public enum AllianceStatus {
        PENDING("OCZEKUJACY"),
        ACCEPTED("ZAAKCEPTOWANY");

        private final String displayName;

        AllianceStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public Alliance(int clanId1, int clanId2, AllianceStatus status, int requestedBy, LocalDateTime createdAt) {
        this.clanId1 = clanId1;
        this.clanId2 = clanId2;
        this.status = status;
        this.requestedBy = requestedBy;
        this.createdAt = createdAt;
    }

    public int getClanId1() {
        return clanId1;
    }

    public int getClanId2() {
        return clanId2;
    }

    public AllianceStatus getStatus() {
        return status;
    }

    public void setStatus(AllianceStatus status) {
        this.status = status;
    }

    public int getRequestedBy() {
        return requestedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public int getOtherClanId(int clanId) {
        return clanId == clanId1 ? clanId2 : clanId1;
    }
}