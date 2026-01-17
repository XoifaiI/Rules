package re2j;

import static testing.Testing.describe;
import static testing.Testing.entry;
import static testing.Testing.expect;
import static testing.Testing.test;

public class RegexpHashcodeEqualsTest {

  public static void main(String[] args) {
    entry(() -> {
      describe("Regexp", () -> {
        describe("equals and hashCode", () -> {
          for (Object[] tc : TEST_CASES) {
            String a = (String) tc[0];
            String b = (String) tc[1];
            boolean areEqual = (Boolean) tc[2];
            int mode = (Integer) tc[3];
            String modeName = mode == RE2.POSIX ? "POSIX" : "PERL";

            if (areEqual) {
              test(escape(a) + " equals " + escape(b) + " (" + modeName + ")", () -> {
                Regexp ra = Parser.parse(a, mode);
                Regexp rb = Parser.parse(b, mode);
                expect(ra.equals(rb)).toBe(true);
              });

              test(escape(a) + " hashCode equals " + escape(b) + " hashCode (" + modeName + ")",
                  () -> {
                    Regexp ra = Parser.parse(a, mode);
                    Regexp rb = Parser.parse(b, mode);
                    expect(ra.hashCode()).toBe(rb.hashCode());
                  });
            } else {
              test(escape(a) + " not equals " + escape(b) + " (" + modeName + ")", () -> {
                Regexp ra = Parser.parse(a, mode);
                Regexp rb = Parser.parse(b, mode);
                expect(ra.equals(rb)).toBe(false);
              });
            }
          }
        });
      });
    });
  }

  private static String escape(String s) {
    if (s.isEmpty()) {
      return "(empty)";
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < 32 || c > 126) {
        sb.append(String.format("\\x%02x", (int) c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  private static final Object[][] TEST_CASES = {
      { "abc", "abc", true, RE2.POSIX },
      { "abc", "def", false, RE2.POSIX },
      { "(abc)", "(a)(b)(c)", false, RE2.POSIX },
      { "a|$", "a|$", true, RE2.POSIX },
      { "abc|def", "def|abc", false, RE2.POSIX },
      { "a?", "b?", false, RE2.POSIX },
      { "a?", "a?", true, RE2.POSIX },
      { "a{1,3}", "a{1,3}", true, RE2.POSIX },
      { "a{2,3}", "a{1,3}", false, RE2.POSIX },
      { "^((?P<foo>what)a)$", "^((?P<foo>what)a)$", true, RE2.PERL },
      { "^((?P<foo>what)a)$", "^((?P<bar>what)a)$", false, RE2.PERL },
  };
}