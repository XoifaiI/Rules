package re2j;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class RE2 {
  public static final int FOLD_CASE = 0x01;
  public static final int LITERAL = 0x02;
  public static final int CLASS_NL = 0x04;
  public static final int DOT_NL = 0x08;
  public static final int ONE_LINE = 0x10;
  public static final int NON_GREEDY = 0x20;
  public static final int PERL_X = 0x40;
  public static final int UNICODE_GROUPS = 0x80;
  public static final int WAS_DOLLAR = 0x100;
  public static final int MATCH_NL = CLASS_NL | DOT_NL;
  public static final int PERL = CLASS_NL | ONE_LINE | PERL_X | UNICODE_GROUPS;
  public static final int POSIX = 0;

  public static final int UNANCHORED = 0;
  public static final int ANCHOR_START = 1;
  public static final int ANCHOR_BOTH = 2;

  private static final int POOL_STRIPE_COUNT = 64;
  private static final int POOL_STRIPE_MASK = POOL_STRIPE_COUNT - 1;
  private static final int MAX_POOL_SIZE_PER_STRIPE = 8;

  final String expr;
  final Prog prog;
  final int cond;
  final int numSubexp;
  boolean longest;

  String prefix;
  byte[] prefixUTF8;
  boolean prefixComplete;
  int prefixRune;

  @SuppressWarnings("unchecked")
  private final AtomicReference<Machine>[] stripedPools = new AtomicReference[POOL_STRIPE_COUNT];

  private final AtomicInteger[] poolSizes = new AtomicInteger[POOL_STRIPE_COUNT];
  Map<String, Integer> namedGroups;

  public RE2(String expr) {
    var re2 = compile(expr);
    this.expr = re2.expr;
    this.prog = re2.prog;
    this.cond = re2.cond;
    this.numSubexp = re2.numSubexp;
    this.longest = re2.longest;
    this.prefix = re2.prefix;
    this.prefixUTF8 = re2.prefixUTF8;
    this.prefixComplete = re2.prefixComplete;
    this.prefixRune = re2.prefixRune;
    this.namedGroups = re2.namedGroups;
    initPools();
  }

  private RE2(String expr, Prog prog, int numSubexp, boolean longest) {
    this.expr = expr;
    this.prog = prog;
    this.numSubexp = numSubexp;
    this.cond = prog.startCond();
    this.longest = longest;
    initPools();
  }

  private void initPools() {
    for (int index = 0; index < POOL_STRIPE_COUNT; index++) {
      stripedPools[index] = new AtomicReference<>();
      poolSizes[index] = new AtomicInteger(0);
    }
  }

  public static RE2 compile(String expr) throws PatternSyntaxException {
    return compileImpl(expr, PERL, false);
  }

  public static RE2 compilePOSIX(String expr) throws PatternSyntaxException {
    return compileImpl(expr, POSIX, true);
  }

  public static RE2 compileImpl(String expr, int mode, boolean longest)
      throws PatternSyntaxException {
    var regexp = Parser.parse(expr, mode);
    int maxCap = regexp.maxCap();
    regexp = Simplify.simplify(regexp);
    var prog = Compiler.compileRegexp(regexp);
    var re2 = new RE2(expr, prog, maxCap, longest);
    var prefixBuilder = new StringBuilder();
    re2.prefixComplete = prog.prefix(prefixBuilder);
    re2.prefix = prefixBuilder.toString();
    re2.prefixUTF8 = re2.prefix.getBytes(StandardCharsets.UTF_8);
    if (!re2.prefix.isEmpty()) {
      re2.prefixRune = re2.prefix.codePointAt(0);
    }
    re2.namedGroups = regexp.namedGroups;
    return re2;
  }

  public int numberOfCapturingGroups() {
    return numSubexp;
  }

  int numberOfInstructions() {
    return prog.numInst();
  }

  private int stripeIndex() {
    return (int) (Thread.currentThread().threadId() & POOL_STRIPE_MASK);
  }

  Machine get() {
    int index = stripeIndex();
    AtomicReference<Machine> pool = stripedPools[index];
    Machine head;
    Machine next;
    int spinCount = 0;
    do {
      head = pool.get();
      if (head == null) {
        return null;
      }
      next = head.next;
      if (++spinCount > 100) {
        return null;
      }
    } while (!pool.compareAndSet(head, next));
    head.next = null;
    poolSizes[index].decrementAndGet();
    return head;
  }

  void reset() {
    for (int index = 0; index < POOL_STRIPE_COUNT; index++) {
      stripedPools[index].set(null);
      poolSizes[index].set(0);
    }
  }

  void put(Machine machine, boolean isNew) {
    int index = stripeIndex();
    if (poolSizes[index].get() >= MAX_POOL_SIZE_PER_STRIPE) {
      return;
    }
    if (!isNew) {
      machine = new Machine(machine);
    }
    AtomicReference<Machine> pool = stripedPools[index];
    Machine head;
    int spinCount = 0;
    do {
      head = pool.get();
      machine.next = head;
      if (++spinCount > 100) {
        return;
      }
    } while (!pool.compareAndSet(head, machine));
    poolSizes[index].incrementAndGet();
  }

  @Override
  public String toString() {
    return expr;
  }

  private int[] doExecute(MachineInput input, int position, int anchor, int numCaptures) {
    var machine = get();
    boolean isNew = false;
    if (machine == null) {
      machine = new Machine(this);
      isNew = true;
    }
    machine.init(numCaptures);
    int[] captures = machine.match(input, position, anchor) ? machine.submatches() : null;
    put(machine, isNew);
    return captures;
  }

  public boolean match(CharSequence charSequence) {
    return doExecute(MachineInput.fromUTF16(charSequence), 0, UNANCHORED, 0) != null;
  }

  public boolean match(
      CharSequence input, int start, int end, int anchor, int[] group, int numGroups) {
    return match(MatcherInput.utf16(input), start, end, anchor, group, numGroups);
  }

  public boolean match(
      MatcherInput input, int start, int end, int anchor, int[] group, int numGroups) {
    if (start > end) {
      return false;
    }
    var machineInput = input.getEncoding() == MatcherInput.Encoding.UTF_16
        ? MachineInput.fromUTF16(input.asCharSequence(), 0, end)
        : MachineInput.fromUTF8(input.asBytes(), 0, end);
    int[] groupMatch = doExecute(machineInput, start, anchor, 2 * numGroups);
    if (groupMatch == null) {
      return false;
    }
    if (group != null) {
      System.arraycopy(groupMatch, 0, group, 0, groupMatch.length);
    }
    return true;
  }

  public boolean matchUTF8(byte[] bytes) {
    return doExecute(MachineInput.fromUTF8(bytes), 0, UNANCHORED, 0) != null;
  }

  public static boolean match(String pattern, CharSequence charSequence)
      throws PatternSyntaxException {
    return compile(pattern).match(charSequence);
  }

  public interface ReplaceFunc {
    String replace(String orig);
  }

  public String replaceAll(String source, String replacement) {
    return replaceAllFunc(source, orig -> replacement, 2 * source.length() + 1);
  }

  public String replaceFirst(String source, String replacement) {
    return replaceAllFunc(source, orig -> replacement, 1);
  }

  public String replaceAllFunc(String source, ReplaceFunc replaceFunc, int maxReplaces) {
    int lastMatchEnd = 0;
    int searchPos = 0;
    var buffer = new StringBuilder();
    var input = MachineInput.fromUTF16(source);
    int numReplaces = 0;
    while (searchPos <= source.length()) {
      int[] matchBounds = doExecute(input, searchPos, UNANCHORED, 2);
      if (matchBounds == null || matchBounds.length == 0) {
        break;
      }
      buffer.append(source, lastMatchEnd, matchBounds[0]);
      if (matchBounds[1] > lastMatchEnd || matchBounds[0] == 0) {
        buffer.append(replaceFunc.replace(source.substring(matchBounds[0], matchBounds[1])));
        ++numReplaces;
      }
      lastMatchEnd = matchBounds[1];
      int width = input.step(searchPos) & 0x7;
      if (searchPos + width > matchBounds[1]) {
        searchPos += width;
      } else if (searchPos + 1 > matchBounds[1]) {
        searchPos++;
      } else {
        searchPos = matchBounds[1];
      }
      if (numReplaces >= maxReplaces) {
        break;
      }
    }
    buffer.append(source.substring(lastMatchEnd));
    return buffer.toString();
  }

  public static String quoteMeta(String string) {
    var builder = new StringBuilder(2 * string.length());
    for (int index = 0, length = string.length(); index < length; index++) {
      char character = string.charAt(index);
      if ("\\.+*?()|[]{}^$".indexOf(character) >= 0) {
        builder.append('\\');
      }
      builder.append(character);
    }
    return builder.toString();
  }

  private int[] pad(int[] matchArray) {
    if (matchArray == null) {
      return null;
    }
    int requiredLength = (1 + numSubexp) * 2;
    if (matchArray.length < requiredLength) {
      int[] paddedArray = new int[requiredLength];
      System.arraycopy(matchArray, 0, paddedArray, 0, matchArray.length);
      Arrays.fill(paddedArray, matchArray.length, requiredLength, -1);
      matchArray = paddedArray;
    }
    return matchArray;
  }

  private interface DeliverFunc {
    void deliver(int[] matchBounds);
  }

  private void allMatches(MachineInput input, int maxMatches, DeliverFunc deliver) {
    int endPosition = input.endPos();
    if (maxMatches < 0) {
      maxMatches = endPosition + 1;
    }
    for (int position = 0, matchCount = 0, prevMatchEnd = -1; matchCount < maxMatches && position <= endPosition;) {
      int[] matches = doExecute(input, position, UNANCHORED, prog.numCap);
      if (matches == null || matches.length == 0) {
        break;
      }
      boolean shouldAccept = true;
      if (matches[1] == position) {
        if (matches[0] == prevMatchEnd) {
          shouldAccept = false;
        }
        int stepResult = input.step(position);
        if (stepResult < 0) {
          position = endPosition + 1;
        } else {
          position += stepResult & 0x7;
        }
      } else {
        position = matches[1];
      }
      prevMatchEnd = matches[1];
      if (shouldAccept) {
        deliver.deliver(pad(matches));
        matchCount++;
      }
    }
  }

  public byte[] findUTF8(byte[] bytes) {
    int[] matchBounds = doExecute(MachineInput.fromUTF8(bytes), 0, UNANCHORED, 2);
    if (matchBounds == null) {
      return null;
    }
    return Utils.subarray(bytes, matchBounds[0], matchBounds[1]);
  }

  public int[] findUTF8Index(byte[] bytes) {
    int[] matchBounds = doExecute(MachineInput.fromUTF8(bytes), 0, UNANCHORED, 2);
    if (matchBounds == null) {
      return null;
    }
    return Utils.subarray(matchBounds, 0, 2);
  }

  public String find(String string) {
    int[] matchBounds = doExecute(MachineInput.fromUTF16(string), 0, UNANCHORED, 2);
    if (matchBounds == null) {
      return "";
    }
    return string.substring(matchBounds[0], matchBounds[1]);
  }

  public int[] findIndex(String string) {
    return doExecute(MachineInput.fromUTF16(string), 0, UNANCHORED, 2);
  }

  public byte[][] findUTF8Submatch(byte[] bytes) {
    int[] matchBounds = doExecute(MachineInput.fromUTF8(bytes), 0, UNANCHORED, prog.numCap);
    if (matchBounds == null) {
      return null;
    }
    byte[][] result = new byte[1 + numSubexp][];
    for (int groupIndex = 0; groupIndex < result.length; groupIndex++) {
      if (2 * groupIndex < matchBounds.length && matchBounds[2 * groupIndex] >= 0) {
        result[groupIndex] = Utils.subarray(bytes, matchBounds[2 * groupIndex], matchBounds[2 * groupIndex + 1]);
      }
    }
    return result;
  }

  public int[] findUTF8SubmatchIndex(byte[] bytes) {
    return pad(doExecute(MachineInput.fromUTF8(bytes), 0, UNANCHORED, prog.numCap));
  }

  public String[] findSubmatch(String string) {
    int[] matchBounds = doExecute(MachineInput.fromUTF16(string), 0, UNANCHORED, prog.numCap);
    if (matchBounds == null) {
      return null;
    }
    String[] result = new String[1 + numSubexp];
    for (int groupIndex = 0; groupIndex < result.length; groupIndex++) {
      if (2 * groupIndex < matchBounds.length && matchBounds[2 * groupIndex] >= 0) {
        result[groupIndex] = string.substring(matchBounds[2 * groupIndex], matchBounds[2 * groupIndex + 1]);
      }
    }
    return result;
  }

  public int[] findSubmatchIndex(String string) {
    return pad(doExecute(MachineInput.fromUTF16(string), 0, UNANCHORED, prog.numCap));
  }

  public List<byte[]> findAllUTF8(byte[] bytes, int maxMatches) {
    var result = new ArrayList<byte[]>();
    allMatches(
        MachineInput.fromUTF8(bytes),
        maxMatches,
        match -> result.add(Utils.subarray(bytes, match[0], match[1])));
    return result.isEmpty() ? null : result;
  }

  public List<int[]> findAllUTF8Index(byte[] bytes, int maxMatches) {
    var result = new ArrayList<int[]>();
    allMatches(
        MachineInput.fromUTF8(bytes), maxMatches, match -> result.add(Utils.subarray(match, 0, 2)));
    return result.isEmpty() ? null : result;
  }

  public List<String> findAll(String string, int maxMatches) {
    var result = new ArrayList<String>();
    allMatches(
        MachineInput.fromUTF16(string),
        maxMatches,
        match -> result.add(string.substring(match[0], match[1])));
    return result.isEmpty() ? null : result;
  }

  public List<int[]> findAllIndex(String string, int maxMatches) {
    var result = new ArrayList<int[]>();
    allMatches(
        MachineInput.fromUTF16(string), maxMatches, match -> result.add(Utils.subarray(match, 0, 2)));
    return result.isEmpty() ? null : result;
  }

  public List<byte[][]> findAllUTF8Submatch(byte[] bytes, int maxMatches) {
    var result = new ArrayList<byte[][]>();
    allMatches(
        MachineInput.fromUTF8(bytes),
        maxMatches,
        match -> {
          byte[][] slice = new byte[match.length / 2][];
          for (int groupIndex = 0; groupIndex < slice.length; ++groupIndex) {
            if (match[2 * groupIndex] >= 0) {
              slice[groupIndex] = Utils.subarray(bytes, match[2 * groupIndex], match[2 * groupIndex + 1]);
            }
          }
          result.add(slice);
        });
    return result.isEmpty() ? null : result;
  }

  public List<int[]> findAllUTF8SubmatchIndex(byte[] bytes, int maxMatches) {
    var result = new ArrayList<int[]>();
    allMatches(MachineInput.fromUTF8(bytes), maxMatches, result::add);
    return result.isEmpty() ? null : result;
  }

  public List<String[]> findAllSubmatch(String string, int maxMatches) {
    var result = new ArrayList<String[]>();
    allMatches(
        MachineInput.fromUTF16(string),
        maxMatches,
        match -> {
          String[] slice = new String[match.length / 2];
          for (int groupIndex = 0; groupIndex < slice.length; ++groupIndex) {
            if (match[2 * groupIndex] >= 0) {
              slice[groupIndex] = string.substring(match[2 * groupIndex], match[2 * groupIndex + 1]);
            }
          }
          result.add(slice);
        });
    return result.isEmpty() ? null : result;
  }

  public List<int[]> findAllSubmatchIndex(String string, int maxMatches) {
    var result = new ArrayList<int[]>();
    allMatches(MachineInput.fromUTF16(string), maxMatches, result::add);
    return result.isEmpty() ? null : result;
  }
}