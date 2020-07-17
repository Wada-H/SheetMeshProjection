package hw.sheetmeshprojection;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.util.ArrayList;



/**
 * Find all z positions using MeshMap <Double []> {x, y, z} based on the input image.
 * Judgment based on the average value of 3x3 area centered on each point (total of each channel).
 *
 * MeshMap<Double[]> {x, y, z}において、すべてのzの位置を画像より計算する。
 * 各点を中心とした3x3の領域の平均値(各Channelも合算)で判断
 *
 * @author WADA Housei
 */
// 今後の変更点として各チャンネルでの指定も可能にしたい //

public class Fitter {

    ImagePlus mainImage; //xycz画像
    MeshMap<Double[]> meshMap;

    //scale//
    double thickness;

    // threshold //
    double threshold = 5.0; //最大輝度のパーセント, これも選択できるようにする？
    double tValue;

    public Fitter(ImagePlus img, MeshMap<Double[]> mmap){

        mainImage = img;
        meshMap = mmap;

        double xScale = img.getCalibration().pixelWidth;
        double zScale = img.getCalibration().pixelDepth;
        double az = zScale / xScale;
        if(az == 0){
            az = 1;
        }
        thickness = az;
        tValue = mainImage.getDisplayRangeMax() * (threshold / 100);
        //System.out.println("thickness, tValue = " + thickness + ", " + tValue);

    }

    public void setThreshold(double t){
        threshold = t;
        tValue = mainImage.getDisplayRangeMax() * (threshold / 100);
    }

    public void fitZpositoin(){

        meshMap.mainMap.parallelStream().forEach(yList ->{
            yList.parallelStream().forEach(doubleArray ->{
                ArrayList<Double> intensityArray = this.get3x3AreaIntensity(doubleArray[0], doubleArray[1]);
                float[] floatIntesity = new float[intensityArray.size()];
                for(int i = 0; i < intensityArray.size(); i++){
                    floatIntesity[i] = intensityArray.get(i).floatValue();
                }

                ImageProcessor ip = new FloatProcessor(intensityArray.size(), 1, floatIntesity);
                ImageProcessor resizedIp = ip.resize((int)Math.round(ip.getWidth() * thickness), 1);
                resizedIp.blurGaussian((thickness * 0.4));

                for(int i = 0; i < resizedIp.getWidth(); i++){
                    doubleArray[2] = (double)(i); //+1する必要があるか？
                    if(resizedIp.getInterpolatedValue(i, 0) > tValue){
                        break;
                    }
                }

            });
        });


    }


    public ArrayList<Double> get3x3AreaIntensity(double ox, double oy){
        ArrayList<Double> result = new ArrayList<>();
        double sx = ox - 1;
        double sy = oy - 1;
        double ex = ox + 1;
        double ey = oy + 1;

        if(sx < 0) sx = 0;
        if(sy < 0) sy = 0;
        if(ex >= mainImage.getWidth()) ex = mainImage.getWidth() -1;
        if(ey >= mainImage.getHeight()) ey = mainImage.getHeight() -1;


        for(int z = 0; z < mainImage.getNSlices(); z++){
            double buff = 0;
            for(int c = 0; c < mainImage.getNChannels(); c++){
                int index = mainImage.getStackIndex(c+1, z+1, 1);

                for(double x = sx; x < ex + 1; x++){
                    for(double y = sy; y < ey + 1; y++){
                        buff = buff + mainImage.getStack().getProcessor(index).getInterpolatedValue(x, y);
                    }
                }
            }
            result.add(buff / (9 * mainImage.getNChannels()));
        }


        return result;
    }


}
