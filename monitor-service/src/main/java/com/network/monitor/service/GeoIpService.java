package com.network.monitor.service;

import com.network.monitor.dto.GeoIpDTO;

public interface GeoIpService {

    GeoIpDTO lookup(String ip);

    String getCountry(String ip);

    String getCity(String ip);

    boolean isAvailable();
}
