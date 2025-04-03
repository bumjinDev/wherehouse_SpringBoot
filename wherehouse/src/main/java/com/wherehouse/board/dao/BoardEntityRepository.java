package com.wherehouse.board.dao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.wherehouse.board.model.BoardEntity;

import jakarta.transaction.Transactional;

@Repository
public interface BoardEntityRepository extends JpaRepository<BoardEntity, Integer> {

    /** BoardRepository : 게시글 목록 가져오기 */
	@Query(value = """
	        SELECT * 
	        FROM whereboard
	        ORDER BY bdate DESC
	        OFFSET :offset ROWS FETCH NEXT :pageSize ROWS ONLY
	    """, nativeQuery = true)
	List<BoardEntity> findByBdateWithPagination(
	        @Param("offset") int offset, 
	        @Param("pageSize") int pageSize
	    );
	
	@Query("SELECT b.userid FROM BoardEntity b WHERE b.connum IN :boardIds")
	List<String> findUserIdByConnumIn(@Param("boardIds") List<Integer> boardIds);

    /** 특정 connum에 해당하는 userid 가져오기 */
    @Query("SELECT b.userid FROM BoardEntity b WHERE b.connum = :connum")
    String findUseridByConnum(@Param("connum") int connum);
    
    /* 조회수 증가 용. */
    @Modifying
    @Transactional
    @Query("UPDATE BoardEntity b SET b.hit = :hit WHERE b.connum = :connum")
    int updateHitByConnum(@Param("connum") int connum, @Param("hit") int hit);
}
