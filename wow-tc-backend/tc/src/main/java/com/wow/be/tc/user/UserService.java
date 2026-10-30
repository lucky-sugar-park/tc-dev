package com.wow.be.tc.user;

import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.wow.be.tc.ToolControlException;

public interface UserService {
	
	public abstract void recordUserHistory(UserHistoryInfo uhInfo);

	public abstract void requestUserRegistion(UserInfo uInfo) throws ToolControlException;
	
	public abstract void approveUserRegistration(String userId) throws ToolControlException;
	
	public abstract void releaseUserRegistration(String userId) throws ToolControlException;
	
	public abstract void updateUser(UserInfo uInfo, String operation) throws ToolControlException;

	public abstract UserInfo findUserById(String id) throws UsernameNotFoundException;
	
	public abstract UserInfo findUserByEmail(String email) throws UsernameNotFoundException;
	
	public abstract void deleteUser(String userId) throws ToolControlException;
	
	public abstract boolean checkIfUserIdExist(String userId);
}
