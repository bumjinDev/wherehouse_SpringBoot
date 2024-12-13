package com.wherehouse.information.model;

public class AddrRateVO {
	private String addr;
	private double rate;
	
	public String getAddr() {
		return addr;
	}
	public void setAddr(String addr) {
		this.addr = addr;
	}
	public double getRate() {
		return rate;
	}
	public void setRate(double rate) {
		this.rate = rate;
	}
	
	@Override
	public String toString() {
		return "AddrRateVO [addr=" + addr + ", rate=" + rate + "]";
	}
}
