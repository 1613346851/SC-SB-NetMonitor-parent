package com.network.monitor.service;

import com.network.monitor.entity.AlertEntity;

/**
 * 通知服务接口
 */
public interface NotificationService {

    boolean sendEmail(AlertEntity alert);

    boolean sendFeishu(AlertEntity alert);

    boolean sendTestEmail();

    boolean sendTestFeishu();

    boolean isEmailEnabled();

    boolean isFeishuEnabled();
}
