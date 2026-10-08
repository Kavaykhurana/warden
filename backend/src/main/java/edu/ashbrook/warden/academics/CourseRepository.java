package edu.ashbrook.warden.academics;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CourseRepository extends JpaRepository<Course, String> {

    @Query("select c from Course c join fetch c.faculty order by c.code")
    List<Course> findAllWithFaculty();

    @Query("select c from Course c join fetch c.faculty where c.faculty.id = :facultyId order by c.code")
    List<Course> findByFaculty(UUID facultyId);

    @Query("select c from Course c join fetch c.faculty where c.department.code = :departmentCode order by c.code")
    List<Course> findByDepartment(String departmentCode);

    @Query("select c from Course c join fetch c.faculty where c.code in "
            + "(select e.course.code from Enrollment e where e.student.id = :studentId) order by c.code")
    List<Course> findByStudent(UUID studentId);

    boolean existsByFacultyId(UUID facultyId);
}
