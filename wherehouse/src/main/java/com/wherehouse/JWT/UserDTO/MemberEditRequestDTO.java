package com.wherehouse.JWT.UserDTO;

import lombok.Data;

@Data
public class MemberEditRequestDTO {

	private String id;
	private String pw;
	private String nickName;
	private String tel;
	private String email;
}
