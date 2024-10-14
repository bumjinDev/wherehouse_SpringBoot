package com.wherehouse.board.model;

import java.sql.Date;

public class CommentVO {
	
	private int num;
	private String id;
	private int connum;
	private String nickname;
	private String title;
	private String content;
	private Date recorddate;
	
	public CommentVO() {
		
	}

	public CommentVO(int num, String id, int connum, String nickname, String title, String content, String bcontent) {
		
		this.num = num;
		this.id = id;
		this.connum = connum;
		this.nickname = nickname;
		this.title = title;
		this.content = content;
	}
	
	public int getnum() {
		return num;
	}
	
	public void  setnum(int num) {
		this.num = num;
	}
	
	public String getid() {
		return id;
	}
	
	public void setid(String id) {
		this.id = id;
	}
	
	public int getconnum() {
		return connum;
	}
	
	public void setconnum(int connum) {
		this.connum = connum;
	}
	
	public String getnickname() {
		return nickname;
	}
	
	public void setnickname(String nickname) {
		this.nickname = nickname;
	}
	
	public String gettitle() {
		return title;
	}
	
	public void settitle(String title) {
		this.title = title;
	}
	
	public String getcontent() {
		return content;
	}
	
	public void setcontent(String content) {
		this.content = content;
	}
	
	
	public Date getrecorddate() {
		return recorddate;
	}
	
	public void setrecorddate(Date recorddate) {
		this.recorddate = recorddate;
	}
}
