package type;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import type.AstType.AstBaseType;
import type.AstType.JSValueType;

public class TypeMapHybrid extends TypeMapFull {
    Set<String> dispatchSet;

    public TypeMapHybrid(){
        dictSet = new HashSet<>();
        dispatchSet = new HashSet<>();
        dictSet.add(new HashMap<String, AstType>());
    }

    public TypeMapHybrid(Map<Map<String, AstType>, AstType> _exprTypeMap){
        super(_exprTypeMap);
        dictSet = new HashSet<>();
        dispatchSet = new HashSet<>();
        dictSet.add(new HashMap<String, AstType>());
    }
    public TypeMapHybrid(Set<Map<String, AstType>> _dictSet, Set<String> _dispatchSet){
        dictSet = _dictSet;
        dispatchSet = _dispatchSet;
    }

    public void addDispatch(String name){
        dispatchSet.add(name);
    }
    public void clearDispatch(){
        dispatchSet = new HashSet<>();
    }
    public Set<String> getDispatchSet(){
        return dispatchSet;
    }
    public TypeMapBase select(Collection<String> domain){
        Set<Map<String, AstType>> selectedSet = new HashSet<>();
        Set<String> newDispatchSet = new HashSet<>();
        for(Map<String, AstType> m : dictSet){
            Map<String, AstType> selectedMap = new HashMap<>();
            for(String s : domain){
                AstType type = m.get(s);
                if(type==null){
                    if(containsKey(s)){
                        type = AstType.BOT;
                    }else
                        throw new Error("Failure select : no such element \""+s+"\"");
                }
                selectedMap.put(s, type);
            }
            selectedSet.add(selectedMap);
        }
        for(String s : dispatchSet){
            newDispatchSet.add(s);
        }
        return new TypeMapHybrid(selectedSet, newDispatchSet);
    }
    @Override
    public TypeMapBase clone(){
        Set<Map<String, AstType>> newSet = new HashSet<>();
        Set<String> newDispatchSet = new HashSet<>();
        for(Map<String, AstType> m : dictSet){
            Map<String, AstType> newGamma = new HashMap<>();
            for(String s : m.keySet()){
                newGamma.put(s, m.get(s));
            }
            newSet.add(newGamma);
        }
        for(String s : dispatchSet){
            newDispatchSet.add(s);
        }
        return new TypeMapHybrid(newSet, newDispatchSet);
    }
    private AstType getLubType(Set<AstType> set){
        AstType result = AstType.BOT;
        for(AstType t : set){
            if(t==AstType.BOT) continue;
            //if(!(t instanceof JSValueType)){
                if(t instanceof AstType.AstProductType){
                    result = t;
                    continue;
                }
                //throw new Error("type error :"+t.toString());
            //}
            result = result.lub(t);
        }
        return result;
    }
    public TypeMapBase combine(TypeMapBase that){
        Set<Map<String, AstType>> newSet = new HashSet<>();
        Set<String> newDispatchSet = new HashSet<>();
        Map<String, AstType> lubTypeMap = new HashMap<>();
        if(!dispatchSet.equals(that.getDispatchSet())){
            throw new Error("Failure combine: different dispatch set");
        }
        for(Map<String, AstType> m : dictSet){
            for(String s : m.keySet()){
                if(dispatchSet.contains(s)) continue; //thatのディスパッチ対象は見る必要がある？
                Set<AstType> typeSet = this.get(s);
                typeSet.addAll(that.get(s));
                lubTypeMap.put(s, getLubType(typeSet));
            }
        }
        for(Map<String, AstType> m : dictSet){
            Map<String, AstType> newGamma = new HashMap<>();
            for(String s : m.keySet()){
                AstType lubType = lubTypeMap.get(s);
                if(lubType==null){
                    newGamma.put(s, m.get(s)); //ディスパッチ対象の変数
                }else{
                    newGamma.put(s, lubType); //ディスパッチ対象でない変数
                }
            }
            newSet.add(newGamma);
        }
        for(Map<String, AstType> m : that.getDictSet()){
            Map<String, AstType> newGamma = new HashMap<>();
            for(String s : m.keySet()){
                AstType lubType = lubTypeMap.get(s);
                if(lubType==null){
                    newGamma.put(s, m.get(s)); //ディスパッチ対象の変数
                }else{
                    newGamma.put(s, lubType); //ディスパッチ対象でない変数
                }
            }
            newSet.add(newGamma);
        }
        for(String s : dispatchSet){
            newDispatchSet.add(s);
        }
        return new TypeMapHybrid(newSet, newDispatchSet);
    }
    private int indexOf(String[] varNames, String v) {
        for (int i = 0; i < varNames.length; i++) {
            if (varNames[i].equals(v)) {
                return i;
            }
        }
        return -1;
    }
    public TypeMapBase enterCase(String[] varNames, VMDataTypeVecSet caseCondition){
        Set<VMDataType[]> conditionSet = caseCondition.getTuples();
        Set<Map<String, AstType>> newSet = new HashSet<>();
        Set<String> newDispatchSet = new HashSet<>();
        if(conditionSet.isEmpty()){
            Map<String, AstType> newGamma = new HashMap<>();
            Set<String> keys = getKeys();
            for(String s : keys){
                 newGamma.put(s, AstBaseType.BOT);
            }
            newSet.add(newGamma);
        }else{
            for(VMDataType[] v : conditionSet){
                int length = varNames.length;
                NEXT_MAP: for(Map<String, AstType> m : dictSet){
                    for(int i=0; i<length; i++){
                        if(!((JSValueType)AstType.get(v[i])).isSuperOrEqual((JSValueType)m.get(varNames[i]))){
                            continue NEXT_MAP;
                        }
                    }
                    Map<String, AstType> newGamma = new HashMap<>();
                    newGamma.putAll(m);
                    newSet.add(newGamma);
                }
            }
            if(newSet.isEmpty()){
                Map<String, AstType> newGamma = new HashMap<>();
                Set<String> keys = getKeys();
                for(String s : keys){
                    newGamma.put(s, AstBaseType.BOT);
                }
                newSet.add(newGamma);
            }
        }
        for(String s : dispatchSet){
            newDispatchSet.add(s);
        }
        return new TypeMapHybrid(newSet, newDispatchSet);
    }
    public TypeMapBase rematch(String[] params, String[] args, Set<String> domain){
        Set<Map<String, AstType>> newSet = new HashSet<>();
        Set<String> newDispatchSet = new HashSet<>();
        for(Map<String, AstType> m : dictSet){
            Map<String, AstType> newGamma = new HashMap<>();
            for (String v : domain) {
                int index = indexOf(params, v);
                if (index == -1) {
                    newGamma.put(v, m.get(v));
                } else {
                    newGamma.put(v, m.get(args[index]));
                }
            }
            newSet.add(newGamma);
        }
        for(String s : dispatchSet){
            newDispatchSet.add(s);
        }
        return new TypeMapHybrid(newSet, newDispatchSet);
    }
    public TypeMapBase getBottomDict(){
        Set<String> domain = getKeys();
        Map<String, AstType> newGamma = new HashMap<>();
        Set<Map<String, AstType>> newSet = new HashSet<>();
        for (String v : domain) {
            newGamma.put(v, AstType.BOT);
        }
        newSet.add(newGamma);
        return new TypeMapHybrid(newSet, new HashSet<String>());
    }
    @Override
    public String toString() {
        return dictSet.toString();
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj || obj != null && obj instanceof TypeMapHybrid) {
            TypeMapHybrid tm = (TypeMapHybrid)obj;
            Set<Map<String, AstType>> tmDictSet = tm.getDictSet();
            return (dictSet != null && tmDictSet !=null && dictSet.equals(tmDictSet)) &&
                (exprTypeMap != null && tm.exprTypeMap != null && exprTypeMap.equals(tm.exprTypeMap));
        } else {
            return false;
        }
    }
}