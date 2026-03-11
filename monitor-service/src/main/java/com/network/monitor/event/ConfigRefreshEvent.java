package com.network.monitor.event;

import org.springframework.context.ApplicationEvent;

public class ConfigRefreshEvent extends ApplicationEvent {

    public ConfigRefreshEvent(Object source) {
        super(source);
    }
}
