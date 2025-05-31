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

import java.awt.BorderLayout;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.util.*;

import javax.swing.*;

import docking.dnd.GClipboard;
import ghidra.framework.*;
import ghidra.util.SystemUtilities;

/**
 * A {@link JPanel} that displays version information that would be useful to include in a bug 
 * report, and provide a button that copies this information to the system clipboard
 */
class VersionInfoPanel extends JPanel {

	/**
	 * Creates a new {@link VersionInfoPanel}
	 */
	VersionInfoPanel() {
		setLayout(new BorderLayout());

		JTextArea textArea = new JTextArea(gatherVersionInfo());
		add(textArea, BorderLayout.CENTER);

		JPanel bottomPanel = new JPanel();
		JButton copyButton = new JButton("复制");
		copyButton.addActionListener(e -> {
			Clipboard clipboard = GClipboard.getSystemClipboard();
			clipboard.setContents(new StringSelection(textArea.getText()), null);
		});
		bottomPanel.add(copyButton);
		add(bottomPanel, BorderLayout.SOUTH);

	}

	/**
	 * Gathers version information
	 * 
	 * @return The version information text
	 */
	private String gatherVersionInfo() {
		final String def = "???";
		List<String> lines = new ArrayList<>();

		addApplicationInfo(lines, def);
		addOperatingSystemInfo(lines, def);
		addJavaInfo(lines, def);

		return String.join("\n", lines);
	}

	/**
	 * Adds Ghidra application information to the version information
	 * 
	 * @param lines A {@link List} of lines to add to
	 * @param def A default value to use if a piece of information cannot be found
	 */
	private void addApplicationInfo(List<String> lines, String def) {
		ApplicationProperties props = Application.getApplicationLayout().getApplicationProperties();
		lines.add("Ghidra 版本：" + props.getApplicationVersion());
		lines.add("Ghidra 发行：" + props.getApplicationReleaseName());
		lines.add("Ghidra 编译日期：" + props.getApplicationBuildDate());
		lines.add("Ghidra 修订：" +
			props.getProperty(ApplicationProperties.REVISION_PROPERTY_PREFIX + "ghidra", def));
		lines.add("Ghidra 开发者模式：" + SystemUtilities.isInDevelopmentMode());
	}

	/**
	 * Adds operating system information to the version information
	 * 
	 * @param lines A {@link List} of lines to add to
	 * @param def A default value to use if a piece of information cannot be found
	 */
	private void addOperatingSystemInfo(List<String> lines, String def) {
		lines.add("系统名称：" + System.getProperty("os.name", def));
		lines.add("系统架构：" + System.getProperty("os.arch", def));
		lines.add("系统版本：" + System.getProperty("os.version", def));
		if (OperatingSystem.CURRENT_OPERATING_SYSTEM.equals(OperatingSystem.LINUX)) {
			String prettyName = def;
			File osReleaseFile = new File("/etc/os-release");
			if (!osReleaseFile.isFile()) {
				osReleaseFile = new File("/usr/lib/os-release");
			}
			try (BufferedReader reader = new BufferedReader(new FileReader(osReleaseFile))) {
				Properties props = new Properties();
				props.load(reader);
				prettyName = props.getProperty("PRETTY_NAME", def);
				if (prettyName.startsWith("\"") && prettyName.endsWith("\"")) {
					prettyName = prettyName.substring(1, prettyName.length() - 1);
				}
			}
			catch (IOException e) {
				// That's ok, pretty name is optional
			}
			lines.add("操作系统美名：" + prettyName);
		}
	}

	/**
	 * Adds Java/JVM information to the version information
	 * 
	 * @param lines A {@link List} of lines to add to
	 * @param def A default value to use if a piece of information cannot be found
	 */
	private void addJavaInfo(List<String> lines, String def) {
		lines.add("Java 供应商：" + System.getProperty("java.vendor", def));
		lines.add("Java 版本: " + System.getProperty("java.version", def));
		lines.add("Java 路径: " + System.getProperty("java.home"));
	}
}
