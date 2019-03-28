package edu.cg;


import java.awt.Color;
import java.awt.image.BufferedImage;

public class SeamsCarver extends ImageProcessor {

    // MARK: An inner interface for functional programming.
    @FunctionalInterface
    interface ResizeOperation {

        BufferedImage resize();
    }

    // MARK: Fields
    private int numOfSeams;
    private ResizeOperation resizeOp;
    private boolean[][] imageMask;
    private int[][] greyScaledImageMatrix;
    private int[][] energyMatrix = new int[inHeight][inWidth];
    private int[][] mappingMatrix = new int[inHeight][inWidth];
    private long[][] costMatrix;
    private boolean[][] shiftedMask;
    private int currentSeam;
    private int[][] seams = new int[inHeight][inWidth];


    public SeamsCarver(Logger logger, BufferedImage workingImage, int outWidth, RGBWeights rgbWeights, boolean[][] imageMask) {

        super((s) -> logger.log("Seam carving: " + s), workingImage, rgbWeights, outWidth, workingImage.getHeight());

        numOfSeams = Math.abs(outWidth - inWidth);

        this.imageMask = imageMask;

        if (inWidth < 2 | inHeight < 2)
            throw new RuntimeException("Can not apply seam carving: workingImage is too small");

        if (numOfSeams > inWidth / 2)
            throw new RuntimeException("Can not apply seam carving: too many seams...");

        // Setting resizeOp by with the appropriate method reference
        if (outWidth > inWidth)
            resizeOp = this::increaseImageWidth;
        else if (outWidth < inWidth)
            resizeOp = this::reduceImageWidth;
        else
            resizeOp = this::duplicateWorkingImage;

        logger.log("begins preliminary calculations.");

        // Make this part only if need to remove \ add seams to original picture
        if (numOfSeams > 0) {
            logger.log("Seam carving: initializes some additional fields.");
            generateGreyscaleImage();
            shiftedMask = duplicateMask();
            initEnergyMatrix();
            initMappingMatrix();
            calculateSeams();
        }

        logger.log("preliminary calculations were ended.");
    }

    private boolean[][] duplicateMask() {
        boolean[][] dup = new boolean[inHeight][inWidth];
        forEach((y, x) -> dup[y][x] = imageMask[y][x]);
        return dup;
    }

    private void generateGreyscaleImage() {
        greyScaledImageMatrix = new int[inHeight][inWidth];
        BufferedImage greyScaledImage = greyscale();
        forEach((y, x) -> {
            int grey = new Color(greyScaledImage.getRGB(x, y)).getRed();
            greyScaledImageMatrix[y][x] = grey;
        });
    }

    private void initEnergyMatrix() {
        forEach((y, x) -> {
            int xNeighbor = (x < (inWidth - 1)) ? (x + 1) : (x - 1);
            int yNeighbor = (y < (inHeight - 1)) ? (y + 1) : (y - 1);
            int maskedAddition = shiftedMask[y][x] ? Integer.MAX_VALUE : 0;
            int derX = derivativeCalculate(y, x, y, xNeighbor);
            int derY = derivativeCalculate(y, x, yNeighbor, x);
            if (maskedAddition != 0) {
                energyMatrix[y][x] = maskedAddition;
            } else {
                energyMatrix[y][x] = derX + derY;
            }
        });
    }

    // initialize the mapping table for the energy matrix
    private void initMappingMatrix() {
        logger.log("Seam carving: creates a 2D matrix of original \"x\" indices.");
        forEach((y, x) -> mappingMatrix[y][x] = x);
    }

    private void calculateSeams() {
        logger.log("Seam carving: finds the " + numOfSeams + " minimal seams.");
        for (currentSeam = 0; currentSeam < numOfSeams; currentSeam++) {
            initCostMatrix();
            logger.log("Seam carving: finds seam no: " + (currentSeam + 1) + ".");
            backTrackSeam();

        }
    }

    private void initCostMatrix() {
        logger.log("calculates the costs matrix \"m\".");
        costMatrix = new long[inHeight][inWidth - currentSeam];

        initFirstRow();
        forEach((y, x) -> {
            if (y > 0) costMatrix[y][x] = calculateCellCost(y, x);
        });
//		System.out.print("cost matrix print");
        //printMatrix(costMatrix);

    }

    private void initFirstRow() {
        setForEachWidth(inWidth - currentSeam);
        forEachWidth((x) -> {
            costMatrix[0][x] = energyMatrix[0][mappingMatrix[0][x]];
        });
    }

    private void backTrackSeam() {
        logger.log("looking for the \"x\" index of the bottom row that holds the minimal cost.");
        int minIndexAtBottom = findMinAtBottom();
        logger.log("constructs the path of the minimal seam.");
        logger.log("stores the path.");
        findSeam(minIndexAtBottom);
        logger.log("removes the seam.");
    }

    private void findSeam(int prevMinIndex) {
        int minX = prevMinIndex;
        seams[inHeight - 1][mappingMatrix[inHeight - 1][minX]] = 1;
//		System.out.print(mappingMatrix[inHeight-1][minX] + "->");

        for (int i = inHeight - 2; i >= 0; i--) {
            long currentValue = costMatrix[i + 1][minX];
            prevMinIndex = minX;
            //the current x came from the upper pixel
            if (minX != 0 && minX != (inWidth - currentSeam - 1) &&
                    currentValue == energyMatrix[i + 1][mappingMatrix[i + 1][minX]] + costMatrix[i][minX] + costV(i + 1, minX)) {
                seams[i][mappingMatrix[i][minX]] = 1;
            } else {
                if (minX == 0) {
                    seams[i][mappingMatrix[i][++minX]] = 1;
                } else if (minX == inWidth - currentSeam - 1) {
                    seams[i][mappingMatrix[i][--minX]] = 1;
                } else {
                    if (currentValue == energyMatrix[i + 1][mappingMatrix[i + 1][minX]] + costMatrix[i][minX - 1] + costL(i + 1, minX)) {
                        seams[i][mappingMatrix[i][--minX]] = 1;
                    } else {
                        seams[i][mappingMatrix[i][++minX]] = 1;
                    }
                }
            }

//			System.out.print(mappingMatrix[i][minX] + "->");
            shiftLeft(i + 1, prevMinIndex);
        }
        shiftLeft(0, minX);
//		System.out.println();
//		System.out.print("mapping matrix print");
//		printMatrix(mappingMatrix);
//		System.out.print("seams matrix print");
//		printMatrix(seams);
    }

    private int findMinAtBottom() {
        int minIndex = 0;
        for (int x = 0; x < inWidth - currentSeam; x++) {
            if (costMatrix[inHeight - 1][x] < costMatrix[inHeight - 1][minIndex]) {
                minIndex = x;
            }
        }
        logger.log("minX = " + minIndex + ".");

        return minIndex;
    }

    private void shiftLeft(int y, int seamXindex) {
        for (int i = seamXindex; i < inWidth - currentSeam - 1; i++) {
            mappingMatrix[y][i] = mappingMatrix[y][i + 1];
            shiftedMask[y][i] = shiftedMask[y][i + 1];
        }
        mappingMatrix[y][inWidth - currentSeam - 1] = Integer.MAX_VALUE;
    }

    private long calculateCellCost(int y, int x) {

        int currentWidth = inWidth - currentSeam - 1;
        long costV = costV(y, x);
        long costMatrixV = costMatrix[y - 1][x];


        if (x == 0) { // first column
            long costR = costR(y, x);
            long costMatrixR = costMatrix[y - 1][x + 1];
            return energyMatrix[y][mappingMatrix[y][0]] + Math.min(costMatrixR + costR, costMatrixV + costV);

        } else {

            if (x != currentWidth) {
                long costR = costR(y, x);
                long costL = costL(y, x);
                long costMatrixR = costMatrix[y - 1][x + 1];
                long costMatrixL = costMatrix[y - 1][x - 1];
                return energyMatrix[y][mappingMatrix[y][x]] + Math.min(Math.min(costMatrixR + costR, costMatrixV + costV), costMatrixL + costL);

            } else { // last column
                long costL = costL(y, x);
                long costMatrixL = costMatrix[y - 1][x - 1];
                return energyMatrix[y][mappingMatrix[y][x]] + Math.min(costMatrixL + costL, costMatrixV + costV);
            }
        }
    }

    private int derivativeCalculate(int y2, int x2, int y1, int x1) {
        return Math.abs(greyScaledImageMatrix[y2][x2] - greyScaledImageMatrix[y1][x1]);
    }

    private int costR(int y, int x) {
        return (costV(y, x) + derivativeCalculate(y - 1, x, y, x + 1));
    }

    private int costV(int y, int x) {
        if (x == 0 || x == inWidth - currentSeam - 1) {
            return 255;
        }

        return derivativeCalculate(y, x + 1, y, x - 1);
    }

    private int costL(int y, int x) {
        return (costV(y, x) + derivativeCalculate(y - 1, x, y, x - 1));
    }

    public BufferedImage resize() {
        return resizeOp.resize();
    }

    private BufferedImage reduceImageWidth() {

        logger.log("reduces image width by " + numOfSeams + " pixels.");

        BufferedImage reducedImg = newEmptyOutputSizedImage();
        setForEachOutputParameters();

        forEach((y, x) -> {
            reducedImg.setRGB(x, y, workingImage.getRGB(mappingMatrix[y][x], y));
        });

        return reducedImg;

    }

    private BufferedImage increaseImageWidth() {
        logger.log("increases image width by + " + numOfSeams + " pixels.");
        BufferedImage increasedImg = newEmptyOutputSizedImage();
        int[][] indexMatrix = new int[inHeight][outWidth];
        boolean seen = false;
        int seamSeen = 0;        //the number of seams found this far
        //calculate the index matrix that we need to increase
        for (int y = 0; y < inHeight; y++) {
            for (int x = 0; x < inWidth; x++) {
                if (seams[y][x] == 1) {
                    if(numOfSeams != 0)  seamSeen++;
                    indexMatrix[y][x + 1] = indexMatrix[y][x];
                    indexMatrix[y][x + seamSeen] = x;
                    if(numOfSeams == 0)  seamSeen++;
                }
                else{
                    indexMatrix[y][x + seamSeen] = x;
                }
            }
            //start new row, search from the beginning of the row for the number of seams seen yet
            seamSeen = 0;
        }


        //set the new increased picture to the index matrix of the increased picture we calculated earlier
        setForEachOutputParameters();
        forEach((y, x) -> {
            increasedImg.setRGB(x, y, workingImage.getRGB(indexMatrix[y][x], y));
        });


        return increasedImg;
    }

    public BufferedImage showSeams(int seamColorRGB) {
        // TODO: Implement this method (bonus), remove the exception.
        BufferedImage img = workingImage;
        for (int j = 0; j < seams.length; j++) {
            for (int i = 0; i < seams[0].length; i++) {
                if (seams[j][i] == 1) {
                    img.setRGB(i, j, seamColorRGB);
                }
            }
        }
        forEach((y, x) -> {

        });

        return img;

//		throw new UnimplementedMethodException("showSeams");
    }

    public boolean[][] getMaskAfterSeamCarving() {
        boolean[][] newMask = new boolean[inHeight][outWidth];
        if (shiftedMask != null) {
            for (int j = 0; j < newMask.length; j++) {
                for (int i = 0; i < newMask[0].length; i++) {
                    if (i >= inWidth) {
                        newMask[j][i] = false;
                    } else {
                        newMask[j][i] = shiftedMask[j][i];
                    }
                }
            }
        } else {
            newMask = imageMask;
        }

        return newMask;
    }

    private int[][] imageToMatrix() {
        int[][] img = new int[inHeight][inWidth];
        forEach((y, x) -> {
            img[y][x] = workingImage.getRGB(y, x);
        });
        return img;
    }

    void printImageAsMatrix(BufferedImage image) {
        // TODO - delete this method
        System.out.println("-----------------------------");
        for (int i = 0; i < image.getHeight(); i++) {
            for (int j = 0; j < image.getWidth(); j++) {
                System.out.format("%3d", image.getRGB(j, i));
            }
            System.out.println();
        }
    }

    void printMatrix(long[][] image) {
        // TODO - delete this method
        System.out.println("-----------------------------");
        for (int i = 0; i < image.length; i++) {
            for (int j = 0; j < image[0].length; j++) {
                if (image[i][j] == Integer.MAX_VALUE) {
                    System.out.print("\u001B[31m" + " inf " + "\u001B[0m");
                } else {
                    if (image[i][j] < 0)
                        System.out.print("\u001B[34m" + "[" + i + "," + j + "]\u001B[0m");
                    else
                        System.out.format("%3d", image[i][j]);
                }
            }
            System.out.println();
        }
    }

    void printMatrix(int[][] image) {
        // TODO - delete this method
        System.out.println("-----------------------------");
        for (int i = 0; i < image.length; i++) {
            for (int j = 0; j < image[0].length; j++) {
                if (image[i][j] == Integer.MAX_VALUE) {
                    System.out.print("\u001B[31m" + " inf " + "\u001B[0m");
                } else {
                    if (image[i][j] < 0)
                        System.out.print("\u001B[34m" + "[" + i + "," + j + "]\u001B[0m");
                    else
                        System.out.format("%3d", image[i][j]);
                }
            }
            System.out.println();
        }
    }

    void printSeamMatrix(boolean[][] image) {
        // TODO - delete this method
        System.out.println("-----------------------------");
        for (int i = 0; i < image.length; i++) {
            for (int j = 0; j < image[0].length; j++) {
                if (image[i][j]) {
                    System.out.print("\u001B[34m" + "T " + "\u001B[0m");
                } else {
                    System.out.print("F ");
                }

            }
            System.out.println();
        }
    }

}
