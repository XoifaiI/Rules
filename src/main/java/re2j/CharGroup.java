package re2j;

import java.util.Map;

public record CharGroup(int sign, int[] cls) {
    private static final int[] CODE_DIGIT = { 0x30, 0x39 };

    private static final int[] CODE_SPACE = { 0x9, 0xa, 0xc, 0xd, 0x20, 0x20 };

    private static final int[] CODE_WORD = { 0x30, 0x39, 0x41, 0x5a, 0x5f, 0x5f, 0x61, 0x7a };

    private static final int[] CODE_ALNUM = { 0x30, 0x39, 0x41, 0x5a, 0x61, 0x7a };

    private static final int[] CODE_ALPHA = { 0x41, 0x5a, 0x61, 0x7a };

    private static final int[] CODE_ASCII = { 0x0, 0x7f };

    private static final int[] CODE_BLANK = { 0x9, 0x9, 0x20, 0x20 };

    private static final int[] CODE_CNTRL = { 0x0, 0x1f, 0x7f, 0x7f };

    private static final int[] CODE_GRAPH = { 0x21, 0x7e };

    private static final int[] CODE_LOWER = { 0x61, 0x7a };

    private static final int[] CODE_PRINT = { 0x20, 0x7e };

    private static final int[] CODE_PUNCT = { 0x21, 0x2f, 0x3a, 0x40, 0x5b, 0x60, 0x7b, 0x7e };

    private static final int[] CODE_POSIX_SPACE = { 0x9, 0xd, 0x20, 0x20 };

    private static final int[] CODE_UPPER = { 0x41, 0x5a };

    private static final int[] CODE_XDIGIT = { 0x30, 0x39, 0x41, 0x46, 0x61, 0x66 };

    public static final Map<String, CharGroup> PERL_GROUPS = Map.of(
            "\\d", new CharGroup(+1, CODE_DIGIT),
            "\\D", new CharGroup(-1, CODE_DIGIT),
            "\\s", new CharGroup(+1, CODE_SPACE),
            "\\S", new CharGroup(-1, CODE_SPACE),
            "\\w", new CharGroup(+1, CODE_WORD),
            "\\W", new CharGroup(-1, CODE_WORD));

    static final Map<String, CharGroup> POSIX_GROUPS = Map.ofEntries(
            Map.entry("[:alnum:]", new CharGroup(+1, CODE_ALNUM)),
            Map.entry("[:^alnum:]", new CharGroup(-1, CODE_ALNUM)),
            Map.entry("[:alpha:]", new CharGroup(+1, CODE_ALPHA)),
            Map.entry("[:^alpha:]", new CharGroup(-1, CODE_ALPHA)),
            Map.entry("[:ascii:]", new CharGroup(+1, CODE_ASCII)),
            Map.entry("[:^ascii:]", new CharGroup(-1, CODE_ASCII)),
            Map.entry("[:blank:]", new CharGroup(+1, CODE_BLANK)),
            Map.entry("[:^blank:]", new CharGroup(-1, CODE_BLANK)),
            Map.entry("[:cntrl:]", new CharGroup(+1, CODE_CNTRL)),
            Map.entry("[:^cntrl:]", new CharGroup(-1, CODE_CNTRL)),
            Map.entry("[:digit:]", new CharGroup(+1, CODE_DIGIT)),
            Map.entry("[:^digit:]", new CharGroup(-1, CODE_DIGIT)),
            Map.entry("[:graph:]", new CharGroup(+1, CODE_GRAPH)),
            Map.entry("[:^graph:]", new CharGroup(-1, CODE_GRAPH)),
            Map.entry("[:lower:]", new CharGroup(+1, CODE_LOWER)),
            Map.entry("[:^lower:]", new CharGroup(-1, CODE_LOWER)),
            Map.entry("[:print:]", new CharGroup(+1, CODE_PRINT)),
            Map.entry("[:^print:]", new CharGroup(-1, CODE_PRINT)),
            Map.entry("[:punct:]", new CharGroup(+1, CODE_PUNCT)),
            Map.entry("[:^punct:]", new CharGroup(-1, CODE_PUNCT)),
            Map.entry("[:space:]", new CharGroup(+1, CODE_POSIX_SPACE)),
            Map.entry("[:^space:]", new CharGroup(-1, CODE_POSIX_SPACE)),
            Map.entry("[:upper:]", new CharGroup(+1, CODE_UPPER)),
            Map.entry("[:^upper:]", new CharGroup(-1, CODE_UPPER)),
            Map.entry("[:word:]", new CharGroup(+1, CODE_WORD)),
            Map.entry("[:^word:]", new CharGroup(-1, CODE_WORD)),
            Map.entry("[:xdigit:]", new CharGroup(+1, CODE_XDIGIT)),
            Map.entry("[:^xdigit:]", new CharGroup(-1, CODE_XDIGIT)));
}