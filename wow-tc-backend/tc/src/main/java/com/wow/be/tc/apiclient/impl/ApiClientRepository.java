package com.wow.be.tc.apiclient.impl;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiClientRepository extends JpaRepository<ApiClientEntity, String> {

	List<ApiClientEntity> findByDeletedAtIsNullOrderByClientIdAsc();
}
