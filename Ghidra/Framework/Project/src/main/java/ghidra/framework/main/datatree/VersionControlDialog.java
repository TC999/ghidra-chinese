/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.framework.main.datatree;

import java.awt.BorderLayout;
import java.awt.Component;

import javax.swing.*;

import docking.DialogComponentProvider;
import docking.widgets.checkbox.GCheckBox;
import docking.widgets.label.*;
import generic.theme.GIcon;
import ghidra.app.util.GenericHelpTopics;
import ghidra.framework.plugintool.PluginTool;
import ghidra.util.HelpLocation;

/**
 * Dialog to get comments for adding a file to version control or 
 * checking in a file.
 * 
 */
public class VersionControlDialog extends DialogComponentProvider {

	private static final Icon ADD_ICON = new GIcon("icon.version.control.dialog.add");
	private static final Icon CHECK_IN_ICON = new GIcon("icon.version.control.dialog.check.in");

	static final int OK = 0;
	public static final int APPLY_TO_ALL = 1;
	public static final int CANCEL = 2;

	private JCheckBox keepCB; // keep checked out
	private JCheckBox keepFileCB; // create keep file
	private JLabel descriptionLabel;
	private JTextArea commentsTextArea;
	private JButton allButton; // apply to all
	private int actionID;
	private boolean addToVersionControl; // true if the dialog is for adding

	/**
	 * Constructor
	 * @param addToVersionControl true for adding; false for check-in
	 */
	public VersionControlDialog(boolean addToVersionControl) {
		super(addToVersionControl ? "添加文件至版本控制" : "检入文件", true);
		this.addToVersionControl = addToVersionControl;
		addWorkPanel(buildMainPanel());

		allButton = new JButton("全部应用");
		allButton.getAccessibleContext().setAccessibleName("All");
		allButton.setMnemonic('A');
		allButton.addActionListener(e -> {
			actionID = APPLY_TO_ALL;
			close();
		});

		addOKButton();
		addButton(allButton);
		addCancelButton();
		String tag = addToVersionControl ? "Add_to_Version_Control" : "CheckIn";
		setHelpLocation(new HelpLocation(GenericHelpTopics.REPOSITORY, tag));
		setRememberLocation(false);
		setRememberSize(false);
	}

	@Override
	protected void cancelCallback() {
		actionID = CANCEL;
		close();
	}

	@Override
	protected void okCallback() {
		actionID = OK;
		close();
	}

	/**
	 * Show the dialog; return an ID for the action that the user chose.
	 * @param tool the tool
	 * @param parent parent to this dialog
	 * @return OK, APPLY_TO_ALL, or CANCEL
	 */
	int showDialog(PluginTool tool, Component parent) {
		tool.showDialog(this, parent);
		return actionID;
	}

	void setMultiFiles(boolean multi) {
		allButton.setEnabled(multi);
	}

	boolean keepCheckedOut() {
		return keepCB.isSelected();
	}

	void setKeepCheckedOut(boolean selected) {
		keepCB.setSelected(selected);
	}

	boolean shouldCreateKeepFile() {
		if (addToVersionControl) {
			return false;
		}
		return keepFileCB.isSelected();
	}

	/**
	 * Return the comments for the add to version control.
	 * @return may be the empty string
	 */
	String getComments() {
		return commentsTextArea.getText();
	}

	/*
	 * Disable the check box for "keep checked out" because some files are still in use. 
	 * @param enabled true if checkbox control should be enabled, false if disabled
	 * @param selected true if default state should be selected, else not-selected
	 * @param disabledMsg tooltip message if enabled is false, otherwise ignored.
	 */
	public void setKeepCheckboxEnabled(boolean enabled, boolean selected, String disabledMsg) {
		keepCB.setEnabled(enabled);
		keepCB.setSelected(selected);
		keepCB.setToolTipText(enabled ? "" : disabledMsg);
	}

	private JPanel buildMainPanel() {

		JPanel innerPanel = new JPanel();
		innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.Y_AXIS));
		innerPanel.getAccessibleContext().setAccessibleName("版本控制");
		Icon icon = addToVersionControl ? ADD_ICON : CHECK_IN_ICON;

		descriptionLabel = new GDLabel(addToVersionControl ? "添加评论解释文件。"
				: "添加评论解释更改。",
			SwingConstants.LEFT);
		descriptionLabel.getAccessibleContext().setAccessibleName("介绍");
		JPanel dPanel = new JPanel(new BorderLayout(10, 0));
		dPanel.add(new GIconLabel(icon), BorderLayout.WEST);
		dPanel.add(descriptionLabel, BorderLayout.CENTER);
		dPanel.getAccessibleContext().setAccessibleName("介绍");

		JPanel cPanel = new JPanel(new BorderLayout());
		cPanel.add(new GLabel("评论：", SwingConstants.LEFT));
		cPanel.getAccessibleContext().setAccessibleName("评论");
		commentsTextArea = new JTextArea(4, 20);
		JScrollPane sp = new JScrollPane(commentsTextArea);
		sp.getAccessibleContext().setAccessibleName("评论");
		keepCB = new GCheckBox("保持文件已检出", true);
		JPanel kPanel = new JPanel(new BorderLayout());
		kPanel.add(keepCB, BorderLayout.WEST);
		kPanel.getAccessibleContext().setAccessibleName("保持文件");

		innerPanel.add(Box.createVerticalStrut(10));
		innerPanel.add(dPanel);
		innerPanel.add(Box.createVerticalStrut(5));
		innerPanel.add(cPanel);
		innerPanel.add(sp);
		innerPanel.add(Box.createVerticalStrut(5));
		innerPanel.add(kPanel);

		if (!addToVersionControl) {
			keepFileCB = new GCheckBox("创建 \".keep\" 文件", false);
			JPanel kpPanel = new JPanel(new BorderLayout());
			kpPanel.add(keepFileCB, BorderLayout.WEST);
			kpPanel.getAccessibleContext().setAccessibleName("保持文件");
			innerPanel.add(kpPanel);
		}

		JPanel mainPanel = new JPanel(new BorderLayout());
		mainPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
		mainPanel.add(innerPanel);
		mainPanel.getAccessibleContext().setAccessibleName("版本控制");
		return mainPanel;
	}

	/**
	 * Set the name of the current file being added to version control or being updated.
	 * @param filename the name of the file currently to be added, whose comment we need.
	 */
	public void setCurrentFileName(String filename) {
		String description = addToVersionControl ? "添加评论解释 " + filename + "。"
				: "添加评论解释更改至 " + filename + "。";
		descriptionLabel.setText(description);
	}
}
