package com.gis.servelq.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Entities;

import java.text.Normalizer;

public final class RssTextCleaner {

    private RssTextCleaner() {}

    public static String clean(String input) {
        if (input == null) return null;
        if (input.isBlank()) return "";

        String text = input;

        for (int i = 0; i < 5; i++) {
            String decoded = Entities.unescape(text);
            if (decoded.equals(text)) break;
            text = decoded;
        }

        text = Jsoup.parse(text).text();
        text = Normalizer.normalize(text, Normalizer.Form.NFC);
        text = normalizeSpaces(text);
        text = removeInvisibleCharacters(text);
        text = removeControlCharacters(text);
        text = text.replaceAll("[\\p{Z}\\s]+", " ");

        return text.trim();
    }

    private static String normalizeSpaces(String text) {
        return text
                .replace('\u00A0', ' ')
                .replace('\u1680', ' ')
                .replace('\u2000', ' ')
                .replace('\u2001', ' ')
                .replace('\u2002', ' ')
                .replace('\u2003', ' ')
                .replace('\u2004', ' ')
                .replace('\u2005', ' ')
                .replace('\u2006', ' ')
                .replace('\u2007', ' ')
                .replace('\u2008', ' ')
                .replace('\u2009', ' ')
                .replace('\u200A', ' ')
                .replace('\u202F', ' ')
                .replace('\u205F', ' ')
                .replace('\u3000', ' ');
    }

    private static String removeInvisibleCharacters(String text) {
        return text
                .replace("\u200B", "")
                .replace("\u2060", "")
                .replace("\uFEFF", "")
                .replace("\u00AD", "")
                .replace("\u180E", "");
    }

    private static String removeControlCharacters(String text) {
        StringBuilder result = new StringBuilder(text.length());
        text.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint)
                    && codePoint != '\t'
                    && codePoint != '\n'
                    && codePoint != '\r') {
                return;
            }
            result.appendCodePoint(codePoint);
        });
        return result.toString();
    }
}