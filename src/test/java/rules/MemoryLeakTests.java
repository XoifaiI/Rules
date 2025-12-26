package rules;

import static testing.Testing.describe;
import static testing.Testing.entry;
import static testing.Testing.expect;
import static testing.Testing.test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class MemoryLeakTests {

  private static final int GC_CYCLES = 5;
  private static final int ITERATIONS = 1000;
  private static final int LEAK_THRESHOLD_PERCENT = 10;

  private MemoryLeakTests() {
  }

  public static void main(String[] args) {
    entry(MemoryLeakTests::runAllTests);
  }

  private static void runAllTests() {
    describe("Memory Leak Chaos Tests", () -> {
      patternCacheTests();
      validationStateTests();
      ruleCompositionTests();
      compositeRulesTests();
      structRuleTests();
      collectionRulesTests();
      objectRulesTests();
    });
  }

  private static void patternCacheTests() {
    describe("Pattern Cache", () -> {
      test("cache bounded under pollution attack", () -> {
        StringRules.clearPatternCache();

        for (int i = 0; i < ITERATIONS; i++) {
          StringRules.matches("^attack_" + i + "$").validate("test");
        }

        int size = StringRules.patternCacheSize();
        int maxAllowed = 256 + 16;

        if (size > maxAllowed) {
          scream("CACHE OVERFLOW", size + " entries (max " + maxAllowed + ")");
        }

        StringRules.clearPatternCache();
      });

      test("cache clear releases all", () -> {
        StringRules.clearPatternCache();
        for (int i = 0; i < 100; i++) {
          StringRules.matches("pattern_" + i);
        }
        expect(StringRules.patternCacheSize() > 0).toBe(true);
        StringRules.clearPatternCache();
        expect(StringRules.patternCacheSize()).toBe(0);
      });

      test("concurrent cache access bounded", () -> {
        StringRules.clearPatternCache();
        int threadCount = 10;
        int patternsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
          final int threadId = t;
          executor.submit(() -> {
            try {
              for (int i = 0; i < patternsPerThread; i++) {
                StringRules.matches("^concurrent_" + threadId + "_" + i + "$")
                    .validate("test");
              }
            } catch (Throwable e) {
              errors.incrementAndGet();
            } finally {
              latch.countDown();
            }
          });
        }

        try {
          latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        executor.shutdownNow();

        int size = StringRules.patternCacheSize();
        int maxAllowed = 256 + threadCount;

        if (size > maxAllowed) {
          scream("CONCURRENT CACHE OVERFLOW", size + " entries");
        }
        if (errors.get() > 0) {
          scream("CONCURRENT CACHE ERRORS", errors.get() + " exceptions");
        }

        StringRules.clearPatternCache();
      });
    });
  }

  private static void validationStateTests() {
    describe("ValidationState", () -> {
      test("visited objects released after validation", () -> {
        ValidationContext ctx = ValidationContext.builder()
            .cycleDetection(true)
            .maxTrackedObjects(100)
            .build();

        List<WeakReference<Object>> refs = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
          Object obj = new HashMap<String, String>();
          refs.add(new WeakReference<>(obj));
          ctx.validate(obj, ObjectRules.notNull());
        }

        forceGc();
        assertLeakThreshold(refs, 50, "VALIDATION STATE");
      });

      test("cleanup after exception", () -> {
        ValidationContext ctx = ValidationContext.builder()
            .cycleDetection(true)
            .failSafe(true)
            .build();

        List<WeakReference<Object>> refs = new ArrayList<>();
        Rule<Object> exploder = v -> { throw new RuntimeException("boom"); };

        for (int i = 0; i < ITERATIONS; i++) {
          Object obj = new HashMap<>();
          refs.add(new WeakReference<>(obj));
          ctx.validate(obj, exploder);
        }

        forceGc();
        assertLeakThreshold(refs, ITERATIONS, "EXCEPTION PATH");
      });

      test("cleanup after timeout", () -> {
        ValidationContext ctx = ValidationContext.builder()
            .cycleDetection(true)
            .timeout(java.time.Duration.ofNanos(1))
            .failSafe(true)
            .build();

        List<WeakReference<Object>> refs = new ArrayList<>();
        Rule<Object> slow = v -> {
          try { Thread.sleep(10); } catch (InterruptedException e) {}
          return ValidationResult.valid();
        };

        for (int i = 0; i < 100; i++) {
          Object obj = new HashMap<>();
          refs.add(new WeakReference<>(obj));
          ctx.validate(obj, slow);
        }

        forceGc();
        assertLeakThreshold(refs, 100, "TIMEOUT PATH", 15);
      });

      test("deep nesting cleanup", () -> {
        ValidationContext ctx = ValidationContext.builder()
            .maxDepth(32)
            .cycleDetection(true)
            .build();

        List<WeakReference<Map<String, Object>>> refs = new ArrayList<>();

        for (int iter = 0; iter < 20; iter++) {
          Map<String, Object> root = new HashMap<>();
          Map<String, Object> current = root;
          for (int d = 0; d < 10; d++) {
            Map<String, Object> child = new HashMap<>();
            if (iter == 0) {
              refs.add(new WeakReference<>(child));
            }
            current.put("n", child);
            current = child;
          }
          ctx.validateTyped(root, CollectionRules.mapNotEmpty());
        }

        forceGc();
        assertLeakThreshold(refs, 10, "DEEP NESTING", 2);
      });

      test("array resize does not leak old array", () -> {
        ValidationContext ctx = ValidationContext.builder()
            .cycleDetection(true)
            .maxTrackedObjects(1000)
            .build();

        List<WeakReference<Object>> refs = new ArrayList<>();

        // Force multiple array resizes by validating many objects
        for (int i = 0; i < 500; i++) {
          Object obj = new HashMap<>();
          refs.add(new WeakReference<>(obj));

          // Deep validation to trigger enterScope/exitScope
          Rule<Object> rule = ObjectRules.notNull();
          ctx.validate(obj, rule);
        }

        forceGc();
        assertLeakThreshold(refs, 500, "ARRAY RESIZE");
      });
    });
  }

  private static void ruleCompositionTests() {
    describe("Rule Composition", () -> {
      test("and() chain no retention", () -> {
        List<WeakReference<String>> refs = new ArrayList<>();
        Rule<String> rule = StringRules.notEmpty()
            .and(StringRules.minLength(1))
            .and(StringRules.maxLength(100));

        for (int i = 0; i < ITERATIONS; i++) {
          String s = "target_" + i + "_" + System.nanoTime();
          refs.add(new WeakReference<>(s));
          rule.validate(s);
        }

        forceGc();
        assertLeakThreshold(refs, ITERATIONS, "AND CHAIN");
      });

      test("or() chain no retention", () -> {
        List<WeakReference<String>> refs = new ArrayList<>();
        Rule<String> rule = StringRules.length(5)
            .or(StringRules.length(10))
            .or(StringRules.length(15));

        for (int i = 0; i < ITERATIONS; i++) {
          String s = "or_target_" + i + "_" + System.nanoTime();
          refs.add(new WeakReference<>(s));
          rule.validate(s);
        }

        forceGc();
        assertLeakThreshold(refs, ITERATIONS, "OR CHAIN");
      });

      test("negate() no retention", () -> {
        List<WeakReference<String>> refs = new ArrayList<>();
        Rule<String> rule = StringRules.notEmpty().negate("should be empty");

        for (int i = 0; i < ITERATIONS; i++) {
          String s = "negate_" + i + "_" + System.nanoTime();
          refs.add(new WeakReference<>(s));
          rule.validate(s);
        }

        forceGc();
        assertLeakThreshold(refs, ITERATIONS, "NEGATE");
      });

      test("compose() no retention", () -> {
        List<WeakReference<String>> refs = new ArrayList<>();
        Rule<String> rule = NumberRules.positive().compose((String s) -> s.length());

        for (int i = 0; i < 500; i++) {
          String s = "x".repeat(i + 1) + System.nanoTime();
          refs.add(new WeakReference<>(s));
          rule.validate(s);
        }

        forceGc();
        assertLeakThreshold(refs, 500, "COMPOSE");
      });

      test("deeply chained rules no retention", () -> {
        List<WeakReference<String>> refs = new ArrayList<>();

        // Build a chain of 100 rules
        Rule<String> rule = StringRules.notEmpty();
        for (int i = 0; i < 99; i++) {
          rule = rule.and(StringRules.minLength(1));
        }

        for (int i = 0; i < 500; i++) {
          String s = "deep_chain_" + i + "_" + System.nanoTime();
          refs.add(new WeakReference<>(s));
          rule.validate(s);
        }

        forceGc();
        assertLeakThreshold(refs, 500, "DEEP CHAIN");
      });
    });
  }

  private static void compositeRulesTests() {
    describe("CompositeRules", () -> {
      test("all() no retention", () -> {
        List<WeakReference<String>> refs = new ArrayList<>();

        Rule<String> rule = CompositeRules.all(
            StringRules.notEmpty(),
            StringRules.minLength(1),
            StringRules.maxLength(1000));

        for (int i = 0; i < ITERATIONS; i++) {
          String s = "all_" + i + "_" + System.nanoTime();
          refs.add(new WeakReference<>(s));
          rule.validate(s);
        }

        forceGc();
        assertLeakThreshold(refs, ITERATIONS, "COMPOSITE ALL");
      });

      test("any() firstFailure no retention", () -> {
        List<WeakReference<String>> refs = new ArrayList<>();

        Rule<String> rule = CompositeRules.any(
            StringRules.length(1),
            StringRules.length(2),
            StringRules.length(3));

        for (int i = 0; i < ITERATIONS; i++) {
          String s = "any_fail_" + i + "_" + System.nanoTime();
          refs.add(new WeakReference<>(s));
          rule.validate(s); // All fail
        }

        forceGc();
        assertLeakThreshold(refs, ITERATIONS, "COMPOSITE ANY");
      });

      test("none() no retention", () -> {
        List<WeakReference<String>> refs = new ArrayList<>();

        Rule<String> rule = CompositeRules.none(
            StringRules.length(1),
            StringRules.length(2));

        for (int i = 0; i < ITERATIONS; i++) {
          String s = "none_" + i + "_" + System.nanoTime();
          refs.add(new WeakReference<>(s));
          rule.validate(s);
        }

        forceGc();
        assertLeakThreshold(refs, ITERATIONS, "COMPOSITE NONE");
      });

      test("when/whenElse no retention", () -> {
        List<WeakReference<String>> refs = new ArrayList<>();

        Rule<String> whenRule = CompositeRules.when(
            StringRules.minLength(5),
            StringRules.maxLength(100));

        Rule<String> whenElseRule = CompositeRules.whenElse(
            StringRules.minLength(10),
            StringRules.maxLength(50),
            StringRules.maxLength(200));

        for (int i = 0; i < ITERATIONS; i++) {
          String s = "when_" + i + "_" + System.nanoTime();
          refs.add(new WeakReference<>(s));
          whenRule.validate(s);
          whenElseRule.validate(s);
        }

        forceGc();
        assertLeakThreshold(refs, ITERATIONS, "WHEN/WHENELSE");
      });
    });
  }

  private static void structRuleTests() {
    describe("StructRule", () -> {
      test("map snapshot no retention", () -> {
        List<WeakReference<Map<String, Object>>> refs = new ArrayList<>();

        Rule<Map<String, Object>> rule = StructRule.<String, Object>builder()
            .field("name", ObjectRules.notNull())
            .field("value", ObjectRules.notNull())
            .build()
            .toRule();

        ValidationContext ctx = ValidationContext.builder()
            .cycleDetection(true)
            .build();

        for (int i = 0; i < ITERATIONS; i++) {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("name", "n" + i);
          m.put("value", i);
          refs.add(new WeakReference<>(m));
          ctx.validateTyped(m, rule);
        }

        forceGc();
        assertLeakThreshold(refs, ITERATIONS, "STRUCT SNAPSHOT");
      });

      test("strict mode no retention", () -> {
        List<WeakReference<Map<String, Object>>> refs = new ArrayList<>();

        Rule<Map<String, Object>> rule = StructRule.<String, Object>builder()
            .field("allowed", ObjectRules.notNull())
            .strict()
            .build()
            .toRule();

        for (int i = 0; i < ITERATIONS; i++) {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("allowed", "v" + i);
          refs.add(new WeakReference<>(m));
          ValidationContext.DEFAULT.validateTyped(m, rule);
        }

        forceGc();
        assertLeakThreshold(refs, ITERATIONS, "STRICT MODE");
      });

      test("nested struct rules no retention", () -> {
        List<WeakReference<Map<String, Object>>> refs = new ArrayList<>();

        Rule<Map<String, Object>> innerRule = StructRule.<String, Object>builder()
            .field("inner", ObjectRules.notNull())
            .build()
            .toRule();

        Rule<Map<String, Object>> outerRule = StructRule.<String, Object>builder()
            .field("outer", ObjectRules.notNull())
            .build()
            .toRule();

        Rule<Map<String, Object>> combined = outerRule.and(innerRule);

        for (int i = 0; i < ITERATIONS; i++) {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("outer", "o" + i);
          m.put("inner", "i" + i);
          refs.add(new WeakReference<>(m));
          combined.validate(m);
        }

        forceGc();
        assertLeakThreshold(refs, ITERATIONS, "NESTED STRUCT");
      });
    });
  }

  private static void collectionRulesTests() {
    describe("CollectionRules", () -> {
      test("allMatch no element retention", () -> {
        List<WeakReference<List<String>>> refs = new ArrayList<>();

        Rule<Collection<String>> rule = CollectionRules.allMatch(StringRules.notEmpty());

        ValidationContext ctx = ValidationContext.builder()
            .cycleDetection(true)
            .build();

        for (int i = 0; i < ITERATIONS; i++) {
          List<String> list = new ArrayList<>();
          list.add("item_" + i + "_" + System.nanoTime());
          list.add("item2_" + i);
          refs.add(new WeakReference<>(list));
          ctx.validateTyped(list, rule);
        }

        forceGc();
        assertLeakThreshold(refs, ITERATIONS, "ALLMATCH");
      });

      test("anyMatch no element retention", () -> {
        List<WeakReference<List<String>>> refs = new ArrayList<>();

        Rule<Collection<String>> rule = CollectionRules.anyMatch(StringRules.minLength(100));

        ValidationContext ctx = ValidationContext.builder()
            .cycleDetection(true)
            .build();

        for (int i = 0; i < ITERATIONS; i++) {
          List<String> list = new ArrayList<>();
          list.add("short" + i);
          refs.add(new WeakReference<>(list));
          ctx.validateTyped(list, rule);
        }

        forceGc();
        assertLeakThreshold(refs, ITERATIONS, "ANYMATCH");
      });

      test("noneMatch no element retention", () -> {
        List<WeakReference<List<String>>> refs = new ArrayList<>();

        Rule<Collection<String>> rule = CollectionRules.noneMatch(StringRules.length(1));

        ValidationContext ctx = ValidationContext.builder()
            .cycleDetection(true)
            .build();

        for (int i = 0; i < ITERATIONS; i++) {
          List<String> list = new ArrayList<>();
          list.add("longer" + i);
          refs.add(new WeakReference<>(list));
          ctx.validateTyped(list, rule);
        }

        forceGc();
        assertLeakThreshold(refs, ITERATIONS, "NONEMATCH");
      });

      test("large collection no retention", () -> {
        List<WeakReference<List<String>>> refs = new ArrayList<>();

        Rule<Collection<String>> rule = CollectionRules.allMatch(StringRules.notEmpty());

        ValidationContext ctx = ValidationContext.builder()
            .cycleDetection(true)
            .maxCollectionSize(10000)
            .build();

        for (int i = 0; i < 50; i++) {
          List<String> list = new ArrayList<>();
          for (int j = 0; j < 1000; j++) {
            list.add("large_" + i + "_" + j);
          }
          refs.add(new WeakReference<>(list));
          ctx.validateTyped(list, rule);
        }

        forceGc();
        assertLeakThreshold(refs, 50, "LARGE COLLECTION", 5);
      });

      test("map allKeys/allValues no retention", () -> {
        List<WeakReference<Map<String, Integer>>> refs = new ArrayList<>();

        Rule<Map<String, Integer>> rule = CollectionRules.<String, Integer>allKeys(
            StringRules.notEmpty());

        ValidationContext ctx = ValidationContext.builder()
            .cycleDetection(true)
            .build();

        for (int i = 0; i < ITERATIONS; i++) {
          Map<String, Integer> m = new HashMap<>();
          m.put("key_" + i, i);
          refs.add(new WeakReference<>(m));
          ctx.validateTyped(m, rule);
        }

        forceGc();
        assertLeakThreshold(refs, ITERATIONS, "MAP KEYS");
      });
    });
  }

  private static void objectRulesTests() {
    describe("ObjectRules", () -> {
      test("secureEquals string no input retention", () -> {
        List<WeakReference<String>> refs = new ArrayList<>();

        Rule<String> rule = ObjectRules.secureEquals("secret");

        for (int i = 0; i < ITERATIONS; i++) {
          String s = "attempt_" + i + "_" + System.nanoTime();
          refs.add(new WeakReference<>(s));
          rule.validate(s);
        }

        forceGc();
        assertLeakThreshold(refs, ITERATIONS, "SECURE EQUALS STRING");
      });

      test("secureEqualsBytes no input retention", () -> {
        List<WeakReference<byte[]>> refs = new ArrayList<>();

        Rule<byte[]> rule = ObjectRules.secureEqualsBytes("secret".getBytes());

        for (int i = 0; i < ITERATIONS; i++) {
          byte[] b = ("attempt_" + i + "_" + System.nanoTime()).getBytes();
          refs.add(new WeakReference<>(b));
          rule.validate(b);
        }

        forceGc();
        assertLeakThreshold(refs, ITERATIONS, "SECURE EQUALS BYTES");
      });

      test("oneOf set no input retention", () -> {
        List<WeakReference<String>> refs = new ArrayList<>();

        Rule<String> rule = ObjectRules.oneOf("a", "b", "c");

        for (int i = 0; i < ITERATIONS; i++) {
          String s = "notinset_" + i + "_" + System.nanoTime();
          refs.add(new WeakReference<>(s));
          rule.validate(s);
        }

        forceGc();
        assertLeakThreshold(refs, ITERATIONS, "ONEOF");
      });

      test("instanceOf no input retention", () -> {
        List<WeakReference<Object>> refs = new ArrayList<>();

        Rule<Object> rule = ObjectRules.instanceOf(String.class);

        for (int i = 0; i < ITERATIONS; i++) {
          Object obj = new HashMap<>(); // Not a String
          refs.add(new WeakReference<>(obj));
          rule.validate(obj);
        }

        forceGc();
        assertLeakThreshold(refs, ITERATIONS, "INSTANCEOF");
      });

      test("optional rule no retention", () -> {
        List<WeakReference<String>> refs = new ArrayList<>();

        Rule<String> rule = ObjectRules.optional(StringRules.notEmpty());

        for (int i = 0; i < ITERATIONS; i++) {
          String s = "optional_" + i + "_" + System.nanoTime();
          refs.add(new WeakReference<>(s));
          rule.validate(s);
        }

        forceGc();
        assertLeakThreshold(refs, ITERATIONS, "OPTIONAL");
      });

      test("equalTo/notEqualTo no retention", () -> {
        List<WeakReference<String>> refs = new ArrayList<>();

        Rule<String> eqRule = ObjectRules.equalTo("match");
        Rule<String> neqRule = ObjectRules.notEqualTo("nomatch");

        for (int i = 0; i < ITERATIONS; i++) {
          String s = "test_" + i + "_" + System.nanoTime();
          refs.add(new WeakReference<>(s));
          eqRule.validate(s);
          neqRule.validate(s);
        }

        forceGc();
        assertLeakThreshold(refs, ITERATIONS, "EQUALTO");
      });
    });
  }

  private static void forceGc() {
    for (int i = 0; i < GC_CYCLES; i++) {
      System.gc();
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  private static <T> int countSurviving(List<WeakReference<T>> refs) {
    int count = 0;
    for (WeakReference<T> ref : refs) {
      if (ref.get() != null) {
        count++;
      }
    }
    return count;
  }

  private static <T> void assertLeakThreshold(
      List<WeakReference<T>> refs, int total, String name) {
    int maxAllowed = (total * LEAK_THRESHOLD_PERCENT) / 100;
    assertLeakThreshold(refs, total, name, maxAllowed);
  }

  private static <T> void assertLeakThreshold(
      List<WeakReference<T>> refs, int total, String name, int maxAllowed) {
    int surviving = countSurviving(refs);
    if (surviving > maxAllowed) {
      scream(name, surviving + " of " + total + " retained (max " + maxAllowed + ")");
    }
  }

  private static void scream(String type, String details) {
    String banner = "!".repeat(60);
    System.err.println();
    System.err.println(banner);
    System.err.println("!!! MEMORY LEAK DETECTED: " + type);
    System.err.println("!!! " + details);
    System.err.println(banner);
    System.err.println();
    throw new AssertionError("MEMORY LEAK: " + type + " - " + details);
  }
}