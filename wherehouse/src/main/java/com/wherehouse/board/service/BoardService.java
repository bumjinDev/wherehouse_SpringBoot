package com.wherehouse.board.service;

import java.security.Key;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import com.wherehouse.JWT.Filter.Util.CookieUtil;
import com.wherehouse.JWT.Filter.Util.JWTUtil;
import com.wherehouse.board.dao.BoardEntityRepository;
import com.wherehouse.board.dao.IBoardRepository;
import com.wherehouse.board.exception.BoardAuthorizationException;
import com.wherehouse.board.exception.BoardNotFoundException;
import com.wherehouse.board.model.BoardConverter;
import com.wherehouse.board.model.BoardDTO;
import com.wherehouse.board.model.BoardEntity;
import com.wherehouse.board.model.CommandtVO;
import com.wherehouse.board.model.CommentConverter;
import com.wherehouse.members.dao.IMembersRepository;
import com.wherehouse.members.model.MemberConverter;
import com.wherehouse.members.model.MembersEntity;
import com.wherehouse.redis.handler.RedisHandler;

@Service
public class BoardService implements IBoardService {

    private static final Logger logger = LoggerFactory.getLogger(BoardService.class);
    
    IBoardRepository boardRepository;
    IMembersRepository membersRepository;
    BoardEntityRepository boardEntityRepository;
    
    RedisHandler redisHandler;
    JWTUtil jwtUtil; // JWTUtil 의존성 주입
    CookieUtil cookieUtil;
    
    BoardConverter boardConverter;
    CommentConverter commentConverter;
    MemberConverter memberConverter;
    
    public BoardService(
            
            IBoardRepository boardRepository,
            IMembersRepository membersRepository,
            BoardEntityRepository boardEntityRepository,
            
            RedisHandler redisHandler,
            JWTUtil jwtUtil,
            CookieUtil cookieUtil,
            
            BoardConverter boardConverter,
            CommentConverter commentConverter,
            MemberConverter memberConverter
        ) {
        
        this.boardRepository = boardRepository;
        this.membersRepository = membersRepository;
        this.boardEntityRepository = boardEntityRepository;
        
        this.redisHandler = redisHandler;
        this.jwtUtil = jwtUtil;
        this.cookieUtil = cookieUtil;
        
        this.boardConverter = boardConverter;
        this.commentConverter = commentConverter;
        this.memberConverter = memberConverter;
    }
    
    /* ============================ 게시판 관련 메소드 =================================== */

    /**
     * 지정한 페이지 번호에 해당하는 게시글 목록을 조회하고, 각 게시글의 작성자 닉네임을 함께 반환한다.
     *
     * @param pnIndex 조회할 페이지 번호 (0부터 시작)
     * @return 다음 항목을 포함하는 Map 객체:
     *         - "pnSize": 전체 게시글 수를 기준으로 계산된 전체 페이지 수 (int)
     *         - "boardList": 해당 페이지의 게시글 엔티티 리스트 (List<BoardEntity>)
     *         - "members": 게시글별 작성자 닉네임 리스트 (List<String>)
     *
     * 동작 방식:
     * - 전체 게시글 수를 기반으로 페이지 수 계산 (페이지당 10건 기준)
     * - 지정 페이지 번호에 해당하는 게시글 10건 조회
     * - 각 게시글의 작성자 ID를 추출하여 닉네임 리스트 생성
     */

    public Map<String, Object> searchBoard(int pnIndex) {
        
        HashMap<String, Object> board = new HashMap<>(); // 결과 데이터 저장용
        
        // 전체 게시글 수를 기준으로 페이지 수 계산 (페이지당 10개 게시글 기준)
        int pnSize = (int) Math.ceil(boardEntityRepository.count() / 10.0);
        
        // 요청 받은 페이지 번호에 해당하는 게시글 10건 조회
        List<BoardEntity> boardList = boardRepository.searchBoardList(pnIndex);
        
        // 게시글 작성자 ID 목록 추출
        List<String> boardUserIds = boardList.stream()
                .map(BoardEntity::getUserid)
                .collect(Collectors.toList());
        // 각 게시글 별 작성자 닉네임을 가져오기
        List<String> members = (boardList.size() >= 1) ? membersRepository.getMembers(boardUserIds) : null;
        // 결과 데이터 구성
        board.put("pnSize", pnSize); // 전체 게시글 수에 대한 페이지 네이션 개수
        board.put("boardList", boardList); // 게시글 전체 목록
        board.put("members", members); // 작성자 닉네임
        
        return board;
    }
    
    /**
     * 게시글 상세 정보를 조회하고, 관련 댓글 및 작성자 닉네임을 함께 반환한다.
     *
     * @param contentnum 조회 대상 게시글 번호
     * @return 다음 항목을 포함하는 Map 객체:
     *         - "content_view": 조회된 게시글 DTO (BoardDTO)
     *         - "comments": 해당 게시글에 대한 댓글 리스트 (List<CommandtVO>)
     *         - "userName": 게시글 작성자의 닉네임 (String)
     *
     * 동작 방식:
     * 1. 게시글 번호를 기반으로 게시글을 조회한다. 존재하지 않을 경우 NoSuchElementException 발생
     * 2. 게시글 조회수(upHit)를 증가시킨다. 실패 시 NoSuchElementException 발생
     * 3. 게시글 DTO, 댓글 목록, 작성자 닉네임을 Map 형태로 구성하여 반환한다
     *
     * 예외 처리 메시지 :
     * - 게시글이 존재하지 않는 경우: "해당 게시글을 찾을 수 없습니다."
     * - 조회수 증가 실패 시: "게시글 선택을 실패 하였습니다."
     * - 작성자 정보가 존재하지 않는 경우 닉네임은 "Anonymous"로 설정
     *
     * 인증 정보는 필요하지 않으며, 무인증 상태에서도 접근 가능한 조회 기능이다.
     */
    
    @Override
    public Map<String, Object> sarchView(int contentnum) {
        
        HashMap<String, Object> resultMap = new HashMap<>();
        
        // 존재하지 않은 게시글 번호로 조회 (존재하지 않으면 예외 발생)
        BoardDTO boardDTO = boardConverter.toDTO(boardRepository.findBoard(contentnum)
                                                 .orElseThrow(() -> new BoardNotFoundException("해당 게시글을 찾을 수 없습니다. ID: " + contentnum)));
        
        // 조회수 증가 처리 : 성공 시 1 반환, 실패 시 0 반환.(실패 시 예외 발생)
        if(boardRepository.upHit(contentnum) == 0) { throw new BoardNotFoundException("게시글 선택을 실패 하였습니다."); }
        
        // 게시글 DTO 반환
        resultMap.put("content_view", boardDTO);
        
        // 댓글 목록 조회 및 변환
        resultMap.put("comments", commentConverter.toVOList(boardRepository.commentSearch(contentnum)));
        
        // 게시글 작성자가 현재 존재하지 않는 사용자 일 시 별도의 처리
        Optional<MembersEntity> membersEntity = membersRepository.getMember(boardDTO.getUserId());
        String userName = (membersEntity.get().getNickName() == null) ? "Anonymous" : membersEntity.get().getNickName();
        
        // 게시글 작성자 ID를 기반으로 닉네임 조회
        resultMap.put("userName",userName);
        return resultMap;
    }
    
    /**
     * 게시글 작성 페이지 진입 시, JWT 토큰을 기반으로 사용자 정보를 반환한다.
     *
     * @param jwtToken 클라이언트로부터 전달받은 JWT 액세스 토큰 (HttpOnly 쿠키 기반에서 추출된 값)
     * @return 다음 항목을 포함하는 Map<String, String> 객체:
     *         - "userId"   : JWT Payload 내 사용자 ID
     *         - "userName" : JWT Payload 내 사용자 이름 (닉네임)
     *
     * 내부 동작:
     * 1. Redis 에서 JWT 토큰 문자열에 해당하는 서명 키(Base64 문자열)를 조회한다.
     * 2. JWTUtil을 통해 서명 키를 복원한 후, 해당 키로 토큰의 유효성을 검증하고 클레임 정보를 추출한다.
     * 3. 추출한 사용자 식별자(userId) 및 사용자명(userName)을 Map에 담아 반환한다.
     *
     * 인증 처리 주석:
     * - Spring Security FilterChain 내에서 이미 JWT 유효성 검증이 완료된 상태에서 이 메서드가 호출되므로,
     *   본 메서드는 추가적인 인증 로직을 수행하지 않으며, 클레임 파싱만을 담당한다.
     *
     * 예외 처리:
     * - Redis에 키가 존재하지 않거나 서명 키 복원 실패 시 Optional.get()에서 예외 발생 가능성 존재
     */
    
    @Override
    public Map<String, String> writePage(String jwtToken) {
        
    	/* 글 작성 페이지 내 포함될 데이터 셋 */
        Map<String, String> dataSet = new HashMap<>();
        
        /* 별도의 추가 검증 없이 JWT 내 글 작성 페이지 제공에 필요한 userId 와 userName 만 포함 */
        /* JWT 검증 및 JWT 클레임 인 사용자 ID 와 이름을 글 작성 페이지 내 포함 목적 */
        String keyValue = (String) redisHandler.getValueOperations().get(jwtToken);
        // Key 생성
        Key key = jwtUtil.getSigningKeyFromToken(keyValue).get();
        		
        dataSet.put("userId", jwtUtil.extractUserId(jwtToken, key));
        dataSet.put("userName", jwtUtil.extractUsername(jwtToken, key));
        
        return dataSet;
    }
    
    /**
     * 게시글 작성 요청 처리 메서드
     *
     * @param boardDTO 사용자가 입력한 게시글 작성 정보 (제목, 내용, 지역 등)를 포함하는 DTO 객체
     *
     * 처리 흐름:
     * 1. 클라이언트로부터 전달받은 BoardDTO 객체에 대해 서버 측에서 다음 값을 강제 설정함:
     *    - 조회수(hit): 최초 작성 시 0으로 초기화
     *    - 작성일자(boardDate): 서버 기준 현재 일자로 설정 (LocalDate.now())
     *    
     * 2. DTO를 BoardEntity로 변환한 후, Repository 계층을 통해 DB에 저장 수행
     *
     * 설계 근거:
     * - 클라이언트에서 임의로 조작 가능한 값(조회수, 작성일자)은 서버에서 무조건 재설정하여 데이터 무결성 보장
     * - DTO → Entity 변환은 전용 Converter에서 처리함으로써 계층 간 명확한 역할 분리 유지
     *
     * 인증 관련:
     * - 본 메서드는 JWT 인증이 사전 필터 체인 내에서 완료된 상태를 전제로 하며, 별도의 사용자 검증은 필요하지 않음
     *   사용자 ID는 이미 DTO 내에 포함되어 있다고 간주함 (writePage 단계에서 클레임 기반 셋팅)
     */
    @Override
    public void boardWrite(BoardDTO boardDTO) {
    
        // 게시글 작성 시 조회수 초기화 및 현재 날짜로 작성일 설정
        boardDTO.setBoardHit(0); // 조회수 초기화
        boardDTO.setBoardDate(Date.valueOf(LocalDate.now())); // 현재 날짜 설정
        // 게시글을 Entity로 변환 후 저장
        boardRepository.boardWrite(boardConverter.toEntity(boardDTO));
    }
    
    /**
     * 게시글 수정 페이지 진입 요청 처리
     *
     * @param token 클라이언트로부터 전달된 JWT 액세스 토큰 (쿠키 기반 전달)
     * @param boardDTO 수정 대상 게시글 정보를 담은 DTO (boardId, 작성자 ID 포함)
     * @return 수정 페이지 렌더링에 필요한 게시글 상세 정보를 Map 형태로 반환
     *
     * 처리 흐름:
     * 1. 전달받은 JWT 토큰을 Redis에서 키 조회하여 서명 키 복원
     * 2. 복원된 키로 JWT에서 사용자 ID 및 닉네임 추출
     * 3. 게시글의 작성자 ID와 JWT의 사용자 ID가 일치하는지 검증
     *    - 일치: 수정 페이지에 필요한 게시글 정보 + 작성자 닉네임 반환
     *    - 불일치: AccessDeniedException 발생 (인가 실패 처리)
     *
     * 설계 근거:
     * - 본 메서드는 필터 체인 내 인증 처리가 완료된 상태를 전제로 하며, 인증 자체는 수행하지 않음
     * - 작성자 본인 여부에 대한 재검증만 서버에서 수행하여 불법 접근 차단
     * - JWT에서 직접 사용자 ID/이름 추출하여 클라이언트에 의존하지 않고 신뢰성 확보
     *
     * 예외 발생 조건:
     * - Redis 내 서명 키가 존재하지 않거나 JWT 파싱 실패: NoSuchElementException 또는 Optional.get() NPE
     * - 작성자 ID 불일치: AccessDeniedException (Spring Security 인가 실패 처리 대상)
     *
     * 반환 형태:
     * - key: "boardId", "title", "boardContent", "region", "boardDate", "boardHit", "AuthorNickname"
     *   → JSP 수정 화면 렌더링에 필요한 필드만 포함
     */

    @Override
    public HashMap<String, String> boardModifyPage(String token, BoardDTO boardDTO) {
        
        logger.info("BoardModifyPageService.boardModifyPage()!");
        
        // JWT 토큰을 기반으로 서명 키를 Redis에서 조회
        String keyValue = (String) redisHandler.getValueOperations().get(token);
        Key signingKey = jwtUtil.getSigningKeyFromToken(keyValue).get();
        
        // JWT에서 사용자 ID 추출
        String sessionId = jwtUtil.extractUserId(token, signingKey);
        String userName = jwtUtil.extractUsername(token, signingKey);
        
        // 게시글 작성자와 요청자가 일치하는지 확인
        if (sessionId.equals(boardDTO.getUserId())) {
            
            // 작성자가 맞으면 수정 페이지 데이터 반환
            HashMap<String, String> boardViewData = new HashMap<>();
            boardViewData.put("boardId", String.valueOf(boardDTO.getBoardId()));
            boardViewData.put("title", boardDTO.getTitle());
            boardViewData.put("boardContent", boardDTO.getBoardContent());
            boardViewData.put("region", boardDTO.getRegion());
            boardViewData.put("boardDate", String.valueOf(boardDTO.getBoardDate()));
            boardViewData.put("boardHit", String.valueOf(boardDTO.getBoardHit()));
            boardViewData.put("AuthorNickname", userName);
            
            return boardViewData;
        } else {
        	/* 현재 발생된 예외는 이미 이전에 권한을 검증 했음에도 불구하고 게시글 작성자가 아닌 사용자가 수정 페이지 요청 한 것이므로 별도의 페이지 반환. */
        	throw new BoardAuthorizationException("게시글 작성자가 아닙니다. 수정 접근이 거부되었습니다.");
        }
    }

    /**
     * 게시글 실제 수정 요청을 받아 DBMS 에 적용.
     *
     * @param boardDTO 수정된 게시글 정보 DTO
     * !JWT 검증 자체는 Spring Security Filter Chain 내 이미 검증 완료 했으므로 별도의 로직 처리 하지 않으며
     * 사용자가 동일한 지에 대한 여부만 검증한다.
     */
    @Override
    public void modifyBoard(BoardDTO boardDTO, String token) {
    	
    	// JWT 토큰을 기반으로 서명 키를 Redis에서 조회
        String keyValue = (String) redisHandler.getValueOperations().get(token);
        Key signingKey = jwtUtil.getSigningKeyFromToken(keyValue).get();
        
        /* 수정된 게시글 Entity로 변환 후 저장 */
        if(!jwtUtil.extractUserId(token, signingKey).equals(boardDTO.getUserId())) {	// JWT에서 사용자 ID 추출 : 현재 API 요청 사용자 토큰과 실제 작성자가 같은지 확인.
        	throw new BoardAuthorizationException("게시글 작성자가 아니므로 수정할 수 없습니다.");
        }
        boardRepository.boardModify(boardConverter.toEntity(boardDTO));			// 수정 요청 게시글의 실제 DBMS 작업 수행.	
    }

    /**
     * 게시글 삭제 요청
     * 
     * @param boardId 삭제할 게시글 ID
     * @param jwt JWT 토큰
     * @return 삭제 성공 시 게시판 목록 페이지 URL, 실패 시 게시글 상세 페이지 URL
     * @throws UnauthorizedException 삭제 권한이 없는 경우
     */
    @Override
    public Map<String, String> deleteBoard(int boardId, String jwt) {
        
        logger.info("BoardDeleteService.deleteBoard()!");
        
        Map<String, String> response = new HashMap<>();
        
        // JWT 토큰을 기반으로 사용자 ID를 추출
        String keyValue = (String) redisHandler.getValueOperations().get(jwt);
        String sessionId = jwtUtil.extractUserId(jwt, jwtUtil.getSigningKeyFromToken(keyValue).get());
        
        // 게시글 작성자와 요청자가 일치하는지 확인, 작성자가 아니라면 삭제 불가
        if (!sessionId.equals(boardEntityRepository.findUseridByConnum(boardId))) {
            throw new BoardAuthorizationException("게시글 작성자가 아니므로 삭제할 수 없습니다.");
        }
        // 게시글 삭제
        boardRepository.deleteBoard(boardId);
        
        return response;
    }

    /* ============================ 댓글 관련 메소드 =================================== */
    
    /**
     * 댓글 작성 요청을 처리.
     * 
     * @param token JWT 토큰
     * @param commandtVO 댓글 내용 및 작성자 정보 VO
     * @throws UnauthorizedException JWT 인증 실패 시
     * 
     * Spring Security 내 토큰 검증이 완료 되어서 별도의 추가 검증은 수행하지 않으며, 댓글 작성 시 사용자 구분 없으니 별도의 인증 과정은 생략.
     */
    @Override
    public void writeReply(String token, CommandtVO commandtVO) {
        
        // Redis에서 JWT 토큰에 해당하는 키 값 조회
        String keyValue = (String) redisHandler.getValueOperations().get(token);
        // 서명 키 복원 및 유효성 검증
        Key signingKey = jwtUtil.getSigningKeyFromToken(keyValue).get();
        
        // JWT에서 사용자 ID 및 이름 추출 후 댓글 VO에 설정
        commandtVO.setUserId(jwtUtil.extractUserId(token, signingKey));
        commandtVO.setUserName(jwtUtil.extractUsername(token, signingKey));
        
        // 댓글 Entity로 변환 후 저장
        boardRepository.replyWrite(commentConverter.toEntity(commandtVO));
    }
}