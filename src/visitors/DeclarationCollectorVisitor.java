package visitors;

import syntaxtree.*;
import models.ClassHierarchy;
import models.MethodSignature;

import java.util.ArrayList;
import java.util.List;

public class DeclarationCollectorVisitor extends StringRepresentationVisitor {
    private ClassHierarchy.Builder classesBuilder = new ClassHierarchy.Builder();
    private String currentClass;

    public ClassHierarchy getClassHierarchy() {
        return classesBuilder.build();
    }

    @Override
    public String visit(MainClass n) throws Exception {
        String mainClassName = n.f1.accept(this);
        currentClass = mainClassName;
        classesBuilder.addClass(mainClassName);
        return null;
    }

    @Override
    public String visit(ClassDeclaration n) throws Exception {
        this.currentClass = n.f1.accept(this);
        classesBuilder.addClass(currentClass);
        n.f4.accept(this);
        return null;
    }

    @Override
    public String visit(ClassExtendsDeclaration n) throws Exception {
        this.currentClass = n.f1.accept(this);
        String parentClass = n.f3.accept(this);
        classesBuilder.addClass(currentClass, parentClass);
        n.f6.accept(this);
        return null;
    }

    @Override
    public String visit(MethodDeclaration n) throws Exception {
        String methodName = n.f2.accept(this);
        List<String> argTypes = new ArrayList<>();
        if (n.f4.present()) {
            for (FormalParamListData.Parameter p : n.f4.accept(new FormalParameterVisitor()).getParameters()) {
                argTypes.add(p.type());
            }
        }
        MethodSignature signature = new MethodSignature(n.f1.accept(this), argTypes);
        classesBuilder.addMethod(currentClass, methodName, signature);
        return null;
    }
}
