package com.wherehouse.members.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestExecutionListeners(
        value = TransactionalTestExecutionListener.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
@Transactional
@Rollback
class MembersControllerAPITest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private Environment environment;

    private String token;
    private HttpHeaders headers;

    private static final Logger logger = LoggerFactory.getLogger(MembersControllerAPITest.class);

    @BeforeEach
    void setup() {
        token = environment.getProperty("TestJWT.token");

        if (token == null || token.isEmpty()) {
            logger.warn("TestJWT.token 값이 존재하지 않음");
            throw new IllegalStateException("테스트 실행을 위해 JWT 토큰이 필요.");
        } else {
            logger.info("Test용 JWT: {}", token);
        }

        headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "Authorization=" + token);
        headers.setContentType(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("로그인 성공 API 테스트")
    void testLoginSuccess() {
        logger.info("로그인 성공 API 테스트");

        headers.set(HttpHeaders.ACCEPT, "text/html");

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "/loginSuccess",
                HttpMethod.GET,
                requestEntity,
                String.class
        );

        String responseBody = response.getBody();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(responseBody);
        assertTrue(responseBody.contains("<!DOCTYPE html>"));
        assertTrue(responseBody.contains("</html>"));
    }

    @Test
    @DisplayName("회원 가입 API 테스트")
    @Transactional // 이 테스트 실행 후 DB 변경 사항 롤백
    @Rollback // 기본값 true, 명시적 선언 가능
    void testJoinMember() {
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("id", "testuser1");
        requestBody.add("pw", "testpassword");
        requestBody.add("nickName", "testuser1");
        requestBody.add("tel", "010-1234-5678");
        requestBody.add("email", "testuser@gmail.com");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "/joinOk",
                HttpMethod.POST,
                request,
                String.class
        );

        String responseBody = response.getBody();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(responseBody);
        assertTrue(responseBody.contains("<!DOCTYPE html>"));
        assertTrue(responseBody.contains("회원가입이 정상적으로 되었습니다."));
        assertTrue(responseBody.contains("</html>"));
    }

    @Test
    @DisplayName("회원 정보 수정 페이지 요청 API 테스트")
    void testModifyMemberPage() {
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("editid", "testuser1");

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "/membermodifypage",
                HttpMethod.POST,
                requestEntity,
                String.class
        );

        String responseBody = response.getBody();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(responseBody);
        assertTrue(responseBody.contains("<!DOCTYPE html>"));
        assertTrue(responseBody.contains("항목, 수정 불가"));
        assertTrue(responseBody.contains("회원 정보 수정"));
        assertTrue(responseBody.contains("</html>"));
    }

    @Test
    @DisplayName("회원 정보 수정 API 테스트")
    void testModifyMember() {
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("id", "testuser1");
        requestBody.add("pw", "testpassword");
        requestBody.add("nickName", "testuser1");
        requestBody.add("tel", "010-1234-5678");
        requestBody.add("email", "testuser@gmail.com");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "/membermodifyok",
                HttpMethod.POST,
                request,
                String.class
        );

        String responseBody = response.getBody();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(responseBody);
        assertTrue(responseBody.contains("<!DOCTYPE html>"));
        assertTrue(responseBody.contains("정보 수정되었습니다."));
        assertTrue(responseBody.contains("</html>"));
    }
}
