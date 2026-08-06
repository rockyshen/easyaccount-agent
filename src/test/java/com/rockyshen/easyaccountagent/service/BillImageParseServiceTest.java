package com.rockyshen.easyaccountagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockyshen.easyaccountagent.config.BillParseProperties;
import com.rockyshen.easyaccountagent.constant.ContentValues;
import com.rockyshen.easyaccountagent.dto.BillParseItemDto;
import com.rockyshen.easyaccountagent.dto.BillParseResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillImageParseServiceTest {

    @Mock
    private ChatModel qwenVlChatModel;

    private BillImageParseService service;
    private BillParseProperties properties;

    @BeforeEach
    void setUp() {
        properties = new BillParseProperties();
        properties.setVlModel("qwen-vl-plus");
        properties.setMaxBytes(1024 * 1024);
        service = new BillImageParseService(qwenVlChatModel, new ObjectMapper(), properties);
    }

    @Test
    void parseImage_success_normalizesFields() {
        String vlJson = """
                {
                  "items": [
                    {
                      "handle": 1,
                      "money": "¥23",
                      "date": "2026年8月4日",
                      "merchant": "便利店",
                      "accountNameHint": "招行信用卡",
                      "accountToNameHint": "",
                      "typeNameHint": "餐饮",
                      "note": "早餐",
                      "confidence": 0.91
                    }
                  ]
                }
                """;
        stubVl(vlJson);

        BillParseResultDto result = service.parseImage(new byte[] {1, 2, 3}, "image/jpeg");

        assertEquals("image/jpeg", result.getSourceMimeType());
        assertEquals("qwen-vl-plus", result.getModel());
        assertEquals(1, result.getItems().size());
        BillParseItemDto item = result.getItems().get(0);
        assertEquals(ContentValues.ACTION_SUB, item.getHandle());
        assertEquals("23.00", item.getMoney());
        assertEquals("2026-08-04", item.getDate());
        assertEquals("便利店", item.getMerchant());
        assertEquals("招行信用卡", item.getAccountNameHint());
        assertEquals("餐饮", item.getTypeNameHint());
        assertEquals(0.91, item.getConfidence());

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(qwenVlChatModel).call(promptCaptor.capture());
        assertFalse(promptCaptor.getValue().getInstructions().isEmpty());
        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        String promptText = promptCaptor.getValue().getInstructions().get(0).getText();
        assertTrue(promptText.contains("当前日期：" + today), promptText);
    }

    @Test
    void parseAndNormalizeItems_resolvesRelativeChineseDates() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        List<BillParseItemDto> items = service.parseAndNormalizeItems("""
                {
                  "items": [
                    {"handle":1,"money":"10","date":"今天"},
                    {"handle":1,"money":"11","date":"昨天"},
                    {"handle":1,"money":"12","date":"前天"},
                    {"handle":1,"money":"13","date":"今日 14:30"},
                    {"handle":1,"money":"14","date":"昨日晚上"}
                  ]
                }
                """);
        assertEquals(5, items.size());
        assertEquals(today.format(fmt), items.get(0).getDate());
        assertEquals(today.minusDays(1).format(fmt), items.get(1).getDate());
        assertEquals(today.minusDays(2).format(fmt), items.get(2).getDate());
        assertEquals(today.format(fmt), items.get(3).getDate());
        assertEquals(today.minusDays(1).format(fmt), items.get(4).getDate());
    }

    @Test
    void resolveRelativeDate_prefersLongerMatch() {
        LocalDate today = LocalDate.of(2026, 8, 6);
        assertEquals(today.minusDays(3), BillImageParseService.resolveRelativeDate("大前天", today));
        assertEquals(today.minusDays(2), BillImageParseService.resolveRelativeDate("前天", today));
        assertNull(BillImageParseService.resolveRelativeDate("未知", today));
        assertNull(BillImageParseService.resolveRelativeDate("", today));
    }

    @Test
    void parseImage_acceptsMarkdownFencedJson() {
        stubVl("""
                ```json
                {"items":[{"handle":0,"money":"100.5","date":"2026-08-01","merchant":"工资"}]}
                ```
                """);

        BillParseResultDto result = service.parseImage(new byte[] {9}, "image/png");
        assertEquals(1, result.getItems().size());
        assertEquals(ContentValues.ACTION_ADD, result.getItems().get(0).getHandle());
        assertEquals("100.50", result.getItems().get(0).getMoney());
        assertEquals("2026-08-01", result.getItems().get(0).getDate());
    }

    @Test
    void parseAndNormalizeItems_skipsMissingMoney() {
        List<BillParseItemDto> items = service.parseAndNormalizeItems(
                "{\"items\":[{\"handle\":1,\"merchant\":\"无金额\"},{\"money\":\"12\",\"handle\":1}]}");
        assertEquals(1, items.size());
        assertEquals("12.00", items.get(0).getMoney());
    }

    @Test
    void parseImage_rejectsUnsupportedMime() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.parseImage(new byte[] {1}, "application/pdf"));
        assertTrue(ex.getMessage().contains("仅支持图片"));
    }

    @Test
    void parseImage_rejectsEmptyBytes() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.parseImage(new byte[0], "image/jpeg"));
        assertEquals("图片内容不能为空", ex.getMessage());
    }

    @Test
    void parseImage_rejectsTooLarge() {
        properties.setMaxBytes(3);
        byte[] big = "abcd".getBytes(StandardCharsets.UTF_8);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.parseImage(big, "image/jpeg"));
        assertTrue(ex.getMessage().contains("图片过大"));
    }

    private void stubVl(String text) {
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        when(qwenVlChatModel.call(any(Prompt.class))).thenReturn(response);
    }
}
