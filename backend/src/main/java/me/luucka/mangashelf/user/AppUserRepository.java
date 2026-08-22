package me.luucka.mangashelf.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    List<AppUser> findAllByOrderByUsernameAsc();

    long countByRoleAndEnabledTrue(Role role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.id = :id")
    Optional<AppUser> findByIdForUpdate(@Param("id") Long id);
}
