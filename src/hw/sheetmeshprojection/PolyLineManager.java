package hw.sheetmeshprojection;

import ij.gui.Line;
import ij.gui.PolygonRoi;
import ij.process.FloatPolygon;
import java.awt.geom.Point2D;
import java.util.ArrayList;


/**
 * Process for FloatPolygon.
 * @author WADA Housei
 */

public class PolyLineManager {

    FloatPolygon mainPolygon;

    public PolyLineManager(FloatPolygon r){
        mainPolygon = r;
    }



    public FloatPolygon getSegmentAsFloatPolygon(int index){

        Point2D sPoint = new Point2D.Float(mainPolygon.xpoints[index], mainPolygon.ypoints[index]);
        Point2D ePoint = new Point2D.Float(mainPolygon.xpoints[index + 1], mainPolygon.ypoints[index + 1]);
        //System.out.println("sPoint vs ePoint" + sPoint + "  vs " + ePoint);
        return this.getSegmentAsFloatPolygon(sPoint, ePoint);
    }


    public FloatPolygon getSegmentAsFloatPolygon(Point2D s, Point2D e){ //s-eの間に含まれるspline化したときの座標を取得

        PolygonRoi forSpline = new PolygonRoi(mainPolygon.xpoints, mainPolygon.ypoints, PolygonRoi.POLYLINE);

        //System.out.println("XPOINT SIZE : " + mainPolygon.xpoints.length);
        // * infinity 対策 //
        //System.out.println("abs : " + Math.abs(forSpline.getFloatPolygon().xpoints[1] - forSpline.getFloatPolygon().xpoints[0]));
        if(Math.abs(forSpline.getFloatPolygon().xpoints[1] - forSpline.getFloatPolygon().xpoints[0]) > 1.0) {
            forSpline.fitSpline();
        }

        //System.out.println("PolyLineManage.getSegmentAsFloatPolygon : " + mainPolygon.npoints);
        FloatPolygon interpolatedPolygon = forSpline.getInterpolatedPolygon(1.0, false);//getFloatPolygonでは適当な位置に点が取られるため、1pixel毎欲しい場合はこっち。


        /*** 1pixel間隔にするということはもとの点と微小なズレが生じる。このため数値ではヒットしなくなってしまう。このため距離が一番近い点をindexとする。 ***/


        int sindex = this.getShortestIndex(interpolatedPolygon, s);
        int eindex = this.getShortestIndex(interpolatedPolygon, e);

        float[] xpoints = new float[Math.abs(eindex - sindex) + 1];
        float[] ypoints = new float[Math.abs(eindex - sindex) + 1];

        for(int i = sindex; i < eindex + 1; i++){
            xpoints[i - sindex] = interpolatedPolygon.xpoints[i];
            ypoints[i - sindex] = interpolatedPolygon.ypoints[i];
        }


        FloatPolygon result = new FloatPolygon(xpoints, ypoints);
        return result;
    }


    private int getShortestIndex(FloatPolygon roi, Point2D p){

        int index = 0;
        double length = Double.MAX_VALUE;
        for(int i = 0; i < roi.npoints; i++){
            double l = this.getDistance(p.getX(), p.getY(), roi.xpoints[i], roi.ypoints[i]);
            if(length > l){
                length = l;
                index = i;
            }
        }

        return index;
    }

    private double getDistance(double sx, double sy, double ex, double ey){
        double l = Math.sqrt(Math.pow((ex - sx), 2) + Math.pow((ey - sy),2));
        return l;
    }

    public FloatPolygon getSegmentAsFloatPolygon(int index, int pointNum){//指定の点数で返す
        Point2D sPoint = new Point2D.Float(mainPolygon.xpoints[index], mainPolygon.ypoints[index]);
        Point2D ePoint = new Point2D.Float(mainPolygon.xpoints[index + 1], mainPolygon.ypoints[index + 1]);
        FloatPolygon extructedPolygon =  this.getSegmentAsFloatPolygon(sPoint, ePoint);

        PolygonRoi buff = new PolygonRoi(extructedPolygon, PolygonRoi.POLYLINE);
        //buff.fitSpline();//なしでもいい気がするが。

        FloatPolygon interpolatedPolygon = buff.getInterpolatedPolygon(0.01, false);
        double stepSize = (interpolatedPolygon.npoints -1) / (double)(pointNum - 1);
        float[] xpoints = new float[pointNum];
        float[] ypoints = new float[pointNum];

        for(int i = 0; i < pointNum; i++){
            if(i == (pointNum - 1)){
                xpoints[i] = interpolatedPolygon.xpoints[interpolatedPolygon.npoints -1];
                ypoints[i] = interpolatedPolygon.ypoints[interpolatedPolygon.npoints -1];
            }else{
                int p = (int)(Math.round(i * stepSize));
                xpoints[i] = interpolatedPolygon.xpoints[p];
                ypoints[i] = interpolatedPolygon.ypoints[p];
            }

        }

        return new FloatPolygon(xpoints, ypoints);
    }


    public FloatPolygon getShiftedPolygon(float width){
        //一つ前のROIと次のROIの離れるまたは交差する部分について交点を求めればいけるとおもわれる
        ArrayList<FloatPolygon> fpArray = new ArrayList<>(mainPolygon.npoints);
        float[] xpoints = mainPolygon.xpoints;
        float[] ypoints = mainPolygon.ypoints;

        //マルチスレッド化可能
        for(int i = 0; i < mainPolygon.npoints - 1; i++){
            float x1 = xpoints[i];
            float y1 = ypoints[i];
            float x2 = xpoints[i+1];
            float y2 = ypoints[i+1];

            //System.out.println("original points : " + x1 + ", " + y1);
            FloatPolygon fp = this.getOutlineWithWidth(x1, y1, x2, y2, width);
            fpArray.add(i, fp);
        }

        float[] shiftedXpoints = new float[mainPolygon.npoints];
        float[] shiftedYpoints = new float[mainPolygon.npoints];

        // 1        4
        //  --------
        // |------->|
        //  --------
        // 2        3

        shiftedXpoints[0] = fpArray.get(0).xpoints[1];
        shiftedYpoints[0] = fpArray.get(0).ypoints[1];

        for(int i = 1; i < fpArray.size(); i++){

            double x1 = fpArray.get(i - 1).xpoints[1];
            double y1 = fpArray.get(i - 1).ypoints[1];

            double x2 = fpArray.get(i - 1).xpoints[2];
            double y2 = fpArray.get(i - 1).ypoints[2];

            double x3 = fpArray.get(i).xpoints[1];
            double y3 = fpArray.get(i).ypoints[1];

            double x4 = fpArray.get(i).xpoints[2];
            double y4 = fpArray.get(i).ypoints[2];


            Point2D crossPoint = this.calc4(x1, y1, x2, y2, x3, y3, x4, y4); //before 1.52t
            //Point2D crossPoint = this.calc4(x4, y4, x1, y1, x2, y2, x3, y3); //1.52u40 later *この対応では厳しいかも -> 1.53b33でLine.getFloatPolygonに修正（1.52tと同じなに変更)

            //System.out.println("shifted xz : " + crossPoint.getX() + ", " + crossPoint.getY());
            if(Double.isNaN(crossPoint.getX())) {
                shiftedXpoints[i] = shiftedXpoints[i - 1];
                shiftedYpoints[i] = shiftedYpoints[i - 1];
            }else if(Double.isInfinite(crossPoint.getX())){
                shiftedXpoints[i] = (float) x2;
                shiftedYpoints[i] = (float) y2;

            }else {
                if(this.chechParallel(x1, y1, x2, y2, x3, y3, x4, y4) == true){
                    shiftedXpoints[i] = (float) x2;
                    shiftedYpoints[i] = (float) y2;
                }else {
                    shiftedXpoints[i] = (float) crossPoint.getX();
                    shiftedYpoints[i] = (float) crossPoint.getY();
                }
            }

        }

        shiftedXpoints[mainPolygon.npoints -1] = fpArray.get(fpArray.size() -1).xpoints[2];
        shiftedYpoints[mainPolygon.npoints -1] = fpArray.get(fpArray.size() -1).ypoints[2];

        // x座標費合わせたz座標を取得 //
        PolygonRoi shiftedPoints = new PolygonRoi(shiftedXpoints, shiftedYpoints, PolygonRoi.POLYLINE);
        shiftedPoints.fitSpline();
        FloatPolygon interplatedPolygon = shiftedPoints.getInterpolatedPolygon(1.0, false);
        float[] resultX = new float[mainPolygon.npoints];
        float[] resultY = new float[mainPolygon.npoints];

        for(int i = 0; i < mainPolygon.npoints; i++){
            resultX[i] = xpoints[i];
            resultY[i] = Spline3D.searchZpositon(interplatedPolygon, xpoints[i]);

        }

        FloatPolygon result = new FloatPolygon(resultX, resultY);


        return result;
    }


    public FloatPolygon getOutlineWithWidth(float x1, float y1, float x2, float y2, float width){
        Line line = new Line(x1, y1, x2, y2);
        line.setStrokeWidth(width);

        return line.getFloatPolygon();
    }


    /**
     * 計算結果を確認するために表示するメソッド
     */
    public void checkProgram(){
        FloatPolygon fp1 = this.getOutlineWithWidth(100, 100, 125, 125, 50);
        FloatPolygon fp2 = this.getOutlineWithWidth(125, 125, 140, 125, 50);

        double x1 = fp1.xpoints[1];
        double y1 = fp1.ypoints[1];

        double x2 = fp1.xpoints[2];
        double y2 = fp1.ypoints[2];

        double x3 = fp2.xpoints[1];
        double y3 = fp2.ypoints[1];

        double x4 = fp2.xpoints[2];
        double y4 = fp2.ypoints[2];


        Point2D points = this.calc4(x1, y1, x2, y2, x3, y3, x4, y4);

        //System.out.println("1 : " + x1 + ", " + y1);
        //System.out.println("2 : " + x2 + ", " + y2);
        //System.out.println("3 : " + x3 + ", " + y3);
        //System.out.println("4 : " + x4 + ", " + y4);

        //System.out.println("check position : " + points.getX() + ", " + points.getY());
    }

    //できた。これ最強の予感 //もしかすると角度の問題で対応する点の位置を変える必要があるかも
    public Point2D calc4(double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4){

        //p1 -> p1, p2 -> p3, p3 -> p2, p4 -> p4 //この計算は点の位置関係に注意
        //https://imagingsolution.blog.fc2.com/blog-entry-137.html　外積の応用らしい

        double s1 = (((x4 - x3) * (y1 - y3)) - ((y4 - y3) * (x1 - x3))) / 2.0;
        double s2 = (((x4 - x3) * (y3 - y2)) - ((y4 - y3) * (x3 - x2))) / 2.0;

        double x = x1 + ((x2 - x1) * (s1 / (s1 + s2)));
        double y = y1 + ((y2 - y1) * (s1 / (s1 + s2)));

        Point2D.Double result = new Point2D.Double(x, y);

        return result;
    }

    public boolean chechParallel(double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4){
        boolean result = false;

        double accuracy = 1000.0;
        double a1 = (y2 - y1) / (x2 - x1);
        double a2 = (y4 - y3) / (x4 - x3);
        double d = Math.abs((a1 - a2) * accuracy);
        if(d < 1){
            result = true;
        }

        return result;
    }

}
