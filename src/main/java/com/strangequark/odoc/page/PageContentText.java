package com.strangequark.odoc.page;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Extracts only visible text from a Tiptap JSON document for server-side search. */
final class PageContentText {
    private static final ObjectMapper JSON = new ObjectMapper();

    private PageContentText() {}

    static String from(String content) {
        if (content == null || content.isBlank()) return "";
        try {
            JsonNode root = JSON.readTree(content);
            StringBuilder text = new StringBuilder();
            append(root, text);
            return text.toString().replaceAll("\\s+", " ").trim();
        } catch (Exception ignored) {
            // Keep early Markdown/plain-text pages searchable while the client
            // migrates each page to its canonical Tiptap JSON representation.
            return content.replaceAll("[`*_>#]", " ").replaceAll("\\s+", " ").trim();
        }
    }

    private static void append(JsonNode node, StringBuilder text) {
        if (node.isObject()) {
            JsonNode value = node.get("text");
            if (value != null && value.isTextual()) text.append(value.asText()).append(' ');
            // Odoc stores editor documents in a versioned envelope. Traverse
            // the payload itself before the regular ProseMirror `content`
            // array so rich-document pages remain searchable after saving.
            JsonNode document = node.get("document");
            if (document != null) append(document, text);
            JsonNode children = node.get("content");
            if (children != null) append(children, text);
            return;
        }
        if (node.isArray()) for (JsonNode child : node) append(child, text);
    }
}
