package type;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import type.AstType.AstBaseType;
import type.AstType.JSValueType;

public class TypeMapFull extends TypeMapBase {
    Set<Map<String, AstType>> dictSet;

    public TypeMapFull(){
        dictSet = new HashSet<>();
        dictSet.add(new HashMap<String, AstType>());
    }

    public TypeMapFull(Set<AstType> _exprType){
        super(_exprType);
        dictSet = new HashSet<>();
        dictSet.add(new HashMap<String, AstType>());
    }
    public TypeMapFull(Set<Map<String, AstType>> _dictSet, Set<String> _dispatchSet){
        dictSet = _dictSet;
    }
    public Set<Map<String, AstType>> getDictSet(){
        return dictSet;
    }
    public Set<AstType> get(String name){
        Set<AstType> typeSet = new HashSet<>();
        for(Map<String, AstType> m : dictSet){
            AstType t = m.get(name);
            if(t==null) continue;
            typeSet.add(t);
        }
        return typeSet;
    }
    public void addDispatch(String name){}
    public void removeAllDispatch(){}
    public void add(String name, AstType type){

        for(Map<String, AstType> m : dictSet){
            if(m.get(name)==null){
                m.put(name, type);
            }else{
                m.replace(name, type);
            }
        }
    }
    public void assignment(String name, Set<AstType> types){
        Set<Map<String, AstType>> newSet = new HashSet<>();
        for(AstType t : types){
            for(Map<String, AstType> m : dictSet){
                Map<String, AstType> newGamma = new HashMap<>();
                for(String s : m.keySet()){
                    newGamma.put(s, m.get(s));
                }
                newGamma.put(name, t);
                newSet.add(newGamma);
            }
        }
        dictSet = newSet;
    }
    public void add(Map<String, AstType> map){
        for(Map<String, AstType> m : dictSet){
            m.putAll(map);
        }
    }
    public void add(Set<Map<String, AstType>> set){
        Set<Map<String, AstType>> newSet = new HashSet<>();
        for(Map<String, AstType> m : set){
            for(Map<String, AstType> dsm : dictSet){
                Map<String, AstType> newGamma = new HashMap<>();
                newGamma.putAll(dsm);
                newGamma.putAll(m);
                newSet.add(newGamma);
            }
        }
        dictSet = newSet;
    }
    public boolean containsKey(String key){
        for(Map<String, AstType> m : dictSet){
            if(m.containsKey(key)) return true;
        }
        return false;
    }
    public Set<String> getKeys(){
        Set<String> keySet = new HashSet<>();
        for(Map<String, AstType> m : dictSet){
            keySet.addAll(m.keySet());
        }
        return keySet;
    }
    public TypeMapBase select(Collection<String> domain){
        Set<Map<String, AstType>> selectedSet = new HashSet<>();
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
        return new TypeMapFull(selectedSet, null);
    }
    public TypeMapBase clone(){
        Set<Map<String, AstType>> newSet = new HashSet<>();
        for(Map<String, AstType> m : dictSet){
            Map<String, AstType> newGamma = new HashMap<>();
            for(String s : m.keySet()){
                newGamma.put(s, m.get(s));
            }
            newSet.add(newGamma);
        }
        return new TypeMapFull(newSet, null);
    }
    public Set<AstType> combineExprTypes(Set<AstType> exprType1, Set<AstType> exprType2){
        Set<AstType> newExprType = new HashSet<>();
        newExprType.addAll(exprType1);
        newExprType.addAll(exprType2);
        return newExprType;
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
        Set<Map<String, AstType>> thatDictSet = that.getDictSet();
        for(Map<String, AstType> m : dictSet){
            Map<String, AstType> newGamma = new HashMap<>();
            newGamma.putAll(m);
            newSet.add(newGamma);
        }
        for(Map<String, AstType> m : thatDictSet){
            Map<String, AstType> newGamma = new HashMap<>();
            newGamma.putAll(m);
            newSet.add(newGamma);
        }
        return new TypeMapFull(newSet, null);
    }
    private int indexOf(String[] varNames, String v) {
        for (int i = 0; i < varNames.length; i++) {
            if (varNames[i].equals(v)) {
                return i;
            }
        }
        return -1;
    }
    private Set<Map<String, AstType>> exceptSet(Set<Map<String, AstType>> set, String[] varNames){
        Set<Map<String, AstType>> newSet = new HashSet<>();
        for(Map<String, AstType> m : set){
            Map<String, AstType> newMap = new HashMap<>();
            for (String s : m.keySet()) {
                AstType t = m.get(s);
                int index = indexOf(varNames, s);
                if (index == -1)
                    newMap.put(s, t);
            }
            newSet.add(newMap);
        }
        return newSet;
    }
    public TypeMapBase enterCase(String[] varNames, VMDataTypeVecSet caseCondition){
        Set<VMDataType[]> conditionSet = caseCondition.getTuples();
        Set<Map<String, AstType>> newSet = new HashSet<>();
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
            /*
            for(VMDataType[] v : conditionSet){
                Map<String, AstType> condGamma = new HashMap<>();
                int length = varNames.length;
                for(int i=0; i<length; i++){
                    condGamma.put(varNames[i], AstType.get(v[i]));
                }
                Set<Map<String, AstType>> exceptCondSet = exceptSet(dictSet, varNames);
                for(Map<String, AstType> m : exceptCondSet){
                    Map<String, AstType> newGamma = new HashMap<>();
                    newGamma.putAll(condGamma);
                    newGamma.putAll(m);
                    newSet.add(newGamma);
                }
            }*/
        }
        return new TypeMapFull(newSet, null);
    }
    public TypeMapBase rematch(String[] params, String[] args, Set<String> domain){
        Set<Map<String, AstType>> newSet = new HashSet<>();
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
        return new TypeMapFull(newSet, null);
    }
    public TypeMapBase getBottomDict(){
        Set<String> domain = getKeys();
        Map<String, AstType> newGamma = new HashMap<>();
        Set<Map<String, AstType>> newSet = new HashSet<>();
        for (String v : domain) {
            newGamma.put(v, AstType.BOT);
        }
        newSet.add(newGamma);
        return new TypeMapFull(newSet, null);
    }
    public Set<VMDataType[]> filterTypeVecs(String[] formalParams, Set<VMDataType[]> vmtVecs){
        Set<VMDataType[]> filtered = new HashSet<VMDataType[]>();
        int length = formalParams.length;
        for(VMDataType[] dts : vmtVecs){
            NEXT_MAP: for(Map<String, AstType> m : dictSet){
                for(int i=0; i<length; i++){
                    VMDataType dt = dts[i];
                    AstType xt = m.get(formalParams[i]);
                    if(xt==null) continue NEXT_MAP;
                    if(xt instanceof JSValueType){
                        JSValueType t = (JSValueType)xt;
                        if(!t.isSuperOrEqual(JSValueType.get(dt))) continue NEXT_MAP;
                    }else
                        throw new Error("internal error :"+xt.toString());
                }
                filtered.add(dts);
                break;
            }
        }
        return filtered;
    }
    public boolean hasBottom(){
        for(Map<String, AstType> m : dictSet){
            for(AstType t : m.values()){
                if(t==AstType.BOT) return true;
            }
        }
        return false;
    }
    @Override
    public String toString() {
        return dictSet.toString();
    }
    @Override
    public boolean equals(Object obj) { //Set<Map>>のequalsは期待通りに動作するのか？
        if (this == obj || obj != null && obj instanceof TypeMapFull) {
            TypeMapFull tm = (TypeMapFull)obj;
            Set<Map<String, AstType>> tmDictSet = tm.getDictSet();
            return (dictSet != null && tmDictSet !=null && dictSet.equals(tmDictSet)) ||
                (exprType != null && tm.exprType != null && exprType.equals(tm.exprType));
        } else {
            return false;
        }
    }
}