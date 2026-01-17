package re2j;

import static testing.Testing.describe;
import static testing.Testing.entry;
import static testing.Testing.expect;
import static testing.Testing.test;

import java.nio.charset.StandardCharsets;

public class MatcherTest {

  public static void main(String[] args) {
    entry(() -> {
      describe("Matcher", () -> {
        describe("lookingAt", () -> {
          test("matches prefix", () -> {
            verifyLookingAt("abcdef", "abc", true);
          });

          test("fails when prefix too long", () -> {
            verifyLookingAt("ab", "abc", false);
          });
        });

        describe("matches", () -> {
          test("ab+c matches abbbc but not cbbba", () -> {
            testMatcherMatches("ab+c", "abbbc", "cbbba");
          });

          test("ab.*c matches abxyzc but not ab newline xyzc", () -> {
            testMatcherMatches("ab.*c", "abxyzc", "ab\nxyzc");
          });

          test("anchored pattern matches single line", () -> {
            testMatcherMatches("^ab.*c$", "abc", "xyz\nabc\ndef");
          });

          test("ab+c matches abbbc but not abbcabc", () -> {
            testMatcherMatches("ab+c", "abbbc", "abbcabc");
          });
        });

        describe("replaceAll", () -> {
          test("replaces all occurrences", () -> {
            testReplaceAll(
                "What the Frog's Eye Tells the Frog's Brain",
                "Frog", "Lizard",
                "What the Lizard's Eye Tells the Lizard's Brain");
          });

          test("handles escape sequences and groups", () -> {
            testReplaceAll(
                "What the Frog's Eye Tells the Frog's Brain",
                "F(rog)", "\\$Liza\\rd$1",
                "What the $Lizardrog's Eye Tells the $Lizardrog's Brain");
          });

          test("handles double digit group references", () -> {
            testReplaceAll(
                "abcdefghijklmnopqrstuvwxyz123",
                "(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)",
                "$10$20",
                "jb0wo0123");
          });

          test("handles unicode characters", () -> {
            testReplaceAll(
                "\u00e1\u0062\u00e7\u2655",
                "(.)", "<$1>",
                "<\u00e1><\u0062><\u00e7><\u2655>");
          });

          test("handles unicode character class", () -> {
            testReplaceAll(
                "\u00e1\u0062\u00e7\u2655",
                "[\u00e0-\u00e9]", "<$0>",
                "<\u00e1>\u0062<\u00e7>\u2655");
          });

          test("handles zero-width matches", () -> {
            testReplaceAll("hello world", "z*", "x", "xhxexlxlxox xwxoxrxlxdx");
          });

          test("handles alternation first branch", () -> {
            testReplaceAll("123:foo", "(?:\\w+|\\d+:foo)", "x", "x:x");
          });

          test("handles alternation second branch", () -> {
            testReplaceAll("123:foo", "(?:\\d+:foo|\\w+)", "x", "x");
          });

          test("handles greedy star", () -> {
            testReplaceAll("aab", "a*", "<$0>", "<aa><>b<>");
          });

          test("handles non-greedy star", () -> {
            testReplaceAll("aab", "a*?", "<$0>", "<>a<>a<>b<>");
          });
        });

        describe("replaceFirst", () -> {
          test("replaces first occurrence only", () -> {
            testReplaceFirst(
                "What the Frog's Eye Tells the Frog's Brain",
                "Frog", "Lizard",
                "What the Lizard's Eye Tells the Frog's Brain");
          });

          test("handles escape sequences and groups", () -> {
            testReplaceFirst(
                "What the Frog's Eye Tells the Frog's Brain",
                "F(rog)", "\\$Liza\\rd$1",
                "What the $Lizardrog's Eye Tells the Frog's Brain");
          });

          test("handles double digit group references", () -> {
            testReplaceFirst(
                "abcdefghijklmnopqrstuvwxyz123",
                "(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)",
                "$10$20",
                "jb0nopqrstuvwxyz123");
          });

          test("handles unicode characters", () -> {
            testReplaceFirst(
                "\u00e1\u0062\u00e7\u2655",
                "(.)", "<$1>",
                "<\u00e1>\u0062\u00e7\u2655");
          });

          test("handles unicode character class", () -> {
            testReplaceFirst(
                "\u00e1\u0062\u00e7\u2655",
                "[\u00e0-\u00e9]", "<$0>",
                "<\u00e1>\u0062\u00e7\u2655");
          });

          test("handles zero-width matches", () -> {
            testReplaceFirst("hello world", "z*", "x", "xhello world");
          });

          test("handles greedy star", () -> {
            testReplaceFirst("aab", "a*", "<$0>", "<aa>b");
          });

          test("handles non-greedy star", () -> {
            testReplaceFirst("aab", "a*?", "<$0>", "<>aab");
          });
        });

        describe("programSize", () -> {
          test("returns consistent size for pattern and matcher", () -> {
            Pattern pattern = Pattern.compile("go+d");
            int programSize = pattern.programSize();
            expect(programSize > 1).toBe(true);
            expect(pattern.matcher("good").programSize()).toBe(programSize);
            expect(pattern.matcher("bad").programSize()).toBe(programSize);
          });
        });

        describe("groupCount", () -> {
          test("counts groups correctly", () -> {
            testGroupCount("(a)(b(c))d?(e)", 4);
          });
        });

        describe("group", () -> {
          test("captures greek letters", () -> {
            testGroup(
                "\u03b1\u03b2\u03be\u03b4\u03b5\u03c6\u03b3",
                "(.)(..)(...)",
                new String[] {
                    "\u03b1\u03b2\u03be\u03b4\u03b5\u03c6",
                    "\u03b1",
                    "\u03b2\u03be",
                    "\u03b4\u03b5\u03c6"
                });
          });
        });

        describe("find", () -> {
          test("finds from position 0", () -> {
            testFind("abcdefgh", ".*[aeiou]", 0, "abcde");
          });

          test("finds from position 1", () -> {
            testFind("abcdefgh", ".*[aeiou]", 1, "bcde");
          });

          test("finds from position 2", () -> {
            testFind("abcdefgh", ".*[aeiou]", 2, "cde");
          });

          test("finds from position 3", () -> {
            testFind("abcdefgh", ".*[aeiou]", 3, "de");
          });

          test("finds from position 4", () -> {
            testFind("abcdefgh", ".*[aeiou]", 4, "e");
          });

          test("no match from position 5", () -> {
            testFindNoMatch("abcdefgh", ".*[aeiou]", 5);
          });

          test("no match from position 6", () -> {
            testFindNoMatch("abcdefgh", ".*[aeiou]", 6);
          });

          test("no match from position 7", () -> {
            testFindNoMatch("abcdefgh", ".*[aeiou]", 7);
          });
        });

        describe("invalid find", () -> {
          test("throws on out of bounds start", () -> {
            expect(() -> {
              Pattern p = Pattern.compile(".*");
              Matcher m = p.matcher("abcdef");
              m.find(10);
            }).toThrow();
          });
        });

        describe("invalid replacement", () -> {
          test("throws on invalid group reference", () -> {
            expect(() -> {
              Pattern p = Pattern.compile("abc");
              Matcher m = p.matcher("abc");
              m.find();
              m.appendReplacement(new StringBuilder(), "$4");
            }).toThrow();
          });
        });

        describe("invalid group", () -> {
          test("throws on no match", () -> {
            expect(() -> {
              Pattern p = Pattern.compile("xxx");
              Matcher m = p.matcher("abc");
              m.find();
              m.group(0);
            }).toThrow();
          });

          test("throws on out of range group", () -> {
            expect(() -> {
              Pattern p = Pattern.compile("abc");
              Matcher m = p.matcher("abc");
              m.find();
              m.group(1);
            }).toThrow();
          });
        });

        describe("null input handling", () -> {
          test("throws on null input in constructor", () -> {
            expect(() -> {
              new Matcher(Pattern.compile("pattern"), (String) null);
            }).toThrow();
          });

          test("throws on null pattern in constructor", () -> {
            expect(() -> {
              new Matcher(null, "input");
            }).toThrow();
          });
        });

        describe("start/end before find", () -> {
          test("throws IllegalStateException", () -> {
            expect(() -> {
              Matcher m = Pattern.compile("a").matcher("abaca");
              m.start();
            }).toThrow();
          });
        });

        describe("matches updates match information", () -> {
          test("group returns matched text after matches", () -> {
            Matcher m = Pattern.compile("a+").matcher("aaa");
            expect(m.matches()).toBe(true);
            expect(m.group(0)).toBe("aaa");
          });
        });

        describe("alternation matches", () -> {
          test("first alternative matches", () -> {
            String s = "123:foo";
            expect(Pattern.compile("(?:\\w+|\\d+:foo)").matcher(s).matches()).toBe(true);
          });

          test("second alternative matches", () -> {
            String s = "123:foo";
            expect(Pattern.compile("(?:\\d+:foo|\\w+)").matcher(s).matches()).toBe(true);
          });
        });

        describe("appendTail with StringBuffer", () -> {
          test("appends remaining text", () -> {
            Pattern p = Pattern.compile("cat");
            Matcher m = p.matcher("one cat two cats in the yard");
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
              m.appendReplacement(sb, "dog");
            }
            m.appendTail(sb);
            m.appendTail(sb);
            expect(sb.toString()).toBe("one dog two dogs in the yards in the yard");
          });
        });

        describe("appendTail with StringBuilder", () -> {
          test("appends remaining text", () -> {
            Pattern p = Pattern.compile("cat");
            Matcher m = p.matcher("one cat two cats in the yard");
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
              m.appendReplacement(sb, "dog");
            }
            m.appendTail(sb);
            m.appendTail(sb);
            expect(sb.toString()).toBe("one dog two dogs in the yards in the yard");
          });
        });

        describe("reset on find int with StringBuffer", () -> {
          test("resets append position", () -> {
            Matcher matcher = Pattern.compile("a").matcher("zza");
            expect(matcher.find()).toBe(true);

            StringBuffer buffer = new StringBuffer();
            matcher.appendReplacement(buffer, "foo");
            expect(buffer.toString()).toBe("zzfoo");

            expect(matcher.find(0)).toBe(true);

            buffer = new StringBuffer();
            matcher.appendReplacement(buffer, "foo");
            expect(buffer.toString()).toBe("zzfoo");
          });
        });

        describe("reset on find int with StringBuilder", () -> {
          test("resets append position", () -> {
            Matcher matcher = Pattern.compile("a").matcher("zza");
            expect(matcher.find()).toBe(true);

            StringBuilder buffer = new StringBuilder();
            matcher.appendReplacement(buffer, "foo");
            expect(buffer.toString()).toBe("zzfoo");

            expect(matcher.find(0)).toBe(true);

            buffer = new StringBuilder();
            matcher.appendReplacement(buffer, "foo");
            expect(buffer.toString()).toBe("zzfoo");
          });
        });

        describe("empty replacement groups with StringBuffer", () -> {
          test("handles optional groups", () -> {
            StringBuffer buffer = new StringBuffer();
            Matcher matcher = Pattern.compile("(a)(b$)?(b)?").matcher("abc");
            expect(matcher.find()).toBe(true);
            matcher.appendReplacement(buffer, "$1-$2-$3");
            expect(buffer.toString()).toBe("a--b");
            matcher.appendTail(buffer);
            expect(buffer.toString()).toBe("a--bc");
          });

          test("handles matching optional group", () -> {
            StringBuffer buffer = new StringBuffer();
            Matcher matcher = Pattern.compile("(a)(b$)?(b)?").matcher("ab");
            expect(matcher.find()).toBe(true);
            matcher.appendReplacement(buffer, "$1-$2-$3");
            matcher.appendTail(buffer);
            expect(buffer.toString()).toBe("a-b-");
          });

          test("handles anchor optional group", () -> {
            StringBuffer buffer = new StringBuffer();
            Matcher matcher = Pattern.compile("(^b)?(b)?c").matcher("abc");
            expect(matcher.find()).toBe(true);
            matcher.appendReplacement(buffer, "$1-$2");
            matcher.appendTail(buffer);
            expect(buffer.toString()).toBe("a-b");
          });

          test("handles complex optional groups", () -> {
            StringBuffer buffer = new StringBuffer();
            Matcher matcher = Pattern.compile("^(.)[^-]+(-.)?(.*)").matcher("Name");
            expect(matcher.find()).toBe(true);
            matcher.appendReplacement(buffer, "$1$2");
            matcher.appendTail(buffer);
            expect(buffer.toString()).toBe("N");
          });
        });

        describe("empty replacement groups with StringBuilder", () -> {
          test("handles optional groups", () -> {
            StringBuilder buffer = new StringBuilder();
            Matcher matcher = Pattern.compile("(a)(b$)?(b)?").matcher("abc");
            expect(matcher.find()).toBe(true);
            matcher.appendReplacement(buffer, "$1-$2-$3");
            expect(buffer.toString()).toBe("a--b");
            matcher.appendTail(buffer);
            expect(buffer.toString()).toBe("a--bc");
          });

          test("handles matching optional group", () -> {
            StringBuilder buffer = new StringBuilder();
            Matcher matcher = Pattern.compile("(a)(b$)?(b)?").matcher("ab");
            expect(matcher.find()).toBe(true);
            matcher.appendReplacement(buffer, "$1-$2-$3");
            matcher.appendTail(buffer);
            expect(buffer.toString()).toBe("a-b-");
          });

          test("handles anchor optional group", () -> {
            StringBuilder buffer = new StringBuilder();
            Matcher matcher = Pattern.compile("(^b)?(b)?c").matcher("abc");
            expect(matcher.find()).toBe(true);
            matcher.appendReplacement(buffer, "$1-$2");
            matcher.appendTail(buffer);
            expect(buffer.toString()).toBe("a-b");
          });

          test("handles complex optional groups", () -> {
            StringBuilder buffer = new StringBuilder();
            Matcher matcher = Pattern.compile("^(.)[^-]+(-.)?(.*)").matcher("Name");
            expect(matcher.find()).toBe(true);
            matcher.appendReplacement(buffer, "$1$2");
            matcher.appendTail(buffer);
            expect(buffer.toString()).toBe("N");
          });
        });

        describe("documented example", () -> {
          test("matches as documented", () -> {
            Pattern p = Pattern.compile("b(an)*(.)");
            Matcher m = p.matcher("by, band, banana");
            expect(m.lookingAt()).toBe(true);
            m.reset();
            expect(m.find()).toBe(true);
            expect(m.group(0)).toBe("by");
            expect(m.group(1)).toBeNull();
            expect(m.group(2)).toBe("y");
            expect(m.find()).toBe(true);
            expect(m.group(0)).toBe("band");
            expect(m.group(1)).toBe("an");
            expect(m.group(2)).toBe("d");
            expect(m.find()).toBe(true);
            expect(m.group(0)).toBe("banana");
            expect(m.group(1)).toBe("an");
            expect(m.group(2)).toBe("a");
            expect(m.find()).toBe(false);
          });
        });

        describe("mutable CharSequence", () -> {
          test("handles modified input", () -> {
            Pattern p = Pattern.compile("b(an)*(.)");
            StringBuilder b = new StringBuilder("by, band, banana");
            Matcher m = p.matcher(b);
            expect(m.find(0)).toBe(true);
            int start = b.indexOf("ban");
            b.replace(b.indexOf("ban"), start + 3, "b");
            expect(m.find(b.indexOf("ban"))).toBe(true);
          });
        });

        describe("named groups", () -> {
          test("captures and retrieves named groups", () -> {
            Pattern p = Pattern.compile(
                "(?P<baz>f(?P<foo>b*a(?P<another>r+)){0,10})"
                    + "(?P<bag>bag)?(?P<nomatch>zzz)?");
            Matcher m = p.matcher("fbbarrrrrbag");
            expect(m.matches()).toBe(true);
            expect(m.group("baz")).toBe("fbbarrrrr");
            expect(m.group("foo")).toBe("bbarrrrr");
            expect(m.group("another")).toBe("rrrrr");
            expect(m.start("baz")).toBe(0);
            expect(m.start("foo")).toBe(1);
            expect(m.start("another")).toBe(4);
            expect(m.end("baz")).toBe(9);
            expect(m.end("foo")).toBe(9);
            expect(m.group("bag")).toBe("bag");
            expect(m.start("bag")).toBe(9);
            expect(m.end("bag")).toBe(12);
            expect(m.group("nomatch")).toBeNull();
            expect(m.start("nomatch")).toBe(-1);
            expect(m.end("nomatch")).toBe(-1);

            StringBuilder sb = new StringBuilder();
            m.appendReplacement(sb, "what$2ever${bag}");
            expect(sb.toString()).toBe("whatbbarrrrreverbag");
          });

          test("throws on nonexistent named group", () -> {
            Pattern p = Pattern.compile("(?P<foo>bar)");
            Matcher m = p.matcher("bar");
            m.matches();
            expect(() -> m.group("nonexistent")).toThrow();
          });
        });

        describe("group zero width assertions", () -> {
          test("handles end of line correctly in substring", () -> {
            Matcher m = Pattern.compile("(\\d{2} ?(\\d|[a-z])?)($|[^a-zA-Z])").matcher("22 bored");
            expect(m.find()).toBe(true);
            expect(m.group(1)).toBe("22");
          });
        });

        describe("longest match", () -> {
          test("default matches first alternative", () -> {
            String pattern = "(?:a+)|(?:a+ b+)";
            String text = "xxx aaa bbb yyy";
            Matcher matcher = Pattern.compile(pattern).matcher(text);
            expect(matcher.find()).toBe(true);
            expect(text.substring(matcher.start(), matcher.end())).toBe("aaa");
          });

          test("LONGEST_MATCH matches longer alternative", () -> {
            String pattern = "(?:a+)|(?:a+ b+)";
            String text = "xxx aaa bbb yyy";
            Matcher matcher = Pattern.compile(pattern, Pattern.LONGEST_MATCH).matcher(text);
            expect(matcher.find()).toBe(true);
            expect(text.substring(matcher.start(), matcher.end())).toBe("aaa bbb");
          });
        });
      });
    });
  }

  private static void verifyLookingAt(String text, String regexp, boolean expected) {
    expect(Pattern.compile(regexp).matcher(text).lookingAt()).toBe(expected);
    expect(Pattern.compile(regexp).matcher(getUtf8Bytes(text)).lookingAt()).toBe(expected);
  }

  private static void testMatcherMatches(String regexp, String match, String nonMatch) {
    Pattern p = Pattern.compile(regexp);
    expect(p.matcher(match).matches()).toBe(true);
    expect(p.matcher(getUtf8Bytes(match)).matches()).toBe(true);
    expect(p.matcher(nonMatch).matches()).toBe(false);
    expect(p.matcher(getUtf8Bytes(nonMatch)).matches()).toBe(false);
  }

  private static void testReplaceAll(String orig, String regex, String repl, String expected) {
    Pattern p = Pattern.compile(regex);
    Matcher m = p.matcher(orig);
    expect(m.replaceAll(repl)).toBe(expected);
  }

  private static void testReplaceFirst(String orig, String regex, String repl, String expected) {
    Pattern p = Pattern.compile(regex);
    Matcher m = p.matcher(orig);
    expect(m.replaceFirst(repl)).toBe(expected);
  }

  private static void testGroupCount(String pattern, int count) {
    Pattern p = Pattern.compile(pattern);
    Matcher m = p.matcher("x");
    Matcher m2 = p.matcher(getUtf8Bytes("x"));
    expect(p.groupCount()).toBe(count);
    expect(m.groupCount()).toBe(count);
    expect(m2.groupCount()).toBe(count);
  }

  private static void testGroup(String text, String regexp, String[] output) {
    Pattern p = Pattern.compile(regexp);
    Matcher matchString = p.matcher(text);
    expect(matchString.find()).toBe(true);
    expect(matchString.group()).toBe(output[0]);
    for (int i = 0; i < output.length; i++) {
      expect(matchString.group(i)).toBe(output[i]);
    }
    expect(matchString.groupCount()).toBe(output.length - 1);
  }

  private static void testFind(String text, String regexp, int start, String expected) {
    Pattern p = Pattern.compile(regexp);
    Matcher m = p.matcher(text);
    expect(m.find(start)).toBe(true);
    expect(m.group()).toBe(expected);
  }

  private static void testFindNoMatch(String text, String regexp, int start) {
    Pattern p = Pattern.compile(regexp);
    Matcher m = p.matcher(text);
    expect(m.find(start)).toBe(false);
  }

  private static byte[] getUtf8Bytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }
}