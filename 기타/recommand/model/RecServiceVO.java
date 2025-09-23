package com.wherehouse.recommand.model;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
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
}
