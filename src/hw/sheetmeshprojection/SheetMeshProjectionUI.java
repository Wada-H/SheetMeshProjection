package hw.sheetmeshprojection;

import ij.*;
import ij.gui.*;
import ij.plugin.Duplicator;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Mesh;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.GeneralPath;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * Describe the process that is the starting point in the UI part.
 *
 * @author WADA Housei
 */

public class SheetMeshProjectionUI extends AnchorPane implements ItemListener, ImageListener, MouseListener, MouseMotionListener, MouseWheelListener, WindowListener, KeyListener{

    ImagePlus mainImage;
    ImagePlus forCrossSectionImage; //元画像のDuplicate
    ImagePlus currentTImage; // 現在のTのXYCZ画像

    ImageCanvas ic;
    ImageCanvas icXZ;
    ImageCanvas icYZ;

    OverlaySM currentOverlay = new OverlaySM();

    Color currentStrokColor = Color.YELLOW;

    int chSize;
    LUT old_lut; //for single?

    //ROI保存用//

    String imageFileName;
    String imageFileDir;
    File saveFileName;
    Scene scene;

    //ROI読み込み用//
    File loadFile;


    // xz, yz image //
    CompositeImage xzImage;
    CompositeImage yzImage;

    ImagePlus xzImageSingle; //channel = 1の場合
    ImagePlus yzImageSingle;


    String[] viewModeList = {"Shape", "XLine", "YLine", "Points"};
    int viewMode = 0; //viewModeListより

    ConcurrentHashMap<Integer, MeshMap<Double[]>> meshTlist = new ConcurrentHashMap<>();


    //完全な補完座標を保持したMeshMap// -> この座標値のpixel値を用いて矩形画像を作ることでx,yの引き伸ばし画像が作れる。(zの扱いはどうするか？)

    MeshMap<Double[]> getInterpolatedMapForCol;
    MeshMap<Double[]> getInterpolatedMapForRow;

    MeshMap<Double[]> interpolatedMap;


    //ドラッグ管理用
    double clickedPointX = 0.0;
    double clickedPointY = 0.0;
    double draggedPointX = 0.0;
    double draggedPointY = 0.0;


    @FXML public Button sizeChangeButton;
    @FXML public Button createNewMeshButton;
    @FXML public Button autoFitButton;
    @FXML public Button checkMeshButton;
    @FXML public Button createFlatImageButton;
    @FXML public Button saveButton;
    @FXML public Button loadButton;

    @FXML public TextField meshSizeWidth;

    @FXML public TextField meshSizeHeight;
    @FXML public TextField meshStepSize;
    @FXML public TextField meshThickness;

    @FXML public TextField autofitThreshold;

    @FXML public CheckBox forZCheckBox;

    public SheetMeshProjectionUI(ImagePlus ip){

        mainImage = ip;
        forCrossSectionImage = mainImage.duplicate();
        chSize = mainImage.getNChannels();
        ic = mainImage.getCanvas();

        if(mainImage.getOriginalFileInfo() != null){
            imageFileName = mainImage.getOriginalFileInfo().fileName;
            imageFileDir = mainImage.getOriginalFileInfo().directory;

        }else{
            imageFileName = mainImage.getTitle();
            imageFileDir = System.getProperty("user.home");

        }

        // 何かの拍子でnullになると保存ができなくなるので保険 //
        if(imageFileName == null) {
            imageFileName = "NewData";
        }
        if(imageFileDir == null) {
            imageFileDir = "./";
        }
        //



    }


    public JFXPanel getFXML(){
        Pane result; // = new Pane();
        JFXPanel jfxPanel = new JFXPanel();
        FXMLLoader loader = new FXMLLoader();
        loader.setRoot(this);
        loader.setController(this);
        //loader.setController(new Test()); //こんな書き方でもいける。ただし、今回の場合は分離するほうが面倒

        try {
            //result = FXMLLoader.load(getClass().getResource(fileName));
            result = loader.load(getClass().getResourceAsStream("ui.fxml"));

            scene = new Scene(result,result.getPrefWidth(),result.getPrefHeight());
            jfxPanel.setScene(scene);

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return jfxPanel;
    }


    public void setListener(){
        ic.addMouseListener(this);
        ic.addMouseMotionListener(this);
        ic.addMouseWheelListener(this);

        ic.addKeyListener(this);

        ImagePlus.addImageListener(this);

        icXZ.addMouseListener(this);
        icYZ.addMouseListener(this);

        mainImage.getWindow().addWindowListener(this);

    }

    public void removeListener() {
        ic.removeMouseListener(this);
        ic.removeMouseMotionListener(this);
        ic.removeMouseWheelListener(this);

        ic.removeKeyListener(this);

        ImagePlus.removeImageListener(this);

        icXZ.removeMouseListener(this);
        icYZ.removeMouseListener(this);

        mainImage.getWindow().removeWindowListener(this);


    }


    public void createMesh(int xSize, int ySize, int xyInterval){ //interval間隔の正方形メッシュを作る
        Double[] b = new Double[3];

        MeshMap<Double[]> buff = new MeshMap<>();
        buff.createMesh(xSize, ySize, b);

        for(int x = 0; x < xSize; x++){
            ConcurrentHashMap<Integer, Double[]> yMap = new ConcurrentHashMap<>();
            for(int y = 0; y < ySize; y++){
                Double[] values = new Double[3];
                values[0] = Double.valueOf(x * xyInterval);
                values[1] = Double.valueOf(y * xyInterval);
                values[2] = 1.0;

                yMap.put(y, values);
            }
            buff.setColumn(x, yMap); //このやり方でないと何故かDouble[]の値が変化してしまう。おそらくスコープあたりのバグではないかと思われる。
        }

        meshTlist.put(mainImage.getT(), buff);

    }


    // GeneralPathを使用してsplineを表現しようとすると難しいかも。 -> LineRoi + splinefitを駆使する方法が良いかも。
    public void showMesh(MeshMap<Double[]> mesh){ //行、列ごとにfitsplineで結んだlineRoiで表示？ あくまでも見せるだけとするか。
        currentOverlay.clear();
        GeneralPath gpath = new GeneralPath();

        for(int x = 0; x < mesh.getXsize(); x++){
            gpath.moveTo(mesh.getValue(x, 0)[0], mesh.getValue(x, 0)[1]);
            for(int y = 0; y < mesh.getYsize(); y++){
                gpath.lineTo(mesh.getValue(x, y)[0], mesh.getValue(x, y)[1]);
            }
        }


        for(int y = 0; y < mesh.getYsize(); y++){
            gpath.moveTo(mesh.getValue(0, y)[0], mesh.getValue(0, y)[1]);
            for(int x = 0; x < mesh.getXsize(); x++){
                gpath.lineTo(mesh.getValue(x, y)[0], mesh.getValue(x, y)[1]);
            }
        }

        ShapeRoi roi = new ShapeRoi(gpath); //FloatPolygon化すると各点が2つずつある。上記の線を書く際の弊害と思われる。

        currentOverlay.add(roi);
        currentOverlay.setStrokeColor(currentStrokColor);
        currentOverlay.drawLabels(true);
        mainImage.setOverlay(null);
        mainImage.setOverlay(currentOverlay);
    }


    public void showMeshAllShape(MeshMap<Double[]> mesh){
        currentOverlay.clear();



        ShapeRoi roi = this.getMeshAllShape(mesh);

        currentOverlay.add(roi);
        currentOverlay.setStrokeColor(currentStrokColor);
        currentOverlay.drawLabels(true);
        mainImage.setOverlay(null);
        mainImage.setOverlay(currentOverlay);


    }

    public ShapeRoi getMeshAllShape(MeshMap<Double[]> mesh){
        GeneralPath gpath = new GeneralPath();

        MeshManager meshManager = new MeshManager(mesh);

        System.out.println("showMeshAllShape mesh size : " + mesh.getXsize() + ", " + mesh.getYsize());
        for(int x = 0; x < mesh.getXsize(); x++){
            PolygonRoi rowPolygon = new PolygonRoi(meshManager.getColumnByFloatPolygon(x), PolygonRoi.POLYLINE);
            rowPolygon.fitSpline();

            for(int i = 0; i < rowPolygon.getNCoordinates() - 1; i++){
                gpath.moveTo(rowPolygon.getFloatPolygon().xpoints[i], rowPolygon.getFloatPolygon().ypoints[i]);
                gpath.lineTo(rowPolygon.getFloatPolygon().xpoints[i+1], rowPolygon.getFloatPolygon().ypoints[i+1]);
            }
        }

        for(int y = 0; y < mesh.getYsize(); y++){
            PolygonRoi colPolygon = new PolygonRoi(meshManager.getRowByFloatPolygon(y), PolygonRoi.POLYLINE);
            colPolygon.fitSpline();

            for(int i = 0; i < colPolygon.getNCoordinates() - 1; i++){
                gpath.moveTo(colPolygon.getFloatPolygon().xpoints[i], colPolygon.getFloatPolygon().ypoints[i]);
                gpath.lineTo(colPolygon.getFloatPolygon().xpoints[i+1], colPolygon.getFloatPolygon().ypoints[i+1]);
            }
        }

        ShapeRoi roi = new ShapeRoi(gpath);
        return roi;
    }



    public void showMeshByLineX(MeshMap<Double[]> mesh){
        currentOverlay.clear();
        for(int y = 0; y < mesh.getYsize(); y++){
            currentOverlay.add(this.getPolyLineRow(mesh, y));
        }

        currentOverlay.drawLabels(true);
        currentOverlay.setStrokeColor(currentStrokColor);

        mainImage.setOverlay(null);
        mainImage.setOverlay(currentOverlay);

        MeshManager meshManager = new MeshManager(meshTlist.get(mainImage.getT()));
        ArrayList<Integer> dRow = meshManager.getMaxDistanceRowMax();
        dRow.forEach(value ->{ System.out.println("dRow " + value);});
    }

    public void showMeshByLineY(MeshMap<Double[]> mesh){
        currentOverlay.clear();

        for(int x = 0; x < mesh.getXsize(); x++){

            currentOverlay.add(this.getPolyLineCol(mesh, x));
        }


        currentOverlay.drawLabels(true);
        currentOverlay.setStrokeColor(currentStrokColor);

        mainImage.setOverlay(null);
        mainImage.setOverlay(currentOverlay);

        MeshManager meshManager = new MeshManager(meshTlist.get(mainImage.getT()));
        ArrayList<Integer> dCol = meshManager.getMaxDistanceColMax();
        dCol.forEach(value ->{ System.out.println("dCol " + value);});
    }

    public void showMeshByPoints(MeshMap<Double[]> mesh){
        currentOverlay.clear();

        for(int x = 0; x < mesh.getXsize(); x++){
            for(int y = 0; y < mesh.getYsize(); y++){
                PointRoi buff = new PointRoi(mesh.getValue(x, y)[0], mesh.getValue(x, y)[1]);
                currentOverlay.add(buff);
            }

        }

        currentOverlay.drawLabels(true);
        currentOverlay.setStrokeColor(currentStrokColor);

        mainImage.setOverlay(null);
        mainImage.setOverlay(currentOverlay);
    }



    public void changeViewMode(){
        mainImage.killRoi();
        switch (viewMode) {
            case 0:
                this.showMeshAllShape(meshTlist.get(mainImage.getT()));
                break;
            case 1:
                this.showMeshByLineY(meshTlist.get(mainImage.getT()));
                break;
            case 2:
                this.showMeshByLineX(meshTlist.get(mainImage.getT()));
                break;
            case 3:
                this.showMeshByPoints(meshTlist.get(mainImage.getT()));
                break;
            default:
                System.out.println("デフォルト");
                break;
        }

        mainImage.updateAndDraw();
    }


    // とりあえずテスト用 Tが複数あるつもりで//
    public void meshTest(){

        this.createMesh(Integer.valueOf(meshSizeWidth.getText()), Integer.valueOf(meshSizeHeight.getText()), Integer.valueOf(meshStepSize.getText()));
        this.addValueXY(meshTlist.get(mainImage.getT()), 0, 0);
        //Double[] v = {200.0, 200.0, 1.0};
        //meshTlist.get(mainImage.getT()).setValue(4, 4, v);
        //this.showMesh(meshTlist.get(mainImage.getT()));
        this.showMeshAllShape(meshTlist.get(mainImage.getT()));

        this.createCrossSectionImageFirst();

    }


    public void createCrossSectionImageFirst(){
        // 元画像よりも大きくなる想定ができるため、キャンバスサイズを固定するのは不可。
        // 最初と最後の点より、中央点が求まるのでMeshRoi時にはこれを使用してcross sectionを表示。FloatRoi, filtsplineでのnpoints, 半分の位置
        // 各line表示の場合は選択されたlineおよび選択中の点の位置からもう一方の画像を作る。初期表示はMeshRoi時と同様に中央画像でいいかも。


        if(viewMode == 0){ //Shape
            Duplicator duplicator = new Duplicator();
            currentTImage = duplicator.run(forCrossSectionImage, 1, mainImage.getNChannels(), 1, mainImage.getNSlices(), mainImage.getT(), mainImage.getT());
            ImageCreator imageCreator = new ImageCreator(currentTImage, meshTlist.get(mainImage.getT()));

            if(mainImage.getNChannels() > 1) {

                xzImage = (CompositeImage) imageCreator.getXZcenterImage();
                yzImage = (CompositeImage) imageCreator.getYZcenterImage();

                xzImage.show();
                yzImage.show();

                ImageWindow impWindow = mainImage.getWindow();
                xzImage.getWindow().setLocation(impWindow.getX(), (impWindow.getY() + impWindow.getHeight()));
                yzImage.getWindow().setLocation(impWindow.getX() + impWindow.getWidth(), impWindow.getY());

                icXZ = xzImage.getCanvas();
                icYZ = yzImage.getCanvas();
            }else{

                xzImageSingle = imageCreator.getXZcenterImage();
                yzImageSingle = imageCreator.getYZcenterImage();

                xzImageSingle.show();
                yzImageSingle.show();

                ImageWindow impWindow = mainImage.getWindow();
                xzImageSingle.getWindow().setLocation(impWindow.getX(), (impWindow.getY() + impWindow.getHeight()));
                yzImageSingle.getWindow().setLocation(impWindow.getX() + impWindow.getWidth(), impWindow.getY());

                icXZ = xzImageSingle.getCanvas();
                icYZ = yzImageSingle.getCanvas();

            }

        }else if(viewMode == 1){ //XLine

        }else if(viewMode == 2){ //YLine

        }else if(viewMode == 3){ //Points

        }else{
            //なにもしない
        }

    }



    private ImagePlus[] createCrossSectionImage(){

        ImageCreator imageCreator = new ImageCreator(currentTImage, meshTlist.get(mainImage.getT()));

        ImagePlus buffImageXZ = new ImagePlus();
        ImagePlus buffImageYZ = new ImagePlus();

        if(viewMode == 0){
            buffImageXZ = imageCreator.getXZcenterImage();
            buffImageYZ = imageCreator.getYZcenterImage();
        }else if(viewMode == 1) {
            buffImageXZ = imageCreator.getXZcenterImage();
            buffImageYZ = imageCreator.getYZimage(mainImage.getRoi());
        }else if(viewMode == 2){
            buffImageXZ = imageCreator.getXZimage(mainImage.getRoi());
            buffImageYZ = imageCreator.getYZcenterImage();

        }else if(viewMode == 3){

        }


        delay_time = (int)imageCreator.creatingTime;

        //System.out.println("delayTime : " + delay_time);

        //if(csi.isComposite()){
        //    delay_time = delay_time + 10;
        //}


        if((xzImage != null)&&(yzImage != null)){
            if(buffImageYZ.getHeight() != yzImage.getHeight()){
                delay_time = delay_time * 4;
            }

        }else if(xzImageSingle != null){
            if(buffImageXZ.getWidth() != xzImageSingle.getWidth()){
                delay_time = delay_time * 2;
            }
        }


        ImagePlus[] result = new ImagePlus[2];

        result[0] = buffImageXZ;
        result[1] = buffImageYZ;

        return result;

    }



    private long orth_timer = 0;
    int delay_time = 0;

    private void createImage(){
        //int delay_time = 5; //ms

        long now_time = System.currentTimeMillis();
        orth_timer = orth_timer - now_time;

        if(orth_timer <= 0){
            SheetMeshProjectionUI.CrossSectionThread cst = new SheetMeshProjectionUI.CrossSectionThread();
            cst.start();
            orth_timer = System.currentTimeMillis() + delay_time;

        }else{
            orth_timer = System.currentTimeMillis() + delay_time;

        }

    }


    private void setCrossSectionImage(){

        final String[] titles = {"X-Z", "Y-Z"};

        final ImagePlus[] xzyzImages = this.createCrossSectionImage();

        IntStream zParallelProcess = IntStream.range(0, xzyzImages.length);
        if(xzyzImages[0] != null) {

            zParallelProcess.parallel().forEach(i -> {
                ImagePlus[] zImages;

                if (chSize > 1) {
                     zImages = new ImagePlus[]{xzImage, yzImage};

                } else {
                    zImages = new ImagePlus[]{xzImageSingle, yzImageSingle};

                }

                if ((zImages[i] != null) && (zImages[i].isVisible())) {
                    //crossSectionImage.setImage(buffImage); //->CompositeImageで配列の長さ違いエラーが出る場合がある

                    // xz, yz のサイズが変わるのは許容しないといけない //

                    zImages[i].setStack(xzyzImages[i].getStack(), chSize, 1, 1);

                } else {
                    if(chSize > 1) {
                        CompositeImage ci_img = (CompositeImage) mainImage;
                        zImages[i] = new CompositeImage(xzyzImages[i], ci_img.getMode());
                        zImages[i].setTitle(titles[i]);
                    }else{
                        zImages[i] = new ImagePlus(titles[i], xzyzImages[i].getProcessor());
                    }
                }
                this.syncZYZ(mainImage, xzyzImages[i]);
            });



        }

        //this.updateAndDraw();
    }


    public void updateAndDraw(){
        if(xzImage != null){
            xzImage.updateAndDraw();
        }else{
            xzImageSingle.updateAndDraw();
        }

        if(yzImage != null){
            yzImage.updateAndDraw();
        }else{
            yzImageSingle.updateAndDraw();
        }
    }

    class CrossSectionThread extends Thread{
        public void run(){
            setCrossSectionImage();
        }
    }


    /*
    private void syncLut(ImagePlus donor, ImagePlus acceptor){

        LUT[] a_lut = ((CompositeImage)donor).getLuts();
        LUT[] b_lut = ((CompositeImage)acceptor).getLuts();

        boolean check = true;


        out:for(int i = 0; i < a_lut.length; i++){
            if(a_lut[i] != b_lut[i]){
                check = false;
                break out;
            }
        }

        if(((CompositeImage)donor).getMode() == IJ.GRAYSCALE){
            check = true;
        }

        if(check == false){
            ((CompositeImage)acceptor).setLuts(a_lut);
        }

    }
    */

    /*
    private void syncLut(ImagePlus donor, ImagePlus acceptor){
        LUT[] donorLut = donor.getLuts();
        LUT[] acceptorLut = acceptor.getLuts();
        boolean check = true;


        out:for(int i = 0; i < donorLut.length; i++){
            if(donorLut[i] != acceptorLut[i]){
                check = false;
                break out;
            }
        }

        if(check == false){

            for(int i = 0; i < donorLut.length; i++){
                //acceptor.getLuts()[i] = donorLut[i];
                acceptorLut[i] = donorLut[i];
            }

        }

        if(donor.isComposite()){
            int cc = donor.getC();
            ((CompositeImage)acceptor).setMode(((CompositeImage)donor).getMode());
            boolean[] donorActive = ((CompositeImage)donor).getActiveChannels();
            boolean[] acceptorActive = ((CompositeImage)acceptor).getActiveChannels();

            for(int i = 0; i < acceptorActive.length; i++){
                acceptorActive[i] = donorActive[i];
            }

            acceptor.setC(cc);
        }
    }

     */


    private void syncZYZ(ImagePlus donor, ImagePlus acceptor){
        int cc =  donor.getC();

        /// Mode 同期 ///
        if(donor.isComposite()){
            CompositeImage ci_imp = (CompositeImage)donor;
            CompositeImage crossSectionImage = (CompositeImage)acceptor;


            //Channelsで切り替えたときのみの動作。
            if(ci_imp.getMode() != crossSectionImage.getMode()) {
                crossSectionImage.setMode(ci_imp.getMode());

            }else{ // なにかうごいたときいつでも


            }

            ///ChannelsでComposite時にChannelを変えた場合の動作

            boolean[] active = ci_imp.getActiveChannels();
            boolean[] active_xz = crossSectionImage.getActiveChannels();

            for(int i = 0; i < active_xz.length; i++){
                active_xz[i] = active[i];
            }

            ///////////////

            // lut set //
            syncLut(ci_imp,crossSectionImage);

            crossSectionImage.setC(cc);

        }else{
            LUT imp_l = mainImage.getProcessor().getLut();
            LUT xz_l = donor.getProcessor().getLut();

            //if(xz_l != old_lut){
             //   donor.getProcessor().setLut(imp_l);
                //
                // crossSectionImage_single.updateAndDraw();
            //}

        }

    }

    private void syncLut(CompositeImage a, CompositeImage b){
        LUT[] a_lut = a.getLuts();
        LUT[] b_lut = b.getLuts();

        boolean check = true;


        out:for(int i = 0; i < a_lut.length; i++){
            if(a_lut[i] != b_lut[i]){
                check = false;
                break out;
            }
        }

        if(a.isComposite()) {

            if (a.getMode() == IJ.GRAYSCALE) {
                check = true;
            }

            if (check == false) {
                b.setLuts(a_lut);
            }
        }
    }



    private void changeCrossSectionImage(){
        if(mainImage.getRoi() != null) {
            Roi r = (Roi) mainImage.getRoi().clone();
            if (r.isLine()) {
                //if (r.getLength() > 0) {
                if(r.getContainedFloatPoints().npoints > 2){
                    if (chSize > 1) {
                        if ((xzImage != null) && (xzImage.isVisible())) {
                            this.createImage();
                        }
                    } else {
                        if ((xzImageSingle != null) && (xzImageSingle.isVisible())) {
                            this.createImage();
                        }
                    }

                }
            }
        }
    }

    private  void addValueXY(MeshMap<Double[]> mesh, double valueX, double valueY){
        mesh.mainMap.forEach(yList ->{
            yList.forEach(dArray ->{
                dArray[0] = dArray[0] + valueX;
                dArray[1] = dArray[1] + valueY;
            });
        });


    }

    public void createInterporatedMap(MeshMap<Double[]> meshMap){
        interpolatedMap = new MeshMap<>();
        MeshManager meshManager = new MeshManager(meshMap);
        interpolatedMap = meshManager.createInterporatedMap(forZCheckBox.isSelected());

    }

    public PolygonRoi getPolyLineRow(MeshMap mesh, int rowNum){

        ConcurrentHashMap<Integer, Double[]> xList = mesh.getRow(rowNum);
        float[] xPoints = new float[mesh.getXsize()];
        float[] yPoints = new float[mesh.getXsize()];
        for(int i = 0; i < mesh.getXsize(); i++){
            xPoints[i] = xList.get(i)[0].floatValue();
            yPoints[i] = xList.get(i)[1].floatValue();
        }

        PolygonRoi buff = new PolygonRoi(xPoints, yPoints, PolygonRoi.POLYLINE);
        buff.fitSpline();
        return buff;
    }


    public PolygonRoi getPolyLineCol(MeshMap mesh, int colNum){

        ConcurrentHashMap<Integer, Double[]> yList = mesh.getColumn(colNum);
        float[] xPoints = new float[mesh.getYsize()];
        float[] yPoints = new float[mesh.getYsize()];
        for(int i = 0; i < mesh.getYsize(); i++){
            xPoints[i] = yList.get(i)[0].floatValue();
            yPoints[i] = yList.get(i)[1].floatValue();
        }

        PolygonRoi buff = new PolygonRoi(xPoints, yPoints, PolygonRoi.POLYLINE);
        buff.fitSpline();
        return buff;
    }

    public void changeActiveRoi(int index) {
        //意外とむずい->ImageCanvasを参考にして、Roiのimageをnullにする。
        PolygonRoi roi = (PolygonRoi)currentOverlay.get(index);
        roi.setImage(null);
        mainImage.setRoi(roi);
    }


    @FXML
    public void changeMeshSize(){
        int xSize = Integer.valueOf(meshSizeWidth.getText());
        int ySize = Integer.valueOf(meshSizeHeight.getText());
        boolean forZ = forZCheckBox.isSelected();

        MeshManager meshManager = new MeshManager(meshTlist.get(mainImage.getT()));
        meshTlist.get(mainImage.getT()).setMainMap(meshManager.changeMeshPoints(xSize, ySize, true, forZ)); //widthZpositionは処理が早くなるのであれば必須項目とし、削除予定
        this.changeViewMode();
    }

    @FXML
    public void changeMeshSizeX(){
        boolean forZ = forZCheckBox.isSelected();

        int xSize = Integer.valueOf(meshSizeWidth.getText());
        MeshManager meshManager = new MeshManager(meshTlist.get(mainImage.getT()));
        meshTlist.get(mainImage.getT()).setMainMap(meshManager.changeColPointsNum(xSize, true, forZ));

        this.changeViewMode();

    }

    @FXML
    public void changeMeshSizeY(){
        boolean forZ = forZCheckBox.isSelected();

        int ySize = Integer.valueOf(meshSizeHeight.getText());

        MeshManager meshManager = new MeshManager(meshTlist.get(mainImage.getT()));
        meshTlist.get(mainImage.getT()).setMainMap(meshManager.changeRowPointsNum(ySize, true, forZ));

        this.changeViewMode();
    }


    @Override
    public void imageOpened(ImagePlus imp) {

    }

    @Override
    public void imageClosed(ImagePlus imp) {

    }

    @Override
    public void imageUpdated(ImagePlus imp) {

        final int mainImageID = mainImage.getID();
        final int impID = imp.getID();
        /*
        if(mainImage.isComposite()){
            boolean mainImageActive[] = ((CompositeImage)mainImage).getActiveChannels();
            boolean xzActive[] = ((CompositeImage)xzImage).getActiveChannels();
            boolean yzActive[] = ((CompositeImage)yzImage).getActiveChannels();
            for(int i = 0; i < mainImageActive.length; i++){
                xzActive[i] = mainImageActive[i];
                yzActive[i] = mainImageActive[i];
            }
        }
        */
        if(impID == mainImageID) {
            ImagePlus[] zImages;
            if(mainImage.isComposite()){
                zImages = new ImagePlus[]{xzImage, yzImage};
            }else{
                zImages = new ImagePlus[]{xzImageSingle, yzImageSingle};
            }
            this.syncZYZ(mainImage, zImages[0]);
            this.syncZYZ(mainImage, zImages[1]);
            this.updateAndDraw();
        }
    }

    @Override
    public void itemStateChanged(ItemEvent e) {

    }

    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {

    }

    @Override
    public void keyReleased(KeyEvent e) {

        if(e.getSource() == ic){

            if(e.getKeyCode() == KeyEvent.VK_SPACE) {
                viewMode = viewMode + 1;
                if (viewMode > viewModeList.length -1) {
                    viewMode = 0;
                }
                this.changeViewMode();

                /*
            } else if(e.getKeyCode() == KeyEvent.VK_ESCAPE){ //test

                MeshManager meshManager = new MeshManager(meshTlist.get(mainImage.getT()));
                getInterpolatedMapForRow = meshManager.createInterpolatedMapForRow();
                meshManager.setMeshMap(getInterpolatedMapForRow);
                interpolatedMap = meshManager.createInterpolatedMapForCol();


                meshTlist.replace(mainImage.getT(), interpolatedMap);

                this.changeViewMode();
            */

            } else if(e.getKeyCode() == KeyEvent.VK_TAB){

                Duplicator duplicator = new Duplicator();
                currentTImage = duplicator.run(forCrossSectionImage, 1, mainImage.getNChannels(), 1, mainImage.getNSlices(), mainImage.getT(), mainImage.getT());
                currentTImage.setCalibration(mainImage.getCalibration());
                Fitter fitter = new Fitter(currentTImage, meshTlist.get(mainImage.getT()));
                fitter.fitZpositoin();
                meshTlist.get(mainImage.getT()).mainMap.forEach(yList ->{
                    yList.forEach(dArray ->{
                        System.out.println("x, y, z = " + dArray[0] + ", " + dArray[1] + ", " + dArray[2]);

                    });
                });


            }
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {

    }

    @Override
    public void mousePressed(MouseEvent e) {
        if(e.getSource() == ic){

            Duplicator duplicator = new Duplicator();
            currentTImage = duplicator.run(forCrossSectionImage, 1, mainImage.getNChannels(), 1, mainImage.getNSlices(), mainImage.getT(), mainImage.getT());

            clickedPointX = ic.offScreenXD(e.getX());
            clickedPointY = ic.offScreenY(e.getY());

        }

    }

    @Override
    public void mouseReleased(MouseEvent e) {

        if(e.getSource() == ic){
            draggedPointX = ic.offScreenXD(e.getX());
            draggedPointY = ic.offScreenYD(e.getY());

            Roi selectedRoi = mainImage.getRoi();
            if(selectedRoi != null) {
                int selectedRoiIndex = currentOverlay.getIndex(selectedRoi);

                MeshManager meshManager = new MeshManager(meshTlist.get(mainImage.getT()));

                if(selectedRoiIndex != -1) {

                    if (viewMode == 0) {
                        double xvalue = draggedPointX - clickedPointX;
                        double yvalue = draggedPointY - clickedPointY;
                        this.addValueXY(meshTlist.get(mainImage.getT()), xvalue, yvalue);


                    } else if (viewMode == 1) { //これらについてもマウスリリースの都度登録し直すのが安全か？選択しているROIだけか、すべてか？


                        MeshAgent meshAgentYZ;
                        if(yzImage != null) {
                            meshAgentYZ = new MeshAgent(yzImage, meshTlist.get(mainImage.getT()));
                        }else{
                            meshAgentYZ = new MeshAgent(yzImageSingle, meshTlist.get(mainImage.getT()));
                        }

                        PolygonRoi pRoi = (PolygonRoi) selectedRoi;
                        pRoi.removeSplineFit();

                        float[] xPoints = pRoi.getFloatPolygon().xpoints;
                        float[] yPoints = pRoi.getFloatPolygon().ypoints;

                        for (int i = 0; i < pRoi.getNCoordinates(); i++) {
                            meshManager.setXYvalues(selectedRoiIndex, i, xPoints[i], yPoints[i]);
                        }
                        pRoi.fitSpline(); //これを入れないとドラッグ中に解除されてしまう。

                        meshAgentYZ.displayROI(selectedRoiIndex, "col");

                    } else if (viewMode == 2) {
                        MeshAgent meshAgentXZ;
                        if(xzImage != null) {
                            meshAgentXZ = new MeshAgent(xzImage, meshTlist.get(mainImage.getT()));
                        }else{
                            meshAgentXZ = new MeshAgent(xzImageSingle, meshTlist.get(mainImage.getT()));
                        }

                        PolygonRoi pRoi = (PolygonRoi) selectedRoi;
                        pRoi.removeSplineFit();

                        float[] xPoints = pRoi.getFloatPolygon().xpoints;
                        float[] yPoints = pRoi.getFloatPolygon().ypoints;

                        for (int i = 0; i < pRoi.getNCoordinates(); i++) {
                            meshManager.setXYvalues(i, selectedRoiIndex, xPoints[i], yPoints[i]);
                        }
                        pRoi.fitSpline(); //これを入れないとドラッグ中に解除されてしまう。
                        meshAgentXZ.displayROI(selectedRoiIndex, "row");

                    } else if(viewMode == 3){
                        PointRoi pRoi = (PointRoi)selectedRoi;
                        meshManager.setXYvalues(selectedRoiIndex, pRoi.getContainedPoints()[0].getX(), pRoi.getContainedPoints()[0].getY());
                    }

                    createImage();
                }
            }
        } else if(e.getSource() == icXZ){
            Roi selectedRoi = mainImage.getRoi();
            if(selectedRoi != null) {
                int selectedRoiIndex = currentOverlay.getIndex(selectedRoi);
                PolygonRoi xzRoi = (PolygonRoi) icXZ.getImage().getRoi();
                if(xzRoi != null){
                    MeshAgent meshAgentXZ = new MeshAgent(icXZ.getImage(), meshTlist.get(mainImage.getT()));
                    meshAgentXZ.setROItoMeshMapByRow(selectedRoiIndex, selectedRoi.getXBase());
                }
                xzRoi.fitSpline();//解除対策
                this.changeViewMode();
                this.changeActiveRoi(selectedRoiIndex);
            }

        } else if(e.getSource() == icYZ){
            Roi selectedRoi = mainImage.getRoi();
            if(selectedRoi != null) {
                int selectedRoiIndex = currentOverlay.getIndex(selectedRoi);
                PolygonRoi yzRoi = (PolygonRoi) icYZ.getImage().getRoi();
                if(yzRoi != null){
                    MeshAgent meshAgentYZ = new MeshAgent(icYZ.getImage(), meshTlist.get(mainImage.getT()));
                    meshAgentYZ.setROItoMeshMapByCol(selectedRoiIndex, selectedRoi.getYBase());
                }
                yzRoi.fitSpline();//解除対策
                this.changeViewMode();
                this.changeActiveRoi(selectedRoiIndex);

            }
        }


    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {

    }

    @Override
    public void mouseDragged(MouseEvent e) {


        if(e.getSource() == ic){

            if(viewMode == 0) {
                int currentPX = ic.offScreenX(e.getX());
                int currentPY = ic.offScreenY(e.getY());

                double xposition = currentPX - clickedPointX;
                double yposition = currentPY - clickedPointY;

                MeshManager meshManager = new MeshManager(meshTlist.get(mainImage.getT()));
                meshManager.translateXY(xposition, yposition);

                clickedPointX = currentPX;
                clickedPointY = currentPY;

                this.createImage();
            }else if(viewMode == 1){

                this.createImage();

            }else if(viewMode == 2){
                this.createImage();
            }

        }

    }

    @Override
    public void mouseMoved(MouseEvent e) {

    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        mainImage.killRoi();
        if(e.getSource() == ic){
            int count = e.getWheelRotation();
            mainImage.setZ(mainImage.getZ() + count);
        }
    }

    @Override
    public void windowOpened(WindowEvent e) {

    }

    @Override
    public void windowClosing(WindowEvent e) {
        this.removeListener();
        this.close();
    }

    @Override
    public void windowClosed(WindowEvent e) {

    }

    @Override
    public void windowIconified(WindowEvent e) {

    }

    @Override
    public void windowDeiconified(WindowEvent e) {

    }

    @Override
    public void windowActivated(WindowEvent e) {

    }

    @Override
    public void windowDeactivated(WindowEvent e) {

    }

    @FXML void createNewMesh(){
        mainImage.killRoi();
        this.createMesh(Integer.valueOf(meshSizeWidth.getText()), Integer.valueOf(meshSizeHeight.getText()), Integer.valueOf(meshStepSize.getText()));
        this.addValueXY(meshTlist.get(mainImage.getT()), 0, 0);
        this.showMeshAllShape(meshTlist.get(mainImage.getT()));

        this.createCrossSectionImage();

    }

    @FXML
    public void autoFit(){
        Duplicator duplicator = new Duplicator();
        currentTImage = duplicator.run(forCrossSectionImage, 1, mainImage.getNChannels(), 1, mainImage.getNSlices(), mainImage.getT(), mainImage.getT());
        currentTImage.setCalibration(mainImage.getCalibration());
        Fitter fitter = new Fitter(currentTImage, meshTlist.get(mainImage.getT()));
        fitter.setThreshold(Double.valueOf(autofitThreshold.getText()));
        fitter.fitZpositoin();

        MeshManager meshManager = new MeshManager(meshTlist.get(mainImage.getT()));


        /* //flat imageを作る際のほうがいいかも 2020220
        double xScale = mainImage.getCalibration().pixelWidth;
        double zScale = mainImage.getCalibration().pixelDepth;
        double az = zScale / xScale;
        if(az == 0){
            az = 1;
        }
        int depth = (int)Math.round((mainImage.getNSlices() * az));
        meshManager.translateDeep(Integer.valueOf(meshThickness.getText()), depth);
        */

        meshTlist.get(mainImage.getT()).mainMap.forEach(yList ->{
            yList.forEach(dArray ->{
                System.out.println("x, y, z = " + dArray[0] + ", " + dArray[1] + ", " + dArray[2]);

            });
        });

        this.showMessageFX("AutoFit", "Done!");
    }



    @FXML
    public void checkMesh(){

        MeshManager meshManager = new MeshManager(meshTlist.get(mainImage.getT()));

        //*// Autofitから移動 20200220
        double xScale = mainImage.getCalibration().pixelWidth;
        double zScale = mainImage.getCalibration().pixelDepth;
        double az = zScale / xScale;
        if(az == 0){
            az = 1;
        }
        //*/

        int depth = (int)Math.round((mainImage.getNSlices() * az));
        meshManager.translateDeep(Integer.valueOf(meshThickness.getText()), depth); //20200522 ここに問題がある
        interpolatedMap = meshManager.createInterporatedMap(forZCheckBox.isSelected());
        this.createCheckImage(interpolatedMap);

    }

    public void checkInterpolatedMap(){
        ImagePlus testImage = forCrossSectionImage.duplicate();
        ShapeRoi roi = this.getMeshAllShape(interpolatedMap);
        testImage.setRoi(roi);
        testImage.setTitle("Check Interpolated Mesh");
        testImage.show();
    }



    public void createCheckImage(MeshMap<Double[]> meshMap){
        float[][] data = new float[meshMap.getXsize()][meshMap.getYsize()];

        for(int x = 0; x < meshMap.getXsize(); x++){
            for(int y = 0; y < meshMap.getYsize(); y++){
                data[x][y] = (float)(meshMap.getValue(x, y)[2].doubleValue() * (mainImage.getCalibration().pixelWidth/mainImage.getCalibration().pixelDepth));
            }
        }
        ImageProcessor buff = new FloatProcessor(data);
        ImagePlus result = new ImagePlus();
        result.setProcessor("CheckImage", buff);
        result.getProcessor().invert();
        result.setCalibration(mainImage.getCalibration());

        result.show();


        IJ.run("Interactive 3D Surface Plot");

    }


    @FXML
    public void createFlatImage(){

        ImageCreator imageCreator = new ImageCreator(mainImage, interpolatedMap);
        ImagePlus flatImage = imageCreator.getFlattenedImage(Integer.valueOf(meshThickness.getText()));


        /* 計算テスト
        float[] x ={1.0f, 1.2f, 1.4f};
        float[] y ={1.0f, 1.2f, 1.4f};
        FloatPolygon fp = new FloatPolygon(x, y);
        PolyLineManager test = new PolyLineManager(fp);
        test.checkProgram();
        */

        flatImage.show();
    }


    public void checkMeshMapValue(MeshMap<Double[]> mesh){

        mesh.mainMap.forEach(yList ->{
            yList.forEach(data ->{
                System.out.println(data[0] + "¥t" + data[1] + "¥t" + data[2]);

            });
        });

    }

    @FXML
    public void saveROI(){
        String exportExtension = "tsv";
        if(this.showSaveDialog(exportExtension)){
            this.saveTsv();
        }
    }


    public boolean showSaveDialog(String exp){
        boolean b = false;

        System.out.println(imageFileDir);
        FileChooser fChooser = new FileChooser();

        String[] splitFileName = imageFileName.split("\\.");
        String changedExp = "";
        for(int i = 0; i < splitFileName.length -1; i++){
            changedExp = changedExp + splitFileName[i] + ".";
        }
        changedExp = changedExp + exp;

        fChooser.setInitialDirectory(new File(imageFileDir));
        fChooser.setInitialFileName(changedExp);
        saveFileName = fChooser.showSaveDialog(scene.getWindow()); //ちゃんと指定しないとだめっぽい
        if(saveFileName == null){
            b = false;
        }else{
            b = true;
        }

        return b;
    }


    public void saveTsv(){

        int meshNum = meshTlist.size();

        if(meshNum == 0){
            Alert alert   = new Alert( Alert.AlertType.NONE , "No ROIs Data" , ButtonType.OK);
            alert.show();
            return;
        }else{
            IntStream intStream = IntStream.range(0, meshTlist.size());
            intStream.forEach(i ->{
                String fName = "roiData_" + (i+1) + "_" + saveFileName.getName();
                MeshManager.saveMeshData(meshTlist.get(i+1), saveFileName.getParent(), fName);
            });
        }

        this.showMessageFX("From save", "Done!");
    }




    @FXML
    public void loadROI(){
        //String exportExtension = "tsv";
        if(this.showLoadDialog()){
            this.loadTsv();
        }
    }



    public boolean showLoadDialog(){
        boolean b = false;

        //FileChooser fChooser = new FileChooser();
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setInitialDirectory(new File(imageFileDir));
        loadFile = directoryChooser.showDialog(scene.getWindow()); //ちゃんと指定しないとだめっぽい

        if(loadFile == null){
            b = false;
        }else{
            b = true;
        }

        return b;
    }

    public void loadTsv(){

        int meshNum = meshTlist.size();

        if(meshNum == 0){
            Alert alert   = new Alert( Alert.AlertType.NONE , "No ROIs Data" , ButtonType.OK);
            alert.show();
            return;
        }else{
            int count = 1;
            for(File f : loadFile.listFiles()){
                System.out.println(f.getPath());
                if(f.getName().matches(".*\\.tsv")) {
                    //MeshManager meshManager = new MeshManager(meshTlist.get(count));
                    //meshManager.loadMeshData(f.getParent(), f.getName());
                    MeshMap<Double[]> buffMesh = MeshManager.loadMeshData(f.getParent(), f.getName());
                    meshTlist.replace(count, buffMesh);
                    count = count + 1;
                }
            }
        }

        meshSizeWidth.setText(String.valueOf(meshTlist.get(1).getXsize()));
        meshSizeHeight.setText(String.valueOf(meshTlist.get(1).getYsize()));

        this.changeViewMode();
        this.showMessageFX("From load", "Done!");
    }





    public void showMessageFX(String title, String message){
        Alert alert   = new Alert( Alert.AlertType.WARNING , "" , ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }



    public void close(){
        mainImage.setOverlay(null);
        this.removeListener();
        if (xzImage != null){
            xzImage.close();
        }else{
            xzImageSingle.close();
        }

        if(yzImage != null) {
            yzImage.close();
        }else{
            yzImageSingle.close();
        }

        Platform.setImplicitExit(false);
    }
}
