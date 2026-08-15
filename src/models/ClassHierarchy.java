package models;

import java.util.Map;
import java.util.HashMap;
import exceptions.SemanticCheckException;

public class ClassHierarchy {
    public record ClassInfo(String parentClassName, Map<String, MethodInfo> methods) {
    }

    private final Map<String, ClassInfo> classes;

    private ClassHierarchy(Map<String, ClassInfo> classes) {
        this.classes = classes;
    }

    public boolean classExists(String className) {
        return classes.containsKey(className);
    }

    public String getParent(String className) {
        ClassInfo info = classes.get(className);
        return info != null ? info.parentClassName() : null;
    }

    public MethodInfo getMethod(String className, String methodName) {
        String currentClass = className;
        while (currentClass != null) {
            ClassInfo info = classes.get(currentClass);
            if (info != null && info.methods().containsKey(methodName)) {
                return info.methods().get(methodName);
            }
            currentClass = getParent(currentClass);
        }
        return null;
    }

    // Builder class for constructing the hierarchy
    public static class Builder {
        private Map<String, ClassInfo> classes = new HashMap<>();

        public void addClass(String className, String parentClassName) throws SemanticCheckException {
            if (classes.containsKey(className)) {
                throw new SemanticCheckException("This class has already been declared elsewhere: " + className);
            }
            if (parentClassName != null && !classes.containsKey(parentClassName)) {
                throw new SemanticCheckException("Class " + className + " tried to inherit from a class called "
                        + parentClassName + ", but that class had not been declared yet or has no declaration at all");
            }
            classes.put(className, new ClassInfo(parentClassName, new HashMap<>()));
        }

        public void addClass(String className) throws SemanticCheckException {
            addClass(className, null);
        }

        public void addMethod(String className, String methodName, MethodSignature signature)
                throws SemanticCheckException {
            if (signature.returnType().equals("void")) {
                throw new SemanticCheckException("A void method was declared. There are no void methods in minijava.");
            }
            Map<String, MethodInfo> methods = classes.get(className).methods();
            if (methods.containsKey(methodName)) {
                throw new SemanticCheckException("Declared the method " + methodName + " twice in one class");
            }
            // Overriding is allowed, but overloads are not.
            boolean isOverride = false;
            String currentClassToCheckForOverload = classes.get(className).parentClassName();
            while (currentClassToCheckForOverload != null) {
                Map<String, MethodInfo> inheritedMethods = classes.get(currentClassToCheckForOverload).methods();
                MethodInfo inheritedMethodWithSameName = inheritedMethods.get(methodName);
                if (inheritedMethodWithSameName != null) {
                    if (!inheritedMethodWithSameName.signature().equals(signature)) {
                        throw new SemanticCheckException(
                                "In class " + className + ": Tried to overload method " + methodName
                                        + " inherited from "
                                        + currentClassToCheckForOverload + ". Minijava does not support overloads.");
                    }
                    isOverride = true;
                }
                currentClassToCheckForOverload = classes.get(currentClassToCheckForOverload).parentClassName();
            }
            methods.put(methodName, new MethodInfo(signature, isOverride));
        }

        public ClassHierarchy build() {
            return new ClassHierarchy(this.classes);
        }
    }
}