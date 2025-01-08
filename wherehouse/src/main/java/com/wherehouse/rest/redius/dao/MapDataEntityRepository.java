package com.wherehouse.rest.redius.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wherehouse.rest.redius.model.MapDataENtity;


public interface MapDataEntityRepository extends JpaRepository<MapDataENtity, Integer>{
	
	/* guIds(List<String> 로 인자를 전달 받아 지역구 3개를 MapDataDTO 로 orm mapping 결과로 반환하는 jpa 메소드. */
	List<MapDataENtity> findByGuidIn(List<Integer> guIds);
}
