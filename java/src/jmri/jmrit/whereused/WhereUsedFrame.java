package jmri.jmrit.whereused;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Font;
import java.awt.event.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import jmri.*;
import jmri.jmrit.logix.OBlock;
import jmri.jmrit.logix.OBlockManager;
import jmri.jmrit.logix.Warrant;
import jmri.jmrit.logix.WarrantManager;
import jmri.swing.NamedBeanComboBox;
import jmri.util.FileUtil;
import jmri.util.swing.JComboBoxUtil;

/**
 * Create a where used report based on the selected bean.  The selection combo box is
 * based on the selected type.

 * @author Dave Sand Copyright (C) 2020
 */
public class WhereUsedFrame extends jmri.util.JmriJFrame {
    ItemType _itemType = ItemType.NONE;
    JComboBox<ItemType> _itemTypeBox;

    NamedBean _itemBean;
    NamedBeanComboBox<?> _itemNameBox = new NamedBeanComboBox<Sensor>(
                        InstanceManager.getDefault(SensorManager.class));

    JPanel _topPanel;
    JPanel _bottomPanel;
    JPanel _scrolltext = new JPanel();
    JTextArea _textArea;
    JButton _createButton;

    public WhereUsedFrame() {
        super(true, true);
        setTitle(Bundle.getMessage("TitleWhereUsed"));  // NOI18N
        createFrame();
    }

    /**
     * Create the window frame.  The top part contains the item type, the item name
     * combo box, and a Create button.  The middle contains the scrollable "where used" text area and the
     * bottom part has a button for saving the content to a file.
     */
    void createFrame() {
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        // Build the top panel
        buildTopPanel();
        contentPane.add(_topPanel, BorderLayout.NORTH);

        // Build an empty where used listing
        JScrollPane scrollPane = null;
        buildWhereUsedListing(ItemType.NONE, null);
        scrollPane = new JScrollPane(_scrolltext);
        contentPane.add(scrollPane);

        // Build the bottom panel
        buildBottomPanel();
        contentPane.add(_bottomPanel, BorderLayout.SOUTH);

        pack();
    }

    void buildTopPanel() {
        _topPanel = new JPanel();
        _topPanel.add(new JLabel(Bundle.getMessage("MakeLabel", Bundle.getMessage("LabelItemType"))));  // NOI18N
        _itemTypeBox = new JComboBox<>();
        for (ItemType itemType : ItemType.values()) {
            _itemTypeBox.addItem(itemType);
        }
        JComboBoxUtil.setupComboBoxMaxRows(_itemTypeBox);
        _topPanel.add(_itemTypeBox);

        _topPanel.add(new JLabel(Bundle.getMessage("MakeLabel", Bundle.getMessage("LabelItemName"))));  // NOI18N
        _topPanel.add(_itemNameBox);
        _itemTypeBox.addActionListener((e) -> {
            _itemType = _itemTypeBox.getItemAt(_itemTypeBox.getSelectedIndex());
            setItemNameBox(_itemType);
        });

        _createButton = new JButton(Bundle.getMessage("ButtonCreate"));  // NOI18N
        _createButton.addActionListener((e) -> buildWhereUsedListing(_itemType, _itemBean));

        _topPanel.add(_createButton);
        _itemNameBox.setEnabled(false);
        _createButton.setEnabled(false);
        return;
    }

    void buildBottomPanel() {
        _bottomPanel = new JPanel();
        _bottomPanel.setLayout(new BorderLayout());

        JButton saveButton = new JButton(Bundle.getMessage("SaveButton"));   // NOI18N
        saveButton.setToolTipText(Bundle.getMessage("SaveButtonHint"));      // NOI18N
        _bottomPanel.add(saveButton, BorderLayout.EAST);
        saveButton.addActionListener((ActionEvent e) -> saveWhereUsedPressed());
    }

    /**
     * Create a new NamedBeanComboBox based on the item type and refresh the panel.
     * A selection listener saves the selection and enables the Create button.
     * @param itemType The enum for the selected item type.
     */
    void setItemNameBox(ItemType itemType) {
        _createButton.setEnabled(false);
        buildWhereUsedListing(ItemType.NONE, null);
        NamedBeanComboBox<?> newNameBox = createNameBox(itemType);
        if (newNameBox == null) {
            _itemNameBox.setSelectedIndex(-1);
            _itemNameBox.setEnabled(false);
            return;
        }
        _itemNameBox = newNameBox;
        _itemNameBox.setSelectedIndex(-1);
        _topPanel.remove(3);
        _topPanel.add(_itemNameBox, 3);

        _itemNameBox.setEnabled(true);
        _itemNameBox.addItemListener((e) -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                _itemBean = (NamedBean) e.getItem();
                _createButton.setEnabled(true);
            }
        });
        pack();
        repaint();
    }

    /**
     * Build the where used content and update the JScrollPane.
     * <p>
     * The selected object is passed to the appropriate detail class which returns a populated textarea.
     * The textarea is formatted and inserted into a scrollable panel.
     */
    void buildWhereUsedListing(ItemType type, NamedBean bean) {
        switch (type) {
            case SENSOR:
                _textArea = SensorWhereUsed.getWhereUsed(bean);
                break;
            case TURNOUT:
                _textArea = TurnoutWhereUsed.getWhereUsed(bean);
                break;
            case LIGHT:
                _textArea = LightWhereUsed.getWhereUsed(bean);
                break;
            case SIGNALHEAD:
                _textArea = SignalHeadWhereUsed.getWhereUsed(bean);
                break;
            case SIGNALMAST:
                _textArea = SignalMastWhereUsed.getWhereUsed(bean);
                break;
            case MEMORY:
                _textArea = MemoryWhereUsed.getWhereUsed(bean);
                break;
            case BLOCK:
                _textArea = BlockWhereUsed.getWhereUsed(bean);
                break;
            case OBLOCK:
                _textArea = OBlockWhereUsed.getWhereUsed(bean);
                break;
            case WARRANT:
                _textArea = WarrantWhereUsed.getWhereUsed(bean);
                break;
            default:
                _textArea = new JTextArea(Bundle.getMessage("TypePrompt"));
                break;
        }

        _textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        _textArea.setTabSize(4);
        _textArea.setEditable(false);
        _textArea.setCaretPosition(0);
        if (_scrolltext.getComponentCount() > 0) {
            _scrolltext.remove(0);
        }
        _scrolltext.add(_textArea);
        pack();
        repaint();
        return;
    }

    JFileChooser userFileChooser = new JFileChooser(FileUtil.getUserFilesPath());

    /**
     * Save the where used textarea content to a text file.
     */
    void saveWhereUsedPressed() {
        userFileChooser.setApproveButtonText(Bundle.getMessage("SaveDialogApprove"));  // NOI18N
        userFileChooser.setDialogTitle(Bundle.getMessage("SaveDialogTitle"));  // NOI18N
        userFileChooser.rescanCurrentDirectory();

        String itemName = _itemNameBox.getSelectedItemDisplayName();
        String fileName = Bundle.getMessage("SaveFileName", (itemName == null) ? "Unknown" : itemName);  // NOI18N
        userFileChooser.setSelectedFile(new File(fileName));
        int retVal = userFileChooser.showSaveDialog(null);
        if (retVal != JFileChooser.APPROVE_OPTION) {
            log.debug("Save where used content stopped, no file selected");  // NOI18N
            return;  // give up if no file selected or cancel pressed
        }
        File file = userFileChooser.getSelectedFile();
        log.debug("Save where used content to '{}'", file);  // NOI18N

        if (file.exists()) {
            Object[] options = {Bundle.getMessage("SaveDuplicateReplace"),  // NOI18N
                    Bundle.getMessage("SaveDuplicateAppend"),  // NOI18N
                    Bundle.getMessage("ButtonCancel")};               // NOI18N
            int selectedOption = JOptionPane.showOptionDialog(null,
                    Bundle.getMessage("SaveDuplicatePrompt", file.getName()), // NOI18N
                    Bundle.getMessage("SaveDuplicateTitle"),   // NOI18N
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null, options, options[0]);
            if (selectedOption == 2 || selectedOption == -1) {
                log.debug("Save where used content stopped, file replace/append cancelled");  // NOI18N
                return;  // Cancel selected or dialog box closed
            }
            if (selectedOption == 0) {
                FileUtil.delete(file);  // Replace selected
            }
        }

        // Create the file content
        try {
            FileUtil.appendTextToFile(file, _textArea.getText());
        } catch (IOException e) {
            log.error("Unable to write where used content to '{}', exception: '{}'", file, e);  // NOI18N
        }
    }

    /**
     * Create a combo name box for name selection.
     *
     * @param itemType The selected bean type
     * @return a combo box based on the item type or null if no match
     */
    NamedBeanComboBox<?> createNameBox(ItemType itemType) {
        NamedBeanComboBox<?> nameBox;
        switch (itemType) {
            case SENSOR:
                nameBox = new NamedBeanComboBox<Sensor>(InstanceManager.getDefault(SensorManager.class));
                break;
            case TURNOUT:
                nameBox = new NamedBeanComboBox<Turnout>(InstanceManager.getDefault(TurnoutManager.class));
                break;
            case LIGHT:
                nameBox = new NamedBeanComboBox<Light>(InstanceManager.getDefault(LightManager.class));
                break;
            case SIGNALHEAD:
                nameBox = new NamedBeanComboBox<SignalHead>(InstanceManager.getDefault(SignalHeadManager.class));
                break;
            case SIGNALMAST:
                nameBox = new NamedBeanComboBox<SignalMast>(InstanceManager.getDefault(SignalMastManager.class));
                break;
            case MEMORY:
                nameBox = new NamedBeanComboBox<Memory>(InstanceManager.getDefault(MemoryManager.class));
                break;
            case BLOCK:
                nameBox = new NamedBeanComboBox<Block>(InstanceManager.getDefault(BlockManager.class));
                break;
            case OBLOCK:
                nameBox = new NamedBeanComboBox<OBlock>(InstanceManager.getDefault(OBlockManager.class));
                break;
            case WARRANT:
                nameBox = new NamedBeanComboBox<Warrant>(InstanceManager.getDefault(WarrantManager.class));
                break;
//             case LOGIX:       // 7
//                 nameBox = new NamedBeanComboBox<Logix>(
//                         InstanceManager.getDefault(LogixManager.class), null, DisplayOptions.DISPLAYNAME);
//                 break;
//             case ENTRYEXIT:   // 11
//                 nameBox = new NamedBeanComboBox<DestinationPoints>(
//                         InstanceManager.getDefault(EntryExitPairs.class), null, DisplayOptions.DISPLAYNAME);
//                 break;
//             case OTHER:   // 14
//                 nameBox = new NamedBeanComboBox<Route>(
//                         InstanceManager.getDefault(jmri.RouteManager.class), null, DisplayOptions.DISPLAYNAME);
//                 break;
            default:
                return null;             // Skip any other items.
        }
        nameBox.setEditable(false);
        nameBox.setValidatingInput(false);
        JComboBoxUtil.setupComboBoxMaxRows(nameBox);
        return nameBox;
    }

    /**
     * The item types.  A bundle key for each type is stored with the type to
     * create a language dependent toString result.
     */
    enum ItemType {
        NONE("ItemTypeNone"),
        SENSOR("ItemTypeSensor"),
        TURNOUT("ItemTypeTurnout"),
        LIGHT("ItemTypeLight"),
        SIGNALHEAD("ItemTypeSignalHead"),
        SIGNALMAST("ItemTypeSignalMast"),
        MEMORY("ItemTypeMemory"),
        BLOCK("ItemTypeBlock"),
        OBLOCK("ItemTypeOBlock"),
        WARRANT("ItemTypeWarrant");
//         ENTRYEXIT(ITEM_TYPE_ENTRYEXIT, IsStateVar.IS_STATE_VAR, "ItemTypeEntryExit"),
//         CONDITIONAL(ITEM_TYPE_CONDITIONAL, IsStateVar.IS_STATE_VAR, "ItemTypeConditional"),  // used only by ConditionalVariable
//         LOGIX(ITEM_TYPE_LOGIX, IsStateVar.IS_STATE_VAR, "ItemTypeLogix"),                    // used only by ConditionalAction
//         CLOCK(ITEM_TYPE_CLOCK, IsStateVar.IS_STATE_VAR, "ItemTypeClock"),
//
//         AUDIO(ITEM_TYPE_AUDIO, IsStateVar.IS_NOT_STATE_VAR, "ItemTypeAudio"),
//         SCRIPT(ITEM_TYPE_SCRIPT, IsStateVar.IS_NOT_STATE_VAR, "ItemTypeScript"),
//         OTHER(ITEM_TYPE_OTHER, IsStateVar.IS_NOT_STATE_VAR, "ItemTypeOther");

        private final String _bundleKey;

        private ItemType(String bundleKey) {
            _bundleKey = bundleKey;
        }

        @Override
        public String toString() {
            return Bundle.getMessage(_bundleKey);
        }
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WhereUsedFrame.class);

}
