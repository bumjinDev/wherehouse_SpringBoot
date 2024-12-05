package com.wherehouse.JWT.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wherehouse.JWT.UserDTO.JwtTokenEntity;
import com.wherehouse.JWT.UserDTO.UserEntity;

public interface JwtTokenRepository extends JpaRepository<JwtTokenEntity, String>{
	
	boolean existsById(String Jwt);	// 존재하는지 확인하는 쿼리.
	Optional<JwtTokenEntity> findById(String Jwt);
}