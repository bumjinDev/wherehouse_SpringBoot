package com.wherehouse.JWT.UserDTO;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;


/* UserEntity : 기존 회원 관리 테이블 "MembersTbl" 에서 JWT 생성 및 검증 위해 "UserDetails" 구현체가
 * 	필요한 id, username, password 를 단순하게 겨져오기 위한 객체. */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder // 빌더 패턴 추가
@Table(name = "userentity")
public class UserEntity {

    @Id
    @Column(nullable = false)
    private String userid; // 사용자 아이디
    
    @Column(nullable = false)
    private String username; // 사용자 이름
    
    
    @Column(nullable = false)
    private String password; // 비밀번호
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
    		name = "userentity_roles",
    		joinColumns = @JoinColumn(name = "userid", referencedColumnName = "userid"))
    @Column(name = "roles")
    private List<String> roles; // 사용자 권한

    @PreRemove
    public void removeRoles() {
        roles.clear(); // 엔터티 삭제 전에 하위 테이블 데이터 삭제
    }
}
