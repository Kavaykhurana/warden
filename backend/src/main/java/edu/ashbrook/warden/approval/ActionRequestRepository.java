package edu.ashbrook.warden.approval;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import edu.ashbrook.warden.user.Role;

public interface ActionRequestRepository extends JpaRepository<ActionRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ActionRequest a where a.id = :id")
    Optional<ActionRequest> findForUpdate(UUID id);

    @Query("select a from ActionRequest a join fetch a.requestedBy left join fetch a.reviewer where a.id = :id")
    Optional<ActionRequest> findDetailed(UUID id);

    @Query("select a from ActionRequest a join fetch a.requestedBy left join fetch a.reviewer "
            + "where a.requestedBy.id = :userId and a.status in :statuses order by a.createdAt desc")
    List<ActionRequest> findRequestedBy(UUID userId, Collection<ActionStatus> statuses);

    @Query("select a from ActionRequest a join fetch a.requestedBy left join fetch a.reviewer "
            + "where a.requiredRole = :role and a.status in :statuses order by a.createdAt desc")
    List<ActionRequest> findByRequiredRole(Role role, Collection<ActionStatus> statuses);

    /** Enrollment ids in a course with a pending grade change — one query against the partial unique index. */
    @Query(value = "select cast(a.arguments->>'enrollmentId' as bigint) from action_requests a "
            + "where a.tool_name = 'requestGradeChange' and a.status = 'PENDING' and a.arguments->>'courseCode' = :courseCode",
            nativeQuery = true)
    List<Long> findPendingGradeChangeEnrollments(String courseCode);

    @Query(value = "select exists(select 1 from action_requests a where a.tool_name = 'requestGradeChange' "
            + "and a.status = 'PENDING' and a.arguments->>'enrollmentId' = :enrollmentId)", nativeQuery = true)
    boolean existsPendingGradeChange(String enrollmentId);

    @Query(value = "select exists(select 1 from action_requests a where a.tool_name = 'deactivateUser' "
            + "and a.status = 'PENDING' and a.arguments->>'targetUserId' = :targetUserId)", nativeQuery = true)
    boolean existsPendingDeactivation(String targetUserId);
}
