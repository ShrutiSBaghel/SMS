package org.project.sms.repository;

import java.util.Optional;

import org.project.sms.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthRepository extends JpaRepository<User, Integer> {

	Optional<User> findByName(String name);

}
