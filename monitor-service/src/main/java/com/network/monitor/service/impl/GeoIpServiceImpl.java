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
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class GeoIpServiceImpl implements GeoIpService {

    @Autowired(required = false)
    private DatabaseReader geoIpDatabaseReader;

    private static final Map<String, String> IP_LOCATION_MAP = new HashMap<>();
    
    static {
        IP_LOCATION_MAP.put("127.0.0.1", "本地回环");
        IP_LOCATION_MAP.put("0.0.0.0", "本地回环");
        IP_LOCATION_MAP.put("192.168", "局域网");
        IP_LOCATION_MAP.put("10.", "局域网");
        IP_LOCATION_MAP.put("172.16", "局域网");
        IP_LOCATION_MAP.put("172.17", "局域网");
        IP_LOCATION_MAP.put("172.18", "局域网");
        IP_LOCATION_MAP.put("172.19", "局域网");
        IP_LOCATION_MAP.put("172.20", "局域网");
        IP_LOCATION_MAP.put("172.21", "局域网");
        IP_LOCATION_MAP.put("172.22", "局域网");
        IP_LOCATION_MAP.put("172.23", "局域网");
        IP_LOCATION_MAP.put("172.24", "局域网");
        IP_LOCATION_MAP.put("172.25", "局域网");
        IP_LOCATION_MAP.put("172.26", "局域网");
        IP_LOCATION_MAP.put("172.27", "局域网");
        IP_LOCATION_MAP.put("172.28", "局域网");
        IP_LOCATION_MAP.put("172.29", "局域网");
        IP_LOCATION_MAP.put("172.30", "局域网");
        IP_LOCATION_MAP.put("172.31", "局域网");
        IP_LOCATION_MAP.put("::1", "本地回环");
        IP_LOCATION_MAP.put("localhost", "本地回环");
    }

    @Override
    public GeoIpDTO lookup(String ip) {
        if (ip == null || ip.isEmpty()) {
            return GeoIpDTO.unknown("未知");
        }

        if (isAvailable()) {
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
                return getSimpleLocation(ip);
            } catch (Exception e) {
                log.warn("IP地理位置查询异常: ip={}", ip, e);
                return getSimpleLocation(ip);
            }
        }
        
        return getSimpleLocation(ip);
    }

    private GeoIpDTO getSimpleLocation(String ip) {
        if (IP_LOCATION_MAP.containsKey(ip)) {
            String location = IP_LOCATION_MAP.get(ip);
            return GeoIpDTO.builder()
                    .ip(ip)
                    .country(location)
                    .province(location)
                    .city(location)
                    .valid(true)
                    .build();
        }

        for (Map.Entry<String, String> entry : IP_LOCATION_MAP.entrySet()) {
            if (ip.startsWith(entry.getKey())) {
                String location = entry.getValue();
                return GeoIpDTO.builder()
                        .ip(ip)
                        .country(location)
                        .province(location)
                        .city(location)
                        .valid(true)
                        .build();
            }
        }

        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
            return GeoIpDTO.builder()
                    .ip(ip)
                    .country("局域网")
                    .province("局域网")
                    .city("局域网")
                    .valid(true)
                    .build();
        }

        if (ip.equals("127.0.0.1") || ip.equals("::1") || ip.equals("localhost")) {
            return GeoIpDTO.builder()
                    .ip(ip)
                    .country("本地回环")
                    .province("本地回环")
                    .city("本地回环")
                    .valid(true)
                    .build();
        }

        return GeoIpDTO.builder()
                .ip(ip)
                .country("未知地区")
                .province("未知地区")
                .city("未知地区")
                .valid(true)
                .build();
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
