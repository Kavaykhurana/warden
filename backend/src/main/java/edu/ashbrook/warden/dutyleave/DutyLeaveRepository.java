package edu.ashbrook.warden.dutyleave;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DutyLeaveRepository extends JpaRepository<DutyLeaveRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DutyLeaveRequest d where d.id = :id")
    Optional<DutyLeaveRequest> findForUpdate(Long id);

    @Query("select d from DutyLeaveRequest d join fetch d.student s left join fetch s.mentor where d.id = :id")
    Optional<DutyLeaveRequest> findDetailed(Long id);

    @Query("select d from DutyLeaveRequest d join fetch d.student s left join fetch s.mentor left join fetch d.decidedBy "
            + "where s.id = :studentId order by d.createdAt desc, d.id desc")
    List<DutyLeaveRequest> findByStudent(UUID studentId);

    @Query("select d from DutyLeaveRequest d join fetch d.student s left join fetch s.mentor left join fetch d.decidedBy "
            + "where s.mentor.id = :mentorId and d.status in :statuses order by d.createdAt desc, d.id desc")
    List<DutyLeaveRequest> findForMentor(UUID mentorId, Collection<LeaveStatus> statuses);

    @Query("select d from DutyLeaveRequest d join fetch d.student s left join fetch s.mentor left join fetch d.decidedBy "
            + "where s.department.code = :departmentCode and d.status in :statuses order by d.createdAt desc, d.id desc")
    List<DutyLeaveRequest> findForDepartment(String departmentCode, Collection<LeaveStatus> statuses);
}
