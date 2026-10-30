package com.wow.be.tc.dbconn.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DbConnectionRepository extends JpaRepository<DbConnectionEntity, String> {
}
