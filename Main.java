import syntaxtree.*;
import visitor.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        if(args.length != 1){
            System.err.println("Usage: java Main <inputFile>");
            System.exit(1);
        }

        FileInputStream fis = null;
        try{
            fis = new FileInputStream(args[0]);
            MiniJavaParser parser = new MiniJavaParser(fis);

            Goal root = parser.Goal();

            System.err.println("Program parsed successfully.");

            DeclarationCollectorVisitor declarations = new DeclarationCollectorVisitor();
            root.accept(declarations, null);
            TypecheckVisitor eval = new TypecheckVisitor(declarations.getMethods(), declarations.getClassesAndTheirParents());
            root.accept(eval, null);
        }
        catch(ParseException ex){
            System.out.println(ex.getMessage());
        }
        catch(FileNotFoundException ex){
            System.err.println(ex.getMessage());
        }
        finally{
            try{
                if(fis != null) fis.close();
            }
            catch(IOException ex){
                System.err.println(ex.getMessage());
            }
        }
    }
}

class MethodInfo{
    public String returnType;
    public LinkedList<String> argumentTypes; //todo: arraylist because i could figure it out all at once and so it won't have to be resized?
    MethodInfo(String retType, LinkedList<String> argTypes){
        this.returnType = retType;
        this.argumentTypes = argTypes;
    }
    @Override
    public boolean equals(Object m){
        MethodInfo mInfo = (MethodInfo)m;
        return mInfo.returnType.equals(this.returnType) && mInfo.argumentTypes.equals(this.argumentTypes);
    }
}

class IdentifierInfo{
    public String type;
    public MethodInfo additionalInfo; //if the identifier is actually a function, use this. todo: seperate symbol table for functions and romeve this from regular symbol table?
}

class ClassAndIdentifier {
    String className;
    String identifier;
    ClassAndIdentifier(String className, String identifier){
        this.className = className;
        this.identifier = identifier;
    }
    @Override
    public boolean equals(Object c){
        ClassAndIdentifier y = (ClassAndIdentifier)c;
        return this.className == y.className && this.identifier == y.identifier;
    }
    @Override
    public int hashCode(){
        return this.className.hashCode() + this.identifier.hashCode();
    }
}

class SymbolTable<K, V>{
    // Pair<ClassName, IdentifierName>, IdentifierInfo>
    private LinkedList<HashMap<K, V>> symbols = new LinkedList<HashMap<K, V>>();
    public void enter(){
        symbols.add(0, new HashMap<K, V>());
    }
    public void insert(K classAndIdentifier, V info) throws Exception{
        if (symbols.get(0).containsKey(classAndIdentifier)) throw new Exception("An identifier was declared twice in the same scope.");
        symbols.get(0).put(classAndIdentifier, info);
    }
    //returns null if the specified symbol does not exist
    public V lookup(K key){
        for (Map<K, V> table:symbols){
            V val = table.get(key);
            if (val != null) {return val;}
        }
        return null;
    }
    public void exit(){
        symbols.remove(0);
    }
}

class TypecheckVisitor extends GJDepthFirst<LinkedList<String>, Void>{
    //in SymbolTable<ClassAndIdentifier, String> the string represents the type of the field/variable
    private SymbolTable<ClassAndIdentifier, String> variableSymbolTable = new SymbolTable<ClassAndIdentifier, String>();
    private final HashMap<ClassAndIdentifier, MethodInfo> methods; // why would this be a symbol table? it would always have one layer that is never exited
    private final HashMap<String, String> classesAndTheirParents; // merge into one with the above? would it make the algo for checking overloaded functions slower or faster?
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
    public LinkedList<String> visit(MainClass n, Void argu) throws Exception {

        currentClass = n.f1.accept(this, null).get(1);
        // classesAndTheirParents.put(currentClass, null); //shouldn't this be a compiler error anyway because classesAndTheirParents is final?

        // we don't add main method to the table since it's handled by exception later (?), just immediately start taking its local variables
        variableSymbolTable.enter(); //create first scope where the fields of all classes are
        variableSymbolTable.enter(); //go into the scope of main method
        for(int i = 0; i < n.f14.size(); i++){ //insert the local variables of main
            LinkedList<String> typeAndID = n.f14.elementAt(i).accept(this, null);
            variableSymbolTable.insert(new ClassAndIdentifier(currentClass, typeAndID.get(1)), typeAndID.get(0));
        }
        // handle statements here
        n.f15.accept(this, null);
        variableSymbolTable.exit(); //exit main method scope
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
    public LinkedList<String> visit(ClassDeclaration n, Void argu) throws Exception {
        currentClass = n.f1.accept(this, null).get(1);
        for(int i = 0; i < n.f3.size(); i++){//insert fields to symbol table
            LinkedList<String> typeAndID = n.f3.elementAt(i).accept(this, null);
            variableSymbolTable.insert(new ClassAndIdentifier(currentClass, typeAndID.get(1)), typeAndID.get(0));
        }
        //handle methods
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
   public LinkedList<String> visit(ClassExtendsDeclaration n, Void argu) throws Exception {
        currentClass = n.f1.accept(this, null).get(1);
        for(int i = 0; i < n.f5.size(); i++){//insert fields to symbol table
            LinkedList<String> typeAndID = n.f5.elementAt(i).accept(this, null);
            variableSymbolTable.insert(new ClassAndIdentifier(currentClass, typeAndID.get(1)), typeAndID.get(0));
        }
        //handle methods
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
    public LinkedList<String> visit(MethodDeclaration n, Void argu) throws Exception {
        variableSymbolTable.enter();
        //add the parameters and the varDeclarations to the symboltable -- todo: what is f4 if we have no parameters and does it break this?
        //parameters:
        if (n.f4.present()){
        FormalParamListData params = n.f4.accept(new FormalParameterVisitor(), null);
            for (int i = 0; i < params.size(); i++){
                variableSymbolTable.insert(new ClassAndIdentifier(currentClass, params.argumentIDs.get(i)), params.argumentTypes.get(i));
            }
        }
        //local vars:
        for(int i = 0; i < n.f7.size(); i++){//insert fields to symbol table
            LinkedList<String> typeAndID = n.f7.elementAt(i).accept(this, null);
            variableSymbolTable.insert(new ClassAndIdentifier(currentClass, typeAndID.get(1)), typeAndID.get(0));
        }
        n.f8.accept(this, argu);
        //make sure the return type is correct
        if (!(n.f1.accept(this, null).get(0) == n.f10.accept(this, null).get(0))) { throw new Exception("the return type of the function " + n.f2.accept(this, null).get(1) + " is wrong.");}
        variableSymbolTable.exit();
        return null;
    }

    //------------var declarations------------
    /**
    * f0 -> Type()
    * f1 -> Identifier()
    * f2 -> ";"
    */
    // index 0 = type, index 1 = identifier name
    public LinkedList<String> visit(VarDeclaration n, Void argu) throws Exception {
        LinkedList<String> typeAndID = new LinkedList<String>();
        typeAndID.add(n.f0.accept(this, null).get(0));
        typeAndID.add(n.f1.accept(this, null).get(1));
        return typeAndID;
    }

    // index 0 = type of identifier if it already exists, null if it doesn't
    // index 1 = name of the identifier
    public LinkedList<String> visit(Identifier n, Void argu) throws Exception {
        LinkedList<String> typeIfItExistsAndID = new LinkedList<String>();
        String IDname = n.f0.toString();
        String type = null;

        //look in the current class and the parent classes for the type of this identifier, if it's not found
        //then it has not been declared in this scope so type = null
        String currentClassToCheck = currentClass;
        while (currentClassToCheck != null){
            type = variableSymbolTable.lookup(new ClassAndIdentifier(currentClassToCheck, IDname));
            if (type != null) break;
            currentClassToCheck = classesAndTheirParents.get(currentClassToCheck);
        }

        typeIfItExistsAndID.add(type);
        typeIfItExistsAndID.add(IDname);
        return typeIfItExistsAndID;
    }
   //------------getting types------------
   /**
    * f0 -> ArrayType()
    *       | BooleanType()
    *       | IntegerType()
    *       | Identifier()
    */
    public LinkedList<String> visit(Type n, Void argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> BooleanArrayType()
     *       | IntegerArrayType()
     */
    public LinkedList<String> visit(ArrayType n, Void argu) throws Exception {
       return n.f0.accept(this, argu);
    }
    /**
     * f0 -> "boolean"
     * f1 -> "["
     * f2 -> "]"
     */
    public LinkedList<String> visit(BooleanArrayType n, Void argu) throws Exception {
        LinkedList<String> ret= new LinkedList<String>();
        ret.add("boolean[]");
        return ret;
    }
    /**
     * f0 -> "int"
     * f1 -> "["
     * f2 -> "]"
     */
    public LinkedList<String> visit(IntegerArrayType n, Void argu) throws Exception {
        LinkedList<String> ret= new LinkedList<String>();
        ret.add("int[]");
        return ret;
    }
    /**
     * f0 -> "boolean"
     */
    public LinkedList<String> visit(BooleanType n, Void argu) throws Exception {
        LinkedList<String> ret= new LinkedList<String>();
        ret.add("boolean");
        return ret;
    }
    /**
     * f0 -> "int"
     */
    public LinkedList<String> visit(IntegerType n, Void argu) throws Exception {
        LinkedList<String> ret= new LinkedList<String>();
        ret.add("int");
        return ret;
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
    public LinkedList<String> visit(Expression n, Void argu) throws Exception {
        return n.f0.accept(this, argu);
    }
    //returns the type of the expressions in a list of one string, or throws an exception if they're not the same
    private LinkedList<String> bothAreSpecificType(Node node1, Node node2, String wantedType) throws Exception{
        LinkedList<String> ret = new LinkedList<String>();
        String type1 = node1.accept(this, null).get(0);
        String type2 = node2.accept(this, null).get(0);
        if (type1.equals(type2) && type1.equals(wantedType)) {ret.add(type1); return ret;}
        else throw new Exception("Expected that the variables would be of type " + wantedType + " but they were not.");
    }
    /**
     * f0 -> Clause()
     * f1 -> "&&"
     * f2 -> Clause()
     */
    public LinkedList<String> visit(AndExpression n, Void argu) throws Exception {
        return bothAreSpecificType(n.f0, n.f2, "boolean");
    }
    /**
     * f0 -> PrimaryExpression()
     * f1 -> "<"
     * f2 -> PrimaryExpression()
     */
    public LinkedList<String> visit(CompareExpression n, Void argu) throws Exception {
        return bothAreSpecificType(n.f0, n.f2, "int");

    }   
    /**
     * f0 -> PrimaryExpression()
     * f1 -> "+"
     * f2 -> PrimaryExpression()
     */
    public LinkedList<String> visit(PlusExpression n, Void argu) throws Exception {
        return bothAreSpecificType(n.f0, n.f2, "int");
    }   
    /**
     * f0 -> PrimaryExpression()
     * f1 -> "-"
     * f2 -> PrimaryExpression()
     */
    public LinkedList<String> visit(MinusExpression n, Void argu) throws Exception {
       return bothAreSpecificType(n.f0, n.f2, "int");
    }   
    /**
     * f0 -> PrimaryExpression()
     * f1 -> "*"
     * f2 -> PrimaryExpression()
     */
    public LinkedList<String> visit(TimesExpression n, Void argu) throws Exception {
       return bothAreSpecificType(n.f0, n.f2, "int");
    }
    /**
     * f0 -> PrimaryExpression()
     * f1 -> "["
     * f2 -> PrimaryExpression()
     * f3 -> "]"
     */
    public LinkedList<String> visit(ArrayLookup n, Void argu) throws Exception {
        LinkedList<String> type = new LinkedList<String>();
        if (!n.f2.accept(this, argu).get(0).equals("int")) { throw new Exception("Array lookup needs an int inside the brackets"); }
        type.add(n.f0.accept(this, argu).get(0));
        return type;
    }
    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> "length"
     */
    public LinkedList<String> visit(ArrayLength n, Void argu) throws Exception {
        LinkedList<String> type = new LinkedList<String>();
        String exp_type = n.f0.accept(this, argu).get(0);
        if (!exp_type.endsWith("[]")) { throw new Exception("tried to get array length of something that is not an array"); }
        type.add(exp_type);
        return type;
    }
    /**
     * f0 -> PrimaryExpression() ->must be class
     * f1 -> "."
     * f2 -> Identifier() ->must be function of class
     * f3 -> "("
     * f4 -> ( ExpressionList() )? -> must be correct (methodInfo)
     * f5 -> ")"
     */
    public LinkedList<String> visit(MessageSend n, Void argu) throws Exception {// this won't work until i create the thing that grabs all classes and functions at the start
        String className = n.f0.accept(this, argu).get(0);
        // i think i don't have to check if the class exists because if it didn't then we'd already have thrown an exception
        // if (!classesAndTheirParents.containsKey(className)) {throw new Exception();} 
        String methodName = n.f2.accept(this, argu).get(0);

        //make sure the method exists in the class (but what about inherited methods? must check those too)
        MethodInfo methodInfo = methods.get(new ClassAndIdentifier(className, methodName));
        if (methodInfo == null) {throw new Exception("Void method was called on an object that doesn't have that method");}

        //make sure the expression list matches the methodInfo
        LinkedList<String> expressionList = n.f4.accept(this, argu);
        if (!expressionList.equals(methodInfo.argumentTypes)) {throw new Exception("The types of the arguments given to a method do not match its signature");}

        //return our return type
        LinkedList<String> returnType = new LinkedList<String>();
        returnType.add(methodInfo.returnType);
        return returnType;
    }   
    /**
     * f0 -> Expression()
     * f1 -> ExpressionTail()
     */
    public LinkedList<String> visit(ExpressionList n, Void argu) throws Exception {
       LinkedList<String> typesOfExpressions = new LinkedList<String>();
       typesOfExpressions.add(n.f0.accept(this, argu).get(0));
       typesOfExpressions.add(n.f1.accept(this, argu).get(0));
       return typesOfExpressions;
    }
    /**
     * f0 -> ( ExpressionTerm() )*
     */
    public LinkedList<String> visit(ExpressionTail n, Void argu) throws Exception {
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
    public LinkedList<String> visit(ExpressionTerm n, Void argu) throws Exception {
       LinkedList<String> type = new LinkedList<String>();
       type.add(n.f1.accept(this, argu).get(0));
       return type;
    }
    /**
     * f0 -> NotExpression()
     *       | PrimaryExpression()
     */
    public LinkedList<String> visit(Clause n, Void argu) throws Exception {
       return n.f0.accept(this, argu); //todo: does thid need anything?
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
    public LinkedList<String> visit(PrimaryExpression n, Void argu) throws Exception {
       return n.f0.accept(this, argu);
    }   
    /**
     * f0 -> <INTEGER_LITERAL>
     */
    public LinkedList<String> visit(IntegerLiteral n, Void argu) throws Exception {
        LinkedList<String> type = new LinkedList<String>();
        type.add("int");
        return type;
    }   
    /**
     * f0 -> "true"
     */
    public LinkedList<String> visit(TrueLiteral n, Void argu) throws Exception {
        LinkedList<String> type = new LinkedList<String>();
        type.add("boolean");
        return type; //extract to createListWithOneElement ?
    }   
    /**
     * f0 -> "false"
     */
    public LinkedList<String> visit(FalseLiteral n, Void argu) throws Exception {
        LinkedList<String> type = new LinkedList<String>();
        type.add("boolean");
        return type;
    }   
    /**
     * f0 -> "this"
     */
    public LinkedList<String> visit(ThisExpression n, Void argu) throws Exception {
        LinkedList<String> type = new LinkedList<String>();
        type.add(currentClass);
        return type;
    }   
    /**
     * f0 -> BooleanArrayAllocationExpression()
     *       | IntegerArrayAllocationExpression()
     */
    public LinkedList<String> visit(ArrayAllocationExpression n, Void argu) throws Exception {
       return n.f0.accept(this, argu);
    }
    private LinkedList<String> checkNodeForTypeAndReturnAnother (Node node, String typeToCheckFor, String typeToReturn) throws Exception{
        if (!node.accept(this,null).equals(typeToCheckFor)) {throw new Exception("wrong type, was supposed to be " + typeToCheckFor);}
        LinkedList<String> type = new LinkedList<String>();
        type.add(typeToReturn);
        return type;
    }
    /**
     * f0 -> "new"
     * f1 -> "boolean"
     * f2 -> "["
     * f3 -> Expression()
     * f4 -> "]"
     */
    public LinkedList<String> visit(BooleanArrayAllocationExpression n, Void argu) throws Exception {
        return checkNodeForTypeAndReturnAnother(n.f3, "int", "boolean[]");
    }   
    /**
     * f0 -> "new"
     * f1 -> "int"
     * f2 -> "["
     * f3 -> Expression()
     * f4 -> "]"
     */
    public LinkedList<String> visit(IntegerArrayAllocationExpression n, Void argu) throws Exception {
        return checkNodeForTypeAndReturnAnother(n.f3, "int", "int[]");
    }   
    /**
     * f0 -> "new"
     * f1 -> Identifier()
     * f2 -> "("
     * f3 -> ")"
     */
    public LinkedList<String> visit(AllocationExpression n, Void argu) throws Exception {
        return checkNodeForTypeAndReturnAnother(n.f3, "int", "boolean[]");
    }   
    /**
     * f0 -> "!"
     * f1 -> Clause()
     */
    public LinkedList<String> visit(NotExpression n, Void argu) throws Exception {
        return checkNodeForTypeAndReturnAnother(n.f1, "boolean", "boolean");
    }   
    /**
     * f0 -> "("
     * f1 -> Expression()
     * f2 -> ")"
     */
    public LinkedList<String> visit(BracketExpression n, Void argu) throws Exception {
        return n.f1.accept(this, argu);
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
   public LinkedList<String> visit(Statement n, Void argu) throws Exception {
      return n.f0.accept(this, argu);
   }

   /**
    * f0 -> "{"
    * f1 -> ( Statement() )*
    * f2 -> "}"
    */
   public LinkedList<String> visit(Block n, Void argu) throws Exception {
      LinkedList<String> _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> Identifier()
    * f1 -> "="
    * f2 -> Expression()
    * f3 -> ";"
    */
    private LinkedList<String> bothAreSameType(Node node1, Node node2) throws Exception{
        LinkedList<String> ret = new LinkedList<String>();
        String type1 = node1.accept(this, null).get(0);
        String type2 = node2.accept(this, null).get(0);
        if (type1 == null || type2 == null) {throw new Exception("could not find type of identifier, probably not declared yet");}
        if (type1.equals(type2)) {ret.add(type1); return ret;}
        else throw new Exception("Expected that two things would be of the same type");
    }
    public LinkedList<String> visit(AssignmentStatement n, Void argu) throws Exception {
         return bothAreSameType(n.f0, n.f2);
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
    public LinkedList<String> visit(ArrayAssignmentStatement n, Void argu) throws Exception {
        LinkedList<String> returnType = new LinkedList<String>();
        String IDtype = n.f0.accept(this, argu).get(0);
        String rType = n.f5.accept(this, argu).get(0);
        returnType.add(rType);
        if (IDtype == null) { throw new Exception("Tried to do an array assignment Statement on something that has not been declared");}
        if (!IDtype.endsWith("[]")) {throw new Exception("tried to do an array assignment statement on a non-array");}
        if (!(n.f2.accept(this, argu).get(0) == "int")) { throw new Exception("Tried to do an array assignment Statement but the there wasn't an int inside the brackets");}
        if (!IDtype.equals(rType + "[]")) {throw new Exception("In an array assignment statement, the right hand value is not of the right type");}
        return returnType;
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
    public LinkedList<String> visit(IfStatement n, Void argu) throws Exception {
       if (!(n.f2.accept(this, argu).get(0) == "boolean")) {throw new Exception("if statement must have a boolean type in its parentheses");}
       n.f4.accept(this, argu);
       n.f6.accept(this, argu);
       return null;
    }   
    /**
     * f0 -> "while"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     */
    public LinkedList<String> visit(WhileStatement n, Void argu) throws Exception {
       if (!(n.f2.accept(this, argu).get(0) == "boolean")) {throw new Exception("while statement must have a boolean type in its parentheses");}
       n.f4.accept(this, argu);
       return null;
    }   
    /**
     * f0 -> "System.out.println"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> ";"
     */
    public LinkedList<String> visit(PrintStatement n, Void argu) throws Exception {
       LinkedList<String> _ret=null;
       n.f0.accept(this, argu);
       n.f1.accept(this, argu);
       n.f2.accept(this, argu);
       n.f3.accept(this, argu);
       n.f4.accept(this, argu);
       return _ret;
    }

}

class DeclarationCollectorVisitor extends GJDepthFirst<String, Void>{
    private HashMap<ClassAndIdentifier, MethodInfo> methods = new HashMap<ClassAndIdentifier, MethodInfo>(); // why would this be a symbol table? it would always have one layer that is never exited
    private HashMap<String, String> classesAndTheirParents = new HashMap<String, String>(); // merge into one with the above? would it make the algo for checking overloaded functions slower or faster?
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
    public String visit(MainClass n, Void argu) throws Exception {
        String mainClassName = n.f1.accept(this, null);
        currentClass = mainClassName;
        classesAndTheirParents.put(mainClassName, null);
        return null;
    }

    // returns the name, it will be a type if it's used to find the name of a class
    public String visit(Identifier n, Void argu) throws Exception {
        return n.f0.toString();
    }

   /**
    * f0 -> "class"
    * f1 -> Identifier()
    * f2 -> "{"
    * f3 -> ( VarDeclaration() )*
    * f4 -> ( MethodDeclaration() )*
    * f5 -> "}"
    */
   public String visit(ClassDeclaration n, Void argu) throws Exception {
        this.currentClass = n.f1.accept(this, null);
        if (classesAndTheirParents.containsKey(currentClass)) {throw new Exception("This class has already been declared elsewhere: " + currentClass); }
        classesAndTheirParents.put(currentClass, null);
        n.f4.accept(this, argu);
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
   public String visit(ClassExtendsDeclaration n, Void argu) throws Exception {
        this.currentClass = n.f1.accept(this, null);
        String parentClass = n.f3.accept(this, null);
        if (classesAndTheirParents.containsKey(currentClass)) {throw new Exception("This class has already been declared elsewhere: " + currentClass); }
        if (!classesAndTheirParents.containsKey(parentClass)) { throw new Exception("Class " + currentClass + " tried to inherit from a class called " + parentClass + ", but that class had not been declared yet or has no declaration at all");}
        classesAndTheirParents.put(currentClass, parentClass);
        n.f6.accept(this, argu);
        return null;
   }

   /**
    * f0 -> "public"
    * f1 -> Type()
    * f2 -> Identifier()
    * f3 -> "("
    * f4 -> ( FormalParameterList() )? -> visitor with return type List<String> which is just the types of the args in order
    * f5 -> ")"
    * f6 -> "{"
    * f7 -> ( VarDeclaration() )*
    * f8 -> ( Statement() )*
    * f9 -> "return"
    * f10 -> Expression()
    * f11 -> ";"
    * f12 -> "}"
    */
    public String visit(MethodDeclaration n, Void argu) throws Exception {
        String methodName = n.f2.accept(this, argu);
        LinkedList<String> argTypes = n.f4.present() ? n.f4.accept(new FormalParameterVisitor(), null).argumentTypes : new LinkedList<String>();
        MethodInfo methodInfo = new MethodInfo(n.f1.accept(this, argu), argTypes);
        if (methodInfo.returnType == "void") {throw new Exception("A void method was declared. There are no void methods in this language.");} 
        // first check the current class to see if this method name has already been used and error if it has
        // then check parent classes to see if the name has been used in which case they must have same methodInfo
        if (methods.containsKey(new ClassAndIdentifier(currentClass, methodName))) { throw new Exception("Declared the method " + methodName + " twice in one class");}
        String currentClassToCheckForWrongOverload = classesAndTheirParents.get(currentClass);
        while(!(currentClassToCheckForWrongOverload == null)){
            MethodInfo inheritedMethodInfo = methods.get(new ClassAndIdentifier(currentClassToCheckForWrongOverload, methodName));
            if (!(inheritedMethodInfo == null)){
                if (inheritedMethodInfo.equals(methodInfo)) { break; }
                else { throw new Exception("Incorrect overload for method " + methodName + " in class " + currentClass);}
            }
            currentClassToCheckForWrongOverload = classesAndTheirParents.get(currentClassToCheckForWrongOverload);
        }
        methods.put(new ClassAndIdentifier(currentClass, methodName), methodInfo);
        return null;
    }

   public String visit(BooleanArrayType n, Void argu) throws Exception {
       return "boolean[]";
   }

   public String visit(IntegerArrayType n, Void argu) throws Exception {
      return "int[]";
   }

   public String visit(BooleanType n, Void argu) throws Exception {
      return "boolean";
   }

   public String visit(IntegerType n, Void argu) throws Exception {
      return "int";
   }
}

//visitor that returns the string that represents primitive types, as well as IDs (identifiers) in string format
class StringRepresentationVisitor extends GJDepthFirst<String, Void>{
   public String visit(BooleanArrayType n, Void argu){
       return "boolean[]";
   }

   public String visit(IntegerArrayType n, Void argu){
      return "int[]";
   }

   public String visit(BooleanType n, Void argu){
      return "boolean";
   }

   public String visit(IntegerType n, Void argu){
      return "int";
   }
    // returns the name, it will be a type if it's used to find the name of a class
    public String visit(Identifier n, Void argu){
        return n.f0.toString();
    }
} //todo: use this to get rid of repetition in other visitors

class FormalParamListData{
    public LinkedList<String> argumentTypes = new LinkedList<String>();
    public LinkedList<String> argumentIDs = new LinkedList<String>();
    public void add(String type, String ID){
        this.argumentTypes.add(type);
        this.argumentIDs.add(ID);
    }
    public void addAll(FormalParamListData otherList){
        this.argumentTypes.addAll(otherList.argumentTypes);
        this.argumentIDs.addAll(otherList.argumentIDs);
    }
    public int size(){
        return argumentTypes.size();
    }
}

class FormalParameterVisitor extends GJDepthFirst<FormalParamListData, Void>{
    private StringRepresentationVisitor stringRep = new StringRepresentationVisitor();
   /**
    * f0 -> FormalParameter()
    * f1 -> FormalParameterTail()
    */
    public FormalParamListData visit(FormalParameterList n, Void argu) throws Exception {
        FormalParamListData list = n.f0.accept(this, argu);
        list.addAll(n.f1.accept(this, argu));
        return list;
    }

   /**
    * f0 -> Type()
    * f1 -> Identifier()
    */
    public FormalParamListData visit(FormalParameter n, Void argu) throws Exception {
        FormalParamListData data = new FormalParamListData();
        data.add(n.f0.accept(stringRep, null), n.f1.accept(stringRep, null));
        return data;
    }

   /**
    * f0 -> ( FormalParameterTerm() )*
    */
    public FormalParamListData visit(FormalParameterTail n, Void argu) throws Exception {
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
    public FormalParamListData visit(FormalParameterTerm n, Void argu) throws Exception {
        return n.f1.accept(this, argu);
    }

}