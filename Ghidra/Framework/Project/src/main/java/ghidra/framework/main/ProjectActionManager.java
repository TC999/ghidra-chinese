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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import docking.ActionContext;
import docking.ComponentProvider;
import docking.action.DockingAction;
import docking.action.MenuData;
import docking.tool.ToolConstants;
import docking.widgets.OptionDialog;
import docking.widgets.PasswordChangeDialog;
import docking.widgets.filechooser.GhidraFileChooser;
import ghidra.framework.client.ClientUtil;
import ghidra.framework.client.RepositoryAdapter;
import ghidra.framework.model.*;
import ghidra.framework.preferences.Preferences;
import ghidra.framework.protocol.ghidra.GhidraURL;
import ghidra.framework.remote.User;
import ghidra.util.*;

class ProjectActionManager {
	private final static String CLOSE_ALL_OPEN_VIEWS = "Close All Read-Only Views";
	private final static String LAST_VIEWED_PROJECT_DIRECTORY = "LastViewedProjectDirectory";
	private final static String LAST_VIEWED_REPOSITORY_URL = "LastViewedRepositoryURL";

	private FrontEndTool tool;
	private FrontEndPlugin plugin;
	private List<ViewInfo> openViewsList;
	private List<ViewInfo> reopenViewsList;
	private Project activeProject;
	private RepositoryChooser repositoryChooser;

	private DockingAction openProjectViewAction;
	private DockingAction openRepositoryViewAction;
	private DockingAction addWSAction;
	private DockingAction removeWSAction;
	private DockingAction renameWSAction;
	private DockingAction switchWSAction;

	private DockingAction editAccessAction;
	private DockingAction viewAccessAction;
	private DockingAction setPasswordAction;
	private DockingAction viewInfoAction;
	private ProjectInfoDialog infoDialog;

	ProjectActionManager(FrontEndPlugin plugin) {
		this.plugin = plugin;
		tool = plugin.getFrontEndTool();

		openViewsList = new ArrayList<>();
		reopenViewsList = new ArrayList<>();

		createActions();
		createSwitchWorkspaceAction();
	}

	void dispose() {
		if (infoDialog != null) {
			infoDialog.dispose();
		}
		if (repositoryChooser != null) {
			repositoryChooser.dispose();
		}
	}

	private void openRecentView(String urlPath) {
		URL url = GhidraURL.toURL(urlPath);
		openView(url);
	}

	private void createActions() {

		String owner = plugin.getName();

		// create the listeners for the menuitems
		openProjectViewAction = new DockingAction("查看项目", owner) {
			@Override
			public void actionPerformed(ActionContext context) {
				openProjectView();
			}
		};
		openProjectViewAction.setEnabled(false);

		openProjectViewAction.setMenuBarData(
			new MenuData(new String[] { ToolConstants.MENU_PROJECT, "查看项目..." }, "AView"));
		openProjectViewAction.getMenuBarData().setMenuSubGroup("1");
		tool.addAction(openProjectViewAction);

		openRepositoryViewAction = new DockingAction("查看仓库", owner) {
			@Override
			public void actionPerformed(ActionContext context) {
				openRepositoryView();
			}
		};
		openRepositoryViewAction.setEnabled(false);

		openRepositoryViewAction.setMenuBarData(new MenuData(
			new String[] { ToolConstants.MENU_PROJECT, "查看仓库..." }, "AView"));
		openRepositoryViewAction.getMenuBarData().setMenuSubGroup("2");
		tool.addAction(openRepositoryViewAction);

		addWSAction = new DockingAction("添加工作区", owner) {
			@Override
			public void actionPerformed(ActionContext context) {
				plugin.getWorkspacePanel().addWorkspace();
			}
		};
		addWSAction.setEnabled(false);

		addWSAction.setMenuBarData(new MenuData(
			new String[] { ToolConstants.MENU_PROJECT, "Workspace", "添加..." }, "zProject"));
		tool.addAction(addWSAction);

		renameWSAction = new DockingAction("重命名工作区", owner) {
			@Override
			public void actionPerformed(ActionContext context) {
				plugin.getWorkspacePanel().renameWorkspace();
			}
		};
		renameWSAction.setEnabled(false);

		renameWSAction.setMenuBarData(new MenuData(
			new String[] { ToolConstants.MENU_PROJECT, "Workspace", "重命名..." }, "zProject"));
		tool.addAction(renameWSAction);

		removeWSAction = new DockingAction("删除工作区", owner) {
			@Override
			public void actionPerformed(ActionContext context) {
				plugin.getWorkspacePanel().removeWorkspace();
			}
		};
		removeWSAction.setEnabled(false);

		removeWSAction.setMenuBarData(new MenuData(
			new String[] { ToolConstants.MENU_PROJECT, "Workspace", "删除..." }, "zProject"));
		tool.addAction(removeWSAction);

		tool.setMenuGroup(new String[] { ToolConstants.MENU_PROJECT, "Workspace" }, "zProject");

		editAccessAction = new DockingAction("编辑项目访问列表", owner) {
			@Override
			public void actionPerformed(ActionContext context) {
				editProjectAccess();
			}
		};

		editAccessAction.setMenuBarData(
			new MenuData(new String[] { "Project", "编辑项目访问列表..." }, "zzProject"));

		viewAccessAction = new DockingAction("编辑项目访问列表", owner) {
			@Override
			public void actionPerformed(ActionContext context) {
				viewProjectAccess();
			}
		};

		viewAccessAction.setMenuBarData(
			new MenuData(new String[] { "Project", "编辑项目访问列表..." }, "zzProject"));

		setPasswordAction = new DockingAction("更改密码", owner) {
			@Override
			public void actionPerformed(ActionContext context) {
				changePassword();
			}
		};

		setPasswordAction.setMenuBarData(
			new MenuData(new String[] { "Project", "更改密码..." }, "zzProject"));

		viewInfoAction = new DockingAction("查看项目信息", owner) {
			@Override
			public void actionPerformed(ActionContext context) {
				showProjectInfo();
			}
		};
		viewInfoAction.setEnabled(false);

		viewInfoAction.setMenuBarData(
			new MenuData(new String[] { "Project", "查看项目信息..." }, "zzzProject"));
		tool.addAction(viewInfoAction);
	}

	private void createSwitchWorkspaceAction() {
		String owner = plugin.getName();

		switchWSAction = new DockingAction("切换工作区", owner) {
			@Override
			public void actionPerformed(ActionContext context) {
				ToolManager toolManager = activeProject.getToolManager();
				Workspace[] workspaces = toolManager.getWorkspaces();
				if (workspaces.length <= 1) {
					Msg.info("FrontEnd", "Unable to switch workspace, only 1 exists.");
					return;//can't switch, there is only 1
				}
				Workspace activeWorkspace = plugin.getWorkspacePanel().getActiveWorkspace();
				int index = 0;
				for (Workspace workspace : workspaces) {
					++index;
					if (workspace.equals(activeWorkspace)) {
						break;
					}
				}
				if (index >= workspaces.length) {
					index = 0;//at the end, so loop back around
				}
				plugin.getWorkspacePanel().setActiveWorkspace(workspaces[index]);
			}
		};
		switchWSAction.setEnabled(false);

		switchWSAction.setMenuBarData(new MenuData(
			new String[] { ToolConstants.MENU_PROJECT, "Workspace", "切换..." }, "zProject"));
		tool.addAction(switchWSAction);
	}

	/**
	 * creates the recent projects menu
	 */
	private void buildCloseViewsActions() {
		for (ViewInfo info : openViewsList) {
			tool.removeAction(info.getAction());
		}

		openViewsList.clear();

		ProjectDataPanel pdp = plugin.getProjectDataPanel();
		if (pdp == null) {
			return;
		}

		tool.setMenuGroup(new String[] { ToolConstants.MENU_PROJECT, "关闭查看" }, "AView", "4");

		ProjectLocator[] projectViews = pdp.getProjectViews();
		for (ProjectLocator view : projectViews) {
			DockingAction action =
				new CloseViewPluginAction(GhidraURL.getDisplayString(view.getURL()));
			openViewsList.add(new ViewInfo(action, view.getURL()));
			tool.addAction(action);
		}

		if (projectViews.length > 1) {
			DockingAction action =
				new DockingAction(CLOSE_ALL_OPEN_VIEWS, plugin.getName(), false) {
					@Override
					public void actionPerformed(ActionContext context) {
						closeView(CLOSE_ALL_OPEN_VIEWS);
					}
				};
			action.setMenuBarData(new MenuData(
				new String[] { ToolConstants.MENU_PROJECT, "关闭查看", CLOSE_ALL_OPEN_VIEWS },
				"AView"));
			openViewsList.add(new ViewInfo(action, null));
			tool.addAction(action);
		}
		else if (projectViews.length == 0) {
			DockingAction action = new DockingAction("关闭查看", plugin.getName()) {
				@Override
				public void actionPerformed(ActionContext context) {
					// do nothing - place holder menu item only
				}
			};
			action.setEnabled(false);

			action.setMenuBarData(
				new MenuData(new String[] { ToolConstants.MENU_PROJECT, "关闭查看" }, "AView"));
			action.getMenuBarData().setMenuSubGroup("4");
			openViewsList.add(new ViewInfo(action, null));
			tool.addAction(action);
		}

	}

	/**
	 * creates the recent projects menu
	 */
	void buildRecentViewsActions() {
		for (ViewInfo info : reopenViewsList) {
			tool.removeAction(info.getAction());
		}

		// remove these actions
		reopenViewsList.clear();

		if (activeProject == null) {
			return;
		}

		// don't include the active project in the list of views 
		URL[] recentViews = plugin.getRecentViewedProjects();

		tool.setMenuGroup(new String[] { ToolConstants.MENU_PROJECT, "最近查看" }, "AView", "3");

		// the project manager maintains the order of the projects
		// with the most recent being first in the list
		for (URL projectView : recentViews) {
			String urlPath = GhidraURL.getDisplayString(projectView);
			DockingAction action = new RecentViewPluginAction(urlPath);
			reopenViewsList.add(new ViewInfo(action, projectView));
			tool.addAction(action);
		}

		// disable the menu if no recent project views
		if (reopenViewsList.size() == 0) {
			DockingAction action = new DockingAction("最近查看", plugin.getName(), false) {
				@Override
				public void actionPerformed(ActionContext context) {
					// no-op; disabled action placeholder
				}
			};
			action.setEnabled(false);
			action.setMenuBarData(
				new MenuData(new String[] { ToolConstants.MENU_PROJECT, "最近查看" }, "AView"));
			action.getMenuBarData().setMenuSubGroup("3");
			reopenViewsList.add(new ViewInfo(action, null));
			tool.addAction(action);
		}
	}

	void showProjectInfo() {
		if (infoDialog != null && !infoDialog.isVisible()) {
			infoDialog = null;
		}
		if (infoDialog == null) {
			infoDialog = new ProjectInfoDialog(plugin);
			tool.showDialog(infoDialog, (ComponentProvider) null);
		}
		else {
			infoDialog.toFront();
		}
	}

	void enableActions(boolean enabled) {
		openProjectViewAction.setEnabled(enabled);
		openRepositoryViewAction.setEnabled(enabled);
		addWSAction.setEnabled(enabled);
		removeWSAction.setEnabled(enabled);
		renameWSAction.setEnabled(enabled);
		switchWSAction.setEnabled(enabled);
		viewInfoAction.setEnabled(enabled);
	}

	void setActiveProject(Project activeProject) {
		if (infoDialog != null) {
			infoDialog.close();
			infoDialog = null;
		}

		// Remove all the view/edit access-related actions so we always start
		// with a clean slate. If we don't do this we could eventually end up with
		// both edit and view options available at the same time (open a project with 
		// admin rights, then open one without).
		//
		// Note that overriding the isValidContext method in the actions themselves will
		// have no effect; that only works for context menus.
		tool.removeAction(viewAccessAction);
		tool.removeAction(editAccessAction);
		tool.removeAction(setPasswordAction);

		viewAccessAction.setEnabled(false);
		editAccessAction.setEnabled(false);
		setPasswordAction.setEnabled(false);

		this.activeProject = activeProject;
		plugin.rebuildRecentMenus();
		buildCloseViewsActions();

		enableActions(activeProject != null);

		if (activeProject != null) {
			// update repository related actions since we may initially be connected
			RepositoryAdapter repository = activeProject.getRepository();
			if (repository != null) {
				connectionStateChanged(repository);
			}
		}
	}

	/**
	 * Notification that the connection state has changed;
	 * @param repository shared project repository adapter
	 */
	void connectionStateChanged(RepositoryAdapter repository) {

		// Action removal is done each time to avoid possibility
		// of adding actions twice. Action manipulated here are
		// not intended to appear in menu when not available.

		setPasswordAction.setEnabled(false);
		editAccessAction.setEnabled(false);
		viewAccessAction.setEnabled(false);

		tool.removeAction(setPasswordAction);
		tool.removeAction(editAccessAction);
		tool.removeAction(viewAccessAction);

		if (repository.isConnected()) {
			if (repository.getServer().canSetPassword()) {
				tool.addAction(setPasswordAction);
				setPasswordAction.setEnabled(true);
			}
			if (isUserAdmin(repository)) {
				tool.addAction(editAccessAction);
				editAccessAction.setEnabled(true);
			}
			else if (!isAnonymousUserOrNotConnected(repository)) {
				tool.addAction(viewAccessAction);
				viewAccessAction.setEnabled(true);
			}
		}

		if (infoDialog != null && infoDialog.isVisible()) {
			infoDialog.updateConnectionStatus();
		}
	}

	/**
	 * en/disable operations on views depending on whether
	 * any are opened
	 */
	void setViewsVisible(boolean visible) {
		buildCloseViewsActions();
	}

	private boolean isUserAdmin(RepositoryAdapter rep) {
		try {
			User user = rep.getUser();
			return user.isAdmin();
		}
		catch (IOException e) {
			// ignore
		}
		return false;
	}

	private boolean isAnonymousUserOrNotConnected(RepositoryAdapter rep) {
		try {
			User user = rep.getUser();
			if (User.ANONYMOUS_USERNAME.equals(user.getName())) {
				return true;
			}
			// work around when user authenticates with their SID
			for (User u : rep.getUserList()) {
				if (u.equals(user)) {
					return false;
				}
			}
		}
		catch (IOException e) {
			// ignore
		}
		return true;
	}

	/**
	 * closes all the open views
	 */
	private void closeAllViews() {
		ProjectDataPanel pdp = plugin.getProjectDataPanel();
		ProjectLocator[] openViews = pdp.getProjectViews();
		for (ProjectLocator openView : openViews) {
			URL view = openView.getURL();
			pdp.closeView(view);
		}
		buildCloseViewsActions();
	}

	/**
	 * closes a view for Project | Close View
	 * @throws IllegalArgumentException if urlPath is invalid
	 */
	private void closeView(String urlPath) {
		if (urlPath.equals(CLOSE_ALL_OPEN_VIEWS)) {
			closeAllViews();
			return;
		}

		// close the named view
		URL url = GhidraURL.toURL(urlPath);
		closeView(url);
	}

	void closeView(URL view) {
		ProjectDataPanel pdp = plugin.getProjectDataPanel();
		pdp.closeView(view);

		buildCloseViewsActions();
	}

	/**
	 * Notification that a view was closed; called when the user
	 * right mouse clicks on the project tab and hits the "close" option.
	 */
	void viewClosed() {
		buildCloseViewsActions();
	}

	/**
	 * menu listener for Project | Add View...
	 */
	private void openProjectView() {

		GhidraFileChooser fileChooser = plugin.createFileChooser(LAST_VIEWED_PROJECT_DIRECTORY);
		ProjectLocator projectView =
			plugin.chooseProject(fileChooser, "Select", LAST_VIEWED_PROJECT_DIRECTORY);
		if (projectView != null) {
			openView(projectView.getURL());
		}
		fileChooser.dispose();
	}

	private void openRepositoryView() {
		if (repositoryChooser == null) {
			repositoryChooser = new RepositoryChooser("查看远程仓库");
			repositoryChooser.setHelpLocation(
				new HelpLocation("FrontEndPlugin", "View_Repository"));
		}

		String urlStr = Preferences.getProperty(LAST_VIEWED_REPOSITORY_URL);
		URL lastURL = null;
		if (urlStr != null) {
			try {
				lastURL = new URL(urlStr);
			}
			catch (MalformedURLException e) {
				// ignore
			}
		}

		URL repositoryURL = repositoryChooser.getSelectedRepository(tool, lastURL);

		if (repositoryURL != null) {
			openView(repositoryURL);
			Preferences.setProperty(LAST_VIEWED_REPOSITORY_URL, repositoryURL.toExternalForm());
			Preferences.store();
		}

	}

	private void openView(URL view) {
		// don't allow opening the active project as a read-only view
		if (activeProject != null && activeProject.getProjectLocator().getURL().equals(view)) {
			Msg.showError(getClass(), tool.getToolFrame(), "打开为只读时出错",
				"无法以只读视图打开活动项目！");
			return;
		}

		try {
			activeProject.addProjectView(view, true); // listener will trigger data panel display
		}
		catch (IOException e) {
			ProjectManager projectManager = tool.getProjectManager();
			projectManager.forgetViewedProject(view);
			Msg.showError(getClass(), tool.getToolFrame(), "添加视图出错",
				"查看项目/仓库时出错：" + e.getMessage(), e);
		}
	}

	private void editProjectAccess() {
		RepositoryAdapter repository = activeProject.getRepository();

		try {
			ProjectAccessDialog dialog =
				new ProjectAccessDialog(plugin, repository, repository.getServerUserList(), true);
			tool.showDialog(dialog);
		}
		catch (IOException e) {
			ClientUtil.handleException(repository, e, "编辑项目访问列表",
				tool.getToolFrame());
		}
	}

	private void viewProjectAccess() {
		RepositoryAdapter repository = activeProject.getRepository();

		try {
			ProjectAccessDialog dialog =
				new ProjectAccessDialog(plugin, repository, repository.getServerUserList(), false);
			tool.showDialog(dialog);
		}
		catch (IOException e) {
			ClientUtil.handleException(repository, e, "查看项目访问列表",
				tool.getToolFrame());
		}
	}

	private void changePassword() {
		RepositoryAdapter repository = activeProject.getRepository();
		if (repository == null) {
			return;
		}
		PasswordChangeDialog dlg = null;
		char[] pwd = null;
		try {
			repository.connect();

			ServerInfo info = repository.getServerInfo();

			if (OptionDialog.OPTION_ONE != OptionDialog.showOptionDialog(tool.getToolFrame(),
				"确认修改密码",
				"您即将更改以下仓库服务器的密码：\n" + info +
				    "\n \n此密码用于连接与此服务器关联的项目仓库。\n",
				"继续", OptionDialog.WARNING_MESSAGE)) {
				return;
			}

			dlg = new PasswordChangeDialog("修改密码", "仓库服务器",
				repository.getServerInfo().getServerName(), repository.getServer().getUser());
			tool.showDialog(dlg);
			pwd = dlg.getPassword();
			if (pwd != null) {
				repository.getServer()
						.setPassword(
							HashUtilities.getSaltedHash(HashUtilities.SHA256_ALGORITHM, pwd));
				Msg.showInfo(getClass(), tool.getToolFrame(), "密码已修改",
					"密码修改成功");
			}
		}
		catch (IOException e) {
			ClientUtil.handleException(repository, e, "密码已修改", tool.getToolFrame());
		}
		finally {
			if (pwd != null) {
				Arrays.fill(pwd, ' ');
			}
			if (dlg != null) {
				dlg.dispose();
			}
		}
	}

	/**
	 * Class for recent view actions; subclass to set the help ID. 
	 */
	private class RecentViewPluginAction extends DockingAction {

		private final String urlPath;

		private RecentViewPluginAction(String urlPath) {
			super("View " + urlPath, plugin.getName(), false);
			this.urlPath = urlPath;
			setMenuBarData(new MenuData(
				new String[] { ToolConstants.MENU_PROJECT, "View Recent", urlPath }, "AView"));
			setHelpLocation(new HelpLocation(plugin.getName(), "View_Recent"));
		}

		@Override
		public void actionPerformed(ActionContext context) {
			openRecentView(urlPath);
		}
	}

	private class CloseViewPluginAction extends DockingAction {

		private final String urlPath;

		private CloseViewPluginAction(String urlPath) {
			super("Close View " + urlPath, plugin.getName(), false);
			this.urlPath = urlPath;
			setMenuBarData(new MenuData(
				new String[] { ToolConstants.MENU_PROJECT, "Close View", urlPath }, "AView"));
			setHelpLocation(new HelpLocation(plugin.getName(), "Close_View"));
		}

		@Override
		public void actionPerformed(ActionContext context) {
			closeView(urlPath);
		}
	}
}
