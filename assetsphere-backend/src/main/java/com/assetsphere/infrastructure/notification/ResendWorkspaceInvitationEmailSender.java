package com.assetsphere.infrastructure.notification;

import com.assetsphere.modules.workspace.api.WorkspaceInvitationEmailSender;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class ResendWorkspaceInvitationEmailSender implements WorkspaceInvitationEmailSender {
    private final RestClient client;
    private final String from;
    private final InvitationEmailContent content;

    ResendWorkspaceInvitationEmailSender(RestClient.Builder builder, String apiKey, String from, String timeZone) {
        client = builder.clone()
                .baseUrl("https://api.resend.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.USER_AGENT, "AssetSphere/1.0")
                .build();
        this.from = from;
        content = new InvitationEmailContent(timeZone);
    }

    @Override
    public void send(InvitationEmail invitation) {
        try {
            client.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ResendEmailRequest(from, List.of(invitation.recipientEmail()),
                            content.subject(invitation), content.html(invitation)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new IllegalStateException("Workspace invitation email delivery failed");
        }
    }

    private record ResendEmailRequest(String from, List<String> to, String subject, String html) { }
}
