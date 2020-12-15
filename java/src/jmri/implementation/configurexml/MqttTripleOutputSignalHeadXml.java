/**
 * 
 */
package jmri.implementation.configurexml;

import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jmri.InstanceManager;
import jmri.SignalHead;
import jmri.implementation.MqttTripleOutputSignalHead;
import jmri.implementation.VirtualSignalHead;
import jmri.jmrix.mqtt.MqttSystemConnectionMemo;
import jmri.managers.configurexml.AbstractNamedBeanManagerConfigXML;

/**
 * Handle XML configuration for MqttTripleOutputSignalHead objects.
 * 
 * Based upon {@link jmri.implementation.configurexml.VirtualSignalHeadXml} by Bob Jacobsen
 * 
 * @author Joe Martin Copyright (C) 2020
 *
 */
public class MqttTripleOutputSignalHeadXml extends AbstractNamedBeanManagerConfigXML {

    /**
     * Default constructor, doesn't do anything.
     */
    public MqttTripleOutputSignalHeadXml() {
    }

    /**
     * Call for storing the contents of a MqttTripleOutputSignalHead.
     *
     * @param o Object to store, of type MqttTripleOutputSignalHead
     * @return Element containing the complete info
     */
    @Override
    public Element store(Object o) {
        MqttTripleOutputSignalHead p = (MqttTripleOutputSignalHead) o;

        Element element = new Element("signalhead");
        element.setAttribute("class", this.getClass().getName());

        // include contents
        element.addContent(new Element("systemName").addContent(p.getSystemName()));
        
        // Store common elements, including userName, which must come before canFlash
        storeCommon(p, element);
        
        // Store canFlash
        if (p.canFlash()){
            element.addContent(new Element("canFlash").addContent("true"));
        } else {
            element.addContent(new Element("canFlash").addContent("false"));
        }

        return element;
    }

    /**
     * Call for loading an MqttTripleOutputSignalHead from XML and instantiating the object
     * 
     * @param shared
     * @param perNode
     */
    @Override
    public boolean load(Element shared, Element perNode) {
        // put it together
        String sys = getSystemName(shared);
        String uname = getUserName(shared);
        boolean canFlash = false;
        SignalHead h;
        
        MqttSystemConnectionMemo memo;
        try {
            memo = InstanceManager.getDefault(jmri.jmrix.mqtt.MqttSystemConnectionMemo.class);
        } catch (NullPointerException e) {
            // Null here means we have MQTT signal heads defined, but no MQTT broker.
            log.error("Trying to load MQTT Signal Heads, but no MQTT brokers defined.  You must add an MQTT connection before loading this file.");
            return false;
        }
        
        String topicPrefix = memo.getMqttAdapter().getOptionState("14");    // TopicSignalHead
        
        // Get topic suffix (systemName without system prefix)
        String suffix = sys.substring(memo.getSystemPrefix().length() + 1);

        String sendTopic = java.text.MessageFormat.format(
            topicPrefix.contains("{0}") ? topicPrefix : (topicPrefix + "{0}"),
            suffix);
        String rcvTopic = java.text.MessageFormat.format(
            topicPrefix.contains("{0}") ? topicPrefix : (topicPrefix + "{0}"),
            suffix);
        
        // read element for canFlash
        if (shared.getChild("canFlash") != null) {
            canFlash = shared.getChild("canFlash").getText().equals("true");
        }
        
        if (uname == null) {
            h = new MqttTripleOutputSignalHead(memo.getMqttAdapter(), sys, null, sendTopic, rcvTopic,canFlash);
        } else {
            h = new MqttTripleOutputSignalHead(memo.getMqttAdapter(), sys, uname, sendTopic, rcvTopic,canFlash);
        }

        loadCommon(h, shared);

        SignalHead existingBean =
                InstanceManager.getDefault(jmri.SignalHeadManager.class)
                        .getBySystemName(sys);

        if ((existingBean != null) && (existingBean != h)) {
            log.error("systemName is already registered: {}", sys);
        } else {
            InstanceManager.getDefault(jmri.SignalHeadManager.class).register(h);
        }

        return true;
    }

    private final static Logger log = LoggerFactory.getLogger(MqttTripleOutputSignalHead.class);

}
