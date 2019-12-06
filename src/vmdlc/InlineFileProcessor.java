package vmdlc;

public class InlineFileProcessor{
    public enum Keyword{
        FUNC,
        COND,
        EXPR;
    }

    public static String code(Keyword keyword, String text){
        return "#"+keyword+" "+text;
    }
}