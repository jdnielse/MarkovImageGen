import java.io.File;
import java.io.FileFilter;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

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
		new buildChain().drawImage(outFile, probs, stolenInfo, troutchain, generalCounts);
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
	 * @param generalCounts
	 */
	private void drawImage(File outFile, Map<RGB, Map<RGB, Double>> probs, ImageInfo stolenInfo, Map<RGB, Map<RGB, Long>> troutchain, Map<RGB, Long> generalCounts) {
		PngWriter out = null;
		try {
			ImageInfo newimg = new ImageInfo(WIDTH, HEIGHT, 8, false);
			out = new PngWriter(outFile, newimg, true);

			Random generator = new Random();

			RGB[][] img = new RGB[WIDTH][HEIGHT];
			Map<RGB, Double> generalProb = new HashMap<>();
			Long totalCount = 0L;
			for (Long numCounts : generalCounts.values()) {
				totalCount += numCounts;
			}
			for (RGB chancePixel : generalCounts.keySet()) {
				generalProb.put(chancePixel, generalCounts.get(chancePixel).doubleValue() / totalCount.doubleValue());
			}

			// initialize img to weighted random
			for (int i = 0; i < img.length; i++) {
				img[i] = new RGB[img[i].length];
				for (int j = 0; j < img[i].length; j++) {
					Double totalWeight = 0.0;
					RGB pickedPixel = null;
					for (RGB possiblePixel : generalProb.keySet()) {
						totalWeight += generalProb.get(possiblePixel);
						if (generator.nextDouble() <= totalWeight) {
							pickedPixel = possiblePixel;
							break;
						}
					}
					if (pickedPixel == null) {
						throw new RuntimeException("fix your prob code, dumdum");
					}
					img[i][j] = pickedPixel;
				}
			}

			// generateRandom(troutchain, generator, img);
			// generateRandomWalk(troutchain, generator, img);
			// generateDiagonalStripesConcat(troutchain, generator, img);
			generateDiagonalStripesConcatRemoveNonCommon(troutchain, generator, img, generalProb);

			// generateDiagonalStripes(probs, generator, img);

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

	private void generateRandom(Map<RGB, Map<RGB, Long>> troutchain, Random generator, RGB[][] img) {
		// now that we have a weighted, randomly seeded img, pick a random
		// pixel and pick it's color based on all 8 neighbors
		for (int iter = 0; iter < (WIDTH * HEIGHT) * 10; iter++) {
			// get a random position excluding the image border
			int x = generator.nextInt(WIDTH - 2) + 1;
			int y = generator.nextInt(HEIGHT - 2) + 1;
			RGB pickedPixel = null;
			// concat count maps of all neighbors
			Map<RGB, Long> concatMap = new HashMap<>();
			// for each x pos
			for (int i = -1; i < 2; i++) {
				for (int j = -1; j < 2; j++) {
					// don't count ourself
					if (i != 0 && j != 0) {
						// get the map
						Map<RGB, Long> tempMap = troutchain.get(img[x - i][y - j]);
						// concat to our map
						for (Entry<RGB, Long> targetCount : tempMap.entrySet()) {
							if (concatMap.containsKey(targetCount.getKey())) {
								concatMap.put(targetCount.getKey(), concatMap.get(targetCount.getKey()) + targetCount.getValue());
							} else {
								concatMap.put(targetCount.getKey(), targetCount.getValue());
							}
						}
					}
				}
			}

			// now we have a concatenated map of counts, pick what color
			// we're gonna be!
			Map<RGB, Double> singleChance = new HashMap<>();
			Long singleCount = 0L;
			for (Long numCounts : concatMap.values()) {
				singleCount += numCounts;
			}
			for (RGB chancePixel : concatMap.keySet()) {
				singleChance.put(chancePixel, concatMap.get(chancePixel).doubleValue() / singleCount.doubleValue());
			}
			Double totalWeight = 0.0;
			for (RGB possiblePixel : singleChance.keySet()) {
				totalWeight += singleChance.get(possiblePixel);
				if (generator.nextDouble() <= totalWeight) {
					pickedPixel = possiblePixel;
					break;
				}
			}
			if (pickedPixel == null) {
				throw new RuntimeException("fix your prob code dumbutt");
			}
			img[x][y] = pickedPixel;
		}
	}

	private void generateRandomNormalize(Map<RGB, Map<RGB, Long>> troutchain, Random generator, RGB[][] img) {
		// now that we have a weighted, randomly seeded img, pick a random
		// pixel and pick it's color based on all 8 neighbors
		for (int iter = 0; iter < (WIDTH * HEIGHT) * 10; iter++) {
			// get a random position excluding the image border
			int x = generator.nextInt(WIDTH - 2) + 1;
			int y = generator.nextInt(HEIGHT - 2) + 1;
			RGB pickedPixel = null;
			// concat count maps of all neighbors
			Map<RGB, Long> concatMap = new HashMap<>();
			// for each x pos
			for (int i = -1; i < 2; i++) {
				for (int j = -1; j < 2; j++) {
					// don't count ourself
					if (i != 0 && j != 0) {
						// get the map
						Map<RGB, Long> tempMap = troutchain.get(img[x - i][y - j]);
						// concat to our map
						for (Entry<RGB, Long> targetCount : tempMap.entrySet()) {
							if (concatMap.containsKey(targetCount.getKey())) {
								concatMap.put(targetCount.getKey(), concatMap.get(targetCount.getKey()) + targetCount.getValue());
							} else {
								concatMap.put(targetCount.getKey(), targetCount.getValue());
							}
						}
					}
				}
			}

			// now we have a concatenated map of counts, pick what color
			// we're gonna be!
			Map<RGB, Double> singleChance = new HashMap<>();
			Long singleCount = 0L;
			for (Long numCounts : concatMap.values()) {
				singleCount += numCounts;
			}
			for (RGB chancePixel : concatMap.keySet()) {
				singleChance.put(chancePixel, concatMap.get(chancePixel).doubleValue() / singleCount.doubleValue());
			}
			Double totalWeight = 0.0;
			for (RGB possiblePixel : singleChance.keySet()) {
				totalWeight += singleChance.get(possiblePixel);
				if (generator.nextDouble() <= totalWeight) {
					pickedPixel = possiblePixel;
					break;
				}
			}
			if (pickedPixel == null) {
				throw new RuntimeException("fix your prob code dumbutt");
			}
			img[x][y] = pickedPixel;
		}
	}

	private void generateRandomWalk(Map<RGB, Map<RGB, Long>> troutchain, Random generator, RGB[][] img) {
		// now that we have a weighted, randomly seeded img, pick a random
		// pixel and pick it's color based on all 8 neighbors
		int lastx = 1;
		int lasty = 1;
		for (int iter = 0; iter < (WIDTH * HEIGHT) * 10; iter++) {
			// get a random pixel next to our last one
			int x = lastx + generator.nextInt(3) - 1;
			int y = lasty + generator.nextInt(3) - 1;
			if (x <= 0 || y <= 0 || x >= WIDTH - 1 || y >= HEIGHT - 1) {
				// don't go out of bounds
				continue;
			}
			lastx = x;
			lasty = y;
			RGB pickedPixel = null;
			// concat count maps of all neighbors
			Map<RGB, Long> concatMap = new HashMap<>();
			// for each x pos
			for (int i = -1; i < 2; i++) {
				for (int j = -1; j < 2; j++) {
					// don't count ourself
					if (i != 0 && j != 0) {
						// get the map
						Map<RGB, Long> tempMap = troutchain.get(img[x - i][y - j]);
						// concat to our map
						for (Entry<RGB, Long> targetCount : tempMap.entrySet()) {
							if (concatMap.containsKey(targetCount.getKey())) {
								concatMap.put(targetCount.getKey(), concatMap.get(targetCount.getKey()) + targetCount.getValue());
							} else {
								concatMap.put(targetCount.getKey(), targetCount.getValue());
							}
						}
					}
				}
			}

			// now we have a concatenated map of counts, pick what color
			// we're gonna be!
			Map<RGB, Double> singleChance = new HashMap<>();
			Long singleCount = 0L;
			for (Long numCounts : concatMap.values()) {
				singleCount += numCounts;
			}
			for (RGB chancePixel : concatMap.keySet()) {
				singleChance.put(chancePixel, concatMap.get(chancePixel).doubleValue() / singleCount.doubleValue());
			}
			Double totalWeight = 0.0;
			for (RGB possiblePixel : singleChance.keySet()) {
				totalWeight += singleChance.get(possiblePixel);
				if (generator.nextDouble() <= totalWeight) {
					pickedPixel = possiblePixel;
					break;
				}
			}
			if (pickedPixel == null) {
				throw new RuntimeException("fix your prob code dumbutt");
			}
			img[x][y] = pickedPixel;
		}
	}

	private void generateDiagonalStripes(Map<RGB, Map<RGB, Double>> probs, Random generator, RGB[][] img) {
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
	}

	private void generateDiagonalStripesConcat(Map<RGB, Map<RGB, Long>> troutchain, Random generator, RGB[][] img) {
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
					RGB pickedPixel = null;
					Map<RGB, Double> concatChance = new HashMap<>();
					// concat to our map

					// to normalize chances, need to make each countmap count
					// for 1/3rd max
					// TODO get totalcount from all 3 count maps
					// get totalcount from each individual map
					//
					// JUST CONCAT CHANCE MAPS * .333333

					// calcualte each chance map
					Map<RGB, Double> leftChance = new HashMap<>();
					Long leftCount = 0L;
					for (Long numCounts : troutchain.get(img[i - 1][j]).values()) {
						leftCount += numCounts;
					}
					for (RGB chancePixel : troutchain.get(img[i - 1][j]).keySet()) {
						leftChance.put(chancePixel, (troutchain.get(img[i - 1][j]).get(chancePixel).doubleValue() / leftCount.doubleValue()) * (1.0 / 3.0));
					}
					concatChance.putAll(leftChance);

					Map<RGB, Double> topleftChance = new HashMap<>();
					Long topleftCount = 0L;
					for (Long numCounts : troutchain.get(img[i - 1][j - 1]).values()) {
						topleftCount += numCounts;
					}
					for (RGB chancePixel : troutchain.get(img[i - 1][j - 1]).keySet()) {
						topleftChance.put(chancePixel, (troutchain.get(img[i - 1][j - 1]).get(chancePixel).doubleValue() / topleftCount.doubleValue()) * (1.0 / 3.0));
					}
					for (Entry<RGB, Double> chance : topleftChance.entrySet()) {
						if (concatChance.containsKey(chance.getKey())) {
							concatChance.put(chance.getKey(), concatChance.get(chance.getKey()) + chance.getValue());
						} else {
							concatChance.put(chance.getKey(), chance.getValue());
						}
					}

					Map<RGB, Double> topChance = new HashMap<>();
					Long topCount = 0L;
					for (Long numCounts : troutchain.get(img[i][j - 1]).values()) {
						topCount += numCounts;
					}
					for (RGB chancePixel : troutchain.get(img[i][j - 1]).keySet()) {
						topChance.put(chancePixel, (troutchain.get(img[i][j - 1]).get(chancePixel).doubleValue() / topCount.doubleValue()) * (1.0 / 3.0));
					}
					for (Entry<RGB, Double> chance : topChance.entrySet()) {
						if (concatChance.containsKey(chance.getKey())) {
							concatChance.put(chance.getKey(), concatChance.get(chance.getKey()) + chance.getValue());
						} else {
							concatChance.put(chance.getKey(), chance.getValue());
						}
					}

					// now we have a concatenated map of counts, pick what color
					// we're gonna be!
					// concat our 3 chance maps
					Double totalWeight = 0.0;
					for (RGB possiblePixel : concatChance.keySet()) {
						totalWeight += concatChance.get(possiblePixel);
						if (generator.nextDouble() <= totalWeight) {
							pickedPixel = possiblePixel;
							break;
						}
					}
					// System.out.println("For point: " + i + ", " + j);
					// System.out.println("My neighbors were: " + img[i - 1][j]
					// + ", " + img[i - 1][j - 1] + ", " + img[i][j - 1]);
					// System.out.println("My chances are: " +
					// concatChance.toString());
					// System.out.println("I picked: " +
					// pickedPixel.toString());

					if (pickedPixel == null) {
						throw new RuntimeException("fix your probability code, dummy");
					}
					img[i][j] = pickedPixel;
				}
			}
		}
	}

	private void generateDiagonalStripesConcatRemoveNonCommon(Map<RGB, Map<RGB, Long>> troutchain, Random generator, RGB[][] img, Map<RGB, Double> generalProb) {
		int noshared = 0;
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
					RGB pickedPixel = null;
					Map<RGB, Double> concatChance = new HashMap<>();
					// concat to our map

					// to normalize chances, need to make each countmap count
					// for 1/3rd max
					// TODO get totalcount from all 3 count maps
					// get totalcount from each individual map
					//
					// JUST CONCAT CHANCE MAPS * .333333

					// remove colors that aren't shared by all 3
					Set<RGB> leftColors = new HashSet<>(troutchain.get(img[i - 1][j]).keySet());
					Set<RGB> topColors = new HashSet<>(troutchain.get(img[i][j - 1]).keySet());
					Set<RGB> topleftColors = new HashSet<>(troutchain.get(img[i - 1][j - 1]).keySet());
					Set<RGB> sharedColors = new HashSet<>();
					sharedColors.addAll(topColors);
					sharedColors.retainAll(topleftColors);
					sharedColors.retainAll(leftColors);
					if (sharedColors.isEmpty()) {
						// our neighbors don't share any colors, just pick a
						// rando I guess
						Double totalWeight = 0.0;
						for (RGB possiblePixel : generalProb.keySet()) {
							totalWeight += generalProb.get(possiblePixel);
							if (generator.nextDouble() <= totalWeight) {
								pickedPixel = possiblePixel;
								break;
							}
						}
						noshared++;
					} else {

						// calculate each chance map
						Map<RGB, Double> leftChance = new HashMap<>();
						Long leftCount = 0L;
						for (Entry<RGB,Long> numCounts: troutchain.get(img[i - 1][j]).entrySet()) {
							if (sharedColors.contains(numCounts.getKey())) {
								leftCount += numCounts.getValue();
							}
						}
						for (Entry<RGB,Long> chancePixel : troutchain.get(img[i - 1][j]).entrySet()) {
							if (sharedColors.contains(chancePixel.getKey())) {
								leftChance.put(chancePixel.getKey(), (chancePixel.getValue().doubleValue() / leftCount.doubleValue()) * (1.0 / 3.0));
							}
						}
						concatChance.putAll(leftChance);
						
						Map<RGB, Double> topleftChance = new HashMap<>();
						Long topleftCount = 0L;
						for (Entry<RGB,Long> numCounts : troutchain.get(img[i - 1][j - 1]).entrySet()) {
							if (sharedColors.contains(numCounts.getKey())) {
								topleftCount += numCounts.getValue();
							}
						}
						for (Entry<RGB,Long> chancePixel : troutchain.get(img[i - 1][j - 1]).entrySet()) {
							if (sharedColors.contains(chancePixel.getKey())) {
								topleftChance.put(chancePixel.getKey(), (chancePixel.getValue().doubleValue() / topleftCount.doubleValue()) * (1.0 / 3.0));
							}
						}
						for (Entry<RGB, Double> chance : topleftChance.entrySet()) {
							if (concatChance.containsKey(chance.getKey())) {
								concatChance.put(chance.getKey(), concatChance.get(chance.getKey()) + chance.getValue());
							} else {
								concatChance.put(chance.getKey(), chance.getValue());
							}
						}

						Map<RGB, Double> topChance = new HashMap<>();
						Long topCount = 0L;
						for (Entry<RGB,Long> numCounts : troutchain.get(img[i][j - 1]).entrySet()) {
							if (sharedColors.contains(numCounts.getKey())) {
								topCount += numCounts.getValue();
							}
						}
						for (Entry<RGB, Long> chancePixel : troutchain.get(img[i][j - 1]).entrySet()) {
							if (sharedColors.contains(chancePixel.getKey())) {
								topChance.put(chancePixel.getKey(), (chancePixel.getValue().doubleValue() / topCount.doubleValue()) * (1.0 / 3.0));
							}
						}
						for (Entry<RGB, Double> chance : topChance.entrySet()) {
							if (concatChance.containsKey(chance.getKey())) {
								concatChance.put(chance.getKey(), concatChance.get(chance.getKey()) + chance.getValue());
							} else {
								concatChance.put(chance.getKey(), chance.getValue());
							}
						}

						// now we have a concatenated map of counts, pick what
						// color
						// we're gonna be!
						// concat our 3 chance maps
						Double totalWeight = 0.0;
						for (RGB possiblePixel : concatChance.keySet()) {
							totalWeight += concatChance.get(possiblePixel);
							if (generator.nextDouble() <= totalWeight) {
								pickedPixel = possiblePixel;
								break;
							}
						}
//						System.out.println("For point: " + i + ", " + j);
//						System.out.println("My neighbors were: " + img[i - 1][j] + ", " + img[i - 1][j - 1] + ", " + img[i][j - 1]);
//						System.out.println("My chances are: " + concatChance.toString());
//						System.out.println("I picked: " + pickedPixel.toString());
					}
					if (pickedPixel == null) {
						Double totalChance = 0.0;
						for (Double chance : concatChance.values()) {
							totalChance += chance;
						}
						System.out.println("Had a chance total of: " + totalChance);
						System.out.println(noshared + " pixels had no colors in common");
						System.out.println("Shared colors: " + sharedColors.toString());
						System.out.println("For point: " + i + ", " + j);
						System.out.println("My neighbors were: " + img[i - 1][j] + ", " + img[i - 1][j - 1] + ", " + img[i][j - 1]);
						System.out.println("My chances are: " + concatChance.toString());
						throw new RuntimeException("fix your probability code, dummy");
					}
					img[i][j] = pickedPixel;
				}
			}
		}
		System.out.println(noshared + " pixels had no colors in common");
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
