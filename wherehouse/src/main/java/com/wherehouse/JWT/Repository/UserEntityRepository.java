package com.wherehouse.JWT.Repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import com.wherehouse.JWT.UserDTO.AuthenticationEntity;

public interface UserEntityRepository extends JpaRepository<AuthenticationEntity, String>{
	
	Optional<AuthenticationEntity> findByUsername(String nickName);
	Optional<AuthenticationEntity> findByUserid(String userid);
}