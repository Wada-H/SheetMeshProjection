package hw.sheetmeshprojection;


import ij.process.FloatPolygon;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;


/**
 * Does various processing for MeshMap <Double []>
 * MeshMap<Double[]> に対していろいろな処理をする
 *
 * @author WADA Housei
 */

public class MeshManager {

    MeshMap<Double[]> mainMeshMap;
    ArrayList<Integer> rowMaxList;
    ArrayList<Integer> colMaxList;

    public MeshManager(MeshMap<Double[]> meshMap){
        mainMeshMap = meshMap.copy(); //もとの表示に影響ないようになるか？これで大丈夫そう
        rowMaxList = this.getMaxDistanceRowMax();
        colMaxList = this.getMaxDistanceColMax();
    }


    public MeshMap<Double[]> createInterporatedMap(boolean forZ){

        MeshManager meshManager = new MeshManager(this.createInterpolatedMapForCol(forZ));

        return meshManager.createInterpolatedMapForRow(forZ);
        //Col -> Rowでおこなうとなめらかになる。逆だと筋ができる
    }

    public void setMeshMap(MeshMap<Double[]> meshMap){
        mainMeshMap = meshMap.copy();
    }


    public MeshMap<Double[]> createInterpolatedMapForRow(boolean forZ){ //現在testだけで使用 -> keypress> escape

        MeshMap<Double[]> result = new MeshMap<>();
        int rowLength = rowMaxList.stream().mapToInt(value -> value).sum() - (mainMeshMap.getXsize() - 2);

        Double[] defaultValue = {0.0, 0.0, 0.0};

        result.createMesh(rowLength, mainMeshMap.getYsize(), defaultValue);

        IntStream yStream = IntStream.range(0, mainMeshMap.getYsize());
        yStream.parallel().forEach(y ->{
            FloatPolygon buff = this.getRowByFloatPolygon(y);
            PolyLineManager polyLineManager = new PolyLineManager(buff);

            FloatPolygon xzBuff = this.getXZpolygon(y);
            PolyLineManager xzPolylineManager = new PolyLineManager(xzBuff);

            int xIndex = 0;
            ConcurrentHashMap<Integer, Double[]> buffXmap = new ConcurrentHashMap<>();


            for(int x = 0; x < mainMeshMap.getXsize() - 1; x++){
                FloatPolygon segmentedLine = polyLineManager.getSegmentAsFloatPolygon(x);
                FloatPolygon xzSegmentedLine = xzPolylineManager.getSegmentAsFloatPolygon(x, segmentedLine.npoints);


                // ここから Spline3Dを使用する方法で考える
                ArrayList<Double[]> segment3DList = new ArrayList<>();

                for(int i = 0; i < segmentedLine.npoints; i++){
                    Double xValue = Double.valueOf(String.valueOf(segmentedLine.xpoints[i])); //float -> double 誤差対策
                    Double yValue = Double.valueOf(String.valueOf(segmentedLine.ypoints[i]));
                    Double zValue = Double.valueOf(String.valueOf(xzSegmentedLine.ypoints[i]));
                    Double[] coordinate = {xValue, yValue, zValue};
                    segment3DList.add(coordinate);
                }



                Spline3D spline3D = new Spline3D(segment3DList);
                ArrayList<Double[]> newLineArray = spline3D.getThinedSplineCoordinateXZ(1/10.0, rowMaxList.get(x), true, forZ);


                int limitPosition = rowMaxList.get(x) -1;
                if(x == mainMeshMap.getXsize() - 2){
                    limitPosition++;
                }


                for(int i = 0; i < limitPosition; i++){

                    buffXmap.put(xIndex, newLineArray.get(i));
                    xIndex++;
                }
            }
            result.setRow(y, buffXmap);
        });
        return result;
    }


    public MeshMap<Double[]> createInterpolatedMapForCol(boolean forZ){


        MeshMap<Double[]> result = new MeshMap<>();
        int colLength = colMaxList.stream().mapToInt(value -> value).sum() - (mainMeshMap.getYsize() - 2);

        Double[] defaultValue = {0.0, 0.0, 0.0};

        result.createMesh(mainMeshMap.getXsize(), colLength, defaultValue);

        IntStream xStream = IntStream.range(0, mainMeshMap.getXsize());
        xStream.parallel().forEach(x ->{
            FloatPolygon buff = this.getColumnByFloatPolygon(x);
            PolyLineManager polyLineManager = new PolyLineManager(buff);

            FloatPolygon yzBuff = this.getYZpolygon(x);
            PolyLineManager yzPolyLineManager = new PolyLineManager(yzBuff);

            int yIndex = 0;
            ConcurrentHashMap<Integer, Double[]> buffYmap = new ConcurrentHashMap<>();

            for(int y = 0; y < mainMeshMap.getYsize() - 1; y++){
                FloatPolygon segmentedLine = polyLineManager.getSegmentAsFloatPolygon(y);
                FloatPolygon yzSegmentedLine = yzPolyLineManager.getSegmentAsFloatPolygon(y, segmentedLine.npoints);


                // ここから Spline3Dを使用する方法で考える
                ArrayList<Double[]> segment3DList = new ArrayList<>();

                for(int i = 0; i < segmentedLine.npoints; i++){
                    Double xValue = Double.valueOf(String.valueOf(segmentedLine.xpoints[i])); //float -> double 誤差対策
                    Double yValue = Double.valueOf(String.valueOf(segmentedLine.ypoints[i]));
                    Double zValue = Double.valueOf(String.valueOf(yzSegmentedLine.ypoints[i]));

                    Double[] coordinate = {xValue, yValue, zValue};
                    segment3DList.add(coordinate);
                }

                Spline3D spline3D = new Spline3D(segment3DList);
                ArrayList<Double[]> newLineArray = spline3D.getThinedSplineCoordinateYZ(1/10.0, colMaxList.get(y), true, forZ);


                int limitPosition = colMaxList.get(y) -1; //ここをcalMaxList.get(y)にすればとりあえず回避できそう

                if(y == mainMeshMap.getYsize() - 2){
                    limitPosition++;
                }

                for(int i = 0; i < limitPosition; i++){
                    buffYmap.put(yIndex, newLineArray.get(i));
                    yIndex++;
                }
            }
            result.setColumn(x, buffYmap);

        });
        return result;
    }



    public double getMaxDistance(ConcurrentHashMap<Integer, Double[]> firstPosition, ConcurrentHashMap<Integer, Double[]> secondPosition){

        ArrayList<Double> dArray = new ArrayList<>();
        firstPosition.forEach((y, fp) ->{
            Double[] sp = secondPosition.get(y);
            double d = Math.sqrt(Math.pow((sp[0] - fp[0]), 2) + Math.pow((sp[1] - fp[1]), 2) + Math.pow((sp[2] - fp[2]), 2));
            dArray.add(d);
        });

        return dArray.parallelStream().max(Comparator.naturalOrder()).get().doubleValue();
    }


    public ArrayList<Integer> getMaxDistanceRowMax(){ //Rowにおける各点間のspline時の最大点数を返す

        ArrayList<Integer> xlist =new ArrayList<>();
        for(int x = 0; x < mainMeshMap.getXsize() -1; x++){
            xlist.add(0);
        }

        IntStream intStream = IntStream.range(0, mainMeshMap.getYsize());

        intStream.parallel().forEach(i ->{

            FloatPolygon buffPR = this.getRowByFloatPolygon(i);
            PolyLineManager polyLineManager = new PolyLineManager(buffPR);

            for(int n = 0; n < mainMeshMap.getXsize() -1; n++){
                int pointNum = polyLineManager.getSegmentAsFloatPolygon(n).npoints;
                if(xlist.get(n) < pointNum) xlist.set(n, pointNum);
            }

        });

        return xlist;
    }



    public ArrayList<Integer> getMaxDistanceColMax(){ //Columnにおける各点間のspline時の最大点数を返す

        ArrayList<Integer> ylist =new ArrayList<>();
        for(int y = 0; y < mainMeshMap.getYsize() -1; y++){
            ylist.add(0);
        }

        IntStream intStream = IntStream.range(0, mainMeshMap.getXsize());

        intStream.parallel().forEach(i ->{

            FloatPolygon buffPR = this.getColumnByFloatPolygon(i);
            PolyLineManager polyLineManager = new PolyLineManager(buffPR);

            for(int n = 0; n < mainMeshMap.getYsize() -1; n++){
                int pointNum = polyLineManager.getSegmentAsFloatPolygon(n).npoints;
                if(ylist.get(n) < pointNum) ylist.set(n, pointNum);
            }

        });

        return ylist;
    }

    public FloatPolygon getColumnByFloatPolygon(int colNum){
        float[] xpoints = new float[mainMeshMap.getYsize()];
        float[] ypoints = new float[mainMeshMap.getYsize()];

        for(int y = 0; y < mainMeshMap.getYsize(); y++){
            xpoints[y] = mainMeshMap.getValue(colNum, y)[0].floatValue();
            ypoints[y] = mainMeshMap.getValue(colNum, y)[1].floatValue();
        }

        FloatPolygon buffPR = new FloatPolygon(xpoints, ypoints);
        return buffPR;
    }

    public FloatPolygon getRowByFloatPolygon(int rowNum){
        float[] xpoints = new float[mainMeshMap.getXsize()];
        float[] ypoints = new float[mainMeshMap.getXsize()];

        for(int x = 0; x < mainMeshMap.getXsize(); x++){

            xpoints[x] = mainMeshMap.getValue(x, rowNum)[0].floatValue();
            ypoints[x] = mainMeshMap.getValue(x, rowNum)[1].floatValue();
        }

        FloatPolygon buffPR = new FloatPolygon(xpoints, ypoints);
        return buffPR;
    }

    public FloatPolygon getRowByFloatPolygonXZ(int rowNum){
        float[] xpoints = new float[mainMeshMap.getXsize()];
        float[] ypoints = new float[mainMeshMap.getXsize()];

        for(int x = 0; x < mainMeshMap.getXsize(); x++){

            xpoints[x] = mainMeshMap.getValue(x, rowNum)[0].floatValue();
            ypoints[x] = mainMeshMap.getValue(x, rowNum)[2].floatValue();
        }

        FloatPolygon buffPR = new FloatPolygon(xpoints, ypoints);
        return buffPR;
    }


    public FloatPolygon getXZpolygon(int rowNum){
        float[] xpoints = new float[mainMeshMap.getXsize()];
        float[] zpoints = new float[mainMeshMap.getXsize()];

        for(int x = 0; x < mainMeshMap.getXsize(); x++){

            xpoints[x] = mainMeshMap.getValue(x, rowNum)[0].floatValue();
            zpoints[x] = mainMeshMap.getValue(x, rowNum)[2].floatValue();
        }

        FloatPolygon buffPR = new FloatPolygon(xpoints, zpoints);
        return buffPR;
    }

    public FloatPolygon getXZpolygonKeepXpoints(int rowNum){
        float[] xpoints = new float[mainMeshMap.getXsize()];
        float[] zpoints = new float[mainMeshMap.getXsize()];

        for(int x = 0; x < mainMeshMap.getXsize(); x++){

            xpoints[x] = x;
            zpoints[x] = mainMeshMap.getValue(x, rowNum)[2].floatValue();
        }
        FloatPolygon buffPR = new FloatPolygon(xpoints, zpoints);
        return buffPR;
    }

    public FloatPolygon getInitializedXZpolygon(int rowNum){ //for crosssection image
        float[] xpoints = new float[mainMeshMap.getXsize()];
        float[] zpoints = new float[mainMeshMap.getXsize()];

        for(int x = 0; x < mainMeshMap.getXsize(); x++){

            xpoints[x] = mainMeshMap.getValue(x, rowNum)[0].floatValue() - mainMeshMap.getValue(0, rowNum)[0].floatValue();
            zpoints[x] = mainMeshMap.getValue(x, rowNum)[2].floatValue();
        }
        FloatPolygon buffPR = new FloatPolygon(xpoints, zpoints);
        return buffPR;
    }



    public FloatPolygon getYZpolygon(int colNum){
        float[] ypoints = new float[mainMeshMap.getYsize()];
        float[] zpoints = new float[mainMeshMap.getYsize()];
        for(int y = 0; y < mainMeshMap.getYsize(); y++){
            ypoints[y] = mainMeshMap.getValue(colNum, y)[1].floatValue();
            zpoints[y] = mainMeshMap.getValue(colNum, y)[2].floatValue();
        }

        FloatPolygon buffPR = new FloatPolygon(ypoints, zpoints);
        return buffPR;
    }



    /* translateに不具合か?
    public void translateXYZ(double x, double y, double z){
        mainMeshMap.mainMap.parallelStream().forEach(yList ->{
            yList.parallelStream().forEach(doubleArray ->{
                doubleArray[0] = doubleArray[0] + x;
                doubleArray[1] = doubleArray[1] + y;
                doubleArray[2] = doubleArray[2] + z;
            });
        });
    }
    */



    public void translateDeep(int thickness, int limitPosition){
        for(int i = 0; i < mainMeshMap.getYsize(); i++){
            FloatPolygon fpxy = this.getRowByFloatPolygon(i);
            FloatPolygon fp = this.getRowByFloatPolygonXZ(i);
            PolyLineManager polyLineManager = new PolyLineManager(fp);
            FloatPolygon shiftedFp = polyLineManager.getShiftedPolygon(thickness);
            //FloatPolygon shiftedFp = fp; //getShfitedPolygon check
            //ここでx, yをそれぞれ行って、座標を変更すればそれなりの値か？ 20200515

            ArrayList<Double[]> list = new ArrayList<>();
            for(int n = 0; n < shiftedFp.npoints; n++){
                double x = Double.parseDouble(String.valueOf(fp.xpoints[n]));
                double y = Double.parseDouble(String.valueOf(fpxy.ypoints[n]));
                double z = Double.parseDouble(String.valueOf(shiftedFp.ypoints[n]));

                //手動を考えると画像からはみ出るのを阻止したいが、厚みを考えるとはみ出さないと、、、どうする？ 2020.1.6
                /*
                if(z > limitPosition){
                    z = limitPosition;
                }
                */
                //System.out.println("new z : "+ z);
                Double[] buffD = {x, y, z};

                list.add(buffD);
            }
            mainMeshMap.setRow(i, list);
        }
    }



    public void translateXYZ(double x, double y, double z){
        mainMeshMap.mainMap.forEach(yList ->{
            yList.forEach(doubleArray ->{
                doubleArray[0] = doubleArray[0] + x;
                doubleArray[1] = doubleArray[1] + y;
                doubleArray[2] = doubleArray[2] + z;
            });
        });
    }


    public void translateXY(double x, double y){
        this.translateXYZ(x, y , 0);

    }

    public void traslateZ(double z){
        this.translateXYZ(0, 0, z);
    }

    public void setXYvalues(int x, int y, double xValue, double yValue ){
        Double[] buff = mainMeshMap.getValue(x, y);
        buff[0] = xValue;
        buff[1] = yValue;
        mainMeshMap.setValue(x, y, buff);
    }

    public void setXYvalues(int index, double xValue, double yValue){
        int x = index / mainMeshMap.getYsize();
        int y = index % mainMeshMap.getYsize();

        this.setXYvalues(x, y, xValue, yValue);
    }

    public void setXZvalues(int yIndex, FloatPolygon fp, double xOffset){ //XZ画像とのつじつまを合わせるなら、zの値のみにするか？
        List<Double[]> buff = mainMeshMap.getRowAsList(yIndex);
        if(buff.size() == fp.npoints){
            for(int i = 0; i < buff.size(); i++){

                //buff.get(i)[0] = Double.valueOf(String.valueOf(fp.xpoints[i])) + xOffset;
                buff.get(i)[2] = Double.valueOf(String.valueOf(fp.ypoints[i]));

            }
        }
    }

    public void setYZvalues(int xIndex, FloatPolygon fp, double yOffset){ //YZ画像のつじつまを合わせるなら、zの値のみにするか？ ->つまり、ポジションが逆転することが起こり得る。そうすると作られるYZ画像に矛盾が生じる。
        List<Double[]> buff = mainMeshMap.getColumnAsList(xIndex);
        if(buff.size() == fp.npoints){
            for(int i = 0; i < buff.size(); i++){


                buff.get(i)[2] = Double.valueOf(String.valueOf(fp.xpoints[i]));
                //buff.get(i)[1] = Double.valueOf(String.valueOf(fp.ypoints[i])) + yOffset;

            }
        }
    }


    public MeshMap<Double[]> copy(){
        MeshMap<Double[]> result = new MeshMap<>();
        Double[] defaultValue = {0.0, 0.0, 0.0};
        result.createMesh(mainMeshMap.getXsize(), mainMeshMap.getYsize(), defaultValue);
        for(int x = 0; x < mainMeshMap.getXsize(); x++){
            for(int y = 0; y < mainMeshMap.getYsize(); y++){
                result.setValue(x, y, mainMeshMap.getValue(x,y).clone());
            }

        }
        return result;
    }


    public MeshMap<Double[]> changeRowPointsNum(int num, boolean withZposition, boolean forZ){ //y方向のsize変更
        //long sTime = System.currentTimeMillis();

        int row = num;
        int col = mainMeshMap.getXsize();
        Double[] defaultValue = {0.0, 0.0, 0.0};

        MeshMap<Double[]> newMap = new MeshMap<>();
        newMap.createMesh(col, row, defaultValue);

        //long ssTime = System.currentTimeMillis();
        //System.out.println("createMesh :"  + (ssTime - sTime) + "msec");


        IntStream intStream = IntStream.range(0, mainMeshMap.getXsize());
        intStream.parallel().forEach(x ->{
            Spline3D spline3D = new Spline3D((ArrayList)mainMeshMap.getColumnAsList(x));
            ArrayList<Double[]> reconstructedArray = spline3D.getThinedSplineCoordinateYZ(1/100.0, row, withZposition, forZ);

            newMap.setColumn(x, reconstructedArray);

        });


        //long eTime = System.currentTimeMillis();
        //System.out.println("changeRowPointNum : " + (eTime - sTime) + "msec");
        return newMap;
    }

    public MeshMap<Double[]> changeColPointsNum(int num, boolean withZposition, boolean forZ){ //x方向のsize変更
        //long sTime = System.currentTimeMillis();

        int row = mainMeshMap.getYsize();
        int col = num;
        Double[] defaultValue = {0.0, 0.0, 0.0};

        MeshMap<Double[]> newMap = new MeshMap<>();
        newMap.createMesh(col, row, defaultValue);


        IntStream intStream = IntStream.range(0, mainMeshMap.getYsize());
        intStream.parallel().forEach(y ->{
            Spline3D spline3D = new Spline3D((ArrayList)mainMeshMap.getRowAsList(y));
            ArrayList<Double[]> reconstructedArray = spline3D.getThinedSplineCoordinateXZ(1/100.0, col, withZposition, forZ);

            newMap.setRow(y, reconstructedArray);

        });


        //long eTime = System.currentTimeMillis();
        //System.out.println("changeColPointNum : " + (eTime - sTime) + "msec");

        return newMap;
    }



    public MeshMap<Double[]> changeMeshPoints(int xNum, int yNum, boolean withZposition, boolean forZ){ //このままでは反映されない。col,row同時に変更するか？段階踏むとうまくいく。なぜか？
        //long sTime = System.currentTimeMillis();

        int row = yNum;
        int col = xNum;
        Double[] defaultValue = {0.0, 0.0, 0.0};

        MeshMap<Double[]> newMapCol = new MeshMap<>();
        newMapCol.createMesh(col, mainMeshMap.getYsize(), defaultValue);



        IntStream intStreamRow = IntStream.range(0, mainMeshMap.getYsize()); //Xのサイズ変更　何故か一つ飛ばしている
        intStreamRow.parallel().forEach(y ->{
            Spline3D spline3D = new Spline3D((ArrayList)mainMeshMap.getRowAsList(y));
            ArrayList<Double[]> reconstructedArray = spline3D.getThinedSplineCoordinateXZ(1/100.0, col, withZposition, forZ);

            newMapCol.setRow(y, reconstructedArray);

        });




        MeshMap<Double[]> newMapRow = new MeshMap<>();
        newMapRow.createMesh(col, row, defaultValue);


        IntStream intStreamCol = IntStream.range(0, newMapRow.getXsize()); //Yのサイズ変更　問題ない
        intStreamCol.parallel().forEach(x ->{
            Spline3D spline3D = new Spline3D((ArrayList)newMapCol.getColumnAsList(x));
            ArrayList<Double[]> reconstructedArray = spline3D.getThinedSplineCoordinateYZ(1/100.0, row, withZposition, forZ);

            newMapRow.setColumn(x, reconstructedArray);

        });


        //long eTime = System.currentTimeMillis();
        //System.out.println("changeMeshPoints : " + (eTime - sTime) + "msec");

        return newMapRow;


    }


    static boolean saveMeshData(MeshMap<Double[]> meshmap, String filePath, String fileName){ //Tab区切り、x, y, z のデータを1frame分
        Path path = Paths.get(filePath, fileName);
        File f = path.toFile();

        FileWriter filewriter = null;
        try {
            filewriter = new FileWriter(f);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }

        BufferedWriter bw = new BufferedWriter(filewriter);
        PrintWriter pw = new PrintWriter(bw);

        int colSize = meshmap.getXsize();
        int rowSize = meshmap.getYsize();

        for(int y = 0; y < rowSize; y++){
            List<Double[]> rowList = meshmap.getRowAsList(y);
            for(int i = 0; i < rowList.size(); i++){
                Double[] dArray = rowList.get(i);
                pw.print(dArray[0] + ",");
                pw.print(dArray[1] + ",");
                pw.print(dArray[2]);
                if(i != (rowList.size()-1)) {
                    pw.print("\t");
                }
            }
            if(y != (rowSize -1) ) {
                pw.print("\n");
            }
        }

        pw.close();
        return true;
    }

    static boolean saveMeshData(ConcurrentHashMap<Integer, MeshMap<Double[]>> cmap, String filePath, String fileName){ //Tab区切り、x, y, z のデータを全フレームを1ファイルに。とりあえずsave, loadともに保留。
        Path path = Paths.get(filePath, fileName);
        File f = path.toFile();

        FileWriter filewriter = null;
        try {
            filewriter = new FileWriter(f);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }

        BufferedWriter bw = new BufferedWriter(filewriter);
        PrintWriter pw = new PrintWriter(bw);
        HashMap<Integer, MeshMap<Double[]>> test = new HashMap<>();
        cmap.keySet().parallelStream().mapToInt(i ->i).sorted().forEach(num ->{

            pw.print("T" + (num + 1) + "\n");
            MeshMap<Double[]> meshmap = cmap.get(num);

            int colSize = meshmap.getXsize();
            int rowSize = meshmap.getYsize();

            for(int y = 0; y < rowSize; y++){
                List<Double[]> rowList = meshmap.getRowAsList(y);
                for(int i = 0; i < rowList.size(); i++){
                    Double[] dArray = rowList.get(i);
                    pw.print(dArray[0] + ",");
                    pw.print(dArray[1] + ",");
                    pw.print(dArray[2]);
                    if(i != (rowList.size()-1)) {
                        pw.print("\t");
                    }
                }
                if(y != (rowSize -1) ) {
                    pw.print("\n");
                }
            }


        });

        pw.close();
        return true;
    }


    static MeshMap<Double[]> loadMeshData(String filePath, String fileName){ //Tab区切り、x, y, z のデータを1frame分
        Path path = Paths.get(filePath, fileName);

        ArrayList<String> stringList = new ArrayList();

        try (Stream<String> lineString_s = Files.lines(path)){
            lineString_s.forEach(stringList::add);

        }catch (IOException e) {
            e.printStackTrace();
        }

        return MeshManager.setCoordinate(stringList);
    }

    static private MeshMap<Double[]> setCoordinate(ArrayList<String> coordinateLines){
        int xSize = coordinateLines.get(0).split("\t").length;
        int ySize = coordinateLines.size();

        /* //なくていいのか？
        if(mainMeshMap.getYsize() != ySize){
            return false;
        }else if(mainMeshMap.getXsize() != xSize){
            return false;
        }
        */

        Double[] defaultValue = {0.0, 0.0, 0.0};
        MeshMap<Double[]> buffMap = new MeshMap<>();
        buffMap.createMesh(xSize, ySize, defaultValue);
        for(int y = 0; y < ySize; y++){
            ArrayList<Double[]> rowList = new ArrayList<>();

            String[] coordinateDataX = coordinateLines.get(y).split("\t");
            for(String coordinate : coordinateDataX){
                ArrayList<Double> buffList = new ArrayList<>();
                String[] splitData = coordinate.split(",");
                for(String coo : splitData){
                    buffList.add(Double.valueOf(coo));
                }

                Double[] d = new Double[buffList.size()];
                for(int i = 0; i < buffList.size(); i++){
                    d[i] = buffList.get(i);
                }
                rowList.add(d);
            }

            buffMap.setRow(y, rowList);
        }

        for(int y = 0; y < ySize; y++){
            for(int x = 0; x < xSize; x++){
                Double[] buff = buffMap.getValue(x, y);
            }
        }

        return buffMap;
    }


}
