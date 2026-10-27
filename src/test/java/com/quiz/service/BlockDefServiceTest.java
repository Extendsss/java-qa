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

    private BlockDef dateDefinition(String key, String label, boolean required) {
        BlockDef dates = new BlockDef();
        com.fasterxml.jackson.databind.node.ArrayNode schema = json.createArrayNode();
        schema.addObject().put("key", key).put("label", label).put("type", "date").put("required", required);
        dates.fieldSchema = schema.toString();
        return dates;
    }

    @Test void acceptsPresentForEndTimeByKeyOrLabelIncludingRequiredFields() throws Exception {
        for (boolean required : new boolean[] {false, true}) {
            BlockDef byKey = dateDefinition("结束时间", "结束月份", required);
            String byKeyResult = service.validateContent(byKey, json.createObjectNode().put("结束时间", "至今"));
            assertEquals("至今", json.readTree(byKeyResult).path("结束时间").asText());

            BlockDef byLabel = dateDefinition("end", "结束时间", required);
            String byLabelResult = service.validateContent(byLabel, json.createObjectNode().put("end", "至今"));
            assertEquals("至今", json.readTree(byLabelResult).path("end").asText());
        }
    }

    @Test void rejectsPresentForStartTimeAndUnrelatedDates() {
        for (String label : new String[] {"开始时间", "日期"}) {
            BlockDef dates = dateDefinition("date", label, false);
            BizException error = assertThrows(BizException.class,
                    () -> service.validateContent(dates, json.createObjectNode().put("date", "至今")));
            assertEquals(400, error.code);
        }
    }

    @Test void endTimePreservesMonthsLegacyDatesAndOptionalEmptyValues() throws Exception {
        BlockDef dates = dateDefinition("end", "结束时间", false);
        for (String value : new String[] {"2026-10", "2024-02-29", ""}) {
            String result = service.validateContent(dates, json.createObjectNode().put("end", value));
            assertEquals(value, json.readTree(result).path("end").asText());
        }
        for (String value : new String[] {"2026-13", "2026-02-30", "目前"}) {
            BizException error = assertThrows(BizException.class,
                    () -> service.validateContent(dates, json.createObjectNode().put("end", value)));
            assertTrue(error.getMessage().contains("至今"));
        }
        BlockDef requiredDate = dateDefinition("end", "结束时间", true);
        assertThrows(BizException.class,
                () -> service.validateContent(requiredDate, json.createObjectNode().put("end", "")));
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

    private String textOfLength(int length) {
        return String.join("", java.util.Collections.nCopies(length, "字"));
    }

    @Test void acceptsThirtyListItemsOfTwoHundredCharactersEach() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode content = (com.fasterxml.jackson.databind.node.ObjectNode) node("{\"name\":\"张三\",\"date\":\"\",\"items\":[]}");
        com.fasterxml.jackson.databind.node.ArrayNode list = (com.fasterxml.jackson.databind.node.ArrayNode) content.get("items");
        for (int i=0; i<30; i++) list.add(textOfLength(200));

        JsonNode savedItems = json.readTree(service.validateContent(definition, content)).path("items");
        assertEquals(30, savedItems.size());
        int totalCharacters = 0;
        for (JsonNode item : savedItems) {
            assertEquals(200, item.asText().length());
            totalCharacters += item.asText().length();
        }
        assertEquals(6000, totalCharacters);
    }

    @Test void rejectsMoreThanThirtyItemsEvenBelowTwoHundredCharactersTotal() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode content = (com.fasterxml.jackson.databind.node.ObjectNode) node("{\"name\":\"张三\",\"date\":\"\",\"items\":[]}");
        com.fasterxml.jackson.databind.node.ArrayNode list = (com.fasterxml.jackson.databind.node.ArrayNode) content.get("items");
        for (int i=0; i<31; i++) list.add("字");
        BizException error = assertThrows(BizException.class, () -> service.validateContent(definition, content));
        assertTrue(error.getMessage().contains("最多 30 条"));
    }

    @Test void rejectsOneItemOverTwoHundredCharactersEvenBelowListCapacity() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode content = (com.fasterxml.jackson.databind.node.ObjectNode) node("{\"name\":\"张三\",\"date\":\"\",\"items\":[]}");
        ((com.fasterxml.jackson.databind.node.ArrayNode) content.get("items")).add(textOfLength(201));
        BizException error = assertThrows(BizException.class, () -> service.validateContent(definition, content));
        assertTrue(error.getMessage().contains("每条需为最多 200 字"));
    }

    @Test void acceptsAnEmptyOptionalList() throws Exception {
        JsonNode content = node("{\"name\":\"张三\",\"date\":\"\",\"items\":[]}");
        JsonNode savedItems = json.readTree(service.validateContent(definition, content)).path("items");
        assertTrue(savedItems.isArray());
        assertEquals(0, savedItems.size());
    }

    @Test void textareaAcceptsTwoThousandCharactersAndRejectsOneMore() throws Exception {
        BlockDef textarea = new BlockDef();
        textarea.fieldSchema = "[{\"key\":\"description\",\"label\":\"简介\",\"type\":\"textarea\",\"required\":false}]";
        com.fasterxml.jackson.databind.node.ObjectNode content = json.createObjectNode().put("description", textOfLength(2000));
        String saved = service.validateContent(textarea, content);
        assertEquals(2000, json.readTree(saved).path("description").asText().length());

        content.put("description", textOfLength(2001));
        BizException error = assertThrows(BizException.class, () -> service.validateContent(textarea, content));
        assertTrue(error.getMessage().contains("最多 2000 字"));
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
