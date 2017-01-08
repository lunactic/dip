package ch.unifr.diva.dip.core.model;

import ch.unifr.diva.dip.core.ApplicationHandler;
import ch.unifr.diva.dip.core.ImageFormat;
import ch.unifr.diva.dip.core.ui.Localizable;
import ch.unifr.diva.dip.eventbus.events.ProjectNotification;
import ch.unifr.diva.dip.utils.FileFinder;
import ch.unifr.diva.dip.utils.IOUtils;
import ch.unifr.diva.dip.utils.Modifiable;
import ch.unifr.diva.dip.utils.ModifiedProperty;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.image.Image;
import javax.xml.bind.JAXBException;
import org.slf4j.LoggerFactory;

/**
 * Project page.
 */
public class ProjectPage implements Modifiable, Localizable {

	private static final org.slf4j.Logger log = LoggerFactory.getLogger(ProjectPage.class);
	private final ApplicationHandler handler;

	/**
	 * Root directory dedicated to the page in the zip file system (or
	 * savefile).
	 */
	protected final String ROOT_DIR;
	protected static final String ROOT_DIR_FORMAT = "/pages/%d/";

	/**
	 * XML file containing the page's pipeline delta patch.
	 */
	protected final String PIPELINE_PATCH_XML;
	protected static final String PIPELINE_PATCH_XML_FORMAT = "/pages/%d/patch.xml";

	/**
	 * Root directory of persitent data of processors.
	 */
	protected final String PROCESSORS_ROOT_DIR;
	protected static final String PROCESSORS_ROOT_DIR_FORMAT = "/pages/%d/processors/";

	/**
	 * Directory format for persistent data of a processor. Every runnable
	 * processor gets a directory all for himself.
	 */
	protected static final String PROCESSOR_DATA_DIR_FORMAT = "/pages/%d/processors/%d/data/";

	/**
	 * XML file format for persistent, serialized data of a processor. Every
	 * runnable processor gets a {@code Map<String, Object>} that is
	 * un-/marshalled by the host application (in addition to the dedicated
	 * directory, for ease of use).
	 */
	protected static final String PROCESSOR_DATA_XML_FORMAT = "/pages/%d/processors/%d/data.xml";

	/**
	 * Page id. Unique within the project.
	 */
	public final int id;

	/**
	 * Page file.
	 */
	public final Path file;

	/**
	 * Checksum of the page file.
	 */
	public final String checksum;

	private final Project project;
	private final StringProperty nameProperty;
	private final IntegerProperty pipelineIdProperty;
	private final ModifiedProperty modifiedPageProperty;

	// we load page resources lazily
	private Image image = null;
	private BufferedImage bufferedImage = null;
	private RunnablePipeline pipeline = null;

	/**
	 * Creates a new ProjectPage given an image file. Used to add pages to a
	 * living project.
	 *
	 * @param project the project.
	 * @param file the image file for the new page.
	 * @throws IOException
	 */
	public ProjectPage(Project project, Path file) throws IOException {
		this(project, newPageData(project, file));
	}

	private static ProjectData.Page newPageData(Project project, Path file) throws IOException {
		return new ProjectData.Page(
				project.newPageId(),
				file,
				project.pipelineManager().getDefaultPipelineId()
		);
	}

	/**
	 * Creates a ProjectPage.
	 *
	 * @param project the project.
	 * @param page the project page data.
	 */
	public ProjectPage(Project project, ProjectData.Page page) {
		this.handler = project.applicationHandler();
		this.project = project;
		this.id = page.id;
		this.ROOT_DIR = String.format(ROOT_DIR_FORMAT, this.id);
		this.PIPELINE_PATCH_XML = String.format(PIPELINE_PATCH_XML_FORMAT, this.id);
		this.PROCESSORS_ROOT_DIR = String.format(PROCESSORS_ROOT_DIR_FORMAT, this.id);
		this.pipelineIdProperty = new SimpleIntegerProperty(page.pipelineId);
		this.nameProperty = new SimpleStringProperty(page.name);
		this.file = page.file;
		this.checksum = page.checksum;
		this.modifiedPageProperty = new ModifiedProperty();

		this.modifiedPageProperty.addObservedProperty(pipelineIdProperty);
		this.modifiedPageProperty.addObservedProperty(nameProperty);
	}

	@Override
	public ModifiedProperty modifiedProperty() {
		return modifiedPageProperty;
	}

	/**
	 * Returns the project this page belongs to.
	 *
	 * @return the project this page belongs to.
	 */
	protected Project project() {
		return this.project;
	}

	/**
	 * Returns the name of the project page.
	 *
	 * @return the name of the project page.
	 */
	public String getName() {
		return nameProperty().get();
	}

	/**
	 * Sets the name of the project page.
	 *
	 * @param name new name of the project page.
	 */
	public void setName(String name) {
		this.nameProperty().set(name);
	}

	/**
	 * Name property.
	 *
	 * @return name property.
	 */
	public StringProperty nameProperty() {
		return nameProperty;
	}

	/**
	 * Returns a prototype of the page's pipeline. A prototype is a new pipeline
	 * without project/page data.
	 *
	 * @return a new/fresh pipeline.
	 */
	public Pipeline getPipelinePrototype() {
		return getPipelinePrototype(getPipelineId());
	}

	/**
	 * Returns a prototype of the pipeline with given id. A prototype is a new
	 * pipeline without project/page data.
	 *
	 * @param pipelineId pipeline id.
	 * @return a new/fresh pipeline.
	 */
	private Pipeline getPipelinePrototype(int pipelineId) {
		return project.pipelineManager().getPipeline(pipelineId);
	}

	/**
	 * Returns the runnable pipeline of the project page. The project page needs
	 * to be opened first (which loads the page's resources).
	 *
	 * @return a {@code RunnablePipeline}, or {@code null} if not opened(!).
	 * @see #open()
	 */
	public RunnablePipeline getPipeline() {
		return pipeline; // null if not opened!
	}

	/**
	 * Returns the pipeline id of the project pages's pipeline.
	 *
	 * @return the pipeline id, or -1 if none (or the empty pipeline) is set.
	 */
	public int getPipelineId() {
		return this.pipelineIdProperty().get();
	}

	/**
	 * Sets/updates the pipeline of the project page. This will delete all data
	 * associated to the previous/current pipeline!
	 *
	 * @param id new pipeline id, or -1 to delete the current one/set to the
	 * empty pipeline.
	 */
	public synchronized void setPipelineId(int id) {
		if (id == getPipelineId()) {
			return;
		}

		// TODO: option to keep state of all processors unchanged starting from
		// root/PageGenerator (case: only some procs. have been added/removed in
		// the new pipeline/variant)
		// for now; just start from scratch on pipeline change.
		clear();

		if (project.pipelineManager().pipelineExists(id)) {
			this.pipelineIdProperty.set(id);
			this.pipeline = getRunnablePipeline(id);
		} else {
			this.pipelineIdProperty.set(-1);
			this.pipeline = getRunnablePipeline();
		}

		handler.eventBus.post(new ProjectNotification(ProjectNotification.Type.MODIFIED));
	}

	/**
	 * Returns the pipeline id property.
	 *
	 * @return pipeline id property.
	 */
	public ReadOnlyIntegerProperty pipelineIdProperty() {
		return this.pipelineIdProperty;
	}

	/**
	 * Returns the pipeline's name.
	 *
	 * @return the name of the pipeline.
	 */
	public String getPipelineName() {
		final Pipeline pl = getPipelinePrototype(); // no need to have this page opened yet
		if (pl == null) {
			return localize("none").toLowerCase();
		}

		return pl.getName();
	}

	/**
	 * Loads page resources. This method is called upon selecting this page.
	 */
	public synchronized void open() {
		this.pipeline = getRunnablePipeline();
		if (this.pipeline != null) {
			this.modifiedPageProperty.addManagedProperty(this.pipeline);
		}
	}

	/**
	 * Checks whether the image associated to the page exists.
	 *
	 * @return True if the image exists, False otherwise.
	 */
	public boolean imageExists() {
		return Files.exists(file);
	}

	/**
	 * Returns the page's image.
	 *
	 * @return the image of the page as JavaFX Image.
	 * @throws IOException
	 */
	public synchronized Image image() throws IOException {
		if (image == null) {
			image = ImageFormat.getImage(file);
		}
		return image;
	}

	/**
	 * Returns the page's image.
	 *
	 * @return the image of the page as AWT BufferedImage.
	 * @throws IOException
	 */
	public synchronized BufferedImage bufferedImage() throws IOException {
		if (bufferedImage == null) {
			bufferedImage = ImageFormat.getBufferedImage(file);
		}
		return bufferedImage;
	}

	private RunnablePipeline getRunnablePipeline() {
		return getRunnablePipeline(this.getPipelineId());
	}

	private RunnablePipeline getRunnablePipeline(int pipelineId) {
		if (pipelineId < 0) {
			// return the empty pipeline (with nothing but a PageGenerator) s.t.
			// at least the image itself can be displayed/viewed
			return new RunnablePipeline(
					handler,
					this,
					PipelineData.emptyPipeline(handler.settings)
			);
		}

		// make sure we have a root directory for this page
		try {
			IOUtils.getRealDirectories(rootDirectory());
		} catch (IOException ex) {
			log.error("failed to retrieve the root directory of the project page: {}", this, ex);
			handler.uiStrategy.showError(ex);
			return null;
		}

		final Pipeline prototype = this.project().pipelineManager().getPipeline(pipelineId);
		if (prototype == null) {
			return null;
		}

		final PipelinePatch patch = pipelinePatch();
		if (patch != null && !patch.isEmpty()) {
			return prototype.cloneAsRunnablePipeline(this, patch);
		}

		return prototype.cloneAsRunnablePipeline(this);
	}

	/**
	 * Clears the project page. This removes any data associated to this project
	 * page including pipeline parameters or processor data. This method is
	 * usually called before a page is deleted and removed from the project for
	 * good; or just to reset the page and start over again.
	 */
	public synchronized void clear() {
		if (!Files.exists(rootDirectory())) {
			return;
		}

		try {
			FileFinder.deleteDirectory(rootDirectory());
		} catch (IOException ex) {
			log.error("failed to clear the project page: {}", this, ex);
			handler.uiStrategy.showError(ex);
		}
	}

	/**
	 * Returns a path to the root directory of the page.
	 *
	 * @return a path to the root directory of the page.
	 */
	protected Path rootDirectory() {
		return project.zipFileSystem().getPath(ROOT_DIR);
	}

	/**
	 * Returns a path to the root directory of processors of this page's
	 * pipeline.
	 *
	 * @return a path to the root directory of processors.
	 */
	protected Path processorRootDirectory() {
		return project.zipFileSystem().getPath(PROCESSORS_ROOT_DIR);
	}

	/**
	 * Returns the path to the pipeline patch XML file of the page.
	 *
	 * @return the path to the pipeline patch XML file of the page.
	 */
	protected Path pipelinePatchXml() {
		return project.zipFileSystem().getPath(PIPELINE_PATCH_XML);
	}

	/**
	 * Returns the pipeline patch of the page.
	 *
	 * @return the pipeline patch of the page, or null.
	 */
	private PipelinePatch pipelinePatch() {
		if (Files.exists(pipelinePatchXml())) {
			try {
				final PipelinePatch patch = PipelinePatch.loadAsStream(pipelinePatchXml());
				return patch;
			} catch (IOException | JAXBException ex) {
				log.error("failed to read the pipeline's patch: {}", this, ex);
				handler.uiStrategy.showError(ex);
			}
		}

		return null;
	}

	/**
	 * Returns a path to the data directory of a processor.
	 *
	 * @param processorId id of the processor.
	 * @return a path to the data directory of a processor.
	 */
	private Path processorDataDirectory(int processorId) {
		final String path = String.format(PROCESSOR_DATA_DIR_FORMAT, this.id, processorId);
		return project.zipFileSystem().getPath(path);
	}

	/**
	 * Frees page resources. This method is called once another page is
	 * selected/opened or before the project is closed.
	 */
	public synchronized void close() {
		save();

		this.image = null;
		this.bufferedImage = null;
		this.pipeline = null;
	}

	/**
	 * Saves the page.
	 */
	public synchronized void save() {
		if ((getPipeline() != null) && (getPipelineId() > 0)) {
			getPipeline().save();
			modifiedProperty().set(false);
		}
	}

	@Override
	public int hashCode() {
		return id;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final ProjectPage other = (ProjectPage) obj;
		return id == other.id;
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName()
				+ "{"
				+ "id=" + id
				+ ", name=" + nameProperty
				+ "}";
	}

}
