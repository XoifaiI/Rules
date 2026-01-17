package re2j;

import static testing.Testing.describe;
import static testing.Testing.entry;
import static testing.Testing.expect;
import static testing.Testing.test;

public class RE2QuoteMetaTest {

  public static void main(String[] args) {
    entry(() -> {
      describe("RE2", () -> {
        describe("quoteMeta", () -> {
          for (Object[] tc : META_TESTS) {
            String pattern = (String) tc[0];
            String output = (String) tc[1];

            test("quotes " + escape(pattern), () -> {
              String quoted = RE2.quoteMeta(pattern);
              expect(quoted).toBe(output);
            });

            if (!pattern.isEmpty()) {
              test("quoted pattern matches original " + escape(pattern), () -> {
                String quoted = RE2.quoteMeta(pattern);
                RE2 re = RE2.compile(quoted);
                String src = "abc" + pattern + "def";
                String replaced = re.replaceAll(src, "xyz");
                expect(replaced).toBe("abcxyzdef");
              });
            }
          }
        });

        describe("literalPrefix", () -> {
          for (Object[] tc : META_TESTS) {
            String pattern = (String) tc[0];
            String literal = (String) tc[2];
            boolean isLiteral = (Boolean) tc[3];

            test("prefixComplete for " + escape(pattern), () -> {
              RE2 re = RE2.compile(pattern);
              expect(re.prefixComplete).toBe(isLiteral);
            });

            test("prefix for " + escape(pattern), () -> {
              RE2 re = RE2.compile(pattern);
              expect(re.prefix).toBe(literal);
            });
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

  private static final Object[][] META_TESTS = {
      { "", "", "", true },
      { "foo", "foo", "foo", true },
      { "foo\\.\\$", "foo\\\\\\.\\\\\\$", "foo.$", true },
      { "foo.\\$", "foo\\.\\\\\\$", "foo", false },
      {
          "!@#$%^&*()_+-=[{]}\\|,<.>/?~",
          "!@#\\$%\\^&\\*\\(\\)_\\+-=\\[\\{\\]\\}\\\\\\|,<\\.>/\\?~",
          "!@#",
          false
      },
  };
}