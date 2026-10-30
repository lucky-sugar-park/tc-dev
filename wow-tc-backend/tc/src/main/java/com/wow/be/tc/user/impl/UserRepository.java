package com.wow.be.tc.user.impl;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, String>, JpaSpecificationExecutor<UserEntity> {

	public Optional<UserEntity> findByEmail(String email);
	
	@Query("UPDATE UserEntity user SET user.enabled = true WHERE user.id = :userId")
	@Modifying
	public void approveUserRegistration(@Param("userId") String userId);

	@Query("UPDATE UserEntity user SET user.enabled = false WHERE user.id = :userId")
	@Modifying
	public void releaseUserRegistration(@Param("userId") String userId);
}
