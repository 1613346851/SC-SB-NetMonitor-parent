package com.network.monitor.service.impl;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.network.monitor.dto.GeoIpDTO;
import com.network.monitor.service.GeoIpService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;

@Slf4j
@Service
public class GeoIpServiceImpl implements GeoIpService {

    @Autowired(required = false)
    private DatabaseReader geoIpDatabaseReader;

    @Override
    public GeoIpDTO lookup(String ip) {
        if (!isAvailable()) {
            return GeoIpDTO.unknown(ip);
        }

        try {
            InetAddress ipAddress = InetAddress.getByName(ip);
            CityResponse response = geoIpDatabaseReader.city(ipAddress);

            return GeoIpDTO.builder()
                    .ip(ip)
                    .country(response.getCountry().getName())
                    .countryCode(response.getCountry().getIsoCode())
                    .province(response.getMostSpecificSubdivision().getName())
                    .provinceCode(response.getMostSpecificSubdivision().getIsoCode())
                    .city(response.getCity().getName())
                    .latitude(response.getLocation().getLatitude())
                    .longitude(response.getLocation().getLongitude())
                    .timezone(response.getLocation().getTimeZone())
                    .isp(response.getTraits().getIsp())
                    .organization(response.getTraits().getOrganization())
                    .accuracyRadius(response.getLocation().getAccuracyRadius())
                    .valid(true)
                    .build();
        } catch (IOException | GeoIp2Exception e) {
            log.debug("IP地理位置查询失败: ip={}, error={}", ip, e.getMessage());
            return GeoIpDTO.unknown(ip);
        } catch (Exception e) {
            log.warn("IP地理位置查询异常: ip={}", ip, e);
            return GeoIpDTO.unknown(ip);
        }
    }

    @Override
    public String getCountry(String ip) {
        GeoIpDTO geo = lookup(ip);
        return geo.getCountry();
    }

    @Override
    public String getCity(String ip) {
        GeoIpDTO geo = lookup(ip);
        String city = geo.getCity();
        String province = geo.getProvince();

        if ("未知".equals(city) && "未知".equals(province)) {
            return "未知";
        }

        if ("未知".equals(province) || province == null) {
            return city;
        }

        if ("未知".equals(city) || city == null) {
            return province;
        }

        return province + " " + city;
    }

    @Override
    public boolean isAvailable() {
        return geoIpDatabaseReader != null;
    }
}
