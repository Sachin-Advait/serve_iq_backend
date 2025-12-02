package com.gis.servelq.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TvContentDTO {

    @JsonProperty("iptv")
    private List<IptvDto> iptv;

    @JsonProperty("video")
    private List<VideoDto> video;


    // ---------------------- Video DTO -----------------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VideoDto {

        @JsonProperty("id")
        private String id;

        @JsonProperty("branchId")
        private String branchId;

        @JsonProperty("name")
        private String name;

        @JsonProperty("url")
        private String url;     // May be null if stored locally

        @JsonProperty("type")
        private String type;    // "VIDEO"

        @JsonProperty("active")
        private Boolean active;

        @JsonProperty("size")
        private Long size;      // File size in bytes
    }


    // ---------------------- IPTV DTO ------------------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IptvDto {

        @JsonProperty("id")
        private String id;

        @JsonProperty("branchId")
        private String branchId;

        @JsonProperty("name")
        private String name;

        @JsonProperty("url")
        private String url;

        @JsonProperty("type")
        private String type;    // "URL"

        @JsonProperty("active")
        private Boolean active;

        @JsonProperty("size")
        private Long size;      // Optional
    }

}
