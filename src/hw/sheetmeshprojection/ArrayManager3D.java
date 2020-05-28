package hw.sheetmeshprojection;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;


/*
 *  n次元配列を表現したいが、、、自動で構築するのは無理か？
 *  ...この構造つかうかなぁ。 20190820
 */

public class ArrayManager3D<T> {

    ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, T>>> arrayMap;

    public ArrayManager3D(){
        arrayMap = new ConcurrentHashMap();
    }

    public void createMap(int xSize, int ySize, int zSize, T defaultValue){

        ConcurrentHashMap<Integer, T> buffZ = new ConcurrentHashMap<>();
        for(int z = 0; z < zSize; z++){
            buffZ.put(z+1, defaultValue);

        }

        ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, T>> buffY = new ConcurrentHashMap<>();
        for(int y = 0; y < ySize; y++){
            buffY.put(y+1, buffZ);

        }

        for(int x = 0; x < xSize; x++){
            arrayMap.put(x+1, buffY);
        }

    }

    public T getValue(int x, int y, int z){
        T value = arrayMap.get(x).get(y).get(z);
        return value;
    }

    public void setValue(int x, int y, int z, T value){
        arrayMap.get(x).get(y).replace(z, value);
    }
}
