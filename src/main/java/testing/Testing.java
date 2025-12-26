package testing;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

public final class Testing {

  private static final Deque<String> scopeStack = new ArrayDeque<>();
  private static final Deque<Runnable> deferredCleanupQueue = new ArrayDeque<>();

  private static final AtomicInteger testCount = new AtomicInteger(0);
  private static final AtomicInteger failedCount = new AtomicInteger(0);
  private static long suiteStartTimeNanos = 0;

  private static final String RESET = "\u001B[0m";
  private static final String GREEN = "\u001B[32m";
  private static final String RED = "\u001B[31m";
  private static final String YELLOW = "\u001B[38;5;179m";
  private static final String CYAN = "\u001B[36m";
  private static final String MAGENTA = "\u001B[35m";
  private static final String WHITE = "\u001B[37m";
  private static final String BOLD = "\u001B[1m";

  private static volatile boolean colorsEnabled;

  static {
    colorsEnabled = initializeColorSupport();
  }

  private Testing() {
  }

  private static boolean initializeColorSupport() {
    String noColor = System.getenv("NO_COLOR");
    if (noColor != null && !noColor.isEmpty()) {
      return false;
    }

    String forceColor = System.getenv("FORCE_COLOR");
    if (forceColor != null && !forceColor.isEmpty()) {
      return true;
    }

    if (System.console() == null) {
      String term = System.getenv("TERM_PROGRAM");
      if (term != null && term.toLowerCase().contains("vscode")) {
        return true;
      }
      return true;
    }

    return true;
  }

  public static void setColorsEnabled(boolean enabled) {
    colorsEnabled = enabled;
  }

  public static boolean isColorsEnabled() {
    return colorsEnabled;
  }

  private static String red(String text) {
    return colorsEnabled ? RED + text + RESET : text;
  }

  private static String yellow(String text) {
    return colorsEnabled ? YELLOW + text + RESET : text;
  }

  private static String cyan(String text) {
    return colorsEnabled ? CYAN + text + RESET : text;
  }

  private static String magenta(String text) {
    return colorsEnabled ? MAGENTA + text + RESET : text;
  }

  private static String white(String text) {
    return colorsEnabled ? WHITE + text + RESET : text;
  }

  private static String bold(String text) {
    return colorsEnabled ? BOLD + text + RESET : text;
  }

  private static String greenBold(String text) {
    return colorsEnabled ? GREEN + BOLD + text + RESET : text;
  }

  private static String redBold(String text) {
    return colorsEnabled ? RED + BOLD + text + RESET : text;
  }

  public static int getCount() {
    return testCount.get();
  }

  public static int getFailed() {
    return failedCount.get();
  }

  public static void entry(Runnable suiteCallback) {
    testCount.set(0);
    failedCount.set(0);
    suiteStartTimeNanos = System.nanoTime();

    try {
      suiteCallback.run();
    } finally {
      complete();
    }
  }

  public static void complete() {
    long elapsedNanos = System.nanoTime() - suiteStartTimeNanos;
    int totalTests = testCount.get();
    int failedTests = failedCount.get();

    System.out.println();
    if (failedTests > 0) {
      System.out.printf(" %s: %s, %d total%n",
          bold("Tests"),
          redBold(failedTests + " failed"),
          totalTests);
    } else {
      System.out.printf(" %s: %s%n",
          bold("Tests"),
          greenBold(totalTests + " passed"));
    }
    System.out.printf(" %s: %s%n", bold("Time"), formatDuration(elapsedNanos / 1000));
  }

  public static void describe(String suiteName, Runnable suiteCallback) {
    scopeStack.push(suiteName);
    try {
      suiteCallback.run();
    } catch (Throwable thrown) {
      failedCount.incrementAndGet();
      System.out.printf("%s Describe(\"%s\"): %s%n",
          redBold("ERROR"),
          yellow(suiteName),
          red(thrown.getMessage()));
    } finally {
      scopeStack.pop();
    }
  }

  public static void test(String testName, ThrowingRunnable testCallback) {
    testCount.incrementAndGet();

    if (suiteStartTimeNanos == 0) {
      suiteStartTimeNanos = System.nanoTime();
    }

    long testStartNanos = System.nanoTime();
    Throwable caughtError = null;
    StackTraceElement[] stackTrace = null;

    try {
      testCallback.run();
    } catch (Throwable thrown) {
      caughtError = thrown;
      stackTrace = thrown.getStackTrace();
    }

    long elapsedMicros = (System.nanoTime() - testStartNanos) / 1000;

    runDeferredCleanup();

    String formattedPath = buildColoredTestPath(testName);
    String formattedDuration = formatDuration(elapsedMicros);

    if (caughtError == null) {
      System.out.printf("%s %s [%s]%n",
          greenBold("PASS"),
          formattedPath,
          formattedDuration);
    } else {
      failedCount.incrementAndGet();
      System.err.printf("%s %s [%s]%n",
          redBold("FAIL"),
          formattedPath,
          formattedDuration);
      System.err.printf("  -> %s%n", red(caughtError.getMessage()));

      if (stackTrace != null) {
        printFilteredStackTrace(stackTrace);
      }
    }
  }

  public static void defer(Runnable cleanupCallback) {
    deferredCleanupQueue.push(cleanupCallback);
  }

  public static Expect expect(Object value) {
    return new Expect(value, false);
  }

  public static Expect expect(ThrowingRunnable runnable) {
    return new Expect(runnable, false);
  }

  public static void expected(Object value) {
    if (value == null || Boolean.FALSE.equals(value)) {
      throw new AssertionError("Truthy value expected");
    }
  }

  public static void expectEqual(Object expected, Object actual) {
    if (!java.util.Objects.equals(expected, actual)) {
      throw new AssertionError(String.format(
          "Expected %s to be equal to %s",
          Readable.format(expected),
          Readable.format(actual)));
    }
  }

  private static void runDeferredCleanup() {
    while (!deferredCleanupQueue.isEmpty()) {
      Runnable cleanup = deferredCleanupQueue.pop();
      try {
        cleanup.run();
      } catch (Throwable thrown) {
        System.err.printf("  %s %s%n",
            yellow("Cleanup failed:"),
            yellow(thrown.getMessage()));
      }
    }
  }

  private static String buildColoredTestPath(String testName) {
    StringBuilder pathBuilder = new StringBuilder();
    if (!scopeStack.isEmpty()) {
      String[] scopes = scopeStack.toArray(new String[0]);
      for (int index = scopes.length - 1; index >= 0; index--) {
        pathBuilder.append(cyan(scopes[index]));
        pathBuilder.append(magenta("/"));
      }
    }
    pathBuilder.append(yellow(testName));
    return pathBuilder.toString();
  }

  private static void printFilteredStackTrace(StackTraceElement[] stackTrace) {
    int relevantFrameCount = 0;
    for (StackTraceElement frame : stackTrace) {
      if (!frame.getClassName().startsWith("testing.")) {
        relevantFrameCount++;
      }
    }

    int printedFrames = 0;
    for (StackTraceElement frame : stackTrace) {
      if (frame.getClassName().startsWith("testing.")) {
        continue;
      }
      printedFrames++;
      String treePrefix = (printedFrames == relevantFrameCount) ? "  └─" : "  ├─";
      System.err.printf("%s %s%n",
          magenta(treePrefix),
          white(String.format("%s.%s(%s:%d)",
              frame.getClassName(),
              frame.getMethodName(),
              frame.getFileName(),
              frame.getLineNumber())));
    }
  }

  private static String formatDuration(long microseconds) {
    if (microseconds >= 1_000_000) {
      return String.format("%.2fs", microseconds / 1_000_000.0);
    } else if (microseconds >= 1_000) {
      return String.format("%.2fms", microseconds / 1_000.0);
    } else {
      return String.format("%dus", microseconds);
    }
  }

  @FunctionalInterface
  public interface ThrowingRunnable {
    void run() throws Throwable;
  }
}