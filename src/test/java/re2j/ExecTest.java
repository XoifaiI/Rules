package re2j;

import static testing.Testing.describe;
import static testing.Testing.entry;
import static testing.Testing.expect;
import static testing.Testing.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class ExecTest {

  public static void main(String[] args) {
    entry(() -> {
      describe("ExecTest", () -> {
        describe("Documentation Examples", () -> {
          test("findAll returns all matches", () -> {
            RE2 re = RE2.compile("(?i:co(.)a)");
            List<String> matches = re.findAll("Copacobana", 10);
            expect(matches.size()).toBe(2);
            expect(matches.get(0)).toBe("Copa");
            expect(matches.get(1)).toBe("coba");
          });

          test("findAllSubmatch returns groups", () -> {
            RE2 re = RE2.compile("(?i:co(.)a)");
            List<String[]> submatches = re.findAllSubmatch("Copacobana", 100);
            expect(submatches.size()).toBe(2);
            expect(submatches.get(0)[0]).toBe("Copa");
            expect(submatches.get(0)[1]).toBe("p");
            expect(submatches.get(1)[0]).toBe("coba");
            expect(submatches.get(1)[1]).toBe("b");
          });
        });

        describe("RE2 Search Tests", () -> {
          test("runs re2-search test file", () -> {
            runRE2TestFile("re2-search.txt");
          });
        });

        describe("RE2 Exhaustive Tests", () -> {
          test("runs re2-exhaustive test file", () -> {
            runRE2TestFile("re2-exhaustive.txt.gz");
          });
        });

        describe("Fowler Tests", () -> {
          test("runs basic.dat", () -> {
            runFowlerTestFile("basic.dat");
          });

          test("runs nullsubexpr.dat", () -> {
            runFowlerTestFile("nullsubexpr.dat");
          });

          test("runs repetition.dat", () -> {
            runFowlerTestFile("repetition.dat");
          });
        });
      });
    });
  }

  private static void runRE2TestFile(String file) {
    try {
      InputStream in = ExecTest.class.getResourceAsStream("/" + file);
      if (in == null) {
        System.err.println("Skipping " + file + ": resource not found");
        return;
      }
      String displayFile = file;
      if (file.endsWith(".gz")) {
        in = new GZIPInputStream(in);
        displayFile = file.substring(0, file.length() - ".gz".length());
      }
      int lineno = 0;
      UNIXBufferedReader r = new UNIXBufferedReader(new InputStreamReader(in, "UTF-8"));
      ArrayList<String> strings = new ArrayList<>();
      int input = 0;
      boolean inStrings = false;
      RE2 re = null;
      RE2 refull = null;
      int nfail = 0;
      int ncase = 0;
      String line;

      while ((line = r.readLine()) != null) {
        lineno++;
        if (line.isEmpty()) {
          throw new AssertionError(
              String.format("%s:%d: unexpected blank line", displayFile, lineno));
        }

        char first = line.charAt(0);
        if (first == '#') {
          continue;
        }
        if ('A' <= first && first <= 'Z') {
          continue;
        } else if (line.equals("strings")) {
          if (input < strings.size()) {
            throw new AssertionError(String.format(
                "%s:%d: out of sync: have %d strings left",
                displayFile, lineno, strings.size() - input));
          }
          strings.clear();
          inStrings = true;
        } else if (line.equals("regexps")) {
          inStrings = false;
        } else if (first == '"') {
          String q;
          try {
            q = Strconv.unquote(line);
          } catch (Exception e) {
            throw new AssertionError(String.format(
                "%s:%d: unquote %s: %s", displayFile, lineno, line, e.getMessage()));
          }
          if (inStrings) {
            strings.add(q);
            continue;
          }
          re = refull = null;
          try {
            re = RE2.compile(q);
          } catch (Throwable e) {
            if (e.getMessage().equals(
                "error parsing regexp: invalid escape sequence: `\\C`")) {
              continue;
            }
            System.err.format("%s:%d: compile %s: %s%n",
                displayFile, lineno, q, e.getMessage());
            if (++nfail >= 100) {
              throw new AssertionError("stopping after " + nfail + " errors");
            }
            continue;
          }
          String full = "\\A(?:" + q + ")\\z";
          try {
            refull = RE2.compile(full);
          } catch (Throwable e) {
            throw new AssertionError(String.format(
                "%s:%d: compile full %s: %s", displayFile, lineno, full, e.getMessage()));
          }
          input = 0;
        } else if (first == '-' || ('0' <= first && first <= '9')) {
          ncase++;
          if (re == null) {
            continue;
          }
          if (input >= strings.size()) {
            throw new AssertionError(String.format(
                "%s:%d: out of sync: no input remaining", displayFile, lineno));
          }
          String text = strings.get(input++);
          boolean multibyte = !isSingleBytes(text);
          if (multibyte && re.toString().contains("\\B")) {
            continue;
          }
          List<String> res = splitOnSemicolon(line);
          if (res.size() != 4) {
            throw new AssertionError(String.format(
                "%s:%d: have %d test results, want %d",
                displayFile, lineno, res.size(), 4));
          }
          for (int i = 0; i < 4; ++i) {
            boolean partial = (i & 1) != 0;
            boolean longest = (i & 2) != 0;
            RE2 regexp = partial ? re : refull;

            regexp.longest = longest;
            int[] have = regexp.findSubmatchIndex(text);
            if (multibyte && have != null) {
              have = utf16IndicesToUtf8(have, text);
            }
            int[] want = parseResult(displayFile, lineno, res.get(i));
            if (!Arrays.equals(want, have)) {
              System.err.format(
                  "%s:%d: %s[partial=%b,longest=%b].findSubmatchIndex(%s) = %s, want %s%n",
                  displayFile, lineno, re, partial, longest, text,
                  Arrays.toString(have), Arrays.toString(want));
              if (++nfail >= 100) {
                throw new AssertionError("stopping after " + nfail + " errors");
              }
              continue;
            }

            regexp.longest = longest;
            boolean b = regexp.match(text);
            if (b != (want != null)) {
              System.err.format(
                  "%s:%d: %s[partial=%b,longest=%b].match(%s) = %b, want %b%n",
                  displayFile, lineno, re, partial, longest, text, b, !b);
              if (++nfail >= 100) {
                throw new AssertionError("stopping after " + nfail + " errors");
              }
            }
          }
        } else {
          throw new AssertionError(String.format(
              "%s:%d: out of sync: %s%n", displayFile, lineno, line));
        }
      }

      if (input < strings.size()) {
        throw new AssertionError(String.format(
            "%s:%d: out of sync: have %d strings left at EOF",
            displayFile, lineno, strings.size() - input));
      }

      if (nfail > 0) {
        throw new AssertionError(String.format(
            "Of %d cases tested, %d failed", ncase, nfail));
      } else {
        System.err.format("%d cases tested%n", ncase);
      }
    } catch (IOException e) {
      throw new AssertionError("IO error: " + e.getMessage());
    }
  }

  private static final RE2 NOTAB = RE2.compilePOSIX("[^\t]+");

  private static void runFowlerTestFile(String file) {
    try {
      InputStream in = ExecTest.class.getResourceAsStream("/" + file);
      if (in == null) {
        System.err.println("Skipping " + file + ": resource not found");
        return;
      }
      UNIXBufferedReader r = new UNIXBufferedReader(new InputStreamReader(in, "UTF-8"));
      int lineno = 0;
      int nerr = 0;
      String line;
      String lastRegexp = "";

      while ((line = r.readLine()) != null) {
        lineno++;
        if (line.isEmpty() || line.charAt(0) == '#') {
          continue;
        }
        List<String> field = NOTAB.findAll(line, -1);
        for (int i = 0; i < field.size(); ++i) {
          if (field.get(i).equals("NULL")) {
            field.set(i, "");
          }
          if (field.get(i).equals("NIL")) {
            continue;
          }
        }
        if (field.isEmpty()) {
          continue;
        }

        String flag = field.get(0);
        switch (flag.charAt(0)) {
          case '?':
          case '&':
          case '|':
          case ';':
          case '{':
          case '}':
            flag = flag.substring(1);
            if (flag.isEmpty()) {
              continue;
            }
            break;
          case ':': {
            int i = flag.indexOf(':', 1);
            if (i < 0) {
              continue;
            }
            flag = flag.substring(1 + i + 1);
            break;
          }
          case 'C':
          case 'N':
          case 'T':
          case '0':
          case '1':
          case '2':
          case '3':
          case '4':
          case '5':
          case '6':
          case '7':
          case '8':
          case '9':
            continue;
        }

        if (field.size() < 4) {
          System.err.format("%s:%d: too few fields: %s%n", file, lineno, line);
          nerr++;
          continue;
        }

        if (flag.indexOf('$') >= 0) {
          String f = "\"" + field.get(1) + "\"";
          try {
            field.set(1, Strconv.unquote(f));
          } catch (Exception e) {
            System.err.format("%s:%d: cannot unquote %s%n", file, lineno, f);
            nerr++;
          }
          f = "\"" + field.get(2) + "\"";
          try {
            field.set(2, Strconv.unquote(f));
          } catch (Exception e) {
            System.err.format("%s:%d: cannot unquote %s%n", file, lineno, f);
            nerr++;
          }
        }

        if (field.get(1).equals("SAME")) {
          field.set(1, lastRegexp);
        }
        lastRegexp = field.get(1);

        String text = field.get(2);

        boolean[] shouldCompileMatch = { false, false };
        List<Integer> pos;
        try {
          pos = parseFowlerResult(field.get(3), shouldCompileMatch);
        } catch (Exception e) {
          System.err.format("%s:%d: cannot parse result %s%n", file, lineno, field.get(3));
          nerr++;
          continue;
        }

        for (int i = 0; i < flag.length(); i++) {
          String pattern = field.get(1);
          int flags = RE2.POSIX | RE2.CLASS_NL;
          switch (flag.charAt(i)) {
            default:
              continue;
            case 'E':
              break;
            case 'L':
              pattern = RE2.quoteMeta(pattern);
          }

          if (flag.indexOf('i') >= 0) {
            flags |= RE2.FOLD_CASE;
          }

          RE2 re = null;
          try {
            re = RE2.compileImpl(pattern, flags, true);
          } catch (PatternSyntaxException e) {
            if (shouldCompileMatch[0]) {
              System.err.format("%s:%d: %s did not compile%n", file, lineno, pattern);
              nerr++;
            }
            continue;
          }
          if (!shouldCompileMatch[0]) {
            System.err.format("%s:%d: %s should not compile%n", file, lineno, pattern);
            nerr++;
            continue;
          }
          boolean match = re.match(text);
          if (match != shouldCompileMatch[1]) {
            System.err.format("%s:%d: %s.match(%s) = %s, want %s%n",
                file, lineno, pattern, text, match, !match);
            nerr++;
            continue;
          }
          int[] haveArray = re.findSubmatchIndex(text);
          if (haveArray == null) {
            haveArray = Utils.EMPTY_INTS;
          }
          if ((haveArray.length > 0) != match) {
            System.err.format(
                "%s:%d: %s.match(%s) = %s, but %s.findSubmatchIndex(%s) = %s%n",
                file, lineno, pattern, text, match, pattern, text, Arrays.toString(haveArray));
            nerr++;
            continue;
          }
          List<Integer> have = new ArrayList<>();
          for (int j = 0; j < pos.size(); ++j) {
            have.add(haveArray[j]);
          }
          if (!have.equals(pos)) {
            System.err.format("%s:%d: %s.findSubmatchIndex(%s) = %s, want %s%n",
                file, lineno, pattern, text, have, pos);
            nerr++;
          }
        }
      }

      if (nerr > 0) {
        throw new AssertionError("There were " + nerr + " errors");
      }
    } catch (IOException e) {
      throw new AssertionError("IO error: " + e.getMessage());
    }
  }

  private static boolean isSingleBytes(String s) {
    for (int i = 0, len = s.length(); i < len; ++i) {
      if (s.charAt(i) >= 0x80) {
        return false;
      }
    }
    return true;
  }

  private static int[] utf16IndicesToUtf8(int[] idx16, String text) {
    try {
      int[] idx8 = new int[idx16.length];
      for (int i = 0; i < idx16.length; ++i) {
        idx8[i] = text.substring(0, idx16[i]).getBytes("UTF-8").length;
      }
      return idx8;
    } catch (java.io.UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static List<String> splitOnSemicolon(String s) {
    List<String> result = new ArrayList<>();
    int start = 0;
    for (int i = 0; i <= s.length(); i++) {
      if (i == s.length() || s.charAt(i) == ';') {
        result.add(s.substring(start, i));
        start = i + 1;
      }
    }
    return result;
  }

  private static int[] parseResult(String file, int lineno, String res) {
    if (res.equals("-")) {
      return null;
    }
    int n = 1;
    int len = res.length();
    for (int j = 0; j < len; j++) {
      if (res.charAt(j) == ' ') {
        n++;
      }
    }
    int[] out = new int[2 * n];
    int i = 0;
    n = 0;
    for (int j = 0; j <= len; j++) {
      if (j == len || res.charAt(j) == ' ') {
        String pair = res.substring(i, j);
        if (pair.equals("-")) {
          out[n++] = -1;
          out[n++] = -1;
        } else {
          int k = pair.indexOf('-');
          if (k < 0) {
            throw new AssertionError(String.format(
                "%s:%d: invalid pair %s", file, lineno, pair));
          }
          int lo = -1;
          int hi = -2;
          try {
            lo = Integer.parseInt(pair.substring(0, k));
            hi = Integer.parseInt(pair.substring(k + 1));
          } catch (NumberFormatException e) {

          }
          if (lo > hi) {
            throw new AssertionError(String.format(
                "%s:%d: invalid pair %s", file, lineno, pair));
          }
          out[n++] = lo;
          out[n++] = hi;
        }
        i = j + 1;
      }
    }
    return out;
  }

  private static List<Integer> parseFowlerResult(String s, boolean[] shouldCompileMatch) {
    if (s.isEmpty()) {
      shouldCompileMatch[0] = true;
      shouldCompileMatch[1] = true;
      return Collections.emptyList();
    } else if (s.equals("NOMATCH")) {
      shouldCompileMatch[0] = true;
      shouldCompileMatch[1] = false;
      return Collections.emptyList();
    } else if ('A' <= s.charAt(0) && s.charAt(0) <= 'Z') {
      shouldCompileMatch[0] = false;
      return Collections.emptyList();
    }
    shouldCompileMatch[0] = true;
    shouldCompileMatch[1] = true;

    List<Integer> result = new ArrayList<>();
    while (!s.isEmpty()) {
      char end = ')';
      if ((result.size() % 2) == 0) {
        if (s.charAt(0) != '(') {
          throw new RuntimeException("parse error: missing '('");
        }
        s = s.substring(1);
        end = ',';
      }
      int i = s.indexOf(end);
      if (i <= 0) {
        throw new RuntimeException("parse error: missing '" + end + "'");
      }
      String num = s.substring(0, i);
      if (!num.equals("?")) {
        result.add(Integer.valueOf(num));
      } else {
        result.add(-1);
      }
      s = s.substring(i + 1);
    }
    if ((result.size() % 2) != 0) {
      throw new RuntimeException("parse error: odd number of fields");
    }
    return result;
  }

  private static class Strconv {
    static String unquote(String s) {
      if (s.length() < 2) {
        throw new IllegalArgumentException("string too short");
      }
      char quote = s.charAt(0);
      if (quote != '"' && quote != '\'') {
        throw new IllegalArgumentException("invalid quote character");
      }
      if (s.charAt(s.length() - 1) != quote) {
        throw new IllegalArgumentException("mismatched quotes");
      }
      s = s.substring(1, s.length() - 1);
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (c != '\\') {
          sb.append(c);
          continue;
        }
        if (i + 1 >= s.length()) {
          throw new IllegalArgumentException("trailing backslash");
        }
        c = s.charAt(++i);
        switch (c) {
          case 'a':
            sb.append('\007');
            break;
          case 'b':
            sb.append('\b');
            break;
          case 'f':
            sb.append('\f');
            break;
          case 'n':
            sb.append('\n');
            break;
          case 'r':
            sb.append('\r');
            break;
          case 't':
            sb.append('\t');
            break;
          case 'v':
            sb.append('\013');
            break;
          case '\\':
            sb.append('\\');
            break;
          case '"':
            sb.append('"');
            break;
          case '\'':
            sb.append('\'');
            break;
          case '0':
          case '1':
          case '2':
          case '3':
          case '4':
          case '5':
          case '6':
          case '7': {
            int val = c - '0';
            for (int j = 0; j < 2 && i + 1 < s.length(); j++) {
              char next = s.charAt(i + 1);
              if (next < '0' || next > '7') {
                break;
              }
              val = val * 8 + (next - '0');
              i++;
            }
            sb.append((char) val);
            break;
          }
          case 'x': {
            if (i + 2 >= s.length()) {
              throw new IllegalArgumentException("invalid hex escape");
            }
            int val = hexDigit(s.charAt(++i)) << 4 | hexDigit(s.charAt(++i));
            sb.append((char) val);
            break;
          }
          case 'u': {
            if (i + 4 >= s.length()) {
              throw new IllegalArgumentException("invalid unicode escape");
            }
            int val = hexDigit(s.charAt(++i)) << 12
                | hexDigit(s.charAt(++i)) << 8
                | hexDigit(s.charAt(++i)) << 4
                | hexDigit(s.charAt(++i));
            sb.append((char) val);
            break;
          }
          default:
            sb.append(c);
        }
      }
      return sb.toString();
    }

    private static int hexDigit(char c) {
      if ('0' <= c && c <= '9') {
        return c - '0';
      }
      if ('a' <= c && c <= 'f') {
        return c - 'a' + 10;
      }
      if ('A' <= c && c <= 'F') {
        return c - 'A' + 10;
      }
      throw new IllegalArgumentException("invalid hex digit: " + c);
    }
  }

  private static class UNIXBufferedReader {
    private final Reader reader;
    private final char[] buffer = new char[8192];
    private int pos = 0;
    private int limit = 0;

    UNIXBufferedReader(Reader reader) {
      this.reader = reader;
    }

    String readLine() throws IOException {
      StringBuilder sb = null;
      while (true) {
        if (pos >= limit) {
          limit = reader.read(buffer);
          pos = 0;
          if (limit <= 0) {
            return sb == null ? null : sb.toString();
          }
        }
        int start = pos;
        while (pos < limit) {
          if (buffer[pos] == '\n') {
            String line;
            if (sb == null) {
              line = new String(buffer, start, pos - start);
            } else {
              sb.append(buffer, start, pos - start);
              line = sb.toString();
            }
            pos++;
            return line;
          }
          pos++;
        }
        if (sb == null) {
          sb = new StringBuilder();
        }
        sb.append(buffer, start, pos - start);
      }
    }
  }
}