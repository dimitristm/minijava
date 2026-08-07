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
    public void insert(K classAndIdentifier, V info){
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

        String mainClassName = n.f1.accept(this, null).get(0);
        currentClass = mainClassName;
        classesAndTheirParents.put(mainClassName, null);

        // we don't add main method to the table since it's handled by exception later, just immediately start taking its local variables
        variableSymbolTable.enter();
        for(int i = 0; i < n.f14.size(); i++){
            LinkedList<String> typeAndID = n.f14.elementAt(i).accept(this, null);
            variableSymbolTable.insert(new ClassAndIdentifier(currentClass, typeAndID.get(0)), typeAndID.get(1));
        }
        // handle statements here

        variableSymbolTable.exit();

        super.visit(n, argu);
        return null;
    }

    /**
    * f0 -> Type()
    * f1 -> Identifier()
    * f2 -> ";"
    */
    // index 0 = type, index 1 = identifier name
    public LinkedList<String> visit(VarDeclaration n, Void argu) throws Exception {
        LinkedList<String> ret = new LinkedList<String>();
        ret.add(n.f0.accept(this, null).get(0));
        ret.add(n.f1.accept(this, null).get(0));
        return ret;
    }

    // returns just the name of the identifier in index 0
    public LinkedList<String> visit(Identifier n, Void argu) throws Exception {
        LinkedList<String> ret = new LinkedList<String>();
        ret.add(n.f0.toString());
        return ret;
    }

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

}