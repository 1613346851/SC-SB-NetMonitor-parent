package com.network.monitor.config;

import com.maxmind.geoip2.DatabaseReader;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
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
                log.info("GeoIP数据库加载成功: {}", databaseFile.getAbsolutePath());
                return reader;
            }
        } catch (IOException e) {
            log.warn("GeoIP数据库加载失败: {}", e.getMessage());
        }

        log.warn("GeoIP数据库未找到，IP地理位置查询功能将不可用");
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
}
