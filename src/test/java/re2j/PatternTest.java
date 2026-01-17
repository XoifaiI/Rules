package re2j;

import static testing.Testing.describe;
import static testing.Testing.entry;
import static testing.Testing.expect;
import static testing.Testing.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class PatternTest {

    public static void main(String[] args) {
        entry(() -> {
            describe("Pattern", () -> {
                describe("compile", () -> {
                    test("compiles simple pattern", () -> {
                        Pattern p = Pattern.compile("abc");
                        expect(p.pattern()).toBe("abc");
                        expect(p.flags()).toBe(0);
                    });

                    test("throws on duplicate group names", () -> {
                        expect(() -> Pattern.compile("(?P<any>.*)(?P<any>.*"))
                                .toThrow("error parsing regexp: duplicate capture group name: `any`");
                    });
                });

                describe("toString", () -> {
                    test("returns pattern string", () -> {
                        Pattern p = Pattern.compile("abc");
                        expect(p.toString()).toBe("abc");
                    });
                });

                describe("compile with flags", () -> {
                    test("preserves flags", () -> {
                        Pattern p = Pattern.compile("abc", 5);
                        expect(p.pattern()).toBe("abc");
                        expect(p.flags()).toBe(5);
                    });
                });

                describe("syntax error", () -> {
                    test("throws PatternSyntaxException on invalid pattern", () -> {
                        try {
                            Pattern.compile("abc(");
                            throw new AssertionError("should have thrown");
                        } catch (PatternSyntaxException e) {
                            expect(e.getIndex()).toBe(-1);
                            expect(e.getDescription().isEmpty()).toBe(false);
                            expect(e.getMessage().isEmpty()).toBe(false);
                            expect(e.getPattern()).toBe("abc(");
                        }
                    });
                });

                describe("matches no flags", () -> {
                    test("ab+c matches abbbc but not cbbba", () -> {
                        testMatches("ab+c", "abbbc", "cbbba");
                    });

                    test("ab.*c matches abxyzc but not ab newline xyzc", () -> {
                        testMatches("ab.*c", "abxyzc", "ab\nxyzc");
                    });

                    test("anchored pattern matches abc but not multiline", () -> {
                        testMatches("^ab.*c$", "abc", "xyz\nabc\ndef");
                    });

                    test("surrogate pair codepoint matches", () -> {
                        String source = new StringBuilder().appendCodePoint(110781).toString();
                        testMatches(source, source, "blah");
                        testMatches("\\Q" + source + "\\E", source, "blah");
                    });
                });

                describe("matches with flags", () -> {
                    test("ab+c with no flags", () -> {
                        testMatchesRE2("ab+c", 0, "abbbc", "cbba");
                    });

                    test("ab+c case insensitive", () -> {
                        testMatchesRE2("ab+c", Pattern.CASE_INSENSITIVE, "abBBc", "cbbba");
                    });

                    test("ab.*c with no flags", () -> {
                        testMatchesRE2("ab.*c", 0, "abxyzc", "ab\nxyzc");
                    });

                    test("ab.*c with DOTALL", () -> {
                        testMatchesRE2("ab.*c", Pattern.DOTALL, "ab\nxyzc", "aB\nxyzC");
                    });

                    test("ab.*c with DOTALL and CASE_INSENSITIVE", () -> {
                        testMatchesRE2("ab.*c", Pattern.DOTALL | Pattern.CASE_INSENSITIVE, "aB\nxyzC", "z");
                    });

                    test("anchored with no flags", () -> {
                        testMatchesRE2("^ab.*c$", 0, "abc", "xyz\nabc\ndef");
                    });

                    test("anchored with MULTILINE", () -> {
                        testMatchesRE2("^ab.*c$", Pattern.MULTILINE, "abc", "xyz\nabc\ndef");
                        testMatchesRE2("^ab.*c$", Pattern.MULTILINE, "abc", "");
                    });

                    test("anchored with DOTALL and MULTILINE", () -> {
                        testMatchesRE2("^ab.*c$", Pattern.DOTALL | Pattern.MULTILINE, "ab\nc", "AB\nc");
                    });

                    test("anchored with all flags", () -> {
                        testMatchesRE2("^ab.*c$",
                                Pattern.DOTALL | Pattern.MULTILINE | Pattern.CASE_INSENSITIVE, "AB\nc", "z");
                    });
                });

                describe("find", () -> {
                    test("ab+c finds in xxabbbc", () -> {
                        testFind("ab+c", 0, "xxabbbc", "cbbba");
                    });

                    test("ab+c case insensitive", () -> {
                        testFind("ab+c", Pattern.CASE_INSENSITIVE, "abBBc", "cbbba");
                    });

                    test("ab.*c with no flags", () -> {
                        testFind("ab.*c", 0, "xxabxyzc", "ab\nxyzc");
                    });

                    test("ab.*c with DOTALL", () -> {
                        testFind("ab.*c", Pattern.DOTALL, "ab\nxyzc", "aB\nxyzC");
                    });

                    test("ab.*c with DOTALL and CASE_INSENSITIVE", () -> {
                        testFind("ab.*c", Pattern.DOTALL | Pattern.CASE_INSENSITIVE, "xaB\nxyzCz", "z");
                    });

                    test("anchored with no flags", () -> {
                        testFind("^ab.*c$", 0, "abc", "xyz\nabc\ndef");
                    });

                    test("anchored with MULTILINE", () -> {
                        testFind("^ab.*c$", Pattern.MULTILINE, "xyz\nabc\ndef", "xyz\nab\nc\ndef");
                    });

                    test("anchored with DOTALL and MULTILINE", () -> {
                        testFind("^ab.*c$", Pattern.DOTALL | Pattern.MULTILINE,
                                "xyz\nab\nc\ndef", "xyz\nAB\nc\ndef");
                    });

                    test("anchored with all flags", () -> {
                        testFind("^ab.*c$", Pattern.DOTALL | Pattern.MULTILINE | Pattern.CASE_INSENSITIVE,
                                "xyz\nAB\nc\ndef", "z");
                    });
                });

                describe("split", () -> {
                    test("no delimiter found", () -> {
                        testSplit("/", "abcde", new String[] { "abcde" });
                    });

                    test("multiple delimiters", () -> {
                        testSplit("/", "a/b/cc//d/e//", new String[] { "a", "b", "cc", "", "d", "e" });
                    });

                    test("with limit 3", () -> {
                        testSplit("/", "a/b/cc//d/e//", 3, new String[] { "a", "b", "cc//d/e//" });
                    });

                    test("with limit 4", () -> {
                        testSplit("/", "a/b/cc//d/e//", 4, new String[] { "a", "b", "cc", "/d/e//" });
                    });

                    test("with limit 5", () -> {
                        testSplit("/", "a/b/cc//d/e//", 5, new String[] { "a", "b", "cc", "", "d/e//" });
                    });

                    test("with limit 6", () -> {
                        testSplit("/", "a/b/cc//d/e//", 6, new String[] { "a", "b", "cc", "", "d", "e//" });
                    });

                    test("with limit 7", () -> {
                        testSplit("/", "a/b/cc//d/e//", 7, new String[] { "a", "b", "cc", "", "d", "e", "/" });
                    });

                    test("with limit 8", () -> {
                        testSplit("/", "a/b/cc//d/e//", 8, new String[] { "a", "b", "cc", "", "d", "e", "", "" });
                    });

                    test("with limit 9", () -> {
                        testSplit("/", "a/b/cc//d/e//", 9, new String[] { "a", "b", "cc", "", "d", "e", "", "" });
                    });

                    test("boo:and:foo with limit 2", () -> {
                        testSplit(":", "boo:and:foo", 2, new String[] { "boo", "and:foo" });
                    });

                    test("boo:and:foo with limit 5", () -> {
                        testSplit(":", "boo:and:foo", 5, new String[] { "boo", "and", "foo" });
                    });

                    test("boo:and:foo with limit -2", () -> {
                        testSplit(":", "boo:and:foo", -2, new String[] { "boo", "and", "foo" });
                    });

                    test("split on o with limit 5", () -> {
                        testSplit("o", "boo:and:foo", 5, new String[] { "b", "", ":and:f", "", "" });
                    });

                    test("split on o with limit -2", () -> {
                        testSplit("o", "boo:and:foo", -2, new String[] { "b", "", ":and:f", "", "" });
                    });

                    test("split on o with limit 0", () -> {
                        testSplit("o", "boo:and:foo", 0, new String[] { "b", "", ":and:f" });
                    });

                    test("split on o with no limit", () -> {
                        testSplit("o", "boo:and:foo", new String[] { "b", "", ":and:f" });
                    });

                    test("split on x* matches zero width", () -> {
                        testSplit("x*", "foo", new String[] { "f", "o", "o" });
                    });

                    test("split on x* with limit 1", () -> {
                        testSplit("x*", "foo", 1, new String[] { "foo" });
                    });

                    test("split on x* single char with limit 2", () -> {
                        testSplit("x*", "f", 2, new String[] { "f", "" });
                    });

                    test("split with leading delimiter", () -> {
                        testSplit(":", ":a::b", new String[] { "", "a", "", "b" });
                    });
                });

                describe("programSize", () -> {
                    test("empty pattern", () -> {
                        testProgramSize("", 3);
                    });

                    test("single char", () -> {
                        testProgramSize("a", 3);
                    });

                    test("anchor", () -> {
                        testProgramSize("^", 3);
                    });

                    test("two anchors", () -> {
                        testProgramSize("^$", 4);
                    });

                    test("a+b", () -> {
                        testProgramSize("a+b", 5);
                    });

                    test("a+b?", () -> {
                        testProgramSize("a+b?", 6);
                    });

                    test("(a+b)", () -> {
                        testProgramSize("(a+b)", 7);
                    });

                    test("a+b.*", () -> {
                        testProgramSize("a+b.*", 7);
                    });

                    test("(a+b?)", () -> {
                        testProgramSize("(a+b?)", 8);
                    });
                });

                describe("groupCount", () -> {
                    test("two groups", () -> {
                        testGroupCount("(.*)ab(.*)a", 2);
                    });

                    test("three groups", () -> {
                        testGroupCount("(.*)(ab)(.*)a", 3);
                    });

                    test("four groups with nesting", () -> {
                        testGroupCount("(.*)((a)b)(.*)a", 4);
                    });

                    test("escaped parens", () -> {
                        testGroupCount("(.*)(\\(ab)(.*)a", 3);
                    });

                    test("more escaped parens", () -> {
                        testGroupCount("(.*)(\\(a\\)b)(.*)a", 3);
                    });
                });

                describe("namedGroups", () -> {
                    test("single named group", () -> {
                        expect(Pattern.compile("(?P<foo>\\d{2})").namedGroups())
                                .toBe(Map.of("foo", 1));
                    });

                    test("no named groups with digits", () -> {
                        expect(Pattern.compile("\\d{2}").namedGroups())
                                .toBe(Collections.emptyMap());
                    });

                    test("no named groups with literal", () -> {
                        expect(Pattern.compile("hello").namedGroups())
                                .toBe(Collections.emptyMap());
                    });

                    test("no named groups with anonymous group", () -> {
                        expect(Pattern.compile("(.*)").namedGroups())
                                .toBe(Collections.emptyMap());
                    });

                    test("named group any", () -> {
                        expect(Pattern.compile("(?P<any>.*)").namedGroups())
                                .toBe(Map.of("any", 1));
                    });

                    test("multiple named groups", () -> {
                        expect(Pattern.compile("(?P<foo>.*)(?P<bar>.*)").namedGroups())
                                .toBe(Map.of("foo", 1, "bar", 2));
                    });
                });

                describe("issue 93", () -> {
                    test("alternation with capture produces same match", () -> {
                        Pattern p1 = Pattern.compile("(a.*?c)|a.*?b");
                        Pattern p2 = Pattern.compile("a.*?c|a.*?b");

                        Matcher m1 = p1.matcher("abc");
                        m1.find();
                        Matcher m2 = p2.matcher("abc");
                        m2.find();

                        expect(m2.group()).toBe(m1.group());
                    });
                });

                describe("quote", () -> {
                    test("escapes metacharacters", () -> {
                        testMatchesRE2(Pattern.quote("ab+c"), 0, "ab+c", "abc");
                    });
                });

                describe("serialization", () -> {
                    test("simple pattern serializes", () -> {
                        assertSerializes(Pattern.compile("ab+c"));
                    });

                    test("pattern with flags serializes", () -> {
                        assertSerializes(Pattern.compile("^ab.*c$", Pattern.DOTALL | Pattern.MULTILINE));
                    });

                    test("reserialized pattern works", () -> {
                        expect(reserialize(Pattern.compile("abc")).matcher("def").find()).toBe(false);
                    });
                });

                describe("equals", () -> {
                    test("equal patterns are equal", () -> {
                        Pattern pattern1 = Pattern.compile("abc");
                        Pattern pattern2 = Pattern.compile("abc");
                        expect(pattern1.equals(pattern2)).toBe(true);
                        expect(pattern1.hashCode()).toBe(pattern2.hashCode());
                    });

                    test("different patterns are not equal", () -> {
                        Pattern pattern1 = Pattern.compile("abc");
                        Pattern pattern3 = Pattern.compile("def");
                        expect(pattern1.equals(pattern3)).toBe(false);
                    });

                    test("same pattern different flags not equal", () -> {
                        Pattern pattern1 = Pattern.compile("abc");
                        Pattern pattern4 = Pattern.compile("abc", Pattern.CASE_INSENSITIVE);
                        expect(pattern1.equals(pattern4)).toBe(false);
                    });
                });
            });
        });
    }

    private static void testMatches(String regexp, String match, String nonMatch) {
        expect(Pattern.matches(regexp, match)).toBe(true);
        expect(Pattern.matches(regexp, nonMatch)).toBe(false);
        expect(Pattern.matches(regexp, match.getBytes(StandardCharsets.UTF_8))).toBe(true);
        expect(Pattern.matches(regexp, nonMatch.getBytes(StandardCharsets.UTF_8))).toBe(false);
    }

    private static void testMatchesRE2(String regexp, int flags, String match, String nonMatch) {
        Pattern p = Pattern.compile(regexp, flags);
        expect(p.matches(match)).toBe(true);
        expect(p.matches(match.getBytes(StandardCharsets.UTF_8))).toBe(true);
        expect(p.matches(nonMatch)).toBe(false);
        expect(p.matches(nonMatch.getBytes(StandardCharsets.UTF_8))).toBe(false);
    }

    private static void testFind(String regexp, int flag, String match, String nonMatch) {
        expect(Pattern.compile(regexp, flag).matcher(match).find()).toBe(true);
        expect(Pattern.compile(regexp, flag).matcher(nonMatch).find()).toBe(false);
    }

    private static void testSplit(String regexp, String text, String[] expected) {
        testSplit(regexp, text, 0, expected);
    }

    private static void testSplit(String regexp, String text, int limit, String[] expected) {
        String[] result = Pattern.compile(regexp).split(text, limit);
        if (!Arrays.equals(expected, result)) {
            throw new AssertionError(String.format(
                    "split(%s, %s, %d): expected %s, got %s",
                    regexp, text, limit, Arrays.toString(expected), Arrays.toString(result)));
        }
    }

    private static void testProgramSize(String pattern, int expectedSize) {
        Pattern p = Pattern.compile(pattern);
        expect(p.programSize()).toBe(expectedSize);
    }

    private static void testGroupCount(String pattern, int count) {
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher("x");
        expect(p.groupCount()).toBe(count);
        expect(m.groupCount()).toBe(count);
    }

    private static Pattern reserialize(Pattern object) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bytes);
            out.writeObject(object);
            ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
            return (Pattern) in.readObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void assertSerializes(Pattern p) {
        Pattern reserialized = reserialize(p);
        expect(reserialized.pattern()).toBe(p.pattern());
        expect(reserialized.flags()).toBe(p.flags());
    }
}