package com.assetsphere.modules.processing.text;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

final class SafeTextDecoder {

    private SafeTextDecoder() {
    }

    static String decode(InputStream content, String format) {
        try {
            byte[] bytes = content.readAllBytes();
            int offset = 0;
            var charset = StandardCharsets.UTF_8;
            if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
                offset = 3;
            } else if (bytes.length >= 2 && bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
                charset = StandardCharsets.UTF_16BE;
                offset = 2;
            } else if (bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
                charset = StandardCharsets.UTF_16LE;
                offset = 2;
            }
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new TextExtractionException(format + " text is not valid UTF encoded content", exception);
        } catch (IOException exception) {
            throw new TextExtractionException(format + " text extraction failed", exception);
        }
    }
}
