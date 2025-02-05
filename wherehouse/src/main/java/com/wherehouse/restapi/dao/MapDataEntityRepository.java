package com.wherehouse.restapi.dao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.wherehouse.rest.redius.model.MapDataEntity;

public interface MapDataEntityRepository extends JpaRepository<MapDataEntity, Integer> {
    List<MapDataEntity> findAll(); // ✅ Page → List 변경
    List<MapDataEntity> findByGuidIn(List<Integer> guIds);
}
