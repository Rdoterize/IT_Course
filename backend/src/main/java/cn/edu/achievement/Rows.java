package cn.edu.achievement;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class Rows {
    private Rows() {}
    public static class User {
        public Long id; public String username, passwordHash, name, role, number, cohort, department; public boolean enabled;
    }
    public static class Achievement {
        public Long id, ownerId; public String title, type, level, description, detailsJson, status;
        public LocalDate achievedOn; public LocalDateTime createdAt, updatedAt;
    }
    public static class Participant {
        public Long achievementId, userId; public String role; public int sortOrder;
    }
    public static class Indicator { public Long id; public String code, name, description; }
    public static class Review {
        public Long id, achievementId, reviewerId; public boolean approved; public String comment; public LocalDateTime createdAt;
    }
    public static class Attachment {
        public Long id, achievementId, size; public String originalName, storedName, contentType; public LocalDateTime createdAt;
    }
}
