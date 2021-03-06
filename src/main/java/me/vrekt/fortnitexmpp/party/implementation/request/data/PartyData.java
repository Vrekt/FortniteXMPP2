package me.vrekt.fortnitexmpp.party.implementation.request.data;

import me.vrekt.fortnitexmpp.party.implementation.Party;
import me.vrekt.fortnitexmpp.party.implementation.configuration.PartyConfiguration;
import me.vrekt.fortnitexmpp.party.implementation.configuration.PrivacySetting;
import me.vrekt.fortnitexmpp.party.implementation.playlist.StandardPlaylists;
import me.vrekt.fortnitexmpp.party.implementation.request.PartyRequest;
import me.vrekt.fortnitexmpp.party.implementation.request.RequestBuilder;
import me.vrekt.fortnitexmpp.party.type.PartyType;

import javax.json.Json;
import javax.json.JsonArray;
import java.util.concurrent.atomic.AtomicInteger;

public final class PartyData implements PartyRequest {

    private final String payload;

    /**
     * Creates a new instance
     *
     * @param configuration   the current party configuration
     * @param currentPlaylist the desired playlist, {@see} {@link me.vrekt.fortnitexmpp.party.implementation.playlist.StandardPlaylists}
     * @param party           the party
     * @return a new {@link PartyData} instance
     */
    public static PartyData createNewWithConfiguration(final PartyConfiguration configuration, final String currentPlaylist, final Party party) {
        return new PartyData(configuration, currentPlaylist, party);
    }

    /**
     * Creates a new instance
     *
     * @param configuration the current party configuration
     * @param playlist      the desired playlist
     * @param party         the party
     * @return a new {@link PartyData} instance
     */
    public static PartyData createNewWithConfiguration(final PartyConfiguration configuration, final StandardPlaylists playlist, final Party party) {
        return new PartyData(configuration, playlist.getName(), party);
    }

    /**
     * Creates a new instance that changes the playlist.
     *
     * @param newPlaylist the new playlist
     * @param partyId     the ID of the party
     * @return a new {@link PartyData} instance
     */
    public static PartyData forNewPlaylist(final String newPlaylist, final String partyId) {
        return new PartyData(newPlaylist, null, partyId);
    }

    /**
     * Creates a new instance that changes the custom key
     *
     * @param customKey the custom key to use, can be {@code ""}
     * @param partyId   the ID of the party
     * @return a new {@link PartyData} instance
     */
    public static PartyData forNewCustomKey(final String customKey, final String partyId) {
        return new PartyData("", customKey, partyId);
    }

    /**
     * Creates a new instance that changes the privacy settings
     *
     * @param configuration the configuration to use
     * @param partyId       the ID of the party
     * @return a new {@link PartyData} instance
     */
    public static PartyData forNewPrivacySettings(final PartyConfiguration configuration, final String partyId) {
        return new PartyData(configuration, partyId);
    }

    /**
     * Creates the squad assignment data used for determining which spot a player should go.
     *
     * @param party the party
     * @return a new {@link PartyData} instance
     */
    public static PartyData forRawSquadAssignment(final Party party) {
        return new PartyData(party);
    }

    /**
     * Initialize
     *
     * @param configuration   the configuration
     * @param party           the party
     * @param currentPlaylist the playlist
     */
    private PartyData(final PartyConfiguration configuration, final String currentPlaylist, final Party party) {
        final var payload = Json.createObjectBuilder().add("Rev", RequestBuilder.getRevisionFor(PartyType.PARTY_DATA));

        // get the party type based on the configuration type.
        final var partType = configuration.settings() == PrivacySetting.FRIENDS ? "FriendsOnly" :
                configuration.settings() == PrivacySetting.FRIENDS_ALLOW_FRIENDS_OF_FRIENDS ?
                        "FriendsOnly" : configuration.settings() == PrivacySetting.PUBLIC ? "Public" : "Private";

        // any member = public
        // leader only = do not allow friends of friends.
        final var partyInviteRestriction = configuration.settings() == PrivacySetting.FRIENDS_ALLOW_FRIENDS_OF_FRIENDS ? "AnyMember" :
                configuration.settings() == PrivacySetting.PRIVATE_ALLOW_FRIENDS_OF_FRIENDS ? "AnyMember" : configuration.settings() == PrivacySetting.PUBLIC ? "AnyMember" :
                        "LeaderOnly";

        final var indexes = fillMemberIndexes(party);

        final var attributes = Json.createObjectBuilder()
                .add("PrimaryGameSessionId_s", "")
                .add("PartyState_s", "BattleRoyaleView")
                .add("LobbyConnectionStarted_b", false)
                .add("MatchmakingResult_s", "NoResults")
                .add("MatchmakingState_s", "NotMatchmaking")
                .add("SessionIsCriticalMission_b", false)
                .add("ZoneTileIndex_U", "-1")
                .add("ZoneInstanceId_s", "")
                .add("TheaterId_s", "")
                .add("TileStates_j", Json.createObjectBuilder()
                        .add("TileStates", Json.createArrayBuilder().build())
                        .build())
                .add("MatchmakingInfoString_s", "")
                .add("CustomMatchKey_s", "")
                .add("PlaylistData_j", Json.createObjectBuilder()
                        .add("PlaylistData", Json.createObjectBuilder()
                                .add("playlistName", currentPlaylist)
                                .add("tournamentId", "")
                                .add("eventWindowId", "")
                                .add("regionId", "NAE") // TODO: Customizable?
                                .build()).build())
                .add("AllowJoinInProgress_b", false)
                .add("LFGTime_s", "0001-01-01T00:00:00.000Z")
                .add("AthenaSquadFill_b", false)
                .add("PartyIsJoinedInProgress_b", false)
                .add("GameSessionKey_s", "")
                .add("RawSquadAssignments_j", Json.createObjectBuilder()
                        .add("RawSquadAssignments", indexes).build())
                .add("PrivacySettings_j", Json.createObjectBuilder()
                        .add("PrivacySettings", Json.createObjectBuilder()
                                .add("partyType", partType)
                                .add("partyInviteRestriction", partyInviteRestriction)
                                .add("bOnlyLeaderFriendsCanJoin", partyInviteRestriction.equalsIgnoreCase("LeaderOnly")).build()).build())
                .add("PlatformSessions_j", Json.createObjectBuilder()
                        .add("PlatformSessions", Json.createArrayBuilder().build()).build()).build();
        this.payload = RequestBuilder.buildRequestDoublePayload(party.partyId(), payload.add("Attrs", attributes).build(), PartyType.PARTY_DATA).toString();
    }

    /**
     * Initialize this instance for either a playlist change or custom key change.
     *
     * @param playlistName      the playlist name
     * @param optionalCustomKey the custom key to use
     * @param partyId           the ID of the party
     */
    private PartyData(final String playlistName, final String optionalCustomKey, final String partyId) {
        final var payload = Json.createObjectBuilder();
        final var attributes = Json.createObjectBuilder();
        payload.add("Rev", RequestBuilder.getRevisionFor(PartyType.PARTY_DATA));

        if (optionalCustomKey != null) {
            attributes.add("CustomMatchKey_s", optionalCustomKey);
        } else {
            attributes.add("PlaylistData_j", Json.createObjectBuilder()
                    .add("PlaylistData", Json.createObjectBuilder()
                            .add("playlistName", playlistName)
                            .add("tournamentId", "")
                            .add("eventWindowId", "").build()).build());
        }
        this.payload = RequestBuilder.buildRequestDoublePayload(partyId, payload.add("Attrs", attributes).build(), PartyType.PARTY_DATA).toString();
    }

    /**
     * Initialize this instance for a party configuration change
     *
     * @param configuration the configuration
     * @param partyId       the ID of the party
     */
    private PartyData(final PartyConfiguration configuration, final String partyId) {

        // get the party type based on the configuration type.
        final var partType = configuration.settings() == PrivacySetting.FRIENDS ? "FriendsOnly" :
                configuration.settings() == PrivacySetting.FRIENDS_ALLOW_FRIENDS_OF_FRIENDS ?
                        "FriendsOnly" : configuration.settings() == PrivacySetting.PUBLIC ? "Public" : "Private";

        // any member = public
        // leader only = do not allow friends of friends.
        final var partyInviteRestriction = configuration.settings() == PrivacySetting.FRIENDS_ALLOW_FRIENDS_OF_FRIENDS ? "AnyMember" :
                configuration.settings() == PrivacySetting.PRIVATE_ALLOW_FRIENDS_OF_FRIENDS ? "AnyMember" : configuration.settings() == PrivacySetting.PUBLIC ? "AnyMember" :
                        "LeaderOnly";

        final var payload = Json.createObjectBuilder();
        payload.add("Rev", RequestBuilder.getRevisionFor(PartyType.PARTY_DATA));
        final var attributes = Json.createObjectBuilder()
                .add("PrivacySettings_j", Json.createObjectBuilder()
                        .add("PrivacySettings", Json.createObjectBuilder()
                                .add("partyType", partType)
                                .add("partyInviteRestriction", partyInviteRestriction)
                                .add("bOnlyLeaderFriendsCanJoin", partyInviteRestriction.equalsIgnoreCase("LeaderOnly")).build()).build());
        this.payload = RequestBuilder.buildRequestDoublePayload(partyId, payload.add("Attrs", attributes).build(), PartyType.PARTY_DATA).toString();
    }

    private JsonArray fillMemberIndexes(final Party party) {
        final var leader = party.partyLeaderId();
        final var index = new AtomicInteger(leader == null ? 0 : 1);
        final var array = Json.createArrayBuilder();

        // add the leader first, should be always at index 0
        if (leader != null) {
            array.add(Json.createObjectBuilder()
                    .add("memberId", leader)
                    .add("absoluteMemberIdx", 0).build());
        }

        // next add all members
        party.members().forEach(member -> {
            if (!member.accountId().equals(leader)) {
                final var object = Json.createObjectBuilder()
                        .add("memberId", member.accountId())
                        .add("absoluteMemberIdx", index.get());
                array.add(object.build());
                index.incrementAndGet();
            }
        });
        return array.build();
    }

    private PartyData(final Party party) {
        final var payload = Json.createObjectBuilder();
        final var attributes = Json.createObjectBuilder();

        payload.add("Rev", RequestBuilder.getRevisionFor(PartyType.PARTY_DATA));
        attributes.add("RawSquadAssignments_j", Json.createObjectBuilder()
                .add("RawSquadAssignments", fillMemberIndexes(party)));
        this.payload = RequestBuilder.buildRequestDoublePayload(party.partyId(), payload.add("Attrs", attributes).build(), PartyType.PARTY_DATA).toString();
    }

    @Override
    public String payload() {
        return payload;
    }
}
