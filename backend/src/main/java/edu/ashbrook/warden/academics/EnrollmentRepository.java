package edu.ashbrook.warden.academics;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    @Query("select e from Enrollment e join fetch e.course where e.student.id = :studentId order by e.course.code")
    List<Enrollment> findByStudent(UUID studentId);

    @Query("select e from Enrollment e join fetch e.student where e.course.code = :courseCode order by e.student.rollNo")
    List<Enrollment> findRoster(String courseCode);

    @Query("select e from Enrollment e join fetch e.course where e.course.code in :courseCodes")
    List<Enrollment> findByCourses(Collection<String> courseCodes);

    @Query("select e from Enrollment e join fetch e.student join fetch e.course where e.course.code = :courseCode and e.student.rollNo = :rollNo")
    Optional<Enrollment> findByCourseAndRollNo(String courseCode, String rollNo);

    @Query("select e from Enrollment e join fetch e.student join fetch e.course where e.id = :id")
    Optional<Enrollment> findDetailed(Long id);
}
