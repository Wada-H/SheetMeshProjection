import hw.sheetmeshprojection.SheetMeshProjectionUI;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.plugin.frame.PlugInFrame;
import javafx.embed.swing.JFXPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;

/*  2019.7.16, 先々週あたりより構想をねっているpluginについてメモ
 *  目的 : ハエのembryoのgermband extension時期の表面の細胞を擬似的に引き伸ばした開き画像を作る
 *
 *  点を手動で打つことでx, y, zの座標を取得する
 *  網目状に作る。これすなわち行列で表現するということ。(ND4Jなどの利用も考えたが、独自クラスが良さそう)
 *  縦、横、高さについて直線化を行い平面図とする（地図のメルカプト図法のように引き伸ばされる）
 *  各軸ごとに直線化の有無を選択できるように。(ダイマクション地図のような表現か？)
 *  メッシュの厚み設定。(これによって表皮のような厚みのある組織の表面を表現できる)
 *  これらを行うためにはZ方向の方向の等倍補間画像が必須(ZTinterpolationで対応可能) -> このプラグインで対応可能かも
 *  3Dのmeshを確認するための画像を出力する機能を追加。その画像をInteractive 3D Surface Plotで読み込む
 *
 *
 *  20191029, PR後板倉さんとのはなしで、YZ, XZの点の位置を等間隔にするほうがいいのではとのこと。
 *      -> AutoZfit時もしくは別メソッドでつけるべきか。
 *      -> そもそもXZの画像などからlineを引いてそれをstretchしているならこれは意味のない操作になる
 *
 *  20191211
 *      CheckMapをおこなうとVM limitエラーがでる場合がある。
 *          x, yのサイズが大きめ(数十)でおこる。とはいえ数百でも作れるはずの設計で。
 *          同じ設定で作れる場合とそうでない場合がある。
 *          Map内の数値の問題か？
 *
 *      x, yのサイズ変更時に時間がかかる。
 *          どうやらSpline3Dにおけるzの値を計算するところが時間がかかるようだ。
 *          とりあえず、この計算をしない場合のメソッドも追加。引数 withZposition = true, falseで切替可能にした
 *          手動でzpositionを変更する機能を付ける場合、ここは基本的に計算させるべき。->高速化が必須
 *
 *  20200202
 *      Z position マニュアル変更機能において、マウスリリース後の位置を反映させるため、viewをもう一度し直すが
 *      これにより操作中のROIのフォーカスが外れてしまう。
 *      これにより、次の変更が反映できない(selected ROIとして取得できないため)
 *       -> viewをし直したあとにROIをselectする機能を追加。*PeakConnectorFx 参照
 *
 *  20200204
 *      MeshManager.changeColPointsNum, RowPointsNumにおいて並列化することである程度高速化できている。
 *      ただし、内部の処理はいぜんと遅い(2-3秒)ため引き続き工夫を探る。
 *
 *  20200205
 *      MeshMapを保存して、読み込む機能が必要と思われる。
 *      
 *  2020219
 *      保存と読み込み機能を追加。
 *      とりあえずは1フレーム1ファイルになるように設計。
 *
 *  2020220
 *      Meshを内面へシフトするタイミングFlat imageを作る際に変更したほうがいいような気がする。
 *          実際作業をすると、やはり目安になるのは境界であるため。
 *      メッシュのYZ, XZ点を等距離にするような補正がいると思われる。これによりx, y の座標にも変更が加わることになる。
 *       いっそ、xyとyz,xzはべつの座標を持っちゃうか？
 *       XZを表示しているときはyの値は固定で、xの値のみをシフトするメソッド。こうすることで断面図との矛盾がおこらなくなるかも。
 *
 *  20200507
 *      CheckMwsh時のshiftにおいてpreprocessingの値がなにか影響をしている
 *      点が詰まっているときにCheckMeshにおいて点がズレる部分がある。カーブしてv字になっている部分が想定より沈み込む
 *      crosssection画像がcombineされることがある
 *      1.52t以降checkmeshが動かない。
 *      1.52aリリースバージョンではほぼ動くが、checkmesh時にz座標が大きく乱れる
 *      メッシュの非表示機能
 *      ROIが元画像をはみ出たときにcross-section画像に不具合が出る(IJのバージョン依存か？)
 *      メッシュの位置を戻すボタンなどがいる
 *
 * 20200527
 *      CheckMeshにおいて点がずれるおよび、処理が止まる問題は、Line.getFloatPolygonにおいて4点の順番が1.52uより変更があったためと判明。
 *      これについてはWayneさんが1.53b31で対応済み。このためこの問題は解決した。
 *      公開に向けて少してなを入れる。
 *      バージョンは1.0-20200528とする。
 *
 * 20200528
 *      Githubにアップロードに備えて、コードの掃除をおこなった。
 *      ImageJ-1.53b33ではLoadした直後にCheckMeshを行うとエラーが出る(1.52sでは出ない)
 *
 * 20200531
 *      ボタンなどの誤字脱字の修正
 *
 * 20200610
 *      シングルチャンネル時に動作するように改善
 *
 * 20200629
 *      Composite:ColorにおいてmainImageとXZ,YZ Imageの同期に対応
 *      -> XZ, YZ画像が消えた時に不具合が起こるので対応必須。ただし、一応nullの場合は新しく画像を作るような記述をした記憶がある
 *
 *
 * 20200717
 *      Fijiのupdate siteに登録。https://sites.imagej.net//SheetMeshProjection/
 *      Fijiに登録済みのInteractive_3D_Surface_Plot-ver3.0.0は同じものだが、runの文章に変更があるためFiji用に変更
 *      Roiをメッシュ表示している際にドラッグするとその情報でメッシュの位置を書き換えてしまう不具合
 *          -> Roiの種類を判定することで対応
 *
 *
 * 20200717
 *      根本的なZのみの歪み補正の機能がある方が良さそう。
 *
 * 20200824
 *      XY(もともとの機能)に加え XZ, YZに対して引き伸ばしの優先を行うメソッドの追加
 *
 * 20201215
 *      create new meshやload roiを行ってすぐにcheckMeshを押すとエラーがでる。(Array size　関連, 11/20にgithub上で報告があった)
 *      PolyLineManager.getShiftedPolygon において交点の計算時にinfinityが出る場合があり、これによるarray size エラーということであった。
 *      このinfinityを回避する処理を入れることで解決。
 *      また、shift後のメッシュを別で保存し、表示するのはshift前のROIとなるように変更。
 */

/**
 * Projects the surface of a 3D image with semi-auto.
 * A mesh ROI is set to cover the object, and the z-coordinate of the brightness is automatically found at each intersection.
 * It can also be modified manually.
 * Creates a flattened image using this fitted mesh.
 *
 * This plugin requires Interactive_3D_Surface_Plot(https://imagej.nih.gov/ij/plugins/surface-plot-3d.html)
 * and CrossSectionViewer(own work).
 *
 * @author WADA Housei
 */

public class SheetMeshProjection_ extends PlugInFrame {
    static String version = "1.0-20201215";

    ImagePlus mainImage;
    ImageCanvas ic;

    SheetMeshProjectionUI ui;

    public SheetMeshProjection_() {
        super("SheetMeshProjection_" + version);

        if(checkImage()){
            this.makeUIpanel();
            //IJ.run(viewImage, "Channels...", "");
            //IJ.run(viewImage, "Color Balance...", "");

        }else{
            IJ.noImage();
            return;
        }

        ui.meshTest();
        ui.setListener();

    }


    public boolean checkImage(){
        boolean b = false;

        ImagePlus checkImage = WindowManager.getCurrentImage();
        if(checkImage == null){
            b = false;
        }else{
            if(checkImage.isStack()) {
                if (checkImage.isHyperStack() == false) {
                    IJ.run(checkImage, "Stack to Hyperstack...", ""); //なんかgetWindow().toFront()でエラー出る
                }
            }
            mainImage = WindowManager.getCurrentImage();
            ic = mainImage.getCanvas();


            b = true;
        }

        return b;
    }



    public void makeUIpanel(){

        //***** Plugin panel setup *****//
        ui = new SheetMeshProjectionUI(mainImage);
        JFXPanel jfxPanel = ui.getFXML();
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        this.setBounds(200,100,jfxPanel.getWidth(),jfxPanel.getHeight());
        this.add(jfxPanel);
        this.pack(); //推奨サイズのｗindow
        this.setPanelPosition();
        this.setVisible(true);//thisの表示
    }



    public void setPanelPosition(){
        Point ijLocation = IJ.getInstance().getLocation(); //imagejのtoolboxの開始座標
        int ij_height = IJ.getInstance().getHeight();
        this.setLocation(ijLocation.x, ijLocation.y + ij_height);
        this.setListener();
    }


    public void setListener(){
        mainImage.getWindow().addWindowListener(this);
    }

    public void removeListener(){
        mainImage.getWindow().removeWindowListener(this);
    }

    @Override
    public void windowClosing(WindowEvent e) {
        ui.close();
        this.removeListener();
        this.close();
    }
}
