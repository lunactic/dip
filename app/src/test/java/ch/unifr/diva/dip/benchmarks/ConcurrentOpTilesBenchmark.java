package ch.unifr.diva.dip.benchmarks;

import ch.unifr.diva.dip.api.imaging.SimpleColorModel;
import ch.unifr.diva.dip.api.imaging.ops.ColorConvertOp;
import ch.unifr.diva.dip.api.imaging.ops.ConcurrentTileOp;
import ch.unifr.diva.dip.api.imaging.ops.NullOp;
import ch.unifr.diva.dip.api.utils.DipThreadPool;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * ConcurrentOp tiles benchmark.
 */
public class ConcurrentOpTilesBenchmark {

	@State(Scope.Benchmark)
	public static class Resources {

		public static final DipThreadPool dtp = new DipThreadPool();

		@Param({"1024", "2048", "4096"})
		int size;

		@Param({"8"})
		public int numThreads;

		@Param({"64", "128", "256", "512", "1024"})
		public int tileSize;

		BufferedImage image;

		@Setup
		public void setup() {
			image = BenchmarkUtils.newRandomImage(size, BufferedImage.TYPE_INT_RGB);
		}

		@TearDown
		public void shutdown() {
			dtp.shutdown();
		}
	}

	@Benchmark
	@BenchmarkMode({Mode.AverageTime})
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public BufferedImage nullOp(Resources r) {
		ConcurrentTileOp op = new ConcurrentTileOp(
				new NullOp(),
				r.tileSize,
				r.tileSize,
				Resources.dtp,
				r.numThreads
		);
		return op.filter(r.image, null);
	}

	@Benchmark
	@BenchmarkMode({Mode.AverageTime})
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public BufferedImage colorConvertOp(Resources r) {
		ConcurrentTileOp op = new ConcurrentTileOp(
				new ColorConvertOp(SimpleColorModel.RGB, SimpleColorModel.YCbCr),
				r.tileSize,
				r.tileSize,
				Resources.dtp,
				r.numThreads
		);
		return op.filter(r.image, null);
	}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include(ConcurrentOpTilesBenchmark.class.getSimpleName())
				.warmupIterations(5)
				.measurementIterations(5)
				.forks(1)
				.build();

		Collection<RunResult> results = new Runner(opt).run();

		BenchmarkUtils.printRunResults(
				results,
				ConcurrentOpTilesBenchmark.class.getSimpleName()
		);
	}

}
