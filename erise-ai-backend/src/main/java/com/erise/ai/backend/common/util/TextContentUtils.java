package com.erise.ai.backend.common.util;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class TextContentUtils {

    private static final Charset GB18030 = Charset.forName("GB18030");

    private TextContentUtils() {
    }

    public static String decodeText(byte[] bytes) {
        String utf8 = tryDecode(bytes, StandardCharsets.UTF_8);
        if (utf8 != null && !utf8.contains("\uFFFD")) {
            return trimBom(utf8);
        }

        String gb18030 = tryDecode(bytes, GB18030);
        if (gb18030 != null && !gb18030.isBlank()) {
            return trimBom(gb18030);
        }

        if (utf8 != null) {
            return trimBom(utf8);
        }
        return trimBom(new String(bytes, StandardCharsets.UTF_8));
    }

    private static String tryDecode(byte[] bytes, Charset charset) {
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ignored) {
            return null;
        }
    }

    private static String trimBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }
}
