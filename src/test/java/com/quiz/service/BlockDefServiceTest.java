package com.quiz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quiz.common.BizException;
import com.quiz.entity.BlockDef;
import com.quiz.entity.dto.ResumeRequests;
import com.quiz.mapper.BlockDefMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlockDefServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private BlockDefMapper mapper;
    private BlockDefService service;
    private BlockDef definition;

    @BeforeEach void setup() {
        mapper = mock(BlockDefMapper.class);
        service = new BlockDefService(mapper, json);
        definition = new BlockDef();
        definition.fieldSchema = "[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"text\",\"required\":true},{\"key\":\"date\",\"label\":\"日期\",\"type\":\"date\",\"required\":false},{\"key\":\"items\",\"label\":\"详情\",\"type\":\"list\",\"required\":false}]";
    }

    private JsonNode node(String text) throws Exception { return json.readTree(text); }

    @Test void acceptsValidStructuredContentAndMonthDates() throws Exception {
        String result = service.validateContent(definition, node("{\"name\":\"张三\",\"date\":\"2026-10\",\"items\":[\"实现鉴权\"]}"));
        assertEquals("张三", json.readTree(result).get("name").asText());
    }

    @Test void rejectsAdditionalAndMissingKeys() throws Exception {
        assertThrows(BizException.class, () -> service.validateContent(definition, node("{\"name\":\"张三\",\"date\":\"\",\"items\":[],\"extra\":\"x\"}")));
        assertThrows(BizException.class, () -> service.validateContent(definition, node("{\"name\":\"张三\",\"date\":\"\"}")));
        assertThrows(BizException.class, () -> service.validateContent(definition, node("{\"name\":\"张三\",\"date\":\"\",\"other\":[]}")));
    }

    @Test void rejectsWhitespaceRequiredValuesAndTypeMismatch() throws Exception {
        assertThrows(BizException.class, () -> service.validateContent(definition, node("{\"name\":\"  \",\"date\":\"\",\"items\":[]}")));
        assertThrows(BizException.class, () -> service.validateContent(definition, node("{\"name\":123,\"date\":\"\",\"items\":[]}")));
        assertThrows(BizException.class, () -> service.validateContent(definition, node("{\"name\":\"张三\",\"date\":\"\",\"items\":[1]}")));
    }

    @Test void rejectsImpossibleCalendarDates() throws Exception {
        assertThrows(BizException.class, () -> service.validateContent(definition, node("{\"name\":\"张三\",\"date\":\"2026-02-30\",\"items\":[]}")));
        assertThrows(BizException.class, () -> service.validateContent(definition, node("{\"name\":\"张三\",\"date\":\"2026-13\",\"items\":[]}")));
    }

    @Test void rejectsOversizedLists() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode content = (com.fasterxml.jackson.databind.node.ObjectNode) node("{\"name\":\"张三\",\"date\":\"\",\"items\":[]}");
        com.fasterxml.jackson.databind.node.ArrayNode list = (com.fasterxml.jackson.databind.node.ArrayNode) content.get("items");
        for (int i=0; i<31; i++) list.add("条目");
        assertThrows(BizException.class, () -> service.validateContent(definition, content));
    }

    @Test void schemaRejectsDuplicateKeysAndUnsupportedTypes() throws Exception {
        assertThrows(BizException.class, () -> service.validateSchema(node("[{\"key\":\"a\",\"label\":\"A\",\"type\":\"text\",\"required\":true},{\"key\":\"a\",\"label\":\"B\",\"type\":\"text\",\"required\":false}]")));
        assertThrows(BizException.class, () -> service.validateSchema(node("[{\"key\":\"a\",\"label\":\"A\",\"type\":\"file\",\"required\":true}]")));
    }

    @Test void enforcesTemplateCapacityAndProtectsBuiltIns() {
        when(mapper.countActive()).thenReturn(20L);
        assertThrows(BizException.class, () -> service.create(new ResumeRequests.Definition()));
        definition.id = 1L;
        definition.builtIn = 1;
        when(mapper.findById(1L)).thenReturn(definition);
        assertThrows(BizException.class, () -> service.delete(1L));
        verify(mapper, never()).softDelete(anyLong());
    }

    @Test void refusesDeletionOfReferencedDefinitions() {
        definition.id = 1L;
        definition.builtIn = 0;
        when(mapper.findById(1L)).thenReturn(definition);
        when(mapper.countReferences(1L)).thenReturn(1L);
        assertThrows(BizException.class, () -> service.delete(1L));
        verify(mapper, never()).softDelete(anyLong());
    }
}
