package com.oshmarket.repository;

import com.oshmarket.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByInnAndDeletedFalse(String inn);

    boolean existsByInnAndDeletedFalse(String inn);
}
