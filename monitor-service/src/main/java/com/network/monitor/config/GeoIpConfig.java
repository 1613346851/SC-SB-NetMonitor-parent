package com.network.monitor.config;

import com.maxmind.geoip2.DatabaseReader;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.Ip2Region;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Data
@Slf4j
@Configuration
public class GeoIpConfig {

    @Value("${geoip.enabled:true}")
    private Boolean enabled;

    @Value("${geoip.database-path:}")
    private String databasePath;

    @Value("${geoip.fallback-to-classpath:true}")
    private Boolean fallbackToClasspath;

    @Value("${geoip.ip2region.enabled:true}")
    private Boolean ip2regionEnabled;

    @Value("${geoip.ip2region.xdb-path:}")
    private String ip2regionXdbPath;

    @Bean
    public DatabaseReader geoIpDatabaseReader() {
        if (!enabled) {
            log.info("GeoIP服务已禁用");
            return null;
        }

        try {
            File databaseFile = findDatabaseFile();
            if (databaseFile != null && databaseFile.exists()) {
                DatabaseReader reader = new DatabaseReader.Builder(databaseFile).build();
                log.info("MaxMind GeoIP数据库加载成功: {}", databaseFile.getAbsolutePath());
                return reader;
            }
        } catch (IOException e) {
            log.warn("MaxMind GeoIP数据库加载失败: {}", e.getMessage());
        }

        log.warn("MaxMind GeoIP数据库未找到，将使用ip2region作为备用");
        return null;
    }

    @Bean
    public Ip2Region ip2Region() {
        if (!ip2regionEnabled) {
            log.info("ip2region服务已禁用");
            return null;
        }

        try {
            File xdbFile = findIp2RegionFile();
            if (xdbFile != null && xdbFile.exists()) {
                Searcher.verifyFromFile(xdbFile);
                Config v4Config = Config.custom()
                        .setCachePolicy(Config.VIndexCache)
                        .setSearchers(10)
                        .setXdbFile(xdbFile)
                        .asV4();
                Ip2Region ip2Region = Ip2Region.create(v4Config, null);
                log.info("ip2region数据库加载成功: {}", xdbFile.getAbsolutePath());
                return ip2Region;
            }
        } catch (Exception e) {
            log.warn("ip2region数据库加载失败: {}", e.getMessage());
        }

        log.warn("ip2region数据库未找到，IP地理位置查询功能将受限");
        return null;
    }

    private File findDatabaseFile() throws IOException {
        if (databasePath != null && !databasePath.isEmpty()) {
            File file = new File(databasePath);
            if (file.exists()) {
                return file;
            }
        }

        if (fallbackToClasspath) {
            ClassPathResource resource = new ClassPathResource("geoip/GeoLite2-City.mmdb");
            if (resource.exists()) {
                Path tempFile = Files.createTempFile("GeoLite2-City", ".mmdb");
                try (InputStream is = resource.getInputStream()) {
                    Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }
                tempFile.toFile().deleteOnExit();
                return tempFile.toFile();
            }
        }

        File defaultLocation = new File("data/geoip/GeoLite2-City.mmdb");
        if (defaultLocation.exists()) {
            return defaultLocation;
        }

        return null;
    }

    private File findIp2RegionFile() throws IOException {
        if (ip2regionXdbPath != null && !ip2regionXdbPath.isEmpty()) {
            File file = new File(ip2regionXdbPath);
            if (file.exists()) {
                log.info("使用配置的ip2region数据库路径: {}", file.getAbsolutePath());
                return file;
            }
        }

        ClassPathResource resource = new ClassPathResource("ip2region/ip2region.xdb");
        if (resource.exists()) {
            Path tempFile = Files.createTempFile("ip2region", ".xdb");
            try (InputStream is = resource.getInputStream()) {
                Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            tempFile.toFile().deleteOnExit();
            log.info("从classpath加载ip2region数据库: {}", resource.getURL());
            return tempFile.toFile();
        }

        ClassPathResource v4Resource = new ClassPathResource("ip2region/ip2region-3.15.0/data/ip2region_v4.xdb");
        if (v4Resource.exists()) {
            Path tempFile = Files.createTempFile("ip2region_v4", ".xdb");
            try (InputStream is = v4Resource.getInputStream()) {
                Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            tempFile.toFile().deleteOnExit();
            log.info("从classpath加载ip2region v4数据库: {}", v4Resource.getURL());
            return tempFile.toFile();
        }

        File defaultLocation = new File("data/ip2region/ip2region.xdb");
        if (defaultLocation.exists()) {
            log.info("从默认位置加载ip2region数据库: {}", defaultLocation.getAbsolutePath());
            return defaultLocation;
        }

        File sourceLocation = new File("src/main/resources/ip2region/ip2region.xdb");
        if (sourceLocation.exists()) {
            log.info("从源码目录加载ip2region数据库: {}", sourceLocation.getAbsolutePath());
            return sourceLocation;
        }

        File sourceV4Location = new File("src/main/resources/ip2region/ip2region-3.15.0/data/ip2region_v4.xdb");
        if (sourceV4Location.exists()) {
            log.info("从源码目录加载ip2region v4数据库: {}", sourceV4Location.getAbsolutePath());
            return sourceV4Location;
        }

        File projectRootLocation = new File("monitor-service/src/main/resources/ip2region/ip2region.xdb");
        if (projectRootLocation.exists()) {
            log.info("从项目根目录加载ip2region数据库: {}", projectRootLocation.getAbsolutePath());
            return projectRootLocation;
        }

        File projectRootV4Location = new File("monitor-service/src/main/resources/ip2region/ip2region-3.15.0/data/ip2region_v4.xdb");
        if (projectRootV4Location.exists()) {
            log.info("从项目根目录加载ip2region v4数据库: {}", projectRootV4Location.getAbsolutePath());
            return projectRootV4Location;
        }

        File absoluteLocation = new File("D:/SC-SB-NetMonitor-parent/monitor-service/src/main/resources/ip2region/ip2region.xdb");
        if (absoluteLocation.exists()) {
            log.info("从绝对路径加载ip2region数据库: {}", absoluteLocation.getAbsolutePath());
            return absoluteLocation;
        }

        log.warn("所有ip2region数据库路径均未找到，尝试的路径包括:");
        log.warn("  - classpath: ip2region/ip2region.xdb");
        log.warn("  - classpath: ip2region/ip2region-3.15.0/data/ip2region_v4.xdb");
        log.warn("  - data/ip2region/ip2region.xdb");
        log.warn("  - src/main/resources/ip2region/ip2region.xdb");
        log.warn("  - monitor-service/src/main/resources/ip2region/ip2region.xdb");
        log.warn("  - D:/SC-SB-NetMonitor-parent/monitor-service/src/main/resources/ip2region/ip2region.xdb");

        return null;
    }
}
