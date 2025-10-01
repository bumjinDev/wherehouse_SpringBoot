package com.database.migration.detailMapService.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ARRESTRATE")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArrestRate {

    @Id
    @Column(name = "ADDR")
    private String addr;

    @Column(name = "RATE")
    private Double rate;
}