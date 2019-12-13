package vmdlc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;

import type.AstType;

public class TypeDependencyProcessor {
    private static Map<String, FunctionTypeDependency> dependencyMap = new HashMap<>();

    public static void addDependency(String fromFunctionName, List<AstType> fromTypes, String toFunctionName, List<AstType> toTypes){
        FunctionTypeDependency fromFunction = dependencyMap.get(fromFunctionName);
        if(fromFunction == null){
            fromFunction = new FunctionTypeDependency(fromFunctionName);
            dependencyMap.put(fromFunctionName, fromFunction);
        }
        fromFunction.addDependency(fromTypes, toFunctionName, toTypes);
    }

    public static void write(FileWriter writer) throws IOException{
        StringBuilder builder = new StringBuilder();
        for(FunctionTypeDependency ftd : dependencyMap.values()){
            builder.append(ftd.getDependencyCode());
        }
        writer.write(builder.toString());
    }

    public static void write(String fileName) throws IOException{
        try{
            write(new FileWriter(new File(fileName), true));
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private static List<AstType> stringToAstTypes(String target){
        String[] strings = target.split(",");
        List<AstType> types = new ArrayList<>(strings.length);
        for(String typeName : strings){
            types.add(AstType.get(typeName.trim()));
        }
        return types;
    }

    public static void load(Scanner sc){
        if(!dependencyMap.isEmpty()){
            System.err.println("[Warning] TypeDependencyProcessor overwrites existing data.");
            dependencyMap = new HashMap<>();
        }
        while(sc.hasNextLine()){
            String line = sc.nextLine();
            if(line == "") continue;
            String[] record = line.split("->");
            if(record.length != 2){
                throw new Error("Type dependency file is broken: "+line);
            }
            String[] from = record[0].trim().split(" ");
            String[] to = record[1].trim().split(" ");
            if(from.length != 2 || to.length != 2){
                throw new Error("Type dependency file is broken: "+line);
            }
            addDependency(from[0].trim(), stringToAstTypes(from[1]), to[0].trim(), stringToAstTypes(to[1]));
        }
    }

    private static class FunctionTypeDependency{
        String functionName;
        Set<List<AstType>> needTypess;
        Map<List<AstType>, Set<Entry<String, List<AstType>>>> needFunctionTypeMap;

        private FunctionTypeDependency(String _functionName){
            functionName = _functionName;
            needTypess = new HashSet<>();
            needFunctionTypeMap = new HashMap<>();
        }

        public void addNeedTypes(List<AstType> types, Map<String, FunctionTypeDependency> dependencyMap){
            needTypess.add(types);
            //call addNeedTypes that this function need
            Set<Entry<String, List<AstType>>> needFunctionTypes = needFunctionTypeMap.get(types);
            for(Entry<String, List<AstType>> entry : needFunctionTypes){
                FunctionTypeDependency ftd = dependencyMap.get(entry.getKey());
                ftd.addNeedTypes(entry.getValue(), dependencyMap);
            }
        }

        public void addDependency(List<AstType> trigger, String needFunctionName, List<AstType> needTypes){
            Set<Entry<String, List<AstType>>> needSet = needFunctionTypeMap.get(trigger);
            if(needSet == null){
                needSet = new HashSet<>();
                needFunctionTypeMap.put(trigger, needSet);
            }
            needSet.add(new SimpleEntry<String, List<AstType>>(needFunctionName, needTypes));
        }

        private void commaSepalateHelper(List<AstType> target, StringBuilder builder){
            int size = target.size();
            for(int i=0; i<size; i++){
                builder.append(target.get(i).toString());
                if(i<size-1){
                    builder.append(',');
                }
            }
        }

        public String getDependencyCode(){
            StringBuilder builder = new StringBuilder();
            for(Entry<List<AstType>, Set<Entry<String, List<AstType>>>> entry : needFunctionTypeMap.entrySet()){
                for(Entry<String, List<AstType>> needsEntry : entry.getValue()){
                    builder.append(functionName);
                    builder.append(" ");
                    commaSepalateHelper(entry.getKey(), builder);
                    builder.append(" -> ");
                    builder.append(needsEntry.getKey());
                    builder.append(" ");
                    commaSepalateHelper(needsEntry.getValue(), builder);
                    builder.append('\n');
                }
            }
            return builder.toString();
        }
    }
}