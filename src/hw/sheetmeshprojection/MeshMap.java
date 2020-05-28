package hw.sheetmeshprojection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


/**
 *  Represents a matrix and assumes row and column operations.
 *  MeshMapは単純に行列を表し、行、列単位の操作を想定する
 *
 * @author WADA Housei
 */

// 今回ではTに関してはdouble[3] = {x, y, z}で考える。
// ConcurrentHashMapからArrayListに変更。


public class MeshMap<T> {

    ArrayList<ArrayList<T>> mainMap; //x, y

    public MeshMap(){
        mainMap = new ArrayList<>();
    }


    public void createMesh(int xSize, int ySize, T defauleValue){
        for(int x = 0; x < xSize; x++){
            ArrayList<T> buff = new ArrayList<>();

            for(int y = 0; y < ySize; y++){
                buff.add(defauleValue);
            }
            mainMap.add(buff);
        }
    }


    public T getValue(int x, int y){

        return mainMap.get(x).get(y);
    }

    public ConcurrentHashMap<Integer, T> getColumn(int xIndex){
        ConcurrentHashMap<Integer, T> result = new ConcurrentHashMap<>();
        for(int i = 0; i < this.getYsize(); i++){
            result.put(i, mainMap.get(xIndex).get(i));
        }
        return result;

    }

    public List<T> getColumnAsList(int xIndex){
        List<T> result = new ArrayList<T>(mainMap.get(xIndex).size());
        for(int i = 0; i < mainMap.get(xIndex).size(); i++){
            result.add(i, mainMap.get(xIndex).get(i));
        }
        return result;
    }


    public ConcurrentHashMap<Integer, T> getRow(int yIndex){
        ConcurrentHashMap<Integer, T> buff = new ConcurrentHashMap<>();

        for(int i = 0; i < this.getXsize(); i++){
            buff.put(i, mainMap.get(i).get(yIndex));
        }

        return buff;
    }

    public List<T> getRowAsList(int yIndex){
        List<T> result = new ArrayList<T>(mainMap.size());
        for(int i = 0; i < mainMap.size(); i++){
            result.add(i, mainMap.get(i).get(yIndex));
        }
        return result;
    }


    public void setColumn(int xIndex, ConcurrentHashMap<Integer, T> values){

        for(int i = 0; i < values.size(); i++) {
            mainMap.get(xIndex).set(i, values.get(i));
        }
    }

    public void setColumn(int xIndex, List<T> colList){
        for(int i = 0; i < colList.size(); i++) {
            mainMap.get(xIndex).set(i, colList.get(i));
        }
    }


    public void setRow(int yIndex, ConcurrentHashMap<Integer, T> values){ //これもだめかも
        for(int i = 0; i < values.size(); i++){
            mainMap.get(i).set(yIndex, values.get(i));
        }

    }

    public void setRow(int yIndex, List<T> rowList){
        for(int x = 0; x < mainMap.size(); x++) {
            mainMap.get(x).set(yIndex, rowList.get(x));
        }

    }


    public void setValue(int x, int y, T value){ //なぜかarrayの最初の値だけ変化してしまう。スコープあたりのバグではないかと思われる。 -> setColumnで回避
        //mainMap.get(x).remove(y);
        //mainMap.get(x).put(y, value);
        //mainMap.get(x).replace(y, value);
        mainMap.get(x).set(y, value);
    }

    public void setValue(int index, T value){
        int x = index / this.getYsize();
        int y = index % this.getYsize();
        mainMap.get(x).set(y, value);
    }


    public int getXsize(){
        return mainMap.size();
    }

    public int getYsize() {
        int firstNum = 0;
        return mainMap.get(firstNum).size();
    }


    public MeshMap<T> copy(){
        MeshMap<T> copy = new MeshMap<>();
        copy.mainMap = (ArrayList<ArrayList<T>>)mainMap.clone();
        return copy;
    }

    public void setMainMap(MeshMap<T> meshMap){
        mainMap = meshMap.mainMap;
    }

}
