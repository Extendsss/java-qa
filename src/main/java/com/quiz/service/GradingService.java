package com.quiz.service;

import java.util.Locale;
import java.util.stream.Collectors;

public final class GradingService {
    private GradingService() { }
    public static String normalizeMulti(String value) {
        return value.trim().toUpperCase(Locale.ROOT).chars().distinct().sorted()
                .mapToObj(c->String.valueOf((char)c)).collect(Collectors.joining());
    }
    public static boolean judge(String type,String standard,String submitted) {
        if(submitted==null || submitted.trim().isEmpty()) return false;
        switch(type) {
            case "SINGLE": case "JUDGE": return standard.trim().equalsIgnoreCase(submitted.trim());
            case "MULTI": return normalizeMulti(standard).equals(normalizeMulti(submitted));
            case "FILL":
                String[] expected=standard.split("\\|\\|",-1), actual=submitted.split("\\|\\|",-1);
                if(expected.length!=actual.length) return false;
                for(int i=0;i<expected.length;i++) if(!expected[i].trim().equalsIgnoreCase(actual[i].trim())) return false;
                return true;
            default: return false;
        }
    }
}
