package visitors;

import syntaxtree.*;
import visitor.GJNoArguDepthFirst;

public class StringRepresentationVisitor extends GJNoArguDepthFirst<String> {
    @Override
    public String visit(BooleanArrayType n) {
        return "boolean[]";
    }

    @Override
    public String visit(IntegerArrayType n) {
        return "int[]";
    }

    @Override
    public String visit(BooleanType n) {
        return "boolean";
    }

    @Override
    public String visit(IntegerType n) {
        return "int";
    }

    @Override
    public String visit(Identifier n) {
        return n.f0.toString();
    }
}
