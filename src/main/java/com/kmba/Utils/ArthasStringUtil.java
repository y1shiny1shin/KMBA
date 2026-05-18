package com.kmba.Utils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArthasStringUtil {
    public static void print(List<String> message) {
        for (String line : message) {
            System.out.println(line);
        }
    }

    public static int getCntBySc(List<String> message) {
        if (message.size() == 1) return 0;
        String cntString = message.get(message.size() - 1);
        // Affect(row-cnt:2)
        Pattern pattern = Pattern.compile("Affect\\(row-cnt\\:(\\d+)\\)");
        Matcher matcher = pattern.matcher(cntString);

        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        } else {
            return 0;
        }
    }
}
