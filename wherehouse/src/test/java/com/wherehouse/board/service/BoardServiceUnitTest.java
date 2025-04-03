package com.wherehouse.board.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;
import com.wherehouse.JWT.Filter.Util.JWTUtil;
import com.wherehouse.board.dao.BoardEntityRepository;
import com.wherehouse.board.dao.IBoardRepository;
import com.wherehouse.board.model.BoardConverter;
import com.wherehouse.board.model.BoardDTO;
import com.wherehouse.board.model.BoardEntity;
import com.wherehouse.board.model.CommandtVO;
import com.wherehouse.board.model.CommentConverter;
import com.wherehouse.board.model.CommentEntity;
import com.wherehouse.globalexception.UnauthorizedException;
import com.wherehouse.members.dao.IMembersRepository;
import com.wherehouse.members.model.MemberConverter;
import com.wherehouse.members.model.MemberDTO;
import com.wherehouse.members.model.MembersEntity;
import com.wherehouse.redis.handler.RedisHandler;

import io.jsonwebtoken.SignatureAlgorithm;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
//import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.ValueOperations;

import java.security.Key;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.crypto.spec.SecretKeySpec;

import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
//@ExtendWith(MockitoExtension.class)
//@ContextConfiguration(classes = BoardServiceUnitTestConfig.class)
//@TestPropertySource(locations = "classpath:application.yml")
class BoardServiceUnitTest {

	private static final Logger logger = LoggerFactory.getLogger(BoardServiceUnitTest.class);
	
	@Autowired
	Environment env;
	
    @InjectMocks
    private BoardService boardService;
    
    @Mock
    JWTUtil jwtUtil = new JWTUtil();
    
    @Mock
    private IBoardRepository boardRepository;

    @Mock
    private BoardEntityRepository boardEntityRepository;

    @Mock
    private IMembersRepository membersRepository;

    @Mock
    private RedisHandler redisHandler;

    @Mock
    private BoardConverter boardConverter;

    @Mock
    private CommentConverter commentConverter;

    @Mock
    private MemberConverter memberConverter;
    
    @Mock
    private ValueOperations<String, Object> mockValueOperations;

    private static String testToken;
    private static String testSecretKey;

    @BeforeEach
    void jwtUtilNotNullCheck() {
        
    	testToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6InRlc3RVc2VyIiwidXNlcklkIjoidGVzdFVzZXJJZCIsInJvbGVzIjpbIlJPTEVfVVNFUiJdLCJpYXQiOjE3NDI0NTUyODIsImV4cCI6MjA1NzgxNTI4Mn0.A6_AjQFf9btisDxrJq4L2qxCTsDZv1zjyeROXdE18oI";
    	testSecretKey = "dEFnIT_lov0A2kRWE83Lkjh5hIZb1hyX1GbTnlhiDsI=";
    	
    }
    
    /* BoardService.searchBoard() : 특정 페이지 네이션 번호로 조회 메소드 테스트 */
    @Test
    void searchBoard() {
        // Given
        int pageIndex = 1;
        
        /* When() 실행 시 반환될 객체 */
        HashMap<String, Object> boardRepositoryMock = new HashMap<>();
        
        List<BoardEntity> mockBoardList = new ArrayList<>();
        /* 반환될 Mock 객체 내 10개의 boardList */
        for (int i = 1; i <= 10; i++) {
            BoardEntity board = BoardEntity.builder()
                .connum(i)
                .title("테스트 제목 " + i)
                .boardcontent("테스트 내용 " + i)
                .userid("user" + i)
                .bdate(Date.valueOf(LocalDate.now().minusDays(i)))
                .hit(10 * i)
                .region("Seoul")
                .build();
            mockBoardList.add(board);
        }        
        /* Mock 결과 반환 객체 내 페이지 수 10개에 대한 사용자 명 */
        List<String> members = new ArrayList<>();
        for (int i = 1; i <= 10; i++)
            members.add("user" + i);
        
        /* Mock 결과 반환 객체 내 페이지 수 10개 */
        boardRepositoryMock.put("pnSize", 10);	
        boardRepositoryMock.put("boardList", mockBoardList);
        boardRepositoryMock.put("members", members);
        
       // When
        when(boardRepository.searchBoardList(10)).thenReturn(boardRepositoryMock);

        Map<String, Object> result = boardService.searchBoard(pageIndex);

        // Then
    	// Map 키 존재 여부
        assertTrue(result.containsKey("boardList"));
        assertTrue(result.containsKey("members"));
        assertTrue(result.containsKey("pnSize"));

        // boardList 내부 객체 검증
        List<?> resultBoardList = (List<?>) result.get("boardList");
        assertEquals(10, resultBoardList.size());
        BoardEntity firstBoard = (BoardEntity) resultBoardList.get(0);
        assertEquals("테스트 제목 1", firstBoard.getTitle());
        assertEquals("user1", firstBoard.getUserid());

        // members 검증
        List<?> resultMembers = (List<?>) result.get("members");
        assertEquals(10, resultMembers.size());
        assertTrue(resultMembers.contains("user5"));
    }

    /* BoardService.sarchView() : 지정된 게시글 번호를 적용하여 해당 게시글 정보를 DBMS 에서 조회 후 컨트롤러로 반환*/
    @Test
    void sarchView() {
        // Given
        int contentNum = 1;
        
        /* 게시글 DTO 와 Entity */
        BoardDTO boardDTO = BoardDTO.builder()
        	    .boardId(1)
        	    .userId("user1")
        	    .title("테스트 글 제목")
        	    .boardContent("테스트 글 내용")
        	    .region("강서구")
        	    .boardHit(10)
        	    .boardDate(Date.valueOf(java.time.LocalDate.now()))
        	    .build();
        
        BoardEntity boardEntity = BoardEntity.builder()
        	    .connum(1)
        	    .userid("user1")
        	    .title("테스트 글 제목")
        	    .boardcontent("테스트 글 내용")
        	    .region("강서구")
        	    .hit(10)
        	    .bdate(Date.valueOf(java.time.LocalDate.now()))
        	    .build();
        
        /* 댓글 DTO 와 Entity : List 단위로 컨버터 진행. */
        CommandtVO commentDTO1 = CommandtVO.builder()
        	    .boardId(1)
        	    .userId("user1")
        	    .userName("user1")
        	    .replyContent("첫번째 테스트 댓글 내용")
        	    .build();
        CommandtVO commentDTO2 = CommandtVO.builder()
        	    .boardId(1)
        	    .userId("user1")
        	    .userName("user1")
        	    .replyContent("두번째 테스트 댓글 내용")
        	    .build();
        
        // List<CommandtVO> voList 생성
        List<CommandtVO> commentVOList = new ArrayList<CommandtVO>();
        commentVOList.add(commentDTO1);
        commentVOList.add(commentDTO2);
        
        CommentEntity commentEntity1 = CommentEntity.builder()
        	    .boardId(1)
        	    .userId("user1")
        	    .userName("user1")
        	    .replyContent("첫번째 테스트 댓글 내용")
        	    .build();
        
        CommentEntity commentEntity2 = CommentEntity.builder()
        	    .boardId(1)
        	    .userId("user1")
        	    .userName("user1")
        	    .replyContent("두번째 테스트 댓글 내용")
        	    .build();
        // List<CommentEntity> entityList 생성
        List<CommentEntity> commentEntityList = new ArrayList<CommentEntity>();
        commentEntityList.add(commentEntity1);
        commentEntityList.add(commentEntity2);
        
        /* 회원 DTO 와 Entity */
        MemberDTO memberDTO = MemberDTO.builder()
        	    .id("user1")
        	    .pw("securePassword")
        	    .nickName("테스트닉네임")
        	    .tel("010-1234-5678")
        	    .email("user1@example.com")
        	    .joinDate(Date.valueOf(java.time.LocalDate.now()))
        	    .build();
        
        MembersEntity memberEntity = MembersEntity.builder()
        	    .id("user1")
        	    .pw("securePassword")
        	    .nickName("테스트닉네임")
        	    .tel("010-1234-5678")
        	    .email("user1@example.com")
        	    .joinDate(Date.valueOf(java.time.LocalDate.now()))
        	    .build();

    	// When
        doNothing().when(boardRepository).upHit(contentNum);
        
	    when(boardRepository.findBoard(contentNum)).thenReturn(boardEntity);
	    when(boardConverter.toDTO(boardEntity)).thenReturn(boardDTO);
	    
	    when(boardRepository.commentSearch(contentNum)).thenReturn(commentEntityList);
	    when(commentConverter.toVOList(commentEntityList)).thenReturn(commentVOList);
	    
	    when(membersRepository.getMember("user1")).thenReturn(memberEntity);
	    when(memberConverter.toDTO(memberEntity)).thenReturn(memberDTO);
		
	    /* BoardService.searchView(contentNum)! */
		Map<String, Object> resultMap = boardService.sarchView(contentNum);
		

	    /* 결과 :
	     * resultMap.put("content_view", boardDTO);
	     * resultMap.put("comments", commentVOList);
	     * resultMap.put("userName", memberDTO);
	     * 
	     *  */
	
		// Then : 전체 Map 구조 검증
		assertNotNull(resultMap);
		assertEquals(3, resultMap.size()); // content_view, comments, userName 키 포함
		
		// Then : 의존 컴포넌트 호출 여부 검증
		verify(boardRepository, times(1)).upHit(contentNum);
		verify(boardRepository, times(1)).findBoard(contentNum);
		verify(boardRepository, times(1)).commentSearch(contentNum);
		verify(membersRepository, times(1)).getMember("user1");
		verify(boardConverter, times(1)).toDTO(boardEntity);
		verify(commentConverter, times(1)).toVOList(commentEntityList);
		verify(memberConverter, times(1)).toDTO(memberEntity);
    }

    /* 게시글 작성 */
    @Test
    void 게시글_작성() {
    	
    	
        // Given
        BoardDTO boardDTO = new BoardDTO();
        boardDTO.setTitle("Test Title");
        boardDTO.setBoardContent("Test Content");

        when(boardConverter.toEntity(boardDTO)).thenReturn(null);
        doNothing().when(boardRepository).boardWrite(null);

  
        // When
        boardService.boardWrite(boardDTO);

        // Then
        verify(boardRepository, times(1)).boardWrite(null);
    }

    /* BoardService.writeReply() : 댓글 작성 테스트. */
    @Test
    void BoardServiceWriteReply() throws InterruptedException {
    	
    	logger.info("BoardServiceUNITest.BoardServiceWriteReply()");

    	logger.info("TestJWT.token: {}", testToken);
    	logger.info("TestJWT.testSecretKey: {}", testSecretKey);
    	
    	Optional<Key> key = Optional.of(
    			new SecretKeySpec(
    					Base64.getUrlDecoder().decode(testSecretKey), SignatureAlgorithm.HS256.getJcaName()));
    	
        CommandtVO commandtVO = CommandtVO.builder()
        		.boardId(1)
        		.userId("user1")
        		.userName("user1")
        		.replyContent("테스트 댓글")
        		.build();

        /* Redis 동작 mocking */
        when(redisHandler.getValueOperations()).thenReturn(mockValueOperations);
        when((String) mockValueOperations.get(testToken)).thenReturn(testSecretKey);

        /* JWTUtil 동작 mocking */
        when(jwtUtil.getSigningKeyFromToken(testSecretKey)).thenReturn(key);
        
        when(jwtUtil.extractUserId(testToken, key.get())).thenReturn("user1");
        when(jwtUtil.extractUsername(testToken, key.get())).thenReturn("user1");
        
        boardService.writeReply(testToken, commandtVO);

        // Then
        assertEquals("user1", commandtVO.getUserId());		// 테스트 토큰 생성 시 적용했던 사용자 아이디 클레임
        assertEquals("user1", commandtVO.getUserName()); 	// 테스트 토큰 생성 시 적용했던 사용자 명 클레임
        verify(boardRepository, times(1)).replyWrite(any());
    }

    @Test
    void boardDeleteSuccess() {
        
    	logger.info("BoardServiceUNITest.boardDeleteSuccess()");

    	logger.info("TestJWT.token: {}", testToken);
    	logger.info("TestJWT.testSecretKey: {}", testSecretKey);
    	
    	// Given
        int boardId = 1;

        // Mocking 시 필요한 Key 객체 생성.
        Optional<Key> key = Optional.of(
    			new SecretKeySpec(
    					Base64.getUrlDecoder().decode(testSecretKey), SignatureAlgorithm.HS256.getJcaName()));
        // Redis 관련 Moking
        when(redisHandler.getValueOperations()).thenReturn(mockValueOperations);
        when((String) mockValueOperations.get(testToken)).thenReturn(testSecretKey);
        
        /* JWTUtil 동작 mocking */
        when(jwtUtil.getSigningKeyFromToken(testSecretKey)).thenReturn(key);    // Key 문자열을 가지고 실제 Key 객체 생성. 
        when(jwtUtil.extractUserId(testToken, key.get())).thenReturn("user1");  // JWT 와 Key 사용해서 userId 추출.
        
        /*boardEntityRepository 동작 Mocking  */
        when(boardEntityRepository.findUseridByConnum(boardId)).thenReturn("user1");
        
        boardService.deleteBoard(boardId, testToken);
        
        verify(boardRepository, times(1)).deleteBoard(boardId);
    }
    /* 게시글 삭제 실패 : 작성자불일치 */
    
    @Test
    void boardDeleteFail() {
        logger.info("BoardServiceUNITest.boardDeleteFail()");

        logger.info("TestJWT.token: {}", testToken);
        logger.info("TestJWT.testSecretKey: {}", testSecretKey);

        // Given
        int boardId = 1;

        // Key mocking
        Optional<Key> key = Optional.of(
            new SecretKeySpec(
                Base64.getUrlDecoder().decode(testSecretKey), SignatureAlgorithm.HS256.getJcaName()));

        // Redis mocking
        when(redisHandler.getValueOperations()).thenReturn(mockValueOperations);
        when((String) mockValueOperations.get(testToken)).thenReturn(testSecretKey);

        // JWTUtil mocking
        when(jwtUtil.getSigningKeyFromToken(testSecretKey)).thenReturn(key);
        when(jwtUtil.extractUserId(testToken, key.get())).thenReturn("user1"); // JWT 사용자

        // Repository mocking: 실제 게시글 작성자는 다른 사용자
        when(boardEntityRepository.findUseridByConnum(boardId)).thenReturn("otherUser");

       // When
        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> {
            boardService.deleteBoard(boardId, testToken);
        });

        // Then
        assertEquals("게시글 작성자가 아니므로 삭제할 수 없습니다.", ex.getMessage());
        verify(boardRepository, never()).deleteBoard(anyInt());
    }
}

