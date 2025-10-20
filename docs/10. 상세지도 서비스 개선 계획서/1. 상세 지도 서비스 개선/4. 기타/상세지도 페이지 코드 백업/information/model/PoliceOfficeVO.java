package com.wherehouse.information.model;

public class PoliceOfficeVO {
	private String address;
	private double latitude;
	private double longitude;
	
	public String getAddress() {
		return address;
	}
	public void setAddress(String address) {
		this.address = address;
	}
	public double getLatitude() {
		return latitude;
	}
	public void setLatitude(double latitude) {
		this.latitude = latitude;
	}
	public double getLongitude() {
		return longitude;
	}
	public void setLongitude(double longitude) {
		this.longitude = longitude;
	}
	
	@Override
	public String toString() {
		return "PoliceOfficeVO [address=" + address + ", latitude=" + latitude + ", longitude=" + longitude + "]";
	}
	
}
