package com.assetsphere.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.assetsphere.modules.workspace.api.WorkspaceInvitationEmailSender.InvitationEmail;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ResendWorkspaceInvitationEmailSenderTests {
    @Test
    void mapsInvitationToResendRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(header(HttpHeaders.USER_AGENT, "AssetSphere/1.0"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(allOf(
                        containsString("\"from\":\"notifications@example.com\""),
                        containsString("\"to\":[\"recipient@example.com\"]"),
                        containsString("\"subject\":\"You\u2019re invited to Engineering Workspace on AssetSphere\""),
                        containsString("\"html\":"),
                        containsString("https://app.example.com/invitations/accept?token=test-token"))))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        sender(builder).send(invitation());

        server.verify();
    }

    @Test
    void providerFailureMapsToSafeDeliveryError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"provider-private-response\"}"));

        Throwable failure = catchThrowable(() -> sender(builder).send(invitation()));

        assertThat(failure).isInstanceOf(IllegalStateException.class)
                .hasMessage("Workspace invitation email delivery failed")
                .hasNoCause();
        assertThat(failure.toString()).doesNotContain("test-key", "provider-private-response");
        server.verify();
    }

    private ResendWorkspaceInvitationEmailSender sender(RestClient.Builder builder) {
        return new ResendWorkspaceInvitationEmailSender(
                builder, "test-key", "notifications@example.com", "Asia/Kolkata");
    }

    private InvitationEmail invitation() {
        return new InvitationEmail("recipient@example.com", "owner@example.com", "Engineering Workspace",
                "MEMBER", Instant.parse("2026-08-18T14:11:44Z"),
                "https://app.example.com/invitations/accept?token=test-token");
    }
}
