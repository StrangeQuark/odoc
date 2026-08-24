package com.strangequark.odoc.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class JavaDocParserTest {
    @Test
    void extractsAnOrdinaryTypeAndItsDocumentedMembersWithoutExecutingSource() {
        ParsedJavaDoc parsed = JavaDocParser.parse("""
                package example.docs;

                /** A small guide type. */
                public class Guide {
                    /**
                     * Adds a named item.
                     * @param name visible name
                     * @return a stable identifier
                     * @throws IllegalArgumentException if name is blank
                     */
                    public String add(String name) { return name; }

                    /** Current title. */
                    private String title;
                }
                """);

        assertThat(parsed.packageName()).isEqualTo("example.docs");
        assertThat(parsed.typeKind()).isEqualTo("class");
        assertThat(parsed.typeName()).isEqualTo("Guide");
        assertThat(parsed.documentation()).isEqualTo("A small guide type.");
        assertThat(parsed.members()).extracting(JavaDocMember::name).containsExactly("add", "title");
        assertThat(parsed.members().getFirst().tags()).contains(
                new JavaDocTag("param", "name", "visible name"),
                new JavaDocTag("return", "", "a stable identifier"),
                new JavaDocTag("throws", "IllegalArgumentException", "if name is blank"));
    }

    @Test
    void rejectsUnsupportedOrOversizedSourcesWithoutTryingToCompileThem() {
        assertThatThrownBy(() -> JavaDocParser.parse("public class NoDocumentation {}"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("could not parse");
        assertThatThrownBy(() -> JavaDocParser.parse("x".repeat(500_001)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("could not parse");
    }
}
