package com.network.monitor.service.impl;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.network.monitor.dto.GeoIpDTO;
import com.network.monitor.service.GeoIpService;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.service.Ip2Region;
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

    @Autowired(required = false)
    private Ip2Region ip2Region;

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

        GeoIpDTO simpleLocation = getSimpleLocation(ip);
        if (simpleLocation != null) {
            return simpleLocation;
        }

        GeoIpDTO ip2regionResult = lookupByIp2Region(ip);
        if (ip2regionResult != null && ip2regionResult.isValid()) {
            return ip2regionResult;
        }

        if (isMaxMindAvailable()) {
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
                log.debug("MaxMind IP地理位置查询失败: ip={}, error={}", ip, e.getMessage());
            } catch (Exception e) {
                log.warn("MaxMind IP地理位置查询异常: ip={}", ip, e);
            }
        }
        
        return GeoIpDTO.unknown(ip);
    }

    private GeoIpDTO lookupByIp2Region(String ip) {
        if (ip2Region == null) {
            log.debug("ip2region未初始化，跳过查询: ip={}", ip);
            return null;
        }

        try {
            String region = ip2Region.search(ip);
            log.debug("ip2region查询结果: ip={}, region={}", ip, region);
            
            if (region == null || region.isEmpty()) {
                log.debug("ip2region返回空结果: ip={}", ip);
                return null;
            }

            String[] parts = region.split("\\|");
            if (parts.length < 5) {
                log.debug("ip2region返回格式不正确: ip={}, parts={}", ip, parts.length);
                return null;
            }

            String country = cleanField(parts[0]);
            String province = cleanField(parts[1]);
            String city = cleanField(parts[2]);
            String isp = cleanField(parts[3]);
            String countryCode = cleanField(parts[4]);

            boolean hasValidInfo = !country.isEmpty() || !province.isEmpty() || !city.isEmpty();
            
            if (!hasValidInfo) {
                log.debug("ip2region返回无有效地理位置信息: ip={}, region={}", ip, region);
                return null;
            }

            return GeoIpDTO.builder()
                    .ip(ip)
                    .country(country.isEmpty() ? "未知" : country)
                    .countryCode(countryCode)
                    .province(province.isEmpty() ? "未知" : province)
                    .city(city.isEmpty() ? "未知" : city)
                    .isp(isp)
                    .valid(true)
                    .build();
        } catch (Exception e) {
            log.debug("ip2region查询失败: ip={}, error={}", ip, e.getMessage());
            return null;
        }
    }

    private String cleanField(String field) {
        if (field == null || "0".equals(field)) {
            return "";
        }
        return field.trim();
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

        return null;
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

        if ("未知".equals(province) || province == null || province.isEmpty()) {
            return city;
        }

        if ("未知".equals(city) || city == null || city.isEmpty()) {
            return province;
        }

        return province + " " + city;
    }

    @Override
    public boolean isAvailable() {
        return ip2Region != null || geoIpDatabaseReader != null;
    }

    private boolean isMaxMindAvailable() {
        return geoIpDatabaseReader != null;
    }
}
