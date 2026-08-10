import syntaxtree.*;
import visitor.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        if(!(args.length >= 1)){
            System.err.println("Usage: java Main <inputFile1>, <inputFile2>, ...");
            System.exit(1);
        }

        for(String arg : args){
            try (FileInputStream fis = new FileInputStream(arg)){
                System.out.println("---Checking " + arg + ".");
                MiniJavaParser parser = new MiniJavaParser(fis);

                Goal root = parser.Goal();

                DeclarationCollectorVisitor declarations = new DeclarationCollectorVisitor();
                root.accept(declarations, null);
                TypecheckVisitor eval = new TypecheckVisitor(declarations.getMethods(), declarations.getClassesAndTheirParents());
                root.accept(eval, false);
                System.err.println("SUCCESS: Program " + arg + " passed the semantic check.");

                System.out.println("Offsets:");
                OffsetGeneratorVisitor ofvis = new OffsetGeneratorVisitor(declarations.getMethods(), declarations.getClassesAndTheirParents());
                root.accept(ofvis, null);
                ofvis.printOffsets();

            }
            catch(FileNotFoundException ex){
                System.err.println(ex.getMessage());
            }
            catch(IOException ex){
                System.err.println(ex.getMessage());
            }
            catch(ParseException ex){
                System.err.println(ex.getMessage());
            }
            catch(SemanticCheckException ex){
                System.err.println("ERROR: " + ex.getMessage());
            }
        }
    }
}

class SemanticCheckException extends Exception{
    SemanticCheckException(String message){
        super(message);
    }
}

record Parameter(String type, String id) {}

record MethodInfo(String returnType, List<String> argumentTypes) {}

class ClassAndIdentifier {
    private String className;
    private String identifier;
    ClassAndIdentifier(String className, String identifier){
        this.className = className;
        this.identifier = identifier;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassAndIdentifier that = (ClassAndIdentifier)o;
        return Objects.equals(className, that.className) && Objects.equals(identifier, that.identifier);
    }
    @Override
    public int hashCode() {
        return Objects.hash(className, identifier);
    }
}

class SymbolTable<K, V>{
    private Deque<HashMap<K, V>> scoped_symbols = new ArrayDeque<>();
    public void enter_scope(){
        scoped_symbols.push(new HashMap<>());
    }
    public void insert(K identifier, V info) throws Exception{
        if (scoped_symbols.peek().containsKey(identifier)) throw new SemanticCheckException("An identifier was declared twice in the same scope.");
        scoped_symbols.peek().put(identifier, info);
    }
    //returns null if the specified symbol does not exist
    public V lookup(K key){
        for (Map<K, V> table:scoped_symbols){
            V val = table.get(key);
            if (val != null) {return val;}
        }
        return null;
    }
    public void exit_scope(){
        scoped_symbols.pop();
    }
}

class TypecheckVisitor extends GJDepthFirst<String, Boolean>{
    //in SymbolTable<ClassAndIdentifier, String> the string represents the type of the field/variable
    private SymbolTable<ClassAndIdentifier, String> variableSymbolTable = new SymbolTable<>();
    private final HashMap<ClassAndIdentifier, MethodInfo> methods;
    private final HashMap<String, String> classesAndTheirParents;
    private String currentClass;

    TypecheckVisitor(HashMap<ClassAndIdentifier, MethodInfo> methods, HashMap<String, String> classesAndTheirParents){
        this.methods = methods;
        this.classesAndTheirParents = classesAndTheirParents;
    }

   /**
    * f0 -> "class"
    * f1 -> Identifier()
    * f2 -> "{"
    * f3 -> "public"
    * f4 -> "static"
    * f5 -> "void"
    * f6 -> "main"
    * f7 -> "("
    * f8 -> "String"
    * f9 -> "["
    * f10 -> "]"
    * f11 -> Identifier()
    * f12 -> ")"
    * f13 -> "{"
    * f14 -> ( VarDeclaration() )*
    * f15 -> ( Statement() )*
    * f16 -> "}"
    * f17 -> "}"
    */
    @Override
    public String visit(MainClass n, Boolean ignored) throws Exception {

        currentClass = n.f1.accept(this, false);

        variableSymbolTable.enter_scope(); //create first scope where the fields of all classes are
        variableSymbolTable.enter_scope(); //go into the scope of main method
        //insert the local variables of the main method
        n.f14.accept(this, false);
        // handle statements here
        n.f15.accept(this, false);
        variableSymbolTable.exit_scope(); //exit main method scope
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
    public String visit(ClassDeclaration n, Boolean ignored) throws Exception {
        currentClass = n.f1.accept(this, false);
        //insert fields to symbol table
        n.f3.accept(this, false);
        //handle methods
        n.f4.accept(this, false);
        return null;
    }

   /**
    * f0 -> "class"
    * f1 -> Identifier()
    * f2 -> "extends"
    * f3 -> Identifier()
    * f4 -> "{"
    * f5 -> ( VarDeclaration() )*
    * f6 -> ( MethodDeclaration() )*
    * f7 -> "}"
    */
   public String visit(ClassExtendsDeclaration n, Boolean ignored) throws Exception {
        currentClass = n.f1.accept(this, false);
        n.f5.accept(this, false);
        n.f6.accept(this, false);
        return null;
   }

   /**
    * f0 -> "public"
    * f1 -> Type()
    * f2 -> Identifier()
    * f3 -> "("
    * f4 -> ( FormalParameterList() )?
    * f5 -> ")"
    * f6 -> "{"
    * f7 -> ( VarDeclaration() )*
    * f8 -> ( Statement() )*
    * f9 -> "return"
    * f10 -> Expression()
    * f11 -> ";"
    * f12 -> "}"
    */
    public String visit(MethodDeclaration n, Boolean ignored) throws Exception {
        variableSymbolTable.enter_scope();
        //add the parameters and the varDeclarations to the symboltable
        //parameters:
        if (n.f4.present()){
            FormalParamListData params = n.f4.accept(new FormalParameterVisitor(), null);
            for (Parameter param : params.getParameters()){
                variableSymbolTable.insert(new ClassAndIdentifier(currentClass, param.id()), param.type());
            }
        }
        //local vars:
        n.f7.accept(this, false);
        //handle statements:
        n.f8.accept(this, false);
        //make sure the return type is correct
        if (! n.f1.accept(this, false).equals(n.f10.accept(this, false))) { throw new SemanticCheckException("the return type of the function " + n.f2.accept(this, false) + " is wrong.");}
        variableSymbolTable.exit_scope();
        return null;
    }

    //------------var declarations------------
    /**
    * f0 -> Type()
    * f1 -> Identifier()
    * f2 -> ";"
    */
    // add to the symboltable
    public String visit(VarDeclaration n, Boolean ignored) throws Exception {
        variableSymbolTable.insert(new ClassAndIdentifier(currentClass, n.f1.accept(this, false)), n.f0.accept(this, false));
        return null;
    }

    private String getTypeOfVariable(String varName) throws Exception{
        String type = null;

        //look in the current class and the parent classes for the type of this identifier, if it's not found
        //then it has not been declared in this scope so type = null
        String currentClassToCheck = currentClass;
        while (currentClassToCheck != null){
            type = variableSymbolTable.lookup(new ClassAndIdentifier(currentClassToCheck, varName));
            if (type != null) {return type;}
            currentClassToCheck = classesAndTheirParents.get(currentClassToCheck);
        }
        throw new SemanticCheckException("The variable " + varName + " has not been declared yet");
    }
    //string rep
    public String visit(Identifier n, Boolean expectingTypeOfIdentifier) throws Exception {
        if(expectingTypeOfIdentifier) { return getTypeOfVariable(n.f0.toString()); }
        return n.f0.toString();
    }
   //------------getting types------------
   /**
    * f0 -> ArrayType()
    *       | BooleanType()
    *       | IntegerType()
    *       | Identifier()
    */
    public String visit(Type n, Boolean ignored) throws Exception {
        return n.f0.accept(this, false);
    }

    /**
     * f0 -> BooleanArrayType()
     *       | IntegerArrayType()
     */
    public String visit(ArrayType n, Boolean ignored) throws Exception {
       return n.f0.accept(this, false);
    }
    /**
     * f0 -> "boolean"
     * f1 -> "["
     * f2 -> "]"
     */
    public String visit(BooleanArrayType n, Boolean ignored) throws Exception {
        return "boolean[]";
    }
    /**
     * f0 -> "int"
     * f1 -> "["
     * f2 -> "]"
     */
    public String visit(IntegerArrayType n, Boolean ignored) throws Exception {
        return "int[]";
    }
    /**
     * f0 -> "boolean"
     */
    public String visit(BooleanType n, Boolean ignored) throws Exception {
        return "boolean";
    }
    /**
     * f0 -> "int"
     */
    public String visit(IntegerType n, Boolean ignored) throws Exception {
        return "int";
    }
   //------------expressions return their type------------
   /**
    * f0 -> AndExpression()
    *       | CompareExpression()
    *       | PlusExpression()
    *       | MinusExpression()
    *       | TimesExpression()
    *       | ArrayLookup()
    *       | ArrayLength()
    *       | MessageSend()
    *       | Clause()
    */
    public String visit(Expression n, Boolean ignored) throws Exception {
        return n.f0.accept(this, false);
    }
    //returns the type of the expressions as a string, or throws an exception if they're not the same
    private String bothAreSpecificType(Node node1, Node node2, String wantedType) throws Exception{
        String type1 = node1.accept(this, false);
        String type2 = node2.accept(this, false);
        if (type1.equals(type2) && type1.equals(wantedType)) {return type1;}
        else throw new SemanticCheckException("Expected that the variables would be of type " + wantedType + " but they were not.");
    }
    /**
     * f0 -> Clause()
     * f1 -> "&&"
     * f2 -> Clause()
     */
    public String visit(AndExpression n, Boolean ignored) throws Exception {
        return bothAreSpecificType(n.f0, n.f2, "boolean");
    }
    /**
     * f0 -> PrimaryExpression()
     * f1 -> "<"
     * f2 -> PrimaryExpression()
     */
    public String visit(CompareExpression n, Boolean ignored) throws Exception {
        bothAreSpecificType(n.f0, n.f2, "int");
        return "boolean";
    }   
    /**
     * f0 -> PrimaryExpression()
     * f1 -> "+"
     * f2 -> PrimaryExpression()
     */
    public String visit(PlusExpression n, Boolean ignored) throws Exception {
        return bothAreSpecificType(n.f0, n.f2, "int");
    }   
    /**
     * f0 -> PrimaryExpression()
     * f1 -> "-"
     * f2 -> PrimaryExpression()
     */
    public String visit(MinusExpression n, Boolean ignored) throws Exception {
       return bothAreSpecificType(n.f0, n.f2, "int");
    }   
    /**
     * f0 -> PrimaryExpression()
     * f1 -> "*"
     * f2 -> PrimaryExpression()
     */
    public String visit(TimesExpression n, Boolean ignored) throws Exception {
       return bothAreSpecificType(n.f0, n.f2, "int");
    }
    /**
     * f0 -> PrimaryExpression()
     * f1 -> "["
     * f2 -> PrimaryExpression()
     * f3 -> "]"
     */
    public String visit(ArrayLookup n, Boolean ignored) throws Exception {
        bothAreCompatibleType(n.f2.accept(this,false), "int");
        String type = n.f0.accept(this, false);
        return type.substring(0, type.length() - 2);//remove the last 2 characters ([])
    }
    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> "length"
     */
    public String visit(ArrayLength n, Boolean ignored) throws Exception {
        String exp_type = n.f0.accept(this, false);
        if (!exp_type.endsWith("[]")) { throw new SemanticCheckException("tried to get array length of something that is not an array"); }
        return "int";
    }
    /**
     * f0 -> PrimaryExpression() ->must be class OR this
     * f1 -> "."
     * f2 -> Identifier() ->must be function of class
     * f3 -> "("
     * f4 -> ( ExpressionList() )? -> must be correct (methodInfo)
     * f5 -> ")"
     */
    public String visit(MessageSend n, Boolean ignored) throws Exception {
        String className = n.f0.accept(this, false);
        String methodName = n.f2.accept(this, false);

        //make sure the method exists in the class or a parent class
        String currentClassToCheck = className;
        MethodInfo methodInfo = methods.get(new ClassAndIdentifier(currentClassToCheck, methodName));
        while(currentClassToCheck != null){
            methodInfo = methods.get(new ClassAndIdentifier(currentClassToCheck, methodName));
            if (methodInfo != null) {break;}
            currentClassToCheck = classesAndTheirParents.get(currentClassToCheck);
        }
        if (methodInfo == null) {throw new SemanticCheckException("method " + methodName + " was called on an object " + "(of type " + className + ")" +" that doesn't have that method");}

        //make sure the expression list matches the methodInfo, ? needed because f.f4.accept returns false if it's not present but we instead need an empty list to represent no falsements
        LinkedList<String> expressionList = n.f4.present() ? n.f4.accept(new GetExpressionListTypesVisitor(this), null) : new LinkedList<String>();

        if (! (expressionList.size() == methodInfo.argumentTypes().size()) ) {throw new SemanticCheckException("Incorrect amount of arguments in method call");}
        for(int i = 0; i < expressionList.size(); i++){
            bothAreCompatibleType(methodInfo.argumentTypes().get(i), expressionList.get(i));
        }
        //return our return type
        return methodInfo.returnType();
    }   
    /**
     * f0 -> NotExpression()
     *       | PrimaryExpression()
     */
    public String visit(Clause n, Boolean ignored) throws Exception {
       return n.f0.accept(this, false);
    }
    /**
     * f0 -> IntegerLiteral()
     *       | TrueLiteral()
     *       | FalseLiteral()
     *       | Identifier()
     *       | ThisExpression()
     *       | ArrayAllocationExpression()
     *       | AllocationExpression()
     *       | BracketExpression()
     */
    public String visit(PrimaryExpression n, Boolean ignored) throws Exception {
       return n.f0.accept(this, true);
    }   
    /**
     * f0 -> <INTEGER_LITERAL>
     */
    public String visit(IntegerLiteral n, Boolean ignored) throws Exception {
        return "int";
    }   
    /**
     * f0 -> "true"
     */
    public String visit(TrueLiteral n, Boolean ignored) throws Exception {
        return "boolean";
    }   
    /**
     * f0 -> "false"
     */
    public String visit(FalseLiteral n, Boolean ignored) throws Exception {
        return "boolean";
    }   
    /**
     * f0 -> "this"
     */
    public String visit(ThisExpression n, Boolean ignored) throws Exception {
        return currentClass;
    }   
    /**
     * f0 -> BooleanArrayAllocationExpression()
     *       | IntegerArrayAllocationExpression()
     */
    public String visit(ArrayAllocationExpression n, Boolean ignored) throws Exception {
       return n.f0.accept(this, false);
    }
    private String checkNodeForTypeAndReturnAnother (Node node, String typeToCheckFor, String typeToReturn) throws Exception{
        if (!node.accept(this,false).equals(typeToCheckFor)) {throw new SemanticCheckException("wrong type, was supposed to be " + typeToCheckFor);}
        return typeToReturn;
    }
    /**
     * f0 -> "new"
     * f1 -> "boolean"
     * f2 -> "["
     * f3 -> Expression()
     * f4 -> "]"
     */
    public String visit(BooleanArrayAllocationExpression n, Boolean ignored) throws Exception {
        return checkNodeForTypeAndReturnAnother(n.f3, "int", "boolean[]");
    }   
    /**
     * f0 -> "new"
     * f1 -> "int"
     * f2 -> "["
     * f3 -> Expression()
     * f4 -> "]"
     */
    public String visit(IntegerArrayAllocationExpression n, Boolean ignored) throws Exception {
        return checkNodeForTypeAndReturnAnother(n.f3, "int", "int[]");
    }   
    /**
     * f0 -> "new"
     * f1 -> Identifier() ->class name
     * f2 -> "("
     * f3 -> ")"
     */
    public String visit(AllocationExpression n, Boolean ignored) throws Exception {
        String className = n.f1.accept(this, false);
        if(!classesAndTheirParents.containsKey(className)){
            throw new SemanticCheckException("Allocation expression did not contain a declared class, " + className + " is not recognised");
        }
        return className;//in this case, identifier is a class name, not a func or var so this is right
    }   
    /**
     * f0 -> "!"
     * f1 -> Clause()
     */
    public String visit(NotExpression n, Boolean ignored) throws Exception {
        return checkNodeForTypeAndReturnAnother(n.f1, "boolean", "boolean");
    }   
    /**
     * f0 -> "("
     * f1 -> Expression()
     * f2 -> ")"
     */
    public String visit(BracketExpression n, Boolean ignored) throws Exception {
        return n.f1.accept(this, false);
    }
   //statements
   /**
    * f0 -> Block()
    *       | AssignmentStatement()
    *       | ArrayAssignmentStatement()
    *       | IfStatement()
    *       | WhileStatement()
    *       | PrintStatement()
    */
   public String visit(Statement n, Boolean ignored) throws Exception {
      return n.f0.accept(this, false);
   }

    //check that type2 can be assigned to type 1, if not, throw an exception
    private void bothAreCompatibleType(String type1, String type2) throws Exception{
        if (type1 == null || type2 == null) {throw new SemanticCheckException("could not find type, probably not declared yet");}
        if (type1.equals(type2)) {return;}
        // check if type2 is derived by type1, if yes, the assignment is correct
        String ancestorOfType2 = classesAndTheirParents.get(type2);
        while(ancestorOfType2 != null){
            if (ancestorOfType2.equals(type1)) {return;}
            ancestorOfType2 = classesAndTheirParents.get(ancestorOfType2);
        }
        throw new SemanticCheckException("Expected that two things would be of the same type");
    }
   /**
    * f0 -> Identifier()
    * f1 -> "="
    * f2 -> Expression()
    * f3 -> ";"
    */
    public String visit(AssignmentStatement n, Boolean ignored) throws Exception {
        bothAreCompatibleType(n.f0.accept(this, true), n.f2.accept(this, false));
        return null;
    }   
    /**
     * f0 -> Identifier()
     * f1 -> "["
     * f2 -> Expression()
     * f3 -> "]"
     * f4 -> "="
     * f5 -> Expression()
     * f6 -> ";"
     */
    public String visit(ArrayAssignmentStatement n, Boolean ignored) throws Exception {
        String IDtype = n.f0.accept(this, true);
        String rType = n.f5.accept(this, false);
        if (IDtype == null) { throw new SemanticCheckException("Tried to do an array assignment Statement on something that has not been declared");}
        if (!IDtype.endsWith("[]")) {throw new SemanticCheckException("tried to do an array assignment statement on a non-array");}
        if (!n.f2.accept(this, false).equals("int")) { throw new SemanticCheckException("Tried to do an array assignment Statement but the there wasn't an int inside the brackets");}
        if (!IDtype.equals(rType + "[]")) {throw new SemanticCheckException("In an array assignment statement, the right hand value is not of the right type");}
        return rType;
    }   
    /**
     * f0 -> "if"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     * f5 -> "else"
     * f6 -> Statement()
     */
    public String visit(IfStatement n, Boolean ignored) throws Exception {
       if (!n.f2.accept(this, false).equals("boolean")) {throw new SemanticCheckException("if statement must have a boolean type in its parentheses");}
       n.f4.accept(this, false);
       n.f6.accept(this, false);
       return null;
    }   
    /**
     * f0 -> "while"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     */
    public String visit(WhileStatement n, Boolean ignored) throws Exception {
       if (!n.f2.accept(this, false).equals("boolean")) {throw new SemanticCheckException("while statement must have a boolean type in its parentheses");}
       n.f4.accept(this, false);
       return null;
    }
       /**
    * f0 -> "System.out.println
    * f1 -> "("
    * f2 -> Expression()
    * f3 -> ")"
    * f4 -> ";"
    */
    public String visit(PrintStatement n, Boolean ignored) throws Exception {
        if (!n.f2.accept(this, false).equals("int")) {throw new SemanticCheckException("print statement can only take int types");}
        return null;
    }

}

class DeclarationCollectorVisitor extends StringRepresentationVisitor{
    private HashMap<ClassAndIdentifier, MethodInfo> methods = new HashMap<>();
    private HashMap<String, String> classesAndTheirParents = new HashMap<>();
    private String currentClass;
    public HashMap<ClassAndIdentifier, MethodInfo> getMethods(){ return this.methods; }
    public HashMap<String, String> getClassesAndTheirParents() { return this.classesAndTheirParents; }

   /**
    * f0 -> "class"
    * f1 -> Identifier()
    * f2 -> "{"
    * f3 -> "public"
    * f4 -> "static"
    * f5 -> "void"
    * f6 -> "main"
    * f7 -> "("
    * f8 -> "String"
    * f9 -> "["
    * f10 -> "]"
    * f11 -> Identifier()
    * f12 -> ")"
    * f13 -> "{"
    * f14 -> ( VarDeclaration() )*
    * f15 -> ( Statement() )*
    * f16 -> "}"
    * f17 -> "}"
    */
    @Override
    public String visit(MainClass n, Void ignored) throws Exception {
        String mainClassName = n.f1.accept(this, null);
        currentClass = mainClassName;
        classesAndTheirParents.put(mainClassName, null);
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
   public String visit(ClassDeclaration n, Void ignored) throws Exception {
        this.currentClass = n.f1.accept(this, null);
        if (classesAndTheirParents.containsKey(currentClass)) {throw new SemanticCheckException("This class has already been declared elsewhere: " + currentClass); }
        classesAndTheirParents.put(currentClass, null);
        n.f4.accept(this, null);
        return null;
   }

   /**
    * f0 -> "class"
    * f1 -> Identifier()
    * f2 -> "extends"
    * f3 -> Identifier()
    * f4 -> "{"
    * f5 -> ( VarDeclaration() )*
    * f6 -> ( MethodDeclaration() )*
    * f7 -> "}"
    */
   public String visit(ClassExtendsDeclaration n, Void ignored) throws Exception {
        this.currentClass = n.f1.accept(this, null);
        String parentClass = n.f3.accept(this, null);
        if (classesAndTheirParents.containsKey(currentClass)) {throw new SemanticCheckException("This class has already been declared elsewhere: " + currentClass); }
        if (!classesAndTheirParents.containsKey(parentClass)) { throw new SemanticCheckException("Class " + currentClass + " tried to inherit from a class called " + parentClass + ", but that class had not been declared yet or has no declaration at all");}
        classesAndTheirParents.put(currentClass, parentClass);
        n.f6.accept(this, null);
        return null;
   }

   /**
    * f0 -> "public"
    * f1 -> Type()
    * f2 -> Identifier()
    * f3 -> "("
    * f4 -> ( FormalParameterList() )?
    * f5 -> ")"
    * f6 -> "{"
    * f7 -> ( VarDeclaration() )*
    * f8 -> ( Statement() )*
    * f9 -> "return"
    * f10 -> Expression()
    * f11 -> ";"
    * f12 -> "}"
    */
    public String visit(MethodDeclaration n, Void ignored) throws Exception {
        String methodName = n.f2.accept(this, null);
        List<String> argTypes = new LinkedList<>();
        if (n.f4.present()) {
            for (Parameter p : n.f4.accept(new FormalParameterVisitor(), null).getParameters()) {
                argTypes.add(p.type());
            }
        }
        MethodInfo methodInfo = new MethodInfo(n.f1.accept(this, null), argTypes);
        if (methodInfo.returnType().equals("void")) {throw new SemanticCheckException("A void method was declared. There are no void methods in this language.");}
        // first check the current class to see if this method name has already been used and error if it has
        // then check parent classes to see if the name has been used in which case they must have same methodInfo
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

class GetExpressionListTypesVisitor extends GJDepthFirst<LinkedList<String>, Void>{
    private TypecheckVisitor typeVisitor;
    GetExpressionListTypesVisitor(TypecheckVisitor typeVisitor){
        this.typeVisitor = typeVisitor;
    }
    /**
     * f0 -> Expression()
     * f1 -> ExpressionTail()
     */
    public LinkedList<String> visit(ExpressionList n, Void ignored) throws Exception {
       LinkedList<String> typesOfExpressions = new LinkedList<String>();
       typesOfExpressions.add(n.f0.accept(typeVisitor, false));
       typesOfExpressions.addAll(n.f1.accept(this, null));
       return typesOfExpressions;
    }
    /**
     * f0 -> ( ExpressionTerm() )*
     */
    public LinkedList<String> visit(ExpressionTail n, Void ignored) throws Exception {
        LinkedList<String> typesOfExpressions = new LinkedList<String>();
        for(int i = 0; i < n.f0.size(); i++){
            typesOfExpressions.add(n.f0.elementAt(i).accept(this, null).get(0));
        }
        return typesOfExpressions;
    }
    /**
     * f0 -> ","
     * f1 -> Expression()
     */
    public LinkedList<String> visit(ExpressionTerm n, Void ignored) throws Exception {
       LinkedList<String> type = new LinkedList<String>();
       type.add(n.f1.accept(typeVisitor, false));
       return type;
    }  
}


//visitor that returns the string that represents primitive types, as well as IDs (identifiers) in string format
class StringRepresentationVisitor extends GJDepthFirst<String, Void>{
   public String visit(BooleanArrayType n, Void ignored){
       return "boolean[]";
   }

   public String visit(IntegerArrayType n, Void ignored){
      return "int[]";
   }

   public String visit(BooleanType n, Void ignored){
      return "boolean";
   }

   public String visit(IntegerType n, Void ignored){
      return "int";
   }
    public String visit(Identifier n, Void ignored){
        return n.f0.toString();
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
class OffsetGeneratorVisitor extends StringRepresentationVisitor{
    private static final int POINTER_SIZE = 8;
    private static final int INT_SIZE = 4;
    private static final int BOOLEAN_SIZE = 1;

    OffsetGeneratorVisitor(HashMap<ClassAndIdentifier, MethodInfo> methods, HashMap<String, String> classesAndTheirParents){
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
    public String visit(MainClass n, Void ignored) throws Exception{
        offsets.add(new ClassOffsets(n.f1.accept(this,null)));
        return null;
    }
    /**
    * f0 -> Type()
    * f1 -> Identifier()
    */
    public String visit(VarDeclaration n, Void ignored) throws Exception {
        String type = n.f0.accept(this, null);
        offsets.getLast().addMember(n.f1.accept(this, null), getStorageRequired(type, false), false);
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
   public String visit(ClassDeclaration n, Void ignored) throws Exception {
        currentClass = n.f1.accept(this, null);
        offsets.add(new ClassOffsets(currentClass));
        n.f3.accept(this, null);
        n.f4.accept(this, null);
        return null;
   }

   /**
    * f1 -> Identifier()
    * f2 -> "extends"
    * f3 -> Identifier()
    * f5 -> ( VarDeclaration() )*
    * f6 -> ( MethodDeclaration() )*
    */
   public String visit(ClassExtendsDeclaration n, Void ignored) throws Exception {
        currentClass = n.f1.accept(this, null);
        String parentClass = n.f3.accept(this, null);
        for (ClassOffsets cos : offsets){
            if (cos.getClassName().equals(parentClass)){
                offsets.add(new ClassOffsets(currentClass, cos.getNextFieldOffsetValue(), cos.getNextMethodOffsetValue()));
                break;
            }
        }
        n.f5.accept(this, null);
        n.f6.accept(this, null);
        return null;
   }

   /**
    * f2 -> Identifier()
    */
    public String visit(MethodDeclaration n, Void ignored) throws Exception {
        String parentClass = classesAndTheirParents.get(currentClass);
        String methodName = n.f2.accept(this, null);
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


class FormalParamListData{
    private List<Parameter> parameters = new LinkedList<>();
    public List<Parameter> getParameters() {
        return Collections.unmodifiableList(parameters);
    }
    public void add(String type, String ID){
        this.parameters.add(new Parameter(type, ID));
    }
    public void addAll(FormalParamListData otherList){
        this.parameters.addAll(otherList.getParameters());
    }
    public int size(){
        return parameters.size();
    }
}

class FormalParameterVisitor extends GJDepthFirst<FormalParamListData, Void>{
    private StringRepresentationVisitor stringRep = new StringRepresentationVisitor();
   /**
    * f0 -> FormalParameter()
    * f1 -> FormalParameterTail()
    */
    public FormalParamListData visit(FormalParameterList n, Void ignored) throws Exception {
        FormalParamListData list = n.f0.accept(this, null);
        list.addAll(n.f1.accept(this, null));
        return list;
    }

   /**
    * f0 -> Type()
    * f1 -> Identifier()
    */
    public FormalParamListData visit(FormalParameter n, Void ignored) throws Exception {
        FormalParamListData data = new FormalParamListData();
        data.add(n.f0.accept(stringRep, null), n.f1.accept(stringRep, null));
        return data;
    }

   /**
    * f0 -> ( FormalParameterTerm() )*
    */
    public FormalParamListData visit(FormalParameterTail n, Void ignored) throws Exception {
        FormalParamListData types = new FormalParamListData();
        for(int i = 0; i < n.f0.size(); i++){
            types.addAll(n.f0.elementAt(i).accept(this, null));
        }
        return types;
    }

   /**
    * f0 -> ","
    * f1 -> FormalParameter()
    */
    public FormalParamListData visit(FormalParameterTerm n, Void ignored) throws Exception {
        return n.f1.accept(this, null);
    }

}