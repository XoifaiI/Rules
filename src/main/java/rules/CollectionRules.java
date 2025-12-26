package rules;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public final class CollectionRules {

  private static final String NULL_MSG = "Value is null";

  private CollectionRules() {
  }

  public static <T> Rule<Collection<T>> notEmpty() {
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      return !value.isEmpty()
          ? ValidationResult.valid()
          : ValidationResult.invalid("Collection is empty");
    };
  }

  public static <T> Rule<Collection<T>> size(int exactSize) {
    if (exactSize < 0) {
      throw new IllegalArgumentException("size cannot be negative");
    }
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      return value.size() == exactSize
          ? ValidationResult.valid()
          : ValidationResult.invalid("Collection size is incorrect");
    };
  }

  public static <T> Rule<Collection<T>> sizeBetween(int min, int max) {
    if (min < 0 || max < 0) {
      throw new IllegalArgumentException("size bounds cannot be negative");
    }
    if (min > max) {
      throw new IllegalArgumentException("min cannot exceed max");
    }
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      int size = value.size();
      return (size >= min && size <= max)
          ? ValidationResult.valid()
          : ValidationResult.invalid("Collection size is out of range");
    };
  }

  public static <T> Rule<Collection<T>> minSize(int min) {
    if (min < 0) {
      throw new IllegalArgumentException("min cannot be negative");
    }
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      return value.size() >= min
          ? ValidationResult.valid()
          : ValidationResult.invalid("Collection is too small");
    };
  }

  public static <T> Rule<Collection<T>> maxSize(int max) {
    if (max < 0) {
      throw new IllegalArgumentException("max cannot be negative");
    }
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      return value.size() <= max
          ? ValidationResult.valid()
          : ValidationResult.invalid("Collection is too large");
    };
  }

  public static <T> Rule<Collection<T>> allMatch(Rule<T> elementRule) {
    Objects.requireNonNull(elementRule, "elementRule cannot be null");
    return new Rule<Collection<T>>() {
      @Override
      public ValidationResult validate(Collection<T> value) {
        if (value == null) {
          return ValidationResult.invalid(NULL_MSG);
        }
        for (T element : value) {
          ValidationResult result = elementRule.validate(element);
          if (result == null || result.isInvalid()) {
            return ValidationResult.invalid("Element validation failed");
          }
        }
        return ValidationResult.valid();
      }

      @Override
      public ValidationResult validateWithState(
          Collection<T> value, ValidationState state) {
        if (value == null) {
          return ValidationResult.invalid(NULL_MSG);
        }

        ValidationResult sizeCheck = state.checkCollectionSize(value.size());
        if (sizeCheck.isInvalid()) {
          return sizeCheck;
        }

        ValidationResult scopeCheck = state.enterScope(value);
        if (scopeCheck.isInvalid()) {
          return scopeCheck;
        }

        try {
          for (T element : value) {
            state.checkTimeout();
            ValidationResult result = elementRule.validateWithState(element, state);
            if (result == null || result.isInvalid()) {
              if (result != null && result.isSystemError()) {
                return result;
              }
              return ValidationResult.invalid(
                  state.context().errorMessage(
                      "Element: " + (result != null
                          ? result.messageOrDefault("invalid")
                          : "invalid"),
                      "Element validation failed"));
            }
          }
          return ValidationResult.valid();
        } finally {
          state.exitScope(value);
        }
      }
    };
  }

  public static <T> Rule<Collection<T>> anyMatch(Rule<T> elementRule) {
    Objects.requireNonNull(elementRule, "elementRule cannot be null");
    return new Rule<Collection<T>>() {
      @Override
      public ValidationResult validate(Collection<T> value) {
        if (value == null) {
          return ValidationResult.invalid(NULL_MSG);
        }
        for (T element : value) {
          ValidationResult result = elementRule.validate(element);
          if (result != null && result.isValid()) {
            return ValidationResult.valid();
          }
        }
        return ValidationResult.invalid("No element matches");
      }

      @Override
      public ValidationResult validateWithState(
          Collection<T> value, ValidationState state) {
        if (value == null) {
          return ValidationResult.invalid(NULL_MSG);
        }

        ValidationResult sizeCheck = state.checkCollectionSize(value.size());
        if (sizeCheck.isInvalid()) {
          return sizeCheck;
        }

        ValidationResult scopeCheck = state.enterScope(value);
        if (scopeCheck.isInvalid()) {
          return scopeCheck;
        }

        try {
          for (T element : value) {
            state.checkTimeout();
            ValidationResult result = elementRule.validateWithState(element, state);
            if (result != null && result.isSystemError()) {
              return result;
            }
            if (result != null && result.isValid()) {
              return ValidationResult.valid();
            }
          }
          return ValidationResult.invalid("No element matches");
        } finally {
          state.exitScope(value);
        }
      }
    };
  }

  public static <T> Rule<Collection<T>> noneMatch(Rule<T> elementRule) {
    Objects.requireNonNull(elementRule, "elementRule cannot be null");
    return new Rule<Collection<T>>() {
      @Override
      public ValidationResult validate(Collection<T> value) {
        if (value == null) {
          return ValidationResult.invalid(NULL_MSG);
        }
        for (T element : value) {
          ValidationResult result = elementRule.validate(element);
          if (result != null && result.isValid()) {
            return ValidationResult.invalid("Element matched unexpectedly");
          }
        }
        return ValidationResult.valid();
      }

      @Override
      public ValidationResult validateWithState(
          Collection<T> value, ValidationState state) {
        if (value == null) {
          return ValidationResult.invalid(NULL_MSG);
        }

        ValidationResult sizeCheck = state.checkCollectionSize(value.size());
        if (sizeCheck.isInvalid()) {
          return sizeCheck;
        }

        ValidationResult scopeCheck = state.enterScope(value);
        if (scopeCheck.isInvalid()) {
          return scopeCheck;
        }

        try {
          for (T element : value) {
            state.checkTimeout();
            ValidationResult result = elementRule.validateWithState(element, state);
            if (result != null && result.isSystemError()) {
              return result;
            }
            if (result != null && result.isValid()) {
              return ValidationResult.invalid("Element matched unexpectedly");
            }
          }
          return ValidationResult.valid();
        } finally {
          state.exitScope(value);
        }
      }
    };
  }

  public static <T> Rule<Collection<T>> contains(T element) {
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      try {
        return value.contains(element)
            ? ValidationResult.valid()
            : ValidationResult.invalid("Collection does not contain element");
      } catch (NullPointerException e) {
        return ValidationResult.invalid("Collection does not contain element");
      }
    };
  }

  public static <K, V> Rule<Map<K, V>> mapNotEmpty() {
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      return !value.isEmpty()
          ? ValidationResult.valid()
          : ValidationResult.invalid("Map is empty");
    };
  }

  public static <K, V> Rule<Map<K, V>> mapSize(int exactSize) {
    if (exactSize < 0) {
      throw new IllegalArgumentException("size cannot be negative");
    }
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      return value.size() == exactSize
          ? ValidationResult.valid()
          : ValidationResult.invalid("Map size is incorrect");
    };
  }

  public static <K, V> Rule<Map<K, V>> allKeys(Rule<K> keyRule) {
    Objects.requireNonNull(keyRule, "keyRule cannot be null");
    return new Rule<Map<K, V>>() {
      @Override
      public ValidationResult validate(Map<K, V> value) {
        if (value == null) {
          return ValidationResult.invalid(NULL_MSG);
        }
        for (K key : value.keySet()) {
          ValidationResult result = keyRule.validate(key);
          if (result == null || result.isInvalid()) {
            return ValidationResult.invalid("Key validation failed");
          }
        }
        return ValidationResult.valid();
      }

      @Override
      public ValidationResult validateWithState(
          Map<K, V> value, ValidationState state) {
        if (value == null) {
          return ValidationResult.invalid(NULL_MSG);
        }

        ValidationResult sizeCheck = state.checkCollectionSize(value.size());
        if (sizeCheck.isInvalid()) {
          return sizeCheck;
        }

        ValidationResult scopeCheck = state.enterScope(value);
        if (scopeCheck.isInvalid()) {
          return scopeCheck;
        }

        try {
          for (K key : value.keySet()) {
            state.checkTimeout();
            ValidationResult result = keyRule.validateWithState(key, state);
            if (result == null || result.isInvalid()) {
              if (result != null && result.isSystemError()) {
                return result;
              }
              return ValidationResult.invalid(
                  state.context().errorMessage(
                      "Key: " + (result != null
                          ? result.messageOrDefault("invalid")
                          : "invalid"),
                      "Key validation failed"));
            }
          }
          return ValidationResult.valid();
        } finally {
          state.exitScope(value);
        }
      }
    };
  }

  public static <K, V> Rule<Map<K, V>> allValues(Rule<V> valueRule) {
    Objects.requireNonNull(valueRule, "valueRule cannot be null");
    return new Rule<Map<K, V>>() {
      @Override
      public ValidationResult validate(Map<K, V> value) {
        if (value == null) {
          return ValidationResult.invalid(NULL_MSG);
        }
        for (V v : value.values()) {
          ValidationResult result = valueRule.validate(v);
          if (result == null || result.isInvalid()) {
            return ValidationResult.invalid("Value validation failed");
          }
        }
        return ValidationResult.valid();
      }

      @Override
      public ValidationResult validateWithState(
          Map<K, V> value, ValidationState state) {
        if (value == null) {
          return ValidationResult.invalid(NULL_MSG);
        }

        ValidationResult sizeCheck = state.checkCollectionSize(value.size());
        if (sizeCheck.isInvalid()) {
          return sizeCheck;
        }

        ValidationResult scopeCheck = state.enterScope(value);
        if (scopeCheck.isInvalid()) {
          return scopeCheck;
        }

        try {
          for (V v : value.values()) {
            state.checkTimeout();
            ValidationResult result = valueRule.validateWithState(v, state);
            if (result == null || result.isInvalid()) {
              if (result != null && result.isSystemError()) {
                return result;
              }
              return ValidationResult.invalid(
                  state.context().errorMessage(
                      "Value: " + (result != null
                          ? result.messageOrDefault("invalid")
                          : "invalid"),
                      "Value validation failed"));
            }
          }
          return ValidationResult.valid();
        } finally {
          state.exitScope(value);
        }
      }
    };
  }

  public static <K, V> Rule<Map<K, V>> containsKey(K key) {
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      try {
        return value.containsKey(key)
            ? ValidationResult.valid()
            : ValidationResult.invalid("Required key not found");
      } catch (NullPointerException e) {
        return ValidationResult.invalid("Required key not found");
      }
    };
  }
}