package com.network.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoIpDTO {

    private String ip;

    private String country;

    private String countryCode;

    private String province;

    private String provinceCode;

    private String city;

    private Double latitude;

    private Double longitude;

    private String timezone;

    private String isp;

    private String organization;

    private Integer accuracyRadius;

    private boolean valid;

    public static GeoIpDTO unknown(String ip) {
        return GeoIpDTO.builder()
                .ip(ip)
                .country("未知")
                .countryCode("N/A")
                .province("未知")
                .city("未知")
                .valid(false)
                .build();
    }
}
