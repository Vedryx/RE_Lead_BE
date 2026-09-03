package com.vedryxtech.voiceagent.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.vedryxtech.voiceagent.webhook.LiveKitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * The three LiveKit calls this service needs, over the Twirp HTTP API.
 *
 * <p>No SDK: LiveKit's Java bindings are a Kotlin artifact carrying a gRPC stack, and
 * these three endpoints are plain JSON over HTTPS with a signed JWT. Adding a
 * transitive gRPC dependency to a Spring app for three POSTs is the more expensive
 * option, not the cheaper one.
 *
 * <p>Each request carries a short-lived token signed with the API secret, scoped to
 * exactly the grant that request needs.
 */
@Component
public class LiveKitClient {

    private static final Logger log = LoggerFactory.getLogger(LiveKitClient.class);

    /** Every room this service creates. Anything else in the project is somebody else's. */
    public static final String ROOM_PREFIX = "Kavita-";

    private final LiveKitProperties credentials;
    private final DispatchProperties properties;
    private final RestClient http;
    private final ObjectMapper objectMapper;

    public LiveKitClient(LiveKitProperties credentials, DispatchProperties properties,
                         ObjectMapper objectMapper) {
        this.credentials = credentials;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.http = RestClient.builder().baseUrl(httpBase(properties.getLivekitUrl())).build();
    }

    /**
     * How many of our calls are in progress, according to LiveKit.
     *
     * <p>Asked rather than counted locally. A call starts and ends inside a worker in
     * another process, so a counter kept here would drift — throttling a healthy
     * service or overrunning the plan's concurrency limit.
     */
    public int liveCallCount() {
        JsonNode response = post("/twirp/livekit.RoomService/ListRooms",
                Map.of(), grant(Map.of("roomList", true)));
        JsonNode rooms = response.path("rooms");
        if (!rooms.isArray()) {
            return 0;
        }
        int count = 0;
        for (JsonNode room : rooms) {
            if (room.path("name").asText("").startsWith(ROOM_PREFIX)) {
                count++;
            }
        }
        return count;
    }

    /** Tell LiveKit which agent should join this room, and hand it the call's metadata. */
    public void dispatchAgent(String roomName, String metadataJson) {
        post("/twirp/livekit.AgentDispatchService/CreateDispatch",
                Map.of("agent_name", properties.getAgentName(),
                        "room", roomName,
                        "metadata", metadataJson),
                grant(Map.of("roomAdmin", true, "room", roomName)));
    }

    /**
     * Ring the phone and put whoever answers into the room.
     *
     * <p>{@code wait_until_answered} is deliberately false: the agent needs to be in the
     * room before the lead says hello, and blocking this call until pickup would hold a
     * scheduler thread for the length of the ring.
     */
    public void dialOut(String roomName, String phone, String displayName, String metadataJson) {
        String identity = "lead-" + phone.replaceAll("[^a-zA-Z0-9_-]", "");
        post("/twirp/livekit.SIP/CreateSIPParticipant",
                Map.of("sip_trunk_id", properties.getOutboundTrunkId(),
                        "sip_number", properties.getCallerId(),
                        "sip_call_to", phone,
                        "room_name", roomName,
                        "participant_identity", identity,
                        "participant_name", displayName,
                        "participant_metadata", metadataJson,
                        "wait_until_answered", false),
                grant(Map.of("roomAdmin", true, "room", roomName, "roomCreate", true)));
    }

    private JsonNode post(String path, Map<String, Object> body, String token) {
        String response = http.post()
                .uri(path)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(String.class);
        try {
            return objectMapper.readTree(response == null || response.isBlank() ? "{}" : response);
        } catch (Exception ex) {
            log.warn("LiveKit answered {} with something that is not JSON", path);
            return objectMapper.createObjectNode();
        }
    }

    /**
     * A token good for one request and ten minutes.
     *
     * <p>Scoped to the grant the call actually needs rather than a blanket admin token,
     * so a leaked one buys very little.
     */
    private String grant(Map<String, Object> video) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),
                    new JWTClaimsSet.Builder()
                            .issuer(credentials.getApiKey())
                            .subject(credentials.getApiKey())
                            .issueTime(Date.from(Instant.now()))
                            .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                            .claim("video", video)
                            .build());
            jwt.sign(new MACSigner(credentials.getApiSecret().getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign a LiveKit token", ex);
        }
    }

    /** LiveKit is addressed as wss:// for media and https:// for its HTTP API. */
    private static String httpBase(String url) {
        return url.replaceFirst("^wss://", "https://").replaceFirst("^ws://", "http://");
    }
}
