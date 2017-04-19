package ch.unifr.diva.dip.api.services;

import ch.unifr.diva.dip.api.components.EditorLayerPane;
import ch.unifr.diva.dip.api.components.InputPort;
import ch.unifr.diva.dip.api.components.OutputPort;
import ch.unifr.diva.dip.api.components.Port;
import ch.unifr.diva.dip.api.components.ProcessorContext;
import ch.unifr.diva.dip.api.parameters.Parameter;
import ch.unifr.diva.dip.api.imaging.BufferedIO;
import ch.unifr.diva.dip.api.imaging.BufferedMatrix;
import ch.unifr.diva.dip.api.imaging.ops.ConcurrentTileOp;
import ch.unifr.diva.dip.api.imaging.ops.Parallelizable;
import ch.unifr.diva.dip.api.utils.DipThreadPool;
import ch.unifr.diva.dip.api.utils.MathUtils;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javax.imageio.ImageIO;
import org.slf4j.LoggerFactory;

/**
 * ProcessorBase already implements some common bits of the {@code Processor}
 * interface, and offers some helper methods as well.
 */
public abstract class ProcessorBase implements Processor {

	protected static final org.slf4j.Logger log = LoggerFactory.getLogger(ProcessorBase.class);

	/**
	 * Name of the processor.
	 */
	protected final String name;

	// we use LinkedHashMap's (as opposed to ordinary HashMap's) since we care
	// about the order of ports and parameters
	/**
	 * Published input ports.
	 */
	protected final Map<String, InputPort> inputs = new LinkedHashMap();

	/**
	 * Published output ports.
	 */
	protected final Map<String, OutputPort> outputs = new LinkedHashMap();

	/**
	 * Published parameters.
	 */
	protected final Map<String, Parameter> parameters = new LinkedHashMap();

	/**
	 * Creates a new processor base.
	 *
	 * @param name name of the processor.
	 */
	public ProcessorBase(String name) {
		this.name = name;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public Map<String, Parameter> parameters() {
		return parameters;
	}

	@Override
	public Map<String, InputPort> inputs() {
		return inputs;
	}

	@Override
	public Map<String, OutputPort> outputs() {
		return outputs;
	}

	/**
	 * Resets all (published) output ports. Make sure to override this method if
	 * the processor is transmutable and offers a dynamic/changing set of output
	 * ports.
	 */
	protected void resetOutputs() {
		for (OutputPort output : outputs.values()) {
			output.setOutput(null);
		}
	}

	/**
	 * Clears/empties the processor layer.
	 *
	 * @param context the processor context.
	 */
	public static void resetLayer(ProcessorContext context) {
		context.getLayer().clear();
	}

	/**
	 * Provides and adds a simple {@code LayerPane} with an {@code ImageView} to
	 * the processor layer.
	 *
	 * @param context the processor context.
	 * @param bufferedImage the image to be displayed in the {@code LayerPane}.
	 * @return the added {@code LayerPane}.
	 */
	public static EditorLayerPane provideImageLayer(ProcessorContext context, BufferedImage bufferedImage) {
		return provideImageLayer(context, bufferedImage, "");
	}

	/**
	 * Provides and adds a simple {@code LayerPane} with an {@code ImageView} to
	 * the processor layer.
	 *
	 * @param context the processor context.
	 * @param bufferedImage the image to be displayed in the {@code LayerPane}.
	 * @param name the name of the layer.
	 * @return the added {@code LayerPane}.
	 */
	public static EditorLayerPane provideImageLayer(ProcessorContext context, BufferedImage bufferedImage, String name) {
		final Image image = SwingFXUtils.toFXImage(bufferedImage, null);
		return provideImageLayer(context, image, name);
	}

	/**
	 * Provides and adds a simple {@code LayerPane} with an {@code ImageView} to
	 * the processor layer.
	 *
	 * @param context the processor context.
	 * @param image the image to be displayed in the {@code LayerPane}.
	 * @return the added {@code LayerPane}.
	 */
	public static EditorLayerPane provideImageLayer(ProcessorContext context, Image image) {
		return provideImageLayer(context, image, "");
	}

	/**
	 * Provides and adds a named {@code LayerPane} with an {@code ImageView} to
	 * the processor layer.
	 *
	 * @param context the processor context.
	 * @param image the image to be displayed in the {@code LayerPane}.
	 * @param name the name of the layer.
	 * @return the added {@code LayerPane}.
	 */
	public static EditorLayerPane provideImageLayer(ProcessorContext context, Image image, String name) {
		final EditorLayerPane layer = context.getLayer().newLayerPane(name);
		layer.add(new ImageView(image));
		return layer;
	}

	/**
	 * Filters an image in parallel (if possible). Runs filters in parallel if
	 * they implement the {@code Parallelizable} interface by wrapping them in a
	 * {@code ConcurrentOp} first, otherwise runs them as is (and single
	 * threaded).
	 *
	 * <p>
	 * <em>Warning:</em> Double-check if you shouldn't pass a compatible
	 * destination image from the original image filter (which might be wrapped
	 * if run in parallel).
	 *
	 * @param context the processor context.
	 * @param op the image filter (hopefully implementing the
	 * {@code Parallelizable} interface).
	 * @param src the source image.
	 * @return the filtered image.
	 */
	protected BufferedImage filter(ProcessorContext context, BufferedImageOp op, BufferedImage src) {
		return filter(context, op, src, null);
	}

	/**
	 * Filters an image in parallel (if possible). Runs filters in parallel if
	 * they implement the {@code Parallelizable} interface by wrapping them in a
	 * {@code ConcurrentOp} first, otherwise runs them as is (and single
	 * threaded).
	 *
	 * <p>
	 * <em>Warning:</em> make absolutely sure to give an actually compatible
	 * destination image in case the image filter doesn't rely on
	 * {@code createCompatibleDestImage(BufferedImage bi, ColorModel cm)}
	 * (overriding it is fine, since the method on the given image filter will
	 * be called if wrapped by {@code ConcurrentOp}). So if the filter depends
	 * on another method (with a different signature) to create a compatible
	 * destination image, better don't pass null here.
	 *
	 * @param context the processor context.
	 * @param op the image filter (hopefully implementing the
	 * {@code Parallelizable} interface).
	 * @param src the source image.
	 * @param dest the destination image, or null.
	 * @return the filtered image.
	 */
	protected BufferedImage filter(ProcessorContext context, BufferedImageOp op, BufferedImage src, BufferedImage dest) {
		return filter(context.getThreadPool(), op, src, dest);
	}

	/**
	 * Filters an image in parallel (if possible). Runs filters in parallel if
	 * they implement the {@code Parallelizable} interface by wrapping them in a
	 * {@code ConcurrentOp} first, otherwise runs them as is (and single
	 * threaded).
	 *
	 * <p>
	 * <em>Warning:</em> make absolutely sure to give an actually compatible
	 * destination image in case the image filter doesn't rely on
	 * {@code createCompatibleDestImage(BufferedImage bi, ColorModel cm)}
	 * (overriding it is fine, since the method on the given image filter will
	 * be called if wrapped by {@code ConcurrentOp}). So if the filter depends
	 * on another method (with a different signature) to create a compatible
	 * destination image, better don't pass null here.
	 *
	 * @param threadPool application wide thread pool/executor service
	 * @param op the image filter (hopefully implementing the
	 * {@code Parallelizable} interface).
	 * @param src the source image.
	 * @param dest the destination image, or null.
	 * @return the filtered image.
	 */
	public static BufferedImage filter(DipThreadPool threadPool, BufferedImageOp op, BufferedImage src, BufferedImage dest) {
		// get parallelizable mode of the op...
		final Parallelizable.Mode mode = Parallelizable.getMode(op, threadPool.poolSize());
		// ...and maybe have some further checks, if we not rather fall back to
		// single-threaded execution.
		switch (mode) {
			case TILE: {
				// do not parallelize super small images/tiles at all
				final int samples = src.getWidth() * src.getHeight() * src.getRaster().getNumBands();
				// TODO: find optimal threshold, this here is just a wild guess
				if (samples < (64 * 64 * 3)) {
					break;
				}

				final Rectangle tileSize = getOptimalTileSize(threadPool.poolSize(), src);
				if (tileSize.width > 0 && tileSize.height > 0) {
					final ConcurrentTileOp cop = new ConcurrentTileOp(
							op,
							tileSize.width,
							tileSize.height,
							threadPool
					);
					return cop.filter(src, dest);
				}
				break;
			}

			case SINGLE_THREADED:
			default:
				break;
		}

		// single threaded execution
		return op.filter(src, dest);
	}

	/**
	 * Returns the optimal tile size to run a filter in parallel.
	 *
	 * @param context the processor context.
	 * @param src the source image to be filtered.
	 * @return optimal tile size.
	 */
	protected Rectangle getOptimalTileSize(ProcessorContext context, BufferedImage src) {
		return getOptimalTileSize(context.getThreadPool().poolSize(), src);
	}

	/**
	 * Returns the optimal tile size to run a filter in parallel.
	 *
	 * @param numThreads number of threads.
	 * @param src the source image to be filtered.
	 * @return optimal tile size.
	 */
	public static Rectangle getOptimalTileSize(int numThreads, BufferedImage src) {
		/*
		 * Recall how ConcurrentOp uses it's workers/threads: we may just throw
		 * as many at it, and each worker will just get a new tile, as long as
		 * there are still tiles left to be processed. I.e. the tile size is not
		 * _that_ crucial after all. Optimally we'd like to have a tile size s.t.
		 * we end up with a number of tiles that can be evenly distributed to all
		 * threads (easier/less error with smaller tile size), while at the same
		 * time we'd like to have as few tiles as possible to reduce overhead -
		 * although that overhead isn't that great and fades in comparison to the
		 * usual workload, so...
		 *
		 * About THREAD SAFETY on binary images
		 * ------------------------------------
		 * Here comes a fun story: if we're operating on a BufferedImage of type
		 * BufferedImage.TYPE_BYTE_BINARY there will be errors/artifacts in the
		 * result IFF we're using a tile size that is not a power of two (and
		 * larger than or equal to 8)! Why? Most likely because the backing data
		 * buffer isn't a bit, but a byte buffer, so different threads want to
		 * manipulate different bits packed in the same bytes, hence the artifacts.
		 *
		 * As a solution we'd like to fix the tile size to be a power of two if
		 * we're dealing with a binary destination image. But since the destination
		 * image at this point might still be null, we can't reliably know that.
		 * Thanksfully the tile size doesn't matter much anyways, so we fix it in
		 * any case, while also making sure to not produce smaller tiles at the
		 * border, but larger ones an iteration earlier.
		 */
		// 1) a power of two
		final int w = MathUtils.nextPowerOfTwo(src.getWidth() / (numThreads + 1));
		final int h = MathUtils.nextPowerOfTwo(src.getHeight() / (numThreads + 1));

		// 2) at least 8
		return new Rectangle(
				Math.max(8, w),
				Math.max(8, h)
		);
	}

	/**
	 * Writes a {@code BufferedImage} to the savefile.
	 *
	 * @param context the processor context.
	 * @param filename the filename of the image.
	 * @param format the format (or extension) of the image (e.g. "PNG").
	 * @param image the image.
	 */
	public static void writeBufferedImage(ProcessorContext context, String filename, String format, BufferedImage image) {
		final Path file = context.getDirectory().resolve(filename);
		deleteFile(file);

		try (OutputStream os = Files.newOutputStream(file)) {
			ImageIO.write(image, format, os);
		} catch (IOException ex) {
			log.warn("failed to write file: {}", file, ex);
		}
	}

	/**
	 * Reads a {@code BufferedImage} from the savefile.
	 *
	 * @param context the processor context.
	 * @param filename the filename of the image.
	 * @return the image, or null.
	 */
	public static BufferedImage readBufferedImage(ProcessorContext context, String filename) {
		final Path file = context.getDirectory().resolve(filename);

		if (Files.exists(file)) {
			try (InputStream is = Files.newInputStream(file)) {
				return ImageIO.read(is);
			} catch (IOException ex) {
				log.warn("failed to read file: {}", file, ex);
			}
		}

		return null;
	}

	/**
	 * Writes a {@code BufferedMatrix} to the savefile.
	 *
	 * @param context the processor context.
	 * @param filename the filename of the image/matrix.
	 * @param mat the image/matrix.
	 */
	public static void writeBufferedMatrix(ProcessorContext context, String filename, BufferedMatrix mat) {
		final Path file = context.getDirectory().resolve(filename);
		deleteFile(file);

		/*
		 We can't close the output stream here! Neither explicitly, nor by means
		 of autoclosable/try-with-resource. Otherwise we end up with a bmat-file
		 of 0 bytes. See: https://bugs.openjdk.java.net/browse/JDK-8069211

		 "There is a bug in ZipFileSystem.EntryOutputStream.close(). The current
		 implementation does not prevent you from writing/closing into the stream
		 after you have closed it. The second close will basically reset the size
		 to wrong number... You test case close the stream twice, one is the
		 explicit close, on by the try/catch. Will file a bug. The temporary
		 workaround is to either remove the "out" out of the try/catch auto-close
		 one or don't close it explicitly."

		 ...so writeMat wrapps the output stream in a DataOutputStream to do his
		 thing and closes it, which gets propagated to the original output stream
		 as usual. If we now close the output stream here as well, that's the
		 second time we're closing it, and that's what triggers this bug and will
		 mess up the bmat-file (check with Files.exists(file), Files.size(file)).

		 Also see:
		 http://hg.openjdk.java.net/jdk9/dev/jdk/rev/b6ae6184b241
		 http://hg.openjdk.java.net/jdk9/jdk9/jdk/rev/b6ae6184b241

		 ...meaning this should be fixed with the next major release (Java9).
		 */
		try {
			OutputStream os = Files.newOutputStream(file);
			BufferedIO.writeMat(mat, os);
			// os.close(); // DON'T! (also no autoclose/try-with-resource)
		} catch (IOException ex) {
			log.warn("failed to write file: {}", file, ex);
		}
	}

	/**
	 * Reads a {@code BufferedMatrix} from the savefile.
	 *
	 * @param context the processor context.
	 * @param filename the filename of the image/matrix.
	 * @return the image/matrix, or null.
	 */
	public static BufferedMatrix readBufferedMatrix(ProcessorContext context, String filename) {
		final Path file = context.getDirectory().resolve(filename);

		if (Files.exists(file)) {
			try (InputStream is = Files.newInputStream(file)) {
				return BufferedIO.readMat(is);
			} catch (IOException ex) {
				log.warn("failed to read file: {}", file, ex);
			}
		}

		return null;
	}

	/**
	 * Writes an {@code Image} to the savefile.
	 *
	 * @param context the processor context.
	 * @param filename the filename of the image.
	 * @param format the format (or extension) of the image (e.g. "PNG").
	 * @param image the image.
	 */
	public static void writeImage(ProcessorContext context, String filename, String format, Image image) {
		final Path file = context.getDirectory().resolve(filename);
		deleteFile(file);

		try (OutputStream os = Files.newOutputStream(file)) {
			ImageIO.write(SwingFXUtils.fromFXImage(image, null), format, os);
		} catch (IOException ex) {
			log.warn("failed to write file: {}", file, ex);
		}
	}

	/**
	 * Reads an {@code Image} from the savefile.
	 *
	 * @param context the processor context.
	 * @param filename the filename of the image.
	 * @return the image, or null.
	 */
	public static Image readImage(ProcessorContext context, String filename) {
		final Path file = context.getDirectory().resolve(filename);

		if (Files.exists(file)) {
			try (InputStream is = Files.newInputStream(file)) {
				return new Image(is);
			} catch (IOException ex) {
				log.warn("failed to read file: {}", file, ex);
			}
		}

		return null;
	}

	/**
	 * Removes a file from the savefile (if it exists).
	 *
	 * @param context the processor context.
	 * @param filename the filename of the file.
	 * @return True if the file was deleted by this method, false otherwise.
	 */
	public static boolean deleteFile(ProcessorContext context, String filename) {
		final Path file = context.getDirectory().resolve(filename);
		return deleteFile(file);
	}

	/**
	 * Removes a file (if it exists).
	 *
	 * @param file the file.
	 * @return True if the file was deleted by this method, false otherwise.
	 */
	public static boolean deleteFile(Path file) {
		try {
			return Files.deleteIfExists(file);
		} catch (IOException ex) {
			log.error("failed to remove processor file: {}", file, ex);
			return false;
		}
	}

	/**
	 * XOR isConnected replacement. This method can be used to override the
	 * isConnected method, and is used on a set of optional ports where we
	 * require exactly a single connection.
	 *
	 * Usually used together with the xorCallback method which disables the rest
	 * of the ports, once the needed connection has been made.
	 *
	 * @see xorCallback
	 * @param ports a collection of optional ports.
	 * @return True if the port is connected, False otherwise.
	 */
	public static boolean xorIsConnected(Collection<? extends Port> ports) {
		for (Port port : ports) {
			if (port.isConnected()) {
				return true; // no need to check that there aren't more connections...
			}
		}
		return false;
	}

}
