package com.wow.be.tc.ui.bookmark;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<BookmarkEntity, BookmarkEntity.Key> {

	List<BookmarkEntity> findByUserIdOrderByNameAsc(String userId);
}
