package com.wow.be.tc.ui.i18n;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface I18nItemRepository extends JpaRepository<I18nItemEntity, I18nItemEntity.Key> {

	List<I18nItemEntity> findByIdLangCode(String langCode);

	List<I18nItemEntity> findByIdCode(String code);
}
