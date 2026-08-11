import syntaxtree.*;
import visitors.*;
import exceptions.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

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
                root.accept(declarations);
                TypecheckVisitor eval = new TypecheckVisitor(declarations.getMethods(), declarations.getClassesAndTheirParents());
                root.accept(eval, false);
                System.err.println("SUCCESS: Program " + arg + " passed the semantic check.");

                System.out.println("Offsets:");
                OffsetGeneratorVisitor ofvis = new OffsetGeneratorVisitor(declarations.getMethods(), declarations.getClassesAndTheirParents());
                root.accept(ofvis);
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
