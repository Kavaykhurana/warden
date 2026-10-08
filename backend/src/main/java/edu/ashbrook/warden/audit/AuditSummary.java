package edu.ashbrook.warden.audit;

import java.util.List;
import java.util.Map;

public record AuditSummary(int hours, Map<String, Long> countsByType, List<ActorCount> topActors) {

    public record ActorCount(String fullName, String role, long events) {
    }
}
