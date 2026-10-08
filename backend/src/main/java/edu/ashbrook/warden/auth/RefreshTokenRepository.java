package edu.ashbrook.warden.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findForUpdate(String tokenHash);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByFamilyId(UUID familyId);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :at where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(UUID familyId, Instant at);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :at where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(UUID userId, Instant at);
}
