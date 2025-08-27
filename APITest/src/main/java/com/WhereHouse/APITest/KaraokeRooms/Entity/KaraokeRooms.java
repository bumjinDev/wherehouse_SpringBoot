package com.WhereHouse.API.Test.APITest.KaraokeRooms.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "KARAOKE_ROOMS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KaraokeRooms {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "karaoke_room_seq")
    @SequenceGenerator(name = "karaoke_room_seq", sequenceName = "SEQ_KARAOKE_ROOMS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "DISTRICT_CODE", nullable = false, length = 20)
    private String districtCode;

    @Column(name = "MANAGEMENT_NUMBER", length = 100)
    private String managementNumber;

    @Column(name = "LICENSE_DATE")
    private LocalDate licenseDate;

    @Column(name = "CANCEL_DATE")
    private LocalDate cancelDate;

    @Column(name = "BUSINESS_STATUS_CODE", length = 20)
    private String businessStatusCode;

    @Column(name = "BUSINESS_STATUS_NAME", length = 100)
    private String businessStatusName;

    @Column(name = "DETAIL_STATUS_CODE", length = 20)
    private String detailStatusCode;

    @Column(name = "DETAIL_STATUS_NAME", length = 100)
    private String detailStatusName;

    @Column(name = "CLOSURE_DATE")
    private LocalDate closureDate;

    @Column(name = "SUSPEND_START_DATE")
    private LocalDate suspendStartDate;

    @Column(name = "SUSPEND_END_DATE")
    private LocalDate suspendEndDate;

    @Column(name = "REOPEN_DATE")
    private LocalDate reopenDate;

    @Column(name = "PHONE_NUMBER", length = 50)
    private String phoneNumber;

    @Column(name = "AREA_SIZE", precision = 10, scale = 2)
    private BigDecimal areaSize;

    @Column(name = "POSTAL_CODE", length = 20)
    private String postalCode;

    @Column(name = "JIBUN_ADDRESS", length = 1000)
    private String jibunAddress;

    @Column(name = "ROAD_ADDRESS", length = 1000)
    private String roadAddress;

    @Column(name = "ROAD_POSTAL_CODE", length = 20)
    private String roadPostalCode;

    @Column(name = "BUSINESS_NAME", length = 500)
    private String businessName;

    @Column(name = "LAST_MODIFIED_DATE")
    private LocalDateTime lastModifiedDate;

    @Column(name = "DATA_UPDATE_TYPE", length = 20)
    private String dataUpdateType;

    @Column(name = "DATA_UPDATE_DATE")
    private LocalDateTime dataUpdateDate;

    @Column(name = "BUSINESS_CATEGORY", length = 100)
    private String businessCategory;

    @Column(name = "COORDINATE_X", precision = 15, scale = 7)
    private BigDecimal coordinateX;

    @Column(name = "COORDINATE_Y", precision = 15, scale = 7)
    private BigDecimal coordinateY;

    @Column(name = "CULTURE_SPORTS_TYPE", length = 100)
    private String cultureSportsType;

    @Column(name = "CULTURE_BUSINESS_TYPE", length = 100)
    private String cultureBusinessType;

    @Column(name = "TOTAL_FLOORS")
    private Integer totalFloors;

    @Column(name = "SURROUNDING_ENVIRONMENT", length = 200)
    private String surroundingEnvironment;

    @Column(name = "PRODUCTION_ITEMS", length = 1000)
    private String productionItems;

    @Column(name = "FACILITY_AREA", precision = 10, scale = 2)
    private BigDecimal facilityArea;

    @Column(name = "GROUND_FLOORS")
    private Integer groundFloors;

    @Column(name = "BASEMENT_FLOORS")
    private Integer basementFloors;

    @Column(name = "BUILDING_PURPOSE", length = 200)
    private String buildingPurpose;

    @Column(name = "CORRIDOR_WIDTH", precision = 10, scale = 2)
    private BigDecimal corridorWidth;

    @Column(name = "LIGHTING_FACILITY_LUX")
    private Integer lightingFacilityLux;

    @Column(name = "KARAOKE_ROOMS_COUNT")
    private Integer karaokeRoomsCount;

    @Column(name = "YOUTH_ROOMS_COUNT")
    private Integer youthRoomsCount;

    @Column(name = "EMERGENCY_STAIRS", length = 20)
    private String emergencyStairs;

    @Column(name = "EMERGENCY_EXITS", length = 20)
    private String emergencyExits;

    @Column(name = "AUTO_VENTILATION", length = 20)
    private String autoVentilation;

    @Column(name = "YOUTH_ROOM_AVAILABLE", length = 20)
    private String youthRoomAvailable;

    @Column(name = "SPECIAL_LIGHTING", length = 20)
    private String specialLighting;

    @Column(name = "SOUNDPROOF_FACILITY", length = 20)
    private String soundproofFacility;

    @Column(name = "VIDEO_PLAYER_NAME", length = 200)
    private String videoPlayerName;

    @Column(name = "LIGHTING_FACILITY_YN", length = 20)
    private String lightingFacilityYn;

    @Column(name = "SOUND_FACILITY_YN", length = 20)
    private String soundFacilityYn;

    @Column(name = "CONVENIENCE_FACILITY_YN", length = 20)
    private String convenienceFacilityYn;

    @Column(name = "FIRE_FACILITY_YN", length = 20)
    private String fireFacilityYn;

    @Column(name = "TOTAL_GAME_MACHINES")
    private Integer totalGameMachines;

    @Column(name = "EXISTING_BUSINESS_TYPE", length = 200)
    private String existingBusinessType;

    @Column(name = "PROVIDED_GAMES", length = 500)
    private String providedGames;

    @Column(name = "VENUE_TYPE", length = 100)
    private String venueType;

    @Column(name = "ITEM_NAME", length = 500)
    private String itemName;

    @Column(name = "FIRST_REGISTRATION_TIME", length = 100)
    private String firstRegistrationTime;

    @Column(name = "REGION_TYPE", length = 100)
    private String regionType;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}