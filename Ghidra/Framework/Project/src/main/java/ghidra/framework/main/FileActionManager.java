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
import java.util.ArrayList;
import java.util.List;

import javax.swing.KeyStroke;

import docking.ActionContext;
import docking.action.*;
import docking.action.builder.ActionBuilder;
import docking.tool.ToolConstants;
import docking.widgets.OptionDialog;
import docking.widgets.filechooser.GhidraFileChooser;
import docking.wizard.WizardDialog;
import ghidra.framework.client.RepositoryAdapter;
import ghidra.framework.main.wizard.project.ProjectWizardModel;
import ghidra.framework.model.*;
import ghidra.framework.options.SaveState;
import ghidra.framework.plugintool.PluginTool;
import ghidra.framework.plugintool.PluginToolAccessUtils;
import ghidra.framework.store.LockException;
import ghidra.util.*;
import ghidra.util.exception.NotFoundException;
import ghidra.util.task.TaskLauncher;

/**
 * Helper class to manage actions on the File menu.
 */
class FileActionManager {

	private final static int CLOSE_ACCELERATOR = KeyEvent.VK_W;
	private final static int SAVE_ACCELERATOR = KeyEvent.VK_S;

	private final static String LAST_SELECTED_PROJECT_DIRECTORY = "LastSelectedProjectDirectory";
	private static final String DISPLAY_DATA = "DISPLAY_DATA";

	private FrontEndTool tool;
	private FrontEndPlugin plugin;

	private DockingAction closeProjectAction;
	private DockingAction saveAction;

	private List<ViewInfo> reopenList;

	private boolean firingProjectOpened;

	FileActionManager(FrontEndPlugin plugin) {
		this.plugin = plugin;
		tool = (FrontEndTool) plugin.getTool();
		reopenList = new ArrayList<>();
		createActions();
	}

	/**
	 * creates all the menu items for the File menu
	 */
	private void createActions() {
		new ActionBuilder("新建项目", plugin.getName())
				.menuPath(ToolConstants.MENU_FILE, "新建项目...")
				.menuGroup("AProject")
				.keyBinding("ctrl N")
				.onAction(c -> newProject())
				.buildAndInstall(tool);

		new ActionBuilder("打开项目", plugin.getName())
				.menuPath(ToolConstants.MENU_FILE, "打开项目...")
				.menuGroup("AProject")
				.keyBinding("ctrl O")
				.onAction(c -> openProject())
				.buildAndInstall(tool);

		saveAction = new DockingAction("保存项目", plugin.getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				saveProject();
				tool.saveToolConfigurationToDisk();
			}
		};
		saveAction.setEnabled(false);
		saveAction.setKeyBindingData(
			new KeyBindingData(KeyStroke.getKeyStroke(SAVE_ACCELERATOR, ActionEvent.CTRL_MASK)));
		saveAction.setMenuBarData(
			new MenuData(new String[] { ToolConstants.MENU_FILE, "保存项目" }, "BProject"));
		tool.addAction(saveAction);

		closeProjectAction = new DockingAction("关闭项目", plugin.getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				closeProject(false); // not exiting
			}
		};
		closeProjectAction.setEnabled(false);
		closeProjectAction.setKeyBindingData(
			new KeyBindingData(KeyStroke.getKeyStroke(CLOSE_ACCELERATOR, ActionEvent.CTRL_MASK)));
		closeProjectAction.setMenuBarData(
			new MenuData(new String[] { ToolConstants.MENU_FILE, "关闭项目" }, "BProject"));
		tool.addAction(closeProjectAction);

		new ActionBuilder("删除项目", plugin.getName())
				.menuPath(ToolConstants.MENU_FILE, "删除项目...")
				.menuGroup("CProject")
				.onAction(c -> deleteProject())
				.buildAndInstall(tool);

	}

	/**
	 * creates the recent projects menu
	 */
	void buildRecentProjectsMenu() {

		for (ViewInfo info : reopenList) {
			tool.removeAction(info.getAction());
		}

		reopenList.clear();

		ProjectLocator[] recentProjects = plugin.getRecentProjects();

		// the project manager maintains the order of the projects
		// with the most recent being first in the list
		for (ProjectLocator projectLocator : recentProjects) {
			String filename = projectLocator.toString();
			DockingAction action = new ReopenProjectAction(projectLocator, filename);
			reopenList.add(new ViewInfo(action, projectLocator.getURL()));
			tool.addAction(action);
		}
	}

	/**
	 * Create a new project using a wizard to get the project information.
	 */
	void newProject() {
		ProjectWizardModel model = new ProjectWizardModel(tool);
		WizardDialog dialog = new WizardDialog(model);
		dialog.show(tool.getToolFrame());

		if (model.wasCancelled()) {
			return;
		}

		if (!closeProject(false)) { // false --> not exiting
			return; // user canceled
		}

		ProjectLocator projectLocator = model.getProjectLocator();
		RepositoryAdapter repository = model.getRepository();

		Project newProject = createProject(projectLocator, repository);
		if (newProject == null && repository != null) {
			repository.disconnect();
			return;
		}

		// make the new project the active one
		tool.setActiveProject(newProject);

		// update our list of recent projects
		plugin.rebuildRecentMenus();

		if (newProject != null) {
			openProjectAndNotify(newProject);
		}
	}

	private Project createProject(ProjectLocator locator, RepositoryAdapter repository) {
		try {
			return tool.getProjectManager().createProject(locator, repository, true);
		}
		catch (Exception e) {
			Msg.showError(this, tool.getToolFrame(), "创建项目出错",
				"创建项目出错 '" + locator.getName() + "': " + e.getMessage(), e);
		}
		return null;
	}

	private void openProject() {
		ProjectLocator currentProjectLocator = null;
		Project activeProject = plugin.getActiveProject();
		if (activeProject != null) {
			currentProjectLocator = activeProject.getProjectLocator();
		}

		GhidraFileChooser fileChooser = plugin.createFileChooser(LAST_SELECTED_PROJECT_DIRECTORY);
		ProjectLocator projectLocator =
			plugin.chooseProject(fileChooser, "Open", LAST_SELECTED_PROJECT_DIRECTORY);
		if (projectLocator != null) {
			if (!doOpenProject(projectLocator) && currentProjectLocator != null) {
				doOpenProject(currentProjectLocator);
			}
		}
		fileChooser.dispose();
	}

	private class OpenTaskRunnable implements Runnable {

		private final ProjectLocator newProjectLocator;
		private boolean result = false;

		OpenTaskRunnable(ProjectLocator newProjectLocator) {
			this.newProjectLocator = newProjectLocator;
		}

		@Override
		public void run() {
			result = doOpenProject(newProjectLocator);
		}

		boolean getResult() {
			return result;
		}
	}

	/**
	 * Opens the given project in a task that will show a dialog to block input while opening
	 * the project in the swing thread.
	 * @param projectLocator the project locator
	 * @return true if the project was opened 
	 */
	final boolean openProject(ProjectLocator projectLocator) {
		OpenTaskRunnable openRunnable = new OpenTaskRunnable(projectLocator);
		TaskLauncher.launchModal("打开项目中", () -> Swing.runNow(openRunnable));
		return openRunnable.getResult();
	}

	/**
	 * Open an existing project, using a file chooser to specify where the
	 * existing project folder is stored.
	 * @param projectLocator the project locator
	 * @return true if the project was opened
	 */
	final boolean doOpenProject(ProjectLocator projectLocator) {
		Project project = null;
		try {
			// first close the active project (if there is one)
			// but if user cancels operation, don't continue
			if (!closeProject(false)) {
				return true;
			}
			ProjectManager pm = plugin.getProjectManager();
			project = pm.openProject(projectLocator, true, false);
			firingProjectOpened = true;
			tool.setActiveProject(project);
			openProjectAndNotify(project);
			firingProjectOpened = false;
			Msg.info(this, "已打开项目：" + projectLocator.getName());
		}
		catch (NotFoundException nfe) {
			String msg = "未找到项目：" + projectLocator;
			Msg.showInfo(getClass(), tool.getToolFrame(), "打开项目出错", msg);
			Msg.error(this, msg);
		}
		catch (NotOwnerException e) {
			Msg.showError(this, null, "不是项目所有者", "不能打开项目 " + projectLocator +
				"\n" + e.getMessage() +
				"\n \n每个用户必须创建自己的项目，如有需要，可以通过在自己打开的项目中使用“查看其他”操作查看\n"+
				"其他用户的项目并复制文件，或者创建“共享项目”以允许一组用户使用基于服务器的共享仓库。\n");
			Msg.error(this,  "不能打开项目: " + e.getMessage());
		}
		catch (LockException e) {
			Msg.showInfo(this, null, "打开项目失败", e.getMessage());
		}
		catch (Exception e) {
			Msg.showError(this, null, "打开项目失败",
				"打开项目出错: " + projectLocator, e);
		}
		finally {
			// update our list of recent projects
			plugin.rebuildRecentMenus();
		}

		return project != null;
	}

	/**
	 * Obtain domain objects from files and lock.  If unable to lock 
	 * one or more of the files, none are locked and null is returned.
	 * @param files the files
	 * @return locked domain objects, or null if unable to lock
	 * all domain objects.
	 */
	private DomainObject[] lockDomainObjects(List<DomainFile> files) {
		DomainObject[] domainObjects = new DomainObject[files.size()];
		int lastIndex = 0;
		boolean locked = true;
		while (lastIndex < files.size()) {
			try {
				domainObjects[lastIndex] =
					files.get(lastIndex).getDomainObject(this, false, false, null);
			}
			catch (Throwable t) {
				Msg.error(this, "未能获取域对象实例", t);
				locked = false;
				break;
			}
			if (!domainObjects[lastIndex].lock(null)) {
				String title = "退出 Ghidra";
				StringBuffer buf = new StringBuffer();
				DomainObject d = domainObjects[lastIndex];
				buf.append("文件 " + files.get(lastIndex).getPathname() +
					" 当前正在被修改，由\n");
				buf.append("以下操作：\n \n");
				TransactionInfo t = d.getCurrentTransactionInfo();
				List<String> list = t.getOpenSubTransactions();
				for (String element : list) {
					buf.append("\n     ");
					buf.append(element);
				}
				buf.append("\n \n");
				buf.append("您可以退出 Ghidra，但上述操作将被中止，且由这些操作所做的所有更改\n");
				buf.append("（以及自这些操作开始以来所做的所有更改）都将丢失！\n");
				buf.append("您仍然可以选择保存在这些操作开始之前所做的任何更改。");
				buf.append("您是否要中止操作并退出 Ghidra？");

				int result = OptionDialog.showOptionDialog(tool.getToolFrame(), title,
					buf.toString(), "退出 Ghidra", OptionDialog.WARNING_MESSAGE);

				if (result == OptionDialog.CANCEL_OPTION) {
					locked = false;
					domainObjects[lastIndex].release(this);
					break;
				}
				d.forceLock(true, null);
			}
			++lastIndex;
		}
		if (!locked) {
			//skip the last one that could not be locked...
			for (int i = 0; i < lastIndex; i++) {
				domainObjects[i].unlock();
				domainObjects[i].release(this);
			}
			return null;
		}
		return domainObjects;
	}

	/**
	 * menu listener for File | Close Project...
	 * <p>
	 * This method will always save the FrontEndTool and project, but not the data unless 
	 * {@code confirmClose} is called.
	 * 
	 * @param isExiting true if we are closing the project because 
	 * Ghidra is exiting
	 * @return false if user cancels the close operation
	 */
	boolean closeProject(boolean isExiting) {
		// if there is no active project currently, ignore request
		Project activeProject = plugin.getActiveProject();
		if (activeProject == null) {
			return true;
		}

		// check for any changes since last saved
		PluginTool[] runningTools = activeProject.getToolManager().getRunningTools();
		for (PluginTool runningTool : runningTools) {
			if (!PluginToolAccessUtils.canClose(runningTool)) {
				return false;
			}
		}

		boolean saveSuccessful = saveChangedData(activeProject);
		if (!saveSuccessful) {
			return false;
		}

		if (!activeProject.saveSessionTools()) {
			return false;
		}

		doSaveProject(activeProject);

		// close the project
		String name = activeProject.getName();
		ProjectLocator projectLocator = activeProject.getProjectLocator();
		activeProject.close();

		// TODO: This should be done by tool.setActiveProject which should always be invoked
		fireProjectClosed(activeProject);

		if (!isExiting) {
			// update the gui now that active project is closed
			tool.setActiveProject(null);
			Msg.info(this, "已关闭项目：" + name);

			// update the list of project views to include the "active"
			// project that is no longer active
			plugin.rebuildRecentMenus();
			plugin.getProjectManager().setLastOpenedProject(null);
		}
		else {
			plugin.getProjectManager().setLastOpenedProject(projectLocator);
		}

		if (tool.getManagePluginsDialog() != null) {
			tool.getManagePluginsDialog().close();
		}

		return true;
	}

	private void doSaveProject(Project project) {
		project.setSaveableData(DISPLAY_DATA, tool.getSaveableDisplayData());
		project.save();
	}

	private void openProjectAndNotify(Project project) {
		doRestoreProject(project);
		fireProjectOpened(project);
	}

	private void doRestoreProject(Project project) {
		SaveState saveState = project.getSaveableData(DISPLAY_DATA);
		if (saveState == null) {
			return;
		}
		tool.setSaveableDisplayData(saveState);
	}

	private boolean saveChangedData(Project activeProject) {
		List<DomainFile> data = activeProject.getOpenData();
		if (data.isEmpty()) {
			return true;
		}

		DomainObject[] lockedObjects = lockDomainObjects(data);
		if (lockedObjects == null) {
			return false;
		}

		List<DomainFile> changedFiles = getChangedFiles(data);

		try {
			if (!checkReadOnlyFiles(lockedObjects)) {
				return false;
			}

			// pop up dialog to save the data
			SaveDataDialog saveDialog = new SaveDataDialog(tool);
			if (!saveDialog.showDialog(changedFiles)) {
				// user hit the cancel button on the "Save" dialog
				// so cancel closing the project
				return false;
			}
		}
		finally {
			for (DomainObject lockedObject : lockedObjects) {
				lockedObject.unlock();
				lockedObject.release(this);
			}
		}
		return true;
	}

	private List<DomainFile> getChangedFiles(List<DomainFile> data) {
		List<DomainFile> changedFiles = new ArrayList<>();
		for (DomainFile domainFile : data) {
			if (domainFile.isChanged()) {
				changedFiles.add(domainFile);
			}
		}
		return changedFiles;
	}

	void setActiveProject(Project activeProject) {
		plugin.rebuildRecentMenus();
		if (!firingProjectOpened && activeProject != null) {
			openProjectAndNotify(activeProject);
		}
	}

	/**
	 * menu listener for File | Save Project
	 */
	void saveProject() {
		Project project = plugin.getActiveProject();
		if (project == null) {
			return;
		}

		if (!project.saveSessionTools()) {
			// if tools have conflicting options, user is presented with a dialog that can
			// be cancelled. If they press the cancel button, abort the entire save project action.
			return;
		}

		doSaveProject(project);
		Msg.info(this, "已保存项目：" + project.getName());
	}

	private boolean allowDelete(Project activeProject) {
		if (activeProject != null) {
			Msg.showWarn(getClass(), tool.getToolFrame(), "无法删除激活项目",
				"您必须关闭后再删除。");
			return false;
		}
		return true;
	}

	/**
	 * menu listener for File | Delete Project...
	 */
	private void deleteProject() {

		GhidraFileChooser fileChooser = plugin.createFileChooser(LAST_SELECTED_PROJECT_DIRECTORY);
		ProjectLocator projectLocator =
			plugin.chooseProject(fileChooser, "Delete", LAST_SELECTED_PROJECT_DIRECTORY);
		fileChooser.dispose();
		if (projectLocator == null) {
			return; // user canceled
		}
		ProjectManager pm = plugin.getProjectManager();
		if (!pm.projectExists(projectLocator)) {
			Msg.showInfo(getClass(), tool.getToolFrame(), "项目不存在",
				"项目 " + projectLocator.getName() + " 未找到。");
			return;
		}
		// confirm delete before continuing
		Project activeProject = plugin.getActiveProject();

		// give a special confirm message if user is about to
		// remove the active project
		StringBuffer confirmMsg = new StringBuffer("项目：");
		confirmMsg.append(projectLocator.toString());
		confirmMsg.append(" ?\n");
		boolean isActiveProject =
			(activeProject != null && activeProject.getProjectLocator().equals(projectLocator));
		// also give special warning if we open this project as read-only view
		boolean isOpenProjectView = isOpenProjectView(projectLocator);

		if (!allowDelete(isActiveProject ? activeProject : null)) {
			return;
		}

		confirmMsg.append(" \n");
		confirmMsg.append("警告：删除不可撤销！");

		if (!plugin.confirmDelete(confirmMsg.toString())) {
			return;
		}

		String projectName = projectLocator.getName();
		try {
			if (!pm.deleteProject(projectLocator)) {
				Msg.showInfo(getClass(), tool.getToolFrame(), "删除项目出错",
					"所有项目文件 " + projectName + " 将不被删除。");
			}
		}
		catch (Exception e) {
			Msg.error(this, "删除项目出错：" + projectName + ", " + e.getMessage(), e);
			return;
		}

		if (isActiveProject) {
			activeProject.close();
			fireProjectClosed(activeProject);
			tool.setActiveProject(null);
		}
		else if (isOpenProjectView) {
			// update the read-only project views if affected
			plugin.getProjectActionManager().closeView(projectLocator.getURL());
		}

		// update our list of recent projects
		plugin.rebuildRecentMenus();

		Msg.info(this, "已删除项目：" + projectName);
	}

	private boolean isOpenProjectView(ProjectLocator projectLocator) {
		boolean isOpenView = false;
		ProjectLocator[] openViews = plugin.getProjectDataPanel().getProjectViews();
		for (int v = 0; !isOpenView && v < openViews.length; v++) {
			isOpenView = openViews[v].equals(projectLocator);
		}

		return isOpenView;
	}

	final void enableActions(boolean enabled) {
//      renameAction.setEnabled(enabled);
		closeProjectAction.setEnabled(enabled);
		saveAction.setEnabled(enabled);
	}

	/**
	 * Checks the list for read-only files; if any are found, pops up
	 * a dialog for whether to save now or lose changes.
	 * @param objs list of files which correspond to modified 
	 * domain objects.
	 * @return true if there are no read only files OR if the user
	 * wants to lose his changes; false if the user wants to save the
	 * files now, so don't continue.
	 */
	private boolean checkReadOnlyFiles(DomainObject[] objs) {
		ArrayList<DomainObject> list = new ArrayList<>(10);
		for (DomainObject domainObject : objs) {
			try {
				if (domainObject.isChanged() && !domainObject.getDomainFile().canSave()) {
					list.add(domainObject);
				}
			}
			catch (Exception e) {
				Msg.showError(this, null, null, null, e);
			}
		}
		if (list.size() == 0) {
			return true;
		}

		StringBuffer sb = new StringBuffer();
	    sb.append("以下文件为只读，无法直接保存“原样”。\n" + "您必须为这些文件手动执行“另存为”操作：\n \n");

		for (DomainObject obj : list) {
			sb.append(obj.getDomainFile().getPathname());
			sb.append("\n");
		}
		// note: put the extra space in or else OptionDialog will not show
		// the new line char
		sb.append("\n选择“取消”以取消关闭项目，或选择“放弃更改”以继续。");

		if (OptionDialog.showOptionDialog(tool.getToolFrame(), "只读文件", sb.toString(),
			"丢弃更改", OptionDialog.QUESTION_MESSAGE) == OptionDialog.OPTION_ONE) {
			return true; // Lose changes, so close the project
		}
		return false;
	}

	/**
	 * Fire the project opened event
	 * @param project project being opened
	 */
	private void fireProjectOpened(Project project) {
		for (ProjectListener listener : tool.getListeners()) {
			listener.projectOpened(project);
		}
	}

	/**
	 * Fire the project closed event.
	 * @param project project being closed
	 */
	private void fireProjectClosed(Project project) {
		for (ProjectListener listener : tool.getListeners()) {
			listener.projectClosed(project);
		}
	}

	/**
	 * Action for a recently opened project.
	 *
	 */
	private class ReopenProjectAction extends DockingAction {
		private ProjectLocator projectLocator;

		private ReopenProjectAction(ProjectLocator projectLocator, String filename) {
			super(filename, plugin.getName(), false);
			this.projectLocator = projectLocator;
// ACTIONS - auto generated
			setMenuBarData(new MenuData(
				new String[] { ToolConstants.MENU_FILE, "重新打开", filename }, null, "AProject"));

			tool.setMenuGroup(new String[] { ToolConstants.MENU_FILE, "重新打开" }, "AProject");
			setEnabled(true);
			setHelpLocation(new HelpLocation(plugin.getName(), "Reopen_Project"));
		}

		/* (non Javadoc)
		 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
		 */
		@Override
		public void actionPerformed(ActionContext context) {
			doOpenProject(projectLocator);
		}

	}
}
