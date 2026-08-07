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

            Visitor eval = new Visitor();
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

class MethodInfo{ //todo: implement equals() and use it for checking that a function isn't overloaded because that's not part of minijava
    public String returnType;
    public LinkedList<String> argumentTypes; //todo: arraylist because i could figure it out all at once and so it won't have to be resized?
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
        if (!(this.lookup(classAndIdentifier) == null)) throw new Exception("An identifier was used twice in the same scope.");
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

class Visitor extends GJDepthFirst<LinkedList<String>, Void>{
    //in SymbolTable<ClassAndIdentifier, String> the string represents the type of the field/variable
    private SymbolTable<ClassAndIdentifier, String> variableSymbolTable = new SymbolTable<ClassAndIdentifier, String>();
    private HashMap<ClassAndIdentifier, MethodInfo> methods = new HashMap<ClassAndIdentifier, MethodInfo>(); // why would this be a symbol table? it would always have one layer that is never exited
    private HashMap<String, String> classesAndTheirParents = new HashMap<String, String>(); // merge into one with the above? would it make the algo for checking overloaded functions slower or faster?
    private String currentClass;

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

        String mainClassName = n.f1.accept(this, null).get(1);
        currentClass = mainClassName;
        classesAndTheirParents.put(mainClassName, null);

        // we don't add main method to the table since it's handled by exception later, just immediately start taking its local variables
        variableSymbolTable.enter();
        for(int i = 0; i < n.f14.size(); i++){
            LinkedList<String> typeAndID = n.f14.elementAt(i).accept(this, null);
            variableSymbolTable.insert(new ClassAndIdentifier(currentClass, typeAndID.get(1)), typeAndID.get(0));
        }
        // handle statements here
        n.f15.accept(this, null);
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
        typeIfItExistsAndID.add(variableSymbolTable.lookup(new ClassAndIdentifier(currentClass, IDname)));
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