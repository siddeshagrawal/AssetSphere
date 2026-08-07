package com.assetsphere.modules.asset.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AssetChecksumTests {

    @Test
    void calculatesKnownSha256Value() throws Exception {
        String checksum = new AssetChecksum().sha256(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)));

        assertThat(checksum).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
