package com.network.monitor.loader;

import com.network.monitor.entity.SysConfigEntity;

import java.util.List;

public interface ConfigLoader {

    List<SysConfigEntity> loadAllConfigs();
}