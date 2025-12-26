package testing;

import java.util.Collection;
import java.util.Map;

public final class Readable {

  private static final int MAX_COLLECTION_DISPLAY_SIZE = 10;

  private Readable() {
  }

  public static String format(Object value) {
    if (value == null) {
      return "null";
    }

    if (value instanceof String stringValue) {
      return formatString(stringValue);
    }

    if (value instanceof Character charValue) {
      return "'" + escapeChar(charValue) + "'";
    }

    if (value instanceof Number || value instanceof Boolean) {
      return value.toString();
    }

    if (value instanceof Collection<?> collection) {
      return formatCollection(collection);
    }

    if (value instanceof Map<?, ?> map) {
      return formatMap(map);
    }

    if (value.getClass().isArray()) {
      return formatArray(value);
    }

    return value.toString();
  }

  private static String formatString(String text) {
    StringBuilder builder = new StringBuilder("\"");
    int length = text.length();
    for (int index = 0; index < length; index++) {
      builder.append(escapeChar(text.charAt(index)));
    }
    builder.append("\"");
    return builder.toString();
  }

  private static String escapeChar(char character) {
    return switch (character) {
      case '\n' -> "\\n";
      case '\r' -> "\\r";
      case '\t' -> "\\t";
      case '\b' -> "\\b";
      case '\f' -> "\\f";
      case '\\' -> "\\\\";
      case '"' -> "\\\"";
      case '\'' -> "\\'";
      default -> {
        if (character < 32 || character > 126) {
          yield String.format("\\x%02X", (int) character);
        }
        yield String.valueOf(character);
      }
    };
  }

  private static String formatCollection(Collection<?> collection) {
    if (collection.isEmpty()) {
      return "[]";
    }
    if (collection.size() > MAX_COLLECTION_DISPLAY_SIZE) {
      return String.format("[...%d items]", collection.size());
    }
    StringBuilder builder = new StringBuilder("[");
    boolean isFirst = true;
    for (Object item : collection) {
      if (!isFirst) {
        builder.append(", ");
      }
      builder.append(format(item));
      isFirst = false;
    }
    builder.append("]");
    return builder.toString();
  }

  private static String formatMap(Map<?, ?> map) {
    if (map.isEmpty()) {
      return "{}";
    }
    if (map.size() > MAX_COLLECTION_DISPLAY_SIZE) {
      return String.format("{...%d entries}", map.size());
    }
    StringBuilder builder = new StringBuilder("{");
    boolean isFirst = true;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (!isFirst) {
        builder.append(", ");
      }
      builder.append(format(entry.getKey()));
      builder.append(": ");
      builder.append(format(entry.getValue()));
      isFirst = false;
    }
    builder.append("}");
    return builder.toString();
  }

  private static String formatArray(Object array) {
    int length = java.lang.reflect.Array.getLength(array);
    if (length == 0) {
      return "[]";
    }
    if (length > MAX_COLLECTION_DISPLAY_SIZE) {
      return String.format("[...%d items]", length);
    }
    StringBuilder builder = new StringBuilder("[");
    for (int index = 0; index < length; index++) {
      if (index > 0) {
        builder.append(", ");
      }
      builder.append(format(java.lang.reflect.Array.get(array, index)));
    }
    builder.append("]");
    return builder.toString();
  }
}