package com.wherehouse.JWT.Repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.wherehouse.JWT.UserDTO.UserEntity;

public interface UserEntityRepository extends JpaRepository<UserEntity, String>{
	
	Optional<UserEntity> findByUsername(String nickName);
	Optional<UserEntity> findByUserid(String userid);
}