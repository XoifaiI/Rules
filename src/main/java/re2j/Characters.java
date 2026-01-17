package re2j;

final class Characters {
  private Characters() {
  }

  static int toLowerCase(int codePoint) {
    return Character.toLowerCase(codePoint);
  }

  static int toUpperCase(int codePoint) {
    return Character.toUpperCase(codePoint);
  }
}