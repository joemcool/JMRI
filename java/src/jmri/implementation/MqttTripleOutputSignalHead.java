package jmri.implementation;
////TODO putting this in implementation implies JMRI should treat MQTT like DCC (loconet and nce as well?): as a high-level interface and not a specific implementation.  Is this desirable?  Alternative is to put it in MQTT, and change AbstractSignalHead's abstract members to all be "protected" (eg isTurnoutUsed() is private), similar to Acela

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jmri.implementation.AbstractMqttSignalHead;
import jmri.jmrix.mqtt.MqttAdapter;
import jmri.jmrix.mqtt.MqttEventListener;

public class MqttTripleOutputSignalHead extends AbstractMqttSignalHead implements MqttEventListener{

    public MqttTripleOutputSignalHead(MqttAdapter ma, String systemName, String userName, String sendTopic, String rcvTopic, boolean canFlash) {
        super(ma, systemName, userName, sendTopic, rcvTopic, canFlash);
    }

//    public MqttTripleOutputSignalHead(String systemName) {
//        super(systemName);
//    }
    
    @SuppressWarnings("unused")
    final static private int[] VALID_STATES = new int[]{
            DARK,
            RED,
            YELLOW,
            GREEN,
            FLASHRED,
            FLASHYELLOW,
            FLASHGREEN,
    }; // No int for Lunar

    @SuppressWarnings("unused")
    final static private String[] VALID_STATE_KEYS = new String[]{
            "SignalHeadStateDark",
            "SignalHeadStateRed",
            "SignalHeadStateYellow",
            "SignalHeadStateGreen",
            "SignalHeadStateFlashingRed",
            "SignalHeadStateFlashingYellow",
            "SignalHeadStateFlashingGreen",
    }; // Lunar not included
    
    @SuppressWarnings("unused")
    private final static Logger log = LoggerFactory.getLogger(MqttTripleOutputSignalHead.class);

}
