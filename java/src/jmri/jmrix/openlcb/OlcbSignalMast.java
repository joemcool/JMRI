package jmri.jmrix.openlcb;

import java.util.*;
import javax.annotation.Nonnull;

import jmri.CommandStation;
import jmri.InstanceManager;
import jmri.NmraPacket;
import jmri.SignalMast;
import jmri.implementation.AbstractSignalMast;
import jmri.jmrix.SystemConnectionMemo;

import org.openlcb.Connection;
import org.openlcb.EventID;
import org.openlcb.EventState;
import org.openlcb.Message;
import org.openlcb.MessageDecoder;
import org.openlcb.NodeID;
import org.openlcb.OlcbInterface;
import org.openlcb.ProducerConsumerEventReportMessage;
import org.openlcb.IdentifyConsumersMessage;
import org.openlcb.ConsumerIdentifiedMessage;
import org.openlcb.IdentifyProducersMessage;
import org.openlcb.ProducerIdentifiedMessage;
import org.openlcb.IdentifyEventsMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements a SignalMast that use <B>OpenLCB Events</B>
 * to set aspects
 * <P>
 * This implementation writes out to the OpenLCB when it's commanded to
 * change appearance, and updates its internal state when it hears Events from
 * the network (including its own events).
 * <p>
 * System name specifies the creation information:
 * <pre>
 * IF$dsm:basic:one-searchlight(123)
 * </pre> The name is a colon-separated series of terms:
 * <ul>
 * <li>I - system prefix
 * <li>F$olm - defines signal masts of this type
 * <li>basic - name of the signaling system
 * <li>one-searchlight - name of the particular aspect map
 * <li>(123) - number distinguishing this from others
 * </ul>
 * <p>
 * EventIDs are returned in format in which they were provided.
 * <p>
 * To keep OpenLCB distributed state consistent, setAspect does not immediately
 * change the local aspect.  Instead, it produced the relevant EventId on the 
 * network, waiting for that to return and do the local state change, notification, etc.
 * <p>
 * Needs to have held/unheld, lit/unlit state completed - those need to Produce and Consume events as above
 * Based upon {@link jmri.implementation.DccSignalMast} by Kevin Dickerson
 *
 * @author Bob Jacobsen    Copyright (c) 2017, 2018
 */
public class OlcbSignalMast extends AbstractSignalMast {

    public OlcbSignalMast(String sys, String user) {
        super(sys, user);
        configureFromName(sys);
    }

    public OlcbSignalMast(String sys) {
        super(sys);
        configureFromName(sys);
    }

    public OlcbSignalMast(String sys, String user, String mastSubType) {
        super(sys, user);
        mastType = mastSubType;
        configureFromName(sys);
    }

    protected String mastType = "F$olm";

    StateMachine<Boolean> litMachine;
    StateMachine<Boolean> heldMachine;
    StateMachine<String> aspectMachine;
    
    NodeID node;
    Connection connection;
        
    // not sure why this is a CanSystemConnectionMemo in simulator, but it is 
    jmri.jmrix.can.CanSystemConnectionMemo systemMemo;

    protected void configureFromName(String systemName) {
        // split out the basic information
        String[] parts = systemName.split(":");
        if (parts.length < 3) {
            log.error("SignalMast system name needs at least three parts: " + systemName);
            throw new IllegalArgumentException("System name needs at least three parts: " + systemName);
        }
        if (!parts[0].endsWith(mastType)) {
            log.warn("First part of SignalMast system name is incorrect " + systemName + " : " + mastType);
        } else {
            String systemPrefix = parts[0].substring(0, parts[0].indexOf("$") - 1);
            java.util.List<SystemConnectionMemo> memoList = jmri.InstanceManager.getList(SystemConnectionMemo.class);

            for (SystemConnectionMemo memo : memoList) {
                if (memo.getSystemPrefix().equals(systemPrefix)) {
                    if (memo instanceof jmri.jmrix.can.CanSystemConnectionMemo) {
                        systemMemo = (jmri.jmrix.can.CanSystemConnectionMemo) memo;
                    } else {
                        log.error("Can't create mast \""+systemName+"\" because system \"" + systemPrefix + "\" is not CanSystemConnectionMemo but rather "+memo.getClass());
                    }
                    break;
                }
            }

            if (systemMemo == null) {
                log.error("No OpenLCB connection found for system prefix \"" + systemPrefix + "\", so mast \""+systemName+"\" will not function");
            }
        }
        String system = parts[1];
        String mast = parts[2];

        mast = mast.substring(0, mast.indexOf("("));
        String tmp = parts[2].substring(parts[2].indexOf("(") + 1, parts[2].indexOf(")"));
        try {
            mastNumber = Integer.parseInt(tmp);
        } catch (NumberFormatException e) {
            log.warn("Mast number of SystemName " + systemName + " is not in the correct format");
        }
        configureSignalSystemDefinition(system);
        configureAspectTable(system, mast);

        if (systemMemo != null) { // initialization that requires a connection, normally present
            node = ((OlcbInterface)systemMemo.get(OlcbInterface.class)).getNodeId();
            connection = ((OlcbInterface)systemMemo.get(OlcbInterface.class)).getOutputConnection();
 
            litMachine = new StateMachine<Boolean>(connection, node, Boolean.TRUE);
            heldMachine = new StateMachine<Boolean>(connection, node, Boolean.FALSE);
            aspectMachine = new StateMachine<String>(connection, node, getAspect());
        
            ((OlcbInterface)systemMemo.get(OlcbInterface.class)).registerMessageListener(new MessageDecoder(){
                @Override
                public void put(Message msg, Connection sender) {
                    handleMessage(msg);
                }
            });

        }   
    }

    int mastNumber; // used to tell them apart
    
    public void setOutputForAppearance(String appearance, String event) {
        aspectMachine.setEventForState(appearance, event);
    }

    public boolean isOutputConfigured(String appearance) {
        return aspectMachine.getEventStringForState(appearance) != null;
    }
    
    public String getOutputForAppearance(String appearance) {
        String retval = aspectMachine.getEventStringForState(appearance);
        if (retval == null) {
            log.error("Trying to get appearance " + appearance + " but it has not been configured");
            return "";
        }
        return retval;
    }

    @Override
    public void setAspect(String aspect) {
        aspectMachine.setState(aspect);
        // Normally, the local state is changed by super.setAspect(aspect); here; see comment at top
    }

    /**
     * Handle incoming messages
     * 
     */
    public void handleMessage(Message msg) {
        // gather before state
        Boolean litBefore = litMachine.getState();
        Boolean heldBefore = heldMachine.getState();
        String aspectBefore = aspectMachine.getState();
        
        // handle message
        msg.applyTo(litMachine, null);
        msg.applyTo(heldMachine, null);
        msg.applyTo(aspectMachine, null);
        
        // handle changes, if any
        if (!litBefore.equals(litMachine.getState())) firePropertyChange("Lit", litBefore, litMachine.getState());
        if (!heldBefore.equals(heldMachine.getState())) firePropertyChange("Held", heldBefore, heldMachine.getState());
        
        if ( (aspectBefore==null && aspectMachine.getState()!=null) || (aspectBefore!=null && !aspectBefore.equals(aspectMachine.getState()) ) ) updateState(aspectMachine.getState());

    }
    
    /** 
     * When the aspect change has returned from the OpenLCB network,
     * change the local state and notify
     */
    void updateState(String aspect) {
        String oldAspect = this.aspect;
        this.aspect = aspect;
        this.speed = (String) getSignalSystem().getProperty(aspect, "speed");
        firePropertyChange("Aspect", oldAspect, aspect);
    }

    /** 
     * Always communicates via OpenLCB
     */
    @Override
    public void setLit(boolean newLit) {
        litMachine.setState(newLit);
        // does not call super.setLit because no local state change until Event consumed
    }
    @Override
    public boolean getLit() {
        return litMachine.getState();
    }

    /** 
     * Always communicates via OpenLCB
     */
    @Override
    public void setHeld(boolean newHeld) {
        heldMachine.setState(newHeld);
        // does not call super.setHeld because no local state change until Event consumed
    }
    @Override
    public boolean getHeld() {
        return heldMachine.getState();
    }

    /**
     * Provide the last used sequence number
     */
    public static int getLastRef() {
        return lastRef;
    }
    protected static int lastRef = 0;

    public void setLitEventId(String event) { litMachine.setEventForState(Boolean.TRUE, event); }
    public String getLitEventId() { return litMachine.getEventStringForState(Boolean.TRUE); }
    public void setNotLitEventId(String event) { litMachine.setEventForState(Boolean.FALSE, event); }
    public String getNotLitEventId() { return litMachine.getEventStringForState(Boolean.FALSE); }

    public void setHeldEventId(String event) { heldMachine.setEventForState(Boolean.TRUE, event); }
    public String getHeldEventId() { return heldMachine.getEventStringForState(Boolean.TRUE); }
    public void setNotHeldEventId(String event) { heldMachine.setEventForState(Boolean.FALSE, event); }
    public String getNotHeldEventId() { return heldMachine.getEventStringForState(Boolean.FALSE); }

    

    /**
     * Implement a general state machine where state transitions are 
     * associated with the production and consumption of specific events.
     * There's a one-to-one mapping between transitions and events.
     * EventID storage is via Strings, so that the user-visible 
     * eventID string is preserved.
     */
    static class StateMachine<T> extends org.openlcb.MessageDecoder {
        public StateMachine(Connection connection, NodeID node, T start) {
            this.connection = connection;
            this.node = node;
            if (start != null) this.state = start;
        }
        
        Connection connection;
        NodeID node;
        T state;
        boolean initizalized = false;
        protected HashMap<T, String> stateToEventString = new HashMap<>();
        protected HashMap<T, EventID> stateToEventID = new HashMap<>();
        protected HashMap<EventID, T> eventToState = new HashMap<>(); // for efficiency, but requires no null entries
        
        public void setState(@Nonnull T newState) {
            log.debug("sending PCER to {}", getEventStringForState(newState));
            connection.put(
                    new ProducerConsumerEventReportMessage(node, getEventIDForState(newState)),
                    null);
        }
        
        @Nonnull
        public T getState() { return state; }
        
        public void setEventForState(@Nonnull T key, @Nonnull String value) {
            stateToEventString.put(key, value);

            EventID eid = new EventID(value);
            stateToEventID.put(key, eid);
            
            eventToState.put(eid, key);
        }
        
        @Nonnull
        public EventID getEventIDForState(@Nonnull T key) {
            EventID retval = stateToEventID.get(key);
            if (retval == null) retval = new EventID("00.00.00.00.00.00.00.00");
            return retval;
        }
        @Nonnull
        public String getEventStringForState(@Nonnull T key) {
            String retval = stateToEventString.get(key);
            if (retval == null) retval = "00.00.00.00.00.00.00.00";
            return retval;
        }

        /**
         * Internal method to determine the EventState for a reply
         * to an Identify* method
         */
        EventState getEventIDState(EventID event) {
            T value = eventToState.get(event);
            if (initizalized) {
                if (value.equals(state)) {
                    return EventState.Valid;
                } else {
                    return EventState.Invalid;
                }
            } else {
                return EventState.Unknown;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleProducerConsumerEventReport(@Nonnull ProducerConsumerEventReportMessage msg, Connection sender){
            if (eventToState.containsKey(msg.getEventID())) {
                initizalized = true;
                state = eventToState.get(msg.getEventID());
            }
        }
        /**
         * {@inheritDoc}
         */
        @Override
        public void handleProducerIdentified(@Nonnull ProducerIdentifiedMessage msg, Connection sender){
            // process if for here and marked "valid"
            if (eventToState.containsKey(msg.getEventID()) && msg.getEventState() == EventState.Valid) {
                initizalized = true;
                state = eventToState.get(msg.getEventID());
            }
        }
        /**
         * {@inheritDoc}
         */
        @Override
        public void handleConsumerIdentified(@Nonnull ConsumerIdentifiedMessage msg, Connection sender){
            // process if for here and marked "valid"
            if (eventToState.containsKey(msg.getEventID()) && msg.getEventState() == EventState.Valid) {
                initizalized = true;
                state = eventToState.get(msg.getEventID());
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleIdentifyEvents(@Nonnull IdentifyEventsMessage msg, Connection sender){
            // ours?
            if (! node.equals(msg.getDestNodeID())) return;  // not to us
            sendAllIdentifiedMessages();
        }            
        
        /**
         * Used at start up to emit the required messages, and in response to a IdentifyEvents message
         */
        public void sendAllIdentifiedMessages() {
            // identify as consumer and producer in same pass
            Set<Map.Entry<EventID,T>> set = eventToState.entrySet();
            for (Map.Entry<EventID,T> entry : set) {
                EventID event = entry.getKey();
                connection.put(
                    new ConsumerIdentifiedMessage(node, event, getEventIDState(event)),
                    null);
                connection.put(
                    new ProducerIdentifiedMessage(node, event, getEventIDState(event)),
                    null);
            }
        }
        /**
         * {@inheritDoc}
         */
        @Override
        public void handleIdentifyProducers(@Nonnull IdentifyProducersMessage msg, Connection sender){
            // process if we have the event
            EventID event = msg.getEventID();
            if (eventToState.containsKey(event)) {
                connection.put(
                    new ProducerIdentifiedMessage(node, event, getEventIDState(event)),
                    null);
            }
        }
        /**
         * {@inheritDoc}
         */
        @Override
        public void handleIdentifyConsumers(@Nonnull IdentifyConsumersMessage msg, Connection sender){
            // process if we have the event
            EventID event = msg.getEventID();
            if (eventToState.containsKey(event)) {
                connection.put(
                    new ConsumerIdentifiedMessage(node, event, getEventIDState(event)),
                    null);
            }
        }
        
    }
    
    private final static Logger log = LoggerFactory.getLogger(OlcbSignalMast.class);

}


