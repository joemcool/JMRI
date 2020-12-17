package jmri.implementation;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.jupiter.api.*;
import org.python.jline.internal.Log;

import jmri.SignalHead;
import jmri.jmrix.mqtt.MqttAdapter;
import jmri.util.JUnitAppender;
import jmri.util.JUnitUtil;

/**
 * Test cases for MQTT Double Output Signal Head
 * 
 * Based upon {@link jmri.implementation.TripleOutputSignalHeadTest} by Paul Bender
 * Based upon {@link jmri.jmrix.mqtt.MqttLightTest} by Bob Jacobsen
 * 
 * @author Joe Martin Copyright (C) 2020
 */
public class MqttDoubleOutputSignalHeadTest extends AbstractSignalHeadTestBase {

    // Adapter and adapter IO fields
    MqttAdapter a;
    String saveTopic;
    byte[] savePayload;
    CountDownLatch latch;
    boolean flashOn;

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
        MqttDoubleOutputSignalHead toReturn;

        toReturn = new MqttDoubleOutputSignalHead(
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
        flashOn = false;
        saveTopic = null;
        savePayload = null;
        a = new MqttAdapter(){
            @Override
            public void publish(String topic, byte[] payload) {
                saveTopic = topic;
                savePayload = payload;
                                
                // flash test latching, here so it can be called by timer thread
                if (latch != null) {
                    String savePayloadString = new String (savePayload);
                    if (flashOn) {
                        if (
                                saveTopic.equals(sendTopic)
                                && savePayloadString.equals(
                                        // Static references to specific base class here because object under test hasn't been instantiated yet
                                        jmri.util.StringUtil.getNameFromState(MqttDoubleOutputSignalHead.DARK,  //TODO if implementing FLASHDARK, update this
                                                MqttDoubleOutputSignalHead.getDefaultValidStates(),
                                                MqttDoubleOutputSignalHead.getDefaultValidStateNames()
                                                )
                                        )
                                ) {                            
                            flashOn = false;
                        } else {
                            Log.warn("Got unexpected message ",savePayloadString);
                        }
                    } else {    // flash is off
                        if (
                                saveTopic.equals(sendTopic)
                                && savePayloadString.equals(
                                        jmri.util.StringUtil.getNameFromState(MqttDoubleOutputSignalHead.FLASHGREEN,
                                                MqttDoubleOutputSignalHead.getDefaultValidStates(),
                                                MqttDoubleOutputSignalHead.getDefaultValidStateNames()
                                                )
                                        )
                                ) {
                            flashOn = true;
                            latch.countDown();
                        } else {
                            if (!savePayloadString.equals(
                                    jmri.util.StringUtil.getNameFromState(
                                            MqttDoubleOutputSignalHead.FLASHGREEN,
                                            MqttDoubleOutputSignalHead.getDefaultValidStates(),
                                            MqttDoubleOutputSignalHead.getDefaultValidStateNames()
                                            )
                                    )
                                    ) {
                                Log.warn("Got unexpected message ",
                                        savePayloadString,
                                        " should have been ",
                                        jmri.util.StringUtil.getNameFromState(MqttDoubleOutputSignalHead.FLASHGREEN,
                                                MqttDoubleOutputSignalHead.getDefaultValidStates(),
                                                MqttDoubleOutputSignalHead.getDefaultValidStateNames()
                                                )
                                        );
                            }
                            
                            if (!saveTopic.equals(sendTopic)) {
                                Log.warn("Got unexpected topic ",
                                        saveTopic,
                                        " should have been ",
                                        sendTopic);
                            }
                           
                        }
                    }
                }
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
        latch = null;
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
    
    // test all valid aspects
    @Test
    public void testAllAspects() {
        SignalHead s = getHeadToTest();
        
        // Don't generate flash pulses for this test
        ((AbstractMqttSignalHead) s).setCanFlash(true);
        
        for (int appearance : s.getValidStates()) {
            saveTopic = null;
            savePayload = null;
            
            s.setAppearance(appearance);
            
            // NOTE: This unit test was buggy, and would only work for a while with this logging statement uncommented.
            // Not sure what whas going on or way, may have been a race condition?  I think adding the above statements
            // to clear saveTopic and savePayload has fixed it, but I'm going to keep the log statement here commented
            // out for now just in case.
//            Log.warn("Setting appearance",jmri.util.StringUtil.getNameFromState(
//                    appearance,
//                    ((MqttDoubleOutputSignalHead) s).getValidStates(),
//                    ((MqttDoubleOutputSignalHead) s).getValidStateNames()
//                    ),appearance);
            
            JUnitUtil.waitFor( ()->{
                return sendTopic.equals(saveTopic);
            }, "topic check");
            Assert.assertEquals("appearance", appearance, s.getAppearance());
            Assert.assertEquals("message",
                    jmri.util.StringUtil.getNameFromState(
                            appearance,
                            s.getValidStates(),
                            s.getValidStateNames()
                            ),
                    new String(savePayload)
                    );
        }
        
        s.dispose();    // Should be called in tear down, but s isn't in scope then
    }
    
    // test Lunar aspect is invalid
    @Test
    public void testNoLunarAspects(){
        SignalHead s = getHeadToTest();
        
        // Don't generate flash pulses for this test
        ((AbstractMqttSignalHead) s).setCanFlash(true);
        
        // Set Lunar
        s.setAppearance(SignalHead.LUNAR);
        
        JUnitUtil.waitFor( ()->{
            return sendTopic.equals(saveTopic);
        }, "topic check");
        Assert.assertEquals("appearance", SignalHead.DARK, s.getAppearance());
        Assert.assertEquals("message",
                jmri.util.StringUtil.getNameFromState(
                        SignalHead.DARK,
                        s.getValidStates(),
                        s.getValidStateNames()
                        ),
                new String(savePayload)
                );
        JUnitAppender.assertWarnMessage("Signal Head Test Head 1(MH1) got unsupported new appearance 64, setting it to DARK instead");
        
        // Set Flashing Lunar
        s.setAppearance(SignalHead.FLASHLUNAR);
        
        JUnitUtil.waitFor( ()->{
            return sendTopic.equals(saveTopic);
        }, "topic check");
        Assert.assertEquals("appearance", SignalHead.DARK, s.getAppearance());
        Assert.assertEquals("message",
                jmri.util.StringUtil.getNameFromState(
                        SignalHead.DARK,
                        s.getValidStates(),
                        s.getValidStateNames()
                        ),
                new String(savePayload)
                );
        JUnitAppender.assertWarnMessage("Signal Head Test Head 1(MH1) got unsupported new appearance 128, setting it to DARK instead");
        
    }
    
    // test Yellow aspect is invalid
    @Test
    public void testNoYellowAspects(){
        SignalHead s = getHeadToTest();
        
        // Don't generate flash pulses for this test
        ((AbstractMqttSignalHead) s).setCanFlash(true);
        
        // Set Yellow
        s.setAppearance(SignalHead.YELLOW);
        
        JUnitUtil.waitFor( ()->{
            return sendTopic.equals(saveTopic);
        }, "topic check");
        Assert.assertEquals("appearance", SignalHead.DARK, s.getAppearance());
        Assert.assertEquals("message",
                jmri.util.StringUtil.getNameFromState(
                        SignalHead.DARK,
                        s.getValidStates(),
                        s.getValidStateNames()
                        ),
                new String(savePayload)
                );
        JUnitAppender.assertWarnMessage("Signal Head Test Head 1(MH1) got unsupported new appearance 4, setting it to DARK instead");
        
        // Set Flashing Yellow
        s.setAppearance(SignalHead.FLASHYELLOW);
        
        JUnitUtil.waitFor( ()->{
            return sendTopic.equals(saveTopic);
        }, "topic check");
        Assert.assertEquals("appearance", SignalHead.DARK, s.getAppearance());
        Assert.assertEquals("message",
                jmri.util.StringUtil.getNameFromState(
                        SignalHead.DARK,
                        s.getValidStates(),
                        s.getValidStateNames()
                        ),
                new String(savePayload)
                );
        JUnitAppender.assertWarnMessage("Signal Head Test Head 1(MH1) got unsupported new appearance 8, setting it to DARK instead");
        
    }

    // test flash pulse generation
    @Test
    public void testFlashPulseGen() {
        latch = new CountDownLatch(3); // wait for 3 flashes
        flashOn = true; // match default state in Signal Head flash logic
        
        SignalHead s = getHeadToTest();
                
        s.setAppearance(SignalHead.FLASHGREEN);
        
        try {
            latch.await(5,TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        Assert.assertEquals("Flash Test Timeout",0,latch.getCount());
        
        s.dispose();    // Should be called in tear down, but s isn't in scope then
    }
}
