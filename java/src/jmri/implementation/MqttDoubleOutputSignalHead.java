package jmri.implementation;
//TODO putting this in implementation implies JMRI should treat MQTT like DCC (loconet and nce as well?): as a high-level interface and not a specific implementation.  Is this desirable?  Alternative is to put it in jmri.jmrix.mqtt, and change AbstractSignalHead's abstract members to all be "protected" (eg isTurnoutUsed() is private), similar to Acela

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jmri.implementation.AbstractMqttSignalHead;
import jmri.jmrix.mqtt.MqttAdapter;
import jmri.jmrix.mqtt.MqttEventListener;

public class MqttDoubleOutputSignalHead extends AbstractMqttSignalHead implements MqttEventListener{

    public MqttDoubleOutputSignalHead(MqttAdapter ma, String systemName, String userName, String sendTopic, String rcvTopic, boolean canFlash) {
        super(ma, systemName, userName, sendTopic, rcvTopic, canFlash);
    }
    
    final static private int[] validStates = new int[]{
            DARK,
            RED,
            GREEN,
            FLASHRED,
            FLASHGREEN,
    }; // No int for Yellow or Lunar

    final static private String[] validStateKeys = new String[]{
            "SignalHeadStateDark",
            "SignalHeadStateRed",
            "SignalHeadStateGreen",
            "SignalHeadStateFlashingRed",
            "SignalHeadStateFlashingGreen",
    }; // Lunar and Yellow not included
    
    /**
     * {@inheritDoc}
     */
    @Override
    public int[] getValidStates() {
        return Arrays.copyOf(validStates, validStates.length);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getValidStateKeys() {
        return Arrays.copyOf(validStateKeys, validStateKeys.length);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getValidStateNames() {        
        String[] stateNames = new String[validStateKeys.length];
        int i = 0;
        for (String stateKey : validStateKeys) {
            stateNames[i++] = Bundle.getMessage(stateKey);
        }
        return stateNames;
    }
    
    @SuppressWarnings("unused")
    private final static Logger log = LoggerFactory.getLogger(MqttDoubleOutputSignalHead.class);

}
