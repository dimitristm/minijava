package visitors;

import syntaxtree.*;
import models.ClassAndIdentifier;
import models.MethodInfo;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Collections;

public class OffsetGeneratorVisitor extends StringRepresentationVisitor{
    private static final int POINTER_SIZE = 8;
    private static final int INT_SIZE = 4;
    private static final int BOOLEAN_SIZE = 1;

    public OffsetGeneratorVisitor(Map<ClassAndIdentifier, MethodInfo> methods, Map<String, String> classesAndTheirParents){
        this.classesAndTheirParents = classesAndTheirParents;
        this.methods = methods;
        storageRequired.put("int", INT_SIZE);
        storageRequired.put("boolean", BOOLEAN_SIZE);
        storageRequired.put("int[]", POINTER_SIZE);
        storageRequired.put("boolean[]", POINTER_SIZE);
    }
    private LinkedList<ClassOffsets> offsets = new LinkedList<>();
    private String currentClass = null;
    private Map<String, Integer> storageRequired = new HashMap<>();

    private Map<String, String> classesAndTheirParents = new HashMap<>();
    private Map<ClassAndIdentifier, MethodInfo> methods = new HashMap<>();

    private Integer getStorageRequired(String type, boolean isFunction){
        if (isFunction) { return POINTER_SIZE; } //it is a pointer
        Integer storage = storageRequired.get(type);
        if (storage == null) { return POINTER_SIZE; } //it is a class type, so a pointer
        return storage;
    }
    public void printOffsets(){
        for(ClassOffsets co : offsets){
            co.printOffsets();
        }
    }
    @Override
    public String visit(MainClass n) throws Exception{
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
        offsets.getLast().addMember(n.f1.accept(this), getStorageRequired(type, false), false);
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
        for (ClassOffsets cos : offsets){
            if (cos.getClassName().equals(parentClass)){
                offsets.add(new ClassOffsets(currentClass, cos.getNextFieldOffsetValue(), cos.getNextMethodOffsetValue()));
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
        String parentClass = classesAndTheirParents.get(currentClass);
        String methodName = n.f2.accept(this);
        if(parentClass == null){
            offsets.getLast().addMember(methodName, getStorageRequired(null, true), true);
        }
        else{
            boolean methodIsBeingOverriden = false;
            while(parentClass != null){
                if(methods.get(new ClassAndIdentifier(parentClass, methodName)) != null){
                    methodIsBeingOverriden = true;
                    break;
                }
                parentClass = classesAndTheirParents.get(parentClass);
            }
            if(!methodIsBeingOverriden){
                offsets.getLast().addMember(methodName, getStorageRequired(null, true), true);
            }
        }
        return null;
    }
}

record MemberAndOffset(String memberName, int offset) {}

class ClassOffsets{
    ClassOffsets(String className){
        this.className = className;
    }
    ClassOffsets(String className, int nextFieldOffsetValueOfParent, int nextMethodOffsetValueOfParent){
        this.className = className;
        this.nextFieldOffsetValue = nextFieldOffsetValueOfParent;
        this.nextMethodOffsetValue = nextMethodOffsetValueOfParent;
    }
    private String className;
    private List<MemberAndOffset> offsets = new LinkedList<>();
    private int nextFieldOffsetValue;
    private int nextMethodOffsetValue;
    public String getClassName(){
        return this.className;
    }
    public List<MemberAndOffset> getOffsets(){
        return Collections.unmodifiableList(offsets);
    }
    public int getNextFieldOffsetValue(){
        return this.nextFieldOffsetValue;
    }
    public int getNextMethodOffsetValue(){
        return this.nextMethodOffsetValue;
    }
    public void addMember(String memberName, int storageRequired, boolean isMethod){
        if(isMethod){
            offsets.add(new MemberAndOffset(memberName, nextMethodOffsetValue));
            nextMethodOffsetValue += storageRequired;
        }
        else{
            offsets.add(new MemberAndOffset(memberName, nextFieldOffsetValue));
            nextFieldOffsetValue += storageRequired;
        }
    }
    public void printOffsets(){
        for (MemberAndOffset memberAndOffset : getOffsets()){
            System.out.println(getClassName() + "." + memberAndOffset.memberName() + " : " + memberAndOffset.offset());
        }
    }
}
