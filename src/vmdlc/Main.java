/*
 * eJS Project
 * Kochi University of Technology
 * The University of Electro-communications
 *
 * The eJS Project is the successor of the SSJS Project at The University of
 * Electro-communications.
 */
package vmdlc;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import dispatch.DispatchProcessor;
import nez.ParserGenerator;
import nez.lang.Grammar;
import nez.parser.Parser;
import nez.parser.ParserStrategy;
import nez.parser.io.FileSource;
import nez.parser.io.StringSource;
import nez.ast.Source;
import nez.ast.SourceError;


import type.*;
import vmdlc.AlphaConvVisitor;
import vmdlc.AstToCVisitor;
import vmdlc.DesugarVisitor;
import vmdlc.SyntaxTree;
import vmdlc.TypeCheckVisitor;


public class Main {
    static final String VMDL_GRAMMAR = "ejsdsl.nez";
    static final String INLINE_FILE = "./inlines.inline";
    static String sourceFile;
    static String dataTypeDefFile;
    static String vmdlGrammarFile;
    static String operandSpecFile;
    static String insnDefFile;
    static String inlineExpansionFile;
    static String functionDependencyFile = "./vmdl_workspace/dependency.ftd";
    static String argumentSpecFile;
    static int typeMapIndex = 1;
    static OutputMode outputMode = OutputMode.Instruction;

    static Option option = new Option();

    public static enum OutputMode{
        Instruction(false),
        Function(true),
        MakeInline(true);

        private boolean functionMode;
        private OutputMode(boolean functionMode){
            this.functionMode = functionMode;
        }
        public boolean isFunctionMode(){
            return functionMode;
        }
    };

    static void parseOption(String[] args) {
        for (int i = 0; i < args.length; ) {
            String opt = args[i++];
            if (opt.equals("-d")) {
                dataTypeDefFile = args[i++];
            } else if (opt.equals("-g")) {
                vmdlGrammarFile = args[i++];
            } else if (opt.equals("-o")) {
                operandSpecFile = args[i++];
            } else if (opt.equals("-no-match-opt")) {
                option.mDisableMatchOptimisation = true;
            } else if(opt.matches("-T.")){
                Integer num = Integer.parseInt(opt.substring(2));
                if((num <= 0) || (num > TypeCheckVisitor.CheckTypePlicy.values().length)){
                    throw new Error("Illigal option");
                }
                typeMapIndex = num;
            } else if (opt.equals("--preprocess")) {
                outputMode = OutputMode.MakeInline;
            } else if (opt.equals("--useinline")) {
                inlineExpansionFile = args[i++];
            } else if (opt.equals("-A")) {
                argumentSpecFile = args[i++];
            } else if (opt.equals("-i")) {
                insnDefFile = args[i++];
            } else if (opt.startsWith("-X")) {
                i = option.addOption(opt, args, i);
                if (i == -1) {
                    break;
                }
            } else {
                sourceFile = opt;
                break;
            }
        }

        if (dataTypeDefFile == null || sourceFile == null) {
            System.out.println("vmdlc [option] source");
            System.out.println("   -d file   [mandatory] datatype specification file");
            System.out.println("   -o file   operand specification file");
            System.out.println("   -g file   Nez grammar file (default: ejsdl.nez in jar file)");
            System.out.println("   -no-match-opt  disable optimisation for match statement");
            System.out.println("   -i file   instruction defs");
            System.out.println("   -TX       type analysis processing");
            System.out.println("              -T1: use Lub");
            System.out.println("              -T2: partly detail");
            System.out.println("              -T3: perfectly detail");
            System.out.println("   --inline  output inline expansion information");
            System.out.println("   -Xcmp:verify_diagram [true|false]");
            System.out.println("   -Xcmp:opt_pass [MR:S]");
            System.out.println("   -Xcmp:rand_seed n    set random seed of dispatch processor");
            System.out.println("   -Xcmp:tree_layer p0:p1:h0:h1");
            System.out.println("   -Xgen:use_goto [true|false]");
            System.out.println("   -Xgen:pad_cases [true|false]");
            System.out.println("   -Xgen:use_default [true|false]");
            System.out.println("   -Xgen:magic_comment [true|false]");
            System.out.println("   -Xgen:debug_comment [true|false]");
            System.out.println("   -Xgen:label_prefix xxx   set xxx as goto label");
            System.out.println("   -Xgen:type_label [true|false]");
            System.exit(1);
        }
    }

    static Grammar getGrammar() throws IOException {
        ParserGenerator pg = new ParserGenerator();
        Grammar grammar;
        if (vmdlGrammarFile != null)
            grammar = pg.loadGrammar(vmdlGrammarFile);
        else {
            StringSource grammarText = readDefaultGrammar();
            grammar = pg.newGrammar(grammarText, "nez");
        }
        return grammar;
    }

    static SyntaxTree parse(String sourceFile) throws IOException {
        Grammar grammar = getGrammar();

        //grammar.dump();
        Parser parser = grammar.newParser(ParserStrategy.newSafeStrategy());

        //Source source = new StringSource("externC constant cint aaa = \"-1\";");
        Source source = new FileSource(sourceFile);
        SyntaxTree ast = (SyntaxTree) parser.parse(source, new SyntaxTree());

        if (parser.hasErrors()) {
            for (SourceError e: parser.getErrors()) {
                System.out.println(e);
            }
            throw new Error("parse error");
        }

        return ast;
    }

    public final static void main(String[] args) throws IOException {
        parseOption(args);

        if (dataTypeDefFile == null)
            throw new Error("no datatype definition file is specified (-d option)");
        TypeDefinition.load(dataTypeDefFile);

        OperandSpecifications opSpec = new OperandSpecifications();
        if (operandSpecFile != null)
            opSpec.load(operandSpecFile);

        InstructionDefinitions insnDef = new InstructionDefinitions();
        if (insnDefFile != null)
            insnDef.load(insnDefFile);

        OperandSpecifications funcSpec = new OperandSpecifications();
        if (argumentSpecFile != null)
            funcSpec.load(argumentSpecFile);
        
        if (sourceFile == null)
            throw new Error("no source file is specified");

        Integer seed = option.getOption(Option.AvailableOptions.CMP_RAND_SEED, 0);
        DispatchProcessor.srand(seed);

        SyntaxTree ast = parse(sourceFile);

        ErrorPrinter.setSource(sourceFile);
        if(inlineExpansionFile != null){
            InlineFileProcessor.read(inlineExpansionFile, getGrammar());
        }
        String functionName = new ExternProcessVisitor().start(ast);
        if(outputMode != OutputMode.MakeInline){
            if(FunctionTable.hasAnnotations(functionName, FunctionAnnotation.vmInstruction)){
                outputMode = OutputMode.Instruction;
            }else{
                outputMode = OutputMode.Function;
            }
        }
        new DesugarVisitor().start(ast);
        new DispatchVarCheckVisitor().start(ast);
        if(!outputMode.isFunctionMode())new AlphaConvVisitor().start(ast, true, insnDef);
        new TypeCheckVisitor().start(ast, opSpec,
            TypeCheckVisitor.CheckTypePlicy.values()[typeMapIndex-1], (inlineExpansionFile != null), (functionDependencyFile != null), funcSpec);

        String program;
        if(outputMode == OutputMode.MakeInline){
            program = new InlineInfoVisitor().start(ast);
        }else{
            program = new AstToCVisitor().start(ast, opSpec, outputMode);
        }
        if(outputMode == OutputMode.MakeInline){
            TypeDependencyProcessor.write(functionDependencyFile);
        }
        if(funcSpec != null){
            funcSpec.write(argumentSpecFile);
        }

        System.out.println(program);
    }

    public static BufferedReader openFileInJar(String path){
        InputStream is = Main.class.getClassLoader().getResourceAsStream(path);
        return new BufferedReader(new InputStreamReader(is));
    }

    static StringSource readDefaultGrammar() throws IOException {
        InputStream is = ClassLoader.getSystemResourceAsStream(VMDL_GRAMMAR);
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader r = new BufferedReader(isr);
        StringBuffer sb = new StringBuffer();
        String line;
        while ((line = r.readLine()) != null) {
            sb.append(line);
            sb.append('\n');
        }
        String peg = sb.toString();
        return new StringSource(peg);
    }
}
