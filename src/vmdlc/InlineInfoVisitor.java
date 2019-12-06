package vmdlc;

import java.io.BufferedWriter;

import nez.ast.Symbol;
import nez.ast.Tree;
import nez.ast.TreeVisitorMap;
import vmdlc.InlineInfoVisitor.DefaultVisitor;

public class InlineInfoVisitor extends TreeVisitorMap<DefaultVisitor> {
    private BufferedWriter writer;
    private static final InlineFileProcessor.Keyword FUNC = InlineFileProcessor.Keyword.FUNC;
    private static final InlineFileProcessor.Keyword COND = InlineFileProcessor.Keyword.COND;
    private static final InlineFileProcessor.Keyword EXPR = InlineFileProcessor.Keyword.EXPR;

    public InlineInfoVisitor(BufferedWriter writer) {
        init(InlineInfoVisitor.class, new DefaultVisitor());
        this.writer = writer;
    }

    public void start(Tree<?> node) {
        try {
            for (Tree<?> chunk : node) {
                visit(chunk);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new Error("visitor thrown an exception");
        }
    }

    private final void visit(Tree<?> node) throws Exception {
        find(node.getTag().toString()).accept(node);
    }

    //Never used
    /*
    private void print(Object o) {
        ConsoleUtils.println(o);
    }
    */

    public class DefaultVisitor {
        public void accept(Tree<?> node) throws Exception {
            return;
        }
    }

    public class FunctionMeta extends DefaultVisitor {
        @Override
        public void accept(Tree<?> node) throws Exception {
            Tree<?> nameNode = node.get(Symbol.unique("name"));
            Tree<?> defNode = node.get(Symbol.unique("definition"));
            writer.write(InlineFileProcessor.code(FUNC, nameNode.toText()));
            writer.newLine();
            visit(defNode);
        }
    }

    public class Block extends DefaultVisitor {
        @Override
        public void accept(Tree<?> node) throws Exception {
            for (Tree<?> seq : node) {
                visit(seq);
            }
        }
    }

    public class Return extends DefaultVisitor {
        @Override
        public void accept(Tree<?> node) throws Exception {
            Tree<?> exprNode = node.get(0);
            writer.write(InlineFileProcessor.code(EXPR, exprNode.toText()));
            writer.newLine();
        }
    }

    public class Match extends DefaultVisitor {
        @Override
        public void accept(Tree<?> node) throws Exception {
            
        }
    }
}