package com.wherehouse.members.model;


import java.sql.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MemberDTO {

	private String id;
	private String pw;
	private String nickName;
	private String tel;
	private String email;
	private Date joinDate;			// 회원 가입 날짜
}
