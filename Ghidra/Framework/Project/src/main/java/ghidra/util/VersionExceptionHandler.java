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
package ghidra.util;

import java.awt.Component;

import docking.widgets.OptionDialog;
import ghidra.framework.model.DomainFile;
import ghidra.util.exception.VersionException;

public class VersionExceptionHandler {

	public static boolean isUpgradeOK(Component parent, DomainFile domainFile, String actionName,
			VersionException ve) {
		String contentType = domainFile.getContentType();
		if (domainFile.isReadOnly() || ve.getVersionIndicator() != VersionException.OLDER_VERSION ||
			!ve.isUpgradable()) {
			showVersionError(parent, domainFile.getName(), contentType, actionName, ve);
			return false;
		}
		String filename = domainFile.getName();

		// make sure the user wants to upgrade

		if (domainFile.isVersioned() && !domainFile.isCheckedOutExclusive()) {
			showNeedExclusiveCheckoutDialog(parent, filename, contentType, actionName);
			return false;
		}

		int userChoice = showUpgradeDialog(parent, ve, filename, contentType, actionName);
		if (userChoice != OptionDialog.OPTION_ONE) {
			return false;
		}
		if (domainFile.isCheckedOut()) {
			userChoice = showWarningDialog(parent, filename, contentType, actionName);
			if (userChoice != OptionDialog.OPTION_ONE) {
				return false;
			}
		}

		return true;
	}

	private static void showNeedExclusiveCheckoutDialog(final Component parent, String filename,
			String contentType, String actionName) {

		Msg.showError(VersionExceptionHandler.class, parent, actionName + " 失败！",
			"无法 " + actionName + " " + contentType + ": " + filename + "\n \n" +
				"An upgrade of the " + contentType +
				" data is required, however, you must have an exclusive checkout\n" +
				"to upgrade a shared file!\n \n" +
				"NOTE: If you are unable to obtain an exclusive checkout, you may be able to " +
				actionName + "\nthe file with an older version of Ghidra.");
	}

	private static int showUpgradeDialog(final Component parent, VersionException ve,
			final String filename, final String contentType, final String actionName) {
		final String detailMessage =
			ve.getDetailMessage() == null ? "" : "\n" + ve.getDetailMessage();

		String title = "升级 " + contentType + " 数据？" + filename;
		String message = "此 " + contentType + " 文件曾 " + actionName +
			" 于旧版本。" + detailMessage + "\n \n" + "您要升级它吗？";
		return OptionDialog.showOptionDialog(parent, title, message, "升级",
			OptionDialog.QUESTION_MESSAGE);
	}

	private static int showWarningDialog(final Component parent, String filename,
			String contentType, String actionName) {

		String title = "升级共享 " + contentType + " 数据？" + filename;
		String message = "此 " + contentType +
			" 同其他用户共享。如果您升级此文件，\n" +
			"其他用户在未升级到兼容版本的Ghidra之前将无法读取新版本。您确定要继续吗？";
		return OptionDialog.showOptionDialog(parent, title, message, "升级",
			OptionDialog.WARNING_MESSAGE);
	}

	public static void showVersionError(final Component parent, final String filename,
			final String contentType, final String actionName, VersionException ve) {

		int versionIndicator = ve.getVersionIndicator();
		final String versionType;
		if (versionIndicator == VersionException.NEWER_VERSION) {
			versionType = " newer";
		}
		else if (versionIndicator == VersionException.OLDER_VERSION) {
			versionType = "n older";
		}
		else {
			versionType = "n unknown";
		}

		String upgradeMsg = "";
		if (ve.isUpgradable()) {
			upgradeMsg = " (data upgrade is possible)";
		}

		Msg.showError(VersionExceptionHandler.class, parent, actionName + " 失败！",
			"Unable to " + actionName + " " + contentType + ": " + filename + "\n \n" +
				"File was created with a" + versionType + " version of Ghidra" + upgradeMsg);
	}
}
