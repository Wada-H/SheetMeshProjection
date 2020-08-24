package hw.sheetmeshprojection;

import hw.crosssectionviewer.CreateCrossSectionImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.CanvasResizer;
import ij.plugin.HyperStackConverter;
import ij.plugin.Straightener;
import ij.plugin.filter.GaussianBlur;
import ij.process.FloatPolygon;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * Creates a image using MeshMap.
 *
 * @author WADA Housei
 */

public class ImageCreator {

    ImagePlus mainImage;
    MeshMap<Double[]> meshMap;


    long creatingTime = 0;

    boolean numKeep = false;


    public static final int XY = 0;
    public static final int XZ = 1;
    public static final int YZ = 2;


    public ImageCreator(ImagePlus stackIamge, MeshMap<Double[]> map){
        mainImage = stackIamge;
        meshMap = map;
    }

    public void setMeshMap(MeshMap<Double[]> map){
        meshMap = map;
    }

    public void setImage(ImagePlus stack){
        mainImage = stack;
    }

    public ImagePlus createFlatImage(){
        IntStream intStream = IntStream.range(0, meshMap.getXsize());
        intStream.forEach(x ->{
            for(int y = 0; y < meshMap.getYsize(); y++){
            }
        });
        return null;
    }



    public ImagePlus createLineImageStackXZ(){ //とりあえずオッケイと思ったけど、チャンネルを考えてない、、、->ok, 次はできた画像のscale問題が。とりあえず何もせずがいいかも。->たぶん必要。

        Calibration cal = mainImage.getCalibration();

        double calx = cal.pixelWidth;
        double calz = cal.pixelDepth;
        double az = calz / calx;
        if(az == 0){
            az = 1;
        }
        double thickness = az;

        int nC = mainImage.getNChannels();

        int width = meshMap.getXsize();
        int height = mainImage.getNSlices();
        ArrayList<ImageProcessor> xzImageList = new ArrayList<>();
        IntStream intStream = IntStream.range(0, height);

        //long sTime = System.currentTimeMillis();

        for(int c = 0; c < nC; c++) {
            IntStream ySizeStream = IntStream.range(0, meshMap.getYsize());
            ySizeStream.forEach(i -> {
                ImageProcessor imp = mainImage.getProcessor().duplicate().resize(width, height );
                xzImageList.add(imp);
            });
        }

        //long initilalTime = System.currentTimeMillis();
        //System.out.println("initialT : " + (initilalTime - sTime));


        IntStream iStream = IntStream.range(0, height);
        iStream.parallel().forEach(stackPosition ->{
            for(int c = 0; c < nC; c++) {
                int index = mainImage.getStackIndex(c + 1, stackPosition + 1, 1);
                ImageProcessor buffip = mainImage.getStack().getProcessor(index);
                for (int xzSlice = 0; xzSlice < (xzImageList.size() / nC); xzSlice++) {
                    int xzImagePosition = c + (xzSlice * 2);
                    for (int x = 0; x < width; x++) {
                        Double[] position = meshMap.getValue(x, xzSlice);
                        xzImageList.get(xzImagePosition).setf(x, stackPosition, (float) buffip.getInterpolatedValue(position[0], position[1]));
                    }
                }
            }
        });

        //long createXZtime = System.currentTimeMillis();
        //System.out.println("creatingTime : " + (createXZtime - initilalTime));


        IntStream afterProcessing = IntStream.range(0, xzImageList.size());
        afterProcessing.parallel().forEach(i ->{
            ImageProcessor imp = xzImageList.get(i);
            GaussianBlur gb_filter = new GaussianBlur();
            ImageProcessor resizedP = imp.resize(imp.getWidth(), (int)Math.round((imp.getHeight() * thickness)));
            xzImageList.set(i, resizedP);
            gb_filter.blurGaussian(resizedP, 0, (thickness * 0.4), 0.02);
        });

        //long afterProcessTime = System.currentTimeMillis();
        //System.out.println("afterProcessTime : " + (afterProcessTime - createXZtime));



        ImageStack buffStack = new ImageStack(width, (int)Math.round((height * thickness)));
        xzImageList.forEach(ip ->{
            buffStack.addSlice(ip);
        });

        ImagePlus result = new ImagePlus();
        result.setStack(buffStack);

        result.setDimensions(nC, (buffStack.size() / nC), 1);
        Calibration calibration = mainImage.getCalibration();
        double ycal = calibration.pixelHeight;
        double zcal = calibration.pixelDepth;
        calibration.pixelDepth = ycal;
        //calibration.pixelHeight = ycal / (result.getHeight() / (height * thickness));
        calibration.pixelHeight = zcal / (result.getHeight() / (double)height); //どっちも同じ値と思われるため計算が少ないこっちを採用

        result.setCalibration(calibration);

        //long eTime = System.currentTimeMillis();
        //System.out.println("endTime : " + (eTime - sTime));

        return result;
    }


    public FloatPolygon getFloatLineRow(int yIndex){
        //Double[] = [x, y, z]
        List<Double[]> buffList = meshMap.getRowAsList(yIndex);
        float[] xpoints = new float[buffList.size()];
        float[] ypoints = new float[buffList.size()];
        for(int n = 0; n < buffList.size(); n++){
            xpoints[n] = buffList.get(n)[0].floatValue();
            ypoints[n] = buffList.get(n)[1].floatValue();
        }
        FloatPolygon result = new FloatPolygon(xpoints, ypoints);
        return  result;

    }

    public FloatPolygon getFloatLineCol(int xIndex){
        //Double[] = [x, y, z]
        List<Double[]> buffList = meshMap.getColumnAsList(xIndex);
        float[] xpoints = new float[buffList.size()];
        float[] ypoints = new float[buffList.size()];
        for(int n = 0; n < buffList.size(); n++){
            xpoints[n] = buffList.get(n)[0].floatValue();
            ypoints[n] = buffList.get(n)[1].floatValue();
        }
        FloatPolygon result = new FloatPolygon(xpoints, ypoints);
        return  result;
    }


    public ImagePlus getXZcenterImage(){
        float[] centerPointsX = new float[meshMap.getXsize()];
        float[] centerPointsY = new float[meshMap.getXsize()];

        for(int x = 0; x < meshMap.getXsize(); x++){
            PolygonRoi proi = new PolygonRoi(this.getFloatLineCol(x), PolygonRoi.POLYLINE);
            proi.fitSpline();
            FloatPolygon interpolatedPolygon = proi.getInterpolatedPolygon();
            centerPointsX[x] = interpolatedPolygon.xpoints[(interpolatedPolygon.npoints/2)];
            centerPointsY[x] = interpolatedPolygon.ypoints[(interpolatedPolygon.npoints/2)];
        }
        PolygonRoi centerLine = new PolygonRoi(centerPointsX, centerPointsY, PolygonRoi.POLYLINE);
        centerLine.fitSpline();
        CreateCrossSectionImage createCrossSectionImage = new CreateCrossSectionImage(mainImage);
        ImagePlus resultImage = createCrossSectionImage.createCrossSectionImage(centerLine);
        creatingTime = createCrossSectionImage.getCreatingTime();

        return resultImage;
    }

    public ImagePlus getYZcenterImage(){
        float[] centerPointsX = new float[meshMap.getYsize()];
        float[] centerPointsY = new float[meshMap.getYsize()];

        for(int y = 0; y < meshMap.getYsize(); y++){
            PolygonRoi proi = new PolygonRoi(this.getFloatLineRow(y), PolygonRoi.POLYLINE);
            proi.fitSpline();
            FloatPolygon interpolatedPolygon = proi.getInterpolatedPolygon();
            centerPointsX[y] = interpolatedPolygon.xpoints[(interpolatedPolygon.npoints/2)];
            centerPointsY[y] = interpolatedPolygon.ypoints[(interpolatedPolygon.npoints/2)];
        }
        PolygonRoi centerLine = new PolygonRoi(centerPointsX, centerPointsY, PolygonRoi.POLYLINE);
        centerLine.fitSpline();
        CreateCrossSectionImage createCrossSectionImage = new CreateCrossSectionImage(mainImage);
        createCrossSectionImage.convertVertical(true);
        ImagePlus resultImage = createCrossSectionImage.createCrossSectionImage(centerLine);
        creatingTime = createCrossSectionImage.getCreatingTime();


        return resultImage;
    }

    public ImagePlus rotateImage(ImagePlus img){ //やっぱり煩雑。CrossSectionViewerの方にYZバージョン作るほうがいいかも

        ImageStack imageStack = new ImageStack(img.getHeight(), img.getWidth());
        for(int i = 0; i < img.getStackSize(); i++){
            img.getStack().getProcessor(i + 1 ).flipHorizontal();
            imageStack.addSlice(img.getStack().getProcessor(i + 1 ).rotateLeft());
        }
        ImagePlus result = new ImagePlus();
        result.setStack("YZ-Image", imageStack);
        result.setCalibration(img.getCalibration());
        result.setDimensions(img.getNChannels(), img.getNSlices(), img.getNFrames());
        return result;
    }



    public ImagePlus getXZimage(int yIndex){

        PolygonRoi proi = new PolygonRoi(this.getFloatLineRow(yIndex), PolygonRoi.POLYLINE);
        proi.fitSpline();

        return this.getXZimage(proi);
    }


    public ImagePlus getYZimage(int xIndex){
        PolygonRoi proi = new PolygonRoi(this.getFloatLineCol(xIndex), PolygonRoi.POLYLINE);
        proi.fitSpline();

        return this.getYZimage(proi);
    }

    public ImagePlus getXZimage(Roi roi){

        CreateCrossSectionImage createCrossSectionImage = new CreateCrossSectionImage(mainImage);
        createCrossSectionImage.setKeepPointNum(numKeep);
        ImagePlus resultImage = createCrossSectionImage.createCrossSectionImage(roi);
        creatingTime = createCrossSectionImage.getCreatingTime();

        return resultImage;
    }

    public ImagePlus getYZimage(Roi roi){

        CreateCrossSectionImage createCrossSectionImage = new CreateCrossSectionImage(mainImage);
        createCrossSectionImage.convertVertical(true);
        createCrossSectionImage.setKeepPointNum(numKeep);
        ImagePlus resultImage = createCrossSectionImage.createCrossSectionImage(roi);
        creatingTime = createCrossSectionImage.getCreatingTime();
        return resultImage;
    }


    public ImagePlus getFlattenedImage(int thickness, int priority){
        MeshManager meshManager = new MeshManager(meshMap);

        if(priority == 1) {
            return this.getFlattenedImageXZ(meshManager.copy(), thickness);
        }else if(priority == 2){
            return this.getFlattenedImageYZ(meshManager.copy(), thickness);
        }else{
            return this.getFlattenedImage(meshManager.copy(), thickness);
        }

    }

    public ImagePlus getFlattenedImage(MeshMap<Double[]> mesh, int thickness){

        MeshManager meshManager = new MeshManager(mesh);
        //meshManager.translateXYZ(0, 0,  (thickness / 2.0));
        //meshManager.traslateDeep(thickness); //20191030 これがうまくいかない

        /*
        ArrayList<ArrayList<Double>> dataArray = new ArrayList();
        for(int i = 0; i < thickness; i++){
            //ArrayList<Double> buff = new ArrayList<>();
            //dataArray.add(buff);
            dataArray.add(new ArrayList<>());
        }
        */

        //　このやり方ではダメそう。全てに同じ数入るバグ。
        // 一回stack画像を作ってそれをもとにresliceがいいかも。

        numKeep = true;
        ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, ArrayList<Double>>> hashMap = new ConcurrentHashMap<>();
        for(int i = 0; i < thickness; i++){
            ConcurrentHashMap<Integer, ArrayList<Double>> buff = new ConcurrentHashMap<>();
            hashMap.put(i, buff);
            for(int c = 0; c < mainImage.getNChannels(); c++){
                ArrayList<Double> buffa = new ArrayList<>();
                hashMap.get(i).put(c, buffa);
            }

        }

        for(int y = 0; y < mesh.getYsize(); y++){
            IJ.showProgress(y, mesh.getYsize());
            ImagePlus xzImage = this.getXZimage(y);
            PolygonRoi proi = new PolygonRoi(meshManager.getXZpolygonKeepXpoints(y), PolygonRoi.FREELINE);
            for(int c = 0; c < mainImage.getNChannels(); c++) {
                ImagePlus singleChannel = new ImagePlus();
                singleChannel.setProcessor(xzImage.getStack().getProcessor(c+1));
                singleChannel.setRoi(proi);
                ImageProcessor ip = this.straightenLine(singleChannel, thickness);

                for (int i = 0; i < ip.getHeight(); i++) {
                    double[] ddata = ip.getLine(0, i, ip.getWidth() - 1, i);
                    for (int n = 0; n < ddata.length; n++) {
                        hashMap.get(i).get(c).add(ddata[n]);
                    }
                }
            }
        }

        int height = mesh.getYsize();
        int width = hashMap.get(0).get(0).size() / height;

        ImageStack imageStack = new ImageStack(width, height);

        for(int i = 0; i < hashMap.size(); i++){
            for(int c = 0; c < mainImage.getNChannels(); c++) {
                double[] ddat = hashMap.get(i).get(c).stream().mapToDouble(d -> d).toArray();
                //System.out.println("ddat size"  + ddat.length);
                ImageProcessor ip = new FloatProcessor(width, height, ddat);
                imageStack.addSlice(ip);
            }
        }


        ImagePlus result = new ImagePlus();
        result.setStack("FlattenedIamge", imageStack);
        result.setDimensions(mainImage.getNChannels(), thickness, 1);
        if(mainImage.getNChannels() > 1){
            return HyperStackConverter.toHyperStack(result, mainImage.getNChannels(), thickness, 1);
        }else{
            return result;
        }
    }


    public ImagePlus getFlattenedImageXZ(MeshMap<Double[]> mesh, int thickness){
        MeshManager meshManager = new MeshManager(mesh);
        numKeep = true;

        ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, ArrayList<Double>>> hashMap = new ConcurrentHashMap<>();//<z,<c, values>>
        ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, ImageProcessor>> ipHashMap = new ConcurrentHashMap<>();//<y, <c, ip>>


        for(int y = 0; y < mesh.getYsize(); y++){
            ConcurrentHashMap<Integer, ImageProcessor> ipBuff = new ConcurrentHashMap<>();
            ipHashMap.put(y, ipBuff);

        }



        for(int i = 0; i < thickness; i++){
            ConcurrentHashMap<Integer, ArrayList<Double>> buff = new ConcurrentHashMap<>();
            hashMap.put(i, buff);

            for(int c = 0; c < mainImage.getNChannels(); c++){
                ArrayList<Double> buffa = new ArrayList<>();
                hashMap.get(i).put(c, buffa);
            }

        }

        //まず、各lineでXZ画像を作り、LineRoiからStraighten画像ををつくる。 -> 画像のサイズがそれぞれで異なるため
        //ついでに最大幅も取る

        int maxWidth = 0;
        for(int y = 0; y < mesh.getYsize(); y++){
            IJ.showProgress(y, mesh.getYsize());
            ImagePlus xzImage = this.getXZimage(y); //ここチェック！

            PolygonRoi proi = new PolygonRoi(meshManager.getXZpolygonKeepXpoints(y), PolygonRoi.FREELINE); //ここも

            for(int c = 0; c < mainImage.getNChannels(); c++) {
                ImagePlus singleChannel = new ImagePlus();
                singleChannel.setProcessor(xzImage.getStack().getProcessor(c+1));
                singleChannel.setRoi(proi);
                //ImageProcessor ip = this.straightenLine(singleChannel, thickness); //なぜ全部同じ長さになるのか？上記KeepXpointsのせいか？
                Straightener straightener = new Straightener();
                ImageProcessor ip = straightener.straightenLine(singleChannel, thickness);

                if(maxWidth < ip.getWidth()){
                    maxWidth = ip.getWidth();
                }
                ipHashMap.get(y).put(c, ip);
            }
        }


        //size 合わせ
        ImageStack testStack = new ImageStack(maxWidth, thickness);
        final int max = maxWidth;
        ipHashMap.forEach((k, v) ->{
            for(int c = 0; c < mainImage.getNChannels(); c++) {
                ImageProcessor buffIp = ipHashMap.get(k).get(c);
                double offset = (max - buffIp.getWidth()) / 2.0;
                CanvasResizer canvasResizer = new CanvasResizer();
                ImageProcessor resizedIp = canvasResizer.expandImage(buffIp, max, buffIp.getHeight(), 0, 0);
                resizedIp.setInterpolate(true);
                resizedIp.setInterpolationMethod(ImageProcessor.BICUBIC);
                resizedIp.translate(offset, 0);
                ipHashMap.get(k).replace(c, resizedIp);
                testStack.addSlice(resizedIp);

                //ここで上のようにhashMapにデータを入れ直せばうごくはず
                for (int i = 0; i < resizedIp.getHeight(); i++) {
                    double[] ddata = resizedIp.getLine(0, i, resizedIp.getWidth() - 1, i);
                    for (int n = 0; n < ddata.length; n++) {
                        hashMap.get(i).get(c).add(ddata[n]);
                    }
                }
            }
        });

        int height = mesh.getYsize();
        int width = hashMap.get(0).get(0).size() / height;

        ImageStack imageStack = new ImageStack(width, height);

        for(int i = 0; i < hashMap.size(); i++){
            for(int c = 0; c < mainImage.getNChannels(); c++) {
                double[] ddat = hashMap.get(i).get(c).stream().mapToDouble(d -> d).toArray();
                //System.out.println("ddat size"  + ddat.length);
                ImageProcessor ip = new FloatProcessor(width, height, ddat);
                imageStack.addSlice(ip);
            }
        }


        ImagePlus result = new ImagePlus();
        result.setStack("FlattenedIamgeXZ", imageStack);
        result.setDimensions(mainImage.getNChannels(), thickness, 1);

        if(mainImage.getNChannels() > 1){
            return HyperStackConverter.toHyperStack(result, mainImage.getNChannels(), thickness, 1);
        }else{
            return result;
        }

    }


    public ImagePlus getFlattenedImageYZ(MeshMap<Double[]> mesh, int thickness){
        MeshManager meshManager = new MeshManager(mesh);
        numKeep = true;

        ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, ArrayList<Double>>> hashMap = new ConcurrentHashMap<>();//<z,<c, values>>
        ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, ImageProcessor>> ipHashMap = new ConcurrentHashMap<>();//<x, <c, ip>>


        for(int x = 0; x < mesh.getXsize(); x++){
            ConcurrentHashMap<Integer, ImageProcessor> ipBuff = new ConcurrentHashMap<>();
            ipHashMap.put(x, ipBuff);

        }



        for(int i = 0; i < thickness; i++){
            ConcurrentHashMap<Integer, ArrayList<Double>> buff = new ConcurrentHashMap<>();
            hashMap.put(i, buff);

            for(int c = 0; c < mainImage.getNChannels(); c++){
                ArrayList<Double> buffa = new ArrayList<>();
                hashMap.get(i).put(c, buffa);
            }

        }

        //まず、各lineでXZ画像を作り、LineRoiからStraighten画像ををつくる。 -> 画像のサイズがそれぞれで異なるため
        //ついでに最大幅も取る

        int maxWidth = 0;
        for(int x = 0; x < mesh.getXsize(); x++){
            IJ.showProgress(x, mesh.getXsize());
            ImagePlus yzImage = this.getYZimage(x); //ここチェック！

            PolygonRoi proi = new PolygonRoi(meshManager.getYZpolygonKeepYpoints(x), PolygonRoi.FREELINE); //ここも

            for(int c = 0; c < mainImage.getNChannels(); c++) {
                ImagePlus singleChannel = new ImagePlus();
                singleChannel.setProcessor(yzImage.getStack().getProcessor(c+1));
                singleChannel.setRoi(proi);


                //ImageProcessor ip = this.straightenLine(singleChannel, thickness); //なぜ全部同じ長さになるのか？上記KeepXpointsのせいか？
                Straightener straightener = new Straightener();
                ImageProcessor ip = straightener.straightenLine(singleChannel, thickness);

                if(maxWidth < ip.getWidth()){
                    maxWidth = ip.getWidth();
                }
                ipHashMap.get(x).put(c, ip);
            }
        }


        //size 合わせ
        ImageStack testStack = new ImageStack(maxWidth, thickness);
        final int max = maxWidth;
        ipHashMap.forEach((k, v) ->{
            for(int c = 0; c < mainImage.getNChannels(); c++) {
                ImageProcessor buffIp = ipHashMap.get(k).get(c);
                double offset = (max - buffIp.getWidth()) / 2.0;
                CanvasResizer canvasResizer = new CanvasResizer();
                ImageProcessor resizedIp = canvasResizer.expandImage(buffIp, max, buffIp.getHeight(),0, 0);
                resizedIp.setInterpolate(true);
                resizedIp.setInterpolationMethod(ImageProcessor.BICUBIC);
                resizedIp.translate(offset, 0.0);
                ipHashMap.get(k).replace(c, resizedIp);
                testStack.addSlice(resizedIp);

                //ここで上のようにhashMapにデータを入れ直せばうごくはず
                for (int i = 0; i < resizedIp.getHeight(); i++) {
                    double[] ddata = resizedIp.getLine(0, i, resizedIp.getWidth() - 1, i);
                    for (int n = 0; n < ddata.length; n++) {
                        hashMap.get(i).get(c).add(ddata[n]);
                    }
                }
            }
        });



        int width = mesh.getXsize();
        int height = hashMap.get(0).get(0).size() / width;

        ImageStack imageStack = new ImageStack(width, height);

        for(int i = hashMap.size()-1; i > -1 ; i--){
            for(int c = 0; c < mainImage.getNChannels(); c++) {
                double[] ddat = hashMap.get(i).get(c).stream().mapToDouble(d -> d).toArray();
                //System.out.println("ddat size"  + ddat.length);
                ImageProcessor ip = new FloatProcessor(height, width, ddat);
                ImageProcessor rotated = ip.rotateRight();
                rotated.flipHorizontal();
                imageStack.addSlice(rotated);
            }
        }


        ImagePlus result = new ImagePlus();
        result.setStack("FlattenedIamgeYZ", imageStack);

        result.setDimensions(mainImage.getNChannels(), thickness, 1);

        if(mainImage.getNChannels() > 1){
            return HyperStackConverter.toHyperStack(result, mainImage.getNChannels(), thickness, 1);
        }else{
            return result;
        }
    }




    // from Straightener, これ用に改造
    public ImageProcessor straightenLine(ImagePlus imp, int width) {
        Roi tempRoi = imp.getRoi();
        if (!(tempRoi instanceof PolygonRoi))
            return null;
        PolygonRoi roi = (PolygonRoi)tempRoi;
        if (roi==null)
            return null;
        if (roi.getState()==Roi.CONSTRUCTING)
            roi.exitConstructingMode();
        if (roi.isSplineFit())
            roi.removeSplineFit();
        int type = roi.getType();
        int n = roi.getNCoordinates();
        double len = roi.getLength();
        //roi.fitSplineForStraightening();
        if (roi.getNCoordinates()<2)
            return null;
        FloatPolygon p = roi.getFloatPolygon();
        n = p.npoints;
        ImageProcessor ip = imp.getProcessor();
        ImageProcessor ip2 = new FloatProcessor(n, width);
        //ImageProcessor distances = null;
        //if (IJ.debugMode)  distances = new FloatProcessor(n, 1);
        float[] pixels = (float[])ip2.getPixels();
        double x1, y1;
        double x2=p.xpoints[0]-(p.xpoints[1]-p.xpoints[0]);
        double y2=p.ypoints[0]-(p.ypoints[1]-p.ypoints[0]);
        if (width==1)
            ip2.putPixelValue(0, 0, ip.getInterpolatedValue(x2, y2));
        for (int i=0; i<n; i++) {
            x1=x2; y1=y2;
            x2=p.xpoints[i]; y2=p.ypoints[i];
            //if (distances!=null) distances.putPixelValue(i, 0, (float)Math.sqrt((x2-x1)*(x2-x1)+(y2-y1)*(y2-y1)));
            if (width==1) {
                ip2.putPixelValue(i, 0, ip.getInterpolatedValue(x2, y2));
                continue;
            }
            double dx = x2-x1;
            double dy = y1-y2;
            double length = (float)Math.sqrt(dx*dx+dy*dy);
            dx /= length;
            dy /= length;
            //IJ.log(i+"  "+x2+"  "+dy+"  "+(dy*width/2f)+"   "+y2+"  "+dx+"   "+(dx*width/2f));
            double x = x2-dy*width/2.0;
            double y = y2-dx*width/2.0;
            int j = 0;
            int n2 = width;
            do {
                ip2.putPixelValue(i, j++, ip.getInterpolatedValue(x, y));;
                //ip.drawDot((int)x, (int)y);
                x += dy;
                y += dx;
            } while (--n2>0);
        }
        if (type==Roi.FREELINE)
            roi.removeSplineFit();
        else
            imp.draw();
        if (imp.getBitDepth()!=24) {
            ip2.setColorModel(ip.getColorModel());
            ip2.resetMinAndMax();
        }
        return ip2;
    }

}
