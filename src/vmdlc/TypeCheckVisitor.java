package vmdlc;

import nez.ast.Tree;
import nez.ast.TreeVisitorMap;
import nez.util.ConsoleUtils;
import nez.ast.Symbol;

import java.util.ArrayList;
import java.util.Set;
import java.util.Map;
import java.util.Stack;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;

import vmdlc.TypeCheckVisitor.DefaultVisitor;
import type.AstType.*;
import type.AstType;
import type.TypeMapBase;
import type.TypeMapHybrid;
import type.TypeMapFull;
import type.TypeMapLub;
import type.VMDataType;
import type.VMDataTypeVecSet;

public class TypeCheckVisitor extends TreeVisitorMap<DefaultVisitor> {
    static class MatchStack {
        static class MatchRecord {
            String name;
            String[] formalParams;
            TypeMapBase dict;
            MatchRecord(String name, String[] formalParams, TypeMapBase dict) {
                this.name = name;
                this.formalParams = formalParams;
                this.dict = dict;
            }
            public void setDict(TypeMapBase _dict) {
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
        
        public void enter(String name, String[] formalParams, TypeMapBase dict) {
            MatchRecord mr = new MatchRecord(name, formalParams, dict);
            stack.push(mr);
        }
        public String[] getParams(String name) {
            MatchRecord mr = lookup(name);
            if (mr == null)
                return null;
            return mr.formalParams;
        }
        public TypeMapBase getDict(String name) {
            MatchRecord mr = lookup(name);
            if (mr == null)
                return null;
            return mr.dict;
        }
        public TypeMapBase pop() {
            MatchRecord mr = stack.pop();
            return mr.dict;
        }
        public boolean isEmpty() {
            return stack.isEmpty();
        }
        public void updateDict(String name, TypeMapBase dict) {
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

    public static TypeMapBase TYPE_MAP;
    
    public TypeCheckVisitor() {
        init(TypeCheckVisitor.class, new DefaultVisitor());
    }

    public void start(Tree<?> node, OperandSpecifications opSpec, TypeMapBase typeMap) {
        this.opSpec = opSpec;
        try {
            TYPE_MAP = typeMap;
            TypeMapBase dict = TYPE_MAP.clone();
            matchStack = new MatchStack();
            for (Tree<?> chunk : node) {
                dict = visit((SyntaxTree)chunk, dict);
            }
            if (!matchStack.isEmpty())
                throw new Error("match stack is not empty after typing process");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final TypeMapBase visit(SyntaxTree node, TypeMapBase dict) throws Exception {
        //System.err.println("-----------------");
        //System.err.println(node.getTag().toString());
        //System.err.println(node.toString());
        //System.err.println("---");
        //System.err.println(dict.toString());
        return find(node.getTag().toString()).accept(node, dict);
    }

    private final void save(SyntaxTree node, TypeMapBase dict) throws Exception {
        find(node.getTag().toString()).saveType(node, dict);
    }

    public class DefaultVisitor {
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            return dict;
        }
        public void saveType(SyntaxTree node, TypeMapBase dict) throws Exception {
            for (SyntaxTree chunk : node) {
                save(chunk, dict);
            }
        }
    }

    public class FunctionMeta extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            SyntaxTree type = node.get(Symbol.unique("type"));
            AstProductType funtype = (AstProductType)AstType.nodeToType((SyntaxTree)type);
            
            SyntaxTree definition = node.get(Symbol.unique("definition"));
            
            SyntaxTree nodeName = node.get(Symbol.unique("name"));
            SyntaxTree nameNode = definition.get(Symbol.unique("name"));
            String name = nameNode.toText();
            dict.add(name, funtype);
            
            Set<String> domain = new HashSet<String>(dict.getKeys());

            TypeMapBase newDict = dict.clone();

            /* add non-JSValue parameters */
            SyntaxTree paramsNode = definition.get(Symbol.unique("params"));
            String[] paramNames = new String[paramsNode.size()];
            String[] jsvParamNames = new String[paramsNode.size()];
            AstType paramTypes = funtype.getDomain();
            int nJsvTypes = 0;
            if (paramTypes instanceof AstBaseType) {
                AstType paramType = paramTypes;
                String paramName = paramsNode.get(0).toText();
                paramNames[0] = paramName;
                if (paramType instanceof JSValueType)
                    jsvParamNames[nJsvTypes++] = paramName;
                else
                    newDict.add(paramName, paramType);
            } else if (paramTypes instanceof AstPairType) {
                List<AstType> paramTypeList = ((AstPairType) paramTypes).getTypes();
                for (int i = 0; i < paramTypeList.size(); i++) {
                    AstType paramType = paramTypeList.get(i);
                    String paramName = paramsNode.get(i).toText();
                    paramNames[i] = paramName;
                    if (paramType instanceof JSValueType)
                        jsvParamNames[nJsvTypes++] = paramName;
                    else
                        newDict.add(paramName, paramType);
                }
            }

            /* add JSValue parameters (apply operand spec) */
            if (nJsvTypes > 0) {
                String[] jsvParamNamesPacked = new String[nJsvTypes];
                System.arraycopy(jsvParamNames, 0, jsvParamNamesPacked, 0, nJsvTypes);
                VMDataTypeVecSet vtvs = opSpec.getAccept(name, paramNames);
                //VMDataTypeVecSetをTypeMapBaseに追加
                Set<VMDataType[]> tupleSet = vtvs.getTuples();
                String[] variableStrings = vtvs.getVarNames();
                int length = variableStrings.length;
                Set<Map<String, AstType>> newDictSet = new HashSet<>();
                for(VMDataType[] vec : tupleSet){
                    Map<String, AstType> tempMap = new HashMap<>();
                    for(int i=0; i<length; i++){
                        tempMap.put(variableStrings[i], AstType.get(vec[i]));
                    }
                    newDictSet.add(tempMap);
                }
                newDict.add(newDictSet);
            }
            
            SyntaxTree body = (SyntaxTree)definition.get(Symbol.unique("body"));
            dict = visit((SyntaxTree)body, newDict);

            save(nameNode, dict);
            save(nodeName, dict);
            save(paramsNode, dict);

            return dict.select((Set<String>)domain);
        }
        public void saveType(SyntaxTree node, TypeMapBase dict) throws Exception {
        }
    }

    public class Parameters extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            for (SyntaxTree chunk : node) {
                // TODO: update dict
                visit(chunk, dict);
                save(chunk, dict);
            }
            return dict;
        }
        public void saveType(SyntaxTree node, TypeMapBase dict) throws Exception {
        }
    }

    public class Block extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            Set<String> domain = new HashSet<String>(dict.getKeys());
            for (SyntaxTree seq : node) {
                dict = visit(seq, dict);
                save(seq, dict);
            }
            TypeMapBase result = dict.select((Set<String>)domain);
            return result;
        }
        public void saveType(SyntaxTree node, TypeMapBase dict) throws Exception {
        }
    }

    public class Match extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            MatchProcessor mp = new MatchProcessor(node);
            SyntaxTree labelNode= node.get(Symbol.unique("label"), null);
            String label = labelNode == null ? null : labelNode.toText();

            TypeMapBase outDict = dict.getBottomDict();

            TypeMapBase entryDict;
            TypeMapBase newEntryDict = dict;
            for(String s : mp.getFormalParams()){
                newEntryDict.addDispatch(s);
            }
            /*
            List<String> formalParams = new ArrayList<String>();
            for (String p: mp.getFormalParams())
                formalParams.add(p);
            int iterationCount = 0;
            do {
                iterationCount++;
                System.out.println("===ITERATION "+iterationCount+"===");
                entryDict = newEntryDict;
                matchStack.enter(label, mp.getFormalParams(), entryDict);
                System.out.println("entry = "+entryDict.select(formalParams));
                for (int i = 0; i < mp.size(); i++) {
                    TypeMapBase dictCaseIn = entryDict.enterCase(mp.getFormalParams(), mp.getVMDataTypeVecSet(i));
                    System.out.println("case in = "+dictCaseIn.select(formalParams));
                    if (dictCaseIn.hasBottom())
                        continue;
                    SyntaxTree body = mp.getBodyAst(i);
                    TypeMapBase dictCaseOut = visit(body, dictCaseIn);
                    System.out.println("case "+" = "+dictCaseOut.select(formalParams));
                    System.out.println(body.toText());
                    outDict = outDict.lub(dictCaseOut);
                }
                newEntryDict = matchStack.pop();
            } while (!entryDict.equals(newEntryDict));
            */

            do {
                entryDict = newEntryDict;
                matchStack.enter(label, mp.getFormalParams(), entryDict);
                for (int i = 0; i < mp.size(); i++) {
                    TypeMapBase dictCaseIn = entryDict.enterCase(mp.getFormalParams(), mp.getVMDataTypeVecSet(i));
                    if (dictCaseIn.hasBottom())
                        continue;
                    SyntaxTree body = mp.getBodyAst(i);
                    TypeMapBase dictCaseOut = visit(body, dictCaseIn);
                    outDict = outDict.combine(dictCaseOut);
                }
                newEntryDict = matchStack.pop();
            } while (!entryDict.equals(newEntryDict));
            
            node.setTypeMap(entryDict);

            SyntaxTree paramsNode= node.get(Symbol.unique("params"));
            save(paramsNode, outDict);
            outDict.removeAllDispatch();

            return outDict;
        }
    }

    public class Rematch extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            String label = node.get(Symbol.unique("label")).toText();
            TypeMapBase matchDict = matchStack.getDict(label);
            if (matchDict == null)
                throw new Error("match label not found: "+label);
            Set<String> domain = matchDict.getKeys();
            String[] matchParams = matchStack.getParams(label);
            
            String[] rematchArgs = new String[matchParams.length];
            for (int i = 1; i < node.size(); i++) {
                rematchArgs[i-1] = node.get(i).toText();
            }
            
            TypeMapBase matchDict2 = dict.rematch(matchParams, rematchArgs, domain);
            TypeMapBase result = matchDict2.combine(matchDict);
            matchStack.updateDict(label, result);
            return dict.getBottomDict();
        }
    }
    
    public class Return extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            Set<AstType> t = visit(node.get(0), dict).getExprType();
            save(node.get(0), dict);
            return dict;
        }
        public void saveType(SyntaxTree node, TypeMapBase dict) throws Exception {
        }
    }

    public class Assignment extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            SyntaxTree right = node.get(Symbol.unique("right"));
            Set<AstType> rhsType = visit(right, dict).getExprType();
            SyntaxTree left = node.get(Symbol.unique("left"));
            for(AstType t : rhsType){
                dict.add(left.toText(), t);
            }
            return dict;
        }
        public void saveType(SyntaxTree node, TypeMapBase dict) throws Exception {
        }
    }

    public class AssignmentPair extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            SyntaxTree right = node.get(Symbol.unique("right"));
            Set<AstType> rhsType = visit(right, dict).getExprType();
            for(AstType t : rhsType){
                if (t instanceof AstPairType) {
                    ArrayList<AstType> types = ((AstPairType)t).getTypes();
                    SyntaxTree left = node.get(Symbol.unique("left"));
                    if (types.size() != left.size()) {
                        throw new Error("AssignmentPair: return type error");
                    }
                    for (int i = 0; i < types.size(); i++) {
                        dict.add(left.get(i).toText(), types.get(i));
                    }
                } else {
                    throw new Error("AssignmentPair: type error");
                }
            }
            return dict;
        }
        @Override
        public void saveType(SyntaxTree node, TypeMapBase dict) throws Exception {
        }
    }

    public class Declaration extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            // SyntaxTree type = node.get(Symbol.unique("type"));
            // AstBaseType varType = new AstBaseType(type.toText());

            SyntaxTree var = node.get(Symbol.unique("var"));
            SyntaxTree expr = node.get(Symbol.unique("expr"));
            Set<AstType> rhsType = visit(expr, dict).getExprType();
            
            for(AstType t : rhsType){
                dict.add(var.toText(), t);
            }
            save(expr, dict);
            save(var, dict);
            return dict;
        }
        @Override
        public void saveType(SyntaxTree node, TypeMapBase dict) throws Exception {
        }
    }
    public class If extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            TypeMapBase copyDict = dict.clone();
            SyntaxTree thenNode = node.get(Symbol.unique("then"));
            
            TypeMapBase thenDict = visit(thenNode, dict);
            SyntaxTree elseNode = node.get(Symbol.unique("else"));

            TypeMapBase resultDict;
            if (elseNode == null) {
                resultDict = thenDict;
            } else {
                TypeMapBase elseDict = visit(elseNode, copyDict);
                resultDict = thenDict.combine(elseDict);
            }
            SyntaxTree condNode = node.get(Symbol.unique("cond"));
            save(condNode, resultDict);

            return resultDict;
        }
        @Override
        public void saveType(SyntaxTree node, TypeMapBase dict) throws Exception {
        }
    }

    public class Do extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            SyntaxTree initNode = node.get(Symbol.unique("init"));
            SyntaxTree varNode = initNode.get(Symbol.unique("var"));
            SyntaxTree exprNode = initNode.get(Symbol.unique("expr"));
            Set<AstType> rhsType = visit(exprNode, dict).getExprType();
            for(AstType t : rhsType){
                dict.add(varNode.toText(), t);
            }
            
            TypeMapBase savedDict;
            do {
                savedDict = dict.clone();
                SyntaxTree blockNode = initNode.get(Symbol.unique("block"));
                dict = visit(blockNode, dict);
            } while (!dict.equals(savedDict));

            return dict;
        }
    }

    public class CTypeDef extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            SyntaxTree varNode = node.get(Symbol.unique("var"));
            SyntaxTree typeNode = node.get(Symbol.unique("type"));
            AstType type = AstType.get(typeNode.toText());
            dict.add(varNode.toText(), type);
            
            return dict;
        }
    }

    public class CFunction extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            SyntaxTree typeNode = node.get(Symbol.unique("type"));
            AstType domain = AstType.nodeToType(typeNode.get(0));
            AstType range = AstType.nodeToType(typeNode.get(1));
            SyntaxTree nameNode = node.get(Symbol.unique("name"));
            AstType type = new AstProductType(domain, range);
            dict.add(nameNode.toText(), type);
            
            return dict;
        }
    }

    public class CConstantDef extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            SyntaxTree typeNode = node.get(Symbol.unique("type"));
            SyntaxTree varNode = node.get(Symbol.unique("var"));
            AstType type = AstType.get(typeNode.toText());
            dict.add(varNode.toText(), type);
            
            return dict;
        }
    }

    public class Trinary extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            //TypeMapBase copyDict = dict.clone();
            SyntaxTree thenNode = node.get(Symbol.unique("then"));
            Set<AstType> thenType = visit(thenNode, dict).getExprType();
            SyntaxTree elseNode = node.get(Symbol.unique("else"));
            //AstBaseType elseType = visit(elseNode, copyDict).getExprType();
            Set<AstType> elseType = visit(elseNode, dict).getExprType();

            TypeMapBase resultMap = TYPE_MAP.clone();
            resultMap.setExprType(resultMap.combineExprTypes(thenType, elseType));
            return resultMap;
        }
    }
    public class Or extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            TypeMapBase tempMap = TYPE_MAP.clone();
            Set<AstType> tempSet = new HashSet<>();
			tempSet.add(AstType.get("cint"));
			tempMap.setExprType(tempSet);
            return tempMap;
        }
    }
    public class And extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            TypeMapBase tempMap = TYPE_MAP.clone();
			Set<AstType> tempSet = new HashSet<>();
			tempSet.add(AstType.get("cint"));
			tempMap.setExprType(tempSet);
			return tempMap;
        }
    }
    public class BitwiseOr extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            AstType t = bitwiseOperator(node, dict);
            TypeMapBase tempMap = TYPE_MAP.clone();
			Set<AstType> tempSet = new HashSet<>();
			tempSet.add(t);
			tempMap.setExprType(tempSet);
			return tempMap;
        }
    }

    public class BitwiseXOr extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            AstType t = bitwiseOperator(node, dict);
            TypeMapBase tempMap = TYPE_MAP.clone();
			Set<AstType> tempSet = new HashSet<>();
			tempSet.add(t);
			tempMap.setExprType(tempSet);
			return tempMap;
        }
    }

    public class BitwiseAnd extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            AstType t = bitwiseOperator(node, dict);
            TypeMapBase tempMap = TYPE_MAP.clone();
			Set<AstType> tempSet = new HashSet<>();
			tempSet.add(t);
			tempMap.setExprType(tempSet);
			return tempMap;
        }
    }

    public class Equals extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            TypeMapBase tempMap = TYPE_MAP.clone();
			Set<AstType> tempSet = new HashSet<>();
			tempSet.add(AstType.get("cint"));
			tempMap.setExprType(tempSet);
			return tempMap;
        }
    }

    public class NotEquals extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            TypeMapBase tempMap = TYPE_MAP.clone();
			Set<AstType> tempSet = new HashSet<>();
			tempSet.add(AstType.get("cint"));
			tempMap.setExprType(tempSet);
			return tempMap;
        }
    }

    public class LessThanEquals extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            TypeMapBase tempMap = TYPE_MAP.clone();
			Set<AstType> tempSet = new HashSet<>();
			tempSet.add(AstType.get("cint"));
			tempMap.setExprType(tempSet);
			return tempMap;
        }
    }
    public class GreaterThanEquals extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            TypeMapBase tempMap = TYPE_MAP.clone();
			Set<AstType> tempSet = new HashSet<>();
			tempSet.add(AstType.get("cint"));
			tempMap.setExprType(tempSet);
			return tempMap;
        }
    }
    public class LessThan extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            TypeMapBase tempMap = TYPE_MAP.clone();
			Set<AstType> tempSet = new HashSet<>();
			tempSet.add(AstType.get("cint"));
			tempMap.setExprType(tempSet);
			return tempMap;
        }
    }
    public class GreaterThan extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            TypeMapBase tempMap = TYPE_MAP.clone();
			Set<AstType> tempSet = new HashSet<>();
			tempSet.add(AstType.get("cint"));
			tempMap.setExprType(tempSet);
			return tempMap;
        }
    }
    public class LeftShift extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            SyntaxTree leftNode = node.get(Symbol.unique("left"));
            Set<AstType> leftType = visit(leftNode, dict).getExprType();
            TypeMapBase tempMap = TYPE_MAP.clone();
			tempMap.setExprType(leftType);
			return tempMap;
        }
    }
    public class RightShift extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            SyntaxTree leftNode = node.get(Symbol.unique("left"));
            Set<AstType> leftType = visit(leftNode, dict).getExprType();
            TypeMapBase tempMap = TYPE_MAP.clone();
			tempMap.setExprType(leftType);
			return tempMap;
        }
    }
    
    AstType tCint = AstType.get("cint");
    AstType tCdouble = AstType.get("cdouble");
    private boolean isNumberSet(Set<AstType> set){
        for(AstType t : set){
            if(!t.equals(tCint)&&!t.equals(tCdouble)) return false;
        }
        return true;
    }
    
    private AstType numberOperator(SyntaxTree node, TypeMapBase dict) throws Exception {
        SyntaxTree leftNode = node.get(Symbol.unique("left"));
        Set<AstType> lType = visit(leftNode, dict).getExprType();
        SyntaxTree rightNode = node.get(Symbol.unique("right"));
        Set<AstType> rType = visit(rightNode, dict).getExprType();

        if(isNumberSet(lType)&&isNumberSet(rType)){
            if(lType.contains(tCdouble)||rType.contains(tCdouble)){
                return tCdouble;
            }else{
                return tCint;
            }
        }
        /*
        if (lType == tCint || rType == tCint) {
            return tCint;
        } else if ((lType == tCint || lType == tCdouble) || (rType == tCint || rType == tCdouble)){
            return tCdouble;
        }
        */
        throw new Error("type error");
    }

    private AstType bitwiseOperator(SyntaxTree node, TypeMapBase dict) throws Exception {
        SyntaxTree leftNode = node.get(Symbol.unique("left"));
        Set<AstType> lType = visit(leftNode, dict).getExprType();
        SyntaxTree rightNode = node.get(Symbol.unique("right"));
        Set<AstType> rType = visit(rightNode, dict).getExprType();

        if (lType.size()==1&&lType.contains(tCint)&&rType.size()==1&&rType.contains(tCint))
            return tCint;
        throw new Error("type error");
    }

    public class Add extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            AstType t = numberOperator(node, dict);
            TypeMapBase tempMap = TYPE_MAP.clone();
			Set<AstType> tempSet = new HashSet<>();
			tempSet.add(t);
			tempMap.setExprType(tempSet);
			return tempMap;
        }
    }
    public class Sub extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            AstType t = numberOperator(node, dict);
            TypeMapBase tempMap = TYPE_MAP.clone();
			Set<AstType> tempSet = new HashSet<>();
			tempSet.add(t);
			tempMap.setExprType(tempSet);
			return tempMap;
        }
    }
    public class Mul extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            AstType t = numberOperator(node, dict);
            TypeMapBase tempMap = TYPE_MAP.clone();
			Set<AstType> tempSet = new HashSet<>();
			tempSet.add(t);
			tempMap.setExprType(tempSet);
			return tempMap;
        }
    }
    public class Div extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            AstType t = numberOperator(node, dict);
            TypeMapBase tempMap = TYPE_MAP.clone();
			Set<AstType> tempSet = new HashSet<>();
			tempSet.add(t);
			tempMap.setExprType(tempSet);
			return tempMap;
        }
    }

    public class Plus extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            SyntaxTree exprNode = node.get(Symbol.unique("expr"));
            Set<AstType> exprType = visit(exprNode, dict).getExprType();
            TypeMapBase tempMap = TYPE_MAP.clone();
			tempMap.setExprType(exprType);
			return tempMap;
        }
    }
    public class Minus extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            SyntaxTree exprNode = node.get(Symbol.unique("expr"));
            Set<AstType> exprType = visit(exprNode, dict).getExprType();
            TypeMapBase tempMap = TYPE_MAP.clone();
			tempMap.setExprType(exprType);
			return tempMap;
        }
    }
    public class Compl extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            SyntaxTree exprNode = node.get(Symbol.unique("expr"));
            Set<AstType> exprType = visit(exprNode, dict).getExprType();
            TypeMapBase tempMap = TYPE_MAP.clone();
			tempMap.setExprType(exprType);
			return tempMap;
        }
    }
    public class Not extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            TypeMapBase tempMap = TYPE_MAP.clone();
			Set<AstType> tempSet = new HashSet<>();
			tempSet.add(AstType.get("Bool"));
			tempMap.setExprType(tempSet);
			return tempMap;
        }
    }
    public class FunctionCall extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            SyntaxTree recv = node.get(Symbol.unique("recv"));
            String funName = recv.toText();
            
            if (!dict.containsKey(funName)) {
                System.err.println(dict.toString());
                throw new Error("FunctionCall: no such name: "+funName+" :"+node.getSource().getResourceName()+" :"+node.getLineNum());
            }
            Set<AstType> funTypeSet = dict.get(funName);
            if(funTypeSet.size() != 1){
                throw new Error("FunctionCall: function \""+funName+"\" has multiple types");
            }
            AstProductType funType = (AstProductType)funTypeSet.toArray()[0];
            AstBaseType rangeType = (AstBaseType)funType.getRange();
           
            // TODO: type check
            for (SyntaxTree arg : node.get(1)) {
                Set<AstType> argType = visit(arg, dict).getExprType();
            }
            
            TypeMapBase tempMap = TYPE_MAP.clone();
            Set<AstType> tempSet = new HashSet<>();
            tempSet.add(rangeType);
			tempMap.setExprType(tempSet);
			return tempMap;
        }
    }

    public class ArrayIndex extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            // TODO
            return dict;
        }
    }
    public class Field extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            // TODO
            return dict;
        }
    }
    public class _Integer extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            TypeMapBase tempMap = TYPE_MAP.clone();
			Set<AstType> tempSet = new HashSet<>();
			tempSet.add(AstType.get("cint"));
			tempMap.setExprType(tempSet);
			return tempMap;
        }
    }

    public class _Float extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            TypeMapBase tempMap = TYPE_MAP.clone();
			Set<AstType> tempSet = new HashSet<>();
			tempSet.add(AstType.get("cdouble"));
			tempMap.setExprType(tempSet);
			return tempMap;
        }
    }

    public class _String extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            TypeMapBase tempMap = TYPE_MAP.clone();
			Set<AstType> tempSet = new HashSet<>();
			tempSet.add(AstType.get("String"));
			tempMap.setExprType(tempSet);
			return tempMap;
        }
    }

    public class _True extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            TypeMapBase tempMap = TYPE_MAP.clone();
			Set<AstType> tempSet = new HashSet<>();
			tempSet.add(AstType.get("Bool"));
			tempMap.setExprType(tempSet);
			return tempMap;
        }
    }
    public class _False extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            TypeMapBase tempMap = TYPE_MAP.clone();
			Set<AstType> tempSet = new HashSet<>();
			tempSet.add(AstType.get("Bool"));
			tempMap.setExprType(tempSet);
			return tempMap;
        }
    }

    public class Name extends DefaultVisitor {
        @Override
        public TypeMapBase accept(SyntaxTree node, TypeMapBase dict) throws Exception {
            if (!dict.containsKey(node.toText())) {
                throw new Error("Name: no such name: "+node.getSource().getResourceName()+": "+node.getLineNum());
            }
            Set<AstType> type = dict.get(node.toText());

            TypeMapBase tempMap = TYPE_MAP.clone();
			tempMap.setExprType(type);
			return tempMap;
        }
        public void saveType(SyntaxTree node, TypeMapBase dict) throws Exception {
            if (dict.containsKey(node.toText())) {
                Set<AstType> type = dict.get(node.toText());
                node.setType(type);
            }
        }
    }
}
