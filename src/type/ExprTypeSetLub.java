package type;

import java.util.Set;

public class ExprTypeSetLub extends ExprTypeSet{

    public ExprTypeSetLub(){
        super();
    }

    public ExprTypeSetLub(AstType type){
        super(type);
    }

    @Override
    public void add(AstType type){
        if(type == AstType.BOT) return;
        if(typeSet.isEmpty()){
            typeSet.add(type);
        }else{
            if(typeSet.size() != 1){
                throw new Error("InternalError: Illigal exprTypeSet state: "+typeSet.toString());
            }
            AstType t = typeSet.iterator().next();
            typeSet.clear();
            typeSet.add(t.lub(type));
        }
    }

    @Override
    public ExprTypeSet combine(ExprTypeSet that){
        Set<AstType> thisSet = this.getTypeSet();
        Set<AstType> thatSet = that.getTypeSet();
        if(thisSet.size() > 1 || thatSet.size() > 1){
            throw new Error("InternalError: Illigal exprTypeSet state: "+typeSet.toString());
        }
        AstType t = typeSet.iterator().next();
        for(AstType type : thisSet){
            t = t.lub(type);
        }
        return new ExprTypeSetLub(t);
    }

    @Override
    public ExprTypeSet clone(){
        return new ExprTypeSetLub(typeSet.iterator().next());
    }
}