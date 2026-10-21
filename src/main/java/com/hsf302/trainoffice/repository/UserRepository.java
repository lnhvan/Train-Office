package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.common.enums.UserRole;
import com.hsf302.trainoffice.common.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    User findByEmail(String email);

    boolean existsByEmail(String email);

    User findByEmailAndPassword(String email, String password);

    boolean existsByEmailAndUserIdNot(String email, Long userId);

    @Query("""
            select u from User u
            where (:keyword is null or lower(u.email) like lower(concat('%', :keyword, '%'))
                or lower(u.fullName) like lower(concat('%', :keyword, '%')))
              and (:role is null or u.role = :role)
              and (:status is null or u.status = :status)
            order by u.userId desc
            """)
    List<User> search(@Param("keyword") String keyword,
                      @Param("role") UserRole role,
                      @Param("status") UserStatus status);
}
