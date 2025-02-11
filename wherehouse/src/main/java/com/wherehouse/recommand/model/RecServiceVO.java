package com.wherehouse.recommand.model;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder	// 테스트 코드 작성 용.
@Data
public class RecServiceVO {

	private int gu_id;
	private String gu_name;
	private int cvt_score;
	private int safe_score;
	private int cafe;
	private int cvt_store;
	private int daiso;
	private int oliveYoung;
	private int restourant;
	private int police_office;
	private int cctv;
	private int charter_avg;
	private int deposit_avg;
	private int monthly_avg;
	
//	public int getGu_id() {
//		return gu_id;
//	}
//	public void setGu_id(int gu_id) {
//		this.gu_id = gu_id;
//	}
//	public String getGu_name() {
//		return gu_name;
//	}
//	public void setGu_name(String gu_name) {
//		this.gu_name = gu_name;
//	}
//	public int getCvt_score() {
//		return cvt_score;
//	}
//	public void setCvt_score(int cvt_score) {
//		this.cvt_score = cvt_score;
//	}
//	public int getSafe_score() {
//		return safe_score;
//	}
//	public void setSafe_score(int safe_score) {
//		this.safe_score = safe_score;
//	}
//	public int getCafe() {
//		return cafe;
//	}
//	public void setCafe(int cafe) {
//		this.cafe = cafe;
//	}
//	public int getCvt_store() {
//		return cvt_store;
//	}
//	public void setCvt_store(int cvt_store) {
//		this.cvt_store = cvt_store;
//	}
//	public int getDaiso() {
//		return daiso;
//	}
//	public void setDaiso(int daiso) {
//		this.daiso = daiso;
//	}
//	public int getOliveYoung() {
//		return oliveYoung;
//	}
//	public void setOliveYoung(int oliveYoung) {
//		this.oliveYoung = oliveYoung;
//	}
//	public int getRestourant() {
//		return restourant;
//	}
//	public void setRestourant(int restourant) {
//		this.restourant = restourant;
//	}
//	public int getPolice_office() {
//		return police_office;
//	}
//	public void setPolice_office(int police_office) {
//		this.police_office = police_office;
//	}
//	public int getCctv() {
//		return cctv;
//	}
//	public void setCctv(int cctv) {
//		this.cctv = cctv;
//	}
//	public int getCharter_avg() {
//		return charter_avg;
//	}
//	public void setCharter_avg(int charter_avg) {
//		this.charter_avg = charter_avg;
//	}
//	public int getDeposit_avg() {
//		return deposit_avg;
//	}
//	public void setDeposit_avg(int deposit_avg) {
//		this.deposit_avg = deposit_avg;
//	}
//	public int getMonthly_avg() {
//		return monthly_avg;
//	}
//	public void setMonthly_avg(int monthly_avg) {
//		this.monthly_avg = monthly_avg;
//	}
}
