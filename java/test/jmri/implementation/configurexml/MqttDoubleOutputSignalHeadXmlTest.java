package jmri.implementation.configurexml;

import jmri.util.JUnitUtil;

import org.junit.jupiter.api.*;
import org.junit.Assert;

/**
 * MqttDoubleOutputSignalHeadXmlTest.java
 *
 * Test for the MqttDoubleOutputSignalHeadXml class
 * 
 * Based upon {@link jmri.implementation.configurexml.TripleOutputSignalHeadXmlTest} by Paul Bender
 *
 * @author   Joe Martin  Copyright (C) 2020
 */
public class MqttDoubleOutputSignalHeadXmlTest {

    @Test
    public void testCtor(){
      Assert.assertNotNull("MqttDoubleOutputSignalHeadXml constructor",new MqttDoubleOutputSignalHeadXml());
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
