package edu.uemwiki.contrib.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/** 从唯一的 classpath catalog.json 读取分类，并负责投稿专业校验与名称解析。 */
@Service
public class CatalogService {
    private final ObjectMapper objectMapper;
    private volatile JsonNode cachedCatalog;

    public CatalogService(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public JsonNode catalog() { return readCatalog(); }

    /** 只接受当前目录中有效且启用的专业 ID。 */
    public MajorInfo requireActiveMajor(String majorId) {
        if (majorId == null || majorId.trim().isEmpty()) throw new IllegalArgumentException("请选择专业");
        JsonNode root = readCatalog();
        for (JsonNode college : root.path("colleges")) {
            if (!college.path("active").asBoolean(false)) continue;
            for (JsonNode major : college.path("majors")) {
                if (majorId.equals(major.path("id").asText()) && major.path("active").asBoolean(false)) {
                    return new MajorInfo(college.path("id").asText(), college.path("name").asText(),
                            major.path("id").asText(), major.path("name").asText());
                }
            }
        }
        throw new IllegalArgumentException("所选专业不存在或已停用，请重新选择");
    }

    /** 查询时允许识别停用的旧分类；空值或目录中未知 ID 显示为“未分类”。 */
    public MajorInfo describeMajor(String majorId) {
        if (majorId == null || majorId.trim().isEmpty()) return MajorInfo.uncategorized();
        JsonNode root = readCatalog();
        for (JsonNode college : root.path("colleges")) {
            for (JsonNode major : college.path("majors")) {
                if (majorId.equals(major.path("id").asText())) {
                    return new MajorInfo(college.path("id").asText(), college.path("name").asText(),
                            major.path("id").asText(), major.path("name").asText());
                }
            }
        }
        return MajorInfo.uncategorized();
    }

    /** 根据分类目录名判断投稿是否属于不区分学院专业的通用资料。 */
    public boolean isGeneralMaterial(String majorId) {
        MajorInfo info = describeMajor(majorId);
        return "通用资料".equals(info.collegeName) || "通用资料".equals(info.majorName);
    }

    private JsonNode readCatalog() {
        JsonNode loaded = cachedCatalog;
        if (loaded != null) return loaded;
        synchronized (this) {
            if (cachedCatalog != null) return cachedCatalog;
            Resource resource = new ClassPathResource("catalog.json");
            if (!resource.exists()) throw new CatalogException("分类文件 catalog.json 不存在；请运行 scripts/generate_catalog.ps1 生成分类数据");
            try (InputStream input = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(input);
                validate(root);
                cachedCatalog = root;
                return root;
            } catch (CatalogException ex) {
                throw ex;
            } catch (IOException ex) {
                throw new CatalogException("无法读取分类文件 catalog.json：" + ex.getMessage(), ex);
            }
        }
    }

    private void validate(JsonNode root) {
        if (root == null || !root.isObject() || root.path("version").asInt(-1) != 1
                || !root.path("colleges").isArray()) {
            throw new CatalogException("分类文件 catalog.json 格式错误：需要 version=1 和 colleges 数组");
        }
        Set<String> ids = new HashSet<String>();
        for (JsonNode college : root.path("colleges")) {
            requireText(college, "学院", "id");
            requireText(college, "学院", "name");
            requireActiveFlag(college, "学院");
            if (!college.path("majors").isArray()) throw new CatalogException("分类文件 catalog.json 格式错误：学院 majors 必须为数组");
            addUnique(ids, college.path("id").asText());
            for (JsonNode major : college.path("majors")) {
                requireText(major, "专业", "id");
                requireText(major, "专业", "name");
                requireActiveFlag(major, "专业");
                addUnique(ids, major.path("id").asText());
            }
        }
    }

    private void requireText(JsonNode node, String type, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().trim().isEmpty()) {
            throw new CatalogException("分类文件 catalog.json 格式错误：" + type + "缺少有效的 " + field);
        }
    }
    private void requireActiveFlag(JsonNode node, String type) {
        if (!node.path("active").isBoolean()) throw new CatalogException("分类文件 catalog.json 格式错误：" + type + "缺少布尔值 active");
    }
    private void addUnique(Set<String> ids, String id) {
        if (!ids.add(id)) throw new CatalogException("分类文件 catalog.json 格式错误：ID 重复 " + id);
    }

    public static class MajorInfo {
        public final String collegeId;
        public final String collegeName;
        public final String majorId;
        public final String majorName;
        public MajorInfo(String collegeId, String collegeName, String majorId, String majorName) {
            this.collegeId = collegeId;
            this.collegeName = collegeName;
            this.majorId = majorId;
            this.majorName = majorName;
        }
        private static MajorInfo uncategorized() { return new MajorInfo(null, "未分类", null, "未分类"); }
    }

    public static class CatalogException extends RuntimeException {
        public CatalogException(String message) { super(message); }
        public CatalogException(String message, Throwable cause) { super(message, cause); }
    }
}
