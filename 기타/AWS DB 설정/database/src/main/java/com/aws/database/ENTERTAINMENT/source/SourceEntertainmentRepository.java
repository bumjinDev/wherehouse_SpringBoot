package com.aws.database.ENTERTAINMENT.source;

import com.aws.database.ENTERTAINMENT.domain.EntertainmentEstablishment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SourceEntertainmentRepository extends JpaRepository<EntertainmentEstablishment, Long> {
}