package com.assetsphere.modules.search.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.common.exception.RateLimitExceededException;
import com.assetsphere.modules.common.exception.ServiceUnavailableException;
import com.assetsphere.modules.search.api.AssetSearchResult;
import com.assetsphere.modules.search.api.EmbeddingModelPort;
import com.assetsphere.modules.search.api.EmbeddingVector;
import com.assetsphere.modules.search.api.SearchMode;
import com.assetsphere.modules.search.api.SemanticIndexProperties;
import com.assetsphere.modules.search.api.SemanticSearchRateLimiter;
import com.assetsphere.modules.search.persistence.AssetSearchDocumentRepository;
import com.assetsphere.modules.workspace.api.WorkspaceAccessFacade;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class SearchApplicationServiceTests {
    @Mock WorkspaceAccessFacade access; @Mock AssetSearchDocumentRepository documents;
    @Mock ObjectProvider<EmbeddingModelPort> models; @Mock EmbeddingModelPort model; @Mock SemanticSearchRateLimiter limiter;

    @Test void omittedModeUsesLexicalPathWithoutEmbeddingOrLimiter() { lexical(); service().search(user(), workspace(), "report", 0, 20); verify(documents).count(any(), eq("report")); verify(models, never()).getIfAvailable(); verify(limiter, never()).check(any(), any()); }
    @Test void explicitLexicalUsesExistingPath() { lexical(); service().search(user(), workspace(), "report", 0, 20, SearchMode.LEXICAL); verify(documents).search(any(), eq("report"), eq(20), eq(0L)); }
    @Test void semanticEmbedsOnceAndUsesLimiter() { semantic(); service().search(user(), workspace(), "report", 0, 20, SearchMode.SEMANTIC); verify(model).embed(List.of("report")); verify(limiter).check(any(), any()); }
    @Test void semanticProviderFailureIsPreserved() { when(models.getIfAvailable()).thenReturn(model); when(model.embed(any())).thenThrow(new IllegalStateException()); assertThatThrownBy(() -> service().search(user(),workspace(),"report",0,20,SearchMode.SEMANTIC)).isInstanceOf(ServiceUnavailableException.class); }
    @Test void hybridProviderFailureFallsBackToLexical() { var lexicalResult=result(UUID.randomUUID(),1); when(documents.search(any(),any(),anyInt(),anyLong())).thenReturn(List.of(lexicalResult)); when(models.getIfAvailable()).thenReturn(model); when(model.embed(any())).thenThrow(new IllegalStateException()); assertThat(service().search(user(),workspace(),"report",0,20,SearchMode.HYBRID).content()).containsExactly(lexicalResult); }
    @Test void hybridRateLimitFailureDoesNotFallback() { org.mockito.Mockito.doThrow(new RateLimitExceededException(1)).when(limiter).check(any(),any()); assertThatThrownBy(() -> service().search(user(),workspace(),"report",0,20,SearchMode.HYBRID)).isInstanceOf(RateLimitExceededException.class); verify(documents, never()).search(any(),any(),anyInt(),anyLong()); }
    @Test void hybridRanksByRrfAndCollapsesAssets() { UUID shared=UUID.randomUUID(), semanticWinner=UUID.randomUUID(); semantic(); when(documents.search(any(),any(),anyInt(),anyLong())).thenReturn(List.of(result(shared,1))); when(documents.semanticSearch(any(),any(),anyInt())).thenReturn(List.of(result(semanticWinner,1),result(shared,1))); var response=service().search(user(),workspace(),"report",0,20,SearchMode.HYBRID); assertThat(response.content()).extracting(AssetSearchResult::assetId).containsExactly(shared,semanticWinner); }
    @Test void hybridTieUsesAssetIdOrdering() { UUID low=new UUID(0,1), high=new UUID(0,2); semantic(); when(documents.search(any(),any(),anyInt(),anyLong())).thenReturn(List.of(result(low,1))); when(documents.semanticSearch(any(),any(),anyInt())).thenReturn(List.of(result(high,1))); var response=service().search(user(),workspace(),"report",0,20,SearchMode.HYBRID); assertThat(response.content()).extracting(AssetSearchResult::assetId).containsExactly(low,high); }
    @Test void hybridFinalResultsAreBoundedToRequestedSize() { semantic(); when(documents.search(any(),any(),anyInt(),anyLong())).thenReturn(List.of(result(UUID.randomUUID(),1),result(UUID.randomUUID(),1))); when(documents.semanticSearch(any(),any(),anyInt())).thenReturn(List.of(result(UUID.randomUUID(),1),result(UUID.randomUUID(),1))); assertThat(service().search(user(),workspace(),"report",0,1,SearchMode.HYBRID).content()).hasSize(1); }
    @Test void semanticPassesRequestedWorkspaceToRepository() { UUID workspace=workspace(); semantic(); service().search(user(),workspace,"report",0,20,SearchMode.SEMANTIC); verify(documents).semanticSearch(eq(workspace),any(),anyInt()); }
    private void lexical(){ when(documents.count(any(),any())).thenReturn(1L); when(documents.search(any(),any(),anyInt(),anyLong())).thenReturn(List.of(result(UUID.randomUUID(),1))); }
    private void semantic(){ when(models.getIfAvailable()).thenReturn(model); when(model.embed(any())).thenReturn(List.of(new EmbeddingVector(new float[1536]))); when(documents.semanticSearch(any(),any(),anyInt())).thenReturn(List.of(result(UUID.randomUUID(),1))); }
    private SearchApplicationService service(){ SemanticIndexProperties p=new SemanticIndexProperties(); return new SearchApplicationService(access,documents,models,p,limiter); }
    private UUID user(){return UUID.randomUUID();} private UUID workspace(){return UUID.randomUUID();}
    private AssetSearchResult result(UUID id,double score){return new AssetSearchResult(id,UUID.randomUUID(),1,"title","file","text/plain","READY",score,"snippet");}
}
