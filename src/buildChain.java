import java.io.File;
import java.io.FileFilter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.ImageLineInt;
import ar.com.hjg.pngj.PngReaderInt;
import ar.com.hjg.pngj.PngWriter;

public class buildChain {
	public final static int WIDTH = 512;
	public final static int HEIGHT = 512;

	public static void main(String[] args) {
		// Read in a png in the given folder
		// parse it to pixels
		// assign each color a node
		// for each pixel on the png
		// check the pixels adjacent to it
		// store the probabilities on it's color node
		System.out.println(new File(".").getAbsolutePath());
		String inputFolderName = args[0];
		File inputFolder = new File(inputFolderName);
		File[] inputFiles = inputFolder.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.getName().endsWith("png");
			}
		});

		if (inputFiles == null) {
			throw new RuntimeException("No .png files in folder: " + inputFolderName);
		}

		ImageInfo stolenInfo = null;
		// current color -> possible color, total times seen
		Map<RGB, Map<RGB, Long>> troutchain = new HashMap<>();
		Map<RGB, Long> generalCounts = new HashMap<>();
		for (File input : inputFiles) {
			// just steal the last info we saw...
			stolenInfo = new buildChain().analyzeImage(input, troutchain, generalCounts);
			System.out.println(troutchain.toString());
		}

		// calculate probabilities based on counts
		Map<RGB, Map<RGB, Double>> probs = new buildChain().calculateProbs(troutchain);

		// drawing image
		String outpath = args[1];
		File outFile = new File(outpath);
		new buildChain().drawImage(outFile, probs, stolenInfo, troutchain);
		// start with cross in middle
		// add 4 pixels that have 3 neighbors
		// use markov chain to determine their color

	}

	/**
	 * Draws an image using the given markov chain
	 * 
	 * @param outFile
	 * @param probs
	 * @param stolenInfo
	 * @param troutchain
	 */
	private void drawImage(File outFile, Map<RGB, Map<RGB, Double>> probs, ImageInfo stolenInfo,
			Map<RGB, Map<RGB, Long>> troutchain) {
		PngWriter out = null;
		try {
			ImageInfo newimg = new ImageInfo(WIDTH, HEIGHT, 8, false);
			out = new PngWriter(outFile, newimg, true);

			Random generator = new Random();
			Object[] keys = probs.keySet().toArray();

			RGB[][] img = new RGB[WIDTH][HEIGHT];
			// initialize img to all random
			for (int i = 0; i < img.length; i++) {
				img[i] = new RGB[img[i].length];
				for (int j = 0; j < img[i].length; j++) {
					img[i][j] = (RGB) keys[generator.nextInt(keys.length)];
				}
			}
			int topleft = 0;
			int top = 0;
			int left = 0;

			for (int i = 0; i < img.length; i++) {
				for (int j = 0; j < img[i].length; j++) {
					if (i - 1 < 0 || i + 1 >= img.length || j - 1 < 0 || j + 1 >= img[i].length) {
						continue;
					} else {
						// otherwise we need to pick a color based on our
						// neighbor colors
						// lets try top left, top, and left
						// TODO how to concat probabilities?
						// CHANGE CHANGE CHANGE just use left for now lol
						Double totalWeight = 0.0;
						RGB pickedPixel = null;
						// TODO combine counts here
						switch (generator.nextInt(3)) {
						case 0:
							for (RGB possiblePixel : probs.get(img[i - 1][j]).keySet()) {
								totalWeight += probs.get(img[i - 1][j]).get(possiblePixel);
								if (generator.nextDouble() <= totalWeight) {
									pickedPixel = possiblePixel;
									top++;
									break;
								}
							}
							break;
						case 1:
							for (RGB possiblePixel : probs.get(img[i - 1][j - 1]).keySet()) {
								totalWeight += probs.get(img[i - 1][j - 1]).get(possiblePixel);
								if (generator.nextDouble() <= totalWeight) {
									pickedPixel = possiblePixel;
									topleft++;
									break;
								}
							}
							break;
						case 2:
							for (RGB possiblePixel : probs.get(img[i][j - 1]).keySet()) {
								totalWeight += probs.get(img[i][j - 1]).get(possiblePixel);
								if (generator.nextDouble() <= totalWeight) {
									pickedPixel = possiblePixel;
									left++;
									break;
								}
							}
							break;
						}

						if (pickedPixel == null) {
							throw new RuntimeException("fix your probability code, dummy");
						}
						img[i][j] = pickedPixel;
					}
				}
			}
			System.out.println("top: " + top);
			System.out.println("topleft: " + topleft);
			System.out.println("left: " + left);

			// convert the 2d array of RGB to imagelines
			for (int i = 0; i < WIDTH; i++) {
				// convert a row of RGB's to int int int array
				List<Integer> row = new ArrayList<>();
				for (int j = 0; j < img[i].length; j++) {
					row.add(img[i][j].R);
					row.add(img[i][j].G);
					row.add(img[i][j].B);
				}
				int[] scanline = new int[WIDTH * 3];
				for (int j = 0; j < scanline.length; j += 3) {
					scanline[j] = row.get(j);
					scanline[j + 1] = row.get(j + 1);
					scanline[j + 2] = row.get(j + 2);
				}

				out.writeRow(new ImageLineInt(newimg, scanline));
			}
			out.end();
		} finally {
			if (out != null) {
				out.close();
			}
		}

	}

	/**
	 * Turns total counts of occurrence into chance/1 that that color will be
	 * picked
	 * 
	 * @param troutchain
	 * @return
	 */
	private Map<RGB, Map<RGB, Double>> calculateProbs(Map<RGB, Map<RGB, Long>> troutchain) {
		Map<RGB, Map<RGB, Double>> probs = new HashMap<>();
		for (RGB basePixel : troutchain.keySet()) {
			if (!probs.containsKey(basePixel)) {
				probs.put(basePixel, new HashMap<>());
			}
			Map<RGB, Double> pixelChances = probs.get(basePixel);
			Long totalCount = 0L;
			Map<RGB, Long> pixCounts = troutchain.get(basePixel);
			for (Long numCounts : pixCounts.values()) {
				totalCount += numCounts;
			}
			for (RGB chancePixel : pixCounts.keySet()) {
				pixelChances.put(chancePixel, pixCounts.get(chancePixel).doubleValue() / totalCount.doubleValue());
			}
		}
		return probs;
	}

	// parse it to pixels
	// assign each color a node
	// for each pixel on the png
	// check the pixels adjacent to it
	// TODO how to store info on each set of 3 pixels next to it? Right now it
	// only cares about counts singles
	// TODO maybe use sets? store counts as Map<Set<color>,count>?
	// store the probabilities on it's color node
	private ImageInfo analyzeImage(File input, Map<RGB, Map<RGB, Long>> troutchain, Map<RGB, Long> generalCounts) {
		PngReaderInt in = null;
		try {
			in = new PngReaderInt(input);
			// need to analyze in sets of 3 rows
			threeLines work = new threeLines();
			while (in.hasMoreRows()) {
				if (work.isFull()) {
					// do work
					// for the middle line length (start at 1 in so we can read
					// to our right)
					for (int i = 3; i < work.three[1].getSize() - 3; i += 3) {
						RGB pixel = new RGB();
						pixel.R = work.three[1].getElem(i);
						pixel.G = work.three[1].getElem(i + 1);
						pixel.B = work.three[1].getElem(i + 2);
						// make an entry in our chain if don't already have one
						if (!generalCounts.containsKey(pixel)) {
							generalCounts.put(pixel, 0L);
						}
						generalCounts.put(pixel, generalCounts.get(pixel) + 1);

						if (!troutchain.containsKey(pixel)) {
							troutchain.put(pixel, new HashMap<>());
						}
						Map<RGB, Long> counts = troutchain.get(pixel);

						countSurroundingPixels(counts, work, i);
					}

				}
				work.add(in.readRowInt());
			}
			return in.imgInfo;
		} finally {
			if (in != null) {
				in.close();
			}
		}
	}

	/**
	 * Given a position on an image, adds counts from all surrounding pixels
	 * 
	 * @param counts
	 * @param work
	 * @param threeLoc
	 * @param i
	 */
	public void countSurroundingPixels(Map<RGB, Long> counts, threeLines work, int i) {
		for (int j = 0; j < 3; j++) {
			for (int k = -1; k < 2; k++) {
				if (j != 1 && k != 0) {
					// System.out.println(k);
					countPixel(counts, work, j, i + k * 3);
				}
			}
		}
	}

	/**
	 * Add's 1 to the count for the given pixel
	 * 
	 * @param counts
	 * @param work
	 * @param threeLoc
	 * @param elemNum
	 */
	public void countPixel(Map<RGB, Long> counts, threeLines work, int threeLoc, int elemNum) {
		RGB pixel = new RGB();
		pixel.R = work.three[threeLoc].getElem(elemNum);
		pixel.G = work.three[threeLoc].getElem(elemNum + 1);
		pixel.B = work.three[threeLoc].getElem(elemNum + 2);
		if (!counts.containsKey(pixel)) {
			counts.put(pixel, 0L);
		}
		counts.put(pixel, counts.get(pixel) + 1);
	}

}
