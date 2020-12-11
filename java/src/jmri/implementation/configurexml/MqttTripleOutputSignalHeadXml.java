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
     * 
     */
    public MqttTripleOutputSignalHeadXml() {
    }

    /**
     * Default implementation for storing the contents of a MqttTripleOutputSignalHead.
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

        storeCommon(p, element);

        return element;
    }

    @Override
    public boolean load(Element shared, Element perNode) {
        // put it together
        String sys = getSystemName(shared);
        String uname = getUserName(shared);
        SignalHead h;
        
        MqttSystemConnectionMemo memo = InstanceManager.getDefault(jmri.jmrix.mqtt.MqttSystemConnectionMemo.class);
        //TODO catch null pointer exception
        
        String topicPrefix = memo.getMqttAdapter().getOptionState("14");    // TopicSignalHead
        //TODO check for null string, shouldn't happen, but might if MQTT not set up, handle gracefully
        
        // Get topic suffix (systemName without system prefix)
        String suffix = sys.substring(memo.getSystemPrefix().length() + 1);

        String sendTopic = java.text.MessageFormat.format(
            topicPrefix.contains("{0}") ? topicPrefix : (topicPrefix + "{0}"),
            suffix);
        String rcvTopic = java.text.MessageFormat.format(
            topicPrefix.contains("{0}") ? topicPrefix : (topicPrefix + "{0}"),
            suffix);
        
        if (uname == null) {
            h = new MqttTripleOutputSignalHead(memo.getMqttAdapter(), sys, null, sendTopic, rcvTopic);;
        } else {
            h = new MqttTripleOutputSignalHead(memo.getMqttAdapter(), sys, uname, sendTopic, rcvTopic);;
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
