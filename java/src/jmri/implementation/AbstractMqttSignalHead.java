package jmri.implementation;
//TODO putting this in implementation implies JMRI should treat MQTT like DCC (loconet and nce as well?): as a high-level interface and not a specific implementation.  Is this desirable?  Alternative is to put it in MQTT, and change AbstractSignalHead's abstract members to all be "protected" (eg isTurnoutUsed() is private), similar to Acela

import javax.annotation.Nonnull;

import jmri.SignalHead;
import jmri.implementation.AbstractSignalHead;
import jmri.jmrix.mqtt.MqttAdapter;
import jmri.jmrix.mqtt.MqttContentParser;
import jmri.jmrix.mqtt.MqttEventListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of the basic logic of the SignalHead interface.
 *
 * This class only claims support for the Red, Yellow and Green appearances, and
 * their corresponding flashing forms. Support for Lunar will be implemented
 * later in another class.
 * 
 * Based upon {@link jmri.implementation.DefaultSignalHead} by Bob Jacobsen
 * Based upon {@link jmri.implementation.DccSignalHead} by Alex Shepherd
 * Based upon {@link jmri.jmrix.mqtt.MqttLight} by Bob Jacobsen, Paul Bender, and Fredrik Elestedt

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
    protected boolean mCanFlash = false;
    
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


    public AbstractMqttSignalHead(MqttAdapter ma, String systemName, String userName, String sendTopic, String rcvTopic, boolean canFlash) {
        super(systemName, userName);
        this.sendTopic = sendTopic;
        this.rcvTopic = rcvTopic;
        this.mqttAdapter = ma;
        this.mCanFlash = canFlash;
        this.mqttAdapter.subscribe(rcvTopic, this);
    }

    @Override
    public void dispose() {
        stopFlash();
        super.dispose();
    }

    /**
     * Update the appearacnce set on this Signal Head.  Also updates flashing
     * logic, sends HW output, and notifies property change listeners.
     * 
     * @param newAppearance the new appearance
     */
    @Override
    public void setAppearance(int newAppearance) {
        // validate newAppearance
        if (jmri.util.StringUtil.getNameFromState(
                newAppearance, getValidStates(), getValidStateNames()) == null) {
            log.warn("Signal Head {}({}) got unsupported new appearance {}, setting it to DARK instead",getUserName(),getSystemName(),newAppearance);
            newAppearance = DARK;
        }
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
    
    /**
     * Set the lit parameter.
     * <p>
     * Call to set lit/dark status of this Signal head.
     * 
     * @param newLit new Lit state, <code>true</code> for lit, <code>false</code> for dark
     */
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
     * @return the mCanFlash
     */
    public boolean canFlash() {
        return mCanFlash;
    }

    /**
     * Set the Can Flash parameter
     * <p>
     * Call to set whether the hardware being controled by this SignalHead
     * object is capable of generating flashing aspects on it's own, or if we
     * need to generate the flash pulses in software.  This call updates the
     * the local variable, and also updates the output if necesarry.
     * 
     * @param canFlash the mCanFlash to set
     */
    public void setCanFlash(boolean canFlash) {
        // update local variable
        this.mCanFlash = canFlash;
        
        // Check if flash generation logic needs to be updated
        if (canFlash) { // if the hardware can now generate flashes on it's own...
            stopFlash();    // Stop generating the flashes in logic here
        } else {
            if (mLit && ((mAppearance == FLASHGREEN)    // if we're lit and in a flashing aspect
                    || (mAppearance == FLASHYELLOW)
                    || (mAppearance == FLASHRED)
                    || (mAppearance == FLASHLUNAR))) {
                startFlash();   // Start flash generation logic
            }
        }
        
        // update output to reflect new HW capabilities
        updateOutput();
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
            
            toSend = parser.payloadFromBean(this, DARK);
            
        } else if (!mFlashOn                    // If we're generating flash pulses, and between flashes right now
                && ((mAppearance == FLASHGREEN)
                || (mAppearance == FLASHYELLOW)
                || (mAppearance == FLASHRED))
                && !mCanFlash) {

            toSend = parser.payloadFromBean(this, DARK);

        } else {

            toSend = parser.payloadFromBean(this, mAppearance);
            
        }
        
        //TODO catch IllegalArgumentException from payloadFromBean
        
        sendMessage(toSend);
    }   
    
    /**
     * Send data to the Signal Head hardware
     * <p>
     * This function publishes a string to the MQTT broker on the sending topic
     * for this Signal Head.
     * 
     * @param c the string to send
     */
    private void sendMessage(String c) {
        jmri.util.ThreadingUtil.runOnLayoutEventually(() -> {
            mqttAdapter.publish(this.sendTopic, c.getBytes());
        });
    }
    
//    public void setParser(MqttContentParser<SignalHead> parser) {
//        this.parser = parser;
//    }

    /**
     * Anonymous interface class for parsing MQTT Signal Head data received
     * from somewhere external to JMRI.
     */
    MqttContentParser<SignalHead> parser = new MqttContentParser<SignalHead>() {
        //TODO should we send "Flashing Green" or "Green" to a signal head that doesn't support flashing aspects on it's own?
        
        /**
         * Process an MQTT message
         * <p>
         * Process an MQTT message for a given SignalHead object based on
         * payload and topic.  Currently, just logs the message.  Eventually
         * will handle feedback from Signal Heads that report it and Signal
         * Head commands from other sources to update JMRI's internal
         * representation of this Signal Head's state.
         * 
         * @param bean the Signal Head the message is for, based on topic subscription
         * @param payload the message received from the MQTT broker
         * @param topic the MQTT topic on which the payload was received
         */
        @Override
        public void beanFromPayload(@Nonnull SignalHead bean, @Nonnull String payload, @Nonnull String topic) {
            int oldAppearance = mAppearance; // store the current appearance
            int newAppearance = jmri.util.StringUtil.getStateFromName(
                    payload, getValidStates(), getValidStateNames());

            boolean couldBeSendMessage = topic.endsWith(sendTopic);
            boolean couldBeRcvMessage = topic.endsWith(rcvTopic);

            if (couldBeSendMessage) {
//                setCommandedState(state);
                //TODO processing received appearances on the sending channel breaks flashing (since sending the "Dark" appearance is echoed back by the broker, which we process and change state to dark.  Either need to remove ability to flash, not process commands, or maybe implement a FLASHDARK state to differentiate between DARK and "between flashes"
       /*         // Someone else has commanded this signal.  Assume they have a reason and update our model
                
                // validate new state
                if (newAppearance == -1) {
                    //TODO throw IllegalArgumentException
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
        
        /**
         * Generate an MQTT payload for a new appearance
         * <p>
         * Takes a new appearance and translates it into the appropreate MQTT
         * message payload.
         * 
         * @todo throw IllegalArgumentException when given unsupported appearance?
         * 
         * @param bean the Signal Head to be updated
         * @param appearance the appearance to translated
         */
        @Override
        public @Nonnull String payloadFromBean(@Nonnull SignalHead bean, int appearance){

            // Send the JMRI state name as a message to the MQTT device
            //TODO is this correct? Will this return an internationalized version of state name? Thats bad, right?  Consider moving away from "as shown in JMRI" using Bundle properties and go to specific strings based on those values.  Also could remove white space from "Flashing Red"
            String toReturn = jmri.util.StringUtil.getNameFromState(mAppearance, getValidStates(), getValidStateNames());
            
            if (toReturn == null) {
                log.error("Got unsupported appearance \"{}\", should never happen!",appearance);
                toReturn = "";
            }
            
            return toReturn;
        }
    };
    
    /**
     * Callback to receive MQTT message
     * <p>
     * Callback function to receive an MQTT message from the
     * {@link MqttEventListener} interface.  Checks that the message was
     * addressed to one of this Signal Head's topics and forwards on to the
     * MQTT parser.
     * 
     * @param receivedTopic the topic this message was received on
     * @param message the message payload
     */
    @Override
    public void notifyMqttMessage(String receivedTopic, String message) {
        if (! ( receivedTopic.endsWith(rcvTopic) || receivedTopic.endsWith(sendTopic) ) ) {
            log.error("Got a message whose topic ({}) wasn't for me ({})", receivedTopic, rcvTopic);
            return;
        }        
        parser.beanFromPayload(this, message, receivedTopic);
    }

    /**
     * Is a given turnout used
     * <p>
     * Inherited from {@link AbstractSignalHead}.  MQTT Signal Heads don't use
     * JMRI Turnouts, so this will always be false.
     * 
     * @param t the Turnout to examine
     */
    @Override
    protected boolean isTurnoutUsed(jmri.Turnout t) {
        return false;
    }
    
    /**
     * The Logger instance for this class.
     */
    private final static Logger log = LoggerFactory.getLogger(AbstractMqttSignalHead.class);

}
