package vmdlc;

import nez.ast.Tree;
import nez.ast.TreeVisitorMap;
import nez.util.ConsoleUtils;
import nez.ast.Symbol;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.lang.Exception;

import vmdlc.TypeCheckPreProcessVisitor.DefaultVisitor;
import vmdlc.ReplaceNameVisitor;

public class TypeCheckPreProcessVisitor extends TreeVisitorMap<DefaultVisitor> {
    public TypeCheckPreProcessVisitor() {
        init(TypeCheckPreProcessVisitor.class, new DefaultVisitor());
    }

    public void start(Tree<?> node) {
        try {
            for (Tree<?> chunk : node) {
                visit(chunk, null);
            }
        } catch (Exception e) {
        }
    }

    private final void visit(Tree<?> node, Tree<?> matchNode) throws Exception {
        find(node.getTag().toString()).accept(node, matchNode);
    }

    public class DefaultVisitor {
        public void accept(Tree<?> node, Tree<?> matchNode) throws Exception {
            for (Tree<?> seq : node) {
                visit(seq, matchNode);
            }
        }
    }

    public class Match extends DefaultVisitor {
        @Override
        public void accept(Tree<?> node, Tree<?> matchNode) throws Exception {
            /*
            Tree<?> nameNode = node.get(Symbol.unique("patternname"));
            Tree<?> varNode = node.get(Symbol.unique("var"));
            Tree<?> patternNode = node.get(Symbol.unique("pattern"));
            dict.intern(nameNode.toText(), varNode.toText(), patternNode);
            */
           ((SyntaxTree)node).setRematchVarSet(new HashSet<String>());
            visit(node.get(Symbol.unique("cases")), node);
        }
    }

    public class Rematch extends DefaultVisitor {
        @Override
        public void accept(Tree<?> node, Tree<?> matchNode) throws Exception {
            Set<String> rematchVarSet = ((SyntaxTree)matchNode).getRematchVarSet();
            for (int i = 1; i < node.size(); i++) {
                rematchVarSet.add(node.get(i).toText());
            }
        }
    }
}