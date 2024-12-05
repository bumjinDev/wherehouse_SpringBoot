package com.wherehouse.board.dao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.wherehouse.board.model.BoardEntity;

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
	
    /** BoardRepository : connum 범위 내 'userid' 컬럼을 목록을 LSIT 로써 가져오기 */
    @Query("SELECT b.userid FROM BoardEntity b WHERE b.connum BETWEEN :start AND :end")
    List<String> findUserIdByConnumBetween(@Param("start") int start, @Param("end") int end);

    /** 특정 connum에 해당하는 userid 가져오기 */
    @Query("SELECT b.userid FROM BoardEntity b WHERE b.connum = :connum")
    String findUseridByConnum(@Param("connum") int connum);
}
