package at.jku.isse.ecco.gui;

import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Stream;

/**
 * A general, content-aware replacement for {@link TableView#CONSTRAINED_RESIZE_POLICY} /
 * {@link TreeTableView#CONSTRAINED_RESIZE_POLICY}. That policy proportionally stretches every
 * resizable column to fill the table's width regardless of what each column actually holds - so a
 * 3-character "Id" column ends up the same width as a "Condition"/"Message" column holding a full
 * sentence. This class instead sizes each column to its own content, measured with {@link Text} -
 * a fully public JavaFX API, not {@code com.sun.javafx.*} internals, which would need
 * {@code --add-exports} flags this module's javafx plugin config doesn't pass - lets long text
 * wrap onto multiple lines instead of eliding ({@link #wrappingCellFactory()}), and lets one or
 * more designated columns absorb any leftover width so tables in wide panes don't leave an empty
 * gutter ({@link #growToFill}).
 * <p>
 * Callers should not call {@code setColumnResizePolicy(...CONSTRAINED_RESIZE_POLICY)} on a table
 * using this class - simply omit that call; JavaFX's default (unconstrained, per-column) resize
 * behavior is exactly what per-column width control needs.
 */
public final class TableColumns {

	private TableColumns() {
	}

	/** Fallback/default width for columns whose cells aren't plain measurable text. */
	private static final double CONTROL_COLUMN_WIDTH = 60;
	private static final double TEXT_PADDING = 24; // cell insets plus a little breathing room
	private static final double MIN_TEXT_WIDTH = 50;
	// long outlier values wrap (see wrappingCellFactory()) and grow the row instead of the column
	// growing without bound
	private static final double MAX_AUTO_WIDTH = 400;
	private static final double GROW_FUDGE = 20; // scrollbar/border slop, so growToFill doesn't trigger a spurious horizontal scrollbar

	private static final Map<TableView<?>, List<TableColumn<?, ?>>> tableGrowColumns = new WeakHashMap<>();
	private static final Map<TreeTableView<?>, List<TreeTableColumn<?, ?>>> treeGrowColumns = new WeakHashMap<>();


	// ---------------------------------------------------------------- TableView

	/**
	 * Auto-fits {@code column} to the width of its longest current cell value (falling back to its
	 * header text if {@code items} is empty), and re-measures whenever {@code items} changes - adds
	 * and removes are coalesced onto the next pulse via {@link Platform#runLater}, so a refresh that
	 * clears then re-adds many rows one at a time only triggers one recompute, not one per row. Use
	 * for ordinary text columns; for non-text columns (booleans, custom graphic cells, controls) use
	 * {@link #controlWidth} instead - there is no meaningful "content width" to measure there.
	 */
	public static <S> void fitToContent(TableColumn<S, ?> column, ObservableList<S> items) {
		boolean[] pending = {false};
		Runnable resize = () -> {
			pending[0] = false;
			double max = measure(column.getText());
			for (S item : items) {
				ObservableValue<?> cellValue = column.getCellObservableValue(item);
				Object value = cellValue == null ? null : cellValue.getValue();
				if (value != null) {
					max = Math.max(max, measure(String.valueOf(value)));
				}
			}
			column.setPrefWidth(clamp(max + TEXT_PADDING));
		};
		resize.run();
		items.addListener((ListChangeListener<S>) change -> {
			if (!pending[0]) {
				pending[0] = true;
				Platform.runLater(resize);
			}
		});
	}

	/**
	 * Sizes {@code column} to its header text only (never its cell content) - for columns whose
	 * cells are checkboxes, custom graphics (e.g. a bar chart), or controls (e.g. a
	 * {@link javafx.scene.control.ColorPicker}) rather than plain text, where text-measuring the
	 * cell value would be meaningless or wrong.
	 */
	public static void controlWidth(TableColumn<?, ?> column) {
		column.setPrefWidth(Math.max(CONTROL_COLUMN_WIDTH, measure(column.getText()) + TEXT_PADDING));
	}

	/**
	 * Sets a sensible starting width without a text-measurement pass, for columns whose content is
	 * short and roughly constant-width (a short id, a formatted date) - unlike {@link #fixedWidth},
	 * the column stays resizable, since there's no reason to stop the user from widening it if they
	 * want to (e.g. to see a full id that happens to be longer than usual).
	 */
	public static void defaultWidth(TableColumn<?, ?> column, double width) {
		column.setPrefWidth(width);
	}

	/**
	 * Sets a genuinely fixed, non-resizable width - only for columns where resizing would actively
	 * break something (e.g. a column holding a fixed-size button or icon). For columns that merely
	 * have a short, roughly-constant-width default (a short id, a formatted date), use
	 * {@link #defaultWidth} instead so the user can still resize them.
	 */
	public static void fixedWidth(TableColumn<?, ?> column, double width) {
		column.setPrefWidth(width);
		column.setMinWidth(width);
		column.setMaxWidth(width);
		column.setResizable(false);
	}

	/**
	 * Designates {@code column} as growing to consume leftover width once every other (non-grow)
	 * column in {@code table} has taken its own (auto-fit or fixed) width, so a table in a wide pane
	 * doesn't leave an empty gutter. Typically the long free-text column ("Condition"/"Message").
	 * May be called more than once for the same table (e.g. a table with two side-by-side long-text
	 * columns); leftover width is then split evenly among every column designated on that table -
	 * the set of "siblings" whose width is subtracted is recomputed fresh on every resize (not
	 * cached at registration time), so this stays correct regardless of how many grow columns end
	 * up registered or in what order.
	 */
	public static void growToFill(TableView<?> table, TableColumn<?, ?> column) {
		List<TableColumn<?, ?>> grown = tableGrowColumns.get(table);
		boolean firstTimeForThisTable = grown == null;
		if (firstTimeForThisTable) {
			grown = new ArrayList<>();
			tableGrowColumns.put(table, grown);
		}
		grown.add(column);

		Runnable recompute = () -> recomputeTableGrow(table);
		if (firstTimeForThisTable) {
			table.widthProperty().addListener((observable, oldValue, newValue) -> recompute.run());
			leaves(table).forEach(leaf -> leaf.widthProperty().addListener((observable, oldValue, newValue) -> recompute.run()));
		}
		Platform.runLater(recompute);
	}

	private static void recomputeTableGrow(TableView<?> table) {
		List<TableColumn<?, ?>> grown = tableGrowColumns.get(table);
		if (grown == null || grown.isEmpty()) {
			return;
		}
		List<TableColumn<?, ?>> siblings = leaves(table).filter(column -> !grown.contains(column)).toList();
		double siblingsWidth = siblings.stream().mapToDouble(TableColumn::getWidth).sum();
		double leftover = Math.max(0, table.getWidth() - siblingsWidth - GROW_FUDGE);
		double share = leftover / grown.size();
		for (TableColumn<?, ?> grow : grown) {
			grow.setPrefWidth(Math.max(share, MIN_TEXT_WIDTH));
		}
	}

	/**
	 * A {@link TableCell} that renders its text wrapped (width bound to the column), so long
	 * content grows the row taller instead of forcing the column - and the table - wider.
	 */
	public static <S> Callback<TableColumn<S, String>, TableCell<S, String>> wrappingCellFactory() {
		return column -> new TableCell<>() {
			private final Label label = new Label();

			{
				label.setWrapText(true);
				label.prefWidthProperty().bind(column.widthProperty().subtract(10));
			}

			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setGraphic(null);
				} else {
					label.setText(item);
					setGraphic(label);
				}
			}
		};
	}


	// ---------------------------------------------------------------- TreeTableView

	/**
	 * Same as {@link #fitToContent(TableColumn, ObservableList)}, for a {@link TreeTableColumn}.
	 * Walks {@code root} and every descendant once - a plain one-shot measurement rather than an
	 * auto-relistening one, since the one live {@code TreeTableView} in this app
	 * ({@code ArtifactTreeTableView}) rebuilds its whole tree rather than mutating an existing one
	 * in place; call this again after rebuilding.
	 */
	public static <S> void fitToContent(TreeTableColumn<S, ?> column, TreeItem<S> root) {
		double max = measure(column.getText());
		max = Math.max(max, measureTree(column, root));
		column.setPrefWidth(clamp(max + TEXT_PADDING));
	}

	private static <S> double measureTree(TreeTableColumn<S, ?> column, TreeItem<S> item) {
		double max = 0;
		ObservableValue<?> cellValue = column.getCellObservableValue(item);
		Object value = cellValue == null ? null : cellValue.getValue();
		if (value != null) {
			max = measure(String.valueOf(value));
		}
		for (TreeItem<S> child : item.getChildren()) {
			max = Math.max(max, measureTree(column, child));
		}
		return max;
	}

	/** Same as {@link #controlWidth(TableColumn)}, for a {@link TreeTableColumn}. */
	public static void controlWidth(TreeTableColumn<?, ?> column) {
		column.setPrefWidth(Math.max(CONTROL_COLUMN_WIDTH, measure(column.getText()) + TEXT_PADDING));
	}

	/** Same as {@link #defaultWidth(TableColumn, double)}, for a {@link TreeTableColumn}. */
	public static void defaultWidth(TreeTableColumn<?, ?> column, double width) {
		column.setPrefWidth(width);
	}

	/** Same as {@link #fixedWidth(TableColumn, double)}, for a {@link TreeTableColumn}. */
	public static void fixedWidth(TreeTableColumn<?, ?> column, double width) {
		column.setPrefWidth(width);
		column.setMinWidth(width);
		column.setMaxWidth(width);
		column.setResizable(false);
	}

	/** Same as {@link #growToFill(TableView, TableColumn)}, for a {@link TreeTableView}/{@link TreeTableColumn}. */
	public static void growToFill(TreeTableView<?> table, TreeTableColumn<?, ?> column) {
		List<TreeTableColumn<?, ?>> grown = treeGrowColumns.get(table);
		boolean firstTimeForThisTable = grown == null;
		if (firstTimeForThisTable) {
			grown = new ArrayList<>();
			treeGrowColumns.put(table, grown);
		}
		grown.add(column);

		Runnable recompute = () -> recomputeTreeGrow(table);
		if (firstTimeForThisTable) {
			table.widthProperty().addListener((observable, oldValue, newValue) -> recompute.run());
			treeLeaves(table).forEach(leaf -> leaf.widthProperty().addListener((observable, oldValue, newValue) -> recompute.run()));
		}
		Platform.runLater(recompute);
	}

	private static void recomputeTreeGrow(TreeTableView<?> table) {
		List<TreeTableColumn<?, ?>> grown = treeGrowColumns.get(table);
		if (grown == null || grown.isEmpty()) {
			return;
		}
		List<TreeTableColumn<?, ?>> siblings = treeLeaves(table).filter(column -> !grown.contains(column)).toList();
		double siblingsWidth = siblings.stream().mapToDouble(TreeTableColumn::getWidth).sum();
		double leftover = Math.max(0, table.getWidth() - siblingsWidth - GROW_FUDGE);
		double share = leftover / grown.size();
		for (TreeTableColumn<?, ?> grow : grown) {
			grow.setPrefWidth(Math.max(share, MIN_TEXT_WIDTH));
		}
	}

	/** Same as {@link #wrappingCellFactory()}, for a {@link TreeTableCell}. */
	public static <S> Callback<TreeTableColumn<S, String>, TreeTableCell<S, String>> wrappingTreeCellFactory() {
		return column -> new TreeTableCell<>() {
			private final Label label = new Label();

			{
				label.setWrapText(true);
				label.prefWidthProperty().bind(column.widthProperty().subtract(10));
			}

			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setGraphic(null);
				} else {
					label.setText(item);
					setGraphic(label);
				}
			}
		};
	}


	// ---------------------------------------------------------------- internals

	/**
	 * Flattens a table's column tree to leaf columns - several views in this app group their real
	 * columns under one umbrella "title" column (e.g. {@code commitsCol.getColumns().addAll(idCol,
	 * ...)}), which {@link #growToFill}'s sibling-width accounting needs to see through.
	 */
	private static Stream<TableColumn<?, ?>> leaves(TableView<?> table) {
		return table.getColumns().stream().flatMap(TableColumns::leavesOf);
	}

	@SuppressWarnings("unchecked")
	private static Stream<TableColumn<?, ?>> leavesOf(TableColumn<?, ?> column) {
		return column.getColumns().isEmpty() ? Stream.of(column)
				: column.getColumns().stream().flatMap(child -> leavesOf((TableColumn<?, ?>) child));
	}

	private static Stream<TreeTableColumn<?, ?>> treeLeaves(TreeTableView<?> table) {
		return table.getColumns().stream().flatMap(TableColumns::treeLeavesOf);
	}

	@SuppressWarnings("unchecked")
	private static Stream<TreeTableColumn<?, ?>> treeLeavesOf(TreeTableColumn<?, ?> column) {
		return column.getColumns().isEmpty() ? Stream.of(column)
				: column.getColumns().stream().flatMap(child -> treeLeavesOf((TreeTableColumn<?, ?>) child));
	}

	private static double measure(String text) {
		if (text == null || text.isEmpty()) {
			return 0;
		}
		Text measuring = new Text(text);
		measuring.setFont(Font.getDefault());
		return measuring.getLayoutBounds().getWidth();
	}

	private static double clamp(double width) {
		return Math.min(MAX_AUTO_WIDTH, Math.max(MIN_TEXT_WIDTH, width));
	}

}
