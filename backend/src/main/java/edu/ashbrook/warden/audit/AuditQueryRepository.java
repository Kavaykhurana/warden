package edu.ashbrook.warden.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import edu.ashbrook.warden.common.PageDto;
import edu.ashbrook.warden.config.ClockConfig;
import edu.ashbrook.warden.user.Role;
import edu.ashbrook.warden.user.UserSummary;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
class AuditQueryRepository {

    private static final TypeReference<TreeMap<String, String>> DETAILS = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    AuditQueryRepository(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    PageDto<AuditEventDto> page(AuditType type, UUID actorId, Instant from, int page, int size) {
        StringBuilder where = new StringBuilder(" where true");
        List<Object> args = new ArrayList<>();
        if (type != null) {
            where.append(" and a.event_type = ?");
            args.add(type.name());
        }
        if (actorId != null) {
            where.append(" and a.actor_id = ?");
            args.add(actorId);
        }
        if (from != null) {
            where.append(" and a.created_at >= ?");
            args.add(utc(from));
        }
        Long total = jdbc.queryForObject("select count(*) from audit_events a" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<AuditEventDto> content = jdbc.query(
                "select a.*, u.full_name, u.role from audit_events a left join users u on u.id = a.actor_id"
                        + where + " order by a.id desc limit ? offset ?",
                (rs, i) -> toDto(rs), pageArgs.toArray());
        long totalElements = total == null ? 0 : total;
        return new PageDto<>(content, page, size, totalElements, (int) ((totalElements + size - 1) / size));
    }

    List<StoredAuditEvent> chunkAfter(long afterId, int limit) {
        return jdbc.query("select * from audit_events where id > ? order by id limit ?",
                (rs, i) -> new StoredAuditEvent(rs.getLong("id"), rs.getObject("actor_id", UUID.class),
                        rs.getString("event_type"), rs.getString("tool_name"), details(rs),
                        rs.getString("prev_hash"), rs.getString("hash"), instant(rs)),
                afterId, limit);
    }

    String latestHash() {
        return jdbc.query("select hash from audit_events order by id desc limit 1",
                rs -> rs.next() ? rs.getString(1) : HashChain.GENESIS);
    }

    void insert(UUID actorId, AuditType type, String toolName, String detailsJson, String prevHash, String hash,
                Instant at) {
        jdbc.update("insert into audit_events (actor_id, event_type, tool_name, details, prev_hash, hash, created_at) "
                        + "values (?, ?, ?, cast(? as jsonb), ?, ?, ?)",
                actorId, type.name(), toolName, detailsJson, prevHash, hash, utc(at));
    }

    void lockChain() {
        jdbc.query("select pg_advisory_xact_lock(4242)", rs -> null);
    }

    Map<String, Long> countsByType(Instant since) {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("select event_type, count(*) from audit_events where created_at >= ? group by event_type order by 2 desc, 1",
                rs -> {
                    counts.put(rs.getString(1), rs.getLong(2));
                }, utc(since));
        return counts;
    }

    List<AuditSummary.ActorCount> topActors(Instant since) {
        return jdbc.query("select u.full_name, u.role, count(*) as n from audit_events a join users u on u.id = a.actor_id "
                        + "where a.created_at >= ? group by u.id, u.full_name, u.role order by n desc, u.full_name limit 5",
                (rs, i) -> new AuditSummary.ActorCount(rs.getString(1), rs.getString(2), rs.getLong(3)), utc(since));
    }

    private AuditEventDto toDto(ResultSet rs) throws SQLException {
        UUID actorId = rs.getObject("actor_id", UUID.class);
        UserSummary actor = actorId == null ? null
                : new UserSummary(actorId.toString(), rs.getString("full_name"), Role.valueOf(rs.getString("role")));
        return new AuditEventDto(rs.getLong("id"), actor, AuditType.valueOf(rs.getString("event_type")),
                rs.getString("tool_name"), details(rs), rs.getString("prev_hash"), rs.getString("hash"),
                ClockConfig.ist(instant(rs)));
    }

    private TreeMap<String, String> details(ResultSet rs) throws SQLException {
        return json.readValue(rs.getString("details"), DETAILS);
    }

    private static Instant instant(ResultSet rs) throws SQLException {
        return rs.getObject("created_at", OffsetDateTime.class).toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
