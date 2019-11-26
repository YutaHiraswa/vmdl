/*
 * eJS Project
 * Kochi University of Technology
 * The University of Electro-communications
 *
 * The eJS Project is the successor of the SSJS Project at The University of
 * Electro-communications.
 */
package vmdlc;

import nez.ast.Tree;
import nez.ast.TreeVisitorMap;
import nez.ast.Symbol;

import java.util.Set;
import java.util.Map;
import java.util.Stack;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;

import vmdlc.TypeCheckVisitor.DefaultVisitor;
import type.AstType.*;
import type.AstType;
import type.ExprTypeSet;
import type.ExprTypeSetLub;
import type.ExprTypeSetDetail;
import type.OperatorTypeChecker;
import type.TypeMap;
import type.TypeMapSet;
import type.TypeMapSetLub;
import type.TypeMapSetHalf;
import type.TypeMapSetFull;
import type.VMDataType;
import type.VMDataTypeVecSet;

public class TypeCheckVisitor extends TreeVisitorMap<DefaultVisitor> {
    static class MatchStack {
        static class MatchRecord {
            String name;
            String[] formalParams;
            TypeMapSet dict;

            MatchRecord(String name, String[] formalParams, TypeMapSet dict) {
                this.name = name;
                this.formalParams = formalParams;
                this.dict = dict;
            }

            public void setDict(TypeMapSet _dict) {
                dict = _dict;
            }

            public String toString() {
                return name;
            }
        }

        Stack<MatchRecord> stack;

        public MatchStack() {
            stack = new Stack<MatchRecord>();
        }

        MatchRecord lookup(String name) {
            for (int i = stack.size() - 1; i >= 0; i--) {
                MatchRecord mr = stack.get(i);
                if (mr.name != null && mr.name.equals(name))
                    return mr;
            }
            return null;
        }

        public void enter(String name, String[] formalParams, TypeMapSet dict) {
            MatchRecord mr = new MatchRecord(name, formalParams, dict);
            stack.push(mr);
        }

        public String[] getParams(String name) {
            MatchRecord mr = lookup(name);
            if (mr == null)
                return null;
            return mr.formalParams;
        }

        public TypeMapSet getDict(String name) {
            MatchRecord mr = lookup(name);
            if (mr == null)
                return null;
            return mr.dict;
        }

        public TypeMapSet pop() {
            MatchRecord mr = stack.pop();
            return mr.dict;
        }

        public boolean isEmpty() {
            return stack.isEmpty();
        }

        public void updateDict(String name, TypeMapSet dict) {
            MatchRecord mr = lookup(name);
            if (mr != null) {
                mr.setDict(dict);
            }
        }

        public String toString() {
            return stack.toString();
        }
    }

    MatchStack matchStack;

    OperandSpecifications opSpec;

    public static enum CheckTypePlicy{
        Lub(new TypeMapSetLub(), new ExprTypeSetLub()),
        Half(new TypeMapSetHalf(), new ExprTypeSetDetail()),
        Full(new TypeMapSetFull(), new ExprTypeSetDetail());

        private TypeMapSet typeMap;
        private ExprTypeSet exprTypeSet;
        private CheckTypePlicy(TypeMapSet typeMap, ExprTypeSet exprTypeSet){
            this.typeMap = typeMap;
            this.exprTypeSet = exprTypeSet;
        }
        public TypeMapSet getTypeMap(){
            return typeMap;
        }
        public ExprTypeSet getExprTypeSet(){
            return exprTypeSet;
        }
    };
    public static TypeMapSet TYPE_MAP;
    public static ExprTypeSet EXPR_TYPE;

    public TypeCheckVisitor() {
        init(TypeCheckVisitor.class, new DefaultVisitor());
    }

    public void start(Tree<?> node, OperandSpecifications opSpec, CheckTypePlicy policy) {
        this.opSpec = opSpec;
        try {
            TYPE_MAP  = policy.getTypeMap();
            EXPR_TYPE = policy.getExprTypeSet();
            TypeMapSet dict = TYPE_MAP.clone();
            matchStack = new MatchStack();
            for (Tree<?> chunk : node) {
                dict = visit((SyntaxTree) chunk, dict);
            }
            if (!matchStack.isEmpty())
                throw new Error("match stack is not empty after typing process");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Visit for statement
    private final TypeMapSet visit(SyntaxTree node, TypeMapSet dict) throws Exception {
        /*
        System.err.println("==================");
        System.err.println(node.getTag().toString());
        System.err.println(node.toString());
        System.err.println("----");
        System.err.println("dict:"+dict.toString());
        System.err.println("----");
        */
        return find(node.getTag().toString()).accept(node, dict);
    }

    // Visit for expression
    private final ExprTypeSet visit(SyntaxTree node, TypeMap dict) throws Exception {
        /*
         * System.err.println("==================");
         * System.err.println(node.getTag().toString());
         * System.err.println(node.toString()); System.err.println("----");
         * System.err.println("dict:"+dict.toString()); System.err.println("----");
         * System.err.println("exprMap:"+dict.getExprTypeMap().toString());
         */
        return find(node.getTag().toString()).accept(node, dict);
    }

    private final void save(SyntaxTree node, TypeMapSet dict) throws Exception {
        find(node.getTag().toString()).saveType(node, dict);
    }

    public class DefaultVisitor {
        public TypeMapSet accept(SyntaxTree node, TypeMapSet dict) throws Exception {
            return dict;
        }

        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
            return null;
        }

        public void saveType(SyntaxTree node, TypeMapSet dict) throws Exception {
            for (SyntaxTree chunk : node) {
                save(chunk, dict);
            }
        }
    }

    //**********************************
    // Statements
    //**********************************

    public class FunctionMeta extends DefaultVisitor {
        @Override
        public TypeMapSet accept(SyntaxTree node, TypeMapSet dict) throws Exception {
            SyntaxTree type = node.get(Symbol.unique("type"));
            AstProductType funtype = (AstProductType) AstType.nodeToType((SyntaxTree) type);

            SyntaxTree definition = node.get(Symbol.unique("definition"));

            SyntaxTree nodeName = node.get(Symbol.unique("name"));
            SyntaxTree nameNode = definition.get(Symbol.unique("name"));
            String name = nameNode.toText();
            TypeMap.addGlobal(name, funtype);

            Set<String> domain = new HashSet<String>(dict.getKeys());

            Set<TypeMap> newSet = dict.clone().getTypeMapSet();
            Set<TypeMap> newSet2 = new HashSet<>();
            /* add non-JSValue parameters */
            SyntaxTree paramsNode = definition.get(Symbol.unique("params"));
            if (paramsNode != null && paramsNode.size() != 0) {
                String[] paramNames = new String[paramsNode.size()];
                String[] jsvParamNames = new String[paramsNode.size()];
                AstType paramTypes = funtype.getDomain();
                int nJsvTypes = 0;
                
                Map<String, AstType> nJsvMap = new HashMap<>();
                if (paramTypes instanceof AstBaseType) {
                    AstType paramType = paramTypes;
                    String paramName = paramsNode.get(0).toText();
                    paramNames[0] = paramName;
                    if (paramType instanceof JSValueType)
                        jsvParamNames[nJsvTypes++] = paramName;
                    else
                        nJsvMap.put(paramName, paramType);
                } else if (paramTypes instanceof AstPairType) {
                    List<AstType> paramTypeList = ((AstPairType) paramTypes).getTypes();
                    for (int i = 0; i < paramTypeList.size(); i++) {
                        AstType paramType = paramTypeList.get(i);
                        String paramName = paramsNode.get(i).toText();
                        paramNames[i] = paramName;
                        if (paramType instanceof JSValueType)
                            jsvParamNames[nJsvTypes++] = paramName;
                        else
                            nJsvMap.put(paramName, paramType);
                    }
                }
                for(TypeMap map : dict){
                    for(String v : nJsvMap.keySet()){
                        AstType t = nJsvMap.get(v);
                        newSet.addAll(dict.getAddedSet(map, v, t));
                    }
                }
                if(newSet.isEmpty()){
                    newSet.add(new TypeMap());
                }
                /* add JSValue parameters (apply operand spec) */
                if (nJsvTypes > 0) {
                    String[] jsvParamNamesPacked = new String[nJsvTypes];
                    System.arraycopy(jsvParamNames, 0, jsvParamNamesPacked, 0, nJsvTypes);
                    VMDataTypeVecSet vtvs = opSpec.getAccept(name, jsvParamNamesPacked);
                    Set<VMDataType[]> tupleSet = vtvs.getTuples();
                    String[] variableStrings = vtvs.getVarNames();
                    int length = variableStrings.length;
                    Set<Map<String, AstType>> jsvMapSet = new HashSet<>();
                    if (tupleSet.isEmpty()) {
                        Map<String, AstType> tempMap = new HashMap<>();
                        for (int i = 0; i < length; i++) {
                            tempMap.put(variableStrings[i], AstType.BOT);
                        }
                        jsvMapSet.add(tempMap);
                    } else {
                        for (VMDataType[] vec : tupleSet) {
                            Map<String, AstType> tempMap = new HashMap<>();
                            for (int i = 0; i < length; i++) {
                                tempMap.put(variableStrings[i], AstType.get(vec[i]));
                            }
                            jsvMapSet.add(tempMap);
                        }
                    }
                    for(TypeMap map : newSet){
                        for(Map<String, AstType> jsvMap : jsvMapSet){
                            newSet2.addAll(dict.getAddedSet(map, jsvMap));
                        }
                    }
                }
            }
            TypeMapSet newDict = TYPE_MAP.clone();
            newDict.setTypeMapSet(newSet2);
            newDict.setDispatchSet(node.getRematchVarSet());
            SyntaxTree body = (SyntaxTree) definition.get(Symbol.unique("body"));
            dict = visit((SyntaxTree) body, newDict);

            save(nameNode, dict);
            save(nodeName, dict);
            save(paramsNode, dict);
            return dict.select(domain);
        }

        public void saveType(SyntaxTree node, TypeMapSet dict) throws Exception {
        }
    }

    public class Parameters extends DefaultVisitor {
        @Override
        public TypeMapSet accept(SyntaxTree node, TypeMapSet dict) throws Exception {
            for (SyntaxTree chunk : node) {
                // TODO: update dict
                visit(chunk, dict);
                save(chunk, dict);
            }
            return dict;
        }

        public void saveType(SyntaxTree node, TypeMapSet dict) throws Exception {
        }
    }

    public class Block extends DefaultVisitor {
        @Override
        public TypeMapSet accept(SyntaxTree node, TypeMapSet dict) throws Exception {
            Set<String> domain = new HashSet<String>(dict.getKeys());
            for (SyntaxTree seq : node) {
                dict = visit(seq, dict);
                save(seq, dict);
            }
            TypeMapSet result = dict.select(domain);
            return result;
        }

        public void saveType(SyntaxTree node, TypeMapSet dict) throws Exception {
        }
    }

    public class Match extends DefaultVisitor {
        @Override
        public TypeMapSet accept(SyntaxTree node, TypeMapSet dict) throws Exception {
            MatchProcessor mp = new MatchProcessor(node);
            SyntaxTree labelNode = node.get(Symbol.unique("label"), null);
            String label = labelNode == null ? null : labelNode.toText();
            TypeMapSet outDict = dict.getBottomDict();
            TypeMapSet entryDict;
            TypeMapSet newEntryDict = dict;
            do {
                entryDict = newEntryDict;
                matchStack.enter(label, mp.getFormalParams(), entryDict);
                for (int i = 0; i < mp.size(); i++) {
                    TypeMapSet dictCaseIn = entryDict.enterCase(mp.getFormalParams(), mp.getVMDataTypeVecSet(i));
                    if (dictCaseIn.noInformationAbout(mp.getFormalParams())){
                        continue;
                    }
                    SyntaxTree body = mp.getBodyAst(i);
                    TypeMapSet dictCaseOut = visit(body, dictCaseIn);
                    outDict = outDict.combine(dictCaseOut);
                }
                newEntryDict = matchStack.pop();
            } while (!entryDict.equals(newEntryDict));
            node.setTypeMapSet(entryDict);
            SyntaxTree paramsNode = node.get(Symbol.unique("params"));
            save(paramsNode, outDict);
            return outDict;
        }
    }

    public class Rematch extends DefaultVisitor {
        @Override
        public TypeMapSet accept(SyntaxTree node, TypeMapSet dict) throws Exception {
            String label = node.get(Symbol.unique("label")).toText();
            TypeMapSet matchDict = matchStack.getDict(label);
            if (matchDict == null){
                throw new Error("Labeled Match not found: "+label+" (at line "+node.getLineNum()+")");
            }
            Set<String> domain = matchDict.getKeys();
            String[] matchParams = matchStack.getParams(label);

            String[] rematchArgs = new String[matchParams.length];
            for (int i = 1; i < node.size(); i++) {
                rematchArgs[i - 1] = node.get(i).toText();
            }

            TypeMapSet rematchDict = dict.rematch(matchParams, rematchArgs, domain);
            TypeMapSet newMatchDict = rematchDict.combine(matchDict);
            matchStack.updateDict(label, newMatchDict);

            return dict.getBottomDict();
        }
    }

    public class Return extends DefaultVisitor {
        @Override
        public TypeMapSet accept(SyntaxTree node, TypeMapSet dict) throws Exception {
            save(node.get(0), dict);
            return dict;
        }

        public void saveType(SyntaxTree node, TypeMapSet dict) throws Exception {
        }
    }

    final static AstType tCint = AstType.get("cint");
    final static AstType tCdobule = AstType.get("cdouble");
    final static AstType tSubscript = AstType.get("Subscript");
    final static AstType tDisplacement = AstType.get("Displacement");
    final static Set<AstType> cNumbers = new HashSet<>();
    final static Set<AstType> cInts = new HashSet<>();
    static{
        cNumbers.add(tCint);
        cNumbers.add(tCdobule);
        cNumbers.add(tSubscript);
        cNumbers.add(tDisplacement);
        cInts.add(tCint);
        cInts.add(tCdobule);
        cInts.add(tSubscript);
        cInts.add(tDisplacement);

    }
    private boolean acceptAssign(AstType type, AstType assign){
        if(type.isSuperOrEqual(assign)) return true;
        if(type==tCdobule && cNumbers.contains(assign)) return true;
        if(cInts.contains(type) && cInts.contains(assign)) return true;
        return false;
    }

    public class Assignment extends DefaultVisitor {
        @Override
        public TypeMapSet accept(SyntaxTree node, TypeMapSet dict) throws Exception {
            SyntaxTree leftNode = node.get(Symbol.unique("left"));
            SyntaxTree rightNode = node.get(Symbol.unique("right"));
            String leftName = leftNode.toText();
            Set<TypeMap> newSet = new HashSet<>();
            for(TypeMap typeMap : dict){
                AstType leftType = typeMap.get(leftName);
                if(leftType instanceof JSValueType){
                    throw new Error("JSValue variable cannot assign (at line "+node.getLineNum()+")");
                }
                ExprTypeSet exprTypeSet = visit(rightNode, typeMap);
                for(AstType type : exprTypeSet){
                    if(!acceptAssign(leftType, type)){
                        throw new Error("Expression types "+type+", need types "+leftType+" (at line "+node.getLineNum()+")");
                    }
                    TypeMap temp = typeMap.clone();
                    Set<TypeMap> assignedSet = dict.getAssignedSet(temp, leftName, type);
                    newSet.addAll(assignedSet);
                }
            }
            TypeMapSet newTypeMapSet = TYPE_MAP.clone();
            newTypeMapSet.setTypeMapSet(newSet);
            return newTypeMapSet;
        }

        public void saveType(SyntaxTree node, TypeMapSet dict) throws Exception {
        }
    }

    public class Declaration extends DefaultVisitor {
        @Override
        public TypeMapSet accept(SyntaxTree node, TypeMapSet dict) throws Exception {
            SyntaxTree typeNode = node.get(Symbol.unique("type"));
            SyntaxTree varNode = node.get(Symbol.unique("var"));
            SyntaxTree exprNode = node.get(Symbol.unique("expr"));
            AstType varType = AstType.get(typeNode.toText());
            String varName = varNode.toText();
            Set<TypeMap> newSet = new HashSet<>();
            for(TypeMap typeMap : dict){
                ExprTypeSet exprTypeSet = visit(exprNode, typeMap);
                for(AstType type : exprTypeSet){
                    if(!acceptAssign(varType, type)){
                        throw new Error("Expression types "+type+", need types "+varType+" (at line "+node.getLineNum()+")");
                    }
                    TypeMap temp = typeMap.clone();
                    Set<TypeMap> addedSet = dict.getAddedSet(temp, varName, type);
                    newSet.addAll(addedSet);
                }
            }
            TypeMapSet newTypeMapSet = TYPE_MAP.clone();
            newTypeMapSet.setTypeMapSet(newSet);
            save(exprNode, dict);
            save(varNode, dict);
            return newTypeMapSet;
        }
        @Override
        public void saveType(SyntaxTree node, TypeMapSet dict) throws Exception {
        }
    }

    public class If extends DefaultVisitor {
        @Override
        public TypeMapSet accept(SyntaxTree node, TypeMapSet dict) throws Exception {
            TypeMapSet copyDict = dict.clone();
            SyntaxTree thenNode = node.get(Symbol.unique("then"));
            TypeMapSet thenDict = visit(thenNode, dict);
            SyntaxTree elseNode = node.get(Symbol.unique("else"));
            TypeMapSet resultDict;
            if (elseNode == null) {
                resultDict = thenDict;
            } else {
                TypeMapSet elseDict = visit(elseNode, copyDict);
                if(!thenDict.getKeys().equals(elseDict.getKeys())){
                    throw new Error("Both environment keys must be equal: "
                    +thenDict.getKeys().toString()+", "+elseDict.getKeys().toString()
                    +" (at line "+node.getLineNum()+")");
                }
                resultDict = thenDict.combine(elseDict);
            }
            SyntaxTree condNode = node.get(Symbol.unique("cond"));
            save(condNode, resultDict);

            return resultDict;
        }

        @Override
        public void saveType(SyntaxTree node, TypeMapSet dict) throws Exception {
        }
    }

    public class Do extends DefaultVisitor {
        @Override
        public TypeMapSet accept(SyntaxTree node, TypeMapSet dict) throws Exception {
            SyntaxTree initNode = node.get(Symbol.unique("init"));
            SyntaxTree typeNode = initNode.get(Symbol.unique("type"));
            SyntaxTree varNode = initNode.get(Symbol.unique("var"));
            SyntaxTree exprNode = initNode.get(Symbol.unique("expr"));
            AstType varType = AstType.get(typeNode.toText());
            String varName = varNode.toText();
            Set<TypeMap> doSet = new HashSet<>();
            for(TypeMap typeMap : dict){
                ExprTypeSet exprTypeSet = visit(exprNode, typeMap);
                for(AstType t : exprTypeSet){
                    if(!(varType.isSuperOrEqual(t))){
                        throw new Error("Illigal type declare: "+t.toString()+" (at line "+node.getLineNum()+")");
                    }
                    doSet.addAll(dict.getAddedSet(typeMap, varName, t));
                }
            }
            TypeMapSet doDict = TYPE_MAP.clone();
            doDict.setTypeMapSet(doSet);
            TypeMapSet savedDict;
            do {
                savedDict = doDict.clone();
                SyntaxTree blockNode = initNode.get(Symbol.unique("block"));
                doDict = visit(blockNode, doDict);
            } while (!dict.equals(savedDict));
            return dict;
        }
    }

    //**********************************
    // ExternCs
    //**********************************

    public class CTypeDef extends DefaultVisitor {
        @Override
        public TypeMapSet accept(SyntaxTree node, TypeMapSet dict) throws Exception {
            SyntaxTree varNode = node.get(Symbol.unique("var"));
            SyntaxTree typeNode = node.get(Symbol.unique("type"));
            AstType type = AstType.get(typeNode.toText());
            TypeMap.addGlobal(varNode.toText(), type);
            return dict;
        }
    }

    public class CFunction extends DefaultVisitor {
        @Override
        public TypeMapSet accept(SyntaxTree node, TypeMapSet dict) throws Exception {
            SyntaxTree typeNode = node.get(Symbol.unique("type"));
            AstType domain = AstType.nodeToType(typeNode.get(0));
            AstType range = AstType.nodeToType(typeNode.get(1));
            SyntaxTree nameNode = node.get(Symbol.unique("name"));
            AstType type = new AstProductType(domain, range);
            TypeMap.addGlobal(nameNode.toText(), type);
            return dict;
        }
    }

    public class CConstantDef extends DefaultVisitor {
        @Override
        public TypeMapSet accept(SyntaxTree node, TypeMapSet dict) throws Exception {
            SyntaxTree typeNode = node.get(Symbol.unique("type"));
            SyntaxTree varNode = node.get(Symbol.unique("var"));
            AstType type = AstType.get(typeNode.toText());
            TypeMap.addGlobal(varNode.toText(), type);

            return dict;
        }
    }

    //**********************************
    // Expressions
    //**********************************

    //**********************************
    // TrinaryOperator
    //**********************************

    public class Trinary extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
            SyntaxTree condNode = node.get(Symbol.unique("cond"));
			SyntaxTree thenNode = node.get(Symbol.unique("then"));
            SyntaxTree elseNode = node.get(Symbol.unique("else"));
            ExprTypeSet condExprTypeSet = visit(condNode, dict);
            ExprTypeSet thenExprTypeSet = visit(thenNode, dict);
            ExprTypeSet elseExprTypeSet = visit(elseNode, dict);
            for(AstType type : condExprTypeSet){
                if(type != AstType.get("cint")){
                    throw new Error("Illigal types given in trinary operator condition: "
                        +condExprTypeSet.toString()+" (at line "+node.getLineNum()+")");
                }
            }
            return thenExprTypeSet.combine(elseExprTypeSet);
        }
    }

    //**********************************
    // BiaryOperators
    //**********************************

    private ExprTypeSet biaryOperator(SyntaxTree node, TypeMap dict, OperatorTypeChecker checker) throws Exception{
        SyntaxTree leftNode  = node.get(Symbol.unique("left"));
        SyntaxTree rightNode = node.get(Symbol.unique("right"));
        ExprTypeSet leftExprTypeSet  = visit(leftNode, dict);
        ExprTypeSet rightExprTypeSet = visit(rightNode, dict);
        ExprTypeSet resultTypeSet = EXPR_TYPE.clone();
        for (AstType lt : leftExprTypeSet){
            for (AstType rt : rightExprTypeSet){
                AstType result = checker.typeOf(lt, rt);
                if(result == null){
                    throw new Error("Illigal types given in operator: "
                        +lt.toString()+","+rt.toString()+" (at line "+node.getLineNum()+")");
                }
                resultTypeSet.add(result);
            }
        }
		return resultTypeSet;
    }

    public class Or extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			return biaryOperator(node, dict, OperatorTypeChecker.OR);
        }
    }

    public class And extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			return biaryOperator(node, dict, OperatorTypeChecker.AND);
        }
    }

    public class BitwiseOr extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			return biaryOperator(node, dict, OperatorTypeChecker.BITWISE_OR);
        }
    }

    public class BitwiseXOr extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			return biaryOperator(node, dict, OperatorTypeChecker.BITWISE_XOR);
        }
    }

    public class BitwiseAnd extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			return biaryOperator(node, dict, OperatorTypeChecker.BITWISE_AND);
        }
    }

    public class Equals extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			return biaryOperator(node, dict, OperatorTypeChecker.EQUALS);
        }
    }

    public class NotEquals extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			return biaryOperator(node, dict, OperatorTypeChecker.NOT_EQUALS);
        }
    }

    public class LessThanEquals extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			return biaryOperator(node, dict, OperatorTypeChecker.LESSTHAN_EQUALS);
        }
    }

    public class GreaterThanEquals extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			return biaryOperator(node, dict, OperatorTypeChecker.GRATORTHAN_EQUALS);
        }
    }

    public class LessThan extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			return biaryOperator(node, dict, OperatorTypeChecker.LESSTHAN);
        }
    }

    public class GreaterThan extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			return biaryOperator(node, dict, OperatorTypeChecker.GRATORTHAN);
        }
    }

    public class LeftShift extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			return biaryOperator(node, dict, OperatorTypeChecker.LEFT_SHIFT);
        }
    }

    public class RightShift extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			return biaryOperator(node, dict, OperatorTypeChecker.RIGHT_SHIFT);
        }
    }

    public class Add extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			return biaryOperator(node, dict, OperatorTypeChecker.ADD);
        }
    }

    public class Sub extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			return biaryOperator(node, dict, OperatorTypeChecker.SUB);
        }
    }

    public class Mul extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			return biaryOperator(node, dict, OperatorTypeChecker.MUL);
        }
    }

    public class Div extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			return biaryOperator(node, dict, OperatorTypeChecker.DIV);
        }
    }

    public class Mod extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			return biaryOperator(node, dict, OperatorTypeChecker.MOD);
        }
    }

    //**********************************
    // UnaryOperators
    //**********************************

    private ExprTypeSet unaryOperator(SyntaxTree node, TypeMap dict, OperatorTypeChecker checker) throws Exception{
        SyntaxTree exprNode = node.get(Symbol.unique("expr"));
            ExprTypeSet exprTypeSet = visit(exprNode, dict);
            ExprTypeSet resultTypeSet = EXPR_TYPE.clone();
            for (AstType t : exprTypeSet) {
                AstType result = checker.typeOf(t);
                if(result == null){
                    throw new Error("Illigal types given in operator: "+exprTypeSet.toString()+" (at line "+node.getLineNum()+")");
                }
                resultTypeSet.add(result);
            }
			return resultTypeSet;
    }

    public class Plus extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			return unaryOperator(node, dict, OperatorTypeChecker.PLUS);
        }
    }

    public class Minus extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
            return unaryOperator(node, dict, OperatorTypeChecker.MINUS);
        }
    }

    public class Compl extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
            return unaryOperator(node, dict, OperatorTypeChecker.COMPL);
        }
    }

    public class Not extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
            return unaryOperator(node, dict, OperatorTypeChecker.NOT);
        }
    }

    //*********************************
    // FunctionCall
    //*********************************

    public class FunctionCall extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
            SyntaxTree recv = node.get(Symbol.unique("recv"));
            String functionName = recv.toText();
            AstType type = dict.get(functionName);
            if(type == null){
                throw new Error("function is not defined: "+functionName);
            }
            if(!(type instanceof AstProductType)){
                throw new Error("function is not AstProductType: "+functionName);
            }
            AstProductType functionType = (AstProductType)type;
            //TODO: domain check
            //AstType domain = functionType.getDomain();
            AstType range  = functionType.getRange();
            ExprTypeSet newSet = EXPR_TYPE.clone();
            newSet.add(range);
            return newSet;
        }
    }

    //*********************************
    // Others
    //*********************************

    public class ArrayIndex extends DefaultVisitor {
        @Override
        public TypeMapSet accept(SyntaxTree node, TypeMapSet dict) throws Exception {
            // TODO
            return dict;
        }
    }
    public class Field extends DefaultVisitor {
        @Override
        public TypeMapSet accept(SyntaxTree node, TypeMapSet dict) throws Exception {
            // TODO
            return dict;
        }
    }

    //*********************************
    // Constants
    //*********************************

    public class _Integer extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
            ExprTypeSet newSet = EXPR_TYPE.clone();
            newSet.add(AstType.get("cint"));
			return newSet;
        }
    }

    public class _Float extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			ExprTypeSet newSet = EXPR_TYPE.clone();
            newSet.add(AstType.get("cdouble"));
			return newSet;
        }
    }

    public class _String extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			ExprTypeSet newSet = EXPR_TYPE.clone();
            newSet.add(AstType.get("cstring"));
			return newSet;
        }
    }

    public class _True extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			ExprTypeSet newSet = EXPR_TYPE.clone();
            newSet.add(AstType.get("cint"));
			return newSet;
        }
    }
    public class _False extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
			ExprTypeSet newSet = EXPR_TYPE.clone();
            newSet.add(AstType.get("cint"));
			return newSet;
        }
    }

    //*********************************
    // Variables
    //*********************************

    public class Name extends DefaultVisitor {
        @Override
        public ExprTypeSet accept(SyntaxTree node, TypeMap dict) throws Exception {
            String name = node.toText();
            if (!dict.containsKey(name)) {
                throw new Error("No such name: "+"\""+name+"\" (at line "+node.getLineNum()+")");
            }
            ExprTypeSet newSet = EXPR_TYPE.clone();
            newSet.add(dict.get(name));
			return newSet;
        }
        public void saveType(SyntaxTree node, TypeMapSet dict) throws Exception {
            String name = node.toText();
            if (dict.containsKey(name)) {
                ExprTypeSet newSet = EXPR_TYPE.clone();
                for(TypeMap map : dict){
                    newSet.add(map.get(name));
                }
                node.setExprTypeSet(newSet);
            }
        }
    }
}
