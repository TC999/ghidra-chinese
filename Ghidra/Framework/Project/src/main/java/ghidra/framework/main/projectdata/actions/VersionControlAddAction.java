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
package ghidra.framework.main.projectdata.actions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;

import docking.action.MenuData;
import docking.action.ToolBarData;
import generic.theme.GIcon;
import ghidra.framework.client.ClientUtil;
import ghidra.framework.main.datatable.DomainFileContext;
import ghidra.framework.main.datatree.*;
import ghidra.framework.model.DomainFile;
import ghidra.framework.plugintool.Plugin;
import ghidra.framework.plugintool.PluginTool;
import ghidra.util.Msg;
import ghidra.util.Swing;
import ghidra.util.exception.CancelledException;
import ghidra.util.task.TaskMonitor;

/**
 * Action to add a domain file to version control in the repository.
 */
public class VersionControlAddAction extends VersionControlAction {

	public VersionControlAddAction(Plugin plugin) {
		super("添加至版本控制", plugin.getName(), plugin.getTool());
		Icon icon = new GIcon("icon.version.control.add");
		setToolBarData(new ToolBarData(icon, GROUP));

		setPopupMenuData(new MenuData(new String[] { "添加至版本控制..." }, icon, GROUP));

		setDescription("添加文件至版本控制");

		setEnabled(false);
	}

	@Override
	public void actionPerformed(DomainFileContext context) {
		addToVersionControl(context.getSelectedFiles());
	}

	@Override
	public boolean isEnabledForContext(DomainFileContext context) {
		if (isFileSystemBusy()) {
			return false; // don't block; we should get called again later
		}

		List<DomainFile> domainFiles = context.getSelectedFiles();
		for (DomainFile domainFile : domainFiles) {
			if (domainFile.canAddToRepository()) {
				return true; // Has at least one domain file that can be added to the repository.
			}
		}
		return false;
	}

	private void addToVersionControl(List<DomainFile> domainFiles) {

		if (!checkRepositoryConnected()) {
			return;
		}
		List<DomainFile> unversioned = new ArrayList<>();
		for (DomainFile domainFile : domainFiles) {
			if (domainFile.canAddToRepository()) {
				unversioned.add(domainFile);
			}
		}
		if (unversioned.isEmpty()) {
			return;
		}

		List<DomainFile> list = new ArrayList<>();
		List<DomainFile> changedList = new ArrayList<>();
		for (DomainFile domainFile : unversioned) {
			if (domainFile.isBusy()) {
				Msg.showWarn(getClass(), null, "添加至版本控制失败！",
					"一个或多个选定的文件当前正在被修改！");
				return;
			}
			if (!canCloseDomainFile(domainFile)) {
				tool.setStatusInfo("添加至版本控制取消");
				return;
			}
			list.add(domainFile);
			if (domainFile.isChanged()) {
				changedList.add(domainFile);
			}
		}
		if (changedList.size() > 0) {
			ChangedFilesDialog dialog = new ChangedFilesDialog(tool, changedList);
			dialog.setCancelToolTipText("取消添加至版本控制");
			if (!dialog.showDialog()) { // blocks until the user hits Save or Cancel
				tool.setStatusInfo("添加至版本控制取消");
				return;
			}
			for (int i = 0; i < changedList.size(); i++) {
				DomainFile df = changedList.get(i);
				if (df.isChanged()) {
					list.remove(df);
				}
			}
		}
		if (list.size() > 0) {
			AddToVersionControlTask task = new AddToVersionControlTask(list, tool);
			tool.execute(task);
		}
	}

	/**
	 * Task for adding files to version control. This task displays a dialog for each file 
	 * which allows a comment to be entered for each check-in.
	 */
	private class AddToVersionControlTask extends VersionControlTask {

		AddToVersionControlTask(List<DomainFile> list, PluginTool tool) {
			super("添加至版本控制", tool, list, tool.getToolFrame());
		}

		@Override
		public void run(TaskMonitor monitor) {
			try {
				for (DomainFile df : list) {
					String name = df.getName();
					monitor.setMessage("正添加 " + name + " 至版本控制");

					if (actionID != VersionControlDialog.APPLY_TO_ALL) {
						showDialog(true, df);
					}
					if (actionID == VersionControlDialog.CANCEL) {
						return;
					}

					// Note: this used to be a sleep(200) 
					Swing.allowSwingToProcessEvents();

					df.addToVersionControl(comments, keepCheckedOut, monitor);
				}
			}
			catch (CancelledException e) {
				Msg.info(this, "添加至版本控制被取消");
			}
			catch (IOException e) {
				ClientUtil.handleException(repository, e, "添加至版本控制",
					tool.getToolFrame());
			}
		}
	}

}
