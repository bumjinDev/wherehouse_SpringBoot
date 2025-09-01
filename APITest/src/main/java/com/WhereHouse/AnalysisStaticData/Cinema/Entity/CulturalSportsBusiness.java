package com.WhereHouse.APITest.Cinema.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "CULTURAL_SPORTS_BUSINESS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CulturalSportsBusiness {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cultural_sports_seq")
    @SequenceGenerator(name = "cultural_sports_seq", sequenceName = "SEQ_CULTURAL_SPORTS_BUSINESS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "LOCAL_GOVT_CODE", nullable = false, length = 50)
    private String localGovtCode;

    @Column(name = "MANAGEMENT_NO", nullable = false, length = 100)
    private String managementNo;

    @Column(name = "LICENSE_DATE")
    private LocalDate licenseDate;

    @Column(name = "LICENSE_CANCEL_DATE")
    private LocalDate licenseCancelDate;

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

    @Column(name = "SUSPENSION_START_DATE")
    private LocalDate suspensionStartDate;

    @Column(name = "SUSPENSION_END_DATE")
    private LocalDate suspensionEndDate;

    @Column(name = "REOPEN_DATE")
    private LocalDate reopenDate;

    @Column(name = "PHONE_NUMBER", length = 50)
    private String phoneNumber;

    @Column(name = "LOCATION_AREA")
    private Double locationArea;

    @Column(name = "LOCATION_POSTAL_CODE", length = 50)
    private String locationPostalCode;

    @Column(name = "JIBUN_ADDRESS", length = 500)
    private String jibunAddress;

    @Column(name = "ROAD_ADDRESS", length = 500)
    private String roadAddress;

    @Column(name = "ROAD_POSTAL_CODE", length = 50)
    private String roadPostalCode;

    @Column(name = "BUSINESS_NAME", length = 300)
    private String businessName;

    @Column(name = "LAST_UPDATE_DATE")
    private LocalDate lastUpdateDate;

    @Column(name = "DATA_UPDATE_TYPE", length = 20)
    private String dataUpdateType;

    @Column(name = "DATA_UPDATE_TIME", length = 50)
    private String dataUpdateTime;

    @Column(name = "BUSINESS_TYPE_NAME", length = 100)
    private String businessTypeName;

    @Column(name = "COORD_X")
    private Double coordX;

    @Column(name = "COORD_Y")
    private Double coordY;

    @Column(name = "CULTURE_SPORTS_TYPE_NAME", length = 100)
    private String cultureSportsTypeName;

    @Column(name = "CULTURE_BUSINESS_TYPE_NAME", length = 100)
    private String cultureBusinessTypeName;

    @Column(name = "TOTAL_FLOORS")
    private Integer totalFloors;

    @Column(name = "SURROUNDING_ENVIRONMENT", length = 500)
    private String surroundingEnvironment;

    @Column(name = "PRODUCTION_ITEM_CONTENT", length = 1000)
    private String productionItemContent;

    @Column(name = "FACILITY_AREA")
    private Double facilityArea;

    @Column(name = "GROUND_FLOORS")
    private Integer groundFloors;

    @Column(name = "UNDERGROUND_FLOORS")
    private Integer undergroundFloors;

    @Column(name = "BUILDING_USE_NAME", length = 200)
    private String buildingUseName;

    @Column(name = "PASSAGE_WIDTH")
    private Double passageWidth;

    @Column(name = "LIGHTING_BRIGHTNESS")
    private Double lightingBrightness;

    @Column(name = "KARAOKE_ROOMS")
    private Integer karaokeRooms;

    @Column(name = "YOUTH_ROOMS")
    private Integer youthRooms;

    @Column(name = "EMERGENCY_STAIRS", length = 50)
    private String emergencyStairs;

    @Column(name = "EMERGENCY_EXIT", length = 50)
    private String emergencyExit;

    @Column(name = "AUTO_VENTILATION", length = 50)
    private String autoVentilation;

    @Column(name = "YOUTH_ROOM_EXISTS", length = 50)
    private String youthRoomExists;

    @Column(name = "SPECIAL_LIGHTING", length = 50)
    private String specialLighting;

    @Column(name = "SOUNDPROOF_FACILITY", length = 50)
    private String soundproofFacility;

    @Column(name = "VIDEO_PLAYER_NAME", length = 500)
    private String videoPlayerName;

    @Column(name = "LIGHTING_FACILITY", length = 50)
    private String lightingFacility;

    @Column(name = "SOUND_FACILITY", length = 50)
    private String soundFacility;

    @Column(name = "CONVENIENCE_FACILITY", length = 50)
    private String convenienceFacility;

    @Column(name = "FIRE_SAFETY_FACILITY", length = 50)
    private String fireSafetyFacility;

    @Column(name = "TOTAL_GAME_MACHINES")
    private Integer totalGameMachines;

    @Column(name = "EXISTING_GAME_BUSINESS_TYPE", length = 200)
    private String existingGameBusinessType;

    @Column(name = "PROVIDED_GAME_NAME", length = 500)
    private String providedGameName;

    @Column(name = "PERFORMANCE_HALL_TYPE", length = 100)
    private String performanceHallType;

    @Column(name = "ITEM_NAME", length = 300)
    private String itemName;

    @Column(name = "FIRST_REGISTRATION_TIME", length = 50)
    private String firstRegistrationTime;

    @Column(name = "REGION_TYPE_NAME", length = 100)
    private String regionTypeName;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}