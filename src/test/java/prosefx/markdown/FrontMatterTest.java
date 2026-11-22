package prosefx.markdown;

import com.atlassian.prosemirror.model.Node;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static prosefx.core.commands.Commands.*;
import static prosefx.core.pm.Pm.attr;
import static prosefx.core.schema.EditorSchema.*;

/** YAML front matter: `---`, key lines, `---` on the very first lines of a file is one verbatim block, not a rule and a heading. */
class FrontMatterTest {

    static boolean isFront(Node n) { return n.getType() == PARAGRAPH && Boolean.TRUE.equals(attr(n, "verbatim")) && n.getTextContent().startsWith("---"); }

    static String fixture() { return LosslessTest.fixture("frontmatter.md"); }

    @Test void aFileWithFrontMatterComesBackByteForByte() {
        var md = fixture();
        assertEquals(md, LosslessTest.roundTrip(md));
    }

    @Test void theBlockAtTheTopIsTheWholeFrontMatterAndNothingElse() {
        var doc = Lossless.read(fixture());
        var first = doc.getFirstChild();
        assertTrue(isFront(first), "first block is verbatim front matter");
        assertTrue(first.getTextContent().startsWith("---\nnumber: 1"), first.getTextContent());
        assertTrue(first.getTextContent().endsWith("'d']\n---"), first.getTextContent());
        // the heading after it is a heading again, not swallowed into a setext heading by the closing line
        assertEquals(HEADING, doc.child(1).getType());
        assertFalse(isFront(doc.child(doc.getChildCount() - 1)), "the later rule is not front matter");
    }

    @Test void theLongLineIsKeptWholeAndTheQuotesAsWritten() {
        var md = fixture();
        var out = LosslessTest.roundTrip(md);
        assertTrue(out.contains("number_b34: \"0001\"\n"));
        assertTrue(out.contains("tags: [a, \"b: c\", 'd']\n"));
        assertEquals(1, out.lines().filter(l -> l.startsWith("conventions: ")).count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"---\nk: v\n---\n", "---\nk: v\n---", "---\n---\n", "---\n# comment\nk: v\n...\n\ntext\n", "---  \nk: v\n---  \n\ntext\n",
            "---\nk: v\n---\n# heading right after\n", "---\r\nk: v\r\n---\r\n\r\ntext\r\n", "﻿---\nk: v\n---\n\ntext\n", "---\nk:\n  - a\n  - b\n---\n"})
    void frontMatterVariantsComeBackAndAreRecognised(String md) {
        assertEquals(md, LosslessTest.roundTrip(md));
        assertTrue(isFront(Lossless.read(md).getFirstChild()), md);
    }

    @ParameterizedTest
    @ValueSource(strings = {"---\n\nA rule, then text, then a rule\n\n---\n", "---\nno closing line\n", "\n---\nk: v\n---\n", " ---\nk: v\n---\n", "----\nk: v\n---\n",
            "text\n\n---\nk: v\n---\n", "---\n\n---\n",
            "text\n\n---\nk: v \n---\n\nmore  \n", "# T\n\n- item\n\n---\n# comment \nk: *v*\n---\n"})
    void whatIsNotFrontMatterStaysAsItWas(String md) {
        assertEquals(md, LosslessTest.roundTrip(md));
        assertFalse(isFront(Lossless.read(md).getFirstChild()), md);
    }

    @Test void typingInTheBlockChangesOnlyThatLine() {
        var md = fixture();
        var m = LosslessTest.after(md, "short_name: example");
        m.type("2");
        var out = Lossless.write(m.state().getDoc());
        assertEquals(List.of("5: short_name: example  ->  short_name: example2"), LosslessTest.changedLines(md, out));
    }

    @Test void deletingInTheBlockChangesOnlyThatLine() {
        var md = fixture();
        var m = LosslessTest.after(md, "code3: co");
        m.run(backspace);
        var out = Lossless.write(m.state().getDoc());
        assertEquals(List.of("4: code3: cos  ->  code3: cs"), LosslessTest.changedLines(md, out));
    }

    @Test void markupCharactersTypedInTheBlockAreNotEscaped() {
        var md = fixture();
        var m = LosslessTest.after(md, "name: example");
        m.type(" *x* _y_ <z>");
        var out = Lossless.write(m.state().getDoc());
        assertEquals(List.of("3: name: example  ->  name: example *x* _y_ <z>"), LosslessTest.changedLines(md, out));
    }

    @Test void enterAtTheEndOfTheClosingLineLeavesTheBlock() {
        var md = "---\nk: v\n---\n\ntext\n";
        var m = new prosefx.core.editor.EditorModel(Lossless.read(md));
        var front = m.state().getDoc().getFirstChild();
        m.select(prosefx.core.pm.Pm.cursor(m.state().getDoc(), front.getNodeSize() - 1));
        m.run(enter);
        m.type("body");
        var doc = m.state().getDoc();
        assertTrue(isFront(doc.getFirstChild()));
        assertEquals("---\nk: v\n---", Lossless.write(doc).substring(0, 12));
        assertFalse(doc.getFirstChild().getTextContent().contains("body"));
        assertTrue(Lossless.write(doc).contains("body"));
    }

    @Test void aLineAddedWithEnterIsAKeyLineOfTheSameBlock() {
        var md = "---\nk: v\n---\n\ntext\n";
        var m = LosslessTest.after(md, "k: v");
        m.run(enter);
        m.type("added: yes");
        var out = Lossless.write(m.state().getDoc());
        assertEquals("---\nk: v\nadded: yes\n---\n\ntext\n", out);
        assertTrue(isFront(Lossless.read(out).getFirstChild()));
    }
}
