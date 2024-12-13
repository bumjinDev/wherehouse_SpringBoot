package com.wherehouse.board.model;

import java.sql.Date;

public class BoardVO {
	
	private int contentnum;
	private String userid;
	private String title;
	private String bcontent;
	private String region;
	private int hit;
	private Date bdate;
	
	public BoardVO() {
		
	}

	public BoardVO(int contentnum, String userid, String title, String bcontent, String region, int hit, Date bdate) {
		
		this.contentnum = contentnum;
		this.userid = userid;
		this.title = title;
		this.bcontent = bcontent;
		this.region = region;
		this.hit = hit;
		this.bdate = bdate;
	}
	
	public int getcontentnum() {
		return contentnum;
	}
	
	public void setcontentnum(int contentnum) {
		this.contentnum = contentnum;
	}
	
	public String getuserid() {
		return userid;
	}
	
	public void setuserid(String userid) {
		this.userid = userid;
	}
	
	public String gettitle() {
		return title;
	}
	
	public void settitle(String title) {
		this.title = title;
	}
	
	
	public String getbcontent() {
		return bcontent;
	}
	
	public void setbcontent(String bcontent) {
		this.bcontent = bcontent;
	}
	
	public String getregion() {
		return region;
	}
	
	public void setregion(String region) {
		this.region = region;
	}
	
	public int gethit() {
		return hit;
	}
	
	public void sethit(int hit) {
		this.hit = hit;
	}
	
	public Date getbdate() {
		return bdate;
	}
	
	public void setbdate(Date bdate) {
		this.bdate = bdate;
	}
}
