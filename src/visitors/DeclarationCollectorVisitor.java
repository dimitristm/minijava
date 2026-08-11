package visitors;

import syntaxtree.*;
import models.ClassAndIdentifier;
import models.MethodInfo;

import exceptions.SemanticCheckException;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public class DeclarationCollectorVisitor extends StringRepresentationVisitor{
    private Map<ClassAndIdentifier, MethodInfo> methods = new HashMap<>();
    private Map<String, String> classesAndTheirParents = new HashMap<>();
    private String currentClass;
    public Map<ClassAndIdentifier, MethodInfo> getMethods(){ return this.methods; }
    public Map<String, String> getClassesAndTheirParents() { return this.classesAndTheirParents; }

    @Override
    public String visit(MainClass n) throws Exception {
        String mainClassName = n.f1.accept(this);
        currentClass = mainClassName;
        classesAndTheirParents.put(mainClassName, null);
        return null;
    }

   @Override
   public String visit(ClassDeclaration n) throws Exception {
        this.currentClass = n.f1.accept(this);
        if (classesAndTheirParents.containsKey(currentClass)) {throw new SemanticCheckException("This class has already been declared elsewhere: " + currentClass); }
        classesAndTheirParents.put(currentClass, null);
        n.f4.accept(this);
        return null;
   }

   @Override
   public String visit(ClassExtendsDeclaration n) throws Exception {
        this.currentClass = n.f1.accept(this);
        String parentClass = n.f3.accept(this);
        if (classesAndTheirParents.containsKey(currentClass)) {throw new SemanticCheckException("This class has already been declared elsewhere: " + currentClass); }
        if (!classesAndTheirParents.containsKey(parentClass)) { throw new SemanticCheckException("Class " + currentClass + " tried to inherit from a class called " + parentClass + ", but that class had not been declared yet or has no declaration at all");}
        classesAndTheirParents.put(currentClass, parentClass);
        n.f6.accept(this);
        return null;
   }

   @Override
    public String visit(MethodDeclaration n) throws Exception {
        String methodName = n.f2.accept(this);
        List<String> argTypes = new LinkedList<>();
        if (n.f4.present()) {
            for (FormalParamListData.Parameter p : n.f4.accept(new FormalParameterVisitor()).getParameters()) {
                argTypes.add(p.type());
            }
        }
        MethodInfo methodInfo = new MethodInfo(n.f1.accept(this), argTypes);
        if (methodInfo.returnType().equals("void")) {throw new SemanticCheckException("A void method was declared. There are no void methods in this language.");}
        if (methods.containsKey(new ClassAndIdentifier(currentClass, methodName))) { throw new SemanticCheckException("Declared the method " + methodName + " twice in one class");}
        String currentClassToCheckForWrongOverload = classesAndTheirParents.get(currentClass);
        while(currentClassToCheckForWrongOverload != null){
            MethodInfo inheritedMethodInfo = methods.get(new ClassAndIdentifier(currentClassToCheckForWrongOverload, methodName));
            if (inheritedMethodInfo != null){
                if (inheritedMethodInfo.equals(methodInfo)) { break; }
                else { throw new SemanticCheckException("Incorrect overload for method " + methodName + " in class " + currentClass);}
            }
            currentClassToCheckForWrongOverload = classesAndTheirParents.get(currentClassToCheckForWrongOverload);
        }
        methods.put(new ClassAndIdentifier(currentClass, methodName), methodInfo);
        return null;
    }
}
