package jmri.jmrit.beantable;

import apps.gui.GuiLafPreferencesManager;
import java.awt.GraphicsEnvironment;
import javax.swing.JFrame;
import jmri.Block;
import jmri.InstanceManager;
import jmri.util.JUnitUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for the jmri.jmrit.beantable.BlockTableAction class
 *
 * @author	Bob Jacobsen Copyright 2004, 2007, 2008
 */
public class BlockTableActionTest extends AbstractTableActionBase {

    @Test
    public void testCreate() {
        Assert.assertNotNull(a);
        Assert.assertNull(a.f); // frame should be null until action invoked
    }

    @Override
    public String getTableFrameName(){
        return Bundle.getMessage("TitleBlockTable");
    }

    @Override
    @Test
    public void testGetClassDescription(){
         Assert.assertEquals("Block Table Action class description","Block Table",a.getClassDescription());
    }

    /**
     * Check the return value of includeAddButton.  The table generated by 
     * this action includes an Add Button.
     */
    @Override
    @Test
    public void testIncludeAddButton(){
         Assert.assertTrue("Default include add button",a.includeAddButton());
    }
 
    @Test
    public void testInvoke() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        a.actionPerformed(null);

        JFrame f = JFrameOperator.waitJFrame(Bundle.getMessage("TitleBlockTable"), true, true);
        Assert.assertNotNull(f);
        // create a couple of blocks, and see if they show
        InstanceManager.getDefault(jmri.BlockManager.class).createNewBlock("IB1", "block 1");

        Block b2 = InstanceManager.getDefault(jmri.BlockManager.class).createNewBlock("IB2", "block 2");
        Assert.assertNotNull(b2);
        b2.setDirection(jmri.Path.EAST);

        // set graphic state column display preference to false, read by createModel()
        InstanceManager.getDefault(GuiLafPreferencesManager.class).setGraphicTableState(false);

        BlockTableAction _bTable;
        _bTable = new BlockTableAction();
        Assert.assertNotNull("found BlockTable frame", _bTable);

        // assert blocks show in table
        //Assert.assertEquals("Block1 getValue","(no name)",_bTable.getValue(null)); // taken out for now, returns null on CI?
        //Assert.assertEquals("Block1 getValue","(no Block)",_bTable.getValue("nonsenseBlock"));
        Assert.assertEquals("Block1 getValue","IB1",_bTable.getValue("block 1"));
        // test value in table
        //Assert.assertEquals("Block2 getColumnCount",5,_bTable.getColumnCount()); // cannot directly acces _bTable methods?
        //Assert.assertEquals("Block2 getValueAt",1,_bTable.getValueAt(1,2));

        // set to true, use icons
        InstanceManager.getDefault(GuiLafPreferencesManager.class).setGraphicTableState(true);
        BlockTableAction _b1Table;
        _b1Table = new BlockTableAction();
        Assert.assertNotNull("found BlockTable1 frame", _b1Table);

        _b1Table.addPressed(null);
        JFrame af = JFrameOperator.waitJFrame(Bundle.getMessage("TitleAddBlock"), true, true);
        Assert.assertNotNull("found Add frame", af);

//        // wait 1 sec (nothing to see)
//        Runnable waiter = new Runnable() {
//            @Override
//            public synchronized void run() {
//                try {
//                    this.wait(1000);
//                } catch (InterruptedException ex) {
//                    log.error("Waiter interrupted.");
//                }
//            }
//        };
//        waiter.run();

        // close AddPane
        _b1Table.cancelPressed(null);

        // clean up
        af.dispose();
        b2 = null;
        _bTable.dispose();
        _b1Table.dispose();
        f.dispose();
    }

    @Before
    @Override
    public void setUp() {
        apps.tests.Log4JFixture.setUp();
        JUnitUtil.resetInstanceManager();
        JUnitUtil.initDefaultUserMessagePreferences();
        JUnitUtil.initInternalTurnoutManager();
        JUnitUtil.initInternalLightManager();
        JUnitUtil.initInternalSensorManager();
        JUnitUtil.initInternalSignalHeadManager();
        a = new BlockTableAction();
    }

    @After
    @Override
    public void tearDown() {
        a = null;
        JUnitUtil.resetInstanceManager();
        apps.tests.Log4JFixture.tearDown();
    }

    private final static Logger log = LoggerFactory.getLogger(BlockTableActionTest.class.getName());

}
