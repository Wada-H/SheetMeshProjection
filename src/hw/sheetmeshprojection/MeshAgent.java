package hw.sheetmeshprojection;


// cross section IamgegePlusとMeshMapの調停

import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.process.FloatPolygon;

import java.awt.geom.Point2D;
import java.util.ArrayList;

public class MeshAgent {

    ImagePlus mainImage;
    MeshMap<Double[]> mainMeshMap;
    MeshManager meshManager;

    public MeshAgent(ImagePlus imp, MeshMap<Double[]> meshMap){
        mainImage = imp;
        mainMeshMap = meshMap;
        meshManager = new MeshManager(mainMeshMap);

    }


    public void displayROI(int index, String rowORcol){

        PolygonRoi pROI;
        if(rowORcol == "row"){
            pROI = this.createROIbyRow(index);
            mainImage.setRoi(pROI);
        }else if(rowORcol == "col"){
            pROI = this.createROIbyCol(index);
            mainImage.setRoi(pROI);
        }

    }


    public PolygonRoi createROIbyCol(int index){
        FloatPolygon buffXY = meshManager.getColumnByFloatPolygon(index);

        PolyLineManager polyLineManager = new PolyLineManager(buffXY);
        ArrayList<Double> lengthList = new ArrayList<>();

        double accumLength = 0.0;
        lengthList.add(accumLength); //最初の点は0始まり
        for(int i = 0; i < buffXY.npoints -1; i++){
            Point2D s = new Point2D.Float(buffXY.xpoints[i], buffXY.ypoints[i]);
            Point2D e = new Point2D.Float(buffXY.xpoints[i + 1], buffXY.ypoints[i + 1]);
            accumLength = accumLength + polyLineManager.getSegmentAsFloatPolygon(s, e).getLength(true);
            if(accumLength > (mainImage.getHeight() - 1)){
                accumLength = mainImage.getHeight() - 1;
            }

            lengthList.add(accumLength);
        }


        FloatPolygon buffYZ = meshManager.getYZpolygon(index);

        float[] xpoints = new float[buffYZ.npoints];
        float[] ypoints = new float[buffYZ.npoints];

        for(int i = 0; i < buffYZ.npoints; i++){
            xpoints[i] = buffYZ.ypoints[i];
            ypoints[i] = lengthList.get(i).floatValue();
            System.out.println("MeshAgent Col: " + xpoints[i] + ", " + ypoints[i]);
        }

        PolygonRoi result = new PolygonRoi(xpoints, ypoints, PolygonRoi.POLYLINE);
        result.fitSpline();
        return result;
    }



    public PolygonRoi createROIbyRow(int index){
        FloatPolygon buffXY = meshManager.getRowByFloatPolygon(index);
        PolyLineManager polyLineManager = new PolyLineManager(buffXY);
        ArrayList<Double> lengthList = new ArrayList<>();

        double accumLength = 0.0;
        lengthList.add(accumLength); //最初の点は0始まり
        for(int i = 0; i < buffXY.npoints -1; i++){
            Point2D s = new Point2D.Float(buffXY.xpoints[i], buffXY.ypoints[i]);
            Point2D e = new Point2D.Float(buffXY.xpoints[i + 1], buffXY.ypoints[i + 1]);
            accumLength = accumLength + polyLineManager.getSegmentAsFloatPolygon(s, e).getLength(true);
            if(accumLength > (mainImage.getWidth() - 1)){
                accumLength = mainImage.getWidth() - 1;
            }
            lengthList.add(accumLength);
        }


        FloatPolygon buffXZ = meshManager.getXZpolygon(index);
        //FloatPolygon buff = meshManager.getInitializedXZpolygon(index);

        float[] xpoints = new float[buffXZ.npoints];
        float[] ypoints = new float[buffXZ.npoints];

        for(int i = 0; i < buffXZ.npoints; i++){
            xpoints[i] = lengthList.get(i).floatValue();
            ypoints[i] = buffXZ.ypoints[i];
        }

        PolygonRoi result = new PolygonRoi(xpoints, ypoints, PolygonRoi.POLYLINE);
        result.fitSpline();
        return result;

    }

    public void setROItoMeshMapByCol(int index, double offset){
        PolygonRoi pr = (PolygonRoi) mainImage.getRoi();
        pr.removeSplineFit();
        meshManager.setYZvalues(index, pr.getFloatPolygon(), offset);
    }


    public void setROItoMeshMapByRow(int index, double offset){
        PolygonRoi pr = (PolygonRoi) mainImage.getRoi();
        pr.removeSplineFit();
        meshManager.setXZvalues(index, pr.getFloatPolygon(), offset);
    }

    public void commit(){ //現在表示中のROIをMeshMapに反映させる

    }
}
