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
package ghidra.app.plugin.runtimeinfo;

import java.awt.*;
import java.util.*;
import java.util.stream.Collectors;

import javax.swing.*;

import docking.ReusableDialogComponentProvider;
import generic.jar.ResourceFile;
import ghidra.GhidraClassLoader;
import ghidra.framework.Application;
import ghidra.util.Disposable;
import ghidra.util.classfinder.*;

/**
 * A dialog that shows useful runtime information
 */
class RuntimeInfoProvider extends ReusableDialogComponentProvider {

	private RuntimeInfoPlugin plugin;
	private JTabbedPane tabbedPane;
	private MemoryUsagePanel memoryUsagePanel;

	/**
	 * Creates a new {@link RuntimeInfoProvider}
	 * 
	 * @param plugin The associated {@link RuntimeInfoPlugin}
	 */
	RuntimeInfoProvider(RuntimeInfoPlugin plugin) {
		super("运行信息", false, false, true, false);
		this.plugin = plugin;

		setHelpLocation(plugin.getRuntimeInfoHelpLocation());
		addWorkPanel(createWorkPanel());
	}

	@Override
	public void dispose() {
		super.dispose();
		for (Component c : tabbedPane.getComponents()) {
			if (c instanceof Disposable d) {
				d.dispose();
			}
		}
	}

	@Override
	protected void dialogShown() {
		memoryUsagePanel.shown();
	}

	@Override
	protected void dialogClosed() {
		memoryUsagePanel.hidden();
	}

	private JComponent createWorkPanel() {
		tabbedPane = new JTabbedPane();

		addVersionInfoPanel();
		addMemory();
		addApplicationLayout();
		addProperties();
		addEnvironment();
		addModules();
		addExtensionPoints();
		addClasspath();
		addExtensionsClasspath();

		JPanel mainPanel = new JPanel(new BorderLayout()) {
			@Override
			public Dimension getPreferredSize() {
				return new Dimension(700, 400);
			}
		};
		mainPanel.add(tabbedPane, BorderLayout.CENTER);
		mainPanel.getAccessibleContext().setAccessibleName("运行信息提供者");
		return mainPanel;
	}

	/**
	 * Adds a "version" panel to the tabbed pane.
	 * <p>
	 * The goal of this panel is to display version information that would be useful to include in 
	 * a bug report, and provide a button that copies this information to the system clipboard.
	 */
	private void addVersionInfoPanel() {
		tabbedPane.add(new VersionInfoPanel(), "版本");
	}

	/**
	 * Adds a "memory" panel to the tabbed pane.
	 * <p>
	 * The goal of this panel is to display live memory usage, and provide a button to initiate 
	 * garbage collection on-demand.
	 */
	private void addMemory() {
		memoryUsagePanel = new MemoryUsagePanel();
		memoryUsagePanel.getAccessibleContext().setAccessibleName("内存用量");
		tabbedPane.add(memoryUsagePanel, "内存");
	}

	/**
	 * Adds an "application layout" panel to the tabbed pane.
	 * <p>
	 * The goal of this panel is to display information about the application such as
	 * what directories it is using on disk, what its PID is, etc.
	 */
	private void addApplicationLayout() {
		Map<String, String> map = new HashMap<>();
		map.put("PID", ProcessHandle.current().pid() + "");
		map.put("安装文件夹", Application.getInstallationDirectory().getAbsolutePath());
		map.put("设置文件夹", Application.getUserSettingsDirectory().getPath());
		map.put("缓存文件夹", Application.getUserCacheDirectory().getPath());
		map.put("临时文件夹", Application.getUserTempDirectory().getPath());
		String name = "程序布局";
		tabbedPane.add(
			new MapTablePanel<String, String>(name, map, "名称", "路径", 200, true, plugin), name);
	}

	/**
	 * Adds a "properties" panel to the tabbed pane.
	 * <p>
	 * The goal of this panel is to display every defined system property in a table.
	 */
	private void addProperties() {
		Properties properties = System.getProperties();
		Map<String, String> map = new HashMap<>();
		for (Object key : properties.keySet()) {
			map.put(key.toString(), properties.getOrDefault(key, "").toString());
		}
		String name = "属性";
		tabbedPane.add(
			new MapTablePanel<String, String>(name, map, "名称", "值", 400, true, plugin), name);
	}

	/**
	 * Adds an "environment" panel to the tabbed pane.
	 * <p>
	 * The goal of this panel is to display every defined environment variable in a table.
	 */
	private void addEnvironment() {
		Map<String, String> map = System.getenv();
		String name = "环境";
		tabbedPane.add(
			new MapTablePanel<String, String>(name, map, "名称", "值", 400, true, plugin), name);
	}

	/**
	 * Adds a "modules" panel to the tabbed pane.
	 * <p>
	 * The goal of this panel is to display every module that Ghidra discovered and loaded.
	 */
	private void addModules() {
		Map<String, ResourceFile> map = Application.getApplicationLayout()
				.getModules()
				.entrySet()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getModuleRoot()));
		String name = "模块";
		tabbedPane.add(
			new MapTablePanel<String, ResourceFile>(name, map, "名称", "路径", 400, true, plugin),
			name);
	}

	/**
	 * Adds an "extension points" panel to the tabbed pane.
	 * <p>
	 * The goal of this panel is to display every {@link ExtensionPoint} that Ghidra discovered and
	 * loaded.
	 */
	private void addExtensionPoints() {
		JTabbedPane epTabbedPane = new JTabbedPane();
		tabbedPane.add("扩展点", epTabbedPane);

		// Discovered Potential Extension Points
		Map<String, String> map = ClassSearcher.getExtensionPointInfo()
				.stream()
				.collect(Collectors.toMap(ClassFileInfo::name, ClassFileInfo::path));
		String name = "扩展点信息 (%d)".formatted(map.size());
		epTabbedPane.add(
			new MapTablePanel<String, String>(name, map, "名称", "路径", 400, true, plugin), name);

		// Loaded Extension Points
		map = ClassSearcher.getLoaded()
				.stream()
				.collect(Collectors.toMap(ClassFileInfo::name, ClassFileInfo::suffix));
		name = "已加载 (%d)".formatted(map.size());
		epTabbedPane.add(
			new MapTablePanel<String, String>(name, map, "名称", "类型", 400, true, plugin), name);

		// False Positive Extension Points
		map = ClassSearcher.getFalsePositives()
				.stream()
				.collect(Collectors.toMap(ClassFileInfo::name, ClassFileInfo::suffix));
		name = "False Positives (%d)".formatted(map.size());
		epTabbedPane.add(
			new MapTablePanel<String, String>(name, map, "名称", "类型", 400, true, plugin), name);
	}

	/**
	 * Adds a "classpath" panel to the tabbed pane.
	 * <p>
	 * The goal of this panel is to display Ghidra's current classpath.
	 */
	private void addClasspath() {
		Map<Integer, String> map = getClasspathMap(GhidraClassLoader.CP);
		String name = "类别路径";
		tabbedPane.add(
			new MapTablePanel<Integer, String>(name, map, "序号", "路径", 40, true, plugin), name);
	}

	/**
	 * Adds an "extensions classpath" panel to the tabbed pane.
	 * <p>
	 * The goal of this panel is to display Ghidra's current extension classpath.
	 */
	private void addExtensionsClasspath() {
		Map<Integer, String> map = getClasspathMap(GhidraClassLoader.CP_EXT);
		String name = "扩展类别路径";
		tabbedPane.add(
			new MapTablePanel<Integer, String>(name, map, "序号", "路径", 40, true, plugin), name);
	}

	/**
	 * Returns a {@link Map} of classpath entries, where the key is a 0-based integer index of each
	 * classpath entry 
	 * 
	 * @param propertyName The classpath system property name
	 * @return A {@link Map} of classpath entries, where the key is a 0-based integer index of each
	 * classpath entry 
	 */
	private Map<Integer, String> getClasspathMap(String propertyName) {
		Map<Integer, String> map = new HashMap<>();
		int i = 0;
		for (String entry : GhidraClassLoader.getClasspath(propertyName)) {
			map.put(i++, entry);
		}
		return map;
	}
}
