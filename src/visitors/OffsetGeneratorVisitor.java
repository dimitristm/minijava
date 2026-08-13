package visitors;

import syntaxtree.*;
import models.ClassHierarchy;

import java.util.LinkedList;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

public class OffsetGeneratorVisitor extends StringRepresentationVisitor {
    private static final int POINTER_SIZE = 8;
    private static final int INT_SIZE = 4;
    private static final int BOOLEAN_SIZE = 1;

    public OffsetGeneratorVisitor(ClassHierarchy classHierarchy) {
        this.classHierarchy = classHierarchy;
    }

    private LinkedList<ClassOffsets> offsets = new LinkedList<>();
    private String currentClass = null;
    private ClassHierarchy classHierarchy;

    private Integer getFieldStorageRequired(String type) {
        if (type.equals("int")) return INT_SIZE;
        else if (type.equals("boolean")) return BOOLEAN_SIZE;
        else if (type.equals("int[]")) return POINTER_SIZE;
        else if (type.equals("boolean[]")) return POINTER_SIZE;
        else { // it is a class type, so a pointer
            return POINTER_SIZE;
        }
    }

    public void printOffsets() {
        for (ClassOffsets co : offsets) {
            co.printOffsets();
        }
    }

    @Override
    public String visit(MainClass n) throws Exception {
        offsets.add(new ClassOffsets(n.f1.accept(this)));
        return null;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     */
    @Override
    public String visit(VarDeclaration n) throws Exception {
        String type = n.f0.accept(this);
        offsets.getLast().addField(n.f1.accept(this), getFieldStorageRequired(type));
        return null;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> ( VarDeclaration() )*
     * f4 -> ( MethodDeclaration() )*
     * f5 -> "}"
     */
    @Override
    public String visit(ClassDeclaration n) throws Exception {
        currentClass = n.f1.accept(this);
        offsets.add(new ClassOffsets(currentClass));
        n.f3.accept(this);
        n.f4.accept(this);
        return null;
    }

    /**
     * f1 -> Identifier()
     * f2 -> "extends"
     * f3 -> Identifier()
     * f5 -> ( VarDeclaration() )*
     * f6 -> ( MethodDeclaration() )*
     */
    @Override
    public String visit(ClassExtendsDeclaration n) throws Exception {
        currentClass = n.f1.accept(this);
        String parentClass = n.f3.accept(this);
        for (ClassOffsets cos : offsets) {
            if (cos.getClassName().equals(parentClass)) {
                offsets.add(
                        new ClassOffsets(currentClass, cos.getNextFieldOffsetValue(), cos.getNextMethodOffsetValue()));
                break;
            }
        }
        n.f5.accept(this);
        n.f6.accept(this);
        return null;
    }

    /**
     * f2 -> Identifier()
     */
    @Override
    public String visit(MethodDeclaration n) throws Exception {
        String methodName = n.f2.accept(this);
        if (!classHierarchy.getMethod(currentClass, methodName).isOverride()) {
            offsets.getLast().addMethod(methodName, POINTER_SIZE);
        }
        return null;
    }
}

record MemberAndOffset(String memberName, int offset) {
}

class ClassOffsets {
    ClassOffsets(String className) {
        this.className = className;
    }

    ClassOffsets(String className, int nextFieldOffsetValueOfParent, int nextMethodOffsetValueOfParent) {
        this.className = className;
        this.nextFieldOffsetValue = nextFieldOffsetValueOfParent;
        this.nextMethodOffsetValue = nextMethodOffsetValueOfParent;
    }

    private String className;
    private List<MemberAndOffset> offsets = new ArrayList<>();
    private int nextFieldOffsetValue;
    private int nextMethodOffsetValue;

    public String getClassName() {
        return this.className;
    }

    public List<MemberAndOffset> getOffsets() {
        return Collections.unmodifiableList(offsets);
    }

    public int getNextFieldOffsetValue() {
        return this.nextFieldOffsetValue;
    }

    public int getNextMethodOffsetValue() {
        return this.nextMethodOffsetValue;
    }

    public void addMethod(String methodName, int storageRequired) {
        offsets.add(new MemberAndOffset(methodName, nextMethodOffsetValue));
        nextMethodOffsetValue += storageRequired;
    }

    public void addField(String fieldName, int storageRequired) {
        offsets.add(new MemberAndOffset(fieldName, nextFieldOffsetValue));
        nextFieldOffsetValue += storageRequired;
    }

    public void printOffsets() {
        for (MemberAndOffset memberAndOffset : getOffsets()) {
            System.out.println(getClassName() + "." + memberAndOffset.memberName() + " : " + memberAndOffset.offset());
        }
    }
}
