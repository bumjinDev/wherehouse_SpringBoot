package com.WhereHouse.APITest.ConvenienceStore.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class KakaoLocalApiResponse {
    private Meta meta;
    private List<Document> documents;

    @Data
    public static class Meta {
        @JsonProperty("total_count")
        private int totalCount;

        @JsonProperty("pageable_count")
        private int pageableCount;

        @JsonProperty("is_end")
        private boolean isEnd;
    }

    @Data
    public static class Document {
        @JsonProperty("id")
        private String id;

        @JsonProperty("place_name")
        private String placeName;

        @JsonProperty("category_name")
        private String categoryName;

        @JsonProperty("category_group_code")
        private String categoryGroupCode;

        @JsonProperty("phone")
        private String phone;

        @JsonProperty("address_name")
        private String addressName;

        @JsonProperty("road_address_name")
        private String roadAddressName;

        @JsonProperty("x")
        private String longitude;

        @JsonProperty("y")
        private String latitude;

        @JsonProperty("place_url")
        private String placeUrl;

        @JsonProperty("distance")
        private String distance;
    }
}