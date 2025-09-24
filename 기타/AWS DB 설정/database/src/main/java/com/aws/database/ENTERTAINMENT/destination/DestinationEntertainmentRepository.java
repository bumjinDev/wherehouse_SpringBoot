package com.aws.database.ENTERTAINMENT.destination;

import com.aws.database.ENTERTAINMENT.domain.EntertainmentEstablishment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DestinationEntertainmentRepository extends JpaRepository<EntertainmentEstablishment, Long> {
}