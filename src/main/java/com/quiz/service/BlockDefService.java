package com.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quiz.common.BizException;
import com.quiz.common.Checks;
import com.quiz.entity.BlockDef;
import com.quiz.entity.dto.ResumeRequests;
import com.quiz.mapper.BlockDefMapper;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockDefService {
    private final BlockDefMapper mapper;
    private final ObjectMapper json;

    public BlockDefService(BlockDefMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    public List<BlockDef> list(boolean enabledOnly) { return mapper.list(enabledOnly); }

    public BlockDef require(Long id) {
        BlockDef definition = mapper.findById(id);
        if (definition == null) throw new BizException(404, "分块定义不存在");
        return definition;
    }

    @Transactional
    public BlockDef create(ResumeRequests.Definition request) {
        mapper.lockDefinitions();
        if (mapper.countActive() >= 20) throw new BizException(1006, "分块定义最多 20 个");
        Checks.require(request.code != null && request.code.matches("[A-Za-z][A-Za-z0-9_]{0,49}"), "分块编码需以字母开头，仅含字母、数字和下划线");
        if (mapper.findByCode(request.code) != null) throw new BizException(400, "分块编码已存在");
        BlockDef definition = new BlockDef();
        definition.code = request.code;
        copy(request, definition);
        definition.builtIn = 0;
        definition.status = 1;
        mapper.insert(definition);
        return definition;
    }

    @Transactional
    public BlockDef update(Long id, ResumeRequests.Definition request) {
        mapper.lockDefinitions();
        BlockDef definition = require(id);
        if (Integer.valueOf(0).equals(request.repeatable) && mapper.countDuplicateSingle(id) > 0)
            throw new BizException(400, "已有简历包含多条该分块，不能改为单条分块");
        copy(request, definition);
        mapper.update(definition);
        return definition;
    }

    @Transactional
    public void delete(Long id) {
        mapper.lockDefinitions();
        BlockDef definition = require(id);
        if (Integer.valueOf(1).equals(definition.builtIn)) throw new BizException(403, "预置分块不能删除，可停用");
        if (mapper.countReferences(id) > 0) throw new BizException(1007, "该分块已有简历内容使用，无法删除，可停用");
        mapper.softDelete(id);
    }

    private void copy(ResumeRequests.Definition request, BlockDef definition) {
        JsonNode schema = normalize(request.fieldSchema);
        validateSchema(schema);
        definition.name = request.name.trim();
        definition.fieldSchema = serialize(schema);
        definition.repeatable = request.repeatable;
        definition.enabled = request.enabled;
        definition.sort = request.sort == null ? 0 : request.sort;
    }

    public JsonNode normalize(JsonNode node) {
        if (node != null && node.isTextual()) {
            try { return json.readTree(node.textValue()); }
            catch (JsonProcessingException ex) { throw new BizException(400, "JSON 格式不正确"); }
        }
        return node;
    }

    public void validateSchema(JsonNode schema) {
        Checks.require(schema != null && schema.isArray() && schema.size() > 0 && schema.size() <= 50, "分块需配置 1～50 个字段");
        Set<String> keys = new HashSet<>();
        for (JsonNode field : schema) {
            Checks.require(field.isObject(), "字段定义必须为对象");
            String key = field.path("key").asText("");
            String label = field.path("label").asText("");
            Checks.require(field.path("key").isTextual() && !key.trim().isEmpty() && key.length() <= 50 && keys.add(key), "字段 key 需非空且唯一，最多 50 字");
            Checks.require(field.path("label").isTextual() && !label.trim().isEmpty() && label.length() <= 50, "字段名称需为 1～50 字");
            Checks.require(Arrays.asList("text", "textarea", "date", "list").contains(field.path("type").asText()), "字段类型仅支持 text、textarea、date、list");
            Checks.require(field.path("required").isBoolean(), "字段 required 必须为布尔值");
        }
    }

    public String validateContent(BlockDef definition, JsonNode requestedContent) {
        JsonNode content = normalize(requestedContent);
        JsonNode schema;
        try { schema = json.readTree(definition.fieldSchema); }
        catch (JsonProcessingException ex) { throw new BizException(500, "分块定义数据异常"); }
        Checks.require(content != null && content.isObject() && content.size() == schema.size(), "请按分块字段要求填写：字段必须与模板一一对应");
        for (JsonNode field : schema) {
            String key = field.path("key").asText();
            String label = field.path("label").asText();
            JsonNode value = content.get(key);
            Checks.require(value != null && !value.isNull(), "请填写字段：" + label);
            boolean required = field.path("required").asBoolean();
            String type = field.path("type").asText();
            if ("list".equals(type)) {
                Checks.require(value.isArray() && value.size() <= 30, label + "需为列表，最多 30 条");
                Checks.require(!required || value.size() > 0, label + "不能为空");
                for (JsonNode item : value) {
                    Checks.require(item.isTextual() && item.textValue().length() <= 200, label + "每条需为最多 200 字的文本");
                    Checks.require(!required || !item.textValue().trim().isEmpty(), label + "不能含空白条目");
                }
            } else {
                Checks.require(value.isTextual(), label + "必须为文本");
                String text = value.textValue();
                Checks.require(!required || !text.trim().isEmpty(), label + "不能为空");
                int max = "textarea".equals(type) ? 2000 : 200;
                Checks.require(text.length() <= max, label + "最多 " + max + " 字");
                if ("date".equals(type) && !text.isEmpty()) validateDate(text, label);
            }
        }
        return serialize(content);
    }

    private void validateDate(String text, String label) {
        try {
            if (text.matches("\\d{4}-\\d{2}")) YearMonth.parse(text);
            else if (text.matches("\\d{4}-\\d{2}-\\d{2}")) LocalDate.parse(text);
            else throw new DateTimeParseException("format", text, 0);
        } catch (DateTimeParseException ex) { throw new BizException(400, label + "需为有效日期 YYYY-MM 或 YYYY-MM-DD"); }
    }

    private String serialize(JsonNode node) {
        try { return json.writeValueAsString(node); }
        catch (JsonProcessingException ex) { throw new BizException(400, "JSON 格式不正确"); }
    }
}
