package type;

import java.util.HashSet;
import java.util.Set;

public class ExprTypeSet {
    private Set<AstType> typeSet;

    public ExprTypeSet(){
        typeSet = new HashSet<>();
    }

    public ExprTypeSet(AstType type){
        typeSet = new HashSet<>(1);
        typeSet.add(type);
    }

    public void add(AstType type){
        if(type == AstType.BOT) return;
        typeSet.add(type);
    }

    public Set<AstType> getTypeSet(){
        return typeSet;
    }

    @Override
    public String toString(){
        return typeSet.toString();
    }
}