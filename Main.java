import syntaxtree.*;
import visitor.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.util;
import javafx.util;

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

            Visitor eval = new MyVisitor();
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

enum Type{
    INT,
    INT_ARRAY,
    BOOL,
    BOOL_ARRAY,
    FUNC
}

class AdditionalInfo{
    public String returnType;
    public LinkedList<String> arguments; //todo: arraylist because i could figure it out all at once and so it won't have to be resized?
}

class IdentifierInfo{
    public Type type;
    public AdditionalInfo additionalInfo;
}

class SymbolTable{
    // Pair<ClassName, IdentifierName>, IdentifierInfo>
    private LinkedList<Map<Pair<String, String>, IdentifierInfo>> symbols;
    public void enter(){
        symbols.add(0, new Map<Pair<String, String>, IdentifierInfo>());
    }
    public void insert(Map<Pair<String, String>, IdentifierInfo> newSymbol){
        symbols.get(0).add(newSymbol);
    }
    //returns null if the specified symbol does not exist
    public IdentifierInfo lookup(Pair<String, String> key){
        for (Map<Pair<String, String>, IdentifierInfo> table:symbols){
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

}