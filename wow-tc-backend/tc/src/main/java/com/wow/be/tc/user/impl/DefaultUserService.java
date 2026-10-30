package com.wow.be.tc.user.impl;

import java.util.Date;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.wow.be.framework.persist.PersistentService;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.user.UserHistoryInfo;
import com.wow.be.tc.user.UserInfo;
import com.wow.be.tc.user.UserService;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DefaultUserService implements UserService, PersistentService<UserInfo, UserEntity>, UserDetailsService {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserHistoryRepository userHistoryRepository;

	@Override
	@Transactional
	public void recordUserHistory(UserHistoryInfo uhInfo) {
		log.debug("");
		// Target user
		UserInfo uInfo = null;
		if (uhInfo.getRemark() == null || "".equals(uhInfo.getRemark())) {
			uInfo = this.findUserById(uhInfo.getUserId());
			uhInfo.setRemark(uInfo.toJson(false));
		}

		try {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication != null) {
				// Worker user on current context
				uInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getDetails();
				if(uInfo != null) {
					uhInfo.setUserId(uInfo.getId());
					uhInfo.setCreatedBy(uInfo.getId());
					uhInfo.setUpdatedBy(uInfo.getId());
					uhInfo.setIpAddress(uInfo.getLoginedIp());
				}
			}
			UserHistoryEntity uhEntity = new UserHistoryEntity();
			uhEntity.from(uhInfo);

			userHistoryRepository.save(uhEntity);
		} catch (Throwable t) {
			log.warn("", t);
		}
	}

	@Override
	@Transactional
	public void requestUserRegistion(UserInfo uInfo) throws ToolControlException {
		log.debug("");
		try {
			UserEntity user = new UserEntity();
			user.from(uInfo);
			userRepository.save(user);

			UserHistoryInfo uhInfo = this.createUserHistoryInfo(uInfo, null);
			uhInfo.setOper("REGISTRATION_REQUEST");
			this.recordUserHistory(uhInfo);
		} catch (Throwable t) {
			log.warn("");
			throw new ToolControlException("사용자 등록 요청을 처리하지 못했습니다: " + uInfo.getId(), t);
		}
	}

	@Override
	@Transactional
	public void approveUserRegistration(String userId) throws ToolControlException {
		log.debug("");
		try {
			userRepository.approveUserRegistration(userId);

			UserHistoryInfo uhInfo = this.createUserHistoryInfo(null, userId);
			uhInfo.setOper("REGISTRATION_AAPROVAL");
			this.recordUserHistory(uhInfo);
		} catch (Throwable t) {
			log.warn("");
			throw new ToolControlException("사용자 등록 승인을 처리하지 못했습니다: " + userId, t);
		}
	}

	@Override
	@Transactional
	public void releaseUserRegistration(String userId) throws ToolControlException {
		log.debug("");
		try {
			userRepository.approveUserRegistration(userId);
			UserHistoryInfo uhInfo = this.createUserHistoryInfo(null, userId);
			uhInfo.setOper("REGISTRATION_RELEASE");
			this.recordUserHistory(uhInfo);
		} catch (Throwable t) {
			log.warn("");
			throw new ToolControlException("사용자 등록 해제를 처리하지 못했습니다: " + userId, t);
		}
	}
	
	private UserHistoryInfo createUserHistoryInfo(UserInfo uInfo, String userId) {
		if(uInfo==null) {
			uInfo = this.findUserById(userId);
		}
		
		UserHistoryInfo uhInfo = new UserHistoryInfo();

		uhInfo.setUserId(userId);
		uhInfo.setIpAddress(uhInfo.getIpAddress());
		uhInfo.setRemark(uhInfo.toJson(true));
		uhInfo.setTimestamp(new Date());
		uhInfo.setCreatedBy("SYSTEM");
		uhInfo.setCreatedDate(new Date());
		uhInfo.setUpdatedBy("SYSTEM");
		uhInfo.setUpdatedDate(new Date());

		return uhInfo;
	}

	@Override
	@Transactional
	public void updateUser(UserInfo uInfo, String operation) throws ToolControlException {
		log.debug("");
		Optional<UserEntity> opt = userRepository.findById(uInfo.getId());
		if (opt.isEmpty()) {
			throw new ToolControlException("사용자를 찾을 수 없습니다: " + uInfo.getId());
		}
		UserEntity user = opt.get();
		user.from(uInfo);
		userRepository.save(user);
		
		UserHistoryInfo uhInfo = this.createUserHistoryInfo(uInfo, uInfo.getId());
		uhInfo.setOper(operation);
		this.recordUserHistory(uhInfo);
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		log.debug("");
		return this.findUserById(username);
	}

	@Override
	@Transactional
	public UserInfo findUserById(String id) throws UsernameNotFoundException {
		log.debug("");
		return userRepository.findById(id).orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + id)).info();
	}

	@Override
	@Transactional
	public UserInfo findUserByEmail(String email) throws UsernameNotFoundException {
		log.debug("");
		UserEntity user = userRepository.findByEmail(email).get();
		if (user == null) {
			throw new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + email);
		}
		return user.info();
	}

	@Override
	@Transactional
	public void deleteUser(String userId) throws ToolControlException {
		log.debug("");
		try {
			UserInfo uInfo = this.findUserById(userId);

			userRepository.deleteById(userId);

			UserHistoryInfo uhInfo = this.createUserHistoryInfo(uInfo, null);
			uhInfo.setOper("DELETION");
			this.recordUserHistory(uhInfo);
		} catch (Throwable t) {
			log.warn("");
			throw new ToolControlException("사용자를 삭제하지 못했습니다: " + userId, t);
		}
	}

	@Override
	public boolean checkIfUserIdExist(String userId) {
		log.debug("");
		Optional<UserEntity> opt = userRepository.findById(userId);
		if (opt.isEmpty()) {
			return true;
		}
		return false;
	}
}
