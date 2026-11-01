package prosemirrorfx.markdown;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SpansTest {

    static String sig(String src) { return Sig.inline(InlineParser.parse(src), false); }

    @Test
    void highlightIsAMarkAndNotText() {
        assertEquals("{:a }{highlight:b}{: c}\n", sig("a ^^b^^ c"));
        assertEquals("{:>> }{highlight:AI}{::}\n", sig(">> ^^AI^^:"));
    }

    @Test
    void aSpanRunsAcrossOtherMarks() {
        assertEquals("{highlight:one }{highlight+strong:two}{highlight: three}\n", sig("^^one **two** three^^"));
    }

    @Test
    void strikeThrough() { assertEquals("{:x }{strike:gone}{: y}\n", sig("x ~~gone~~ y")); }

    @Test
    void aLoneDelimiterStaysText() {
        assertEquals("{:a ^^ b}\n", sig("a ^^ b"));
        assertEquals("{highlight:x }{:y^^}\n", sig("^^x ^^y^^"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a ^^b^^ c", "^^one **two** three^^", "x ~~gone~~ y", "**^^bold lit^^** and _~~gone~~_", ">> ^^AI^^: yes", "a ^^ lone", "~~a~~ ^^b^^"})
    void writtenBackAsItWasRead(String src) {
        var read = InlineParser.parse(src);
        var written = String.join("\n", InlineWriter.lines(read, false));
        assertEquals(Sig.inline(read, true), Sig.inline(InlineParser.parse(written), true));
    }

    @Test
    void markdownReaderAndWriterKnowThemToo() {
        var doc = Markdown.read("a ^^b^^ and ~~c~~\n");
        assertEquals("a ^^b^^ and ~~c~~\n", Markdown.write(doc));
    }

    @Test
    void aFileWithThemComesBackByteForByte() {
        var md = ">> got it?\n\n\t>> ^^AI^^: yes\n\nplain ^^lit^^ and ~~out~~\n";
        assertEquals(md, Lossless.write(Lossless.read(md)));
    }

    @Test
    void aFootnoteReferenceIsTextUnderItsOwnMark() {
        assertEquals("{:see }{footnote:[^1]}{: and }{footnote:[^long-note]}{:.}\n", sig("see [^1] and [^long-note]."));
        assertEquals("{:no [^ ] here}\n", sig("no [^ ] here"));
    }

    @Test
    void footnotesComeBackAsTheyWereWrittenAndAFootnoteDefinitionIsKept() {
        var md = "A claim[^1] and **bold[^2]**.\n\n[^1]: The source, with _emphasis_.\n[^2]: Another.\n";
        assertEquals(md, Lossless.write(Lossless.read(md)));
        var read = InlineParser.parse("A claim[^1] and **bold[^2]**.");
        var written = String.join("\n", InlineWriter.lines(read, false));
        assertEquals(Sig.inline(read, true), Sig.inline(InlineParser.parse(written), true));
        assertEquals("[^1]: The source", Lossless.read("[^1]: The source\n").getFirstChild().getTextContent());
    }

    // ---- typing code

    static prosemirrorfx.core.editor.EditorModel typed(String md, String keys) {
        var m = new prosemirrorfx.core.editor.EditorModel(Lossless.read(md));
        m.select(prosemirrorfx.core.pm.Pm.cursor(m.state().getDoc(), m.state().getDoc().getContent().getSize() - 1));
        keys.codePoints().forEach(c -> m.type(new String(Character.toChars(c))));
        return m;
    }

    @Test
    void typingBackticksMakesAnInlineCodeSpanAndTheFileKeepsThemUnescaped() {
        var m = typed("a\n", " `x_y` b");
        assertEquals("a `x_y` b\n", Lossless.write(m.state().getDoc()));
        assertEquals("{:a }{code:x_y}{: b}\n", Sig.inline(m.state().getDoc().getFirstChild().getContent(), false));
    }

    @Test
    void typingHighlightAndStrikeMarksThemAndTheFileKeepsTheMarkup() {
        var m = typed("a\n", " ^^AI^^ : yes ~~no~~");
        assertEquals("{:a }{highlight:AI}{: : yes }{strike:no}\n", Sig.inline(m.state().getDoc().getFirstChild().getContent(), false));
        assertEquals("a ^^AI^^ : yes ~~no~~\n", Lossless.write(m.state().getDoc()));
        var c = typed(">> x\n", "");
        " ^^AI^^: ok".codePoints().forEach(ch -> c.type(new String(Character.toChars(ch))));
        assertEquals(">> x ^^AI^^: ok\n", Lossless.write(c.state().getDoc()));
    }

    @Test
    void aLoneOrTripleBacktickIsJustText() {
        assertEquals("a ` b\n", Lossless.write(typed("a\n", " ` b").state().getDoc()));
        assertEquals("a ``` b\n", Lossless.write(typed("a\n", " ``` b").state().getDoc()));
    }

    @Test
    void nothingTypedInAFencedBlockBecomesFormatting() {
        var md = "```\ncode\n```\n";
        var m = new prosemirrorfx.core.editor.EditorModel(Lossless.read(md));
        m.select(prosemirrorfx.core.pm.Pm.cursor(m.state().getDoc(), 1 + "```".length() + 1 + "code".length()));
        "_x_ and `y`".codePoints().forEach(c -> m.type(new String(Character.toChars(c))));
        assertEquals("```\ncode_x_ and `y`\n```\n", Lossless.write(m.state().getDoc()));
    }

    // ---- typing a fenced block

    static void type(prosemirrorfx.core.editor.EditorModel m, String keys) {
        var first = true;
        for (var line : keys.split("\n", -1)) {
            if (!first) m.run(prosemirrorfx.core.commands.Commands.enter);
            first = false;
            line.codePoints().forEach(c -> m.type(new String(Character.toChars(c))));
        }
    }

    @Test
    void typingAFenceAndEnterMakesACodeBlockWithTheCaretInside() {
        var m = typed("a\n", "");
        type(m, "\n```java\nint x = 1;\n  y_z();");
        assertEquals("a\n\n```java\nint x = 1;\n  y_z();\n```\n", Lossless.write(m.state().getDoc()));
        // the file reads back as one code block, the same as the one typed
        assertEquals(Lossless.write(m.state().getDoc()), Lossless.write(Lossless.read(Lossless.write(m.state().getDoc()))));
    }

    @Test
    void enterInsideACodeBlockAddsALineAndOnlyTheClosingFenceLeavesIt() {
        var md = "```\ncode\n```\n";
        var m = new prosemirrorfx.core.editor.EditorModel(Lossless.read(md));
        m.select(prosemirrorfx.core.pm.Pm.cursor(m.state().getDoc(), 1 + 3 + 1 + 4));
        type(m, "\nmore");
        assertEquals("```\ncode\nmore\n```\n", Lossless.write(m.state().getDoc()));
        assertEquals(1, m.state().getDoc().getChildCount());
        m.select(prosemirrorfx.core.pm.Pm.cursor(m.state().getDoc(), m.state().getDoc().getFirstChild().getNodeSize() - 1));
        type(m, "\nafter _it_");
        assertEquals("```\ncode\nmore\n```\n\nafter _it_\n", Lossless.write(m.state().getDoc()));
        assertEquals(2, m.state().getDoc().getChildCount());
    }

    @Test
    void aBlockAtTheEndOfTheDocumentLeavesAParagraphToArrowInto() {
        var m = typed("a\n", "");
        type(m, "\n~~~\nz");
        assertEquals(3, m.state().getDoc().getChildCount());
        assertEquals("a\n\n~~~\nz\n~~~\n",Lossless.write(m.state().getDoc()));
    }

    @Test
    void aTableTypedRowByRowStaysOneTableAndAnEmptyLineLeavesIt() {
        var m = typed("a\n", "");
        type(m, "\n| hello | world |\n|---|---|\n| some | thing |\n\nafter");
        assertEquals("a\n\n| hello | world |\n|---|---|\n| some | thing |\n\nafter\n", Lossless.write(m.state().getDoc()));
        assertEquals(3, m.state().getDoc().getChildCount());
    }

    @Test
    void inAListItemAFenceStaysText() {
        var m = new prosemirrorfx.core.editor.EditorModel(Lossless.read("- item\n"));
        m.select(prosemirrorfx.core.pm.Pm.cursor(m.state().getDoc(), 3 + "item".length()));
        type(m, "\n```\nq");
        assertEquals("- item\n- \\```\n- q\n", Lossless.write(m.state().getDoc()));
    }
}
