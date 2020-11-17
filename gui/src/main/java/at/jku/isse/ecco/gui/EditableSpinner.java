package at.jku.isse.ecco.gui;

import javafx.scene.control.Spinner;

public class EditableSpinner extends Spinner<Integer> {

	public EditableSpinner(int min, int max, int initial) {
		super(min, max, initial);

		this.setDisable(false);
		this.setEditable(true);
		this.getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
			try {
				Integer.parseInt(newValue);
				this.increment(0);
			} catch (NumberFormatException e) {
			}
		}); // workaround for not committing value when editing value
	}

}
