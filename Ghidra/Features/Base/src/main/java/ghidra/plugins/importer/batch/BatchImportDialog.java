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
package ghidra.plugins.importer.batch;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.table.*;

import docking.DialogComponentProvider;
import docking.widgets.ListSelectionTableDialog;
import docking.widgets.checkbox.GCheckBox;
import docking.widgets.combobox.GComboBox;
import docking.widgets.filechooser.GhidraFileChooser;
import docking.widgets.filechooser.GhidraFileChooserMode;
import docking.widgets.label.GDLabel;
import docking.widgets.table.*;
import generic.theme.GThemeDefaults.Colors.Messages;
import ghidra.app.services.ProgramManager;
import ghidra.formats.gfilesystem.FSRL;
import ghidra.formats.gfilesystem.FileSystemService;
import ghidra.framework.main.AppInfo;
import ghidra.framework.model.DomainFolder;
import ghidra.framework.plugintool.PluginTool;
import ghidra.framework.preferences.Preferences;
import ghidra.plugin.importer.ImporterUtilities;
import ghidra.plugins.importer.batch.BatchGroup.BatchLoadConfig;
import ghidra.plugins.importer.tasks.ImportBatchTask;
import ghidra.util.*;
import ghidra.util.filechooser.GhidraFileFilter;
import ghidra.util.task.TaskLauncher;

public class BatchImportDialog extends DialogComponentProvider {

	private static final String PREF_STRIPCONTAINER = "BATCHIMPORT.STRIPCONTAINER";
	private static final String PREF_STRIPLEADING = "BATCHIMPORT.STRIPLEADING";
	private static final String LAST_IMPORT_DIR = "LastBatchImportDir";

	/**
	 * Shows the batch import dialog (via runSwingLater) and prompts the user to select
	 * a file if the supplied {@code batchInfo} is empty.
	 * <p>
	 * The dialog will chain to the {@link ImportBatchTask} when the user clicks the
	 * OK button.
	 * 
	 * @param tool {@link PluginTool} that will be the parent of the dialog
	 * @param batchInfo optional {@link BatchInfo} instance with already discovered applications, or null.
	 * @param initialFiles optional {@link List} of {@link FSRL files} to add to the batch import dialog, or null.
	 * @param defaultFolder optional default destination folder for imported files or null for root folder.
	 * @param programManager optional {@link ProgramManager} that will be used to open the newly imported
	 * binaries.
	 */
	public static void showAndImport(PluginTool tool, BatchInfo batchInfo, List<FSRL> initialFiles,
			DomainFolder defaultFolder, ProgramManager programManager) {
		BatchImportDialog dialog = new BatchImportDialog(batchInfo, defaultFolder, programManager);
		SystemUtilities.runSwingLater(() -> {
			if (initialFiles != null && !initialFiles.isEmpty()) {
				dialog.addSources(initialFiles);
			}
			if (!dialog.setupInitialDefaults()) {
				return;
			}
			tool.showDialog(dialog);
		});
	}

	private BatchInfo batchInfo;
	private DomainFolder destinationFolder;
	private ProgramManager programManager;
	private boolean stripLeading = getBooleanPref(PREF_STRIPLEADING, true);
	private boolean stripContainer = getBooleanPref(PREF_STRIPCONTAINER, false);
	private boolean openAfterImporting = false;

	private BatchImportTableModel tableModel;
	private GTable table;
	private JButton removeSourceButton;
	private JButton rescanButton;
	private JSpinner maxDepthSpinner;

	private SourcesListModel sourceListModel;

	private BatchImportDialog(BatchInfo batchInfo, DomainFolder defaultFolder,
			ProgramManager programManager) {
		super("Batch Import", true);

		this.batchInfo = (batchInfo != null) ? batchInfo : new BatchInfo();
		this.destinationFolder = defaultFolder != null ? defaultFolder
				: AppInfo.getActiveProject().getProjectData().getRootFolder();
		this.programManager = programManager;

		setHelpLocation(new HelpLocation("ImporterPlugin", "Batch_Import_Dialog"));

		// a reasonable size that is long enough to show path information and table columns with
		// a height that has enough room to show table rows and import sources
		setPreferredSize(900, 600);

		build();
	}

	private void build() {
		tableModel = new BatchImportTableModel(batchInfo) {
			@Override
			public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
				super.setValueAt(aValue, rowIndex, columnIndex);
				refreshButtons();
			}

		};
		table = new GTable(tableModel);
		table.getAccessibleContext().setAccessibleName("多项内容");
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {

				Point p = e.getPoint();
				int row = table.rowAtPoint(p);
				int col = table.columnAtPoint(p);
				TableColumnModel columnModel = table.getColumnModel();
				TableColumn column = columnModel.getColumn(col);
				int modelIndex = column.getModelIndex();
				if (modelIndex == BatchImportTableModel.COLS.FILES.ordinal()) {
					showFiles(row);
				}
			}
		});

		// Turn off all grid lines - this is a problem on windows.
		table.setShowGrid(false);
		table.setIntercellSpacing(new Dimension(0, 0));

		TableColumn selectedColumn =
			table.getColumnModel().getColumn(BatchImportTableModel.COLS.SELECTED.ordinal());
		selectedColumn.setResizable(false);
		// TODO: automagically get necessary col width
		selectedColumn.setMaxWidth(50);

		TableColumn filesColumn =
			table.getColumnModel().getColumn(BatchImportTableModel.COLS.FILES.ordinal());

		filesColumn.setCellEditor(createFilesColumnCellEditor());
		filesColumn.setCellRenderer(createFilesColumnCellRenderer());

		TableColumn langColumn =
			table.getColumnModel().getColumn(BatchImportTableModel.COLS.LANG.ordinal());
		langColumn.setCellEditor(createLangColumnCellEditor());
		langColumn.setCellRenderer(createLangColumnCellRenderer());

		JScrollPane scrollPane = new JScrollPane(table);

		JPanel filesPanel = new JPanel();
		filesPanel.setLayout(new BorderLayout());
		filesPanel.add(scrollPane, BorderLayout.CENTER);
		filesPanel.setBorder(createTitledBorder("导入文件", true));

		JPanel sourceListPanel = new JPanel();
		sourceListPanel.setLayout(new BorderLayout());
		sourceListPanel.setBorder(createTitledBorder("导入源", false));
		sourceListPanel.getAccessibleContext().setAccessibleName("源列表");

		sourceListModel = new SourcesListModel();

		JList<String> sourceList = new JList<>(sourceListModel);
		sourceList.setName("batch.import.source.list");
		sourceList.getAccessibleContext().setAccessibleName("多项导入源列表");
		sourceList.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				boolean hasSelection = sourceList.getSelectedIndices().length > 0;
				removeSourceButton.setEnabled(hasSelection);
			}
		});
		JScrollPane sourceListScrollPane = new JScrollPane(sourceList);
		sourceListPanel.add(sourceListScrollPane, BorderLayout.CENTER);
		sourceListScrollPane.getAccessibleContext().setAccessibleName("源列表滚动");

		JPanel sourceOptionsPanel = new JPanel();
		sourceOptionsPanel.getAccessibleContext().setAccessibleName("源选项");

		// some padding before the files table
		sourceOptionsPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		sourceListPanel.add(sourceOptionsPanel, BorderLayout.SOUTH);

		JPanel maxDepthPanel = new JPanel();
		JLabel maxDepthLabel = new GDLabel("深度限制：");
		String maxDepthTip = "源文件中递归解析的最大容器深度（例如嵌套的 zip、tar 等）。";
		maxDepthLabel.setToolTipText(maxDepthTip);
		maxDepthPanel.add(maxDepthLabel);

		SpinnerNumberModel spinnerNumberModel =
			new SpinnerNumberModel(batchInfo.getMaxDepth(), 0, 99, 1);
		maxDepthSpinner = new JSpinner(spinnerNumberModel);
		maxDepthSpinner.setToolTipText(maxDepthTip);
		rescanButton = new JButton("重新");
		rescanButton.setToolTipText(
			"清空文件导入列表并重新扫描导入源以导入应用程序。");

		spinnerNumberModel.addChangeListener(e -> {
			rescanButton.setEnabled(
				spinnerNumberModel.getNumber().intValue() != batchInfo.getMaxDepth());
		});
		rescanButton.addActionListener(e -> {
			// NOTE: using invokeLater to avoid event handling issues where
			// the spinner model gets updated several times (ie. multi-decrement when
			// it should be just 1 dec) if we do anything modal.
			SystemUtilities.runSwingLater(() -> {
				setMaxDepth(spinnerNumberModel.getNumber().intValue());
			});
		});
		maxDepthPanel.add(maxDepthSpinner);
		maxDepthPanel.add(rescanButton);
		sourceOptionsPanel.add(maxDepthPanel);

		JPanel sourceListButtonsPanel = new JPanel();
		sourceListButtonsPanel.setLayout(new BorderLayout());
		sourceListButtonsPanel.getAccessibleContext().setAccessibleName("源列表按钮");

		JButton addSourceButton = new JButton("添加");
		addSourceButton.getAccessibleContext().setAccessibleName("添加源");
		this.removeSourceButton = new JButton("移除");
		removeSourceButton.setEnabled(false);
		removeSourceButton.getAccessibleContext().setAccessibleName("移除");

		addSourceButton.addActionListener(e -> {
			addSources();
		});

		removeSourceButton.addActionListener(e -> {
			List<FSRL> sourcesToRemove = new ArrayList<>();
			for (int index : sourceList.getSelectedIndices()) {
				if (index >= 0 && index < batchInfo.getUserAddedSources().size()) {
					UserAddedSourceInfo uasi = batchInfo.getUserAddedSources().get(index);
					sourcesToRemove.add(uasi.getFSRL());
				}
			}
			for (FSRL fsrl : sourcesToRemove) {
				batchInfo.remove(fsrl);
			}
			refreshData();
		});

		sourceListButtonsPanel.add(addSourceButton, BorderLayout.NORTH);
		sourceListButtonsPanel.add(removeSourceButton, BorderLayout.SOUTH);

		// another wrapping panel so the borderlayout'd sourceListButtonsPanel doesn't
		// get forced to take up the full EAST cell of the containing panel.
		JPanel buttonWrapperPanel = new JPanel();
		buttonWrapperPanel.add(sourceListButtonsPanel);
		sourceListPanel.add(buttonWrapperPanel, BorderLayout.EAST);

		sourceListModel.addListDataListener(new ListDataListener() {
			@Override
			public void intervalRemoved(ListDataEvent e) {
				contentsChanged(e);
			}

			@Override
			public void intervalAdded(ListDataEvent e) {
				contentsChanged(e);
			}

			@Override
			public void contentsChanged(ListDataEvent e) {
				boolean hasSelection = sourceList.getSelectedIndices().length > 0;
				removeSourceButton.setEnabled(hasSelection);
			}
		});

		JPanel outputOptionsPanel = buildOutputOptionsPanel();
		outputOptionsPanel.getAccessibleContext().setAccessibleName("输出选项");

		Box box = Box.createVerticalBox();
		box.add(sourceListPanel);
		box.add(filesPanel);
		box.add(outputOptionsPanel);
		box.getAccessibleContext().setAccessibleName("多项导入");

		addOKButton();
		addCancelButton();

		addWorkPanel(box);
	}

	private Border createTitledBorder(String title, boolean drawLine) {
		// a bit of padding to separate the sections
		return BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5),
			BorderFactory.createTitledBorder(' ' + title + ' '));
	}

	private JPanel buildOutputOptionsPanel() {

		JPanel outputChoicesPanel = new JPanel();
		outputChoicesPanel.setLayout(new BoxLayout(outputChoicesPanel, BoxLayout.LINE_AXIS));
		outputChoicesPanel.getAccessibleContext().setAccessibleName("输出选项");

		GCheckBox stripLeadingCb = new GCheckBox("去除前导路径", stripLeading);
		stripLeadingCb.addChangeListener(e -> setStripLeading(stripLeadingCb.isSelected()));
		stripLeadingCb.setToolTipText("导入文件的目标文件夹将不包含源文件的前导路径。");
		stripLeadingCb.getAccessibleContext().setAccessibleName("去除前导路径");

		GCheckBox stripContainerCb = new GCheckBox("去除容器路径", stripContainer);
		stripContainerCb.addChangeListener(e -> setStripContainer(stripContainerCb.isSelected()));
		stripContainerCb.setToolTipText(
			"导入文件的目标文件夹将不会包含任何源路径名称。");
		stripContainerCb.getAccessibleContext().setAccessibleName("去除容器路径");

		GCheckBox openAfterImportCb = new GCheckBox("导入后打开", openAfterImporting);
		openAfterImportCb
				.addChangeListener(e -> setOpenAfterImporting(openAfterImportCb.isSelected()));
		openAfterImportCb.setToolTipText("在代码浏览器中打开导入的二进制文件");
		openAfterImportCb.getAccessibleContext().setAccessibleName("导入后打开");

		outputChoicesPanel.add(stripLeadingCb);
		outputChoicesPanel.add(stripContainerCb);
		if (programManager != null) {
			outputChoicesPanel.add(openAfterImportCb);
		}

		// add some spacing between this panel and the one below it
		outputChoicesPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

		BatchProjectDestinationPanel destPanel =
			new BatchProjectDestinationPanel(getComponent(), destinationFolder) {
				@Override
				public void onProjectDestinationChange(DomainFolder newFolder) {
					destinationFolder = newFolder;
				}
			};

		JPanel outputOptionsPanel = new JPanel(new BorderLayout());
		outputOptionsPanel.setBorder(createTitledBorder("导入选项", true));
		outputOptionsPanel.add(outputChoicesPanel, BorderLayout.NORTH);
		outputOptionsPanel.add(destPanel, BorderLayout.SOUTH);
		outputOptionsPanel.getAccessibleContext().setAccessibleName("输出选项");
		return outputOptionsPanel;
	}

	private void showFiles(int row) {

		BatchGroup group = tableModel.getRowObject(row);
		List<BatchLoadConfig> batchLoadConfigs = group.getBatchLoadConfig();

		//@formatter:off		
		List<String> names = batchLoadConfigs.stream()
			.map(batchLoadConfig -> batchLoadConfig.getPreferredFileName())
			.sorted()
			.collect(Collectors.toList())
			;
		//@formatter:on

		ListSelectionTableDialog<String> dialog =
			new ListSelectionTableDialog<>("Application Files", names);
		dialog.hideOkButton();
		dialog.showSelectMultiple(table);
	}

	private void setOpenAfterImporting(boolean b) {
		this.openAfterImporting = b;
	}

	private void refreshData() {
		sourceListModel.refresh();
		tableModel.refreshData();
		maxDepthSpinner.setValue(batchInfo.getMaxDepth());
		refreshButtons();
	}

	private void refreshButtons() {
		setOkEnabled(batchInfo.getEnabledCount() > 0);
		rescanButton.setEnabled(
			((Number) maxDepthSpinner.getValue()).intValue() != batchInfo.getMaxDepth());

	}

	public boolean setupInitialDefaults() {
		if (batchInfo.getUserAddedSources().isEmpty()) {
			if (!addSources()) {
				return false;
			}
		}

		if (batchInfo.getMaxDepth() < BatchInfo.MAXDEPTH_DEFAULT) {
			setMaxDepth(BatchInfo.MAXDEPTH_DEFAULT);
		}
		return true;
	}

	private boolean addSources() {

		GhidraFileChooser chooser = new GhidraFileChooser(getComponent());
		chooser.setMultiSelectionEnabled(true);
		chooser.setTitle("选择文件批量导入");
		chooser.setApproveButtonText("选择文件");
		chooser.setFileSelectionMode(GhidraFileChooserMode.FILES_AND_DIRECTORIES);
		chooser.addFileFilter(ImporterUtilities.LOADABLE_FILES_FILTER);
		chooser.addFileFilter(ImporterUtilities.CONTAINER_FILES_FILTER);
		chooser.setSelectedFileFilter(GhidraFileFilter.ALL);

		chooser.setLastDirectoryPreference(LAST_IMPORT_DIR);

		List<File> selectedFiles = chooser.getSelectedFiles();
		if (selectedFiles.isEmpty()) {
			return !chooser.wasCancelled();
		}

		List<FSRL> filesToAdd = new ArrayList<>();
		for (File selectedFile : selectedFiles) {
			filesToAdd.add(FileSystemService.getInstance().getLocalFSRL(selectedFile));
		}

		chooser.dispose();

		return addSources(filesToAdd);
	}

	private boolean addSources(List<FSRL> filesToAdd) {

		List<FSRL> updatedFiles = filesToAdd.stream().map(FSRL::convertRootToContainer).toList();

		List<FSRL> badFiles = batchInfo.addFiles(updatedFiles);
		if (!badFiles.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			for (FSRL fsrl : badFiles) {
				if (sb.length() > 0) {
					sb.append(",\n");
				}
				sb.append(fsrl.getPath());
			}

			Msg.showWarn(this, getComponent(), "跳过 " + badFiles.size() + " 文件",
				"将文件添加到批处理中时遇到程序：" + sb.toString());

		}

		refreshData();

		return true;
	}

	@Override
	protected void okCallback() {
		new TaskLauncher(
			new ImportBatchTask(batchInfo, destinationFolder,
				openAfterImporting ? programManager : null, stripLeading, stripContainer),
			getComponent());
		close();
	}

	private TableCellEditor createFilesColumnCellEditor() {
		JComboBox<Object> comboBox = new GComboBox<>();
		DefaultCellEditor cellEditor = new DefaultCellEditor(comboBox) {
			@Override
			public boolean shouldSelectCell(EventObject anEvent) {
				return true;
			}

			@Override
			public Component getTableCellEditorComponent(JTable jtable, Object value,
					boolean isSelected, int row, int column) {
				comboBox.setSelectedItem("");
				comboBox.removeAllItems();

				BatchGroup rowVal = tableModel.getRowObject(row);
				comboBox.addItem("" + rowVal.size() + " 文件...");

				for (BatchLoadConfig batchLoadConfig : rowVal.getBatchLoadConfig()) {
					comboBox.addItem(batchLoadConfig.getPreferredFileName());
				}

				return super.getTableCellEditorComponent(table, value, isSelected, row, column);
			}
		};
		cellEditor.setClickCountToStart(2);
		return cellEditor;
	}

	private TableCellRenderer createFilesColumnCellRenderer() {
		TableCellRenderer cellRenderer = new GTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(GTableCellRenderingData data) {

				JLabel renderer = (JLabel) super.getTableCellRendererComponent(data);
				renderer.setToolTipText("点击查看文件");
				return renderer;
			}

			@Override
			protected String getText(Object value) {
				BatchGroup batchGroup = (BatchGroup) value;
				if (batchGroup != null) {
					return batchGroup.size() + " 文件...";
				}
				return "";
			}
		};

		return cellRenderer;
	}

	private TableCellEditor createLangColumnCellEditor() {
		JComboBox<Object> comboBox = new GComboBox<>();
		DefaultCellEditor cellEditor = new DefaultCellEditor(comboBox) {
			@Override
			public boolean shouldSelectCell(EventObject anEvent) {
				return false;
			}

			@Override
			public Component getTableCellEditorComponent(JTable jtable, Object value,
					boolean isSelected, int row, int column) {
				comboBox.removeAllItems();
				BatchGroup batchGroup = tableModel.getRowObject(row);
				for (BatchGroupLoadSpec bo : batchGroup.getCriteria().getBatchGroupLoadSpecs()) {
					comboBox.addItem(bo);
				}

				return super.getTableCellEditorComponent(jtable, value, isSelected, row, column);
			}
		};

		return cellEditor;
	}

	private TableCellRenderer createLangColumnCellRenderer() {
		TableCellRenderer cellRenderer = new GTableCellRenderer() {
			{
				setHTMLRenderingEnabled(true);
			}

			@Override
			public Component getTableCellRendererComponent(GTableCellRenderingData data) {
				JLabel renderer = (JLabel) super.getTableCellRendererComponent(data);
				renderer.setToolTipText("点击设置语言");
				return renderer;
			}

			@Override
			protected String getText(Object value) {
				BatchGroupLoadSpec bgls = (BatchGroupLoadSpec) value;
				return (bgls != null) ? bgls.toString()
						: "<html><font size=\"-2\" color=\"" + Messages.HINT +
							"\">Click to set language</font>";
			}
		};

		return cellRenderer;
	}

	private class SourcesListModel extends AbstractListModel<String> {

		int prevSize = batchInfo.getUserAddedSources().size();

		@Override
		public int getSize() {
			return prevSize;
		}

		@Override
		public String getElementAt(int index) {
			List<UserAddedSourceInfo> list = batchInfo.getUserAddedSources();
			if (index >= list.size()) {
				return "Missing";
			}

			UserAddedSourceInfo uasi = list.get(index);
			String info = String.format("%s [%d files/%d apps/%d containers/%d%s levels]",
				uasi.getFSRL().getPath(), uasi.getRawFileCount(), uasi.getFileCount(),
				uasi.getContainerCount(),
				uasi.getMaxNestLevel() - uasi.getFSRL().getNestingDepth() + 1,
				uasi.wasRecurseTerminatedEarly() ? "+" : "");
			return info;
		}

		public void refresh() {
			if (prevSize > 0) {
				fireIntervalRemoved(this, 0, prevSize - 1);
			}
			prevSize = batchInfo.getUserAddedSources().size();
			if (prevSize > 0) {
				fireIntervalAdded(this, 0, prevSize - 1);
			}
		}

	}

	private void setStripLeading(boolean stripLeading) {
		this.stripLeading = stripLeading;
		setBooleanPref(PREF_STRIPLEADING, stripLeading);
	}

	private void setStripContainer(boolean stripContainer) {
		this.stripContainer = stripContainer;
		setBooleanPref(PREF_STRIPCONTAINER, stripContainer);
	}

	private void setMaxDepth(int newMaxDepth) {
		if (newMaxDepth == batchInfo.getMaxDepth()) {
			return;
		}

		batchInfo.setMaxDepth(newMaxDepth); // this runs a task
		refreshData();
	}

	private static boolean getBooleanPref(String name, boolean defaultValue) {
		return Boolean
				.parseBoolean(Preferences.getProperty(name, Boolean.toString(defaultValue), true));
	}

	private static void setBooleanPref(String name, boolean value) {
		Preferences.setProperty(name, Boolean.toString(value));
	}
}
