package com.kiminonawa.mydiary.entries.diary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tags of a diary, like #school or #trip.
 * <p>
 * What the person writes: "#School  trip, #fun"
 * What is saved in the database: ",school,trip,fun," (always lower case, no #, no repeated tags)
 * What is shown: "#school #trip #fun"
 */
public class TagHelper {

    /**
     * Text written by the person -> text saved in the database ("" when there are no tags).
     */
    public static String normalize(String input) {
        List<String> tags = new ArrayList<>();
        if (input != null) {
            for (String part : input.split("[\\s,;]+")) {
                String tag = cleanTag(part);
                if (tag.length() > 0 && !tags.contains(tag)) {
                    tags.add(tag);
                }
            }
        }
        if (tags.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(",");
        for (String tag : tags) {
            sb.append(tag).append(",");
        }
        return sb.toString();
    }

    /**
     * Text saved in the database -> list of tags (without #).
     */
    public static List<String> toList(String saved) {
        List<String> tags = new ArrayList<>();
        if (saved != null) {
            for (String part : saved.split(",")) {
                if (part.length() > 0) {
                    tags.add(part);
                }
            }
        }
        return tags;
    }

    /**
     * Text saved in the database -> "#school #trip" ("" when there are no tags).
     */
    public static String toDisplay(String saved) {
        StringBuilder sb = new StringBuilder();
        for (String tag : toList(saved)) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append("#").append(tag);
        }
        return sb.toString();
    }

    private static String cleanTag(String raw) {
        String lower = raw.toLowerCase(Locale.getDefault());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
