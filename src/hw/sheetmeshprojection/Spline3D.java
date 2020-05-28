package hw.sheetmeshprojection;


import ij.gui.PolygonRoi;
import ij.process.FloatPolygon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Represent a spline using 3D coordinates with FloatPolygon etc.
 * FloatPolygonなどで3D座標を用いてsplineを表現する
 *
 * @author WADA Housei
 *
 */

public class Spline3D {

    PolygonRoi xyPolygon;
    PolygonRoi xzPolygon;
    PolygonRoi yzPolygon;



    public Spline3D(ArrayList<Double[]> coordinateList){
        float[] xpoints = new float[coordinateList.size()];
        float[] ypoints = new float[coordinateList.size()];
        float[] zpoints = new float[coordinateList.size()];
        for(int i = 0; i < coordinateList.size(); i++){
            xpoints[i] = coordinateList.get(i)[0].floatValue();
            ypoints[i] = coordinateList.get(i)[1].floatValue();
            zpoints[i] = coordinateList.get(i)[2].floatValue();
        }

        xyPolygon = new PolygonRoi(xpoints, ypoints, PolygonRoi.POLYLINE);
        xzPolygon = new PolygonRoi(xpoints, zpoints, PolygonRoi.POLYLINE);
        yzPolygon = new PolygonRoi(ypoints, zpoints, PolygonRoi.POLYLINE);


        // * fitsSpline()では計算のバグとおもれれるものでInfinityがでる。これの半分の値で配列を作ろうとすることがあり、これによってVM limit エラーがでるようである。
        // * とりあえず、100で固定してみる。 -> NaNがでるように、、、
        // * 極端に距離が短いものは不具合のものとのようなので、それを監視する方向ではどうか。

        if(Math.abs(xpoints[1] - xpoints[0]) > 1.0) {
            xyPolygon.fitSpline(100);
            xzPolygon.fitSpline(100);
            yzPolygon.fitSpline(100);
        }
    }

    public Spline3D(FloatPolygon xy, FloatPolygon xz, FloatPolygon yz){
        xyPolygon = new PolygonRoi(xy, PolygonRoi.POLYLINE);
        xzPolygon = new PolygonRoi(xz, PolygonRoi.POLYLINE);
        yzPolygon = new PolygonRoi(yz, PolygonRoi.POLYLINE);
        if((xy.xpoints[1] - xy.xpoints[0]) > 0.5) {
            xyPolygon.fitSpline(100);
            xzPolygon.fitSpline(100);
            yzPolygon.fitSpline(100);
        }

    }

    public ArrayList<Double[]> getThinedSplineCoordinateXZ(double interPolatedNum, int positionsNum, boolean withZposition, boolean forZ){ // 2点以上
        //long sTime = System.currentTimeMillis();

        ArrayList<Double[]> allList;
        if(withZposition){
            allList = this.getSplineCoordinageXZ(interPolatedNum, forZ);
        }else{
            allList = this.getSplineCoordinageX(interPolatedNum);
        }

        ArrayList<Double[]> result = new ArrayList<>();


        if(positionsNum >= 2){


            //下手に四捨五入とかしないほうがいいのか？
            //int stepSize = (int)Math.round((allList.size() - 1) / (double)(positionsNum - 1));
            double stepSize = (allList.size() - 1) / (double)(positionsNum - 1);
            ///*
            for(int i = 0; i < positionsNum; i++){
                if(i == 0){
                    result.add(allList.get(0));

                }else if(i == (positionsNum - 1)){
                    result.add(allList.get(allList.size()-1));

                }else{

                    result.add(allList.get((int)(Math.round(i*stepSize))));
                }

            }
            //*/

            //あまり変わらない
            /*
            IntStream intStream = IntStream.range(0, positionsNum);
            intStream.parallel().forEach(i ->{
                if(i == 0){
                    result.set(i, allList.get(0));

                }else if(i == (positionsNum - 1)){
                    result.set(i, allList.get(allList.size()-1));

                }else{
                    result.set(i, allList.get((int)(Math.round(i*stepSize))));
                }

            });
            */

        }

        //long eTime = System.currentTimeMillis();

        //System.out.println("getThinedSplineCoordinateXZ : " + (eTime - sTime) + "msec");
        return result;
    }

    public ArrayList<Double[]> getThinedSplineCoordinateYZ(double intePolatedNum, int positionsNum, boolean withZposition, boolean forZ){ // 2点以上

        ArrayList<Double[]> allList;
        if(withZposition) {
            allList = this.getSplineCoordinageYZ(intePolatedNum, forZ);
        }else{
            allList = this.getSplineCoordinageY(intePolatedNum);
        }

        ArrayList<Double[]> result = new ArrayList<>();


        if(positionsNum >= 2){


            //下手に四捨五入とかしないほうがいいのか？
            //int stepSize = (int)Math.round((allList.size() - 1) / (double)(positionsNum - 1));
            double stepSize = (allList.size() - 1) / (double)(positionsNum - 1);

            ///*
            for(int i = 0; i < positionsNum; i++){
                if(i == 0){
                    result.add(allList.get(0));

                }else if(i == (positionsNum - 1)){
                    result.add(allList.get(allList.size()-1));

                }else{
                    result.add(allList.get((int)(Math.round(i*stepSize))));
                }

            }
            //*/

            //あまり変わらない
            /*
            IntStream intStream = IntStream.range(0, positionsNum);
            intStream.parallel().forEach(i ->{
                if(i == 0){
                    result.set(i, allList.get(0));

                }else if(i == (positionsNum - 1)){
                    result.set(i, allList.get(allList.size()-1));

                }else{
                    result.set(i, allList.get((int)(Math.round(i*stepSize))));
                }

            });
            */

        }
        return result;
    }




    public ArrayList<Double[]> getSplineCoordinageXZ(double interPolatedNum, boolean forZ){
        //long sTime = System.currentTimeMillis();

        FloatPolygon interpolatedXY = xyPolygon.getInterpolatedPolygon(interPolatedNum, false);
        FloatPolygon interpolatedXZ = xzPolygon.getInterpolatedPolygon(interPolatedNum, false);



        ArrayList<Double[]> result = new ArrayList<>();


        ///*
        if(forZ == true){ //ここ、ちょっと考え方が違うかも。
            float[] xpoints = interpolatedXZ.xpoints;
            //float[] ypoints = interpolatedXY.ypoints;
            float[] zpoints = interpolatedXZ.ypoints;

            for (int n = 0; n < interpolatedXZ.npoints; n++) {
                //System.out.println("xpoints[n] = " + xpoints[n]); //ok
                Double xValue = Double.valueOf(String.valueOf(xpoints[n])); //float -> double 誤差対策
                Double yValue = Double.valueOf(String.valueOf(this.searchYpositionFromXvalue(interpolatedXY, xpoints[n])));
                Double zValue = Double.valueOf(String.valueOf(zpoints[n]));
                //Double zValue = Double.valueOf(String.valueOf(this.searchZposition(interpolatedXZ, xpoints[n])));

                Double[] buff = {xValue, yValue, zValue};
                //System.out.println("getSplingCorrdinateXZ buff(x,y,z): " + xValue + ", " + yValue + ", " + zValue);
                result.add(buff);
            }
        }else {
            float[] xpoints = interpolatedXY.xpoints;
            float[] ypoints = interpolatedXY.ypoints;

            for (int n = 0; n < interpolatedXY.npoints; n++) {
                Double xValue = Double.valueOf(String.valueOf(xpoints[n])); //float -> double 誤差対策
                Double yValue = Double.valueOf(String.valueOf(ypoints[n]));
                Double zValue = Double.valueOf(String.valueOf(this.searchZpositionFromXvalue(interpolatedXZ, xpoints[n])));
                //Double zValue = Double.valueOf(String.valueOf(this.searchZposition(interpolatedXZ, xpoints[n])));

                Double[] buff = {xValue, yValue, zValue};
                result.add(buff);
            }
        }
        //*/

        //あまり変わらない
        /*
        IntStream intStream = IntStream.range(0, interpolatedXY.npoints);
        intStream.parallel().forEach(n ->{
            Double xValue = Double.valueOf(String.valueOf(xpoints[n])); //float -> double 誤差対策
            Double yValue = Double.valueOf(String.valueOf(ypoints[n]));
            Double zValue = Double.valueOf(String.valueOf(this.searchZpositionFromXvalue(interpolatedXZ, xpoints[n]))); //Zのポジションを計算する必要があるか？ -> 手動で修正するときには使うかも

            Double[] buff = {xValue, yValue, zValue};
            result.set(n, buff);

        });
        */

        //long eTime = System.currentTimeMillis();
        //System.out.println("getSplineCoordinateXZ : " + (eTime - sTime) + "msec");
        return result;
    }




    public ArrayList<Double[]> getSplineCoordinageYZ(double interPolatedNum, boolean forZ){
        FloatPolygon interpolatedXY = xyPolygon.getInterpolatedPolygon(interPolatedNum, false);
        FloatPolygon interpolatedYZ = yzPolygon.getInterpolatedPolygon(interPolatedNum, false);

        ArrayList<Double[]> result = new ArrayList<>();


        ///*
        if(forZ == true){
            //float[] xpoints = interpolatedXY.xpoints;
            float[] ypoints = interpolatedYZ.xpoints;
            float[] zpoints = interpolatedYZ.ypoints;

            for (int n = 0; n < interpolatedYZ.npoints; n++) {
                //System.out.println("ypoints[n] = " + ypoints[n]); //ここ全部同じ値。。。おかしい

                Double xValue = Double.valueOf(String.valueOf(this.searchXpositionFromYvalue(interpolatedXY, ypoints[n]))); //float -> double 誤差対策
                Double yValue = Double.valueOf(String.valueOf(ypoints[n]));
                Double zValue = Double.valueOf(String.valueOf(zpoints[n]));
                //Double zValue = Double.valueOf(String.valueOf(this.searchZposition(interpolatedXZ, xpoints[n])));

                Double[] buff = {xValue, yValue, zValue};
                //System.out.println("x, y z = " + xValue + ", " + yValue + ", " + zValue);
                result.add(buff);
            }
        }else {
            float[] xpoints = interpolatedXY.xpoints;
            float[] ypoints = interpolatedXY.ypoints;
            for (int n = 0; n < interpolatedXY.npoints; n++) {
                Double xValue = Double.valueOf(String.valueOf(xpoints[n])); //float -> double 誤差対策
                Double yValue = Double.valueOf(String.valueOf(ypoints[n]));
                Double zValue = Double.valueOf(String.valueOf(this.searchZpositionFromYvalue(interpolatedYZ, ypoints[n]))); //Zポジションの計算今はいらないと思われる -> 手動で修正する場合には必要
                //Double zValue = Double.valueOf(String.valueOf(this.searchZposition(interpolatedYZ, ypoints[n])));

                Double[] buff = {xValue, yValue, zValue};
                result.add(buff);
            }
        }
        //*/


        //あまり変わらない
        /*
        IntStream intStream = IntStream.range(0, interpolatedXY.npoints);
        intStream.parallel().forEach(n ->{
            Double xValue = Double.valueOf(String.valueOf(xpoints[n])); //float -> double 誤差対策
            Double yValue = Double.valueOf(String.valueOf(ypoints[n]));
            Double zValue = Double.valueOf(String.valueOf(this.searchZpositionFromYvalue(interpolatedYZ, ypoints[n])));

            Double[] buff = {xValue, yValue, zValue};
            result.set(n, buff);

        });
        */


        return result;
    }


    public ArrayList<Double[]> getSplineCoordinageX(double interPolatedNum){
        //long sTime = System.currentTimeMillis();

        FloatPolygon interpolatedXY = xyPolygon.getInterpolatedPolygon(interPolatedNum, false);

        float[] xpoints = interpolatedXY.xpoints;
        float[] ypoints = interpolatedXY.ypoints;

        ArrayList<Double[]> result = new ArrayList<>();
        for(int i = 0; i < interpolatedXY.npoints; i++){
            Double[] buff = {0.0, 0.0, 0.0};
            result.add(buff);
        }

        IntStream intStream = IntStream.range(0, interpolatedXY.npoints);
        intStream.parallel().forEach(n ->{
            Double xValue = Double.valueOf(String.valueOf(xpoints[n])); //float -> double 誤差対策
            Double yValue = Double.valueOf(String.valueOf(ypoints[n]));
            Double zValue = 0.0;


            Double[] buff = {xValue, yValue, zValue};
            result.set(n, buff);

        });

        //long eTime = System.currentTimeMillis();
        //System.out.println("getSplineCoordinateXZ : " + (eTime - sTime) + "msec");

        return result;
    }

    public ArrayList<Double[]> getSplineCoordinageY(double interPolatedNum){
        FloatPolygon interpolatedXY = xyPolygon.getInterpolatedPolygon(interPolatedNum, false);

        float[] xpoints = interpolatedXY.xpoints;
        float[] ypoints = interpolatedXY.ypoints;

        ArrayList<Double[]> result = new ArrayList<>();
        for(int i = 0; i < interpolatedXY.npoints; i++){
            Double[] buff = {0.0, 0.0, 0.0};
            result.add(buff);
        }


        IntStream intStream = IntStream.range(0, interpolatedXY.npoints);
        intStream.parallel().forEach(n ->{
            Double xValue = Double.valueOf(String.valueOf(xpoints[n])); //float -> double 誤差対策
            Double yValue = Double.valueOf(String.valueOf(ypoints[n]));
            Double zValue = 0.0; //ここだけの違い

            Double[] buff = {xValue, yValue, zValue};
            result.set(n, buff);

        });



        return result;
    }



    private float searchZpositionFromXvalue(FloatPolygon xz, float xNum){ //これでは処理が遅い
        //long sTime = System.nanoTime();

        float checkNum = Float.MAX_VALUE;
        float result = 0;

        float[] xArray = xz.xpoints;
        float[] zArray = xz.ypoints;

        for(int x = 0; x < xArray.length; x++){
            float buffValue = Math.abs(xArray[x] - xNum);
            if(buffValue < checkNum){
                result = zArray[x];
                checkNum = buffValue;
            }
        }

        //long eTime = System.nanoTime();
        //System.out.println("searchZpositionFromXvalue : " + (eTime - sTime) + "nsec");

        return result;
    }


    //多分同じ記述。上を使いまわしていける
    private float searchZpositionFromYvalue(FloatPolygon yz, float yNum){
        float checkNum = Float.MAX_VALUE;
        float result = 0;

        float[] yArray = yz.xpoints;
        float[] zArray = yz.ypoints;

        for(int y = 0; y < yArray.length; y++){
            float buffValue = Math.abs(yArray[y] - yNum);
            if(buffValue < checkNum){
                result = zArray[y];
                checkNum = buffValue;
            }
        }

        return result;
    }



    private float searchXpositionFromZvalue(FloatPolygon xz, float xNum){ //これでは処理が遅い
        //long sTime = System.nanoTime();

        float checkNum = Float.MAX_VALUE;
        float result = 0;

        float[] xArray = xz.ypoints; //値を入れ替えることで逆の検索が可能
        float[] zArray = xz.xpoints;

        for(int x = 0; x < xArray.length; x++){
            float buffValue = Math.abs(xArray[x] - xNum);
            if(buffValue < checkNum){
                result = zArray[x];
                checkNum = buffValue;
            }
        }

        //long eTime = System.nanoTime();
        //System.out.println("searchZpositionFromXvalue : " + (eTime - sTime) + "nsec");

        return result;
    }

    private float searchYpositionFromZvalue(FloatPolygon yz, float yNum){
        float checkNum = Float.MAX_VALUE;
        float result = 0;

        float[] yArray = yz.ypoints; //上記より反対に入れることで逆の検索が可能になる
        float[] zArray = yz.xpoints;

        for(int y = 0; y < yArray.length; y++){
            float buffValue = Math.abs(yArray[y] - yNum);
            if(buffValue < checkNum){
                result = zArray[y];
                checkNum = buffValue;
            }
        }

        return result;
    }

    private float searchXpositionFromYvalue(FloatPolygon xy, float yNum){
        float checkNum = Float.MAX_VALUE;
        float result = 0;

        float[] xArray = xy.xpoints;
        float[] yArray = xy.ypoints;

        for(int y = 0; y < xArray.length; y++){
            float buffValue = Math.abs(yArray[y] - yNum);
            if(buffValue < checkNum){
                result = xArray[y];
                checkNum = buffValue;
            }
        }
        return result;
    }


    private float searchYpositionFromXvalue(FloatPolygon xy, float xNum){
        float checkNum = Float.MAX_VALUE;
        float result = 0;

        float[] xArray = xy.xpoints;
        float[] yArray = xy.ypoints;

        for(int x = 0; x < xArray.length; x++){
            float buffValue = Math.abs(xArray[x] - xNum);
            if(buffValue < checkNum){
                result = yArray[x];
                checkNum = buffValue;
            }
        }

        return result;
    }


    private float searchZposition(FloatPolygon xzORyz, float value){//一旦すべて計算させて一番小さいものを選ぶ？
        float result = 0.0f;

        float[] xORyArray = xzORyz.xpoints;
        float[] zArray = xzORyz.ypoints;

        ArrayList<Double> doubleList = new ArrayList<>();
        for(int i = 0; i < xORyArray.length; i++){
            doubleList.add(Double.valueOf(String.valueOf(xORyArray[i])));
        }

        double[] processedValues = doubleList.stream().parallel().mapToDouble(v -> Math.abs(v - value)).toArray();

        double minValue = Arrays.stream(processedValues).min().getAsDouble();

        int findIndex = IntStream.range(0, processedValues.length).map(i -> processedValues[i] == minValue ? i : -1).filter(i->i >= 0).min().getAsInt();
        result = zArray[findIndex];
        return result;
    }



}
