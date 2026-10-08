package edu.ashbrook.warden.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    @Query("select u from User u where u.role = edu.ashbrook.warden.user.Role.HOD and u.active = true and u.department.code = :code")
    Optional<User> findActiveHod(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = :role and u.active = true")
    List<User> lockActiveByRole(Role role);

    boolean existsByMentorIdAndActiveTrue(UUID mentorId);

    @Query("select u from User u left join fetch u.mentor order by u.createdAt, u.fullName")
    List<User> findAllWithMentor();
}
