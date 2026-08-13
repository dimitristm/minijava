package visitors;

import syntaxtree.*;
import visitor.GJNoArguDepthFirst;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

class FormalParamListData {
    record Parameter(String type, String id) {
    }

    private List<Parameter> parameters = new ArrayList<>();

    public List<Parameter> getParameters() {
        return Collections.unmodifiableList(parameters);
    }

    public void add(String type, String ID) {
        this.parameters.add(new Parameter(type, ID));
    }

    public void addAll(FormalParamListData otherList) {
        this.parameters.addAll(otherList.getParameters());
    }

    public int size() {
        return parameters.size();
    }
}

public class FormalParameterVisitor extends GJNoArguDepthFirst<FormalParamListData> {
    private StringRepresentationVisitor stringRep = new StringRepresentationVisitor();

    /**
     * f0 -> FormalParameter()
     * f1 -> FormalParameterTail()
     */
    @Override
    public FormalParamListData visit(FormalParameterList n) throws Exception {
        FormalParamListData list = n.f0.accept(this);
        list.addAll(n.f1.accept(this));
        return list;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     */
    @Override
    public FormalParamListData visit(FormalParameter n) throws Exception {
        FormalParamListData data = new FormalParamListData();
        data.add(n.f0.accept(stringRep), n.f1.accept(stringRep));
        return data;
    }

    /**
     * f0 -> ( FormalParameterTerm() )*
     */
    @Override
    public FormalParamListData visit(FormalParameterTail n) throws Exception {
        FormalParamListData types = new FormalParamListData();
        for (int i = 0; i < n.f0.size(); i++) {
            types.addAll(n.f0.elementAt(i).accept(this));
        }
        return types;
    }

    /**
     * f0 -> ","
     * f1 -> FormalParameter()
     */
    @Override
    public FormalParamListData visit(FormalParameterTerm n) throws Exception {
        return n.f1.accept(this);
    }

}
