package com.network.monitor.event;

import com.network.monitor.cache.SysConfigCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConfigRefreshEventListener {

    @Autowired
    private SysConfigCache sysConfigCache;

    @EventListener
    public void onConfigRefresh(ConfigRefreshEvent event) {
        try {
            sysConfigCache.refresh();
            log.info("配置缓存刷新成功");
        } catch (Exception e) {
            log.error("配置缓存刷新失败", e);
        }
    }
}
