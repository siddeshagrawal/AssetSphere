package com.assetsphere.modules.search.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class AssetContentChunkVectorRepositoryTests {

    @Test
    void rejectsMissingVectorUpdateSoIndexCannotBecomeReady() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.update(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class)))
                .thenReturn(0);

        assertThatThrownBy(() -> new AssetContentChunkVectorRepository(jdbc)
                .store(UUID.randomUUID(), new float[1536]))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("updated 0");
    }
}
