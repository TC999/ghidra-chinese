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
package ghidra.framework.project.extensions;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import docking.widgets.OkDialog;
import docking.widgets.OptionDialog;
import generic.jar.ResourceFile;
import ghidra.framework.Application;
import ghidra.util.Msg;
import ghidra.util.extensions.*;
import ghidra.util.task.TaskLauncher;
import utility.application.ApplicationLayout;

/**
 * Utility class for managing Ghidra Extensions.
 * <p>
 * Extensions are defined as any archive or folder that contains an <code>extension.properties</code>
 * file. This properties file can contain the following attributes:
 * <ul>
 * <li>name (required)</li>
 * <li>description</li>
 * <li>author</li>
 * <li>createdOn (format: MM/dd/yyyy)</li>
 * <li>version</li>
 * </ul>
 *
 * <p>
 * Extensions may be installed/uninstalled by users at runtime, using the
 * {@link ExtensionTableProvider}. Installation consists of unzipping the extension archive to an
 * installation folder, currently <code>{ghidra user settings dir}/Extensions</code>. To uninstall,
 * the unpacked folder is simply removed.
 */
public class ExtensionInstaller {

	private static final Logger log = LogManager.getLogger(ExtensionInstaller.class);

	/**
	 * Installs the given extension file. This can be either an archive (zip) or a directory that
	 * contains an extension.properties file.
	 *
	 * @param file the extension to install
	 * @return true if the extension was successfully installed
	 */
	public static boolean install(File file) {

		log.trace("安装扩展文件 " + file);

		if (file == null) {
			log.error("安装文件不能为空");
			return false;
		}

		ExtensionDetails extension = ExtensionUtils.getExtension(file, false);
		if (extension == null) {
			Msg.showError(ExtensionInstaller.class, null, "安装扩展错误",
				file.getAbsolutePath() + " 不是一个有效的 Ghidra 扩展");
			return false;
		}

		Extensions extensions = ExtensionUtils.getAllInstalledExtensions();
		if (checkForConflictWithDevelopmentExtension(extension, extensions)) {
			return false;
		}

		if (checkForDuplicateExtensions(extension, extensions)) {
			return false;
		}

		// Verify that the version of the extension is valid for this version of Ghidra. If not,
		// just exit without installing.
		if (!validateExtensionVersion(extension)) {
			return false;
		}

		AtomicBoolean installed = new AtomicBoolean(false);
		TaskLauncher.launchModal("安装扩展中", (monitor) -> {
			installed.set(ExtensionUtils.install(extension, file, monitor));
		});

		boolean success = installed.get();
		if (success) {
			log.trace("成功安装扩展 " + file);
		}
		else {
			log.trace("安装扩展失败 " + file);
		}

		return success;
	}

	/**
	 * Installs the given extension from its declared archive path
	 * @param extension the extension
	 * @return true if successful
	 */
	public static boolean installExtensionFromArchive(ExtensionDetails extension) {
		if (extension == null) {
			log.error("要安装的扩展不能为空");
			return false;
		}

		String archivePath = extension.getArchivePath();
		if (archivePath == null) {
			log.error("无法从归档安装；扩展缺少归档路径");
			return false;
		}

		ApplicationLayout layout = Application.getApplicationLayout();
		ResourceFile extInstallDir = layout.getExtensionInstallationDirs().get(0);
		String extName = extension.getName();
		File extDestinationDir = new ResourceFile(extInstallDir, extName).getFile(false);
		File archiveFile = new File(archivePath);
		if (install(archiveFile)) {
			extension.setInstallDir(new File(extDestinationDir, extName));
			return true;
		}

		return false;
	}

	/**
	 * Compares the given extension version to the current Ghidra version.  If they are different,
	 * then the user will be prompted to confirm the installation.   This method will return true
	 * if the versions match or the user has chosen to install anyway.
	 *
	 * @param extension the extension
	 * @return true if the versions match or the user has chosen to install anyway
	 */
	private static boolean validateExtensionVersion(ExtensionDetails extension) {
		String extVersion = extension.getVersion();
		if (extVersion == null) {
			extVersion = "<no version>";
		}

		String appVersion = Application.getApplicationVersion();
		if (extVersion.equals(appVersion)) {
			return true;
		}

		String message = "扩展版本不匹配。\n名称: " + extension.getName() +
			"扩展版本: " + extVersion + ".\nGhidra 版本: " + appVersion + ".";
		int choice = OptionDialog.showOptionDialogWithCancelAsDefaultButton(null,
			"扩展版本不匹配", message, "安装");
		if (choice != OptionDialog.OPTION_ONE) {
			log.info(removeNewlines(message + " 不安装"));
			return false;
		}
		return true;
	}

	private static String removeNewlines(String s) {
		return s.replaceAll("\n", " ");
	}

	private static boolean checkForDuplicateExtensions(ExtensionDetails newExtension,
			Extensions extensions) {

		String name = newExtension.getName();
		log.trace("检查名称为 '" + name + "' 的重复扩展");

		List<ExtensionDetails> matches = extensions.getMatchingExtensions(newExtension);
		if (matches.isEmpty()) {
			log.trace("没有安装名称为 '" + name + "' 的匹配扩展");
			return false;
		}

		log.trace("找到名称为 '" + name + "' 的重复扩展");

		if (matches.size() > 1) {
			reportMultipleDuplicateExtensionsWhenInstalling(newExtension, matches);
			return true;
		}

		ExtensionDetails installedExtension = matches.get(0);
		String message =
			"尝试安装一个与已安装扩展名称匹配的扩展。\n" +
				"新扩展版本: " + newExtension.getVersion() + ".\n" +
				"已安装扩展版本: " + installedExtension.getVersion() + ".\n\n" +
				"要安装，请点击 '移除现有'，重新启动 Ghidra，然后再次安装。";
		int choice = OptionDialog.showOptionDialogWithCancelAsDefaultButton(null,
			"重复扩展", message, "移除现有");

		String installPath = installedExtension.getInstallPath();
		if (choice != OptionDialog.OPTION_ONE) {
			log.info(removeNewlines(message +
				" 跳过安装。原始扩展仍安装在: " + installPath));
			return true;
		}

		//
		// At this point the user would like to replace the existing extension.  We cannot delete
		// the existing extension, as it may be in use; mark it for removal.
		//
		log.info(removeNewlines(
			message + " 安装新扩展。安装后，原始扩展将在重启后被移除: " + installPath));
		installedExtension.markForUninstall();
		return true;
	}

	private static void reportMultipleDuplicateExtensionsWhenInstalling(ExtensionDetails extension,
			List<ExtensionDetails> matches) {

		StringBuilder buffy = new StringBuilder();
		buffy.append("尝试安装扩展时发现多个重复扩展 '")
				.append(extension.getName())
				.append("'\n");
		for (ExtensionDetails otherExtension : matches) {
			buffy.append("重复扩展: " + otherExtension.getInstallPath()).append('\n');
		}
		buffy.append("请关闭 Ghidra 并手动从文件系统中移除这些扩展。");

		Msg.showInfo(ExtensionInstaller.class, null, "发现重复扩展",
			buffy.toString());
	}

	private static boolean checkForConflictWithDevelopmentExtension(ExtensionDetails newExtension,
			Extensions extensions) {

		String name = newExtension.getName();
		log.trace("检查名称为 '" + name + "' 的重复开发模式扩展");

		List<ExtensionDetails> matches = extensions.getMatchingExtensions(newExtension);
		if (matches.isEmpty()) {
			log.trace("没有安装名称为 '" + name + "' 的匹配扩展");
			return false;
		}

		for (ExtensionDetails extension : matches) {

			if (extension.isInstalledInInstallationFolder()) {

				String message = "尝试安装一个与已安装扩展名称匹配的扩展，该扩展位于 Ghidra 安装文件夹中。\n" +
					"您必须手动移除现有扩展才能安装新扩展。\n" +
					"现有扩展: " + extension.getInstallDir();

				log.trace(removeNewlines(message));

				OkDialog.showError("发现重复扩展", message);
				return true;
			}
		}

		return false;
	}
}
