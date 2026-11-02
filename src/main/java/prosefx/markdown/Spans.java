package prosefx.markdown;

import com.atlassian.prosemirror.model.Mark;
import com.atlassian.prosemirror.model.MarkType;
import com.atlassian.prosemirror.model.Node;
import java.util.*;

import static prosefx.core.schema.EditorSchema.*;

/**
 * The two span marks commonmark does not know: ^^highlight^^ (a Logseq thing) and ~~strike~~. A delimiter turns a span
 * on or off across the text around it, whatever other marks sit in between (^^a **b** c^^), but only when it has a
 * partner on the same line: a lone ^^ or ~~ stays text. Read once per line, then used for each of its text runs.
 */
final class Spans {

    static final String[] TOKENS = {"^^", "~~"};
    static final MarkType[] TYPES = {HIGHLIGHT, STRIKE};

    final boolean[] on = new boolean[2];
    final int[] left = new int[2];

    /** Counts the delimiters under parent; an odd one out has no partner and is not counted. */
    Spans(org.commonmark.node.Node parent) { count(parent); for (var t = 0; t < 2; t++) left[t] -= left[t] % 2; }

    void count(org.commonmark.node.Node parent) {
        for (var c = parent.getFirstChild(); c != null; c = c.getNext()) {
            if (c instanceof org.commonmark.node.Text t) for (var k = 0; k < 2; k++) left[k] += occurrences(t.getLiteral(), TOKENS[k]);
            else count(c);
        }
    }

    static int occurrences(String s, String token) {
        var n = 0;
        for (var i = s.indexOf(token); i >= 0; i = s.indexOf(token, i + 2)) n++;
        return n;
    }

    /** Adds the text, cut at each paired delimiter, under the marks in force. */
    void add(List<Node> out, String text, List<Mark> marks) {
        var plain = new StringBuilder();
        var i = 0;
        while (i < text.length()) {
            var t = tokenAt(text, i);
            if (t < 0) { plain.append(text.charAt(i++)); continue; }
            if (left[t] == 0) { plain.append(TOKENS[t]); i += 2; continue; }
            flush(out, plain, marks);
            on[t] = !on[t];
            left[t]--;
            i += 2;
        }
        flush(out, plain, marks);
    }

    int tokenAt(String s, int i) {
        for (var t = 0; t < 2; t++) if (s.startsWith(TOKENS[t], i)) return t;
        return -1;
    }

    /** A footnote reference, [^note]: it stays text, under the footnote mark, so the view can style it and jump from it to its definition. */
    static final java.util.regex.Pattern FOOTNOTE_REF = java.util.regex.Pattern.compile("\\[\\^[^\\]\\s]+\\]");

    void flush(List<Node> out, StringBuilder plain, List<Mark> marks) {
        if (plain.isEmpty()) return;
        var all = marks;
        for (var t = 0; t < 2; t++) if (on[t]) all = Markdown.with(all, TYPES[t].create(null));
        var text = plain.toString();
        var m = FOOTNOTE_REF.matcher(text);
        var last = 0;
        while (m.find()) {
            Markdown.add(out, text.substring(last, m.start()), all);
            Markdown.add(out, m.group(), Markdown.with(all, FOOTNOTE.create(null)));
            last = m.end();
        }
        Markdown.add(out, text.substring(last), all);
        plain.setLength(0);
    }
}
