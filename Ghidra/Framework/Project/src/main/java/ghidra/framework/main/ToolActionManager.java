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
package ghidra.framework.main;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.*;
import java.util.*;

import org.jdom.Element;
import org.jdom.input.SAXBuilder;

import docking.ActionContext;
import docking.action.*;
import docking.tool.ToolConstants;
import docking.widgets.filechooser.GhidraFileChooser;
import ghidra.framework.model.*;
import ghidra.framework.plugintool.PluginTool;
import ghidra.framework.preferences.Preferences;
import ghidra.framework.project.tool.GhidraToolTemplate;
import ghidra.util.HelpLocation;
import ghidra.util.Msg;
import ghidra.util.filechooser.ExtensionFileFilter;
import ghidra.util.xml.XmlUtilities;
import resources.ResourceManager;

/**
 * Helper class to manage actions on the Tool menu.
 */
class ToolActionManager implements ToolChestChangeListener {
	private final static int TYPICAL_NUM_TOOLS_IN_TOOLCHEST = 5;
	private final static int NEWTOOL_ACCELERATOR = KeyEvent.VK_T;

	private static final String MENU_ITEM_CREATE_TOOL = "&创建工具..."; // Group: ATools	
	private static final String MENU_ITEM_RUN_TOOL = "&运行工具"; // Group: BTools	
	private static final String MENU_ITEM_DELETE_TOOL = "删除工具"; // Group: CTools	
	private static final String MENU_ITEM_IMPORT_TOOL = "&导入工具..."; // Group: DTools
	private static final String MENU_ITEM_IMPORT_DEFAULT_TOOLS = "导入&默认工具...";
	private static final String MENU_ITEM_EXPORT_TOOL = "&导出工具";
	private static final String MENU_ITEM_CONNECT_TOOLS = "连接&工具..."; // Group: ETools     
	private static final String MENU_ITEM_SET_DEFAULT_TOOL = "&设为默认"; // Group: FTools 

	private FrontEndPlugin plugin;
	private FrontEndTool tool;
	private ToolConnectionDialog toolConnectionDialog;
	private DockingAction createToolAction;
	private DockingAction connectToolsAction;
	private DockingAction importAction;
	private DockingAction importDefaultToolsAction;
	private DockingAction setToolAssociationsAction;

	private Map<String, DockingAction> runToolActionMap;
	private Map<String, DockingAction> delToolActionMap;
	private Map<String, DockingAction> exportToolActionMap;

	ToolActionManager(FrontEndPlugin fePlugin) {
		plugin = fePlugin;
		tool = (FrontEndTool) plugin.getTool();

		// initialize the table of tool menu items
		runToolActionMap = new HashMap<>(TYPICAL_NUM_TOOLS_IN_TOOLCHEST);
		delToolActionMap = new HashMap<>(TYPICAL_NUM_TOOLS_IN_TOOLCHEST);
		exportToolActionMap = new HashMap<>(TYPICAL_NUM_TOOLS_IN_TOOLCHEST);

		createActions();
	}

	void enableActions(boolean enabled) {
		createToolAction.setEnabled(enabled);
		enableConnectTools();
		enableActions(runToolActionMap, enabled);
		enableActions(delToolActionMap, enabled);
		enableActions(exportToolActionMap, enabled);

		importAction.setEnabled(enabled);
		importDefaultToolsAction.setEnabled(enabled);
		setToolAssociationsAction.setEnabled(enabled);
	}

	void updateConnectionDialog() {
		if (toolConnectionDialog != null) {
			toolConnectionDialog.updateDisplay();
		}
		enableConnectTools();
	}

	void setActiveProject(Project activeProject) {
		if (toolConnectionDialog != null) {
			if (activeProject != null) {
				toolConnectionDialog.setToolManager(activeProject.getToolManager());
			}
			else if (toolConnectionDialog.isVisible()) {
				toolConnectionDialog.setVisible(false);
			}
		}
		populateToolMenus(activeProject);
	}

	private void createActions() {
		// create the menu items and listeners
		createToolAction = new DockingAction("Create Tool", plugin.getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				createNewTool();
			}
		};
		createToolAction.setKeyBindingData(
			new KeyBindingData(NEWTOOL_ACCELERATOR, ActionEvent.CTRL_MASK));
		createToolAction.setMenuBarData(new MenuData(
			new String[] { ToolConstants.MENU_TOOLS, MENU_ITEM_CREATE_TOOL }, null, "ATools"));
		createToolAction.setEnabled(false);
		createToolAction.setHelpLocation(
			new HelpLocation(ToolConstants.TOOL_HELP_TOPIC, "Create_Tool"));

		tool.addAction(createToolAction);

		importAction = new DockingAction("导入工具", plugin.getName()) {
			@Override
			public void actionPerformed(ActionContext e) {
				importTool();
			}
		};
		importAction.setMenuBarData(new MenuData(
			new String[] { ToolConstants.MENU_TOOLS, MENU_ITEM_IMPORT_TOOL }, null, "DTools"));
		importAction.setHelpLocation(
			new HelpLocation(ToolConstants.TOOL_HELP_TOPIC, "导入工具"));
		importAction.setEnabled(false);

		tool.addAction(importAction);

		importDefaultToolsAction = new DockingAction("导入 Ghidra 工具", plugin.getName()) {
			@Override
			public void actionPerformed(ActionContext e) {
				addDefaultTools();
			}
		};
		importDefaultToolsAction.setMenuBarData(
			new MenuData(new String[] { ToolConstants.MENU_TOOLS, MENU_ITEM_IMPORT_DEFAULT_TOOLS },
				null, "DTools"));
		importDefaultToolsAction.setHelpLocation(
			new HelpLocation(ToolConstants.TOOL_HELP_TOPIC, "导入 Ghidra 工具"));
		importDefaultToolsAction.setEnabled(false);
		tool.addAction(importDefaultToolsAction);

		connectToolsAction = new DockingAction("连接工具", plugin.getName()) {
			@Override
			public void actionPerformed(ActionContext e) {
				connectTools();
			}
		};
		connectToolsAction.setMenuBarData(new MenuData(
			new String[] { ToolConstants.MENU_TOOLS, MENU_ITEM_CONNECT_TOOLS }, null, "ETools"));
		connectToolsAction.setEnabled(false);
		tool.addAction(connectToolsAction);

		setToolAssociationsAction = new DockingAction("设置工具关联", plugin.getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				showToolAssociationsDialog();
			}
		};
		setToolAssociationsAction.setEnabled(false);
		setToolAssociationsAction.setMenuBarData(new MenuData(
			new String[] { ToolConstants.MENU_TOOLS, "设置工具关联..." }, null, "FTools"));
		tool.addAction(setToolAssociationsAction);

		setToolAssociationsAction.setHelpLocation(
			new HelpLocation(ToolConstants.TOOL_HELP_TOPIC, "设置工具关联"));

		tool.setMenuGroup(new String[] { ToolConstants.MENU_TOOLS, MENU_ITEM_RUN_TOOL }, "BTools");
		tool.setMenuGroup(new String[] { ToolConstants.MENU_TOOLS, MENU_ITEM_DELETE_TOOL },
			"CTools");
		tool.setMenuGroup(new String[] { ToolConstants.MENU_TOOLS, MENU_ITEM_EXPORT_TOOL },
			"DTools");
		tool.setMenuGroup(new String[] { ToolConstants.MENU_TOOLS, MENU_ITEM_SET_DEFAULT_TOOL },
			"FTools");

		// populate the menu items corresponding to the tool templates
		// in the project's toolchest
		populateToolMenus(plugin.getActiveProject());
	}

	private void showToolAssociationsDialog() {
		SetToolAssociationsDialog dialog = new SetToolAssociationsDialog(tool);
		dialog.showDialog();
	}

	/**
	 * Get the default tools from the defaultTools location.
	 */
	private void addDefaultTools() {
		ImportGhidraToolsDialog dialog = new ImportGhidraToolsDialog(tool);
		dialog.showDialog();
		if (dialog.isCancelled()) {
			return;
		}

		List<String> list = dialog.getSelectedList();
		for (String filename : list) {
			addDefaultTool(filename);
		}
	}

	private void addDefaultTool(String filename) {
		try {
			InputStream is = ResourceManager.getResourceAsStream(filename);
			addToolTemplate(is, filename);
		}
		catch (Exception e) {
			Msg.showError(this, null, "错误", "加载默认工具出错：" + filename, e);
		}
	}

	private void enableActions(Map<String, DockingAction> map, boolean enabled) {
		Iterator<String> iter = map.keySet().iterator();
		while (iter.hasNext()) {
			String name = iter.next();
			DockingAction action = map.get(name);
			action.setEnabled(enabled);
		}
	}

	private void populateToolMenus(Project activeProject) {
		removeActions(runToolActionMap);
		removeActions(delToolActionMap);
		removeActions(exportToolActionMap);

		// get the active workspace to host the running tool
		if (activeProject == null) {
			createPlaceHolderActions();
			return;
		}

		ToolTemplate[] templates = activeProject.getLocalToolChest().getToolTemplates();
		for (ToolTemplate template : templates) {
			addConfig(template);
		}

		// if there are no tools in the toolchest, disable menus
		if (templates.length == 0) {
			createPlaceHolderActions();
		}
	}

	private void removeActions(Map<String, DockingAction> map) {
		Iterator<String> iter = map.keySet().iterator();
		while (iter.hasNext()) {
			String toolName = iter.next();
			DockingAction action = map.get(toolName);
			tool.removeAction(action);
		}
		map.clear();
	}

	/**
	 * Remove the action named toolName.
	 * 
	 * @param map map to search for the action
	 * @param toolName name of the action (happens to be the name of the tool)
	 */
	private void removeDefaultAction(Map<String, DockingAction> map, String toolName) {
		DockingAction action = map.get(toolName);
		if (action != null) {
			tool.removeAction(action);
			map.remove(toolName);
		}
	}

	/**
	 * Pop up the connect tools dialog.
	 */
	private void connectTools() {
		ToolManager tm = plugin.getActiveProject().getToolManager();
		if (toolConnectionDialog == null) {
			toolConnectionDialog = new ToolConnectionDialog(tool, tm);
		}
		else {
			toolConnectionDialog.setToolManager(tm);
		}
		toolConnectionDialog.setVisible(true); // dialog handles the connections
	}

	/**
	 * Disable the connect tools if 1 or less than 1 tool is running.
	 */
	void enableConnectTools() {
		Project project = plugin.getActiveProject();
		if (project == null) {
			connectToolsAction.setEnabled(false);
			return;
		}
		// only enable if project has more than 1 running tool
		ToolManager tm = project.getToolManager();
		PluginTool[] runningTools = tm.getRunningTools();
		connectToolsAction.setEnabled(runningTools.length > 1);
	}

	/**
	 * Create a new tool; pop up the manage plugins dialog.
	 */
	private void createNewTool() {
		// get the active workspace to host the running tool
		Workspace ws = plugin.getActiveWorkspace();

		// create the running tool
		PluginTool runningTool = ws.createTool();

		// whenever we create a new tool, the first thing the
		// user will want to do is configure it, so automatically
		// bring up the manage plugins dialog as well
//        ((PluginTool)runningTool).managePlugins(true);
		// TODO replace old manage plugins with new component

		runningTool.showConfig(true, true);
	}

	/**
	 * ToolConfig was added to the project toolchest
	 */
	@Override
	public void toolTemplateAdded(ToolTemplate tc) {
		populateToolMenus(plugin.getActiveProject());
	}

	/**
	 * ToolSet was added to the project toolchest
	 */
	@Override
	public void toolSetAdded(ToolSet toolset) {
		ToolChest toolChest = plugin.getActiveProject().getLocalToolChest();
		toolTemplateAdded(toolChest.getToolTemplate(toolset.getName()));
	}

	/**
	 * ToolConfig was removed from the project toolchest
	 */
	@Override
	public void toolRemoved(String toolName) {
		removeDefaultAction(runToolActionMap, toolName);
		removeDefaultAction(delToolActionMap, toolName);
		removeDefaultAction(exportToolActionMap, toolName);

		// create default disable menu items if the number of tools
		// in toolchest is now zero
		if (runToolActionMap.size() == 0) {
			createPlaceHolderActions();
		}
	}

	/**
	 * Create default actions as Place holders in the menu.
	 */
	private void createPlaceHolderActions() {
		String owner = plugin.getName();

		DockingAction action = new DockingAction("运行工具", owner) {
			@Override
			public void actionPerformed(ActionContext context) {
				// no-op; placeholder action
			}
		};
		action.setMenuBarData(new MenuData(
			new String[] { ToolConstants.MENU_TOOLS, MENU_ITEM_RUN_TOOL }, null, "BTools"));
		action.setHelpLocation(new HelpLocation(ToolConstants.TOOL_HELP_TOPIC, "运行工具"));
		action.setEnabled(false);

		tool.addAction(action);
		runToolActionMap.put(action.getName(), action);

		action = new DockingAction("删除工具", owner) {
			@Override
			public void actionPerformed(ActionContext context) {
				// no-op; placeholder action
			}
		};
		action.setMenuBarData(new MenuData(
			new String[] { ToolConstants.MENU_TOOLS, MENU_ITEM_DELETE_TOOL }, null, "CTools"));
		action.setHelpLocation(new HelpLocation(ToolConstants.TOOL_HELP_TOPIC, "删除工具"));
		action.setEnabled(false);

		tool.addAction(action);
		delToolActionMap.put(action.getName(), action);

		action = new DockingAction("Export Tool", owner) {
			@Override
			public void actionPerformed(ActionContext context) {
				// no-op; placeholder action
			}
		};
		action.setMenuBarData(new MenuData(
			new String[] { ToolConstants.MENU_TOOLS, MENU_ITEM_EXPORT_TOOL }, null, "DTools"));
		action.setHelpLocation(new HelpLocation(ToolConstants.TOOL_HELP_TOPIC, "导出工具"));
		action.setEnabled(false);

		exportToolActionMap.put(action.getName(), action);
		tool.addAction(action);
	}

	/**
	 * Pop up a file chooser dialog for the user to find the file to import as a
	 * tool.
	 */
	private void importTool() {

		GhidraFileChooser fileChooser = new GhidraFileChooser(tool.getToolFrame());
		fileChooser.setFileFilter(
			new ExtensionFileFilter(new String[] { "tool", "tcd" }, "工具"));
		fileChooser.setTitle("导入工具");
		fileChooser.setApproveButtonText("导入");

		String importDir = Preferences.getProperty(Preferences.LAST_TOOL_IMPORT_DIRECTORY);
		if (importDir != null) {
			File dir = new File(importDir);
			if (dir.isDirectory()) {
				fileChooser.setCurrentDirectory(dir);
			}
		}

		fileChooser.rescanCurrentDirectory();

		File selectedFile = fileChooser.getSelectedFile(true);
		fileChooser.dispose();
		if (selectedFile == null) {
			return;
		}

		if (!selectedFile.exists()) {
			Msg.showError(this, null, "错误",
				"工具 " + selectedFile.getName() + " 不存在！");
		}
		Preferences.setProperty(Preferences.LAST_TOOL_IMPORT_DIRECTORY, selectedFile.getParent());
		try {
			addToolTemplate(new FileInputStream(selectedFile.getAbsolutePath()),
				selectedFile.getAbsolutePath());
		}
		catch (Exception e) {
			Msg.showError(this, tool.getToolFrame(), "创建输入流时出错",
				"创建输入流时出错\n" + selectedFile.getAbsolutePath() + ": " + e, e);
		}
	}

	/**
	 * Create the Template object and add it to the tool chest.
	 */
	private void addToolTemplate(InputStream instream, String path) {
		try {
			SAXBuilder sax = XmlUtilities.createSecureSAXBuilder(false, false);
			Element root = sax.build(instream).getRootElement();

			ToolTemplate template = new GhidraToolTemplate(root, path);
			if (plugin.getActiveProject().getLocalToolChest().addToolTemplate(template)) {
				Msg.info(this,
					"成功添加 " + template.getName() + " 至项目工具箱。");
			}
			else {
				Msg.warn(this, "不能添加 " + template.getName() + " 至项目工具箱。");
			}
		}
		catch (Exception e) {
			Msg.showError(getClass(), tool.getToolFrame(), "读取工具出错",
				"不能读取工具：" + e, e);
		}
	}

	/**
	 * Add a menu for the given tool template.
	 */
	private void addConfig(ToolTemplate template) {
		String toolName = template.getName();

		ToolAction runAction = new ToolAction(toolName, "Run_Tool") {
			@Override
			public void actionPerformed(ActionContext context) {
				String name = getName();
				Workspace ws = plugin.getActiveWorkspace();
				ToolChest toolChest = plugin.getActiveProject().getLocalToolChest();
				ws.runTool(toolChest.getToolTemplate(name));
			}
		};
		runAction.setEnabled(true);
		runAction.setMenuBarData(
			new MenuData(new String[] { ToolConstants.MENU_TOOLS, MENU_ITEM_RUN_TOOL, toolName },
				null, "BTools"));
		runAction.setHelpLocation(new HelpLocation(ToolConstants.TOOL_HELP_TOPIC, "Run_Tool"));

		runToolActionMap.put(toolName, runAction);
		tool.addAction(runAction);

		ToolAction deleteAction = new ToolAction(toolName, "Delete_Tool") {
			@Override
			public void actionPerformed(ActionContext context) {
				String name = getName();
				if (!plugin.confirmDelete(name + " 从项目工具箱？")) {
					return;
				}
				ToolChest toolChest = plugin.getActiveProject().getLocalToolChest();
				toolChest.remove(name);
			}
		};
		deleteAction.setEnabled(true);
		deleteAction.setMenuBarData(
			new MenuData(new String[] { ToolConstants.MENU_TOOLS, MENU_ITEM_DELETE_TOOL, toolName },
				null, "CTools"));
		deleteAction.setHelpLocation(
			new HelpLocation(ToolConstants.TOOL_HELP_TOPIC, "Delete_Tool"));

		delToolActionMap.put(toolName, deleteAction);
		tool.addAction(deleteAction);

		ToolAction exportToolAction = new ToolAction(toolName, "Export_Tool") {
			@Override
			public void actionPerformed(ActionContext context) {
				String name = getName();
				ToolChest toolChest = plugin.getActiveProject().getLocalToolChest();
				plugin.exportToolConfig(toolChest.getToolTemplate(name), "工具目录");
			}
		};
		exportToolAction.setEnabled(true);
		exportToolAction.setMenuBarData(
			new MenuData(new String[] { ToolConstants.MENU_TOOLS, MENU_ITEM_EXPORT_TOOL, toolName },
				null, "DTools"));
		exportToolAction.setHelpLocation(
			new HelpLocation(ToolConstants.TOOL_HELP_TOPIC, "Export_Tool"));

		exportToolActionMap.put(toolName, exportToolAction);
		tool.addAction(exportToolAction);
	}

	/////////////////////////////////////////////////////////////////////
	/**
	 * Subclass to set the help ID for the tool actions whose names are the same
	 * as the tool name for run, delete, and export.
	 *
	 */
	private abstract class ToolAction extends DockingAction {
		private ToolAction(String toolName, String helpStr) {
			super(toolName, plugin.getName(), false);
			setHelpLocation(new HelpLocation("FrontEndPlugin", helpStr));
		}

	}

}
