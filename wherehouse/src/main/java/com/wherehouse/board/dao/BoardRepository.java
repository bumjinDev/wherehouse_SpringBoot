package com.wherehouse.board.dao;

import com.wherehouse.board.model.BoardEntity;
import com.wherehouse.board.model.CommentEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * BoardRepository는 게시판 관련 데이터베이스 작업을 처리하는 클래스입니다.
 * - 데이터 조회, 추가, 수정, 삭제 기능을 제공합니다.
 * - JPA Repository 및 기타 데이터베이스 관리 객체를 활용합니다.
 */
@Service
public class BoardRepository implements IBoardRepository {


    BoardEntityRepository boardEntityRepository;

    CommentEntityRepository commentEntityManger;

    
    public BoardRepository(
    		
    		BoardEntityRepository boardEntityRepository,
    		CommentEntityRepository commentEntityManger
    	) {
    	
    	this.boardEntityRepository = boardEntityRepository;
    	this.commentEntityManger = commentEntityManger;

    }
    
    /**
     * 게시글 목록 조회 및 페이지네이션 처리.
     * 
     * - 게시글의 총 개수를 계산하고, 페이지 단위로 데이터를 가져옵니다.
     * - 요청받은 페이지 번호에 따라 특정 범위의 게시글을 반환합니다.
     * - 게시글 작성자 닉네임 목록도 함께 반환합니다.
     *
     * @param pnIndex 요청 페이지 번호 (0부터 시작)
     * @return Map 형태로 페이지네이션 정보, 게시글 목록, 작성자 닉네임 목록 포함
     */
    public HashMap<String, Object> searchBoardList(int pnIndex) {

        HashMap<String, Object> resultBoard = new HashMap<>(); 		// 결과 데이터 저장용
        
        //List<BoardVO> boardList = null;							// 페이지네이션 범위에 해당하는 게시글 목록
        List<BoardEntity> boardList = null;							// 페이지네이션 범위에 해당하는 게시글 목록
        
        List<String> members = null;
        
        // 게시글 전체 페이지 수 계산 (페이지 당 10개 게시글 기준)
        int pnSize = ((int) Math.ceil(boardEntityRepository.count() / 10.0));

        // 요청받은 페이지 번호에 해당하는 게시글 데이터 가져오기
        // boardList = boardConverter.toVOList(boardEntityRepository.findByBdateWithPagination(pnIndex * 10, 10));
        boardList = boardEntityRepository.findByBdateWithPagination(pnIndex * 10, 10);
        
        List<Integer> boardIdList = boardList.stream()
                .map(BoardEntity::getConnum)
                .collect(Collectors.toList());

        if(boardList.size() >= 1)
        	members = getMembers(boardIdList);
         else
			members = null;
			
        // 결과 데이터 구성
        resultBoard.put("pnSize", pnSize); 			// 전체 게시글 수에 대한 페이지 네이션 개수로써 게시글 페이지 버튼을 구현.
        resultBoard.put("boardList", boardList); 	// 게시글 전체 목록에 각 게시글 별 실제 데이터 제공.
        resultBoard.put("members", members);		// 게시글 전체 목록에 각 게시글 별 실제 작성자 닉네임을 표기
        
        return resultBoard;
    }

    /**
     * 게시글 작성.
     * 
     * - 새로운 게시글 데이터를 데이터베이스에 저장합니다.
     *
     * @param boardEntity 저장할 게시글 데이터
     */
    public void boardWrite(BoardEntity boardEntity) {
        boardEntityRepository.save(boardEntity);
    }

    /**
     * 특정 게시글 조회.
     *
     * @param boardId 조회할 게시글 ID
     * @return 조회된 게시글 데이터
     */
    public BoardEntity findBoard(int boardId) {
        return boardEntityRepository.findById(boardId)
            .orElseThrow(() -> new NoSuchElementException("해당 게시글을 찾을 수 없습니다. ID: " + boardId));
    }


    /**
     * 특정 게시글 삭제.
     *
     * @param boardId 삭제할 게시글 ID
     */
    public void deleteBoard(int boardId) {
        boardEntityRepository.deleteById(boardId);
    }

    /**
     * 게시글 조회수 증가.
     *
     * - 특정 게시글의 조회수를 1 증가시킵니다.
     *
     * @param boardId 조회수를 증가시킬 게시글 ID
     */
    public void upHit(int boardId) {
    	
        boardEntityRepository.findById(boardId).ifPresent(board -> {  	
            board.setHit(board.getHit() + 1);
            boardEntityRepository.save(board);
        });
    }

    /**
     * 게시글 수정.
     *
     * - 특정 게시글의 내용을 수정합니다.
     *
     * @param boardEntity 수정할 게시글 데이터 (ID 포함)
     */
    public void boardModify(BoardEntity boardEntity) {
        boardEntityRepository.findById(boardEntity.getConnum()).ifPresent(board -> {
        	
            board.setTitle(boardEntity.getTitle());
            board.setBoardcontent(boardEntity.getBoardcontent());
            board.setRegion(boardEntity.getRegion());
            
            boardEntityRepository.save(board);
        });
    }

    /**
     * 댓글 작성.
     *
     * - 특정 게시글에 댓글을 추가합니다.
     *
     * @param comment 추가할 댓글 데이터
     */
    @Override
    public void replyWrite(CommentEntity comment) {
        commentEntityManger.save(comment);
    }

    /**
     * 특정 게시글의 댓글 조회.
     *
     * @param commentId 댓글이 속한 게시글 ID
     * @return 해당 게시글의 댓글 목록
     */
    @Override
    public List<CommentEntity> commentSearch(int commentId) {
        return commentEntityManger.findByBoardId(commentId);
    }

    /**
     * 게시글 작성자 닉네임 조회.
     *
     * - 특정 범위의 게시글 ID를 기반으로 작성자 닉네임 목록을 반환합니다.
     *
     * @param start 시작 게시글 ID
     * @param end   끝 게시글 ID
     * @return 작성자 닉네임 목록
     */
    @Override
    public ArrayList<String> getMembers(List<Integer> boardIdList) {
        return (ArrayList<String>) boardEntityRepository.findUserIdByConnumIn(boardIdList);
    }

}
