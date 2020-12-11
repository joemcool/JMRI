package jmri.implementation;
//TODO putting this in implementation implies JMRI should treat MQTT like DCC (loconet and nce as well?): as a high-level interface and not a specific implementation.  Is this desirable?  Alternative is to put it in MQTT, and change AbstractSignalHead's abstract members to all be "protected" (eg isTurnoutUsed() is private)

import javax.annotation.Nonnull;

import jmri.SignalHead;
import jmri.implementation.AbstractSignalHead;
import jmri.jmrix.mqtt.MqttAdapter;
import jmri.jmrix.mqtt.MqttContentParser;
import jmri.jmrix.mqtt.MqttEventListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO update header, based on MqttLight, DccSignalHead DefaultSignalHead
/**
 * Default implementation of the basic logic of the SignalHead interface.
 *
 * This class only claims support for the Red, Yellow and Green appearances, and
 * their corresponding flashing forms. Support for Lunar is deferred to
 * DefaultLunarSignalHead or an extended class.
 * 
 * Based upon {@link jmri.implementation.DefaultSignalHead} by Bob Jacobsen
 *
 * @author Joe Martin Copyright (C) 2020
 */

public abstract class AbstractMqttSignalHead extends AbstractSignalHead implements MqttEventListener {

    private final MqttAdapter mqttAdapter;
    private final String sendTopic;
    private final String rcvTopic;
    javax.swing.Timer timer = null;
    
    /**
     * Is the Signal Head we're controlling capable of flashing on it's own
     * (ie, when given a "FLASHGREEN" state), or do we need to generate the
     * flashing pattern here?
     */
    //TODO should this be final?
    protected boolean mCanFlash = false;
    //TODO tie the above flash-capable flag to something in the GUI so the user can control it, a la how inverted turnouts are handled?
    
    /**
     * Should a flashing signal be on (lit) now?
     */
    protected boolean mFlashOn = true;

    /**
     * On or off time of flashing signal.
     * Public so that it can be overridden by 
     * scripting (before first use)
     */
    public int delay = masterDelay;

    public static int masterDelay = 750;
    
    final static private int[] VALID_STATES = new int[]{
            DARK,
            RED,
            YELLOW,
            GREEN,
            FLASHRED,
            FLASHYELLOW,
            FLASHGREEN,
    }; // No int for Lunar

    final static private String[] VALID_STATE_KEYS = new String[]{
            "SignalHeadStateDark",
            "SignalHeadStateRed",
            "SignalHeadStateYellow",
            "SignalHeadStateGreen",
            "SignalHeadStateFlashingRed",
            "SignalHeadStateFlashingYellow",
            "SignalHeadStateFlashingGreen",
    }; // Lunar not included


    public AbstractMqttSignalHead(MqttAdapter ma, String systemName, String userName, String sendTopic, String rcvTopic) {
        super(systemName, userName);
        this.sendTopic = sendTopic;
        this.rcvTopic = rcvTopic;
        this.mqttAdapter = ma;
        this.mqttAdapter.subscribe(rcvTopic, this);
    }

//    public AbstractMqttSignalHead(String systemName) {
//        super(systemName);
//    }

    @Override
    public void setAppearance(int newAppearance) {
        //TODO validate newAppearance
        int oldAppearance = mAppearance; // store the current appearance
        mAppearance = newAppearance;
        appearanceSetsFlashTimer(newAppearance);

        /* there are circumstances (admittedly rare) where signals and turnouts can get out of sync
         * allow 'newAppearance' to be set to resync these cases - P Cressman
         * if (oldAppearance != newAppearance) */
        updateOutput();

        // notify listeners, if any
        firePropertyChange("Appearance", oldAppearance, newAppearance);
    }

    /**
     * Call to set timer when updating the appearance.
     *
     * @param newAppearance the new appearance
     */
    protected void appearanceSetsFlashTimer(int newAppearance) {
        if (mLit && ((newAppearance == FLASHGREEN)
                || (newAppearance == FLASHYELLOW)
                || (newAppearance == FLASHRED)
                || (newAppearance == FLASHLUNAR))) {
            startFlash();
        }
        if ((!mLit) || ((newAppearance != FLASHGREEN)
                && (newAppearance != FLASHYELLOW)
                && (newAppearance != FLASHRED)
                && (newAppearance != FLASHLUNAR))) {
            stopFlash();
        }
    }
    
    @Override
    public void setLit(boolean newLit) {
        boolean oldLit = mLit;
        mLit = newLit;
        if (oldLit != newLit) {
            if (mLit && ((mAppearance == FLASHGREEN)
                    || (mAppearance == FLASHYELLOW)
                    || (mAppearance == FLASHRED)
                    || (mAppearance == FLASHLUNAR))) {
                startFlash();
            }
            if (!mLit) {
                stopFlash();
            }
            updateOutput();
            // notify listeners, if any
            firePropertyChange("Lit", oldLit, newLit);
        }

    }

    /**
     * Set the held parameter.
     * <p>
     * Note that this does not directly effect the output on the layout; the
     * held parameter is a local variable which effects the aspect only via
     * higher-level logic.
     *
     * @param newHeld new Held state, true if Held, to be compared with current
     *                Held state
     */
    @Override
    public void setHeld(boolean newHeld) {
        boolean oldHeld = mHeld;
        mHeld = newHeld;
        if (oldHeld != newHeld) {
            // notify listeners, if any
            firePropertyChange("Held", oldHeld, newHeld);
        }

    }
    
    /**
     * Start the timer that controls flashing.
     */
    protected void startFlash() {
        // Only start the timer if the SignalHead we're controlling doesn't
        // understand "FLASHING..." states
        if (!mCanFlash) {   
            // note that we don't force mFlashOn to be true at the start
            // of this; that way a flash in process isn't disturbed.
            if (timer == null) {
                timer = new javax.swing.Timer(delay, (java.awt.event.ActionEvent e) -> {
                    timeout();
                });
                timer.setInitialDelay(delay);
                timer.setRepeats(true);
            }
            timer.start();
        }
    }

    /**
     * Flash timer's periodic action
     */
    private void timeout() {
        mFlashOn = !mFlashOn;

        updateOutput();
    }

    /**
     * Stop the timer that controls flashing.
     * <p>
     * This is only a resource-saver; the actual use of
     * flashing happens elsewhere.
     */
    protected void stopFlash() {
        if (timer != null) {
            timer.stop();
        }
        mFlashOn = true;
    }

    /**
     * {@inheritDoc}
     */
//    @Override
//    public int[] getValidStates() {
//        return Arrays.copyOf(VALID_STATES, VALID_STATES.length);
//    }
//
//    /**
//     * {@inheritDoc}
//     */
//    @Override
//    public String[] getValidStateKeys() {
//        return Arrays.copyOf(VALID_STATE_KEYS, VALID_STATE_KEYS.length);
//    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getValidStateNames() {
        String[] stateNames = new String[VALID_STATE_KEYS.length];
        int i = 0;
        for (String stateKey : VALID_STATE_KEYS) {
            stateNames[i++] = Bundle.getMessage(stateKey);
        }
        return stateNames;
    }

    /**
     * General routine to handle output to the layout hardware.
     * <p>
     * Does not notify listeners of changes; that's done elsewhere. May be
     * overridden for special cases; should use the following variables to
     * determine what to send:
     * <ul>
     * <li>mAppearance
     * <li>mLit
     * <li>mFlashOn
     * <li>mCanFlash
     * </ul>
     */
    protected void updateOutput() {
        // assumes that writing a turnout to an existing state is cheap!
        
        String toSend;
        
        if (mLit == false) {
//            commandState(Turnout.CLOSED, Turnout.CLOSED);
//            return;
            //TODO is this correct? Will this return an internationalized version of state name? Thats bad, right?  Consider moving away from "as shown in JMRI" using Bundle properties and go to specific strings based on those values.  Also could remove white space from "Flashing Red"
            toSend = Bundle.getMessage("SignalHeadStateDark");
        } else if (!mFlashOn
                && ((mAppearance == FLASHGREEN)
                || (mAppearance == FLASHYELLOW)
                || (mAppearance == FLASHRED))
                && !mCanFlash) {
            // flash says to make output dark
//            commandState(Turnout.CLOSED, Turnout.CLOSED);
//            return;
          //TODO see above note re: source of the output values
            toSend = Bundle.getMessage("SignalHeadStateDark");

        } else {
//            switch (mAppearance) {
//                case RED:
//                case FLASHRED:
//                    commandState(Turnout.THROWN, Turnout.CLOSED);
//                    break;
//                case YELLOW:
//                case FLASHYELLOW:
//                    commandState(Turnout.THROWN, Turnout.THROWN);
//                    break;
//                case GREEN:
//                case FLASHGREEN:
//                    commandState(Turnout.CLOSED, Turnout.THROWN);
//                    break;
//                default:
//                    log.warn("Unexpected new appearance: {}", mAppearance);
//                // go dark by falling through
//                case DARK:
//                    commandState(Turnout.CLOSED, Turnout.CLOSED);
//                    break;
//            }
            toSend = jmri.util.StringUtil.getNameFromState(mAppearance, getValidStates(), getValidStateNames());
            
            if (toSend == null) {
                log.error("Trying to send unsupported appearance \"{}\", should never happen!",mAppearance);
                toSend = "";
            }
        }
        
        sendMessage(toSend);
    }   
    
    private void sendMessage(String c) {
        jmri.util.ThreadingUtil.runOnLayoutEventually(() -> {
            mqttAdapter.publish(this.sendTopic, c.getBytes());
        });
    }
    
    public void setParser(MqttContentParser<SignalHead> parser) {
        this.parser = parser;
    }

    MqttContentParser<SignalHead> parser = new MqttContentParser<SignalHead>() {
//        private final static String onText = "ON";
//        private final static String offText = "OFF";
//
//        int stateFromString(String payload) {
//            switch (payload) {
//                case onText: return ON;
//                case offText: return OFF;
//                default: return UNKNOWN;
//            }
//        }

        @Override
        public void beanFromPayload(@Nonnull SignalHead bean, @Nonnull String payload, @Nonnull String topic) {
//            int state = stateFromString(payload);
            int oldAppearance = mAppearance; // store the current appearance
            int newAppearance = jmri.util.StringUtil.getStateFromName(
                    payload, getValidStates(), getValidStateNames());

            boolean couldBeSendMessage = topic.endsWith(sendTopic);
            boolean couldBeRcvMessage = topic.endsWith(rcvTopic);

            if (couldBeSendMessage) {
//                setCommandedState(state);
                //TODO processing received appearances on the sending channel breaks flashing (since sending the "Dark" appearance is echoed back by the broker, which we process and change state to dark.  Either need to remove ability to flash, not process commands, or maybe implement a FLASHDARK state to differentiate between DARK and "between flashes"
       /*         // Someone else has commanded this signal.  Assume they have a reason and update our model
                
                //TODO validate new state
                if (newAppearance == -1) {
                    log.warn("Got unsupported Signal Head Appearance \"{}\" from MQTT topic {}",payload,topic);
                    newAppearance = DARK;    // Set signal in JMRI to DARK since we don't know what it's been set to
                }
                
                mAppearance = newAppearance;
                appearanceSetsFlashTimer(newAppearance);

                // notify listeners, if any
                firePropertyChange("Appearance", oldAppearance, newAppearance);*/
             // Got a command, maybe from someone else, just log it for now
                log.info("Got MQTT Signal Head command {} {}", topic, payload);
            } else if (couldBeRcvMessage) {
                //TODO process feedback, check for unsupported states
                // Got feedback, just log it for now
                log.info("Got MQTT Signal Head feedback {} {}", topic, payload);
//                setState(state);
            } else {
                log.warn("failure to decode topic {} {}", topic, payload);
            }
        }
        
        @Override
        public @Nonnull String payloadFromBean(@Nonnull SignalHead bean, int newState){
//            String toReturn = "UNKNOWN";
//            switch (getState()) {
//                case Light.ON:
//                    toReturn = onText;
//                    break;
//                case Light.OFF:
//                    toReturn = offText;
//                    break;
//                default:
//                    log.error("Light has a state which is not supported {}", newState);
//                    break;
//            }
            String toReturn = jmri.util.StringUtil.getNameFromState(mAppearance, getValidStates(), getValidStateNames());
            
            if (toReturn == null) { // Check for unsupported state
                log.error("Got unsupported appearance \"{}\", should never happen!",newState);
                toReturn = "";
            }
            
            return toReturn;
        }
    };
    
    @Override
    public void notifyMqttMessage(String receivedTopic, String message) {
        if (! ( receivedTopic.endsWith(rcvTopic) || receivedTopic.endsWith(sendTopic) ) ) {
            log.error("Got a message whose topic ({}) wasn't for me ({})", receivedTopic, rcvTopic);
            return;
        }        
        parser.beanFromPayload(this, message, receivedTopic);
    }

    @Override
    protected boolean isTurnoutUsed(jmri.Turnout t) {
        return false;
    }
    
    //TODO is this ok with an abstract class? Should be ok, see DccSignalHead
    private final static Logger log = LoggerFactory.getLogger(AbstractMqttSignalHead.class);

}
