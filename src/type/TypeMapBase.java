package type;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public abstract class TypeMapBase {
    Set<AstType> exprType;

    public TypeMapBase(){
    }

    public TypeMapBase(Set<AstType> _exprType){
        exprType = _exprType;
    }

    public abstract Set<Map<String, AstType>> getDictSet();
    public abstract Set<AstType> get(String name);
    public abstract void addDispatch(String name);
    public abstract void removeAllDispatch();
    public abstract void add(String name, AstType type);
    public abstract void add(Map<String, AstType> map);
    public abstract void add(Set<Map<String, AstType>> set);
    //public abstract void add(VMDataTypeVecSet vtvs) <- 上のaddを用いる形に書き換えるのが良さげ？
    public abstract boolean containsKey(String key);
    public abstract Set<String> getKeys();
    public abstract TypeMapBase select(Collection<String> domain); //TODO: CollectionとSetの違いについて調べる
    public TypeMapBase clone(){
        return null;
    }
    public Set<AstType> getExprType() {
        return exprType;
    }
    public void setExprType(Set<AstType> _exprType) {
        exprType = _exprType;
    }
    public abstract Set<AstType> combineExprTypes(Set<AstType> exprType1, Set<AstType> exprType2);
    public abstract TypeMapBase combine(TypeMapBase that);
    public abstract TypeMapBase enterCase(String[] varNames, VMDataTypeVecSet caseCondition);
    public abstract TypeMapBase rematch(String[] params, String[] args, Set<String> domain);
    public abstract TypeMapBase getBottomDict();
    public abstract Set<VMDataType[]> filterTypeVecs(String[] formalParams, Set<VMDataType[]> vmtVecs);
    public abstract boolean hasBottom();
}