package ch.unifr.diva.dip.awt.imaging.ops;

import ch.unifr.diva.dip.awt.imaging.features.Histogram;
import ch.unifr.diva.dip.awt.imaging.scanners.Location;
import ch.unifr.diva.dip.awt.imaging.scanners.RasterScanner;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;

/**
 * Global threshold filter. This filter operates on a single band in the source
 * image.
 */
public class GlobalThresholdOp extends NullOp implements SimpleTileParallelizable {

	private int band;
	private int threshold;

	/**
	 * Creates a new, global threshold filter.
	 */
	public GlobalThresholdOp() {
		this(0);
	}

	/**
	 * Creates a new, global threshold filter.
	 *
	 * @param band the band to threshold.
	 */
	public GlobalThresholdOp(int band) {
		setBand(band);
		setThreshold(127);
	}

	/**
	 * Sets the band to threshold.
	 *
	 * @param band the band.
	 */
	public final void setBand(int band) {
		this.band = band;
	}

	/**
	 * Returns the band to threshold.
	 *
	 * @return the band.
	 */
	public int getBand() {
		return this.band;
	}

	/**
	 * Sets the global threshold.
	 *
	 * @param threshold the global threshold.
	 */
	public final void setThreshold(int threshold) {
		this.threshold = threshold;
	}

	/**
	 * Returns the global threshold.
	 *
	 * @return the global threshold.
	 */
	public int getThreshold() {
		return this.threshold;
	}

	/**
	 * Creates a compatible, binary destination image.
	 *
	 * @param src the source image.
	 * @return a compatible, binary destination image.
	 */
	public BufferedImage createBinaryDestImage(BufferedImage src) {
		return new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
	}

	@Override
	public BufferedImage filter(BufferedImage src, BufferedImage dst) {
		if (dst == null) {
			dst = createBinaryDestImage(src);
		}

		final WritableRaster srcRaster = src.getRaster();
		final WritableRaster dstRaster = dst.getRaster();

		for (Location pt : new RasterScanner(src, false)) {
			final int sample = srcRaster.getSample(pt.col, pt.row, this.band);
			dstRaster.setSample(
					pt.col,
					pt.row,
					0,
					(sample > this.threshold) ? 1 : 0
			);
		}

		return dst;
	}

	/**
	 * Takes the mean of the sample values as a threshold.
	 *
	 * @param src the image.
	 * @param band the band to compute the histogram from.
	 * @param histogram the histogram of the image (if already computed), or
	 * {@code null} (can be used if you need a pointer to the generated
	 * histogram).
	 * @return an optimal threshold.
	 */
	public static int computeMean(BufferedImage src, int band, Histogram histogram) {
		if (histogram == null) {
			histogram = new Histogram(src, band);
		}

		final int n = histogram.getNumberOfBins();
		double total = 0;
		double sum = 0;

		for (int i = 0; i < n; i++) {
			total += histogram.getValue(i);
			sum += i * histogram.getValue(i);
		}

		return (int) Math.floor(sum / total);
	}

	/**
	 * Automatic thresholding using the moment-preserving principle.
	 *
	 * <p>
	 * Tsai Wen-Hsiang: "Moment-Preserving Thresholding: A New Approach", 1985.
	 *
	 * @param src the image.
	 * @param band the band to compute the histogram from.
	 * @param histogram the histogram of the image (if already computed), or
	 * {@code null} (can be used if you need a pointer to the generated
	 * histogram).
	 * @return an optimal threshold.
	 */
	public static int computeMoments(BufferedImage src, int band, Histogram histogram) {
		if (histogram == null) {
			histogram = new Histogram(src, band);
		}

		final int n = histogram.getNumberOfBins();
		final double[] hist = new double[n];
		double total = 0;

		for (int i = 0; i < n; i++) {
			total += histogram.getValue(i);
		}

		// normalise histogram
		for (int i = 0; i < n; i++) {
			hist[i] = histogram.getValue(i) / total;
		}

		// calculate 1st, 2nd, and 3rd order moments
		final double[] m = new double[]{1.0, 0.0, 0.0, 0.0}; // m0, m1, m2, m3
		for (int i = 0; i < n; i++) {
			m[1] += i * hist[i];
			m[2] += i * i * hist[i];
			m[3] += i * i * i * hist[i];
		}

		// moment preserving equations
		final double cd = m[0] * m[2] - m[1] * m[1];
		final double c0 = (-m[2] * m[2] + m[1] * m[3]) / cd;
		final double c1 = (-m[0] * m[3] + m[2] * m[1]) / cd;
		final double z = Math.sqrt(c1 * c1 - 4.0 * c0);
		final double z0 = 0.5 * (-c1 - z);
		final double z1 = 0.5 * (-c1 + z);
		final double p0 = (z1 - m[1]) / (z1 - z0);

		double sum = 0;
		for (int i = 0; i < n; i++) {
			sum += hist[i];
			if (sum > p0) {
				return i;
			}
		}

		return -1;
	}

	/**
	 * Computes an optimal threshold by Otsu's method. Computes an optimal
	 * threshold by the discriminant criterion; namely, by maximizing the
	 * discriminant measure "n" (eta): the measure of separability of the
	 * resultant classes in gray levels.
	 *
	 * <p>
	 * Otsu, Nobuyuki: "A Threshold Selection Method from Gray-Level
	 * Histograms", 1979.
	 *
	 * @param src the image.
	 * @param band the band to compute the histogram from.
	 * @param histogram the histogram of the image (if already computed), or
	 * {@code null} (can be used if you need a pointer to the generated
	 * histogram).
	 * @return an optimal threshold.
	 */
	public static int computeOtsu(BufferedImage src, int band, Histogram histogram) {
		if (histogram == null) {
			histogram = new Histogram(src, band);
		}

		// compute total mean level
		float sum = 0;
		for (int i = 0; i < histogram.getNumberOfBins(); i++) {
			sum += i * histogram.getValue(i);
		}

		int threshold = 0;
		double vMax = 0;

		int wB = 0;
		int wF = 0;
		int sumB = 0;

		// find optimal threshold by maximizing the between-class variance
		for (int i = 0; i < histogram.getNumberOfBins(); i++) {
			// weights (or probability of class occurence)
			wB += histogram.getValue(i);
			if (wB == 0) {
				continue;
			}

			wF = histogram.getTotalSamples() - wB;
			if (wF == 0) {
				continue;
			}

			sumB += i * histogram.getValue(i);

			// class mean levels (of back- and foreground)
			final double mB = sumB / (double) wB;
			final double mF = (sum - sumB) / (double) wF;

			// between-class variance
			final double v = wB * wF * (mB - mF) * (mB - mF);

			if (v > vMax) {
				vMax = v;
				threshold = i;
			}
		}

		return threshold;
	}

}
