package com.network.target.controller;

import com.network.target.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Java反序列化漏洞测试接口
 * 核心：模拟对象序列化/反序列化功能，直接解析用户上传的序列化数据
 */
@RestController
@RequestMapping("/target/deserial")
@Slf4j
public class DeserialController {

    private static final Set<String> ALLOWED_CLASSES = new HashSet<>();
    
    static {
        ALLOWED_CLASSES.add("com.network.target.controller.DeserialController$TestUser");
        ALLOWED_CLASSES.add("com.network.target.controller.DeserialController$TestData");
        ALLOWED_CLASSES.add("java.lang.String");
        ALLOWED_CLASSES.add("java.lang.Integer");
        ALLOWED_CLASSES.add("java.lang.Long");
        ALLOWED_CLASSES.add("java.util.HashMap");
        ALLOWED_CLASSES.add("java.util.ArrayList");
    }

    public static class TestUser implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private String username;
        private String nickname;
        private String email;

        public TestUser() {}

        public TestUser(Long id, String username, String nickname, String email) {
            this.id = id;
            this.username = username;
            this.nickname = nickname;
            this.email = email;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        @Override
        public String toString() {
            return String.format("TestUser{id=%d, username='%s', nickname='%s', email='%s'}", 
                    id, username, nickname, email);
        }
    }

    public static class TestData implements Serializable {
        private static final long serialVersionUID = 1L;
        private String key;
        private String value;

        public TestData() {}

        public TestData(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }

        @Override
        public String toString() {
            return String.format("TestData{key='%s', value='%s'}", key, value);
        }
    }

    /**
     * 漏洞接口：无校验反序列化
     * 攻击场景：攻击者可构造恶意序列化数据进行攻击
     */
    @PostMapping("/parse")
    public ApiResponse parseSerializedVulnerable(@RequestBody String serializedData) {
        try {
            log.warn("【高危反序列化漏洞】尝试反序列化数据：{}", 
                    serializedData.substring(0, Math.min(100, serializedData.length())));

            byte[] data = Base64.getDecoder().decode(serializedData);
            
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
            Object obj = ois.readObject();
            ois.close();

            log.warn("【反序列化漏洞触发】成功反序列化对象：{}", obj.getClass().getName());

            Map<String, Object> result = new HashMap<>();
            result.put("class_name", obj.getClass().getName());
            result.put("to_string", obj.toString());
            
            if (obj instanceof TestUser) {
                TestUser user = (TestUser) obj;
                result.put("id", user.getId());
                result.put("username", user.getUsername());
                result.put("nickname", user.getNickname());
                result.put("email", user.getEmail());
            } else if (obj instanceof TestData) {
                TestData testData = (TestData) obj;
                result.put("key", testData.getKey());
                result.put("value", testData.getValue());
            }

            return ApiResponse.success()
                    .message("反序列化成功（漏洞接口）")
                    .data("deserialized_object", result)
                    .data("warning", "反序列化漏洞：未对类进行白名单校验，可能导致远程代码执行！");

        } catch (ClassNotFoundException e) {
            log.error("反序列化类未找到", e);
            return ApiResponse.error()
                    .message("类未找到：" + e.getMessage())
                    .data("error_type", "ClassNotFoundException");
        } catch (IOException e) {
            log.error("反序列化IO异常", e);
            return ApiResponse.error()
                    .message("反序列化失败：" + e.getMessage())
                    .data("error_type", "IOException");
        } catch (Exception e) {
            log.error("反序列化异常", e);
            return ApiResponse.error()
                    .message("反序列化失败：" + e.getMessage())
                    .data("error_type", e.getClass().getSimpleName());
        }
    }

    /**
     * 安全接口：类白名单校验
     * 防护措施：
     * 1. 自定义ObjectInputStream，重写resolveClass方法
     * 2. 仅允许白名单内的类进行反序列化
     */
    @PostMapping("/safe-parse")
    public ApiResponse parseSerializedSafe(@RequestBody String serializedData) {
        try {
            log.info("【安全接口】尝试反序列化数据：{}", 
                    serializedData.substring(0, Math.min(100, serializedData.length())));

            byte[] data = Base64.getDecoder().decode(serializedData);
            
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data)) {
                @Override
                protected Class<?> resolveClass(java.io.ObjectStreamClass desc) 
                        throws IOException, ClassNotFoundException {
                    String className = desc.getName();
                    
                    if (!ALLOWED_CLASSES.contains(className)) {
                        log.warn("【安全拦截】检测到非法类：{}", className);
                        throw new ClassNotFoundException("类不在白名单中: " + className);
                    }
                    
                    return super.resolveClass(desc);
                }
            };
            
            Object obj = ois.readObject();
            ois.close();

            log.info("【安全接口】成功反序列化对象：{}", obj.getClass().getName());

            Map<String, Object> result = new HashMap<>();
            result.put("class_name", obj.getClass().getName());
            result.put("to_string", obj.toString());
            
            if (obj instanceof TestUser) {
                TestUser user = (TestUser) obj;
                result.put("id", user.getId());
                result.put("username", user.getUsername());
                result.put("nickname", user.getNickname());
                result.put("email", user.getEmail());
            } else if (obj instanceof TestData) {
                TestData testData = (TestData) obj;
                result.put("key", testData.getKey());
                result.put("value", testData.getValue());
            }

            return ApiResponse.success()
                    .message("反序列化成功（安全接口）")
                    .data("deserialized_object", result)
                    .data("security_note", "已通过类白名单校验");

        } catch (ClassNotFoundException e) {
            log.warn("安全反序列化拦截：{}", e.getMessage());
            return ApiResponse.error()
                    .message("安全拦截：" + e.getMessage())
                    .data("blocked_reason", "类不在白名单中")
                    .data("allowed_classes", ALLOWED_CLASSES);
        } catch (Exception e) {
            log.error("安全反序列化异常", e);
            return ApiResponse.error()
                    .message("反序列化失败：" + e.getMessage());
        }
    }

    /**
     * 生成测试序列化数据
     */
    @GetMapping("/generate-test-data")
    public ApiResponse generateTestData() {
        try {
            TestUser user = new TestUser(1001L, "testuser", "测试用户", "test@example.com");
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(user);
            oos.close();
            
            String serializedData = Base64.getEncoder().encodeToString(baos.toByteArray());

            Map<String, Object> result = new HashMap<>();
            result.put("original_object", user.toString());
            result.put("serialized_base64", serializedData);

            return ApiResponse.success()
                    .message("生成测试数据成功")
                    .data("test_data", result);

        } catch (Exception e) {
            log.error("生成测试数据异常", e);
            return ApiResponse.error()
                    .message("生成测试数据失败：" + e.getMessage());
        }
    }

    /**
     * 获取允许的类列表
     */
    @GetMapping("/allowed-classes")
    public ApiResponse getAllowedClasses() {
        return ApiResponse.success()
                .message("获取允许类列表成功")
                .data("allowed_classes", ALLOWED_CLASSES);
    }
}
