package ch.unifr.diva.dip.gui.layout;

import ch.unifr.diva.dip.core.ApplicationSettings;
import ch.unifr.diva.dip.core.ui.Localizable;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

/**
 * Abstract class to build a stage (used as dialog or window). This class
 * already implements {@code Localizable} s.t. localized strings and messages
 * are easily available.
 */
public abstract class AbstractStage implements Localizable {

	protected final Stage stage;
	protected final Scene scene;
	protected final BorderPane root;

	/**
	 * Creates a stage without title.
	 *
	 * @param owner owner/parent window.
	 */
	public AbstractStage(Window owner) {
		this(owner, "");
	}

	/**
	 * Creates a stage.
	 *
	 * @param owner owner/parent window.
	 * @param title title of the stage.
	 */
	public AbstractStage(Window owner, String title) {
		super();

		stage = new Stage();
		stage.initStyle(StageStyle.UTILITY);
		stage.initModality(Modality.WINDOW_MODAL);
		stage.initOwner(owner);
		root = new BorderPane();
		root.getStyleClass().add("dip-stage");
		scene = new Scene(root);
		scene.getStylesheets().add(ApplicationSettings.stylesheet);

		stage.setTitle(title);
		stage.setScene(scene);
	}

	/**
	 * Shows the stage.
	 */
	public void show() {
		stage.show();
	}

	/**
	 * Shows the stage and waits for it to be hidden/closed.
	 */
	public void showAndWait() {
		stage.showAndWait();
	}

	/**
	 * Closes the stage.
	 */
	public void close() {
		stage.close();
	}

	/**
	 * Sets the value of the stage's property onCloseRequest.
	 *
	 * @param value event handler called when there is a request to close this
	 * stage.
	 */
	public void setOnCloseRequest(EventHandler<WindowEvent> value) {
		stage.setOnCloseRequest(value);
	}

	/**
	 * Returns the active scene.
	 *
	 * @return the scene.
	 */
	public Scene scene() {
		return scene;
	}

	/**
	 * Returns the active stage.
	 *
	 * @return the stage.
	 */
	public Stage stage() {
		return stage;
	}

	/**
	 * Sets the title of the stage.
	 *
	 * @param title the title of the stage.
	 */
	public void setTitle(String title) {
		stage.setTitle(title);
	}
}
