package rules;

import re2j.Pattern;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class StringRules {

  private static final String NULL_MSG = "Value is null";
  private static final int MAX_REGEX_INPUT_LENGTH = 10_000;
  private static final int MAX_CACHED_PATTERNS = 256;

  private static final class PatternCache {
    private final int maxSize;
    private final Map<String, Pattern> cache;

    PatternCache(int maxSize) {
      this.maxSize = maxSize;
      this.cache = new LinkedHashMap<String, Pattern>(maxSize, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Pattern> eldest) {
          return size() > PatternCache.this.maxSize;
        }
      };
    }

    Pattern getOrCompile(String regex) {
      synchronized (cache) {
        Pattern cached = cache.get(regex);
        if (cached != null) {
          return cached;
        }
        Pattern compiled = Pattern.compile(regex);
        cache.put(regex, compiled);
        return compiled;
      }
    }

    void clear() {
      synchronized (cache) {
        cache.clear();
      }
    }

    int size() {
      synchronized (cache) {
        return cache.size();
      }
    }
  }

  private static final PatternCache PATTERN_CACHE = new PatternCache(MAX_CACHED_PATTERNS);

  private static final Pattern UUID_V4 = Pattern.compile(
      "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-"
          + "[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

  private static final Pattern UUID_V4_BRACES = Pattern.compile(
      "^\\{[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-"
          + "[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}}$");

  private static final Rule<String> NOT_NULL = value -> value != null
      ? ValidationResult.valid()
      : ValidationResult.invalid(NULL_MSG);

  private static final Rule<String> NOT_EMPTY = value -> {
    if (value == null) {
      return ValidationResult.invalid(NULL_MSG);
    }
    return !value.isEmpty()
        ? ValidationResult.valid()
        : ValidationResult.invalid("Value is empty");
  };

  private static final Rule<String> NOT_BLANK = value -> {
    if (value == null) {
      return ValidationResult.invalid(NULL_MSG);
    }
    return !value.isBlank()
        ? ValidationResult.valid()
        : ValidationResult.invalid("Value is blank");
  };

  private static final Rule<String> IS_UUID_V4 = value -> {
    if (value == null) {
      return ValidationResult.invalid(NULL_MSG);
    }
    if (value.length() > 38) {
      return ValidationResult.invalid("Value is not a valid UUID v4");
    }
    boolean valid = UUID_V4.matcher(value).matches()
        || UUID_V4_BRACES.matcher(value).matches();
    return valid
        ? ValidationResult.valid()
        : ValidationResult.invalid("Value is not a valid UUID v4");
  };

  private StringRules() {
  }

  private static Pattern getOrCompilePattern(String regex) {
    return PATTERN_CACHE.getOrCompile(regex);
  }

  public static void clearPatternCache() {
    PATTERN_CACHE.clear();
  }

  public static int patternCacheSize() {
    return PATTERN_CACHE.size();
  }

  public static Rule<String> notNull() {
    return NOT_NULL;
  }

  public static Rule<String> notEmpty() {
    return NOT_EMPTY;
  }

  public static Rule<String> notBlank() {
    return NOT_BLANK;
  }

  public static Rule<String> length(int exactLength) {
    if (exactLength < 0) {
      throw new IllegalArgumentException("length cannot be negative");
    }
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      return value.length() == exactLength
          ? ValidationResult.valid()
          : ValidationResult.invalid("Value length is incorrect");
    };
  }

  public static Rule<String> lengthBetween(int min, int max) {
    if (min < 0 || max < 0) {
      throw new IllegalArgumentException("length bounds cannot be negative");
    }
    if (min > max) {
      throw new IllegalArgumentException("min cannot exceed max");
    }
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      int len = value.length();
      return (len >= min && len <= max)
          ? ValidationResult.valid()
          : ValidationResult.invalid("Value length is out of range");
    };
  }

  public static Rule<String> minLength(int min) {
    if (min < 0) {
      throw new IllegalArgumentException("min cannot be negative");
    }
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      return value.length() >= min
          ? ValidationResult.valid()
          : ValidationResult.invalid("Value is too short");
    };
  }

  public static Rule<String> maxLength(int max) {
    if (max < 0) {
      throw new IllegalArgumentException("max cannot be negative");
    }
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      return value.length() <= max
          ? ValidationResult.valid()
          : ValidationResult.invalid("Value is too long");
    };
  }

  public static Rule<String> matches(String regex) {
    Objects.requireNonNull(regex, "regex cannot be null");
    Pattern pattern = getOrCompilePattern(regex);
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      if (value.length() > MAX_REGEX_INPUT_LENGTH) {
        return ValidationResult.invalid("Value exceeds maximum length");
      }
      return pattern.matcher(value).find()
          ? ValidationResult.valid()
          : ValidationResult.invalid("Value does not match pattern");
    };
  }

  public static Rule<String> matchesExactly(String regex) {
    Objects.requireNonNull(regex, "regex cannot be null");
    Pattern pattern = getOrCompilePattern(regex);
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      if (value.length() > MAX_REGEX_INPUT_LENGTH) {
        return ValidationResult.invalid("Value exceeds maximum length");
      }
      return pattern.matcher(value).matches()
          ? ValidationResult.valid()
          : ValidationResult.invalid("Value does not match pattern");
    };
  }

  public static Rule<String> matchesPattern(Pattern pattern) {
    Objects.requireNonNull(pattern, "pattern cannot be null");
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      if (value.length() > MAX_REGEX_INPUT_LENGTH) {
        return ValidationResult.invalid("Value exceeds maximum length");
      }
      return pattern.matcher(value).matches()
          ? ValidationResult.valid()
          : ValidationResult.invalid("Value does not match pattern");
    };
  }

  public static Rule<String> uuidV4() {
    return IS_UUID_V4;
  }

  public static Rule<String> startsWith(String prefix) {
    Objects.requireNonNull(prefix, "prefix cannot be null");
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      return value.startsWith(prefix)
          ? ValidationResult.valid()
          : ValidationResult.invalid("Value does not have expected prefix");
    };
  }

  public static Rule<String> endsWith(String suffix) {
    Objects.requireNonNull(suffix, "suffix cannot be null");
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      return value.endsWith(suffix)
          ? ValidationResult.valid()
          : ValidationResult.invalid("Value does not have expected suffix");
    };
  }

  public static Rule<String> contains(String substring) {
    Objects.requireNonNull(substring, "substring cannot be null");
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      return value.contains(substring)
          ? ValidationResult.valid()
          : ValidationResult.invalid("Value does not contain expected content");
    };
  }
}