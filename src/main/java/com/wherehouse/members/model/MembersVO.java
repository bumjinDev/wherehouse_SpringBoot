package com.wherehouse.members.model;

import java.sql.Date;

public class MembersVO {
	
	String id;				// 아이디
	String pw;				// 비밀번호
	String nickName;		// 회원 닉네임
	String tel;					// 회원 전화번호
	String email;				// 이메일
	Date joinDate;			// 회원 가입 날짜
	
	// 회원 id
	public void setId(String id) {
		
		this.id = id;
	}
	
	public String getId() {
		
		return id;
	}
	
	// 회원 pw
	public void setPw(String pw) {
		
		this.pw = pw; 
	}
	
	public String getPw() {
		
		return pw;
	}
	
	// 회원 nickName
	public void setNickName(String nickName) {
		
		this.nickName = nickName;
	}
	
	public String getNickName() {
		
		return nickName;
	}
	
	// 회원 tel
	public void setTel(String tel) {
		
		this.tel = tel;
	}
	
	public String geTel() {
		
		return tel;
	}
	
	// 회원 email
	public void setEmail(String email) {
		
		this.email = email;
	}
	
	public String getEmail() {
		
		return email;
	}
	
	// 회원 joinDate
	public void setJoinDate(Date date) {
		
		this.joinDate = date;
	}
	
	public Date getjoindate() {
		
		return joinDate;
	}
}
