package me.vrekt.fortnitexmpp.presence;

import com.google.common.flogger.FluentLogger;
import me.vrekt.fortnitexmpp.FortniteXMPP;
import me.vrekt.fortnitexmpp.presence.implementation.FortnitePresence;
import me.vrekt.fortnitexmpp.presence.implementation.listener.FortnitePresenceHandler;
import me.vrekt.fortnitexmpp.presence.implementation.listener.FortnitePresenceListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DefaultPresenceResource implements PresenceResource {
    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private final List<FortnitePresenceListener> listeners = new CopyOnWriteArrayList<>();
    private final List<FortnitePresenceHandler> handlers = new CopyOnWriteArrayList<>();
    private final PresenceListener presenceListener = new PresenceListener();
    private XMPPTCPConnection connection;
    private Roster roster;

    // currently no use for debug logging, but keep it here for now.
    // do not log incoming presences, could get very spammy.
    private boolean onlyHandleAvailablePresences, enableLogging;

    /**
     * Initialize this resource
     *
     * @param fortniteXMPP the {@link FortniteXMPP} instance
     */
    public DefaultPresenceResource(final FortniteXMPP fortniteXMPP) {
        this.connection = fortniteXMPP.connection();
        this.roster = Roster.getInstanceFor(connection);
        connection.addAsyncStanzaListener(presenceListener, StanzaTypeFilter.PRESENCE);
        onlyHandleAvailablePresences = true;
    }

    @Override
    public void addPresenceListener(final FortnitePresenceListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removePresenceListener(final FortnitePresenceListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void addPresenceHandler(final FortnitePresenceHandler handler) {
        handlers.add(handler);
    }

    @Override
    public void removePresenceHandler(final FortnitePresenceHandler handler) {
        handlers.remove(handler);
    }

    @Override
    public void reloadRoster(final boolean wait) {
        try {
            if (wait) {
                roster.reloadAndWait();
            } else {
                roster.reload();
            }
        } catch (final SmackException.NotLoggedInException | SmackException.NotConnectedException | InterruptedException exception) {
            LOGGER.atWarning().withCause(exception).log("Failed to reload roster. If you have large friend accounts its not recommended to reload or load the roster!");
        }
    }

    @Override
    public void handleAllRosterEntries() {
        roster.getEntries().forEach(entry -> handlePresence(roster.getPresence(entry.getJid())));
    }

    @Override
    public void setOnlyHandleAvailablePresences(final boolean onlyHandleAvailablePresences) {
        this.onlyHandleAvailablePresences = onlyHandleAvailablePresences;
    }

    @Override
    public Roster roster() {
        return roster;
    }

    @Override
    public void close() {
        connection.removeAsyncStanzaListener(presenceListener);
        listeners.clear();
        handlers.clear();
        connection = null;
        roster = null;
    }

    @Override
    public void disposeConnection() {
        connection.removeAsyncStanzaListener(presenceListener);
    }

    @Override
    public void reinitialize(final FortniteXMPP fortniteXMPP) {
        this.roster = Roster.getInstanceFor(fortniteXMPP.connection());
        connection.addAsyncStanzaListener(presenceListener, StanzaTypeFilter.PRESENCE);
    }

    /**
     * Parses the presence and passes it off to the listeners and handlers.
     *
     * @param presence the presence
     */
    private void handlePresence(final Presence presence) {
        if (presence.getStatus() == null) return;

        final var localPart = presence.getFrom().getLocalpartOrNull();
        if (localPart == null) return;

        final var accountId = localPart.asUnescapedString();
        final var fortnitePresence = FortnitePresence.createNew(accountId, presence.getStatus(), presence.getFrom());
        if (fortnitePresence != null) {
            listeners.forEach(fortnitePresenceListener -> fortnitePresenceListener.presenceReceived(fortnitePresence));
            handlers.stream().filter(handler -> handler.isActive() && handler.isReady() && handler.isRelevant(accountId)).forEach(handler -> handler.handlePresence(fortnitePresence));
        }
    }

    private final class PresenceListener implements StanzaListener {
        @Override
        public void processStanza(Stanza packet) {
            final var presence = (Presence) packet;
            if (onlyHandleAvailablePresences && !presence.isAvailable()) return;
            handlePresence(presence);
        }
    }

}
