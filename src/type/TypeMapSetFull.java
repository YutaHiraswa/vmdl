package type;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import type.AstType.AstBaseType;
import type.AstType.JSValueType;
import type.AstType.JSValueVMType;

public class TypeMapSetFull extends TypeMapSet {

    public TypeMapSetFull(Set<TypeMap> _typeMapSet){
        super(_typeMapSet);
    }

    @Override
    public void addDispatch(String name){}
    @Override
    public void clearDispatch(){}
    @Override
    public Set<String> getDispatchSet(){
        return new HashSet<String>(0);
    }
    protected boolean needDetailType(String name, AstType type){
        return ((type instanceof JSValueType) && !(type instanceof JSValueVMType));
    }
    @Override
    public Set<TypeMap> getAddedSet(TypeMap typeMap, String name, AstType type){
        Set<AstType> addTypes = new HashSet<>();
        Set<TypeMap> addedSet = new HashSet<>();
        if(needDetailType(name, type)){
            addTypes.addAll(AstType.getChildren((JSValueType)type));
        }else{
            addTypes.add(type);
        }
        for(AstType t : addTypes){
            TypeMap temp = typeMap.clone();
            temp.add(name, t);
            addedSet.add(temp);
        }
        return addedSet;
    }
    @Override
    public Set<TypeMap> getAssignedSet(TypeMap typeMap, String name, AstType type){
        Set<AstType> addTypes = new HashSet<>();
        Set<TypeMap> assignedSet = new HashSet<>();
        if(needDetailType(name, type)){
            addTypes.addAll(AstType.getChildren((JSValueType)type));
        }else{
            addTypes.add(type);
        }
        for(AstType t : addTypes){
            TypeMap temp = typeMap.clone();
            temp.assign(name, t);
            assignedSet.add(temp);
        }
        return assignedSet;
    }
    @Override
    public boolean containsKey(String key){
        if(typeMapSet.isEmpty()) return false;
        TypeMap typeMap = ((TypeMap[])typeMapSet.toArray())[0];
        return typeMap.containsKey(key);
    }
    @Override
    public Set<String> getKeys(){
        if(typeMapSet.isEmpty()) return null;
        TypeMap typeMap = ((TypeMap[])typeMapSet.toArray())[0];
        return typeMap.keySet();
    }
    @Override
    public TypeMapSet select(Collection<String> domain){
        Set<TypeMap> selectedSet = new HashSet<>();
        for(TypeMap m : typeMapSet){
            TypeMap selectedMap = new TypeMap();
            for(String s : domain){
                AstType type = m.get(s);
                if(type==null){
                    if(containsKey(s)){
                        System.err.println("InternalWarnig: TypeMap has no element: \""+s+"\"");
                        type = AstType.BOT;
                    }else{
                        throw new Error("InternalError: No such element: \""+s+"\"");
                    }
                }
                selectedMap.add(s, type);
            }
            selectedSet.add(selectedMap);
        }
        return new TypeMapSetFull(selectedSet);
    }
    @Override
    public TypeMapSet clone(){
        Set<TypeMap> cloneTypeMapSet = new HashSet<>();
        for(TypeMap typeMap : typeMapSet){
            cloneTypeMapSet.add(typeMap.clone());
        }
        return new TypeMapSetFull(cloneTypeMapSet);
    }
    @Override
    public TypeMapSet combine(TypeMapSet that){
        Set<TypeMap> newTypeMapSet = new HashSet<>();
        Set<TypeMap> thatTypeMapSet = that.getTypeMapSet();
        for(TypeMap m : typeMapSet){
            newTypeMapSet.add(m.clone());
        }
        for(TypeMap m : thatTypeMapSet){
            newTypeMapSet.add(m.clone());
        }
        return new TypeMapSetFull(newTypeMapSet);
    }
    @Override
    public TypeMapSet enterCase(String[] varNames, VMDataTypeVecSet caseCondition){
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
                        if(!(m.get(varNames[i]) instanceof JSValueType)) continue NEXT_MAP;
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
        return new TypeMapSetFull(newSet, null);
    }
    private int indexOf(String[] varNames, String v) {
        for (int i = 0; i < varNames.length; i++) {
            if (varNames[i].equals(v)) {
                return i;
            }
        }
        return -1;
    }
    @Override
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
        return new TypeMapSetFull(newSet, null);
    }
    @Override
    public TypeMapBase getBottomDict(){
        Set<String> domain = getKeys();
        Map<String, AstType> newGamma = new HashMap<>();
        Set<Map<String, AstType>> newSet = new HashSet<>();
        for (String v : domain) {
            newGamma.put(v, AstType.BOT);
        }
        newSet.add(newGamma);
        return new TypeMapSetFull(newSet, null);
    }
    @Override
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
                    } else if (xt == AstType.BOT){
                        continue NEXT_MAP;
                    } else
                        throw new Error("internal error: "+formalParams[i]+"="+xt.toString());
                    }
                filtered.add(dts);
                break;
            }
        }
        return filtered;
    }
    @Override
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
        return dictSet.toString()+", "+globalDict.toString();
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj || obj != null && obj instanceof TypeMapSetFull) {
            TypeMapSetFull tm = (TypeMapSetFull)obj;
            Set<Map<String, AstType>> tmDictSet = tm.getDictSet();
            return (dictSet != null && tmDictSet !=null && dictSet.equals(tmDictSet)) &&
                (exprTypeMap != null && tm.exprTypeMap != null && exprTypeMap.equals(tm.exprTypeMap));
        } else {
            return false;
        }
    }
    @Override
    public Map<Map<String, AstType>, AstType> combineExprTypeMap(Map<Map<String, AstType>, AstType> exprTypeMap1, Map<Map<String, AstType>, AstType> exprTypeMap2) {
        Map<Map<String, AstType>, AstType> newExprTypeMap = new HashMap<>();
        for(Map<String,AstType> map : exprTypeMap1.keySet()){
            newExprTypeMap.put(map, exprTypeMap1.get(map));
        }
        for(Map<String,AstType> map : exprTypeMap2.keySet()){
            newExprTypeMap.put(map, exprTypeMap2.get(map));
        }
        return newExprTypeMap;
    }
    @Override
    public void putExprTypeElement(Map<String, AstType> key, AstType type) {
        if((type instanceof JSValueType) && !(type instanceof JSValueVMType)){
            for(JSValueVMType t : AstType.getChildren((JSValueType)type)){
                exprTypeMap.put(key, t);
            }
        }else{
            exprTypeMap.put(key, type);
        }
    }
}