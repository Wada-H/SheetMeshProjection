package hw.sheetmeshprojection;

import ij.ImagePlus;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;


/*
    1zのスケールが等倍変換されている前提 ->これも吸収するような記述が必要かも
 */

public class VoxelValueGetter {

    ImagePlus mainImage; //XYCZT画像とする

    void VoxelValueGetter(ImagePlus img){
        mainImage = img;
    }

    public double getValue(double x, double y, double c, double z, double t){
        double value = 0.0;
        return value;
    }

    public double getValue(double x, double y, double z){
        ImageProcessor.setUseBicubic(true);
        double value = 0.0;
        ImageProcessor ip = new FloatProcessor(mainImage.getNSlices(), 1);
        for(int i = 0; i < mainImage.getNSlices(); i++){
            int index = mainImage.getStackIndex(1, i + 1, 1);
            ip.setf(i+1, (float)mainImage.getStack().getProcessor(index).getInterpolatedValue(x, y));
        }

        value = ip.getInterpolatedValue(z, 1);

        return value;
    }
}
