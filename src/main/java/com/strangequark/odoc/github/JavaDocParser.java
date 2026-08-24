package com.strangequark.odoc.github;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A purposely small, non-executing parser for ordinary Java source. It only
 * reads declarations immediately following JavaDoc comments; unsupported Java
 * syntax receives a clear response instead of invoking a compiler or build.
 */
final class JavaDocParser {
    private static final int MAX_SOURCE_CHARACTERS = 500_000;
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*;");
    private static final Pattern JAVADOC = Pattern.compile("/\\*\\*(.*?)\\*/", Pattern.DOTALL);
    private static final Pattern TYPE = Pattern.compile(
            "(?:public|protected|private|abstract|final|static|sealed|non-sealed|\\s)*(class|interface|enum)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern METHOD = Pattern.compile("([A-Za-z_$][\\w$]*)\\s*\\(([^()]*)\\)");
    private static final Pattern FIELD = Pattern.compile("([A-Za-z_$][\\w$]*)\\s*(?:=|;)");

    private JavaDocParser() {}

    static ParsedJavaDoc parse(String source) {
        if (source == null || source.isBlank() || source.length() > MAX_SOURCE_CHARACTERS) throw unsupported();
        Matcher packageMatcher = PACKAGE.matcher(source);
        String packageName = packageMatcher.find() ? packageMatcher.group(1) : "";
        Matcher comments = JAVADOC.matcher(source);
        String typeName = null;
        String typeKind = null;
        String typeDocumentation = "";
        List<JavaDocMember> members = new ArrayList<>();

        while (comments.find()) {
            String declaration = declarationAfter(source, comments.end());
            Matcher type = TYPE.matcher(declaration);
            if (type.find()) {
                if (typeName == null) {
                    typeKind = type.group(1);
                    typeName = type.group(2);
                    typeDocumentation = comment(comments.group(1)).description();
                }
                continue;
            }
            if (typeName == null) continue;
            JavaDocMember member = member(declaration, typeName, comment(comments.group(1)));
            if (member != null) members.add(member);
        }
        if (typeName == null) throw unsupported();
        return new ParsedJavaDoc(packageName, typeName, typeKind, typeDocumentation, List.copyOf(members));
    }

    private static String declarationAfter(String source, int start) {
        int end = Math.min(source.length(), start + 1_500);
        for (int index = start; index < end; index++) {
            char current = source.charAt(index);
            if (current == '{' || current == ';') {
                end = index + 1;
                break;
            }
        }
        return source.substring(start, end).replaceAll("//.*", "").replaceAll("\\s+", " ").trim();
    }

    private static JavaDocMember member(String declaration, String typeName, Comment comment) {
        Matcher method = METHOD.matcher(declaration);
        if (method.find()) {
            String name = method.group(1);
            return new JavaDocMember(name.equals(typeName) ? "constructor" : "method", name, declaration, comment.description(), comment.tags());
        }
        Matcher field = FIELD.matcher(declaration);
        if (field.find()) {
            return new JavaDocMember("field", field.group(1), declaration, comment.description(), comment.tags());
        }
        return null;
    }

    private static Comment comment(String raw) {
        StringBuilder description = new StringBuilder();
        List<JavaDocTag> tags = new ArrayList<>();
        for (String rawLine : raw.split("\\R")) {
            String line = rawLine.replaceFirst("^\\s*\\*?\\s?", "").trim();
            if (line.isBlank()) continue;
            if (!line.startsWith("@")) {
                if (!description.isEmpty()) description.append(' ');
                description.append(line);
                continue;
            }
            String[] parts = line.substring(1).split("\\s+", 3);
            String kind = parts[0];
            String subject = (kind.equals("param") || kind.equals("throws") || kind.equals("exception")) && parts.length > 1
                    ? parts[1] : "";
            String text = subject.isEmpty()
                    ? line.substring(Math.min(line.length(), kind.length() + 1)).trim()
                    : parts.length > 2 ? parts[2] : "";
            tags.add(new JavaDocTag(kind, subject, text));
        }
        return new Comment(description.toString(), List.copyOf(tags));
    }

    private static ResponseStatusException unsupported() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                "Odoc could not parse an ordinary documented Java type from that source file.");
    }

    private record Comment(String description, List<JavaDocTag> tags) {}
}
