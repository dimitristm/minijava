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

class FunctionInfo{ //todo: implement equals() and use it for checking that a function isn't overloaded because that's not part of minijava
    public String returnType;
    public LinkedList<String> argumentTypes; //todo: arraylist because i could figure it out all at once and so it won't have to be resized?
}

class IdentifierInfo{
    public String type;
    public FunctionInfo additionalInfo; //if the identifier is actually a function, use this. todo: seperate symbol table for functions and romeve this from regular symbol table?
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

class SymbolTable{
    // Pair<ClassName, IdentifierName>, IdentifierInfo>
    private LinkedList<HashMap<ClassAndIdentifier, IdentifierInfo>> symbols;
    public void enter(){
        symbols.add(0, new HashMap<ClassAndIdentifier, IdentifierInfo>());
    }
    public void insert(ClassAndIdentifier classAndIdentifier, IdentifierInfo identifierInfo){
        symbols.get(0).put(classAndIdentifier, identifierInfo);
    }
    //returns null if the specified symbol does not exist
    public IdentifierInfo lookup(ClassAndIdentifier key){
        for (Map<ClassAndIdentifier, IdentifierInfo> table:symbols){
            IdentifierInfo val = table.get(key);
            if (val != null) {return val;}
        }
        return null;
    }
    public void exit(){
        symbols.remove(0);
    }
}

class Visitor extends GJDepthFirst<String, Void>{
    private SymbolTable symbolTable;
    private String currentClass;

    @Override
    public String visit(MainClass n, Void argu) throws Exception {
        this.currentClass = "Main"; //todo: looks like main class doesn't need to be called main
        super.visit(n, argu);
        return null;
    }

    /**
    * f0 -> Type()
    * f1 -> Identifier()
    * f2 -> ";"
    */
    public R visit(VarDeclaration n, A argu) throws Exception {
        String 
        super.visit(n, argu);
    }
}