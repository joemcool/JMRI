package jmri.implementation.configurexml;

import jmri.util.JUnitUtil;

import org.junit.jupiter.api.*;
import org.junit.Assert;

/**
 * MqttTripleOutputSignalHeadXmlTest.java
 *
 * Test for the MqttTripleOutputSignalHeadXml class
 * 
 * Based upon {@link jmri.implementation.configurexml.TripleOutputSignalHeadXmlTest} by Paul Bender
 *
 * @author   Joe Martin  Copyright (C) 2020
 */
public class MqttTripleOutputSignalHeadXmlTest {

    @Test
    public void testCtor(){
      Assert.assertNotNull("MqttTripleOutputSignalHeadXml constructor",new MqttTripleOutputSignalHeadXml());
    }
    
    @BeforeEach
    public void setUp() {
        JUnitUtil.setUp();
    }

    @AfterEach
    public void tearDown() {
        JUnitUtil.tearDown();
    }

}
