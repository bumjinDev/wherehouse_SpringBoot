package com.wherehouse.information.model;

public class CctvVO {
	private int number;
	private String address;
	private double latitude;
	private double longitude;
	private int cameraCount;
	
	public int getNumber() {
		return number;
	}
	public void setNumber(int number) {
		this.number = number;
	}
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
	public int getCameraCount() {
		return cameraCount;
	}
	public void setCameraCount(int cameraCount) {
		this.cameraCount = cameraCount;
	}
	
	@Override
	public String toString() {
		return "CctvVO [number=" + number + ", address=" + address + ", latitude=" + latitude + ", longitude="
				+ longitude + ", cameraCount=" + cameraCount + "]";
	}
}