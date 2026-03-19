package com.network.target.controller;

import com.network.target.common.ApiResponse;
import com.network.target.entity.SerializedObjectEntity;
import com.network.target.repository.SerializedObjectRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/target/deserial")
@Slf4j
public class DeserialController {

    private final SerializedObjectRepository serializedObjectRepository;

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

    public DeserialController(SerializedObjectRepository serializedObjectRepository) {
        this.serializedObjectRepository = serializedObjectRepository;
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

            SerializedObjectEntity entity = new SerializedObjectEntity();
            entity.setObjectName(obj.toString());
            entity.setObjectType(obj.getClass().getName());
            entity.setSerializedData(data);
            serializedObjectRepository.save(entity);
            log.info("【数据库存储】已将序列化对象存储到数据库，ID：{}", entity.getId());

            Map<String, Object> result = new HashMap<>();
            result.put("class_name", obj.getClass().getName());
            result.put("to_string", obj.toString());
            result.put("db_id", entity.getId());
            
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
                    .data("db_stored", true)
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

    @GetMapping("/allowed-classes")
    public ApiResponse getAllowedClasses() {
        return ApiResponse.success()
                .message("获取允许类列表成功")
                .data("allowed_classes", ALLOWED_CLASSES);
    }

    @GetMapping("/stored-objects")
    public ApiResponse getStoredObjects() {
        List<SerializedObjectEntity> objects = serializedObjectRepository.findAll();
        List<Map<String, Object>> result = objects.stream().map(obj -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", obj.getId());
            map.put("object_name", obj.getObjectName());
            map.put("object_type", obj.getObjectType());
            map.put("create_time", obj.getCreateTime().toString());
            map.put("data_size", obj.getSerializedData() != null ? obj.getSerializedData().length : 0);
            return map;
        }).collect(Collectors.toList());

        return ApiResponse.success()
                .message("获取存储对象列表成功")
                .data("objects", result)
                .data("total", result.size());
    }

    @GetMapping("/stored-objects/{id}")
    public ApiResponse getStoredObjectById(@PathVariable Integer id) {
        SerializedObjectEntity entity = serializedObjectRepository.findById(id);
        if (entity == null) {
            return ApiResponse.error().message("对象不存在");
        }

        try {
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(entity.getSerializedData()));
            Object obj = ois.readObject();
            ois.close();

            Map<String, Object> result = new HashMap<>();
            result.put("id", entity.getId());
            result.put("class_name", obj.getClass().getName());
            result.put("to_string", obj.toString());
            result.put("create_time", entity.getCreateTime().toString());

            return ApiResponse.success()
                    .message("对象读取成功")
                    .data("object", result);
        } catch (Exception e) {
            log.error("读取存储对象失败", e);
            return ApiResponse.error().message("对象读取失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/stored-objects/{id}")
    public ApiResponse deleteStoredObject(@PathVariable Integer id) {
        int deleted = serializedObjectRepository.deleteById(id);
        if (deleted > 0) {
            return ApiResponse.success().message("对象删除成功");
        }
        return ApiResponse.error().message("对象不存在或删除失败");
    }

    @DeleteMapping("/stored-objects")
    public ApiResponse deleteAllStoredObjects() {
        int deleted = serializedObjectRepository.deleteAll();
        return ApiResponse.success()
                .message("已清空所有存储对象")
                .data("deleted_count", deleted);
    }

    @GetMapping("/stored-objects/count")
    public ApiResponse getStoredObjectsCount() {
        long count = serializedObjectRepository.count();
        return ApiResponse.success()
                .message("获取存储对象数量成功")
                .data("count", count);
    }
}
