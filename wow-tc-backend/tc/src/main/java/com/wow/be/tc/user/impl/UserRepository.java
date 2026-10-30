package com.wow.be.tc.user.impl;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {

	List<UserEntity> findAllByOrderByIdAsc();
}
