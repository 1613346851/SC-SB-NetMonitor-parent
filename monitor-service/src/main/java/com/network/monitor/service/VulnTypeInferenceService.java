package com.network.monitor.service;

import com.network.monitor.dto.InterfaceFeatureDTO;
import com.network.monitor.dto.VulnInferenceResult;
import com.network.monitor.entity.ScanInterfaceEntity;

import java.util.List;

/**
 * 漏洞类型推断服务接口
 */
public interface VulnTypeInferenceService {

    /**
     * 根据接口特征推断可能的漏洞类型
     * @param feature 接口特征
     * @return 推断结果列表
     */
    List<VulnInferenceResult> inferVulnTypes(InterfaceFeatureDTO feature);

    /**
     * 根据接口实体推断可能的漏洞类型
     * @param entity 接口实体
     * @return 推断结果列表
     */
    List<VulnInferenceResult> inferVulnTypes(ScanInterfaceEntity entity);

    /**
     * 根据接口ID推断漏洞类型并更新
     * @param interfaceId 接口ID
     * @return 推断结果列表
     */
    List<VulnInferenceResult> inferAndUpdate(Long interfaceId);
}
