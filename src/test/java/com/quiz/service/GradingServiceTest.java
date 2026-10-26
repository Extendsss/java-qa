package com.quiz.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GradingServiceTest {
    @Test void multiDeduplicatesAndSortsButRejectsExtraOrMissingChoices() {
        assertTrue(GradingService.judge("MULTI","ACD","dCAAC"));
        assertFalse(GradingService.judge("MULTI","ACD","AC"));
        assertFalse(GradingService.judge("MULTI","ACD","ABCD"));
    }
    @Test void fillComparesEachTrimmedCaseInsensitiveBlankAndPreservesTrailingBlanks() {
        assertTrue(GradingService.judge("FILL","Process||Thread"," process || THREAD "));
        assertFalse(GradingService.judge("FILL","Process||Thread","Thread||Process"));
        assertFalse(GradingService.judge("FILL","Process","Process||"));
    }
    @Test void objectiveAnswersHandleBlankAndCaseAndQaNeverAutoGrades() {
        assertTrue(GradingService.judge("SINGLE","A"," a "));
        assertTrue(GradingService.judge("JUDGE","T","t"));
        assertFalse(GradingService.judge("SINGLE","A",null));
        assertFalse(GradingService.judge("QA","参考答案","参考答案"));
    }
}
