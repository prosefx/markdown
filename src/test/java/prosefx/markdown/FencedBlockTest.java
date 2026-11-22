package prosefx.markdown;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import prosefx.core.editor.EditorModel;
import prosefx.core.pm.Pm;

import static org.junit.jupiter.api.Assertions.*;

/** PRP 0011: fenced code blocks (triple backticks) are read, typed in and written back as they are, at top level and inside list items. */
class FencedBlockTest {

    static EditorModel open(String md) { return new EditorModel(Lossless.read(md)); }

    static String write(EditorModel m) { return Lossless.write(m.state().getDoc()); }

    /** The caret goes after the first occurrence of `after` in the text of the document, where the text is found by walking its text nodes. */
    static void caretAfter(EditorModel m, String after) {
        var found = new int[]{-1};
        var doc = m.state().getDoc();
        doc.descendants((node, pos, parent, index) -> {
            if (found[0] < 0 && node.isText() && node.getText().contains(after)) found[0] = pos + node.getText().indexOf(after) + after.length();
            return true;
        });
        assertTrue(found[0] >= 0, "no text " + after);
        m.select(Pm.cursor(doc, found[0]));
    }

    static void type(EditorModel m, String keys) { SpansTest.type(m, keys); }

    // ---- 1. with and without an info string, at top level and inside a list item, space or tab indented

    @ParameterizedTest
    @ValueSource(strings = {
            "```\ncode\n```\n",
            "```java\nint x;\n```\n",
            "```java title=\"x\"\nint x;\n```\n",
            "~~~\ncode\n~~~\n",
            "- item\n  ```\n  code\n  ```\n",
            "- item\n  ```java\n  code\n  ```\n",
            "- item\n\t```\n\tcode\n\t```\n",
            "- item\n\t```java\n\tcode\n\t```\n",
            "- item\n\n\t```\n\tcode\n\t```\n- next\n",
            "- item\n\t- sub\n\t\t```\n\t\tcode\n\t\t```\n",
            "- item\n\t- sub\n\t  ```\n\t  code\n\t  ```\n",
            "1. step\n   ```sh\n   ls\n   ```\n2. next\n",
            "- The user's instruction (2026-11-19, verbatim)\n\t```\n\tmake adf open in notepadfx\n\t```\n- Context\n\t- more\n",
            "- a\n\t```\n\tcode\n\t```\t\n- b\n",
            "before\n```\ncode\n```\nafter\n",
            "```\n```\n",
            "```\ncode\n",
            "- item\n  ```\n  code\n"})
    void aFenceComesBackByteForByteAndIsOneBlock(String md) {
        assertEquals(md, Lossless.write(Lossless.read(md)));
    }

    @Test void aTopLevelFenceIsOneBlockWithItsInfoStringOnTheFirstLine() {
        var doc = Lossless.read("a\n\n```java\nint x;\n```\n\nb\n");
        assertEquals(3, doc.getChildCount());
        assertTrue(Boolean.TRUE.equals(Pm.attr(doc.child(1), "verbatim")));
        assertEquals("```java\nint x;\n```", doc.child(1).textBetween(0, doc.child(1).getContent().getSize(), null, "\n"));
    }

    @Test void aTabIndentedFenceInAnItemStaysInTheItem() {
        var doc = Lossless.read("- item\n\t```\n\tcode\n\t```\n- next\n");
        assertEquals(1, doc.getChildCount());
        assertEquals(2, doc.getFirstChild().getChildCount());
        assertEquals(2, doc.getFirstChild().getFirstChild().getChildCount());
    }

    // ---- 2. markdown-looking lines inside, and a shorter fence inside a longer one

    @ParameterizedTest
    @ValueSource(strings = {
            "```\n# heading\n- item\n>> comment\n---\n> quote\n1. one\n| a | b |\n```\n",
            "````\n```\ninner\n```\nmore\n````\n",
            "````md\n```java\nint x;\n```\n````\n",
            "~~~\n```\ninner\n```\n~~~\n",
            "```\n~~~\nnot a close\n```\n",
            "- item\n\t````\n\t```\n\tinner\n\t```\n\t````\n- next\n",
            "```\n\n\na\n\n```\n",
            "```\nline with trailing  \n\ttabbed\t\n   spaces\n```\n",
            "```  \ncode\n```  \n",
            "para\n\n```\n- not a list\n```\n\n- list\n"})
    void whatLooksLikeMarkdownInsideStaysCode(String md) {
        assertEquals(md, Lossless.write(Lossless.read(md)));
    }

    @Test void aShorterFenceInsideALongerOneDoesNotCloseIt() {
        var doc = Lossless.read("````\n```\ninner\n```\nmore\n````\n\nafter\n");
        assertEquals(2, doc.getChildCount());
    }

    // ---- 4. long lines are not wrapped

    @Test void aLongLineInsideABlockIsOneLineOnSave() {
        var long1 = "word ".repeat(200).strip();
        var md = "```\n" + long1 + "\n```\n";
        assertEquals(md, Lossless.write(Lossless.read(md)));
        var m = open(md);
        caretAfter(m, "word word");
        type(m, "X");
        assertEquals(1, write(m).lines().filter(l -> l.length() > 500).count());
        assertEquals(3, write(m).lines().count());
    }

    @Test void aLongLineInAnItemsBlockIsOneLineOnSave() {
        var md = "- item\n\t```\n\t" + "word ".repeat(200).strip() + "\n\t```\n";
        assertEquals(md, Lossless.write(Lossless.read(md)));
        var m = open(md);
        caretAfter(m, "word word");
        type(m, "X");
        assertEquals(4, write(m).lines().count());
    }

    // ---- 5. editing outside the block leaves the block's bytes alone

    static final String[] BLOCKS = {
            "```\n# h\n- i\n>> c\n---\n  indented\n\ttabbed\n```",
            "```java\nint x = 1;\n    y();\n```",
            "````\n```\ninner\n```\n````"};

    @Test void aLetterTypedBeforeOrAfterATopLevelBlockLeavesTheBlockUntouched() {
        for (var block : BLOCKS) {
            var md = "before text\n\n" + block + "\n\nafter text\n";
            var m = open(md);
            caretAfter(m, "before");
            type(m, "X");
            assertEquals("beforeX text\n\n" + block + "\n\nafter text\n", write(m), block);
            m = open(md);
            caretAfter(m, "after");
            type(m, "Y");
            assertEquals("before text\n\n" + block + "\n\nafterY text\n", write(m), block);
        }
    }

    @Test void aLetterTypedInAnItemAroundATabIndentedBlockLeavesTheBlockUntouched() {
        var md = "- first item\n\t```\n\tcode  \n\t\ttabbed\n\t```\n- last item\n\t- sub\n";
        var m = open(md);
        caretAfter(m, "first");
        type(m, "X");
        assertEquals(md.replace("first", "firstX"), write(m));
        m = open(md);
        caretAfter(m, "last");
        type(m, "Y");
        assertEquals(md.replace("last", "lastY"), write(m));
    }

    // ---- 3. typing inside the block: nothing is turned into formatting, Enter adds a line

    @Test void whatIsTypedInsideABlockStaysLiteral() {
        var md = "```\ncode\n```\n";
        var m = open(md);
        caretAfter(m, "code");
        type(m, "\n# h\n- i\n>> c\n---\n  two **x** _y_ `z` ^^w^^ ~~v~~ [l](u)\n\tTab\n1. one");
        assertEquals("```\ncode\n# h\n- i\n>> c\n---\n  two **x** _y_ `z` ^^w^^ ~~v~~ [l](u)\n\tTab\n1. one\n```\n", write(m));
        assertEquals(1, m.state().getDoc().getChildCount());
        assertEquals(write(m), Lossless.write(Lossless.read(write(m))));
    }

    @Test void enterAfterAShorterFenceLineAtTheEndOfAnUnclosedLongerBlockStaysInsideIt() {
        var m = open("````\ncode\n```\n");
        var doc = m.state().getDoc();
        assertEquals(1, doc.getChildCount());
        m.select(Pm.cursor(doc, doc.getFirstChild().getNodeSize() - 1));
        type(m, "\nmore");
        assertEquals(1, m.state().getDoc().getChildCount());
        assertEquals("````\ncode\n```\nmore\n", write(m));
    }

    @Test void enterAfterTheClosingFenceOfALongerBlockLeavesIt() {
        var m = open("````\n```\n````\n");
        var doc = m.state().getDoc();
        m.select(Pm.cursor(doc, doc.getFirstChild().getNodeSize() - 1));
        type(m, "\nafter");
        assertEquals(2, m.state().getDoc().getChildCount());
        assertEquals("````\n```\n````\n\nafter\n", write(m));
    }

    @Test void enterInsideATabIndentedItemBlockKeepsItsIndent() {
        var md = "- item\n\t```\n\tcode\n\t```\n- next\n";
        var m = open(md);
        caretAfter(m, "code");
        type(m, "\nmore");
        assertEquals("- item\n\t```\n\tcode\n\tmore\n\t```\n- next\n", write(m));
    }
}
