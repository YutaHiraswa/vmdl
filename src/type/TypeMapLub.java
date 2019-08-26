package type;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import type.AstType.JSValueType;

public class TypeMapLub extends TypeMapBase {
    Map<String, AstType> dict;
    Set<Map<String, AstType>> dictSet;

    public TypeMapLub(){
        dict = new HashMap<String, AstType>();
        dictSet = new HashSet<>();
        dictSet.add(dict);
    }
    public TypeMapLub(Map<String, AstType> _dict) {
        dict = _dict;
        dictSet = new HashSet<>();
        dictSet.add(dict);
    }
    public Set<Map<String, AstType>> getDictSet() {
        return dictSet;
    }
    public Set<AstType> get(String name){
        Set<AstType> set = new HashSet<>();
        set.add(dict.get(name));
        return set;
    }
    public void addDispatch(String name){}
    public void removeAllDispatch(){}
    public void assignment(String name, Set<AstType> types){
        AstType newType = AstType.BOT;
        for(AstType t : types){
            newType = newType.lub(t);
        }
        dict.put(name, newType);
    }
    public void add(String name, AstType type){
        dict.put(name, type);
    }
    public void add(Map<String, AstType> map){
        for(String k : map.keySet()){
            AstType t = dict.get(k);
            if(t==null){
                dict.put(k, map.get(k));
            }else{
                dict.replace(k, t.lub(map.get(k)));
            }
        }
        
    }
    public void add(Set<Map<String, AstType>> set){
        for(Map<String, AstType> m : set){
            add(m);
        }
    }
    public boolean containsKey(String key) {
        return dict.containsKey(key);
    }
    public Set<String> getKeys() {
        return dict.keySet();
    }
    public TypeMapBase select(Collection<String> domain) {
        HashMap<String, AstType> newGamma = new HashMap<String, AstType>();
        
        for (String v : domain) {
            newGamma.put(v, dict.get(v));
        }
        return new TypeMapLub(newGamma);
    }
    @Override
    public TypeMapBase clone() {
        HashMap<String, AstType> newGamma = new HashMap<String, AstType>();
        //newGamma = ((HashMap<String, AstType>)this.dict).clone();
        for(String s : dict.keySet()){
            newGamma.put(s, dict.get(s));
        }
        return new TypeMapLub(newGamma);
    }
    public void update(String key, AstType value) {
        dict.replace(key, value);
    }
    public Set<AstType> combineExprTypes(Set<AstType> exprType1, Set<AstType> exprType2){
        Set<AstType> newExprType = new HashSet<>();
        AstType type = AstType.BOT;
        for(AstType t : exprType1){
            type = type.lub(t);
        }
        for(AstType t : exprType2){
            type = type.lub(t);
        }
        newExprType.add(type);
        return newExprType;
    }
    private Map<String, AstType> getLubDict(Set<Map<String, AstType>> _dictSet){
        HashMap<String, AstType> lubDict = new HashMap<>();

        for(Map<String, AstType> m : _dictSet){
            for(String k : m.keySet()){
                AstType t = lubDict.get(k);
                if(t==null){
                    lubDict.put(k, m.get(k));
                }else{
                    lubDict.replace(k, t.lub(m.get(k)));
                }
            }
        }
        return lubDict;
    }
    public TypeMapBase combine(TypeMapBase that) {
        HashMap<String, AstType> newGamma = new HashMap<String, AstType>();
        Map<String, AstType> thatDict = getLubDict(that.getDictSet());
        for (String v : dict.keySet()) {
            AstType t1 = dict.get(v);
            AstType t2 = thatDict.get(v);
            if (t2 == null) {
                throw new Error("inconsistent type environment: v = "+v);
            } else {
                if (t1 == t2) {
                    newGamma.put(v, t1);
                } else if (t1 == AstType.BOT) {
                    newGamma.put(v, t2);
                } else if (t2 == AstType.BOT) {
                    newGamma.put(v, t1);
                } else if (!(t1 instanceof JSValueType && t2 instanceof JSValueType))
                    throw new Error("type error");
                else {
                    JSValueType jsvt1 = (JSValueType) t1;
                    JSValueType jsvt2 = (JSValueType) t2;
                    newGamma.put(v, jsvt1.lub(jsvt2));
                }
            }
        }
        return new TypeMapLub(newGamma);
    }
    private int indexOf(String[] varNames, String v) {
        for (int i = 0; i < varNames.length; i++) {
            if (varNames[i].equals(v)) {
                return i;
            }
        }
        return -1;
    }

    public TypeMapBase enterCase(String[] varNames, VMDataTypeVecSet caseCondition) {
        HashMap<String, AstType> newGamma = new HashMap<String, AstType>();

        /* parameters */
        AstType[] paramTypes = new AstType[varNames.length];
        for (int i = 0; i < varNames.length; i++)
            paramTypes[i] = dict.get(varNames[i]);
        VMDataTypeVecSet.ByCommonTypes vtvs = new VMDataTypeVecSet.ByCommonTypes(varNames, paramTypes);
        vtvs = vtvs.intersection(caseCondition);
        for (int i = 0; i < varNames.length; i++) {
            AstType t = vtvs.getMostSpecificType(varNames[i]);
            newGamma.put(varNames[i], t);
        }
        
        /* add other variables */
        for (String v : dict.keySet()) {
            AstType t = dict.get(v);
            int index = indexOf(varNames, v);
            if (index == -1)
                newGamma.put(v, t);
        }
        
        return new TypeMapLub(newGamma);
    }

    public TypeMapBase rematch(String[] params, String[] args, Set<String> domain) {
        HashMap<String, AstType> newGamma = new HashMap<String, AstType>();
        for (String v : domain) {
            int index = indexOf(params, v);
            if (index == -1) {
                newGamma.put(v, dict.get(v));
            } else {
                newGamma.put(v, dict.get(args[index]));
            }
        }
        return new TypeMapLub(newGamma);
    }

    public TypeMapBase getBottomDict() {
        Set<String> domain = getKeys();
        Map<String, AstType> mapTemp = new HashMap<>();
        TypeMapLub result = new TypeMapLub();
        for (String v : domain) {
            mapTemp.put(v, AstType.BOT);
        }
        result.add(mapTemp);
        return result;
    }

    public Set<VMDataType[]> filterTypeVecs(String[] formalParams, Set<VMDataType[]> vmtVecs) {
        Set<VMDataType[]> filtered = new HashSet<VMDataType[]>();
        NEXT_DTS: for (VMDataType[] dts: vmtVecs) {
            for (int i = 0; i < formalParams.length; i++) {
                VMDataType dt = dts[i];
                AstType xt = dict.get(formalParams[i]);
                if (xt instanceof JSValueType) {
                    JSValueType t = (JSValueType) xt;
                    if (!t.isSuperOrEqual(dt))
                        continue NEXT_DTS;
                } else
                    throw new Error("internal error");
            }
            filtered.add(dts);
        }
        return filtered;
    }
    
    public boolean hasBottom() {
        for (AstType t: dict.values())
            if (t == AstType.BOT)
                return true;
        return false;
    }
    
    @Override
    public String toString() {
        return dict.toString();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj ||
            obj != null && obj instanceof TypeMapLub) {
                TypeMapLub tm = (TypeMapLub)obj;
            return (dict != null && tm.dict !=null && dict.equals(tm.dict)) ||
                (exprType != null && tm.exprType != null && exprType.equals(tm.exprType));
        } else {
            return false;
        }
    }
}

/*

public TypeMapLub(TypeMapBase typeMap){
        this();
        Set<Map<String, AstType>> dictSet = typeMap.getDictSet();

        for(Map<String, AstType> m : dictSet){
            for(String k : m.keySet()){
                AstType t = dict.get(k);
                if(t==null){
                    dict.put(k, m.get(k));
                }else{
                    dict.replace(k, t.lub(m.get(k)));
                }
            }
        }
    }
*/ 