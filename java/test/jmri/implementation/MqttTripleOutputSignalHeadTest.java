package jmri.implementation;

import org.junit.Assert;
import org.junit.jupiter.api.*;
import org.python.jline.internal.Log;

import io.cucumber.core.logging.Logger;
import jmri.InstanceManager;
import jmri.Light;
import jmri.NamedBeanHandle;
import jmri.SignalHead;
import jmri.Turnout;
import jmri.TurnoutManager;
import jmri.jmrix.mqtt.MqttAdapter;
import jmri.util.JUnitAppender;
import jmri.util.JUnitUtil;

//TODO based on TripleOutputSignalHead, MqttLightTest
/**
 * 
 * @author joe
 *
 */
public class MqttTripleOutputSignalHeadTest extends AbstractSignalHeadTestBase {

    // Adapter and adapter IO fields
    MqttAdapter a;
    String saveTopic;
    byte[] savePayload;

    boolean subBeforeConfigWarningSent;

    final String systemName = "MH1";
    // Get topic suffix (systemName without system prefix)
    final String suffix = "1";
    
    // Info usually set by user preferences
    String prefix;
    String sendTopic;
    String rcvTopic;

    @Override
    SignalHead getHeadToTest() {
        MqttTripleOutputSignalHead toReturn;

        toReturn = new MqttTripleOutputSignalHead(
                a,              // MQTT Adapter
                systemName,     // System Name
                "Test Head 1",  // User Name
                sendTopic,      // Send Topic
                rcvTopic,       // Receive Topic
                false           // Can Flash
                );

        if(!subBeforeConfigWarningSent) { // Warning is only set once, so we should only see this message the first time
            subBeforeConfigWarningSent = true;
            JUnitAppender.assertWarnMessage("Trying to subscribe before connect/configure is done");
        }

        return toReturn;
    }

    @BeforeEach
    public void setUp() {
        JUnitUtil.setUp();

        subBeforeConfigWarningSent = false;
        saveTopic = null;
        savePayload = null;
        a = new MqttAdapter(){
            @Override
            public void publish(String topic, byte[] payload) {
                saveTopic = topic;
                savePayload = payload;
            }
        };
        
        // Get topic prefix (path to Signal Head topics)
        //TODO if send/rcv topics are different, update this
        prefix = a.getOptionState("14");

        sendTopic = java.text.MessageFormat.format(
                prefix.contains("{0}") ? prefix : (prefix + "{0}"),
                        suffix);
        rcvTopic = java.text.MessageFormat.format(
                prefix.contains("{0}") ? prefix : (prefix + "{0}"),
                suffix);
    }

    @AfterEach
    public void tearDown() {
        saveTopic = null;
        savePayload = null;
        prefix = null;
        sendTopic = null;
        rcvTopic = null;
        a.dispose();
        a = null;
        JUnitUtil.tearDown();
    }

    @Test
    public void testCTor() {
        SignalHead s = getHeadToTest();
        Assert.assertNotNull("exists",s);
    }
    
    // check initial state is DARK and that it's been sent to broker
    @Test
    public void testInitialConditions() {
        SignalHead s = getHeadToTest();
        
        JUnitUtil.waitFor( ()->{
            return sendTopic.equals(saveTopic);
        }, "topic check");
        Assert.assertEquals("message", "Dark", new String(savePayload));
        Assert.assertEquals("appearance", SignalHead.DARK, s.getAppearance());
    }

    //TODO test MQTT received messages for feedback and external commands once implemented

    // test sending MQTT commands
    @Test
    public void testMqttOutput() {
        SignalHead s = getHeadToTest();

        s.setAppearance(SignalHead.GREEN);

        JUnitUtil.waitFor( ()->{
            return sendTopic.equals(saveTopic);
        }, "topic check");
        Assert.assertEquals("message", "Green", new String(savePayload));
        Assert.assertEquals("appearance", SignalHead.GREEN, s.getAppearance());

        saveTopic = null;
        savePayload = null;

        s.setAppearance(SignalHead.DARK);

        JUnitUtil.waitFor( ()->{
            return sendTopic.equals(saveTopic);
        }, "topic check");
        Assert.assertEquals("message", "Dark", new String(savePayload));
        Assert.assertEquals("appearance", SignalHead.DARK, s.getAppearance());
    }
    
    //TODO test all valid aspects
    @Test
    public void testAllAspects() {
        SignalHead s = getHeadToTest();
        
        // Don't generate flash pulses for this test
        ((MqttTripleOutputSignalHead) s).setCanFlash(true);
        
        for (int appearance : ((MqttTripleOutputSignalHead) s).getValidStates()) {
            saveTopic = null;
            savePayload = null;
            
            s.setAppearance(appearance);
            
            // NOTE: This unit test was buggy, and would only work for a while with this logging statement uncommented.
            // Not sure what whas going on or way, may have been a race condition?  I think adding the above statements
            // to clear saveTopic and savePayload has fixed it, but I'm going to keep the log statement here commented
            // out for now just in case.
//            Log.warn("Setting appearance",jmri.util.StringUtil.getNameFromState(
//                    appearance,
//                    ((MqttTripleOutputSignalHead) s).getValidStates(),
//                    ((MqttTripleOutputSignalHead) s).getValidStateNames()
//                    ),appearance);
            
            JUnitUtil.waitFor( ()->{
                return sendTopic.equals(saveTopic);
            }, "topic check");
            Assert.assertEquals("appearance", appearance, s.getAppearance());
            Assert.assertEquals("message",
                    jmri.util.StringUtil.getNameFromState(
                            appearance,
                            ((MqttTripleOutputSignalHead) s).getValidStates(),
                            ((MqttTripleOutputSignalHead) s).getValidStateNames()
                            ),
                    new String(savePayload)
                    );
        }
    }

    //TODO test flash pulse generation
    
}
